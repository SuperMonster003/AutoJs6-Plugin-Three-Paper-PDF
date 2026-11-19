package io.github.supermonster003.autojs6.plugin.three.paper.pdf.textlayer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sampleFile
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * `addTextLayer` through Binder (roadmap P6.5, E2): after writing, `getText` answers every line (Chinese, Latin, mixed,
 * emoji), `search` finds each line with a rectangle whose intersection over union with the line box is at least 0.5
 * (also on rotated pages and under a crop box), the system `PdfRenderer` renders every page pixel for pixel as before,
 * the font objects of a document stay within 2 KiB, pages that carry text are skipped unless `force`, empty layers write
 * the source unchanged, and too many lines, a too large payload or a page outside the document fail. Numbers go to
 * logcat under `PaperTextLayer`.
 * zh-CN: 经 Binder 验证 `addTextLayer` (路线图 P6.5, E2): 写入后 `getText` 取回每一行 (中文, 拉丁, 混排, 表情), `search` 找到每一行,
 * 命中矩形与行框的交并比不低于 0.5 (旋转页与裁剪框下同样), 系统 `PdfRenderer` 对每页的渲染与写入前逐像素相同, 每个文档的字体对象
 * 合计不超过 2 KiB, 带文本的页除非 `force` 否则跳过, 空图层原样写出, 行数过多, payload 过大或页码不在文档内时失败. 数值以
 * `PaperTextLayer` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class TextLayerDeviceTest {

    private val directory = File(context.cacheDir, "text-layer-op")

    @After
    fun removeOutputs() {
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    /** Records the end of one operation. zh-CN: 记录一个操作的结束. */
    private class Recorder : IPdfProgressCallback.Stub() {

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        private val ended = CountDownLatch(1)

        override fun onProgress(opId: Int, progress: Bundle) = Unit

        override fun onComplete(opId: Int, result: Bundle) {
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            this.error = error
            ended.countDown()
        }

        fun await(): Recorder {
            assertTrue("the operation did not end in time", ended.await(300, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

        fun failedWith(): String? = error?.getString(PdfContract.KEY_ERROR_CODE)

    }

    /** A line on a `width` x `height` image. zh-CN: `width` x `height` 图像上的一行. */
    private class Line(val text: String, vararg val bounds: Double)

    private class Layer(val page: Int, val width: Double, val height: Double, val lines: List<Line>)

    private fun file(name: String): File = File(directory, name).also { it.parentFile?.mkdirs() }

    private fun read(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun write(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

    private fun payload(layers: List<Layer>): String = JSONObject().put(
        PdfContract.FIELD_LAYERS,
        JSONArray().apply {
            for (layer in layers) {
                put(
                    JSONObject()
                        .put(PdfContract.FIELD_PAGE, layer.page)
                        .put(PdfContract.FIELD_WIDTH, layer.width)
                        .put(PdfContract.FIELD_HEIGHT, layer.height)
                        .put(PdfContract.FIELD_LINES, JSONArray().apply {
                            for (line in layer.lines) {
                                put(JSONObject().put(PdfContract.FIELD_TEXT, line.text).put(PdfContract.FIELD_BOUNDS, JSONArray().apply { line.bounds.forEach { put(it) } }))
                            }
                        }),
                )
            }
        },
    ).toString()

    private fun IPdfPlugin.run(source: File, payload: File, target: File, force: Boolean = false): Recorder {
        val recorder = Recorder()
        read(source).use { input ->
            read(payload).use { data ->
                write(target).use { output ->
                    val request = Bundle().apply {
                        putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
                        putString(PdfContract.KEY_OP, PdfOps.ADD_TEXT_LAYER)
                        if (force) putBoolean(PdfContract.KEY_FORCE, true)
                    }
                    run(request, arrayOf(input, data), output, recorder)
                }
            }
        }
        return recorder.await()
    }

    private fun IPdfPlugin.run(source: File, payload: String, target: File, force: Boolean = false): Recorder =
        run(source, file("payload-${SystemClock.elapsedRealtimeNanos()}.json").apply { writeText(payload) }, target, force)

    private fun <T> IPdfPlugin.opened(file: File, block: (IPdfDocument) -> T): T {
        val handle = read(file).use { open(it, request()) }
        val document = IPdfDocument.Stub.asInterface(MarshallingBinder(handle.asBinder()))
        try {
            return block(document)
        } finally {
            document.close()
        }
    }

    private fun Bundle.succeeded(): Bundle {
        assertNull(getString(PdfContract.KEY_ERROR_MESSAGE), getString(PdfContract.KEY_ERROR_CODE))
        return this
    }

    /** The text of [page] (reading layout), one trimmed string per line. zh-CN: [page] 的文本 (阅读布局), 每行一个去掉首尾空白的字符串. */
    private fun IPdfDocument.lines(page: Int): List<String> =
        getText(request { putString(PdfContract.KEY_PAGES, "$page") }).succeeded().getStringArray(PdfContract.KEY_TEXTS)!!.single().lines().map { it.trim() }

    /** The hits of [keyword]: page and rectangles in display points. zh-CN: [keyword] 的命中: 页码与以显示点数表示的矩形. */
    private fun IPdfDocument.hits(keyword: String): List<Pair<Int, List<DoubleArray>>> {
        val answer = search(request { putString(PdfContract.KEY_KEYWORD, keyword) }).succeeded()
        val items = JSONArray(answer.getString(PdfContract.KEY_ITEMS))
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val rects = item.getJSONArray(PdfContract.FIELD_RECTS)
            item.getInt(PdfContract.FIELD_PAGE) to List(rects.length()) { r -> rects.getJSONArray(r).let { rect -> DoubleArray(4) { rect.getDouble(it) } } }
        }
    }

    /** Display size (points) of every page: crop box size turned by `/Rotate`. zh-CN: 每页的显示尺寸 (点): 按 `/Rotate` 转过的裁剪框尺寸. */
    private fun displaySizes(file: File): List<Pair<Double, Double>> = PDDocument.load(file).use { document ->
        document.pages.map { page ->
            val box = page.cropBox
            if (Math.floorMod(page.rotation, 180) == 90) box.height.toDouble() to box.width.toDouble() else box.width.toDouble() to box.height.toDouble()
        }
    }

    private fun iou(a: DoubleArray, b: DoubleArray): Double {
        val width = min(a[2], b[2]) - max(a[0], b[0])
        val height = min(a[3], b[3]) - max(a[1], b[1])
        if (width <= 0 || height <= 0) return 0.0
        val intersection = width * height
        return intersection / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - intersection)
    }

    /**
     * Checks that every line of [layers] is in the text of its page and found by `search` within IoU 0.5 of its box;
     * answers the smallest IoU.
     * zh-CN: 检查 [layers] 的每一行都在所在页的文本中, 且 `search` 的命中与行框的交并比不低于 0.5; 返回最小交并比.
     */
    private fun IPdfPlugin.assertReadBack(target: File, layers: List<Layer>): Double {
        val sizes = displaySizes(target)
        var smallest = 1.0
        opened(target) { document ->
            for (layer in layers) {
                val text = document.lines(layer.page)
                val (displayWidth, displayHeight) = sizes[layer.page - 1]
                for (line in layer.lines) {
                    assertTrue("page ${layer.page}: \"${line.text}\" not in $text", line.text in text)
                    val expected = doubleArrayOf(
                        line.bounds[0] * displayWidth / layer.width, line.bounds[1] * displayHeight / layer.height,
                        line.bounds[2] * displayWidth / layer.width, line.bounds[3] * displayHeight / layer.height,
                    )
                    val hits = document.hits(line.text).filter { it.first == layer.page }
                    assertEquals("page ${layer.page}: hits of \"${line.text}\"", 1, hits.size)
                    val rects = hits.single().second
                    val found = doubleArrayOf(rects.minOf { it[0] }, rects.minOf { it[1] }, rects.maxOf { it[2] }, rects.maxOf { it[3] })
                    val overlap = iou(expected, found)
                    assertTrue("page ${layer.page}: \"${line.text}\" expected ${expected.toList()}, found ${found.toList()}, IoU $overlap", overlap >= 0.5)
                    smallest = min(smallest, overlap)
                }
            }
        }
        return smallest
    }

    /** Pixels of page [index] rendered by `PdfRenderer` at 144 dpi. zh-CN: `PdfRenderer` 以 144 dpi 渲染的第 [index] 页像素. */
    private fun render(file: File, index: Int): IntArray = read(file).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, Matrix().apply { setScale(2f, 2f) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.also { bitmap.recycle() }
            }
        }
    }

    private fun pageCount(file: File): Int = read(file).use { PdfRenderer(it).use { renderer -> renderer.pageCount } }

    /** Checks that `PdfRenderer` renders every page of [after] as [before]; answers the pages. zh-CN: 检查 `PdfRenderer` 对 [after] 每页的渲染与 [before] 相同; 返回页数. */
    private fun assertSameRendering(before: File, after: File): Int {
        val pages = pageCount(before)
        assertEquals(pages, pageCount(after))
        for (index in 0 until pages) {
            val old = render(before, index)
            val new = render(after, index)
            assertEquals(old.size, new.size)
            val differing = old.indices.count { old[it] != new[it] }
            assertEquals("page ${index + 1}: $differing of ${old.size} pixels differ", 0, differing)
        }
        return pages
    }

    /** Bytes of the indirect objects reachable from the layer font. zh-CN: 从图层字体可达的间接对象的字节数. */
    private fun fontBytes(file: File): Int {
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        val sizes = Regex("""(?m)^(\d+) 0 obj""").findAll(raw).associate { match ->
            match.groupValues[1].toLong() to raw.indexOf("endobj", match.range.last) + "endobj".length - match.range.first
        }
        return PDDocument.load(file).use { document ->
            val fonts = document.pages.mapNotNull { page ->
                val dictionary = page.resources?.cosObject?.getDictionaryObject(COSName.FONT) as? COSDictionary
                dictionary?.keySet()?.firstOrNull { it.name.startsWith("GL") }?.let { dictionary.getItem(it) as? COSObject }
            }
            assertEquals("one layer font per document", 1, fonts.map { it.objectNumber }.toSet().size)
            val found = LinkedHashSet<Long>()
            fun visit(base: COSBase?) {
                when (base) {
                    is COSObject -> if (found.add(base.objectNumber)) visit(base.`object`)
                    is COSDictionary -> base.values.forEach { visit(it) }
                    is COSArray -> for (index in 0 until base.size()) visit(base.get(index))
                    else -> Unit
                }
            }
            visit(fonts.first())
            found.sumOf { sizes.getValue(it) }
        }
    }

    private val emoji = String(Character.toChars(0x1F600))

    @Test
    fun linesAreReadBackFoundAndInvisible() = withPdfPlugin { plugin ->
        val source = sampleFile("scanned.pdf")
        val (width, height) = displaySizes(source).single().let { (w, h) -> w * 300 / 72 to h * 300 / 72 }
        val layers = listOf(
            Layer(
                1, width, height,
                listOf(
                    Line("中文识别结果第一行", 180.0, 240.0, 1460.0, 300.0),
                    Line("Latin line, quick brown fox", 180.0, 400.0, 1700.0, 460.0),
                    Line("混排 Mixed 文本 2026", 180.0, 560.0, 1500.0, 640.0),
                    Line("表情 $emoji smile", 180.0, 720.0, 1000.0, 790.0),
                ),
            ),
        )
        val target = file("scanned-layer.pdf")
        val started = SystemClock.elapsedRealtime()
        val result = plugin.run(source, payload(layers), target).completed()
        val elapsed = SystemClock.elapsedRealtime() - started
        assertEquals(1, result.getInt(PdfContract.KEY_LAYER_PAGES))
        assertEquals(0, result.getInt(PdfContract.KEY_SKIPPED_PAGES))
        assertEquals(4, result.getInt(PdfContract.KEY_LINES))
        assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        assertFalse(result.containsKey(PdfContract.KEY_WARNINGS))
        val smallest = plugin.assertReadBack(target, layers)
        val pages = assertSameRendering(source, target)
        val fonts = fontBytes(target)
        assertTrue("font objects take $fonts bytes", fonts <= 2048)
        log("scanned.pdf: 4 lines in $elapsed ms, smallest IoU ${"%.3f".format(smallest)}, $pages page(s) render the same, font objects $fonts bytes, ${source.length()} -> ${target.length()} bytes")
    }

    @Test
    fun rotatedPagesAndCropBoxesPlaceTheLines() = withPdfPlugin { plugin ->
        val source = sampleFile("rotated.pdf")
        // The sample's own two lines sit near the top of the unrotated page, that is along one edge of the displayed
        // page; the overlay rows stay in the middle so text extraction never merges them with those.
        // zh-CN: 样本自带的两行在未旋转页面的顶部, 即显示页面的某一条边上; 叠加行放在中部, 文本提取不会把它们与原有文字并成一行.
        val layers = (1..5).map { page ->
            Layer(page, 1000.0, 1000.0, listOf(Line("Overlay $page alpha", 250.0, 400.0, 750.0, 460.0), Line("Second row $page beta", 300.0, 600.0, 700.0, 660.0)))
        }
        val target = file("rotated-layer.pdf")
        val result = plugin.run(source, payload(layers), target, force = true).completed()
        assertEquals(5, result.getInt(PdfContract.KEY_LAYER_PAGES))
        assertEquals(10, result.getInt(PdfContract.KEY_LINES))
        val rotated = plugin.assertReadBack(target, layers)
        val rotatedPages = assertSameRendering(source, target)
        val cropped = file("cropped.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(0f, 0f, 600f, 800f))
            page.cropBox = PDRectangle(50f, 100f, 450f, 500f)
            page.rotation = 270
            document.addPage(page)
            document.save(cropped)
        }
        val cropLayers = listOf(Layer(1, 1200.0, 1000.0, listOf(Line("Cropped and turned", 100.0, 100.0, 900.0, 160.0), Line("Lower line 甲乙", 300.0, 800.0, 1100.0, 900.0))))
        val croppedTarget = file("cropped-layer.pdf")
        plugin.run(cropped, payload(cropLayers), croppedTarget).completed()
        val crop = plugin.assertReadBack(croppedTarget, cropLayers)
        assertSameRendering(cropped, croppedTarget)
        log("rotated.pdf (force): smallest IoU ${"%.3f".format(rotated)}, $rotatedPages page(s) render the same; crop box + /Rotate 270: smallest IoU ${"%.3f".format(crop)}")
    }

    @Test
    fun pagesWithTextAreSkippedUnlessForced() = withPdfPlugin { plugin ->
        val mixed = file("mixed.pdf")
        PDDocument().use { document ->
            val text = PDPage(PDRectangle.A4).also { document.addPage(it) }
            PDPageContentStream(document, text).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 14f)
                content.newLineAtOffset(72f, 700f)
                content.showText("Existing text")
                content.endText()
            }
            document.addPage(PDPage(PDRectangle.A4))
            document.save(mixed)
        }
        val layers = (1..2).map { page -> Layer(page, 595.0, 842.0, listOf(Line("Recognized $page", 72.0, 300.0, 300.0, 320.0))) }
        val target = file("mixed-layer.pdf")
        val result = plugin.run(mixed, payload(layers), target).completed()
        assertEquals(1, result.getInt(PdfContract.KEY_LAYER_PAGES))
        assertEquals(1, result.getInt(PdfContract.KEY_SKIPPED_PAGES))
        assertEquals(1, result.getInt(PdfContract.KEY_LINES))
        plugin.opened(target) { document ->
            assertFalse("Recognized 1" in document.lines(1))
            assertTrue("Existing text" in document.lines(1))
            assertTrue("Recognized 2" in document.lines(2))
        }
        val forced = file("mixed-forced.pdf")
        val again = plugin.run(mixed, payload(layers), forced, force = true).completed()
        assertEquals(2, again.getInt(PdfContract.KEY_LAYER_PAGES))
        assertEquals(0, again.getInt(PdfContract.KEY_SKIPPED_PAGES))
        plugin.assertReadBack(forced, layers)
        assertSameRendering(mixed, forced)
        val texted = sampleFile("text.pdf")
        val skipped = plugin.run(texted, payload(listOf(Layer(1, 100.0, 100.0, listOf(Line("Never written", 10.0, 10.0, 90.0, 20.0))))), file("text-skipped.pdf")).completed()
        assertEquals(0, skipped.getInt(PdfContract.KEY_LAYER_PAGES))
        assertEquals(1, skipped.getInt(PdfContract.KEY_SKIPPED_PAGES))
        assertEquals(0, skipped.getInt(PdfContract.KEY_LINES))
        log("mixed.pdf: page 1 skipped, page 2 written; forced: both written and rendered the same; text.pdf skipped")
    }

    @Test
    fun emptyLayersWriteTheSourceUnchanged() = withPdfPlugin { plugin ->
        val source = sampleFile("text.pdf")
        val target = file("text-empty.pdf")
        val result = plugin.run(source, """{ "layers": [] }""", target).completed()
        assertEquals(0, result.getInt(PdfContract.KEY_LAYER_PAGES))
        assertEquals(0, result.getInt(PdfContract.KEY_SKIPPED_PAGES))
        assertEquals(0, result.getInt(PdfContract.KEY_LINES))
        assertEquals(1, result.getInt(PdfContract.KEY_PAGE_COUNT))
        val before = plugin.opened(source) { it.lines(1) }
        val after = plugin.opened(target) { it.lines(1) }
        assertEquals(before, after)
        assertFalse(String(target.readBytes(), Charsets.ISO_8859_1).contains("GlyphLessFont"))
        assertSameRendering(source, target)
        log("text.pdf with empty layers: same text and rendering, rewritten ${source.length()} -> ${target.length()} bytes")
    }

    @Test
    fun limitsAndPagesOutsideTheDocumentFail() = withPdfPlugin { plugin ->
        val source = sampleFile("scanned.pdf")
        val lines = file("too-many-lines.json")
        lines.bufferedWriter().use { writer ->
            writer.write("""{ "layers": [ { "page": 1, "width": 100, "height": 100, "lines": [""")
            for (index in 0..PdfContract.MAX_TEXT_LAYER_LINES) {
                if (index > 0) writer.write(",")
                writer.write("""{"text":"a","bounds":[0,0,1,1]}""")
            }
            writer.write("] } ] }")
        }
        assertTrue("${lines.length()} bytes stay under the payload ceiling", lines.length() < PdfContract.MAX_PAYLOAD_BYTES)
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, plugin.run(source, lines, file("too-many-lines.pdf")).failedWith())
        val large = file("too-large.json")
        large.bufferedWriter().use { writer ->
            writer.write("""{ "layers": [ { "page": 1, "width": 100, "height": 100, "lines": [ { "text": """")
            val chunk = "a".repeat(64 * 1024)
            repeat((PdfContract.MAX_PAYLOAD_BYTES / chunk.length + 1).toInt()) { writer.write(chunk) }
            writer.write("""", "bounds": [0, 0, 1, 1] } ] } ] }""")
        }
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, plugin.run(source, large, file("too-large.pdf")).failedWith())
        val outside = payload(listOf(Layer(2, 100.0, 100.0, listOf(Line("x", 0.0, 0.0, 10.0, 10.0)))))
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, plugin.run(source, outside, file("outside.pdf")).failedWith())
        log("${PdfContract.MAX_TEXT_LAYER_LINES + 1} lines (${lines.length()} bytes) and ${large.length()} payload bytes: LIMIT_EXCEEDED; page 2 of 1: PAGE_OUT_OF_RANGE")
    }

    companion object {

        private const val TAG = "PaperTextLayer"

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

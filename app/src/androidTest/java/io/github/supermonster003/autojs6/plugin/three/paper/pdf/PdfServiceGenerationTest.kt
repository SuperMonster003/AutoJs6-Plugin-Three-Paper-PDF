package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.openSample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfCapabilityKeys
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Generation and image extraction through Binder (roadmap P2.5): `fromImages` with a JPEG embedded as stored and a PNG,
 * `fromText` with the system font and a font input, `fromHtml` (appendix D Q5: the output opens with the system
 * `PdfRenderer`, scripts run, remote resources are counted, and `UNSUPPORTED` where `generate.html` is not declared),
 * `planImages` + `extractImage` on `images.pdf`, and the appendix F timing of 20 images.
 * zh-CN: 经 Binder 验证生成与图片提取 (路线图 P2.5): `fromImages` 原样嵌入 JPEG 并转码 PNG, `fromText` 用系统字体与字体输入,
 * `fromHtml` (附录 D Q5: 输出可被系统 `PdfRenderer` 打开, 脚本会执行, 远程资源计数, 未声明 `generate.html` 时报 `UNSUPPORTED`),
 * 在 `images.pdf` 上的 `planImages` + `extractImage`, 以及附录 F 的 20 张图片耗时.
 */
@RunWith(AndroidJUnit4::class)
class PdfServiceGenerationTest {

    private val outputs = File(context.cacheDir, "generation")

    @After
    fun removeOutputs() {
        outputs.deleteRecursively()
    }

    /** Records the callbacks of one operation. zh-CN: 记录一个操作的回调. */
    private class Recorder : IPdfProgressCallback.Stub() {

        val progress: MutableList<Bundle> = Collections.synchronizedList(ArrayList())

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        private val ended = CountDownLatch(1)

        override fun onProgress(opId: Int, progress: Bundle) {
            this.progress += progress
        }

        override fun onComplete(opId: Int, result: Bundle) {
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            this.error = error
            ended.countDown()
        }

        fun await(seconds: Long = 60): Recorder {
            assertTrue("the operation did not end in time", ended.await(seconds, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

        fun failedWith(): String? = error?.getString(PdfContract.KEY_ERROR_CODE)

    }

    private fun output(name: String): File = File(outputs, name).also { it.parentFile?.mkdirs() }

    private fun writable(file: File): ParcelFileDescriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
    )

    private fun readable(file: File): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun IPdfPlugin.openFile(file: File): IPdfDocument = readable(file).use { source ->
        IPdfDocument.Stub.asInterface(MarshallingBinder(open(source, request()).asBinder()))
    }

    /** `run` [op] over [inputs] into [target]. zh-CN: 对 [inputs] 执行 `run` [op], 写入 [target]. */
    private fun IPdfPlugin.generate(op: String, inputs: List<File>, target: File, seconds: Long = 60, fill: Bundle.() -> Unit = {}): Recorder {
        val recorder = Recorder()
        val descriptors = inputs.map { readable(it) }
        try {
            writable(target).use { output ->
                run(request { putString(PdfContract.KEY_OP, op); fill() }, descriptors.toTypedArray(), output, recorder)
            }
        } finally {
            descriptors.forEach { it.close() }
        }
        return recorder.await(seconds)
    }

    private fun IPdfPlugin.pageSizes(file: File): List<Pair<Float, Float>> {
        val document = openFile(file)
        try {
            val info = document.info
            val widths = checkNotNull(info.getFloatArray(PdfContract.KEY_PAGE_WIDTHS))
            val heights = checkNotNull(info.getFloatArray(PdfContract.KEY_PAGE_HEIGHTS))
            return widths.indices.map { widths[it] to heights[it] }
        } finally {
            document.close()
        }
    }

    private fun IPdfPlugin.allText(file: File): String {
        val document = openFile(file)
        try {
            val answer = document.getText(request { putString(PdfContract.KEY_PAGES, "1-") })
            assertNull(answer.getString(PdfContract.KEY_ERROR_CODE))
            return checkNotNull(answer.getStringArray(PdfContract.KEY_TEXTS)).joinToString("\n")
        } finally {
            document.close()
        }
    }

    private fun imageFile(name: String, width: Int, height: Int, format: Bitmap.CompressFormat, alpha: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (alpha) Color.TRANSPARENT else Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(200, 60, 40)
        canvas.drawRect(0f, 0f, width / 2f, height / 2f, paint)
        paint.color = Color.rgb(40, 90, 200)
        canvas.drawCircle(width * 0.7f, height * 0.6f, minOf(width, height) / 4f, paint)
        val file = output(name)
        file.outputStream().use { bitmap.compress(format, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun assertSizes(expected: List<Pair<Float, Float>>, actual: List<Pair<Float, Float>>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (wanted, got) ->
            assertTrue("expected $wanted, got $got", abs(wanted.first - got.first) < 0.01f && abs(wanted.second - got.second) < 0.01f)
        }
    }

    @Test
    fun imagesBecomeOnePageEach(): Unit = withPdfPlugin { plugin ->
        val jpeg = imageFile("photo.jpg", 300, 200, Bitmap.CompressFormat.JPEG)
        val png = imageFile("drawing.png", 120, 160, Bitmap.CompressFormat.PNG, alpha = true)
        val fitted = output("fit.pdf")
        val recorder = plugin.generate(PdfOps.FROM_IMAGES, listOf(jpeg, png), fitted)
        val result = recorder.completed()
        assertEquals(2, result.getInt(PdfContract.KEY_PAGE_COUNT))
        assertEquals(fitted.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        assertTrue(recorder.progress.all { it.getString(PdfContract.KEY_OP) == PdfOps.FROM_IMAGES })
        // `fit` pages take the pixel size in points (no margin by default). zh-CN: `fit` 页面按像素尺寸取磅值 (缺省无页边距).
        assertSizes(listOf(300f to 200f, 120f to 160f), plugin.pageSizes(fitted))
        val document = plugin.openFile(fitted)
        try {
            // The PNG is transcoded to JPEG at the default quality 90. zh-CN: PNG 按默认质量 90 转码为 JPEG.
            assertEquals(listOf(PdfContract.FORMAT_JPEG, PdfContract.FORMAT_JPEG), imageFormats(document))
            // The JPEG is embedded as stored. zh-CN: JPEG 原样嵌入.
            val extracted = output("photo-out.jpg")
            writable(extracted).use { target ->
                val answer = document.extractImage(request { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_INDEX, 0) }, target)
                assertNull(answer.getString(PdfContract.KEY_ERROR_CODE))
            }
            assertArrayEquals(jpeg.readBytes(), extracted.readBytes())
        } finally {
            document.close()
        }
        // A4 with `orientation: auto` turns the page to the image; `lossless` keeps the PNG lossless.
        // zh-CN: A4 加 `orientation: auto` 时页面随图片转向; `lossless` 使 PNG 保持无损.
        val a4 = output("a4.pdf")
        plugin.generate(PdfOps.FROM_IMAGES, listOf(jpeg, png), a4) {
            putString(PdfContract.KEY_PAGE_SIZE, PdfContract.PAGE_SIZE_A4)
            putFloat(PdfContract.KEY_MARGIN, 20f)
            putString(PdfContract.KEY_QUALITY, PdfContract.QUALITY_LOSSLESS)
        }.completed()
        assertSizes(listOf(A4_HEIGHT to A4_WIDTH, A4_WIDTH to A4_HEIGHT), plugin.pageSizes(a4))
        val lossless = plugin.openFile(a4)
        try {
            assertEquals(listOf(PdfContract.FORMAT_JPEG, PdfContract.FORMAT_PNG), imageFormats(lossless))
        } finally {
            lossless.close()
        }
    }

    private fun imageFormats(document: IPdfDocument): List<String> {
        val items = JSONArray(document.planImages(request()).getString(PdfContract.KEY_ITEMS))
        return List(items.length()) { items.getJSONObject(it).getString(PdfContract.FIELD_FORMAT) }
    }

    @Test
    fun textIsLaidOutOnPages(): Unit = withPdfPlugin { plugin ->
        val text = output("lines.txt").apply {
            writeText((1..200).joinToString("\n") { "Line $it: the quick brown fox jumps over the lazy dog." })
        }
        val plain = output("text.pdf")
        val result = plugin.generate(PdfOps.FROM_TEXT, listOf(text), plain) { putString(PdfContract.KEY_TITLE, "Lines") }.completed()
        val pages = result.getInt(PdfContract.KEY_PAGE_COUNT)
        assertTrue("200 lines at 12 pt need more than 2 A4 pages, got $pages", pages >= 3)
        assertSizes(List(pages) { A4_WIDTH to A4_HEIGHT }, plugin.pageSizes(plain))
        val content = plugin.allText(plain)
        assertTrue(content.contains("Line 1:") && content.contains("Line 200:"))
        val document = plugin.openFile(plain)
        try {
            assertEquals("Lines", document.info.getString(PdfContract.KEY_TITLE))
        } finally {
            document.close()
        }
        // A larger font and the LETTER size take more pages. zh-CN: 更大的字号与 LETTER 尺寸需要更多页.
        val font = SYSTEM_FONTS.map(::File).firstOrNull { it.canRead() && it.length() < PdfContract.MAX_TEXT_INPUT_BYTES }
        assumeTrue("no readable system font", font != null)
        val styled = output("styled.pdf")
        val larger = plugin.generate(PdfOps.FROM_TEXT, listOf(text, font!!), styled) {
            putString(PdfContract.KEY_PAGE_SIZE, PdfContract.PAGE_SIZE_LETTER)
            putFloat(PdfContract.KEY_FONT_SIZE, 18f)
        }.completed().getInt(PdfContract.KEY_PAGE_COUNT)
        assertTrue("18 pt on LETTER should take more than $pages pages, got $larger", larger > pages)
        assertSizes(List(larger) { 612f to 792f }, plugin.pageSizes(styled))
        // A font input that is not a font. zh-CN: 不是字体的字体输入.
        val refused = plugin.generate(PdfOps.FROM_TEXT, listOf(text, text), output("refused.pdf"))
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, refused.failedWith())
    }

    @Test
    fun htmlPrintsThroughTheWebView(): Unit = withPdfPlugin { plugin ->
        val declared = plugin.capabilities.getStringArray(PdfCapabilityKeys.FEATURES).orEmpty().contains(PdfContract.FEATURE_GENERATE_HTML)
        val html = output("page.html").apply {
            writeText(
                "<!doctype html><html><head><meta charset=\"utf-8\"><title>Paper HTML</title></head><body>" +
                    "<h1>Heading</h1><img src=\"https://example.com/remote.png\"><p id=\"s\">script did not run</p>" +
                    "<script>document.getElementById('s').textContent = 'script ran';</script>" +
                    (1..120).joinToString("") { "<p>Paragraph $it of the HTML sample.</p>" } +
                    "</body></html>",
            )
        }
        val target = output("html.pdf")
        if (!declared) {
            val error = assertThrows(IllegalStateException::class.java) { plugin.generate(PdfOps.FROM_HTML, listOf(html), target) }
            assertEquals(PdfErrorCodes.UNSUPPORTED, PdfErrorCodes.decode(error.message)?.first)
            Log.i(SPIKE_TAG, "device=${Build.MODEL} api=${Build.VERSION.SDK_INT} generate.html=false UNSUPPORTED")
            return@withPdfPlugin
        }
        val start = SystemClock.elapsedRealtime()
        val result = plugin.generate(PdfOps.FROM_HTML, listOf(html), target) { putLong(PdfContract.KEY_TIMEOUT, 30_000) }.completed()
        val elapsed = SystemClock.elapsedRealtime() - start
        val pages = result.getInt(PdfContract.KEY_PAGE_COUNT)
        assertTrue("120 paragraphs need more than one page, got $pages", pages >= 2)
        assertEquals(listOf("1 remote resource was not loaded"), result.getStringArray(PdfContract.KEY_WARNINGS)?.toList())
        assertSizes(List(pages) { A4_WIDTH to A4_HEIGHT }, plugin.pageSizes(target))
        val content = plugin.allText(target)
        assertTrue(content.contains("script ran") && content.contains("Paragraph 120"))
        val document = plugin.openFile(target)
        try {
            assertEquals("Paper HTML", document.info.getString(PdfContract.KEY_TITLE))
        } finally {
            document.close()
        }
        // The system renderer opens the output and draws ink on the first page. zh-CN: 系统渲染器能打开输出, 第一页有笔迹.
        readable(target).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(pages, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                    bitmap.recycle()
                    assertTrue(pixels.count { Color.red(it) < 128 } > 100)
                }
            }
        }
        Log.i(SPIKE_TAG, "device=${Build.MODEL} api=${Build.VERSION.SDK_INT} generate.html=true pages=$pages size=${result.getLong(PdfContract.KEY_SIZE_BYTES)} B elapsed=$elapsed ms")
        // A timeout ends the operation with TIMEOUT. zh-CN: 超时以 TIMEOUT 结束.
        val slow = output("slow.html").apply { writeText("<html><body><script>var e = Date.now() + 5000; while (Date.now() < e) {}</script></body></html>") }
        val timedOut = plugin.generate(PdfOps.FROM_HTML, listOf(slow), output("slow.pdf")) { putLong(PdfContract.KEY_TIMEOUT, 500) }
        assertEquals(PdfErrorCodes.TIMEOUT, timedOut.failedWith())
    }

    @Test
    fun imagesOfTheSampleAreListedAndExtracted(): Unit = withPdfPlugin { plugin ->
        val document = plugin.openSample("images.pdf")
        try {
            val plan = document.planImages(request())
            assertNull(plan.getString(PdfContract.KEY_ERROR_CODE))
            val items = JSONArray(plan.getString(PdfContract.KEY_ITEMS))
            assertEquals(2, items.length())
            val described = List(items.length()) {
                val item = items.getJSONObject(it)
                listOf(item.getInt(PdfContract.FIELD_PAGE), item.getInt(PdfContract.FIELD_INDEX), item.getInt(PdfContract.FIELD_WIDTH), item.getInt(PdfContract.FIELD_HEIGHT), item.getString(PdfContract.FIELD_FORMAT))
            }
            assertEquals(listOf(listOf(1, 0, 160, 120, PdfContract.FORMAT_JPEG), listOf(1, 1, 64, 64, PdfContract.FORMAT_PNG)), described)
            // Im1 comes out as stored. zh-CN: Im1 原样取出.
            val jpeg = output("im1.jpg")
            val first = writable(jpeg).use { document.extractImage(request { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_INDEX, 0) }, it) }
            assertEquals(PdfContract.FORMAT_JPEG, first.getString(PdfContract.KEY_FORMAT))
            assertEquals(2717L, first.getLong(PdfContract.KEY_SIZE_BYTES))
            assertEquals(IM1_SHA256, MessageDigest.getInstance("SHA-256").digest(jpeg.readBytes()).joinToString("") { "%02x".format(it) })
            // Im2 is decoded with its soft mask and written as PNG. zh-CN: Im2 连同软蒙版解码后写成 PNG.
            val png = output("im2.png")
            val second = writable(png).use { document.extractImage(request { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_INDEX, 1) }, it) }
            assertEquals(PdfContract.FORMAT_PNG, second.getString(PdfContract.KEY_FORMAT))
            assertEquals(png.length(), second.getLong(PdfContract.KEY_SIZE_BYTES))
            val bitmap = checkNotNull(BitmapFactory.decodeFile(png.path))
            try {
                assertEquals(64 to 64, bitmap.width to bitmap.height)
                assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(0, Color.alpha(bitmap.getPixel(63, 63)))
                val quadrants = listOf(24 to 24, 40 to 24, 24 to 40, 40 to 40).map { (x, y) -> bitmap.getPixel(x, y) }
                listOf(intArrayOf(220, 40, 40), intArrayOf(40, 180, 60), intArrayOf(40, 80, 220), intArrayOf(240, 200, 40)).zip(quadrants).forEach { (wanted, pixel) ->
                    val got = intArrayOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                    assertTrue("expected ${wanted.toList()}, got ${got.toList()}", wanted.indices.all { abs(wanted[it] - got[it]) <= 2 } && Color.alpha(pixel) == 255)
                }
            } finally {
                bitmap.recycle()
            }
            // An index past the last image. zh-CN: 超出最后一张图片的序号.
            writable(output("none.png")).use {
                val answer = document.extractImage(request { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_INDEX, 2) }, it)
                assertEquals(PdfErrorCodes.INVALID_ARGUMENT, answer.getString(PdfContract.KEY_ERROR_CODE))
            }
        } finally {
            document.close()
        }
        val scanned = plugin.openSample("scanned.pdf")
        try {
            val items = JSONArray(scanned.planImages(request()).getString(PdfContract.KEY_ITEMS))
            assertNotNull(items.optJSONObject(0))
            assertEquals(1240 to 1754, items.getJSONObject(0).getInt(PdfContract.FIELD_WIDTH) to items.getJSONObject(0).getInt(PdfContract.FIELD_HEIGHT))
            assertEquals(PdfContract.FORMAT_PNG, items.getJSONObject(0).getString(PdfContract.FIELD_FORMAT))
        } finally {
            scanned.close()
        }
    }

    /**
     * Appendix F "20 images to PDF": 20 JPEG photos of 1600 x 1200 (embedded as stored) and 20 PNG drawings of the same
     * size (transcoded), each set converted through Binder once to warm up and then three times; logcat tag
     * `PaperImagesPerf` gives the medians.
     * zh-CN: 附录 F "20 张图片转 PDF": 20 张 1600 x 1200 的 JPEG 照片 (原样嵌入) 与 20 张同尺寸的 PNG 图 (转码), 每组经 Binder
     * 转换一次预热, 再转换三次; logcat 标签 `PaperImagesPerf` 给出中位数.
     */
    @Test
    fun timingOfTwentyImages(): Unit = withPdfPlugin { plugin ->
        val sets = listOf(
            "jpeg" to List(20) { imageFile("photo-$it.jpg", 1600, 1200, Bitmap.CompressFormat.JPEG) },
            "png" to List(20) { imageFile("drawing-$it.png", 1600, 1200, Bitmap.CompressFormat.PNG) },
        )
        val lines = sets.map { (kind, images) ->
            val times = ArrayList<Long>()
            var size = 0L
            repeat(4) { round ->
                val target = output("$kind-$round.pdf")
                val start = SystemClock.elapsedRealtime()
                val result = plugin.generate(PdfOps.FROM_IMAGES, images, target, seconds = 300).completed()
                val elapsed = SystemClock.elapsedRealtime() - start
                assertEquals(20, result.getInt(PdfContract.KEY_PAGE_COUNT))
                size = result.getLong(PdfContract.KEY_SIZE_BYTES)
                if (round > 0) times += elapsed
                target.delete()
            }
            "$kind input=${images.sumOf { it.length() }} B output=$size B median=${times.sorted()[1]} ms runs=${times.joinToString()}"
        }
        Log.i(PERF_TAG, "device=${Build.MODEL} api=${Build.VERSION.SDK_INT} 20x1600x1200 ${lines.joinToString("; ")}")
    }

    private companion object {
        const val PERF_TAG = "PaperImagesPerf"

        /** A4 is 210 x 297 mm. zh-CN: A4 为 210 x 297 毫米. */
        const val A4_WIDTH = 595.27563f
        const val A4_HEIGHT = 841.8898f
        const val SPIKE_TAG = "PaperHtmlSpike"
        const val IM1_SHA256 = "d75b3b7340d9c360f66330361779ebb3dc54222737402edfc2a37de7f53b77d8"
        val SYSTEM_FONTS = listOf("/system/fonts/Roboto-Regular.ttf", "/system/fonts/DroidSans.ttf", "/system/fonts/NotoSerif-Regular.ttf")
    }

}

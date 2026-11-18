package io.github.supermonster003.autojs6.plugin.three.paper.pdf.write

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.CanvasStamps
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.TextStamp
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * The renderer half of the object stream acceptance (roadmap P6.7, D65, spike S4): every committed sample, a document
 * drawn by the system `PdfDocument` (embedded fonts of the device) and a stamped document (imported forms of the stamp
 * pipeline) are written once with `PDDocument.save` and once with [CompactWriting] (object streams, with the
 * `PdfRendererCheck` of the device), and the system `PdfRenderer` draws every page of both outputs to the same pixels.
 * Sizes and pixel counts go to logcat under `PaperObjectStreams`; `-e paperKeep true` keeps the outputs.
 * zh-CN: 对象流验收中由渲染器完成的一半 (路线图 P6.7, D65, spike S4): 每个已提交样例, 一份由系统 `PdfDocument` 绘制的文档 (设备的
 * 嵌入字体) 与一份加过印章的文档 (印章管线导入的表单) 各用 `PDDocument.save` 与 [CompactWriting] (对象流, 使用设备上的
 * `PdfRendererCheck`) 写出一次, 系统 `PdfRenderer` 把两份输出的每一页绘制成相同的像素. 体积与像素数以 `PaperObjectStreams` 写入
 * logcat; `-e paperKeep true` 保留输出.
 */
@RunWith(AndroidJUnit4::class)
class ObjectStreamsDeviceTest {

    private val testContext: Context get() = InstrumentationRegistry.getInstrumentation().context

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "objstm-device").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    private fun asset(name: String): ByteArray = testContext.assets.open(name).use { it.readBytes() }

    private val fixtures: JSONObject by lazy { JSONObject(asset("fixtures.json").toString(Charsets.UTF_8)).getJSONObject("fixtures") }

    private class Input(val name: String, val bytes: ByteArray, val password: String? = null)

    private fun load(input: Input): PDDocument = if (input.password == null) PDDocument.load(input.bytes) else PDDocument.load(input.bytes, input.password)

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    /** Three pages drawn by the system `PdfDocument`: text in the default and a bold face, shapes and a bitmap. zh-CN: 系统 `PdfDocument` 绘制的三页. */
    private fun drawnDocument(): ByteArray {
        val document = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until 64) for (x in 0 until 64) setPixel(x, y, Color.rgb(x * 4, y * 4, 160))
            }
            for (number in 1..3) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
                val canvas = page.canvas
                paint.color = Color.BLACK
                paint.textSize = 18f
                paint.isFakeBoldText = false
                canvas.drawText("Drawn page $number of 3", 72f, 96f, paint)
                paint.textSize = 12f
                for (line in 0 until 30) canvas.drawText("Line ${line + 1}: the quick brown fox jumps over the lazy dog, 0123456789.", 72f, 130f + line * 18, paint)
                paint.isFakeBoldText = true
                canvas.drawText("Bold heading on page $number", 72f, 700f, paint)
                paint.color = Color.rgb(200, 40, 40)
                canvas.drawRect(72f, 720f, 272f, 780f, paint)
                canvas.drawBitmap(bitmap, 320f, 716f, null)
                document.finishPage(page)
            }
            bitmap.recycle()
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    /** text.pdf with a text stamp from the Canvas stamp pipeline on its page. zh-CN: 用 Canvas 印章管线加过文字印章的 text.pdf. */
    private fun stampedDocument(): ByteArray = PDDocument.load(asset("text.pdf")).use { document ->
        val stamps = listOf(TextStamp("机密 Confidential", 300.0, 420.0, anchorX = 0.5, anchorY = 0.5, fontSize = 36.0, opacity = 0.4, rotation = 30.0))
        CanvasStamps(File(directory, "stamps").apply { mkdirs() }).apply(document, listOf(CanvasStamps.PageStamps(0, stamps)))
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun inputs(): List<Input> {
        val samples = fixtures.keys().asSequence().sorted().map { name ->
            Input(name, asset(name), fixtures.getJSONObject(name).optString("ownerPassword").ifEmpty { null })
        }.toList()
        return samples + Input("drawn.pdf", drawnDocument()) + Input("stamped.pdf", stampedDocument())
    }

    private fun render(renderer: PdfRenderer, index: Int): Bitmap = renderer.openPage(index).use { page ->
        // Twice the point size, the longer side at most MAX_SIDE pixels. zh-CN: 点尺寸的两倍, 长边至多 MAX_SIDE 像素.
        val scale = min(2.0, MAX_SIDE.toDouble() / max(page.width, page.height))
        val bitmap = Bitmap.createBitmap(max(1, (page.width * scale).toInt()), max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    /** Pixels that differ between [first] and [second], which have the same size. zh-CN: 两张同尺寸位图之间不同的像素数. */
    private fun differences(first: Bitmap, second: Bitmap): Int {
        val width = first.width
        val a = IntArray(width)
        val b = IntArray(width)
        var count = 0
        for (y in 0 until first.height) {
            first.getPixels(a, 0, width, 0, y, width, 1)
            second.getPixels(b, 0, width, 0, y, width, 1)
            for (x in 0 until width) if (a[x] != b[x]) count++
        }
        return count
    }

    @Test
    fun everyPageRendersPixelIdenticalToTheClassicOutput() {
        var total = 0
        for (input in inputs()) {
            val classic = File(directory, input.name.removeSuffix(".pdf") + ".classic.pdf")
            load(input).use { document -> FileOutputStream(classic).use { PdfWriting.write(document, it, StopSignal.NEVER) } }
            val compact = File(directory, input.name.removeSuffix(".pdf") + ".objstm.pdf")
            val started = System.nanoTime()
            val result = load(input).use { document -> FileOutputStream(compact).use { CompactWriting.write(document, it, directory) } }
            val millis = (System.nanoTime() - started) / 1_000_000
            assertTrue("${input.name}: ${result.warnings}", result.objectStreams)
            assertEquals(input.name, emptyList<String>(), result.warnings)
            assertEquals(input.name, compact.length(), result.sizeBytes)
            var pixels = 0L
            var pages = 0
            ParcelFileDescriptor.open(classic, ParcelFileDescriptor.MODE_READ_ONLY).use { first ->
                ParcelFileDescriptor.open(compact, ParcelFileDescriptor.MODE_READ_ONLY).use { second ->
                    PdfRenderer(first).use { expected ->
                        PdfRenderer(second).use { actual ->
                            assertEquals(input.name, expected.pageCount, actual.pageCount)
                            for (index in 0 until expected.pageCount) {
                                val a = render(expected, index)
                                val b = render(actual, index)
                                try {
                                    assertEquals("${input.name} page ${index + 1}", a.width to a.height, b.width to b.height)
                                    assertEquals("${input.name} page ${index + 1} differing pixels", 0, differences(a, b))
                                    pixels += a.width.toLong() * a.height
                                } finally {
                                    a.recycle()
                                    b.recycle()
                                }
                            }
                            pages = expected.pageCount
                        }
                    }
                }
            }
            log(
                String.format(
                    java.util.Locale.ROOT, "%s: %d pages, classic %d B, objstm %d B (%.1f %%), %d ms with the checks, %d pixels identical",
                    input.name, pages, classic.length(), compact.length(), 100.0 * compact.length() / classic.length(), millis, pixels,
                ),
            )
            total += pages
        }
        log("all inputs: $total pages rendered pixel-identical")
    }

    companion object {

        private const val TAG = "PaperObjectStreams"

        private const val MAX_SIDE = 1600

        @BeforeClass
        @JvmStatic
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

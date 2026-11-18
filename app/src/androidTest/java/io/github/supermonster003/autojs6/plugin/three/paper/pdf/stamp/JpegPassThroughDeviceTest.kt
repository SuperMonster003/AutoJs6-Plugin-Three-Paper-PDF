package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random
import kotlin.math.abs

/**
 * [JpegPassThrough] (roadmap Q201 (a)): a JPEG photo stamped at half opacity, turned and drawn twice (a tiled copy)
 * adds about its own size to the output instead of Skia's lossless copy, through one shared stream, and the system
 * `PdfRenderer` draws it like Skia's copy (mean channel difference under 2); a PNG input keeps Skia's stream. Sizes go
 * to logcat under `PaperStampSpike`.
 * zh-CN: [JpegPassThrough] (路线图 Q201 (a)): 以一半不透明度, 旋转并画两次 (平铺副本) 的 JPEG 照片印章, 给输出增加的约为其自身体积, 而不是
 * Skia 的无损副本, 两次绘制共用一个流, 系统 `PdfRenderer` 的渲染与 Skia 副本相同 (通道平均差小于 2); PNG 输入保留 Skia 的流.
 * 体积以 `PaperStampSpike` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class JpegPassThroughDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val directory by lazy { File(context.cacheDir, "jpeg-pass").apply { mkdirs() } }

    @After
    fun removeOutputs() {
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i("PaperStampSpike", "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    /** An 800 x 600 photo-like image (gradients and noise). zh-CN: 800 x 600 的类照片图像 (渐变加噪声). */
    private fun photo(): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val random = Random(11)
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            for (x in row.indices) {
                val n = random.nextInt(40) - 20
                row[x] = Color.rgb((x * 255 / 800 + n).coerceIn(0, 255), (y * 255 / 600 + n).coerceIn(0, 255), (255 - x * 255 / 800 - n).coerceIn(0, 255))
            }
            bitmap.setPixels(row, 0, row.size, 0, y, row.size, 1)
        }
        return bitmap
    }

    private fun stamped(name: String, image: Bitmap, jpeg: JpegData?, pass: Boolean): Pair<File, CanvasStamps.Summary> {
        val target = File(directory, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.setNonStrokingColor(0.5f)
                stream.addRect(100f, 100f, 395f, 300f)
                stream.fill()
            }
            val stamp = ImageStamp(image, 297.5, 300.0, 400.0, 300.0, rotation = 30.0, opacity = 0.5, copies = listOf(297.5 to 650.0), jpeg = jpeg)
            val summary = CanvasStamps(directory, passJpegs = pass).apply(document, listOf(CanvasStamps.PageStamps(0, listOf(stamp))))
            document.save(target)
            return target to summary
        }
    }

    private fun render(file: File): Bitmap = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    /** Mean and largest channel difference. zh-CN: 通道差的平均值与最大值. */
    private fun difference(a: Bitmap, b: Bitmap): Pair<Double, Int> {
        assertEquals(a.width, b.width)
        val ra = IntArray(a.width)
        val rb = IntArray(a.width)
        var sum = 0L
        var largest = 0
        for (y in 0 until a.height) {
            a.getPixels(ra, 0, ra.size, 0, y, ra.size, 1)
            b.getPixels(rb, 0, rb.size, 0, y, rb.size, 1)
            for (i in ra.indices) {
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = abs((ra[i] shr shift and 0xFF) - (rb[i] shr shift and 0xFF))
                    sum += d
                    if (d > largest) largest = d
                }
            }
        }
        return sum.toDouble() / (a.width.toLong() * a.height * 3) to largest
    }

    @Test
    fun aJpegInputReplacesSkiasCopy() {
        val source = photo()
        val bytes = java.io.ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        source.recycle()
        val jpeg = JpegData.of(bytes)
        assertNotNull("the frame header of the system JPEG encoder is read", jpeg)
        assertEquals(800, jpeg!!.width)
        assertEquals(600, jpeg.height)
        assertEquals(3, jpeg.components)
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            val (passed, on) = stamped("passed.pdf", image, jpeg, pass = true)
            val (skia, off) = stamped("skia.pdf", image, jpeg, pass = false)
            log("jpeg pass-through: jpeg ${bytes.size} B, output ${passed.length()} B (Skia's copy ${skia.length()} B), streams replaced ${on.jpegImages}")
            assertEquals("both draws share one stream", 1, on.jpegImages)
            assertEquals(0, off.jpegImages)
            assertTrue("output ${passed.length()} B for a ${bytes.size} B jpeg", passed.length() < bytes.size + 16 * 1024)
            assertTrue("Skia's copy ${skia.length()} B is larger", skia.length() > 2L * passed.length())
            val a = render(passed)
            val b = render(skia)
            val (mean, largest) = difference(a, b)
            log("jpeg pass-through: rendering differs from Skia's copy by $mean on average, at most $largest")
            assertTrue("mean channel difference $mean", mean < 2.0)
            a.recycle()
            b.recycle()
        } finally {
            image.recycle()
        }
    }

    @Test
    fun aPngInputKeepsSkiasStream() {
        val image = photo()
        try {
            val (_, summary) = stamped("png.pdf", image, null, pass = true)
            assertEquals(0, summary.jpegImages)
        } finally {
            image.recycle()
        }
    }

    companion object {

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

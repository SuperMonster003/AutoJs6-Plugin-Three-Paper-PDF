package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import android.graphics.Bitmap
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
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sampleFile
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs

/**
 * The `run` operation `compress` through Binder (roadmap P6.6, E1 + E2): the multi-page scan sample with the default
 * numbers ends at most half its size with the same pages and page sizes and a small mean pixel difference in the system
 * `PdfRenderer`, a transparent image stays transparent, 1-bit images and images the plugin cannot decode are kept
 * byte for byte and counted as skipped, an output that is not smaller is written with a warning (D57), progress goes
 * through `verifying`, and `imageDpi` / `imageQuality` outside their ranges are `INVALID_ARGUMENT`. Sizes and times of
 * three kinds of documents (text over many pages, the scan sample, text with photos and a transparent image) go to
 * logcat under `PaperCompress` (appendix F); the platform JPEG encoder and `PdfRenderer` differ by API level.
 * zh-CN: 经 Binder 验证 `run` 操作 `compress` (路线图 P6.6, E1 + E2): 多页扫描样例按默认数值压缩后不超过原体积的一半, 页数与页面
 * 尺寸不变, 系统 `PdfRenderer` 渲染的平均像素差很小; 透明图片保持透明; 1 位图与插件解不了的图片逐字节保留并计为跳过; 输出不更小时
 * 照样写出并给警告 (D57); 进度经过 `verifying`; `imageDpi` / `imageQuality` 越界为 `INVALID_ARGUMENT`. 三类文档 (多页文字, 扫描
 * 样例, 文字加照片与透明图片) 的体积与耗时以 `PaperCompress` 写入 logcat (附录 F); 平台 JPEG 编码器与 `PdfRenderer` 随 API 级别不同.
 */
@RunWith(AndroidJUnit4::class)
class CompressOperationDeviceTest {

    private val directory = File(context.cacheDir, "compress-op")

    @After
    fun removeOutputs() {
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    /** Records the callbacks of one operation, progress included. zh-CN: 记录一个操作的回调, 包括进度. */
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

        fun await(): Recorder {
            assertTrue("the operation did not end in time", ended.await(300, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

    }

    private fun file(name: String): File = File(directory, name).also { it.parentFile?.mkdirs() }

    private fun read(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun write(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

    /**
     * Starts `compress` on [source] into [target] with the numbers the host sends for the default preset; [fill] changes
     * request keys.
     * zh-CN: 以宿主为默认预设发送的数值对 [source] 启动 `compress`, 输出到 [target]; [fill] 修改请求键.
     */
    private fun IPdfPlugin.start(source: File, target: File, recorder: Recorder = Recorder(), fill: Bundle.() -> Unit = {}): Recorder {
        read(source).use { input ->
            write(target).use { output ->
                val request = Bundle().apply {
                    putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
                    putString(PdfContract.KEY_OP, PdfOps.COMPRESS)
                    putInt(PdfContract.KEY_IMAGE_DPI, PdfContract.DEFAULT_IMAGE_DPI)
                    putInt(PdfContract.KEY_IMAGE_QUALITY, PdfContract.DEFAULT_IMAGE_QUALITY)
                    fill()
                }
                run(request, arrayOf(input), output, recorder)
            }
        }
        return recorder
    }

    private fun IPdfPlugin.compress(source: File, target: File, fill: Bundle.() -> Unit = {}): Recorder = start(source, target, fill = fill).await()

    /** The error code a refused call throws. zh-CN: 被拒绝的调用抛出的错误码. */
    private fun codeOf(block: () -> Unit): String? {
        try {
            block()
        } catch (e: Exception) {
            return PdfErrorCodes.decode(e.message)?.first
        }
        fail("the call was not refused")
        return null
    }

    private fun render(file: File, index: Int, scale: Float = 1f): Bitmap = read(file).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val matrix = android.graphics.Matrix().apply { setScale(scale, scale) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    private fun pageSizes(file: File): List<Pair<Int, Int>> = read(file).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> (0 until renderer.pageCount).map { index -> renderer.openPage(index).use { it.width to it.height } } }
    }

    /** Mean absolute difference over the RGB channels, 0 - 255. zh-CN: RGB 各通道的平均绝对差, 0 - 255. */
    private fun meanDifference(a: Bitmap, b: Bitmap): Double {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        val rowA = IntArray(a.width)
        val rowB = IntArray(a.width)
        var sum = 0L
        for (y in 0 until a.height) {
            a.getPixels(rowA, 0, a.width, 0, y, a.width, 1)
            b.getPixels(rowB, 0, b.width, 0, y, b.width, 1)
            for (x in rowA.indices) {
                sum += abs(Color.red(rowA[x]) - Color.red(rowB[x])) + abs(Color.green(rowA[x]) - Color.green(rowB[x])) + abs(Color.blue(rowA[x]) - Color.blue(rowB[x]))
            }
        }
        return sum.toDouble() / (3L * a.width * a.height)
    }

    /** The share of pixels darker than mid grey. zh-CN: 比中灰更暗的像素所占比例. */
    private fun darkShare(bitmap: Bitmap): Double {
        val row = IntArray(bitmap.width)
        var dark = 0L
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) if (Color.green(pixel) < 128) dark++
        }
        return dark.toDouble() / (bitmap.width.toLong() * bitmap.height)
    }

    private fun stages(recorder: Recorder): List<String> = recorder.progress.mapNotNull { it.getString(PdfContract.KEY_STAGE) }.distinct()

    private fun warnings(result: Bundle): List<String> = result.getStringArray(PdfContract.KEY_WARNINGS)?.filterNotNull().orEmpty()

    private fun summary(name: String, source: File, target: File, result: Bundle, millis: Long): String =
        "sample=$name pages=${result.getInt(PdfContract.KEY_PAGE_COUNT)} source=${result.getLong(PdfContract.KEY_SOURCE_BYTES)} size=${result.getLong(PdfContract.KEY_SIZE_BYTES)} " +
            "ratio=${"%.3f".format(result.getLong(PdfContract.KEY_SIZE_BYTES).toDouble() / source.length())} ms=$millis images=${result.getInt(PdfContract.KEY_IMAGES_TOTAL)} " +
            "recompressed=${result.getInt(PdfContract.KEY_IMAGES_RECOMPRESSED)} skipped=${result.getInt(PdfContract.KEY_IMAGES_SKIPPED)} objectStreams=${result.getBoolean(PdfContract.KEY_OBJECT_STREAMS)} " +
            "file=${target.length()} warnings=${warnings(result).size}"

    @Test
    fun theScanSampleShrinksToHalfWithItsPagesAndPixelsKept() = withPdfPlugin { plugin ->
        val source = sampleFile("scanned-multi.pdf")
        val target = file("scanned-multi-compressed.pdf")
        val started = SystemClock.elapsedRealtime()
        val recorder = plugin.compress(source, target)
        val millis = SystemClock.elapsedRealtime() - started
        val result = recorder.completed()
        log(summary("scanned-multi.pdf", source, target, result, millis))
        assertEquals(source.length(), result.getLong(PdfContract.KEY_SOURCE_BYTES))
        assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        assertTrue("at most half: ${target.length()} of ${source.length()}", target.length() * 2 <= source.length())
        assertEquals(3, result.getInt(PdfContract.KEY_PAGE_COUNT))
        assertEquals(3, result.getInt(PdfContract.KEY_IMAGES_TOTAL))
        assertEquals(3, result.getInt(PdfContract.KEY_IMAGES_RECOMPRESSED))
        assertEquals(0, result.getInt(PdfContract.KEY_IMAGES_SKIPPED))
        assertTrue("object streams: ${warnings(result)}", result.getBoolean(PdfContract.KEY_OBJECT_STREAMS))
        assertTrue(warnings(result).toString(), warnings(result).isEmpty())
        // Reports inside the 250 ms throttle are dropped, so later stages may not show; those seen keep the order.
        // zh-CN: 250 ms 节流间隔内的报告被丢弃, 因此后面的阶段可能看不到; 看到的阶段保持顺序.
        val order = listOf(PdfContract.STAGE_OPENING, PdfContract.STAGE_PROCESSING, PdfContract.STAGE_WRITING, PdfContract.STAGE_VERIFYING)
        val seen = stages(recorder)
        log("scanned-multi.pdf stages $seen")
        assertEquals(PdfContract.STAGE_OPENING, seen.first())
        assertEquals(seen.toString(), seen, order.filter { it in seen })
        assertEquals(pageSizes(source), pageSizes(target))
        for (index in 0 until 3) {
            for (scale in listOf(1f, 2f, 300f / 72)) {
                val before = render(source, index, scale)
                val after = render(target, index, scale)
                val difference = meanDifference(before, after)
                val dark = darkShare(before) to darkShare(after)
                log("scanned-multi.pdf page ${index + 1} at ${(72 * scale).toInt()} dpi: mean difference ${"%.3f".format(difference)}, dark share ${"%.4f".format(dark.first)} / ${"%.4f".format(dark.second)}")
                assertTrue("the scan is drawn: $dark", dark.first > 0.02 && dark.second > 0.02)
                assertTrue("page ${index + 1} at scale $scale differs by $difference on average", difference < MAX_MEAN_DIFFERENCE)
                before.recycle()
                after.recycle()
            }
        }
        PDDocument.load(target).use { document ->
            val image = document.getPage(0).resources.xObjectNames.map { document.getPage(0).resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
            log("scanned-multi.pdf page 1 image ${image.width} x ${image.height}, filter ${image.cosObject.getDictionaryObject(COSName.FILTER)}")
            assertEquals(COSName.DCT_DECODE, image.cosObject.getDictionaryObject(COSName.FILTER))
        }
    }

    @Test
    fun aTransparentImageStaysTransparent() = withPdfPlugin { plugin ->
        val source = file("transparent.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            // 800 x 800 pixels: the left half opaque red, the right half transparent; drawn 192 pt square (300 dpi).
            // zh-CN: 800 x 800 像素: 左半边不透明红色, 右半边透明; 画成 192 pt 见方 (300 dpi).
            val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            Canvas(bitmap).drawRect(0f, 0f, 400f, 800f, Paint().apply { color = Color.RED })
            val image = LosslessFactory.createFromImage(document, bitmap)
            bitmap.recycle()
            PDPageContentStream(document, page).use { stream ->
                stream.setNonStrokingColor(0.5f)
                stream.addRect(300f, 400f, 200f, 200f)
                stream.fill()
                stream.drawImage(image, 200f, 400f, 192f, 192f)
            }
            document.save(source)
        }
        val target = file("transparent-compressed.pdf")
        val result = plugin.compress(source, target).completed()
        log(summary("transparent.pdf", source, target, result, 0))
        assertEquals(1, result.getInt(PdfContract.KEY_IMAGES_RECOMPRESSED))
        PDDocument.load(target).use { document ->
            val resources = document.getPage(0).resources
            val image = resources.xObjectNames.map { resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
            assertEquals(COSName.DCT_DECODE, image.cosObject.getDictionaryObject(COSName.FILTER))
            val mask = image.cosObject.getDictionaryObject(COSName.SMASK) as COSStream
            assertEquals(image.width, mask.getInt(COSName.WIDTH))
            assertTrue("150 dpi: ${image.width}", image.width in 399..401)
        }
        val rendered = render(target, 0)
        val height = rendered.height
        val red = rendered.getPixel(248, height - 496)
        val throughMask = rendered.getPixel(344, height - 496)
        val block = rendered.getPixel(450, height - 496)
        log("transparent.pdf: red ${Integer.toHexString(red)}, under the transparent half ${Integer.toHexString(throughMask)}, block ${Integer.toHexString(block)}")
        assertTrue("opaque half is red", Color.red(red) > 200 && Color.green(red) < 60 && Color.blue(red) < 60)
        assertTrue("the grey block shows through the transparent half", abs(Color.red(throughMask) - Color.red(block)) <= 8 && Color.red(block) in 100..160)
        assertTrue("not red under the transparent half", Color.green(throughMask) > 90)
        rendered.recycle()
    }

    @Test
    fun oneBitAndUndecodableImagesAreKeptByteForByte() = withPdfPlugin { plugin ->
        val source = file("kept.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val random = Random(3)
            val bits = ByteArray(2400 / 8 * 2400).also { random.nextBytes(it) }
            val oneBit = PDImageXObject(document, ByteArrayInputStream(deflate(bits)), COSName.FLATE_DECODE, 2400, 2400, 1, PDDeviceGray.INSTANCE)
            val jbig2 = PDImageXObject(document, ByteArrayInputStream(ByteArray(64) { it.toByte() }), COSName.JBIG2_DECODE, 2400, 2400, 1, PDDeviceGray.INSTANCE)
            val jpx = PDImageXObject(document, ByteArrayInputStream(ByteArray(256) { (it * 7).toByte() }), COSName.JPX_DECODE, 2400, 2400, 8, PDDeviceRGB.INSTANCE)
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(oneBit, 72f, 600f, 144f, 144f)
                stream.drawImage(jbig2, 300f, 600f, 144f, 144f)
                stream.drawImage(jpx, 72f, 400f, 144f, 144f)
            }
            document.save(source)
        }
        val before = rawImages(source)
        val target = file("kept-compressed.pdf")
        val result = plugin.compress(source, target).completed()
        log(summary("kept.pdf", source, target, result, 0) + " ${warnings(result)}")
        assertEquals(3, result.getInt(PdfContract.KEY_IMAGES_TOTAL))
        assertEquals(0, result.getInt(PdfContract.KEY_IMAGES_RECOMPRESSED))
        assertEquals(3, result.getInt(PdfContract.KEY_IMAGES_SKIPPED))
        assertTrue(warnings(result).toString(), warnings(result).any { it.startsWith("1 image cannot be decoded") })
        val after = rawImages(target)
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (a, b) -> assertArrayEquals(a, b) }
    }

    @Test
    fun anOutputThatIsNotSmallerIsWrittenWithAWarning() = withPdfPlugin { plugin ->
        val source = file("tiny.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(200f, 200f)))
            document.save(source)
        }
        val target = file("tiny-compressed.pdf")
        val result = plugin.compress(source, target) { putBoolean(PdfContract.KEY_OBJECT_STREAMS, false) }.completed()
        log(summary("tiny.pdf", source, target, result, 0) + " ${warnings(result)}")
        assertTrue("the output gains producer and modificationDate", result.getLong(PdfContract.KEY_SIZE_BYTES) > result.getLong(PdfContract.KEY_SOURCE_BYTES))
        assertFalse(result.getBoolean(PdfContract.KEY_OBJECT_STREAMS))
        assertTrue(warnings(result).toString(), warnings(result).any { it.startsWith("the output (") && it.contains("is not smaller than the source") })
        assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        assertEquals(1, PDDocument.load(target).use { it.numberOfPages })
        // A text sample: written with object streams; the warning follows the size. zh-CN: 文字样例: 以对象流写出; 警告随体积而定.
        val text = sampleFile("outline.pdf")
        val textTarget = file("outline-compressed.pdf")
        val textResult = plugin.compress(text, textTarget).completed()
        log(summary("outline.pdf", text, textTarget, textResult, 0) + " ${warnings(textResult)}")
        val larger = textResult.getLong(PdfContract.KEY_SIZE_BYTES) >= textResult.getLong(PdfContract.KEY_SOURCE_BYTES)
        assertEquals(larger, warnings(textResult).any { it.contains("is not smaller than the source") })
    }

    @Test
    fun removeMetadataKeepsProducerAlone() = withPdfPlugin { plugin ->
        val source = file("metadata.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            document.documentInformation = PDDocumentInformation().apply {
                title = "Title"
                author = "Author"
            }
            document.save(source)
        }
        val target = file("metadata-compressed.pdf")
        plugin.compress(source, target) { putBoolean(PdfContract.KEY_REMOVE_METADATA, true) }.completed()
        PDDocument.load(target).use { document ->
            assertEquals(setOf(COSName.PRODUCER), document.documentInformation.cosObject.keySet())
        }
    }

    @Test
    fun numbersOutsideTheirRangesAreInvalid() = withPdfPlugin { plugin ->
        val source = sampleFile("text.pdf")
        for ((key, value) in listOf(
            PdfContract.KEY_IMAGE_DPI to PdfContract.MIN_IMAGE_DPI - 1,
            PdfContract.KEY_IMAGE_DPI to PdfContract.MAX_IMAGE_DPI + 1,
            PdfContract.KEY_IMAGE_QUALITY to PdfContract.MIN_QUALITY - 1,
            PdfContract.KEY_IMAGE_QUALITY to PdfContract.MAX_QUALITY + 1,
        )) {
            assertEquals("$key $value", PdfErrorCodes.INVALID_ARGUMENT, codeOf { plugin.start(source, file("invalid.pdf")) { putInt(key, value) } })
        }
    }

    @Test
    fun threeKindsOfDocumentsGoToTheLog() = withPdfPlugin { plugin ->
        val text = file("text-30.pdf")
        PDDocument().use { document ->
            repeat(30) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 10f)
                    stream.newLineAtOffset(56f, 780f)
                    repeat(48) { line ->
                        stream.showText("Page ${index + 1}, line ${line + 1}: the quick brown fox jumps over the lazy dog, again and again.")
                        stream.newLineAtOffset(0f, -15f)
                    }
                    stream.endText()
                }
            }
            document.save(text)
        }
        val mixed = file("mixed-5.pdf")
        PDDocument().use { document ->
            val photo = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
            val random = Random(5)
            val canvas = Canvas(photo)
            val paint = Paint()
            repeat(400) {
                paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                canvas.drawCircle(random.nextFloat() * 1600, random.nextFloat() * 1200, 20 + random.nextFloat() * 120, paint)
            }
            val jpeg = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
            photo.recycle()
            val badge = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
            badge.eraseColor(Color.TRANSPARENT)
            Canvas(badge).drawCircle(300f, 300f, 280f, Paint().apply { color = Color.rgb(30, 120, 200) })
            repeat(5) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                val image = JPEGFactory.createFromByteArray(document, jpeg)
                val transparent = LosslessFactory.createFromImage(document, badge)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 10f)
                    stream.newLineAtOffset(56f, 780f)
                    repeat(12) { line ->
                        stream.showText("Mixed page ${index + 1}, line ${line + 1}: text above a photo of 400 dpi and a transparent badge.")
                        stream.newLineAtOffset(0f, -15f)
                    }
                    stream.endText()
                    stream.drawImage(image, 56f, 300f, 288f, 216f)
                    stream.drawImage(transparent, 380f, 330f, 144f, 144f)
                }
            }
            badge.recycle()
            document.save(mixed)
        }
        for ((name, source) in listOf("text-30.pdf" to text, "scanned-multi.pdf" to sampleFile("scanned-multi.pdf"), "mixed-5.pdf" to mixed)) {
            plugin.compress(source, file("warm-$name")).completed()
            val target = file("compressed-$name")
            val started = SystemClock.elapsedRealtime()
            val result = plugin.compress(source, target).completed()
            val millis = SystemClock.elapsedRealtime() - started
            log(summary(name, source, target, result, millis) + " ${warnings(result)}")
            assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
            if (name == "mixed-5.pdf") {
                assertEquals("one photo and one badge per page", 10, result.getInt(PdfContract.KEY_IMAGES_TOTAL))
                assertEquals(10, result.getInt(PdfContract.KEY_IMAGES_RECOMPRESSED))
                for (scale in listOf(1f, 2f)) {
                    val before = render(source, 0, scale)
                    val after = render(target, 0, scale)
                    val difference = meanDifference(before, after)
                    log("mixed-5.pdf page 1 at ${(72 * scale).toInt()} dpi: mean difference ${"%.3f".format(difference)}")
                    assertTrue("mixed page 1 at scale $scale differs by $difference on average", difference < MAX_MEAN_DIFFERENCE)
                    before.recycle()
                    after.recycle()
                }
            }
        }
    }

    /** The raw bytes of every image XObject of page 1, by resource name. zh-CN: 第 1 页每个图片 XObject 的原始字节, 按资源名. */
    private fun rawImages(file: File): List<ByteArray> = PDDocument.load(file).use { document ->
        val resources = document.getPage(0).resources
        resources.xObjectNames.sortedBy { it.name }.mapNotNull { name ->
            (resources.getXObject(name) as? PDImageXObject)?.cosObject?.createRawInputStream()?.use { it.readBytes() }
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(bytes) }
    }.toByteArray()

    companion object {

        private const val TAG = "PaperCompress"

        /** Mean RGB difference allowed between a page and its compressed copy. zh-CN: 页面与压缩副本之间允许的平均 RGB 差. */
        private const val MAX_MEAN_DIFFERENCE = 6.0

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

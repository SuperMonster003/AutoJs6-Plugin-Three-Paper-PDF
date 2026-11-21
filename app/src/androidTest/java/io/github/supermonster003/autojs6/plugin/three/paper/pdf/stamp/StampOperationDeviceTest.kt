package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sampleFile
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The `run` operation `stamp` through Binder (roadmap P6.3, E1 + E2): page numbers on 100 pages are extracted page by
 * page with their number and the total, the opacity, rotation and tiling of a text watermark show in pixel statistics
 * of the system `PdfRenderer`, the transparent area of a PNG watermark leaves the content visible, rotated pages and
 * pages of different sizes take the named positions as displayed, progress counts pages, a cancelled run leaves no
 * output, and the request and payload checks give `LIMIT_EXCEEDED` (roadmap Q185 (a)), `INVALID_ARGUMENT`,
 * `PAGE_OUT_OF_RANGE` and `PERMISSION_DENIED` (Q30); `pageOffset` shifts `{page}` only (Q211 (a)). The time of a text watermark on 100 A4 pages goes to logcat under
 * `PaperStampSpike` (appendix F).
 * zh-CN: 经 Binder 验证 `run` 操作 `stamp` (路线图 P6.3, E1 + E2): 100 页文档加页码后逐页提取含各自页码与总页数, 文字水印的不透明度,
 * 旋转与平铺用系统 `PdfRenderer` 渲染的像素统计断言, PNG 水印的透明区域不遮挡内容, 旋转页与尺寸不一的页面按显示方向取命名位置,
 * 进度按页计数, 取消后不留输出, 请求与 payload 的检查给出 `LIMIT_EXCEEDED` (路线图 Q185 (a)), `INVALID_ARGUMENT`, `PAGE_OUT_OF_RANGE`
 * 与 `PERMISSION_DENIED` (Q30); `pageOffset` 只改变 `{page}` (Q211 (a)). 100 页 A4 加文字水印的耗时以 `PaperStampSpike` 写入 logcat (附录 F).
 */
@RunWith(AndroidJUnit4::class)
class StampOperationDeviceTest {

    private val directory = File(context.cacheDir, "stamp-op")

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
            assertTrue("the operation did not end in time", ended.await(120, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

        fun failedWith(): String? = error?.getString(PdfContract.KEY_ERROR_CODE)

    }

    private fun file(name: String): File = File(directory, name).also { it.parentFile?.mkdirs() }

    private fun read(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun write(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

    /**
     * Starts `stamp` on [source] with [payload] and [images] into [target]; [fill] adds request keys. `imageCount` is
     * written unless [fill] sets it.
     * zh-CN: 以 [payload] 与 [images] 对 [source] 启动 `stamp`, 输出到 [target]; [fill] 补充请求键. 除非 [fill] 设置, 否则写入 `imageCount`.
     */
    private fun IPdfPlugin.start(
        source: File,
        payload: String,
        target: File,
        images: List<File> = emptyList(),
        font: File? = null,
        recorder: Recorder = Recorder(),
        fill: Bundle.() -> Unit = {},
    ): Pair<Int, Recorder> {
        val payloadFile = file("payload-${SystemClock.elapsedRealtimeNanos()}.json").apply { writeText(payload) }
        val inputs = (listOf(source, payloadFile) + images + listOfNotNull(font)).map(::read)
        try {
            write(target).use { output ->
                val request = Bundle().apply {
                    putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
                    putString(PdfContract.KEY_OP, PdfOps.STAMP)
                    putInt(PdfContract.KEY_IMAGE_COUNT, images.size)
                    if (font != null) putBoolean(PdfContract.KEY_HAS_FONT, true)
                    fill()
                }
                return run(request, inputs.toTypedArray(), output, recorder) to recorder
            }
        } finally {
            inputs.forEach { it.close() }
        }
    }

    private fun IPdfPlugin.stamp(source: File, payload: String, target: File, images: List<File> = emptyList(), fill: Bundle.() -> Unit = {}): Recorder =
        start(source, payload, target, images, fill = fill).second.await()

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

    /** A document of [count] A4 pages with a grey block on each (display 300 - 500 x 600 - 700). zh-CN: [count] 页 A4 文档, 每页一个灰块. */
    private fun blank(name: String, count: Int, media: PDRectangle = PDRectangle.A4): File {
        val target = file(name)
        PDDocument().use { document ->
            repeat(count) {
                val page = PDPage(media)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.setNonStrokingColor(0.5f)
                    stream.addRect(300f, media.height - 700f, 200f, 100f)
                    stream.fill()
                }
            }
            document.save(target)
        }
        return target
    }

    private fun text(file: File, number: Int): String = PDDocument.load(file).use { document ->
        PDFTextStripper().apply {
            sortByPosition = true
            startPage = number
            endPage = number
        }.getText(document)
    }

    /**
     * The text of page [number] in content stream order, whitespace removed: PDFTextStripper splits text that is not
     * axis-aligned (a watermark at 45 degrees) into one character per line, in reverse when sorted by position.
     * zh-CN: 第 [number] 页按内容流顺序的文本, 去掉空白: PDFTextStripper 把不沿坐标轴的文字 (45 度水印) 拆成每行一个字符, 按位置排序时还会倒序.
     */
    private fun inStreamOrder(file: File, number: Int): String = PDDocument.load(file).use { document ->
        PDFTextStripper().apply {
            startPage = number
            endPage = number
        }.getText(document).filterNot { it.isWhitespace() }
    }

    private fun render(file: File, index: Int): Bitmap = read(file).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    /** `[left, top, right, bottom]` (pixels, right / bottom exclusive) of the pixels matching [match]. zh-CN: 匹配像素的外接框. */
    private fun bounds(bitmap: Bitmap, left: Int = 0, top: Int = 0, right: Int = bitmap.width, bottom: Int = bitmap.height, match: (Int) -> Boolean): IntArray? {
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = -1
        var b = -1
        val row = IntArray(right - left)
        for (y in top until bottom) {
            bitmap.getPixels(row, 0, row.size, left, y, row.size, 1)
            for (i in row.indices) {
                if (!match(row[i])) continue
                val x = left + i
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        return if (r < 0) null else intArrayOf(l, t, r + 1, b + 1)
    }

    private fun count(bitmap: Bitmap, match: (Int) -> Boolean): Int {
        val row = IntArray(bitmap.width)
        var n = 0
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (pixel in row) if (match(pixel)) n++
        }
        return n
    }

    /** Tinted by the red stamp: red channel high, green and blue lower. zh-CN: 被红色印章着色: 红色通道高, 绿与蓝较低. */
    private fun isRedTint(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) < 235 && Color.green(pixel) - Color.blue(pixel) in -12..12

    private fun isRed(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) < 80 && Color.blue(pixel) < 80

    private fun isBlue(pixel: Int) = Color.blue(pixel) > 200 && Color.red(pixel) < 80 && Color.green(pixel) < 80

    @Test
    fun pageNumbersOnAHundredPages(): Unit = withPdfPlugin { plugin ->
        val source = blank("hundred.pdf", 100)
        val target = file("hundred-numbers.pdf")
        val started = SystemClock.elapsedRealtime()
        val recorder = plugin.stamp(source, """{ "items": [ { "type": "text", "text": "第 {page} 页, 共 {total} 页", "position": "bottom-center", "fontSize": 10 } ] }""", target)
        val result = recorder.completed()
        log("page numbers on 100 pages: ${SystemClock.elapsedRealtime() - started} ms, ${source.length()} -> ${target.length()} B")
        assertEquals(100, result.getInt(PdfContract.KEY_PAGE_COUNT))
        assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        PDDocument.load(target).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (number in 1..100) {
                stripper.startPage = number
                stripper.endPage = number
                val extracted = stripper.getText(document)
                assertTrue("page $number: '$extracted'", extracted.contains("第 $number 页, 共 100 页"))
            }
        }
        // Reports inside the 250 ms throttle are dropped: a warmed-up fast device (XQ-DQ72 in a full run, 2026-11-22) places
        // the 100 pages before a processing report may pass, so the stages seen are checked for their order only, as in
        // CompressOperationDeviceTest. zh-CN: 250 ms 节流间隔内的报告被丢弃: 预热后的快设备 (XQ-DQ72 全量运行, 2026-11-22) 在处理阶段的报告
        // 能通过之前就放完 100 页, 因此与 CompressOperationDeviceTest 一样只核对看到的阶段的顺序.
        val processing = recorder.progress.filter { it.getString(PdfContract.KEY_STAGE) == PdfContract.STAGE_PROCESSING }
        assertTrue("progress counts the 100 pages", processing.all { it.getInt(PdfContract.KEY_TOTAL) == 100 })
        val stages = recorder.progress.map { it.getString(PdfContract.KEY_STAGE) }.distinct()
        val order = listOf(PdfContract.STAGE_OPENING, PdfContract.STAGE_PROCESSING, PdfContract.STAGE_WRITING)
        assertEquals(PdfContract.STAGE_OPENING, stages.firstOrNull())
        assertEquals(stages.toString(), stages, order.filter { it in stages })
    }

    /**
     * Roadmap Q211 (a): `pageOffset` shifts what `{page}` shows (from page 3 on, -2 numbers it as 1), `{total}` stays the
     * page count, a sum below 1 is shown as it is, and an absent field or 0 extracts as before.
     * zh-CN: 路线图 Q211 (a): `pageOffset` 改变 `{page}` 显示的页码 (从第 3 页起, -2 使其编号为 1), `{total}` 仍是总页数, 小于 1 的和照样显示,
     * 缺省或 0 时提取结果与以前相同.
     */
    @Test
    fun aPageOffsetShiftsThePageNumber(): Unit = withPdfPlugin { plugin ->
        val source = blank("offset.pdf", 5)
        fun stamped(name: String, offset: String): File {
            val target = file(name)
            val item = """{ "type": "text", "text": "P{page}/{total}", "pages": "3-", "position": "bottom-center", "fontSize": 10$offset }"""
            plugin.stamp(source, """{ "items": [ $item ] }""", target).completed()
            return target
        }
        val shifted = stamped("offset-shifted.pdf", """, "pageOffset": -2""")
        for ((number, shown) in listOf(3 to 1, 4 to 2, 5 to 3)) {
            val extracted = text(shifted, number)
            assertTrue("page $number: '$extracted'", extracted.contains("P$shown/5"))
        }
        for (number in 1..2) assertTrue("page $number has no stamp", !text(shifted, number).contains("P"))
        assertTrue("a sum below 1 is shown", text(stamped("offset-negative.pdf", """, "pageOffset": -4"""), 3).contains("P-1/5"))
        val absent = stamped("offset-absent.pdf", "")
        val zero = stamped("offset-zero.pdf", """, "pageOffset": 0""")
        for (number in 1..5) {
            val before = text(absent, number)
            assertEquals("page $number with pageOffset 0", before, text(zero, number))
            if (number >= 3) assertTrue("page $number without pageOffset: '$before'", before.contains("P$number/5"))
        }
        val limit = PdfContract.MAX_PAGE_OFFSET + 1
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """{ "items": [ { "type": "text", "text": "{page}", "pageOffset": $limit } ] }""", file("offset-limit.pdf")).failedWith())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """{ "items": [ { "type": "text", "text": "{page}", "pageOffset": 1.5 } ] }""", file("offset-fraction.pdf")).failedWith())
    }

    @Test
    fun textWatermarkOpacityRotationAndTiling(): Unit = withPdfPlugin { plugin ->
        val source = blank("three.pdf", 3)
        val target = file("three-watermarks.pdf")
        val common = """ "type": "text", "text": "CONFIDENTIAL", "color": "#FF0000" """
        plugin.stamp(
            source,
            """{ "items": [
              { $common, "pages": "1", "rotation": 45, "opacity": 0.3, "fontSize": 40 },
              { $common, "pages": "2", "fontSize": 40 },
              { $common, "pages": "3", "rotation": 30, "tile": true, "fontSize": 20 }
            ] }""",
            target,
        ).completed()
        assertTrue(inStreamOrder(target, 1).contains("CONFIDENTIAL"))
        val turned = render(target, 0)
        val flat = render(target, 1)
        val tiled = render(target, 2)
        // Opacity 0.3 of red over white: green and blue near 255 * 0.7 = 178. zh-CN: 白底上 0.3 不透明度的红色: 绿与蓝约为 178.
        var palest = 255
        val row = IntArray(flat.width)
        for (y in 0 until turned.height) {
            turned.getPixels(row, 0, row.size, 0, y, row.size, 1)
            for (pixel in row) if (Color.red(pixel) > 240 && Color.green(pixel) < 250 && Color.green(pixel) - Color.blue(pixel) in -8..8) palest = minOf(palest, Color.green(pixel))
        }
        log("watermark opacity 0.3: darkest green $palest (expected about 178)")
        assertTrue("opacity 0.3 gives a darkest green of $palest", palest in 160..200)
        val solid = count(flat, ::isRed)
        assertTrue("the opaque watermark has solid red pixels ($solid)", solid > 500)
        assertEquals("the faint watermark has no solid red pixel", 0, count(turned, ::isRed))
        val flatBox = bounds(flat, match = ::isRedTint)!!
        val turnedBox = bounds(turned, match = ::isRedTint)!!
        val flatRatio = (flatBox[2] - flatBox[0]).toDouble() / (flatBox[3] - flatBox[1])
        val turnedRatio = (turnedBox[2] - turnedBox[0]).toDouble() / (turnedBox[3] - turnedBox[1])
        log("watermark bounds: flat ${flatBox.joinToString()} (w / h $flatRatio), 45 degrees ${turnedBox.joinToString()} (w / h $turnedRatio)")
        assertTrue("unrotated text is wide: $flatRatio", flatRatio > 4)
        assertTrue("text turned by 45 degrees is about square: $turnedRatio", turnedRatio in 0.7..1.4)
        // Both centred on the displayed page (595 x 842). zh-CN: 两者都在显示页面 (595 x 842) 居中.
        for (box in listOf(flatBox, turnedBox)) {
            assertTrue("centre x of ${box.joinToString()}", kotlin.math.abs((box[0] + box[2]) / 2.0 - 297.5) < 6)
            assertTrue("centre y of ${box.joinToString()}", kotlin.math.abs((box[1] + box[3]) / 2.0 - 421.0) < 6)
        }
        // Tiles reach every corner region of the page. zh-CN: 平铺覆盖页面的每个角区域.
        val w = tiled.width
        val h = tiled.height
        val corners = listOf(intArrayOf(0, 0, w / 4, h / 4), intArrayOf(3 * w / 4, 0, w, h / 4), intArrayOf(0, 3 * h / 4, w / 4, h), intArrayOf(3 * w / 4, 3 * h / 4, w, h))
        for (corner in corners) {
            assertTrue("no tile in ${corner.joinToString()}", bounds(tiled, corner[0], corner[1], corner[2], corner[3], ::isRedTint) != null)
        }
        log("tiled watermark: ${count(tiled, ::isRedTint)} tinted pixels")
        listOf(turned, flat, tiled).forEach { it.recycle() }
    }

    /** A 200 x 200 PNG, transparent but for an opaque blue 50 x 50 square in the middle. zh-CN: 200 x 200 的 PNG, 除中间不透明的 50 x 50 蓝块外全透明. */
    private fun png(name: String): File {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        Canvas(bitmap).drawRect(75f, 75f, 125f, 125f, Paint().apply { color = Color.BLUE })
        val target = file(name)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return target
    }

    @Test
    fun aPngWatermarkKeepsItsTransparency(): Unit = withPdfPlugin { plugin ->
        val source = blank("one.pdf", 1)
        val target = file("one-png.pdf")
        val image = png("mark.png")
        // The PNG box covers display 300 - 500 x 550 - 750, over the grey block (300 - 500 x 600 - 700).
        // zh-CN: PNG 方框覆盖显示坐标 300 - 500 x 550 - 750, 盖在灰块 (300 - 500 x 600 - 700) 之上.
        val started = SystemClock.elapsedRealtime()
        val result = plugin.stamp(source, """{ "items": [ { "type": "image", "image": 0, "position": [300, 550] } ] }""", target, listOf(image)).completed()
        log("png watermark: ${SystemClock.elapsedRealtime() - started} ms, ${source.length()} -> ${result.getLong(PdfContract.KEY_SIZE_BYTES)} B")
        val bitmap = render(target, 0)
        val blue = bounds(bitmap, match = ::isBlue)
        log("png watermark: blue at ${blue?.joinToString()}")
        assertTrue("the opaque part is drawn", blue != null)
        // 72 dpi: the 50 x 50 square sits at 375 - 425 x 625 - 675. zh-CN: 72 dpi: 50 x 50 的方块位于 375 - 425 x 625 - 675.
        val expected = intArrayOf(375, 625, 425, 675)
        assertTrue("blue at ${blue!!.joinToString()}, expected ${expected.joinToString()}", blue.indices.all { kotlin.math.abs(blue[it] - expected[it]) <= 2 })
        // The grey block still shows around the square. zh-CN: 方块周围仍能看到灰块.
        val grey = bitmap.getPixel(320, 620)
        assertTrue("the transparent area covers the content: ${Integer.toHexString(grey)}", Color.red(grey) in 110..145 && Color.blue(grey) in 110..145)
        bitmap.recycle()
    }

    @Test
    fun rotatedPagesAndPagesOfOtherSizesTakeTheNamedPositionsAsDisplayed(): Unit = withPdfPlugin { plugin ->
        val source = sampleFile("rotated.pdf")
        val target = file("rotated-stamped.pdf")
        plugin.stamp(
            source,
            """{ "items": [
              { "type": "text", "text": "TL {page}", "position": "top-left", "fontSize": 20, "color": "#FF0000", "margin": 20 },
              { "type": "text", "text": "BR {page}", "position": "bottom-right", "fontSize": 20, "color": "#0000FF", "margin": 20 }
            ] }""",
            target,
        ).completed()
        val pages = PDDocument.load(target).use { it.numberOfPages }
        for (index in 0 until pages) {
            val extracted = text(target, index + 1)
            assertTrue("page ${index + 1}: '$extracted'", extracted.contains("TL ${index + 1}") && extracted.contains("BR ${index + 1}"))
            val bitmap = render(target, index)
            val red = bounds(bitmap, match = ::isRed)
            val blue = bounds(bitmap, match = ::isBlue)
            log("rotated page ${index + 1} (${bitmap.width} x ${bitmap.height}): TL at ${red?.joinToString()}, BR at ${blue?.joinToString()}")
            assertTrue("page ${index + 1}: TL at ${red?.joinToString()}", red != null && red[0] in 18..30 && red[1] in 18..30)
            assertTrue("page ${index + 1}: BR at ${blue?.joinToString()}", blue != null && bitmap.width - blue[2] in 18..30 && bitmap.height - blue[3] in 14..30)
            bitmap.recycle()
        }
    }

    /**
     * A 1600 x 1200 photo-like JPEG (gradients and noise, quality 85) as an image stamp adds about its own size to the
     * output: the JPEG replaces Skia's lossless copy (roadmap Q201 (a), about 9.5 times the JPEG on API 33 before).
     * zh-CN: 1600 x 1200 的类照片 JPEG (渐变加噪声, 质量 85) 作图片印章给输出增加的约为其自身体积: JPEG 取代 Skia 的无损副本 (路线图
     * Q201 (a), 之前在 API 33 上约为 JPEG 的 9.5 倍).
     */
    @Test
    fun aJpegPhotoStamp(): Unit = withPdfPlugin { plugin ->
        val source = blank("photo.pdf", 1)
        val target = file("photo-stamped.pdf")
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(7)
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            for (x in row.indices) {
                val n = random.nextInt(48) - 24
                row[x] = Color.rgb((x * 255 / 1600 + n).coerceIn(0, 255), (y * 255 / 1200 + n).coerceIn(0, 255), ((x + y) * 255 / 2800 - n).coerceIn(0, 255))
            }
            bitmap.setPixels(row, 0, row.size, 0, y, row.size, 1)
        }
        val image = file("photo.jpg")
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        val started = SystemClock.elapsedRealtime()
        val result = plugin.stamp(source, """{ "items": [ { "type": "image", "image": 0, "opacity": 0.5 } ] }""", target, listOf(image)).completed()
        val grown = result.getLong(PdfContract.KEY_SIZE_BYTES) - source.length()
        log("jpeg photo stamp: ${SystemClock.elapsedRealtime() - started} ms, jpeg ${image.length()} B, source ${source.length()} -> ${result.getLong(PdfContract.KEY_SIZE_BYTES)} B (+$grown B, ${"%.2f".format(grown.toDouble() / image.length())} x the jpeg)")
        assertTrue("the output grows by $grown B for a ${image.length()} B jpeg (Q201 (a))", grown < image.length() + 64 * 1024)
        val rendered = render(target, 0)
        assertTrue("the photo is drawn", count(rendered) { Color.red(it) != Color.green(it) } > 10_000)
        rendered.recycle()
    }

    @Test
    fun aHundredA4PagesWithATextWatermark(): Unit = withPdfPlugin { plugin ->
        val source = blank("hundred-watermark.pdf", 100)
        val target = file("hundred-watermarked.pdf")
        val payload = """{ "items": [ { "type": "text", "text": "CONFIDENTIAL 机密", "rotation": 45, "opacity": 0.3, "color": "#808080" } ] }"""
        // One warm-up run, then the measured one. zh-CN: 先预热一次, 再计时.
        plugin.stamp(source, payload, target).completed()
        val started = SystemClock.elapsedRealtime()
        val result = plugin.stamp(source, payload, target).completed()
        val millis = SystemClock.elapsedRealtime() - started
        log("text watermark on 100 A4 pages: $millis ms, ${source.length()} -> ${result.getLong(PdfContract.KEY_SIZE_BYTES)} B")
        val extracted = inStreamOrder(target, 100)
        log("text watermark on 100 A4 pages: page 100 extracts '$extracted'")
        assertTrue(extracted, extracted.contains("CONFIDENTIAL机密"))
    }

    @Test
    fun aCancelledRunLeavesNoOutput(): Unit = withPdfPlugin { plugin ->
        val source = blank("cancel.pdf", 100)
        val target = file("cancelled.pdf")
        val (id, recorder) = plugin.start(source, """{ "items": [ { "type": "text", "text": "{page}", "tile": true, "fontSize": 12, "gapX": 6, "gapY": 6 } ] }""", target)
        plugin.cancel(id)
        recorder.await()
        // The run may end before the cancel arrives on a fast device. zh-CN: 在快的设备上, 操作可能在取消到达之前结束.
        if (recorder.result == null) {
            assertEquals(PdfErrorCodes.CANCELLED, recorder.failedWith())
            assertEquals("a cancelled run leaves no output", 0L, target.length())
        } else {
            log("cancel: the run ended before the cancel")
        }
    }

    @Test
    fun requestAndPayloadChecks(): Unit = withPdfPlugin { plugin ->
        val source = blank("checks.pdf", 1)
        val target = file("checks-out.pdf")
        val text = """{ "type": "text", "text": "x" }"""
        val many = (0..PdfContract.MAX_STAMP_ITEMS).joinToString(",") { text }
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, plugin.stamp(source, """{ "items": [ $many ] }""", target).failedWith())
        assertEquals(0L, target.length())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """{ "items": [] }""", target).failedWith())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """{ "items": [ { "type": "text", "text": "x", "position": "middle" } ] }""", target).failedWith())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """{ "items": [ { "type": "image", "image": 0 } ] }""", target).failedWith())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """[ $text ]""", target).failedWith())
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, plugin.stamp(source, """{ "items": [ { "type": "text", "text": "x", "pages": "2" } ] }""", target).failedWith())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.stamp(source, """{ "items": [ { "type": "image", "image": 0 } ] }""", target, listOf(file("not-an-image.png").apply { writeText("text") })).failedWith())
        val tooMany = codeOf { plugin.stamp(source, """{ "items": [ $text ] }""", target) { putInt(PdfContract.KEY_IMAGE_COUNT, PdfContract.MAX_STAMP_IMAGES + 1) } }
        assertEquals("more than MAX_STAMP_IMAGES images (Q185 (a))", PdfErrorCodes.LIMIT_EXCEEDED, tooMany)
        assertEquals("inputs unlike 2 + imageCount", PdfErrorCodes.INVALID_ARGUMENT, codeOf { plugin.stamp(source, """{ "items": [ $text ] }""", target) { putInt(PdfContract.KEY_IMAGE_COUNT, 1) } })
        // Q30: the owner password writes an unencrypted copy, the user password alone is refused.
        // zh-CN: Q30: 所有者密码写出不加密的副本, 只有用户密码时被拒绝.
        val encrypted = sampleFile("encrypted.pdf")
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, plugin.stamp(encrypted, """{ "items": [ $text ] }""", target) { putString(PdfContract.KEY_PASSWORD, "paper-user") }.failedWith())
        val owner = plugin.stamp(encrypted, """{ "items": [ { "type": "text", "text": "{name}" } ] }""", target) {
            putString(PdfContract.KEY_PASSWORD, "paper-owner")
            putString(PdfContract.KEY_DISPLAY_NAME, "Secret Report.PDF")
        }.completed()
        assertTrue("page count ${owner.getInt(PdfContract.KEY_PAGE_COUNT)}", owner.getInt(PdfContract.KEY_PAGE_COUNT) > 0)
        PDDocument.load(target).use { assertTrue("the output is unencrypted", !it.isEncrypted) }
        assertTrue(text(target, 1).contains("Secret Report"))
    }

    companion object {

        private const val TAG = "PaperStampSpike"

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

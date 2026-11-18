package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Spike S2 of roadmap P6.2 (D60) on a device: text stamps with CJK and emoji drawn by the system `PdfDocument` and
 * imported by `LayerUtility` are extractable by PDFBox, visible in the rendering of the system `PdfRenderer`, and land
 * at the same display position (within 1 pt) on pages rotated by 0, 90, 180 and 270 degrees and on pages whose crop
 * box is offset; opacity, rotation, the lower layer and a font input behave. Numbers go to logcat under
 * `PaperStampSpike`.
 * zh-CN: 路线图 P6.2 (D60) 的 spike S2 在设备上的验证: 由系统 `PdfDocument` 绘制, 经 `LayerUtility` 导入的含中日韩文字与表情的
 * 文字印章可被 PDFBox 提取, 在系统 `PdfRenderer` 的渲染中可见, 并且在旋转 0, 90, 180, 270 度的页面与裁剪框有偏移的页面上落在
 * 相同的显示位置 (误差不超过 1 pt); 不透明度, 旋转, 下层与字体输入都按预期工作. 数值以 `PaperStampSpike` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class CanvasStampsDeviceTest {

    /** A filled rectangle in display points, the anchor probe of the spike. zh-CN: 以显示坐标给出的实心矩形, spike 的锚点探针. */
    private data class RectStamp(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
        val color: Int,
        override val layer: StampLayer = StampLayer.OVER,
    ) : Stamp {
        override fun draw(canvas: Canvas, fonts: StampFonts) {
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), Paint().apply { color = this@RectStamp.color })
        }
    }

    /** A test page: media box, optional crop box, rotation. zh-CN: 测试页: 媒体框, 可选的裁剪框, 旋转. */
    private data class PageSpec(val media: PDRectangle, val crop: PDRectangle?, val rotation: Int, val label: String)

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File

    private val pages = listOf(
        PageSpec(PDRectangle(0f, 0f, 595f, 842f), null, 0, "rotate 0"),
        PageSpec(PDRectangle(0f, 0f, 842f, 595f), null, 90, "rotate 90"),
        PageSpec(PDRectangle(0f, 0f, 595f, 842f), null, 180, "rotate 180"),
        PageSpec(PDRectangle(0f, 0f, 842f, 595f), null, 270, "rotate 270"),
        PageSpec(PDRectangle(0f, 0f, 700f, 950f), PDRectangle(40f, 50f, 595f, 842f), 0, "crop offset"),
        PageSpec(PDRectangle(0f, 0f, 950f, 700f), PDRectangle(50f, 40f, 842f, 595f), 90, "crop offset, rotate 90"),
        PageSpec(PDRectangle(0f, 0f, 950f, 700f), PDRectangle(50f, 40f, 842f, 595f), 270, "crop offset, rotate 270"),
    )

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "stamp-spike").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        // `-e paperKeep true` keeps the outputs for a look with run-as. zh-CN: `-e paperKeep true` 保留输出, 供 run-as 查看.
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    /** The target document: the pages of [pages], each with a grey block (display 300 - 500 x 600 - 700). zh-CN: 目标文档. */
    private fun target(): PDDocument {
        val document = PDDocument()
        for (spec in pages) {
            val page = PDPage(spec.media)
            spec.crop?.let { page.cropBox = it }
            page.rotation = spec.rotation
            document.addPage(page)
            // The block is placed in display coordinates through the same frame the stamps use.
            // zh-CN: 灰块通过印章所用的同一页框按显示坐标放置.
            val frame = StampFrame.of(page)
            val corners = listOf(frame.toUser(300.0, 600.0), frame.toUser(500.0, 700.0))
            val left = minOf(corners[0].first, corners[1].first).toFloat()
            val bottom = minOf(corners[0].second, corners[1].second).toFloat()
            val width = abs(corners[0].first - corners[1].first).toFloat()
            val height = abs(corners[0].second - corners[1].second).toFloat()
            PDPageContentStream(document, page).use {
                it.setNonStrokingColor(0.5f)
                it.addRect(left, bottom, width, height)
                it.fill()
            }
        }
        return document
    }

    private fun render(file: File, index: Int, scale: Int): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(index).use { page ->
                    // Integer page sizes here, so the fit of a null transform is exact. zh-CN: 这里的页面尺寸为整数, 空变换的铺满是精确的.
                    val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    /** The bounding box `[left, top, right, bottom]` (pixels, right / bottom exclusive) of pixels matching [match]. zh-CN: 外接框. */
    private fun bounds(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int, match: (Int) -> Boolean): IntArray? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        val row = IntArray(right - left)
        for (y in top until bottom) {
            bitmap.getPixels(row, 0, row.size, left, y, row.size, 1)
            for (x in row.indices) {
                if (!match(row[x])) continue
                minX = minOf(minX, left + x)
                maxX = maxOf(maxX, left + x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
        }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX + 1, maxY + 1)
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) < 80 && Color.blue(pixel) < 80

    private fun isBlue(pixel: Int) = Color.blue(pixel) > 200 && Color.red(pixel) < 80 && Color.green(pixel) < 80

    private fun isInk(pixel: Int) = Color.red(pixel) < 160 && Color.green(pixel) < 160 && Color.blue(pixel) < 160

    private fun luminance(pixel: Int) = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000

    private fun text(document: PDDocument, number: Int): String = PDFTextStripper().apply {
        sortByPosition = true
        startPage = number
        endPage = number
    }.getText(document)

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    @Test
    fun textStampsAreExtractableVisibleAndAnchoredOnEveryRotation() {
        val stamps = listOf(
            RectStamp(100.0, 150.0, 140.0, 170.0, Color.RED),
            TextStamp(STAMP_TEXT, 60.0, 400.0, fontSize = 24.0),
            // Lower layer: one probe under the grey block (hidden), one beside it (visible).
            // zh-CN: 下层: 一个探针在灰块之下 (被遮住), 一个在灰块旁 (可见).
            RectStamp(380.0, 640.0, 420.0, 660.0, Color.BLUE, StampLayer.UNDER),
            RectStamp(520.0, 640.0, 560.0, 660.0, Color.BLUE, StampLayer.UNDER),
        )
        val output = File(directory, "stamped.pdf")
        val started = SystemClock.elapsedRealtime()
        val summary = target().use { document ->
            val summary = CanvasStamps(directory).apply(document, pages.indices.map { CanvasStamps.PageStamps(it, stamps) })
            document.save(output)
            summary
        }
        val bitmaps = EmojiBitmaps.embedsWholeEmojiFont
        log("stamped ${pages.size} pages in ${SystemClock.elapsedRealtime() - started} ms, forms ${summary.forms}, stamp document ${summary.stampBytes} B, ToUnicode repairs ${summary.repairedMappings}, Type 3 glyphs pruned ${summary.prunedGlyphs}, output ${output.length()} B, emoji as bitmaps $bitmaps")
        assertEquals(pages.size, summary.pages)
        // Pages of the same canvas size (595 x 842 here) share the forms of each layer. zh-CN: 画布尺寸相同的页面共用各层的表单.
        assertEquals(2, summary.forms)
        assertTrue("the stamp document has ${summary.stampBytes} B", summary.stampBytes < 1024 * 1024)

        // Emoji drawn as bitmaps (older backends) are visible but not extractable. zh-CN: 画成位图的表情 (旧后端) 可见但不可提取.
        val expected = if (bitmaps) STAMP_TEXT_WITHOUT_EMOJI else STAMP_TEXT
        PDDocument.load(output).use { document ->
            for (index in pages.indices) {
                val extracted = text(document, index + 1)
                log("${pages[index].label}: extracted ${extracted.trim().replace('\n', '|')}")
                assertTrue("${pages[index].label}: '$extracted' lacks '$expected'", extracted.contains(expected))
            }
        }

        val scale = 2
        val expectedRed = intArrayOf(100 * scale, 150 * scale, 140 * scale, 170 * scale)
        var reference: IntArray? = null
        for (index in pages.indices) {
            val label = pages[index].label
            val bitmap = render(output, index, scale)
            assertEquals("$label: displayed width", 595 * scale, bitmap.width)
            assertEquals("$label: displayed height", 842 * scale, bitmap.height)
            val red = bounds(bitmap, 0, 0, bitmap.width, 500 * scale, ::isRed)
            assertTrue("$label: the anchor probe is not drawn", red != null)
            val errors = expectedRed.indices.map { abs(red!![it] - expectedRed[it]) / scale.toDouble() }
            log("$label: anchor probe ${red!!.joinToString()} px, error ${errors.max()} pt")
            assertTrue("$label: anchor probe at ${red.joinToString()} px, expected ${expectedRed.joinToString()} (scale $scale)", errors.all { it <= 1.0 })

            val ink = bounds(bitmap, 50 * scale, 390 * scale, 560 * scale, 450 * scale, ::isInk)
            assertTrue("$label: the text stamp has no ink", ink != null)
            val inkPixels = (ink!![0] until ink[2]).sumOf { x -> (ink[1] until ink[3]).count { y -> isInk(bitmap.getPixel(x, y)) } }
            log("$label: text ink ${ink.joinToString()} px, $inkPixels dark pixels")
            if (reference == null) reference = ink
            val drift = ink.indices.map { abs(ink[it] - reference!![it]) / scale.toDouble() }
            assertTrue("$label: text ink at ${ink.joinToString()}, page 1 at ${reference!!.joinToString()}", drift.all { it <= 1.0 })
            // The anchor (60, 400) is the top left corner of the text box; glyphs start at or right of it.
            // zh-CN: 锚点 (60, 400) 是文字方框的左上角; 字形从它开始或在其右下方.
            assertTrue("$label: text starts left of or above its anchor", ink[0] >= 59 * scale && ink[1] >= 399 * scale)
            // The emoji is the only colourful part of the text stamp. zh-CN: 表情是文字印章中唯一有彩色的部分.
            val emoji = bounds(bitmap, 50 * scale, 390 * scale, 560 * scale, 450 * scale) { maxOf(Color.red(it), Color.green(it), Color.blue(it)) - minOf(Color.red(it), Color.green(it), Color.blue(it)) > 80 }
            log("$label: emoji colour ${emoji?.joinToString() ?: "none"}")
            assertTrue("$label: the emoji is not drawn", emoji != null && emoji[2] - emoji[0] >= 10 * scale)

            val hidden = bounds(bitmap, 370 * scale, 630 * scale, 430 * scale, 670 * scale, ::isBlue)
            val visible = bounds(bitmap, 510 * scale, 630 * scale, 570 * scale, 670 * scale, ::isBlue)
            assertTrue("$label: the lower layer shows through the page content", hidden == null)
            assertTrue("$label: the lower layer beside the content is missing", visible != null)
            bitmap.recycle()
        }
    }

    @Test
    fun opacityRotationAndAFontInput() {
        val system = listOf("/system/fonts/Roboto-Regular.ttf", "/system/fonts/DroidSans.ttf").map(::File).firstOrNull { it.isFile }
        val custom = system?.let { source ->
            File(directory, "font.ttf").also { source.copyTo(it) }.let { Typeface.createFromFile(it) }
        }
        val stamps = listOf(
            TextStamp("MMMM", 60.0, 120.0, fontSize = 60.0, opacity = 0.3),
            TextStamp("Rotated", 300.0, 420.0, anchorX = 0.5, anchorY = 0.5, fontSize = 40.0, rotation = 90.0),
            TextStamp("Custom font", 60.0, 700.0, fontSize = 20.0, customFont = custom != null),
        )
        val output = File(directory, "styles.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(0f, 0f, 595f, 842f)))
            CanvasStamps(directory, StampFonts(custom)).apply(document, listOf(CanvasStamps.PageStamps(0, stamps)))
            document.save(output)
        }
        val scale = 2
        val bitmap = render(output, 0, scale)
        // Opacity 0.3 of black is a luminance near 255 * 0.7. zh-CN: 黑色在不透明度 0.3 下的亮度接近 255 * 0.7.
        val faint = bounds(bitmap, 50 * scale, 110 * scale, 420 * scale, 200 * scale) { luminance(it) < 250 }
        assertTrue("the faint stamp is not drawn", faint != null)
        var darkest = 255
        for (x in faint!![0] until faint[2]) for (y in faint[1] until faint[3]) darkest = minOf(darkest, luminance(bitmap.getPixel(x, y)))
        log("opacity 0.3: darkest luminance $darkest (expected about ${(255 * 0.7).roundToInt()})")
        assertTrue("opacity 0.3 gives a darkest luminance of $darkest", darkest in 160..200)
        // Turned by 90 degrees, the word is taller than wide. zh-CN: 旋转 90 度后, 单词高大于宽.
        val turned = bounds(bitmap, 200 * scale, 250 * scale, 400 * scale, 600 * scale, ::isInk)
        assertTrue("the rotated stamp is not drawn", turned != null)
        val width = turned!![2] - turned[0]
        val height = turned[3] - turned[1]
        log("rotation 90: ink ${turned.joinToString()} px, $width x $height")
        assertTrue("the rotated stamp is $width x $height px", height > 2 * width)
        bitmap.recycle()

        PDDocument.load(output).use { document ->
            val extracted = text(document, 1)
            assertTrue("'$extracted' lacks the custom font text", extracted.contains("Custom font"))
            val fonts = fontNames(document.getPage(0).resources)
            log("font input ${system?.name ?: "none"}: fonts $fonts")
            if (custom != null) assertTrue("no Roboto / Droid font among $fonts", fonts.any { it.contains("Roboto") || it.contains("Droid") })
        }
    }

    @Test
    fun pageNumbersOnFiftyPages() {
        val count = 50
        val output = File(directory, "numbers.pdf")
        val started = SystemClock.elapsedRealtime()
        val summary = PDDocument().use { document ->
            repeat(count) { document.addPage(PDPage(PDRectangle.A4)) }
            val plan = (0 until count).map { CanvasStamps.PageStamps(it, listOf(TextStamp("第 ${it + 1} 页, 共 $count 页", 297.0, 820.0, anchorX = 0.5, anchorY = 1.0))) }
            val summary = CanvasStamps(directory).apply(document, plan)
            document.save(output)
            summary
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        log("page numbers on $count pages: $elapsed ms, forms ${summary.forms}, stamp document ${summary.stampBytes} B, ToUnicode repairs ${summary.repairedMappings}, Type 3 glyphs pruned ${summary.prunedGlyphs}, output ${output.length()} B")
        assertEquals(count, summary.forms)
        PDDocument.load(output).use { document ->
            for (number in listOf(1, 25, count)) {
                assertTrue("page $number lacks its number", text(document, number).contains("第 $number 页, 共 $count 页"))
            }
            // The fonts of the temporary document are cloned once and serve every page.
            // zh-CN: 临时文档的字体只克隆一次, 服务所有页面.
            val first = fontObjects(document.getPage(0).resources).distinct()
            val fonts = (0 until count).flatMap { fontObjects(document.getPage(it).resources) }.distinct()
            log("page numbers: ${fonts.size} distinct font objects, ${first.size} on page 1")
            assertEquals("font objects of $count pages", first.size, fonts.size)
        }
    }

    /** Font names used by the forms in [resources]. zh-CN: [resources] 中各表单使用的字体名称. */
    private fun fontNames(resources: PDResources): List<String> =
        forms(resources).flatMap { form -> form.resources.fontNames.map { form.resources.getFont(it).name.orEmpty() } }

    private fun fontObjects(resources: PDResources): List<COSDictionary> =
        forms(resources).flatMap { form -> form.resources.fontNames.map { form.resources.getFont(it).cosObject } }

    private fun forms(resources: PDResources): List<PDFormXObject> =
        resources.xObjectNames.mapNotNull { resources.getXObject(it) as? PDFormXObject }

    companion object {

        private const val TAG = "PaperStampSpike"

        private const val STAMP_TEXT_WITHOUT_EMOJI = "机密文件 Stamp"

        private val STAMP_TEXT = STAMP_TEXT_WITHOUT_EMOJI + " " + String(Character.toChars(0x1F600))

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

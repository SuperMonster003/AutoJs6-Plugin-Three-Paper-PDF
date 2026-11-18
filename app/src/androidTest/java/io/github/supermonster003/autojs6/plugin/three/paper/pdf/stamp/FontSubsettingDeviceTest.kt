package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Roadmap Q191 (b) on a device: [FontSubsetting] shrinks a TrueType font embedded whole (a system font PDFBox embeds
 * without subsetting, and the static Roboto the Skia of API 33 and later writes into every stamp document) while text
 * extraction stays the same and the system `PdfRenderer` draws the same pixels. Numbers go to logcat under
 * `PaperFontSubset`.
 * zh-CN: 路线图 Q191 (b) 在设备上的验证: [FontSubsetting] 缩小整个嵌入的 TrueType 字体 (PDFBox 不做子集化嵌入的系统字体, 以及 API 33
 * 起 Skia 写进每个印章文档的静态 Roboto), 文本提取不变, 系统 `PdfRenderer` 画出相同的像素. 数值以 `PaperFontSubset` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class FontSubsettingDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "font-subset").apply { deleteRecursively(); mkdirs() }
    }

    private val keep: Boolean get() = InstrumentationRegistry.getArguments().getString("paperKeep") == "true"

    @After
    fun tearDown() {
        if (!keep) directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    private fun text(file: File, number: Int): String = PDDocument.load(file).use { document ->
        PDFTextStripper().apply {
            sortByPosition = true
            startPage = number
            endPage = number
        }.getText(document)
    }

    private fun render(file: File, index: Int): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    /**
     * Pixels of [a] and [b] whose channels differ by more than [tolerance], and the inked pixels of [a].
     * zh-CN: [a] 与 [b] 中通道差超过 [tolerance] 的像素数, 以及 [a] 的着墨像素数.
     */
    private fun difference(a: Bitmap, b: Bitmap, tolerance: Int = 8): Pair<Int, Int> {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        val rowA = IntArray(a.width)
        val rowB = IntArray(b.width)
        var differing = 0
        var inked = 0
        for (y in 0 until a.height) {
            a.getPixels(rowA, 0, a.width, 0, y, a.width, 1)
            b.getPixels(rowB, 0, b.width, 0, y, b.width, 1)
            for (x in rowA.indices) {
                if (rowA[x] != Color.WHITE) inked++
                val delta = maxOf(
                    abs(Color.red(rowA[x]) - Color.red(rowB[x])),
                    abs(Color.green(rowA[x]) - Color.green(rowB[x])),
                    abs(Color.blue(rowA[x]) - Color.blue(rowB[x])),
                )
                if (delta > tolerance) differing++
            }
        }
        return differing to inked
    }

    private fun assertSameRendering(label: String, whole: File, subset: File, pages: List<Int>) {
        for (index in pages) {
            val a = render(whole, index)
            val b = render(subset, index)
            val (differing, inked) = difference(a, b)
            log("$label page ${index + 1}: $differing of ${a.width * a.height} pixels differ, $inked inked")
            if (keep) {
                // Kept for a look on the computer (`-e paperKeep true`). zh-CN: 保留以便在电脑上查看 (`-e paperKeep true`).
                for ((bitmap, name) in listOf(a to "whole", b to "subset")) {
                    File(directory, "$label-${index + 1}-$name.png".replace(' ', '-')).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            assertTrue("$label page ${index + 1}: nothing drawn", inked > 0)
            // Hinting may differ by a pixel at glyph edges; no more than 0.5 % of the ink. zh-CN: 字形边缘的微调可能相差一个像素; 不超过着墨的 0.5 %.
            assertTrue("$label page ${index + 1}: $differing pixels differ ($inked inked)", differing * 200 <= inked)
            a.recycle()
            b.recycle()
        }
    }

    @Test
    fun aSystemFontEmbeddedWholeIsSubset() {
        val font = SYSTEM_FONTS.map(::File).firstOrNull { it.isFile }
        assumeTrue("no static system TrueType font", font != null)
        val whole = File(directory, "whole.pdf")
        val subset = File(directory, "subset.pdf")
        PDDocument().use { document ->
            // PDFBox 2.0 takes embedSubset only with a stream; a whole font is read and embedded while loading.
            // zh-CN: PDFBox 2.0 只在传入流时接受 embedSubset; 整个嵌入的字体在载入时即读入并嵌入.
            val type0 = font!!.inputStream().use { PDType0Font.load(document, it, false) }
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(type0, 28f)
                stream.newLineAtOffset(60f, 700f)
                stream.showText(SAMPLE)
                stream.endText()
            }
            document.save(whole)
        }
        val result = PDDocument.load(whole).use { document ->
            val result = FontSubsetting.subset(document)
            document.save(subset)
            result
        }
        log("${font!!.name}: ${result.fonts} fonts, program ${result.bytesBefore} -> ${result.bytesAfter} B, file ${whole.length()} -> ${subset.length()} B, skipped ${result.skipped}")
        assertEquals("skipped: ${result.skipped}", 1, result.fonts)
        assertTrue("the file did not shrink: ${whole.length()} -> ${subset.length()} B", subset.length() * 3 < whole.length())
        assertEquals(text(whole, 1), text(subset, 1))
        assertTrue(text(subset, 1).contains(SAMPLE))
        PDDocument.load(subset).use { document ->
            val resources = document.getPage(0).resources
            val type0 = resources.getFont(resources.fontNames.single()).cosObject
            val descendant = (type0.getDictionaryObject(COSName.DESCENDANT_FONTS) as COSArray).getObject(0) as COSDictionary
            assertTrue("the CIDToGIDMap is a stream", descendant.getDictionaryObject(COSName.CID_TO_GID_MAP) is COSStream)
            assertTrue("the names carry a subset tag", Regex("^[A-Z]{6}\\+").containsMatchIn(type0.getNameAsString(COSName.BASE_FONT).orEmpty()))
        }
        assertSameRendering(font.name, whole, subset, listOf(0))
    }

    @Test
    fun pageNumbersOnFiftyPagesWithAndWithoutSubsetting() {
        val count = 50
        val outputs = listOf(false, true).map { subsetting ->
            val output = File(directory, "numbers-$subsetting.pdf")
            val started = SystemClock.elapsedRealtime()
            val summary = PDDocument().use { document ->
                repeat(count) { document.addPage(PDPage(PDRectangle.A4)) }
                val plan = (0 until count).map { CanvasStamps.PageStamps(it, listOf(TextStamp("第 ${it + 1} 页, 共 $count 页", 297.0, 820.0, anchorX = 0.5, anchorY = 1.0))) }
                val summary = CanvasStamps(directory, subsetFonts = subsetting).apply(document, plan)
                document.save(output)
                summary
            }
            log(
                "page numbers on $count pages, subsetting $subsetting: ${SystemClock.elapsedRealtime() - started} ms, stamp document ${summary.stampBytes} B, " +
                    "fonts subset ${summary.fonts.fonts} (${summary.fonts.bytesBefore} -> ${summary.fonts.bytesAfter} B), skipped ${summary.fonts.skipped}, output ${output.length()} B",
            )
            output
        }
        val (whole, subset) = outputs
        assertTrue("subsetting grew the output: ${whole.length()} -> ${subset.length()} B", subset.length() <= whole.length())
        // Roadmap Q191 (b): the 50-page sample stays below 100 KB on every API level. zh-CN: 路线图 Q191 (b): 50 页样本在各 API 级别都小于 100 KB.
        assertTrue("the 50-page sample has ${subset.length()} B", subset.length() < 100 * 1024)
        for (number in listOf(1, 25, count)) assertEquals("page $number", text(whole, number), text(subset, number))
        assertTrue(text(subset, count).contains("第 $count 页, 共 $count 页"))
        assertSameRendering("page numbers", whole, subset, listOf(0, count - 1))
    }

    companion object {

        private const val TAG = "PaperFontSubset"

        private const val SAMPLE = "Subset 3-Paper PDF, fi fl 0123"

        /** Static TrueType system fonts (API 33 and later ship a variable Roboto-Regular). zh-CN: 静态 TrueType 系统字体. */
        private val SYSTEM_FONTS = listOf("/system/fonts/RobotoStatic-Regular.ttf", "/system/fonts/Roboto-Regular.ttf", "/system/fonts/DroidSans.ttf")

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

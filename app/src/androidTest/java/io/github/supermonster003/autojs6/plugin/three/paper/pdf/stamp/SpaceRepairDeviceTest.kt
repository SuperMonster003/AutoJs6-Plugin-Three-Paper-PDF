package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Roadmap Q192 (c) on a device: [SpaceRepair] on content shaped like the Type 3 output of the Skia of API 35 (glyphs
 * with a 0.25 em gap where a space was laid out lose that space in extraction; a space with no glyph gets an invisible
 * one right after the text object of the glyph before it, or before the one after it, or after the form XObject that
 * holds it, placed through the inverse of the CTM in force; a space whose glyph is there gets nothing), then the real
 * stamp pipeline with the three cases [StampFonts.noBreakSpaces] cannot cover (a space after an emoji, between two
 * Arabic-Indic digits, at the start of a line before CJK text): with the repair every space is extracted, on API 24 to
 * 34 nothing is added, and the system `PdfRenderer` draws the same pixels with and without it. PDFBox text extraction
 * needs the font resources of a device, hence no JVM test. Numbers go to logcat under `PaperStampSpike`.
 * zh-CN: 路线图 Q192 (c) 在设备上的验证: 先用形如 API 35 的 Skia 所写 Type 3 输出的内容检查 [SpaceRepair] (排版时有空格之处只留下
 * 0.25 em 的间隙, 提取时丢失该空格; 没有字形的空格在其前一字形的文本对象之后, 或后一字形的对象之前, 或包含它的表单 XObject 之后得到
 * 一个不可见字形, 经当时 CTM 的逆矩阵放置; 字形存在的空格不加任何东西), 再用真实的印章管线检查 [StampFonts.noBreakSpaces] 覆盖不到的
 * 三种情况 (表情之后, 两个阿拉伯-印度数字之间, 行首且后接中日韩文字的空格): 修复后每个空格都能提取, API 24 - 34 上不增加任何东西, 有无
 * 修复时系统 `PdfRenderer` 画出相同的像素. PDFBox 文本提取需要设备上的字体资源, 因此不写 JVM 测试. 数值以 `PaperStampSpike` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class SpaceRepairDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File

    private val keep: Boolean get() = InstrumentationRegistry.getArguments().getString("paperKeep") == "true"

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "space-repair").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        if (!keep) directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    private fun PDDocument.stream(content: String) = PDStream(this).apply { createOutputStream().use { it.write(content.toByteArray(Charsets.US_ASCII)) } }

    /**
     * A Type 3 font of the glyphs `A`, `B`, `C` (codes 1 - 3, 1 em wide) and, with [space], a space (code 4, 0.25 em)
     * with a `/ToUnicode` map, as Skia writes them on API 35 (a font run that lost its space has no space glyph, so
     * PDFBox takes the average width as the space width and adds no word separator for a 0.25 em gap).
     * zh-CN: 一个 Type 3 字体, 字形 `A`, `B`, `C` (编码 1 - 3, 宽 1 em), [space] 为 true 时另有空格 (编码 4, 0.25 em), 带 `/ToUnicode`
     * 映射, 形如 API 35 的 Skia 所写 (丢了空格的字体段没有空格字形, PDFBox 于是以平均宽度作空格宽度, 不会为 0.25 em 的间隙加词分隔).
     */
    private fun PDDocument.font(space: Boolean = false): COSDictionary {
        val procedures = COSDictionary()
        val names = COSArray().apply { add(COSInteger.get(1)) }
        for (name in listOf("A", "B", "C", "space")) {
            procedures.setItem(COSName.getPDFName(name), stream("0 0 d0").cosObject)
            names.add(COSName.getPDFName(name))
        }
        val toUnicode = stream(
            "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CMapName /T3 def\n/CMapType 2 def\n" +
                "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n" +
                (if (space) "4 beginbfchar\n<04> <0020>\n" else "3 beginbfchar\n") + "<01> <0041>\n<02> <0042>\n<03> <0043>\nendbfchar\n" +
                "endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n",
        )
        return COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.TYPE3)
            setItem(COSName.FONT_BBOX, COSArray().apply { listOf(0, -200, 1000, 800).forEach { add(COSInteger.get(it.toLong())) } })
            setItem(COSName.FONT_MATRIX, COSArray().apply { listOf(0.001f, 0f, 0f, 0.001f, 0f, 0f).forEach { add(COSFloat(it)) } })
            setItem(COSName.CHAR_PROCS, procedures)
            setItem(COSName.ENCODING, COSDictionary().apply { setItem(COSName.DIFFERENCES, names) })
            setInt(COSName.FIRST_CHAR, 1)
            setInt(COSName.LAST_CHAR, 4)
            setItem(COSName.WIDTHS, COSArray().apply { listOf(1000, 1000, 1000, 250).forEach { add(COSInteger.get(it.toLong())) } })
            setItem(COSName.RESOURCES, COSDictionary())
            setItem(COSName.TO_UNICODE, toUnicode.cosObject)
        }
    }

    /** A page of 200 x 100 pt with [content] and [font] as `/F1`. zh-CN: 一个 200 x 100 pt 的页面, 内容为 [content], [font] 名为 `/F1`. */
    private fun PDDocument.page(content: String, font: COSDictionary, forms: Map<String, PDFormXObject> = emptyMap()): PDPage {
        val page = PDPage(PDRectangle(200f, 100f))
        page.resources = PDResources().apply {
            cosObject.setItem(COSName.FONT, COSDictionary().apply { setItem(COSName.getPDFName("F1"), font) })
            if (forms.isNotEmpty()) cosObject.setItem(COSName.XOBJECT, COSDictionary().apply { forms.forEach { (name, form) -> setItem(COSName.getPDFName(name), form) } })
        }
        page.setContents(stream(content))
        addPage(page)
        return page
    }

    private fun text(document: PDDocument, sorted: Boolean, number: Int? = null): String = PDFTextStripper().apply {
        sortByPosition = sorted
        lineSeparator = "\n"
        if (number != null) {
            startPage = number
            endPage = number
        }
    }.getText(document)

    private fun operators(page: PDPage): List<String> = PDFStreamParser(page).apply { parse() }.tokens.filterIsInstance<Operator>().map { it.name }

    /**
     * A space laid out from canvas x [left] to [right] on the canvas baseline [baseline] (canvas y down from the top of
     * the 100 pt page) with an em of [em].
     * zh-CN: 在画布基线 [baseline] (画布 y 自 100 pt 页面顶部向下) 上从画布 x [left] 到 [right] 排版的空格, em 为 [em].
     */
    private fun space(left: Double, right: Double, baseline: Double, em: Double = 10.0, character: Char = ' ') = LaidSpace(left, baseline, right, baseline, em, character)

    @Test
    fun aDroppedSpaceIsAddedAfterTheTextObjectBeforeItThroughTheInverseCtm() {
        PDDocument().use { document ->
            // CTM 2x: A from user x 20 to 30, B from 32.5 to 42.5 on user y 40 (canvas y 60); the space was 30 - 32.5.
            // zh-CN: CTM 放大 2 倍: A 位于用户空间 x 20 - 30, B 位于 32.5 - 42.5, 用户空间 y 40 (画布 y 60); 空格原在 30 - 32.5.
            val page = document.page("q 2 0 0 2 0 0 cm BT /F1 5 Tf 10 20 Td <01> Tj ET BT /F1 5 Tf 16.25 20 Td <02> Tj ET Q", document.font())
            assertEquals("AB", text(document, sorted = true).trim())
            assertEquals(1, SpaceRepair.repair(document, mapOf(0 to listOf(space(30.0, 32.5, 60.0)))))
            assertEquals("A B", text(document, sorted = true).trim())
            assertEquals("A B", text(document, sorted = false).trim())
            assertEquals(
                listOf("q", "cm", "BT", "Tf", "Td", "Tj", "ET", "q", "cm", "BT", "Tr", "Tz", "Tc", "Tw", "Ts", "Tf", "Tj", "ET", "Q", "BT", "Tf", "Td", "Tj", "ET", "Q"),
                operators(page),
            )
            assertEquals(2, page.resources.fontNames.count())
        }
    }

    @Test
    fun aSpaceAtTheStartOfALineGoesBeforeTheTextObjectAfterItAndAPresentSpaceGetsNothing() {
        PDDocument().use { document ->
            // Line 1: (space) B at user x 52.5; line 2 (user y 20): A, a space glyph, C.
            // zh-CN: 第 1 行: (空格) B 位于用户空间 x 52.5; 第 2 行 (用户空间 y 20): A, 空格字形, C.
            document.page(
                "BT /F1 10 Tf 52.5 40 Td <02> Tj ET " +
                    "BT /F1 10 Tf 20 20 Td <01> Tj <04> Tj <03> Tj ET",
                document.font(space = true),
            )
            val spaces = listOf(space(50.0, 52.5, 60.0, character = StampFonts.NO_BREAK_SPACE), space(30.0, 32.5, 80.0))
            assertEquals(1, SpaceRepair.repair(document, mapOf(0 to spaces)))
            // Not trimmed: the line starts with the no-break space. zh-CN: 不去除首尾空白: 该行以不换行空格开头.
            val lines = text(document, sorted = false).lines()
            assertEquals(StampFonts.NO_BREAK_SPACE + "B", lines[0])
            assertEquals("A C", lines[1])
            assertEquals(0, SpaceRepair.repair(document, mapOf(0 to spaces)))
        }
    }

    @Test
    fun aGlyphInsideAFormXObjectPutsTheSpaceAfterItsDo() {
        PDDocument().use { document ->
            val font = document.font()
            val form = PDFormXObject(document.stream("BT /F1 10 Tf 20 40 Td <01> Tj ET"))
            form.resources = PDResources().apply { cosObject.setItem(COSName.FONT, COSDictionary().apply { setItem(COSName.getPDFName("F1"), font) }) }
            form.bBox = PDRectangle(200f, 100f)
            val page = document.page("/Fm1 Do BT /F1 10 Tf 32.5 40 Td <02> Tj ET", font, mapOf("Fm1" to form))
            assertEquals(1, SpaceRepair.repair(document, mapOf(0 to listOf(space(30.0, 32.5, 60.0)))))
            assertEquals(listOf("Do", "q", "cm", "BT"), operators(page).take(4))
            assertEquals("A B", text(document, sorted = true).trim())
        }
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

    /** Pixels of [a] and [b] that differ at all. zh-CN: [a] 与 [b] 中有任何差异的像素数. */
    private fun differing(a: Bitmap, b: Bitmap): Int {
        val rowA = IntArray(a.width)
        val rowB = IntArray(b.width)
        var count = 0
        for (y in 0 until a.height) {
            a.getPixels(rowA, 0, a.width, 0, y, a.width, 1)
            b.getPixels(rowB, 0, b.width, 0, y, b.width, 1)
            for (x in rowA.indices) if (rowA[x] != rowB[x]) count++
        }
        return count
    }

    @Test
    fun spacesAfterAnEmojiBetweenArabicIndicDigitsAndAtALineStartAreExtracted() {
        val emoji = String(Character.toChars(0x1F600))
        val digits = String(intArrayOf(0x0661, 0x0662), 0, 2) + " " + String(intArrayOf(0x0663, 0x0664), 0, 2)
        val texts = listOf("印章$emoji 文字", "页 $digits", " 第一行")
        val outputs = listOf(false, true).map { repair ->
            val output = File(directory, "spaces-$repair.pdf")
            val summary = PDDocument().use { document ->
                repeat(2) { document.addPage(PDPage(PDRectangle.A4)) }
                val first = texts.mapIndexed { index, text -> TextStamp(text, 60.0, 120.0 + 80.0 * index, fontSize = 24.0) }
                // A rotated copy of the emoji case checks the placement through the stamp rotation.
                // zh-CN: 表情情况的旋转副本检查经印章旋转的放置.
                val second = listOf(TextStamp(texts[0], 300.0, 420.0, anchorX = 0.5, anchorY = 0.5, fontSize = 24.0, rotation = 90.0))
                val summary = CanvasStamps(directory, repairSpaces = repair).apply(document, listOf(CanvasStamps.PageStamps(0, first), CanvasStamps.PageStamps(1, second)))
                document.save(output)
                summary
            }
            log("spaces, repair $repair: added ${summary.addedSpaces}, output ${output.length()} B")
            output to summary
        }
        val (without, withRepair) = outputs.map { it.first }
        val added = outputs[1].second.addedSpaces
        val texts1 = PDDocument.load(withRepair).use { listOf(text(it, sorted = true, number = 1), text(it, sorted = false, number = 1), text(it, sorted = true, number = 2)) }
        val texts0 = PDDocument.load(without).use { listOf(text(it, sorted = true, number = 1), text(it, sorted = false, number = 1), text(it, sorted = true, number = 2)) }
        listOf("sorted", "content order", "rotated").forEachIndexed { index, label ->
            log("spaces $label without repair: ${texts0[index].replace("\n", " | ")}")
            log("spaces $label with repair: ${texts1[index].replace("\n", " | ")}")
        }
        val (sorted, raw, rotated) = texts1
        for ((label, text) in listOf("sorted" to sorted, "content order" to raw)) {
            assertTrue("$label: no space before 文字 in '$text'", text.contains(" 文字"))
            assertTrue("$label: no space at the start of 第一行 in '$text'", text.lines().any { it.startsWith(" 第一行") })
            assertTrue("$label: no space between the Arabic-Indic digits in '$text'", Regex("[\\u0660-\\u0669]+ [\\u0660-\\u0669]+").containsMatchIn(text))
            assertFalse("$label: a doubled space in '$text'", text.contains("  "))
        }
        assertTrue("rotated: no space before 文字 in '$rotated'", rotated.contains(" 文字"))
        if (Build.VERSION.SDK_INT < 35) assertEquals("spaces added below API 35", 0, added)
        for (index in 0 until 2) {
            val a = render(without, index)
            val b = render(withRepair, index)
            val count = differing(a, b)
            log("spaces page ${index + 1}: $count pixels differ with the repair")
            assertEquals("page ${index + 1}: pixels differ with the repair", 0, count)
            a.recycle()
            b.recycle()
        }
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

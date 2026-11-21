package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.fontbox.cmap.CMapParser
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ToUnicodeRepair] maps the radicals and ligatures Skia writes for drawn ideographs and letters back to what was drawn
 * (spike S2), and leaves entries it cannot decide alone.
 * zh-CN: [ToUnicodeRepair] 把 Skia 为所画表意文字与字母写出的部首与连字映射回实际绘制的字符 (spike S2), 无法判定的条目保持不变.
 */
class ToUnicodeRepairTest {

    private fun points(text: String): Set<Int> = text.codePoints().toArray().toHashSet()

    private val radicalLeaf = String(Character.toChars(0x2EDA))
    private val radicalScript = String(Character.toChars(0x2F42))
    private val page = String(Character.toChars(0x9875))
    private val script = String(Character.toChars(0x6587))
    private val ordinal = String(Character.toChars(0x7B2C))

    private val leafIsPage: (Int, Int) -> Boolean = { a, b -> a == 0x2EDA && b == 0x9875 }

    @Test
    fun `a radical without decomposition goes to the drawn ideograph that draws the same glyph`() {
        val mappings = mapOf(0x21 to radicalLeaf, 0xE1 to ordinal, 0x0D to ",")
        val drawn = points("$ordinal 1 $page, 12")
        val accounted = points(radicalLeaf + ordinal + ",")
        assertEquals(mapOf(0x21 to page), ToUnicodeRepair.plan(mappings, drawn, accounted, leafIsPage))
    }

    @Test
    fun `Kangxi radicals and ligatures take their NFKC form when it was drawn`() {
        val drawn = points("$script fi")
        val mappings = mapOf(5 to radicalScript, 7 to String(Character.toChars(0xFB01)), 9 to "f")
        assertEquals(mapOf(5 to script, 7 to "fi"), ToUnicodeRepair.plan(mappings, drawn, points(radicalScript + "f"), { _, _ -> false }))
    }

    @Test
    fun `no-break spaces laid out for spaces map back to the space unless one was drawn`() {
        val noBreak = StampFonts.NO_BREAK_SPACE.toString()
        val mappings = mapOf(0xD1 to noBreak, 0x05 to " ", 0x16 to "1")
        assertEquals(mapOf(0xD1 to " "), ToUnicodeRepair.plan(mappings, points("$ordinal 1 $page"), points(" 1$noBreak"), { _, _ -> false }))
        assertEquals(emptyMap<Int, String>(), ToUnicodeRepair.plan(mappings, points("$ordinal 1${noBreak}2"), points(" 1$noBreak"), { _, _ -> false }))
    }

    @Test
    fun `spaces are laid out as no-break spaces except between two digits`() {
        val noBreak = StampFonts.NO_BREAK_SPACE
        assertEquals("$ordinal${noBreak}1${noBreak}$page,${noBreak}12 34${noBreak}A", StampFonts.noBreakSpaces("$ordinal 1 $page, 12 34 A"))
        assertEquals("${noBreak}x$noBreak", StampFonts.noBreakSpaces(" x "))
        assertEquals("plain", StampFonts.noBreakSpaces("plain"))
    }

    @Test
    fun `entries that were drawn, that match several candidates or none stay`() {
        val drawn = points("$page$script")
        assertEquals(emptyMap<Int, String>(), ToUnicodeRepair.plan(mapOf(1 to page), drawn, points(page), { _, _ -> true }))
        // Two unaccounted candidates draw "the same glyph": undecidable. zh-CN: 两个未对应的候选都 "同字形": 无法判定.
        assertEquals(emptyMap<Int, String>(), ToUnicodeRepair.plan(mapOf(1 to radicalLeaf), drawn, points(radicalLeaf), { _, _ -> true }))
        assertEquals(emptyMap<Int, String>(), ToUnicodeRepair.plan(mapOf(1 to radicalLeaf), drawn, points(radicalLeaf), { _, _ -> false }))
    }

    private fun cmap(codeDigits: Int, entries: Map<Int, String>): String = buildString {
        append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
        append("1 begincodespacerange\n<").append("0".repeat(codeDigits)).append("> <").append("F".repeat(codeDigits)).append(">\nendcodespacerange\n")
        append(entries.size).append(" beginbfchar\n")
        for ((code, target) in entries) {
            append('<').append(code.toString(16).padStart(codeDigits, '0')).append("> <")
            target.forEach { append(it.code.toString(16).padStart(4, '0')) }
            append(">\n")
        }
        append("endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
    }

    private fun font(document: PDDocument, subtype: COSName, cmap: String): COSDictionary {
        val stream: COSStream = document.document.createCOSStream()
        stream.createOutputStream().use { it.write(cmap.toByteArray(Charsets.US_ASCII)) }
        return COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, subtype)
            setItem(COSName.TO_UNICODE, stream)
        }
    }

    private fun mapping(font: COSDictionary, code: Int): String? =
        (font.getDictionaryObject(COSName.TO_UNICODE) as COSStream).createInputStream().use { CMapParser(true).parse(it) }.toUnicode(code)

    @Test
    fun `the maps of Type 3 and Type 0 fonts are rewritten in place`() {
        PDDocument().use { document ->
            val type3 = font(document, COSName.TYPE3, cmap(2, mapOf(0x21 to radicalLeaf, 0xE1 to ordinal)))
            val type0 = font(document, COSName.TYPE0, cmap(4, mapOf(0x0005 to " ", 0x0016 to "1", 0x0101 to radicalScript)))
            val fonts = COSDictionary().apply {
                setItem(COSName.getPDFName("F1"), type3)
                setItem(COSName.getPDFName("F2"), type0)
            }
            document.addPage(PDPage().apply { resources = PDResources(COSDictionary().apply { setItem(COSName.FONT, fonts) }) })
            val changed = ToUnicodeRepair.repair(document, listOf(listOf("$ordinal 1 $page $script")), leafIsPage)
            assertEquals(2, changed)
            assertEquals(page, mapping(type3, 0x21))
            assertEquals(ordinal, mapping(type3, 0xE1))
            assertEquals(script, mapping(type0, 0x0101))
            assertEquals("1", mapping(type0, 0x0016))
            assertEquals(" ", mapping(type0, 0x0005))
        }
    }

    private fun PDDocument.content(text: String) = PDStream(this).apply { createOutputStream().use { it.write(text.toByteArray(Charsets.US_ASCII)) } }

    private fun PDDocument.page(fonts: Map<String, COSDictionary>, operators: String): PDPage {
        val dictionary = COSDictionary().apply { fonts.forEach { (name, font) -> setItem(COSName.getPDFName(name), font) } }
        return PDPage().apply {
            resources = PDResources(COSDictionary().apply { setItem(COSName.FONT, dictionary) })
            setContents(content(operators))
            addPage(this)
        }
    }

    /** The map Skia writes for the system font of ColorOS 15: the hyphen before a capital is glyph 0x91 at U+0000. zh-CN: Skia 为 ColorOS 15 系统字体写出的映射: 大写字母前的连字符为字形 0x91, 映射到 U+0000. */
    private val coloros = mapOf(0x33 to "3", 0x91 to Char(0).toString(), 0x2D to "-", 0x50 to "P", 0x61 to "a", 0x70 to "p", 0x65 to "e", 0x72 to "r", 0x20 to " ", 0x41 to "A", 0x42 to "B")

    @Test
    fun `a glyph at U+0000 takes the character drawn at its place on the page (Q322)`() {
        PDDocument().use { document ->
            val type3 = font(document, COSName.TYPE3, cmap(2, coloros))
            val type0 = font(document, COSName.TYPE0, cmap(4, mapOf(0x0101 to "x", 0x0102 to Char(0).toString(), 0x0103 to "y")))
            // "3-Paper" in one text object, split over two Type 3 resource names of the same font, then "x-y" in a Type 0 font.
            // zh-CN: "3-Paper" 在一个文本对象中, 分在同一字体的两个 Type 3 资源名上; 然后是 Type 0 字体中的 "x-y".
            document.page(mapOf("F1" to type3, "F2" to type3, "F3" to type0),
                "BT /F1 8 Tf <33> Tj /F2 8 Tf <91> Tj <50617065> Tj <72> Tj ET BT /F3 8 Tf <010101020103> Tj ET")
            val changed = ToUnicodeRepair.repair(document, listOf(listOf("3-Paper x-y")), { _, _ -> false })
            assertEquals(2, changed)
            assertEquals("-", mapping(type3, 0x91))
            assertEquals("-", mapping(type0, 0x0102))
            assertEquals("3", mapping(type3, 0x33))
        }
    }

    @Test
    fun `texts are matched per page and a code read differently on two pages stays`() {
        PDDocument().use { document ->
            val type3 = font(document, COSName.TYPE3, cmap(2, coloros))
            document.page(mapOf("F1" to type3), "BT /F1 8 Tf <419142> Tj ET")
            document.page(mapOf("F1" to type3), "BT /F1 8 Tf <419142> Tj ET")
            // Page 1 drew "A-B", page 2 "A+B": the glyph is not decided. zh-CN: 第 1 页画 "A-B", 第 2 页画 "A+B": 无法判定.
            assertEquals(0, ToUnicodeRepair.repair(document, listOf(listOf("A-B"), listOf("A+B")), { _, _ -> false }))
            assertEquals(Char(0).toString(), mapping(type3, 0x91))
            // Each page is read against its own texts: without the texts of page 2, page 1 decides.
            // zh-CN: 每页只对照本页的文字: 没有第 2 页的文字时由第 1 页决定.
            assertEquals(1, ToUnicodeRepair.repair(document, listOf(listOf("A-B"), emptyList()), { _, _ -> false }))
            assertEquals("-", mapping(type3, 0x91))
        }
    }

    @Test
    fun `a page without unknown glyphs is not parsed and nothing changes`() {
        PDDocument().use { document ->
            val type3 = font(document, COSName.TYPE3, cmap(2, mapOf(0x41 to "A", 0x2D to "-", 0x42 to "B")))
            document.page(mapOf("F1" to type3), "BT /F1 8 Tf <412D42> Tj ET")
            assertEquals(0, ToUnicodeRepair.repair(document, listOf(listOf("A-B")), { _, _ -> false }))
        }
    }

}

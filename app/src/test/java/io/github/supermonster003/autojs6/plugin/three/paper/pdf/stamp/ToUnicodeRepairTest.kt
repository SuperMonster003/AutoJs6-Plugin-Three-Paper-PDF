package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.fontbox.cmap.CMapParser
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
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
            val changed = ToUnicodeRepair.repair(document, points("$ordinal 1 $page $script"), leafIsPage)
            assertEquals(2, changed)
            assertEquals(page, mapping(type3, 0x21))
            assertEquals(ordinal, mapping(type3, 0xE1))
            assertEquals(script, mapping(type0, 0x0101))
            assertEquals("1", mapping(type0, 0x0016))
            assertEquals(" ", mapping(type0, 0x0005))
        }
    }

}

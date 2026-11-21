package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GlyphAlignment] gives an unknown glyph between known ones the character drawn at its place (roadmap Q322), and
 * leaves glyphs alone that fit nowhere, fit with different readings or stand for several characters.
 * zh-CN: [GlyphAlignment] 给夹在已知字形之间的未知字形取其位置上所画的字符 (路线图 Q322); 对不上任何位置, 两处读法不同或代表多个
 * 字符的字形保持不变.
 */
class GlyphAlignmentTest {

    /** Glyphs from [text], a `?` standing for an unknown glyph. zh-CN: 由 [text] 得到的字形, `?` 表示未知字形. */
    private fun glyphs(text: String): List<String?> = text.map { if (it == '?') null else it.toString() }

    private fun texts(vararg texts: String): List<IntArray> = texts.map { it.codePoints().toArray() }

    @Test
    fun `the hyphen of a contextual alternate is read from the drawn text`() {
        assertEquals(mapOf(1 to '-'.code), GlyphAlignment.place(glyphs("3?Paper"), texts("3-Paper PDF")))
        assertEquals(mapOf(1 to '-'.code, 5 to '-'.code), GlyphAlignment.place(glyphs("A?B x?y"), texts("note", "A-B x-y")))
        // The same text drawn twice reads the same. zh-CN: 同一文字画两次读法相同.
        assertEquals(mapOf(1 to '-'.code), GlyphAlignment.place(glyphs("A?B"), texts("A-B and A-B")))
    }

    @Test
    fun `no-break spaces of stamps match the spaces of the text`() {
        val noBreak = StampFonts.NO_BREAK_SPACE.toString()
        assertEquals(mapOf(3 to '-'.code), GlyphAlignment.place(listOf("a", noBreak, "b", null, "C"), texts("a b-C")))
    }

    @Test
    fun `different readings, no fit and glyphs without known neighbours stay`() {
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("A?B"), texts("A-B", "A+B")))
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("A?B"), texts("AB", "A--B")))
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("?nd"), texts("find")))
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("A??B"), texts("A--B")))
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("AB"), texts("AB")))
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("A?B"), emptyList()))
    }

    @Test
    fun `a ligature inside a run fits nowhere`() {
        // "office" drawn with an ffi ligature: four glyphs for six characters. zh-CN: 用 ffi 连字画 "office": 六个字符四个字形.
        assertEquals(emptyMap<Int, Int>(), GlyphAlignment.place(glyphs("o?ce"), texts("office")))
    }

    @Test
    fun `a known glyph may stand for several characters`() {
        assertEquals(mapOf(2 to '-'.code), GlyphAlignment.place(listOf("x", "fi", null, "B"), texts("xfi-B")))
    }

}

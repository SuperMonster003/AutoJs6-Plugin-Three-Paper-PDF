package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

/**
 * Places the glyphs of one text object on the text that was drawn (roadmap Q322 (a)): a font may draw a character with
 * a contextual alternate that no code point maps to (the system font of ColorOS 15 draws the hyphen of "3-Paper" and
 * "A-B" so), and Skia then writes U+0000 for that glyph, which text extraction and search return. The glyphs are given
 * as the text each maps to, null where it is unknown; the run fits a drawn text where its known glyphs read that text
 * exactly and every unknown glyph covers one code point, so a glyph that stands for several characters (a ligature)
 * fits nowhere and stays as it is. Only an unknown glyph between two known glyphs of the run is placed, and only when
 * every place where the run fits reads the same character there; spaces of any kind count as one character class
 * (stamps lay spaces out as no-break spaces).
 * zh-CN: 把一个文本对象的字形放回所画的文字上 (路线图 Q322 (a)): 字体可能用没有码位对应的上下文替代字形画某个字符 (ColorOS 15 的系统
 * 字体就这样画 "3-Paper" 与 "A-B" 中的连字符), Skia 随之为该字形写入 U+0000, 文本提取与搜索得到的就是它. 字形以各自映射到的文字
 * 给出, 未知的为 null; 已知字形与某段所画文字逐字相同, 且每个未知字形恰好对应一个码位时, 这段字形就对上了该文字, 因此代表多个字符
 * 的字形 (连字) 对不上任何位置, 保持不变. 只放回两侧都是已知字形的未知字形, 而且要求所有对得上的位置在该处都是同一个字符; 各种空格
 * 视为同一类字符 (印章把空格排成不换行空格).
 */
internal object GlyphAlignment {

    /**
     * The code point each placeable unknown glyph of [glyphs] stands for, by glyph index, as read from [texts] (the
     * code points of the texts drawn on the page); empty when the run fits nowhere, places differently at two spots, or
     * has no unknown glyph between known ones.
     * zh-CN: 从 [texts] (该页所画文字的码位) 读出 [glyphs] 中每个可放回的未知字形代表的码位, 以字形序号为键; 字形段对不上任何位置,
     * 两处对上但读法不同, 或者没有夹在已知字形之间的未知字形时为空.
     */
    fun place(glyphs: List<String?>, texts: List<IntArray>): Map<Int, Int> {
        val placeable = glyphs.indices.filter { index ->
            glyphs[index] == null && index > 0 && index < glyphs.size - 1 && glyphs[index - 1] != null && glyphs[index + 1] != null
        }
        if (placeable.isEmpty()) return emptyMap()
        val pattern = glyphs.map { it?.codePoints()?.toArray() }
        val length = pattern.sumOf { it?.size ?: 1 }
        var found: Map<Int, Int>? = null
        for (text in texts) {
            for (start in 0..text.size - length) {
                val read = read(pattern, text, start) ?: continue
                val placed = placeable.associateWith { read[it] }
                if (found == null) found = placed else if (found != placed) return emptyMap()
            }
        }
        return found ?: emptyMap()
    }

    /** The code point under each glyph when [pattern] fits [text] at [start], else null. zh-CN: [pattern] 在 [start] 处对上 [text] 时每个字形下的码位, 否则为 null. */
    private fun read(pattern: List<IntArray?>, text: IntArray, start: Int): IntArray? {
        val under = IntArray(pattern.size)
        var at = start
        for (index in pattern.indices) {
            val points = pattern[index]
            if (points == null) {
                under[index] = text[at++]
                continue
            }
            for (point in points) if (!same(point, text[at++])) return null
            under[index] = points[0]
        }
        return under
    }

    private fun same(a: Int, b: Int): Boolean = a == b || (space(a) && space(b))

    private fun space(codePoint: Int): Boolean = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

}

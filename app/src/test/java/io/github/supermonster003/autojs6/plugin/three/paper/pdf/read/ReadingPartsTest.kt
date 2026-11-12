package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure parts of `getText` and `search` (roadmap P2.2): chunking, keyword matching, hit paging, option ceilings and
 * the rectangles of glyphs on rotated pages.
 * zh-CN: `getText` 与 `search` 的纯逻辑部分 (路线图 P2.2): 分块, 关键字匹配, 命中分页, 选项上限, 以及旋转页上字形的矩形.
 */
class ReadingPartsTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun failureOf(block: () -> Unit): String = assertThrows(PdfFailure::class.java) { block() }.code

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.texts() = (this[PdfContract.KEY_TEXTS] as Array<String>).toList()

    private fun Map<String, Any?>.numbers() = (this[PdfContract.KEY_PAGE_NUMBERS] as IntArray).toList()

    @Test
    fun `whole pages fit and the answer ends the text`() {
        val chunker = TextChunker(offset = 0, maxChars = 100)
        assertTrue(chunker.accept(1, "ab\n"))
        assertTrue(chunker.accept(2, ""))
        assertTrue(chunker.accept(3, "cd\n"))
        val answer = chunker.answer()
        assertEquals(listOf("ab\n", "", "cd\n"), answer.texts())
        assertEquals(listOf(1, 2, 3), answer.numbers())
        assertEquals(false, answer[PdfContract.KEY_HAS_MORE])
        assertFalse(answer.containsKey(PdfContract.KEY_NEXT_PAGE))
        assertFalse(answer.containsKey(PdfContract.KEY_NEXT_OFFSET))
    }

    @Test
    fun `a full answer continues on the next page or inside the page`() {
        val atPage = TextChunker(offset = 0, maxChars = 4)
        assertTrue(atPage.accept(1, "abc\n"))
        assertFalse(atPage.accept(2, "de\n"))
        atPage.answer().let { answer ->
            assertEquals(listOf("abc\n"), answer.texts())
            assertEquals(true, answer[PdfContract.KEY_HAS_MORE])
            assertEquals(2, answer[PdfContract.KEY_NEXT_PAGE])
            assertEquals(0, answer[PdfContract.KEY_NEXT_OFFSET])
        }
        // Repeating with page = nextPage and offset = nextOffset reads the rest. zh-CN: 以 page = nextPage, offset = nextOffset 重复请求即可读完.
        val pieces = ArrayList<String>()
        var offset = 0
        do {
            val chunker = TextChunker(offset, maxChars = 3)
            chunker.accept(1, "abcdefg\n")
            val answer = chunker.answer()
            pieces += answer.texts()
            offset = answer[PdfContract.KEY_NEXT_OFFSET] as Int? ?: -1
        } while (answer[PdfContract.KEY_HAS_MORE] == true)
        assertEquals(listOf("abc", "def", "g\n"), pieces)
    }

    @Test
    fun `chunks never split a surrogate pair and always make progress`() {
        val text = "a😀b\n"
        val first = TextChunker(0, maxChars = 2).apply { accept(1, text) }.answer()
        assertEquals(listOf("a"), first.texts())
        assertEquals(1, first[PdfContract.KEY_NEXT_OFFSET])
        val second = TextChunker(1, maxChars = 1).apply { accept(1, text) }.answer()
        assertEquals(listOf("😀"), second.texts())
        assertEquals(3, second[PdfContract.KEY_NEXT_OFFSET])
    }

    @Test
    fun `offset beyond the page fails and the byte budget ends an answer`() {
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, failureOf { TextChunker(5, 10).accept(1, "abc\n") })
        val atEnd = TextChunker(4, 10)
        assertTrue(atEnd.accept(1, "abc\n"))
        assertEquals(listOf(""), atEnd.answer().texts())
        val budget = TextChunker(0, maxChars = 1000, budgetBytes = TextChunker.ENTRY_BYTES + 6 + 4)
        assertTrue(budget.accept(1, "abc"))
        assertFalse(budget.accept(2, "def"))
        assertEquals(2, budget.answer()[PdfContract.KEY_NEXT_PAGE])
        assertEquals(0, budget.answer()[PdfContract.KEY_NEXT_OFFSET])
    }

    @Test
    fun `text and search options have their defaults and ceilings`() {
        assertEquals(PdfContract.MAX_TEXT_CHARS_PER_CALL, TextReader.maxChars(options()))
        assertEquals(PdfContract.MAX_TEXT_CHARS_PER_CALL, TextReader.maxChars(options(PdfContract.KEY_MAX_CHARS to Int.MAX_VALUE)))
        assertEquals(7, TextReader.maxChars(options(PdfContract.KEY_MAX_CHARS to 7)))
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, failureOf { TextReader.maxChars(options(PdfContract.KEY_MAX_CHARS to 0)) })

        assertEquals(PdfContract.DEFAULT_SEARCH_LIMIT, SearchReader.limit(options()))
        assertEquals(PdfContract.DEFAULT_SEARCH_LIMIT, SearchReader.limit(options(PdfContract.KEY_LIMIT to 0)))
        assertEquals(PdfContract.MAX_SEARCH_HITS, SearchReader.limit(options(PdfContract.KEY_LIMIT to 5000)))
        assertEquals(3, SearchReader.limit(options(PdfContract.KEY_LIMIT to 3)))
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, failureOf { SearchReader.limit(options(PdfContract.KEY_LIMIT to -1)) })

        listOf(null, "", "   ", "x".repeat(PdfContract.MAX_KEYWORD_LENGTH + 1)).forEach { keyword ->
            assertEquals("$keyword", PdfErrorCodes.INVALID_ARGUMENT, failureOf { SearchReader.matcher(options(PdfContract.KEY_KEYWORD to keyword)) })
        }
        SearchReader.matcher(options(PdfContract.KEY_KEYWORD to "x".repeat(PdfContract.MAX_KEYWORD_LENGTH)))
    }

    @Test
    fun `keywords match case folded, without overlaps and across line breaks`() {
        fun find(keyword: String, text: String, caseSensitive: Boolean = false, wholeWord: Boolean = false) =
            KeywordMatcher(keyword, caseSensitive, wholeWord).find(text).map { it.first }.toList()
        assertEquals(listOf(0, 7, 14), find("needle", "Needle needle NEEDLE"))
        assertEquals(listOf(7), find("needle", "Needle needle NEEDLE", caseSensitive = true))
        assertEquals(listOf(0, 2), find("aa", "aaaa"))
        assertEquals(listOf(6), find("brown fox", "quick brown\nfox"))
        assertEquals(listOf(0, 7, 11, 16), find("cat", "cat concat cats cat."))
        assertEquals(listOf(0, 16), find("cat", "cat concat cats cat.", wholeWord = true))
        // Ends that are not letters or digits need no boundary. zh-CN: 不是字母或数字的一端不要求边界.
        assertEquals(listOf(3), find("(x)", "abc(x)d", wholeWord = true))
        assertEquals(emptyList<Int>(), find("needle", "need"))
    }

    @Test
    fun `hits skip the offset, stop at the limit and report the next offset`() {
        var built = 0
        val hits = HitCollector(offset = 1, limit = 2)
        val accepted = (1..5).map { index -> hits.accept { built++; JSONObject().put(PdfContract.FIELD_PAGE, index) } }
        assertEquals(listOf(true, true, true, false, false), accepted)
        assertEquals(2, built)
        val answer = hits.answer()
        val items = JSONArray(answer[PdfContract.KEY_ITEMS] as String)
        assertEquals(listOf(2, 3), List(items.length()) { items.getJSONObject(it).getInt(PdfContract.FIELD_PAGE) })
        assertEquals(true, answer[PdfContract.KEY_HAS_MORE])
        assertEquals(3, answer[PdfContract.KEY_NEXT_OFFSET])

        val all = HitCollector(offset = 0, limit = 2).apply { accept { JSONObject() }; accept { JSONObject() } }.answer()
        assertEquals(false, all[PdfContract.KEY_HAS_MORE])
        assertNull(all[PdfContract.KEY_NEXT_OFFSET])
    }

    /** "Rotate" in 18 pt Helvetica-Bold at (72, 722): 56 pt wide, ascent 0.718 and descent 0.207 of the size. zh-CN: 18 pt Helvetica-Bold 的 "Rotate" 位于 (72, 722): 宽 56 pt, 上伸与下伸为字号的 0.718 与 0.207. */
    private val heading = Glyph(x = 72.0, y = 722.0, ux = 1.0, uy = 0.0, advance = 56.0, ascent = 0.718 * 18, descent = 0.207 * 18)

    @Test
    fun `glyph rectangles follow the page rotation`() {
        fun rect(rotation: Int) = DisplayBounds(PageFrame(595.0, 842.0, rotation)).add(heading).toArray()
        assertArrayEquals(doubleArrayOf(72.0, 107.08, 128.0, 123.73), rect(0), 1e-9)
        assertArrayEquals(doubleArrayOf(718.27, 72.0, 734.92, 128.0), rect(90), 1e-9)
        assertArrayEquals(doubleArrayOf(467.0, 718.27, 523.0, 734.92), rect(180), 1e-9)
        assertArrayEquals(doubleArrayOf(107.08, 467.0, 123.73, 523.0), rect(270), 1e-9)
        assertEquals(842.0, PageFrame(595.0, 842.0, 90).displayWidth, 0.0)
        assertEquals(595.0, PageFrame(595.0, 842.0, 270).displayHeight, 0.0)
        // Boxes are clamped to the displayed page. zh-CN: 矩形限制在显示页面内.
        val outside = DisplayBounds(PageFrame(100.0, 100.0, 0)).add(heading.copy(x = 90.0, y = 95.0)).toArray()
        assertArrayEquals(doubleArrayOf(90.0, 0.0, 100.0, 8.73), outside, 1e-9)
        assertTrue(DisplayBounds(PageFrame(1.0, 1.0, 0)).isEmpty)
    }

    private fun position(unicode: String, x: Float = 72f, endX: Float = 128f, font: Float = 18f) =
        TextPosition(0, 595f, 842f, Matrix(font, 0f, 0f, font, x, 722f), endX, 722f, font, endX - x, 5f, unicode, intArrayOf(1), null, font, font.toInt())

    @Test
    fun `glyph boxes come from the text matrix and the end point`() {
        val glyph = SearchReader.glyphOf(position("Rotate"))
        assertEquals(heading.x, glyph.x, 1e-6)
        assertEquals(heading.y, glyph.y, 1e-6)
        assertEquals(1.0, glyph.ux, 1e-9)
        assertEquals(0.0, glyph.uy, 1e-9)
        assertEquals(56.0, glyph.advance, 1e-4)
        assertEquals(heading.ascent, glyph.ascent, 1e-4)
        assertEquals(heading.descent, glyph.descent, 1e-4)
    }

    @Test
    fun `characters map to their glyphs, evenly when the text was normalized`() {
        val ab = position("ab")
        val c = position("c")
        assertEquals(listOf(ab, ab, c), SearchReader.ownersOf("abc", listOf(ab, c)))
        assertEquals(listOf(ab, ab, c, c), SearchReader.ownersOf("abcd", listOf(ab, c)))
        assertEquals(listOf(null, null), SearchReader.ownersOf("  ", emptyList()))
    }

    @Test
    fun `a hit has one rectangle per line and its context`() {
        val text = "x".repeat(50) + " brown\nfox " + "y".repeat(50)
        val brown = position("brown", x = 72f, endX = 112f, font = 10f)
        val fox = position("fox", x = 72f, endX = 90f, font = 10f)
        val owners = List(50) { null } + null + List(5) { brown } + null + List(3) { fox } + List(51) { null }
        val range = KeywordMatcher("brown fox", caseSensitive = false, wholeWord = false).find(text).single()
        val item = SearchReader.item(4, range, text, owners, PageFrame(595.0, 842.0, 0))
        assertEquals(4, item.getInt(PdfContract.FIELD_PAGE))
        val rects = item.getJSONArray(PdfContract.FIELD_RECTS)
        assertEquals(2, rects.length())
        assertEquals(72.0, rects.getJSONArray(0).getDouble(0), 1e-9)
        assertEquals(112.0, rects.getJSONArray(0).getDouble(2), 1e-9)
        assertEquals(90.0, rects.getJSONArray(1).getDouble(2), 1e-9)
        // 40 characters on each side, the line break as a blank. zh-CN: 两侧各 40 个字符, 换行写作空格.
        assertEquals("x".repeat(39) + " brown fox " + "y".repeat(39), item.getString(PdfContract.FIELD_CONTEXT))
    }

}

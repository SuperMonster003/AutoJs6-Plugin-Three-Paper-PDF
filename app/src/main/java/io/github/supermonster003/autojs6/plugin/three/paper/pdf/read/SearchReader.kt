package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.TextPosition
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The `search` answer (protocol "Methods", roadmap P2.2): hits of `keyword` in the reading order text of the selected
 * pages, `offset` hits skipped and at most `limit` returned, each with its page, one rectangle per line it spans and up
 * to [CONTEXT_CHARS] characters of text on each side. A blank in the keyword also matches a line break. Like
 * `getText`, the text permission bits are not enforced (appendix D Q11).
 * zh-CN: `search` 的应答 (协议 "Methods" 一节, 路线图 P2.2): 在所选页按阅读顺序抽取的文本中查找 `keyword`, 跳过 `offset` 个命中,
 * 至多返回 `limit` 个, 每个命中带页码, 所跨每行一个矩形, 以及两侧各至多 [CONTEXT_CHARS] 个字符的上下文. 关键字中的空白也能匹配
 * 换行. 与 `getText` 一样, 不执行文本相关的权限位 (附录 D Q11).
 */
internal object SearchReader {

    const val CONTEXT_CHARS = 40

    fun read(document: PDDocument, options: PdfOptions, stop: StopSignal, budgetChars: Int = JsonItems.BUDGET_CHARS): Map<String, Any?> {
        val matcher = matcher(options)
        val pages = options.pages(document.numberOfPages)
        val offset = options.int(PdfContract.KEY_OFFSET, 0..Int.MAX_VALUE, 0)
        val limit = limit(options)
        val hits = HitCollector(offset, limit, budgetChars)
        object : PageSweep(stop, PdfContract.LAYOUT_READING) {

            private val text = StringBuilder()
            private val owners = ArrayList<TextPosition?>()

            override fun startPage(page: PDPage) {
                text.setLength(0)
                owners.clear()
                super.startPage(page)
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) = append(text, textPositions)

            override fun writeWordSeparator() = append(" ", emptyList())

            override fun writeLineSeparator() = append("\n", emptyList())

            override fun onPage(number: Int, page: PDPage?): Boolean {
                if (page == null || text.isEmpty()) return true
                val frame = frameOf(page)
                for (range in matcher.find(text)) {
                    if (!hits.accept { item(number, range, text, owners, frame) }) return false
                }
                return true
            }

            private fun append(value: String, positions: List<TextPosition>) {
                text.append(value)
                ownersOf(value, positions).forEach { owners += it }
            }

        }.sweep(document, pages)
        return hits.answer()
    }

    fun matcher(options: PdfOptions): KeywordMatcher {
        val keyword = options.string(PdfContract.KEY_KEYWORD) ?: throw PdfFailure.invalid("request carries no ${PdfContract.KEY_KEYWORD}")
        val range = PdfContract.MIN_KEYWORD_LENGTH..PdfContract.MAX_KEYWORD_LENGTH
        if (keyword.length !in range) {
            throw PdfFailure.invalid("${PdfContract.KEY_KEYWORD} has ${keyword.length} characters, outside ${range.first}..${range.last}")
        }
        if (keyword.isBlank()) throw PdfFailure.invalid("${PdfContract.KEY_KEYWORD} is blank")
        return KeywordMatcher(
            keyword,
            caseSensitive = options.boolean(PdfContract.KEY_CASE_SENSITIVE, false),
            wholeWord = options.boolean(PdfContract.KEY_WHOLE_WORD, false),
        )
    }

    /** `limit`: 0 or absent means `DEFAULT_SEARCH_LIMIT`, larger than `MAX_SEARCH_HITS` is cut to it. zh-CN: `limit`: 0 或缺省为 `DEFAULT_SEARCH_LIMIT`, 大于 `MAX_SEARCH_HITS` 时按它截断. */
    fun limit(options: PdfOptions): Int {
        val limit = options.int(PdfContract.KEY_LIMIT, 0..Int.MAX_VALUE, 0)
        return if (limit == 0) PdfContract.DEFAULT_SEARCH_LIMIT else limit.coerceAtMost(PdfContract.MAX_SEARCH_HITS)
    }

    fun frameOf(page: PDPage): PageFrame {
        val box = page.cropBox
        val rotation = Math.floorMod(page.rotation, 360).let { if (it % 90 == 0) it else 0 }
        return PageFrame(box.width.toDouble(), box.height.toDouble(), rotation)
    }

    /**
     * The glyph each character of [value] came from: one per character when the Unicode texts of [positions] add up to
     * [value] (the usual case), else spread evenly (ligatures and other normalized words); `null` for separators.
     * zh-CN: [value] 中每个字符来自的字形: [positions] 的 Unicode 文本拼起来正好是 [value] 时 (通常如此) 逐字符对应, 否则均匀分摊
     * (连字等经过规范化的词); 分隔符为 `null`.
     */
    fun ownersOf(value: String, positions: List<TextPosition>): List<TextPosition?> {
        if (positions.isEmpty()) return List(value.length) { null }
        if (positions.sumOf { it.unicode?.length ?: 0 } == value.length) {
            return positions.flatMap { position -> List(position.unicode?.length ?: 0) { position } }
        }
        return List(value.length) { index -> positions[(index.toLong() * positions.size / value.length).toInt()] }
    }

    /** One hit as a JSON item. zh-CN: 一个命中对应的 JSON 条目. */
    fun item(page: Int, range: IntRange, text: CharSequence, owners: List<TextPosition?>, frame: PageFrame): JSONObject {
        val rects = JSONArray()
        var bounds = DisplayBounds(frame)
        val seen = IdentityHashMap<TextPosition, Unit>()
        fun flush() {
            if (!bounds.isEmpty) rects.put(JSONArray(bounds.toArray().toList()))
            bounds = DisplayBounds(frame)
        }
        for (index in range) {
            if (text[index] == '\n') flush()
            val owner = owners.getOrNull(index) ?: continue
            if (seen.put(owner, Unit) == null) bounds.add(glyphOf(owner))
        }
        flush()
        return JSONObject()
            .put(PdfContract.FIELD_PAGE, page)
            .put(PdfContract.FIELD_RECTS, rects)
            .put(PdfContract.FIELD_CONTEXT, context(text, range))
    }

    /**
     * Up to [CONTEXT_CHARS] characters before and after [range] with the hit, line breaks as blanks, never cutting a
     * surrogate pair.
     * zh-CN: 命中及其前后各至多 [CONTEXT_CHARS] 个字符, 换行写作空格, 不会截断代理对.
     */
    fun context(text: CharSequence, range: IntRange): String {
        var start = (range.first - CONTEXT_CHARS).coerceAtLeast(0)
        var end = (range.last + 1 + CONTEXT_CHARS).coerceAtMost(text.length)
        if (start > 0 && Character.isLowSurrogate(text[start])) start++
        if (end < text.length && Character.isLowSurrogate(text[end])) end--
        return text.substring(start, end).replace('\n', ' ').trim()
    }

    /**
     * The glyph box of [position] in unrotated user space relative to the crop box: the baseline from the text matrix
     * to the end point, ascent and descent from the font descriptor (Helvetica-like values when the font has none).
     * zh-CN: [position] 在相对裁剪框的未旋转用户空间中的字形框: 基线从文本矩阵原点到终点, 上伸与下伸取自字体描述符 (字体没有时按
     * Helvetica 一类的值).
     */
    fun glyphOf(position: TextPosition): Glyph {
        val matrix = position.textMatrix
        val x = matrix.translateX.toDouble()
        val y = matrix.translateY.toDouble()
        var dx = position.endX - x
        var dy = position.endY - y
        var advance = hypot(dx, dy)
        if (advance < 1e-6) {
            dx = matrix.scaleX.toDouble()
            dy = matrix.shearY.toDouble()
            advance = 0.0
        }
        val length = hypot(dx, dy).takeIf { it > 1e-9 } ?: 1.0
        val size = hypot(matrix.shearX.toDouble(), matrix.scaleY.toDouble()).takeIf { it > 1e-9 } ?: abs(position.fontSizeInPt.toDouble())
        val descriptor = runCatching { position.font?.fontDescriptor }.getOrNull()
        val ascent = descriptor?.ascent?.toDouble()?.div(1000)?.takeIf { it in 0.3..1.5 } ?: DEFAULT_ASCENT
        val descent = descriptor?.descent?.toDouble()?.let { abs(it) / 1000 }?.takeIf { it <= 0.8 } ?: DEFAULT_DESCENT
        return Glyph(x, y, dx / length, dy / length, advance, ascent * size, descent * size)
    }

    private const val DEFAULT_ASCENT = 0.718

    private const val DEFAULT_DESCENT = 0.207

}

/**
 * Finds [keyword] in page text without overlaps: case folded unless [caseSensitive], and with [wholeWord] only where
 * no letter or digit touches a letter or digit at the ends of the keyword. A blank in the keyword matches a blank or a
 * line break.
 * zh-CN: 在页文本中查找 [keyword], 命中互不重叠: 除非 [caseSensitive], 否则忽略大小写; [wholeWord] 时, 关键字两端的字母或数字
 * 不得紧挨字母或数字. 关键字中的空白匹配空白或换行.
 */
internal class KeywordMatcher(private val keyword: String, private val caseSensitive: Boolean, private val wholeWord: Boolean) {

    fun find(text: CharSequence): Sequence<IntRange> = sequence {
        var from = 0
        val last = text.length - keyword.length
        while (from <= last) {
            if (matchesAt(text, from) && boundaryAt(text, from)) {
                yield(from until from + keyword.length)
                from += keyword.length
            } else {
                from++
            }
        }
    }

    private fun matchesAt(text: CharSequence, start: Int): Boolean {
        for (index in keyword.indices) {
            if (!same(keyword[index], text[start + index])) return false
        }
        return true
    }

    private fun same(expected: Char, actual: Char): Boolean = when {
        expected == actual -> true
        expected.isWhitespace() -> actual.isWhitespace()
        caseSensitive -> false
        else -> expected.uppercaseChar() == actual.uppercaseChar() || expected.lowercaseChar() == actual.lowercaseChar()
    }

    private fun boundaryAt(text: CharSequence, start: Int): Boolean {
        if (!wholeWord) return true
        val end = start + keyword.length
        if (isWordChar(keyword.first()) && start > 0 && isWordChar(text[start - 1])) return false
        if (isWordChar(keyword.last()) && end < text.length && isWordChar(text[end])) return false
        return true
    }

    private fun isWordChar(char: Char): Boolean = Character.isLetterOrDigit(char) || char == '_'

}

/**
 * Collects hits after skipping [offset] of them, up to [limit] or the item budget; `hasMore` and `nextOffset` are
 * answered when a further hit exists.
 * zh-CN: 跳过 [offset] 个命中后收集命中, 直到 [limit] 个或用完条目预算; 还有后续命中时在应答中给出 `hasMore` 与 `nextOffset`.
 */
internal class HitCollector(private val offset: Int, private val limit: Int, budgetChars: Int = JsonItems.BUDGET_CHARS) {

    private val items = JsonItems(budgetChars)
    private var skipped = 0
    private var hasMore = false

    /** Takes the next hit, built by [item] only when kept; false once the answer is full. zh-CN: 接收下一个命中, 仅在保留时调用 [item] 构造; 应答已满时返回 false. */
    fun accept(item: () -> JSONObject): Boolean {
        if (skipped < offset) {
            skipped++
            return true
        }
        if (items.count >= limit || !items.add(item())) {
            hasMore = true
            return false
        }
        return true
    }

    fun answer(): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_ITEMS, items.toString())
        put(PdfContract.KEY_HAS_MORE, hasMore)
        if (hasMore) put(PdfContract.KEY_NEXT_OFFSET, offset + items.count)
    }

}

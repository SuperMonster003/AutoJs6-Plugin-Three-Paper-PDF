package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import java.io.StringWriter

/**
 * The `getText` answer (protocol "Methods", roadmap P2.2): the text of the selected pages from `page` (default the
 * first selected page) and character `offset` of it, at most `maxChars` characters per answer. Each page text keeps
 * its line breaks and ends with one unless the page has no text. The text permission bits are not enforced (appendix D
 * Q11): a document that opens can be read.
 * zh-CN: `getText` 的应答 (协议 "Methods" 一节, 路线图 P2.2): 所选页从 `page` (默认为第一个所选页) 的第 `offset` 个字符起的文本,
 * 每次应答至多 `maxChars` 个字符. 每页文本保留换行, 有文本的页以一个换行结尾. 不执行文本相关的权限位 (附录 D Q11): 能打开的文档
 * 就能读取.
 */
internal object TextReader {

    fun read(document: PDDocument, options: PdfOptions, stop: StopSignal): Map<String, Any?> {
        val count = document.numberOfPages
        val pages = options.pages(count)
        if (pages.isEmpty()) return TextChunker(0, maxChars(options)).answer()
        val page = options.page(count, default = pages.first())
        val start = pages.indexOf(page)
        if (start < 0) throw PdfFailure.invalid("${PdfContract.KEY_PAGE} $page is not one of the ${PdfContract.KEY_PAGES} ${PageRanges.format(pages)}")
        val offset = options.int(PdfContract.KEY_OFFSET, 0..Int.MAX_VALUE, 0)
        val maxChars = maxChars(options)
        val layout = checkNotNull(options.choice(PdfContract.KEY_LAYOUT, PdfContract.LAYOUTS, PdfContract.LAYOUT_READING))
        val chunker = TextChunker(offset, maxChars)
        val writer = StringWriter()
        object : PageSweep(stop, layout) {
            override fun onPage(number: Int, page: PDPage?): Boolean {
                val text = if (page == null) "" else pageText(writer)
                writer.buffer.setLength(0)
                return chunker.accept(number, text)
            }
        }.sweep(document, pages.copyOfRange(start, pages.size), writer)
        return chunker.answer()
    }

    /**
     * `maxChars`: 1 or more, absent means `MAX_TEXT_CHARS_PER_CALL`, larger values are cut to it (the answer is at most
     * that long either way).
     * zh-CN: `maxChars`: 至少为 1, 缺省为 `MAX_TEXT_CHARS_PER_CALL`, 更大的值按它截断 (无论如何应答都不会更长).
     */
    fun maxChars(options: PdfOptions): Int =
        options.int(PdfContract.KEY_MAX_CHARS, 1..Int.MAX_VALUE, PdfContract.MAX_TEXT_CHARS_PER_CALL).coerceAtMost(PdfContract.MAX_TEXT_CHARS_PER_CALL)

    /** The text written for one page, ending with exactly one line break when not empty. zh-CN: 为一页写出的文本, 非空时恰以一个换行结尾. */
    fun pageText(writer: StringWriter): String {
        val text = writer.buffer.trimEnd('\n', '\r')
        return if (text.isEmpty()) "" else "$text\n"
    }

}

/**
 * Cuts page texts into one `getText` answer: the first page from [offset], then whole pages until [maxChars]
 * characters or [budgetBytes] of answer are used; a page that does not fit is cut (never inside a surrogate pair) and
 * `nextPage` / `nextOffset` say where to continue. Every answer holds at least one entry or ends the text.
 * zh-CN: 把页文本切成一次 `getText` 应答: 第一页从 [offset] 起, 之后按整页追加, 直到用完 [maxChars] 个字符或 [budgetBytes] 的应答
 * 空间; 放不下的页被截断 (不会截在代理对中间), 并由 `nextPage` / `nextOffset` 指明继续的位置. 每次应答至少含一个条目, 或者文本已结束.
 */
internal class TextChunker(private val offset: Int, private val maxChars: Int, private val budgetBytes: Int = BUDGET_BYTES) {

    private val texts = ArrayList<String>()
    private val numbers = ArrayList<Int>()
    private var chars = 0
    private var bytes = 0
    private var nextPage = 0
    private var nextOffset = 0

    /** Takes the text of the next selected page; false once the answer is full. zh-CN: 接收下一个所选页的文本; 应答已满时返回 false. */
    fun accept(page: Int, text: String): Boolean {
        val from = if (texts.isEmpty()) {
            if (offset > text.length) {
                throw PdfFailure.invalid("${PdfContract.KEY_OFFSET} $offset is beyond the ${text.length} characters of page $page")
            }
            offset
        } else 0
        var end = text.length
        val room = maxChars - chars
        if (end - from > room) {
            end = from + room
            if (end > from && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            // A first entry always makes progress. zh-CN: 第一个条目总能推进.
            if (end == from && texts.isEmpty()) end = text.offsetByCodePoints(from, 1)
        }
        val cost = ENTRY_BYTES + (end - from) * 2
        if (texts.isNotEmpty() && ((end == from && from < text.length) || bytes + cost > budgetBytes)) {
            nextPage = page
            nextOffset = from
            return false
        }
        texts += text.substring(from, end)
        numbers += page
        chars += end - from
        bytes += cost
        if (end < text.length) {
            nextPage = page
            nextOffset = end
            return false
        }
        return true
    }

    fun answer(): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_TEXTS, texts.toTypedArray())
        put(PdfContract.KEY_PAGE_NUMBERS, numbers.toIntArray())
        put(PdfContract.KEY_HAS_MORE, nextPage > 0)
        if (nextPage > 0) {
            put(PdfContract.KEY_NEXT_PAGE, nextPage)
            put(PdfContract.KEY_NEXT_OFFSET, nextOffset)
        }
    }

    companion object {

        /** Parcel bytes of one entry besides its characters. zh-CN: 一个条目除字符以外占用的 Parcel 字节数. */
        const val ENTRY_BYTES = 16

        /** Bytes of the `texts` and `pageNumbers` values of one answer. zh-CN: 一次应答中 `texts` 与 `pageNumbers` 的字节数. */
        const val BUDGET_BYTES = 700_000

    }

}

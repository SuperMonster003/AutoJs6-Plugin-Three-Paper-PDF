package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import org.autojs.plugin.pdf.api.PdfContract
import java.io.Writer
import java.util.BitSet

/**
 * One pass of PDFBox text extraction over the selected pages of a document, page by page in ascending order:
 * [onPage] sees every selected page (with `null` for a page without content) and ends the pass early by answering
 * false. Lines end with `\n` and words are separated by a space. `reading` sorts the text by position (needed for
 * rotated pages and text drawn out of order, roadmap P0.3); `raw` keeps the order of the content stream. The stop
 * signal is checked every 256 glyphs, so closing the document ends a long page with `SESSION_CLOSED`.
 * zh-CN: 对文档所选页做一次 PDFBox 文本抽取, 按页码升序逐页进行: [onPage] 收到每个所选页 (没有内容的页为 `null`), 返回 false 时
 * 提前结束. 行以 `\n` 结尾, 词以空格分隔. `reading` 按位置排序文本 (旋转页与乱序绘制的文本需要它, 路线图 P0.3); `raw` 保持内容流
 * 的顺序. 每 256 个字形检查一次停止信号, 因此关闭文档会以 `SESSION_CLOSED` 结束一个很长的页.
 */
internal abstract class PageSweep(private val stop: StopSignal, layout: String) : PDFTextStripper() {

    private object Finished : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private var selected = BitSet()
    private var expected = IntArray(0)
    private var next = 0
    private var glyphs = 0

    init {
        setSortByPosition(layout == PdfContract.LAYOUT_READING)
        setLineSeparator("\n")
        setWordSeparator(" ")
        setParagraphStart("")
        setParagraphEnd("")
        setPageStart("")
        setPageEnd("")
        setArticleStart("")
        setArticleEnd("")
    }

    /** Called after each selected page; false ends the pass. zh-CN: 每个所选页之后调用; 返回 false 结束本次遍历. */
    protected abstract fun onPage(number: Int, page: PDPage?): Boolean

    /** Runs the pass over [pages] (ascending, from 1). zh-CN: 对 [pages] (升序, 从 1 起) 执行遍历. */
    fun sweep(document: PDDocument, pages: IntArray, output: Writer = NullWriter) {
        if (pages.isEmpty()) return
        selected = BitSet().apply { pages.forEach { set(it) } }
        expected = pages
        next = 0
        setStartPage(pages.first())
        setEndPage(pages.last())
        try {
            writeText(document, output)
            while (next < expected.size) {
                if (!onPage(expected[next++], null)) return
            }
        } catch (_: Finished) {
            // The answer is full. zh-CN: 应答已满.
        }
    }

    override fun processPage(page: PDPage) {
        val number = currentPageNo
        if (!selected.get(number)) return
        // Selected pages before this one had no content stream. zh-CN: 此前的所选页没有内容流.
        while (next < expected.size && expected[next] < number) {
            if (!onPage(expected[next++], null)) throw Finished
        }
        stop.checkStop()
        super.processPage(page)
        next++
        if (!onPage(number, page)) throw Finished
    }

    override fun processTextPosition(text: TextPosition) {
        if (++glyphs and 0xFF == 0) stop.checkStop()
        super.processTextPosition(text)
    }

    private object NullWriter : Writer() {
        override fun write(buffer: CharArray, offset: Int, length: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

}

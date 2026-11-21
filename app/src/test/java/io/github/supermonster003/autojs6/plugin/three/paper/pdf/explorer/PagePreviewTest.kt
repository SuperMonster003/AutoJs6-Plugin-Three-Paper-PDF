package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.autojs.plugin.pdf.api.PdfContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the single-page preview stamps on its one-page cut-out (roadmap P6.12, E4 feedback F4 / F5): the placeholders
 * that count pages are written out as the whole document shows them on the previewed page, in one pass like the engine,
 * and `{date}` and `{name}` stay for the engine.
 * zh-CN: 单页预览盖在单页切出文件上的内容 (路线图 P6.12, E4 反馈 F4 / F5): 计页的占位符按整个文档在预览页上的显示值写出, 与引擎一样单遍替换,
 * `{date}` 与 `{name}` 留给引擎.
 */
class PagePreviewTest {

    @Test
    fun `a text watermark shows the page and the total of the previewed page`() {
        val stamp = PagePreview.stampFor(watermark("{page} / {total} {name} {date} {{page}}", pages = "2-3"), page = 3, total = 6) as ToolOptions.Watermark
        assertEquals("3 / 6 {name} {date} {3}", stamp.text)
        // The cut-out has one page: the range goes. zh-CN: 切出文件只有一页: 去掉范围.
        assertNull(stamp.pages)
    }

    @Test
    fun `an image watermark is stamped as it is without the range`() {
        val image = watermark("", pages = "1").copy(type = PdfContract.STAMP_IMAGE)
        assertEquals(image.copy(pages = null), PagePreview.stampFor(image, page = 1, total = 1))
    }

    @Test
    fun `the page number counts from its start number and headers and footers from the first page`() {
        // Start 5 on pages 2-: page 2 shows 5, so page 4 shows 7 (appendix D Q211); headers count physically (Q262).
        // zh-CN: 从第 2 页起起始为 5: 第 2 页显示 5, 因此第 4 页显示 7 (附录 D Q211); 页眉按物理页计数 (Q262).
        val options = numbers(format = "{page}/{total}", start = 5, pages = "2-", header = ToolOptions.Slots(right = "Q {page} of {total}"))
        val stamp = PagePreview.stampFor(options, page = 4, total = 6) as ToolOptions.PageNumbers
        assertEquals("7/6", stamp.format)
        assertEquals(ToolOptions.Slots(right = "Q 4 of 6"), stamp.header)
        assertEquals(ToolOptions.Slots(center = "{name} 4"), stamp.footer)
        // The cut-out gets no offset of its own and no range. zh-CN: 切出文件不再带偏移与范围.
        assertEquals(1, stamp.start)
        assertNull(stamp.pages)
        assertNull(ToolPayloads.pageOffset(stamp.start, stamp.pages))
        assertEquals(options.position, stamp.position)
        assertEquals(options.fontSize, stamp.fontSize, 0.0)
    }

    @Test
    fun `a start below the first page shows as it is and headers alone keep no number`() {
        val below = PagePreview.stampFor(numbers(format = "{page}", start = 0, pages = "3-"), page = 3, total = 6) as ToolOptions.PageNumbers
        assertEquals("0", below.format)
        val headersOnly = PagePreview.stampFor(numbers(format = null, start = 1, pages = null), page = 2, total = 6) as ToolOptions.PageNumbers
        assertNull(headersOnly.format)
        assertEquals(ToolOptions.Slots(center = "{name} 2"), headersOnly.footer)
    }

    private fun numbers(format: String?, start: Int, pages: String?, header: ToolOptions.Slots = ToolOptions.Slots()) = ToolOptions.PageNumbers(
        format = format,
        position = PdfContract.POSITION_BOTTOM_RIGHT,
        start = start,
        header = header,
        footer = ToolOptions.Slots(center = "{name} {page}"),
        fontSize = 12.5,
        pages = pages,
    )

    private fun watermark(text: String, pages: String?) = ToolOptions.Watermark(
        type = PdfContract.STAMP_TEXT,
        text = text,
        image = null,
        position = PdfContract.POSITION_CENTER,
        rotation = 45.0,
        opacity = 0.3,
        tile = false,
        color = null,
        fontSize = null,
        width = null,
        layer = PdfContract.LAYER_OVER,
        pages = pages,
    )

}

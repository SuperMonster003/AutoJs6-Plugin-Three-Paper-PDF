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

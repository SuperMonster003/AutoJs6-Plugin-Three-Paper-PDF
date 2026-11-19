package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payloads of the editing tools of roadmap P6.12 (D68): the defaults of the host's script facade, the keys left out
 * when they hold the default, the `pageOffset` of appendix D Q211 and the order of page number, header and footer items.
 * zh-CN: 路线图 P6.12 编辑类工具的 payload (D68): 与宿主脚本门面相同的默认值, 取默认值时省略的键, 附录 D Q211 的 `pageOffset`, 以及
 * 页码, 页眉, 页脚条目的顺序.
 */
class ToolPayloadsTest {

    @Test
    fun `a text watermark carries the facade defaults and leaves out default keys`() {
        val item = ToolPayloads.watermarkItem(watermark())
        assertEquals(PdfContract.STAMP_TEXT, item.getString(PdfContract.FIELD_TYPE))
        assertEquals("DRAFT", item.getString(PdfContract.FIELD_TEXT))
        assertEquals(PdfContract.POSITION_CENTER, item.getString(PdfContract.FIELD_POSITION))
        assertEquals(45.0, item.getDouble(PdfContract.FIELD_ROTATION), DELTA)
        assertEquals(0.3, item.getDouble(PdfContract.FIELD_OPACITY), DELTA)
        assertEquals(ToolPayloads.WATERMARK_COLOR, item.getString(PdfContract.FIELD_COLOR))
        listOf(PdfContract.FIELD_TILE, PdfContract.FIELD_LAYER, PdfContract.FIELD_PAGES, PdfContract.FIELD_FONT_SIZE, PdfContract.FIELD_IMAGE, PdfContract.FIELD_WIDTH)
            .forEach { assertFalse(it, item.has(it)) }
    }

    @Test
    fun `an image watermark names image 0 and carries width, layer, tiling and pages`() {
        val item = ToolPayloads.watermarkItem(
            watermark().copy(type = PdfContract.STAMP_IMAGE, text = "", rotation = 0f, tile = true, color = null, width = 144f, layer = PdfContract.LAYER_UNDER, pages = "2-4"),
        )
        assertEquals(0, item.getInt(PdfContract.FIELD_IMAGE))
        assertFalse(item.has(PdfContract.FIELD_TEXT))
        assertFalse(item.has(PdfContract.FIELD_COLOR))
        assertEquals(144.0, item.getDouble(PdfContract.FIELD_WIDTH), DELTA)
        assertEquals(PdfContract.LAYER_UNDER, item.getString(PdfContract.FIELD_LAYER))
        assertTrue(item.getBoolean(PdfContract.FIELD_TILE))
        assertEquals("2-4", item.getString(PdfContract.FIELD_PAGES))
        assertEquals(0.0, item.getDouble(PdfContract.FIELD_ROTATION), DELTA)
    }

    @Test
    fun `page numbers come first, then header and footer slots left to right`() {
        val items = ToolPayloads.pageNumberItems(
            numbers().copy(header = ToolOptions.Slots(right = "Header"), footer = ToolOptions.Slots(left = "{name}", center = "  ")),
        )
        assertEquals(listOf("{page} / {total}", "Header", "{name}"), items.map { it.getString(PdfContract.FIELD_TEXT) })
        assertEquals(
            listOf(PdfContract.POSITION_BOTTOM_CENTER, PdfContract.POSITION_TOP_RIGHT, PdfContract.POSITION_BOTTOM_LEFT),
            items.map { it.getString(PdfContract.FIELD_POSITION) },
        )
        items.forEach { assertEquals(10.0, it.getDouble(PdfContract.FIELD_FONT_SIZE), DELTA) }
        // Start 1 on page 1 needs no offset, and header or footer items never carry one. zh-CN: 第 1 页从 1 起无需偏移, 页眉页脚条目从不带偏移.
        items.forEach { assertFalse(it.has(PdfContract.FIELD_PAGE_OFFSET)) }
    }

    @Test
    fun `the start number becomes a page offset from the first selected page`() {
        val item = ToolPayloads.pageNumberItems(numbers().copy(start = 5, pages = "3-6, 9")).single()
        assertEquals(2, item.getInt(PdfContract.FIELD_PAGE_OFFSET))
        assertEquals("3-6, 9", item.getString(PdfContract.FIELD_PAGES))
        assertNull(ToolPayloads.pageOffset(1, null))
        assertEquals(-1, ToolPayloads.pageOffset(0, null))
        assertEquals(-4, ToolPayloads.pageOffset(1, "8-, 5"))
        assertEquals(PdfContract.MAX_PAGE_OFFSET, ToolPayloads.pageOffset(Int.MAX_VALUE, null))
    }

    @Test
    fun `the first page of a range is the smallest start and 1 when it cannot be read`() {
        assertEquals(1, ToolPayloads.firstPage(null))
        assertEquals(1, ToolPayloads.firstPage(" "))
        assertEquals(2, ToolPayloads.firstPage("5-, 2-3"))
        assertEquals(1, ToolPayloads.firstPage("x, 4"))
    }

    @Test
    fun `without page numbers only the filled slots are sent`() {
        val items = ToolPayloads.pageNumberItems(numbers().copy(format = null, header = ToolOptions.Slots(center = "Title")))
        assertEquals(1, items.size)
        assertEquals(PdfContract.POSITION_TOP_CENTER, items.single().getString(PdfContract.FIELD_POSITION))
        assertEquals(1, ToolPayloads.stamp(items).getJSONArray(PdfContract.FIELD_ITEMS).length())
    }

    @Test
    fun `fill, info and outline payloads follow the protocol`() {
        val fill = ToolPayloads.fill(ToolOptions.FillForm(linkedMapOf("name" to "Ada", "colors" to listOf("red", "blue"), "agree" to true, "reset" to null), flatten = false))
        val values = fill.getJSONObject(PdfContract.FIELD_VALUES)
        assertEquals("Ada", values.getString("name"))
        assertEquals(2, values.getJSONArray("colors").length())
        assertTrue(values.getBoolean("agree"))
        assertTrue(values.isNull("reset"))
        assertFalse(fill.has(PdfContract.FIELD_FLATTEN))
        assertTrue(ToolPayloads.fill(ToolOptions.FillForm(emptyMap(), flatten = true)).getBoolean(PdfContract.FIELD_FLATTEN))

        val info = ToolPayloads.info(linkedMapOf(PdfContract.FIELD_TITLE to "Report", PdfContract.FIELD_AUTHOR to null)).getJSONObject(PdfContract.FIELD_INFO)
        assertEquals("Report", info.getString(PdfContract.FIELD_TITLE))
        assertEquals(JSONObject.NULL, info.get(PdfContract.FIELD_AUTHOR))

        assertEquals(0, ToolPayloads.clearedOutline().getJSONArray(PdfContract.FIELD_ITEMS).length())
    }

    private fun watermark() = ToolOptions.Watermark(
        type = PdfContract.STAMP_TEXT,
        text = "DRAFT",
        image = null,
        position = PdfContract.POSITION_CENTER,
        rotation = ToolPayloads.WATERMARK_TEXT_ROTATION,
        opacity = ToolPayloads.WATERMARK_OPACITY,
        tile = false,
        color = null,
        fontSize = null,
        width = null,
        layer = PdfContract.LAYER_OVER,
        pages = null,
    )

    private fun numbers() = ToolOptions.PageNumbers(
        format = ToolPayloads.PAGE_NUMBER_FORMAT,
        position = ToolPayloads.PAGE_NUMBER_POSITION,
        start = 1,
        header = ToolOptions.Slots(),
        footer = ToolOptions.Slots(),
        fontSize = ToolPayloads.STAMP_FONT_SIZE,
        pages = null,
    )

    private companion object {

        const val DELTA = 1e-6

    }

}

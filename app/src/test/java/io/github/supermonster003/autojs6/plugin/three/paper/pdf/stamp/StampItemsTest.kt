package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StampItems] reads the `items` of a `stamp` payload as protocol "Stamp items" says: defaults, limits (roadmap Q185
 * (a): too many items is `LIMIT_EXCEEDED`), field paths in the messages, colours, positions and the single-pass
 * placeholders.
 * zh-CN: [StampItems] 按协议 "Stamp items" 一节读取 `stamp` payload 的 `items`: 缺省值, 上限 (路线图 Q185 (a): 条目过多为
 * `LIMIT_EXCEEDED`), 消息中的字段路径, 颜色, 位置与单遍替换的占位符.
 */
class StampItemsTest {

    private fun items(json: String, images: Int = 1) = StampItems.parse(PdfPayload.parse(json.toByteArray(Charsets.UTF_8)), images)

    private fun failure(code: String, json: String, images: Int = 1): String {
        val failure = assertThrows(PdfFailure::class.java) { items(json, images) }
        assertEquals(failure.message, code, failure.code)
        return failure.detail.orEmpty()
    }

    @Test
    fun `defaults follow the protocol table`() {
        val item = items("""{ "items": [ { "type": "text", "text": "Draft" } ] }""").single()
        assertTrue(item.isText)
        assertEquals("Draft", item.text)
        assertEquals("absent pageOffset (Q211 (a))", 0L, item.pageOffset)
        assertNull(item.pages)
        assertEquals(StampPosition.Named(1, 1), item.position)
        assertEquals(0.0, item.offsetX, 0.0)
        assertEquals(0.0, item.rotation, 0.0)
        assertEquals(1.0, item.opacity, 0.0)
        assertNull("absent fontSize fits the page width", item.fontSize)
        assertEquals(0xFF000000.toInt(), item.color)
        assertEquals(false, item.tile)
        assertEquals(PdfContract.DEFAULT_STAMP_TILE_GAP.toDouble(), item.gapX, 0.0)
        assertEquals(PdfContract.DEFAULT_STAMP_MARGIN.toDouble(), item.margin, 0.0)
        assertEquals(StampLayer.OVER, item.layer)
        assertEquals(StampAlign.CENTER, item.align)
    }

    @Test
    fun `every field is read`() {
        val (text, image) = items(
            """{ "items": [
              { "type": "text", "text": "{page} / {total}", "pages": "2-", "position": "bottom-right", "offsetX": -5, "offsetY": 2.5,
                "rotation": 45, "opacity": 0.3, "fontSize": 10, "color": "#80FF0000", "bold": true, "italic": true, "tile": true,
                "gapX": 10, "gapY": 0, "layer": "under", "margin": 0, "unknown": [1, 2] },
              { "type": "image", "image": 1, "position": [10, 20.5], "width": 72, "scale": 2 }
            ] }""",
            images = 2,
        )
        assertEquals("2-", text.pages)
        assertEquals(StampPosition.Named(2, 2), text.position)
        assertEquals(StampAlign.END, text.align)
        assertEquals(-5.0, text.offsetX, 0.0)
        assertEquals(45.0, text.rotation, 0.0)
        assertEquals(0.3, text.opacity, 0.0)
        assertEquals(10.0, text.fontSize!!, 0.0)
        assertEquals(0x80FF0000.toInt(), text.color)
        assertTrue(text.bold && text.italic && text.tile)
        assertEquals(0.0, text.gapY, 0.0)
        assertEquals(StampLayer.UNDER, text.layer)
        assertEquals(0.0, text.margin, 0.0)
        assertEquals(false, image.isText)
        assertEquals(1, image.image)
        assertEquals(StampPosition.At(10.0, 20.5), image.position)
        assertEquals(StampAlign.START, image.align)
        assertEquals(72.0, image.width!!, 0.0)
        assertNull(image.height)
        assertEquals(2.0, image.scale!!, 0.0)
    }

    @Test
    fun `item counts, images and values out of range fail with the field path`() {
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [] }""").let { assertTrue(it, it.contains("payload.items is empty")) }
        val many = (0..PdfContract.MAX_STAMP_ITEMS).joinToString(",") { """{ "type": "text", "text": "t$it" }""" }
        failure(PdfErrorCodes.LIMIT_EXCEEDED, """{ "items": [ $many ] }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "shape" } ] }""").let { assertTrue(it, it.contains("payload.items[0].type")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text" } ] }""").let { assertTrue(it, it.contains("payload.items[0].text")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "image", "image": 1 } ] }""").let { assertTrue(it, it.contains("payload.items[0].image")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "image", "image": 0 } ] }""", images = 0)
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "position": "middle" } ] }""").let { assertTrue(it, it.contains("payload.items[0].position")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "position": [1, 2, 3] } ] }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "position": ["1", 2] } ] }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "layer": "middle" } ] }""").let { assertTrue(it, it.contains("payload.items[0].layer")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "opacity": 1.5 } ] }""").let { assertTrue(it, it.contains("payload.items[0].opacity")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "fontSize": 0 } ] }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "gapX": -1 } ] }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "image", "image": 0, "width": 0 } ] }""")
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "color": "red" } ] }""").let { assertTrue(it, it.contains("payload.items[0].color")) }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "bold": "yes" } ] }""")
        val long = "x".repeat(PdfContract.MAX_STAMP_TEXT_LENGTH + 1)
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "$long" } ] }""").let { assertTrue(it, it.contains("at most ${PdfContract.MAX_STAMP_TEXT_LENGTH}")) }
        assertEquals(1, items("""{ "items": [ { "type": "text", "text": "${"x".repeat(PdfContract.MAX_STAMP_TEXT_LENGTH)}" } ] }""").size)
        assertEquals(PdfContract.MAX_STAMP_ITEMS, items("""{ "items": [ ${(1..PdfContract.MAX_STAMP_ITEMS).joinToString(",") { """{ "type": "text", "text": "t" }""" }} ] }""").size)
    }

    @Test
    fun `a page offset shifts only the page number (Q211 (a))`() {
        val max = PdfContract.MAX_PAGE_OFFSET
        val (shifted, image, low, high) = items(
            """{ "items": [
              { "type": "text", "text": "{page}", "pageOffset": -2 },
              { "type": "image", "image": 0, "pageOffset": 5 },
              { "type": "text", "text": "{page}", "pageOffset": -$max },
              { "type": "text", "text": "{page}", "pageOffset": $max.0 }
            ] }""",
        )
        assertEquals(-2L, shifted.pageOffset)
        assertEquals("image items take the field without effect", 5L, image.pageOffset)
        assertEquals(-max.toLong(), low.pageOffset)
        assertEquals(max.toLong(), high.pageOffset)
        assertEquals("1 / 10", StampItems.expand("{page} / {total}", 3, 10, "d", "n", -2))
        assertEquals("sums below 1 are shown as they are", "0 -1", StampItems.expand("{page}", 1, 3, "d", "n", -1) + " " + StampItems.expand("{page}", 1, 3, "d", "n", -2))
        assertEquals("1000005 of 5", StampItems.expand("{page} of {total}", 5, 5, "d", "n", max.toLong()))
        assertEquals("{date} and {name} stay as they are", "d n 3", StampItems.expand("{date} {name} {page}", 3, 3, "d", "n", 0))
        for (bad in listOf("1.5", "\"2\"", "true", "${max + 1}", "-${max + 1}", "1e7")) {
            failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a", "pageOffset": $bad } ] }""").let { assertTrue("$bad: $it", it.contains("payload.items[0].pageOffset")) }
        }
        failure(PdfErrorCodes.INVALID_ARGUMENT, """{ "items": [ { "type": "text", "text": "a" }, { "type": "image", "image": 0, "pageOffset": 0.5 } ] }""").let { assertTrue(it, it.contains("payload.items[1].pageOffset")) }
    }

    @Test
    fun `colours take six or eight hex digits`() {
        assertEquals(0xFF336699.toInt(), StampItems.color("#336699", "c"))
        assertEquals(0xFFAABBCC.toInt(), StampItems.color("#aabbcc", "c"))
        assertEquals(0x00000000, StampItems.color("#00000000", "c"))
        for (bad in listOf("336699", "#12345", "#1234567", "#GGGGGG", "#", "")) {
            assertEquals(bad, PdfErrorCodes.INVALID_ARGUMENT, assertThrows(PdfFailure::class.java) { StampItems.color(bad, "c") }.code)
        }
    }

    @Test
    fun `placeholders are replaced in a single pass`() {
        assertEquals("3 / 10", StampItems.expand("{page} / {total}", 3, 10, "2026-11-19", ""))
        assertEquals("Report 2026-11-19 {x} {", StampItems.expand("{name} {date} {x} {", 1, 1, "2026-11-19", "Report"))
        assertEquals("a {page} name stays", StampItems.expand("a {name} name stays", 7, 9, "d", "{page}"))
        assertEquals("plain", StampItems.expand("plain", 1, 1, "d", "n"))
        assertEquals("Report", StampItems.name("Report.PDF"))
        assertEquals("a.pdf.txt", StampItems.name("a.pdf.txt"))
        assertEquals("", StampItems.name(null))
        assertEquals("", StampItems.name(".pdf"))
    }

}

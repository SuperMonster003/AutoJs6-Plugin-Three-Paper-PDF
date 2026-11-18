package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payload reader of roadmap P6.2 row 2: size, encoding and strict JSON checks, and the typed accessors with the
 * paths in their messages.
 * zh-CN: 路线图 P6.2 第 2 行的 payload 读取: 大小, 编码与严格 JSON 检查, 以及在消息中带路径的类型访问方法.
 */
class PdfPayloadTest {

    private fun parse(text: String, limit: Long = PdfPayload.MAX_PAYLOAD_BYTES) = PdfPayload.parse(text.toByteArray(Charsets.UTF_8), limit)

    private fun failure(code: String, block: () -> Unit): String {
        val failure = assertThrows(PdfFailure::class.java) { block() }
        assertEquals(failure.message, code, failure.code)
        return failure.detail.orEmpty()
    }

    private fun invalid(text: String): String = failure(PdfErrorCodes.INVALID_ARGUMENT) { parse(text) }

    @Test
    fun `the local constants match contract version 2`() {
        // TODO(P6.2 row 1): compare with PdfContract once the contract version 2 AAR is staged.
        // zh-CN: 换入契约版本 2 的 AAR 后改为与 PdfContract 比较.
        assertEquals(1, PdfPayload.PAYLOAD_INPUT_INDEX)
        assertEquals(16L * 1024 * 1024, PdfPayload.MAX_PAYLOAD_BYTES)
    }

    @Test
    fun `a payload above the limit is LIMIT_EXCEEDED`() {
        val text = """{"items":[]}"""
        assertEquals(setOf("items"), parse(text, limit = text.length.toLong()).keys)
        val detail = failure(PdfErrorCodes.LIMIT_EXCEEDED) { parse(text, limit = text.length - 1L) }
        assertTrue(detail, detail.contains("${text.length} bytes"))
    }

    @Test
    fun `values of every JSON type are read`() {
        val payload = parse(
            """ { "text": "Zhang San 张三 😀 \"q\" \\ \/ \b\f\n\r\t", "yes": true, "no": false, "none": null,
                "count": 42, "negative": -7, "ratio": 0.25, "big": 1e3, "tiny": -2.5E-3, "list": [1, "two", [3], {}], "nested": { "a": { "b": [] } } } """,
        )
        assertEquals(listOf("text", "yes", "no", "none", "count", "negative", "ratio", "big", "tiny", "list", "nested"), payload.keys.toList())
        assertEquals("Zhang San " + String(Character.toChars(0x5F20)) + String(Character.toChars(0x4E09)) + " " + String(Character.toChars(0x1F600)) +
            " \"q\" \\ / " + Char(8) + Char(12) + "\n\r\t", payload.string("text"))
        assertTrue(payload.boolean("yes"))
        assertFalse(payload.boolean("no"))
        assertTrue(payload.has("none"))
        assertNull(payload.value("none"))
        assertNull(payload.optString("none"))
        assertFalse(payload.has("absent"))
        assertEquals(42L, payload.value("count"))
        assertEquals(-7, payload.int("negative", -10..10))
        assertEquals(0.25, payload.double("ratio", 0.0..1.0), 0.0)
        assertEquals(1000L, payload.long("big"))
        assertEquals(-0.0025, payload.double("tiny"), 1e-12)
        val list = payload.array("list")
        assertEquals(4, list.size)
        assertEquals("two", list.string(1))
        assertEquals(1, list.array(2).size)
        assertEquals("payload.list[3]", list.obj(3).path)
        assertEquals("payload.nested.a.b", payload.obj("nested").obj("a").array("b").path)
    }

    @Test
    fun `unknown fields are kept but ignored and field order is kept`() {
        val payload = parse("""{"z":1,"a":2,"future":{"x":[true]}}""")
        assertEquals(listOf("z", "a", "future"), payload.keys.toList())
        assertEquals(2L, payload.long("a"))
    }

    @Test
    fun `text that is not strict JSON is INVALID_ARGUMENT`() {
        val cases = listOf(
            "" to "empty",
            "   " to "empty",
            "{" to "field name",
            "{}x" to "after the JSON value",
            "{} {}" to "after the JSON value",
            "{'a':1}" to "field name",
            "{a:1}" to "field name",
            """{"a":1,}""" to "field name",
            """{"a":[1,]}""" to "unexpected ']'",
            """{"a":01}""" to "leading zeros",
            """{"a":-}""" to "digit",
            """{"a":1.}""" to "after '.'",
            """{"a":1e}""" to "exponent",
            """{"a":+1}""" to "unexpected '+'",
            """{"a":NaN}""" to "unexpected 'N'",
            """{"a":1e999}""" to "out of range",
            """{"a":tru}""" to "unexpected 't'",
            """{"a":"x""" to "not closed",
            "{\"a\":\"line\nbreak\"}" to "control character",
            """{"a":"\x"}""" to "unknown escape",
            """{"a":"\u12"}""" to "four hexadecimal digits",
            """{"a":"\ud83d"}""" to "unpaired surrogate",
            """{"a":"\ude00"}""" to "unpaired surrogate",
            """{"a":"\ud83dA"}""" to "unpaired surrogate",
            """{"a":1 /* note */}""" to "',' or '}'",
            """{"a":1}// note""" to "after the JSON value",
            """{"a" 1}""" to "':'",
            """{"a":1 "b":2}""" to "',' or '}'",
        )
        for ((text, expected) in cases) {
            val detail = invalid(text)
            assertTrue("$text: $detail", detail.contains(expected))
        }
    }

    @Test
    fun `the top level must be an object`() {
        for ((text, kind) in listOf("[]" to "an array", "\"text\"" to "a string", "1" to "a number", "true" to "a boolean", "null" to "null")) {
            val detail = invalid(text)
            assertTrue(detail, detail.contains("is $kind, not an object"))
        }
    }

    @Test
    fun `bytes that are not UTF-8, a byte order mark and duplicate keys are INVALID_ARGUMENT`() {
        val latin = byteArrayOf('{'.code.toByte(), '"'.code.toByte(), 0xE9.toByte(), '"'.code.toByte(), ':'.code.toByte(), '1'.code.toByte(), '}'.code.toByte())
        assertTrue(failure(PdfErrorCodes.INVALID_ARGUMENT) { PdfPayload.parse(latin) }.contains("not UTF-8"))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{}".toByteArray()
        assertTrue(failure(PdfErrorCodes.INVALID_ARGUMENT) { PdfPayload.parse(bom) }.contains("byte order mark"))
        assertTrue(invalid("""{"a":1,"b":{"c":1,"c":2}}""").contains("\"c\" twice"))
    }

    @Test
    fun `nesting is limited`() {
        val deepest = "{\"a\":" + "[".repeat(PdfPayload.MAX_DEPTH - 1) + "]".repeat(PdfPayload.MAX_DEPTH - 1) + "}"
        assertEquals(setOf("a"), parse(deepest).keys)
        val deeper = "{\"a\":" + "[".repeat(PdfPayload.MAX_DEPTH) + "]".repeat(PdfPayload.MAX_DEPTH) + "}"
        assertTrue(invalid(deeper).contains("deeper than ${PdfPayload.MAX_DEPTH}"))
        // Far deeper input ends with the same failure, not a stack overflow. zh-CN: 远更深的输入同样失败, 不会栈溢出.
        assertTrue(invalid("[".repeat(100_000)).contains("deeper than"))
    }

    @Test
    fun `missing and mistyped fields name their path`() {
        val payload = parse("""{"items":[{"text":1,"opacity":1.5,"page":2.5,"pages":["x",1]}],"values":{"odd key":true,"name":null}}""")
        val item = payload.array("items").obj(0)
        assertEquals("payload.items[0].text must be a string, not a number", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.string("text") })
        assertEquals("payload.items[0].opacity must be in 0.0 - 1.0, not 1.5", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.double("opacity", 0.0..1.0) })
        assertEquals("payload.items[0].page must be a whole number, not a number", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.long("page") })
        assertEquals("payload.items[0].pages[1] must be a string, not a number", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.array("pages").strings() })
        assertEquals("payload.items[0].font is required", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.string("font") })
        assertEquals("payload.items[0].layer is required", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.obj("layer") })
        assertEquals("payload.items must be an object, not an array", failure(PdfErrorCodes.INVALID_ARGUMENT) { payload.obj("items") })
        assertEquals("payload.items[0] must be a string, not an object", failure(PdfErrorCodes.INVALID_ARGUMENT) { payload.array("items").string(0) })
        val values = payload.obj("values")
        assertEquals("payload.values[\"odd key\"] must be a string, not a boolean", failure(PdfErrorCodes.INVALID_ARGUMENT) { values.string("odd key") })
        assertEquals("payload.values.name is required", failure(PdfErrorCodes.INVALID_ARGUMENT) { values.string("name") })
        assertTrue(values.has("name"))
        assertFalse(values.has("absent"))
        assertNull(item.optInt("absent", 1..10))
        assertNull(values.optBoolean("name"))
        assertEquals("payload.items[0].text must be in 2 - 9, not 1", failure(PdfErrorCodes.INVALID_ARGUMENT) { item.int("text", 2..9) })
    }

}

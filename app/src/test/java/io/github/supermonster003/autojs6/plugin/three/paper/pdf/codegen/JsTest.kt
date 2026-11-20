package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.Js.Raw
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptFixtures.text
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import kotlin.random.Random

/**
 * The literals and layout of [Js] (roadmap P6.13): string literals read back as the same text in the host's Rhino (a
 * fixed-seed fuzz over every kind of code point, unpaired surrogates included) and never hold a line terminator, numbers,
 * keys, and laid-out values and calls that keep their value and the line width.
 * zh-CN: [Js] 的字面量与排版 (路线图 P6.13): 字符串字面量在宿主的 Rhino 中读回的文字与原文相同 (对各类码位做固定种子的模糊测试, 含不成对的
 * 代理项), 且不含行终止符; 数字, 键, 以及排版后保持取值与行宽的值和调用.
 */
class JsTest {

    private fun readBack(literal: String): String = Context.toString(ScriptHarness.evalRhino(literal))

    private fun jsonOf(code: String): Any? {
        val json = Context.toString(ScriptHarness.evalRhino("JSON.stringify($code)"))
        return ScriptHarness.norm(if (json.startsWith("[")) JSONArray(json) else if (json.startsWith("{")) JSONObject(json) else JSONArray("[$json]").opt(0))
    }

    @Test
    fun stringsReadBackAsTheSameText() {
        val samples = listOf(
            "", "plain", ScriptFixtures.TRICKY, ScriptFixtures.NAME, "'", "\"", text(0x5C), text(0x5C, 0x75, 0x30, 0x30, 0x34, 0x31),
            text(0x0A, 0x0D, 0x09, 0x0B, 0x0C, 0x08, 0x00, 0x1F, 0x7F, 0x85), text(0x2028, 0x2029), text(0xFEFF, 0x200B, 0x200E, 0x200F, 0x202A, 0x202E, 0x2066, 0x2069),
            text(0xE000, 0xF8FF), text(0x1F600, 0x1F4C4, 0x1F468, 0x200D, 0x1F469), text(0x4E2D, 0x6587), "${'$'}{x} `t` </script> <!-- -->",
            "a" + Char(0xD800) + "b", Char(0xDC00) + "x", "x" + Char(0xD83D), Char(0xDE00).toString() + Char(0xD83D),
        )
        samples.forEach { sample ->
            val literal = Js.string(sample)
            assertEquals(literal, sample, readBack(literal))
            assertNoLineTerminator(literal)
        }
    }

    @Test
    fun fuzzedStringsReadBackAsTheSameText() {
        val random = Random(6013)
        val ranges = listOf(0x00..0x7F, 0x80..0x24F, 0x2000..0x206F, 0x3000..0x9FFF, 0xD800..0xDFFF, 0xE000..0xFFFF, 0x10000..0x10FFFF, 0x1F300..0x1FAFF)
        repeat(400) {
            val sample = buildString {
                repeat(random.nextInt(0, 40)) {
                    val range = ranges[random.nextInt(ranges.size)]
                    val codePoint = random.nextInt(range.first, range.last + 1)
                    if (codePoint in 0xD800..0xDFFF) append(Char(codePoint)) else appendCodePoint(codePoint)
                }
            }
            val literal = Js.string(sample)
            assertEquals(literal, sample, readBack(literal))
            assertNoLineTerminator(literal)
        }
    }

    @Test
    fun stringsKeepReadableTextAndEscapeTheRest() {
        assertEquals("'It\\'s'", Js.string("It's"))
        assertEquals("'\"'", Js.string("\""))
        assertEquals("'a\\\\b'", Js.string("a" + text(0x5C) + "b"))
        assertEquals("'a\\nb\\r\\t'", Js.string("a" + text(0x0A) + "b" + text(0x0D, 0x09)))
        assertEquals("'\\u2028\\u2029'", Js.string(text(0x2028, 0x2029)))
        assertEquals("'\\u202Ex\\u202C'", Js.string(text(0x202E) + "x" + text(0x202C)))
        assertEquals("'\\uFEFF\\u0000\\u007F'", Js.string(text(0xFEFF, 0x00, 0x7F)))
        assertEquals("'\\uD83D'", Js.string(Char(0xD83D).toString()))
        val readable = text(0x4E2D, 0x6587) + " " + text(0x1F600) + " " + text(0xE9)
        assertEquals("'$readable'", Js.string(readable))
    }

    @Test
    fun numbers() {
        assertEquals("42", Js.number(42))
        assertEquals("-7", Js.number(-7L))
        assertEquals("12.5", Js.number(12.5))
        assertEquals("12.5", Js.number(12.5f))
        assertEquals("0.1", Js.number(0.1f))
        assertEquals("0.333", Js.number(0.333))
        assertEquals("-36000", Js.number(-36000.0))
        assertEquals("1", Js.number(1.0))
        assertEquals("0", Js.number(-0.0))
        assertEquals("0.0000001", Js.number(1e-7))
        assertEquals("1000000000000000000000", Js.number(1e21))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Js.number(value) }
        }
        listOf(12.5, 0.333, -36000.0, 1e-7, 144.5, 0.1).forEach { value ->
            assertEquals(value, (ScriptHarness.evalRhino(Js.number(value)) as Number).toDouble(), 0.0)
        }
    }

    @Test
    fun keys() {
        assertEquals("name", Js.key("name"))
        assertEquals("${'$'}x_1", Js.key("${'$'}x_1"))
        listOf("__proto__", "class", "default", "a.b[0]", "1a", "", "with space", text(0x540D)).forEach { name ->
            assertEquals(name, Js.string(name), Js.key(name))
        }
    }

    @Test
    fun inlineValues() {
        assertEquals("null", Js.inline(null))
        assertEquals("true", Js.inline(true))
        assertEquals("{}", Js.inline(emptyMap<String, Any>()))
        assertEquals("[]", Js.inline(emptyList<Any>()))
        assertEquals("{ a: 1, 'b c': ['x', null, false], d: {} }", Js.inline(linkedMapOf("a" to 1, "b c" to listOf("x", null, false), "d" to emptyMap<String, Any>())))
        assertEquals("pages(1)", Js.inline(Raw("pages(1)")))
        assertThrows(IllegalArgumentException::class.java) { Js.inline(Any()) }
    }

    @Test
    fun formattedValuesKeepTheirValueAndTheWidth() {
        val long = linkedMapOf<String, Any?>(
            "text" to ScriptFixtures.TRICKY,
            "items" to listOf(linkedMapOf("type" to "text", "text" to "a".repeat(120)), linkedMapOf("type" to "image", "width" to 144.5)),
            "order" to (1..200).toList(),
            "empty" to emptyList<Any>(),
            "class" to "quoted key",
        )
        val code = Js.format(long, 0, "let options = ".length, 1)
        assertTrue(code, code.contains('\n'))
        assertFalse(code, Regex(",\\s*[}\\]]").containsMatchIn(code))
        code.lines().forEach { line ->
            val wide = line.contains("text: '")
            if (!wide) assertTrue(line, line.length <= Js.WIDTH)
        }
        assertEquals(ScriptHarness.norm(long), jsonOf("(" + code + ")"))
        assertEquals("{ a: 1 }", Js.format(mapOf("a" to 1), 3, 10))
    }

    @Test
    fun calls() {
        assertEquals("let r = pdf.merge(inputs, output);", Js.call("let r = ", "pdf.merge", listOf(Raw("inputs"), Raw("output")), 0))
        val options = linkedMapOf<String, Any?>("text" to "x".repeat(80), "rotation" to 12.5, "pages" to "1-2, 4")
        val lastLaidOut = Js.call("let result = ", "pdf.watermark", listOf(Raw("input"), Raw("output"), options), 1)
        assertTrue(lastLaidOut, lastLaidOut.startsWith("    let result = pdf.watermark(input, output, {\n"))
        assertTrue(lastLaidOut, lastLaidOut.endsWith("\n    });"))
        val everyLine = Js.call("let result = ", "pdf.setInfo", listOf("p".repeat(90), "q".repeat(90), options), 0)
        assertTrue(everyLine, everyLine.startsWith("let result = pdf.setInfo(\n    'ppp"))
        assertTrue(everyLine, everyLine.endsWith("\n);"))
        listOf(lastLaidOut, everyLine).forEach { code ->
            val recorded = jsonOf("(function () { var calls = []; var pdf = { watermark: function () { calls.push([].slice.call(arguments)); }, setInfo: function () { calls.push([].slice.call(arguments)); } };" +
                " var input = 'i', output = 'o'; " + code.trim() + " return calls; })()")
            val args = (recorded as List<*>).single() as List<*>
            assertEquals(ScriptHarness.norm(options), args.last())
        }
    }

    private fun assertNoLineTerminator(literal: String) {
        listOf(0x0A, 0x0D, 0x2028, 0x2029).forEach { codePoint -> assertFalse(literal, literal.contains(Char(codePoint))) }
    }

}

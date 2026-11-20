package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles.children
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The warnings of the result page (appendix D Q264 (b)): reading the three arrays, localized text for a known code
 * with complete arguments of the right type and English otherwise, and resources for every code in every locale.
 * zh-CN: 结果页的警告 (附录 D Q264 (b)): 读取三个数组, 已知代码且参数齐全, 类型正确时显示本地化文字, 否则显示英文, 以及每个代码在每个语言里都有资源.
 */
class ToolWarningTest {

    @Test
    fun `a version-1 completion shows the English texts`() {
        val warnings = ToolWarning.parse(arrayOf("a", "b"), null, null)
        assertEquals(listOf(ToolWarning("a"), ToolWarning("b")), warnings)
        warnings.forEach {
            assertFalse(it.isLocalizable)
            assertEquals(it.text, it.localized(FakeStrings()))
        }
        assertEquals(emptyList<ToolWarning>(), ToolWarning.parse(null, arrayOf("XFA_REMOVED"), arrayOf("{}")))
    }

    @Test
    fun `a version-2 completion reads codes and arguments, integers as Long`() {
        val warnings = ToolWarning.parse(
            arrayOf("1 image cannot be decoded", "object streams were not used because x", "the static XFA form data was removed"),
            arrayOf(PdfWarningCodes.IMAGES_UNDECODABLE, PdfWarningCodes.OBJECT_STREAMS_NOT_USED, PdfWarningCodes.XFA_REMOVED),
            arrayOf("{\"count\":1}", "{\"reason\":\"x\"}", "{}"),
        )
        assertEquals(PdfWarningCodes.IMAGES_UNDECODABLE, warnings[0].code)
        assertEquals(mapOf(PdfWarningCodes.ARG_COUNT to 1L), warnings[0].args)
        assertEquals(mapOf(PdfWarningCodes.ARG_REASON to "x"), warnings[1].args)
        assertEquals(emptyMap<String, Any>(), warnings[2].args)
        warnings.forEach { assertTrue(it.toString(), it.isLocalizable) }
        // What the engine writes reads back the same. zh-CN: 引擎写出的内容原样读回.
        val written = PdfWarning("t", PdfWarningCodes.OUTPUT_NOT_SMALLER, mapOf(PdfWarningCodes.ARG_SIZE_BYTES to 5_000_000_000L, PdfWarningCodes.ARG_SOURCE_BYTES to 7))
        val read = ToolWarning.parse(arrayOf(written.text), arrayOf(written.code), arrayOf(written.argsJson())).single()
        assertEquals(mapOf(PdfWarningCodes.ARG_SIZE_BYTES to 5_000_000_000L, PdfWarningCodes.ARG_SOURCE_BYTES to 7L), read.args)
    }

    @Test
    fun `an unknown code, a missing argument or a wrong type falls back to English`() {
        fun english(code: String?, args: String?) {
            val warning = ToolWarning.parse(arrayOf("english"), arrayOf(code), arrayOf(args)).single()
            assertFalse("$code $args", warning.isLocalizable)
            val strings = FakeStrings()
            assertEquals("$code $args", "english", warning.localized(strings))
            assertTrue(strings.calls.isEmpty())
        }
        english("FUTURE_CODE", "{}")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, "{}")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, "{\"count\":\"3\"}")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, "{\"count\":1.5}")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, "{\"count\":null}")
        english(PdfWarningCodes.OBJECT_STREAMS_NOT_USED, "{\"reason\":5}")
        english(PdfWarningCodes.FIELDS_IGNORED, "{\"count\":2,\"names\":\"'a'\"}")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, "[1]")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, "not json")
        english(PdfWarningCodes.IMAGES_UNDECODABLE, null)
        english("lower_case", "{}")
        english("", "{}")
        english(null, "{\"count\":1}")
        // Extra arguments do not matter. zh-CN: 多出的参数不影响.
        assertTrue(ToolWarning.parse(arrayOf("e"), arrayOf(PdfWarningCodes.XFA_REMOVED), arrayOf("{\"extra\":1}")).single().isLocalizable)
    }

    @Test
    fun `arrays of another length are left out as a whole`() {
        val texts = arrayOf<String?>("a", "b")
        val shortCodes = ToolWarning.parse(texts, arrayOf(PdfWarningCodes.XFA_REMOVED), arrayOf("{}", "{}"))
        shortCodes.forEach { assertNull(it.code); assertEquals(it.text, it.localized(FakeStrings())) }
        val shortArgs = ToolWarning.parse(texts, arrayOf(PdfWarningCodes.XFA_REMOVED, PdfWarningCodes.XMP_REMOVED), arrayOf("{}"))
        shortArgs.forEach { assertNull(it.args); assertEquals(it.text, it.localized(FakeStrings())) }
        val withNull = ToolWarning.parse(arrayOf("a", null, "c"), arrayOf(PdfWarningCodes.XFA_REMOVED, PdfWarningCodes.XFA_REMOVED, PdfWarningCodes.XMP_REMOVED), arrayOf("{}", "{}", "{}"))
        assertEquals(listOf("a" to PdfWarningCodes.XFA_REMOVED, "c" to PdfWarningCodes.XMP_REMOVED), withNull.map { it.text to it.code })
    }

    @Test
    fun `every code is localized with its own resource and arguments`() {
        assertEquals(18, PdfWarningCodes.ALL.size)
        PdfWarningCodes.ALL.forEach { code ->
            val strings = FakeStrings()
            val warning = complete(code)
            assertTrue(code, warning.isLocalizable)
            val text = warning.localized(strings)
            assertFalse(code, text == warning.text)
            val call = strings.calls.last()
            assertEquals(code, "tool_warning_" + code.lowercase(), call.name)
            assertEquals(code, if (code in PLURAL_CODES) "plural" else "string", call.kind)
        }
    }

    @Test
    fun `the arguments are passed in the order of the resources`() {
        fun call(code: String, vararg args: Pair<String, Any>): Call {
            val strings = FakeStrings()
            ToolWarning(code + " text", code, mapOf(*args)).localized(strings)
            return strings.calls.last()
        }
        assertEquals(
            listOf<Any>(2L, 9000L, 8000L, 16_000_000L, 4500L, 4000L),
            call(
                PdfWarningCodes.IMAGE_DOWNSAMPLED,
                PdfWarningCodes.ARG_IMAGE to 2L, PdfWarningCodes.ARG_WIDTH to 9000L, PdfWarningCodes.ARG_HEIGHT to 8000L,
                PdfWarningCodes.ARG_DECODED_WIDTH to 4500L, PdfWarningCodes.ARG_DECODED_HEIGHT to 4000L, PdfWarningCodes.ARG_MAX_PIXELS to 16_000_000L,
            ).args,
        )
        assertEquals(listOf<Any>("size(120)", "size(100)"), call(PdfWarningCodes.OUTPUT_NOT_SMALLER, PdfWarningCodes.ARG_SIZE_BYTES to 120L, PdfWarningCodes.ARG_SOURCE_BYTES to 100L).args)
        val characters = call(PdfWarningCodes.CHARACTERS_REPLACED, PdfWarningCodes.ARG_COUNT to 3L, PdfWarningCodes.ARG_LIMIT to 2048L)
        assertEquals(3, characters.quantity)
        assertEquals(listOf<Any>(3L, 2048L), characters.args)

        val strings = FakeStrings()
        val fields = ToolWarning("e", PdfWarningCodes.FIELDS_IGNORED, mapOf(PdfWarningCodes.ARG_COUNT to 25L, PdfWarningCodes.ARG_NAMES to "'a', 'b'", PdfWarningCodes.ARG_MORE to 5L))
        fields.localized(strings)
        assertEquals(listOf("tool_warning_fields_ignored_more", "tool_warning_fields_ignored"), strings.calls.map { it.name })
        assertEquals(listOf<Any>("'a', 'b'", 5L), strings.calls[0].args)
        assertEquals(5, strings.calls[0].quantity)
        assertEquals(listOf<Any>(25L, "plural:tool_warning_fields_ignored_more"), strings.calls[1].args)
        val none = FakeStrings()
        ToolWarning("e", PdfWarningCodes.FIELDS_IGNORED, mapOf(PdfWarningCodes.ARG_COUNT to 1L, PdfWarningCodes.ARG_NAMES to "'a'", PdfWarningCodes.ARG_MORE to 0L)).localized(none)
        assertEquals(listOf<Any>(1L, "'a'"), none.calls.single().args)
        // A count beyond Int picks the quantity of Int.MAX_VALUE and still shows the number. zh-CN: 超出 Int 的数量按 Int.MAX_VALUE 选复数形式, 仍显示原数.
        val huge = call(PdfWarningCodes.REMOTE_RESOURCES_BLOCKED, PdfWarningCodes.ARG_COUNT to 5_000_000_000L)
        assertEquals(Int.MAX_VALUE, huge.quantity)
        assertEquals(listOf<Any>(5_000_000_000L), huge.args)
    }

    @Test
    fun `every code has its resource in every locale`() {
        val expected = PdfWarningCodes.ALL.map { "tool_warning_" + it.lowercase() } + FIELDS_IGNORED_MORE
        val pluralNames = (PLURAL_CODES.map { "tool_warning_" + it.lowercase() } + FIELDS_IGNORED_MORE).toSet()
        val reflectedStrings = R.string::class.java.fields.map { it.name }.toSet()
        val reflectedPlurals = R.plurals::class.java.fields.map { it.name }.toSet()
        expected.forEach { name ->
            assertTrue(name, name in if (name in pluralNames) reflectedPlurals else reflectedStrings)
        }
        assertEquals(pluralNames, reflectedPlurals.filter { it.startsWith("tool_warning_") }.toSet())
        assertEquals(expected.toSet() - pluralNames, reflectedStrings.filter { it.startsWith("tool_warning_") }.toSet())

        val english = texts("values-en")
        assertEquals(expected.toSet(), english.keys.map { it.substringBefore('#') }.toSet())
        assertEquals(11, DIRECTORIES.size)
        DIRECTORIES.forEach { directory ->
            val localized = texts(directory)
            assertEquals(directory, expected.toSet(), localized.keys.map { it.substringBefore('#') }.toSet())
            localized.forEach { (key, value) ->
                assertTrue("$directory/$key is blank", value.isNotBlank())
                // A plural item has the placeholders of the English "other" item. zh-CN: 复数项的占位符与英文 other 项相同.
                val reference = english[key] ?: english.getValue(key.substringBefore('#') + "#other")
                assertEquals("$directory/$key placeholders", placeholders(reference), placeholders(value))
                assertTrue("$directory/$key has ASCII punctuation only", RepositoryFiles.nonAsciiPunctuation(value).isEmpty())
                if (directory == "values-ar") {
                    Regex("%[0-9][$]s").findAll(value).forEach { match ->
                        assertEquals("$directory/$key isolates ${match.value}", FSI, value.getOrNull(match.range.first - 1))
                        assertEquals("$directory/$key isolates ${match.value}", PDI, value.getOrNull(match.range.last + 1))
                    }
                }
            }
        }
        assertEquals("values and values-en plurals are identical", plurals("values-en"), plurals("values"))
    }

    @Test
    fun `the plurals carry the quantities of each language`() {
        DIRECTORIES.forEach { directory ->
            val quantities = QUANTITIES.getValue(directory)
            plurals(directory).forEach { (name, items) ->
                assertEquals("$directory/$name", quantities, items.keys.toList())
            }
        }
    }

    private fun complete(code: String): ToolWarning = ToolWarning(
        "$code text",
        code,
        PdfWarningCodes.ARGS.getValue(code).associateWith { name -> if (name in PdfWarningCodes.STRING_ARGS) "'$name'" else 3L },
    )

    /** The tool_warning texts of [directory], plural items as `name#quantity`. zh-CN: [directory] 的 tool_warning 文字, 复数项记作 `name#quantity`. */
    private fun texts(directory: String): Map<String, String> = buildMap {
        RepositoryFiles.strings(directory).filterKeys { it.startsWith("tool_warning_") }.forEach { (name, value) -> put(name, value) }
        plurals(directory).forEach { (name, items) -> items.forEach { (quantity, value) -> put("$name#$quantity", value) } }
    }

    private fun plurals(directory: String): Map<String, LinkedHashMap<String, String>> {
        val root = RepositoryFiles.xml("app/src/main/res/$directory/plurals.xml")
        return root.children("plurals").associate { plurals ->
            plurals.getAttribute("name") to LinkedHashMap<String, String>().also { items ->
                plurals.children("item").forEach { item -> items[item.getAttribute("quantity")] = item.textContent }
            }
        }
    }

    private fun placeholders(text: String): Set<String> = Regex("%[0-9][$][sd]").findAll(text).map { it.value }.toSet()

    private class Call(val kind: String, val name: String, val quantity: Int?, val args: List<Any>)

    private class FakeStrings : ToolWarningStrings {

        val calls = ArrayList<Call>()

        override fun string(id: Int, vararg args: Any): String {
            val name = NAMES.getValue(id)
            calls += Call("string", name, null, args.toList())
            return "string:$name"
        }

        override fun plural(id: Int, quantity: Int, vararg args: Any): String {
            val name = NAMES.getValue(id)
            calls += Call("plural", name, quantity, args.toList())
            return "plural:$name"
        }

        override fun size(bytes: Long): String = "size($bytes)"

    }

    private companion object {

        const val FIELDS_IGNORED_MORE = "tool_warning_fields_ignored_more"
        val FSI = 0x2068.toChar()
        val PDI = 0x2069.toChar()

        val PLURAL_CODES = setOf(
            PdfWarningCodes.IMAGES_UNDECODABLE, PdfWarningCodes.IMAGES_TOO_LARGE, PdfWarningCodes.FIELDS_IGNORED,
            PdfWarningCodes.SIGNATURES_INVALIDATED, PdfWarningCodes.REMOTE_RESOURCES_BLOCKED, PdfWarningCodes.CHARACTERS_REPLACED,
        )

        val DIRECTORIES = listOf(
            "values", "values-ar", "values-en", "values-es", "values-fr", "values-ja", "values-ko", "values-ru",
            "values-zh", "values-zh-rHK", "values-zh-rTW",
        )

        val QUANTITIES: Map<String, List<String>> = DIRECTORIES.associateWith { directory ->
            when (directory) {
                "values", "values-en" -> listOf("one", "other")
                "values-fr", "values-es" -> listOf("one", "many", "other")
                "values-ru" -> listOf("one", "few", "many", "other")
                "values-ar" -> listOf("zero", "one", "two", "few", "many", "other")
                else -> listOf("other")
            }
        }

        val NAMES: Map<Int, String> = (R.string::class.java.fields + R.plurals::class.java.fields)
            .filter { it.name.startsWith("tool_warning_") }
            .associate { it.getInt(null) to it.name }

    }

}

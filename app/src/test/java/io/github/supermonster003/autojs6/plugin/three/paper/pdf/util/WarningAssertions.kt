package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Asserts that [warning] has [code], exactly the argument names of [PdfWarningCodes.ARGS] for it, String values for
 * [PdfWarningCodes.STRING_ARGS] and integers for the others, and an [PdfWarning.argsJson] that reads back the same
 * (appendix D Q264 (b)).
 * zh-CN: 断言 [warning] 的代码为 [code], 参数名与 [PdfWarningCodes.ARGS] 中该代码的完全一致, [PdfWarningCodes.STRING_ARGS] 的值为
 * String, 其余为整数, 且 [PdfWarning.argsJson] 读回后相同 (附录 D Q264 (b)).
 */
internal fun assertWarning(warning: PdfWarning, code: String) {
    assertEquals(warning.text, code, warning.code)
    assertEquals(warning.text, PdfWarningCodes.ARGS.getValue(code).toSet(), warning.args.keys)
    warning.args.forEach { (name, value) ->
        if (name in PdfWarningCodes.STRING_ARGS) {
            assertTrue("$code.$name is a String: $value", value is String)
        } else {
            assertTrue("$code.$name is an integer: $value", value is Int || value is Long)
        }
    }
    val json = JSONObject(warning.argsJson())
    assertEquals(warning.args.keys, json.keys().asSequence().toSet())
    warning.args.forEach { (name, value) -> assertEquals("$code.$name", value.toString(), json.get(name).toString()) }
}

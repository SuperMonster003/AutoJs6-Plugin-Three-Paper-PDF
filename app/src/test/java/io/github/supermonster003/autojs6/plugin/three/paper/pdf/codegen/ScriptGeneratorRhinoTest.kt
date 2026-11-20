package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * E1 of roadmap P6.13: every generated Rhino script parses in the host's Rhino (the build's `testlibs/` copy of the
 * host commit) and, run against stubs, makes the calls of its request field by field ([ScriptExpectations]), for all
 * 13 tools with all fields and with only the required ones, through quotes, backslashes, line breaks, U+2028 / U+2029,
 * Chinese and emoji. Password placeholders left null stop the script before any call.
 * zh-CN: 路线图 P6.13 的 E1: 生成的每个 Rhino 脚本都能在宿主的 Rhino (构建使用的 `testlibs/` 中宿主提交的副本) 中解析, 在桩对象上运行时
 * 发出的调用与其请求逐字段相等 ([ScriptExpectations]); 覆盖全部 13 个工具的全字段与仅必填字段请求, 以及引号, 反斜杠, 换行,
 * U+2028 / U+2029, 中文与 emoji. 密码占位保持 null 时, 脚本在发出任何调用之前停止.
 */
class ScriptGeneratorRhinoTest {

    @Test
    fun fixturesCoverEveryTool() {
        assertEquals(ToolKind.entries.toSet(), ScriptFixtures.ALL.map { it.request.kind }.toSet())
    }

    @Test
    fun rhinoScriptsMakeTheCallsOfTheirRequest() {
        ScriptFixtures.ALL.forEach { case ->
            val script = ScriptGenerator.generate(case.request, ScriptStyle.RHINO)
            val calls = try {
                ScriptHarness.runRhino(ScriptHarness.fill(script.code), script.fileName)
            } catch (e: ScriptHarness.ScriptFailure) {
                throw AssertionError("${case.name}: ${e.message}\n${script.code}", e)
            }
            assertEquals("${case.name}\n${script.code}", ScriptExpectations.of(case.request), ScriptExpectations.canonical(calls))
        }
    }

    @Test
    fun placeholdersLeftNullStopTheScriptBeforeAnyCall() {
        val cases = ScriptFixtures.ALL.filter { case -> ScriptHarness.placeholdersOf(ScriptGenerator.generate(case.request, ScriptStyle.RHINO).code).isNotEmpty() }
        assertTrue(cases.size >= 10)
        cases.forEach { case ->
            val code = ScriptGenerator.generate(case.request, ScriptStyle.RHINO).code
            try {
                ScriptHarness.runRhino(code)
                fail("${case.name} ran without its passwords")
            } catch (e: ScriptHarness.ScriptFailure) {
                assertTrue("${case.name}: ${e.message}", e.message.orEmpty().contains("Fill in the password"))
                assertEquals(case.name, emptyList<ScriptHarness.Call>(), e.calls)
            }
        }
    }

    @Test
    fun placeholdersAreTheOnesTheRequestNeeds() {
        ScriptFixtures.ALL.forEach { case ->
            val request = case.request
            val expected = buildList {
                if (request.kind == ToolKind.MERGE) {
                    request.inputs.forEachIndexed { index, input -> if (input.password) add("password${index + 1}") }
                } else if (request.inputs.first().password) {
                    add("password")
                }
                if (request.kind == ToolKind.ENCRYPT) add("newPassword")
            }
            ScriptStyle.entries.forEach { style ->
                assertEquals("${case.name} $style", expected, ScriptHarness.placeholdersOf(ScriptGenerator.generate(request, style).code))
            }
        }
    }

    @Test
    fun guessedPathsAskToBeChecked() {
        val merge = ScriptFixtures.ALL.first { it.name == "merge-full" }
        val code = ScriptGenerator.generate(merge.request, ScriptStyle.RHINO).code
        assertTrue(code, code.contains("// Check item 3: the document provider did not tell their paths."))
        val image = ScriptFixtures.ALL.first { it.name == "from-images-full" }
        assertTrue(ScriptGenerator.generate(image.request, ScriptStyle.RHINO).code.contains("// Check item 3:"))
        val single = ScriptRequest.of(ToolKind.DECRYPT, listOf(ScriptFixtures.input("x.pdf", guessed = true)), ToolOptions.Decrypt, ScriptFixtures.output("x.pdf"))
        val line = ScriptGenerator.generate(single, ScriptStyle.RHINO).code.lines().first { it.startsWith("let input = ") }
        assertTrue(line, line.endsWith("; // Check this path: the document provider did not tell it."))
    }

}

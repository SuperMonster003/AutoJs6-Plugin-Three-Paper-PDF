package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of generated scripts (roadmap P6.13): no password reaches any style, the custom values of Q265 (b) are
 * written as entered, each style starts with its own header (`"nodejs";` with `require('pdf')`, `"bun";` with the
 * `pdf.control` check), file names and the `project.json` of the Node.js project folder.
 * zh-CN: 生成脚本的形态 (路线图 P6.13): 任何写法都不含密码, Q265 (b) 的自定义数值按输入写出, 每种写法有各自的文件头 (`"nodejs";` 与
 * `require('pdf')`, `"bun";` 与 `pdf.control` 检查), 文件名, 以及 Node.js 项目文件夹的 `project.json`.
 */
class ScriptGeneratorTest {

    private fun case(name: String) = ScriptFixtures.ALL.first { it.name == name }.request

    @Test
    fun noPasswordReachesAScript() {
        ScriptFixtures.ALL.forEach { case ->
            ScriptStyle.entries.forEach { style ->
                val script = ScriptGenerator.generate(case.request, style)
                ScriptFixtures.SECRETS.forEach { secret ->
                    assertFalse("${case.name} $style", script.code.contains(secret))
                    assertFalse("${case.name} $style", script.projectJson.orEmpty().contains(secret))
                }
            }
            assertFalse(case.name, ScriptFixtures.SECRETS.any { case.request.options.toString().contains(it) })
        }
        val encrypt = ScriptRequest.of(ToolKind.ENCRYPT, listOf(ScriptFixtures.input()), ToolOptions.Encrypt(ScriptFixtures.SECRETS[0]), ScriptFixtures.output("e.pdf"))
        assertEquals(ToolOptions.Encrypt(""), encrypt.options)
    }

    @Test
    fun passwordPlaceholdersCarryAComment() {
        ScriptFixtures.ALL.forEach { case ->
            ScriptStyle.entries.forEach { style ->
                val code = ScriptGenerator.generate(case.request, style).code
                code.lines().filter { PLACEHOLDER_LINE.containsMatchIn(it) }.forEach { line ->
                    assertTrue("${case.name} $style: $line", line.contains("; // "))
                }
                if (ScriptHarness.placeholdersOf(code).isNotEmpty()) {
                    assertTrue("${case.name} $style", code.contains("// Scripts never keep passwords: fill in the ones set to null below."))
                }
            }
        }
    }

    @Test
    fun customValuesAreWrittenAsEntered() {
        val expected = mapOf(
            "watermark-full" to listOf("rotation: 12.5", "opacity: 0.333", "fontSize: 37.5", "color: '#80112233'", "tile: true", "layer: 'under'", "pages: '1-2, 4'"),
            "watermark-image" to listOf("rotation: -36000", "opacity: 1", "width: 144.5"),
            "page-numbers-full" to listOf("fontSize: 9.5"),
            "compress-full" to listOf("imageDpi: 123", "imageQuality: 42"),
            "from-images-full" to listOf("margin: 12.5"),
            "to-images-full" to listOf("dpi: 300"),
        )
        expected.forEach { (name, parts) ->
            ScriptStyle.entries.forEach { style ->
                val code = ScriptGenerator.generate(case(name), style).code
                parts.forEach { part -> assertTrue("$name $style: $part\n$code", code.contains(part)) }
            }
        }
    }

    @Test
    fun compressOmitsNumbersEqualToThePreset() {
        ScriptStyle.entries.forEach { style ->
            val code = ScriptGenerator.generate(case("compress-preset"), style).code
            assertFalse(code, code.contains("imageDpi"))
            assertFalse(code, code.contains("imageQuality"))
            assertTrue(code, code.contains("preset: 'low'"))
        }
    }

    @Test
    fun eachStyleHasItsHeader() {
        ScriptFixtures.ALL.forEach { case ->
            val rhino = ScriptGenerator.generate(case.request, ScriptStyle.RHINO).code
            assertTrue(case.name, rhino.startsWith("// 3-Paper PDF: " + ScriptGenerator.titleOf(case.request.kind) + ".\n"))
            assertFalse(case.name, rhino.contains("await "))
            assertFalse(case.name, rhino.contains("require("))
            assertFalse(case.name, rhino.contains("host."))

            val node = ScriptGenerator.generate(case.request, ScriptStyle.NODE).code
            assertTrue(case.name, node.startsWith("\"nodejs\";\n\n// 3-Paper PDF: "))
            assertTrue(case.name, node.contains("    let pdf = require('pdf');\n"))
            assertTrue(case.name, node.contains("\"node\": { \"permissions\": "))
            assertFalse(case.name, node.contains("host."))
            assertTrue(case.name, node.endsWith("})().catch((e) => {\n    console.error(String(e));\n    process.exitCode = 1;\n});\n"))

            val bun = ScriptGenerator.generate(case.request, ScriptStyle.BUN).code
            assertTrue(case.name, bun.startsWith("\"bun\";\n\n// 3-Paper PDF: "))
            assertTrue(case.name, bun.contains("    if (!host.hasCapability('pdf.control')) {\n"))
            assertTrue(case.name, bun.contains("    let pdf = host.pdf;\n"))
            assertTrue(case.name, bun.indexOf("hasCapability") < bun.indexOf("let pdf = host.pdf;"))
            assertFalse(case.name, bun.contains("require("))
            assertTrue(case.name, bun.endsWith("})().catch((e) => {\n    console.error(String(e));\n    process.exitCode = 1;\n});\n"))
        }
    }

    @Test
    fun extractTextWritesTheFileTheWayOfItsStyle() {
        val request = case("extract-text-full")
        assertTrue(ScriptGenerator.generate(request, ScriptStyle.RHINO).code.contains("files.write(output, texts.join('\\n'));"))
        assertTrue(ScriptGenerator.generate(request, ScriptStyle.NODE).code.contains("await fs.promises.writeFile(output, texts.join('\\n'));"))
        assertTrue(ScriptGenerator.generate(request, ScriptStyle.BUN).code.contains("await Bun.write(output, texts.join('\\n'));"))
    }

    @Test
    fun fileNamesAndTheNodeProject() {
        ToolKind.entries.forEach { kind ->
            val request = ScriptFixtures.ALL.first { it.request.kind == kind }.request
            val base = ScriptGenerator.FILE_PREFIX + kind.id
            val rhino = ScriptGenerator.generate(request, ScriptStyle.RHINO)
            assertEquals(base + GeneratedScript.RHINO_SUFFIX, rhino.fileName)
            assertNull(rhino.projectJson)
            assertNull(rhino.projectName)
            val bun = ScriptGenerator.generate(request, ScriptStyle.BUN)
            assertEquals(base + GeneratedScript.BUN_SUFFIX, bun.fileName)
            assertNull(bun.projectJson)
            val node = ScriptGenerator.generate(request, ScriptStyle.NODE)
            assertEquals(base + GeneratedScript.NODE_SUFFIX, node.fileName)
            assertEquals(base, node.projectName)
            val project = JSONObject(node.projectJson!!)
            assertEquals(base, project.getString("name"))
            assertEquals("node", project.getString("type"))
            assertEquals(GeneratedScript.NODE_MAIN, project.getString("main"))
            val permissions = project.getJSONObject("node").getJSONArray("permissions").let { array -> List(array.length()) { array.getString(it) } }
            assertEquals(kind.id, ScriptGenerator.permissionsOf(kind), permissions)
            assertEquals(kind.id, if (kind == ToolKind.EXTRACT_TEXT) listOf("files") else listOf("files", "files.write"), permissions)
            assertTrue(node.code.contains("\"node\": { \"permissions\": " + permissions.joinToString(", ", "[", "]") { "\"$it\"" } + " }"))
        }
    }

    @Test
    fun scriptsAreTheSameForTheSameRequest() {
        ScriptFixtures.ALL.forEach { case ->
            ScriptStyle.entries.forEach { style ->
                assertEquals(case.name, ScriptGenerator.generate(case.request, style).code, ScriptGenerator.generate(case.request, style).code)
            }
        }
    }

    @Test
    fun styleIds() {
        assertEquals(listOf("rhino", "node", "bun"), ScriptStyle.entries.map { it.id })
        ScriptStyle.entries.forEach { assertEquals(it, ScriptStyle.of(it.id)) }
        assertNull(ScriptStyle.of("python"))
        assertNull(ScriptStyle.of(null))
    }

    private companion object {

        val PLACEHOLDER_LINE = Regex("let \\w*[Pp]assword\\d* = null;")

    }

}

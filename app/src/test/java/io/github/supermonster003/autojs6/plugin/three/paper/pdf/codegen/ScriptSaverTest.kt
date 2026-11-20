package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * "Save as .js" (roadmap P6.13): scripts go to `scripts/` under the output folder, a Node.js script as a project folder
 * with `main.js` and `project.json`; taken names get ` (n)` before the engine suffix and nothing is overwritten.
 * zh-CN: "保存为 .js" (路线图 P6.13): 脚本保存到输出文件夹下的 `scripts/`, Node.js 脚本保存为含 `main.js` 与 `project.json` 的项目文件夹;
 * 名称已被占用时在引擎后缀之前加 ` (n)`, 不覆盖任何文件.
 */
class ScriptSaverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val merge = ScriptFixtures.ALL.first { it.name == "merge-full" }.request

    @Test
    fun rhinoAndBunScriptsAreFilesWithUniqueNames() {
        val output = temp.newFolder("out")
        val rhino = ScriptGenerator.generate(merge, ScriptStyle.RHINO)
        val first = ScriptSaver.save(output, rhino)
        assertEquals(File(output, "scripts/3-paper-merge.js"), first)
        assertEquals(rhino.code, first.readText(Charsets.UTF_8))
        assertEquals(File(output, "scripts/3-paper-merge (1).js"), ScriptSaver.save(output, rhino))
        assertEquals(File(output, "scripts/3-paper-merge (2).js"), ScriptSaver.save(output, rhino))
        assertEquals(rhino.code, first.readText(Charsets.UTF_8))

        val bun = ScriptGenerator.generate(merge, ScriptStyle.BUN)
        assertEquals(File(output, "scripts/3-paper-merge.bun.js"), ScriptSaver.save(output, bun))
        val second = ScriptSaver.save(output, bun)
        assertEquals(File(output, "scripts/3-paper-merge (1).bun.js"), second)
        assertEquals(bun.code, second.readText(Charsets.UTF_8))
    }

    @Test
    fun nodeScriptsAreProjectFolders() {
        val output = temp.newFolder("out")
        val node = ScriptGenerator.generate(merge, ScriptStyle.NODE)
        val project = ScriptSaver.save(output, node)
        assertEquals(File(output, "scripts/3-paper-merge"), project)
        assertTrue(project.isDirectory)
        assertEquals(setOf("main.js", "project.json"), project.list()!!.toSet())
        assertEquals(node.code, File(project, "main.js").readText(Charsets.UTF_8))
        assertEquals("3-paper-merge", JSONObject(File(project, "project.json").readText(Charsets.UTF_8)).getString("name"))
        assertEquals(File(output, "scripts/3-paper-merge (1)"), ScriptSaver.save(output, node))
        assertEquals(File(output, "scripts/3-paper-merge.js"), ScriptSaver.save(output, ScriptGenerator.generate(merge, ScriptStyle.RHINO)))
    }

    @Test
    fun takenNamesOfAnyKindAreSkipped() {
        val output = temp.newFolder("out")
        File(output, "scripts/3-paper-merge.js").mkdirs()
        File(output, "scripts/3-paper-merge").writeText("a file", Charsets.UTF_8)
        assertEquals(File(output, "scripts/3-paper-merge (1).js"), ScriptSaver.save(output, ScriptGenerator.generate(merge, ScriptStyle.RHINO)))
        assertEquals(File(output, "scripts/3-paper-merge (1)"), ScriptSaver.save(output, ScriptGenerator.generate(merge, ScriptStyle.NODE)))
        assertEquals("a file", File(output, "scripts/3-paper-merge").readText(Charsets.UTF_8))
    }

    @Test
    fun aFileInTheWayOfTheScriptsFolderFails() {
        val output = temp.newFolder("out")
        File(output, ScriptSaver.FOLDER).writeText("", Charsets.UTF_8)
        assertThrows(IOException::class.java) { ScriptSaver.save(output, ScriptGenerator.generate(merge, ScriptStyle.RHINO)) }
    }

    @Test
    fun namesKeepTheEngineSuffix() {
        assertEquals("3-paper-merge.node.js", ScriptSaver.nameOf("3-paper-merge.node.js", 0))
        assertEquals("3-paper-merge (1).node.js", ScriptSaver.nameOf("3-paper-merge.node.js", 1))
        assertEquals("3-paper-merge (12).bun.js", ScriptSaver.nameOf("3-paper-merge.bun.js", 12))
        assertEquals("3-paper-merge (3).js", ScriptSaver.nameOf("3-paper-merge.js", 3))
        assertEquals("3-paper-merge (2)", ScriptSaver.nameOf("3-paper-merge", 2))
        assertEquals(".js (2)", ScriptSaver.nameOf(".js", 2))
    }

}

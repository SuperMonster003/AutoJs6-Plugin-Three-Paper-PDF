package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptHarness.Call
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The Node.js and Bun scripts of roadmap P6.13 under a real Node.js: every one passes `node --check`, and run against
 * the stubs of [ScriptHarness] (promises, `require` and the Bun host client replaced) it makes the same calls as the
 * Rhino script of its request, whose calls E1 checks field by field. A Bun script without the `pdf.control` grant, and
 * any script with a password placeholder left null, stops with an error and exit code 1. Needs `node` on the PATH:
 * skipped without it, failed on CI (`CI` set).
 * zh-CN: 路线图 P6.13 的 Node.js 与 Bun 脚本在真实 Node.js 下的检查: 每个都通过 `node --check`, 在 [ScriptHarness] 的桩对象上运行时
 * (Promise 形式, 替换了 `require` 与 Bun 宿主客户端) 发出的调用与其请求的 Rhino 脚本相同, 而 Rhino 脚本的调用由 E1 逐字段检查. 没有
 * `pdf.control` 授权的 Bun 脚本, 以及密码占位保持 null 的脚本, 以错误和退出码 1 停止. 需要 PATH 中有 `node`: 没有时跳过, 在 CI 上
 * (设置了 `CI`) 则失败.
 */
class ScriptGeneratorNodeTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun nodeAndBunScriptsPassNodeCheck() {
        val node = requireNode()
        val folder = temp.newFolder("check")
        ScriptFixtures.ALL.forEach { case ->
            listOf(ScriptStyle.NODE, ScriptStyle.BUN).forEach { style ->
                val script = ScriptGenerator.generate(case.request, style)
                val file = File(folder, case.name + "-" + script.fileName).apply { writeText(script.code, Charsets.UTF_8) }
                val (exit, output) = run(listOf(node, "--check", file.path))
                assertEquals("${case.name} $style: $output\n${script.code}", 0, exit)
            }
        }
    }

    @Test
    fun nodeAndBunScriptsMakeTheCallsOfTheRhinoScript() {
        val node = requireNode()
        val rhino = ScriptFixtures.ALL.associate { case ->
            case.name to ScriptHarness.runRhino(ScriptHarness.fill(ScriptGenerator.generate(case.request, ScriptStyle.RHINO).code)).filter { it.name != "files.ensureDir" }
        }
        listOf(ScriptStyle.NODE, ScriptStyle.BUN).forEach { style ->
            val results = runAll(node, style, granted = true) { ScriptHarness.fill(it) }
            ScriptFixtures.ALL.forEach { case ->
                val (calls, exitCode) = results.getValue(case.name)
                assertEquals("${case.name} $style exit code, calls $calls", 0, exitCode)
                if (style == ScriptStyle.BUN) assertEquals(case.name, ScriptHarness.call("host.hasCapability", ScriptGenerator.BUN_GRANT), calls.first())
                if (style == ScriptStyle.NODE && case.request.kind == ToolKind.EXTRACT_TEXT) {
                    assertTrue(case.name, ScriptHarness.call("fs.mkdir", File(case.request.output.path).parent.replace('\\', '/'), mapOf("recursive" to true)) in calls)
                }
                val compared = calls.filter { it.name != "host.hasCapability" && it.name != "fs.mkdir" }
                assertEquals("${case.name} $style", rhino.getValue(case.name), compared)
            }
        }
    }

    @Test
    fun bunScriptsStopWithoutTheGrant() {
        val node = requireNode()
        val results = runAll(node, ScriptStyle.BUN, granted = false) { ScriptHarness.fill(it) }
        ScriptFixtures.ALL.forEach { case ->
            val (calls, exitCode) = results.getValue(case.name)
            assertEquals(case.name, 1, exitCode)
            assertEquals(
                case.name,
                listOf(
                    ScriptHarness.call("host.hasCapability", ScriptGenerator.BUN_GRANT),
                    ScriptHarness.call("console.error", "Error: Allow \"${ScriptGenerator.BUN_GRANT}\" for the Bun runtime in the Plugin Center first."),
                ),
                calls,
            )
        }
    }

    @Test
    fun placeholdersLeftNullStopNodeAndBunScripts() {
        val node = requireNode()
        val names = ScriptFixtures.ALL.filter { ScriptHarness.placeholdersOf(ScriptGenerator.generate(it.request, ScriptStyle.NODE).code).isNotEmpty() }.map { it.name }.toSet()
        assertTrue(names.size >= 10)
        listOf(ScriptStyle.NODE, ScriptStyle.BUN).forEach { style ->
            val results = runAll(node, style, granted = true) { it }
            names.forEach { name ->
                val (calls, exitCode) = results.getValue(name)
                assertEquals("$name $style", 1, exitCode)
                val pdfCalls = calls.filter { it.name != "host.hasCapability" }
                assertEquals("$name $style: $calls", 1, pdfCalls.size)
                assertEquals("$name $style", "console.error", pdfCalls[0].name)
                assertTrue("$name $style: $calls", (pdfCalls[0].args[0] as String).contains("Fill in the password"))
            }
        }
    }

    /**
     * Runs the [style] scripts of every fixture, [prepare]d, in one Node.js process, and answers their calls and exit
     * codes by fixture name.
     * zh-CN: 在一个 Node.js 进程中运行每个夹具经 [prepare] 处理后的 [style] 脚本, 按夹具名称返回其调用与退出码.
     */
    private fun runAll(node: String, style: ScriptStyle, granted: Boolean, prepare: (String) -> String): Map<String, Pair<List<Call>, Int>> {
        val folder = temp.newFolder()
        ScriptFixtures.ALL.forEach { case ->
            File(folder, case.name + ".js").writeText(prepare(ScriptGenerator.generate(case.request, style).code), Charsets.UTF_8)
        }
        val harness = File(temp.root, "harness.js").apply { if (!exists()) writeText(HARNESS, Charsets.UTF_8) }
        val (exit, output) = run(listOf(node, harness.path, style.id, if (granted) "granted" else "denied", folder.path), stderr = false)
        assertEquals(output, 0, exit)
        val results = JSONObject(output)
        return ScriptFixtures.ALL.associate { case ->
            val result = results.getJSONObject(case.name + ".js")
            case.name to (ScriptHarness.callsOf(result.getJSONArray("calls")) to result.getInt("exitCode"))
        }
    }

    /** Runs [command]; answers its exit code and output (stdout, with stderr when [stderr]). zh-CN: 运行 [command], 返回退出码与输出. */
    private fun run(command: List<String>, stderr: Boolean = true): Pair<Int, String> {
        val errors = temp.newFile()
        val process = ProcessBuilder(command).redirectError(if (stderr) ProcessBuilder.Redirect.PIPE else ProcessBuilder.Redirect.to(errors)).redirectErrorStream(stderr).start()
        process.outputStream.close()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            fail("timed out: $command")
        }
        val exit = process.exitValue()
        return exit to if (exit == 0 || stderr) output else output + errors.readText(Charsets.UTF_8)
    }

    private fun requireNode(): String {
        val found = runCatching {
            val process = ProcessBuilder(NODE, "--version").redirectErrorStream(true).start()
            process.inputStream.readBytes()
            process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0
        }.getOrDefault(false)
        if (!found && System.getenv("CI") != null) fail("node is needed to check the generated scripts on CI")
        assumeTrue("node is not on the PATH", found)
        return NODE
    }

    private companion object {

        const val NODE = "node"

        /** Runs every `.js` file of a folder against promise stubs. zh-CN: 在 Promise 形式的桩对象上运行文件夹中的每个 `.js` 文件. */
        val HARNESS = "'use strict';\n" + ScriptHarness.STUBS + "\n" + """
            |const fs = require('fs');
            |const path = require('path');
            |const style = process.argv[2];
            |const granted = process.argv[3] === 'granted';
            |const folder = process.argv[4];
            |const results = {};
            |(async () => {
            |    for (const name of fs.readdirSync(folder).filter((name) => name.endsWith('.js')).sort()) {
            |        const source = fs.readFileSync(path.join(folder, name), 'utf8');
            |        const calls = [];
            |        const record = (call, args) => calls.push({ name: call, args: Array.prototype.slice.call(args) });
            |        const stubs = __makeStubs(record, (value) => Promise.resolve(value), (texts) => ({ texts: texts }));
            |        globalThis.__host = {
            |            hasCapability: (capability) => { record('host.hasCapability', [capability]); return granted; },
            |            pdf: stubs.pdf
            |        };
            |        const requireStub = (id) => {
            |            if (id === 'pdf') return stubs.pdf;
            |            if (id === 'fs') return stubs.fs;
            |            if (id === 'path') return path.posix;
            |            throw new Error('unexpected module ' + id);
            |        };
            |        const host = 'data:text/javascript,export function createHostClient() { return globalThis.__host; }';
            |        const proc = { env: { AUTOJS6_HOST_CLIENT_MODULE: host }, exitCode: 0 };
            |        const start = source.indexOf('(async () => {');
            |        if (start < 0) throw new Error(name + ' (' + style + '): no async body');
            |        const run = new Function('require', 'process', 'console', 'Bun', source.substring(0, start) + 'return ' + source.substring(start));
            |        await run(requireStub, proc, stubs.console, stubs.Bun);
            |        results[name] = { calls: calls, exitCode: proc.exitCode };
            |    }
            |    process.stdout.write(JSON.stringify(results));
            |})().catch((e) => {
            |    process.stderr.write(String((e && e.stack) || e));
            |    process.exit(2);
            |});
        """.trimMargin() + "\n"

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.samples

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptHarness.Call
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms.AppearanceDrawer
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms.FormFields
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms.FormFill
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * The samples of roadmap P6.14: the five Rhino samples of the 1.1.0 methods (forms, watermark and page numbers,
 * searchable PDF, compression, document properties) run in the host's Rhino against [SampleStubs], with and without the
 * fixtures, and every `pdf` call they make, with its option keys, is in the signature table of the documentation
 * (`api/pdf.md`). On a 1.0.x plugin each stops after one 1.1.0 call with a readable hint, the searchable sample stops
 * with a hint when no OCR engine is installed, and every sample stops at once when the plugin is not available. The
 * form the forms sample writes when there is no fixture is a valid AcroForm for PDFBox. The Node.js and Bun samples pass
 * `node --check` and run against the same stubs (needs `node` on the PATH: skipped without it, failed on CI). The host
 * copies are compared by `.python/check_host_samples.py`; the device runs belong to P6.15 (`.python/device_samples_run.py`).
 * zh-CN: 路线图 P6.14 的样例: 1.1.0 方法的五个 Rhino 样例 (表单, 水印与页码, 可搜索 PDF, 压缩, 文档属性) 在宿主的 Rhino 中以
 * [SampleStubs] 运行, 有夹具与没有夹具两种情况都跑, 它们发出的每个 `pdf` 调用及其选项键都在文档 (`api/pdf.md`) 的签名表里. 插件为
 * 1.0.x 时每个样例在一次 1.1.0 调用后给出可读的提示并停止, 没有 OCR 引擎时可搜索样例给出提示并停止, 插件不可用时每个样例立即停止.
 * 没有夹具时表单样例写出的表单是 PDFBox 能读的 AcroForm. Node.js 与 Bun 样例通过 `node --check` 并在同一套桩对象上运行 (需要 PATH
 * 中有 `node`: 没有时跳过, 在 CI 上则失败). 宿主副本由 `.python/check_host_samples.py` 比对; 设备运行属于 P6.15
 * (`.python/device_samples_run.py`).
 */
class SamplesScriptTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** One Rhino run. zh-CN: 一次 Rhino 运行. */
    private class Run(val calls: List<Call>, val summary: JSONObject, val written: JSONObject, val logs: List<String>) {

        fun pdfCalls() = calls.filter { it.name.startsWith("pdf.") }

        fun v110Calls() = pdfCalls().filter { it.name.removePrefix("pdf.") in SampleStubs.V110 }

    }

    @Test
    fun rhinoSamplesRunAgainstTheStubsWithDocumentedCalls() {
        SAMPLES.forEach { sample ->
            listOf(true, false).forEach { fixtures ->
                val run = runRhino(sample, "normal", fixtures)
                val label = "$sample (fixtures: $fixtures)\n${run.logs.joinToString("\n")}"
                assertEquals(label, 0, run.summary.getJSONArray("errors").length())
                assertFalse(label, run.summary.has("unsupported"))
                assertEquals(label, emptyList<String>(), problemsOf(run.calls, node = false))
                assertTrue(label, run.v110Calls().isNotEmpty())
                expect(sample, fixtures, run, label)
            }
        }
    }

    @Test
    fun rhinoSamplesStopWithAHintOnA10xPlugin() {
        SAMPLES.forEach { sample ->
            listOf(true, false).forEach { fixtures ->
                val run = runRhino(sample, "unsupported", fixtures)
                val label = "$sample (fixtures: $fixtures)\n${run.logs.joinToString("\n")}"
                assertEquals(label, SampleStubs.UNSUPPORTED_MESSAGE, run.summary.getString("unsupported"))
                assertEquals(label, 0, run.summary.getJSONArray("errors").length())
                assertEquals(label, 1, run.v110Calls().size)
                assertTrue(label, run.logs.any { it.startsWith("error: ") && it.contains("更新到 1.1.0 或以上") })
                assertEquals(label, emptyList<String>(), problemsOf(run.calls, node = false))
            }
        }
    }

    @Test
    fun theSearchableSampleStopsWithAHintWithoutAnOcrEngine() {
        listOf(true, false).forEach { fixtures ->
            val run = runRhino(SEARCHABLE, "noOcr", fixtures)
            val label = "fixtures: $fixtures\n${run.logs.joinToString("\n")}"
            assertTrue(label, run.summary.getString("ocrUnavailable").isNotEmpty())
            assertFalse(label, run.summary.has("unsupported"))
            assertEquals(label, 0, run.summary.getJSONArray("errors").length())
            assertEquals(label, listOf("pdf.makeSearchable"), run.v110Calls().map { it.name })
            assertFalse(label, run.calls.any { it.name == "ocr.detect" })
            assertTrue(label, run.logs.any { it.startsWith("error: ") && it.contains("没有可用的 OCR 引擎") })
            assertTrue(label, run.logs.any { it.contains("ML Kit OCR, Paddle OCR 或 Rapid OCR") })
        }
    }

    @Test
    fun rhinoSamplesStopAtOnceWhenThePluginIsNotAvailable() {
        SAMPLES.forEach { sample ->
            val run = runRhino(sample, "unavailable", fixtures = true)
            assertEquals(sample, listOf("pdf.isAvailable"), run.pdfCalls().map { it.name })
            assertEquals(sample, listOf("PLUGIN_UNAVAILABLE"), ScriptHarness.norm(run.summary.getJSONArray("errors")))
        }
    }

    @Test
    fun theDemoFormIsAValidAcroForm() {
        val run = runRhino(FORMS, "normal", fixtures = false)
        val text = run.written.getString(DEMO_FORM)
        assertTrue(text.all { it.code < 128 })
        val startxref = Regex("startxref\n(\\d+)\n%%EOF\n$").find(text)!!.groupValues[1].toInt()
        assertTrue(text.startsWith("xref\n0 10\n0000000000 65535 f \n", startxref))
        val entries = text.substring(startxref).lines().drop(3).take(9)
        entries.forEachIndexed { index, line ->
            assertTrue(line, Regex("\\d{10} 00000 n ").matches(line))
            assertTrue(line, text.startsWith("${index + 1} 0 obj\n", line.take(10).toInt()))
        }
        PDDocument.load(text.toByteArray(Charsets.US_ASCII)).use { document ->
            val fields = FormFields.terminals(FormFields.acroForm(document))
            assertEquals(listOf("name" to "text", "agree" to "checkbox"), fields.map { it.name to it.type })
            assertEquals("Ada", FormFields.value(fields[0]))
            assertEquals("Ada", FormFields.defaultValue(fields[0]))
            assertEquals(false, FormFields.value(fields[1]))
            val payload = FormFill.parse(PdfPayload.parse("""{ "values": { "name": "AutoJs6", "agree": true } }""".toByteArray(Charsets.UTF_8)))
            val drawer = AppearanceDrawer { doc, requests, _ ->
                requests.map { PDFormXObject(doc).apply { bBox = PDRectangle(0f, 0f, ceil(it.width).toFloat(), ceil(it.height).toFloat()) } }
            }
            assertEquals(2, FormFill.apply(document, payload, false, drawer).filled)
        }
    }

    @Test
    fun headersNameTheVersionsTheSamplesNeed() {
        SAMPLES.forEach { sample ->
            val header = RepositoryFiles.text("samples/$sample").substringBefore("*/")
            assertTrue(sample, header.lines()[1].endsWith(" [v6.8.1+]"))
            assertTrue(sample, header.contains("AutoJs6 6.8.1 或以上"))
            assertTrue(sample, header.contains("3-Paper PDF 1.1.0 或以上"))
        }
        mapOf(NODE_SAMPLE to "Node.js Runtime 插件 2.2.0 或以上", BUN_SAMPLE to "Bun Runtime 插件 0.23.0 或以上").forEach { (sample, runtime) ->
            val header = RepositoryFiles.text(sample).substringBefore("*/")
            listOf("AutoJs6 6.8.1 或以上", runtime, "3-Paper PDF 插件 1.0.0 或以上", "3-Paper PDF 1.1.0 或以上", "UNSUPPORTED").forEach {
                assertTrue("$sample: $it", header.contains(it))
            }
        }
    }

    @Test
    fun theSignatureCheckReportsUndocumentedCalls() {
        val calls = listOf(
            ScriptHarness.call("pdf.watermark", "a.pdf", "b.pdf", mapOf("text" to "x", "image" to "y.png")),
            ScriptHarness.call("pdf.stamp", "a.pdf", "b.pdf", mapOf("items" to listOf(mapOf("type" to "image", "image" to "y.png", "text" to "x")))),
            ScriptHarness.call("pdf.compress", "a.pdf", "b.pdf", mapOf("level" to 1)),
            ScriptHarness.call("pdf.setOutline", "a.pdf", "b.pdf", listOf(mapOf("title" to "t", "page" to 1, "children" to listOf(mapOf("title" to "c", "href" to "x"))))),
            ScriptHarness.call("pdf.fillForm", "a.pdf", "b.pdf"),
            ScriptHarness.call("pdf.info", "a.pdf", mapOf<String, Any>(), "extra"),
            ScriptHarness.call("pdf.erase", "a.pdf"),
            ScriptHarness.call("pdf.render", "a.pdf", 1, mapOf("encoding" to "base64")),
        )
        val problems = problemsOf(calls, node = false)
        assertEquals(problems.joinToString("\n"), 8, problems.size)
        assertEquals(emptyList<String>(), problemsOf(listOf(ScriptHarness.call("pdf.render", "a.pdf", 1, mapOf("encoding" to "base64", "timeoutMs" to 5))), node = true))
    }

    @Test
    fun nodeAndBunSamplesPassNodeCheckAndRunAgainstTheStubs() {
        val node = requireNode()
        listOf(NODE_SAMPLE, BUN_SAMPLE).forEach { sample ->
            val (exit, output) = run(listOf(node, "--check", RepositoryFiles.path(sample).toString()))
            assertEquals("$sample: $output", 0, exit)
        }
        val harness = temp.newFile("harness.js").apply { writeText(HARNESS, Charsets.UTF_8) }

        runScript(node, harness, "node", "normal", NODE_SAMPLE).let { (calls, summary, label) ->
            assertEquals(label, 0, summary.getJSONArray("errors").length())
            assertEquals(label, emptyList<String>(), problemsOf(calls, node = true))
            val v110 = summary.getJSONObject("v110")
            assertEquals(label, 6, v110.getInt("stamp"))
            assertEquals(label, "Node.js sample", v110.getString("title"))
            assertEquals(label, 0, v110.getInt("fields"))
            assertEquals(label, 2, v110.getInt("filled"))
            assertEquals(label, 0, v110.getJSONObject("compress").getJSONArray("warningCodes").length())
        }
        runScript(node, harness, "bun", "normal", BUN_SAMPLE).let { (calls, summary, label) ->
            assertEquals(label, 0, summary.getJSONArray("errors").length())
            assertEquals(label, ScriptHarness.call("host.hasCapability", "pdf.control"), calls.first())
            assertEquals(label, emptyList<String>(), problemsOf(calls, node = true))
            val v110 = summary.getJSONObject("v110")
            assertEquals(label, 6, v110.getInt("stamp"))
            assertEquals(label, listOf("Start", "End"), ScriptHarness.norm(v110.getJSONArray("outline")))
            assertEquals(label, 0, v110.getInt("formFieldCount"))
            assertEquals(label, 11, v110.getInt("fields"))
            assertEquals(label, 1, v110.getInt("filled"))
        }
        listOf("node" to NODE_SAMPLE, "bun" to BUN_SAMPLE).forEach { (style, sample) ->
            runScript(node, harness, style, "unsupported", sample).let { (calls, summary, label) ->
                assertEquals(label, 0, summary.getJSONArray("errors").length())
                assertEquals(label, SampleStubs.UNSUPPORTED_MESSAGE, summary.getJSONObject("v110").getString("unsupported"))
                assertEquals(label, 1, calls.count { it.name.startsWith("pdf.") && it.name.removePrefix("pdf.") in SampleStubs.V110 })
                assertEquals(label, emptyList<String>(), problemsOf(calls, node = true))
            }
        }
    }

    /** The expectations of a normal run of [sample]. zh-CN: [sample] 正常运行时的预期. */
    private fun expect(sample: String, fixtures: Boolean, run: Run, label: String) {
        val summary = run.summary
        when (sample) {
            FORMS -> {
                assertEquals(label, if (fixtures) 11 else 2, summary.getJSONObject("forms").getInt("count"))
                val fill = summary.getJSONObject("fill")
                assertEquals(label, if (fixtures) 9 else 2, fill.getInt("filled"))
                assertEquals(label, fill.getInt("filled"), fill.getInt("matched"))
                val recover = summary.getJSONObject("recover")
                assertEquals(label, "INVALID_ARGUMENT", recover.getString("strict"))
                assertEquals(label, listOf("FIELDS_IGNORED"), ScriptHarness.norm(recover.getJSONArray("ignoreMissing")))
                assertEquals(label, "Ada", recover.getString("restored"))
                assertEquals(label, "Ada", recover.getString("defaultValue"))
                assertEquals(label, "INVALID_ARGUMENT", recover.getString("wrongType"))
                val flatten = summary.getJSONObject("flatten")
                assertEquals(label, 0, flatten.getInt("fields"))
                assertEquals(label, 0, flatten.getInt("formFieldCount"))
                assertEquals(label, if (fixtures) listOf(1.0, 2.0) else listOf(1.0), ScriptHarness.norm(flatten.getJSONArray("hits")))
                assertEquals(label, fixtures, run.calls.none { it.name == "files.write" && it.args[0] == DEMO_FORM })
            }
            WATERMARK -> {
                val pages = if (fixtures) 6 else 3
                listOf("watermark", "tile", "image").forEach { assertEquals("$label $it", pages, summary.getJSONObject(it).getInt("pageCount")) }
                assertEquals(label, pages, summary.getJSONObject("pageNumbers").getInt("pageCount"))
                assertTrue(label, summary.getJSONObject("pageNumbers").getBoolean("found"))
                assertEquals(label, 2, summary.getJSONObject("stamp").getInt("items"))
                assertTrue(label, summary.getJSONObject("stamp").getBoolean("secondPage"))
            }
            SEARCHABLE -> {
                val made = summary.getJSONObject("makeSearchable")
                assertEquals(label, listOf(1, 1, 0, 2), listOf("ocrPages", "layerPages", "skippedPages", "lines").map { made.getInt(it) })
                assertEquals(label, "ocr", made.getJSONArray("stages").getString(0))
                assertEquals(label, listOf(1.0), ScriptHarness.norm(summary.getJSONObject("read").getJSONArray("hits")))
                assertEquals(label, listOf(0, 1), listOf("ocrPages", "skippedPages").map { summary.getJSONObject("again").getInt(it) })
                val layer = summary.getJSONObject("addTextLayer")
                assertEquals(label, listOf(2, 1, 0, 2), listOf("detected", "layerPages", "skippedPages", "lines").map { layer.getInt(it) })
                assertEquals(label, 1, run.calls.count { it.name == "ocr.detect" })
                assertEquals(label, 1, run.calls.count { it.name == "img.recycle" })
            }
            COMPRESS -> {
                assertTrue(label, summary.getJSONObject("low").getBoolean("kept"))
                assertTrue(label, summary.getJSONObject("low").getInt("sizeBytes") < summary.getJSONObject("low").getInt("sourceBytes"))
                assertFalse(label, summary.getJSONObject("high").getBoolean("kept"))
                assertTrue(label, summary.getJSONObject("custom").getBoolean("kept"))
                assertEquals(label, listOf(OUT + "/compress/high.pdf"), run.calls.filter { it.name == "files.remove" }.map { it.args[0] })
            }
            PROPERTIES -> {
                val info = summary.getJSONObject("setInfo")
                assertEquals(label, "3-Paper PDF 样例", info.getString("title"))
                assertEquals(label, "AutoJs6", info.getString("author"))
                assertTrue(label, info.isNull("creator"))
                val outline = summary.getJSONObject("setOutline")
                assertEquals(label, if (fixtures) listOf(4, 5) else listOf(0, 2), listOf(outline.getInt("before"), outline.getInt("after")))
                assertEquals(label, if (fixtures) "附录 (样例添加)" else "正文", outline.getString("last"))
                assertEquals(label, 0, summary.getInt("cleared"))
            }
            else -> fail("no expectations for $sample")
        }
    }

    /**
     * Runs the Rhino [sample] against the stubs in [mode]; a script error fails with the console output.
     * zh-CN: 以 [mode] 在桩对象上运行 Rhino 样例 [sample]; 脚本出错时连同控制台输出一起失败.
     */
    private fun runRhino(sample: String, mode: String, fixtures: Boolean): Run {
        val context = ContextFactory.getGlobal().enterContext()
        try {
            context.languageVersion = Context.VERSION_ES6
            context.setInterpretedMode(true)
            val scope = context.initStandardObjects()
            context.evaluateString(scope, SampleStubs.JS + "\n" + rhinoPrelude(mode, fixtures), "stubs.js", 1, null)
            val failure = runCatching { context.evaluateString(scope, RepositoryFiles.text("samples/$sample"), sample, 1, null) }.exceptionOrNull()
            val report = JSONObject(Context.toString(context.evaluateString(scope, "JSON.stringify({ calls: __calls, report: __s.report() })", "report.js", 1, null)))
            val written = report.getJSONObject("report").getJSONObject("written")
            val logs = strings(report.getJSONObject("report").getJSONArray("logs"))
            val label = "$sample ($mode, fixtures: $fixtures)"
            if (failure != null) throw AssertionError("$label: ${failure.message}\n${logs.joinToString("\n")}", failure)
            assertTrue("$label wrote no summary\n${logs.joinToString("\n")}", written.has(SUMMARY))
            return Run(ScriptHarness.callsOf(report.getJSONArray("calls")), JSONObject(written.getString(SUMMARY)), written, logs)
        } finally {
            Context.exit()
        }
    }

    private fun rhinoPrelude(mode: String, fixtures: Boolean) = """
        |var __calls = [];
        |var __s = __sampleStubs({
        |    mode: '$mode', fixtures: $fixtures, node: false, args: { output: '$SUMMARY' },
        |    record: function (name, args) { __calls.push({ name: name, args: args }); },
        |    wrap: function (value) { return value; },
        |    fail: function (e) { throw e; }
        |});
        |var pdf = __s.pdf;
        |var files = __s.files;
        |var console = __s.console;
        |var engines = __s.engines;
        |var ocr = __s.ocr;
    """.trimMargin()

    /** One Node.js or Bun run: calls, summary and a label with the console output. zh-CN: 一次 Node.js 或 Bun 运行. */
    private data class ScriptRun(val calls: List<Call>, val summary: JSONObject, val label: String)

    private fun runScript(node: String, harness: File, style: String, mode: String, sample: String): ScriptRun {
        val (exit, output) = run(listOf(node, harness.path, style, mode, RepositoryFiles.path(sample).toString()), stderr = false)
        assertEquals("$sample ($style, $mode): $output", 0, exit)
        val result = JSONObject(output)
        val label = "$sample ($style, $mode)\n${strings(result.getJSONArray("logs")).joinToString("\n")}"
        return ScriptRun(ScriptHarness.callsOf(result.getJSONArray("calls")), result.getJSONObject("summary"), label)
    }

    private fun strings(array: JSONArray): List<String> = List(array.length()) { array.getString(it) }

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
        if (!found && System.getenv("CI") != null) fail("node is needed to check the samples on CI")
        assumeTrue("node is not on the PATH", found)
        return NODE
    }

    /** Checks one argument; problems are added with [at] naming the place. zh-CN: 检查一个参数; 问题连同位置 [at] 一起加入. */
    private fun interface Shape {
        fun check(value: Any?, at: String, problems: MutableList<String>)
    }

    /** A positional parameter. zh-CN: 一个位置参数. */
    private class Param(val name: String, val required: Boolean, val shape: Shape = ANY)

    private companion object {

        const val NODE = "node"
        const val FORMS = "pdf-forms.js"
        const val WATERMARK = "pdf-watermark.js"
        const val SEARCHABLE = "pdf-searchable.js"
        const val COMPRESS = "pdf-compress.js"
        const val PROPERTIES = "pdf-properties.js"
        val SAMPLES = listOf(FORMS, WATERMARK, SEARCHABLE, COMPRESS, PROPERTIES)
        const val NODE_SAMPLE = "samples/node/pdf-node/main.js"
        const val BUN_SAMPLE = "samples/pdf.bun.js"

        /** Where the Rhino samples write their summary in the tests. zh-CN: 测试中 Rhino 样例写入摘要的位置. */
        const val SUMMARY = "/sdcard/summary.json"
        const val OUT = "/sdcard/Documents/3-Paper PDF/samples"
        const val DEMO_FORM = "$OUT/forms/demo-form.pdf"
        const val UNDEFINED = "<undefined>"

        val ANY = Shape { _, _, _ -> }

        /** An object with only the [keys]; [rule] checks the object as a whole. zh-CN: 只含 [keys] 的对象; [rule] 检查整个对象. */
        fun obj(keys: Map<String, Shape>, rule: ((Map<*, *>, String, MutableList<String>) -> Unit)? = null) = Shape { value, at, problems ->
            if (value !is Map<*, *>) {
                problems += "$at: not an object: $value"
                return@Shape
            }
            value.forEach { (key, item) ->
                val shape = keys[key]
                if (shape == null) problems += "$at: undocumented key $key" else shape.check(item, "$at.$key", problems)
            }
            rule?.invoke(value, at, problems)
        }

        fun obj(vararg keys: String, nested: Map<String, Shape> = emptyMap()) = obj(keys.associateWith { nested[it] ?: ANY })

        fun items(item: Shape) = Shape { value, at, problems ->
            if (value !is List<*>) problems += "$at: not an array: $value" else value.forEachIndexed { index, it -> item.check(it, "$at[$index]", problems) }
        }

        /** [shape] for an object, anything else (`true`, a string) as it is. zh-CN: 对象按 [shape] 检查, 其他值 (`true`, 字符串) 照收. */
        fun orScalar(shape: Shape) = Shape { value, at, problems -> if (value is Map<*, *>) shape.check(value, at, problems) }

        fun nullable(shape: Shape) = Shape { value, at, problems -> if (value != null) shape.check(value, at, problems) }

        fun outlineItem(): Shape = Shape { value, at, problems ->
            obj(mapOf("title" to ANY, "page" to ANY, "level" to ANY, "children" to items(outlineItem()))).check(value, at, problems)
        }

        val PLACEMENT = listOf("pages", "position", "offsetX", "offsetY", "rotation", "opacity", "tile", "gapX", "gapY", "layer", "margin")
        val STYLE = listOf("pages", "fontSize", "color", "opacity", "bold", "italic", "offsetX", "offsetY", "margin", "layer")

        /** A stamp item: text keys only on text items, image keys only on image items. zh-CN: 印章项: 文字键只用于文字项, 图片键只用于图片项. */
        val STAMP_ITEM = Shape { value, at, problems ->
            val own = when (val type = (value as? Map<*, *>)?.get("type")) {
                "text" -> listOf("text", "fontSize", "color", "bold", "italic", "pageOffset")
                "image" -> listOf("image", "width", "height", "scale")
                else -> {
                    problems += "$at: type $type"
                    return@Shape
                }
            }
            obj(*(listOf("type") + PLACEMENT + own).toTypedArray()).check(value, at, problems)
        }

        /**
         * The signature table of `api/pdf.md` (Rhino style, outputs always given). Node.js and Bun add `signal` and
         * `timeoutMs` to every options object, and `output` and `encoding` to those of `render`.
         * zh-CN: `api/pdf.md` 的签名表 (Rhino 写法, 总是给出输出). Node.js 与 Bun 的每个选项对象另有 `signal` 与 `timeoutMs`, `render` 的
         * 选项另有 `output` 与 `encoding`.
         */
        fun table(node: Boolean): Map<String, List<Param>> {
            val extra = if (node) listOf("signal", "timeoutMs") else emptyList()
            fun optionsOf(keys: List<String>, nested: Map<String, Shape> = emptyMap(), required: Boolean = false, rule: ((Map<*, *>, String, MutableList<String>) -> Unit)? = null) =
                Param("options", required, obj((keys + extra).associateWith { nested[it] ?: ANY }, rule))

            fun options(vararg keys: String) = optionsOf(keys.toList())
            val path = Param("path", true)
            val output = Param("output", false)
            val render = listOf("dpi", "scale", "width", "height", "transparent", "password")
            val textLine = obj("text", "bounds")
            val layer = obj("page", "width", "height", "lines", nested = mapOf("lines" to items(textLine)))
            val oneOf: (Map<*, *>, String, MutableList<String>) -> Unit = { value, at, problems ->
                if (listOf("text", "image").count { value.containsKey(it) } != 1) problems += "$at: exactly one of text and image"
            }
            return mapOf(
                "isAvailable" to emptyList(),
                "info" to listOf(path, options("password")),
                "text" to listOf(path, options("pages", "layout", "ocr", "ocrDpi", "ocrOptions", "onProgress", "password")),
                "search" to listOf(path, Param("keyword", true), options("pages", "caseSensitive", "wholeWord", "limit", "password")),
                "outline" to listOf(path, options("password")),
                "render" to listOf(path, Param("page", true), optionsOf(render + if (node) listOf("output", "encoding") else emptyList())),
                "renderToFile" to listOf(path, Param("page", true), output, optionsOf(listOf("format", "quality") + render)),
                "merge" to listOf(Param("inputs", true), output, options("outline", "passwords", "displayNames", "onProgress")),
                "extract" to listOf(path, Param("output", true), Param("pages", true), options("onProgress", "password")),
                "fromImages" to listOf(Param("paths", true), output, options("pageSize", "margin", "fit", "orientation", "quality", "onProgress")),
                "fromText" to listOf(Param("text", true), output, options("pageSize", "margin", "fontSize", "fontPath", "lineSpacing", "title", "onProgress")),
                "forms" to listOf(path, options("password")),
                "fillForm" to listOf(path, output, Param("values", true, ANY_OBJECT), options("flatten", "ignoreMissing", "password", "onProgress")),
                "watermark" to listOf(
                    path, output,
                    optionsOf(
                        listOf("text", "image") + PLACEMENT + listOf("fontSize", "color", "bold", "italic", "fontPath", "width", "height", "scale", "password", "onProgress"),
                        required = true, rule = oneOf,
                    ),
                ),
                "stamp" to listOf(
                    path, output,
                    optionsOf(
                        listOf("pageNumbers", "header", "footer", "items", "fontSize", "color", "margin", "pages", "fontPath", "password", "onProgress"),
                        nested = mapOf(
                            "pageNumbers" to orScalar(obj(*(listOf("format", "position", "start") + STYLE).toTypedArray())),
                            "header" to orScalar(obj(*(listOf("left", "center", "right") + STYLE).toTypedArray())),
                            "footer" to orScalar(obj(*(listOf("left", "center", "right") + STYLE).toTypedArray())),
                            "items" to items(STAMP_ITEM),
                        ),
                        required = true,
                    ),
                ),
                "addTextLayer" to listOf(path, output, Param("layers", true, items(layer)), options("force", "password", "onProgress")),
                "compress" to listOf(path, output, options("preset", "imageDpi", "imageQuality", "removeMetadata", "objectStreams", "password", "onProgress")),
                "setInfo" to listOf(path, output, Param("info", true, obj("title", "author", "subject", "keywords", "creator")), options("password", "onProgress")),
                "setOutline" to listOf(path, output, Param("items", true, nullable(items(outlineItem()))), options("password", "onProgress")),
                "makeSearchable" to listOf(path, output, options("pages", "force", "ocrDpi", "ocrOptions", "password", "onProgress")),
            )
        }

        /** Any object (the field values of `fillForm`). zh-CN: 任意对象 (`fillForm` 的字段值). */
        val ANY_OBJECT = Shape { value, at, problems -> if (value !is Map<*, *>) problems += "$at: not an object: $value" }

        /**
         * The problems of the `pdf` calls among [calls]: undocumented methods, option keys and nested keys, too many
         * arguments, missing required arguments. Trailing `undefined` arguments count as not given.
         * zh-CN: [calls] 中 `pdf` 调用的问题: 文档未列出的方法, 选项键与嵌套键, 参数过多, 缺少必需参数. 末尾的 `undefined` 参数视为未给出.
         */
        fun problemsOf(calls: List<Call>, node: Boolean): List<String> {
            val table = table(node)
            val problems = ArrayList<String>()
            calls.filter { it.name.startsWith("pdf.") }.forEach { call ->
                val params = table[call.name.removePrefix("pdf.")]
                if (params == null) {
                    problems += "$call: undocumented method"
                    return@forEach
                }
                val args = call.args.dropLastWhile { it == UNDEFINED }
                if (args.size > params.size) problems += "$call: ${args.size} arguments, at most ${params.size}"
                params.forEachIndexed { index, param ->
                    val arg = args.getOrNull(index)
                    if (index >= args.size || arg == UNDEFINED) {
                        if (param.required) problems += "$call: missing ${param.name}"
                    } else {
                        param.shape.check(arg, "${call.name} ${param.name}", problems)
                    }
                }
            }
            return problems
        }

        /**
         * Runs a Node.js or Bun sample against promise stubs: `node harness.js <node|bun> <mode> <file>` prints
         * `{ calls, summary, logs }` once the sample prints its summary line.
         * zh-CN: 在 Promise 形式的桩对象上运行 Node.js 或 Bun 样例: `node harness.js <node|bun> <mode> <file>` 在样例输出摘要行后打印
         * `{ calls, summary, logs }`.
         */
        val HARNESS = "'use strict';\n" + SampleStubs.JS + "\n" + """
            |const fs = require('fs');
            |const path = require('path');
            |const [style, mode, file] = process.argv.slice(2);
            |(async () => {
            |    const source = fs.readFileSync(file, 'utf8');
            |    const calls = [];
            |    let summary = null;
            |    let done = null;
            |    const finished = new Promise((resolve) => { done = resolve; });
            |    const promise = (make) => { const p = make(); p.cancel = () => {}; return p; };
            |    const stubs = __sampleStubs({
            |        mode: mode, fixtures: true, node: true, args: {},
            |        record: (name, args) => calls.push({ name: name, args: args }),
            |        wrap: (value) => promise(() => Promise.resolve(value)),
            |        fail: (e) => promise(() => Promise.reject(e)),
            |        onLog: (text) => {
            |            if (text.indexOf('summary ') === 0) {
            |                summary = JSON.parse(text.substring('summary '.length));
            |                done();
            |            }
            |        }
            |    });
            |    if (style === 'node') {
            |        const requireStub = (id) => {
            |            if (id === 'pdf' || id === 'autojs6:pdf') return stubs.pdf;
            |            if (id === 'path' || id === 'node:path') return path.posix;
            |            throw new Error('unexpected module ' + id);
            |        };
            |        new Function('require', 'console', '__dirname', source)(requireStub, stubs.console, '/sdcard/Scripts/pdf-node');
            |    } else {
            |        globalThis.__host = {
            |            hasCapability: (capability) => { calls.push({ name: 'host.hasCapability', args: [capability] }); return true; },
            |            pdf: stubs.pdf
            |        };
            |        const proc = { env: { AUTOJS6_HOST_CLIENT_MODULE: 'data:text/javascript,export function createHostClient() { return globalThis.__host; }' } };
            |        const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
            |        await new AsyncFunction('process', 'console', '__dir', source.split('import.meta.dir').join('__dir'))(proc, stubs.console, '/sdcard/Scripts');
            |    }
            |    const timer = setTimeout(() => { throw new Error('no summary line'); }, 20000);
            |    await finished;
            |    clearTimeout(timer);
            |    process.stdout.write(JSON.stringify({ calls: calls, summary: summary, logs: stubs.report().logs }));
            |})().catch((e) => {
            |    process.stderr.write(String((e && e.stack) || e));
            |    process.exit(2);
            |});
        """.trimMargin() + "\n"

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.CompressState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.OrganizedPage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes the scripts of every tool in the three styles for the device run of `.python/device_codegen_run.py` (roadmap
 * P6.15, checkpoint 11 of the P6.13 scripts): the requests use the fixtures the runner pushes to [DEVICE_INPUT], every
 * style writes to its own folder under [DEVICE_OUTPUT], and `manifest.json` lists each script with the output it must
 * leave and the passwords to fill in. Opt-in: runs only when the environment variable `PAPER_CODEGEN_EXPORT` names
 * the folder to write to (skipped otherwise).
 * zh-CN: 为 `.python/device_codegen_run.py` 的设备运行写出每个工具三种写法的脚本 (路线图 P6.15, P6.13 生成脚本的检查点 11):
 * 请求使用运行器推到 [DEVICE_INPUT] 的样例, 每种写法写到 [DEVICE_OUTPUT] 下各自的文件夹, `manifest.json` 列出每个脚本须留下的输出与
 * 要填入的密码. 按需运行: 只在环境变量 `PAPER_CODEGEN_EXPORT` 给出写入的文件夹时运行 (否则跳过).
 */
class ScriptDeviceExportTest {

    private class Export(val name: String, val request: ScriptRequest, val expect: JSONObject, val passwords: Map<String, String> = emptyMap())

    private fun input(name: String, password: Boolean = false) = ScriptInput(ScriptFile("$DEVICE_INPUT/$name"), name, password)

    private fun pdf(pages: Int) = JSONObject().put("type", "pdf").put("pages", pages)

    private fun exports(style: ScriptStyle): List<Export> {
        val out = "$DEVICE_OUTPUT/${style.id}"
        fun output(name: String) = ScriptFile("$out/$name")
        fun export(name: String, kind: ToolKind, options: ToolOptions, inputs: List<ScriptInput>, output: ScriptFile, expect: JSONObject, passwords: Map<String, String> = emptyMap()) =
            Export(name, ScriptRequest.of(kind, inputs, options, output, null, ScriptRequest.KEY_LENGTH_256), expect.put("path", output.path), passwords)
        val low = CompressState.numbersOf(PdfContract.COMPRESS_PRESET_LOW)
        return listOf(
            export("merge", ToolKind.MERGE, ToolOptions.Merge(keepOutline = true), listOf(input("text.pdf"), input("outline.pdf")), output("merge.pdf"), pdf(7)),
            export("split", ToolKind.SPLIT, ToolOptions.Split(every = 2, ranges = null), listOf(input("outline.pdf")), output("split"), JSONObject().put("type", "dir").put("count", 3)),
            export("to-images", ToolKind.TO_IMAGES, ToolOptions.ToImages(PdfContract.FORMAT_PNG, 72, "1-2"), listOf(input("outline.pdf")), output("images"), JSONObject().put("type", "dir").put("count", 2)),
            export(
                "from-images", ToolKind.FROM_IMAGES, ToolOptions.FromImages(PdfContract.PAGE_SIZE_A4, PdfContract.ORIENTATION_AUTO, 12f),
                listOf(input("page-1.png"), input("page-2.jpg")), output("from-images.pdf"), pdf(2),
            ),
            export("encrypt", ToolKind.ENCRYPT, ToolOptions.Encrypt("ignored"), listOf(input("text.pdf")), output("encrypt.pdf"), pdf(1).put("password", NEW_PASSWORD), mapOf("newPassword" to NEW_PASSWORD)),
            export("decrypt", ToolKind.DECRYPT, ToolOptions.Decrypt, listOf(input("encrypted.pdf", password = true)), output("decrypt.pdf"), pdf(2).put("encrypted", false), mapOf("password" to OWNER_PASSWORD)),
            export("extract-text", ToolKind.EXTRACT_TEXT, ToolOptions.ExtractText("2"), listOf(input("outline.pdf")), output("text.txt"), JSONObject().put("type", "text").put("contains", "needle")),
            export(
                "organize", ToolKind.ORGANIZE, ToolOptions.Organize(listOf(OrganizedPage(2, 0), OrganizedPage(1, 90), OrganizedPage(3, 0))),
                listOf(input("outline.pdf")), output("organize.pdf"), pdf(3).put("rotations", "0,90,0"),
            ),
            export(
                "watermark", ToolKind.WATERMARK,
                ToolOptions.Watermark(PdfContract.STAMP_TEXT, "CODEGEN", null, PdfContract.POSITION_CENTER, 45.0, 0.3, false, "#C62828", null, null, PdfContract.LAYER_OVER, "1-2"),
                listOf(input("outline.pdf")), output("watermark.pdf"), pdf(6),
            ),
            export(
                "page-numbers", ToolKind.PAGE_NUMBERS,
                ToolOptions.PageNumbers("{page} / {total}", PdfContract.POSITION_BOTTOM_CENTER, 1, ToolOptions.Slots(left = "Codegen"), ToolOptions.Slots(), 10.0, null),
                listOf(input("outline.pdf")), output("page-numbers.pdf"), pdf(6).put("lastPageText", "6 / 6"),
            ),
            export(
                "compress", ToolKind.COMPRESS, ToolOptions.Compress(PdfContract.COMPRESS_PRESET_LOW, low.imageDpi, low.imageQuality, removeMetadata = false, objectStreams = true),
                listOf(input("scanned-multi.pdf")), output("compress.pdf"), pdf(3).put("smallerThan", "scanned-multi.pdf"),
            ),
            export(
                "fill-form", ToolKind.FILL_FORM, ToolOptions.FillForm(linkedMapOf("name" to "Codegen", "agree" to false), flatten = false),
                listOf(input("form.pdf")), output("fill-form.pdf"), pdf(2).put("fields", JSONObject().put("name", "Codegen").put("agree", false)),
            ),
            export(
                "properties", ToolKind.PROPERTIES, ToolOptions.Properties(linkedMapOf("title" to "Codegen title", "author" to "AutoJs6"), clearOutline = true),
                listOf(input("outline.pdf")), output("properties.pdf"), pdf(6).put("title", "Codegen title").put("outline", 0),
            ),
        )
    }

    @Test
    fun writesTheScriptsOfEveryToolForTheDeviceRun() {
        val target = System.getenv(ENV)
        assumeTrue("Opt-in; set $ENV to the folder to write to", !target.isNullOrBlank())
        val root = File(target!!).apply { deleteRecursively(); mkdirs() }
        val manifest = JSONArray()
        for (style in ScriptStyle.entries) {
            val folder = File(root, style.id).apply { mkdirs() }
            val list = exports(style)
            assertEquals(ToolKind.entries.toSet(), list.map { it.request.kind }.toSet())
            for (export in list) {
                val script = ScriptGenerator.generate(export.request, style)
                val main = if (script.projectJson != null) {
                    val project = File(folder, export.name).apply { mkdirs() }
                    File(project, GeneratedScript.PROJECT_JSON).writeText(script.projectJson, Charsets.UTF_8)
                    File(project, "main.js").also { it.writeText(script.code, Charsets.UTF_8) }
                } else {
                    File(folder, "${export.name}.js").also { it.writeText(script.code, Charsets.UTF_8) }
                }
                manifest.put(
                    JSONObject()
                        .put("style", style.id)
                        .put("tool", export.request.kind.id)
                        .put("script", main.relativeTo(root).invariantSeparatorsPath)
                        .put("project", script.projectJson != null)
                        .put("passwords", JSONObject(export.passwords))
                        .put("expect", export.expect),
                )
            }
        }
        File(root, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)
    }

    private companion object {
        const val ENV = "PAPER_CODEGEN_EXPORT"
        const val DEVICE_INPUT = "/storage/emulated/0/Documents/3-Paper PDF/codegen/in"
        const val DEVICE_OUTPUT = "/storage/emulated/0/Documents/3-Paper PDF/codegen/out"
        const val NEW_PASSWORD = "codegen-new"
        const val OWNER_PASSWORD = "paper-owner"
    }

}

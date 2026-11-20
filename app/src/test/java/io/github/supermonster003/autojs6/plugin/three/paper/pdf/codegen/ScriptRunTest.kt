package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptFixtures.SECRETS
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The run the result page writes as a script (roadmap P6.13): kept without the new password or the watermark image,
 * paths from the URIs, the folder the file manager showed, or a guess, and the result path from the result page.
 * zh-CN: 结果页写成脚本的执行 (路线图 P6.13): 保存时去掉新密码与水印图片, 路径取自 URI, 文件管理器显示的文件夹或推测, 结果路径取自结果页.
 */
class ScriptRunTest {

    private val storage = "content://com.android.externalstorage.documents/document/primary%3ADocuments%2F"

    @Test
    fun theNewPasswordIsNotKept() {
        val run = ScriptRun.of(ToolKind.ENCRYPT, listOf(ScriptSource(storage + "a.pdf", "a.pdf", password = true)), ToolOptions.Encrypt(SECRETS[1]))
        assertEquals(ToolOptions.Encrypt(""), run.options)
        val request = checkNotNull(run.requestFor("/storage/emulated/0/Documents/3-Paper PDF/a_encrypted.pdf", 128))
        assertEquals(listOf(ScriptInput(ScriptFile("/storage/emulated/0/Documents/a.pdf"), "a.pdf", password = true)), request.inputs)
        assertEquals(ScriptFile("/storage/emulated/0/Documents/3-Paper PDF/a_encrypted.pdf"), request.output)
        assertEquals(128, request.keyLength)
        ScriptStyle.entries.forEach { style ->
            val script = ScriptGenerator.generate(request, style)
            SECRETS.forEach { secret -> assertFalse("$style", script.code.contains(secret) || script.projectJson.orEmpty().contains(secret)) }
        }
    }

    @Test
    fun theWatermarkImageIsAPath() {
        // The image target of the options needs an android.net.Uri, which JVM tests cannot build; the view model hands it
        // over as a ScriptSource. zh-CN: 选项中的图片目标需要 android.net.Uri, JVM 测试无法构造; 视图模型以 ScriptSource 交出.
        val options = ToolOptions.Watermark(
            type = PdfContract.STAMP_IMAGE, text = "", image = null, position = PdfContract.POSITION_CENTER,
            rotation = 0.0, opacity = 1.0, tile = false, color = null, fontSize = null, width = 144.5, layer = PdfContract.LAYER_OVER, pages = null,
        )
        val run = ScriptRun.of(ToolKind.WATERMARK, listOf(ScriptSource("file:///sdcard/a.pdf", "a.pdf")), options, ScriptSource(storage + "logo.png", "logo.png"))
        val request = checkNotNull(run.requestFor("/sdcard/out/a_watermarked.pdf"))
        assertEquals(ScriptFile("/storage/emulated/0/Documents/logo.png"), request.image)
        assertTrue(ScriptGenerator.generate(request, ScriptStyle.RHINO).code.contains("'/storage/emulated/0/Documents/logo.png'"))
    }

    @Test
    fun theFileManagerFolderFillsInWhatProvidersHide() {
        val run = ScriptRun.of(
            ToolKind.MERGE,
            listOf(ScriptSource("content://org.autojs.autojs6.fileprovider/root/x", "x.pdf"), ScriptSource("file:///sdcard/y.pdf", "y.pdf")),
            ToolOptions.Merge(keepOutline = true),
            parentPath = " /storage/emulated/0/Books ",
        )
        assertEquals("/storage/emulated/0/Books", run.parentPath)
        val request = checkNotNull(run.requestFor("x_merged.pdf"))
        assertEquals(listOf(ScriptFile("/storage/emulated/0/Books/x.pdf"), ScriptFile("/sdcard/y.pdf")), request.inputs.map { it.file })
        assertEquals(ScriptFile("/storage/emulated/0/Books/x_merged.pdf"), request.output)
    }

    @Test
    fun withoutAFolderThePathsAreGuessed() {
        val run = ScriptRun.of(ToolKind.SPLIT, listOf(ScriptSource("content://media/external/file/12", "z.pdf")), ToolOptions.Split(every = 2, ranges = null), parentPath = " ")
        assertNull(run.parentPath)
        val request = checkNotNull(run.requestFor(null))
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/z.pdf", guessed = true), request.inputs.single().file)
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/${ScriptRun.DEFAULT_OUTPUT}", guessed = true), request.output)
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/z_split", guessed = true), checkNotNull(run.requestFor(" z_split ")).output)
    }

    @Test
    fun runsThatCannotBeWrittenGiveNoRequest() {
        assertNull(ScriptRun.of(ToolKind.MERGE, emptyList(), ToolOptions.Merge(keepOutline = false)).requestFor("/sdcard/a.pdf"))
        assertNull(ScriptRun.of(ToolKind.MERGE, listOf(ScriptSource("file:///sdcard/a.pdf", "a.pdf")), ToolOptions.Decrypt).requestFor("/sdcard/a.pdf"))
    }

}

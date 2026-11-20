package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.AutoJs6Scripts
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.GeneratedScript
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptSaver
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptStyle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolTarget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The script block of the toolbox result page (roadmap P6.13, D51, D69) on a device, from the home screen to the result
 * page: the runtime switch rewrites the code with the paths of the run and its "Custom" values (appendix D Q265 (b)),
 * "Copy script" puts the shown code on the clipboard, and "Save as .js" writes each runtime under `scripts/` of the
 * output folder in the settings (Node.js as a project folder) without the password typed for an encryption. The
 * "Open in AutoJs6" button shows exactly when the host takes scripts and the device runs the runtime; opening and running
 * the scripts in the host are roadmap P6.15.
 * zh-CN: 在设备上从首页走到结果页检查工具箱结果页的脚本区 (路线图 P6.13, D51, D69): 运行时切换用本次执行的路径与 "自定义" 值
 * (附录 D Q265 (b)) 重写代码, "复制脚本" 把显示的代码放入剪贴板, "保存为 .js" 把每种运行时写到设置中输出文件夹的 `scripts/` 下 (Node.js
 * 为项目文件夹), 且不含加密时输入的密码. "在 AutoJs6 中打开" 按钮恰好在宿主能接收脚本且设备能运行该运行时时显示; 在宿主中打开与运行
 * 脚本属于路线图 P6.15.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolboxScriptDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("toolbox-script-test")

    private val context get() = environment.context

    private val output by lazy { File(environment.root, "output") }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        environment.prepare()
        output.mkdirs()
        ToolboxPreferences.setOutputFolder(context, output)
    }

    @After
    fun tearDown() {
        environment.restore()
    }

    @Test
    fun theRuntimeSwitchRewritesTheRunAndCopyTakesTheShownCode() = wizard(ToolKind.WATERMARK) { model ->
        val input = PdfServiceHarness.sampleFile("text.pdf")
        give(model, ToolTarget(Uri.fromFile(input), "Text.pdf"))
        compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement(WATERMARK)
        compose.onNodeWithTag("tool-watermark-rotation-custom").performScrollTo().performClick()
        awaitTag("tool-watermark-rotation-value")
        compose.onNodeWithTag("tool-watermark-rotation-value").performScrollTo().performTextReplacement("$CUSTOM_ROTATION")
        start()
        awaitTag("tool-done")
        awaitTag("tool-script")
        val result = File(output, "Text_watermarked.pdf")
        assertTrue("the result was not written", result.isFile)
        val available = AutoJs6Scripts.isAvailable(context)
        val codes = ScriptStyle.entries.associateWith { style ->
            compose.onNodeWithTag("tool-script-style-${style.id}").performScrollTo().performClick()
            compose.waitForIdle()
            val code = code()
            listOf(input.path, result.path, WATERMARK, "$CUSTOM_ROTATION").forEach { assertTrue("$style lacks $it:\n$code", code.contains(it)) }
            assertEquals("$style: Open in AutoJs6", available && AutoJs6Scripts.supports(style), exists("tool-script-open"))
            assertEquals("$style: Bun note", style == ScriptStyle.BUN && !AutoJs6Scripts.supports(style), exists("tool-script-bun-note"))
            assertFalse("$style: no password was used", exists("tool-script-passwords"))
            code
        }
        assertTrue(codes.getValue(ScriptStyle.RHINO), codes.getValue(ScriptStyle.RHINO).contains("pdf.watermark("))
        assertTrue(codes.getValue(ScriptStyle.NODE), codes.getValue(ScriptStyle.NODE).contains("require('pdf')"))
        assertTrue(codes.getValue(ScriptStyle.BUN), codes.getValue(ScriptStyle.BUN).startsWith("\"bun\";"))
        assertTrue(codes.getValue(ScriptStyle.BUN), codes.getValue(ScriptStyle.BUN).contains("host.pdf"))
        assertEquals("two runtimes wrote the same code", ScriptStyle.entries.size, codes.values.toSet().size)
        // Bun is chosen last: the copy is its code. zh-CN: 最后选中的是 Bun: 复制的是它的代码.
        compose.onNodeWithTag("tool-script-copy").performScrollTo().performClick()
        compose.waitForIdle()
        var copied: String? = null
        instrumentation.runOnMainSync {
            copied = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }
        assertEquals(codes.getValue(ScriptStyle.BUN), copied)
    }

    @Test
    fun saveWritesEachRuntimeWithoutThePassword() = wizard(ToolKind.ENCRYPT) { model ->
        give(model, ToolTarget(Uri.fromFile(PdfServiceHarness.sampleFile("text.pdf")), "Text.pdf"))
        compose.onNodeWithTag("tool-new-password").performScrollTo().performTextReplacement(SECRET)
        compose.onNodeWithTag("tool-confirm-password").performScrollTo().performTextReplacement(SECRET)
        start()
        awaitTag("tool-done")
        awaitTag("tool-script")
        assertTrue("the result was not written", File(output, "Text_encrypted.pdf").isFile)
        assertTrue("the password note is missing", exists("tool-script-passwords"))
        val scripts = File(output, ScriptSaver.FOLDER)
        val written = ScriptStyle.entries.map { style ->
            compose.onNodeWithTag("tool-script-style-${style.id}").performScrollTo().performClick()
            compose.waitForIdle()
            val code = code()
            assertFalse("$style shows the password", code.contains(SECRET))
            assertTrue("$style has no placeholder:\n$code", code.contains("newPassword = null"))
            save(scripts)
            val file = when (style) {
                ScriptStyle.RHINO -> File(scripts, "3-paper-encrypt" + GeneratedScript.RHINO_SUFFIX)
                ScriptStyle.NODE -> File(File(scripts, "3-paper-encrypt"), GeneratedScript.NODE_MAIN)
                ScriptStyle.BUN -> File(scripts, "3-paper-encrypt" + GeneratedScript.BUN_SUFFIX)
            }
            compose.waitUntil(TIMEOUT_MILLIS) { file.isFile }
            assertEquals("$style saved other code", code, file.readText())
            file
        }
        val project = File(written[1].parentFile, GeneratedScript.PROJECT_JSON)
        assertTrue("the Node.js project has no project.json", project.isFile)
        val permissions = JSONObject(project.readText()).toString()
        assertTrue(permissions, permissions.contains("files.write"))
        (written + project).forEach { assertFalse("${it.name} holds the password", it.readText().contains(SECRET)) }
        // Saving the same runtime again keeps the first file. zh-CN: 再次保存同一运行时保留第一个文件.
        save(scripts)
        compose.waitUntil(TIMEOUT_MILLIS) { File(scripts, "3-paper-encrypt (1)" + GeneratedScript.BUN_SUFFIX).isFile }
        assertEquals(written[2].readText(), File(scripts, "3-paper-encrypt (1)" + GeneratedScript.BUN_SUFFIX).readText())
    }

    /** Opens the wizard of [kind] from the home screen and runs [block] once it waits for files. zh-CN: 从首页打开 [kind] 的向导, 等它等待文件后运行 [block]. */
    private fun wizard(kind: ToolKind, block: (PdfToolViewModel) -> Unit) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag("paper.home.tool.${kind.id}"))
            compose.onNodeWithTag("paper.home.tool.${kind.id}").performClick()
            awaitTag("tool-no-files")
            compose.waitForIdle()
            lateinit var model: PdfToolViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(checkNotNull(activity.navigation?.currentBackStackEntry))[PdfToolViewModel::class.java]
            }
            block(model)
        }
    }

    /** Hands [target] to the wizard as the file browser does. zh-CN: 像文件浏览器那样把 [target] 交给向导. */
    private fun give(model: PdfToolViewModel, target: ToolTarget) {
        instrumentation.runOnMainSync { model.addTargets(listOf(target)) }
        compose.waitUntil(TIMEOUT_MILLIS) {
            var ready = false
            instrumentation.runOnMainSync { ready = model.inputs.size == 1 && model.inputs.none { it.inspecting } }
            ready
        }
    }

    private fun start() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        // The bottom bar follows the keyboard, so a click while it moves can miss: click until the button goes.
        // zh-CN: 底栏跟随键盘移动, 移动中的点击可能落空: 点击直到按钮消失.
        repeat(START_ATTEMPTS) {
            if (compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().isEmpty()) return
            compose.onNodeWithTag("tool-start").performClick()
            val gone = runCatching {
                compose.waitUntil(START_WAIT_MILLIS) { compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().isEmpty() }
            }.isSuccess
            if (gone) return
        }
        fail("the start button took no click")
    }

    /**
     * Saves the shown script once no "Saved to" message covers the buttons at the bottom: a tap on the message would miss the button.
     * zh-CN: 等底部按钮上没有 "已保存到" 消息遮挡后保存显示的脚本: 点在消息上会落空.
     */
    private fun save(scripts: File) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(scripts.path, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("tool-script-save").performScrollTo().performClick()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The code the block shows. zh-CN: 脚本区显示的代码. */
    private fun code(): String =
        compose.onNodeWithTag("tool-script-code").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val START_ATTEMPTS = 3
        const val START_WAIT_MILLIS = 5_000L
        const val WATERMARK = "CONFIDENTIAL"
        const val CUSTOM_ROTATION = 30.5
        const val SECRET = "Zq7-unlock-9041"
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolTarget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Back from the result and failure pages of the toolbox (roadmap P6.15, E4 feedback F3): the system back key and the
 * back arrow of the top bar lead to the options with the files, the choices and the typed values kept; a second start
 * writes a numbered copy next to the first result (the first stays), and the script of the result page follows the
 * second run. Back from the options closes the wizard.
 * zh-CN: 工具箱结果页与失败页的返回 (路线图 P6.15, E4 反馈 F3): 系统返回键与顶栏的返回箭头回到选项页, 文件, 选择与输入的值都保留; 第二次
 * 开始在第一个结果旁边写出带序号的副本 (第一个保留), 结果页的脚本跟随第二次运行. 在选项页返回则关闭向导.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolboxBackToOptionsDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("toolbox-back-to-options-test")

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
    fun theBackKeyOnTheResultKeepsTheWatermarkOptionsAndTheSecondRunWritesANumberedCopy() = wizard(ToolKind.WATERMARK) { model ->
        give(model, sample("text.pdf", "Text.pdf"))
        compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement(FIRST)
        compose.onNodeWithTag("tool-watermark-rotation-0").performScrollTo().performClick()
        compose.onNodeWithTag("tool-watermark-opacity-50").performScrollTo().performClick()
        compose.onNodeWithTag("tool-watermark-tile").performScrollTo().performClick()
        awaitTile(model)
        start()
        awaitTag("tool-done")
        assertEquals(File(output, "Text_watermarked.pdf").absolutePath, scriptOutput(model))
        Espresso.pressBack()
        awaitTag("tool-start")
        onMain {
            assertEquals(1, model.inputs.size)
            assertEquals(FIRST, model.watermark.text)
            assertTrue("rotation 0 was not kept", model.watermark.rotation.selected(0))
            assertTrue("opacity 50 was not kept", model.watermark.opacity.selected(50))
            assertTrue("the tile switch was not kept", model.watermark.tile)
        }
        assertEquals(FIRST, fieldText("tool-watermark-text"))
        compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement(SECOND)
        start()
        awaitTag("tool-done")
        val first = File(output, "Text_watermarked.pdf")
        val second = File(output, "Text_watermarked (1).pdf")
        assertTrue("the first result was replaced or removed", first.isFile)
        assertTrue("the second result was not numbered", second.isFile)
        // The script of the second result page is the second run. zh-CN: 第二个结果页的脚本对应第二次运行.
        assertEquals(second.absolutePath, scriptOutput(model))
        onMain {
            val run = checkNotNull(model.scriptRequestOf((model.phase as PdfToolViewModel.Phase.Done).result))
            assertEquals(SECOND, (run.options as ToolOptions.Watermark).text)
        }
        // The back arrow of the top bar does the same; back on the options then closes the wizard.
        // zh-CN: 顶栏的返回箭头效果相同; 随后在选项页返回关闭向导.
        compose.onNodeWithTag("paper.topBar.back").performClick()
        awaitTag("tool-start")
        assertEquals(SECOND, fieldText("tool-watermark-text"))
        compose.onNodeWithTag("paper.topBar.back").performClick()
        awaitTag("paper.home")
    }

    @Test
    fun theBackArrowOnTheResultKeepsTheOrganizedOrderAndTurns() = wizard(ToolKind.ORGANIZE) { model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.waitUntil(TIMEOUT_MILLIS) { onMain { model.organized.size == OUTLINE_PAGES } }
        onMain {
            model.movePage(0, 2)
            model.rotatePage(1)
        }
        val order = onMain { model.organized.map { it.page to it.rotation } }
        start()
        awaitTag("tool-done")
        compose.onNodeWithTag("paper.topBar.back").performClick()
        awaitTag("tool-start")
        assertEquals(order, onMain { model.organized.map { it.page to it.rotation } })
        Espresso.pressBack()
        awaitTag("paper.home")
    }

    @Test
    fun theBackKeyOnAFailureKeepsThePageNumberOptionsAndTheNextRunSucceeds() = wizard(ToolKind.PAGE_NUMBERS) { model ->
        val copy = File(environment.root, "Copy.pdf")
        PdfServiceHarness.sampleFile("outline.pdf").copyTo(copy, overwrite = true)
        give(model, ToolTarget(Uri.fromFile(copy), "Copy.pdf"))
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("2-")
        compose.onNodeWithTag("tool-numbers-start").performScrollTo().performTextReplacement("5")
        // The input disappears before the start, so the run fails. zh-CN: 输入在开始之前消失, 运行因此失败.
        val moved = File(environment.root, "Moved.pdf")
        assertTrue(copy.renameTo(moved))
        start()
        awaitTag("tool-error")
        Espresso.pressBack()
        awaitTag("tool-start")
        onMain {
            assertEquals("2-", model.pages)
            assertEquals(1, model.inputs.size)
        }
        assertEquals("5", fieldText("tool-numbers-start"))
        assertTrue(moved.renameTo(copy))
        start()
        awaitTag("tool-done")
        assertTrue(File(output, "Copy_numbered.pdf").isFile)
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

    private fun give(model: PdfToolViewModel, target: ToolTarget) {
        instrumentation.runOnMainSync { model.addTargets(listOf(target)) }
        compose.waitUntil(TIMEOUT_MILLIS) { onMain { model.inputs.size == 1 && model.inputs.none { it.inspecting } } }
    }

    private fun sample(name: String, displayName: String) = ToolTarget(Uri.fromFile(PdfServiceHarness.sampleFile(name)), displayName)

    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }

    /** The output path of the script request on the result page. zh-CN: 结果页脚本请求的输出路径. */
    private fun scriptOutput(model: PdfToolViewModel): String = onMain {
        val done = model.phase as? PdfToolViewModel.Phase.Done ?: error("not on the result page")
        checkNotNull(model.scriptRequestOf(done.result)).output.path
    }

    private fun fieldText(tag: String): String? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text

    private fun awaitTile(model: PdfToolViewModel) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            onMain { model.watermark.tile } &&
                compose.onAllNodesWithTag("tool-watermark-tile").fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }
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

    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val START_ATTEMPTS = 3
        const val START_WAIT_MILLIS = 5_000L
        const val OUTLINE_PAGES = 6
        const val FIRST = "CONFIDENTIAL"
        const val SECOND = "DRAFT"
    }

}

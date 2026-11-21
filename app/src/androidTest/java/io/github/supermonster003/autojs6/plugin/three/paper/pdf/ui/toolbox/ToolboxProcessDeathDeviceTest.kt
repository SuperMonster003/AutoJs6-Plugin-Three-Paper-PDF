package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ExplorerTool
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel.Phase
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolResult
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolRequest
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolScreenState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolSummary
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolTarget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The toolbox screen after its process died (E4 R3, roadmap appendix D Q326 (a)): the kept state names the files, the
 * output folder and the result but no password; a screen built from it in a new process shows the result page without
 * the script, the encrypted input asks for its password again with an empty field, and back leads to the options with
 * the file. In the same process (only the screen was destroyed) the screen takes the task back.
 * zh-CN: 进程死亡后的工具箱界面 (E4 R3, 路线图附录 D Q326 (a)): 保留的状态含文件, 输出目录与结果, 但不含密码; 在新进程中由它重建的界面
 * 显示不带脚本的结果页, 加密的输入再次询问密码且密码框为空, 返回回到带有该文件的选项页. 在同一进程中 (只有界面被销毁) 界面接回原任务.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolboxProcessDeathDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("toolbox-process-death-test")

    private val context get() = environment.context

    private val output by lazy { File(environment.root, "output") }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        environment.prepare()
        output.mkdirs()
        ToolboxPreferences.setOutputFolder(context, output)
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        environment.restore()
    }

    @Test
    fun theResultAndTheFilesComeBackWithoutPasswordOrScript() = wizard(ToolKind.COMPRESS) { model ->
        give(model, ToolTarget(Uri.fromFile(PdfServiceHarness.sampleFile("encrypted.pdf")), "Encrypted.pdf"))
        assertTrue("the encrypted sample did not ask for a password", onMain { model.inputs.single().locked })
        onMain { model.unlock(0, OWNER_PASSWORD) }
        compose.waitUntil(TIMEOUT_MILLIS) { onMain { model.inputs.single().let { !it.locked && !it.unlocking } } }
        start()
        awaitTag("tool-done")
        val first = onMain { (model.phase as Phase.Done).result }
        assertTrue("the compress result has no summary", first.summary is ToolSummary.Compressed)
        val saved = checkNotNull(onMain { model.savedScreen() })
        assertFalse("the kept state holds the owner password", saved.contains(OWNER_PASSWORD))
        assertFalse("the kept state holds the user password", saved.contains(USER_PASSWORD))

        // A new process: the task of this one is unknown there. zh-CN: 新进程: 那里不知道本进程的任务.
        val kept = checkNotNull(ToolScreenState.decode(saved))
        val elsewhere = ToolScreenState(kept.kind, kept.inputs, kept.outputFolder, kept.task?.let { ToolScreenState.SavedTask(-1, it.id) }, kept.result)
        val restored = rebuilt(elsewhere.encode())
        onMain { restored.startToolbox(ToolKind.COMPRESS) }
        onMain {
            val done = restored.phase as? Phase.Done ?: error("the result page did not come back: ${restored.phase}")
            assertSameResult(first, done.result)
            assertNull(restored.task)
            assertNull("the script came back", restored.scriptRequestOf(done.result))
            assertEquals(output.absolutePath, restored.outputFolder?.absolutePath)
        }
        compose.waitUntil(TIMEOUT_MILLIS) { onMain { restored.inputs.size == 1 && restored.inputs.none { it.inspecting } } }
        onMain {
            val input = restored.inputs.single()
            assertEquals("Encrypted.pdf", input.target.displayName)
            assertTrue("the encrypted input did not ask for its password again", input.locked)
            assertEquals("", input.passwordDraft)
            assertNull(input.password)
            assertTrue(restored.backToOptions())
            assertEquals(Phase.Ready, restored.phase)
            assertFalse("the locked input can be started", restored.canStart())
        }

        // The same process, only the screen destroyed: the task comes back. zh-CN: 同一进程, 只有界面被销毁: 任务接回.
        val again = rebuilt(saved)
        onMain { again.startToolbox(ToolKind.COMPRESS) }
        onMain {
            assertSame(model.task, again.task)
            assertSameResult(first, (again.phase as Phase.Done).result)
        }

        // Another tool keeps nothing of it. zh-CN: 其他工具不沿用它.
        val other = rebuilt(saved)
        onMain {
            other.startToolbox(ToolKind.MERGE)
            assertTrue(other.inputs.isEmpty())
            assertNull(other.task)
            assertFalse(other.phase is Phase.Done)
        }
    }

    @Test
    fun theFileManagerResultComesBackWithTheFilesOfTheRequest() {
        val target = ToolTarget(Uri.fromFile(PdfServiceHarness.sampleFile("text.pdf")), "Text.pdf")
        val result = ToolResult("/storage/emulated/0/Download/Text_1.pdf", 2, emptyList())
        val model = rebuilt(ToolScreenState(ToolKind.SPLIT.name, result = result).encode())
        onMain { model.start(ToolRequest(ExplorerTool.SPLIT, listOf(target), "/storage/emulated/0/Download", null)) }
        onMain {
            val done = model.phase as? Phase.Done ?: error("the result page did not come back: ${model.phase}")
            assertSameResult(result, done.result)
            assertNull("the script came back", model.scriptRequestOf(done.result))
        }
        compose.waitUntil(TIMEOUT_MILLIS) { onMain { model.inputs.size == 1 && model.inputs.single().pageCount > 0 } }
        onMain {
            assertTrue(model.backToOptions())
            assertEquals(Phase.Ready, model.phase)
            // The file manager gives the files again with its request: they are not kept. zh-CN: 文件管理器会随请求再次给出文件: 不保留它们.
            assertTrue(checkNotNull(ToolScreenState.decode(model.savedScreen())).inputs.isEmpty())
        }
    }

    /**
     * A screen model built from [saved] as a new screen gets it: through a parcel, as the system keeps it.
     * zh-CN: 由 [saved] 构建的界面模型, 与新界面得到的相同: 经过 parcel, 与系统保存它的方式一样.
     */
    private fun rebuilt(saved: String): PdfToolViewModel {
        val parcel = Parcel.obtain()
        val bundle = try {
            Bundle().apply { putString(PdfToolViewModel.KEY_SCREEN, saved) }.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Bundle.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
        val handle = SavedStateHandle(mapOf(PdfToolViewModel.KEY_SCREEN to bundle))
        val app = environment.context.applicationContext as android.app.Application
        val store = ViewModelStore()
        stores += store
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PdfToolViewModel(app, handle) as T
        }
        return onMain { ViewModelProvider(store, factory)[PdfToolViewModel::class.java] }
    }

    private fun assertSameResult(expected: ToolResult, actual: ToolResult) {
        assertEquals(expected.displayPath, actual.displayPath)
        assertEquals(expected.files, actual.files)
        assertEquals(expected.warnings, actual.warnings)
        assertEquals(expected.summary, actual.summary)
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

    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
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
        const val USER_PASSWORD = "paper-user"
        const val OWNER_PASSWORD = "paper-owner"
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
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
import kotlin.math.abs

/**
 * Every toolbox wizard once on a device, started from the home screen the way a user starts it (roadmap P4.2): the
 * inputs are handed to the wizard's view model in place of the file browser, the options are set on the screen, the
 * task runs in the foreground service, writes into the output folder under the names of appendix D Q74, announces the
 * outcome and lists a single PDF result in the recent files. Splitting goes back home while it runs and finishes on the
 * home task card.
 * zh-CN: 在设备上把每个工具箱向导各走一次, 以用户的方式从首页启动 (路线图 P4.2): 输入代替文件浏览器直接交给向导的视图模型, 选项在界面上设置,
 * 任务在前台服务中运行, 按附录 D Q74 的名称写入输出目录, 发出结果通知, 单个 PDF 结果进入最近文件. 拆分在运行中返回首页, 由首页任务卡片显示
 * 结束.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolboxWizardDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("toolbox-wizard-test")

    private val context get() = environment.context

    private val output by lazy { File(environment.root, "output") }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        environment.prepare()
        output.mkdirs()
        ToolboxPreferences.setOutputFolder(context, output)
        PDFBoxResourceLoader.init(context)
    }

    @After
    fun tearDown() {
        environment.restore()
    }

    @Test
    fun mergeAnnouncesTheResultAndListsItInTheRecentFiles() = wizard(ToolKind.MERGE) { scenario, model ->
        give(model, sample("outline.pdf", "Outline.pdf"), sample("text.pdf", "Text.pdf"))
        start()
        awaitTag("tool-done")
        val task = checkNotNull(model.task)
        PDDocument.load(File(output, "Outline_merged.pdf")).use { assertEquals(OUTLINE_PAGES + TEXT_PAGES, it.numberOfPages) }
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(PdfServiceHarness.eventually { manager.activeNotifications.any { it.id == FINISHED_ID_BASE + task.id } })

        click("tool-finish")
        awaitTag("paper.home")
        reveal("paper.home.recent.Outline_merged.pdf")
        assertEquals(HomeActivity.ROUTE_HOME, route(scenario))
    }

    @Test
    fun splitFinishesOnTheHomeTaskCard() = wizard(ToolKind.SPLIT) { scenario, model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.onNodeWithTag("tool-split-every").performScrollTo().performTextReplacement("3")
        start()
        val task = checkNotNull(model.task)
        // Back home while the task may still run: the task goes on and finishes on its home card. A task that already
        // finished shows its result, where back leads to the options and drops the card (E4 feedback F3); back there goes home.
        // zh-CN: 任务可能仍在运行时回到首页: 任务继续, 在首页卡片上完成. 已经完成的任务显示结果页, 在那里返回回到选项页并移除卡片
        // (E4 反馈 F3), 再返回才回首页.
        click("paper.topBar.back")
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("paper.home").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().isNotEmpty()
        }
        val finishedFirst = compose.onAllNodesWithTag("paper.home").fetchSemanticsNodes().isEmpty()
        if (finishedFirst) click("paper.topBar.back")
        awaitTag("paper.home")
        assertEquals(HomeActivity.ROUTE_HOME, route(scenario))
        if (!finishedFirst) reveal("paper.home.task.${task.id}.path")
        listOf("Outline-01.pdf", "Outline-02.pdf").forEach { name ->
            PDDocument.load(File(output, "Outline_split/$name")).use { assertEquals(3, it.numberOfPages) }
        }
        if (!finishedFirst) click("paper.home.task.${task.id}.dismiss")
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag("paper.home.task.${task.id}").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun toImagesRendersTheChosenPagesAtTheChosenResolution() = wizard(ToolKind.TO_IMAGES) { _, model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("1-2")
        compose.onNodeWithTag("tool-dpi-72").performScrollTo().performClick()
        start()
        awaitTag("tool-done")
        val folder = File(output, "Outline_images")
        assertEquals(listOf("Outline-p01.png", "Outline-p02.png"), folder.list().orEmpty().sorted())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(folder, "Outline-p01.png").path, bounds)
        assertTrue("${bounds.outWidth} is not about $A4_WIDTH_POINTS", abs(bounds.outWidth - A4_WIDTH_POINTS) <= 1)
    }

    @Test
    fun fromImagesMakesOnePagePerImage() = wizard(ToolKind.FROM_IMAGES) { _, model ->
        give(model, image("first.png", 300, 200), image("second.png", 200, 300))
        compose.onNodeWithTag("tool-margin-36").performScrollTo().performClick()
        start()
        awaitTag("tool-done")
        PDDocument.load(File(output, "first.pdf")).use { assertEquals(2, it.numberOfPages) }
    }

    @Test
    fun encryptAsksForThePasswordTwice() = wizard(ToolKind.ENCRYPT) { _, model ->
        give(model, sample("text.pdf", "Text.pdf"))
        compose.onNodeWithTag("tool-new-password").performScrollTo().performTextReplacement(NEW_PASSWORD)
        compose.onNodeWithTag("tool-confirm-password").performScrollTo().performTextReplacement(NEW_PASSWORD)
        start()
        awaitTag("tool-done")
        val result = File(output, "Text_encrypted.pdf")
        try {
            PDDocument.load(result).close()
            fail("the result opened without a password")
        } catch (_: InvalidPasswordException) {
        }
        PDDocument.load(result, NEW_PASSWORD).use { assertEquals(TEXT_PAGES, it.numberOfPages) }
    }

    @Test
    fun decryptTakesTheOwnerPassword() = wizard(ToolKind.DECRYPT) { _, model ->
        give(model, sample("encrypted.pdf", "Encrypted.pdf"))
        awaitTag("tool-password-0")
        compose.onNodeWithTag("tool-password-0").performScrollTo().performTextReplacement(OWNER_PASSWORD)
        compose.onNodeWithTag("tool-unlock-0").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag("tool-password-0").fetchSemanticsNodes().isEmpty() }
        start()
        awaitTag("tool-done")
        PDDocument.load(File(output, "Encrypted_decrypted.pdf")).use { document ->
            assertTrue(!document.isEncrypted)
            assertEquals(2, document.numberOfPages)
        }
    }

    @Test
    fun extractTextWritesATextFile() = wizard(ToolKind.EXTRACT_TEXT) { _, model ->
        give(model, sample("text.pdf", "Text.pdf"))
        start()
        awaitTag("tool-done")
        assertTrue(File(output, "Text_text.txt").readText().isNotBlank())
    }

    @Test
    fun organizeKeepsTheNewOrderTheTurnsAndTheRemovals() = wizard(ToolKind.ORGANIZE) { _, model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        awaitTag("tool-rotate-all")
        click("tool-rotate-all")
        revealPage("tool-delete-page-1")
        compose.onNodeWithTag("tool-delete-page-1").performClick()
        // Page 3 one place earlier, through the accessibility action of the drag. zh-CN: 经拖动的无障碍动作把第 3 页前移一位.
        revealPage("tool-page-3")
        val earlier = context.getString(R.string.toolbox_move_earlier)
        val actions = compose.onNodeWithTag("tool-page-3").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        instrumentation.runOnMainSync { actions.first { it.label == earlier }.action() }
        compose.waitForIdle()
        start()
        awaitTag("tool-done")
        PDDocument.load(File(output, "Outline_edited.pdf")).use { document ->
            assertEquals(OUTLINE_PAGES - 1, document.numberOfPages)
            assertTrue(document.pages.all { it.rotation == 90 })
            // Turned pages come out of the stripper broken across lines. zh-CN: 旋转后的页面经文本抽取会被拆成多行.
            val first = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(document).filterNot { it.isWhitespace() }
            val second = PDFTextStripper().apply { startPage = 2; endPage = 2 }.getText(document).filterNot { it.isWhitespace() }
            assertTrue(first, first.contains("Page3of6"))
            assertTrue(second, second.contains("Page2of6"))
        }
    }

    /** Opens the wizard of [kind] from the home screen and runs [block] once it waits for files. zh-CN: 从首页打开 [kind] 的向导, 等它等待文件后运行 [block]. */
    private fun wizard(kind: ToolKind, block: (ActivityScenario<HomeActivity>, PdfToolViewModel) -> Unit) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag("paper.home.tool.${kind.id}"))
            compose.onNodeWithTag("paper.home.tool.${kind.id}").performClick()
            awaitTag("tool-no-files")
            awaitText("tool-output", output.absolutePath)
            compose.waitForIdle()
            lateinit var model: PdfToolViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(checkNotNull(activity.navigation?.currentBackStackEntry))[PdfToolViewModel::class.java]
            }
            block(scenario, model)
        }
    }

    /** Hands [targets] to the wizard as the file browser does. zh-CN: 像文件浏览器那样把 [targets] 交给向导. */
    private fun give(model: PdfToolViewModel, vararg targets: ToolTarget) {
        instrumentation.runOnMainSync { model.addTargets(targets.toList()) }
        compose.waitUntil(TIMEOUT_MILLIS) {
            var ready = false
            instrumentation.runOnMainSync { ready = model.inputs.size == targets.size && model.inputs.none { it.inspecting } }
            ready
        }
    }

    private fun sample(name: String, displayName: String) = ToolTarget(Uri.fromFile(PdfServiceHarness.sampleFile(name)), displayName)

    private fun image(name: String, width: Int, height: Int): ToolTarget {
        val file = File(environment.root, "images/$name")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 120, 200))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return ToolTarget(Uri.fromFile(file), name)
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

    private fun click(tag: String) {
        awaitTag(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    /** Scrolls the home list to [tag] once it is there. zh-CN: 待 [tag] 出现在首页列表后滚动到它. */
    private fun reveal(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            runCatching { compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    /** Scrolls the organize grid to [tag]. zh-CN: 把页面整理网格滚动到 [tag]. */
    private fun revealPage(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            runCatching { compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    private fun route(scenario: ActivityScenario<HomeActivity>): String? {
        compose.waitForIdle()
        var route: String? = null
        scenario.onActivity { route = it.navigation?.currentBackStackEntry?.destination?.route }
        return route
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitText(tag: String, expected: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == expected } == true
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val START_ATTEMPTS = 3
        const val START_WAIT_MILLIS = 5_000L
        const val OUTLINE_PAGES = 6
        const val TEXT_PAGES = 1
        const val A4_WIDTH_POINTS = 595
        const val FINISHED_ID_BASE = 4_200
        const val NEW_PASSWORD = "s3cret"
        const val OWNER_PASSWORD = "paper-owner"
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.app.Activity
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.release.ReleaseHistoryAssets
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.release.loadReleaseHistory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The release history page (plugin-center P21.1, release history specification section 10): the title "Release
 * history" with the plugin name as the subtitle, the newest release expanded and the others collapsed with their
 * category counts, a header toggles its release, the top bar expands or collapses all, the choice survives recreation,
 * the settings row opens the page, and Tab walks every release down to the oldest one. The page is the history route of
 * [HomeActivity] (no activity of its own), so the tests open it through the navigation of the home screen.
 * zh-CN: 发行历史页 (plugin-center P21.1, 发行历史规范第 10 节): 标题 "发行历史", 子标题为插件名称; 最新版本展开, 其余收起并显示各类别条目数; 点版本头
 * 切换该版本, 顶栏按钮展开或收起全部, 重建后保留; 设置页那一行打开本页; Tab 依次走过各版本直到最早的版本. 本页是 [HomeActivity]
 * 的 history 路由 (没有独立的活动), 所以测试经首页的导航打开它.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseHistoryPageTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After
    fun finishAll() {
        instrumentation.setInTouchMode(true)
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED).flatMap { monitor.getActivitiesInStage(it) }.forEach(Activity::finish)
        }
        instrumentation.waitForIdleSync()
    }

    /** Unmerged: the count chips sit inside the clickable header, which merges its children. zh-CN: 未合并树: 计数胶囊在合并子节点的可点按版本头里. */
    private fun exists(tag: String): Boolean = compose.shows(hasTestTag(tag), useUnmergedTree = true)

    /**
     * Scrolls the list to a version header. A release below the fold is not composed at all, so its tags stay missing
     * until it is scrolled into view (the expanded newest release can fill the screen).
     * zh-CN: 把列表滚到某个版本头. 视野以下的版本根本不会被组合, 滚入视野前它的标签都不存在 (展开的最新版本可能占满整屏).
     */
    private fun scrollTo(version: String) {
        compose.onNodeWithTag("paper.releaseHistory.list").performScrollToNode(hasTestTag("paper.releaseHistory.entry.$version"))
    }

    private fun clickHeader(version: String) {
        scrollTo(version)
        compose.onNodeWithTag("paper.releaseHistory.entry.$version").performClick()
    }

    private fun versions(): List<String> {
        val locale = context.resources.configuration.locales[0]
        return loadReleaseHistory(ReleaseHistoryAssets.candidatesFor(locale)) { path ->
            context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
        }.map { it.version }
    }

    /** Launches home and opens the release history route. zh-CN: 启动首页并打开发行历史路由. */
    private fun launchHistory(): ActivityScenario<HomeActivity> {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)
        compose.waitUntil(TIMEOUT) { exists("paper.home") }
        scenario.onActivity { it.navigation?.navigate(HomeActivity.ROUTE_HISTORY) }
        compose.waitUntil(TIMEOUT) { exists("paper.releaseHistory.list") }
        return scenario
    }

    private fun route(scenario: ActivityScenario<HomeActivity>): String? {
        compose.waitForIdle()
        var route: String? = null
        scenario.onActivity { route = it.navigation?.currentBackStackEntry?.destination?.route }
        return route
    }

    private fun assertTitleAndSubtitle() {
        compose.onNode(hasText(context.getString(R.string.release_history))).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.app_name))).assertIsDisplayed()
    }

    @Test
    fun theTitleIsReleaseHistoryAndTheSubtitleThePluginName() {
        launchHistory().use {
            assertTitleAndSubtitle()
            assertFalse(exists("paper.emptyState"))
        }
    }

    /**
     * The newest release starts expanded, the others show their category counts; a header expands or collapses its
     * release, the top bar expands or collapses all of them, and the choice survives recreation.
     * zh-CN: 最新版本默认展开, 其余显示各类别计数; 点版本头展开或收起该版本, 顶栏按钮展开或收起全部, 选择在重建后保留.
     */
    @Test
    fun releasesExpandAndCollapse() {
        launchHistory().use { scenario ->
            val versions = versions()
            assertTrue("releases: ${versions.size}", versions.size >= 2)
            val (newest, second) = versions
            assertTrue(exists("paper.releaseHistory.body.$newest"))
            assertFalse(exists("paper.releaseHistory.counts.$newest"))
            scrollTo(second)
            assertFalse(exists("paper.releaseHistory.body.$second"))
            assertTrue(exists("paper.releaseHistory.counts.$second"))

            clickHeader(second)
            compose.waitUntil(TIMEOUT) { exists("paper.releaseHistory.body.$second") }
            clickHeader(newest)
            compose.waitUntil(TIMEOUT) { !exists("paper.releaseHistory.body.$newest") && exists("paper.releaseHistory.counts.$newest") }

            scenario.recreate()
            compose.waitUntil(TIMEOUT) { exists("paper.releaseHistory.list") }
            assertEquals(HomeActivity.ROUTE_HISTORY, route(scenario))
            scrollTo(second)
            assertTrue(exists("paper.releaseHistory.body.$second"))
            scrollTo(newest)
            assertFalse(exists("paper.releaseHistory.body.$newest"))

            compose.onNodeWithTag("paper.releaseHistory.toggleAll")
                .assertContentDescriptionEquals(context.getString(R.string.release_history_expand_all))
                .performClick()
            compose.waitUntil(TIMEOUT) { exists("paper.releaseHistory.body.$newest") }
            compose.onNodeWithTag("paper.releaseHistory.toggleAll")
                .assertContentDescriptionEquals(context.getString(R.string.release_history_collapse_all))
                .performClick()
            compose.waitUntil(TIMEOUT) { !exists("paper.releaseHistory.body.$newest") && !exists("paper.releaseHistory.body.$second") }
        }
    }

    /** The "Release history" row of the settings page opens the page. zh-CN: 设置页的 "发行历史" 一行打开本页. */
    @Test
    fun theSettingsRowOpensThePage() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) { exists("paper.home.settings") }
            compose.onNodeWithTag("paper.home.settings").performClick()
            compose.waitUntil(TIMEOUT) { exists("paper.settings.releaseHistory") }
            val row = compose.onNodeWithTag("paper.settings.releaseHistory")
            if (generateSequence(row.fetchSemanticsNode().parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }) row.performScrollTo()
            row.assert(hasText(context.getString(R.string.release_history))).performClick()
            compose.waitUntil(TIMEOUT) { exists("paper.releaseHistory.list") }
            assertEquals(HomeActivity.ROUTE_HISTORY, route(scenario))
            assertTitleAndSubtitle()
        }
    }

    /**
     * Every release takes focus in turn, scrolled into view, down to the oldest one; the back button and the top bar
     * button are reachable too.
     * zh-CN: 各版本依次获得焦点并滚入视野, 直到最早的版本; 返回按钮与顶栏按钮同样可达.
     */
    @Test
    fun theReleaseHistoryCanBeReadToTheEndFromTheKeyboard() {
        launchHistory().use { scenario ->
            awaitWindowFocus(scenario)
            // Newer versions take setInTouchMode at once; API 24 learns it only from a key. zh-CN: 新版本立即生效; API 24 要靠按键得知.
            instrumentation.setInTouchMode(false)
            key(KeyEvent.KEYCODE_DPAD_UP)
            val versions = versions()
            val visited = tabCycle(scenario)
            fun expect(ok: Boolean, what: String) { if (!ok) fail("$what; visited: $visited; ${focusedWindow()}") }
            expect(visited.containsAll(listOf("paper.topBar.back", "paper.releaseHistory.toggleAll")), "Tab never reached the back button or the expand / collapse all button")
            val expected = versions.map { "paper.releaseHistory.entry.$it" }
            val entries = visited.filter { it.startsWith("paper.releaseHistory.entry.") }
            expect(entries == expected, "the releases in Tab order: expected $expected, got $entries")
        }
    }

    private fun key(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    /** The test tag of the focused control or of its nearest tagged ancestor. zh-CN: 获得焦点的控件或其最近的带标签祖先的测试标签. */
    private fun focusedTag(): String? {
        val node = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        return generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) } ?: UNTAGGED
    }

    /** Runs [command] as the shell user (it connects UiAutomation) and returns its output. zh-CN: 以 shell 用户运行 [command] 并返回输出 (会连接 UiAutomation). */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    /**
     * The focused window and app as `dumpsys window` reports them, for diagnostics only. API 33 and 35 leave
     * mCurrentFocus out of `dumpsys window windows`, so the whole dump is read.
     * zh-CN: `dumpsys window` 报告的焦点窗口与应用, 只用于诊断. API 33 与 35 的 `dumpsys window windows` 不含 mCurrentFocus, 所以读整份输出.
     */
    private fun focusedWindow(): String = runCatching {
        shell("dumpsys window").lineSequence().map(String::trim)
            .filter { it.startsWith("mCurrentFocus=") || it.startsWith("mFocusedApp=") }.distinct().joinToString()
            .ifEmpty { "mCurrentFocus not in dumpsys" }
    }.getOrElse { "dumpsys failed: $it" }

    private fun windowFocused(scenario: ActivityScenario<*>): Boolean = try {
        var focused = false
        compose.waitUntil(TIMEOUT) { scenario.onActivity { focused = it.hasWindowFocus() }; focused }
        true
    } catch (timeout: ComposeTimeoutException) {
        false
    }

    /**
     * Keys go to the focused window: while a system dialog has focus (an ANR or crash dialog of another app on a slow
     * emulator, say), every Tab would be lost. When focus does not come, the test closes system dialogs (error dialogs
     * take that as "wait" or "close") and brings the page back to the front, once, before it gives up.
     * zh-CN: 按键送往焦点窗口: 系统对话框 (例如慢模拟器上其他应用的 ANR 或崩溃对话框) 持有焦点时, 每次 Tab 都会丢失. 等不到焦点时关闭系统对话框
     * (错误对话框视之为 "等待" 或 "关闭") 并把页面带回前台, 只做一次, 仍等不到才失败.
     */
    private fun awaitWindowFocus(scenario: ActivityScenario<*>, visited: List<String> = emptyList()) {
        if (windowFocused(scenario)) return
        val before = focusedWindow()
        Log.w(LOG_TAG, "no window focus after $TIMEOUT ms ($before), closing system dialogs")
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        scenario.onActivity { it.startActivity(Intent(it, it.javaClass).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        if (!windowFocused(scenario)) {
            fail("the page has no window focus after $TIMEOUT ms, nor after closing system dialogs; visited: $visited; " +
                "before: $before; after: ${focusedWindow()}")
        }
        Log.i(LOG_TAG, "window focus is back: ${focusedWindow()}")
    }

    /** Presses Tab until focus returns to a control seen before; every focused control must be on screen. zh-CN: 按 Tab 直到焦点回到见过的控件. */
    private fun tabCycle(scenario: ActivityScenario<*>): List<String> {
        val visited = mutableListOf<String>()
        repeat(MAX_TABS) {
            awaitWindowFocus(scenario, visited)
            key(KeyEvent.KEYCODE_TAB)
            val tag = focusedTag() ?: return@repeat
            if (tag in visited) return visited
            compose.onNode(isFocused(), useUnmergedTree = true).assertIsDisplayed()
            visited += tag
        }
        return visited
    }

    private companion object {
        const val LOG_TAG = "ReleaseHistoryTest"
        const val TIMEOUT = 15_000L
        const val MAX_TABS = 60
        const val UNTAGGED = "(untagged)"
    }
}

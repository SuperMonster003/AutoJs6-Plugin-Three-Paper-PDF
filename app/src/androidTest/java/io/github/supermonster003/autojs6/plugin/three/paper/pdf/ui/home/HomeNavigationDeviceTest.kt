package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.DeviceShots
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.AppearancePreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The navigation and the settings of the plugin's own screens on a device (roadmap P4.1): home, settings, release
 * history and every toolbox wizard open and close with the top bar and the system back; the default resolution, the
 * output folder and the language chosen in the settings are kept and reach the wizards after a restart; the two viewer
 * switches of full-screen reading (E4 feedback F2) start on and are kept.
 * zh-CN: 在设备上测试插件自身界面的导航与设置 (路线图 P4.1): 首页, 设置, 发行历史与每个工具箱向导都能经顶栏与系统返回打开和关闭; 设置中选择的
 * 默认分辨率, 输出目录与语言会被保留, 重启后仍作用于向导; 查看器全屏阅读的两个开关 (E4 反馈 F2) 默认开启并被保留.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeNavigationDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("home-navigation-test")

    private val context get() = environment.context

    @Before
    fun setUp() {
        environment.prepare()
    }

    @After
    fun tearDown() {
        environment.restore()
    }

    @Test
    fun everyScreenOpensAndGoesBack() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            assertEquals(HomeActivity.ROUTE_HOME, route(scenario))

            click("paper.home.settings")
            awaitTag("paper.settings")
            assertEquals(HomeActivity.ROUTE_SETTINGS, route(scenario))
            compose.onNodeWithTag("paper.settings.releaseHistory").performScrollTo().performClick()
            awaitTag("paper.releaseHistory.list")
            assertEquals(HomeActivity.ROUTE_HISTORY, route(scenario))

            // The top bar goes back to the settings, the system back to home. zh-CN: 顶栏返回到设置, 系统返回到首页.
            click("paper.topBar.back")
            awaitTag("paper.settings")
            systemBack(scenario)
            awaitTag("paper.home")
            assertEquals(HomeActivity.ROUTE_HOME, route(scenario))

            ToolKind.entries.forEachIndexed { index, kind ->
                openTool(kind)
                awaitTag("tool-no-files")
                assertEquals("${HomeActivity.ROUTE_WIZARD}/{kind}", route(scenario))
                assertEquals(kind.id, argument(scenario))
                if (index % 2 == 0) click("paper.topBar.back") else systemBack(scenario)
                awaitTag("paper.home")
                assertEquals(HomeActivity.ROUTE_HOME, route(scenario))
            }
        }
    }

    @Test
    fun theResolutionAndTheOutputFolderAreKeptAndReachTheWizards() {
        val chosen = File(environment.root, "chosen").apply { mkdirs() }
        ToolboxPreferences.setOutputFolder(context, chosen)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            click("paper.home.settings")
            awaitTag("paper.settings")

            choose("paper.settings.dpi", "paper.dialog.dpi", ToolboxPreferences.DPI_CHOICES.indexOf(LOW_DPI))
            awaitCondition { ToolboxPreferences.defaultDpi(context) == LOW_DPI }

            // The wizard starts from the chosen folder and the new resolution. zh-CN: 向导从所选目录与新的分辨率开始.
            systemBack(scenario)
            awaitTag("paper.home")
            openTool(ToolKind.TO_IMAGES)
            awaitText("tool-output", chosen.absolutePath)
            assertEquals(LOW_DPI, wizard(scenario).imageDpi)
            click("paper.topBar.back")
            awaitTag("paper.home")

            click("paper.home.settings")
            awaitTag("paper.settings")
            choose("paper.settings.outputFolder", "paper.dialog.outputFolder", 0)
            awaitCondition { ToolboxPreferences.chosenOutputFolder(context) == null }
        }

        // A new start reads both back. zh-CN: 重新启动后两者仍被读回.
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            openTool(ToolKind.TO_IMAGES)
            awaitText("tool-output", ToolboxPreferences.defaultOutputFolder().absolutePath)
            assertEquals(LOW_DPI, wizard(scenario).imageDpi)
        }
        assertNull(ToolboxPreferences.chosenOutputFolder(context))
        assertEquals(LOW_DPI, ToolboxPreferences.defaultDpi(context))
    }

    @Test
    fun theChosenLanguageRecreatesTheScreenAndIsKept() {
        val japanese = localized(Locale.JAPANESE, R.string.settings_title)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            click("paper.home.settings")
            awaitTag("paper.settings")

            choose("paper.settings.language", "paper.dialog.language", AppearancePreferences.LANGUAGES.indexOf(JAPANESE))
            awaitCondition { AppearancePreferences.read(context).language == JAPANESE }
            // The recreated Activity keeps the settings screen, now in Japanese. zh-CN: 重建后的 Activity 保持在设置页, 且已是日语.
            compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(japanese).fetchSemanticsNodes().isNotEmpty() }
            awaitTag("paper.settings")
            var language: String? = null
            scenario.onActivity { language = it.resources.configuration.locales[0].language }
            assertEquals(JAPANESE, language)

            choose("paper.settings.language", "paper.dialog.language", AppearancePreferences.LANGUAGES.indexOf(AppearancePreferences.FOLLOW_SYSTEM))
            awaitCondition { AppearancePreferences.read(context).language == AppearancePreferences.FOLLOW_SYSTEM }
            awaitTag("paper.settings")
        }
    }

    @Test
    fun theViewerSwitchesStartOnAndAreKeptAcrossARestart() {
        val preferences = ViewerPreferences.of(context)
        preferences.edit().remove(ViewerPreferences.KEY_HIDE_PAGE_INDICATOR).remove(ViewerPreferences.KEY_HIDE_SYSTEM_BARS).commit()
        ActivityScenario.launch(HomeActivity::class.java).use {
            awaitTag("paper.home")
            click("paper.home.settings")
            awaitTag("paper.settings")
            // E4 feedback F2: both switches start on. zh-CN: E4 反馈 F2: 两个开关默认开启.
            awaitToggle(HIDE_INDICATOR, ToggleableState.On)
            awaitToggle(HIDE_SYSTEM_BARS, ToggleableState.On)
            DeviceShots.take("F2-settings-viewer")
            compose.onNodeWithTag(HIDE_INDICATOR).performScrollTo().performClick()
            awaitCondition { !ViewerPreferences.fullscreen(preferences).hidePageIndicator }
            awaitToggle(HIDE_INDICATOR, ToggleableState.Off)
            awaitToggle(HIDE_SYSTEM_BARS, ToggleableState.On)
            compose.onNodeWithTag(HIDE_SYSTEM_BARS).performScrollTo().performClick()
            awaitCondition { !ViewerPreferences.fullscreen(preferences).hideSystemBars }
        }
        ActivityScenario.launch(HomeActivity::class.java).use {
            awaitTag("paper.home")
            click("paper.home.settings")
            awaitTag("paper.settings")
            awaitToggle(HIDE_INDICATOR, ToggleableState.Off)
            awaitToggle(HIDE_SYSTEM_BARS, ToggleableState.Off)
            compose.onNodeWithTag(HIDE_INDICATOR).performScrollTo().performClick()
            awaitCondition { ViewerPreferences.fullscreen(preferences).hidePageIndicator }
        }
        assertEquals(ViewerPreferences.Fullscreen(hidePageIndicator = true, hideSystemBars = false), ViewerPreferences.fullscreen(preferences))
    }

    private fun awaitToggle(tag: String, state: ToggleableState) {
        awaitTag(tag)
        compose.onNodeWithTag(tag).performScrollTo()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == state }
        }
    }

    private fun openTool(kind: ToolKind) {
        compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag("paper.home.tool.${kind.id}"))
        compose.onNodeWithTag("paper.home.tool.${kind.id}").performClick()
    }

    /** Opens [row], picks option [index] of [dialog] and confirms it. zh-CN: 打开 [row], 选择 [dialog] 的第 [index] 项并确认. */
    private fun choose(row: String, dialog: String, index: Int) {
        awaitTag(row)
        compose.onNodeWithTag(row).performScrollTo().performClick()
        awaitTag("$dialog.$index")
        compose.onNodeWithTag("$dialog.$index").performScrollTo().performClick()
        compose.onNodeWithTag("$dialog.confirm").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag("$dialog.confirm").fetchSemanticsNodes().isEmpty() }
    }

    private fun click(tag: String) {
        awaitTag(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun systemBack(scenario: ActivityScenario<HomeActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun route(scenario: ActivityScenario<HomeActivity>): String? {
        compose.waitForIdle()
        var route: String? = null
        scenario.onActivity { route = it.navigation?.currentBackStackEntry?.destination?.route }
        return route
    }

    private fun argument(scenario: ActivityScenario<HomeActivity>): String? {
        var argument: String? = null
        scenario.onActivity { argument = it.navigation?.currentBackStackEntry?.arguments?.getString("kind") }
        return argument
    }

    private fun wizard(scenario: ActivityScenario<HomeActivity>): PdfToolViewModel {
        compose.waitForIdle()
        lateinit var model: PdfToolViewModel
        scenario.onActivity { activity ->
            model = ViewModelProvider(checkNotNull(activity.navigation?.currentBackStackEntry))[PdfToolViewModel::class.java]
        }
        return model
    }

    private fun localized(locale: Locale, id: Int): String {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).getString(id)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitUntil(TIMEOUT_MILLIS) { condition() }
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
        const val TIMEOUT_MILLIS = 20_000L
        const val LOW_DPI = 72
        const val JAPANESE = "ja"
        const val HIDE_INDICATOR = "paper.settings.viewerHidePageIndicator"
        const val HIDE_SYSTEM_BARS = "paper.settings.viewerHideSystemBars"
    }

}

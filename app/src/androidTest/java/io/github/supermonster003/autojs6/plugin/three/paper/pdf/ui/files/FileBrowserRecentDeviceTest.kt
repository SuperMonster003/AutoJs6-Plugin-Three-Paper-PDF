package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.DeviceShots
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.shows
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.GrantingTestProvider
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentEntry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.StorageAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The group "Recent" of the file browser and the folder it remembers, on a device the way a user picks files (roadmap
 * P6.15 batch 17d, appendix D Q324 (a)): a PDF chosen in a tool that did not run stays out, one a tool ran on (Q353 (b))
 * is offered by the next tool and by "Open PDF" of the
 * home screen and picked with one tap, and they all start in its folder (a deleted folder starts from its parent); a
 * PDF the toolbox wrote is offered and opens; checking in "Recent" and in the folder shares one state and one order;
 * a document of the system picker is checked under its grant and removed with a notice once revoked; images list no
 * recent files and remember their own folder; clearing on home empties the group; many entries leave the folder on
 * screen until "Show all"; checks, folder and group survive a recreation; a recent file the plugin cannot read now
 * (another app's file without storage access) is not listed. The files live in the plugin's own external folder,
 * inside the primary volume, so no storage access is needed; [GrantingTestProvider] stands in for another app's
 * documents.
 * zh-CN: 在设备上以用户选文件的方式测试文件浏览器的 "最近使用" 与记住的文件夹 (路线图 P6.15 批次 17d, 附录 D Q324 (a)): 在没有运行的工具中选过的 PDF 不记入, 运行过的工具的输入 (Q353 (b))
 * 出现在下一个工具与首页 "打开 PDF" 中并一点即选, 它们都从该文件所在的文件夹开始 (文件夹删除后从其上级开始); 工具箱写出的 PDF 出现并能打开;
 * 在 "最近使用" 与目录中勾选共用一个状态与顺序; 系统选择器的文档凭授权核对, 授权撤销后移除并提示; 图片不列最近文件且单独记住文件夹; 首页清空后
 * 分组为空; 条目多时文件夹列表仍在屏幕上, 直到点 "全部"; 勾选, 文件夹与分组状态在重建后保留; 插件现在读不了的最近文件 (没有存储权限时其他
 * 应用的文件) 不列出. 文件放在插件自己的外部目录 (位于主存储卷内), 不需要存储权限; [GrantingTestProvider] 充当其他应用的文档.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FileBrowserRecentDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("file-browser-recent-test")

    private val context get() = environment.context

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val providerUri: Uri = "content://${InstrumentationRegistry.getInstrumentation().context.packageName}.grants".toUri()

    private val base by lazy { File(checkNotNull(context.getExternalFilesDir(null)), "browser-recent-test") }

    private val folderA by lazy { File(base, "a") }

    private val folderB by lazy { File(base, "b") }

    private val alpha by lazy { File(folderA, "alpha.pdf") }

    private val beta by lazy { File(folderA, "beta.pdf") }

    private val gamma by lazy { File(folderB, "gamma.pdf") }

    private val output by lazy { File(base, "out") }

    @Before
    fun setUp() {
        environment.prepare()
        base.deleteRecursively()
        listOf(alpha, beta, gamma).forEach(::pdf)
        output.mkdirs()
        ToolboxPreferences.setOutputFolder(context, output)
        RecentFiles.clear(context)
        provider(GrantingTestProvider.METHOD_RESET)
    }

    @After
    fun tearDown() {
        RecentFiles.clear(context)
        provider(GrantingTestProvider.METHOD_RESET)
        environment.restore()
        base.deleteRecursively()
    }

    @Test
    fun aPdfRunInOneToolIsOfferedByTheNextAndEveryBrowserStartsInItsFolder() {
        remember(ToolboxPreferences.KEY_PDF_FOLDER, folderB)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val split = openTool(scenario, ToolKind.SPLIT)
            openInputs()
            assertPathEndsWith("b")
            assertFalse("no recent files yet", compose.shows(hasTestTag(RECENT)))
            // Up to the parent folder, into a, and a tap on alpha. zh-CN: 返回上级, 进入 a, 点 alpha.
            back(scenario)
            click("browser.entry.a")
            click("browser.entry.alpha.pdf")
            awaitInputs(split, alpha)
            assertEquals("its folder is remembered", folderA.absolutePath, remembered(ToolboxPreferences.KEY_PDF_FOLDER))
            assertTrue("an input of a tool that did not run stays out of the recent files (Q353 (b))", RecentFiles.read(context).isEmpty())
            backHome(scenario)

            // The next tool starts in that folder; the input enters the recent files when the tool runs.
            // zh-CN: 下一个工具从该文件夹开始; 工具运行时输入才记入最近文件.
            val compress = openTool(scenario, ToolKind.COMPRESS)
            openInputs()
            assertPathEndsWith("a")
            assertFalse("still no recent files", compose.shows(hasTestTag(RECENT)))
            click("browser.entry.alpha.pdf")
            awaitInputs(compress, alpha)
            assertTrue(RecentFiles.read(context).isEmpty())
            start()
            awaitTag("tool-done")
            compose.waitUntil(TIMEOUT_MILLIS) { RecentEntry.Local(alpha.absolutePath) in RecentFiles.read(context) }
            click("tool-finish")
            awaitTag("paper.home")

            val again = openTool(scenario, ToolKind.SPLIT)
            openInputs()
            assertPathEndsWith("a")
            awaitTag("$RECENT.alpha.pdf")
            DeviceShots.take("R1-browser-recent-one-tool-to-another")
            click("$RECENT.alpha.pdf")
            awaitInputs(again, alpha)
            backHome(scenario)

            openHomeBrowser()
            assertPathEndsWith("a")
            awaitTag("$RECENT.alpha.pdf")
            DeviceShots.take("R3-home-browser-starts-in-the-remembered-folder")
            click("browser.close")

            // A deleted folder starts from its nearest existing parent; its files leave "Recent" (the compressed copy in
            // the output folder stays). zh-CN: 已删除的文件夹从最近的已存在上级开始; 其中的文件不再出现在 "最近使用" (输出文件夹中的压缩副本仍在).
            assertTrue(folderA.deleteRecursively())
            openHomeBrowser()
            assertPathEndsWith(base.name)
            awaitTag("$RECENT.alpha_compressed.pdf")
            assertFalse(compose.shows(hasTestTag("$RECENT.alpha.pdf")))
            awaitTag("browser.entry.b")
        }
    }

    @Test
    fun aPdfTheToolboxWroteIsOfferedInRecentAndOpensFromTheHomeBrowser() {
        RecentFiles.add(context, alpha)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val compress = openTool(scenario, ToolKind.COMPRESS)
            openInputs()
            click("$RECENT.alpha.pdf")
            awaitInputs(compress, alpha)
            start()
            awaitTag("tool-done")
            click("tool-finish")
            awaitTag("paper.home")

            val written = File(output, "alpha_compressed.pdf")
            assertTrue(written.isFile)
            assertEquals(RecentEntry.Local(written.absolutePath), RecentFiles.read(context).first())
            openHomeBrowser()
            awaitTag("$RECENT.${written.name}")
            DeviceShots.take("R1-home-browser-recent-tool-output")
            val viewer = instrumentation.addMonitor(ThreePaperPdfViewerActivity::class.java.name, null, false)
            try {
                click("$RECENT.${written.name}")
                closeViewer(Uri.fromFile(written))
                assertEquals(1, viewer.hits)
            } finally {
                instrumentation.removeMonitor(viewer)
            }
        }
    }

    @Test
    fun checkingInRecentAndInTheFolderSharesOneStateAndOneOrder() {
        RecentFiles.add(context, gamma)
        RecentFiles.add(context, beta)
        remember(ToolboxPreferences.KEY_PDF_FOLDER, folderA)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val merge = openTool(scenario, ToolKind.MERGE)
            openInputs()
            assertPathEndsWith("a")
            click("$RECENT.beta.pdf")
            assertChecked("$RECENT.beta.pdf", true)
            assertChecked("browser.entry.beta.pdf", true)
            click("browser.entry.alpha.pdf")
            click("$RECENT.gamma.pdf")
            click("browser.entry.beta.pdf")
            assertChecked("$RECENT.beta.pdf", false)
            assertChecked("browser.entry.beta.pdf", false)
            click("$RECENT.beta.pdf")
            assertChecked("browser.entry.beta.pdf", true)
            assertText("browser.confirm", context.getString(R.string.browser_add, 3))
            DeviceShots.take("R1-browser-pdfs-recent-and-folder-checked")
            click("browser.confirm")
            awaitInputs(merge, alpha, gamma, beta)
            assertEquals(
                "choosing records nothing (Q353 (b))",
                listOf(beta, gamma).map { RecentEntry.Local(it.absolutePath) },
                RecentFiles.read(context),
            )
            start()
            awaitTag("tool-done")
            val inputs = listOf(beta, gamma, alpha).map { RecentEntry.Local(it.absolutePath) }
            compose.waitUntil(TIMEOUT_MILLIS) { RecentFiles.read(context).filter { it in inputs } == inputs }
            assertEquals(
                "recorded when the tool runs, in the order checked, the last checked first",
                inputs,
                RecentFiles.read(context).filter { it in inputs },
            )
            click("tool-finish")
            awaitTag("paper.home")
            assertEquals("the folder of the last local file", folderA.absolutePath, remembered(ToolboxPreferences.KEY_PDF_FOLDER))
        }
    }

    @Test
    fun aDocumentIsCheckedUnderItsGrantAndRemovedWithANoticeOnceRevoked() {
        val kept = document("kept.pdf")
        val revoked = document("revoked.pdf")
        provider(GrantingTestProvider.METHOD_REVOKE, revoked.toString())
        remember(ToolboxPreferences.KEY_PDF_FOLDER, folderA)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val split = openTool(scenario, ToolKind.SPLIT)
            openInputs()
            awaitTag("$RECENT.revoked.pdf")
            assertText("$RECENT.kept.pdf", context.getString(R.string.home_recent_picked))
            click("$RECENT.revoked.pdf")
            awaitText(context.getString(R.string.home_recent_unavailable, "revoked.pdf"))
            DeviceShots.take("R1-browser-recent-document-unavailable")
            compose.waitUntil(TIMEOUT_MILLIS) { !compose.shows(hasTestTag("$RECENT.revoked.pdf")) }
            assertFalse(RecentFiles.read(context).any { it.key == revoked.toString() })

            click("$RECENT.kept.pdf")
            compose.waitUntil(TIMEOUT_MILLIS) {
                onMain { split.inputs.singleOrNull()?.let { it.target.uri == kept && !it.inspecting } == true }
            }
            assertEquals("a document leaves the remembered folder alone", folderA.absolutePath, remembered(ToolboxPreferences.KEY_PDF_FOLDER))
        }
    }

    @Test
    fun imagesListNoRecentFilesAndRememberTheirOwnFolder() {
        val media = File(base, "media")
        val pictures = File(media, "pics").apply { mkdirs() }
        val image = File(pictures, "one.png")
        Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(40, 120, 200)) }.let { bitmap ->
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        RecentFiles.add(context, alpha)
        remember(ToolboxPreferences.KEY_PDF_FOLDER, folderA)
        remember(ToolboxPreferences.KEY_IMAGE_FOLDER, media)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val images = openTool(scenario, ToolKind.FROM_IMAGES)
            openInputs()
            assertPathEndsWith("media")
            awaitTag("browser.entry.pics")
            assertFalse("images list no recent files", compose.shows(hasTestTag(RECENT)))
            DeviceShots.take("R3-images-browser-own-folder-no-recent")
            click("browser.entry.pics")
            click("browser.entry.one.png")
            click("browser.confirm")
            compose.waitUntil(TIMEOUT_MILLIS) { onMain { images.inputs.size == 1 } }
            assertEquals(pictures.absolutePath, remembered(ToolboxPreferences.KEY_IMAGE_FOLDER))
            assertEquals("the PDF memory is its own", folderA.absolutePath, remembered(ToolboxPreferences.KEY_PDF_FOLDER))
            assertEquals("images do not enter the recent files", listOf(RecentEntry.Local(alpha.absolutePath)), RecentFiles.read(context))
            backHome(scenario)
            openHomeBrowser()
            assertPathEndsWith("a")
        }
    }

    @Test
    fun clearingTheRecentFilesOnHomeEmptiesTheGroup() {
        RecentFiles.add(context, gamma)
        RecentFiles.add(context, alpha)
        ActivityScenario.launch(HomeActivity::class.java).use {
            openHomeBrowser()
            awaitTag("$RECENT.gamma.pdf")
            click("browser.close")
            awaitListed("paper.home.recent.clear")
            compose.onNodeWithTag("paper.home.recent.clear").performClick()
            awaitListed("paper.home.recent.empty")
            openHomeBrowser()
            awaitTag("browser.path")
            assertFalse(compose.shows(hasTestTag(RECENT)))
            assertFalse(compose.shows(hasTestTag("$RECENT.gamma.pdf")))
        }
    }

    @Test
    fun manyEntriesLeaveTheFolderOnScreenAndChecksSurviveARecreation() {
        val many = File(base, "many")
        val files = (0 until RecentFiles.MAX).map { File(many, "r%02d.pdf".format(it)).also(::pdf) }
        files.forEach { RecentFiles.add(context, it) }
        remember(ToolboxPreferences.KEY_PDF_FOLDER, folderA)
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            openTool(scenario, ToolKind.MERGE)
            openInputs()
            awaitTag("$RECENT.r19.pdf")
            val rows = recentRows()
            assertTrue("at most ${BrowserRules.COMPACT_MAX} rows before Show all, not $rows", rows in 1..BrowserRules.COMPACT_MAX)
            compose.onNodeWithTag("browser.path").assertIsDisplayed()
            compose.onNodeWithTag("browser.entry.alpha.pdf").assertIsDisplayed()
            assertText(TOGGLE, context.getString(R.string.browser_recent_all, RecentFiles.MAX))
            DeviceShots.take("R1-browser-recent-compact")

            click(TOGGLE)
            assertText(TOGGLE, context.getString(R.string.browser_recent_less))
            DeviceShots.take("R1-browser-recent-expanded")
            reveal("$RECENT.r00.pdf")
            click("$RECENT.r00.pdf")
            reveal("browser.entry.alpha.pdf")
            click("browser.entry.alpha.pdf")
            assertText("browser.confirm", context.getString(R.string.browser_add, 2))

            scenario.recreate()
            awaitTag("browser.list")
            DeviceShots.take("R1-browser-recreated")
            assertPathEndsWith("a")
            // The list keeps its scroll position, so the header may be above the screen. zh-CN: 列表保留滚动位置, 表头可能在屏幕上方.
            reveal(TOGGLE)
            assertText(TOGGLE, context.getString(R.string.browser_recent_less))
            assertText("browser.confirm", context.getString(R.string.browser_add, 2))
            reveal("browser.entry.alpha.pdf")
            assertChecked("browser.entry.alpha.pdf", true)
            click("browser.confirm")
            awaitInputs(model(scenario), files.first(), alpha)
        }
    }

    @Test
    fun aRecentFileThePluginCannotReadNowIsNotListed() {
        // A file of another uid in Download: readable only with storage access (all files access from Android 11).
        // zh-CN: Download 中属于其他 uid 的文件: 只有存储访问权限 (Android 11 起为所有文件访问权限) 才能读取.
        val foreign = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOREIGN)
        shell("rm -f ${foreign.absolutePath}")
        shell("touch ${foreign.absolutePath}")
        try {
            RecentFiles.add(context, foreign)
            RecentFiles.add(context, alpha)
            val readable = StorageAccess.granted(context) && foreign.canRead()
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                openTool(scenario, ToolKind.SPLIT)
                openInputs()
                awaitTag("$RECENT.alpha.pdf")
                assertEquals("listed only when it can be read now", readable, compose.shows(hasTestTag("$RECENT.$FOREIGN")))
            }
        } finally {
            shell("rm -f ${foreign.absolutePath}")
        }
    }

    // Helpers. zh-CN: 辅助方法.

    private fun pdf(file: File) {
        file.parentFile?.mkdirs()
        PdfServiceHarness.sampleFile("text.pdf").copyTo(file, overwrite = true)
    }

    /** Runs [command] as the shell user and waits for it. zh-CN: 以 shell 用户执行 [command] 并等待其结束. */
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun remember(key: String, folder: File) = ToolboxPreferences.setBrowseFolder(context, key, folder)

    private fun remembered(key: String): String? = ToolboxPreferences.browseFolder(context, key)?.absolutePath

    /** A document of the test provider under a persistable grant, adopted into the recent files. zh-CN: 测试提供方的一个文档, 带可持久授权并记入最近文件. */
    private fun document(name: String): Uri {
        val uri = checkNotNull(provider(GrantingTestProvider.METHOD_CREATE, name).getString(GrantingTestProvider.EXTRA_URI)).toUri()
        provider(
            GrantingTestProvider.METHOD_GRANT, uri.toString(),
            bundleOf(GrantingTestProvider.EXTRA_PACKAGE to context.packageName, GrantingTestProvider.EXTRA_PERSISTABLE to true),
        )
        checkNotNull(RecentFiles.adopt(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        return uri
    }

    private fun provider(method: String, arg: String? = null, extras: Bundle? = null): Bundle =
        checkNotNull(context.contentResolver.call(providerUri, method, arg, extras ?: Bundle()))

    /** Opens the wizard of [kind] from home and answers its view model. zh-CN: 从首页打开 [kind] 的向导并返回其视图模型. */
    private fun openTool(scenario: ActivityScenario<HomeActivity>, kind: ToolKind): PdfToolViewModel {
        awaitListed("paper.home.tool.${kind.id}")
        compose.onNodeWithTag("paper.home.tool.${kind.id}").performClick()
        awaitTag("tool-no-files")
        compose.waitForIdle()
        return model(scenario)
    }

    private fun model(scenario: ActivityScenario<HomeActivity>): PdfToolViewModel {
        lateinit var model: PdfToolViewModel
        scenario.onActivity { activity ->
            model = ViewModelProvider(checkNotNull(activity.navigation?.currentBackStackEntry))[PdfToolViewModel::class.java]
        }
        return model
    }

    private fun openInputs() {
        click("tool-add")
        awaitTag("browser.list")
    }

    private fun openHomeBrowser() {
        awaitListed("paper.home.open")
        compose.onNodeWithTag("paper.home.open").performClick()
        awaitTag("browser.list")
    }

    /**
     * The back key through the activity's dispatcher, where the browser and the tool register: unlike a key event it
     * needs no window focus, which a system dialog of a freshly booted emulator may hold.
     * zh-CN: 经活动的分发器执行返回键 (浏览器与工具都在那里注册): 与按键事件不同, 它不需要窗口焦点, 而刚开机的模拟器上的系统对话框可能占着焦点.
     */
    private fun back(scenario: ActivityScenario<HomeActivity>) {
        compose.waitForIdle()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun backHome(scenario: ActivityScenario<HomeActivity>) {
        back(scenario)
        awaitTag("paper.home")
    }

    private fun awaitInputs(model: PdfToolViewModel, vararg files: File) {
        val expected = files.map { Uri.fromFile(it) }
        compose.waitUntil(TIMEOUT_MILLIS) { onMain { model.inputs.map { it.target.uri } == expected && model.inputs.none { it.inspecting } } }
    }

    private fun start() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasTestTag("tool-start")).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        compose.onNodeWithTag("tool-start").performClick()
    }

    /** The rows of "Recent" composed now; collapsed, those are all the rows shown. zh-CN: 当前组合出的 "最近使用" 行; 收起时即显示的全部行. */
    private fun recentRows(): Int = compose.onAllNodes(
        SemanticsMatcher("a recent row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("$RECENT.") && it != TOGGLE } == true
        },
    ).fetchSemanticsNodes().size

    private fun assertPathEndsWith(folder: String) {
        awaitTag("browser.path")
        compose.waitUntil(TIMEOUT_MILLIS) { textOf("browser.path")?.endsWith(" / $folder") == true }
    }

    private fun assertChecked(tag: String, checked: Boolean) {
        val expected = if (checked) ToggleableState.On else ToggleableState.Off
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == expected }
        }
    }

    private fun assertText(tag: String, expected: String) {
        val texts = {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false)
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        }
        val found = runCatching { compose.waitUntil(TIMEOUT_MILLIS) { expected in texts() } }.isSuccess
        assertTrue("$tag shows ${texts()}, not \"$expected\"", found)
    }

    private fun awaitText(expected: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(SemanticsMatcher("text $expected") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == expected } == true
            }).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun textOf(tag: String): String? = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun click(tag: String) {
        reveal(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    /** Waits for [tag] and scrolls the browser list (when shown) to it. zh-CN: 等待 [tag] 出现, 并在浏览器列表显示时滚动到它. */
    private fun reveal(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            if (compose.shows(hasTestTag("browser.list"))) {
                runCatching { compose.onNodeWithTag("browser.list").performScrollToNode(hasTestTag(tag)) }
            }
            compose.shows(hasTestTag(tag))
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.shows(hasTestTag(tag)) }
    }

    /** Waits until the home list holds [tag] and scrolls it into view. zh-CN: 等首页列表含有 [tag], 并将其滚动到可见处. */
    private fun awaitListed(tag: String) {
        awaitTag("paper.home")
        compose.waitUntil(TIMEOUT_MILLIS) {
            runCatching { compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    /** Waits for the viewer of [uri], then closes it and waits for home. zh-CN: 等待打开 [uri] 的查看器, 关闭它并等首页回到前台. */
    private fun closeViewer(uri: Uri) {
        compose.waitUntil(TIMEOUT_MILLIS) { viewers(Stage.RESUMED).any { it.intent.data == uri } }
        val open = viewers(Stage.RESUMED)
        instrumentation.runOnMainSync { open.forEach(Activity::finish) }
        compose.waitUntil(TIMEOUT_MILLIS) {
            LIVE_STAGES.all { viewers(it).isEmpty() } && activities(Stage.RESUMED).any { it is HomeActivity }
        }
    }

    private fun viewers(stage: Stage): List<Activity> = activities(stage).filterIsInstance<ThreePaperPdfViewerActivity>()

    private fun activities(stage: Stage): List<Activity> {
        var result = emptyList<Activity>()
        instrumentation.runOnMainSync { result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).toList() }
        return result
    }

    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val RECENT = "browser.recent"
        const val TOGGLE = "browser.recent.toggle"
        const val FOREIGN = "paper-recent-foreign-test.pdf"
        val LIVE_STAGES = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED)
    }

}

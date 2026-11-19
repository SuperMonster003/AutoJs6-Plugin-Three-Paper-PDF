package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.GrantingTestProvider
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.OpenPersistableDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.PickedDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentEntry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Documents picked with the system file picker in the recent files (roadmap P4.1, appendix D Q106 = B), on a device.
 * [GrantingTestProvider] of the test APK stands in for another app's documents provider and gives the plugin real URI
 * grants; an [Instrumentation.ActivityMonitor] answers the picker, so no system screen is tapped. Covered: a persistable
 * grant is taken and the document listed, a one-time grant is not; evicting, removing and clearing release the grants,
 * so at most [RecentFiles.MAX] stay persisted; a deleted document or a revoked grant removes the entry when it opens;
 * the home screen lists a picked document, opens it in the viewer again after a restart and drops it once deleted.
 * zh-CN: 设备上测试经系统文件选择器选取的文档进入最近文件 (路线图 P4.1, 附录 D Q106 = B). 测试 APK 的 [GrantingTestProvider] 充当其他
 * 应用的文档提供方, 给插件真实的 URI 授权; 由 [Instrumentation.ActivityMonitor] 代替选择器作答, 不点按任何系统界面. 覆盖: 可持久授权
 * 被持久化且文档进入列表, 一次性授权不进入; 挤出, 移除与清空都释放授权, 持久授权至多 [RecentFiles.MAX] 个; 文档已删除或授权被撤销时,
 * 打开即移除条目; 首页列出选取的文档, 重启后仍能在查看器中再次打开, 文档删除后移除.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RecentDocumentsDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("recent-documents-test")

    private val context get() = environment.context

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val providerUri: Uri = "content://${instrumentation.context.packageName}.grants".toUri()

    @Before
    fun setUp() {
        environment.prepare()
        provider(GrantingTestProvider.METHOD_RESET)
        RecentFiles.clear(context)
        assertEquals(emptyList<Uri>(), persisted())
    }

    @After
    fun tearDown() {
        RecentFiles.clear(context)
        provider(GrantingTestProvider.METHOD_RESET)
        environment.restore()
    }

    @Test
    fun aPersistableGrantIsTakenAndTheDocumentListedFirst() {
        val local = File(environment.root, "local.pdf").apply { writeBytes(PDF_HEADER) }
        RecentFiles.add(context, local)
        val uri = document("picked.pdf", persistable = true)

        val entry = RecentFiles.adopt(context, uri, PICKED)

        assertEquals(RecentEntry.Document(uri.toString(), "picked.pdf"), entry)
        assertEquals(listOf(entry, RecentEntry.Local(local.absolutePath)), RecentFiles.read(context))
        assertEquals(listOf(uri), persisted())
        assertTrue("the persisted grant reads the document", readsPdf(uri))
        assertEquals(entry, RecentFiles.reopen(context, entry!!))
        assertEquals("a local file does not touch the grants", listOf(uri), persisted())
    }

    @Test
    fun oneTimeGrantsStayOutOfTheList() {
        val uri = document("once.pdf", persistable = false)
        assertTrue("the one-time grant reads the document", readsPdf(uri))

        assertNull("no persistable flag", RecentFiles.adopt(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertNull("the flag without a persistable grant", RecentFiles.adopt(context, uri, PICKED))
        assertNull("not a content uri", RecentFiles.adopt(context, Uri.fromFile(File(environment.root, "x.pdf")), PICKED))

        assertEquals(emptyList<RecentEntry>(), RecentFiles.read(context))
        assertEquals(emptyList<Uri>(), persisted())
    }

    @Test
    fun evictingRemovingAndClearingReleaseTheGrants() {
        val uris = (0..RecentFiles.MAX).map { document("doc-$it.pdf", persistable = true) }
        uris.forEach { assertNotNull(RecentFiles.adopt(context, it, PICKED)) }

        val listed = RecentFiles.read(context)
        assertEquals(RecentFiles.MAX, listed.size)
        assertEquals("newest first", uris.last().toString(), (listed.first() as RecentEntry.Document).uri)
        assertEquals("the oldest is evicted with its grant", uris.drop(1).toSet(), persisted().toSet())

        val removed = listed[3]
        RecentFiles.remove(context, removed)
        assertFalse(persisted().any { it.toString() == removed.key })
        assertEquals(RecentFiles.MAX - 1, persisted().size)

        RecentFiles.clear(context)
        assertEquals(emptyList<RecentEntry>(), RecentFiles.read(context))
        assertEquals(emptyList<Uri>(), persisted())
    }

    @Test
    fun aDeletedDocumentIsRemovedWhenItOpens() {
        val uri = document("deleted.pdf", persistable = true)
        val entry = checkNotNull(RecentFiles.adopt(context, uri, PICKED))
        provider(GrantingTestProvider.METHOD_DELETE, uri.toString())

        assertNull(RecentFiles.reopen(context, entry))
        assertEquals(emptyList<RecentEntry>(), RecentFiles.read(context))
        assertEquals("the grant is released with the entry", emptyList<Uri>(), persisted())
    }

    @Test
    fun aRevokedGrantRemovesTheEntryWhenItOpens() {
        val uri = document("revoked.pdf", persistable = true)
        val entry = checkNotNull(RecentFiles.adopt(context, uri, PICKED))
        provider(GrantingTestProvider.METHOD_REVOKE, uri.toString())
        assertEquals("the provider revoked the persisted grant", emptyList<Uri>(), persisted())

        assertNull(RecentFiles.reopen(context, entry))
        assertEquals(emptyList<RecentEntry>(), RecentFiles.read(context))
    }

    @Test
    fun thePickerContractKeepsTheGrantFlags() {
        val uri = document("contract.pdf", persistable = false)
        val contract = OpenPersistableDocument()
        val request = contract.createIntent(context, arrayOf(MIME_PDF))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)

        val result = Intent().setData(uri).addFlags(PICKED)
        assertEquals(PickedDocument(uri, PICKED), contract.parseResult(Activity.RESULT_OK, result))
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, result))
        assertNull(contract.parseResult(Activity.RESULT_OK, Intent()))
        assertNull(contract.parseResult(Activity.RESULT_OK, null))
    }

    @Test
    fun theHomeScreenListsAPickedDocumentReopensItAfterARestartAndDropsItOnceDeleted() {
        val uri = document(NAME, persistable = true)
        val picker = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addDataType("*/*"); addCategory(Intent.CATEGORY_OPENABLE) },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(PICKED)),
            true,
        )
        val viewer = instrumentation.addMonitor(ThreePaperPdfViewerActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                awaitListed(EMPTY_TAG)
                // The tool list can push the recent section below the screen, so scroll back to the open entry.
                // zh-CN: 工具列表可能把最近文件区挤出屏幕, 因此先滚回 "打开" 一项.
                awaitListed("paper.home.open")
                click("paper.home.open")
                click("browser.system")
                assertEquals(1, picker.hits)
                // The picked document opens in the viewer at once. zh-CN: 选取的文档立即在查看器中打开.
                closeViewer(uri)
                awaitCondition { RecentFiles.read(context).firstOrNull() == RecentEntry.Document(uri.toString(), NAME) }
                assertEquals(listOf(uri), persisted())
                awaitRecent()
                compose.onNodeWithTag(RECENT_TAG).assert(hasText(context.getString(R.string.home_recent_picked)))
            }

            // A new start reads the entry back and opens the document under the persisted grant.
            // zh-CN: 重新启动后读回条目, 并凭持久授权打开文档.
            ActivityScenario.launch(HomeActivity::class.java).use {
                awaitRecent()
                compose.onNodeWithTag(RECENT_TAG).performClick()
                closeViewer(uri)

                provider(GrantingTestProvider.METHOD_DELETE, uri.toString())
                awaitRecent()
                compose.onNodeWithTag(RECENT_TAG).performClick()
                awaitListed(EMPTY_TAG)
                assertEquals(emptyList<RecentEntry>(), RecentFiles.read(context))
                assertEquals(emptyList<Uri>(), persisted())
                assertEquals("a deleted document does not reach the viewer", 2, viewer.hits)
            }
        } finally {
            instrumentation.removeMonitor(picker)
            instrumentation.removeMonitor(viewer)
        }
    }

    /** A document of the test provider with a read grant for the plugin. zh-CN: 测试提供方的一个文档, 并授予插件读取权限. */
    private fun document(name: String, persistable: Boolean): Uri {
        val uri = checkNotNull(provider(GrantingTestProvider.METHOD_CREATE, name).getString(GrantingTestProvider.EXTRA_URI)).toUri()
        provider(
            GrantingTestProvider.METHOD_GRANT, uri.toString(),
            bundleOf(GrantingTestProvider.EXTRA_PACKAGE to context.packageName, GrantingTestProvider.EXTRA_PERSISTABLE to persistable),
        )
        return uri
    }

    private fun provider(method: String, arg: String? = null, extras: Bundle? = null): Bundle =
        checkNotNull(context.contentResolver.call(providerUri, method, arg, extras ?: Bundle()))

    private fun persisted(): List<Uri> = context.contentResolver.persistedUriPermissions
        .map { it.uri }
        .filter { it.authority == providerUri.authority }

    private fun readsPdf(uri: Uri): Boolean = context.contentResolver.openInputStream(uri)?.use { input ->
        val head = ByteArray(PDF_HEADER.size)
        input.read(head) == head.size && head.contentEquals(PDF_HEADER)
    } ?: false

    /**
     * Waits for the viewer to open [uri] and the first page to show, closes it and waits for the home screen. The viewer is
     * looked up by its lifecycle stage, not taken from the activity monitor: a monitor also records an activity when it
     * resumes, so it may still hold the viewer of an earlier opening.
     * zh-CN: 等查看器打开 [uri] 并显示第一页, 关闭它并等首页回到前台. 查看器按生命周期阶段查找, 不从活动监视器取: 监视器在活动恢复时也会记录
     * 该活动, 可能仍持有之前那次打开的查看器.
     */
    private fun closeViewer(uri: Uri) {
        awaitCondition { viewers(Stage.RESUMED).any { it.intent.data == uri } }
        awaitText("viewer-page-indicator", context.getString(R.string.viewer_page_indicator, 1, 1))
        val open = viewers(Stage.RESUMED)
        assertEquals("one viewer of the document", listOf(uri), open.map { it.intent.data })
        instrumentation.runOnMainSync { open.forEach(Activity::finish) }
        awaitCondition {
            LIVE_STAGES.all { viewers(it).isEmpty() } && activities(Stage.RESUMED).any { it is HomeActivity }
        }
    }

    private fun viewers(stage: Stage): List<Activity> = activities(stage).filterIsInstance<ThreePaperPdfViewerActivity>()

    private fun activities(stage: Stage): List<Activity> {
        var result = emptyList<Activity>()
        instrumentation.runOnMainSync { result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).toList() }
        return result
    }

    private fun awaitRecent() = awaitListed(RECENT_TAG)

    /** Waits until the home list holds [tag] and scrolls it into view. zh-CN: 等首页列表含有 [tag], 并将其滚动到可见处. */
    private fun awaitListed(tag: String) {
        awaitTag("paper.home")
        awaitCondition {
            runCatching { compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    private fun click(tag: String) {
        awaitTag(tag)
        compose.onNodeWithTag(tag).performClick()
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
        const val MIME_PDF = "application/pdf"
        const val NAME = "picked.pdf"
        const val RECENT_TAG = "paper.home.recent.$NAME"
        const val EMPTY_TAG = "paper.home.recent.empty"
        const val PICKED = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        val PDF_HEADER = "%PDF-".toByteArray()
        val LIVE_STAGES = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED)
    }

}

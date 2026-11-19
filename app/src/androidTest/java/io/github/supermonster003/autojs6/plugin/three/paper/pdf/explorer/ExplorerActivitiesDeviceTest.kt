package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import java.io.File
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.PdfContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The file manager entries on a device, started the way the host starts them (`EXECUTE` with the targets, their
 * granted URIs and the host session; roadmap P3.3, appendix D Q70 / Q75): the open action shows the viewer under the
 * file manager's name, a tool screen writes next to the selection, a locked input asks for its password and the retry
 * after `PERMISSION_DENIED` takes the owner password, requests without a session or a file cannot start, and the
 * compress and watermark entries of P6.12 write their results next to the selection (read back through the engine).
 * zh-CN: 以宿主的方式在设备上启动文件管理器入口 (`EXECUTE`, 带目标, 已授权的 URI 与宿主会话; 路线图 P3.3, 附录 D Q70 / Q75): 打开
 * 动作以文件管理器给出的名称显示查看器, 工具界面把结果写到选中文件旁边, 被锁定的输入询问密码, `PERMISSION_DENIED` 之后的重试接受
 * 所有者密码, 没有会话或文件的请求无法开始, 以及 P6.12 的压缩与加水印入口把结果写到选中文件旁边 (经引擎回读).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ExplorerActivitiesDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = PdfServiceHarness.context

    private val root by lazy { File(context.cacheDir, "explorer-activity-test") }

    private val host by lazy { FakeHostSession(root) }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun theOpenActionShowsTheSelectionInTheViewerUnderItsDisplayName() {
        launch(request(ExplorerTool.OPEN, "outline.pdf" to "Book.pdf")).use {
            awaitText("viewer-page-indicator", context.getString(R.string.viewer_page_indicator, 1, OUTLINE_PAGES))
            compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("Book.pdf").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun encryptingFromTheScreenWritesNextToTheSelectionAndClosesTheSession() {
        launch(request(ExplorerTool.ENCRYPT, "text.pdf" to "Text.pdf")).use {
            awaitTag("tool-new-password")
            compose.onNodeWithTag("tool-new-password").performTextReplacement("s3cret")
            compose.onNodeWithTag("tool-confirm-password").performTextReplacement("s3cret")
            start()
            awaitText("tool-done", context.getString(R.string.tool_done_saved, "${FakeHostSession.PARENT_PATH}/Text_encrypted.pdf"))
            assertEquals(listOf("Text_encrypted.pdf"), host.files())
            assertEquals("the screen attaches one client binder", 1, host.clients.size)
        }
        assertTrue("leaving the screen closes the host session", PdfServiceHarness.eventually { host.closed })
    }

    @Test
    fun mergingTwoSelectedFilesKeepsTheSelectionOrder() {
        launch(request(ExplorerTool.MERGE, "outline.pdf" to "Outline.pdf", "text.pdf" to "Text.pdf")).use {
            awaitTag("tool-merge-outline")
            start()
            awaitText("tool-done", context.getString(R.string.tool_done_saved, "${FakeHostSession.PARENT_PATH}/Outline_merged.pdf"))
            assertEquals(listOf("Outline_merged.pdf"), host.files())
        }
    }

    @Test
    fun aLockedInputAsksForItsPasswordAndTheRetryTakesTheOwnerPassword() {
        launch(request(ExplorerTool.SPLIT, "encrypted.pdf" to "Encrypted.pdf")).use {
            unlock("paper-user")
            start()
            awaitText("tool-error", context.getString(R.string.tool_error_permission))
            assertEquals(listOf<String>(), host.files())
            compose.onNodeWithTag("tool-retry").performClick()
            unlock("paper-owner")
            start()
            awaitText("tool-done", context.getString(R.string.tool_done_saved, "${FakeHostSession.PARENT_PATH}/Encrypted_split"))
            assertEquals(listOf("Encrypted_split/Encrypted-01.pdf", "Encrypted_split/Encrypted-02.pdf"), host.files())
        }
    }

    @Test
    fun compressingFromTheMenuWritesTheSmallerCopyNextToTheSelection() {
        launch(request(ExplorerTool.COMPRESS, "images.pdf" to "Images.pdf")).use {
            awaitTag("tool-compress-${PdfContract.COMPRESS_PRESET_LOW}")
            compose.onNodeWithTag("tool-compress-${PdfContract.COMPRESS_PRESET_LOW}").performScrollTo().performClick()
            start()
            awaitText("tool-done", context.getString(R.string.tool_done_saved, "${FakeHostSession.PARENT_PATH}/Images_compressed.pdf"))
            awaitTag("tool-result-summary")
            assertEquals(listOf("Images_compressed.pdf"), host.files())
            val result = File(host.directory, "Images_compressed.pdf")
            val source = PdfServiceHarness.sampleFile("images.pdf")
            assertTrue("${result.length()} < ${source.length()}", result.length() < source.length())
            assertEquals(1, opened(result) { it.getInfo().getInt(PdfContract.KEY_PAGE_COUNT) })
        }
    }

    @Test
    fun addingAWatermarkFromTheMenuWritesTheStampedCopyNextToTheSelection() {
        launch(request(ExplorerTool.WATERMARK, "text.pdf" to "Text.pdf")).use {
            awaitTag("tool-watermark-text")
            compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement(WATERMARK)
            // The file manager screen shows the same one-page preview as the toolbox. zh-CN: 文件管理器界面与工具箱一样显示单页预览.
            awaitTag("tool-watermark-preview")
            start()
            awaitText("tool-done", context.getString(R.string.tool_done_saved, "${FakeHostSession.PARENT_PATH}/Text_watermarked.pdf"))
            assertEquals(listOf("Text_watermarked.pdf"), host.files())
            val text = opened(File(host.directory, "Text_watermarked.pdf")) { document ->
                val answer = document.getText(request { putString(PdfContract.KEY_PAGES, "1") })
                assertNull(answer.getString(PdfContract.KEY_ERROR_MESSAGE), answer.getString(PdfContract.KEY_ERROR_CODE))
                checkNotNull(answer.getStringArray(PdfContract.KEY_TEXTS)).joinToString(" ").filterNot { it.isWhitespace() }
            }
            assertTrue(text, text.contains(WATERMARK))
        }
    }

    @Test
    fun withoutAHostSessionTheToolCannotStart() {
        launch(request(ExplorerTool.EXTRACT_TEXT, "text.pdf" to "Text.pdf", session = null)).use {
            awaitTag("tool-pages")
            compose.onNodeWithTag("tool-start").assertIsNotEnabled()
        }
    }

    @Test
    fun aRequestWithoutAKnownToolOrAGrantedFileShowsTheNoFilesError() {
        val unknown = request(ExplorerTool.SPLIT, "text.pdf" to "Text.pdf").putExtra(ExplorerActionIntentExtras.ACTION_ID, "unknown")
        launch(unknown).use { awaitText("tool-error", context.getString(R.string.tool_error_no_files)) }
        val ungranted = request(ExplorerTool.SPLIT, "text.pdf" to "Text.pdf").apply {
            data = null
            clipData = null
        }
        launch(ungranted).use {
            awaitText("tool-error", context.getString(R.string.tool_error_no_files))
            assertTrue("nothing to retry", compose.onAllNodesWithTag("tool-retry").fetchSemanticsNodes().isEmpty())
        }
    }

    private fun launch(intent: Intent): ActivityScenario<Activity> = ActivityScenario.launch(intent)

    /** The intent of the host for [tool] on [targets] (sample to display name). zh-CN: 宿主针对 [targets] (样例到显示名) 启动 [tool] 的 intent. */
    private fun request(tool: ExplorerTool, vararg targets: Pair<String, String>, session: FakeHostSession? = host): Intent {
        val uris = targets.map { (sample, _) -> Uri.fromFile(PdfServiceHarness.sampleFile(sample)) }
        val described = targets.mapIndexed { index, (_, name) ->
            Bundle().apply {
                putParcelable(ExplorerActionTargetKeys.URI, uris[index])
                putString(ExplorerActionTargetKeys.DISPLAY_NAME, name)
            }
        }
        return Intent(ExplorerActionPluginActions.EXECUTE).setClass(context, tool.activityClass).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            data = uris.first()
            clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            putExtra(ExplorerActionIntentExtras.ACTION_ID, tool.id)
            putParcelableArrayListExtra(ExplorerActionIntentExtras.TARGETS, ArrayList(described))
            putExtra(ExplorerActionIntentExtras.PARENT_DISPLAY_PATH, FakeHostSession.PARENT_PATH)
            session?.let { putExtra(ExplorerActionIntentExtras.HOST_SESSION, Bundle().apply { putBinder(ExplorerActionHostSessionKeys.BINDER, it) }) }
        }
    }

    private fun unlock(password: String) {
        awaitTag("tool-password-0")
        compose.onNodeWithTag("tool-password-0").performTextReplacement(password)
        compose.onNodeWithTag("tool-unlock-0").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag("tool-password-0").fetchSemanticsNodes().isEmpty() }
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

    /** Opens [file] in the engine (contract version 2) for [block]. zh-CN: 在引擎中打开 [file] (契约版本 2) 交给 [block]. */
    private fun <T> opened(file: File, block: (IPdfDocument) -> T): T {
        assertTrue("${file.name} was not written", file.isFile)
        return withPdfPlugin { plugin ->
            val handle = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                plugin.open(source, request { putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2) })
            }
            val document = IPdfDocument.Stub.asInterface(MarshallingBinder(handle.asBinder()))
            try {
                block(document)
            } finally {
                document.close()
            }
        }
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
        const val START_ATTEMPTS = 3
        const val START_WAIT_MILLIS = 5_000L
        const val OUTLINE_PAGES = 6
        const val WATERMARK = "CONFIDENTIAL"
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import org.autojs.plugin.pdf.api.PdfActions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * The viewer Activity on a device (roadmap P3.1): page count and jump on a sample opened by a `file:` URI, the state
 * kept across recreation, a `content:` URI with its provider name, the session token entry (start page, search from
 * there, an unknown token), the password prompt, and 100 pages scrolled without blocking the main thread (the timing
 * is logged under `PaperViewerPerf`).
 * zh-CN: 设备上的查看器 Activity (路线图 P3.1): 以 `file:` URI 打开样例时的页数与跳页, 重建后保留的状态, 带提供方名称的 `content:`
 * URI, 会话令牌入口 (起始页, 从起始页搜索, 未知令牌), 密码提示, 以及滚动 100 页时主线程不被阻塞 (耗时记录在 `PaperViewerPerf` 下).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ViewerActivityDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = PdfServiceHarness.context

    @Test
    fun aFileUriShowsThePageCountJumpsAndKeepsThePageAcrossRecreation() {
        val count = pagesOf("outline.pdf")
        launch(viewIntent(Uri.fromFile(PdfServiceHarness.sampleFile("outline.pdf")))).use { scenario ->
            awaitIndicator(1, count)
            compose.onNodeWithTag("viewer-page-indicator").performClick()
            compose.onNodeWithTag("viewer-jump-field").performTextInput("4")
            compose.onNodeWithTag("viewer-jump-go").performClick()
            awaitIndicator(4, count)
            scenario.recreate()
            awaitIndicator(4, count)
        }
    }

    @Test
    fun aContentUriShowsTheProviderNameAndItsPages() {
        val directory = File(context.cacheDir, "${ViewerViewModel.SHARE_DIRECTORY}/device-test").apply { mkdirs() }
        try {
            val file = PdfServiceHarness.sampleFile("text.pdf").copyTo(File(directory, "text.pdf"), overwrite = true)
            val uri = FileProvider.getUriForFile(context, ThreePaperPdfViewerActivity.authorityOf(context), file)
            launch(viewIntent(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)).use {
                awaitIndicator(1, pagesOf("text.pdf"))
                compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("text.pdf").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aClaimedTokenStartsAtItsPageAndSearchesFromThere() {
        val claim = ViewerClaim(PdfServiceHarness.sample("outline.pdf"), "Outline sample", null, "needle")
        val session = ViewerSessions.open(host = Any(), claim = claim, startPage = 2, events = null)
        launch(tokenIntent(session.token)).use {
            // The needle is on pages 2 and 5: the first hit from page 3 on is on page 5.
            // zh-CN: needle 位于第 2 与第 5 页: 从第 3 页起的第一个命中在第 5 页.
            awaitIndicator(5, pagesOf("outline.pdf"))
            awaitText("viewer-search-status", context.getString(R.string.viewer_search_count, 2, 2))
            // The token was claimed once. zh-CN: 令牌只能认领一次.
            assertNull(ViewerSessions.claim(session.token, IdleController))
        }
        // Closing the viewer ended the session. zh-CN: 关闭查看器结束了会话.
        assertTrue(PdfServiceHarness.eventually { !session.isOpen })
    }

    @Test
    fun thePackagedActionReachesTheViewerThroughItsAliasAndClaimsAToken() {
        // Roadmap D43: packaged apps start the alias `.viewer.PackagedPdfViewerActivity` with the packaged action; the
        // session token stays the credential.
        // zh-CN: 路线图 D43: 打包应用以打包 action 启动别名 `.viewer.PackagedPdfViewerActivity`; 会话令牌仍是凭据.
        val query = Intent(PdfActions.PACKAGED_VIEWER_ACTIVITY_ACTION).setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(query, 0).single().activityInfo
        assertEquals(ThreePaperPdfViewerActivity::class.java.`package`!!.name + ".PackagedPdfViewerActivity", resolved.name)
        assertEquals(ThreePaperPdfViewerActivity::class.java.name, resolved.targetActivity)
        val claim = ViewerClaim(PdfServiceHarness.sample("outline.pdf"), "Outline sample", null, null)
        // startPage is the zero-based index: page 3. zh-CN: startPage 是从 0 起的下标: 第 3 页.
        val session = ViewerSessions.open(host = Any(), claim = claim, startPage = 2, events = null)
        launch(tokenIntent(session.token, PdfActions.PACKAGED_VIEWER_ACTIVITY_ACTION)).use {
            awaitIndicator(3, pagesOf("outline.pdf"))
            assertNull(ViewerSessions.claim(session.token, IdleController))
        }
        assertTrue(PdfServiceHarness.eventually { !session.isOpen })
    }

    @Test
    fun anUnknownTokenShowsTheExpiredMessage() {
        launch(tokenIntent("unknown-token")).use {
            awaitText("viewer-error", context.getString(R.string.viewer_error_expired))
        }
    }

    @Test
    fun anEncryptedFileAsksForThePasswordUntilItIsRight() {
        launch(viewIntent(Uri.fromFile(PdfServiceHarness.sampleFile("encrypted.pdf")))).use {
            awaitTag("viewer-password-field")
            compose.onNodeWithTag("viewer-password-field").performTextInput("wrong")
            submitPassword()
            compose.waitUntil(TIMEOUT_MILLIS) {
                compose.onAllNodesWithText(context.getString(R.string.viewer_password_wrong)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("viewer-password-field").performTextReplacement(fixture("encrypted.pdf").getString("userPassword"))
            submitPassword()
            awaitIndicator(1, pagesOf("encrypted.pdf"))
        }
    }

    @Test
    fun aHundredPagesScrollWithoutBlockingTheMainThread() {
        val file = hundredPages()
        launch(viewIntent(Uri.fromFile(file))).use {
            awaitIndicator(1, HUNDRED)
            var worst = 0L
            val started = SystemClock.elapsedRealtime()
            repeat(SWIPES) {
                // The press point decides which node gets the gesture: start above the page indicator, which reaches
                // 0.74 of the height on a 720 x 1280 screen (G8441), and above the thumbnail bar.
                // zh-CN: 按下点决定由哪个节点接收手势: 从页码指示器上方开始 (720 x 1280 屏幕 (G8441) 上它伸到高度的 0.74), 也避开缩略图栏.
                compose.onNodeWithTag(VIEWER_CANVAS_TAG).performTouchInput {
                    swipe(Offset(centerX, height * 0.6f), Offset(centerX, height * 0.15f), durationMillis = 150)
                }
                worst = max(worst, mainThreadLatency())
            }
            compose.waitForIdle()
            val page = indicatorPage()
            Log.i(
                "PaperViewerPerf",
                "scroll pages=$HUNDRED swipes=$SWIPES lastPage=$page worstMainLatencyMs=$worst " +
                    "totalMs=${SystemClock.elapsedRealtime() - started} model=${Build.MODEL} api=${Build.VERSION.SDK_INT}",
            )
            assertTrue("the swipes did not scroll (page $page)", page > 1)
            assertTrue("the main thread was blocked for $worst ms", worst < MAX_MAIN_LATENCY_MILLIS)
        }
    }

    @Test
    fun theFirstPageOpensBelowTheTopBarTheLastScrollsAboveTheThumbnailsAndTheBarsMoveNothing() {
        // E4 feedback F1: a short and a longer document, in portrait and in landscape. zh-CN: E4 反馈 F1: 一份短文档与一份较长的文档, 竖屏与横屏.
        for (name in listOf("outline.pdf", "text.pdf")) {
            val count = pagesOf(name)
            launch(viewIntent(Uri.fromFile(PdfServiceHarness.sampleFile(name)))).use { scenario ->
                awaitIndicator(1, count)
                checkBarsAndPages(scenario, count, name)
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
                compose.waitUntil(TIMEOUT_MILLIS) { scenario.isLandscape() }
                checkBarsAndPages(scenario, count, "$name landscape")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            }
        }
    }

    // Helpers. zh-CN: 辅助方法.

    /**
     * From the first page: its top is below the top bar, a tap hides the bars without moving it and another shows them;
     * at the last page its bottom is above the thumbnail bar.
     * zh-CN: 从第一页开始: 其顶边在顶栏之下, 单击隐藏各栏而它不动, 再单击显示各栏; 到最后一页时其底边在缩略图栏之上.
     */
    private fun checkBarsAndPages(scenario: ActivityScenario<ThreePaperPdfViewerActivity>, count: Int, label: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            var ready = false
            scenario.onActivity { ready = modelOf(it).layout != null }
            ready
        }
        scenario.onActivity { modelOf(it).goToPage(0) }
        awaitIndicator(1, count)
        awaitTag(VIEWER_TOP_BAR_TAG)
        compose.waitForIdle()
        val barBottom = boundsOf(VIEWER_TOP_BAR_TAG).bottom
        val top = pageEdge(scenario, 0, bottom = false)
        assertTrue("$label: the first page starts at $top, under the top bar ending at $barBottom", top >= barBottom)
        compose.onNodeWithTag(VIEWER_CANVAS_TAG).performTouchInput { click(Offset(centerX, height * 0.45f)) }
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(VIEWER_TOP_BAR_TAG).fetchSemanticsNodes().isEmpty() }
        assertEquals("$label: hiding the bars moved the first page", top, pageEdge(scenario, 0, bottom = false), 0.5f)
        compose.onNodeWithTag(VIEWER_CANVAS_TAG).performTouchInput { click(Offset(centerX, height * 0.45f)) }
        awaitTag(VIEWER_THUMBNAILS_TAG)
        scenario.onActivity { modelOf(it).goToPage(count - 1) }
        awaitIndicator(count, count)
        compose.waitForIdle()
        val stripTop = boundsOf(VIEWER_THUMBNAILS_TAG).top
        val bottom = pageEdge(scenario, count - 1, bottom = true)
        assertTrue("$label: the last page ends at $bottom, under the thumbnails starting at $stripTop", bottom <= stripTop)
    }

    /** The top or bottom of page [page] on screen (root coordinates). zh-CN: 第 [page] 页在屏幕上的顶边或底边 (根坐标). */
    private fun pageEdge(scenario: ActivityScenario<ThreePaperPdfViewerActivity>, page: Int, bottom: Boolean): Float {
        val canvasTop = boundsOf(VIEWER_CANVAS_TAG).top
        var edge = 0f
        scenario.onActivity { activity ->
            val model = modelOf(activity)
            val layout = checkNotNull(model.layout)
            val zoom = model.position.zoom
            edge = layout.viewportY(layout.pageTop(page, zoom) + if (bottom) layout.pageHeight(page, zoom) else 0f, model.position)
        }
        return canvasTop + edge
    }

    private fun boundsOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun modelOf(activity: ThreePaperPdfViewerActivity): ViewerViewModel = ViewModelProvider(activity)[ViewerViewModel::class.java]

    private fun ActivityScenario<ThreePaperPdfViewerActivity>.isLandscape(): Boolean {
        var landscape = false
        onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        return landscape
    }

    private fun launch(intent: Intent): ActivityScenario<ThreePaperPdfViewerActivity> = ActivityScenario.launch(intent)

    private fun viewIntent(uri: Uri) = Intent(Intent.ACTION_VIEW)
        .setClass(context, ThreePaperPdfViewerActivity::class.java)
        .setDataAndType(uri, ThreePaperPdfViewerActivity.MIME_PDF)

    private fun tokenIntent(token: String, action: String = PdfActions.VIEWER_ACTIVITY_ACTION) = Intent(action)
        .setClass(context, ThreePaperPdfViewerActivity::class.java)
        .putExtra(PdfActions.EXTRA_SESSION_TOKEN, token)

    private fun submitPassword() {
        compose.onNodeWithText(context.getString(R.string.viewer_password_open)).performClick()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitText(tag: String, expected: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { textsOf(tag).any { it == expected } }
    }

    private fun awaitIndicator(page: Int, count: Int) = awaitText("viewer-page-indicator", context.getString(R.string.viewer_page_indicator, page, count))

    private fun textsOf(tag: String): List<String> = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    /** The current page of the indicator, read back through the indicator format. zh-CN: 页码指示中的当前页, 按指示格式反向读取. */
    private fun indicatorPage(): Int {
        val text = textsOf("viewer-page-indicator").single()
        return (1..HUNDRED).first { context.getString(R.string.viewer_page_indicator, it, HUNDRED) == text }
    }

    /** Milliseconds until a runnable posted now runs on the main thread. zh-CN: 现在投递的任务在主线程上运行前经过的毫秒数. */
    private fun mainThreadLatency(): Long {
        val latch = CountDownLatch(1)
        val start = SystemClock.elapsedRealtime()
        Handler(Looper.getMainLooper()).post { latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
        return SystemClock.elapsedRealtime() - start
    }

    private fun fixture(name: String): JSONObject {
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures.json").bufferedReader().use { it.readText() }
        return JSONObject(json).getJSONObject("fixtures").getJSONObject(name)
    }

    private fun pagesOf(name: String): Int = fixture(name).getInt("pages")

    /** A text document of [HUNDRED] A4 pages, generated once into the cache. zh-CN: [HUNDRED] 页 A4 文本文档, 只生成一次到缓存. */
    private fun hundredPages(): File {
        val file = File(context.cacheDir, "samples/hundred-pages.pdf")
        if (file.exists()) return file
        file.parentFile?.mkdirs()
        PDDocument().use { document ->
            repeat(HUNDRED) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 28f)
                    stream.newLineAtOffset(72f, 760f)
                    stream.showText("Page ${index + 1} of $HUNDRED")
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.setLeading(15f)
                    stream.newLineAtOffset(0f, -36f)
                    repeat(40) { line -> stream.showText("Line ${line + 1}: The quick brown fox jumps over the lazy dog."); stream.newLine() }
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    /** A viewer that never claims anything in this test. zh-CN: 本测试中不认领任何会话的查看器. */
    private object IdleController : ViewerController {
        override fun goTo(page: Int) = Unit
        override fun finish() = Unit
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val HUNDRED = 100
        const val SWIPES = 30

        /** Well below the 5 s input ANR threshold. zh-CN: 远低于 5 秒的输入 ANR 阈值. */
        const val MAX_MAIN_LATENCY_MILLIS = 1_000L
    }

}

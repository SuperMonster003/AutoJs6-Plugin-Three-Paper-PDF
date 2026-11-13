package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.eventually
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfViewerCallback
import org.autojs.plugin.pdf.api.IPdfViewerSession
import org.autojs.plugin.pdf.api.PdfActions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * `openViewer` through the PDF service with the viewer Activity on a device (protocol "Viewer Sessions", roadmap P3.2):
 * the pending state, the claim by the token, `onPage`, `goTo`, `close(true)` finishing the Activity, `close(false)`
 * leaving it open without events, the viewer closing (`closed`), a later session of the same host (`replaced`) and
 * refused options. The claim timeout (60 s) is covered by the JVM tests.
 * zh-CN: 设备上经 PDF 服务调用 `openViewer` 并配合查看器 Activity (协议 "Viewer Sessions" 一节, 路线图 P3.2): 待认领状态, 凭令牌
 * 认领, `onPage`, `goTo`, `close(true)` 结束 Activity, `close(false)` 让其保持打开且不再发送事件, 查看器关闭 (`closed`), 同一宿主
 * 的后一个会话 (`replaced`), 以及被拒绝的选项. 认领超时 (60 秒) 由 JVM 测试覆盖.
 */
@RunWith(AndroidJUnit4::class)
class ViewerSessionDeviceTest {

    private val context get() = PdfServiceHarness.context

    @Test
    fun aScriptSessionFollowsTheViewerAndFinishesIt() {
        withPdfPlugin { plugin ->
            val events = Events()
            val session = openViewer(plugin, events, page = 3)
            val pending = session.state
            val token = pending.getString(PdfContract.KEY_TOKEN).orEmpty()
            assertTrue(token, Regex("[0-9a-f]{64}").matches(token))
            assertEquals(PdfContract.CONTRACT_VERSION, pending.getInt(PdfContract.KEY_CONTRACT_VERSION))
            assertEquals(3, pending.getInt(PdfContract.KEY_PAGE))
            assertEquals("the page count is known once the viewer loaded the document", 0, pending.getInt(PdfContract.KEY_PAGE_COUNT))
            assertFalse(pending.getBoolean(PdfContract.KEY_VISIBLE))
            ActivityScenario.launch<ThreePaperPdfViewerActivity>(tokenIntent(token)).use { scenario ->
                assertTrue("no onPage for the start page: ${events.pages}", eventually(TIMEOUT_MILLIS) { events.pages.contains(3 to PAGES) })
                assertTrue(eventually(TIMEOUT_MILLIS) { session.state.getBoolean(PdfContract.KEY_VISIBLE) })
                session.goTo(2)
                val moved = session.state
                assertEquals(2, moved.getInt(PdfContract.KEY_PAGE))
                assertEquals(PAGES, moved.getInt(PdfContract.KEY_PAGE_COUNT))
                assertTrue("no onPage for page 2: ${events.pages}", eventually(TIMEOUT_MILLIS) { events.pages.lastOrNull() == 2 to PAGES })
                assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, codeOf { session.goTo(PAGES + 1) })
                assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, codeOf { session.goTo(0) })
                assertTrue("events carry the contract version", events.versions.all { it == PdfContract.CONTRACT_VERSION })
                assertTrue("events carry the session token", events.tokens.all { it == token })
                session.close(true)
                assertTrue("close(true) did not finish the viewer", eventually(TIMEOUT_MILLIS) { scenario.state == Lifecycle.State.DESTROYED })
                assertTrue(eventually { events.closed.isNotEmpty() })
                assertEquals(listOf(PdfContract.REASON_FINISHED), events.closed.toList())
                assertEquals(PdfErrorCodes.SESSION_CLOSED, session.state.getString(PdfContract.KEY_ERROR_CODE))
                assertEquals(PdfErrorCodes.SESSION_CLOSED, codeOf { session.goTo(1) })
            }
        }
    }

    @Test
    fun closingTheViewerEndsTheSessionAndALaterSessionReplacesAnEarlierOne() {
        withPdfPlugin { plugin ->
            val first = Events()
            val firstSession = openViewer(plugin, first)
            val second = Events()
            val secondSession = openViewer(plugin, second)
            assertTrue(eventually { first.closed.isNotEmpty() })
            assertEquals(listOf(PdfContract.REASON_REPLACED), first.closed.toList())
            assertEquals(PdfErrorCodes.SESSION_CLOSED, firstSession.state.getString(PdfContract.KEY_ERROR_CODE))
            val token = secondSession.state.getString(PdfContract.KEY_TOKEN).orEmpty()
            ActivityScenario.launch<ThreePaperPdfViewerActivity>(tokenIntent(token)).use {
                assertTrue(eventually(TIMEOUT_MILLIS) { second.pages.isNotEmpty() })
                assertTrue(second.closed.isEmpty())
            }
            assertTrue("closing the viewer sent no onClosed", eventually(TIMEOUT_MILLIS) { second.closed.isNotEmpty() })
            assertEquals(listOf(PdfContract.REASON_CLOSED), second.closed.toList())
            assertEquals(listOf(PdfContract.REASON_REPLACED), first.closed.toList())
        }
    }

    @Test
    fun closeWithoutFinishLeavesTheViewerOpenAndSilent() {
        withPdfPlugin { plugin ->
            val events = Events()
            val session = openViewer(plugin, events)
            val token = session.state.getString(PdfContract.KEY_TOKEN).orEmpty()
            ActivityScenario.launch<ThreePaperPdfViewerActivity>(tokenIntent(token)).use { scenario ->
                assertTrue(eventually(TIMEOUT_MILLIS) { events.pages.isNotEmpty() })
                session.close(false)
                SystemClock.sleep(SETTLE_MILLIS)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                assertEquals(PdfErrorCodes.SESSION_CLOSED, session.state.getString(PdfContract.KEY_ERROR_CODE))
            }
            SystemClock.sleep(SETTLE_MILLIS)
            assertTrue("a detached session sent ${events.closed}", events.closed.isEmpty())
        }
    }

    @Test
    fun openViewerRefusesBadOptions() {
        withPdfPlugin { plugin ->
            val events = Events()
            val refusals = listOf(
                request { putInt(PdfContract.KEY_PAGE, 0) },
                request { putString(PdfContract.KEY_KEYWORD, "x".repeat(PdfContract.MAX_KEYWORD_LENGTH + 1)) },
                request { putString(PdfContract.KEY_KEYWORD, " ") },
                Bundle(),
            )
            refusals.forEach { options ->
                val error = assertThrows(IllegalArgumentException::class.java) {
                    PdfServiceHarness.sample("outline.pdf").use { plugin.openViewer(it, options, events) }
                }
                assertEquals(options.toString(), PdfErrorCodes.INVALID_ARGUMENT, PdfErrorCodes.decode(error.message)?.first)
            }
            assertEquals(PdfErrorCodes.INVALID_ARGUMENT, codeOf { plugin.openViewer(null, request(), events) })
            assertTrue(events.pages.isEmpty() && events.closed.isEmpty())
        }
    }

    // Helpers. zh-CN: 辅助方法.

    private fun openViewer(plugin: IPdfPlugin, events: Events, page: Int = 1): IPdfViewerSession {
        val options = request {
            putInt(PdfContract.KEY_PAGE, page)
            putString(PdfContract.KEY_DISPLAY_NAME, "outline.pdf")
        }
        val session = PdfServiceHarness.sample("outline.pdf").use { plugin.openViewer(it, options, events) }
        return IPdfViewerSession.Stub.asInterface(MarshallingBinder(session.asBinder()))
    }

    private fun tokenIntent(token: String) = Intent(PdfActions.VIEWER_ACTIVITY_ACTION)
        .setClass(context, ThreePaperPdfViewerActivity::class.java)
        .putExtra(PdfActions.EXTRA_SESSION_TOKEN, token)

    private fun codeOf(block: () -> Unit): String {
        val error = assertThrows(RuntimeException::class.java) { block() }
        return PdfErrorCodes.decode(error.message)?.first.orEmpty()
    }

    /** The host callback, recording what arrives. zh-CN: 记录收到内容的宿主回调. */
    private class Events : IPdfViewerCallback.Stub() {
        val pages = CopyOnWriteArrayList<Pair<Int, Int>>()
        val closed = CopyOnWriteArrayList<String>()
        val versions = CopyOnWriteArrayList<Int>()
        val tokens = CopyOnWriteArrayList<String>()

        override fun onPage(token: String?, event: Bundle?) {
            tokens += token.orEmpty()
            versions += event?.getInt(PdfContract.KEY_CONTRACT_VERSION) ?: -1
            pages += (event?.getInt(PdfContract.KEY_PAGE) ?: -1) to (event?.getInt(PdfContract.KEY_PAGE_COUNT) ?: -1)
        }

        override fun onClosed(token: String?, reason: String?) {
            tokens += token.orEmpty()
            closed += reason.orEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val SETTLE_MILLIS = 500L

        /** Pages of `outline.pdf` (samples/fixtures/fixtures.json). zh-CN: `outline.pdf` 的页数 (samples/fixtures/fixtures.json). */
        const val PAGES = 6
    }

}

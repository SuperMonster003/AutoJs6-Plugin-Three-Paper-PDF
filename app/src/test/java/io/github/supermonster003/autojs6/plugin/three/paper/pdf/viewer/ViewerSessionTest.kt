package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Viewer sessions without Binder (protocol "Viewer Sessions", roadmap P3.2, appendix D Q72 / Q73): the claim, the
 * state before and after it, page events, `goTo`, the four close reasons, `close(false)` without events, the claim
 * timeout and the options of `openViewer`.
 * zh-CN: 不经 Binder 的查看器会话 (协议 "Viewer Sessions" 一节, 路线图 P3.2, 附录 D Q72 / Q73): 认领, 认领前后的状态, 页事件,
 * `goTo`, 四种关闭原因, 不发送事件的 `close(false)`, 认领超时, 以及 `openViewer` 的选项.
 */
class ViewerSessionTest {

    private val scheduler = FakeScheduler()

    private var issued = 0

    private val registry = ViewerSessionRegistry(scheduler, claimTimeoutMillis = 60_000) { "token-${++issued}" }

    private fun open(host: Any = HOST, startPage: Int = 0, events: Recorder = Recorder(), source: FakeDescriptor = FakeDescriptor()) =
        registry.open(host, ViewerClaim(source, "a.pdf", null, null), startPage, events)

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(PdfFailure::class.java) { block() }.code)
    }

    @Test
    fun `a pending session answers its start page and an unknown page count`() {
        val session = open(startPage = 2)
        val state = session.state()
        assertEquals("token-1", state[PdfContract.KEY_TOKEN])
        assertEquals(3, state[PdfContract.KEY_PAGE])
        assertEquals(0, state[PdfContract.KEY_PAGE_COUNT])
        assertEquals(1f, state[PdfContract.KEY_ZOOM])
        assertEquals(false, state[PdfContract.KEY_VISIBLE])
        // Before the page count is known only pages below 1 are out of range (Q73). zh-CN: 页数未知时只有小于 1 的页超出范围 (Q73).
        session.goTo(40)
        assertEquals(39, session.startPage)
        assertEquals(40, session.state()[PdfContract.KEY_PAGE])
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { session.goTo(0) }
    }

    @Test
    fun `a token is claimed once`() {
        val session = open()
        val viewer = Controller()
        val claimed = registry.claim(session.token, viewer)
        assertNotNull(claimed)
        assertSame(session, claimed!!.session)
        assertEquals("a.pdf", claimed.claim.displayName)
        assertTrue(session.isClaimed)
        assertNull(registry.claim(session.token, Controller()))
        assertNull(registry.claim("unknown", viewer))
        assertNull(registry.claim(null, viewer))
    }

    @Test
    fun `the claimed viewer reports pages once per change and goes to pages within the document`() {
        val events = Recorder()
        val session = open(events = events)
        val viewer = Controller()
        // Nothing is reported before the claim. zh-CN: 认领之前不报告任何内容.
        session.update(0, 7, 1f)
        registry.claim(session.token, viewer)
        session.update(0, 7, 1f)
        session.update(0, 7, 2f)
        session.update(3, 7, 2f)
        session.visible(true)
        assertEquals(listOf("page 1/7", "page 4/7"), events.log)
        val state = session.state()
        assertEquals(4, state[PdfContract.KEY_PAGE])
        assertEquals(7, state[PdfContract.KEY_PAGE_COUNT])
        assertEquals(2f, state[PdfContract.KEY_ZOOM])
        assertEquals(true, state[PdfContract.KEY_VISIBLE])
        session.goTo(7)
        scheduler.onMain = false
        session.goTo(2)
        assertEquals(listOf("goTo 6", "goTo 1"), viewer.log)
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { session.goTo(8) }
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { session.goTo(0) }
    }

    @Test
    fun `close with finish tells the host and finishes the viewer, then every call fails`() {
        val events = Recorder()
        val session = open(events = events)
        val viewer = Controller()
        registry.claim(session.token, viewer)
        session.close(finish = true)
        assertEquals(listOf("closed finished", "release"), events.log)
        assertEquals(listOf("finish"), viewer.log)
        assertFalse(session.isOpen)
        assertEquals(0, registry.openCount())
        assertCode(PdfErrorCodes.SESSION_CLOSED) { session.state() }
        assertCode(PdfErrorCodes.SESSION_CLOSED) { session.goTo(1) }
        assertCode(PdfErrorCodes.SESSION_CLOSED) { session.goTo(0) }
        // Closing again does nothing; nothing is reported any more. zh-CN: 再次关闭不做任何事; 不再报告任何内容.
        session.close(finish = true)
        session.end(PdfContract.REASON_CLOSED)
        session.update(2, 7, 1f)
        assertEquals(listOf("closed finished", "release"), events.log)
        assertEquals(listOf("finish"), viewer.log)
    }

    @Test
    fun `close without finish only detaches the host`() {
        val events = Recorder()
        val session = open(events = events)
        val viewer = Controller()
        registry.claim(session.token, viewer)
        session.close(finish = false)
        assertEquals(listOf("release"), events.log)
        assertTrue(viewer.log.isEmpty())
        assertFalse(session.isOpen)
    }

    @Test
    fun `the viewer closing ends the session with closed, a dead host without an event`() {
        val closedEvents = Recorder()
        val closed = open(events = closedEvents)
        registry.claim(closed.token, Controller())
        closed.end(PdfContract.REASON_CLOSED)
        assertEquals(listOf("closed closed", "release"), closedEvents.log)
        val detachedEvents = Recorder()
        val detached = open(host = "another host", events = detachedEvents)
        detached.detach()
        assertEquals(listOf("release"), detachedEvents.log)
        assertFalse(detached.isOpen)
    }

    @Test
    fun `a later session of the same host replaces the earlier one`() {
        val firstEvents = Recorder()
        val first = open(events = firstEvents)
        val otherEvents = Recorder()
        val other = open(host = "another host", events = otherEvents)
        val second = open()
        assertEquals(listOf("closed replaced", "release"), firstEvents.log)
        assertFalse(first.isOpen)
        assertTrue(otherEvents.log.isEmpty())
        assertTrue(other.isOpen)
        assertTrue(second.isOpen)
        assertEquals(2, registry.openCount())
    }

    @Test
    fun `an unclaimed session times out and closes its source`() {
        val events = Recorder()
        val source = FakeDescriptor()
        val session = open(events = events, source = source)
        assertEquals(listOf(60_000L), scheduler.pendingDelays())
        scheduler.runDelayed()
        assertEquals(listOf("closed timeout", "release"), events.log)
        assertTrue(source.closed)
        assertNull(registry.claim(session.token, Controller()))
        assertEquals(0, registry.openCount())
    }

    @Test
    fun `a claim cancels the timeout and hands the source to the viewer`() {
        val events = Recorder()
        val source = FakeDescriptor()
        val session = open(events = events, source = source)
        registry.claim(session.token, Controller())
        assertTrue(scheduler.pendingDelays().isEmpty())
        scheduler.runDelayed()
        session.end(PdfContract.REASON_CLOSED)
        assertEquals(listOf("closed closed", "release"), events.log)
        assertFalse("the viewer owns the claimed source", source.closed)
    }

    @Test
    fun `options count the page from 1 and check the keyword`() {
        fun parse(vararg pairs: Pair<String, Any?>) = ViewerOptions.parse(PdfOptions(MapValues(mapOf(*pairs))))
        val defaults = parse()
        assertEquals(0, defaults.page)
        assertNull(defaults.keyword)
        assertNull(defaults.displayName)
        val full = parse(
            PdfContract.KEY_PAGE to 4,
            PdfContract.KEY_KEYWORD to "needle",
            PdfContract.KEY_DISPLAY_NAME to "  report.pdf ",
            PdfContract.KEY_PASSWORD to "secret",
        )
        assertEquals(3, full.page)
        assertEquals("needle", full.keyword)
        assertEquals("report.pdf", full.displayName)
        assertEquals("secret", full.password)
        assertNull(parse(PdfContract.KEY_DISPLAY_NAME to "   ").displayName)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { parse(PdfContract.KEY_PAGE to 0) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { parse(PdfContract.KEY_KEYWORD to "") }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { parse(PdfContract.KEY_KEYWORD to "   ") }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { parse(PdfContract.KEY_KEYWORD to "x".repeat(PdfContract.MAX_KEYWORD_LENGTH + 1)) }
        assertEquals(PdfContract.MAX_KEYWORD_LENGTH, parse(PdfContract.KEY_KEYWORD to "x".repeat(PdfContract.MAX_KEYWORD_LENGTH)).keyword?.length)
    }

    @Test
    fun `tokens are 64 random hex digits`() {
        val first = ViewerSessionRegistry.randomToken()
        val second = ViewerSessionRegistry.randomToken()
        assertTrue(first, Regex("[0-9a-f]{64}").matches(first))
        assertFalse(first == second)
    }

    /** Runs main and event work at once and keeps delayed work until [runDelayed]. zh-CN: 立即运行主线程与事件工作, 延迟工作保留到 [runDelayed]. */
    private class FakeScheduler : ViewerScheduler {

        private class Delayed(val millis: Long, val action: () -> Unit) {
            var cancelled = false
        }

        private val delayed = ArrayList<Delayed>()

        override var onMain = true

        override fun main(action: () -> Unit) = action()

        override fun event(action: () -> Unit) = action()

        override fun delayed(millis: Long, action: () -> Unit): () -> Unit {
            val entry = Delayed(millis, action)
            delayed += entry
            return { entry.cancelled = true }
        }

        fun pendingDelays(): List<Long> = delayed.filterNot { it.cancelled }.map { it.millis }

        fun runDelayed() {
            val due = delayed.filterNot { it.cancelled }
            delayed.clear()
            due.forEach { it.action() }
        }

    }

    private class Recorder : ViewerEvents {
        val log = ArrayList<String>()
        override fun page(token: String, page: Int, pageCount: Int) {
            log += "page $page/$pageCount"
        }
        override fun closed(token: String, reason: String) {
            log += "closed $reason"
        }
        override fun release() {
            log += "release"
        }
    }

    private class Controller : ViewerController {
        val log = ArrayList<String>()
        override fun goTo(page: Int) {
            log += "goTo $page"
        }
        override fun finish() {
            log += "finish"
        }
    }

    /** A descriptor that only records its close (the stubbed android.jar does nothing else). zh-CN: 只记录关闭的描述符 (桩 android.jar 不做别的). */
    private class FakeDescriptor : ParcelFileDescriptor(null as ParcelFileDescriptor?) {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val HOST = "host"
    }

}

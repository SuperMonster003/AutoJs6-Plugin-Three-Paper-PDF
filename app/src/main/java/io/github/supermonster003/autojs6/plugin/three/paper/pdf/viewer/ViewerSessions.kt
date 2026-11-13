package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What a viewer session hands the Activity that claims it (protocol "Viewer Sessions"): the source duplicated by the
 * plugin, the display name, the password and the keyword to search for. The session owns the source until it is
 * claimed, the viewer afterwards.
 * zh-CN: 查看器会话交给认领它的 Activity 的内容 (协议 "Viewer Sessions" 一节): 插件复制的源, 显示名称, 密码与要搜索的关键词.
 * 认领之前源归会话所有, 之后归查看器所有.
 */
internal class ViewerClaim(
    val source: ParcelFileDescriptor,
    val displayName: String?,
    val password: String?,
    val keyword: String?,
)

/**
 * The options of `openViewer` (protocol "Viewer Sessions"): `displayName`, `password`, the start `page` (counted from
 * 1, kept here from 0) and `keyword`. A start page past the end of the document opens its last page, since the page
 * count is known only once the viewer loaded the document (roadmap appendix D Q73).
 * zh-CN: `openViewer` 的选项 (协议 "Viewer Sessions" 一节): `displayName`, `password`, 起始页 `page` (从 1 计数, 此处保存为从 0
 * 起) 与 `keyword`. 起始页超出文档末尾时打开末页, 因为页数要等查看器加载文档后才知道 (路线图附录 D Q73).
 */
internal class ViewerOptions(val displayName: String?, val password: String?, val page: Int, val keyword: String?) {

    companion object {

        fun parse(options: PdfOptions): ViewerOptions {
            val page = options.int(PdfContract.KEY_PAGE) ?: 1
            if (page < 1) throw PdfFailure.invalid("${PdfContract.KEY_PAGE} $page is not a page counted from 1")
            val keyword = options.string(PdfContract.KEY_KEYWORD)?.also { keyword ->
                val range = PdfContract.MIN_KEYWORD_LENGTH..PdfContract.MAX_KEYWORD_LENGTH
                if (keyword.length !in range) {
                    throw PdfFailure.invalid("${PdfContract.KEY_KEYWORD} has ${keyword.length} characters, outside ${range.first}..${range.last}")
                }
                if (keyword.isBlank()) throw PdfFailure.invalid("${PdfContract.KEY_KEYWORD} is blank")
            }
            return ViewerOptions(
                displayName = options.string(PdfContract.KEY_DISPLAY_NAME)?.trim()?.takeIf { it.isNotEmpty() },
                password = options.password(),
                page = page - 1,
                keyword = keyword,
            )
        }

    }

}

/**
 * The host side of a session: `IPdfViewerCallback` behind an interface, so the session logic runs without Binder.
 * zh-CN: 会话的宿主一侧: 以接口隐藏 `IPdfViewerCallback`, 使会话逻辑无需 Binder 即可运行.
 */
internal interface ViewerEvents {

    /** The visible page changed ([page] counted from 1). zh-CN: 可见页改变 ([page] 从 1 计数). */
    fun page(token: String, page: Int, pageCount: Int)

    /** The session ended with one of the `REASON_*` names; nothing follows. zh-CN: 会话以某个 `REASON_*` 名称结束; 之后不再有事件. */
    fun closed(token: String, reason: String)

    /**
     * The session ended, with or without [closed]; the last call, made after it. zh-CN: 会话已结束, 无论是否发送了 [closed];
     * 最后一个调用, 在它之后发出.
     */
    fun release() {}

}

/**
 * The viewer that claimed a session; both calls arrive on the main thread.
 * zh-CN: 认领了会话的查看器; 两个调用都在主线程上到达.
 */
internal interface ViewerController {

    /** Moves the viewer to [page] (from 0). zh-CN: 把查看器移到第 [page] 页 (从 0 起). */
    fun goTo(page: Int)

    /** Finishes the viewer Activity (the host closed the session with `finish = true`). zh-CN: 结束查看器 Activity (宿主以 `finish = true` 关闭会话). */
    fun finish()

}

/**
 * Where session work runs: [main] for the viewer, [event] for host events (in order, off the main thread, so a slow host
 * never blocks the viewer), [delayed] for the claim timeout.
 * zh-CN: 会话工作的运行位置: [main] 用于查看器, [event] 用于宿主事件 (保持顺序, 不在主线程, 因此慢的宿主不会阻塞查看器),
 * [delayed] 用于认领超时.
 */
internal interface ViewerScheduler {

    val onMain: Boolean

    fun main(action: () -> Unit)

    fun event(action: () -> Unit)

    /** Runs [action] after [millis] unless the answered cancel runs first. zh-CN: [millis] 毫秒后运行 [action], 除非先调用了返回的取消函数. */
    fun delayed(millis: Long, action: () -> Unit): () -> Unit

    object Android : ViewerScheduler {

        private val handler = Handler(Looper.getMainLooper())

        private val events = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "3-paper-viewer-events").apply { isDaemon = true } }

        override val onMain: Boolean get() = Looper.myLooper() == Looper.getMainLooper()

        override fun main(action: () -> Unit) {
            handler.post(action)
        }

        override fun event(action: () -> Unit) = events.execute(action)

        override fun delayed(millis: Long, action: () -> Unit): () -> Unit {
            val runnable = Runnable(action)
            handler.postDelayed(runnable, millis)
            return { handler.removeCallbacks(runnable) }
        }

    }

}

/**
 * One viewer session (protocol "Viewer Sessions", roadmap D21, D23, P3.2). It is pending until the viewer Activity
 * claims it by its token, claimed while the viewer shows it, and ended once: by the host ([close]), by the plugin
 * ([end]: `closed`, `replaced`, `timeout`) or silently ([detach], the host died). An ended session refuses host calls
 * with `SESSION_CLOSED` and sends nothing more; `close(false)` ends it without `onClosed` and leaves the viewer open.
 * The state the host reads ([state]) is kept by the viewer on the main thread ([update], [visible]); before the viewer
 * loaded the document it answers the start page and a page count of 0 (roadmap appendix D Q73).
 * zh-CN: 一个查看器会话 (协议 "Viewer Sessions" 一节, 路线图 D21, D23, P3.2). 查看器 Activity 凭令牌认领之前处于待认领状态,
 * 查看器显示它期间处于已认领状态, 并且只结束一次: 由宿主结束 ([close]), 由插件结束 ([end]: `closed`, `replaced`, `timeout`),
 * 或静默结束 ([detach], 宿主已死亡). 已结束的会话以 `SESSION_CLOSED` 拒绝宿主调用且不再发送任何事件; `close(false)` 结束会话但不发送
 * `onClosed`, 查看器保持打开. 宿主读取的状态 ([state]) 由查看器在主线程上维护 ([update], [visible]); 查看器加载文档之前返回起始页与
 * 页数 0 (路线图附录 D Q73).
 */
internal class ViewerSession(
    val token: String,
    val host: Any,
    private val claim: ViewerClaim,
    startPage: Int,
    private val events: ViewerEvents?,
    private val scheduler: ViewerScheduler,
    private val onEnded: (ViewerSession) -> Unit,
) {

    private enum class Phase { PENDING, CLAIMED, ENDED }

    private val lock = Any()
    private var phase = Phase.PENDING
    private var controller: ViewerController? = null
    private var cancelTimeout: (() -> Unit)? = null
    private var sentPage = -1
    private var sentCount = -1

    /** The page the viewer opens at (from 0); `goTo` before the claim moves it. zh-CN: 查看器打开时的页 (从 0 起); 认领之前的 `goTo` 会改变它. */
    @Volatile
    var startPage: Int = startPage
        private set

    @Volatile
    private var page = startPage

    @Volatile
    private var pageCount = 0

    @Volatile
    private var zoom = 1f

    @Volatile
    private var resumed = false

    val isOpen: Boolean get() = synchronized(lock) { phase != Phase.ENDED }

    val isClaimed: Boolean get() = synchronized(lock) { phase == Phase.CLAIMED }

    // Host calls (Binder threads). zh-CN: 宿主调用 (Binder 线程).

    /** `getState` without the envelope. zh-CN: 不含信封的 `getState`. */
    fun state(): Map<String, Any?> {
        requireOpen("getState")
        return linkedMapOf(
            PdfContract.KEY_TOKEN to token,
            PdfContract.KEY_PAGE to page + 1,
            PdfContract.KEY_PAGE_COUNT to pageCount,
            PdfContract.KEY_ZOOM to zoom,
            PdfContract.KEY_VISIBLE to resumed,
        )
    }

    /**
     * Moves the viewer to [target] (counted from 1): `PAGE_OUT_OF_RANGE` below 1 or past a known page count. A claimed
     * session waits until the viewer moved (at most [GO_TO_WAIT_MILLIS]), so `getState` answers the new page; before the
     * page count is known the page is limited once it is (roadmap appendix D Q73).
     * zh-CN: 把查看器移到第 [target] 页 (从 1 计数): 小于 1 或超出已知页数时报 `PAGE_OUT_OF_RANGE`. 已认领的会话等待查看器移动完成
     * (至多 [GO_TO_WAIT_MILLIS]), 因此 `getState` 返回新页; 页数未知时, 该页在页数已知后再限位 (路线图附录 D Q73).
     */
    fun goTo(target: Int) {
        val known = pageCount
        if (target < 1 || (known > 0 && target > known)) {
            requireOpen("goTo")
            throw PdfFailure.pageOutOfRange("page $target is outside 1..${if (known > 0) known.toString() else "the document"}")
        }
        val index = target - 1
        val viewer = synchronized(lock) {
            when (phase) {
                Phase.ENDED -> throw closedFailure("goTo")
                Phase.PENDING -> {
                    startPage = index
                    page = index
                    null
                }
                Phase.CLAIMED -> controller
            }
        } ?: return
        if (scheduler.onMain) {
            if (isClaimed) viewer.goTo(index)
            return
        }
        val moved = CountDownLatch(1)
        scheduler.main {
            try {
                if (isClaimed) viewer.goTo(index)
            } finally {
                moved.countDown()
            }
        }
        moved.await(GO_TO_WAIT_MILLIS, TimeUnit.MILLISECONDS)
    }

    /**
     * Ends the session for the host. With [finish] the callback receives `finished` and the viewer Activity finishes;
     * without it the session only detaches and the viewer stays open (roadmap D23). Closing an ended session does
     * nothing.
     * zh-CN: 为宿主结束会话. [finish] 为 true 时回调收到 `finished` 且查看器 Activity 结束; 否则会话只是断开, 查看器保持打开 (路线图
     * D23). 关闭已结束的会话不做任何事.
     */
    fun close(finish: Boolean) {
        val viewer = finishPhase(if (finish) PdfContract.REASON_FINISHED else null) ?: return
        if (finish) viewer.controller?.let { scheduler.main { it.finish() } }
    }

    // Plugin side. zh-CN: 插件一侧.

    /**
     * Ends the session with [reason] (`closed`, `replaced` or `timeout`) and tells the host; nothing when already ended.
     * zh-CN: 以 [reason] (`closed`, `replaced` 或 `timeout`) 结束会话并通知宿主; 已结束时不做任何事.
     */
    fun end(reason: String) {
        finishPhase(reason)
    }

    /** Ends the session without an event (the host is gone). zh-CN: 结束会话但不发送事件 (宿主已不在). */
    fun detach() {
        finishPhase(null)
    }

    /**
     * The viewer reports [page] (from 0) of [pageCount] at [zoom] on the main thread; a changed page or count reaches the
     * host as `onPage`.
     * zh-CN: 查看器在主线程上报告当前为第 [page] 页 (从 0 起), 共 [pageCount] 页, 缩放 [zoom]; 页或页数改变时以 `onPage` 通知宿主.
     */
    fun update(page: Int, pageCount: Int, zoom: Float) {
        this.zoom = zoom
        val send = synchronized(lock) {
            if (phase != Phase.CLAIMED) return
            this.page = page
            this.pageCount = pageCount
            if (page == sentPage && pageCount == sentCount) return
            sentPage = page
            sentCount = pageCount
            true
        }
        if (send) events?.let { target -> scheduler.event { runCatching { target.page(token, page + 1, pageCount) } } }
    }

    /** Whether the viewer Activity is resumed. zh-CN: 查看器 Activity 是否处于 resumed 状态. */
    fun visible(resumed: Boolean) {
        this.resumed = resumed
    }

    internal fun startTimeout(millis: Long) {
        val cancel = scheduler.delayed(millis) { expire() }
        synchronized(lock) {
            if (phase == Phase.PENDING) cancelTimeout = cancel else cancel()
        }
    }

    /** Claims the pending session for [viewer]; null when it is not pending. zh-CN: 为 [viewer] 认领待认领的会话; 不是待认领状态时为 null. */
    internal fun claimFor(viewer: ViewerController): ViewerClaim? = synchronized(lock) {
        if (phase != Phase.PENDING) return null
        phase = Phase.CLAIMED
        controller = viewer
        cancelTimeout?.invoke()
        cancelTimeout = null
        claim
    }

    private fun expire() {
        val pending = synchronized(lock) { phase == Phase.PENDING }
        if (pending) end(PdfContract.REASON_TIMEOUT)
    }

    /**
     * Moves to ENDED once and sends [reason] when it is not null; a pending session closes its source. Answers this
     * session (with its controller still readable) when this call ended it, null when it had ended before.
     * zh-CN: 只进入一次 ENDED 状态, [reason] 不为 null 时发送它; 待认领的会话关闭其源. 本次调用结束了会话时返回本会话 (仍可读取其
     * controller), 此前已结束时返回 null.
     */
    private fun finishPhase(reason: String?): Ended? {
        val ended = synchronized(lock) {
            if (phase == Phase.ENDED) return null
            val wasPending = phase == Phase.PENDING
            phase = Phase.ENDED
            cancelTimeout?.invoke()
            cancelTimeout = null
            Ended(controller, wasPending)
        }
        if (ended.wasPending) runCatching { claim.source.close() }
        onEnded(this)
        events?.let { target ->
            scheduler.event {
                if (reason != null) runCatching { target.closed(token, reason) }
                runCatching { target.release() }
            }
        }
        return ended
    }

    private class Ended(val controller: ViewerController?, val wasPending: Boolean)

    private fun requireOpen(call: String) {
        if (!isOpen) throw closedFailure(call)
    }

    private fun closedFailure(call: String) = PdfFailure(PdfErrorCodes.SESSION_CLOSED, "$call: the viewer session is closed")

    companion object {
        const val GO_TO_WAIT_MILLIS = 2_000L
    }

}

/**
 * The viewer sessions of the plugin process, keyed by their random token (protocol "Viewer Sessions"). [open] registers
 * a session for a host and ends the earlier open session of the same host with `replaced`; an unclaimed session ends
 * with `timeout` after `VIEWER_CLAIM_TIMEOUT_MS`. The viewer Activity claims a session once by the token in its intent
 * ([claim]); an unknown, claimed or ended token answers null, which the Activity shows as an expired request.
 * zh-CN: 插件进程中的查看器会话, 以随机令牌为键 (协议 "Viewer Sessions" 一节). [open] 为宿主登记会话, 并以 `replaced` 结束同一宿主
 * 此前仍打开的会话; 未被认领的会话在 `VIEWER_CLAIM_TIMEOUT_MS` 后以 `timeout` 结束. 查看器 Activity 用 intent 中的令牌认领一次
 * ([claim]); 未知, 已认领或已结束的令牌返回 null, Activity 将其显示为请求已过期.
 */
internal open class ViewerSessionRegistry(
    private val scheduler: ViewerScheduler,
    private val claimTimeoutMillis: Long = PdfContract.VIEWER_CLAIM_TIMEOUT_MS,
    private val tokens: () -> String = ::randomToken,
) {

    private val sessions = LinkedHashMap<String, ViewerSession>()

    fun open(host: Any, claim: ViewerClaim, startPage: Int, events: ViewerEvents?): ViewerSession {
        val session = ViewerSession(tokens(), host, claim, startPage, events, scheduler) { ended -> remove(ended) }
        val replaced = synchronized(sessions) {
            val earlier = sessions.values.filter { it.host == host }
            sessions[session.token] = session
            earlier
        }
        replaced.forEach { it.end(PdfContract.REASON_REPLACED) }
        session.startTimeout(claimTimeoutMillis)
        return session
    }

    /**
     * The pending session of [token] with its claim data, now claimed by [viewer]; null otherwise.
     * zh-CN: [token] 的待认领会话及其认领数据, 现由 [viewer] 认领; 否则为 null.
     */
    fun claim(token: String?, viewer: ViewerController): Claimed? {
        token ?: return null
        val session = synchronized(sessions) { sessions[token] } ?: return null
        return session.claimFor(viewer)?.let { Claimed(session, it) }
    }

    class Claimed(val session: ViewerSession, val claim: ViewerClaim)

    /**
     * Ends the sessions of [host] without an event, once the engine found its process gone (roadmap D44); a viewer
     * on screen keeps showing its document.
     * zh-CN: 引擎发现 [host] 的进程已不存在后, 结束其会话且不发送事件 (路线图 D44); 屏幕上的查看器继续显示其文档.
     */
    fun detachHost(host: Any) {
        val ended = synchronized(sessions) { sessions.values.filter { it.host == host } }
        ended.forEach { it.detach() }
    }

    /** Open sessions (tests and diagnostics). zh-CN: 打开中的会话 (测试与诊断). */
    fun openCount(): Int = synchronized(sessions) { sessions.size }

    private fun remove(session: ViewerSession) {
        synchronized(sessions) {
            if (sessions[session.token] === session) sessions.remove(session.token)
        }
    }

    companion object {

        private val random = SecureRandom()

        /** 32 random bytes as 64 hex digits. zh-CN: 32 个随机字节, 写成 64 位十六进制. */
        fun randomToken(): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

    }

}

/** The registry of the plugin process. zh-CN: 插件进程的登记表. */
internal object ViewerSessions : ViewerSessionRegistry(ViewerScheduler.Android)

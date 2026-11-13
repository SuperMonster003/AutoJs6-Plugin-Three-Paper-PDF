package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfAnswers
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfBundles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerEvents
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerSession
import org.autojs.plugin.pdf.api.IPdfViewerCallback
import org.autojs.plugin.pdf.api.IPdfViewerSession
import org.autojs.plugin.pdf.api.PdfContract

/**
 * The `IPdfViewerSession` handle of one viewer session (protocol "Viewer Sessions", roadmap P3.2): `getState` answers the
 * envelope with the negotiated version (`SESSION_CLOSED` as `errorCode` once the session ended), `goTo` and `close`
 * throw across Binder with `CODE: detail`.
 * zh-CN: 一个查看器会话的 `IPdfViewerSession` 句柄 (协议 "Viewer Sessions" 一节, 路线图 P3.2): `getState` 以协商版本返回应答信封
 * (会话结束后以 `errorCode` 返回 `SESSION_CLOSED`), `goTo` 与 `close` 以 `CODE: detail` 跨 Binder 抛出.
 */
internal class ViewerSessionStub(private val session: ViewerSession, private val version: Int) : IPdfViewerSession.Stub() {

    override fun getState(): Bundle {
        val answer = try {
            PdfAnswers.success(version, session.state())
        } catch (e: Throwable) {
            PdfAnswers.failure(version, PdfAnswers.failureOf(e))
        }
        return PdfBundles.toBundle(answer)
    }

    override fun goTo(page: Int) = binderCall { session.goTo(page) }

    override fun close(finish: Boolean) = binderCall { session.close(finish) }

    private inline fun binderCall(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            throw PdfAnswers.failureOf(e).toBinderException()
        }
    }

}

/**
 * The host callback of one session as [ViewerEvents]: `onPage` bundles carry the negotiated version, a dead host
 * ends the session silently ([watch]), and the death link goes with the session ([release]). Calls run on the event
 * thread of the session; a failed call is dropped (the callback is oneway, the host may be gone).
 * zh-CN: 以 [ViewerEvents] 表示的一个会话的宿主回调: `onPage` 的 Bundle 带协商版本, 宿主死亡时静默结束会话 ([watch]), 死亡链接随
 * 会话一起解除 ([release]). 调用在会话的事件线程上进行; 失败的调用被丢弃 (回调是 oneway, 宿主可能已不在).
 */
internal class ViewerCallbackEvents(private val callback: IPdfViewerCallback, private val version: Int) : ViewerEvents {

    @Volatile
    private var recipient: IBinder.DeathRecipient? = null

    override fun page(token: String, page: Int, pageCount: Int) {
        val event = PdfBundles.toBundle(
            linkedMapOf(
                PdfContract.KEY_CONTRACT_VERSION to version,
                PdfContract.KEY_PAGE to page,
                PdfContract.KEY_PAGE_COUNT to pageCount,
            ),
        )
        callback.onPage(token, event)
    }

    override fun closed(token: String, reason: String) = callback.onClosed(token, reason)

    override fun release() {
        recipient?.let { runCatching { callback.asBinder().unlinkToDeath(it, 0) } }
        recipient = null
    }

    /**
     * Ends [session] without an event when the host process dies; at once when it is already dead.
     * zh-CN: 宿主进程死亡时结束 [session] 且不发送事件; 已经死亡时立即结束.
     */
    fun watch(session: ViewerSession) {
        val death = IBinder.DeathRecipient { session.detach() }
        try {
            callback.asBinder().linkToDeath(death, 0)
            recipient = death
            // The session may have ended before the link, so its release found nothing to unlink.
            // zh-CN: 会话可能在链接之前已结束, 那时的 release 没有可解除的链接.
            if (!session.isOpen) release()
        } catch (_: RemoteException) {
            session.detach()
        }
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes

/**
 * A cooperative stop request checked inside PDFBox loops (per page, per glyph): a closed document ends its call with
 * `SESSION_CLOSED`, a cancelled operation with `CANCELLED` (protocol "Methods", "Progress, Completion And Cancellation").
 * Nothing interrupts threads, so a shared descriptor is never closed by an interrupted channel.
 * zh-CN: 在 PDFBox 循环 (逐页, 逐字形) 中检查的协作式停止请求: 文档关闭时调用以 `SESSION_CLOSED` 结束, 操作取消时以 `CANCELLED`
 * 结束 (协议 "Methods" 与 "Progress, Completion And Cancellation" 两节). 不中断线程, 因此共享描述符不会被中断的通道关闭.
 */
fun interface StopSignal {

    /** The failure to end with, or null to go on. zh-CN: 应以之结束的失败, 继续时为 null. */
    fun stopReason(): PdfFailure?

    /** Throws the stop reason, if any. zh-CN: 若有停止原因则抛出. */
    fun checkStop() {
        stopReason()?.let { throw it }
    }

    companion object {
        val NEVER = StopSignal { null }
        fun closed() = PdfFailure(PdfErrorCodes.SESSION_CLOSED, "the document was closed")
        fun cancelled() = PdfFailure(PdfErrorCodes.CANCELLED, "the operation was cancelled")
    }

}

/**
 * Limits `onProgress` to one per [intervalMillis] (protocol: at most one per `PROGRESS_THROTTLE_MS`); the first report
 * passes at once.
 * zh-CN: 把 `onProgress` 限制为每 [intervalMillis] 至多一次 (协议: 每 `PROGRESS_THROTTLE_MS` 至多一次); 首次报告立即放行.
 */
class ProgressThrottle(
    private val intervalMillis: Long = PdfContract.PROGRESS_THROTTLE_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private var last = Long.MIN_VALUE

    /** Whether a report may be sent now; records it when it may. zh-CN: 现在是否可以发送报告; 可以时记下时间. */
    @Synchronized
    fun tryPass(): Boolean {
        val now = clock()
        if (last != Long.MIN_VALUE && now - last < intervalMillis) return false
        last = now
        return true
    }

}

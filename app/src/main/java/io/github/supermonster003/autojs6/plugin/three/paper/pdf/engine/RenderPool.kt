package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Limits renders across documents to [permits] at a time (capability `pdfRenderConcurrency`, roadmap P2.1); the rest
 * wait in arrival order and leave the queue when their [StopSignal] fires (document closed, operation cancelled).
 * zh-CN: 把跨文档的渲染限制为同时 [permits] 个 (能力 `pdfRenderConcurrency`, 路线图 P2.1); 其余按到达顺序等待, 其 [StopSignal]
 * 触发 (文档关闭, 操作取消) 时离开队列.
 */
class RenderPool(val permits: Int = ThreePaperPdfContract.RENDER_CONCURRENCY) {

    private val semaphore = Semaphore(permits, true)

    fun <T> run(stop: StopSignal, block: () -> T): T {
        while (!semaphore.tryAcquire(POLL_MILLIS, TimeUnit.MILLISECONDS)) stop.checkStop()
        try {
            stop.checkStop()
            return block()
        } finally {
            semaphore.release()
        }
    }

    private companion object {
        const val POLL_MILLIS = 50L
    }

}

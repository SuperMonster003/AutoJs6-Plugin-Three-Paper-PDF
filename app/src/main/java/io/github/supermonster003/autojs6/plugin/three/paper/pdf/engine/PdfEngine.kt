package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The engine of one service instance (roadmap P2.1): the documents, the asynchronous operations, the render pool and a
 * sweeper that releases the parsed state of idle documents. Temp files live under `<cacheDir>/pdf/`; what a previous
 * process left there is removed at start.
 * zh-CN: 一个服务实例的引擎 (路线图 P2.1): 文档, 异步操作, 渲染池, 以及释放空闲文档解析状态的清扫器. 临时文件位于
 * `<cacheDir>/pdf/` 之下; 启动时删除上一个进程遗留的内容.
 */
class PdfEngine(cacheDir: File) {

    val root = File(cacheDir, ROOT_NAME)

    val documents = DocumentRegistry(root)

    private val executor = Executors.newCachedThreadPool(named("paper-op"))

    val operations = Operations(executor, onHostDied = { documents.closeHost(it) })

    val renderPool = RenderPool()

    private val sweeper: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(named("paper-sweep"))

    init {
        documents.wipe()
        sweeper.scheduleWithFixedDelay(
            { runCatching { documents.sweep() } },
            DocumentRegistry.SWEEP_PERIOD_MS,
            DocumentRegistry.SWEEP_PERIOD_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Cancels every operation and closes every document, for example when the last host unbinds.
     * zh-CN: 取消全部操作并关闭全部文档, 例如最后一个宿主解绑时.
     */
    fun closeAll() {
        operations.cancelAll()
        documents.closeAll()
    }

    /** Closes everything and stops the threads. zh-CN: 关闭全部内容并停止线程. */
    fun shutdown() {
        closeAll()
        sweeper.shutdownNow()
        // Running operations end at their next stop check; nothing interrupts them. zh-CN: 运行中的操作在下一次停止检查时结束; 不中断它们.
        executor.shutdown()
    }

    companion object {

        const val ROOT_NAME = "pdf"

        private fun named(prefix: String): ThreadFactory {
            val count = AtomicInteger()
            return ThreadFactory { runnable -> Thread(runnable, "$prefix-${count.incrementAndGet()}").apply { isDaemon = true } }
        }

    }

}

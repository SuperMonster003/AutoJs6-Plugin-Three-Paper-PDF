package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The engine behind the services (roadmap P2.1), one per process and shared by the signed and the packaged entry
 * (D44): the documents, the asynchronous operations, the render pool and a sweeper that releases the parsed state of
 * idle documents and reaps dead callers ([reapDeadHosts]). Temp files live under `<cacheDir>/pdf/`; what a previous
 * process left there is removed at start.
 * zh-CN: 服务背后的引擎 (路线图 P2.1), 每个进程一个, 由签名入口与打包入口共用 (D44): 文档, 异步操作, 渲染池, 以及释放空闲文档
 * 解析状态并回收已死调用方 ([reapDeadHosts]) 的清扫器. 临时文件位于 `<cacheDir>/pdf/` 之下; 启动时删除上一个进程遗留的内容.
 *
 * @param onHostDead called for every caller [reapDeadHosts] found dead, after its operations were cancelled and its
 * documents closed (the services detach its viewer sessions there). zh-CN: [reapDeadHosts] 判为死亡的每个调用方在其操作取消,
 * 文档关闭之后调用 (服务在此断开其查看器会话).
 * @param isAlive whether the process of a caller still exists ([HostLiveness.isAlive]). zh-CN: 调用方进程是否仍存在.
 */
class PdfEngine(
    cacheDir: File,
    private val onHostDead: (HostKey) -> Unit = {},
    private val isAlive: (HostKey) -> Boolean = HostLiveness::isAlive,
) {

    val root = File(cacheDir, ROOT_NAME)

    val documents = DocumentRegistry(root)

    private val executor = Executors.newCachedThreadPool(named("paper-op"))

    val operations = Operations(executor, onHostDied = { documents.closeHost(it) })

    val renderPool = RenderPool()

    private val sweeper: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(named("paper-sweep"))

    init {
        documents.wipe()
        sweeper.scheduleWithFixedDelay(
            {
                runCatching { documents.sweep() }
                runCatching { reapDeadHosts() }
            },
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

    /**
     * Cancels the operations and closes the documents of the callers of one service entry ([packaged]), when that
     * service loses its last binding; the other entry keeps its callers (roadmap D44).
     * zh-CN: 取消某一服务入口 ([packaged]) 的调用方的操作并关闭其文档, 用于该服务失去最后一个绑定时; 另一入口的调用方不受影响
     * (路线图 D44).
     */
    fun closeEntry(packaged: Boolean) {
        operations.cancelHosts { it.packaged == packaged }
        documents.closeHosts { it.packaged == packaged }
    }

    /**
     * Finds the callers holding documents or operations whose process is gone ([isAlive]), cancels their operations,
     * closes their documents (their temp directories go with them) and calls [onHostDead]; answers them. The sweeper
     * runs it every `SWEEP_PERIOD_MS`, besides the death of an operation callback (roadmap D44).
     * zh-CN: 找出持有文档或操作但进程已不存在 ([isAlive]) 的调用方, 取消其操作, 关闭其文档 (临时目录随之删除) 并调用 [onHostDead];
     * 返回这些调用方. 除操作回调死亡外, 清扫器每 `SWEEP_PERIOD_MS` 运行一次 (路线图 D44).
     */
    fun reapDeadHosts(): List<HostKey> {
        val dead = (documents.hosts() + operations.hosts()).filterNot(isAlive)
        dead.forEach { host ->
            operations.cancelHost(host)
            documents.closeHost(host)
            runCatching { onHostDead(host) }
        }
        return dead
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

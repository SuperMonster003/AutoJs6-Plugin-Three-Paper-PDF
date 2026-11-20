package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfAnswers
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfBundles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarnings
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Asynchronous operations (`run`, `split`, `save`; protocol "Progress, Completion And Cancellation", roadmap P2.1):
 * ids greater than 0 unique in the process, duplicated outputs flushed and closed before `onComplete`, truncated to
 * zero bytes and closed before `onError` (the host then deletes them), progress at most once per
 * `PROGRESS_THROTTLE_MS`, no default timeout (appendix D Q7). A cancelled operation ends with `CANCELLED` at its next
 * [StopSignal] check; a dead callback cancels every operation of its host and calls [onHostDied].
 * zh-CN: 异步操作 (`run`, `split`, `save`; 协议 "Progress, Completion And Cancellation" 一节, 路线图 P2.1): id 大于 0 且在进程内
 * 唯一, 复制的输出在 `onComplete` 之前刷新并关闭, 在 `onError` 之前截断为 0 字节并关闭 (随后由宿主删除), 进度每
 * `PROGRESS_THROTTLE_MS` 至多一次, 默认无超时 (附录 D Q7). 被取消的操作在下一次 [StopSignal] 检查时以 `CANCELLED` 结束; 回调
 * 死亡时取消该宿主的全部操作并调用 [onHostDied].
 */
class Operations(
    private val executor: ExecutorService,
    private val onHostDied: (HostKey) -> Unit = {},
    private val throttleFactory: () -> ProgressThrottle = { ProgressThrottle() },
) {

    private val running = ConcurrentHashMap<Int, Operation>()
    private val lastId = AtomicInteger()

    /**
     * Context of one running operation. zh-CN: 一个运行中操作的上下文.
     */
    inner class Operation internal constructor(
        val id: Int,
        val host: HostKey,
        val contractVersion: Int,
        val op: String,
        /** The document of a `split` / `save`, closed together with it; null for `run`. zh-CN: `split` / `save` 所属文档, 随其关闭; `run` 为 null. */
        val documentId: Int?,
        /** Duplicated outputs owned by the operation. zh-CN: 操作持有的输出副本. */
        val outputs: List<ParcelFileDescriptor>,
        private val callback: IPdfProgressCallback,
        private val extraStop: StopSignal,
    ) : IBinder.DeathRecipient {

        private val cancelled = AtomicBoolean(false)
        private val ended = AtomicBoolean(false)
        private val throttle = throttleFactory()

        val stop = StopSignal { if (cancelled.get()) StopSignal.cancelled() else extraStop.stopReason() }

        val isRunning: Boolean get() = !ended.get()

        /**
         * Reports progress; dropped inside the throttle interval. [current] of [total] gives the percentage.
         * zh-CN: 报告进度; 节流间隔内的报告被丢弃. 百分比由 [current] / [total] 得出.
         */
        fun progress(stage: String, current: Int, total: Int) {
            require(stage in PdfContract.STAGES) { "unknown stage $stage" }
            if (ended.get() || !throttle.tryPass()) return
            val percent = if (total <= 0) 0 else (current.toLong() * 100 / total).toInt().coerceIn(0, 100)
            val bundle = PdfBundles.toBundle(
                PdfAnswers.success(
                    contractVersion,
                    mapOf(
                        PdfContract.KEY_OP to op,
                        PdfContract.KEY_PERCENT to percent,
                        PdfContract.KEY_STAGE to stage,
                        PdfContract.KEY_CURRENT to current,
                        PdfContract.KEY_TOTAL to total,
                    ),
                ),
            )
            ignoringDeadCallback { callback.onProgress(id, bundle) }
        }

        internal fun cancel(): Boolean {
            if (ended.get()) return false
            cancelled.set(true)
            return !ended.get()
        }

        internal fun start(work: (Operation) -> Map<String, Any?>) {
            val result = try {
                // Warnings become the English texts, plus their codes and arguments for version 2 (Q264 b); a malformed
                // warning fails the operation instead of the completion.
                // zh-CN: 警告展开为英文原文, 版本 2 另加代码与参数 (Q264 b); 格式错误的警告让操作失败, 而不是让完成包出错.
                val answer = PdfWarnings.expand(work(this), contractVersion)
                stop.checkStop()
                outputs.forEach { flush(it) }
                Result.success(answer)
            } catch (e: Throwable) {
                Result.failure(e)
            }
            finish(result)
        }

        private fun finish(result: Result<Map<String, Any?>>) {
            if (!ended.compareAndSet(false, true)) return
            running.remove(id)
            runCatching { callback.asBinder().unlinkToDeath(this, 0) }
            val bundle = result.fold(
                onSuccess = { answer ->
                    outputs.forEach { runCatching { it.close() } }
                    // Q304: above MAX_EVENT_BYTES the arguments of the warnings go first, then their codes.
                    // zh-CN: Q304: 超过 MAX_EVENT_BYTES 时先去掉警告参数, 再去掉警告代码.
                    val envelope = PdfAnswers.success(contractVersion, answer)
                    PdfBundles.toBundle(PdfWarnings.fit(envelope) { PdfBundles.parcelSize(PdfBundles.toBundle(it)) })
                },
                onFailure = { error ->
                    // A partly written file is never left behind: the host deletes the outputs after onError.
                    // zh-CN: 不留下写了一半的文件: 宿主在 onError 之后删除输出.
                    outputs.forEach { truncate(it); runCatching { it.close() } }
                    val failure = if (cancelled.get()) StopSignal.cancelled() else PdfAnswers.failureOf(error)
                    PdfBundles.toBundle(PdfAnswers.failure(contractVersion, failure))
                },
            )
            if (result.isSuccess) ignoringDeadCallback { callback.onComplete(id, bundle) } else ignoringDeadCallback { callback.onError(id, bundle) }
        }

        override fun binderDied() {
            cancelHost(host)
            onHostDied(host)
        }

    }

    /**
     * Starts [work] on the executor and answers the operation id; [outputs] are duplicated first, so a failure there
     * throws instead of returning an id (protocol: a request refused up front throws).
     * zh-CN: 在执行器上启动 [work] 并返回操作 id; 先复制 [outputs], 因此此处的失败直接抛出而不返回 id (协议: 预先拒绝的请求直接抛出).
     */
    fun start(
        host: HostKey,
        contractVersion: Int,
        op: String,
        outputs: List<ParcelFileDescriptor>,
        callback: IPdfProgressCallback,
        documentId: Int? = null,
        extraStop: StopSignal = StopSignal.NEVER,
        work: (Operation) -> Map<String, Any?>,
    ): Int {
        val copies = ArrayList<ParcelFileDescriptor>(outputs.size)
        try {
            outputs.forEach { copies += it.dup() }
        } catch (e: Exception) {
            copies.forEach { runCatching { it.close() } }
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot duplicate the outputs: ${e.message}")
        }
        val operation = Operation(nextId(), host, contractVersion, op, documentId, copies, callback, extraStop)
        try {
            callback.asBinder().linkToDeath(operation, 0)
        } catch (e: RemoteException) {
            copies.forEach { runCatching { it.close() } }
            throw PdfFailure(PdfErrorCodes.SESSION_CLOSED, "the progress callback is already dead")
        }
        running[operation.id] = operation
        try {
            executor.execute { operation.start(work) }
        } catch (e: Exception) {
            running.remove(operation.id)
            runCatching { callback.asBinder().unlinkToDeath(operation, 0) }
            copies.forEach { runCatching { it.close() } }
            throw PdfFailure(PdfErrorCodes.INTERNAL, "cannot schedule the operation: ${e.message}")
        }
        return operation.id
    }

    /** `IPdfPlugin.cancel`: true when the operation was still running. zh-CN: `IPdfPlugin.cancel`: 操作仍在运行时返回 true. */
    fun cancel(opId: Int): Boolean = running[opId]?.cancel() ?: false

    /**
     * `IPdfPlugin.cancel` for the caller [callerUid]: an operation another uid started is refused with
     * `PERMISSION_DENIED` (roadmap D44); an unknown or ended id answers false.
     * zh-CN: 调用方 [callerUid] 的 `IPdfPlugin.cancel`: 其他 uid 启动的操作以 `PERMISSION_DENIED` 拒绝 (路线图 D44); 未知或已结束的
     * id 返回 false.
     */
    fun cancel(opId: Int, callerUid: Int): Boolean {
        val operation = running[opId] ?: return false
        if (operation.host.uid != callerUid) {
            throw PdfFailure(PdfErrorCodes.PERMISSION_DENIED, "operation $opId belongs to another caller")
        }
        return operation.cancel()
    }

    fun cancelHost(host: HostKey) = running.values.filter { it.host == host }.forEach { it.cancel() }

    /** Cancels the operations of the hosts matching [filter]. zh-CN: 取消符合 [filter] 的宿主的操作. */
    fun cancelHosts(filter: (HostKey) -> Boolean) = running.values.filter { filter(it.host) }.forEach { it.cancel() }

    /** The hosts with running operations. zh-CN: 有运行中操作的宿主. */
    fun hosts(): Set<HostKey> = running.values.mapTo(LinkedHashSet()) { it.host }

    fun cancelDocument(documentId: Int) = running.values.filter { it.documentId == documentId }.forEach { it.cancel() }

    fun cancelAll() = running.values.forEach { it.cancel() }

    fun count(): Int = running.size

    private fun nextId(): Int {
        while (true) {
            val current = lastId.get()
            val next = if (current == Int.MAX_VALUE) 1 else current + 1
            if (lastId.compareAndSet(current, next) && !running.containsKey(next)) return next
        }
    }

    internal companion object {

        /** fsync of a written output; refusals are ignored. zh-CN: 对已写入的输出执行 fsync; 忽略拒绝. */
        fun flush(descriptor: ParcelFileDescriptor) {
            try {
                Os.fsync(descriptor.fileDescriptor)
            } catch (e: ErrnoException) {
                // fsync is refused by some pipes and FUSE files; the data is written already. zh-CN: 部分管道与 FUSE 文件拒绝
                // fsync; 数据已写入.
            }
        }

        /** Empties an output after a failure, best effort. zh-CN: 失败后尽力清空输出. */
        fun truncate(descriptor: ParcelFileDescriptor) {
            runCatching { Os.ftruncate(descriptor.fileDescriptor, 0) }
        }

        inline fun ignoringDeadCallback(block: () -> Unit) {
            try {
                block()
            } catch (e: RemoteException) {
                // The host is gone; binderDied cleans up. zh-CN: 宿主已不在; 由 binderDied 清理.
            } catch (e: RuntimeException) {
                // A local callback (tests) threw; the operation is over either way. zh-CN: 本地回调 (测试) 抛出异常; 操作无论如何已结束.
            }
        }

    }

}

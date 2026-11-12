package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import android.os.Binder
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * The caller of a Binder call: the host process whose documents and operations the plugin counts and cleans up
 * together (protocol "Ceilings": `MAX_DOCUMENTS_PER_HOST` per host process). Its temp directory is
 * `cache/pdf/<uid>-<pid>/` (roadmap P2.1 "token").
 * zh-CN: Binder 调用方: 插件按宿主进程统计与一并清理其文档与操作 (协议 "Ceilings": 每个宿主进程 `MAX_DOCUMENTS_PER_HOST`).
 * 其临时目录为 `cache/pdf/<uid>-<pid>/` (路线图 P2.1 所说的 "token").
 */
data class HostKey(val uid: Int, val pid: Int) {

    val directoryName: String get() = "$uid-$pid"

    companion object {
        fun calling() = HostKey(Binder.getCallingUid(), Binder.getCallingPid())
    }

}

/**
 * One open document (protocol "Methods"): owns a duplicate of the source, the password it was opened with and a temp
 * directory. Calls run one at a time ([call]); [close] ends a call in flight with `SESSION_CLOSED` through [StopSignal]
 * and the resources are released by whoever holds the lock last, so a descriptor is never closed under a running read.
 *
 * Parsed state (PDFBox objects, later the renderer and its decrypted copy) lives in [resources] and may be released
 * while the document stays open ([releaseIdle], appendix D Q10 (a)); the next call parses the source again.
 *
 * zh-CN: 一个已打开的文档 (协议 "Methods" 一节): 持有源的副本, 打开时用的密码与一个临时目录. 调用逐个执行 ([call]); [close] 通过
 * [StopSignal] 使在途调用以 `SESSION_CLOSED` 结束, 资源由最后持锁者释放, 因此描述符不会在读取进行中被关闭. 解析状态 (PDFBox
 * 对象, 以及后续的渲染器与其解密副本) 保存在 [resources] 中, 可在文档保持打开时释放 ([releaseIdle], 附录 D Q10 (a)); 下一次调用
 * 重新解析源.
 */
class PdfSession internal constructor(
    val id: Int,
    val host: HostKey,
    val contractVersion: Int,
    val displayName: String?,
    private val descriptor: ParcelFileDescriptor,
    val source: PdfSource,
    val password: String?,
    val directory: File,
    private val loader: (PdfSource, String?, File) -> LoadedPdf,
    private val onClosed: (PdfSession) -> Unit,
    private val onReleased: (PdfSession) -> Unit,
    private val clock: () -> Long,
) {

    private val lock = ReentrantLock(true)
    private val closed = AtomicBoolean(false)
    private var released = false
    private val resources = LinkedHashMap<String, AutoCloseable>()

    @Volatile
    var lastUsed: Long = clock()
        private set

    val isClosed: Boolean get() = closed.get()

    val sizeBytes: Long get() = source.length

    /** Ends calls in flight with `SESSION_CLOSED`. zh-CN: 使在途调用以 `SESSION_CLOSED` 结束. */
    val stopSignal = StopSignal { if (closed.get()) StopSignal.closed() else null }

    /** Holder of the lock inside [call]. zh-CN: [call] 内的持锁者. */
    inner class Call internal constructor() {

        val session: PdfSession get() = this@PdfSession

        val stop: StopSignal get() = stopSignal

        /** The parsed document, parsing the source again after an idle release. zh-CN: 已解析的文档; 空闲释放后重新解析源. */
        val pdf: PDDocument get() = resource(RESOURCE_PDF) { loader(source, password, directory) }.document

        /**
         * A resource that lives until the document closes or idles ([releaseIdle]); [create] runs once.
         * zh-CN: 存活到文档关闭或空闲 ([releaseIdle]) 的资源; [create] 只执行一次.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T : AutoCloseable> resource(key: String, create: () -> T): T {
            check(lock.isHeldByCurrentThread)
            stop.checkStop()
            return resources.getOrPut(key, create) as T
        }

    }

    /**
     * Runs [block] holding the document lock; a call that finds or leaves the document closed ends with
     * `SESSION_CLOSED` (protocol "Methods").
     * zh-CN: 持有文档锁执行 [block]; 发现文档已关闭或执行中被关闭的调用以 `SESSION_CLOSED` 结束 (协议 "Methods" 一节).
     */
    fun <T> call(block: (Call) -> T): T {
        stopSignal.checkStop()
        lock.lock()
        try {
            stopSignal.checkStop()
            lastUsed = clock()
            return try {
                block(Call())
            } catch (e: Throwable) {
                if (closed.get() && e !is Error) throw StopSignal.closed()
                throw e
            }
        } finally {
            lastUsed = clock()
            if (closed.get()) releaseLocked()
            lock.unlock()
        }
    }

    /**
     * Closes the document; idempotent. Answers true for the call that closed it.
     * zh-CN: 关闭文档; 幂等. 由执行关闭的那次调用返回 true.
     */
    fun close(): Boolean {
        if (!closed.compareAndSet(false, true)) return false
        onClosed(this)
        if (lock.tryLock()) {
            try {
                releaseLocked()
            } finally {
                lock.unlock()
            }
        }
        return true
    }

    /**
     * Releases the parsed state of a document idle since [idleSince] or longer; the handle stays valid (appendix D Q10).
     * Skips a document with a call in flight. Answers whether something was released.
     * zh-CN: 释放自 [idleSince] 起 (或更早) 空闲的文档的解析状态; 句柄仍然有效 (附录 D Q10). 跳过有在途调用的文档. 返回是否释放了内容.
     */
    fun releaseIdle(idleSince: Long): Boolean {
        if (closed.get() || lastUsed > idleSince || !lock.tryLock()) return false
        try {
            if (closed.get() || lastUsed > idleSince || resources.isEmpty()) return false
            closeResources()
            return true
        } finally {
            lock.unlock()
        }
    }

    /** Whether parsed state is held, for tests. zh-CN: 是否持有解析状态, 供测试使用. */
    val hasParsedState: Boolean
        get() = lock.withTry { resources.isNotEmpty() } ?: true

    private fun releaseLocked() {
        if (released) return
        released = true
        closeResources()
        runCatching { descriptor.close() }
        onReleased(this)
    }

    private fun closeResources() {
        // Later resources may depend on earlier ones (the renderer on the decrypted copy), so close in reverse order.
        // zh-CN: 后加入的资源可能依赖先加入的 (渲染器依赖解密副本), 因此逆序关闭.
        resources.values.reversed().forEach { runCatching { it.close() } }
        resources.clear()
    }

    private inline fun <T> ReentrantLock.withTry(block: () -> T): T? {
        if (!tryLock()) return null
        try {
            return block()
        } finally {
            unlock()
        }
    }

    companion object {
        const val RESOURCE_PDF = "pdf"
    }

}

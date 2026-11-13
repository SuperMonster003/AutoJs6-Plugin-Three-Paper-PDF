package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File

/**
 * The open documents of the plugin process (roadmap P2.1): at most [maxPerHost] per host process (protocol "Ceilings":
 * `MAX_DOCUMENTS_PER_HOST`; the eight per script are enforced by the host), a temp directory `<root>/<uid>-<pid>/<id>/`
 * per document that disappears with it, closing by host and an idle release of parsed state ([sweep], appendix D Q10).
 * zh-CN: 插件进程中已打开的文档 (路线图 P2.1): 每个宿主进程至多 [maxPerHost] 个 (协议 "Ceilings": `MAX_DOCUMENTS_PER_HOST`;
 * 每个脚本 8 个由宿主执行), 每个文档一个随其消失的临时目录 `<root>/<uid>-<pid>/<id>/`, 按宿主关闭, 以及空闲时释放解析状态
 * ([sweep], 附录 D Q10).
 */
class DocumentRegistry(
    private val root: File,
    private val maxPerHost: Int = PdfContract.MAX_DOCUMENTS_PER_HOST,
    private val loader: (PdfSource, String?, File) -> LoadedPdf = { source, password, dir -> PdfLoader.load(source, password, dir) },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private val lock = Any()
    private val sessions = LinkedHashMap<Int, PdfSession>()
    private val reserved = HashMap<HostKey, Int>()
    private var lastId = 0

    /**
     * Opens [source] for [host]: duplicates the descriptor (the host closes its own after the call), checks it, parses
     * it once to report password and format errors at once, and registers the document.
     * zh-CN: 为 [host] 打开 [source]: 复制描述符 (宿主在调用后关闭自己的), 检查它, 先解析一次以便立即报告密码与格式错误, 然后登记文档.
     */
    fun open(host: HostKey, contractVersion: Int, source: ParcelFileDescriptor, password: String?, displayName: String?): PdfSession {
        val (id, directory) = reserve(host)
        var descriptor: ParcelFileDescriptor? = null
        try {
            descriptor = source.dup()
            val session = PdfSession(
                id = id,
                host = host,
                contractVersion = contractVersion,
                displayName = displayName,
                descriptor = descriptor,
                source = DescriptorSource.of(descriptor),
                password = password,
                directory = directory,
                loader = loader,
                onClosed = ::forget,
                onReleased = { released(it.directory) },
                clock = clock,
            )
            session.call { it.pdf }
            synchronized(lock) {
                unreserve(host)
                sessions[id] = session
            }
            return session
        } catch (e: Throwable) {
            runCatching { descriptor?.close() }
            synchronized(lock) {
                unreserve(host)
                released(directory)
            }
            throw e
        }
    }

    fun count(host: HostKey): Int = synchronized(lock) { sessions.values.count { it.host == host } }

    fun count(): Int = synchronized(lock) { sessions.size }

    /** Closes every document of [host]. zh-CN: 关闭 [host] 的全部文档. */
    fun closeHost(host: HostKey) = snapshot { it.host == host }.forEach { it.close() }

    /**
     * Closes the documents of the hosts matching [filter], for example those of one service entry (roadmap D44).
     * zh-CN: 关闭符合 [filter] 的宿主的文档, 例如某一服务入口的宿主 (路线图 D44).
     */
    fun closeHosts(filter: (HostKey) -> Boolean) = snapshot { filter(it.host) }.forEach { it.close() }

    /** The hosts holding documents or opening one. zh-CN: 持有文档或正在打开文档的宿主. */
    fun hosts(): Set<HostKey> = synchronized(lock) { sessions.values.mapTo(LinkedHashSet()) { it.host } + reserved.keys }

    /** Closes every document, for example when the last host unbinds. zh-CN: 关闭全部文档, 例如最后一个宿主解绑时. */
    fun closeAll() = snapshot { true }.forEach { it.close() }

    /**
     * Releases the parsed state of documents idle for [idleMillis]; answers how many were released.
     * zh-CN: 释放空闲达 [idleMillis] 的文档的解析状态; 返回释放的数量.
     */
    fun sweep(idleMillis: Long = IDLE_RELEASE_MS): Int {
        val idleSince = clock() - idleMillis
        return snapshot { true }.count { it.releaseIdle(idleSince) }
    }

    /** The temp directory of [host]. zh-CN: [host] 的临时目录. */
    fun hostDirectory(host: HostKey) = File(root, host.directoryName)

    /**
     * Creates the temp directory `<root>/<uid>-<pid>/run-<opId>/` of a `run` operation; [releaseRunDirectory] removes it
     * and the host directory once that is empty, under the same lock as the document directories.
     * zh-CN: 创建 `run` 操作的临时目录 `<root>/<uid>-<pid>/run-<opId>/`; [releaseRunDirectory] 删除它, 并在宿主目录为空时一并删除,
     * 与文档目录使用同一把锁.
     */
    fun createRunDirectory(host: HostKey, opId: Int): File = synchronized(lock) {
        val directory = File(hostDirectory(host), "$RUN_DIRECTORY_PREFIX$opId")
        directory.deleteRecursively()
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot create the temp directory of the operation")
        }
        directory
    }

    fun releaseRunDirectory(directory: File) = released(directory)

    /** Removes what a previous plugin process left behind. zh-CN: 删除上一个插件进程遗留的内容. */
    fun wipe() = synchronized(lock) {
        if (sessions.isEmpty() && reserved.isEmpty()) root.deleteRecursively()
    }

    private fun reserve(host: HostKey): Pair<Int, File> = synchronized(lock) {
        val used = sessions.values.count { it.host == host } + (reserved[host] ?: 0)
        if (used >= maxPerHost) {
            throw PdfFailure.limitExceeded("$used documents of this host are open; the plugin keeps at most $maxPerHost (close() unused ones)")
        }
        reserved[host] = (reserved[host] ?: 0) + 1
        lastId = if (lastId == Int.MAX_VALUE) 1 else lastId + 1
        val directory = File(hostDirectory(host), lastId.toString())
        directory.deleteRecursively()
        if (!directory.mkdirs() && !directory.isDirectory) {
            unreserve(host)
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot create the temp directory of the document")
        }
        lastId to directory
    }

    private fun unreserve(host: HostKey) {
        val left = (reserved[host] ?: 0) - 1
        if (left > 0) reserved[host] = left else reserved.remove(host)
    }

    private fun forget(session: PdfSession) = synchronized(lock) {
        sessions.remove(session.id)
        Unit
    }

    private fun released(directory: File) = synchronized(lock) {
        directory.deleteRecursively()
        // Removes the host directory once its last document is gone (File.delete refuses a non-empty directory).
        // zh-CN: 宿主的最后一个文档消失后删除宿主目录 (File.delete 不删除非空目录).
        directory.parentFile?.delete()
        Unit
    }

    private fun snapshot(filter: (PdfSession) -> Boolean): List<PdfSession> = synchronized(lock) { sessions.values.filter(filter) }

    companion object {

        /** Idle time after which parsed state is released (EPUB plugin precedent: 5 minutes). zh-CN: 释放解析状态的空闲时长 (沿用 EPUB 插件: 5 分钟). */
        const val IDLE_RELEASE_MS = 5 * 60 * 1000L

        /** Period of [sweep]. zh-CN: [sweep] 的周期. */
        const val SWEEP_PERIOD_MS = 60 * 1000L

        /** Prefix of the temp directories of `run` operations. zh-CN: `run` 操作临时目录的前缀. */
        const val RUN_DIRECTORY_PREFIX = "run-"

    }

}

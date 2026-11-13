package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * A host session of the file manager for device tests (protocol 28): the output calls of `ACCESS_CREATE_IN_PARENT`
 * write into [directory] the way the host does, staged until the commit and renamed with ` (1)` and so on when a name
 * is taken; every other call fails, since the tools must not use it.
 * zh-CN: 设备测试用的文件管理器宿主会话 (协议 28): `ACCESS_CREATE_IN_PARENT` 的输出调用像宿主那样写入 [directory], 提交前暂存, 名称
 * 被占用时追加 ` (1)` 等; 其他调用一律失败, 因为工具不应使用它们.
 */
internal class FakeHostSession(root: File, private val parentPath: String = PARENT_PATH) : IExplorerActionHostSession.Stub() {

    /** The folder of the selected files, where committed results appear. zh-CN: 选中文件所在的文件夹, 提交的结果出现在这里. */
    val directory = File(root, "parent").apply { deleteRecursively(); mkdirs() }

    private val staging = File(root, "staging").apply { deleteRecursively(); mkdirs() }

    private val transactions = Collections.synchronizedMap(LinkedHashMap<String, Transaction>())

    private val nextId = AtomicInteger()

    val clients: MutableList<IBinder> = Collections.synchronizedList(ArrayList())

    val conflictModes: MutableList<Int> = Collections.synchronizedList(ArrayList())

    @Volatile
    var closed = false
        private set

    @Volatile
    var aborted = 0
        private set

    /** Paths below [directory], relative and sorted. zh-CN: [directory] 之下的相对路径, 已排序. */
    fun files(): List<String> = directory.walkTopDown().filter { it.isFile }.map { it.relativeTo(directory).path.replace('\\', '/') }.sorted().toList()

    /** Nothing is staged any more. zh-CN: 不再有暂存内容. */
    fun stagingEmpty(): Boolean = transactions.isEmpty() && staging.listFiles().isNullOrEmpty()

    override fun prepareOutput(displayName: String?, mimeType: String?, conflictMode: Int): Bundle = prepare(displayName, conflictMode, tree = false)

    override fun prepareOutputTree(displayName: String?, conflictMode: Int): Bundle = prepare(displayName, conflictMode, tree = true)

    override fun openOutput(transactionId: String?): ParcelFileDescriptor {
        val transaction = transaction(transactionId)
        check(!transaction.tree) { "openOutput on a tree" }
        return ParcelFileDescriptor.open(transaction.file, WRITE_MODE)
    }

    override fun openOutputFile(transactionId: String?, relativePath: String?): ParcelFileDescriptor {
        val transaction = transaction(transactionId)
        check(transaction.tree) { "openOutputFile on a file" }
        val name = checkedName(relativePath)
        return ParcelFileDescriptor.open(File(transaction.file, name), WRITE_MODE)
    }

    override fun commitOutput(transactionId: String?): Bundle {
        val transaction = transaction(transactionId)
        val target = File(directory, transaction.name)
        check(transaction.file.renameTo(target)) { "cannot publish ${transaction.name}" }
        transactions.remove(transactionId)
        return Bundle().apply { putString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_PATH, "$parentPath/${transaction.name}") }
    }

    override fun abortOutput(transactionId: String?) {
        val transaction = transactions.remove(transactionId) ?: return
        transaction.file.deleteRecursively()
        aborted += 1
    }

    override fun attachClient(client: IBinder?) {
        client?.let(clients::add)
    }

    override fun close() {
        closed = true
        transactions.values.toList().forEach { abortOutput(it.id) }
    }

    // Calls the tools do not make. zh-CN: 工具不会发起的调用.

    override fun listChildren(path: String?, cursor: String?, offset: Int, limit: Int): Bundle = unused()
    override fun openFile(path: String?, mode: String?): ParcelFileDescriptor = unused()
    override fun openPendingOutput(transactionId: String?): ParcelFileDescriptor = unused()
    override fun prepareTargetReplacement(targetUri: String?): Bundle = unused()
    override fun createOutputDirectory(transactionId: String?, relativePath: String?): Unit = unused()
    override fun queryOutput(transactionId: String?): Bundle = unused()
    override fun listOutputs(): Bundle = unused()
    override fun prepareOutputBatch(displayNames: MutableList<String>?): Bundle = unused()
    override fun commitOutputBatch(batchId: String?): Bundle = unused()
    override fun abortOutputBatch(batchId: String?): Unit = unused()
    override fun queryOutputBatch(batchId: String?): Bundle = unused()
    override fun listOutputBatches(): Bundle = unused()
    override fun moveTargetsToTrash(targetUris: MutableList<String>?, displayNames: MutableList<String>?): Bundle = unused()
    override fun queryTargetTrash(): Bundle = unused()
    override fun getPlaybackProgress(targetUri: String?, key: String?): Bundle = unused()
    override fun reportPlaybackProgress(targetUri: String?, key: String?, position: Long, duration: Long, state: Int): Unit = unused()
    override fun queryTargetReplacement(transactionId: String?): Bundle = unused()
    override fun undoTargetReplacement(transactionId: String?, token: String?): Bundle = unused()
    override fun queryTargetTrashBatch(batchId: String?): Bundle = unused()
    override fun listTargetTrashBatches(): Bundle = unused()
    override fun undoTargetTrashBatch(batchId: String?): Bundle = unused()
    override fun openRelatedFile(targetUri: String?, relativePath: String?): ParcelFileDescriptor = unused()
    override fun listRenameParent(offset: Int, limit: Int): Bundle = unused()
    override fun renameEntries(request: Bundle?): Bundle = unused()
    override fun queryRenameBatch(batchId: String?): Bundle = unused()
    override fun listRenameBatches(): Bundle = unused()
    override fun undoRenameBatch(batchId: String?): Bundle = unused()
    override fun readMetadata(request: Bundle?): Bundle = unused()

    private fun prepare(displayName: String?, conflictMode: Int, tree: Boolean): Bundle {
        val requested = checkedName(displayName)
        conflictModes += conflictMode
        val id = "tx-${nextId.incrementAndGet()}"
        val name = synchronized(transactions) {
            uniqueName(requested).also { transactions[id] = Transaction(id, it, File(staging, id), tree) }
        }
        val file = transactions.getValue(id).file
        if (tree) check(file.mkdirs()) else check(file.createNewFile())
        return Bundle().apply {
            putString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID, id)
            putString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_NAME, name)
            putString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_PATH, "$parentPath/$name")
        }
    }

    /** [requested], or `stem (n).ext` when a file or a staged result already has it. zh-CN: [requested], 已被文件或暂存结果占用时为 `主干 (n).扩展名`. */
    private fun uniqueName(requested: String): String {
        val taken = transactions.values.map { it.name }.toSet() + directory.list().orEmpty()
        if (requested !in taken) return requested
        val dot = requested.lastIndexOf('.').takeIf { it > 0 } ?: requested.length
        val stem = requested.substring(0, dot)
        val extension = requested.substring(dot)
        return generateSequence(1) { it + 1 }.map { "$stem ($it)$extension" }.first { it !in taken }
    }

    private fun checkedName(name: String?): String {
        require(!name.isNullOrBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") { "bad name: $name" }
        require(name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) { "name too long: $name" }
        return name
    }

    private fun transaction(id: String?): Transaction = transactions[id] ?: throw IllegalStateException("unknown transaction $id")

    private fun <T> unused(): T = throw UnsupportedOperationException("3-Paper PDF tools do not use this call")

    private class Transaction(val id: String, val name: String, val file: File, val tree: Boolean)

    companion object {

        const val PARENT_PATH = "/storage/emulated/0/Documents"

        const val MAX_NAME_BYTES = 255

        private const val WRITE_MODE = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE

    }

}

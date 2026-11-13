package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.IExplorerActionHostSession

/**
 * Where a tool writes its result: one file, or one folder of files, each published by [Pending.commit] or removed by
 * [Pending.abort].
 * zh-CN: 工具写入结果的位置: 一个文件, 或一个包含多个文件的文件夹, 各自由 [Pending.commit] 发布或由 [Pending.abort] 删除.
 */
internal interface ToolOutput {

    /** A result not published yet. zh-CN: 尚未发布的结果. */
    interface Pending {

        /** Publishes the result and answers the path the file manager shows. zh-CN: 发布结果并返回文件管理器显示的路径. */
        fun commit(): String

        /** Removes what was written; never throws. zh-CN: 删除已写入的内容; 从不抛出异常. */
        fun abort()

    }

    /** A file being written through [descriptor] (write only). zh-CN: 正经 [descriptor] (只写) 写入的文件. */
    interface PendingFile : Pending {
        val descriptor: ParcelFileDescriptor
    }

    /** A folder whose files are created one by one. zh-CN: 逐个创建其中文件的文件夹. */
    interface PendingFolder : Pending {

        /** Creates the file [name] in the folder for writing. zh-CN: 在文件夹中创建文件 [name] 以供写入. */
        fun openFile(name: String): ParcelFileDescriptor

    }

    fun createFile(name: String, mimeType: String): PendingFile

    fun createFolder(name: String): PendingFolder

}

/**
 * [ToolOutput] through the file manager's host session (protocol 28, `ACCESS_CREATE_IN_PARENT`): the results go into
 * the folder of the selected files; a name already taken gets a number from the host (`OUTPUT_CONFLICT_AUTO_RENAME`),
 * and nothing appears until the commit.
 * zh-CN: 经文件管理器宿主会话的 [ToolOutput] (协议 28, `ACCESS_CREATE_IN_PARENT`): 结果写入选中文件所在的文件夹; 名称已被占用时
 * 由宿主加序号 (`OUTPUT_CONFLICT_AUTO_RENAME`), 提交之前不会出现任何内容.
 */
internal class HostSessionOutput(private val session: IExplorerActionHostSession) : ToolOutput {

    override fun createFile(name: String, mimeType: String): ToolOutput.PendingFile {
        val prepared = session.prepareOutput(name, mimeType, ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME)
        val transaction = transactionOf(prepared)
        val descriptor = try {
            session.openOutput(transaction) ?: throw IllegalStateException("the host gave no output descriptor")
        } catch (e: Throwable) {
            runCatching { session.abortOutput(transaction) }
            throw e
        }
        return object : ToolOutput.PendingFile {
            override val descriptor = descriptor
            override fun commit(): String = committed(transaction, prepared)
            override fun abort() = aborted(transaction)
        }
    }

    override fun createFolder(name: String): ToolOutput.PendingFolder {
        val prepared = session.prepareOutputTree(name, ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME)
        val transaction = transactionOf(prepared)
        return object : ToolOutput.PendingFolder {
            override fun openFile(name: String): ParcelFileDescriptor =
                session.openOutputFile(transaction, name) ?: throw IllegalStateException("the host gave no descriptor for $name")
            override fun commit(): String = committed(transaction, prepared)
            override fun abort() = aborted(transaction)
        }
    }

    private fun committed(transaction: String, prepared: Bundle): String {
        val result = session.commitOutput(transaction)
        return result?.getString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_PATH)
            ?: prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_PATH)
            ?: prepared.getString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_NAME).orEmpty()
    }

    private fun aborted(transaction: String) {
        runCatching { session.abortOutput(transaction) }
    }

    private fun transactionOf(prepared: Bundle?): String =
        prepared?.getString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID)?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("the host prepared no output transaction")

}

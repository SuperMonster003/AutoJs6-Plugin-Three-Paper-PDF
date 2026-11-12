package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.IPdfViewerCallback
import org.autojs.plugin.pdf.api.IPdfViewerSession
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfOps

/**
 * Answers `org.autojs.plugin.PDF` (category `pdf`), guarded by `org.autojs.permission.PLUGIN`. The skeleton reports its
 * identity and capabilities and rejects every operation with `UNSUPPORTED: NOT_IMPLEMENTED` until roadmap P2.1 lands
 * the handle registry and the task model; the file descriptors it receives are closed at once.
 * zh-CN: 响应 `org.autojs.plugin.PDF` (category `pdf`), 受 `org.autojs.permission.PLUGIN` 保护. 骨架只返回身份与能力, 在路线图
 * P2.1 落地句柄登记与任务模型之前, 所有操作都以 `UNSUPPORTED: NOT_IMPLEMENTED` 拒绝; 收到的文件描述符立即关闭.
 */
class ThreePaperPdfService : Service() {

    private val binder = object : IPdfPlugin.Stub() {

        override fun getInfo(): PluginInfo = pluginRuntimeFields().toPluginInfo()

        override fun getCapabilities(): Bundle = pluginRuntimeFields().capabilities().apply {
            putInt(PdfContract.KEY_CONTRACT_VERSION, ThreePaperPdfContract.CONTRACT_VERSION)
        }

        override fun open(source: ParcelFileDescriptor?, options: Bundle?): IPdfDocument {
            source.closeQuietly()
            throw notImplemented(PdfOps.INFO)
        }

        override fun run(
            request: Bundle?,
            inputs: Array<out ParcelFileDescriptor?>?,
            output: ParcelFileDescriptor?,
            callback: IPdfProgressCallback?,
        ): Int {
            inputs?.forEach { it.closeQuietly() }
            output.closeQuietly()
            throw notImplemented(request?.getString(PdfContract.KEY_OP).orEmpty())
        }

        // Nothing runs yet, so no operation id is known. zh-CN: 尚无任务运行, 因此没有已知的操作 id.
        override fun cancel(opId: Int): Boolean = false

        override fun openViewer(source: ParcelFileDescriptor?, options: Bundle?, callback: IPdfViewerCallback?): IPdfViewerSession {
            source.closeQuietly()
            throw notImplemented(PdfOps.OPEN_VIEWER)
        }

    }

    override fun onBind(intent: Intent?): IBinder = binder

    private companion object {

        /**
         * Methods without a Bundle answer report errors as `CODE: detail`, thrown as one of the exception types Binder
         * carries (contract version 1 allows `IllegalArgumentException`, `IllegalStateException`, `SecurityException`).
         * zh-CN: 没有 Bundle 应答的方法以 `CODE: detail` 报错, 抛出 Binder 能传递的异常类型 (契约版本 1 只允许
         * `IllegalArgumentException`, `IllegalStateException`, `SecurityException`).
         */
        fun notImplemented(op: String): RuntimeException {
            val detail = listOf(ThreePaperPdfContract.NOT_IMPLEMENTED, op).filter { it.isNotEmpty() }.joinToString(": ")
            return PdfFailure.unsupported(detail).toBinderException()
        }

        fun ParcelFileDescriptor?.closeQuietly() {
            runCatching { this?.close() }
        }

    }

}

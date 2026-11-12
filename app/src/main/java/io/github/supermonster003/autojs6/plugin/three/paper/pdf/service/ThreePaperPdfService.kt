package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.HostKey
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfAnswers
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfBundles
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
 * Answers `org.autojs.plugin.PDF` (category `pdf`), guarded by `org.autojs.permission.PLUGIN` (roadmap P2.1): identity
 * and capabilities, `open` through the [PdfEngine] document registry (at most `MAX_DOCUMENTS_PER_HOST` per host
 * process), `run` ([RunOperations]) and `cancel` of asynchronous operations. Operations not implemented yet answer `UNSUPPORTED` with the
 * detail `NOT_IMPLEMENTED: <op>`. The host holds one binding per open document, run and viewer, so when the last
 * binding goes ([onUnbind]) every document is closed and its temp files removed.
 * zh-CN: 响应 `org.autojs.plugin.PDF` (category `pdf`), 受 `org.autojs.permission.PLUGIN` 保护 (路线图 P2.1): 身份与能力, 经
 * [PdfEngine] 文档登记表的 `open` (每个宿主进程至多 `MAX_DOCUMENTS_PER_HOST` 个), `run` ([RunOperations]), 以及异步操作的
 * `cancel`. 尚未实现的操作以
 * `UNSUPPORTED` 与详情 `NOT_IMPLEMENTED: <op>` 作答. 宿主为每个打开的文档, 任务与查看器各持有一个绑定, 因此最后一个绑定解除时
 * ([onUnbind]) 关闭全部文档并删除其临时文件.
 */
class ThreePaperPdfService : Service() {

    private lateinit var engine: PdfEngine

    private lateinit var runs: RunOperations

    private val binder = object : IPdfPlugin.Stub() {

        override fun getInfo(): PluginInfo = pluginRuntimeFields().toPluginInfo()

        override fun getCapabilities(): Bundle = pluginRuntimeFields().capabilities().apply {
            putInt(PdfContract.KEY_CONTRACT_VERSION, ThreePaperPdfContract.CONTRACT_VERSION)
        }

        override fun open(source: ParcelFileDescriptor?, options: Bundle?): IPdfDocument {
            val host = HostKey.calling()
            try {
                PdfBundles.checkRequestSize(options)
                val request = PdfBundles.options(options)
                val version = request.contractVersion()
                val password = request.password()
                val displayName = request.string(PdfContract.KEY_DISPLAY_NAME)
                val descriptor = source ?: throw PdfFailure.invalid("open() carries no source")
                val session = engine.documents.open(host, version, descriptor, password, displayName)
                return PdfDocumentStub(session, engine)
            } catch (e: Throwable) {
                throw PdfAnswers.failureOf(e).toBinderException()
            } finally {
                source.closeQuietly()
            }
        }

        override fun run(
            request: Bundle?,
            inputs: Array<out ParcelFileDescriptor?>?,
            output: ParcelFileDescriptor?,
            callback: IPdfProgressCallback?,
        ): Int {
            try {
                PdfBundles.checkRequestSize(request)
                val options = PdfBundles.options(request)
                val version = options.contractVersion()
                val op = options.string(PdfContract.KEY_OP) ?: throw PdfFailure.invalid("request carries no ${PdfContract.KEY_OP}")
                if (op !in PdfOps.RUN_OPS) throw PdfFailure.invalid("${PdfContract.KEY_OP} \"$op\" is not one of ${PdfOps.RUN_OPS.joinToString(", ")}")
                return runs.start(HostKey.calling(), version, op, options, inputs, output, callback)
            } catch (e: Throwable) {
                throw PdfAnswers.failureOf(e).toBinderException()
            } finally {
                inputs?.forEach { it.closeQuietly() }
                output.closeQuietly()
            }
        }

        override fun cancel(opId: Int): Boolean = engine.operations.cancel(opId)

        override fun openViewer(source: ParcelFileDescriptor?, options: Bundle?, callback: IPdfViewerCallback?): IPdfViewerSession {
            source.closeQuietly()
            throw PdfDocumentStub.notImplemented(PdfOps.OPEN_VIEWER).toBinderException()
        }

    }

    override fun onCreate() {
        super.onCreate()
        engine = PdfEngine(cacheDir)
        runs = RunOperations(engine, this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * The last binding is gone, so no host holds a document, run or viewer any more.
     * zh-CN: 最后一个绑定已解除, 因此不再有宿主持有文档, 任务或查看器.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        engine.closeAll()
        return false
    }

    override fun onDestroy() {
        engine.shutdown()
        super.onDestroy()
    }

    private companion object {

        fun ParcelFileDescriptor?.closeQuietly() {
            runCatching { this?.close() }
        }

    }

}

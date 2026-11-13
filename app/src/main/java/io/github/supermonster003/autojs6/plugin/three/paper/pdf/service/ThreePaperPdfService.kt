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
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerClaim
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerSessions
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
 * process), `run` ([RunOperations]), `cancel` of asynchronous operations and `openViewer` ([ViewerSessions], P3.2).
 * Operations not implemented yet answer `UNSUPPORTED` with the detail `NOT_IMPLEMENTED: <op>`. The host holds one
 * binding per open document, run and viewer, so when the last binding goes ([onUnbind]) every document is closed and
 * its temp files removed; viewer sessions end through the viewer, their callback or the claim timeout.
 * zh-CN: 响应 `org.autojs.plugin.PDF` (category `pdf`), 受 `org.autojs.permission.PLUGIN` 保护 (路线图 P2.1): 身份与能力, 经
 * [PdfEngine] 文档登记表的 `open` (每个宿主进程至多 `MAX_DOCUMENTS_PER_HOST` 个), `run` ([RunOperations]), 异步操作的
 * `cancel`, 以及 `openViewer` ([ViewerSessions], P3.2). 尚未实现的操作以 `UNSUPPORTED` 与详情 `NOT_IMPLEMENTED: <op>` 作答.
 * 宿主为每个打开的文档, 任务与查看器各持有一个绑定, 因此最后一个绑定解除时 ([onUnbind]) 关闭全部文档并删除其临时文件; 查看器
 * 会话经查看器, 其回调或认领超时结束.
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

        /**
         * Registers a viewer session for the calling host (protocol "Viewer Sessions", roadmap P3.2): the source is
         * duplicated, the session waits for the viewer Activity to claim its token and ends an earlier session of the
         * same host with `replaced`.
         * zh-CN: 为调用方宿主登记查看器会话 (协议 "Viewer Sessions" 一节, 路线图 P3.2): 复制源, 会话等待查看器 Activity 认领其令牌,
         * 并以 `replaced` 结束同一宿主此前的会话.
         */
        override fun openViewer(source: ParcelFileDescriptor?, options: Bundle?, callback: IPdfViewerCallback?): IPdfViewerSession {
            val host = HostKey.calling()
            try {
                PdfBundles.checkRequestSize(options)
                val request = PdfBundles.options(options)
                val version = request.contractVersion()
                val viewer = ViewerOptions.parse(request)
                val descriptor = source ?: throw PdfFailure.invalid("openViewer() carries no source")
                val events = callback?.let { ViewerCallbackEvents(it, version) }
                val claim = ViewerClaim(descriptor.dup(), viewer.displayName, viewer.password, viewer.keyword)
                val session = ViewerSessions.open(host, claim, viewer.page, events)
                events?.watch(session)
                return ViewerSessionStub(session, version)
            } catch (e: Throwable) {
                throw PdfAnswers.failureOf(e).toBinderException()
            } finally {
                source.closeQuietly()
            }
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

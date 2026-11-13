package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.content.Context
import android.os.Bundle
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
 * The `IPdfPlugin` binder: identity and capabilities, `open` through the [PdfEngine] document registry (at most
 * `MAX_DOCUMENTS_PER_HOST` per host process), `run` ([RunOperations]), `cancel` of asynchronous operations and
 * `openViewer` ([ViewerSessions], P3.2). [ThreePaperPdfService] answers scripts with it; the file manager tools of
 * roadmap P3.3 call it in process (`ToolEngine`), so both take the same code paths. In-process calls carry the
 * plugin's own uid and pid as the host key.
 * zh-CN: `IPdfPlugin` 的 Binder: 身份与能力, 经 [PdfEngine] 文档登记表的 `open` (每个宿主进程至多 `MAX_DOCUMENTS_PER_HOST`
 * 个), `run` ([RunOperations]), 异步操作的 `cancel`, 以及 `openViewer` ([ViewerSessions], P3.2). [ThreePaperPdfService] 用它
 * 响应脚本; 路线图 P3.3 的文件管理器工具在进程内调用它 (`ToolEngine`), 两者走同一条代码路径. 进程内调用以插件自身的 uid 与 pid
 * 作为宿主键.
 */
internal class PdfPluginBinder(
    private val context: Context,
    private val engine: PdfEngine,
    private val runs: RunOperations,
) : IPdfPlugin.Stub() {


    override fun getInfo(): PluginInfo = context.pluginRuntimeFields().toPluginInfo()

    override fun getCapabilities(): Bundle = context.pluginRuntimeFields().capabilities().apply {
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

    private companion object {

        fun ParcelFileDescriptor?.closeQuietly() {
            runCatching { this?.close() }
        }

    }

}

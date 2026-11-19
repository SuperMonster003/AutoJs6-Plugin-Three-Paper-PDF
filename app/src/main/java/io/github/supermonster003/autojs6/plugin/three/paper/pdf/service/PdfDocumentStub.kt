package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PageEditPlan
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PageOperations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSession
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms.FormFields
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageImages
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.SearchReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.TextReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.PageRendering
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfAnswers
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfBundles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfOps

/**
 * The `IPdfDocument` handle of one open document (protocol "Methods"). Every Bundle method answers the envelope
 * (`contractVersion`, `requestId` echoed, result keys or `errorCode` + `errorMessage`); methods without a request
 * (`getInfo`, `getOutline`) answer with the version negotiated at `open`. Calls on one document run one at a time
 * ([PdfSession.call]); after [close] they end with `SESSION_CLOSED`. Received descriptors are closed when the call
 * returns: the plugin duplicates what it keeps. Only the uid that opened the document may call it; other uids get
 * `PERMISSION_DENIED` (roadmap D44, [PdfCallerGuard.checkOwner]).
 * zh-CN: 一个已打开文档的 `IPdfDocument` 句柄 (协议 "Methods" 一节). 每个 Bundle 方法返回应答信封 (`contractVersion`, 回显的
 * `requestId`, 结果键或 `errorCode` + `errorMessage`); 没有请求的方法 (`getInfo`, `getOutline`) 用 `open` 时协商的版本作答.
 * 同一文档的调用逐个执行 ([PdfSession.call]); [close] 之后以 `SESSION_CLOSED` 结束. 收到的描述符在调用返回时关闭: 插件自己
 * 复制需要保留的. 只有打开该文档的 uid 可以调用它; 其他 uid 得到 `PERMISSION_DENIED` (路线图 D44,
 * [PdfCallerGuard.checkOwner]).
 */
internal class PdfDocumentStub(private val session: PdfSession, private val engine: PdfEngine) : IPdfDocument.Stub() {

    override fun getInfo(): Bundle = answer(PdfOps.INFO, request = null) { _, call -> DocumentInfoReader.read(call.pdf, session.sizeBytes, call.stop, session.contractVersion) }

    override fun getOutline(): Bundle = answer(PdfOps.OUTLINE, request = null) { _, call -> OutlineReader.read(call.pdf, call.stop) }

    override fun getText(request: Bundle?): Bundle = answer(PdfOps.TEXT, request) { options, call -> TextReader.read(call.pdf, options, call.stop) }

    override fun search(request: Bundle?): Bundle = answer(PdfOps.SEARCH, request) { options, call -> SearchReader.read(call.pdf, options, call.stop) }

    override fun render(request: Bundle?, fallbackOutput: ParcelFileDescriptor?): Bundle = answer(PdfOps.RENDER, request, fallbackOutput) { options, call ->
        PageRendering.render(call, engine.renderPool, options, fallbackOutput, ::closeAfterReply)
    }

    override fun renderToFile(request: Bundle?, output: ParcelFileDescriptor?): Bundle = answer(PdfOps.RENDER_TO_FILE, request, output) { options, call ->
        PageRendering.renderToFile(call, engine.renderPool, options, output)
    }

    /**
     * Form fields (contract version 2, roadmap P6.4, [FormFields]); a version-1 request is `UNSUPPORTED`.
     * zh-CN: 表单字段 (契约版本 2, 路线图 P6.4, [FormFields]); 版本 1 的请求为 `UNSUPPORTED`.
     */
    override fun getFormFields(request: Bundle?): Bundle = answer(PdfOps.FORM_FIELDS, request) { options, call -> FormFields.read(call.pdf, options, call.stop) }

    override fun planImages(request: Bundle?): Bundle = answer(PdfOps.PLAN_IMAGES, request) { options, call -> PageImages.plan(call.pdf, options, call.stop) }

    override fun extractImage(request: Bundle?, output: ParcelFileDescriptor?): Bundle =
        answer(PdfOps.EXTRACT_IMAGE, request, output) { options, call -> PageImages.extract(call.pdf, options, output, call.stop) }

    override fun planSplit(request: Bundle?): Bundle = answer(PdfOps.PLAN_SPLIT, request) { options, call ->
        PageEditPlan.manifest(PageEditPlan.split(options, call.pdf.numberOfPages))
    }

    override fun split(request: Bundle?, outputs: Array<out ParcelFileDescriptor?>?, callback: IPdfProgressCallback?): Int {
        val received = outputs.orEmpty()
        return start(PdfOps.SPLIT, request, callback, *received) { options, call ->
            val parts = PageEditPlan.split(options, call.pdf.numberOfPages)
            if (received.size != parts.size) {
                throw PdfFailure.invalid("the split gives ${parts.size} parts but ${received.size} outputs were given (planSplit tells the count)")
            }
            PdfWriting.checkWritable(call.pdf)
            Prepared(received.map { it ?: throw PdfFailure.invalid("a split output is null") }) { operation, running ->
                PageOperations.split(running, parts, operation)
            }
        }
    }

    override fun save(request: Bundle?, output: ParcelFileDescriptor?, callback: IPdfProgressCallback?): Int =
        start(PdfOps.SAVE, request, callback, output) { options, call ->
            val placements = PageEditPlan.save(options, call.pdf.numberOfPages)
            PdfWriting.checkWritable(call.pdf)
            Prepared(listOf(output ?: throw PdfFailure.invalid("save() carries no output"))) { operation, running ->
                PageOperations.save(running, placements, operation)
            }
        }

    /**
     * Cancels the running `split` / `save` first, so they end with `CANCELLED` and release the document lock, then
     * closes the document.
     * zh-CN: 先取消运行中的 `split` / `save`, 使其以 `CANCELLED` 结束并释放文档锁, 再关闭文档.
     */
    override fun close() {
        engine.operations.cancelDocument(session.id)
        session.close()
    }

    /**
     * Runs the transaction, then closes what the answer referenced: a Parcel duplicates the descriptor of a
     * `SharedMemory` region, so the plugin releases its own reference once the reply is written (protocol "Rendering").
     * zh-CN: 执行事务后关闭应答引用的对象: Parcel 会复制 `SharedMemory` 区域的描述符, 因此回复写出后插件释放自己的引用 (协议
     * "Rendering" 一节).
     */
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        PdfCallerGuard.checkOwner(code, session.host.uid)
        val outer = afterReply.get()
        val pending = ArrayList<AutoCloseable>(1)
        afterReply.set(pending)
        try {
            return super.onTransact(code, data, reply, flags)
        } finally {
            afterReply.set(outer)
            pending.forEach { runCatching { it.close() } }
        }
    }

    /**
     * Closes [resource] after the reply of the current transaction; a direct call outside a transaction (same process,
     * no Binder) leaves it to the caller.
     * zh-CN: 在当前事务的回复写出后关闭 [resource]; 不经事务的直接调用 (同进程, 未经 Binder) 由调用方负责.
     */
    private fun closeAfterReply(resource: AutoCloseable) {
        afterReply.get()?.add(resource)
    }

    /**
     * Serves a Bundle method: reads the envelope of [request] (absent for `getInfo` / `getOutline`), runs [block] holding
     * the document and answers its results, or the failure of whatever it threw. An answer above `MAX_ANSWER_BYTES` is
     * replaced by `LIMIT_EXCEEDED`; paged methods stay below it themselves.
     * zh-CN: 处理一个 Bundle 方法: 读取 [request] 的信封 (`getInfo` / `getOutline` 没有请求), 持有文档执行 [block] 并返回其结果,
     * 或其抛出的失败. 超过 `MAX_ANSWER_BYTES` 的应答替换为 `LIMIT_EXCEEDED`; 分页方法自行保持在其之下.
     */
    private fun answer(
        op: String,
        request: Bundle?,
        vararg received: ParcelFileDescriptor?,
        hasRequest: Boolean = op != PdfOps.INFO && op != PdfOps.OUTLINE,
        block: (PdfOptions, PdfSession.Call) -> Map<String, Any?>,
    ): Bundle {
        var version = session.contractVersion
        var requestId: String? = null
        try {
            val answer = try {
                val options = PdfBundles.options(request)
                if (hasRequest) {
                    requestId = runCatching { options.string(PdfContract.KEY_REQUEST_ID) }.getOrNull()
                    PdfBundles.checkRequestSize(request)
                    version = options.contractVersion()
                }
                checkAvailable(op, version)
                val results = session.call { block(options, it) }
                PdfBundles.toBundle(PdfAnswers.success(version, results, requestId))
            } catch (e: Throwable) {
                PdfBundles.toBundle(PdfAnswers.failure(version, PdfAnswers.failureOf(e), requestId))
            }
            val size = PdfBundles.parcelSize(answer)
            if (size > PdfContract.MAX_ANSWER_BYTES) {
                val failure = PdfFailure.limitExceeded("the $op answer of $size bytes exceeds ${PdfContract.MAX_ANSWER_BYTES}; ask for fewer pages")
                return PdfBundles.toBundle(PdfAnswers.failure(version, failure, requestId))
            }
            return answer
        } finally {
            received.forEach { runCatching { it?.close() } }
        }
    }

    /** The checked plan of a `split` / `save`: its outputs and its work. zh-CN: 已检查的 `split` / `save` 计划: 输出与工作. */
    private class Prepared(
        val outputs: List<ParcelFileDescriptor>,
        val work: (Operations.Operation, PdfSession.Call) -> Map<String, Any?>,
    )

    /**
     * Starts `split` / `save`: [prepare] checks the request holding the document, before an id exists (protocol: a
     * request refused up front throws); the work runs on the operation executor, holding the document again, so other
     * calls on this document wait until it ends. Received descriptors are closed when the call returns (the operation
     * keeps duplicates).
     * zh-CN: 启动 `split` / `save`: [prepare] 持有文档检查请求, 此时尚无 id (协议: 预先拒绝的请求直接抛出); 工作在操作执行器上再次
     * 持有文档执行, 因此该文档上的其他调用等待其结束. 收到的描述符在调用返回时关闭 (操作保留副本).
     */
    private fun start(
        op: String,
        request: Bundle?,
        callback: IPdfProgressCallback?,
        vararg received: ParcelFileDescriptor?,
        prepare: (PdfOptions, PdfSession.Call) -> Prepared,
    ): Int {
        try {
            if (session.isClosed) throw StopSignal.closed()
            PdfBundles.checkRequestSize(request)
            val options = PdfBundles.options(request)
            val version = options.contractVersion()
            val progress = callback ?: throw PdfFailure.invalid("$op() carries no progress callback")
            val prepared = session.call { prepare(options, it) }
            return engine.operations.start(session.host, version, op, prepared.outputs, progress, session.id, session.stopSignal) { operation ->
                session.call { prepared.work(operation, it) }
            }
        } catch (e: Throwable) {
            throw PdfAnswers.failureOf(e).toBinderException()
        } finally {
            received.forEach { runCatching { it?.close() } }
        }
    }

    companion object {

        /** What to close once the reply of this thread's transaction is written. zh-CN: 本线程的事务回复写出后要关闭的对象. */
        private val afterReply = ThreadLocal<MutableList<AutoCloseable>?>()

        /**
         * Refuses [op] with `UNSUPPORTED` when it belongs to a contract version above the request's [version] (protocol
         * "Version 2": a host never sends such a request, roadmap D58).
         * zh-CN: [op] 属于高于请求版本 [version] 的契约版本时以 `UNSUPPORTED` 拒绝 (协议 "Version 2" 一节: 宿主不会发这样的请求,
         * 路线图 D58).
         */
        fun checkAvailable(op: String, version: Int) {
            if (!PdfOps.isAvailable(op, version)) {
                throw PdfFailure.unsupported("$op needs PDF contract version ${PdfOps.versionOf(op)}, the request carries version $version")
            }
        }

        /** `UNSUPPORTED` with the detail `NOT_IMPLEMENTED: <op>`. zh-CN: 详情为 `NOT_IMPLEMENTED: <op>` 的 `UNSUPPORTED`. */
        fun notImplemented(op: String) = PdfFailure.unsupported("${ThreePaperPdfContract.NOT_IMPLEMENTED}: $op")

    }

}

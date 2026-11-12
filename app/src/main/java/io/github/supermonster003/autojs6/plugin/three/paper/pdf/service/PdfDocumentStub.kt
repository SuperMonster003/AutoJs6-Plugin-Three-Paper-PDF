package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSession
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
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
 * returns: the plugin duplicates what it keeps.
 * zh-CN: 一个已打开文档的 `IPdfDocument` 句柄 (协议 "Methods" 一节). 每个 Bundle 方法返回应答信封 (`contractVersion`, 回显的
 * `requestId`, 结果键或 `errorCode` + `errorMessage`); 没有请求的方法 (`getInfo`, `getOutline`) 用 `open` 时协商的版本作答.
 * 同一文档的调用逐个执行 ([PdfSession.call]); [close] 之后以 `SESSION_CLOSED` 结束. 收到的描述符在调用返回时关闭: 插件自己
 * 复制需要保留的.
 */
internal class PdfDocumentStub(private val session: PdfSession, private val engine: PdfEngine) : IPdfDocument.Stub() {

    override fun getInfo(): Bundle = answer(PdfOps.INFO, request = null) { _, call -> DocumentInfoReader.read(call.pdf, session.sizeBytes, call.stop) }

    override fun getOutline(): Bundle = answer(PdfOps.OUTLINE, request = null) { _, call -> OutlineReader.read(call.pdf, call.stop) }

    override fun getText(request: Bundle?): Bundle = answer(PdfOps.TEXT, request) { options, call -> TextReader.read(call.pdf, options, call.stop) }

    override fun search(request: Bundle?): Bundle = answer(PdfOps.SEARCH, request) { options, call -> SearchReader.read(call.pdf, options, call.stop) }

    override fun render(request: Bundle?, fallbackOutput: ParcelFileDescriptor?): Bundle = answer(PdfOps.RENDER, request, fallbackOutput) { options, call ->
        PageRendering.render(call, engine.renderPool, options, fallbackOutput, ::closeAfterReply)
    }

    override fun renderToFile(request: Bundle?, output: ParcelFileDescriptor?): Bundle = answer(PdfOps.RENDER_TO_FILE, request, output) { options, call ->
        PageRendering.renderToFile(call, engine.renderPool, options, output)
    }

    override fun planImages(request: Bundle?): Bundle = answer(PdfOps.PLAN_IMAGES, request) { _, _ -> throw notImplemented(PdfOps.PLAN_IMAGES) }

    override fun extractImage(request: Bundle?, output: ParcelFileDescriptor?): Bundle =
        answer(PdfOps.EXTRACT_IMAGE, request, output) { _, _ -> throw notImplemented(PdfOps.EXTRACT_IMAGE) }

    override fun planSplit(request: Bundle?): Bundle = answer(PdfOps.PLAN_SPLIT, request) { _, _ -> throw notImplemented(PdfOps.PLAN_SPLIT) }

    override fun split(request: Bundle?, outputs: Array<out ParcelFileDescriptor?>?, callback: IPdfProgressCallback?): Int =
        start(PdfOps.SPLIT, *outputs.orEmpty())

    override fun save(request: Bundle?, output: ParcelFileDescriptor?, callback: IPdfProgressCallback?): Int = start(PdfOps.SAVE, output)

    override fun close() {
        if (session.close()) engine.operations.cancelDocument(session.id)
    }

    /**
     * Runs the transaction, then closes what the answer referenced: a Parcel duplicates the descriptor of a
     * `SharedMemory` region, so the plugin releases its own reference once the reply is written (protocol "Rendering").
     * zh-CN: 执行事务后关闭应答引用的对象: Parcel 会复制 `SharedMemory` 区域的描述符, 因此回复写出后插件释放自己的引用 (协议
     * "Rendering" 一节).
     */
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
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

    /**
     * `split` / `save` are not implemented yet: refused up front (protocol: a request refused before an id exists throws).
     * zh-CN: `split` / `save` 尚未实现: 预先拒绝 (协议: 产生 id 之前被拒绝的请求直接抛出).
     */
    private fun start(op: String, vararg received: ParcelFileDescriptor?): Int {
        try {
            if (session.isClosed) throw StopSignal.closed()
            throw notImplemented(op)
        } catch (e: Throwable) {
            throw PdfAnswers.failureOf(e).toBinderException()
        } finally {
            received.forEach { runCatching { it?.close() } }
        }
    }

    companion object {

        /** What to close once the reply of this thread's transaction is written. zh-CN: 本线程的事务回复写出后要关闭的对象. */
        private val afterReply = ThreadLocal<MutableList<AutoCloseable>?>()

        /** `UNSUPPORTED` with the detail `NOT_IMPLEMENTED: <op>`. zh-CN: 详情为 `NOT_IMPLEMENTED: <op>` 的 `UNSUPPORTED`. */
        fun notImplemented(op: String) = PdfFailure.unsupported("${ThreePaperPdfContract.NOT_IMPLEMENTED}: $op")

    }

}

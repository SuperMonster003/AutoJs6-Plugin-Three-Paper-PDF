package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress.CompressOperation
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PageOperations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfEncryption
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.HostKey
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms.FormFill
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.HtmlToPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.ImagesToPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.TextToPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata.InfoChanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata.OutlineChanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.StampOperation
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.textlayer.TextLayers
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import java.io.File

/**
 * Starts `IPdfPlugin.run` operations (protocol "Run operations"): the request is checked before an id exists (a request
 * refused up front throws), the inputs are duplicated and owned by the operation, and every operation gets a temp
 * directory `<root>/<uid>-<pid>/run-<id>/` (`DocumentRegistry.createRunDirectory`) that disappears when it ends.
 * Operations not implemented yet answer `NOT_IMPLEMENTED: <op>`.
 * zh-CN: 启动 `IPdfPlugin.run` 操作 (协议 "Run operations" 一节): 在产生 id 之前检查请求 (预先拒绝的请求直接抛出), 输入被复制并
 * 归操作所有, 每个操作有一个随其结束而消失的临时目录 `<root>/<uid>-<pid>/run-<id>/`. 尚未实现的操作返回
 * `NOT_IMPLEMENTED: <op>`.
 */
internal class RunOperations(private val engine: PdfEngine, context: Context) {

    private val html = HtmlToPdf(context.applicationContext)

    /** The work of one operation on its owned inputs and temp directory. zh-CN: 一个操作基于其自有输入与临时目录的工作. */
    private fun interface Work {
        fun run(operation: Operations.Operation, inputs: List<ParcelFileDescriptor>, directory: File): Map<String, Any?>
    }

    fun start(
        host: HostKey,
        version: Int,
        op: String,
        options: PdfOptions,
        inputs: Array<out ParcelFileDescriptor?>?,
        output: ParcelFileDescriptor?,
        callback: IPdfProgressCallback?,
    ): Int {
        val received = inputs.orEmpty().mapIndexed { index, input -> input ?: throw PdfFailure.invalid("input ${index + 1} is null") }
        val work = prepare(op, options, received.size)
        val target = output ?: throw PdfFailure.invalid("run() carries no output")
        val progress = callback ?: throw PdfFailure.invalid("run() carries no progress callback")
        val owned = duplicate(received)
        try {
            return engine.operations.start(host, version, op, listOf(target), progress) { operation ->
                var directory: File? = null
                try {
                    directory = engine.documents.createRunDirectory(host, operation.id)
                    work.run(operation, owned, directory)
                } finally {
                    owned.forEach { runCatching { it.close() } }
                    directory?.let { engine.documents.releaseRunDirectory(it) }
                }
            }
        } catch (e: Throwable) {
            // The work never runs, so the inputs are still ours to close. zh-CN: 工作不会执行, 因此输入仍由此处关闭.
            owned.forEach { runCatching { it.close() } }
            throw e
        }
    }

    private fun prepare(op: String, options: PdfOptions, inputCount: Int): Work = when (op) {
        PdfOps.MERGE -> {
            val request = PageOperations.mergeRequest(options, inputCount)
            Work { operation, inputs, directory -> PageOperations.merge(request, inputs, directory, operation) }
        }
        PdfOps.FROM_IMAGES -> {
            val request = ImagesToPdf.request(options, inputCount)
            Work { operation, inputs, directory -> ImagesToPdf.write(request, inputs, directory, operation) }
        }
        PdfOps.FROM_TEXT -> {
            val request = TextToPdf.request(options, inputCount)
            Work { operation, inputs, directory -> TextToPdf.write(request, inputs, directory, operation) }
        }
        PdfOps.FROM_HTML -> {
            val request = HtmlToPdf.request(options, inputCount)
            Work { operation, inputs, directory -> html.write(request, inputs, directory, operation) }
        }
        PdfOps.ENCRYPT -> {
            val request = PdfEncryption.encryptRequest(options, inputCount)
            Work { operation, inputs, directory -> PdfEncryption.encrypt(request, inputs.single(), directory, operation) }
        }
        PdfOps.DECRYPT -> {
            val request = PdfEncryption.decryptRequest(options, inputCount)
            Work { operation, inputs, directory -> PdfEncryption.decrypt(request, inputs.single(), directory, operation) }
        }
        PdfOps.STAMP -> {
            val request = StampOperation.request(options, inputCount)
            Work { operation, inputs, directory -> StampOperation.write(request, inputs, directory, operation) }
        }
        PdfOps.COMPRESS -> {
            val request = CompressOperation.request(options, inputCount)
            Work { operation, inputs, directory -> CompressOperation.write(request, inputs.single(), directory, operation) }
        }
        PdfOps.SET_INFO -> {
            val request = InfoChanges.request(op, options, inputCount)
            Work { operation, inputs, directory -> InfoChanges.write(request, inputs, directory, operation) }
        }
        PdfOps.SET_OUTLINE -> {
            val request = InfoChanges.request(op, options, inputCount)
            Work { operation, inputs, directory -> OutlineChanges.write(request, inputs, directory, operation) }
        }
        PdfOps.ADD_TEXT_LAYER -> {
            val request = TextLayers.request(options, inputCount)
            Work { operation, inputs, directory -> TextLayers.write(request, inputs, directory, operation) }
        }
        PdfOps.FILL_FORM -> {
            val request = FormFill.request(options, inputCount)
            Work { operation, inputs, directory -> FormFill.write(request, inputs, directory, operation) }
        }
        else -> throw PdfDocumentStub.notImplemented(op)
    }

    private fun duplicate(received: List<ParcelFileDescriptor>): List<ParcelFileDescriptor> {
        val copies = ArrayList<ParcelFileDescriptor>(received.size)
        try {
            received.forEach { copies += it.dup() }
        } catch (e: Exception) {
            copies.forEach { runCatching { it.close() } }
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot duplicate the inputs: ${e.message}")
        }
        return copies
    }

}

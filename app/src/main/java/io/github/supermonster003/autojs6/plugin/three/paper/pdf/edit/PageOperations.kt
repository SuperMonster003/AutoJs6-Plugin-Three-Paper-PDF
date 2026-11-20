package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.LoadedPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSession
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * The asynchronous page operations on descriptors (roadmap P2.4): `save` and `split` of an open document and the `run`
 * operation `merge`. Each output is written by [PdfWriting]; the operation framework ([Operations]) flushes and closes
 * the outputs, or empties them after a failure.
 * zh-CN: 基于描述符的异步页面操作 (路线图 P2.4): 已打开文档的 `save` 与 `split`, 以及 `run` 操作 `merge`. 每个输出由 [PdfWriting]
 * 写出; 操作框架 ([Operations]) 刷新并关闭输出, 失败时清空输出.
 */
object PageOperations {

    /** Main memory of each merge input before its scratch file is used. zh-CN: 每个合并输入在使用暂存文件之前的主存额度. */
    private const val MERGE_INPUT_MEMORY_BYTES = 1L * 1024 * 1024

    /**
     * `save`: writes [placements] of the document of [call] to the single output.
     * zh-CN: `save`: 把 [call] 所属文档的 [placements] 写到唯一的输出.
     */
    fun save(call: PdfSession.Call, placements: List<PagePlacement>, operation: Operations.Operation): Map<String, Any?> =
        call.loadSeparately().use { loaded ->
            val document = loaded.document
            operation.progress(PdfContract.STAGE_PROCESSING, 0, 1)
            val warnings = if (PageEditPlan.isIdentity(placements, document.numberOfPages)) {
                emptyList()
            } else {
                PageEditor.apply(document, placements, operation.stop)
            }
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(document, outputStream(operation.outputs.single()), operation.stop)
            result(document.numberOfPages, size, warnings)
        }

    /**
     * `split`: writes part i of [parts] to output i; the result `items` hold `index`, `pageCount` and `sizeBytes`.
     * zh-CN: `split`: 把 [parts] 的第 i 部分写到第 i 个输出; 结果 `items` 含 `index`, `pageCount` 与 `sizeBytes`.
     */
    fun split(call: PdfSession.Call, parts: List<IntArray>, operation: Operations.Operation): Map<String, Any?> {
        check(parts.size == operation.outputs.size) { "${parts.size} parts for ${operation.outputs.size} outputs" }
        val items = JSONArray()
        val warnings = LinkedHashSet<PdfWarning>()
        parts.forEachIndexed { index, pages ->
            operation.stop.checkStop()
            operation.progress(PdfContract.STAGE_WRITING, index, parts.size)
            call.loadSeparately().use { loaded ->
                warnings += PageEditor.apply(loaded.document, pages.map { PagePlacement(it) }, operation.stop)
                val size = PdfWriting.write(loaded.document, outputStream(operation.outputs[index]), operation.stop)
                items.put(
                    JSONObject()
                        .put(PdfContract.FIELD_INDEX, index)
                        .put(PdfContract.FIELD_PAGE_COUNT, pages.size)
                        .put(PdfContract.FIELD_SIZE_BYTES, size),
                )
            }
        }
        return buildMap {
            put(PdfContract.KEY_ITEMS, items.toString())
            if (warnings.isNotEmpty()) put(PdfContract.KEY_WARNINGS, warnings.toList())
        }
    }

    /**
     * Reads the `merge` request for [inputCount] inputs: `passwords` (one per input, empty for none), `displayNames` (one
     * per input, the outline titles, empty for the fallback; appendix D Q31 (b)) and `outline`.
     * zh-CN: 读取 [inputCount] 个输入的 `merge` 请求: `passwords` (每个输入一个, 空串表示没有), `displayNames` (每个输入一个, 作大纲
     * 标题, 空串表示用回退值; 附录 D Q31 (b)) 与 `outline`.
     */
    fun mergeRequest(options: PdfOptions, inputCount: Int): MergeRequest {
        if (inputCount < 1) throw PdfFailure.invalid("merge needs at least one input")
        if (inputCount > PdfContract.MAX_RUN_INPUTS) throw PdfFailure.limitExceeded("$inputCount inputs exceed ${PdfContract.MAX_RUN_INPUTS}")
        val passwords = options.stringArray(PdfContract.KEY_PASSWORDS)?.let { given ->
            if (given.size != inputCount) {
                throw PdfFailure.invalid("${PdfContract.KEY_PASSWORDS} has ${given.size} entries for $inputCount inputs")
            }
            given.mapIndexed { index, password ->
                if (password != null && password.length > PdfContract.MAX_PASSWORD_LENGTH) {
                    throw PdfFailure.invalid("${PdfContract.KEY_PASSWORDS}[$index] is longer than ${PdfContract.MAX_PASSWORD_LENGTH} characters")
                }
                password?.takeIf { it.isNotEmpty() }
            }
        } ?: List(inputCount) { null }
        val displayNames = options.stringArray(PdfContract.KEY_DISPLAY_NAMES)?.let { given ->
            if (given.size != inputCount) {
                throw PdfFailure.invalid("${PdfContract.KEY_DISPLAY_NAMES} has ${given.size} entries for $inputCount inputs")
            }
            given.map { name -> name?.trim()?.takeIf { it.isNotEmpty() } }
        } ?: List(inputCount) { null }
        val outline = checkNotNull(options.choice(PdfContract.KEY_OUTLINE, PdfContract.OUTLINE_MODES, PdfContract.OUTLINE_MERGE))
        return MergeRequest(passwords, displayNames, outline)
    }

    /** A checked `merge` request. zh-CN: 已检查的 `merge` 请求. */
    class MergeRequest(val passwords: List<String?>, val displayNames: List<String?>, val outline: String)

    /**
     * `merge`: appends [inputs] in order and writes the result to the single output; [directory] holds scratch files.
     * zh-CN: `merge`: 按顺序追加 [inputs] 并把结果写到唯一的输出; [directory] 存放暂存文件.
     */
    fun merge(request: MergeRequest, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        val sources = ArrayList<LoadedPdf>(inputs.size)
        val destination = PDDocument(MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory))
        try {
            val merger = PdfMerger(destination, request.outline)
            inputs.forEachIndexed { index, input ->
                operation.stop.checkStop()
                operation.progress(PdfContract.STAGE_PROCESSING, index, inputs.size)
                val loaded = try {
                    PdfLoader.load(DescriptorSource.of(input), request.passwords[index], directory, MERGE_INPUT_MEMORY_BYTES)
                } catch (e: PdfFailure) {
                    throw PdfFailure(e.code, "input ${index + 1}: ${e.detail}")
                }
                sources += loaded
                merger.append(loaded.document, request.displayNames[index])
            }
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(destination, outputStream(operation.outputs.single()), operation.stop)
            return result(destination.numberOfPages, size, emptyList())
        } finally {
            runCatching { destination.close() }
            sources.forEach { runCatching { it.close() } }
        }
    }

    private fun result(pageCount: Int, sizeBytes: Long, warnings: Collection<PdfWarning>): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_PAGE_COUNT, pageCount)
        put(PdfContract.KEY_SIZE_BYTES, sizeBytes)
        if (warnings.isNotEmpty()) put(PdfContract.KEY_WARNINGS, warnings.toList())
    }

    /** An unbuffered stream on [output] that [PdfWriting] never closes. zh-CN: [output] 上的无缓冲流, [PdfWriting] 不会关闭它. */
    private fun outputStream(output: ParcelFileDescriptor) = FileOutputStream(output.fileDescriptor)

}

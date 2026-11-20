package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.write.CompactWriting
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfOps
import org.autojs.plugin.pdf.api.PdfWarningCodes
import java.io.File
import java.io.FileOutputStream

/**
 * The `run` operation `compress` (roadmap P6.6, D55, D57, D64; protocol "Version 2"): the single input is the source
 * PDF, the request carries `imageDpi` (`MIN_IMAGE_DPI` to `MAX_IMAGE_DPI`, absent `DEFAULT_IMAGE_DPI`), `imageQuality`
 * (`MIN_QUALITY` to `MAX_QUALITY`, absent `DEFAULT_IMAGE_QUALITY`), `removeMetadata` and `objectStreams` (absent
 * `DEFAULT_OBJECT_STREAMS`); the presets of D55 are expanded by the host. Images are resampled and re-encoded
 * ([ImageCompression]), streams without a filter are deflated and, with `removeMetadata`, XMP, thumbnails and the
 * document information except `producer` are dropped ([StreamCleanup], `PdfWriting.stamp`); the document is then
 * written with object streams and checked ([CompactWriting], the `verifying` stage), or written the classic way.
 * Writing every reachable object anew drops unreachable objects and earlier revisions. An output that is not smaller
 * than the source is written anyway with a warning (D57); the completion carries `sourceBytes` next to `sizeBytes`.
 * Progress: `opening`, `processing` with the page of the image handled (out of the page count), `writing`,
 * `verifying`. An encrypted source must be opened with its owner password (`PERMISSION_DENIED` otherwise, Q30); the
 * output is unencrypted.
 * zh-CN: `run` 操作 `compress` (路线图 P6.6, D55, D57, D64; 协议 "Version 2" 一节): 唯一的输入是源 PDF, 请求带 `imageDpi`
 * (`MIN_IMAGE_DPI` 至 `MAX_IMAGE_DPI`, 缺省 `DEFAULT_IMAGE_DPI`), `imageQuality` (`MIN_QUALITY` 至 `MAX_QUALITY`, 缺省
 * `DEFAULT_IMAGE_QUALITY`), `removeMetadata` 与 `objectStreams` (缺省 `DEFAULT_OBJECT_STREAMS`); D55 的预设由宿主展开. 图片被重采样
 * 并重编码 ([ImageCompression]), 没有过滤器的流被压缩, `removeMetadata` 时删除 XMP, 缩略图与 `producer` 以外的文档信息
 * ([StreamCleanup], `PdfWriting.stamp`); 之后用对象流写出并校验 ([CompactWriting], `verifying` 阶段), 或以经典写法写出. 重新写出
 * 每个可达对象, 因此丢弃不可达对象与旧的修订. 输出不比源文件小时照样写出并给一条警告 (D57); 完成包在 `sizeBytes` 之外带
 * `sourceBytes`. 进度: `opening`, `processing` (当前为刚处理的图片所在页, 总数为页数), `writing`, `verifying`. 加密的源必须用所有者
 * 密码打开 (否则为 `PERMISSION_DENIED`, Q30); 输出不加密.
 */
internal object CompressOperation {

    /** A checked `compress` request. zh-CN: 已检查的 `compress` 请求. */
    data class Request(val password: String?, val imageDpi: Int, val imageQuality: Int, val removeMetadata: Boolean, val objectStreams: Boolean)

    /** What [compress] changed before writing. zh-CN: [compress] 在写出之前所做的改动. */
    class Changes(val images: ImageCompression.Summary, val streams: StreamCleanup.Summary)

    fun request(options: PdfOptions, inputCount: Int): Request {
        if (inputCount != 1) throw PdfFailure.invalid("${PdfOps.COMPRESS} takes exactly one input, got $inputCount")
        return Request(
            password = options.password()?.takeIf { it.isNotEmpty() },
            imageDpi = options.int(PdfContract.KEY_IMAGE_DPI, PdfContract.MIN_IMAGE_DPI..PdfContract.MAX_IMAGE_DPI, PdfContract.DEFAULT_IMAGE_DPI),
            imageQuality = options.int(PdfContract.KEY_IMAGE_QUALITY, PdfContract.MIN_QUALITY..PdfContract.MAX_QUALITY, PdfContract.DEFAULT_IMAGE_QUALITY),
            removeMetadata = options.boolean(PdfContract.KEY_REMOVE_METADATA, false),
            objectStreams = options.boolean(PdfContract.KEY_OBJECT_STREAMS, PdfContract.DEFAULT_OBJECT_STREAMS),
        )
    }

    /**
     * Changes [document] as [request] asks, before writing; [progress] gets the current page of `processing`.
     * zh-CN: 按 [request] 在写出之前修改 [document]; [progress] 得到 `processing` 阶段的当前页.
     */
    fun compress(document: PDDocument, request: Request, codec: ImageCodec, stop: StopSignal, progress: (Int) -> Unit = {}): Changes {
        val uses = ImageUses.collect(document, stop)
        val images = ImageCompression.apply(uses, request.imageDpi, request.imageQuality, codec, stop, progress)
        val streams = StreamCleanup.apply(document, request.removeMetadata, stop)
        return Changes(images, streams)
    }

    /**
     * The warning of an output of [sizeBytes] that is not smaller than the source of [sourceBytes] (D57), or null.
     * zh-CN: 输出 [sizeBytes] 字节不比源文件 [sourceBytes] 字节小时的警告 (D57), 否则为 null.
     */
    fun sizeWarning(sizeBytes: Long, sourceBytes: Long): PdfWarning? = if (sizeBytes < sourceBytes) null else PdfWarning(
        "the output ($sizeBytes bytes) is not smaller than the source ($sourceBytes bytes); it was written anyway, compare sizeBytes with sourceBytes",
        PdfWarningCodes.OUTPUT_NOT_SMALLER,
        mapOf(PdfWarningCodes.ARG_SIZE_BYTES to sizeBytes, PdfWarningCodes.ARG_SOURCE_BYTES to sourceBytes),
    )

    fun write(request: Request, input: ParcelFileDescriptor, directory: File, operation: Operations.Operation, codec: ImageCodec = AndroidImageCodec): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val source = DescriptorSource.of(input)
        val sourceBytes = source.length
        return PdfLoader.load(source, request.password, directory).use { loaded ->
            val document = loaded.document
            PdfWriting.checkWritable(document, "the source")
            val pageCount = document.numberOfPages
            operation.progress(PdfContract.STAGE_PROCESSING, 0, pageCount)
            val changes = compress(document, request, codec, operation.stop) { page -> operation.progress(PdfContract.STAGE_PROCESSING, page, pageCount) }
            operation.progress(PdfContract.STAGE_PROCESSING, pageCount, pageCount)
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val written = CompactWriting.write(
                document,
                FileOutputStream(operation.outputs.single().fileDescriptor),
                directory,
                operation.stop,
                objectStreams = request.objectStreams,
                onVerifying = { operation.progress(PdfContract.STAGE_VERIFYING, 0, 1) },
                onlyProducer = request.removeMetadata,
            )
            val warnings = changes.images.warnings + written.warnings + listOfNotNull(sizeWarning(written.sizeBytes, sourceBytes))
            buildMap {
                put(PdfContract.KEY_PAGE_COUNT, pageCount)
                put(PdfContract.KEY_SIZE_BYTES, written.sizeBytes)
                put(PdfContract.KEY_SOURCE_BYTES, sourceBytes)
                put(PdfContract.KEY_IMAGES_TOTAL, changes.images.total)
                put(PdfContract.KEY_IMAGES_RECOMPRESSED, changes.images.recompressed)
                put(PdfContract.KEY_IMAGES_SKIPPED, changes.images.skippedCount)
                put(PdfContract.KEY_OBJECT_STREAMS, written.objectStreams)
                if (warnings.isNotEmpty()) put(PdfContract.KEY_WARNINGS, warnings)
            }
        }
    }

}

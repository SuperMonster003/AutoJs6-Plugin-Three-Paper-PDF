package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.OperatorProcessor
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDInlineImage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.BitmapTransport
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.CountingOutputStream
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.IOException
import java.util.IdentityHashMap

/**
 * `planImages` and `extractImage` (roadmap D28, D33): the images of a page are those its content stream draws, in the
 * order first drawn: image XObjects (each stream once per page, also inside forms, at most [MAX_FORM_DEPTH] forms deep)
 * and inline images (each occurrence). An image whose last filter is `DCTDecode` is `jpeg` and written as stored (the
 * filters before it decoded); every other image is `png`, decoded by PDFBox and encoded losslessly. Images above
 * `MAX_RENDER_PIXELS` end with `LIMIT_EXCEEDED`, encodings PDFBox-Android cannot decode (JPEG 2000) with `UNSUPPORTED`.
 * zh-CN: `planImages` 与 `extractImage` (路线图 D28, D33): 页面的图片是其内容流绘制的图片, 按首次绘制的顺序: 图片 XObject (每个流
 * 每页一次, 也包括表单内的, 至多 [MAX_FORM_DEPTH] 层表单) 与内联图片 (每次出现). 最后一个过滤器为 `DCTDecode` 的图片为 `jpeg`,
 * 原样写出 (先解开它之前的过滤器); 其他图片为 `png`, 由 PDFBox 解码后无损编码. 超过 `MAX_RENDER_PIXELS` 的图片报
 * `LIMIT_EXCEEDED`, PDFBox-Android 不能解码的编码 (JPEG 2000) 报 `UNSUPPORTED`.
 */
internal object PageImages {

    const val MAX_FORM_DEPTH = 25

    private val JPEG_FILTERS = setOf(COSName.DCT_DECODE.name, COSName.DCT_DECODE_ABBREVIATION.name)

    fun plan(document: PDDocument, options: PdfOptions, stop: StopSignal, budgetChars: Int = JsonItems.BUDGET_CHARS): Map<String, Any?> {
        val pages = options.pages(document.numberOfPages)
        val offset = options.int(PdfContract.KEY_OFFSET, 0..Int.MAX_VALUE, 0)
        val limit = options.int(PdfContract.KEY_LIMIT, 0..Int.MAX_VALUE, 0).let { if (it == 0) PdfContract.DEFAULT_PLAN_LIMIT else it.coerceAtMost(PdfContract.MAX_PLAN_ITEMS) }
        val hits = HitCollector(offset, limit, budgetChars)
        for (number in pages) {
            stop.checkStop()
            var index = 0
            val more = walk(document.getPage(number - 1), stop) { image ->
                val current = index++
                hits.accept {
                    JSONObject()
                        .put(PdfContract.FIELD_PAGE, number)
                        .put(PdfContract.FIELD_INDEX, current)
                        .put(PdfContract.FIELD_WIDTH, image.width)
                        .put(PdfContract.FIELD_HEIGHT, image.height)
                        .put(PdfContract.FIELD_FORMAT, formatOf(image))
                }
            }
            if (!more) break
        }
        return hits.answer()
    }

    /**
     * `extractImage`: image `index` (from 0) of `page` into [output]; answers `format`, `width`, `height`, `sizeBytes`.
     * zh-CN: `extractImage`: 把 `page` 的第 `index` 张图片 (从 0 起) 写入 [output]; 返回 `format`, `width`, `height`, `sizeBytes`.
     */
    fun extract(document: PDDocument, options: PdfOptions, output: ParcelFileDescriptor?, stop: StopSignal): Map<String, Any?> {
        val number = options.page(document.numberOfPages)
        val wanted = options.int(PdfContract.KEY_INDEX) ?: throw PdfFailure.invalid("extractImage needs ${PdfContract.KEY_INDEX}")
        if (wanted < 0) throw PdfFailure.invalid("${PdfContract.KEY_INDEX} $wanted is below 0")
        val destination = output ?: throw PdfFailure.invalid("extractImage needs an output")
        var found: PDImage? = null
        var count = 0
        walk(document.getPage(number - 1), stop) { image ->
            if (count++ == wanted) {
                found = image
                false
            } else {
                true
            }
        }
        val image = found ?: throw PdfFailure.invalid("page $number has $count image${if (count == 1) "" else "s"}, so there is no image ${PdfContract.KEY_INDEX} $wanted")
        val format = formatOf(image)
        val sizeBytes = if (format == PdfContract.FORMAT_JPEG) copyJpeg(image, destination, stop) else encodePng(image, destination, stop)
        return mapOf(
            PdfContract.KEY_FORMAT to format,
            PdfContract.KEY_WIDTH to image.width,
            PdfContract.KEY_HEIGHT to image.height,
            PdfContract.KEY_SIZE_BYTES to sizeBytes,
        )
    }

    internal fun formatOf(image: PDImage): String =
        if (filtersOf(image).lastOrNull() in JPEG_FILTERS) PdfContract.FORMAT_JPEG else PdfContract.FORMAT_PNG

    /** The filter names of [image], in decoding order. zh-CN: [image] 的过滤器名, 按解码顺序. */
    internal fun filtersOf(image: PDImage): List<String> = when (image) {
        is PDImageXObject -> image.stream.filters?.map { it.name }.orEmpty()
        is PDInlineImage -> image.filters.orEmpty()
        else -> emptyList()
    }

    private fun copyJpeg(image: PDImage, output: ParcelFileDescriptor, stop: StopSignal): Long {
        val stream = CountingOutputStream(FileOutputStream(output.fileDescriptor), Long.MAX_VALUE, stop)
        try {
            image.createInputStream(JPEG_FILTERS.toList()).use { it.copyTo(stream) }
            stream.flush()
            Operations.flush(output)
            return stream.count
        } catch (e: Throwable) {
            Operations.truncate(output)
            throw stream.failure ?: if (e is IOException) PdfFailure(PdfErrorCodes.INVALID_PDF, "the image stream cannot be read: ${e.message}") else e
        }
    }

    private fun encodePng(image: PDImage, output: ParcelFileDescriptor, stop: StopSignal): Long {
        if (image.width.toLong() * image.height.toLong() > PdfContract.MAX_RENDER_PIXELS) {
            throw PdfFailure.limitExceeded("the image of ${image.width} x ${image.height} pixels exceeds ${PdfContract.MAX_RENDER_PIXELS} pixels")
        }
        val bitmap: Bitmap = try {
            image.image ?: throw PdfFailure.unsupported("PDFBox-Android cannot decode this image (filters ${filtersOf(image)})")
        } catch (e: PdfFailure) {
            throw e
        } catch (e: Exception) {
            throw PdfFailure.unsupported("PDFBox-Android cannot decode this image (filters ${filtersOf(image)}): ${e.javaClass.simpleName}: ${e.message}")
        } catch (e: NoClassDefFoundError) {
            // JPEG 2000 needs jp2-android, which the plugin does not ship (proguard-rules.pro).
            // zh-CN: JPEG 2000 需要插件不打包的 jp2-android (见 proguard-rules.pro).
            throw PdfFailure.unsupported("PDFBox-Android cannot decode this image (filters ${filtersOf(image)})")
        }
        try {
            return BitmapTransport.encode(bitmap, Bitmap.CompressFormat.PNG, PdfContract.MAX_QUALITY, output, Long.MAX_VALUE, stop)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Walks the content of [page], handing each image to [visit] until it answers false; answers whether the walk ran to
     * the end.
     * zh-CN: 遍历 [page] 的内容, 把每张图片交给 [visit], 直到其返回 false; 返回遍历是否走到了末尾.
     */
    internal fun walk(page: PDPage, stop: StopSignal, visit: (PDImage) -> Boolean): Boolean {
        val walker = ImageWalker(stop, visit)
        try {
            walker.processPage(page)
        } catch (e: Finished) {
            return false
        } catch (e: IOException) {
            throw PdfFailure(PdfErrorCodes.INVALID_PDF, "the content of the page cannot be read: ${e.message}")
        }
        return true
    }

    /** Thrown to end a walk early. zh-CN: 提前结束遍历时抛出. */
    private class Finished : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private class ImageWalker(private val stop: StopSignal, private val visit: (PDImage) -> Boolean) : PDFStreamEngine() {

        private val seen = IdentityHashMap<COSBase, Unit>()
        private var depth = 0
        private var operators = 0

        init {
            addOperator(DrawObject())
            addOperator(InlineImage())
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (++operators % STOP_CHECK_EVERY == 0) stop.checkStop()
            super.processOperator(operator, operands)
        }

        fun offer(image: PDImage) {
            if (!visit(image)) throw Finished()
        }

        inner class DrawObject : OperatorProcessor() {
            override fun getName() = "Do"

            override fun process(operator: Operator, operands: MutableList<COSBase>) {
                val name = operands.firstOrNull() as? COSName ?: return
                val xObject = runCatching { context.resources?.getXObject(name) }.getOrNull() ?: return
                when (xObject) {
                    is PDImageXObject -> if (seen.put(xObject.cosObject, Unit) == null) offer(xObject)
                    is PDFormXObject -> {
                        if (depth >= MAX_FORM_DEPTH) return
                        depth++
                        try {
                            if (xObject is PDTransparencyGroup) context.showTransparencyGroup(xObject) else context.showForm(xObject)
                        } finally {
                            depth--
                        }
                    }
                }
            }
        }

        inner class InlineImage : OperatorProcessor() {
            override fun getName() = "BI"

            override fun process(operator: Operator, operands: MutableList<COSBase>) {
                val parameters = operator.imageParameters ?: return
                val data = operator.imageData ?: return
                offer(PDInlineImage(parameters, data, context.resources))
            }
        }

        companion object {
            const val STOP_CHECK_EVERY = 256
        }
    }

}

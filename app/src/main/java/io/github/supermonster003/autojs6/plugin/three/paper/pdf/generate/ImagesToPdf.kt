package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import java.io.ByteArrayInputStream
import java.io.File

/**
 * `fromImages` (roadmap D18, D33): one page per image, in input order. A JPEG is embedded as stored (`DCTDecode`, its
 * EXIF orientation applied by the drawing matrix, so nothing is re-encoded); any other image `BitmapFactory` decodes is
 * encoded as JPEG at `quality` (default 90) or losslessly (Flate) for `quality` `lossless`. Images above
 * `MAX_RENDER_PIXELS` are decoded subsampled, with a warning; the page geometry still follows the full size.
 * zh-CN: `fromImages` (路线图 D18, D33): 每张图片一页, 顺序同输入. JPEG 原样嵌入 (`DCTDecode`, EXIF 方向由绘制矩阵实现, 不重新
 * 编码); 其他 `BitmapFactory` 能解码的图片按 `quality` (默认 90) 编码为 JPEG, `quality` 为 `lossless` 时无损 (Flate) 编码. 超过
 * `MAX_RENDER_PIXELS` 的图片降采样解码并给出警告, 页面几何仍按原尺寸计算.
 */
internal object ImagesToPdf {

    /** Bytes of one image input. zh-CN: 单个图片输入的字节数上限. */
    const val MAX_IMAGE_INPUT_BYTES = 64L * 1024 * 1024

    /** A checked `fromImages` request; [quality] null means lossless. zh-CN: 已检查的 `fromImages` 请求; [quality] 为 null 表示无损. */
    class Request(
        val pageSize: PageLayout.PageSize,
        val fit: String,
        val orientation: String,
        val margin: Float,
        val quality: Int?,
    )

    fun request(options: PdfOptions, inputCount: Int): Request {
        if (inputCount < 1) throw PdfFailure.invalid("${PdfOps.FROM_IMAGES} needs at least one image")
        if (inputCount > PdfContract.MAX_RUN_INPUTS) throw PdfFailure.limitExceeded("$inputCount inputs exceed ${PdfContract.MAX_RUN_INPUTS}")
        val quality = when (val value = options.value(PdfContract.KEY_QUALITY)) {
            PdfContract.QUALITY_LOSSLESS -> null
            is String -> throw PdfFailure.invalid("${PdfContract.KEY_QUALITY} \"$value\" is neither ${PdfContract.MIN_QUALITY} to ${PdfContract.MAX_QUALITY} nor \"${PdfContract.QUALITY_LOSSLESS}\"")
            else -> options.quality()
        }
        val margin = PageLayout.margin(options, PageLayout.DEFAULT_IMAGE_MARGIN)
        val pageSize = PageLayout.pageSize(options) ?: PageLayout.PageSize.Fit
        if (pageSize is PageLayout.PageSize.Fixed) PageLayout.contentBox(pageSize, margin)
        return Request(
            pageSize = pageSize,
            fit = checkNotNull(options.choice(PdfContract.KEY_FIT, PdfContract.FITS, PdfContract.FIT_CONTAIN)),
            orientation = checkNotNull(options.choice(PdfContract.KEY_ORIENTATION, PdfContract.ORIENTATIONS, PdfContract.ORIENTATION_AUTO)),
            margin = margin,
            quality = quality,
        )
    }

    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        val warnings = ArrayList<String>()
        PDDocument(MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory)).use { document ->
            inputs.forEachIndexed { index, input ->
                operation.stop.checkStop()
                operation.progress(PdfContract.STAGE_PROCESSING, index, inputs.size)
                val bytes = InputBytes.read(input, MAX_IMAGE_INPUT_BYTES, "image ${index + 1}")
                addPage(document, bytes, request, index + 1, warnings)
            }
            return GeneratedDocuments.write(document, title = null, operation, warnings)
        }
    }

    /** Adds the page of image [number] (from 1). zh-CN: 添加第 [number] 张图片 (从 1 起) 的页面. */
    internal fun addPage(document: PDDocument, bytes: ByteArray, request: Request, number: Int, warnings: MutableList<String>) {
        val jpeg = JpegInfo.read(bytes)?.takeIf { it.embeddable }
        val source = if (jpeg != null) embedJpeg(document, bytes, jpeg) else decode(document, bytes, request.quality, number, warnings)
        val (displayWidth, displayHeight) = if (source.orientation in 5..8) source.height to source.width else source.width to source.height
        val placement = PageLayout.place(displayWidth, displayHeight, request.pageSize, request.fit, request.orientation, request.margin)
        val page = PDPage(PDRectangle(placement.pageWidth, placement.pageHeight))
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            placement.clip?.let { box ->
                stream.saveGraphicsState()
                stream.addRect(box.x, box.y, box.width, box.height)
                stream.clip()
            }
            stream.drawImage(source.image, orientationMatrix(source.orientation, placement.x, placement.y, placement.width, placement.height))
            if (placement.clip != null) stream.restoreGraphicsState()
        }
    }

    /** An image ready to draw, with the size and EXIF orientation of the source. zh-CN: 可绘制的图片, 以及源图的尺寸与 EXIF 方向. */
    private class Source(val image: PDImageXObject, val width: Int, val height: Int, val orientation: Int)

    private fun embedJpeg(document: PDDocument, bytes: ByteArray, info: JpegInfo): Source {
        val colorSpace = if (info.components == 1) PDDeviceGray.INSTANCE else PDDeviceRGB.INSTANCE
        val image = PDImageXObject(document, ByteArrayInputStream(bytes), COSName.DCT_DECODE, info.width, info.height, 8, colorSpace)
        if (info.components == 4) {
            // PDFBox-Android has no DeviceCMYK class; the name is enough for PDF readers. CMYK written by Adobe software
            // stores inverted values. zh-CN: PDFBox-Android 没有 DeviceCMYK 类, 对 PDF 阅读器而言名称即可. Adobe 软件写出的 CMYK
            // 存储反相值.
            image.cosObject.setItem(COSName.COLORSPACE, COSName.DEVICECMYK)
            if (info.adobe) {
                image.cosObject.setItem(COSName.DECODE, COSArray().apply { repeat(4) { add(COSInteger.ONE); add(COSInteger.ZERO) } })
            }
        }
        return Source(image, info.width, info.height, info.orientation)
    }

    private fun decode(document: PDDocument, bytes: ByteArray, quality: Int?, number: Int, warnings: MutableList<String>): Source {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw notAnImage(number)
        val sample = sampleSize(width, height)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw notAnImage(number)
        try {
            if (sample > 1) warnings += "image $number of $width x $height was decoded at ${bitmap.width} x ${bitmap.height} (above ${PdfContract.MAX_RENDER_PIXELS} pixels)"
            dropOpaqueAlpha(bitmap)
            val image = if (quality == null) LosslessFactory.createFromImage(document, bitmap) else JPEGFactory.createFromImage(document, bitmap, quality / 100f)
            return Source(image, width, height, decodedOrientation(bytes))
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Clears the alpha flag of [bitmap] when every pixel is opaque (an RGBA PNG such as a screenshot), so PDFBox writes
     * no soft mask for it; on the API 33 emulator 20 PNG images of 1600 x 1200 went from 5.4 s to 3.1 s.
     * zh-CN: [bitmap] 的像素全部不透明时 (如截图这类 RGBA PNG) 清除其 alpha 标记, 使 PDFBox 不为它写软蒙版; 在模拟器上
     * 20 张 1600 x 1200 的 PNG 图片在 API 33 模拟器上由 5.4 s 降到 3.1 s.
     */
    internal fun dropOpaqueAlpha(bitmap: Bitmap) {
        if (!bitmap.hasAlpha()) return
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) if (pixel ushr 24 != 0xFF) return
        }
        bitmap.setHasAlpha(false)
    }

    /** The smallest power of two that brings [width] x [height] to `MAX_RENDER_PIXELS`. zh-CN: 使 [width] x [height] 不超过 `MAX_RENDER_PIXELS` 的最小 2 的幂. */
    internal fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while ((width.toLong() / sample) * (height.toLong() / sample) > PdfContract.MAX_RENDER_PIXELS) sample *= 2
        return sample
    }

    /**
     * EXIF orientation of a decoded (non-JPEG) image: `ExifInterface` reads HEIF and WebP from API 28 and PNG from API
     * 30; 1 elsewhere. The platform class is enough from API 28; androidx.exifinterface would only add a dependency.
     * zh-CN: 解码路径 (非 JPEG) 图片的 EXIF 方向: `ExifInterface` 自 API 28 起读取 HEIF 与 WebP, 自 API 30 起读取 PNG; 其他情况为 1.
     * 自 API 28 起平台类已足够; androidx.exifinterface 只会多一个依赖.
     */
    @SuppressLint("ExifInterface")
    private fun decodedOrientation(bytes: ByteArray): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 1
        return runCatching {
            android.media.ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1).takeIf { it in 1..8 } ?: 1
        }.getOrDefault(1)
    }

    private fun notAnImage(number: Int) = PdfFailure(PdfErrorCodes.INVALID_PDF, "image $number is not an image the plugin can decode")

    /**
     * The matrix that draws an image stored with EXIF [orientation] upright into the box [x], [y], [width] x [height]
     * (sizes as displayed). The image space of PDF is the unit square with the first stored row at the top.
     * zh-CN: 把以 EXIF [orientation] 存储的图片正向画进方框 [x], [y], [width] x [height] (显示尺寸) 的矩阵. PDF 的图像空间是单位正方形,
     * 存储的第一行在上方.
     */
    fun orientationMatrix(orientation: Int, x: Float, y: Float, width: Float, height: Float): Matrix = when (orientation) {
        2 -> Matrix(-width, 0f, 0f, height, x + width, y)
        3 -> Matrix(-width, 0f, 0f, -height, x + width, y + height)
        4 -> Matrix(width, 0f, 0f, -height, x, y + height)
        5 -> Matrix(0f, -height, -width, 0f, x + width, y + height)
        6 -> Matrix(0f, -height, width, 0f, x, y + height)
        7 -> Matrix(0f, height, width, 0f, x, y)
        8 -> Matrix(0f, height, -width, 0f, x + width, y)
        else -> Matrix(width, 0f, 0f, height, x, y)
    }

}

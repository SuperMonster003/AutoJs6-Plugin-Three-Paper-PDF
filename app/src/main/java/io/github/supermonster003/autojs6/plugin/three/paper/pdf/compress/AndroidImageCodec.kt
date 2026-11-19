package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.JpegData
import org.autojs.plugin.pdf.api.PdfContract
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * [ImageCodec] on the platform: a JPEG stream (`DCTDecode` last) is decoded by `BitmapFactory` with the largest power
 * of two `inSampleSize` that still leaves at least twice the target size (libjpeg scales while decoding, so a large
 * scan never needs its full size in memory); other samples are decoded by PDFBox-Android (`/Decode` applied) with an
 * integer subsampling of the same kind, or at full size through `getOpaqueImage` when a soft mask exists (`getImage`
 * would premultiply it in). No decoded bitmap exceeds `MAX_RENDER_PIXELS` ([ImageCompression.TooLarge] otherwise). The bitmap is then halved with bilinear filtering while it is at least twice the target, which
 * averages 2 x 2 blocks, scaled once more to the exact size and encoded by `Bitmap.compress` as a JPEG.
 * zh-CN: 平台上的 [ImageCodec]: JPEG 流 (最后一个过滤器为 `DCTDecode`) 由 `BitmapFactory` 解码, `inSampleSize` 取仍保留至少两倍目标
 * 尺寸的最大 2 的幂 (libjpeg 在解码时缩放, 大扫描件不必以原尺寸放进内存); 其他采样由 PDFBox-Android 解码 (应用 `/Decode`), 同样
 * 取整数子采样, 带软蒙版时经 `getOpaqueImage` 以原尺寸解码 (`getImage` 会把蒙版预乘进去). 解码出的位图都不超过
 * `MAX_RENDER_PIXELS` (否则为 [ImageCompression.TooLarge]). 之后在不小于目标两倍时用
 * 双线性过滤逐次减半 (相当于 2 x 2 块平均), 再缩放一次到精确尺寸, 由 `Bitmap.compress` 编码为 JPEG.
 */
internal object AndroidImageCodec : ImageCodec {

    private val JPEG_FILTERS = listOf(COSName.DCT_DECODE.name, COSName.DCT_DECODE_ABBREVIATION.name)

    override fun jpeg(image: PDImageXObject, width: Int, height: Int, quality: Int): ByteArray? {
        val decoded = decode(image, width, height) ?: return null
        val scaled = scale(decoded, width, height)
        try {
            val output = ByteArrayOutputStream(max(1024, width * height / 4))
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
            return output.toByteArray()
        } finally {
            scaled.recycle()
        }
    }

    private fun decode(image: PDImageXObject, width: Int, height: Int): Bitmap? {
        val ratio = max(1, min(image.width / width, image.height / height))
        if (ImageCompression.filtersOf(image.cosObject).lastOrNull() in JPEG_FILTERS) {
            val bytes = image.createInputStream(JPEG_FILTERS).use { it.readBytes() }
            val jpeg = JpegData.of(bytes) ?: return null
            if (jpeg.width != image.width || jpeg.height != image.height) return null
            var sample = 1
            while (sample * 2 <= ratio / 2) sample *= 2
            while (pixels(jpeg.width, jpeg.height, sample) > PdfContract.MAX_RENDER_PIXELS) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
        if (image.cosObject.getDictionaryObject(COSName.SMASK) != null) {
            // getImage would apply the soft mask; the opaque image has no subsampling. zh-CN: getImage 会应用软蒙版; 不透明图片不能子采样.
            if (pixels(image.width, image.height, 1) > PdfContract.MAX_RENDER_PIXELS) throw ImageCompression.TooLarge()
            return image.opaqueImage
        }
        var subsampling = max(1, ratio / 2)
        while (pixels(image.width, image.height, subsampling) > PdfContract.MAX_RENDER_PIXELS) subsampling++
        return image.getImage(null, subsampling)
    }

    private fun pixels(width: Int, height: Int, step: Int): Long = ((width + step - 1) / step).toLong() * ((height + step - 1) / step)

    /** [bitmap] scaled to [width] x [height]; [bitmap] is recycled unless it is the answer. zh-CN: 缩放后的位图; [bitmap] 除非即为结果否则被回收. */
    private fun scale(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        var current = bitmap
        fun replace(next: Bitmap) {
            if (next !== current) {
                current.recycle()
                current = next
            }
        }
        while (current.width >= width * 2 && current.height >= height * 2) {
            replace(Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true))
        }
        if (current.width != width || current.height != height) replace(Bitmap.createScaledBitmap(current, width, height, true))
        return current
    }

}

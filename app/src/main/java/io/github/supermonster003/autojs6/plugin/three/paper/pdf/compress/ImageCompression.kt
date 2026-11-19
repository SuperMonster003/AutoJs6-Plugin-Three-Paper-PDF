package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.JpegData
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.IdentityHashMap
import java.util.zip.Deflater
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes an image XObject without its masks, scales it to a size and encodes it as a baseline JPEG; null when the
 * image cannot be decoded. The Android implementation is [AndroidImageCodec]; JVM tests give their own.
 * zh-CN: 把图片 XObject (不含蒙版) 解码, 缩放到给定尺寸并编码为基线 JPEG; 无法解码时返回 null. Android 实现是 [AndroidImageCodec];
 * JVM 测试自带实现.
 */
internal fun interface ImageCodec {

    fun jpeg(image: PDImageXObject, width: Int, height: Int, quality: Int): ByteArray?

}

/**
 * The image part of `compress` (roadmap D64): every image XObject the pages draw ([ImageUses]) whose effective
 * resolution, pixels over its largest displayed size, is above `imageDpi` on both axes is resampled to `imageDpi`
 * (the axis with fewer pixels per inch reaches it exactly, the other keeps at least it) and re-encoded as a JPEG of
 * `imageQuality`; its soft mask is scaled by the same factor and stays lossless (8-bit grey, Flate). The stream is
 * changed in place, so every page and form that draws it sees the new data; when the JPEG and the new mask together
 * are not smaller than the old streams, the image is kept. Left alone and counted as skipped: images not above
 * `imageDpi`, 1-bit images (CCITT, JBIG2 and other 1-bit samples), stencil masks, images with a `/Mask` or a soft mask
 * that is shared, has a `/Matte`, is not 8-bit or is lossy, sample depths other than 8, colour spaces other than
 * `DeviceGray` and `DeviceRGB` (for JPEG streams also calibrated grey or RGB and ICC based with 1 or 3 components;
 * CMYK, Lab, indexed, Separation and DeviceN keep their colours), JPEG streams with a `/Decode` array, external
 * streams, and images that cannot be decoded (JPEG 2000, a damaged stream; each also gives a warning) or are too
 * large to decode (a warning as well). The new JPEG is RGB (`DeviceRGB`, the platform encoder
 * writes three components, grey inputs included) unless it carries one component.
 * zh-CN: `compress` 的图片部分 (路线图 D64): 页面绘制的每个图片 XObject ([ImageUses]) 中, 有效分辨率 (像素数 / 最大显示尺寸) 在两个
 * 方向上都高于 `imageDpi` 的, 重采样到 `imageDpi` (每英寸像素较少的方向恰好达到, 另一方向不低于它) 并以 `imageQuality` 重编码为
 * JPEG; 其软蒙版按同一比例缩放并保持无损 (8 位灰度, Flate). 流被原地修改, 因此绘制它的每个页面与表单都看到新数据; JPEG 加上新
 * 蒙版不比原来的流小时保留原图. 以下图片不处理并计为跳过: 不高于 `imageDpi` 的, 1 位图 (CCITT, JBIG2 及其他 1 位采样), 模板蒙版,
 * 带 `/Mask` 的, 软蒙版被共用, 带 `/Matte`, 不是 8 位或为有损编码的, 采样位深不是 8 的, 颜色空间不是 `DeviceGray` 与 `DeviceRGB`
 * 的 (JPEG 流另外接受校准灰度或 RGB 与 1 或 3 分量的 ICC; CMYK, Lab, 索引色, Separation, DeviceN 保留原色), 带 `/Decode` 数组的
 * JPEG 流, 外部流, 以及无法解码的 (JPEG 2000, 损坏的流; 另各给一条警告) 或太大无法解码的 (同样给警告). 新的 JPEG 为 RGB (`DeviceRGB`, 平台编码器写三个分量, 灰度输入
 * 也是如此), 只有一个分量时为灰度.
 */
internal object ImageCompression {

    /** Why an image was kept. zh-CN: 图片被保留的原因. */
    enum class Skip { NOT_ABOVE_DPI, ONE_BIT, STENCIL, MASKED, UNSUPPORTED, UNDECODABLE, TOO_LARGE, NOT_SMALLER }

    /** The counts of [apply] and its warnings. zh-CN: [apply] 的计数及其警告. */
    class Summary(val total: Int, val recompressed: Int, val skipped: Map<Skip, Int>, val warnings: List<String>) {

        val skippedCount: Int get() = skipped.values.sum()

    }

    /** Thrown by a codec when the image is too large to decode. zh-CN: 图片太大无法解码时由编码器抛出. */
    class TooLarge : RuntimeException("the image is too large to decode")

    /** A width and a height in pixels. zh-CN: 以像素计的宽与高. */
    data class Size(val width: Int, val height: Int)

    private const val POINTS_PER_INCH = 72.0

    private val ONE_BIT_FILTERS = setOf(COSName.CCITTFAX_DECODE.name, COSName.CCITTFAX_DECODE_ABBREVIATION.name, COSName.JBIG2_DECODE.name)

    private val LOSSY_FILTERS = setOf(COSName.DCT_DECODE.name, COSName.DCT_DECODE_ABBREVIATION.name, COSName.JPX_DECODE.name) + ONE_BIT_FILTERS

    private val JPEG_FILTERS = setOf(COSName.DCT_DECODE.name, COSName.DCT_DECODE_ABBREVIATION.name)

    private val MATTE = COSName.getPDFName("Matte")

    private val DECODED_LENGTH = COSName.getPDFName("DL")

    /**
     * Recompresses the images of [uses] with [dpi] and [quality] through [codec]; [progress] gets the page of the image
     * just handled (pages ascend, [uses] are in the order first drawn).
     * zh-CN: 经 [codec] 以 [dpi] 与 [quality] 重新压缩 [uses] 中的图片; [progress] 得到刚处理完的图片所在页 (页码递增, [uses] 按首次
     * 绘制的顺序).
     */
    fun apply(uses: List<ImageUses.Use>, dpi: Int, quality: Int, codec: ImageCodec, stop: StopSignal, progress: (Int) -> Unit = {}): Summary {
        val masks = IdentityHashMap<COSBase, Int>()
        uses.forEach { use -> softMaskOf(use.stream)?.let { masks[it] = (masks[it] ?: 0) + 1 } }
        val skipped = LinkedHashMap<Skip, Int>()
        var recompressed = 0
        for (use in uses) {
            stop.checkStop()
            val reason = recompress(use, dpi, quality, codec, stop) { mask -> (masks[mask] ?: 0) > 1 }
            if (reason == null) recompressed++ else skipped[reason] = (skipped[reason] ?: 0) + 1
            progress(use.page)
        }
        val warnings = ArrayList<String>()
        skipped[Skip.UNDECODABLE]?.let { warnings += "$it ${images(it)} cannot be decoded (for example JPEG 2000 or a damaged stream) and ${were(it)} kept as ${they(it)} ${were(it)}" }
        skipped[Skip.TOO_LARGE]?.let { warnings += "$it ${images(it)} ${are(it)} too large to decode on this device and ${were(it)} kept as ${they(it)} ${were(it)}" }
        return Summary(uses.size, recompressed, skipped, warnings)
    }

    private fun images(count: Int) = if (count == 1) "image" else "images"

    private fun were(count: Int) = if (count == 1) "was" else "were"

    private fun are(count: Int) = if (count == 1) "is" else "are"

    private fun they(count: Int) = if (count == 1) "it" else "they"

    private fun recompress(use: ImageUses.Use, dpi: Int, quality: Int, codec: ImageCodec, stop: StopSignal, shared: (COSStream) -> Boolean): Skip? {
        val image = use.image
        val stream = use.stream
        classify(image, shared)?.let { return it }
        val size = target(image.width, image.height, use.width, use.height, dpi) ?: return Skip.NOT_ABOVE_DPI
        val mask = softMaskOf(stream)
        val newMask = mask?.let { scaledMask(it, image, size, stop) ?: return Skip.MASKED }
        val jpeg = try {
            codec.jpeg(image, size.width, size.height, quality)
        } catch (_: OutOfMemoryError) {
            return Skip.TOO_LARGE
        } catch (_: TooLarge) {
            return Skip.TOO_LARGE
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: NoClassDefFoundError) {
            // JPEG 2000 needs jp2-android, which the plugin does not ship. zh-CN: JPEG 2000 需要插件不打包的 jp2-android.
            null
        } ?: return Skip.UNDECODABLE
        val header = JpegData.of(jpeg) ?: return Skip.UNDECODABLE
        if (header.width != size.width || header.height != size.height) return Skip.UNDECODABLE
        val before = lengthOf(stream) + (mask?.let(::lengthOf) ?: 0L)
        val after = jpeg.size.toLong() + (newMask?.data?.size ?: 0)
        if (after >= before) return Skip.NOT_SMALLER
        replaceWithJpeg(stream, jpeg, size, header.components)
        if (mask != null && newMask != null) replaceWithGrey(mask, newMask)
        return null
    }

    /**
     * The reason [image] is not considered at all, or null; [shared] tells whether a soft mask is used by several images.
     * zh-CN: [image] 完全不予处理的原因, 没有则为 null; [shared] 判断软蒙版是否被多张图片共用.
     */
    internal fun classify(image: PDImageXObject, shared: (COSStream) -> Boolean = { false }): Skip? {
        val stream = image.cosObject
        val filters = filtersOf(stream)
        if (image.isStencil) return Skip.STENCIL
        if (filters.any { it in ONE_BIT_FILTERS } || image.bitsPerComponent == 1) return Skip.ONE_BIT
        if (COSName.JPX_DECODE.name in filters) return Skip.UNDECODABLE
        if (stream.containsKey(COSName.F)) return Skip.UNSUPPORTED
        if (image.bitsPerComponent != 8) return Skip.UNSUPPORTED
        if (stream.getDictionaryObject(COSName.MASK) != null) return Skip.MASKED
        if (stream.getDictionaryObject(COSName.SMASK) != null) {
            val mask = softMaskOf(stream) ?: return Skip.MASKED
            if (shared(mask) || mask.containsKey(MATTE) || mask.containsKey(COSName.F) || mask.getDictionaryObject(COSName.DECODE) != null) return Skip.MASKED
            if (mask.getInt(COSName.BITS_PER_COMPONENT, 0) != 8) return Skip.MASKED
            if (filtersOf(mask).any { it in LOSSY_FILTERS }) return Skip.MASKED
            if (mask.getInt(COSName.WIDTH, 0) <= 0 || mask.getInt(COSName.HEIGHT, 0) <= 0) return Skip.MASKED
        }
        val jpeg = filters.lastOrNull() in JPEG_FILTERS
        if (jpeg && stream.getDictionaryObject(COSName.DECODE) != null) return Skip.UNSUPPORTED
        if (!supported(stream.getDictionaryObject(COSName.COLORSPACE), jpeg)) return Skip.UNSUPPORTED
        return null
    }

    /**
     * Whether the colour space [space] is one the codec decodes faithfully. PDFBox-Android 2.0.27.0 knows only
     * `DeviceGray` and `DeviceRGB` and silently reads every other space as RGB (CMYK included), so raw samples need one
     * of those two names; a JPEG ([jpeg]) is decoded by the platform from its own components, so calibrated grey or RGB
     * and ICC based spaces of 1 or 3 components are fine too.
     * zh-CN: 颜色空间 [space] 能否被编码器如实解码. PDFBox-Android 2.0.27.0 只认识 `DeviceGray` 与 `DeviceRGB`, 其他空间 (包括 CMYK)
     * 一律静默当作 RGB 读取, 因此原始采样必须是这两个名称之一; JPEG ([jpeg]) 由平台按其自身分量解码, 因此校准灰度或 RGB 以及 1 或 3
     * 分量的 ICC 空间也可以.
     */
    private fun supported(space: COSBase?, jpeg: Boolean): Boolean = when {
        space == COSName.DEVICEGRAY || space == COSName.DEVICERGB -> true
        !jpeg || space !is COSArray -> false
        else -> when (space.getObject(0)) {
            COSName.CALGRAY, COSName.CALRGB -> true
            COSName.ICCBASED -> (space.getObject(1) as? COSStream)?.getInt(COSName.N, 0) in setOf(1, 3)
            else -> false
        }
    }

    /**
     * The size an image of [pixelsWide] x [pixelsHigh] drawn at most [widthPoints] x [heightPoints] is resampled to at
     * [dpi], or null when it is not above [dpi] on both axes (nothing to gain) or never drawn with a size.
     * zh-CN: 最大绘制尺寸为 [widthPoints] x [heightPoints] 点的 [pixelsWide] x [pixelsHigh] 图片在 [dpi] 下重采样后的尺寸; 两个方向
     * 不都高于 [dpi] (没有收益) 或从未以非零尺寸绘制时为 null.
     */
    internal fun target(pixelsWide: Int, pixelsHigh: Int, widthPoints: Double, heightPoints: Double, dpi: Int): Size? {
        if (pixelsWide <= 0 || pixelsHigh <= 0 || !(widthPoints > 0.0) || !(heightPoints > 0.0)) return null
        val scale = max(dpi * widthPoints / (POINTS_PER_INCH * pixelsWide), dpi * heightPoints / (POINTS_PER_INCH * pixelsHigh))
        if (!(scale < 1.0)) return null
        val width = ceil(pixelsWide * scale - EPSILON).toInt().coerceIn(1, pixelsWide)
        val height = ceil(pixelsHigh * scale - EPSILON).toInt().coerceIn(1, pixelsHigh)
        if (width >= pixelsWide && height >= pixelsHigh) return null
        return Size(width, height)
    }

    private const val EPSILON = 1e-6

    /** A new soft mask: its size and Flate data. zh-CN: 新的软蒙版: 尺寸与 Flate 数据. */
    private class ScaledMask(val size: Size, val data: ByteArray)

    /**
     * The soft [mask] of [image] scaled by the factor that takes [image] to [size] (exactly [size] when both were the
     * same size), averaged by area, deflated; null when its samples are short.
     * zh-CN: 把 [image] 的软蒙版 [mask] 按 [image] 缩放到 [size] 的同一比例缩放 (两者尺寸相同时恰为 [size]), 按面积平均后 Flate
     * 压缩; 采样不足时为 null.
     */
    private fun scaledMask(mask: COSStream, image: PDImageXObject, size: Size, stop: StopSignal): ScaledMask? {
        val width = mask.getInt(COSName.WIDTH, 0)
        val height = mask.getInt(COSName.HEIGHT, 0)
        val target = if (width == image.width && height == image.height) {
            size
        } else {
            Size(
                ceil(width.toDouble() * size.width / image.width - EPSILON).toInt().coerceIn(1, width),
                ceil(height.toDouble() * size.height / image.height - EPSILON).toInt().coerceIn(1, height),
            )
        }
        val samples = try {
            readAtMost(mask, width.toLong() * height)
        } catch (_: IOException) {
            return null
        } ?: return null
        val scaled = resampleGrey(samples, width, height, target.width, target.height, stop)
        return ScaledMask(target, deflate(scaled))
    }

    /** The first [count] decoded bytes of [stream], or null when it holds fewer. zh-CN: [stream] 解码后的前 [count] 个字节; 不足时为 null. */
    private fun readAtMost(stream: COSStream, count: Long): ByteArray? {
        if (count > Int.MAX_VALUE) return null
        val bytes = ByteArray(count.toInt())
        stream.createInputStream().use { input ->
            var read = 0
            while (read < bytes.size) {
                val n = input.read(bytes, read, bytes.size - read)
                if (n < 0) return null
                read += n
            }
        }
        return bytes
    }

    /**
     * 8-bit grey [source] of [width] x [height] resampled to [newWidth] x [newHeight] (not larger) by area averaging:
     * every target pixel is the mean of the source area it covers, partial pixels weighted by their share.
     * zh-CN: 把 [width] x [height] 的 8 位灰度 [source] 按面积平均重采样为 [newWidth] x [newHeight] (不放大): 每个目标像素是它覆盖的
     * 源区域的均值, 部分覆盖的像素按所占比例加权.
     */
    internal fun resampleGrey(source: ByteArray, width: Int, height: Int, newWidth: Int, newHeight: Int, stop: StopSignal = StopSignal.NEVER): ByteArray {
        require(newWidth in 1..width && newHeight in 1..height) { "resampling only shrinks" }
        if (newWidth == width && newHeight == height) return source.copyOf(width * height)
        val rows = Weights.of(height, newHeight)
        val columns = Weights.of(width, newWidth)
        val sums = DoubleArray(width)
        val target = ByteArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            if (y and 0x3F == 0) stop.checkStop()
            sums.fill(0.0)
            for (k in rows.weights[y].indices) {
                val weight = rows.weights[y][k]
                val base = (rows.starts[y] + k) * width
                for (x in 0 until width) sums[x] += (source[base + x].toInt() and 0xFF) * weight
            }
            for (x in 0 until newWidth) {
                var value = 0.0
                val start = columns.starts[x]
                val weights = columns.weights[x]
                for (k in weights.indices) value += sums[start + k] * weights[k]
                target[y * newWidth + x] = value.roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return target
    }

    /** Area weights from [from] samples to [to] samples. zh-CN: 从 [from] 个采样到 [to] 个采样的面积权重. */
    private class Weights(val starts: IntArray, val weights: Array<DoubleArray>) {

        companion object {

            fun of(from: Int, to: Int): Weights {
                val scale = from.toDouble() / to
                val starts = IntArray(to)
                val weights = Array(to) { index ->
                    val low = index * scale
                    val high = min((index + 1) * scale, from.toDouble())
                    val first = floor(low).toInt()
                    val last = (ceil(high).toInt() - 1).coerceIn(first, from - 1)
                    starts[index] = first
                    DoubleArray(last - first + 1) { k ->
                        val cell = first + k
                        (min(high, cell + 1.0) - max(low, cell.toDouble())).coerceAtLeast(0.0) / (high - low)
                    }
                }
                return Weights(starts, weights)
            }

        }

    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val output = ByteArrayOutputStream(max(64, bytes.size / 4))
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun replaceWithJpeg(stream: COSStream, jpeg: ByteArray, size: Size, components: Int) {
        stream.createRawOutputStream().use { it.write(jpeg) }
        stream.setItem(COSName.FILTER, COSName.DCT_DECODE)
        clearCoding(stream)
        stream.removeItem(COSName.DECODE)
        stream.setInt(COSName.WIDTH, size.width)
        stream.setInt(COSName.HEIGHT, size.height)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.COLORSPACE, if (components == 1) COSName.DEVICEGRAY else COSName.DEVICERGB)
    }

    private fun replaceWithGrey(stream: COSStream, mask: ScaledMask) {
        stream.createRawOutputStream().use { it.write(mask.data) }
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        clearCoding(stream)
        stream.setInt(COSName.WIDTH, mask.size.width)
        stream.setInt(COSName.HEIGHT, mask.size.height)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.COLORSPACE, COSName.DEVICEGRAY)
    }

    /** Drops what described the old encoding. zh-CN: 删除描述旧编码的条目. */
    private fun clearCoding(stream: COSStream) {
        stream.removeItem(COSName.DECODE_PARMS)
        stream.removeItem(DECODED_LENGTH)
    }

    /** The soft mask stream of [image], if it is a stream. zh-CN: [image] 的软蒙版流 (若是流). */
    private fun softMaskOf(image: COSStream): COSStream? = image.getDictionaryObject(COSName.SMASK) as? COSStream

    /** Filter names of [stream] in decoding order. zh-CN: [stream] 的过滤器名, 按解码顺序. */
    internal fun filtersOf(stream: COSStream): List<String> = when (val filters = stream.getDictionaryObject(COSName.FILTER)) {
        is COSName -> listOf(filters.name)
        is COSArray -> (0 until filters.size()).mapNotNull { (filters.getObject(it) as? COSName)?.name }
        else -> emptyList()
    }

    /** The encoded length of [stream]. zh-CN: [stream] 编码后的长度. */
    private fun lengthOf(stream: COSStream): Long {
        val declared = stream.length
        if (declared > 0) return declared
        return stream.createRawInputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
            }
            total
        }
    }

}

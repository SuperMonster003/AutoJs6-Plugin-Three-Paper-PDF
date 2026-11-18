package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The bytes of a baseline or progressive JPEG with 8-bit samples and one (grey) or three (YCbCr or RGB) components,
 * as found in its frame header; [of] answers `null` for anything else (CMYK, 12-bit, a broken header).
 * Identity equality on purpose: an [ImageStamp] holding it stays equal only to stamps of the same image input.
 * zh-CN: 8 位采样, 1 个 (灰度) 或 3 个 (YCbCr 或 RGB) 分量的基线或渐进 JPEG 的字节, 尺寸与分量数取自帧头; 其他情况 (CMYK, 12 位,
 * 损坏的帧头) [of] 返回 `null`. 有意使用同一性相等: 持有它的 [ImageStamp] 只与同一图片输入的印章相等.
 */
internal class JpegData private constructor(val bytes: ByteArray, val width: Int, val height: Int, val components: Int) {

    companion object {

        fun of(bytes: ByteArray): JpegData? {
            if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
            var i = 2
            while (i + 3 < bytes.size) {
                if (bytes[i] != 0xFF.toByte()) return null
                val marker = bytes[i + 1].toInt() and 0xFF
                when {
                    // Fill bytes before a marker. zh-CN: 标记之前的填充字节.
                    marker == 0xFF -> i++
                    // Markers without a length: TEM, RST0 - RST7. zh-CN: 没有长度的标记: TEM, RST0 - RST7.
                    marker == 0x01 || marker in 0xD0..0xD7 -> i += 2
                    // SOI again, EOI or SOS before a frame header. zh-CN: 帧头之前出现 SOI, EOI 或 SOS.
                    marker == 0xD8 || marker == 0xD9 || marker == 0xDA -> return null
                    else -> {
                        val length = u16(bytes, i + 2)
                        if (length < 2) return null
                        // SOF0 - SOF15 except DHT (C4), JPG (C8) and DAC (CC). zh-CN: SOF0 - SOF15, 除去 DHT (C4), JPG (C8) 与 DAC (CC).
                        if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                            if (i + 9 >= bytes.size || length < 8) return null
                            // Huffman baseline, extended or progressive only (no lossless, no arithmetic coding).
                            // zh-CN: 只接受霍夫曼编码的基线, 扩展或渐进 (不接受无损与算术编码).
                            if (marker !in 0xC0..0xC2) return null
                            val precision = bytes[i + 4].toInt() and 0xFF
                            val height = u16(bytes, i + 5)
                            val width = u16(bytes, i + 7)
                            val components = bytes[i + 9].toInt() and 0xFF
                            if (precision != 8 || width <= 0 || height <= 0 || (components != 1 && components != 3)) return null
                            return JpegData(bytes, width, height, components)
                        }
                        i += 2 + length
                    }
                }
            }
            return null
        }

        private fun u16(bytes: ByteArray, at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    }

}

/**
 * Puts the JPEG inputs of image stamps back into the stamp document (roadmap Q201 (a)): Skia writes every bitmap it
 * draws again, losslessly, so a JPEG photo grows about tenfold. Skia writes each `drawBitmap` as one image XObject
 * shown by a `Do` of the page content (equal bitmaps share the object); the image `Do`s of a sheet are matched in
 * order with the draws of its [ImageStamp]s (each copy of a tiled stamp is a draw), and the stream of an opaque image
 * from a [JpegData] input is replaced in place by the JPEG bytes (`/DCTDecode`). Opacity, rotation and position stay in
 * the content and its graphics state. A sheet whose image `Do`s differ in number from its draws (Skia drew through a
 * form or not at all), or an image whose size, mask or bit depth differ, keeps what Skia wrote.
 * zh-CN: 把图片印章的 JPEG 输入放回印章文档 (路线图 Q201 (a)): Skia 会把画出的每个位图无损地重新编码, JPEG 照片因此膨胀约十倍.
 * Skia 把每次 `drawBitmap` 写成一个由页面内容的 `Do` 显示的图片 XObject (相同位图共用对象); 按顺序把一张印章页上显示图片的 `Do` 与
 * 其 [ImageStamp] 的绘制一一对应 (平铺印章的每个副本算一次绘制), 来自 [JpegData] 输入的不透明图片的流原地换成 JPEG 字节
 * (`/DCTDecode`). 不透明度, 旋转与位置仍在内容流及其图形状态里. 图片 `Do` 数与绘制数不同的印章页 (Skia 经表单绘制或没有画),
 * 以及尺寸, 蒙版或位深不符的图片, 保留 Skia 写的内容.
 */
internal object JpegPassThrough {

    /**
     * Replaces the image streams of [sheets] (the stamps of each page of [document], in page order); answers the
     * number of streams replaced.
     * zh-CN: 替换 [sheets] (按页序, [document] 每页的印章) 中的图片流; 返回替换的流数.
     */
    fun apply(document: PDDocument, sheets: List<List<Stamp>>): Int {
        val done = Collections.newSetFromMap(IdentityHashMap<COSStream, Boolean>())
        var replaced = 0
        sheets.forEachIndexed { index, stamps ->
            val draws = stamps.filterIsInstance<ImageStamp>().flatMap { stamp -> List(1 + stamp.copies.size) { stamp } }
            if (draws.none { it.jpeg != null } || index >= document.numberOfPages) return@forEachIndexed
            val images = imageDraws(document.getPage(index)) ?: return@forEachIndexed
            if (images.size != draws.size) return@forEachIndexed
            images.zip(draws).forEach { (stream, stamp) ->
                val jpeg = stamp.jpeg ?: return@forEach
                if (stream in done || !fits(stream, stamp, jpeg)) return@forEach
                done += stream
                replace(stream, jpeg)
                replaced++
            }
        }
        return replaced
    }

    /** The image XObject streams the top level of [page] shows, in content order. zh-CN: [page] 顶层显示的图片 XObject 流, 按内容顺序. */
    private fun imageDraws(page: PDPage): List<COSStream>? {
        val resources = page.resources ?: return null
        val parser = PDFStreamParser(page)
        parser.parse()
        val images = ArrayList<COSStream>()
        var operand: COSBase? = null
        for (token in parser.tokens) {
            if (token !is Operator) {
                operand = token as? COSBase
                continue
            }
            if (token.name == "Do") {
                val name = operand as? COSName
                val xObjects = resources.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
                val stream = name?.let { xObjects?.getDictionaryObject(it) } as? COSStream
                if (stream != null && COSName.IMAGE == stream.getCOSName(COSName.SUBTYPE)) images += stream
            }
            operand = null
        }
        return images
    }

    private fun fits(stream: COSStream, stamp: ImageStamp, jpeg: JpegData): Boolean =
        !stamp.bitmap.hasAlpha() &&
            stamp.bitmap.width == jpeg.width && stamp.bitmap.height == jpeg.height &&
            stream.getInt(COSName.WIDTH) == jpeg.width && stream.getInt(COSName.HEIGHT) == jpeg.height &&
            stream.getInt(COSName.BITS_PER_COMPONENT) == 8 &&
            stream.getDictionaryObject(COSName.SMASK) == null && stream.getDictionaryObject(COSName.MASK) == null

    private fun replace(stream: COSStream, jpeg: JpegData) {
        val interpolate = stream.getDictionaryObject(COSName.INTERPOLATE)
        stream.clear()
        stream.setItem(COSName.TYPE, COSName.XOBJECT)
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE)
        stream.setInt(COSName.WIDTH, jpeg.width)
        stream.setInt(COSName.HEIGHT, jpeg.height)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.COLORSPACE, if (jpeg.components == 1) COSName.DEVICEGRAY else COSName.DEVICERGB)
        interpolate?.let { stream.setItem(COSName.INTERPOLATE, it) }
        stream.createRawOutputStream().use { it.write(jpeg.bytes) }
        stream.setItem(COSName.FILTER, COSName.DCT_DECODE)
    }

}

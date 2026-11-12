package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

/**
 * What `fromImages` needs to embed a JPEG as stored (roadmap D18, D33): size, components, precision, whether an Adobe
 * APP14 marker is present (CMYK JPEGs written by Adobe software store inverted values) and the EXIF orientation. Read
 * from the marker segments only, so neither the image data nor `BitmapFactory` is touched.
 * zh-CN: `fromImages` 原样嵌入 JPEG 所需的信息 (路线图 D18, D33): 尺寸, 分量数, 精度, 是否有 Adobe APP14 标记 (Adobe 软件写出的
 * CMYK JPEG 存储反相值) 以及 EXIF 方向. 只读取标记段, 不触及图像数据, 也不使用 `BitmapFactory`.
 */
internal class JpegInfo(val width: Int, val height: Int, val components: Int, val precision: Int, val adobe: Boolean, val orientation: Int) {

    /** Whether PDF readers can show the stored stream as `DCTDecode`. zh-CN: PDF 阅读器能否以 `DCTDecode` 显示存储的数据流. */
    val embeddable: Boolean get() = precision == 8 && components in EMBEDDABLE_COMPONENTS && width > 0 && height > 0

    companion object {

        private val EMBEDDABLE_COMPONENTS = setOf(1, 3, 4)

        fun isJpeg(bytes: ByteArray): Boolean = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

        /** The marker segments of [bytes] up to the first frame header, or null when they cannot be read. zh-CN: 读取 [bytes] 到第一个帧头为止的标记段, 无法读取时为 null. */
        fun read(bytes: ByteArray): JpegInfo? {
            if (!isJpeg(bytes)) return null
            var position = 2
            var adobe = false
            var orientation = 1
            while (position + 4 <= bytes.size) {
                if (bytes[position] != 0xFF.toByte()) return null
                val marker = bytes[position + 1].toInt() and 0xFF
                if (marker == 0xFF) {
                    position++
                    continue
                }
                if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) {
                    position += 2
                    continue
                }
                if (marker == 0xD9 || marker == 0xDA) return null
                val length = u16(bytes, position + 2)
                val start = position + 4
                val end = position + 2 + length
                if (length < 2 || end > bytes.size) return null
                when {
                    isFrameHeader(marker) -> {
                        if (end - start < 6) return null
                        return JpegInfo(
                            width = u16(bytes, start + 3),
                            height = u16(bytes, start + 1),
                            components = bytes[start + 5].toInt() and 0xFF,
                            precision = bytes[start].toInt() and 0xFF,
                            adobe = adobe,
                            orientation = orientation,
                        )
                    }
                    marker == 0xEE && startsWith(bytes, start, end, ADOBE) -> adobe = true
                    marker == 0xE1 && startsWith(bytes, start, end, EXIF) -> orientation = exifOrientation(bytes, start + EXIF.size, end) ?: orientation
                }
                position = end
            }
            return null
        }

        /** SOF0 - SOF15 except DHT (C4), JPG (C8) and DAC (CC). zh-CN: SOF0 - SOF15, 不含 DHT (C4), JPG (C8) 与 DAC (CC). */
        private fun isFrameHeader(marker: Int) = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

        private val ADOBE = "Adobe".toByteArray(Charsets.US_ASCII)
        private val EXIF = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)

        /** Tag 0x0112 of IFD0 of the TIFF structure in [start]..[end]. zh-CN: [start]..[end] 中 TIFF 结构 IFD0 的 0x0112 标签. */
        internal fun exifOrientation(bytes: ByteArray, start: Int, end: Int): Int? {
            if (end - start < 8) return null
            val little = when {
                bytes[start] == 0x49.toByte() && bytes[start + 1] == 0x49.toByte() -> true
                bytes[start] == 0x4D.toByte() && bytes[start + 1] == 0x4D.toByte() -> false
                else -> return null
            }
            fun u16At(offset: Int): Int? = if (start + offset + 2 > end) null else if (little) {
                (bytes[start + offset].toInt() and 0xFF) or ((bytes[start + offset + 1].toInt() and 0xFF) shl 8)
            } else {
                u16(bytes, start + offset)
            }
            fun u32At(offset: Int): Long? = if (start + offset + 4 > end) null else {
                val first = u16At(offset) ?: return null
                val second = u16At(offset + 2) ?: return null
                if (little) (second.toLong() shl 16) or first.toLong() else (first.toLong() shl 16) or second.toLong()
            }
            if (u16At(2) != 42) return null
            val ifd = u32At(4) ?: return null
            if (ifd > Int.MAX_VALUE - 2) return null
            val count = u16At(ifd.toInt()) ?: return null
            for (index in 0 until count) {
                val entry = ifd.toInt() + 2 + index * 12
                val tag = u16At(entry) ?: return null
                if (tag == 0x0112) return u16At(entry + 8)?.takeIf { it in 1..8 }
            }
            return null
        }

        private fun startsWith(bytes: ByteArray, start: Int, end: Int, prefix: ByteArray): Boolean =
            end - start >= prefix.size && prefix.indices.all { bytes[start + it] == prefix[it] }

        private fun u16(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSourceException
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes

/**
 * Reads a whole `run` input (an image, a text, a font or an HTML file) by absolute position, refusing one larger than
 * a limit with `LIMIT_EXCEEDED` before reading.
 * zh-CN: 按绝对位置读取整个 `run` 输入 (图片, 文本, 字体或 HTML 文件), 大于上限时在读取之前以 `LIMIT_EXCEEDED` 拒绝.
 */
internal object InputBytes {

    fun read(input: ParcelFileDescriptor, limit: Long, label: String): ByteArray {
        val source = DescriptorSource.of(input)
        if (source.length > limit) throw PdfFailure.limitExceeded("$label has ${source.length} bytes, above $limit")
        val bytes = ByteArray(source.length.toInt())
        var position = 0
        try {
            while (position < bytes.size) {
                val read = source.read(position.toLong(), bytes, position, bytes.size - position)
                if (read <= 0) throw PdfFailure(PdfErrorCodes.IO_ERROR, "$label ended at $position of ${bytes.size} bytes")
                position += read
            }
        } catch (e: PdfSourceException) {
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "$label: ${e.message}")
        }
        return bytes
    }

}

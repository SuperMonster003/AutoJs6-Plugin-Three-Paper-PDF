package io.github.supermonster003.autojs6.plugin.three.paper.pdf.render

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * How a rendered bitmap leaves the plugin (roadmap D17, protocol "Rendering"): a read-only `SharedMemory` region in the
 * byte layout of `Bitmap.copyPixelsToBuffer` (API 27+), or an encoded image written to a host descriptor.
 * zh-CN: 渲染出的位图如何离开插件 (路线图 D17, 协议 "Rendering" 一节): 采用 `Bitmap.copyPixelsToBuffer` 字节布局的只读
 * `SharedMemory` 区域 (API 27+), 或写入宿主描述符的编码图像.
 */
internal object BitmapTransport {

    /**
     * A read-only region holding the pixels of [bitmap] (`rowBytes * height` bytes), or null when the region cannot be
     * created, so the caller falls back to the file transport (protocol "Rendering"). The caller owns the region.
     * zh-CN: 存放 [bitmap] 像素 (`rowBytes * height` 字节) 的只读区域; 无法创建区域时为 null, 由调用方回落到文件传输 (协议
     * "Rendering" 一节). 区域归调用方所有.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun region(bitmap: Bitmap): SharedMemory? {
        val memory = try {
            SharedMemory.create(REGION_NAME, bitmap.rowBytes * bitmap.height)
        } catch (e: Exception) {
            return null
        }
        try {
            val buffer = memory.mapReadWrite()
            try {
                bitmap.copyPixelsToBuffer(buffer)
            } finally {
                SharedMemory.unmap(buffer)
            }
            if (!memory.setProtect(OsConstants.PROT_READ)) throw IOException("the render region cannot be made read-only")
            return memory
        } catch (e: Exception) {
            memory.close()
            return null
        } catch (t: Throwable) {
            memory.close()
            throw t
        }
    }

    /**
     * Encodes [bitmap] as [format] into [output] from its current position, flushes it and answers the byte count. More
     * than [limit] bytes fail with `LIMIT_EXCEEDED`; on any failure the output is emptied. The plugin does not close
     * [output] here: the Binder method that received it does.
     * zh-CN: 把 [bitmap] 按 [format] 编码, 从 [output] 的当前位置写入, 刷新后返回字节数. 超过 [limit] 字节报 `LIMIT_EXCEEDED`;
     * 任何失败都会清空输出. 此处不关闭 [output]: 由收到它的 Binder 方法关闭.
     */
    fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, output: ParcelFileDescriptor, limit: Long, stop: StopSignal): Long {
        // Not closed: closing a FileOutputStream built on a FileDescriptor would close the host's descriptor under the
        // ParcelFileDescriptor that owns it.
        // zh-CN: 不关闭: 关闭基于 FileDescriptor 构造的 FileOutputStream 会绕过持有它的 ParcelFileDescriptor 关闭宿主描述符.
        val stream = CountingOutputStream(FileOutputStream(output.fileDescriptor), limit, stop)
        try {
            val encoded = bitmap.compress(format, quality, stream)
            // Bitmap.compress swallows what the stream throws and answers false, so the stream keeps the reason.
            // zh-CN: Bitmap.compress 会吞掉输出流抛出的异常并返回 false, 因此由输出流记录原因.
            stream.failure?.let { throw it }
            if (!encoded) throw PdfFailure(PdfErrorCodes.INTERNAL, "the page bitmap cannot be encoded as $format")
            stream.flush()
            Operations.flush(output)
            return stream.count
        } catch (t: Throwable) {
            Operations.truncate(output)
            throw t
        }
    }

    private const val REGION_NAME = "three-paper-pdf-render"

}

/**
 * Counts what passes to [target] and refuses more than [limit] bytes or a stopped call; the first failure is kept in
 * [failure] because `Bitmap.compress` swallows exceptions of its stream.
 * zh-CN: 统计写入 [target] 的字节, 超过 [limit] 字节或调用已停止时拒绝写入; 首个失败保存在 [failure] 中, 因为
 * `Bitmap.compress` 会吞掉输出流的异常.
 */
internal class CountingOutputStream(private val target: OutputStream, private val limit: Long, private val stop: StopSignal) : OutputStream() {

    var count = 0L
        private set

    var failure: Throwable? = null
        private set

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        failure?.let { throw IOException("the output already failed", it) }
        try {
            stop.checkStop()
            if (len > limit - count) throw PdfFailure.limitExceeded("the encoded page exceeds $limit bytes; render it smaller")
            target.write(b, off, len)
            count += len
        } catch (t: Throwable) {
            failure = t
            throw t
        }
    }

    override fun flush() {
        try {
            target.flush()
        } catch (e: IOException) {
            if (failure == null) failure = e
            throw e
        }
    }

}

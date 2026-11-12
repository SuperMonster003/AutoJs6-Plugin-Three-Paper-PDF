package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.tom_roush.pdfbox.io.RandomAccessRead
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Random reads of a document source by absolute position, which never moves a shared file offset, so the same
 * descriptor serves PDFBox and `PdfRenderer` (protocol "Descriptors").
 * zh-CN: 按绝对位置随机读取文档源, 不移动共享的文件偏移, 因此同一描述符可同时供 PDFBox 与 `PdfRenderer` 使用 (协议 "Descriptors" 一节).
 */
interface PdfSource {

    /** Size in bytes, fixed when the source was opened. zh-CN: 字节数, 打开源时确定. */
    val length: Long

    /**
     * Reads up to [count] bytes at [position] into [buffer]; answers the number read, 0 only at the end of the source.
     * zh-CN: 从 [position] 读取至多 [count] 字节到 [buffer]; 返回读取的字节数, 仅在源末尾时为 0.
     */
    @Throws(IOException::class)
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int
}

/**
 * A read failure of the source itself, as opposed to a malformed document: maps to `IO_ERROR`, never `INVALID_PDF`.
 * zh-CN: 源本身的读取失败 (而非文档格式错误): 映射为 `IO_ERROR`, 不是 `INVALID_PDF`.
 */
class PdfSourceException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * A seekable regular file behind a descriptor the session owns; reads use `pread`.
 * zh-CN: 会话持有的描述符所指向的可定位普通文件; 读取使用 `pread`.
 */
class DescriptorSource private constructor(private val descriptor: ParcelFileDescriptor, override val length: Long) : PdfSource {

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int = try {
        Os.pread(descriptor.fileDescriptor, buffer, offset, count, position)
    } catch (e: ErrnoException) {
        throw PdfSourceException("reading the source at $position failed: ${e.message}", e)
    }

    companion object {

        /**
         * Checks that [descriptor] is a seekable regular file (protocol "Descriptors": `open` needs a seekable source)
         * and answers its size; refuses anything else with `INVALID_ARGUMENT`.
         * zh-CN: 检查 [descriptor] 是可定位的普通文件 (协议 "Descriptors": `open` 需要可定位的源) 并返回其大小; 其他情况以
         * `INVALID_ARGUMENT` 拒绝.
         */
        fun of(descriptor: ParcelFileDescriptor): DescriptorSource {
            val stat = try {
                Os.fstat(descriptor.fileDescriptor)
            } catch (e: ErrnoException) {
                throw PdfFailure.invalid("the source descriptor cannot be inspected: ${e.message}")
            }
            if (!OsConstants.S_ISREG(stat.st_mode)) throw PdfFailure.invalid("the source is not a regular file")
            try {
                Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR)
            } catch (e: ErrnoException) {
                throw PdfFailure.invalid("the source is not seekable: ${e.message}")
            }
            return DescriptorSource(descriptor, stat.st_size)
        }

    }

}

/**
 * A local file opened read-only, for JVM tests and for the decrypted working copies of P2.3.
 * zh-CN: 以只读方式打开的本地文件, 供 JVM 测试与 P2.3 的解密工作副本使用.
 */
class FileSource(private val file: RandomAccessFile) : PdfSource, AutoCloseable {

    override val length: Long = file.length()

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) return 0
        return try {
            synchronized(file) {
                file.seek(position)
                file.read(buffer, offset, count).coerceAtLeast(0)
            }
        } catch (e: IOException) {
            throw PdfSourceException("reading the source at $position failed: ${e.message}", e)
        }
    }

    override fun close() = file.close()

}

/**
 * PDFBox input over a [PdfSource] with one read-ahead window. Not thread-safe: calls on one document are serialized
 * (protocol "Methods"). Closing it leaves the source open; the session closes its descriptor.
 * zh-CN: 基于 [PdfSource] 的 PDFBox 输入, 带一个预读窗口. 非线程安全: 同一文档的调用是串行的 (协议 "Methods" 一节).
 * 关闭它不关闭源; 描述符由会话关闭.
 */
class PdfSourceRead(private val source: PdfSource, windowSize: Int = DEFAULT_WINDOW) : RandomAccessRead {

    private val window = ByteArray(windowSize)
    private var windowStart = 0L
    private var windowLength = 0
    private var position = 0L
    private var closed = false
    private val length = source.length

    override fun read(): Int {
        checkOpen()
        if (position >= length || !fill(position)) return -1
        val value = window[(position - windowStart).toInt()].toInt() and 0xFF
        position++
        return value
    }

    override fun read(b: ByteArray): Int = read(b, 0, b.size)

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkOpen()
        if (len == 0) return 0
        if (position >= length) return -1
        var total = 0
        while (total < len && position < length) {
            if (!fill(position)) break
            val available = (windowStart + windowLength - position).toInt()
            val count = minOf(len - total, available)
            System.arraycopy(window, (position - windowStart).toInt(), b, off + total, count)
            total += count
            position += count
        }
        return if (total == 0) -1 else total
    }

    override fun getPosition(): Long {
        checkOpen()
        return position
    }

    override fun seek(position: Long) {
        checkOpen()
        if (position < 0) throw IOException("Invalid position $position")
        this.position = position
    }

    override fun length(): Long {
        checkOpen()
        return length
    }

    override fun isClosed(): Boolean = closed

    override fun peek(): Int {
        val value = read()
        if (value != -1) rewind(1)
        return value
    }

    override fun rewind(bytes: Int) {
        checkOpen()
        seek(position - bytes)
    }

    override fun readFully(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var done = 0
        while (done < length) {
            val count = read(bytes, done, length - done)
            if (count < 0) throw EOFException("Premature end of buffer reached")
            done += count
        }
        return bytes
    }

    override fun isEOF(): Boolean = peek() == -1

    override fun available(): Int {
        checkOpen()
        return (length - position).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    override fun close() {
        closed = true
    }

    /** Makes [at] part of the window; answers false at the end of the source. zh-CN: 使 [at] 落入窗口; 到达源末尾时返回 false. */
    private fun fill(at: Long): Boolean {
        if (at >= windowStart && at < windowStart + windowLength) return true
        // The parser also reads backwards (the trailer, rewinds while lexing), so a position just before the window
        // starts the next window half a window earlier. zh-CN: 解析器也会向后读 (尾部, 词法分析时回退), 因此紧邻窗口之前的位置
        // 使下一个窗口提前半个窗口开始.
        val backwards = windowLength > 0 && at < windowStart && at >= windowStart - window.size
        val start = if (backwards) maxOf(0L, at - window.size / 2) else at
        val wanted = minOf(window.size.toLong(), length - start).toInt()
        var read = 0
        while (read < wanted) {
            val count = source.read(start + read, window, read, wanted - read)
            if (count <= 0) break
            read += count
        }
        windowStart = start
        windowLength = read
        return read > at - start
    }

    private fun checkOpen() {
        if (closed) throw IOException("RandomAccessRead already closed")
    }

    companion object {
        const val DEFAULT_WINDOW = 64 * 1024
    }

}

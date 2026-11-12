package io.github.supermonster003.autojs6.plugin.three.paper.pdf.render

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The stream `Bitmap.compress` writes the encoded page through (roadmap P2.3): it counts, enforces the byte limit and
 * the stop signal, and keeps the first failure because `Bitmap.compress` swallows it.
 * zh-CN: `Bitmap.compress` 写出编码页面所经的输出流 (路线图 P2.3): 统计字节, 执行字节上限与停止信号, 并保存首个失败, 因为
 * `Bitmap.compress` 会吞掉它.
 */
class CountingOutputStreamTest {

    @Test
    fun `bytes pass through and are counted up to the limit`() {
        val target = ByteArrayOutputStream()
        val stream = CountingOutputStream(target, limit = 4, stop = StopSignal.NEVER)
        stream.write(byteArrayOf(1, 2, 3), 0, 3)
        stream.write(4)
        assertEquals(4L, stream.count)
        assertNull(stream.failure)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), target.toByteArray())
    }

    @Test
    fun `a write past the limit fails with LIMIT_EXCEEDED and later writes fail too`() {
        val target = ByteArrayOutputStream()
        val stream = CountingOutputStream(target, limit = 4, stop = StopSignal.NEVER)
        stream.write(byteArrayOf(1, 2, 3), 0, 3)
        val failure = assertThrows(PdfFailure::class.java) { stream.write(byteArrayOf(4, 5), 0, 2) }
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, failure.code)
        assertSame(failure, stream.failure)
        val later = assertThrows(IOException::class.java) { stream.write(6) }
        assertSame(failure, later.cause)
        assertEquals(3L, stream.count)
        assertEquals(3, target.size())
    }

    @Test
    fun `a stopped call ends the encoding with its reason`() {
        val stream = CountingOutputStream(ByteArrayOutputStream(), limit = Long.MAX_VALUE, stop = StopSignal { StopSignal.closed() })
        val failure = assertThrows(PdfFailure::class.java) { stream.write(1) }
        assertEquals(PdfErrorCodes.SESSION_CLOSED, failure.code)
        assertSame(failure, stream.failure)
    }

}

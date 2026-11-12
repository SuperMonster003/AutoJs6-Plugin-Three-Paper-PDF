package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * The PDFBox input over a positional source (roadmap P2.1): reads across window boundaries, seeks both ways, peeks,
 * rewinds and reports the end like PDFBox's own `RandomAccessBufferedFileInputStream`.
 * zh-CN: 基于定位读取源的 PDFBox 输入 (路线图 P2.1): 跨窗口边界读取, 双向定位, 预读, 回退, 并像 PDFBox 自带的
 * `RandomAccessBufferedFileInputStream` 那样报告末尾.
 */
class PdfSourceReadTest {

    private lateinit var file: File
    private lateinit var source: FileSource
    private val bytes = ByteArray(10_000) { (it * 7 + 3).toByte() }

    @Before
    fun setUp() {
        file = File.createTempFile("paper-source", ".bin").apply { writeBytes(bytes) }
        source = FileSource(RandomAccessFile(file, "r"))
    }

    @After
    fun tearDown() {
        source.close()
        file.delete()
    }

    @Test
    fun `reads byte by byte and in blocks across windows`() {
        val input = PdfSourceRead(source, windowSize = 64)
        assertEquals(bytes.size.toLong(), input.length())
        for (index in 0 until 200) assertEquals(bytes[index].toInt() and 0xFF, input.read())
        val block = ByteArray(1_000)
        assertEquals(1_000, input.read(block, 0, block.size))
        assertArrayEquals(bytes.copyOfRange(200, 1_200), block)
        assertEquals(1_200L, input.position)
        assertEquals(bytes.size - 1_200, input.available())
    }

    @Test
    fun `seeks backwards and forwards, peeks and rewinds`() {
        val input = PdfSourceRead(source, windowSize = 64)
        input.seek(9_000)
        assertEquals(bytes[9_000].toInt() and 0xFF, input.peek())
        assertEquals(9_000L, input.position)
        // Backwards byte by byte, as the parser does around the trailer. zh-CN: 逐字节向后读, 如解析器在尾部附近所做.
        for (position in 8_999 downTo 8_800) {
            input.seek(position.toLong())
            assertEquals(bytes[position].toInt() and 0xFF, input.read())
        }
        input.seek(10)
        assertEquals(bytes[10].toInt() and 0xFF, input.read())
        input.rewind(1)
        assertEquals(10L, input.position)
        assertArrayEquals(bytes.copyOfRange(10, 110), input.readFully(100))
    }

    @Test
    fun `reports the end of the source`() {
        val input = PdfSourceRead(source, windowSize = 64)
        input.seek(bytes.size - 2L)
        assertFalse(input.isEOF)
        assertEquals(2, input.read(ByteArray(10)))
        assertTrue(input.isEOF)
        assertEquals(-1, input.read())
        assertEquals(-1, input.read(ByteArray(4)))
        assertEquals(-1, input.peek())
        input.seek(bytes.size - 3L)
        assertThrows(EOFException::class.java) { input.readFully(4) }
        assertThrows(IOException::class.java) { input.seek(-1) }
    }

    @Test
    fun `closing the input keeps the source open`() {
        val input = PdfSourceRead(source)
        input.close()
        assertTrue(input.isClosed)
        assertThrows(IOException::class.java) { input.read() }
        val again = PdfSourceRead(source)
        assertEquals(bytes[0].toInt() and 0xFF, again.read())
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * Opening documents from a positional source (roadmap P2.1, D12): plain, encrypted with each password, and the error
 * codes for a missing or wrong password, a damaged file and a failing source.
 * zh-CN: 从定位读取源打开文档 (路线图 P2.1, D12): 普通文档, 分别用两种密码打开的加密文档, 以及缺少或错误密码, 损坏文件与读取失败
 * 的源对应的错误码.
 */
class PdfLoaderTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("paper-loader").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun <T> withSource(bytes: ByteArray, block: (PdfSource) -> T): T {
        val file = File(directory, "source.pdf").apply { writeBytes(bytes) }
        return FileSource(RandomAccessFile(file, "r")).use(block)
    }

    private fun failureOf(bytes: ByteArray, password: String?): PdfFailure = withSource(bytes) { source ->
        assertThrows(PdfFailure::class.java) { PdfLoader.load(source, password, directory).close() }
    }

    @Test
    fun `opens a plain document`() = withSource(PdfFixtures.multiPageWithOutline()) { source ->
        PdfLoader.load(source, null, directory).use { loaded ->
            assertEquals(PdfFixtures.CHAPTERS.size * 2, loaded.document.numberOfPages)
            assertEquals(PdfFixtures.TITLE, loaded.document.documentInformation.title)
        }
    }

    @Test
    fun `opens an encrypted document with either password`() {
        for (password in listOf(PdfFixtures.USER_PASSWORD, PdfFixtures.OWNER_PASSWORD)) {
            withSource(PdfFixtures.encrypted()) { source ->
                PdfLoader.load(source, password, directory).use { loaded ->
                    assertTrue(loaded.document.isEncrypted)
                    assertTrue(loaded.document.numberOfPages > 0)
                }
            }
        }
    }

    @Test
    fun `reports a missing or wrong password`() {
        assertEquals(PdfErrorCodes.PASSWORD_REQUIRED, failureOf(PdfFixtures.encrypted(), null).code)
        assertEquals(PdfErrorCodes.PASSWORD_REQUIRED, failureOf(PdfFixtures.encrypted(), "").code)
        assertEquals(PdfErrorCodes.WRONG_PASSWORD, failureOf(PdfFixtures.encrypted(), "not-the-password").code)
    }

    @Test
    fun `reports a damaged file as INVALID_PDF`() {
        assertEquals(PdfErrorCodes.INVALID_PDF, failureOf("this is not a PDF at all".toByteArray(), null).code)
        assertEquals(PdfErrorCodes.INVALID_PDF, failureOf(ByteArray(0), null).code)
    }

    @Test
    fun `reports a failing source as IO_ERROR`() {
        val bytes = PdfFixtures.singlePageText()
        val failing = object : PdfSource {
            override val length = bytes.size.toLong()
            override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int =
                throw PdfSourceException("read failed at $position")
        }
        val failure = assertThrows(PdfFailure::class.java) { PdfLoader.load(failing, null, directory) }
        assertEquals(PdfErrorCodes.IO_ERROR, failure.code)
    }

}

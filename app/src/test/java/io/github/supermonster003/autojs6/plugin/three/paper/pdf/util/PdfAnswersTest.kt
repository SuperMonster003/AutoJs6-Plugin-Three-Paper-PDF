package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

/**
 * Answer encoding and failure decoding (protocol "Conventions", "Errors"). The real Bundle and parcel round trip runs
 * on a device (`PdfBundlesDeviceTest`).
 * zh-CN: 应答编码与失败解码 (协议 "Conventions", "Errors" 两节). 真实 Bundle 与 parcel 往返在设备上运行 (`PdfBundlesDeviceTest`).
 */
class PdfAnswersTest {

    @Test
    fun `success answers carry the request version, the request id and non-null results`() {
        val answer = PdfAnswers.success(
            version = 1,
            results = mapOf(PdfContract.KEY_PAGE_COUNT to 3, PdfContract.KEY_TITLE to null),
            requestId = "r-1",
        )
        assertEquals(listOf(PdfContract.KEY_CONTRACT_VERSION, PdfContract.KEY_REQUEST_ID, PdfContract.KEY_PAGE_COUNT), answer.keys.toList())
        assertEquals(1, answer[PdfContract.KEY_CONTRACT_VERSION])
        assertEquals("r-1", answer[PdfContract.KEY_REQUEST_ID])
        assertNull(PdfAnswers.errorOf(MapValues(answer)))
        assertThrows(IllegalArgumentException::class.java) {
            PdfAnswers.success(1, mapOf(PdfContract.KEY_ERROR_CODE to PdfErrorCodes.INTERNAL))
        }
    }

    @Test
    fun `failure answers carry the code and the detail`() {
        val answer = PdfAnswers.failure(1, PdfFailure.pageOutOfRange("page 9 is outside 1..3"))
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, answer[PdfContract.KEY_ERROR_CODE])
        assertEquals("page 9 is outside 1..3", answer[PdfContract.KEY_ERROR_MESSAGE])
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE to "page 9 is outside 1..3", PdfAnswers.errorOf(MapValues(answer)))
        assertFalse(answer.containsKey(PdfContract.KEY_REQUEST_ID))
    }

    @Test
    fun `unknown error codes decode as internal`() {
        val answer = MapValues(mapOf(PdfContract.KEY_ERROR_CODE to "NEW_CODE", PdfContract.KEY_ERROR_MESSAGE to "detail"))
        assertEquals(PdfErrorCodes.INTERNAL to "NEW_CODE: detail", PdfAnswers.errorOf(answer))
        assertEquals(PdfErrorCodes.INTERNAL to "NEW_CODE", PdfAnswers.errorOf(MapValues(mapOf(PdfContract.KEY_ERROR_CODE to "NEW_CODE"))))
    }

    @Test
    fun `error messages are cut at a code point boundary`() {
        val max = PdfContract.MAX_ERROR_MESSAGE_BYTES
        val ascii = "a".repeat(max + 10)
        assertEquals(max, PdfAnswers.truncateUtf8(ascii, max).length)
        // Three-byte characters: 1365 fit into 4095 bytes, the next one would cross 4096.
        // zh-CN: 三字节字符: 1365 个占 4095 字节, 再加一个就会超过 4096.
        val chinese = "页".repeat(2000)
        assertEquals(max / 3, PdfAnswers.truncateUtf8(chinese, max).length)
        // A surrogate pair (4 bytes) is never split. zh-CN: 代理对 (4 字节) 不会被拆开.
        val emoji = "📄"
        assertEquals("ab", PdfAnswers.truncateUtf8("ab$emoji", 5))
        assertEquals("ab$emoji", PdfAnswers.truncateUtf8("ab$emoji", 6))
        val answer = PdfAnswers.failure(1, PdfFailure(PdfErrorCodes.INTERNAL, chinese))
        assertTrue((answer[PdfContract.KEY_ERROR_MESSAGE] as String).toByteArray(Charsets.UTF_8).size <= max)
    }

    @Test
    fun `exceptions map to contract failures`() {
        val own = PdfFailure.invalid("bad")
        assertTrue(PdfAnswers.failureOf(own) === own)
        assertEquals(PdfErrorCodes.IO_ERROR, PdfAnswers.failureOf(FileNotFoundException("gone")).code)
        assertEquals("FileNotFoundException: gone", PdfAnswers.failureOf(FileNotFoundException("gone")).detail)
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, PdfAnswers.failureOf(SecurityException("no")).code)
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, PdfAnswers.failureOf(OutOfMemoryError()).code)
        assertEquals(PdfErrorCodes.INTERNAL, PdfAnswers.failureOf(IllegalStateException()).code)
    }

    @Test
    fun `binder exceptions use the three allowed types and the encoded message`() {
        val outOfRange = PdfFailure.pageOutOfRange("page 9").toBinderException()
        assertTrue(outOfRange is IllegalArgumentException)
        assertEquals("PAGE_OUT_OF_RANGE: page 9", outOfRange.message)
        assertTrue(PdfFailure(PdfErrorCodes.STORAGE_PERMISSION_DENIED).toBinderException() is SecurityException)
        val unsupported = PdfFailure.unsupported("NOT_IMPLEMENTED: info").toBinderException()
        assertTrue(unsupported is IllegalStateException)
        assertEquals(PdfErrorCodes.UNSUPPORTED to "NOT_IMPLEMENTED: info", PdfErrorCodes.decode(unsupported.message))
        // Host-only codes and unknown codes are programming errors. zh-CN: 宿主专属码与未知码属于编程错误.
        assertThrows(IllegalArgumentException::class.java) { PdfFailure(PdfErrorCodes.PLUGIN_INCOMPATIBLE) }
        assertThrows(IllegalArgumentException::class.java) { PdfFailure("NEW_CODE") }
    }

}

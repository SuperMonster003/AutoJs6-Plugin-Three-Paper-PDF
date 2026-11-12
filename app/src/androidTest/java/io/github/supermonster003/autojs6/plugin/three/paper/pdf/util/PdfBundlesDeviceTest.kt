package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Bundle side of requests and answers on a real framework (roadmap P0.3): answers survive a parcel round trip,
 * request Bundles validate like the JVM maps of `PdfOptionsTest`, and oversized requests fail with `LIMIT_EXCEEDED`.
 * zh-CN: 在真实框架上验证请求与应答的 Bundle 一侧 (路线图 P0.3): 应答经 parcel 往返不变, 请求 Bundle 的校验与 `PdfOptionsTest`
 * 中 JVM Map 的校验一致, 超大请求报 `LIMIT_EXCEEDED`.
 */
@RunWith(AndroidJUnit4::class)
class PdfBundlesDeviceTest {

    /** Writes [bundle] into a parcel and reads it back, as Binder does. zh-CN: 像 Binder 那样把 [bundle] 写入 parcel 再读回. */
    private fun roundTrip(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader)).apply { keySet() }
        } finally {
            parcel.recycle()
        }
    }

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(PdfFailure::class.java) { block() }.code)
    }

    @Test
    fun successAnswersSurviveAParcelRoundTrip() {
        val answer = PdfBundles.toBundle(
            PdfAnswers.success(
                version = PdfContract.CONTRACT_VERSION,
                results = mapOf(
                    PdfContract.KEY_PAGE_COUNT to 3,
                    PdfContract.KEY_TITLE to null,
                    "sizes" to doubleArrayOf(595.0, 842.0),
                    "names" to arrayOf("a", "b"),
                    "nested" to mapOf("page" to 2, "found" to true),
                ),
                requestId = "r-1",
            ),
        )
        val read = roundTrip(answer)
        val values = PdfBundles.values(read)
        assertNull(PdfAnswers.errorOf(values))
        assertEquals(setOf(PdfContract.KEY_CONTRACT_VERSION, PdfContract.KEY_REQUEST_ID, PdfContract.KEY_PAGE_COUNT, "sizes", "names", "nested"), values.keys)
        assertEquals(PdfContract.CONTRACT_VERSION, read.getInt(PdfContract.KEY_CONTRACT_VERSION))
        assertEquals("r-1", read.getString(PdfContract.KEY_REQUEST_ID))
        assertEquals(3, read.getInt(PdfContract.KEY_PAGE_COUNT))
        assertArrayEquals(doubleArrayOf(595.0, 842.0), read.getDoubleArray("sizes"), 0.0)
        assertArrayEquals(arrayOf("a", "b"), read.getStringArray("names"))
        val nested = requireNotNull(read.getBundle("nested"))
        assertEquals(2, nested.getInt("page"))
        assertTrue(nested.getBoolean("found"))
        assertThrows(IllegalArgumentException::class.java) { PdfBundles.toBundle(mapOf("list" to listOf(1, 2))) }
    }

    @Test
    fun failureAnswersDecodeAfterAParcelRoundTrip() {
        val answer = PdfBundles.toBundle(PdfAnswers.failure(PdfContract.CONTRACT_VERSION, PdfFailure.pageOutOfRange("page 9 is outside 1..3")))
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE to "page 9 is outside 1..3", PdfAnswers.errorOf(PdfBundles.values(roundTrip(answer))))
    }

    @Test
    fun requestBundlesValidateLikeMaps() {
        val request = roundTrip(Bundle().apply {
            putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION)
            putInt(PdfContract.KEY_PAGE, 4)
            putString(PdfContract.KEY_PAGES, "1,3-")
            putInt(PdfContract.KEY_DPI, 300)
            putString("laterOptionalKey", "ignored")
        })
        val options = PdfBundles.options(request)
        assertEquals(PdfContract.CONTRACT_VERSION, options.contractVersion())
        assertEquals(4, options.page(pageCount = 5))
        assertArrayEquals(intArrayOf(1, 3, 4, 5), options.pages(pageCount = 5))
        assertEquals(RenderSize.Dpi(300), options.renderSize())

        // A Long page is a type error on a real Bundle too. zh-CN: 真实 Bundle 中 Long 页码同样是类型错误.
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PdfBundles.options(Bundle().apply { putLong(PdfContract.KEY_PAGE, 4L) }).page(pageCount = 5) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PdfBundles.options(Bundle()).contractVersion() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PdfBundles.options(null).contractVersion() }
    }

    @Test
    fun oversizedRequestsFailWithLimitExceeded() {
        PdfBundles.checkRequestSize(null)
        PdfBundles.checkRequestSize(Bundle().apply { putString(PdfContract.KEY_PASSWORD, "p".repeat(PdfContract.MAX_PASSWORD_LENGTH)) })
        // Parcels store strings as UTF-16, so 40000 characters take about 80 KB. zh-CN: parcel 以 UTF-16 存字符串, 40000 个字符约 80 KB.
        val large = Bundle().apply { putString("text", "x".repeat(40_000)) }
        assertTrue(PdfBundles.parcelSize(large) > PdfContract.MAX_OPTIONS_BYTES)
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { PdfBundles.checkRequestSize(large) }
    }

}

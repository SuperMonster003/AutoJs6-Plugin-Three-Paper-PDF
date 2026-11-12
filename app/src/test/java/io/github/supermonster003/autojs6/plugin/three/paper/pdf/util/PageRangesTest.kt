package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Page range grammar of the protocol "Conventions" section (roadmap D32).
 * zh-CN: 协议 "Conventions" 一节的页码范围语法 (路线图 D32).
 */
class PageRangesTest {

    @Test
    fun `absent range selects every page`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4), PageRanges.parse(null, 4))
        assertArrayEquals(intArrayOf(), PageRanges.parse(null, 0))
    }

    @Test
    fun `protocol example selects ascending distinct pages`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 5, 8, 9, 10), PageRanges.parse("1-3,5,8-", 10))
    }

    @Test
    fun `blanks, empty items, overlaps and order are tolerated`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 7), PageRanges.parse(" 7 , 3 - 4 ,, 1-3 , 2 ,", 9))
        assertArrayEquals(intArrayOf(5), PageRanges.parse("5-", 5))
        assertArrayEquals(intArrayOf(2), PageRanges.parse("02", 3))
    }

    @Test
    fun `malformed ranges are invalid arguments`() {
        for (spec in listOf("", " , ", "a", "1;2", "-2", "+1", "1--3", "1-2-3", "3-1", "1 0", "1.5", "99999999999")) {
            assertCode(PdfErrorCodes.INVALID_ARGUMENT, spec, 10)
        }
    }

    @Test
    fun `pages outside the document are out of range`() {
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE, "0", 10)
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE, "11", 10)
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE, "9-11", 10)
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE, "11-", 10)
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE, "1", 0)
        // A huge closed range is refused before any page is materialized. zh-CN: 巨大的闭区间在展开之前就被拒绝.
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE, "1-2000000000", 10)
    }

    @Test
    fun `failure message carries the encoded code`() {
        val failure = assertThrows(PdfFailure::class.java) { PageRanges.parse("12", 3) }
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, PdfErrorCodes.decode(failure.message)?.first)
        assertEquals("page 12 of \"12\" is outside 1..3", failure.detail)
    }

    @Test
    fun `format writes the shortest closed ranges and round-trips`() {
        assertEquals("1-3,5,8-10", PageRanges.format(intArrayOf(10, 1, 2, 3, 5, 8, 9, 2)))
        assertEquals("", PageRanges.format(intArrayOf()))
        assertEquals("7", PageRanges.format(intArrayOf(7)))
        val pages = intArrayOf(1, 2, 4, 6, 7, 8, 12)
        assertArrayEquals(pages, PageRanges.parse(PageRanges.format(pages), 12))
        assertThrows(IllegalArgumentException::class.java) { PageRanges.format(intArrayOf(0, 1)) }
    }

    private fun assertCode(code: String, spec: String, pageCount: Int) {
        val failure = assertThrows("spec \"$spec\"", PdfFailure::class.java) { PageRanges.parse(spec, pageCount) }
        assertEquals("spec \"$spec\"", code, failure.code)
    }

}

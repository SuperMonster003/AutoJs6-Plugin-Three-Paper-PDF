package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request option validation (protocol "Methods", "Rendering"; roadmap D17, D32).
 * zh-CN: 请求选项校验 (协议 "Methods", "Rendering" 两节; 路线图 D17, D32).
 */
class PdfOptionsTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(PdfFailure::class.java) { block() }.code)
    }

    @Test
    fun `contract version must be present and implemented`() {
        assertEquals(PdfContract.CONTRACT_VERSION, options(PdfContract.KEY_CONTRACT_VERSION to PdfContract.CONTRACT_VERSION).contractVersion())
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options().contractVersion() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_CONTRACT_VERSION to "1").contractVersion() }
        assertCode(PdfErrorCodes.UNSUPPORTED) { options(PdfContract.KEY_CONTRACT_VERSION to PdfContract.MAX_CONTRACT_VERSION + 1).contractVersion() }
        assertCode(PdfErrorCodes.UNSUPPORTED) { options(PdfContract.KEY_CONTRACT_VERSION to 0).contractVersion() }
    }

    @Test
    fun `typed getters refuse other types and ignore unknown keys`() {
        val options = options(
            PdfContract.KEY_DISPLAY_NAME to "a.pdf",
            PdfContract.KEY_TRANSPARENT to true,
            PdfContract.KEY_PAGE to 3L,
            "laterOptionalKey" to listOf(1, 2),
        )
        assertEquals("a.pdf", options.string(PdfContract.KEY_DISPLAY_NAME))
        assertTrue(options.boolean(PdfContract.KEY_TRANSPARENT, false))
        assertFalse(options.boolean(PdfContract.KEY_CASE_SENSITIVE, false))
        assertNull(options.string(PdfContract.KEY_PASSWORD))
        // The contract writes Int pages; a Long is a type error, not a silent conversion.
        // zh-CN: 契约写入的页码是 Int; Long 属于类型错误, 不做静默转换.
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options.int(PdfContract.KEY_PAGE) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options.string(PdfContract.KEY_TRANSPARENT) }
    }

    @Test
    fun `page and page ranges follow the document`() {
        val options = options(PdfContract.KEY_PAGE to 4, PdfContract.KEY_PAGES to "2-")
        assertEquals(4, options.page(pageCount = 5))
        assertArrayEquals(intArrayOf(2, 3, 4, 5), options.pages(pageCount = 5))
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { options.page(pageCount = 3) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options().page(pageCount = 3) }
        assertEquals(1, options().page(pageCount = 3, default = 1))
        assertArrayEquals(intArrayOf(1, 2, 3), options().pages(pageCount = 3))
    }

    @Test
    fun `password length is bounded`() {
        assertEquals("p".repeat(PdfContract.MAX_PASSWORD_LENGTH), options(PdfContract.KEY_PASSWORD to "p".repeat(PdfContract.MAX_PASSWORD_LENGTH)).password())
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_PASSWORD to "p".repeat(PdfContract.MAX_PASSWORD_LENGTH + 1)).password() }
    }

    @Test
    fun `quality, degrees and closed value sets`() {
        assertEquals(PdfContract.DEFAULT_QUALITY, options().quality())
        assertEquals(1, options(PdfContract.KEY_QUALITY to 1).quality())
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_QUALITY to 101).quality() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_QUALITY to 0).quality() }

        assertEquals(0, options().degrees())
        assertEquals(270, options(PdfContract.KEY_DEGREES to -90).degrees())
        assertEquals(90, options(PdfContract.KEY_DEGREES to 450).degrees())
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_DEGREES to 45).degrees() }

        assertEquals(PdfContract.FORMAT_PNG, options().choice(PdfContract.KEY_FORMAT, PdfContract.RENDER_FORMATS, PdfContract.FORMAT_PNG))
        assertEquals(PdfContract.FORMAT_WEBP, options(PdfContract.KEY_FORMAT to "webp").choice(PdfContract.KEY_FORMAT, PdfContract.RENDER_FORMATS, null))
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_FORMAT to "gif").choice(PdfContract.KEY_FORMAT, PdfContract.RENDER_FORMATS, null) }
    }

    @Test
    fun `render size is one of dpi, scale, width and height`() {
        assertEquals(RenderSize.Dpi(PdfContract.DEFAULT_DPI), options().renderSize())
        assertEquals(RenderSize.Dpi(300), options(PdfContract.KEY_DPI to 300).renderSize())
        assertEquals(RenderSize.Scale(2.0), options(PdfContract.KEY_SCALE to 2.0).renderSize())
        assertEquals(RenderSize.Width(800), options(PdfContract.KEY_WIDTH to 800).renderSize())
        assertEquals(RenderSize.Height(600), options(PdfContract.KEY_HEIGHT to 600).renderSize())
        // A key present with a null value counts as absent. zh-CN: 值为 null 的键视为缺省.
        assertEquals(RenderSize.Dpi(72), options(PdfContract.KEY_DPI to 72, PdfContract.KEY_WIDTH to null).renderSize())

        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_DPI to 72, PdfContract.KEY_WIDTH to 100).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_DPI to 0).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_DPI to PdfContract.MAX_DPI + 1).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_SCALE to 0.0).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_SCALE to Double.NaN).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_SCALE to 34.0).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_WIDTH to 0).renderSize() }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { options(PdfContract.KEY_SCALE to 2).renderSize() }
    }

    @Test
    fun `render pixels follow the displayed page and the pixel ceiling`() {
        // A4 at the default 144 dpi is about 1190 x 1684 pixels (roadmap D17). zh-CN: A4 在默认 144 dpi 下约 1190 x 1684 像素 (路线图 D17).
        assertEquals(1191 to 1684, RenderSize.Dpi(PdfContract.DEFAULT_DPI).pixels(595.28, 841.89))
        assertEquals(1000 to 1414, RenderSize.Width(1000).pixels(595.28, 841.89))
        assertEquals(707 to 1000, RenderSize.Height(1000).pixels(595.28, 841.89))
        assertEquals(1 to 1, RenderSize.Scale(0.01).pixels(10.0, 10.0))
        assertEquals(4000 to 4000, RenderSize.Width(4000).pixels(100.0, 100.0))
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { RenderSize.Width(4001).pixels(100.0, 100.0) }
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { RenderSize.Dpi(PdfContract.MAX_DPI).pixels(595.28, 841.89) }
    }

}

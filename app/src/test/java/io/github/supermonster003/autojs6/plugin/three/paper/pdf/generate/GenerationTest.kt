package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.assertWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Generation parts that run on the JVM (roadmap P2.5): page sizes, margins and image placement, the JPEG marker reader,
 * EXIF orientation matrices, text chunks and font magic numbers, request checks, and `fromImages` pages built from
 * JPEGs (embedded as stored, page count and size).
 * zh-CN: 可在 JVM 上运行的生成部件 (路线图 P2.5): 页面尺寸, 页边距与图片摆放, JPEG 标记读取, EXIF 方向矩阵, 文本分块与字体魔数,
 * 请求检查, 以及由 JPEG 生成的 `fromImages` 页面 (原样嵌入, 页数与尺寸).
 */
class GenerationTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(PdfFailure::class.java) { block() }.code)
    }

    private fun jpeg(width: Int, height: Int, gray: Boolean = false): ByteArray {
        val image = BufferedImage(width, height, if (gray) BufferedImage.TYPE_BYTE_GRAY else BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.ORANGE
            fillRect(0, 0, width / 2, height)
            dispose()
        }
        return ByteArrayOutputStream().also { assertTrue(ImageIO.write(image, "jpg", it)) }.toByteArray()
    }

    /** [bytes] with an EXIF APP1 segment (big endian) of [orientation] after SOI. zh-CN: 在 SOI 之后插入方向为 [orientation] 的 EXIF APP1 段 (大端). */
    private fun withExif(bytes: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8,
            0, 1,
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
            0, 0, 0, 0,
        )
        val payload = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return bytes.copyOfRange(0, 2) + segment + bytes.copyOfRange(2, bytes.size)
    }

    /** Marker segments of a CMYK JPEG up to its frame header. zh-CN: CMYK JPEG 到帧头为止的标记段. */
    private fun cmykHeader(width: Int, height: Int, adobe: Boolean, precision: Int = 8): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (adobe) {
            val payload = "Adobe".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 100, 0, 0, 0, 0, 2)
            out.write(byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0, (payload.size + 2).toByte()))
            out.write(payload)
        }
        out.write(byteArrayOf(0xFF.toByte(), 0xC0.toByte(), 0, 20, precision.toByte(), (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 4))
        repeat(4) { out.write(byteArrayOf((it + 1).toByte(), 0x11, 0)) }
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun imageRequest(pageSize: PageLayout.PageSize = PageLayout.PageSize.Fit, fit: String = PdfContract.FIT_CONTAIN, orientation: String = PdfContract.ORIENTATION_AUTO, margin: Float = 0f) =
        ImagesToPdf.Request(pageSize, fit, orientation, margin, quality = 90)

    @Test
    fun `page sizes are names or two sides in points`() {
        assertNull(PageLayout.pageSize(options()))
        assertEquals(PageLayout.PageSize.Fit, PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_FIT)))
        assertEquals(PageLayout.A4, PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_A4)))
        assertEquals(PageLayout.LETTER, PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_LETTER)))
        assertEquals(PageLayout.PageSize.Fixed(100f, 200f), PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to floatArrayOf(100f, 200f))))
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to "b5")) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to floatArrayOf(0.5f, 10f))) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to floatArrayOf(10f, 20_000f))) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to floatArrayOf(10f))) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.pageSize(options(PdfContract.KEY_PAGE_SIZE to 595)) }

        val warnings = ArrayList<PdfWarning>()
        assertEquals(PageLayout.A4, PageLayout.documentPageSize(options(), PdfOps.FROM_TEXT, warnings))
        assertTrue(warnings.isEmpty())
        assertEquals(PageLayout.A4, PageLayout.documentPageSize(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_FIT), PdfOps.FROM_TEXT, warnings))
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().text.contains(PdfOps.FROM_TEXT))
        assertWarning(warnings.single(), PdfWarningCodes.FIT_PAGE_SIZE_USED_A4)
        assertEquals(PdfOps.FROM_TEXT, warnings.single().args[PdfWarningCodes.ARG_OP])
    }

    @Test
    fun `margins leave a content box or fail`() {
        assertEquals(PageLayout.Box(36f, 36f, 540f, 720f), PageLayout.contentBox(PageLayout.LETTER, 36f))
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.contentBox(PageLayout.LETTER, 306f) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.contentBox(PageLayout.LETTER, 300f, minimum = 72f) }
        assertEquals(10f, PageLayout.margin(options(PdfContract.KEY_MARGIN to 10.0), 0f))
        assertEquals(36f, PageLayout.margin(options(), 36f))
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.margin(options(PdfContract.KEY_MARGIN to -1f), 0f) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageLayout.margin(options(PdfContract.KEY_MARGIN to Float.NaN), 0f) }
    }

    @Test
    fun `images fit their page, turn it and cover it clipped`() {
        val fit = PageLayout.place(300, 200, PageLayout.PageSize.Fit, PdfContract.FIT_CONTAIN, PdfContract.ORIENTATION_AUTO, 10f)
        assertEquals(PageLayout.Placement(320f, 220f, 10f, 10f, 300f, 200f, null), fit)

        val huge = PageLayout.place(30_000, 100, PageLayout.PageSize.Fit, PdfContract.FIT_CONTAIN, PdfContract.ORIENTATION_AUTO, 0f)
        assertEquals(PageLayout.MAX_PAGE_SIDE, huge.pageWidth, 0.01f)
        assertEquals(48f, huge.pageHeight, 0.01f)

        val turned = PageLayout.place(400, 300, PageLayout.A4, PdfContract.FIT_CONTAIN, PdfContract.ORIENTATION_AUTO, 0f)
        assertEquals(PageLayout.A4.height, turned.pageWidth)
        assertEquals(PageLayout.A4.width, turned.pageHeight)
        assertEquals(turned.pageHeight, turned.height, 0.01f)
        assertEquals((turned.pageWidth - turned.width) / 2, turned.x, 0.01f)
        assertNull(turned.clip)

        val portrait = PageLayout.place(400, 300, PageLayout.A4, PdfContract.FIT_CONTAIN, PdfContract.ORIENTATION_PORTRAIT, 0f)
        assertEquals(PageLayout.A4.width, portrait.pageWidth)
        assertEquals(PageLayout.A4.width, portrait.width, 0.01f)

        val cover = PageLayout.place(400, 300, PageLayout.A4, PdfContract.FIT_COVER, PdfContract.ORIENTATION_PORTRAIT, 20f)
        assertEquals(PageLayout.Box(20f, 20f, PageLayout.A4.width - 40f, PageLayout.A4.height - 40f), cover.clip)
        assertEquals(PageLayout.A4.height - 40f, cover.height, 0.01f)
        assertTrue(cover.width > cover.clip!!.width)
    }

    @Test
    fun `the JPEG reader finds size, components, Adobe marker and EXIF orientation`() {
        val plain = JpegInfo.read(jpeg(30, 20))
        assertNotNull(plain)
        assertEquals(30, plain!!.width)
        assertEquals(20, plain.height)
        assertEquals(3, plain.components)
        assertEquals(8, plain.precision)
        assertEquals(1, plain.orientation)
        assertFalse(plain.adobe)
        assertTrue(plain.embeddable)

        assertEquals(1, JpegInfo.read(jpeg(8, 8, gray = true))!!.components)
        for (orientation in 1..8) assertEquals(orientation, JpegInfo.read(withExif(jpeg(30, 20), orientation))!!.orientation)
        assertEquals(1, JpegInfo.read(withExif(jpeg(30, 20), 9))!!.orientation)

        val cmyk = JpegInfo.read(cmykHeader(40, 50, adobe = true))!!
        assertEquals(4, cmyk.components)
        assertTrue(cmyk.adobe)
        assertTrue(cmyk.embeddable)
        assertFalse(JpegInfo.read(cmykHeader(40, 50, adobe = false, precision = 12))!!.embeddable)

        assertNull(JpegInfo.read(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertNull(JpegInfo.read(jpeg(30, 20).copyOfRange(0, 6)))
    }

    @Test
    fun `orientation matrices draw the stored corners where EXIF puts them`() {
        val x = 10f
        val y = 20f
        val width = 100f
        val height = 50f
        // Image space: (0, 1) is the first stored pixel (top left), (1, 1) the end of the first row (top right).
        // zh-CN: 图像空间: (0, 1) 是存储的第一个像素 (左上), (1, 1) 是第一行的末尾 (右上).
        val topLeft = x to y + height
        val topRight = x + width to y + height
        val bottomLeft = x to y
        val bottomRight = x + width to y
        val expected = mapOf(
            1 to (topLeft to topRight),
            2 to (topRight to topLeft),
            3 to (bottomRight to bottomLeft),
            4 to (bottomLeft to bottomRight),
            5 to (topLeft to bottomLeft),
            6 to (topRight to bottomRight),
            7 to (bottomRight to topRight),
            8 to (bottomLeft to topLeft),
        )
        for ((orientation, corners) in expected) {
            val matrix = ImagesToPdf.orientationMatrix(orientation, x, y, width, height)
            assertEquals("orientation $orientation, first pixel", corners.first, matrix.map(0f, 1f))
            assertEquals("orientation $orientation, end of first row", corners.second, matrix.map(1f, 1f))
        }
    }

    private fun Matrix.map(u: Float, v: Float): Pair<Float, Float> =
        (getValue(0, 0) * u + getValue(1, 0) * v + getValue(2, 0)) to (getValue(0, 1) * u + getValue(1, 1) * v + getValue(2, 1))

    @Test
    fun `oversized images are subsampled by powers of two`() {
        assertEquals(1, ImagesToPdf.sampleSize(4000, 4000))
        assertEquals(2, ImagesToPdf.sampleSize(4001, 4000))
        assertEquals(8, ImagesToPdf.sampleSize(20_000, 20_000))
    }

    @Test
    fun `fromImages embeds JPEGs as stored with one page per image`() {
        val landscape = jpeg(300, 200)
        val rotated = withExif(jpeg(300, 200), 6)
        val gray = jpeg(40, 40, gray = true)
        val cmyk = cmykHeader(60, 80, adobe = true)
        val warnings = ArrayList<PdfWarning>()
        val saved = PDDocument().use { document ->
            ImagesToPdf.addPage(document, landscape, imageRequest(), 1, warnings)
            ImagesToPdf.addPage(document, rotated, imageRequest(), 2, warnings)
            ImagesToPdf.addPage(document, landscape, imageRequest(PageLayout.A4), 3, warnings)
            ImagesToPdf.addPage(document, gray, imageRequest(PageLayout.LETTER, margin = 36f), 4, warnings)
            ImagesToPdf.addPage(document, cmyk, imageRequest(), 5, warnings)
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        assertTrue(warnings.isEmpty())
        PDDocument.load(saved).use { document ->
            assertEquals(5, document.numberOfPages)
            val sizes = document.pages.map { it.mediaBox.width to it.mediaBox.height }
            assertEquals(300f to 200f, sizes[0])
            assertEquals(200f to 300f, sizes[1])
            assertEquals(PageLayout.A4.height to PageLayout.A4.width, sizes[2])
            assertEquals(PageLayout.LETTER.width to PageLayout.LETTER.height, sizes[3])
            assertEquals(60f to 80f, sizes[4])

            val images = document.pages.map { page ->
                val resources = page.resources
                resources.xObjectNames.map { resources.getXObject(it) as PDImageXObject }.single()
            }
            images.forEach { assertEquals(COSName.DCT_DECODE, it.cosObject.getDictionaryObject(COSName.FILTER)) }
            assertArrayEquals(landscape, (images[0].cosObject as COSStream).createRawInputStream().use { it.readBytes() })
            assertArrayEquals(rotated, (images[1].cosObject as COSStream).createRawInputStream().use { it.readBytes() })
            assertEquals(COSName.DEVICEGRAY, images[3].cosObject.getDictionaryObject(COSName.COLORSPACE))
            assertEquals(COSName.DEVICECMYK, images[4].cosObject.getDictionaryObject(COSName.COLORSPACE))
            assertEquals(8, (images[4].cosObject.getDictionaryObject(COSName.DECODE) as COSArray).size())
        }
    }

    @Test
    fun `fromImages requests check quality, sizes and input counts`() {
        val lossless = ImagesToPdf.request(options(PdfContract.KEY_QUALITY to PdfContract.QUALITY_LOSSLESS), 1)
        assertNull(lossless.quality)
        assertEquals(PageLayout.PageSize.Fit, lossless.pageSize)
        assertEquals(PdfContract.FIT_CONTAIN, lossless.fit)
        assertEquals(PdfContract.ORIENTATION_AUTO, lossless.orientation)
        assertEquals(PdfContract.DEFAULT_QUALITY, ImagesToPdf.request(options(), 2).quality)
        assertEquals(50, ImagesToPdf.request(options(PdfContract.KEY_QUALITY to 50), 1).quality)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { ImagesToPdf.request(options(PdfContract.KEY_QUALITY to "best"), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { ImagesToPdf.request(options(PdfContract.KEY_QUALITY to 0), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { ImagesToPdf.request(options(PdfContract.KEY_FIT to "stretch"), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { ImagesToPdf.request(options(), 0) }
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { ImagesToPdf.request(options(), PdfContract.MAX_RUN_INPUTS + 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) {
            ImagesToPdf.request(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_A4, PdfContract.KEY_MARGIN to 400f), 1)
        }
    }

    @Test
    fun `fromText requests check sizes, spacing and inputs`() {
        val request = TextToPdf.request(options(PdfContract.KEY_TITLE to "Notes"), 1)
        assertEquals(PageLayout.A4, request.page)
        assertEquals(PageLayout.DEFAULT_DOCUMENT_MARGIN, request.margin)
        assertEquals(TextToPdf.DEFAULT_FONT_SIZE, request.fontSize)
        assertEquals(1f, request.lineSpacing)
        assertEquals("Notes", request.title)
        assertFalse(request.hasFont)
        assertTrue(TextToPdf.request(options(), 2).hasFont)
        assertEquals(1, TextToPdf.request(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_FIT), 1).warnings.size)
        assertEquals(1.5f, TextToPdf.request(options(PdfContract.KEY_LINE_SPACING to 1.5), 1).lineSpacing)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { TextToPdf.request(options(PdfContract.KEY_FONT_SIZE to 0f), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { TextToPdf.request(options(PdfContract.KEY_FONT_SIZE to 2000f), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { TextToPdf.request(options(PdfContract.KEY_LINE_SPACING to 0.0), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { TextToPdf.request(options(PdfContract.KEY_FONT_SIZE to 600f), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { TextToPdf.request(options(), 0) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { TextToPdf.request(options(), 3) }
    }

    @Test
    fun `fromHtml requests check the timeout and inputs`() {
        assumeTrue("generate.html is not available on this JVM", HtmlToPdf.isAvailable)
        val request = HtmlToPdf.request(options(PdfContract.KEY_BASE_URL to "https://example.org/", PdfContract.KEY_TIMEOUT to 5_000L), 1)
        assertEquals(PageLayout.A4, request.page)
        assertEquals("https://example.org/", request.baseUrl)
        assertEquals(5_000L, request.timeoutMs)
        assertNull(HtmlToPdf.request(options(PdfContract.KEY_BASE_URL to ""), 1).baseUrl)
        assertNull(HtmlToPdf.request(options(), 1).timeoutMs)
        // JavaScript runs unless the request turns it off (appendix D Q35 (c), Q61). zh-CN: 除非请求关闭, 否则执行 JavaScript.
        assertEquals(PdfContract.DEFAULT_JAVA_SCRIPT, HtmlToPdf.request(options(), 1).javaScript)
        assertTrue(HtmlToPdf.request(options(PdfContract.KEY_JAVA_SCRIPT to true), 1).javaScript)
        assertFalse(HtmlToPdf.request(options(PdfContract.KEY_JAVA_SCRIPT to false), 1).javaScript)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { HtmlToPdf.request(options(PdfContract.KEY_JAVA_SCRIPT to "false"), 1) }
        assertEquals(1, HtmlToPdf.request(options(PdfContract.KEY_PAGE_SIZE to PdfContract.PAGE_SIZE_FIT), 1).warnings.size)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { HtmlToPdf.request(options(PdfContract.KEY_TIMEOUT to 0L), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { HtmlToPdf.request(options(), 2) }
    }

    @Test
    fun `text is normalized and cut into chunks at line breaks, spaces or the limit`() {
        val bom = Char(0xFEFF).toString()
        assertEquals("a\nb\nc\n", TextToPdf.normalize(bom + "a\r\nb\rc\n"))
        assertEquals(listOf("short"), TextToPdf.chunks("short", 10))
        assertEquals(listOf("12345678", "90abcdefgh"), TextToPdf.chunks("12345678\n90abcdefgh", 10))
        assertEquals(listOf("abcdef ", "ghijklmnop"), TextToPdf.chunks("abcdef ghijklmnop", 10))
        assertEquals(listOf("abcdefghij", "klmnopq"), TextToPdf.chunks("abcdefghijklmnopq", 10))
        val emoji = String(Character.toChars(0x1F600))
        assertEquals(listOf("a".repeat(9), emoji + "bb"), TextToPdf.chunks("a".repeat(9) + emoji + "bb", 10))
        val long = "word ".repeat(10_000)
        val pieces = TextToPdf.chunks(long)
        assertTrue(pieces.all { it.length <= TextToPdf.CHUNK_CHARS })
        assertEquals(long, pieces.joinToString(""))
    }

    @Test
    fun `fonts are recognized by their magic number`() {
        fun font(vararg head: Int) = ByteArray(16).also { bytes -> head.forEachIndexed { index, value -> bytes[index] = value.toByte() } }
        assertTrue(TextToPdf.isFont(font(0, 1, 0, 0)))
        assertTrue(TextToPdf.isFont("OTTO".toByteArray() + ByteArray(12)))
        assertTrue(TextToPdf.isFont("true".toByteArray() + ByteArray(12)))
        assertTrue(TextToPdf.isFont("ttcf".toByteArray() + ByteArray(12)))
        assertFalse(TextToPdf.isFont("wOFF".toByteArray() + ByteArray(12)))
        assertFalse(TextToPdf.isFont("OTTO".toByteArray()))
    }

}

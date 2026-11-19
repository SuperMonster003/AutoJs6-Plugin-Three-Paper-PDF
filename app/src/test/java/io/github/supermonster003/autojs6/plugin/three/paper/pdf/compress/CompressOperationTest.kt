package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.JpegData
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.write.CompactWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.write.RenderCheck
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO

/**
 * `compress` on the JVM (roadmap P6.6, D55, D57, D64): the request and its ranges, the effective resolution and the
 * target size, which images are found and at what largest size, which are left alone and why, in-place replacement
 * with an area-averaged lossless soft mask, keeping an image whose result is not smaller, deflating streams without a
 * filter, removing metadata and thumbnails, the size warning of D57, and every fixture written back after compressing.
 * The platform JPEG encoder does not exist on the JVM: [FakeCodec] encodes flat JPEGs with `javax.imageio`; the real
 * codec runs in `CompressOperationDeviceTest`.
 * zh-CN: JVM 上的 `compress` (路线图 P6.6, D55, D57, D64): 请求及其取值范围, 有效分辨率与目标尺寸, 找到哪些图片及其最大尺寸, 哪些
 * 图片不处理及原因, 原地替换并按面积平均无损缩放软蒙版, 结果不更小时保留原图, 给无过滤器的流加 Flate, 删除元数据与缩略图, D57 的
 * 体积警告, 以及每个夹具压缩后写回. JVM 上没有平台 JPEG 编码器: [FakeCodec] 用 `javax.imageio` 编码纯色 JPEG; 真实编码器在
 * `CompressOperationDeviceTest` 中运行.
 */
class CompressOperationTest {

    @get:Rule
    val temporary = TemporaryFolder()

    /** Flat JPEGs of the asked size, or [bytes] when given; records the calls. zh-CN: 按要求尺寸编码纯色 JPEG (或返回 [bytes]); 记录调用. */
    private class FakeCodec(private val bytes: ((Int, Int) -> ByteArray)? = null) : ImageCodec {

        val calls = ArrayList<String>()

        override fun jpeg(image: PDImageXObject, width: Int, height: Int, quality: Int): ByteArray {
            calls += "${image.width}x${image.height}->${width}x$height@$quality"
            return bytes?.invoke(width, height) ?: jpeg(width, height, noise = false)
        }

    }

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun code(block: () -> Unit): String = assertThrows(PdfFailure::class.java) { block() }.code

    @Test
    fun `the request reads the numbers of the host and refuses values out of range`() {
        assertEquals(
            CompressOperation.Request(null, PdfContract.DEFAULT_IMAGE_DPI, PdfContract.DEFAULT_IMAGE_QUALITY, removeMetadata = false, objectStreams = true),
            CompressOperation.request(options(), 1),
        )
        assertEquals(
            CompressOperation.Request("owner", 96, 60, removeMetadata = true, objectStreams = false),
            CompressOperation.request(
                options(
                    PdfContract.KEY_PASSWORD to "owner",
                    PdfContract.KEY_IMAGE_DPI to 96,
                    PdfContract.KEY_IMAGE_QUALITY to 60,
                    PdfContract.KEY_REMOVE_METADATA to true,
                    PdfContract.KEY_OBJECT_STREAMS to false,
                ),
                1,
            ),
        )
        assertEquals(36, CompressOperation.request(options(PdfContract.KEY_IMAGE_DPI to 36), 1).imageDpi)
        assertEquals(1200, CompressOperation.request(options(PdfContract.KEY_IMAGE_DPI to 1200), 1).imageDpi)
        for (dpi in listOf(35, 1201, 0, -150)) {
            assertEquals("imageDpi $dpi", PdfErrorCodes.INVALID_ARGUMENT, code { CompressOperation.request(options(PdfContract.KEY_IMAGE_DPI to dpi), 1) })
        }
        for (quality in listOf(0, 101)) {
            assertEquals("imageQuality $quality", PdfErrorCodes.INVALID_ARGUMENT, code { CompressOperation.request(options(PdfContract.KEY_IMAGE_QUALITY to quality), 1) })
        }
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { CompressOperation.request(options(PdfContract.KEY_IMAGE_DPI to "150"), 1) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { CompressOperation.request(options(), 2) })
    }

    @Test
    fun `the target size brings the axis with fewer pixels per inch to imageDpi`() {
        // A 300 dpi scan of 2480 x 3508 pixels to 150 dpi. zh-CN: 2480 x 3508 像素的 300 dpi 扫描件降到 150 dpi.
        assertEquals(ImageCompression.Size(1240, 1754), ImageCompression.target(2480, 3508, 595.2, 841.92, 150))
        // On A4 itself the scan is 299.96 dpi, so the target keeps one pixel more. zh-CN: 在 A4 上它是 299.96 dpi, 目标多保留一个像素.
        assertEquals(ImageCompression.Size(1241, 1755), ImageCompression.target(2480, 3508, 595.2756, 841.8898, 150))
        assertNull("150 dpi is not above 150", ImageCompression.target(1240, 1754, 595.2, 841.92, 150))
        // 1000 x 100 pixels over one inch square: 1000 dpi across, 100 dpi down, so it is not above 150 on both axes.
        // zh-CN: 1000 x 100 像素画在一平方英寸上: 横向 1000 dpi, 纵向 100 dpi, 并非两个方向都高于 150.
        assertNull(ImageCompression.target(1000, 100, 72.0, 72.0, 150))
        assertEquals(ImageCompression.Size(150, 15), ImageCompression.target(1000, 100, 72.0, 7.2, 150))
        assertNull("never drawn with a size", ImageCompression.target(1000, 1000, 0.0, 72.0, 150))
        assertNull(ImageCompression.target(1000, 1000, Double.NaN, 72.0, 150))
        assertEquals(ImageCompression.Size(36, 36), ImageCompression.target(1200, 1200, 72.0, 72.0, 36))
    }

    @Test
    fun `images are found once with the largest size they are drawn at`() {
        PDDocument().use { document ->
            val image = rgbImage(document, 400, 300)
            val unused = rgbImage(document, 10, 10)
            page(document, "q 240 0 0 180 72 500 cm /Im Do Q\nq 0 180 -240 0 300 300 cm /Im Do Q\n", "Im" to image, "Unused" to unused)
            val form = PDFormXObject(document).apply {
                bBox = PDRectangle(0f, 0f, 200f, 200f)
                resources = PDResources().apply { put(COSName.getPDFName("Im"), image) }
                stream.createOutputStream().use { it.write("q 100 0 0 50 0 0 cm /Im Do Q\nBI /W 2 /H 2 /CS /G /BPC 8 ID abcd EI\n".toByteArray(Charsets.ISO_8859_1)) }
            }
            val second = PDPage(PDRectangle.A4).apply { resources = PDResources().apply { put(COSName.getPDFName("Fm"), form) } }
            document.addPage(second)
            content(document, second, "q 3 0 0 3 10 10 cm /Fm Do Q\n")
            val uses = ImageUses.collect(document, StopSignal.NEVER)
            assertEquals("the unused image and the inline image are not image uses", 1, uses.size)
            val use = uses.single()
            assertEquals(1, use.page)
            assertEquals("widest: the form draws it 100 wide three times as large", 300.0, use.width, 1e-6)
            assertEquals("tallest: the rotated draw", 240.0, use.height, 1e-6)
        }
    }

    @Test
    fun `images outside the scope of D64 are left alone with their reason`() {
        PDDocument().use { document ->
            fun flate(width: Int, height: Int, bits: Int, colorSpace: PDColorSpace, bytes: Int) =
                PDImageXObject(document, ByteArrayInputStream(deflate(ByteArray(bytes))), COSName.FLATE_DECODE, width, height, bits, colorSpace)

            val oneBit = flate(800, 800, 1, PDDeviceGray.INSTANCE, 800 * 100)
            assertEquals(ImageCompression.Skip.ONE_BIT, ImageCompression.classify(oneBit))
            val ccitt = PDImageXObject(document, ByteArrayInputStream(ByteArray(16)), COSName.CCITTFAX_DECODE, 800, 800, 1, PDDeviceGray.INSTANCE)
            assertEquals(ImageCompression.Skip.ONE_BIT, ImageCompression.classify(ccitt))
            val stencil = flate(800, 800, 1, PDDeviceGray.INSTANCE, 800 * 100).apply {
                cosObject.setBoolean(COSName.IMAGE_MASK, true)
                cosObject.removeItem(COSName.COLORSPACE)
            }
            assertEquals(ImageCompression.Skip.STENCIL, ImageCompression.classify(stencil))
            val jpx = PDImageXObject(document, ByteArrayInputStream(ByteArray(64) { it.toByte() }), COSName.JPX_DECODE, 800, 800, 8, PDDeviceRGB.INSTANCE)
            assertEquals(ImageCompression.Skip.UNDECODABLE, ImageCompression.classify(jpx))
            val cmyk = flate(100, 100, 8, PDDeviceRGB.INSTANCE, 100 * 100 * 4).apply { cosObject.setItem(COSName.COLORSPACE, COSName.DEVICECMYK) }
            assertEquals(ImageCompression.Skip.UNSUPPORTED, ImageCompression.classify(cmyk))
            val deep = flate(100, 100, 16, PDDeviceRGB.INSTANCE, 100 * 100 * 6)
            assertEquals(ImageCompression.Skip.UNSUPPORTED, ImageCompression.classify(deep))
            val colourKey = flate(100, 100, 8, PDDeviceRGB.INSTANCE, 100 * 100 * 3).apply {
                cosObject.setItem(COSName.MASK, COSArray().apply { repeat(6) { add(COSInteger.get(0)) } })
            }
            assertEquals(ImageCompression.Skip.MASKED, ImageCompression.classify(colourKey))
            val matte = flate(100, 100, 8, PDDeviceRGB.INSTANCE, 100 * 100 * 3).apply {
                val mask = flate(100, 100, 8, PDDeviceGray.INSTANCE, 100 * 100)
                mask.cosObject.setItem(COSName.getPDFName("Matte"), COSArray().apply { repeat(3) { add(COSFloat(0f)) } })
                cosObject.setItem(COSName.SMASK, mask)
            }
            assertEquals(ImageCompression.Skip.MASKED, ImageCompression.classify(matte))
            val inverted = PDImageXObject(document, ByteArrayInputStream(jpeg(64, 64)), COSName.DCT_DECODE, 64, 64, 8, PDDeviceRGB.INSTANCE).apply {
                cosObject.setItem(COSName.DECODE, COSArray().apply { repeat(3) { add(COSInteger.ONE); add(COSInteger.ZERO) } })
            }
            assertEquals(ImageCompression.Skip.UNSUPPORTED, ImageCompression.classify(inverted))
            assertNull(ImageCompression.classify(flate(100, 100, 8, PDDeviceRGB.INSTANCE, 100 * 100 * 3)))
            assertNull(ImageCompression.classify(flate(100, 100, 8, PDDeviceGray.INSTANCE, 100 * 100)))
            assertNull(ImageCompression.classify(PDImageXObject(document, ByteArrayInputStream(jpeg(64, 64)), COSName.DCT_DECODE, 64, 64, 8, PDDeviceRGB.INSTANCE)))
            val indexed = flate(100, 100, 8, PDDeviceRGB.INSTANCE, 100 * 100).apply {
                cosObject.setItem(COSName.COLORSPACE, COSArray().apply {
                    add(COSName.INDEXED)
                    add(COSName.DEVICERGB)
                    add(COSInteger.get(1))
                    add(COSString(byteArrayOf(0, 0, 0, -1, -1, -1)))
                })
            }
            assertEquals("PDFBox-Android reads indexed samples as RGB", ImageCompression.Skip.UNSUPPORTED, ImageCompression.classify(indexed))
            val iccJpeg = PDImageXObject(document, ByteArrayInputStream(jpeg(64, 64)), COSName.DCT_DECODE, 64, 64, 8, PDDeviceRGB.INSTANCE).apply {
                val profile = document.document.createCOSStream().apply { setInt(COSName.N, 3) }
                cosObject.setItem(COSName.COLORSPACE, COSArray().apply {
                    add(COSName.ICCBASED)
                    add(profile)
                })
            }
            assertNull("the platform decodes an ICC based JPEG by its components", ImageCompression.classify(iccJpeg))
            val iccRaw = flate(100, 100, 8, PDDeviceRGB.INSTANCE, 100 * 100 * 3).apply { cosObject.setItem(COSName.COLORSPACE, iccJpeg.cosObject.getDictionaryObject(COSName.COLORSPACE)) }
            assertEquals(ImageCompression.Skip.UNSUPPORTED, ImageCompression.classify(iccRaw))
            assertEquals(ImageCompression.Skip.MASKED, ImageCompression.classify(rgbImage(document, 100, 100, mask = true)) { true })
            assertNull(ImageCompression.classify(rgbImage(document, 100, 100, mask = true)))
        }
    }

    @Test
    fun `recompressed images are replaced in place and their soft masks shrink losslessly`() {
        PDDocument().use { document ->
            // 400 x 400 noise drawn 96 pt square: 300 dpi; the mask is opaque on the left half.
            // zh-CN: 400 x 400 的噪点图画成 96 pt 见方: 300 dpi; 蒙版左半边不透明.
            val photo = rgbImage(document, 400, 400, noise = true, mask = true)
            val lineArt = PDImageXObject(document, ByteArrayInputStream(deflate(ByteArray(800 * 100))), COSName.FLATE_DECODE, 800, 800, 1, PDDeviceGray.INSTANCE)
            val jpx = PDImageXObject(document, ByteArrayInputStream(ByteArray(64) { it.toByte() }), COSName.JPX_DECODE, 800, 800, 8, PDDeviceRGB.INSTANCE)
            val small = rgbImage(document, 100, 100, noise = true)
            page(
                document,
                "q 96 0 0 96 72 600 cm /Photo Do Q\nq 96 0 0 96 200 600 cm /Art Do Q\nq 96 0 0 96 72 400 cm /Jpx Do Q\nq 96 0 0 96 200 400 cm /Small Do Q\n",
                "Photo" to photo, "Art" to lineArt, "Jpx" to jpx, "Small" to small,
            )
            page(document, "q 48 0 0 48 72 600 cm /Photo Do Q\n", "Photo" to photo)
            val artBefore = raw(lineArt.cosObject)
            val codec = FakeCodec()
            val pages = ArrayList<Int>()
            val summary = ImageCompression.apply(ImageUses.collect(document, StopSignal.NEVER), 150, 75, codec, StopSignal.NEVER) { pages += it }
            assertEquals(4, summary.total)
            assertEquals(1, summary.recompressed)
            assertEquals(3, summary.skippedCount)
            assertEquals(mapOf(ImageCompression.Skip.ONE_BIT to 1, ImageCompression.Skip.UNDECODABLE to 1, ImageCompression.Skip.NOT_ABOVE_DPI to 1), summary.skipped)
            assertEquals("the photo is decoded once", listOf("400x400->200x200@75"), codec.calls)
            assertEquals(listOf(1, 1, 1, 1), pages)
            assertEquals(1, summary.warnings.size)
            assertTrue(summary.warnings.single(), summary.warnings.single().startsWith("1 image cannot be decoded"))
            val stream = photo.cosObject
            assertEquals(COSName.DCT_DECODE, stream.getDictionaryObject(COSName.FILTER))
            assertEquals(200, stream.getInt(COSName.WIDTH))
            assertEquals(200, stream.getInt(COSName.HEIGHT))
            assertEquals(COSName.DEVICERGB, stream.getDictionaryObject(COSName.COLORSPACE))
            val jpeg = JpegData.of(raw(stream))
            assertEquals(200, jpeg?.width)
            val mask = stream.getDictionaryObject(COSName.SMASK) as COSStream
            assertEquals(COSName.FLATE_DECODE, mask.getDictionaryObject(COSName.FILTER))
            assertEquals(200, mask.getInt(COSName.WIDTH))
            assertEquals(200, mask.getInt(COSName.HEIGHT))
            val alpha = mask.createInputStream().use { it.readBytes() }
            assertEquals(200 * 200, alpha.size)
            assertEquals(255, alpha[100 * 200 + 50].toInt() and 0xFF)
            assertEquals(0, alpha[100 * 200 + 150].toInt() and 0xFF)
            assertEquals("the edge column averages two opaque source columns", 255, alpha[100 * 200 + 99].toInt() and 0xFF)
            assertArrayEquals("the 1-bit image keeps its bytes", artBefore, raw(lineArt.cosObject))
            assertEquals(COSName.JPX_DECODE, jpx.cosObject.getDictionaryObject(COSName.FILTER))
        }
    }

    @Test
    fun `an image whose result is not smaller keeps its stream`() {
        PDDocument().use { document ->
            val flat = rgbImage(document, 400, 400, noise = false)
            page(document, "q 96 0 0 96 72 600 cm /Flat Do Q\n", "Flat" to flat)
            val before = raw(flat.cosObject)
            val summary = ImageCompression.apply(ImageUses.collect(document, StopSignal.NEVER), 150, 75, FakeCodec { w, h -> jpeg(w, h, noise = true) }, StopSignal.NEVER)
            assertEquals(mapOf(ImageCompression.Skip.NOT_SMALLER to 1), summary.skipped)
            assertArrayEquals(before, raw(flat.cosObject))
            assertEquals(COSName.FLATE_DECODE, flat.cosObject.getDictionaryObject(COSName.FILTER))
            assertEquals(400, flat.cosObject.getInt(COSName.WIDTH))
        }
    }

    @Test
    fun `area averaging keeps flat areas flat and weighs partial pixels`() {
        val square = byteArrayOf(0, 0, 100, 100, 0, 0, 100, 100, 50, 50, 10, 10, 50, 50, 10, 10)
        assertArrayEquals(byteArrayOf(0, 100, 50, 10), ImageCompression.resampleGrey(square, 4, 4, 2, 2))
        val flat = ByteArray(9) { 77 }
        assertArrayEquals(ByteArray(4) { 77 }, ImageCompression.resampleGrey(flat, 3, 3, 2, 2))
        // 3 -> 2: the middle source pixel counts half for each target pixel. zh-CN: 3 -> 2: 中间的源像素对每个目标像素各算一半.
        val row = byteArrayOf(0, 90, 120)
        assertArrayEquals(byteArrayOf(30, 110), ImageCompression.resampleGrey(row, 3, 1, 2, 1))
        assertThrows(IllegalArgumentException::class.java) { ImageCompression.resampleGrey(row, 3, 1, 4, 1) }
    }

    @Test
    fun `streams without a filter are deflated and metadata goes only when asked`() {
        val bytes = PdfFixtures.withXmp()
        for (remove in listOf(false, true)) {
            PDDocument.load(bytes).use { document ->
                val page = document.getPage(0)
                val plain = document.document.createCOSStream().apply { createRawOutputStream().use { it.write(("q 1 0 0 1 0 0 cm Q\n".repeat(200)).toByteArray()) } }
                page.cosObject.setItem(COSName.CONTENTS, COSArray().apply {
                    add(page.cosObject.getDictionaryObject(COSName.CONTENTS))
                    add(plain)
                })
                page.cosObject.setItem(COSName.THUMB, document.document.createCOSStream())
                val text = shown(document.getPage(0))
                val summary = StreamCleanup.apply(document, remove, StopSignal.NEVER)
                assertTrue("the plain content stream is deflated", summary.deflatedStreams >= 1)
                assertEquals(COSName.FLATE_DECODE, plain.getDictionaryObject(COSName.FILTER))
                assertEquals("q 1 0 0 1 0 0 cm Q\n".repeat(200), plain.createInputStream().use { String(it.readBytes()) })
                assertEquals(text, shown(document.getPage(0)))
                val metadata = document.documentCatalog.cosObject.getDictionaryObject(COSName.METADATA) as? COSStream
                if (remove) {
                    assertNull(metadata)
                    assertFalse(page.cosObject.containsKey(COSName.THUMB))
                    assertEquals(1, summary.removedMetadata)
                    assertEquals(1, summary.removedThumbnails)
                } else {
                    assertNotNull(metadata)
                    assertNull("XMP stays unfiltered", metadata!!.getDictionaryObject(COSName.FILTER))
                    assertTrue(page.cosObject.containsKey(COSName.THUMB))
                }
            }
        }
    }

    @Test
    fun `removeMetadata leaves producer alone in the document information`() {
        PDDocument.load(PdfFixtures.withXmp()).use { document ->
            val request = CompressOperation.request(options(PdfContract.KEY_REMOVE_METADATA to true), 1)
            CompressOperation.compress(document, request, FakeCodec(), StopSignal.NEVER)
            val output = ByteArrayOutputStream()
            CompactWriting.write(document, output, temporary.root, render = RenderCheck.NONE, onlyProducer = true)
            PDDocument.load(output.toByteArray()).use { written ->
                assertEquals(setOf(COSName.PRODUCER), written.documentInformation.cosObject.keySet())
                assertNull(written.documentCatalog.metadata)
            }
        }
    }

    @Test
    fun `the size warning appears when the output is not smaller`() {
        assertNull(CompressOperation.sizeWarning(99, 100))
        assertNotNull(CompressOperation.sizeWarning(100, 100))
        assertTrue(CompressOperation.sizeWarning(120, 100)!!.contains("120 bytes"))
    }

    @Test
    fun `every fixture reads back the same after compressing`() {
        for ((name, bytes) in PdfFixtures.all()) {
            val password = PdfFixtures.OWNER_PASSWORD.takeIf { name == "encrypted.pdf" }
            val expected = load(bytes, password).use { document -> document.pages.map(::shown) }
            val output = ByteArrayOutputStream()
            load(bytes, password).use { document ->
                val changes = CompressOperation.compress(document, CompressOperation.request(options(), 1), FakeCodec(), StopSignal.NEVER)
                assertTrue(name, changes.images.total >= changes.images.recompressed)
                val result = CompactWriting.write(document, output, temporary.root, render = RenderCheck.NONE)
                assertTrue("$name: ${result.warnings}", result.objectStreams)
            }
            PDDocument.load(output.toByteArray()).use { written ->
                assertEquals(name, expected, written.pages.map(::shown))
            }
        }
    }

    @Test
    fun `the scanned fixture has its pages recompressed`() {
        PDDocument.load(PdfFixtures.scannedMultiPage()).use { document ->
            val codec = FakeCodec()
            val changes = CompressOperation.compress(document, CompressOperation.request(options(), 1), codec, StopSignal.NEVER)
            assertEquals(PdfFixtures.SCAN_PAGES, changes.images.total)
            assertEquals(PdfFixtures.SCAN_PAGES, changes.images.recompressed)
            assertTrue(codec.calls.toString(), codec.calls.all { it.endsWith("@${PdfContract.DEFAULT_IMAGE_QUALITY}") })
        }
    }

    private fun load(bytes: ByteArray, password: String?): PDDocument = if (password == null) PDDocument.load(bytes) else PDDocument.load(bytes, password)

    /**
     * Shown strings of the page content: PDFTextStripper needs the Android resource loader, which the JVM lacks.
     * zh-CN: 页面内容中显示的字符串: PDFTextStripper 需要 JVM 上没有的 Android 资源加载器.
     */
    private fun shown(page: PDPage): List<String> {
        val result = ArrayList<String>()
        val operands = ArrayList<Any>()
        for (token in PDFStreamParser(page).apply { parse() }.tokens) {
            if (token !is Operator) {
                operands += token
                continue
            }
            when (token.name) {
                "Tj", "'", "\"" -> (operands.lastOrNull() as? COSString)?.let { result += it.string }
                "TJ" -> (operands.lastOrNull() as? COSArray)?.forEach { if (it is COSString) result += it.string }
            }
            operands.clear()
        }
        return result
    }

    private fun page(document: PDDocument, content: String, vararg images: Pair<String, PDImageXObject>): PDPage {
        val page = PDPage(PDRectangle.A4)
        page.resources = PDResources().apply { images.forEach { (name, image) -> put(COSName.getPDFName(name), image) } }
        document.addPage(page)
        content(document, page, content)
        return page
    }

    private fun content(document: PDDocument, page: PDPage, content: String) {
        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(content.toByteArray(Charsets.ISO_8859_1)) }
        page.setContents(stream)
    }

    /** An RGB image, noisy or flat, with a soft mask opaque on its left half when [mask]. zh-CN: RGB 图片, 可带左半边不透明的软蒙版. */
    private fun rgbImage(document: PDDocument, width: Int, height: Int, noise: Boolean = false, mask: Boolean = false): PDImageXObject {
        val random = Random(7)
        val rgb = ByteArray(width * height * 3) { if (noise) random.nextInt(256).toByte() else 0x60 }
        val image = PDImageXObject(document, ByteArrayInputStream(deflate(rgb)), COSName.FLATE_DECODE, width, height, 8, PDDeviceRGB.INSTANCE)
        if (mask) {
            val alpha = ByteArray(width * height) { index -> if (index % width < width / 2) -1 else 0 }
            val soft = PDImageXObject(document, ByteArrayInputStream(deflate(alpha)), COSName.FLATE_DECODE, width, height, 8, PDDeviceGray.INSTANCE)
            image.cosObject.setItem(COSName.SMASK, soft)
        }
        return image
    }

    private fun raw(stream: COSStream): ByteArray = stream.createRawInputStream().use { it.readBytes() }

    companion object {

        fun jpeg(width: Int, height: Int, noise: Boolean = false): ByteArray {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val random = Random(11)
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, if (noise) random.nextInt(0x1000000) else 0x606060)
            return ByteArrayOutputStream().also { check(ImageIO.write(image, "jpg", it)) { "no JPEG writer" } }.toByteArray()
        }

        fun deflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(bytes) }
        }.toByteArray()

    }

}

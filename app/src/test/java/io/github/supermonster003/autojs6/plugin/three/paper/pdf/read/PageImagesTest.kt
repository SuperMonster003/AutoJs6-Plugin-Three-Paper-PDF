package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * `planImages` on a generated document (roadmap P2.5): images in drawing order, an XObject drawn twice listed once,
 * images inside forms, inline images, the paging keys and the formats.
 * zh-CN: 在生成的文档上验证 `planImages` (路线图 P2.5): 按绘制顺序列出图片, 绘制两次的 XObject 只列一次, 表单内的图片, 内联图片,
 * 分页键与格式.
 */
class PageImagesTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun jpegImage(document: PDDocument, width: Int, height: Int): PDImageXObject {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
        return PDImageXObject(document, ByteArrayInputStream(bytes), COSName.DCT_DECODE, width, height, 8, PDDeviceRGB.INSTANCE)
    }

    private fun content(document: PDDocument, text: String, data: ByteArray = ByteArray(0), tail: String = ""): PDStream =
        PDStream(document, ByteArrayInputStream(text.toByteArray(Charsets.US_ASCII) + data + tail.toByteArray(Charsets.US_ASCII)))

    /**
     * Page 1: image A twice, a form with A again and B, an inline 2 x 1 gray image; page 2: image B; page 3: nothing.
     * zh-CN: 第 1 页: 图片 A 两次, 一个含 A 与 B 的表单, 一张 2 x 1 灰度内联图片; 第 2 页: 图片 B; 第 3 页: 没有图片.
     */
    private fun sample(): ByteArray = PDDocument().use { document ->
        val a = jpegImage(document, 30, 20)
        val b = jpegImage(document, 7, 9)
        val form = PDFormXObject(content(document, "q 10 0 0 10 0 0 cm /A Do Q q 10 0 0 10 20 0 cm /B Do Q"))
        form.bBox = PDRectangle(0f, 0f, 100f, 100f)
        form.resources = PDResources().apply {
            put(COSName.getPDFName("A"), a)
            put(COSName.getPDFName("B"), b)
        }
        val first = PDPage(PDRectangle.A4)
        first.resources = PDResources().apply {
            put(COSName.getPDFName("A"), a)
            put(COSName.getPDFName("F"), form)
        }
        first.setContents(content(document, "q 30 0 0 20 0 0 cm /A Do Q q 30 0 0 20 50 0 cm /A Do Q /F Do q 2 0 0 1 0 100 cm BI /W 2 /H 1 /CS /G /BPC 8 ID ", byteArrayOf(0, -1), " EI Q"))
        document.addPage(first)
        val second = PDPage(PDRectangle.A4)
        second.resources = PDResources().apply { put(COSName.getPDFName("B"), b) }
        second.setContents(content(document, "q 7 0 0 9 0 0 cm /B Do Q"))
        document.addPage(second)
        document.addPage(PDPage(PDRectangle.A4))
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun items(answer: Map<String, Any?>): List<List<Any>> {
        val array = JSONArray(answer[PdfContract.KEY_ITEMS] as String)
        return List(array.length()) {
            val item = array.getJSONObject(it)
            listOf(
                item.getInt(PdfContract.FIELD_PAGE),
                item.getInt(PdfContract.FIELD_INDEX),
                item.getInt(PdfContract.FIELD_WIDTH),
                item.getInt(PdfContract.FIELD_HEIGHT),
                item.getString(PdfContract.FIELD_FORMAT),
            )
        }
    }

    @Test
    fun `images are listed once per stream in drawing order, inline images and forms included`() {
        PDDocument.load(sample()).use { document ->
            val answer = PageImages.plan(document, options(), StopSignal.NEVER)
            assertEquals(
                listOf(
                    listOf(1, 0, 30, 20, PdfContract.FORMAT_JPEG),
                    listOf(1, 1, 7, 9, PdfContract.FORMAT_JPEG),
                    listOf(1, 2, 2, 1, PdfContract.FORMAT_PNG),
                    listOf(2, 0, 7, 9, PdfContract.FORMAT_JPEG),
                ),
                items(answer),
            )
            assertEquals(false, answer[PdfContract.KEY_HAS_MORE])
        }
    }

    @Test
    fun `pages, offset and limit page through the images`() {
        PDDocument.load(sample()).use { document ->
            val first = PageImages.plan(document, options(PdfContract.KEY_LIMIT to 2), StopSignal.NEVER)
            assertEquals(2, items(first).size)
            assertEquals(true, first[PdfContract.KEY_HAS_MORE])
            assertEquals(2, first[PdfContract.KEY_NEXT_OFFSET])
            val rest = PageImages.plan(document, options(PdfContract.KEY_OFFSET to 2), StopSignal.NEVER)
            assertEquals(listOf(listOf(1, 2), listOf(2, 0)), items(rest).map { it.take(2) })
            val second = PageImages.plan(document, options(PdfContract.KEY_PAGES to "2-3"), StopSignal.NEVER)
            assertEquals(listOf(listOf(2, 0)), items(second).map { it.take(2) })
            assertTrue(items(PageImages.plan(document, options(PdfContract.KEY_PAGES to "3"), StopSignal.NEVER)).isEmpty())
        }
    }

    @Test
    fun `a walk stops when the visitor has seen enough`() {
        PDDocument.load(sample()).use { document ->
            var seen = 0
            assertFalse(PageImages.walk(document.getPage(0), StopSignal.NEVER) { ++seen < 2 })
            assertEquals(2, seen)
            assertTrue(PageImages.walk(document.getPage(2), StopSignal.NEVER) { true })
        }
    }

}

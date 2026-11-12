package io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * The runtime fixtures of roadmap D39 parse back with PDFBox and have the structure later tests rely on.
 * zh-CN: 路线图 D39 的运行时夹具能被 PDFBox 读回, 并具备后续测试依赖的结构.
 */
class PdfFixturesTest {

    private fun <T> open(bytes: ByteArray, password: String? = null, check: (PDDocument) -> T): T =
        (if (password == null) PDDocument.load(bytes) else PDDocument.load(bytes, password)).use(check)

    private fun contentOf(document: PDDocument, index: Int): String =
        document.getPage(index).contentStreams.asSequence().joinToString("") { it.toByteArray().toString(Charsets.ISO_8859_1) }

    @Test
    fun `single page text carries the lines and the metadata`() = open(PdfFixtures.singlePageText()) { document ->
        assertEquals(1, document.numberOfPages)
        assertFalse(document.isEncrypted)
        val info = document.documentInformation
        assertEquals(PdfFixtures.TITLE, info.title)
        assertEquals(PdfFixtures.AUTHOR, info.author)
        assertEquals(PdfFixtures.KEYWORDS, info.keywords)
        assertEquals(2026, info.creationDate.get(java.util.Calendar.YEAR))
        val content = contentOf(document, 0)
        PdfFixtures.TEXT_LINES.forEach { assertTrue(it, content.contains("($it)") || content.contains(it.replace("(", "\\("))) }
        val font = document.getPage(0).resources.cosObject.getCOSDictionary(COSName.FONT).getCOSDictionary(COSName.getPDFName("F1"))
        assertEquals("Helvetica", font.getNameAsString(COSName.BASE_FONT))
    }

    @Test
    fun `outline lists chapters, sections and an entry without a page`() = open(PdfFixtures.multiPageWithOutline()) { document ->
        assertEquals(PdfFixtures.CHAPTERS.size * 2, document.numberOfPages)
        val outline = document.documentCatalog.documentOutline
        assertNotNull(outline)
        val entries = outline.children().toList()
        assertEquals(PdfFixtures.CHAPTERS + PdfFixtures.OUTLINE_WITHOUT_PAGE, entries.map { it.title })
        entries.dropLast(1).forEachIndexed { index, entry ->
            assertEquals(index * 2, document.pages.indexOf((entry.destination as PDPageDestination).page))
            val section = entry.children().single()
            assertEquals("${PdfFixtures.CHAPTERS[index]}, section 1", section.title)
            assertEquals(index * 2 + 1, document.pages.indexOf((section.destination as PDPageDestination).page))
        }
        assertNull(entries.last().destination)
    }

    @Test
    fun `encrypted fixture needs the password and limits permissions`() {
        for (keyLength in listOf(128, 256)) {
            val bytes = PdfFixtures.encrypted(keyLength)
            assertThrows(InvalidPasswordException::class.java) { PDDocument.load(bytes).close() }
            assertThrows(InvalidPasswordException::class.java) { PDDocument.load(bytes, "wrong").close() }
            open(bytes, PdfFixtures.USER_PASSWORD) { document ->
                assertTrue(document.isEncrypted)
                assertEquals(2, document.numberOfPages)
                assertEquals(keyLength, document.encryption.length)
                val permission = document.currentAccessPermission
                assertTrue(permission.canPrint())
                assertFalse(permission.canExtractContent())
                assertFalse(permission.isOwnerPermission)
            }
            open(bytes, PdfFixtures.OWNER_PASSWORD) { document -> assertTrue(document.currentAccessPermission.isOwnerPermission) }
        }
    }

    @Test
    fun `scan has one grey image and no text layer`() = open(PdfFixtures.imageOnly()) { document ->
        assertEquals(1, document.numberOfPages)
        val resources = document.getPage(0).resources
        assertNull(resources.cosObject.getDictionaryObject(COSName.FONT))
        val images = resources.xObjectNames.map { resources.getXObject(it) as PDImageXObject }
        assertEquals(1, images.size)
        assertEquals(PdfFixtures.SCAN_SIZE, images[0].width to images[0].height)
        assertEquals(COSName.DEVICEGRAY, images[0].cosObject.getCOSName(COSName.COLORSPACE))
        assertFalse(contentOf(document, 0).contains("Tj"))
    }

    @Test
    fun `images page holds a stored JPEG and a PNG-like image with a soft mask`() = open(PdfFixtures.withImages()) { document ->
        val resources = document.getPage(0).resources
        val images = resources.xObjectNames.map { resources.getXObject(it) as PDImageXObject }
        assertEquals(2, images.size)
        val jpeg = images.single { it.cosObject.getCOSName(COSName.FILTER) == COSName.DCT_DECODE }
        val png = images.single { it.cosObject.getCOSName(COSName.FILTER) == COSName.FLATE_DECODE }
        assertEquals(PdfFixtures.JPEG_SIZE, jpeg.width to jpeg.height)
        val stored = jpeg.cosObject.createRawInputStream().use { it.readBytes() }
        val decoded = ImageIO.read(ByteArrayInputStream(stored))
        assertEquals(PdfFixtures.JPEG_SIZE, decoded.width to decoded.height)
        assertEquals(PdfFixtures.PNG_SIZE, png.width to png.height)
        val mask = png.cosObject.getCOSStream(COSName.SMASK)
        assertNotNull(mask)
        assertEquals(PdfFixtures.PNG_SIZE.first * PdfFixtures.PNG_SIZE.second, mask.createInputStream().use { it.readBytes() }.size)
        val content = contentOf(document, 0)
        resources.xObjectNames.forEach { assertTrue(content.contains("/${it.name} Do")) }
    }

    @Test
    fun `rotated pages carry their rotation and size`() = open(PdfFixtures.rotatedPages()) { document ->
        assertArrayEquals(PdfFixtures.ROTATIONS.toIntArray(), IntArray(document.numberOfPages) { document.getPage(it).rotation })
        val last = document.getPage(PdfFixtures.ROTATIONS.lastIndex).mediaBox
        assertTrue(last.width > last.height)
        PdfFixtures.ROTATIONS.forEachIndexed { index, rotation -> assertTrue(contentOf(document, index).contains("(Rotate $rotation)")) }
    }

    @Test
    fun `every fixture is a small PDF`() {
        val all = PdfFixtures.all()
        assertEquals(setOf("text.pdf", "outline.pdf", "encrypted.pdf", "scanned.pdf", "images.pdf", "rotated.pdf"), all.keys)
        all.forEach { (name, bytes) ->
            assertTrue(name, bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-")
            assertTrue("$name is ${bytes.size} bytes", bytes.size in 500..200_000)
        }
    }

}

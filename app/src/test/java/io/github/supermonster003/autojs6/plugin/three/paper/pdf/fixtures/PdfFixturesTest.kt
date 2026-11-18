package io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
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
    fun `form fields carry their types, values, defaults and flags`() = open(PdfFixtures.form()) { document ->
        assertEquals(2, document.numberOfPages)
        val form = document.documentCatalog.getAcroForm(null)
        assertNotNull(form)
        assertFalse(form.hasXFA())
        val terminal = form.fieldTree.filterIsInstance<PDTerminalField>()
        assertEquals(PdfFixtures.FORM_FIELDS.keys.toList(), terminal.map { it.fullyQualifiedName })
        val byName = terminal.associateBy { it.fullyQualifiedName }
        PdfFixtures.FORM_FIELDS.forEach { (name, type) ->
            val field = byName.getValue(name)
            val expected = when (type) {
                "text" -> PDTextField::class.java
                "checkbox" -> PDCheckBox::class.java
                "radio" -> PDRadioButton::class.java
                "combo" -> PDComboBox::class.java
                "list" -> PDListBox::class.java
                else -> PDPushButton::class.java
            }
            assertTrue("$name is ${field.javaClass.simpleName}", expected.isInstance(field))
            assertTrue("$name has widgets", field.widgets.isNotEmpty())
            field.widgets.forEach { assertNotNull("$name has an appearance", it.appearance?.normalAppearance) }
        }
        PdfFixtures.FORM_VALUES.forEach { (name, value) ->
            val actual = when (val field = byName.getValue(name)) {
                is PDTextField -> field.value
                is PDCheckBox -> field.value
                is PDRadioButton -> field.value
                is PDComboBox -> field.value
                is PDListBox -> field.value
                else -> error(name)
            }
            assertEquals(name, value, actual)
        }
        PdfFixtures.FORM_DEFAULTS.forEach { (name, value) ->
            val default = byName.getValue(name).cosObject.getDictionaryObject(COSName.DV)
            assertEquals(name, value, (default as? COSName)?.name ?: (default as COSString).string)
        }
        assertEquals(PdfFixtures.FORM_NAME_LABEL, byName.getValue("name").alternateFieldName)
        assertTrue((byName.getValue("notes") as PDTextField).isMultiline)
        assertTrue(byName.getValue("id").isReadOnly)
        assertTrue(byName.getValue("email").isRequired)
        assertTrue((byName.getValue("pin") as PDTextField).isPassword)
        assertEquals(PdfFixtures.FORM_MAX_LENGTH, (byName.getValue("code") as PDTextField).maxLen)
        val colors = byName.getValue("colors") as PDListBox
        assertTrue(colors.isMultiSelect)
        assertEquals(PdfFixtures.FORM_COLORS.map { it.first }, colors.optionsExportValues)
        assertEquals(PdfFixtures.FORM_COLORS.map { it.second }, colors.optionsDisplayValues)
        assertEquals(PdfFixtures.FORM_SIZES.toSet(), (byName.getValue("size") as PDRadioButton).onValues)
        assertEquals(PdfFixtures.FORM_COUNTRIES, (byName.getValue("country") as PDComboBox).optionsDisplayValues)
        fun pages(name: String) = byName.getValue(name).widgets.map { document.pages.indexOf(it.page) + 1 }
        assertEquals(listOf(1, 1, 1), pages("size"))
        assertEquals(listOf(2), pages("colors"))
        assertEquals(listOf(2), pages("address.city"))
    }

    @Test
    fun `multi-page scan holds one 300 dpi grey JPEG per page and no text`() = open(PdfFixtures.scannedMultiPage()) { document ->
        assertEquals(PdfFixtures.SCAN_PAGES, document.numberOfPages)
        for (index in 0 until document.numberOfPages) {
            val resources = document.getPage(index).resources
            assertNull(resources.cosObject.getDictionaryObject(COSName.FONT))
            val image = resources.xObjectNames.map { resources.getXObject(it) as PDImageXObject }.single()
            assertEquals(PdfFixtures.SCAN_300_DPI_SIZE, image.width to image.height)
            assertEquals(COSName.DCT_DECODE, image.cosObject.getCOSName(COSName.FILTER))
            assertEquals(COSName.DEVICEGRAY, image.cosObject.getCOSName(COSName.COLORSPACE))
            assertEquals(300f, image.width / (document.getPage(index).mediaBox.width / 72f), 1f)
            assertFalse(contentOf(document, index).contains("Tj"))
        }
    }

    @Test
    fun `object stream fixture uses an object stream and a cross-reference stream`() {
        val bytes = PdfFixtures.withObjectStreams()
        val raw = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(raw.contains("/Type /ObjStm"))
        assertTrue(raw.contains("/Type /XRef"))
        assertFalse(raw.contains("trailer"))
        assertFalse(raw.contains("\nxref\n"))
        open(bytes) { document ->
            assertEquals(PdfFixtures.OBJECT_STREAM_PAGES.size, document.numberOfPages)
            PdfFixtures.OBJECT_STREAM_PAGES.forEachIndexed { index, text -> assertTrue(contentOf(document, index).contains("($text)")) }
            val entry = document.documentCatalog.documentOutline.children().single()
            assertEquals(PdfFixtures.OBJECT_STREAM_OUTLINE, entry.title)
            assertEquals(0, document.pages.indexOf((entry.destination as PDPageDestination).page))
            assertEquals(PdfFixtures.TITLE, document.documentInformation.title)
            assertEquals(2026, document.documentInformation.creationDate.get(java.util.Calendar.YEAR))
        }
    }

    @Test
    fun `xmp fixture has a metadata stream next to the document information`() = open(PdfFixtures.withXmp()) { document ->
        val metadata = document.documentCatalog.metadata
        assertNotNull(metadata)
        val xml = metadata.exportXMPMetadata().use { it.readBytes() }.toString(Charsets.UTF_8)
        PdfFixtures.XMP_MARKERS.forEach { assertTrue(it, xml.contains(it)) }
        assertEquals(PdfFixtures.TITLE, document.documentInformation.title)
    }

    @Test
    fun `static xfa fixture has fields, a signed and an unsigned signature field`() = open(PdfFixtures.signatureAndXfa()) { document ->
        val form = document.documentCatalog.getAcroForm(null)
        assertTrue(form.hasXFA())
        assertFalse(form.xfaIsDynamic())
        assertEquals(3, form.cosObject.getInt(COSName.SIG_FLAGS))
        val signatures = form.fieldTree.filterIsInstance<PDSignatureField>().associateBy { it.fullyQualifiedName }
        assertEquals(setOf(PdfFixtures.UNSIGNED_SIGNATURE, PdfFixtures.SIGNED_SIGNATURE), signatures.keys)
        assertNull(signatures.getValue(PdfFixtures.UNSIGNED_SIGNATURE).signature)
        assertNotNull(signatures.getValue(PdfFixtures.SIGNED_SIGNATURE).signature)
        assertEquals("Ada", (form.getField("name") as PDTextField).value)
    }

    @Test
    fun `dynamic xfa fixture has no fields`() = open(PdfFixtures.dynamicXfa()) { document ->
        val form = document.documentCatalog.getAcroForm(null)
        assertTrue(form.hasXFA())
        assertTrue(form.xfaIsDynamic())
        assertTrue(document.documentCatalog.cosObject.getBoolean(COSName.getPDFName("NeedsRendering"), false))
    }

    @Test
    fun `multi-page text has full pages, one shared font and an outline entry per page`() = open(PdfFixtures.multiPageText()) { document ->
        assertEquals(PdfFixtures.MULTI_PAGE_COUNT, document.numberOfPages)
        val fonts = (0 until document.numberOfPages).map {
            document.getPage(it).resources.cosObject.getCOSDictionary(COSName.FONT).getDictionaryObject(COSName.getPDFName("F1"))
        }
        assertEquals(1, fonts.distinct().size)
        assertEquals(PdfFixtures.MULTI_PAGE_LINES, contentOf(document, 0).split(") Tj").size - 1)
        assertTrue(contentOf(document, 4).contains("(Page 5 of ${PdfFixtures.MULTI_PAGE_COUNT})"))
        assertEquals((1..PdfFixtures.MULTI_PAGE_COUNT).map { "Page $it" }, document.documentCatalog.documentOutline.children().map { it.title })
    }

    @Test
    fun `every fixture is a small PDF`() {
        val all = PdfFixtures.all()
        assertEquals(
            setOf(
                "text.pdf", "outline.pdf", "encrypted.pdf", "scanned.pdf", "images.pdf", "rotated.pdf", "form.pdf", "scanned-multi.pdf",
                "object-streams.pdf", "xmp.pdf", "signature-xfa.pdf", "dynamic-xfa.pdf", "multi-page-text.pdf",
            ),
            all.keys,
        )
        all.forEach { (name, bytes) ->
            assertTrue(name, bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-")
            // Only the 300 dpi scan is large. zh-CN: 只有 300 dpi 扫描件较大.
            val limit = if (name == "scanned-multi.pdf") 2_000_000 else 200_000
            assertTrue("$name is ${bytes.size} bytes", bytes.size in 500..limit)
        }
    }

}

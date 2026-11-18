package io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The committed samples of `.python/generate_fixtures.py` (roadmap P0.3) match their manifest `fixtures.json`.
 * Text is read from the `Tj` operands: PDFBox-Android needs a Context for its font resources, so `PDFTextStripper`
 * runs on a device (`SampleFixturesDeviceTest`).
 * zh-CN: `.python/generate_fixtures.py` 生成并已提交的样例 (路线图 P0.3) 与清单 `fixtures.json` 一致.
 * 文本取自 `Tj` 操作数: PDFBox-Android 的字体资源需要 Context, 所以 `PDFTextStripper` 在设备上运行 (`SampleFixturesDeviceTest`).
 */
class SampleFixturesTest {

    private val directory = File(checkNotNull(System.getProperty("paper.samples")) { "system property paper.samples is not set" })

    private val manifest = JSONObject(File(directory, "fixtures.json").readText())

    private val fixtures = manifest.getJSONObject("fixtures")

    private fun fixture(name: String): JSONObject = fixtures.getJSONObject(name)

    private fun bytesOf(name: String): ByteArray = File(directory, name).readBytes()

    private fun <T> open(name: String, password: String? = null, check: (PDDocument) -> T): T {
        val bytes = bytesOf(name)
        return (if (password == null) PDDocument.load(bytes) else PDDocument.load(bytes, password)).use(check)
    }

    private fun openSample(name: String, check: (PDDocument) -> Unit) =
        open(name, fixture(name).optString("userPassword").ifEmpty { null }, check)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun JSONArray.ints(): IntArray = IntArray(length()) { getInt(it) }

    /** The strings shown by `Tj` on one page, in content order. zh-CN: 一页中 `Tj` 显示的字符串, 按内容顺序. */
    private fun shownStrings(document: PDDocument, index: Int): List<String> {
        val parser = PDFStreamParser(document.getPage(index))
        parser.parse()
        val tokens = parser.tokens
        return tokens.indices
            .filter { (tokens[it] as? Operator)?.name == "Tj" }
            .map { (tokens[it - 1] as COSString).string }
    }

    private fun decoded(stream: COSStream): ByteArray = stream.createInputStream().use { it.readBytes() }

    @Test
    fun `the directory holds the manifest and the listed samples, with their size and digest`() {
        assertEquals(1, manifest.getInt("format"))
        val names = fixtures.keys().asSequence().toSet()
        assertEquals(setOf("text.pdf", "outline.pdf", "encrypted.pdf", "scanned.pdf", "images.pdf", "rotated.pdf", "form.pdf", "scanned-multi.pdf"), names)
        assertEquals(names + "fixtures.json", directory.list()!!.toSet())
        names.forEach { name ->
            val bytes = bytesOf(name)
            assertEquals(name, fixture(name).getInt("bytes"), bytes.size)
            assertEquals(name, fixture(name).getString("sha256"), sha256(bytes))
        }
    }

    @Test
    fun `every sample has its title, page count, page sizes and text lines`() {
        fixtures.keys().forEach { name ->
            val expected = fixture(name)
            openSample(name) { document ->
                assertEquals(name, expected.getInt("pages"), document.numberOfPages)
                val info = document.documentInformation
                assertEquals(name, expected.getString("title"), info.title)
                assertEquals(name, manifest.getString("author"), info.author)
                val created = info.creationDate.apply { timeZone = TimeZone.getTimeZone("UTC") }
                assertEquals(listOf(2026, Calendar.NOVEMBER, 12, 8), listOf(Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY).map(created::get))
                val sizes = expected.optJSONArray("mediaBoxes")
                val text = expected.getJSONArray("text")
                for (index in 0 until document.numberOfPages) {
                    val size = sizes?.getJSONArray(index) ?: expected.getJSONArray("pageSize")
                    val mediaBox = document.getPage(index).mediaBox
                    assertEquals("$name page ${index + 1}", size.getInt(0) to size.getInt(1), mediaBox.width.toInt() to mediaBox.height.toInt())
                    assertEquals("$name page ${index + 1}", text.getJSONArray(index).strings(), shownStrings(document, index))
                }
            }
        }
    }

    @Test
    fun `outline sample has the listed outline and search hits`() {
        val expected = fixture("outline.pdf")
        open("outline.pdf") { document ->
            fun check(node: PDOutlineNode, items: JSONArray) {
                val children = node.children().toList()
                assertEquals(items.length(), children.size)
                children.forEachIndexed { index, child ->
                    val item = items.getJSONObject(index)
                    assertEquals(item.getString("title"), child.title)
                    val page = child.findDestinationPage(document)
                    if (item.isNull("page")) assertNull(child.title, page) else assertEquals(child.title, item.getInt("page") - 1, document.pages.indexOf(page))
                    if (item.has("action")) assertEquals(child.title, item.getBoolean("action"), child.action != null)
                    check(child, item.getJSONArray("children"))
                }
            }
            val outline = document.documentCatalog.documentOutline
            assertNotNull(outline)
            check(outline, expected.getJSONArray("outline"))

            val search = expected.getJSONObject("search")
            search.keys().forEach { term ->
                val pages = (0 until document.numberOfPages).filter { index -> shownStrings(document, index).any { it.contains(term) } }.map { it + 1 }
                assertArrayEquals(term, search.getJSONArray(term).ints(), pages.toIntArray())
            }
        }
    }

    @Test
    fun `encrypted sample needs a password and grants the listed permissions`() {
        val expected = fixture("encrypted.pdf")
        assertThrows(InvalidPasswordException::class.java) { open("encrypted.pdf") {} }
        assertThrows(InvalidPasswordException::class.java) { open("encrypted.pdf", "wrong") {} }
        open("encrypted.pdf", expected.getString("userPassword")) { document ->
            val encryption = expected.getJSONObject("encryption")
            assertTrue(document.isEncrypted)
            assertEquals(encryption.getString("filter"), document.encryption.filter)
            assertEquals(encryption.getInt("v"), document.encryption.version)
            assertEquals(encryption.getInt("r"), document.encryption.revision)
            assertEquals(encryption.getInt("keyLength"), document.encryption.length)
            val permissions = expected.getJSONObject("permissions")
            assertEquals(permissions.getInt("p"), document.encryption.permissions)
            val access = document.currentAccessPermission
            assertFalse(access.isOwnerPermission)
            mapOf(
                "print" to access.canPrint(),
                "printFaithful" to access.canPrintFaithful(),
                "modify" to access.canModify(),
                "extractContent" to access.canExtractContent(),
                "modifyAnnotations" to access.canModifyAnnotations(),
                "fillInForm" to access.canFillInForm(),
                "extractForAccessibility" to access.canExtractForAccessibility(),
                "assemble" to access.canAssembleDocument(),
            ).forEach { (key, actual) -> assertEquals(key, permissions.getBoolean(key), actual) }
        }
        open("encrypted.pdf", expected.getString("ownerPassword")) { document ->
            assertTrue(document.currentAccessPermission.isOwnerPermission)
            assertEquals(expected.getJSONArray("text").getJSONArray(1).strings(), shownStrings(document, 1))
        }
    }

    @Test
    fun `scanned sample is one grey image of paper and ink`() {
        val expected = fixture("scanned.pdf").getJSONObject("image")
        open("scanned.pdf") { document ->
            val resources = document.getPage(0).resources
            assertNull(resources.cosObject.getDictionaryObject(COSName.FONT))
            val image = resources.getXObject(COSName.getPDFName(expected.getString("name"))) as PDImageXObject
            val width = expected.getInt("width")
            val height = expected.getInt("height")
            assertEquals(width to height, image.width to image.height)
            assertEquals(expected.getInt("bitsPerComponent"), image.bitsPerComponent)
            assertEquals(COSName.getPDFName(expected.getString("colorSpace")), image.cosObject.getCOSName(COSName.COLORSPACE))
            val pixels = decoded(image.cosObject)
            assertEquals(width * height, pixels.size)
            val ink = expected.getInt("ink").toByte()
            val paper = expected.getInt("paper").toByte()
            var count = 0
            var box = intArrayOf(width, height, 0, 0)
            pixels.forEachIndexed { index, value ->
                if (value == ink) {
                    count++
                    val x = index % width
                    val y = index / width
                    box = intArrayOf(minOf(box[0], x), minOf(box[1], y), maxOf(box[2], x + 1), maxOf(box[3], y + 1))
                } else {
                    assertEquals(paper, value)
                }
            }
            assertEquals(expected.getInt("inkPixels"), count)
            assertArrayEquals(expected.getJSONArray("inkBox").ints(), box)
        }
    }

    @Test
    fun `images sample keeps the stored JPEG and a lossless image with a soft mask`() {
        val images = fixture("images.pdf").getJSONArray("images")
        open("images.pdf") { document ->
            val resources = document.getPage(0).resources
            val photo = images.getJSONObject(0)
            val jpeg = resources.getXObject(COSName.getPDFName(photo.getString("name"))) as PDImageXObject
            assertEquals(COSName.DCT_DECODE, jpeg.cosObject.getCOSName(COSName.FILTER))
            val stored = jpeg.cosObject.createRawInputStream().use { it.readBytes() }
            assertEquals(photo.getInt("bytes"), stored.size)
            assertEquals(photo.getString("sha256"), sha256(stored))
            val bitmap = ImageIO.read(ByteArrayInputStream(stored))
            assertEquals(photo.getInt("width") to photo.getInt("height"), bitmap.width to bitmap.height)
            val centre = bitmap.getRGB(bitmap.width / 2, bitmap.height / 2)
            val channels = intArrayOf(centre shr 16 and 0xFF, centre shr 8 and 0xFF, centre and 0xFF)
            photo.getJSONArray("centre").ints().forEachIndexed { channel, value -> assertTrue("JPEG centre ${channels.toList()}", abs(channels[channel] - value) <= 12) }

            val masked = images.getJSONObject(1)
            val rgb = resources.getXObject(COSName.getPDFName(masked.getString("name"))) as PDImageXObject
            val width = masked.getInt("width")
            val height = masked.getInt("height")
            assertEquals(width to height, rgb.width to rgb.height)
            val samples = decoded(rgb.cosObject)
            assertEquals(width * height * 3, samples.size)
            // Quadrants in reading order: top left, top right, bottom left, bottom right. zh-CN: 象限按阅读顺序: 左上, 右上, 左下, 右下.
            val quadrants = masked.getJSONArray("quadrants")
            listOf(width / 4 to height / 4, width * 3 / 4 to height / 4, width / 4 to height * 3 / 4, width * 3 / 4 to height * 3 / 4).forEachIndexed { index, (x, y) ->
                val offset = (y * width + x) * 3
                val actual = IntArray(3) { samples[offset + it].toInt() and 0xFF }
                assertArrayEquals("quadrant $index", quadrants.getJSONArray(index).ints(), actual)
            }
            val mask = decoded(rgb.cosObject.getCOSStream(COSName.SMASK))
            assertEquals(width * height, mask.size)
            assertEquals(0, mask[0].toInt() and 0xFF)
            assertEquals(255, mask[height / 2 * width + width / 2].toInt() and 0xFF)
        }
    }

    @Test
    fun `rotated sample carries the listed rotations`() {
        val expected = fixture("rotated.pdf")
        open("rotated.pdf") { document ->
            assertArrayEquals(expected.getJSONArray("rotations").ints(), IntArray(document.numberOfPages) { document.getPage(it).rotation })
        }
    }

    @Test
    fun `form sample has the listed fields with appearances`() {
        val expected = fixture("form.pdf").getJSONArray("fields")
        open("form.pdf") { document ->
            val form = document.documentCatalog.getAcroForm(null)
            assertFalse(form.hasXFA())
            val terminal = form.fieldTree.filterIsInstance<PDTerminalField>().associateBy { it.fullyQualifiedName }
            assertEquals(List(expected.length()) { expected.getJSONObject(it).getString("name") }.toSet(), terminal.keys)
            for (index in 0 until expected.length()) {
                val entry = expected.getJSONObject(index)
                val name = entry.getString("name")
                val field = terminal.getValue(name)
                val type = when (field) {
                    is PDTextField -> "text"
                    is PDCheckBox -> "checkbox"
                    is PDRadioButton -> "radio"
                    is PDComboBox -> "combo"
                    is PDListBox -> "list"
                    is PDPushButton -> "button"
                    else -> field.javaClass.simpleName
                }
                assertEquals(name, entry.getString("type"), type)
                val value: Any? = when (field) {
                    is PDTextField -> field.cosObject.getDictionaryObject(COSName.V)?.let { field.value }
                    is PDCheckBox -> field.isChecked
                    is PDRadioButton -> field.value
                    is PDComboBox -> field.value.single()
                    is PDListBox -> JSONArray(field.value)
                    else -> null
                }
                assertEquals(name, entry.opt("value").let { if (it == JSONObject.NULL) null else it }.toString(), value.toString())
                val pages = field.widgets.map { document.pages.indexOf(it.page) + 1 }.toSet()
                assertEquals(name, setOf(entry.getInt("page")), pages)
                field.widgets.forEach { assertNotNull("$name has an appearance", it.appearance?.normalAppearance) }
                val rects = entry.optJSONArray("widgets")?.let { widgets -> List(widgets.length()) { widgets.getJSONArray(it) } } ?: listOf(entry.getJSONArray("rect"))
                field.widgets.zip(rects).forEach { (widget, rect) ->
                    val box = widget.rectangle
                    val top = document.getPage(entry.getInt("page") - 1).mediaBox.height - box.upperRightY
                    assertArrayEquals(name, rect.ints(), intArrayOf(box.lowerLeftX.toInt(), top.toInt(), box.width.toInt(), box.height.toInt()))
                }
                if (field is PDTextField) {
                    assertEquals(name, entry.getBoolean("readOnly"), field.isReadOnly)
                    assertEquals(name, entry.getBoolean("required"), field.isRequired)
                    assertEquals(name, entry.getBoolean("multiline"), field.isMultiline)
                    assertEquals(name, entry.getBoolean("password"), field.isPassword)
                    assertEquals(name, entry.optString("label").ifEmpty { null }, field.alternateFieldName)
                }
                if (field is PDListBox) assertEquals(name, entry.getBoolean("multiSelect"), field.isMultiSelect)
                entry.optJSONArray("options")?.let { options ->
                    val values = List(options.length()) { options.getJSONObject(it).getString("value") }
                    when (field) {
                        is PDListBox -> assertEquals(name, values, field.optionsExportValues)
                        is PDComboBox -> assertEquals(name, values, field.optionsExportValues)
                        is PDRadioButton -> assertEquals(name, values.toSet(), field.onValues)
                        else -> Unit
                    }
                }
            }
        }
    }

    @Test
    fun `multi-page scan holds one 300 dpi grey JPEG per page`() {
        val expected = fixture("scanned-multi.pdf")
        val image = expected.getJSONObject("image")
        val stored = image.getJSONArray("pages")
        open("scanned-multi.pdf") { document ->
            assertEquals(expected.getInt("pages"), stored.length())
            for (index in 0 until document.numberOfPages) {
                val resources = document.getPage(index).resources
                assertNull(resources.cosObject.getDictionaryObject(COSName.FONT))
                val picture = resources.getXObject(COSName.getPDFName(image.getString("name"))) as PDImageXObject
                assertEquals(COSName.DCT_DECODE, picture.cosObject.getCOSName(COSName.FILTER))
                assertEquals(COSName.DEVICEGRAY, picture.cosObject.getCOSName(COSName.COLORSPACE))
                val bytes = picture.cosObject.createRawInputStream().use { it.readBytes() }
                assertEquals(stored.getJSONObject(index).getInt("bytes"), bytes.size)
                assertEquals(stored.getJSONObject(index).getString("sha256"), sha256(bytes))
                val decoded = ImageIO.read(ByteArrayInputStream(bytes))
                assertEquals(image.getInt("width") to image.getInt("height"), decoded.width to decoded.height)
                assertEquals(image.getInt("dpi").toFloat(), decoded.width / (document.getPage(index).mediaBox.width / 72f), 1f)
                // Paper and the first ink dot decode to their grey levels. zh-CN: 纸面与第一个墨点解码为各自的灰度.
                val origin = expected.getJSONObject("glyphs").getJSONArray("origin").ints()
                val dot = expected.getJSONObject("glyphs").getInt("dot")
                val paper = decoded.raster.getSample(8, 8, 0)
                // "P" starts with a dot in its top left cell. zh-CN: "P" 的左上格是一个点.
                val ink = decoded.raster.getSample(origin[0] + dot / 2, origin[1] + dot / 2, 0)
                assertTrue("paper $paper", abs(paper - image.getInt("paper")) <= 2)
                assertTrue("ink $ink", abs(ink - image.getInt("ink")) <= 2)
            }
        }
    }

}

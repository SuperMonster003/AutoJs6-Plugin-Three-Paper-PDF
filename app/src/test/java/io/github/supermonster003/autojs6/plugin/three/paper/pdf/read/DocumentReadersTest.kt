package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * `getInfo` and `getOutline` on the committed samples (roadmap P2.2). Text extraction needs the font resources of
 * PDFBox-Android, so `getText` / `search` run on a device (`PdfServiceReadingTest`); their pure parts are covered by
 * `ReadingPartsTest`.
 * zh-CN: 在已提交样例上验证 `getInfo` 与 `getOutline` (路线图 P2.2). 文本提取需要 PDFBox-Android 的字体资源, 因此 `getText` /
 * `search` 在设备上运行 (`PdfServiceReadingTest`); 其纯逻辑部分由 `ReadingPartsTest` 覆盖.
 */
class DocumentReadersTest {

    private val directory = File(checkNotNull(System.getProperty("paper.samples")) { "system property paper.samples is not set" })

    private val fixtures = JSONObject(File(directory, "fixtures.json").readText()).getJSONObject("fixtures")

    private fun <T> open(name: String, password: String? = null, check: (PDDocument) -> T): T {
        val bytes = File(directory, name).readBytes()
        return (if (password == null) PDDocument.load(bytes) else PDDocument.load(bytes, password)).use(check)
    }

    private fun info(name: String, password: String? = null): Map<String, Any?> =
        open(name, password) { DocumentInfoReader.read(it, File(directory, name).length(), StopSignal.NEVER) }

    private val allPermissions = arrayOf(
        PdfContract.PERMISSION_PRINT, PdfContract.PERMISSION_COPY, PdfContract.PERMISSION_MODIFY, PdfContract.PERMISSION_ANNOTATE,
        PdfContract.PERMISSION_FILL_FORMS, PdfContract.PERMISSION_EXTRACT_FOR_ACCESSIBILITY, PdfContract.PERMISSION_ASSEMBLE,
        PdfContract.PERMISSION_PRINT_HIGH_QUALITY,
    )

    @Test
    fun `info of the text sample has its page, metadata, version and size`() {
        val info = info("text.pdf")
        assertEquals(1, info[PdfContract.KEY_PAGE_COUNT])
        assertArrayEquals(floatArrayOf(595f), info[PdfContract.KEY_PAGE_WIDTHS] as FloatArray, 0f)
        assertArrayEquals(floatArrayOf(842f), info[PdfContract.KEY_PAGE_HEIGHTS] as FloatArray, 0f)
        assertArrayEquals(intArrayOf(0), info[PdfContract.KEY_PAGE_ROTATIONS] as IntArray)
        assertEquals("Text sample", info[PdfContract.KEY_TITLE])
        assertEquals("AutoJs6", info[PdfContract.KEY_AUTHOR])
        assertEquals("3-Paper PDF test fixture", info[PdfContract.KEY_SUBJECT])
        assertEquals("autojs6, pdf, fixture", info[PdfContract.KEY_KEYWORDS])
        assertEquals(".python/generate_fixtures.py", info[PdfContract.KEY_CREATOR])
        assertEquals("3-Paper PDF fixtures", info[PdfContract.KEY_PRODUCER])
        assertEquals("2026-11-12T08:00:00+00:00", info[PdfContract.KEY_CREATION_DATE])
        assertEquals("2026-11-12T08:00:00+00:00", info[PdfContract.KEY_MODIFICATION_DATE])
        assertEquals("1.7", info[PdfContract.KEY_PDF_VERSION])
        assertEquals(false, info[PdfContract.KEY_ENCRYPTED])
        assertArrayEquals(allPermissions, info[PdfContract.KEY_PERMISSIONS] as Array<*>)
        assertEquals(fixtures.getJSONObject("text.pdf").getLong("bytes"), info[PdfContract.KEY_SIZE_BYTES])
    }

    @Test
    fun `info of the rotated sample has every page size and rotation`() {
        val info = info("rotated.pdf")
        assertEquals(5, info[PdfContract.KEY_PAGE_COUNT])
        assertArrayEquals(floatArrayOf(595f, 595f, 595f, 595f, 792f), info[PdfContract.KEY_PAGE_WIDTHS] as FloatArray, 0f)
        assertArrayEquals(floatArrayOf(842f, 842f, 842f, 842f, 612f), info[PdfContract.KEY_PAGE_HEIGHTS] as FloatArray, 0f)
        assertArrayEquals(intArrayOf(0, 90, 180, 270, 0), info[PdfContract.KEY_PAGE_ROTATIONS] as IntArray)
    }

    @Test
    fun `info of the encrypted sample lists what the password grants`() {
        val user = info("encrypted.pdf", "paper-user")
        assertEquals(true, user[PdfContract.KEY_ENCRYPTED])
        assertArrayEquals(arrayOf(PdfContract.PERMISSION_PRINT), user[PdfContract.KEY_PERMISSIONS] as Array<*>)
        assertEquals("Encrypted sample", user[PdfContract.KEY_TITLE])
        val owner = info("encrypted.pdf", "paper-owner")
        assertEquals(true, owner[PdfContract.KEY_ENCRYPTED])
        assertArrayEquals(allPermissions, owner[PdfContract.KEY_PERMISSIONS] as Array<*>)
    }

    @Test
    fun `metadata keys are absent when the document has none and the XMP title is the fallback`() {
        val packet = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title><rdf:Alt><rdf:li xml:lang="x-default"> Tom &amp; Jerry &#x4E2D;&#25991; </rdf:li></rdf:Alt></dc:title>
            </rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end="w"?>"""
        PDDocument().use { document ->
            document.addPage(PDPage())
            val bare = DocumentInfoReader.read(document, 0L, StopSignal.NEVER)
            listOf(
                PdfContract.KEY_TITLE, PdfContract.KEY_AUTHOR, PdfContract.KEY_SUBJECT, PdfContract.KEY_KEYWORDS, PdfContract.KEY_CREATOR,
                PdfContract.KEY_PRODUCER, PdfContract.KEY_CREATION_DATE, PdfContract.KEY_MODIFICATION_DATE,
            ).forEach { assertFalse(it, bare.containsKey(it)) }
            document.documentCatalog.metadata = PDMetadata(document, ByteArrayInputStream(packet.toByteArray()))
            assertEquals("Tom & Jerry 中文", DocumentInfoReader.read(document, 0L, StopSignal.NEVER)[PdfContract.KEY_TITLE])
        }
        assertNull(DocumentInfoReader.xmpTitle("<x:xmpmeta/>"))
    }

    @Test
    fun `dates are ISO 8601 with the numeric offset of the PDF date`() {
        fun calendar(zone: String) = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            clear()
            set(2026, Calendar.JANUARY, 2, 3, 4, 5)
        }
        assertEquals("2026-01-02T03:04:05+00:00", DocumentInfoReader.isoDate(calendar("UTC")))
        assertEquals("2026-01-02T03:04:05+08:00", DocumentInfoReader.isoDate(calendar("GMT+08:00")))
        assertEquals("2026-01-02T03:04:05-05:30", DocumentInfoReader.isoDate(calendar("GMT-05:30")))
    }

    @Test
    fun `outline of the outline sample is flattened depth first with levels and pages`() {
        val answer = open("outline.pdf") { OutlineReader.read(it, StopSignal.NEVER) }
        assertEquals(false, answer[PdfContract.KEY_HAS_MORE])
        val items = JSONArray(answer[PdfContract.KEY_ITEMS] as String)
        val entries = List(items.length()) { index ->
            items.getJSONObject(index).let { Triple(it.getString(PdfContract.FIELD_TITLE), it.getInt(PdfContract.FIELD_PAGE), it.getInt(PdfContract.FIELD_LEVEL)) }
        }
        assertEquals(
            listOf(
                Triple("Chapter 1", 1, 0), Triple("Chapter 1, section 1", 2, 1),
                Triple("Chapter 2", 3, 0), Triple("Chapter 2, section 1", 4, 1),
                // Chapter 3 points to its page through a GoTo action. zh-CN: Chapter 3 经 GoTo 动作指向其页.
                Triple("Chapter 3", 5, 0), Triple("Chapter 3, section 1", 6, 1),
                Triple("Notes", 0, 0),
            ),
            entries,
        )
    }

    @Test
    fun `outline answers an empty list without an outline and hasMore past the budget`() {
        val none = open("text.pdf") { OutlineReader.read(it, StopSignal.NEVER) }
        assertEquals("[]", none[PdfContract.KEY_ITEMS])
        assertEquals(false, none[PdfContract.KEY_HAS_MORE])
        val cut = open("outline.pdf") { OutlineReader.read(it, StopSignal.NEVER, budgetChars = 100) }
        assertEquals(true, cut[PdfContract.KEY_HAS_MORE])
        val items = JSONArray(cut[PdfContract.KEY_ITEMS] as String)
        assertTrue(items.length() in 1..2)
        assertTrue((cut[PdfContract.KEY_ITEMS] as String).length <= 100)
        assertEquals("Chapter 1", items.getJSONObject(0).getString(PdfContract.FIELD_TITLE))
    }

    @Test
    fun `outline cycles are cut and a closed document stops the walk`() {
        PDDocument().use { document ->
            val page = PDPage().also(document::addPage)
            val first = PDOutlineItem().apply { title = "First"; setDestination(page) }
            val second = PDOutlineItem().apply { title = "Second" }
            val outline = PDDocumentOutline().apply { addLast(first); addLast(second) }
            // A damaged sibling chain that loops back. zh-CN: 回到起点的损坏兄弟链.
            second.cosObject.setItem(COSName.NEXT, first.cosObject)
            document.documentCatalog.documentOutline = outline
            val entries = ArrayList<OutlineReader.Entry>()
            OutlineReader.walk(document, StopSignal.NEVER) { entries += it; true }
            assertEquals(listOf(OutlineReader.Entry("First", 1, 0), OutlineReader.Entry("Second", 0, 0)), entries)
            val failure = assertThrows(PdfFailure::class.java) { OutlineReader.read(document, StopSignal { StopSignal.closed() }) }
            assertEquals(PdfErrorCodes.SESSION_CLOSED, failure.code)
        }
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.openSample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Reading through Binder (roadmap P2.2): `getInfo`, `getOutline`, `getText` in chunks with both layouts, and `search`
 * with its options and hit rectangles on rotated pages, all on the committed samples.
 * zh-CN: 经 Binder 读取 (路线图 P2.2): `getInfo`, `getOutline`, 两种布局下分块的 `getText`, 以及 `search` 的各项选项与旋转页上的
 * 命中矩形, 全部基于已提交样例.
 */
@RunWith(AndroidJUnit4::class)
class PdfServiceReadingTest {

    private val fixtures: JSONObject by lazy {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures.json").use { it.readBytes() }
        JSONObject(bytes.toString(Charsets.UTF_8)).getJSONObject("fixtures")
    }

    private fun lines(name: String, page: Int): List<String> = fixtures.getJSONObject(name).getJSONArray("text").getJSONArray(page - 1).let { array ->
        List(array.length()) { array.getString(it) }
    }

    private fun <T> withSample(name: String, password: String? = null, block: (IPdfDocument) -> T): T = withPdfPlugin { plugin ->
        val document = plugin.openSample(name, password)
        try {
            block(document)
        } finally {
            document.close()
        }
    }

    private fun Bundle.succeeded(): Bundle {
        assertNull(getString(PdfContract.KEY_ERROR_MESSAGE), getString(PdfContract.KEY_ERROR_CODE))
        assertEquals(PdfContract.CONTRACT_VERSION, getInt(PdfContract.KEY_CONTRACT_VERSION))
        return this
    }

    private fun assertFailure(code: String, answer: Bundle) =
        assertEquals(answer.getString(PdfContract.KEY_ERROR_MESSAGE), code, answer.getString(PdfContract.KEY_ERROR_CODE))

    private fun Bundle.items(): List<JSONObject> = JSONArray(getString(PdfContract.KEY_ITEMS)).let { array -> List(array.length()) { array.getJSONObject(it) } }

    private fun JSONObject.rects(): List<DoubleArray> = getJSONArray(PdfContract.FIELD_RECTS).let { array ->
        List(array.length()) { index -> array.getJSONArray(index).let { rect -> DoubleArray(rect.length()) { rect.getDouble(it) } } }
    }

    private fun assertRect(message: String, expected: DoubleArray, actual: DoubleArray, tolerance: Double = 0.5) =
        assertTrue("$message: expected ${expected.toList()}, got ${actual.toList()}", expected.indices.all { abs(expected[it] - actual[it]) <= tolerance })

    /** Every chunk of `getText` until `hasMore` is false, as (page, text) pairs. zh-CN: `getText` 直到 `hasMore` 为 false 的全部分块, 以 (页码, 文本) 对给出. */
    private fun IPdfDocument.allText(fill: Bundle.() -> Unit, calls: MutableList<Bundle> = ArrayList()): List<Pair<Int, String>> {
        val chunks = ArrayList<Pair<Int, String>>()
        var next: Pair<Int, Int>? = null
        do {
            val answer = getText(request {
                fill()
                next?.let { (page, offset) -> putInt(PdfContract.KEY_PAGE, page); putInt(PdfContract.KEY_OFFSET, offset) }
            }).succeeded()
            calls += answer
            val texts = answer.getStringArray(PdfContract.KEY_TEXTS)!!
            val pages = answer.getIntArray(PdfContract.KEY_PAGE_NUMBERS)!!
            assertEquals(texts.size, pages.size)
            texts.indices.forEach { chunks += pages[it] to texts[it] }
            next = if (answer.getBoolean(PdfContract.KEY_HAS_MORE)) {
                answer.getInt(PdfContract.KEY_NEXT_PAGE) to answer.getInt(PdfContract.KEY_NEXT_OFFSET)
            } else {
                assertFalse(answer.containsKey(PdfContract.KEY_NEXT_PAGE))
                null
            }
        } while (next != null)
        return chunks
    }

    private fun IPdfDocument.search(keyword: String, fill: Bundle.() -> Unit = {}): Bundle = search(request {
        putString(PdfContract.KEY_KEYWORD, keyword)
        fill()
    })

    @Test
    fun infoAnswersPagesMetadataAndPermissions() {
        withSample("rotated.pdf") { document ->
            val info = document.info.succeeded()
            assertEquals(5, info.getInt(PdfContract.KEY_PAGE_COUNT))
            assertArrayEquals(floatArrayOf(595f, 595f, 595f, 595f, 792f), info.getFloatArray(PdfContract.KEY_PAGE_WIDTHS), 0f)
            assertArrayEquals(floatArrayOf(842f, 842f, 842f, 842f, 612f), info.getFloatArray(PdfContract.KEY_PAGE_HEIGHTS), 0f)
            assertArrayEquals(intArrayOf(0, 90, 180, 270, 0), info.getIntArray(PdfContract.KEY_PAGE_ROTATIONS))
            assertEquals("Rotated sample", info.getString(PdfContract.KEY_TITLE))
            assertEquals("2026-11-12T08:00:00+00:00", info.getString(PdfContract.KEY_CREATION_DATE))
            assertEquals("1.7", info.getString(PdfContract.KEY_PDF_VERSION))
            assertFalse(info.getBoolean(PdfContract.KEY_ENCRYPTED))
            assertEquals(8, info.getStringArray(PdfContract.KEY_PERMISSIONS)!!.size)
            assertEquals(fixtures.getJSONObject("rotated.pdf").getLong("bytes"), info.getLong(PdfContract.KEY_SIZE_BYTES))
        }
        withSample("encrypted.pdf", "paper-user") { document ->
            val info = document.info.succeeded()
            assertTrue(info.getBoolean(PdfContract.KEY_ENCRYPTED))
            assertArrayEquals(arrayOf(PdfContract.PERMISSION_PRINT), info.getStringArray(PdfContract.KEY_PERMISSIONS))
        }
    }

    @Test
    fun outlineIsFlattenedWithLevelsAndPages() = withSample("outline.pdf") { document ->
        val answer = document.outline.succeeded()
        assertFalse(answer.getBoolean(PdfContract.KEY_HAS_MORE))
        val entries = answer.items().map { Triple(it.getString(PdfContract.FIELD_TITLE), it.getInt(PdfContract.FIELD_PAGE), it.getInt(PdfContract.FIELD_LEVEL)) }
        assertEquals(
            listOf(
                Triple("Chapter 1", 1, 0), Triple("Chapter 1, section 1", 2, 1), Triple("Chapter 2", 3, 0), Triple("Chapter 2, section 1", 4, 1),
                Triple("Chapter 3", 5, 0), Triple("Chapter 3, section 1", 6, 1), Triple("Notes", 0, 0),
            ),
            entries,
        )
    }

    @Test
    fun textKeepsLineBreaksAndEchoesTheRequestId() = withSample("text.pdf") { document ->
        val answer = document.getText(request { putString(PdfContract.KEY_REQUEST_ID, "text-1") }).succeeded()
        assertEquals("text-1", answer.getString(PdfContract.KEY_REQUEST_ID))
        assertArrayEquals(intArrayOf(1), answer.getIntArray(PdfContract.KEY_PAGE_NUMBERS))
        assertArrayEquals(arrayOf(lines("text.pdf", 1).joinToString("\n", postfix = "\n")), answer.getStringArray(PdfContract.KEY_TEXTS))
        assertFalse(answer.getBoolean(PdfContract.KEY_HAS_MORE))
    }

    @Test
    fun textChunksAddUpToTheWholeTextOfTheSelectedPages() = withSample("outline.pdf") { document ->
        val whole = document.allText({ putString(PdfContract.KEY_PAGES, "2-5") })
        assertEquals(listOf(2, 3, 4, 5), whole.map { it.first })
        whole.forEach { (page, text) -> assertEquals("page $page", lines("outline.pdf", page).joinToString("\n", postfix = "\n"), text) }
        val calls = ArrayList<Bundle>()
        val chunks = document.allText({ putString(PdfContract.KEY_PAGES, "2-5"); putInt(PdfContract.KEY_MAX_CHARS, 20) }, calls)
        assertTrue("several calls: ${calls.size}", calls.size > 3)
        calls.forEach { answer -> assertTrue(answer.getStringArray(PdfContract.KEY_TEXTS)!!.sumOf { it.length } <= 20) }
        val joined = chunks.groupBy({ it.first }, { it.second }).mapValues { it.value.joinToString("") }
        assertEquals(whole.toMap(), joined)
        // Starting inside the selection at a page and offset. zh-CN: 从所选范围内的某页某偏移开始.
        val tail = document.getText(request { putString(PdfContract.KEY_PAGES, "2-5"); putInt(PdfContract.KEY_PAGE, 5); putInt(PdfContract.KEY_OFFSET, 4) }).succeeded()
        assertArrayEquals(intArrayOf(5), tail.getIntArray(PdfContract.KEY_PAGE_NUMBERS))
        assertEquals(whole.toMap().getValue(5).substring(4), tail.getStringArray(PdfContract.KEY_TEXTS)!!.single())
    }

    @Test
    fun readingLayoutSortsQuarterTurnPagesAndRawKeepsTheStreamOrder() = withSample("rotated.pdf") { document ->
        val reading = document.getText(request { putString(PdfContract.KEY_PAGES, "2") }).succeeded().getStringArray(PdfContract.KEY_TEXTS)!!.single()
        assertEquals(lines("rotated.pdf", 2).joinToString("\n", postfix = "\n"), reading)
        val raw = document.getText(request { putString(PdfContract.KEY_PAGES, "2"); putString(PdfContract.KEY_LAYOUT, PdfContract.LAYOUT_RAW) })
            .succeeded().getStringArray(PdfContract.KEY_TEXTS)!!.single()
        assertNotEquals(reading, raw)
        assertEquals(reading.filterNot(Char::isWhitespace), raw.filterNot(Char::isWhitespace))
    }

    @Test
    fun textRefusesBadRequests() = withSample("outline.pdf") { document ->
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.getText(request { putString(PdfContract.KEY_PAGES, "2-3"); putInt(PdfContract.KEY_PAGE, 5) }))
        assertFailure(PdfErrorCodes.PAGE_OUT_OF_RANGE, document.getText(request { putInt(PdfContract.KEY_PAGE, 9) }))
        assertFailure(PdfErrorCodes.PAGE_OUT_OF_RANGE, document.getText(request { putString(PdfContract.KEY_PAGES, "7") }))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.getText(request { putString(PdfContract.KEY_LAYOUT, "fancy") }))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.getText(request { putInt(PdfContract.KEY_MAX_CHARS, 0) }))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.getText(request { putInt(PdfContract.KEY_OFFSET, 10_000) }))
    }

    @Test
    fun searchFindsTheListedPagesWithOptionsAndPaging() = withSample("outline.pdf") { document ->
        fun pages(answer: Bundle) = answer.succeeded().items().map { it.getInt(PdfContract.FIELD_PAGE) }
        val needle = document.search("needle").succeeded()
        assertEquals(listOf(2, 5), pages(needle))
        assertFalse(needle.getBoolean(PdfContract.KEY_HAS_MORE))
        needle.items().forEach { hit ->
            assertEquals("The needle is on this page.", hit.getString(PdfContract.FIELD_CONTEXT).substringAfterLast("of 6").trim())
            assertEquals(1, hit.rects().size)
        }
        assertEquals(listOf(2, 5), pages(document.search("NEEDLE")))
        assertEquals(emptyList<Int>(), pages(document.search("Needle") { putBoolean(PdfContract.KEY_CASE_SENSITIVE, true) }))
        assertEquals(listOf(2, 5), pages(document.search("need")))
        assertEquals(emptyList<Int>(), pages(document.search("need") { putBoolean(PdfContract.KEY_WHOLE_WORD, true) }))
        assertEquals(listOf(5), pages(document.search("needle") { putString(PdfContract.KEY_PAGES, "3-6") }))
        assertEquals(listOf(5), pages(document.search("needle") { putInt(PdfContract.KEY_OFFSET, 1) }))
        val first = document.search("needle") { putInt(PdfContract.KEY_LIMIT, 1) }
        assertEquals(listOf(2), pages(first))
        assertTrue(first.getBoolean(PdfContract.KEY_HAS_MORE))
        assertEquals(1, first.getInt(PdfContract.KEY_NEXT_OFFSET))
        // A blank in the keyword matches the line break between "section 1" and "Page 2". zh-CN: 关键字中的空白匹配 "section 1" 与 "Page 2" 之间的换行.
        assertEquals(listOf(2), pages(document.search("section 1 page 2")))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.search(""))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.search("x".repeat(PdfContract.MAX_KEYWORD_LENGTH + 1)))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.search(request()))
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.search("needle") { putInt(PdfContract.KEY_LIMIT, -1) })
    }

    @Test
    fun searchRectanglesAreMeasuredOnTheDisplayedPage() {
        withSample("text.pdf") { document ->
            val hit = document.search("needle").succeeded().items().single()
            // Baseline 694 = 770 - 28 - 3 x 16 at 12 pt: top = 842 - 694 - 0.718 x 12, bottom = 842 - 694 + 0.207 x 12. zh-CN: 12 pt 下基线 694.
            val rect = hit.rects().single()
            assertRect("needle", doubleArrayOf(rect[0], 139.38, rect[2], 150.48), rect)
            assertTrue("needle is right of the label: ${rect.toList()}", rect[0] > 145 && rect[2] - rect[0] in 30.0..40.0)
        }
        withSample("rotated.pdf") { document ->
            val hits = document.search("Rotate") { putBoolean(PdfContract.KEY_WHOLE_WORD, true) }.succeeded().items()
            assertEquals(listOf(1, 2, 3, 4, 5), hits.map { it.getInt(PdfContract.FIELD_PAGE) })
            // "Rotate" in 18 pt Helvetica-Bold at (72, height - 120) of the unrotated page. zh-CN: 未旋转页面 (72, 高 - 120) 处 18 pt Helvetica-Bold 的 "Rotate".
            val expected = listOf(
                doubleArrayOf(72.0, 107.08, 128.0, 123.73),
                doubleArrayOf(718.27, 72.0, 734.92, 128.0),
                doubleArrayOf(467.0, 718.27, 523.0, 734.92),
                doubleArrayOf(107.08, 467.0, 123.73, 523.0),
                doubleArrayOf(72.0, 107.08, 128.0, 123.73),
            )
            hits.forEachIndexed { index, hit -> assertRect("page ${index + 1}", expected[index], hit.rects().single()) }
        }
    }

    @Test
    fun theUserPasswordReadsTextWithoutTheCopyPermission() = withSample("encrypted.pdf", "paper-user") { document ->
        val text = document.getText(request()).succeeded()
        assertArrayEquals(intArrayOf(1, 2), text.getIntArray(PdfContract.KEY_PAGE_NUMBERS))
        assertEquals(lines("encrypted.pdf", 1).joinToString("\n", postfix = "\n"), text.getStringArray(PdfContract.KEY_TEXTS)!![0])
        assertEquals(listOf(1), document.search("owner password").succeeded().items().map { it.getInt(PdfContract.FIELD_PAGE) })
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `setInfo` and `setOutline` through Binder (roadmap P6.8, E2): after `setInfo`, `getInfo` answers the new values,
 * null removes a key, keys left out stay, the XMP fallback title equals the new title (the platform XML parser and
 * serializer of this API level), an unparsable XMP packet is removed with a warning; after `setOutline`, `getOutline`
 * answers the tree as written, `[]` removes the outline, 10,000 entries over 32 levels are written and read back (in
 * full by PDFBox; one `getOutline` answer stops at its character budget and says `hasMore`), more entries or levels are
 * `LIMIT_EXCEEDED`, a page outside the document `PAGE_OUT_OF_RANGE`. Times go to logcat under `PaperMetadata`.
 * zh-CN: 经 Binder 验证 `setInfo` 与 `setOutline` (路线图 P6.8, E2): `setInfo` 之后 `getInfo` 返回新值, null 删除键, 未给出的键
 * 不变, XMP 后备标题等于新标题 (本 API 级别平台的 XML 解析器与序列化器), 无法解析的 XMP 被删除并给警告; `setOutline` 之后
 * `getOutline` 返回写入的树, `[]` 删除大纲, 10,000 条 32 层的大纲写入并取回 (PDFBox 读全部; 一次 `getOutline` 应答在其字符预算处
 * 停止并给出 `hasMore`), 更多条目或层级为 `LIMIT_EXCEEDED`, 不在文档内的页码为 `PAGE_OUT_OF_RANGE`. 耗时以 `PaperMetadata` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class MetadataOperationsDeviceTest {

    private val directory = File(context.cacheDir, "metadata-op")

    @After
    fun removeOutputs() {
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    /** Records the end of one operation. zh-CN: 记录一个操作的结束. */
    private class Recorder : IPdfProgressCallback.Stub() {

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        private val ended = CountDownLatch(1)

        override fun onProgress(opId: Int, progress: Bundle) = Unit

        override fun onComplete(opId: Int, result: Bundle) {
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            this.error = error
            ended.countDown()
        }

        fun await(): Recorder {
            assertTrue("the operation did not end in time", ended.await(300, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

        fun failedWith(): String? = error?.getString(PdfContract.KEY_ERROR_CODE)

    }

    private fun file(name: String): File = File(directory, name).also { it.parentFile?.mkdirs() }

    private fun read(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun write(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

    private fun IPdfPlugin.run(op: String, source: File, payload: String, target: File): Recorder {
        val payloadFile = file("payload-${SystemClock.elapsedRealtimeNanos()}.json").apply { writeText(payload) }
        val recorder = Recorder()
        read(source).use { input ->
            read(payloadFile).use { data ->
                write(target).use { output ->
                    val request = Bundle().apply {
                        putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
                        putString(PdfContract.KEY_OP, op)
                    }
                    run(request, arrayOf(input, data), output, recorder)
                }
            }
        }
        return recorder.await()
    }

    private fun <T> IPdfPlugin.opened(file: File, block: (IPdfDocument) -> T): T {
        val handle = read(file).use { open(it, request()) }
        val document = IPdfDocument.Stub.asInterface(MarshallingBinder(handle.asBinder()))
        try {
            return block(document)
        } finally {
            document.close()
        }
    }

    private fun source(name: String, xmp: String? = null, pages: Int = 3): File {
        val target = file(name)
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage(PDRectangle.A4)) }
            document.documentInformation = PDDocumentInformation().apply {
                title = TITLE
                author = "Author"
                subject = "Subject"
                keywords = "old"
                creator = "Creator"
            }
            if (xmp != null) {
                val stream = PDStream(document, ByteArrayInputStream(xmp.toByteArray(Charsets.UTF_8))).cosObject
                stream.setItem(COSName.TYPE, COSName.METADATA)
                stream.setItem(COSName.SUBTYPE, COSName.getPDFName("XML"))
                document.documentCatalog.cosObject.setItem(COSName.METADATA, stream)
            }
            document.save(target)
        }
        return target
    }

    private fun warnings(result: Bundle): List<String> = result.getStringArray(PdfContract.KEY_WARNINGS)?.filterNotNull().orEmpty()

    @Test
    fun setInfoSetsClearsKeepsAndSyncsTheXmp() = withPdfPlugin { plugin ->
        val source = source("info.pdf", XMP)
        val target = file("info-set.pdf")
        val title = "新的标题 <&> 3-Paper"
        val result = plugin.run(PdfOps.SET_INFO, source, """{ "info": { "title": "$title", "author": null, "keywords": "k1, k2", "producer": "ignored" } }""", target).completed()
        assertEquals(warnings(result).toString(), emptyList<String>(), warnings(result))
        assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        plugin.opened(target) { document ->
            val info = document.getInfo()
            assertEquals(title, info.getString(PdfContract.KEY_TITLE))
            assertFalse("author removed", info.containsKey(PdfContract.KEY_AUTHOR))
            assertEquals("Subject", info.getString(PdfContract.KEY_SUBJECT))
            assertEquals("k1, k2", info.getString(PdfContract.KEY_KEYWORDS))
            assertEquals("Creator", info.getString(PdfContract.KEY_CREATOR))
            assertTrue(info.getString(PdfContract.KEY_PRODUCER).orEmpty().startsWith("3-Paper PDF"))
        }
        PDDocument.load(target).use { document ->
            assertEquals("the XMP fallback reads the new title", title, DocumentInfoReader.xmpTitle(document))
            val packet = document.documentCatalog.metadata.createInputStream().use { String(it.readBytes(), Charsets.UTF_8) }
            log("XMP after setInfo: ${packet.length} characters, dc:creator ${packet.contains("dc:creator")}, CreateDate kept ${packet.contains("2026-11-12T08:00:00Z")}")
            assertFalse(packet, packet.contains("dc:creator"))
            assertTrue(packet, packet.contains("k1, k2"))
            assertTrue(packet, packet.contains("2026-11-12T08:00:00Z"))
        }
        val cleared = file("info-cleared.pdf")
        plugin.run(PdfOps.SET_INFO, target, """{ "info": { "title": null } }""", cleared).completed()
        plugin.opened(cleared) { document ->
            val info = document.getInfo()
            assertFalse("no title and no XMP fallback", info.containsKey(PdfContract.KEY_TITLE))
            assertEquals("k1, k2", info.getString(PdfContract.KEY_KEYWORDS))
        }
    }

    @Test
    fun anUnparsableXmpIsRemovedWithAWarning() = withPdfPlugin { plugin ->
        val source = source("broken-xmp.pdf", "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF")
        val target = file("broken-xmp-set.pdf")
        val result = plugin.run(PdfOps.SET_INFO, source, """{ "info": { "title": "T" } }""", target).completed()
        assertEquals(warnings(result).toString(), 1, warnings(result).size)
        PDDocument.load(target).use { document ->
            assertNull(document.documentCatalog.metadata)
            assertEquals("T", document.documentInformation.title)
        }
        val tooLong = "x".repeat(PdfContract.MAX_INFO_VALUE_LENGTH + 1)
        assertEquals(
            PdfErrorCodes.INVALID_ARGUMENT,
            plugin.run(PdfOps.SET_INFO, source, """{ "info": { "subject": "$tooLong" } }""", file("too-long.pdf")).failedWith(),
        )
    }

    @Test
    fun setOutlineReplacesAndRemovesTheOutline() = withPdfPlugin { plugin ->
        val source = source("outline.pdf")
        val target = file("outline-set.pdf")
        plugin.run(
            PdfOps.SET_OUTLINE,
            source,
            """{ "items": [ { "title": "第一章", "page": 1, "children": [ { "title": "1.1", "page": 2, "level": 9 } ] }, { "title": "附录" }, { "title": "Last", "page": 3 } ] }""",
            target,
        ).completed()
        plugin.opened(target) { document ->
            val answer = document.getOutline()
            assertFalse(answer.getBoolean(PdfContract.KEY_HAS_MORE))
            val items = JSONArray(answer.getString(PdfContract.KEY_ITEMS))
            val flat = (0 until items.length()).map { index ->
                items.getJSONObject(index).let { Triple(it.getString(PdfContract.FIELD_TITLE), it.getInt(PdfContract.FIELD_PAGE), it.getInt(PdfContract.FIELD_LEVEL)) }
            }
            assertEquals(listOf(Triple("第一章", 1, 0), Triple("1.1", 2, 1), Triple("附录", 0, 0), Triple("Last", 3, 0)), flat)
        }
        val removed = file("outline-removed.pdf")
        plugin.run(PdfOps.SET_OUTLINE, target, """{ "items": [] }""", removed).completed()
        plugin.opened(removed) { document ->
            assertEquals("[]", JSONArray(document.getOutline().getString(PdfContract.KEY_ITEMS)).toString())
        }
    }

    @Test
    fun tenThousandEntriesOverThirtyTwoLevelsAreWrittenAndReadBack() = withPdfPlugin { plugin ->
        val source = source("outline-large.pdf", pages = 50)
        val json = buildString {
            var item = """{ "title": "Level 32", "page": 32 }"""
            for (level in 31 downTo 1) item = """{ "title": "Level $level", "page": $level, "children": [ $item ] }"""
            append("""{ "items": [ """).append(item)
            for (index in 1..PdfContract.MAX_OUTLINE_ITEMS - PdfContract.MAX_OUTLINE_DEPTH) append(""", { "title": "Entry $index", "page": ${1 + index % 50} }""")
            append(" ] }")
        }
        val target = file("outline-large-set.pdf")
        val started = SystemClock.elapsedRealtime()
        val result = plugin.run(PdfOps.SET_OUTLINE, source, json, target).completed()
        val millis = SystemClock.elapsedRealtime() - started
        val read = ArrayList<OutlineReader.Entry>()
        PDDocument.load(target).use { document -> OutlineReader.walk(document, StopSignal.NEVER) { read += it; true } }
        assertEquals(PdfContract.MAX_OUTLINE_ITEMS, read.size)
        assertEquals((0 until 32).toList(), read.take(32).map { it.level })
        assertEquals(OutlineReader.Entry("Level 32", 32, 31), read[31])
        assertEquals(OutlineReader.Entry("Entry 9968", 1 + 9968 % 50, 0), read.last())
        val answered = plugin.opened(target) { document ->
            val answer = document.getOutline()
            val items = JSONArray(answer.getString(PdfContract.KEY_ITEMS))
            val prefix = (0 until items.length()).map { index -> items.getJSONObject(index).getString(PdfContract.FIELD_TITLE) }
            assertEquals(read.take(prefix.size).map { it.title }, prefix)
            prefix.size to answer.getBoolean(PdfContract.KEY_HAS_MORE)
        }
        log("setOutline 10,000 entries / 32 levels: ${result.getLong(PdfContract.KEY_SIZE_BYTES)} B in $millis ms; one getOutline answer holds ${answered.first} entries, hasMore ${answered.second}")
    }

    @Test
    fun outlineCeilingsAndPagesAreChecked() = withPdfPlugin { plugin ->
        val source = source("outline-checks.pdf")
        val tooMany = (1..PdfContract.MAX_OUTLINE_ITEMS + 1).joinToString(", ", """{ "items": [ """, " ] }") { """{ "title": "E$it" }""" }
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, plugin.run(PdfOps.SET_OUTLINE, source, tooMany, file("a.pdf")).failedWith())
        var deep = """{ "title": "Level 33" }"""
        for (level in 32 downTo 1) deep = """{ "title": "Level $level", "children": [ $deep ] }"""
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, plugin.run(PdfOps.SET_OUTLINE, source, """{ "items": [ $deep ] }""", file("b.pdf")).failedWith())
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, plugin.run(PdfOps.SET_OUTLINE, source, """{ "items": [ { "title": "A", "page": 4 } ] }""", file("c.pdf")).failedWith())
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plugin.run(PdfOps.SET_OUTLINE, source, """{ "items": [ { "page": 1 } ] }""", file("d.pdf")).failedWith())
    }

    companion object {

        private const val TAG = "PaperMetadata"

        private const val TITLE = "Old title"

        private val XMP = "<?xpacket begin='" + Char(0xFEFF) + "' id='W5M0MpCehiHzreSzNTczkc9d'?>\n" +
            "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>" +
            "<rdf:Description rdf:about='' xmlns:dc='http://purl.org/dc/elements/1.1/' xmlns:xmp='http://ns.adobe.com/xap/1.0/' xmlns:pdf='http://ns.adobe.com/pdf/1.3/'>" +
            "<dc:title><rdf:Alt><rdf:li xml:lang='x-default'>$TITLE</rdf:li></rdf:Alt></dc:title>" +
            "<dc:creator><rdf:Seq><rdf:li>Author</rdf:li></rdf:Seq></dc:creator>" +
            "<pdf:Keywords>old</pdf:Keywords><xmp:CreatorTool>Creator</xmp:CreatorTool>" +
            "<xmp:CreateDate>2026-11-12T08:00:00Z</xmp:CreateDate>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta>\n<?xpacket end='w'?>"

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

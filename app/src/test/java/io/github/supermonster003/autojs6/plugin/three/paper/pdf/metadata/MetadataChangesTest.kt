package io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * `setInfo` and `setOutline` on the JVM (roadmap P6.8, D66): the payload rules (set, null clears, left out keeps,
 * unknown fields ignored, value length, required fields, page range, the ceilings of entries and depth), the XMP
 * synchronisation (the fallback title of `getInfo` equals the new title, other properties and the packet wrapper stay,
 * attribute-form properties are replaced, an unparsable or DTD-carrying packet is removed with a warning), and the
 * outline written and read back by `OutlineReader` (10,000 entries, 32 levels, entries without a page, removal).
 * zh-CN: JVM 上的 `setInfo` 与 `setOutline` (路线图 P6.8, D66): payload 规则 (设置, null 清除, 未给出保持, 忽略未知字段, 值长度,
 * 必需字段, 页码范围, 条目数与深度上限), XMP 同步 (`getInfo` 的后备标题等于新标题, 其他属性与数据包外壳保留, 属性形式的属性被
 * 替换, 无法解析或带 DTD 的数据包被删除并给警告), 以及由 `OutlineReader` 读回的大纲 (10,000 条, 32 层, 无页码条目, 删除).
 */
class MetadataChangesTest {

    private fun payload(json: String) = PdfPayload.parse(json.toByteArray(Charsets.UTF_8))

    private fun code(block: () -> Unit): String = assertThrows(PdfFailure::class.java) { block() }.code

    private fun written(document: PDDocument): PDDocument {
        val output = ByteArrayOutputStream()
        PdfWriting.write(document, output, StopSignal.NEVER)
        return PDDocument.load(output.toByteArray())
    }

    private fun xmp(document: PDDocument): String = (document.documentCatalog.cosObject.getDictionaryObject(COSName.METADATA) as COSStream)
        .createInputStream().use { String(it.readBytes(), Charsets.UTF_8) }

    @Test
    fun `info keys are set, cleared or kept and other fields are ignored`() {
        val changes = InfoChanges.parse(payload("""{ "info": { "title": "T", "author": null, "producer": "x", "level": 3 }, "extra": 1 }"""))
        assertEquals(mapOf(InfoChanges.TITLE to "T", InfoChanges.AUTHOR to null), changes)
        assertEquals(emptyMap<String, String?>(), InfoChanges.parse(payload("""{ "info": {} }""")))
        val long = "a".repeat(PdfContract.MAX_INFO_VALUE_LENGTH)
        assertEquals(long, InfoChanges.parse(payload("""{ "info": { "subject": "$long" } }"""))[InfoChanges.SUBJECT])
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { InfoChanges.parse(payload("""{ "info": { "subject": "${long}a" } }""")) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { InfoChanges.parse(payload("""{ "info": { "title": 3 } }""")) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { InfoChanges.parse(payload("""{ "values": {} }""")) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { InfoChanges.parse(payload("""{ "info": [] }""")) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { InfoChanges.parse(payload("""{ "info": null }""")) })
    }

    @Test
    fun `setInfo changes the information and the XMP the fallback title reads`() {
        PDDocument.load(PdfFixtures.withXmp()).use { document ->
            val title = "新的标题 <&> \"quoted\""
            val warnings = InfoChanges.apply(document, mapOf(InfoChanges.TITLE to title, InfoChanges.AUTHOR to null, InfoChanges.KEYWORDS to "a, b"))
            assertEquals(emptyList<String>(), warnings)
            written(document).use { copy ->
                val info = DocumentInfoReader.read(copy, 0, StopSignal.NEVER)
                assertEquals(title, info[PdfContract.KEY_TITLE])
                assertNull("author cleared", info[PdfContract.KEY_AUTHOR])
                assertEquals("subject kept", PdfFixtures.SUBJECT, info[PdfContract.KEY_SUBJECT])
                assertEquals("a, b", info[PdfContract.KEY_KEYWORDS])
                assertEquals(PdfFixtures.CREATOR, info[PdfContract.KEY_CREATOR])
                assertEquals("the XMP fallback reads the same title", title, DocumentInfoReader.xmpTitle(copy))
                val packet = xmp(copy)
                assertTrue(packet, packet.startsWith("<?xpacket begin="))
                assertTrue(packet, packet.trimEnd().endsWith("<?xpacket end='w'?>") || packet.trimEnd().endsWith("<?xpacket end=\"w\"?>"))
                assertFalse("dc:creator removed: $packet", packet.contains("dc:creator"))
                assertTrue(packet, packet.contains(">a, b</pdf:Keywords>"))
                assertTrue("other properties stay: $packet", packet.contains("<xmp:CreateDate>2026-11-12T08:00:00Z</xmp:CreateDate>"))
                assertTrue(packet, packet.contains("<dc:format>application/pdf</dc:format>"))
                assertTrue(packet, packet.contains("x-default\">${PdfFixtures.SUBJECT}<") || packet.contains("x-default'>${PdfFixtures.SUBJECT}<"))
                assertEquals("one title only", 1, Regex("<dc:title").findAll(packet).count())
            }
        }
    }

    @Test
    fun `a cleared title leaves no XMP fallback`() {
        PDDocument.load(PdfFixtures.withXmp()).use { document ->
            InfoChanges.apply(document, mapOf(InfoChanges.TITLE to null))
            written(document).use { copy ->
                assertNull(DocumentInfoReader.read(copy, 0, StopSignal.NEVER)[PdfContract.KEY_TITLE])
                assertNull(DocumentInfoReader.xmpTitle(copy))
            }
        }
    }

    @Test
    fun `attribute-form properties are replaced by elements`() {
        val packet = """<?xpacket begin='' id='W5M0MpCehiHzreSzNTczkc9d'?><x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>""" +
            """<rdf:Description rdf:about='' xmlns:pdf='http://ns.adobe.com/pdf/1.3/' pdf:Keywords='old' pdf:Producer='p'/>""" +
            """<rdf:Description rdf:about='' xmlns:xmp='http://ns.adobe.com/xap/1.0/' xmp:CreatorTool='tool'/></rdf:RDF></x:xmpmeta><?xpacket end='w'?>"""
        val synced = String(XmpSync.apply(packet.toByteArray(), mapOf(InfoChanges.KEYWORDS to "new", InfoChanges.CREATOR to null, InfoChanges.TITLE to "T"))!!, Charsets.UTF_8)
        assertFalse(synced, synced.contains("old"))
        assertFalse(synced, synced.contains("tool"))
        assertTrue(synced, synced.contains(">new</pdf:Keywords>"))
        assertTrue(synced, synced.contains("pdf:Producer=\"p\""))
        assertEquals("T", DocumentInfoReader.xmpTitle(synced))
    }

    @Test
    fun `an XMP packet that cannot be parsed is removed with a warning`() {
        for (packet in listOf(
            "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>",
            "<?xml version='1.0'?><!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x:xmpmeta xmlns:x='adobe:ns:meta/'>&e;</x:xmpmeta>",
            "<x:xmpmeta xmlns:x='adobe:ns:meta/'/>",
        )) {
            PDDocument.load(PdfFixtures.singlePageText()).use { document ->
                val stream = PDStream(document, ByteArrayInputStream(packet.toByteArray())).cosObject
                stream.setItem(COSName.TYPE, COSName.METADATA)
                document.documentCatalog.cosObject.setItem(COSName.METADATA, stream)
                val warnings = InfoChanges.apply(document, mapOf(InfoChanges.TITLE to "T"))
                assertEquals(packet, 1, warnings.size)
                assertNull(packet, document.documentCatalog.cosObject.getDictionaryObject(COSName.METADATA))
                assertEquals("T", document.documentInformation.title)
            }
        }
        PDDocument.load(PdfFixtures.singlePageText()).use { document ->
            assertEquals("no XMP, no warning", emptyList<String>(), InfoChanges.apply(document, mapOf(InfoChanges.AUTHOR to "A")))
        }
    }

    @Test
    fun `outline entries follow the payload rules`() {
        val entries = OutlineChanges.parse(
            payload("""{ "items": [ { "title": "A", "page": 2, "level": 7, "children": [ { "title": "A.1", "page": 3.0 }, { "title": "Group", "page": null, "children": [] } ] }, { "title": "B", "page": 0 } ] }"""),
            3,
        )
        assertEquals(
            listOf(
                OutlineChanges.Entry("A", 2, listOf(OutlineChanges.Entry("A.1", 3, emptyList()), OutlineChanges.Entry("Group", 0, emptyList()))),
                OutlineChanges.Entry("B", 0, emptyList()),
            ),
            entries,
        )
        assertEquals(emptyList<OutlineChanges.Entry>(), OutlineChanges.parse(payload("""{ "items": [] }"""), 3))
        assertEquals(emptyList<OutlineChanges.Entry>(), OutlineChanges.parse(payload("""{ "items": null }"""), 3))
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { OutlineChanges.parse(payload("""{ }"""), 3) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { OutlineChanges.parse(payload("""{ "items": [ { "page": 1 } ] }"""), 3) })
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { OutlineChanges.parse(payload("""{ "items": [ { "title": "A", "page": 1.5 } ] }"""), 3) })
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, code { OutlineChanges.parse(payload("""{ "items": [ { "title": "A", "page": 4 } ] }"""), 3) })
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, code { OutlineChanges.parse(payload("""{ "items": [ { "title": "A", "children": [ { "title": "B", "page": -1 } ] } ] }"""), 3) })
    }

    @Test
    fun `the ceilings of entries and depth are LIMIT_EXCEEDED`() {
        assertEquals(PdfContract.MAX_OUTLINE_ITEMS, OutlineChanges.parse(payload(flat(PdfContract.MAX_OUTLINE_ITEMS)), 5).size)
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, code { OutlineChanges.parse(payload(flat(PdfContract.MAX_OUTLINE_ITEMS + 1)), 5) })
        assertEquals(1, OutlineChanges.parse(payload(deep(PdfContract.MAX_OUTLINE_DEPTH)), 5).size)
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, code { OutlineChanges.parse(payload(deep(PdfContract.MAX_OUTLINE_DEPTH + 1)), 5) })
    }

    @Test
    fun `10,000 entries over 32 levels are written and read back`() {
        PDDocument.load(PdfFixtures.multiPageWithOutline()).use { document ->
            val pages = document.numberOfPages
            // 32 levels of one entry each, then 9,968 top-level entries. zh-CN: 32 层各一条, 再加 9,968 条顶层条目.
            val json = buildString {
                append("""{ "items": [ """).append(deepItems(PdfContract.MAX_OUTLINE_DEPTH, pages))
                for (index in 1..PdfContract.MAX_OUTLINE_ITEMS - PdfContract.MAX_OUTLINE_DEPTH) append(""", { "title": "Entry $index", "page": ${1 + index % pages} }""")
                append(" ] }")
            }
            val entries = OutlineChanges.parse(payload(json), pages)
            OutlineChanges.apply(document, entries)
            written(document).use { copy ->
                val read = ArrayList<OutlineReader.Entry>()
                OutlineReader.walk(copy, StopSignal.NEVER) { read += it; true }
                assertEquals(PdfContract.MAX_OUTLINE_ITEMS, read.size)
                assertEquals((0 until PdfContract.MAX_OUTLINE_DEPTH).toList(), read.take(PdfContract.MAX_OUTLINE_DEPTH).map { it.level })
                assertEquals(OutlineReader.Entry("Level 32", 32 % pages + 1, 31), read[31])
                assertEquals(OutlineReader.Entry("Entry 9968", 1 + 9968 % pages, 0), read.last())
            }
        }
    }

    @Test
    fun `entries without a page and removal read back as getOutline reports them`() {
        PDDocument.load(PdfFixtures.multiPageWithOutline()).use { document ->
            OutlineChanges.apply(document, OutlineChanges.parse(payload("""{ "items": [ { "title": "Group", "children": [ { "title": "第一章", "page": 2 } ] } ] }"""), document.numberOfPages))
            written(document).use { copy ->
                val read = ArrayList<OutlineReader.Entry>()
                OutlineReader.walk(copy, StopSignal.NEVER) { read += it; true }
                assertEquals(listOf(OutlineReader.Entry("Group", 0, 0), OutlineReader.Entry("第一章", 2, 1)), read)
            }
            OutlineChanges.apply(document, emptyList())
            written(document).use { copy ->
                assertNull(copy.documentCatalog.documentOutline)
                assertEquals(false, OutlineReader.read(copy, StopSignal.NEVER)[PdfContract.KEY_HAS_MORE])
            }
        }
    }

    private fun flat(count: Int): String = (1..count).joinToString(", ", """{ "items": [ """, " ] }") { """{ "title": "E$it", "page": 1 }""" }

    private fun deep(levels: Int): String = """{ "items": [ ${deepItems(levels, 5)} ] }"""

    private fun deepItems(levels: Int, pages: Int): String {
        var item = """{ "title": "Level $levels", "page": ${levels % pages + 1} }"""
        for (level in levels - 1 downTo 1) item = """{ "title": "Level $level", "page": ${level % pages + 1}, "children": [ $item ] }"""
        return item
    }

}

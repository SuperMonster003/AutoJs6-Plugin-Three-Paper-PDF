package io.github.supermonster003.autojs6.plugin.three.paper.pdf.write

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSBoolean
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNull
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * The object stream writer of roadmap P6.7 (D48, D65, spike S4): every runtime fixture and committed sample written
 * with object streams reads back like the classic output (pages, text, outline, form fields, document information),
 * a damaged result or a failing renderer check falls back to the classic writer with a warning, the text-type
 * multi-page sample shrinks to at most 95 % of the classic size, and the writer refuses what it cannot write. The
 * outputs go to the directory of the system property `paper.objectStreamsOutput` for `.python/check_object_streams.py`
 * (pypdf), and `-Dpaper.extraPdfs=<file;file>` measures other PDFs too.
 * zh-CN: 路线图 P6.7 的对象流写出器 (D48, D65, spike S4): 全部运行时夹具与入库样例用对象流写出后, 回读结果 (页面, 文本, 大纲, 表单
 * 字段, 文档信息) 与经典写法一致; 结果被写坏或渲染检查失败时回落到经典写法并给警告; 文字型多页样本缩小到经典写法的 95% 以内; 写出器
 * 拒绝写不了的内容. 输出放到系统属性 `paper.objectStreamsOutput` 指定的目录, 供 `.python/check_object_streams.py` (pypdf) 检查;
 * `-Dpaper.extraPdfs=<文件;文件>` 时还测量其他 PDF.
 */
class ObjectStreamWriterTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private class Input(val name: String, val bytes: ByteArray, val password: String? = null)

    /** What the acceptance compares between two outputs. zh-CN: 验收在两份输出之间比较的内容. */
    private data class Summary(
        val pages: Int,
        val boxes: List<String>,
        val text: List<List<String>>,
        val outline: List<String>,
        val fields: List<String>,
        val info: Map<String, String>,
    )

    private fun inputs(): List<Input> {
        val fixtures = PdfFixtures.all().map { (name, bytes) -> Input("fixture-$name", bytes, PdfFixtures.OWNER_PASSWORD.takeIf { name == "encrypted.pdf" }) }
        val directory = File(checkNotNull(System.getProperty("paper.samples")) { "system property paper.samples is not set" })
        val samples = directory.listFiles { file -> file.name.endsWith(".pdf") }.orEmpty().sortedBy { it.name }
            .map { Input("sample-${it.name}", it.readBytes(), SAMPLE_OWNER_PASSWORD.takeIf { _ -> it.name == "encrypted.pdf" }) }
        check(samples.isNotEmpty()) { "no samples in $directory" }
        return fixtures + samples
    }

    private fun load(bytes: ByteArray, password: String? = null): PDDocument = if (password == null) PDDocument.load(bytes) else PDDocument.load(bytes, password)

    private fun classic(input: Input): ByteArray = load(input.bytes, input.password).use { document ->
        ByteArrayOutputStream().also { PdfWriting.write(document, it, StopSignal.NEVER) }.toByteArray()
    }

    private fun compact(
        input: Input,
        render: RenderCheck = RenderCheck.NONE,
        damage: ((File) -> Unit)? = null,
        stop: StopSignal = StopSignal.NEVER,
    ): Pair<CompactWriting.Result, ByteArray> = load(input.bytes, input.password).use { document ->
        val output = ByteArrayOutputStream()
        val result = CompactWriting.write(document, output, temporary.root, stop = stop, render = render, damage = damage)
        result to output.toByteArray()
    }

    private fun summary(bytes: ByteArray, password: String? = null): Summary = load(bytes, password).use { document ->
        Summary(
            pages = document.numberOfPages,
            boxes = document.pages.map { "${describe(it.mediaBox.cosArray)} ${describe(it.cropBox.cosArray)} ${it.rotation}" },
            text = document.pages.map { text(it) },
            outline = outline(document),
            fields = fields(document),
            info = document.documentInformation.cosObject.let { info ->
                info.keySet().filter { it.name !in CHANGED_INFO }.associate { it.name to describe(info.getDictionaryObject(it)) }.toSortedMap()
            },
        )
    }

    /** Shown strings of the page content (`Tj`, `TJ`, `'`, `"`). zh-CN: 页面内容中显示的字符串. */
    private fun text(page: PDPage): List<String> {
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

    private fun outline(document: PDDocument): List<String> {
        val result = ArrayList<String>()
        fun walk(node: PDOutlineNode, depth: Int) {
            for (item in node.children()) {
                val page = try {
                    item.findDestinationPage(document)?.let { document.pages.indexOf(it) }
                } catch (e: IOException) {
                    -2
                }
                result += "$depth ${item.title} -> $page"
                walk(item, depth + 1)
            }
        }
        document.documentCatalog.documentOutline?.let { walk(it, 0) }
        return result
    }

    private fun fields(document: PDDocument): List<String> {
        val form = document.documentCatalog.getAcroForm(null) ?: return emptyList()
        return form.fieldTree.map { field ->
            val widgets = (field as? PDTerminalField)?.widgets?.map { widget ->
                "${widget.page?.let { document.pages.indexOf(it) }} ${describe(widget.rectangle.cosArray)} ${describe(widget.cosObject.getDictionaryObject(COSName.AS))}"
            }
            val value = describe(field.cosObject.getDictionaryObject(COSName.V))
            val default = describe(field.cosObject.getDictionaryObject(COSName.DV))
            "${field.fullyQualifiedName} ${field.fieldType} ${field.cosObject.getInt(COSName.FF, 0)} $value $default $widgets"
        } + "xfa ${form.hasXFA()} ${form.xfaIsDynamic()}"
    }

    /** A readable form of a value; dictionaries list their sorted keys only. zh-CN: 值的可读形式; 字典只列出排序后的键. */
    private fun describe(base: COSBase?): String = when (base) {
        null, is COSNull -> "null"
        is COSObject -> describe(base.getObject())
        is COSString -> "(" + base.string + ")"
        is COSName -> "/" + base.name
        is COSNumber -> String.format(Locale.ROOT, "%.3f", base.floatValue())
        is COSBoolean -> base.value.toString()
        is COSArray -> base.joinToString(" ", "[", "]") { describe(it) }
        is COSDictionary -> base.keySet().map { it.name }.sorted().joinToString(" ", "<<", ">>")
        else -> base.javaClass.simpleName
    }

    private fun output(name: String, bytes: ByteArray) {
        val directory = System.getProperty("paper.objectStreamsOutput")?.let { File(it) } ?: return
        directory.mkdirs()
        File(directory, name).writeBytes(bytes)
    }

    private fun assertObjectStreamLayout(name: String, bytes: ByteArray) {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        assertTrue("$name has an object stream", raw.contains("/Type /ObjStm"))
        assertTrue("$name has a cross-reference stream", raw.contains("/Type /XRef"))
        assertFalse("$name has no trailer", raw.contains("trailer"))
        assertFalse("$name has no cross-reference table", raw.contains("\nxref\n"))
        val generations = Regex("""(?m)^\d+ (\d+) obj""").findAll(raw).map { it.groupValues[1] }.toSet()
        assertEquals("$name numbers every object with generation 0", setOf("0"), generations)
    }

    @Test
    fun `every fixture and sample reads back like the classic output`() {
        val sizes = ArrayList<String>()
        for (input in inputs()) {
            val classic = classic(input)
            val (result, compact) = compact(input)
            assertTrue("${input.name}: ${result.warnings}", result.objectStreams)
            assertEquals(input.name, emptyList<String>(), result.warnings)
            assertEquals(input.name, compact.size.toLong(), result.sizeBytes)
            assertObjectStreamLayout(input.name, compact)
            val expected = summary(classic)
            assertEquals(input.name, expected, summary(compact))
            assertEquals(input.name, summary(input.bytes, input.password), expected)
            output(input.name.removeSuffix(".pdf") + ".classic.pdf", classic)
            output(input.name.removeSuffix(".pdf") + ".objstm.pdf", compact)
            sizes += String.format(Locale.ROOT, "%s source %d classic %d objstm %d (%.1f %%)", input.name, input.bytes.size, classic.size, compact.size, 100.0 * compact.size / classic.size)
        }
        sizes.forEach { println("S4 $it") }
    }

    @Test
    fun `the text-type multi-page sample is at most 95 percent of the classic size`() {
        val input = Input("multi-page-text", PdfFixtures.multiPageText())
        val classic = classic(input).size
        val (result, compact) = compact(input)
        assertTrue(result.objectStreams)
        println(String.format(Locale.ROOT, "S4 text-type multi-page sample: classic %d objstm %d (%.1f %%)", classic, compact.size, 100.0 * compact.size / classic))
        assertTrue("${compact.size} against $classic", compact.size <= classic * 0.95)
    }

    @Test
    fun `a damaged result falls back to the classic writer`() {
        val input = Input("outline", PdfFixtures.multiPageWithOutline())
        val damages = mapOf<String, (File) -> Unit>(
            "truncated" to { file -> file.writeBytes(file.readBytes().let { it.copyOf(it.size / 2) }) },
            "object stream zeroed" to { file ->
                val bytes = file.readBytes()
                val raw = bytes.toString(Charsets.ISO_8859_1)
                val start = raw.indexOf("stream\r\n", raw.indexOf("/Type /ObjStm")) + "stream\r\n".length
                for (index in start until raw.indexOf("\r\nendstream", start)) bytes[index] = 0
                file.writeBytes(bytes)
            },
            "page dropped" to { file ->
                PDDocument.load(file.readBytes()).use { document ->
                    document.removePage(document.numberOfPages - 1)
                    document.save(file)
                }
            },
        )
        for ((label, damage) in damages) {
            val (result, bytes) = compact(input, damage = damage)
            assertFalse(label, result.objectStreams)
            assertEquals(label, 1, result.warnings.size)
            assertTrue(result.warnings.single(), result.warnings.single().startsWith("object streams were not used because "))
            val raw = bytes.toString(Charsets.ISO_8859_1)
            assertTrue(label, raw.contains("\nxref\n") && raw.contains("trailer"))
            assertFalse(label, raw.contains("/Type /ObjStm"))
            assertEquals(label, bytes.size.toLong(), result.sizeBytes)
            assertEquals(label, summary(classic(input)), summary(bytes))
        }
    }

    @Test
    fun `a failing renderer check falls back to the classic writer`() {
        val input = Input("text", PdfFixtures.singlePageText())
        var verifying = 0
        val output = ByteArrayOutputStream()
        val result = load(input.bytes).use { document ->
            CompactWriting.write(document, output, temporary.root, render = { _, pages -> "the renderer saw $pages pages" }, onVerifying = { verifying++ })
        }
        assertEquals(1, verifying)
        assertFalse(result.objectStreams)
        assertTrue(result.warnings.single(), result.warnings.single().contains("the renderer saw 1 pages"))
        assertEquals(1, summary(output.toByteArray()).pages)
    }

    @Test
    fun `objectStreams false writes the classic output without a warning`() {
        val output = ByteArrayOutputStream()
        var verifying = 0
        val result = load(PdfFixtures.singlePageText()).use { document ->
            CompactWriting.write(document, output, temporary.root, objectStreams = false, render = RenderCheck.NONE, onVerifying = { verifying++ })
        }
        assertFalse(result.objectStreams)
        assertEquals(emptyList<String>(), result.warnings)
        assertEquals(0, verifying)
        assertTrue(output.toByteArray().toString(Charsets.ISO_8859_1).contains("\nxref\n"))
        assertEquals(0, temporary.root.list().orEmpty().size)
    }

    @Test
    fun `cancellation ends the call instead of falling back`() {
        var checks = 0
        val stop = StopSignal { if (++checks > 3) StopSignal.cancelled() else null }
        val failure = assertThrows(PdfFailure::class.java) { compact(Input("multi-page-text", PdfFixtures.multiPageText()), stop = stop) }
        assertEquals(PdfErrorCodes.CANCELLED, failure.code)
        assertEquals(0, temporary.root.list().orEmpty().size)
    }

    @Test
    fun `an encrypted input needs its owner password`() {
        load(PdfFixtures.encrypted(), PdfFixtures.USER_PASSWORD).use { document ->
            val failure = assertThrows(PdfFailure::class.java) { CompactWriting.write(document, ByteArrayOutputStream(), temporary.root, render = RenderCheck.NONE) }
            assertEquals(PdfErrorCodes.PERMISSION_DENIED, failure.code)
        }
        load(PdfFixtures.encrypted(), PdfFixtures.OWNER_PASSWORD).use { document ->
            assertThrows(IllegalStateException::class.java) { ObjectStreamWriter(document).write(ByteArrayOutputStream()) }
            document.isAllSecurityToBeRemoved = true
            val output = ByteArrayOutputStream()
            ObjectStreamWriter(document).write(output)
            load(output.toByteArray()).use { copy ->
                assertFalse(copy.isEncrypted)
                assertEquals(2, copy.numberOfPages)
            }
        }
    }

    @Test
    fun `objects per stream bounds the object streams`() {
        assertThrows(IllegalArgumentException::class.java) { load(PdfFixtures.singlePageText()).use { ObjectStreamWriter(it, objectsPerStream = 0) } }
        assertThrows(IllegalArgumentException::class.java) {
            load(PdfFixtures.singlePageText()).use { ObjectStreamWriter(it, objectsPerStream = ObjectStreamWriter.MAX_OBJECTS_PER_STREAM + 1) }
        }
        val bytes = PdfFixtures.multiPageText()
        val full = load(bytes).use { document -> ObjectStreamWriter(document).write(ByteArrayOutputStream()) }
        assertEquals((full.compressedObjects + ObjectStreamWriter.MAX_OBJECTS_PER_STREAM - 1) / ObjectStreamWriter.MAX_OBJECTS_PER_STREAM, full.objectStreams)
        for (size in listOf(1, 7, full.compressedObjects, full.compressedObjects - 1)) {
            val output = ByteArrayOutputStream()
            val summary = load(bytes).use { document -> ObjectStreamWriter(document, objectsPerStream = size).write(output) }
            assertEquals("$size per stream", full.compressedObjects, summary.compressedObjects)
            assertEquals("$size per stream", (summary.compressedObjects + size - 1) / size, summary.objectStreams)
            assertEquals("$size per stream", output.size().toLong(), summary.bytes)
            assertEquals("$size per stream", summary(bytes), summary(output.toByteArray()))
        }
    }

    @Test
    fun `direct cycles and wrong stream lengths end with an IOException`() {
        PDDocument().use { document ->
            document.addPage(PDPage())
            val loop = COSDictionary().apply { isDirect = true }
            loop.setItem(COSName.getPDFName("Self"), loop)
            document.getPage(0).cosObject.setItem(COSName.getPDFName("Loop"), loop)
            val failure = assertThrows(IOException::class.java) { ObjectStreamWriter(document).write(ByteArrayOutputStream()) }
            assertTrue(failure.message, failure.message!!.contains("refer to themselves"))
        }
        PDDocument().use { document ->
            val page = PDPage()
            val stream = PDStream(document, ByteArrayInputStream("BT ET\n".toByteArray(Charsets.ISO_8859_1)))
            page.setContents(stream)
            document.addPage(page)
            stream.cosObject.setInt(COSName.LENGTH, 3)
            val failure = assertThrows(IOException::class.java) { ObjectStreamWriter(document).write(ByteArrayOutputStream()) }
            assertTrue(failure.message, failure.message!!.contains("/Length"))
        }
    }

    @Test
    fun `the read-back check names the first difference`() {
        val source = PdfFixtures.multiPageWithOutline()
        load(source).use { document ->
            val same = temporary.newFile("same.pdf").apply { writeBytes(source) }
            assertNull(ReadBackCheck.verify(document, same, temporary.root))
            val shorter = temporary.newFile("shorter.pdf")
            load(source).use { copy ->
                copy.removePage(0)
                copy.save(shorter)
            }
            assertTrue(ReadBackCheck.verify(document, shorter, temporary.root)!!.contains("pages"))
            val retitled = temporary.newFile("retitled.pdf")
            load(source).use { copy ->
                copy.documentInformation.title = "Another title"
                copy.save(retitled)
            }
            assertTrue(ReadBackCheck.verify(document, retitled, temporary.root)!!.contains("/Title"))
            val garbage = temporary.newFile("garbage.pdf").apply { writeBytes(ByteArray(64)) }
            assertTrue(ReadBackCheck.verify(document, garbage, temporary.root)!!.startsWith("PDFBox cannot load"))
        }
    }

    @Test
    fun `other PDFs given by paper extraPdfs are measured`() {
        val files = System.getProperty("paper.extraPdfs")?.split(';')?.filter { it.isNotBlank() }.orEmpty()
        assumeTrue("set -Dpaper.extraPdfs=<file;file> to measure other PDFs", files.isNotEmpty())
        for (path in files) {
            val file = File(path)
            val input = Input("extra-${file.name.replace(' ', '-')}", file.readBytes())
            val start = System.nanoTime()
            val classic = classic(input)
            val classicMillis = (System.nanoTime() - start) / 1_000_000
            val middle = System.nanoTime()
            val (result, compact) = compact(input)
            val compactMillis = (System.nanoTime() - middle) / 1_000_000
            assertTrue("${input.name}: ${result.warnings}", result.objectStreams)
            assertEquals(input.name, summary(classic), summary(compact))
            output(input.name.removeSuffix(".pdf") + ".classic.pdf", classic)
            output(input.name.removeSuffix(".pdf") + ".objstm.pdf", compact)
            println(
                String.format(
                    Locale.ROOT, "S4 %s: source %d classic %d (%d ms) objstm %d (%d ms, with the read-back check) %.1f %%",
                    input.name, input.bytes.size, classic.size, classicMillis, compact.size, compactMillis, 100.0 * compact.size / classic.size,
                ),
            )
        }
    }

    private companion object {

        /** `ownerPassword` of `samples/fixtures/fixtures.json`. zh-CN: `samples/fixtures/fixtures.json` 的 `ownerPassword`. */
        const val SAMPLE_OWNER_PASSWORD = "paper-owner"

        /** Information entries every write sets. zh-CN: 每次写出都会设置的文档信息条目. */
        val CHANGED_INFO = setOf("ModDate", "Producer")

    }

}

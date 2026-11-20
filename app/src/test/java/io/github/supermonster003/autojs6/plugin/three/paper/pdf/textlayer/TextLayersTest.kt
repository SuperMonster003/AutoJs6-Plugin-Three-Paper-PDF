package io.github.supermonster003.autojs6.plugin.three.paper.pdf.textlayer

import com.tom_roush.fontbox.cmap.CMap
import com.tom_roush.fontbox.cmap.CMapParser
import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.assertWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * `addTextLayer` on the JVM (roadmap P6.5, D54, D63): the payload rules (line ceiling, duplicate pages, sizes, bounds,
 * cleaning), the text objects placed on the displayed page (checked through `PageFrame.display`, which `search` uses:
 * the baseline at a fifth of the line height above the bottom, the advance along the display x axis, the width filled
 * by horizontal scaling; rotated pages, a crop box), the `/ToUnicode` mapping of every code back to the line text
 * (Chinese, Latin, mixed, emoji), the font program and the size of the font objects, empty layers written unchanged,
 * the content arrangement that keeps rendering unchanged, and the slot overflow warning. PDFBox-Android cannot extract
 * text on the JVM (its glyph list and the Identity-H CMap are Android assets), so `getText`, `search`, pages with text
 * skipped unless forced and the rendering comparison run on devices (`TextLayerDeviceTest`).
 * zh-CN: JVM 上的 `addTextLayer` (路线图 P6.5, D54, D63): payload 规则 (行数上限, 重复页, 尺寸, 行框, 清理), 文本对象在显示页面上
 * 的位置 (经 `search` 所用的 `PageFrame.display` 核对: 基线位于底边以上行高的五分之一处, 前进方向沿显示 x 轴, 水平缩放填满行宽;
 * 旋转页, 裁剪框), `/ToUnicode` 把每个编码映射回行文本 (中文, 拉丁, 混排, 表情), 字体程序与字体对象体积, 空图层原样写出, 保证渲染
 * 不变的内容安排, 以及槽位用尽的警告. PDFBox-Android 在 JVM 上无法抽取文本 (字形表与 Identity-H CMap 是 Android 资源), 因此
 * `getText`, `search`, 已有文本的页除非强制否则跳过, 以及渲染比较在设备上运行 (`TextLayerDeviceTest`).
 */
class TextLayersTest {

    private fun payload(json: String) = PdfPayload.parse(json.toByteArray(Charsets.UTF_8))

    private fun code(block: () -> Unit): String = assertThrows(PdfFailure::class.java) { block() }.code

    private fun bytes(document: PDDocument): ByteArray = ByteArrayOutputStream().also { PdfWriting.write(document, it, StopSignal.NEVER) }.toByteArray()

    /** Forced, so the JVM needs no text extraction. zh-CN: 强制写入, 因此 JVM 上不需要文本抽取. */
    private fun apply(document: PDDocument, layers: List<TextLayers.Layer>): TextLayers.Outcome =
        TextLayers.apply(document, layers, true, StopSignal.NEVER) { _, _ -> }

    private fun layer(page: Int, width: Double, height: Double, vararg lines: Pair<String, DoubleArray>) =
        TextLayers.Layer("layers[${page - 1}]", page.toLong(), width, height, lines.map { (text, box) -> TextLayers.Line(TextLayers.clean(text), box[0], box[1], box[2], box[3]) })

    private fun box(vararg values: Double) = values

    private fun blank(pages: Int = 1, size: PDRectangle = PDRectangle.A4) = PDDocument().apply { repeat(pages) { addPage(PDPage(size)) } }

    private fun decoded(base: COSBase): String = (base as COSStream).createInputStream().use { String(it.readBytes(), Charsets.ISO_8859_1) }

    /** One text object of a layer: `size Tf`, `scale Tz`, the text matrix and the codes. zh-CN: 图层中的一个文本对象: `size Tf`, `scale Tz`, 文本矩阵与编码. */
    private class Shown(val size: Double, val scale: Double, val matrix: DoubleArray, val codes: IntArray)

    private val shownPattern = Regex("""BT /(\S+) (\S+) Tf (\S+) Tz (\S+) (\S+) (\S+) (\S+) (\S+) (\S+) Tm <([0-9A-F]*)> Tj ET""")

    /** The text objects of the layer of [page] (the last content stream). zh-CN: [page] 的图层 (最后一个内容流) 中的文本对象. */
    private fun shown(page: PDPage): List<Shown> {
        val contents = page.cosObject.getDictionaryObject(COSName.CONTENTS) as COSArray
        val layer = decoded(contents.getObject(contents.size() - 1))
        return shownPattern.findAll(layer).map { match ->
            val values = match.groupValues
            val hex = values[10]
            Shown(values[2].toDouble(), values[3].toDouble(), DoubleArray(6) { values[4 + it].toDouble() }, IntArray(hex.length / 4) { hex.substring(it * 4, it * 4 + 4).toInt(16) })
        }.toList()
    }

    /** The `/ToUnicode` CMap of the layer font of [page]. zh-CN: [page] 图层字体的 `/ToUnicode` CMap. */
    private fun toUnicode(page: PDPage, name: String = "GL0"): CMap {
        val fonts = page.cosObject.getDictionaryObject(COSName.RESOURCES).let { it as COSDictionary }.getDictionaryObject(COSName.FONT) as COSDictionary
        val font = fonts.getDictionaryObject(COSName.getPDFName(name)) as COSDictionary
        return (font.getDictionaryObject(COSName.TO_UNICODE) as COSStream).createInputStream().use { CMapParser().parse(it) }
    }

    private fun text(cmap: CMap, codes: IntArray): String = codes.joinToString("") { cmap.toUnicode(it) ?: "?" }

    /**
     * Checks that [shown] covers [bounds] (`[left, top, right, bottom]` in display points) of [page].
     * zh-CN: 检查 [shown] 覆盖 [page] 上的 [bounds] (以显示点数表示的 `[left, top, right, bottom]`).
     */
    private fun assertPlaced(page: PDPage, shown: Shown, bounds: DoubleArray, message: String) {
        val frame = PageFrame.of(page)
        val crop = page.cropBox
        fun display(x: Double, y: Double) = frame.display(x - crop.lowerLeftX, y - crop.lowerLeftY)
        val (a, b, c, d, e, f) = shown.matrix.toList()
        val size = bounds[3] - bounds[1]
        assertEquals("$message: font size", size, shown.size, 0.002)
        val origin = display(e, f)
        assertEquals("$message: baseline x", bounds[0], origin.first, 0.002)
        assertEquals("$message: baseline y", bounds[3] - size / 5, origin.second, 0.002)
        val advance = display(e + a, f + b)
        assertEquals("$message: advance along display x", 1.0, advance.first - origin.first, 1e-9)
        assertEquals("$message: advance along display x", 0.0, advance.second - origin.second, 1e-9)
        val up = display(e + c, f + d)
        assertEquals("$message: up along display -y", 0.0, up.first - origin.first, 1e-9)
        assertEquals("$message: up along display -y", -1.0, up.second - origin.second, 1e-9)
        val units = shown.codes.sumOf { GlyphlessFont.width(it) }
        assertEquals("$message: width", bounds[2] - bounds[0], units / 1000.0 * size * shown.scale / 100, 0.01)
    }

    private operator fun <T> List<T>.component6(): T = this[5]

    /** [bounds] of a [width] x [height] image on the displayed [page]. zh-CN: [width] x [height] 图像上的 [bounds] 在显示页面上的位置. */
    private fun displayed(page: PDPage, width: Double, height: Double, bounds: DoubleArray): DoubleArray {
        val frame = PageFrame.of(page)
        val sx = frame.displayWidth / width
        val sy = frame.displayHeight / height
        return doubleArrayOf(bounds[0] * sx, bounds[1] * sy, bounds[2] * sx, bounds[3] * sy)
    }

    private val emoji = String(Character.toChars(0x1F600))

    private val samples = listOf(
        "中文识别结果第一行" to box(180.0, 240.0, 1460.0, 300.0),
        "Latin line, quick brown fox" to box(180.0, 400.0, 1700.0, 460.0),
        "混排 Mixed 文本 2026" to box(180.0, 560.0, 1500.0, 640.0),
        "表情 $emoji smile" to box(180.0, 720.0, 1000.0, 790.0),
    )

    @Test
    fun `payload rules`() {
        assertEquals(emptyList<TextLayers.Layer>(), TextLayers.parse(payload("""{ "layers": [] }""")))
        val parsed = TextLayers.parse(payload("""{ "layers": [ { "page": 2, "width": 100, "height": 50.5, "lines": [
            { "text": " a\tb ", "bounds": [1, 2, 3.5, 4] }, { "text": "c", "bounds": { "left": 5, "top": 6, "right": 7, "bottom": 8 } } ] } ] }"""))
        assertEquals(1, parsed.size)
        assertEquals(2L, parsed[0].page)
        assertEquals(50.5, parsed[0].height, 0.0)
        assertEquals("a b", parsed[0].lines[0].text)
        assertEquals(3.5, parsed[0].lines[0].right, 0.0)
        assertEquals(8.0, parsed[0].lines[1].bottom, 0.0)
        val invalid = listOf(
            """{ }""",
            """{ "layers": {} }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": 10 } ] }""",
            """{ "layers": [ { "width": 10, "height": 10, "lines": [] } ] }""",
            """{ "layers": [ { "page": 1, "width": 0, "height": 10, "lines": [] } ] }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": -1, "lines": [] } ] }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": 10, "lines": [] }, { "page": 1, "width": 10, "height": 10, "lines": [] } ] }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": 10, "lines": [ { "text": "a", "bounds": [1, 2, 3] } ] } ] }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": 10, "lines": [ { "text": "a", "bounds": [1, 2, 3, "4"] } ] } ] }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": 10, "lines": [ { "bounds": [1, 2, 3, 4] } ] } ] }""",
            """{ "layers": [ { "page": 1, "width": 10, "height": 10, "lines": [ { "text": "a" } ] } ] }""",
        )
        for (json in invalid) assertEquals(json, PdfErrorCodes.INVALID_ARGUMENT, code { TextLayers.parse(payload(json)) })
        fun lines(count: Int) = List(count) { "0" }.joinToString(",")
        val half = PdfContract.MAX_TEXT_LAYER_LINES / 2
        val over = """{ "layers": [ { "page": 1, "width": 1, "height": 1, "lines": [${lines(half + 1)}] }, { "page": 2, "width": 1, "height": 1, "lines": [${lines(half)}] } ] }"""
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, code { TextLayers.parse(payload(over)) })
        val atLimit = """{ "layers": [ { "page": 1, "width": 1, "height": 1, "lines": [${lines(half)}] }, { "page": 2, "width": 1, "height": 1, "lines": [${lines(half)}] } ] }"""
        assertEquals("the ceiling itself passes the count, then the entries are checked", PdfErrorCodes.INVALID_ARGUMENT, code { TextLayers.parse(payload(atLimit)) })
    }

    @Test
    fun `clean turns controls into spaces and unpaired surrogates into the replacement character`() {
        val high = 0xD83D.toChar()
        val low = 0xDE00.toChar()
        val bell = 7.toChar()
        assertEquals("a b", TextLayers.clean("${9.toChar()}a${bell}b${10.toChar()}"))
        assertEquals("x${0xFFFD.toChar()}y", TextLayers.clean("x${high}y"))
        assertEquals("x${0xFFFD.toChar()}", TextLayers.clean("x$low"))
        assertEquals("$high$low", TextLayers.clean(" $high$low "))
        assertEquals("", TextLayers.clean(" ${13.toChar()}${10.toChar()} "))
    }

    @Test
    fun `lines are placed on the line boxes and map back to their text`() {
        val output = blank().use { document ->
            val outcome = apply(document, listOf(layer(1, 2480.0, 3508.0, *samples.toTypedArray())))
            assertEquals(1, outcome.layerPages)
            assertEquals(0, outcome.skippedPages)
            assertEquals(samples.size, outcome.lines)
            assertEquals(emptyList<String>(), outcome.warnings)
            bytes(document)
        }
        PDDocument.load(output).use { document ->
            val page = document.getPage(0)
            val shown = shown(page)
            assertEquals(samples.size, shown.size)
            val cmap = toUnicode(page)
            samples.forEachIndexed { index, (line, bounds) ->
                assertEquals(line, text(cmap, shown[index].codes))
                assertPlaced(page, shown[index], displayed(page, 2480.0, 3508.0, bounds), line)
            }
        }
    }

    @Test
    fun `rotated pages and crop boxes map the image onto the displayed page`() {
        val fixture = PdfFixtures.rotatedPages()
        val boxes = listOf(box(100.0, 120.0, 900.0, 180.0), box(50.0, 700.0, 700.0, 760.0))
        val output = PDDocument.load(fixture).use { document ->
            val layers = PdfFixtures.ROTATIONS.indices.map { index -> layer(index + 1, 1000.0, 1000.0, "Layer $index" to boxes[0], "Second row $index" to boxes[1]) }
            val outcome = apply(document, layers)
            assertEquals(PdfFixtures.ROTATIONS.size, outcome.layerPages)
            assertEquals(PdfFixtures.ROTATIONS.size * 2, outcome.lines)
            bytes(document)
        }
        PDDocument.load(output).use { document ->
            PdfFixtures.ROTATIONS.forEachIndexed { index, rotation ->
                val page = document.getPage(index)
                assertEquals(rotation, page.rotation)
                val shown = shown(page)
                assertEquals(2, shown.size)
                boxes.forEachIndexed { row, bounds -> assertPlaced(page, shown[row], displayed(page, 1000.0, 1000.0, bounds), "page ${index + 1} row $row") }
            }
        }
        val cropped = blank(size = PDRectangle(0f, 0f, 600f, 800f)).use { document ->
            document.getPage(0).cropBox = PDRectangle(50f, 100f, 500f, 600f)
            document.getPage(0).rotation = 270
            apply(document, listOf(layer(1, 1200.0, 1000.0, "Cropped and turned" to box(100.0, 100.0, 900.0, 160.0))))
            bytes(document)
        }
        PDDocument.load(cropped).use { document ->
            val page = document.getPage(0)
            val bounds = displayed(page, 1200.0, 1000.0, box(100.0, 100.0, 900.0, 160.0))
            assertArrayEquals(doubleArrayOf(50.0, 50.0, 450.0, 80.0), bounds, 1e-9)
            assertPlaced(page, shown(page).single(), bounds, "crop box")
        }
    }

    @Test
    fun `the font objects of a document stay within 2 KiB`() {
        // Latin with a few emoji; one CJK character from each high byte 0x4E - 0x9F with Latin, Korean, full-width
        // forms and an emoji (the heaviest common OCR text); one character from every high byte of the Basic
        // Multilingual Plane (the bound: the `/ToUnicode` CMap has one range per high byte in use).
        // zh-CN: 拉丁文加几个表情; 0x4E - 0x9F 每个高字节取一个中日韩字符, 外加拉丁文, 韩文, 全角形式与表情 (常见 OCR 文本中最重的
        // 情形); 基本多文种平面每个高字节各取一个字符 (上界: `/ToUnicode` CMap 为每个用到的高字节写一个区间).
        val latin = fontBytes("latin", listOf("Latin text 0123456789, the quick brown fox", "Smile $emoji ${String(Character.toChars(0x1F44D))}"))
        assertTrue("Latin: $latin bytes", latin <= 2048)
        val cjk = (0x4E..0x9F).map { (it shl 8 or 0x2A).toChar() }.joinToString("")
        val heavy = fontBytes("CJK", listOf(cjk.substring(0, 30), cjk.substring(30, 60), cjk.substring(60), "Latin text 0123456789", "한국어 문장", "\uFF21\uFF22\uFF23\uFF11\uFF12\uFF13", "表情 $emoji"))
        assertTrue("CJK: $heavy bytes", heavy <= 2048)
        val every = (0..0xFF).filter { it !in 0xD8..0xDF }.map { (it shl 8 or 0x41).toChar() }.joinToString("")
        val bound = fontBytes("every high byte", every.chunked(62))
        assertTrue("every high byte: $bound bytes", bound <= 3072)
    }

    /**
     * Writes [lines] on each of three pages and answers the bytes of the font objects, checking that the pages share one
     * font and that the codes of the last page map back to [lines].
     * zh-CN: 在三页上各写 [lines], 返回字体对象的字节数, 并检查各页共用一份字体, 末页的编码映射回 [lines].
     */
    private fun fontBytes(label: String, lines: List<String>): Int {
        val output = blank(3).use { document ->
            val layers = (1..3).map { page -> layer(page, 1000.0, 1400.0, *lines.mapIndexed { index, line -> line to box(50.0, 50.0 + index * 80.0, 950.0, 110.0 + index * 80.0) }.toTypedArray()) }
            assertEquals(3, apply(document, layers).layerPages)
            bytes(document)
        }
        val sizes = objectSizes(output)
        return PDDocument.load(output).use { document ->
            val fonts = (0 until 3).map { index ->
                (document.getPage(index).resources.cosObject.getDictionaryObject(COSName.FONT) as COSDictionary).getItem(COSName.getPDFName("GL0")) as COSObject
            }
            assertEquals("one font per document", 1, fonts.map { it.objectNumber }.toSet().size)
            val numbers = fontObjects(fonts.first())
            assertEquals(6, numbers.toSet().size)
            val all = LinkedHashSet<Long>().also { reachable(fonts.first(), it) }
            assertTrue("$all holds $numbers", all.containsAll(numbers))
            val total = all.sumOf { sizes.getValue(it) }
            println("addTextLayer font objects ($label): $total bytes in ${all.size} objects (Type0, CIDFont, descriptor, FontFile2, CIDToGIDMap, ToUnicode: ${numbers.map { sizes.getValue(it) }})")
            val page = document.getPage(2)
            val cmap = toUnicode(page)
            assertEquals(lines, shown(page).map { text(cmap, it.codes) })
            total
        }
    }

    /** Object number to its bytes from "N 0 obj" through "endobj". zh-CN: 对象号到其字节数 (从 "N 0 obj" 到 "endobj"). */
    private fun objectSizes(bytes: ByteArray): Map<Long, Int> {
        val text = String(bytes, Charsets.ISO_8859_1)
        return Regex("""(?m)^(\d+) 0 obj""").findAll(text).associate { match ->
            val end = text.indexOf("endobj", match.range.last) + "endobj".length
            match.groupValues[1].toLong() to end - match.range.first
        }
    }

    /** Every indirect object reachable from [base]. zh-CN: 从 [base] 可达的全部间接对象. */
    private fun reachable(base: COSBase?, found: MutableSet<Long>) {
        when (base) {
            is COSObject -> if (found.add(base.objectNumber)) reachable(base.`object`, found)
            is COSDictionary -> base.values.forEach { reachable(it, found) }
            is COSArray -> for (index in 0 until base.size()) reachable(base.get(index), found)
            else -> Unit
        }
    }

    /** Type 0 font, descendant, descriptor, font program, CIDToGIDMap and ToUnicode. zh-CN: Type 0 字体, 后代字体, 描述符, 字体程序, CIDToGIDMap 与 ToUnicode. */
    private fun fontObjects(font: COSObject): List<Long> {
        fun number(base: COSBase?): Long = (base as COSObject).objectNumber
        val type0 = font.`object` as COSDictionary
        val descendant = (type0.getDictionaryObject(COSName.DESCENDANT_FONTS) as COSArray).get(0)
        val cid = (descendant as COSObject).`object` as COSDictionary
        val descriptor = cid.getItem(COSName.FONT_DESC)
        val descriptorDictionary = (descriptor as COSObject).`object` as COSDictionary
        val program = descriptorDictionary.getDictionaryObject(COSName.FONT_FILE2) as COSStream
        val trueType = program.createInputStream().use { TTFParser(true).parse(it) }
        assertEquals(2, trueType.numberOfGlyphs)
        assertEquals(1000, trueType.unitsPerEm)
        assertEquals(GlyphlessFont.BASE_FONT, type0.getNameAsString(COSName.BASE_FONT))
        assertEquals(COSName.IDENTITY_H, type0.getDictionaryObject(COSName.ENCODING))
        assertEquals(COSName.CID_FONT_TYPE2, cid.getDictionaryObject(COSName.SUBTYPE))
        return listOf(
            font.objectNumber,
            number(descendant),
            number(descriptor),
            number(descriptorDictionary.getItem(COSName.FONT_FILE2)),
            number(cid.getItem(COSName.CID_TO_GID_MAP)),
            number(type0.getItem(COSName.TO_UNICODE)),
        )
    }

    @Test
    fun `old content is wrapped so the layer starts from the initial graphics state`() {
        PDDocument.load(PdfFixtures.singlePageText()).use { document ->
            val page = document.getPage(0)
            val before = (page.cosObject.getDictionaryObject(COSName.CONTENTS) as COSStream).let { decoded(it) }
            apply(document, listOf(layer(1, 100.0, 100.0, "Added line" to box(10.0, 80.0, 90.0, 85.0))))
            PDDocument.load(bytes(document)).use { written ->
                val contents = written.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS) as COSArray
                assertEquals(3, contents.size())
                assertEquals("q", decoded(contents.getObject(0)).trim())
                assertEquals(before, decoded(contents.getObject(1)))
                val layer = decoded(contents.getObject(2))
                assertTrue(layer, layer.startsWith("Q\nq 3 Tr\nBT /GL0 "))
                assertTrue(layer, layer.endsWith("ET\nQ\n"))
                val fonts = written.getPage(0).resources.cosObject.getDictionaryObject(COSName.FONT) as COSDictionary
                assertTrue("the old font stays", fonts.size() >= 2)
            }
        }
        blank().use { document ->
            apply(document, listOf(layer(1, 10.0, 10.0, "Alone" to box(1.0, 1.0, 9.0, 2.0))))
            val contents = document.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS) as COSArray
            assertEquals(1, contents.size())
            assertTrue(decoded(contents.getObject(0)).startsWith("q 3 Tr\nBT /GL0 "))
        }
    }

    @Test
    fun `font resource names avoid the names a page already uses`() {
        blank().use { document ->
            val page = document.getPage(0)
            val fonts = COSDictionary().apply { setItem(COSName.getPDFName("GL0"), COSDictionary()) }
            page.cosObject.setItem(COSName.RESOURCES, COSDictionary().apply { setItem(COSName.FONT, fonts) })
            apply(document, listOf(layer(1, 10.0, 10.0, "Named" to box(1.0, 1.0, 9.0, 2.0))))
            assertTrue(decoded((page.cosObject.getDictionaryObject(COSName.CONTENTS) as COSArray).getObject(0)).startsWith("q 3 Tr\nBT /GL1 "))
            assertEquals("the shared font dictionary is not changed", 1, fonts.size())
        }
    }

    @Test
    fun `empty layers write the source unchanged and empty lines are skipped`() {
        val fixture = PdfFixtures.singlePageText()
        PDDocument.load(fixture).use { document ->
            val before = decoded(document.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS))
            val outcome = TextLayers.apply(document, emptyList(), false, StopSignal.NEVER) { _, _ -> }
            assertEquals(0, outcome.layerPages)
            assertEquals(0, outcome.skippedPages)
            assertEquals(0, outcome.lines)
            val output = bytes(document)
            assertFalse(String(output, Charsets.ISO_8859_1).contains(GlyphlessFont.BASE_FONT))
            PDDocument.load(output).use { written ->
                assertEquals(document.numberOfPages, written.numberOfPages)
                assertEquals(before, decoded(written.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS)))
            }
        }
        blank().use { document ->
            val outcome = apply(
                document,
                listOf(
                    layer(
                        1, 100.0, 100.0,
                        " " to box(0.0, 0.0, 50.0, 10.0),
                        "outside" to box(120.0, 0.0, 150.0, 10.0),
                        "flat" to box(0.0, 20.0, 50.0, 20.0),
                        "clipped" to box(-20.0, 90.0, 40.0, 130.0),
                    ),
                ),
            )
            assertEquals("only the clipped line has a box", 1, outcome.lines)
            assertEquals(1, outcome.layerPages)
            val page = document.getPage(0)
            assertPlaced(page, shown(page).single(), displayed(page, 100.0, 100.0, box(0.0, 90.0, 40.0, 100.0)), "clipped")
        }
        blank().use { document ->
            val outcome = apply(document, listOf(layer(1, 100.0, 100.0, "  " to box(0.0, 0.0, 50.0, 10.0))))
            assertEquals(0, outcome.layerPages)
            assertNull("no font without lines", document.getPage(0).resources?.cosObject?.getDictionaryObject(COSName.FONT))
            assertNull(document.getPage(0).cosObject.getDictionaryObject(COSName.CONTENTS))
        }
    }

    @Test
    fun `pages outside the document are out of range`() {
        for (page in listOf(0, 2)) {
            blank().use { document ->
                val layers = listOf(TextLayers.Layer("layers[0]", page.toLong(), 10.0, 10.0, emptyList()))
                assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, code { apply(document, layers) })
            }
        }
    }

    @Test
    fun `characters beyond the slots become the replacement character with a warning`() {
        val many = (0x20000..0x20000 + GlyphlessFont.SLOT_COUNT).joinToString("") { String(Character.toChars(it)) }
        val output = blank().use { document ->
            val outcome = apply(document, listOf(layer(1, 100.0, 100.0, many to box(0.0, 0.0, 100.0, 5.0))))
            assertEquals(1, outcome.warnings.size)
            assertTrue(outcome.warnings.single().text, outcome.warnings.single().text.startsWith("1 distinct characters"))
            assertWarning(outcome.warnings.single(), PdfWarningCodes.CHARACTERS_REPLACED)
            assertEquals(
                mapOf(PdfWarningCodes.ARG_COUNT to 1, PdfWarningCodes.ARG_LIMIT to GlyphlessFont.SLOT_COUNT),
                outcome.warnings.single().args,
            )
            bytes(document)
        }
        PDDocument.load(output).use { document ->
            val page = document.getPage(0)
            assertEquals(many.substring(0, many.length - 2) + 0xFFFD.toChar(), text(toUnicode(page), shown(page).single().codes))
        }
    }

    @Test
    fun `the generated font program is well formed`() {
        val font = GlyphlessFont.trueType()
        assertTrue("${font.size} bytes", font.size < 400)
        // The whole font sums to 0xB1B0AFBA. zh-CN: 整个字体的校验和为 0xB1B0AFBA.
        var sum = 0L
        for (index in font.indices step 4) {
            var word = 0L
            for (offset in 0 until 4) word = word shl 8 or (font.getOrElse(index + offset) { 0 }.toLong() and 0xFF)
            sum = (sum + word) and 0xFFFFFFFFL
        }
        assertEquals(0xB1B0AFBAL, sum)
        // Parsed as an embedded font, which needs no `name` or `OS/2` table. zh-CN: 按嵌入字体解析, 不需要 `name` 与 `OS/2` 表.
        val parsed = TTFParser(true).parse(font.inputStream())
        assertEquals(2, parsed.numberOfGlyphs)
        assertEquals(1000, parsed.unitsPerEm)
        assertEquals(500, GlyphlessFont.width('a'.code))
        assertEquals(1000, GlyphlessFont.width(0x4E2D))
        assertEquals(1000, GlyphlessFont.width(0xAC00))
        assertEquals(1000, GlyphlessFont.width(GlyphlessFont.FIRST_SLOT))
        assertEquals(1000, GlyphlessFont.width(0xFF21))
        assertEquals(500, GlyphlessFont.width(0xFF61))
        assertEquals(500, GlyphlessFont.width(0xFFFD))
    }

}

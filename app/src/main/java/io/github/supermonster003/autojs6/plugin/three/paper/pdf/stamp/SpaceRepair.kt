package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.contentstream.operator.text.BeginText
import com.tom_roush.pdfbox.contentstream.operator.text.EndText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveTextSetLeading
import com.tom_roush.pdfbox.contentstream.operator.text.NextLine
import com.tom_roush.pdfbox.contentstream.operator.text.SetCharSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.SetFontAndSize
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextHorizontalScaling
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextLeading
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRenderingMode
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRise
import com.tom_roush.pdfbox.contentstream.operator.text.SetWordSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.ShowText
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextAdjusted
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLine
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLineAndSpace
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A space of a text stamp as laid out on the canvas of its stamp page (points, origin at the top left corner, y down):
 * the ends of its advance on the baseline ([leftX], [leftY]) and ([rightX], [rightY]) in the reading direction of the
 * line frame, the em of its text and the character the stamp holds there (a space, or a no-break space of its own).
 * zh-CN: 文字印章中的一个空格在其印章页画布上的排版位置 (单位为点, 原点在左上角, y 向下): 其前进宽度在基线上的两端
 * ([leftX], [leftY]) 与 ([rightX], [rightY]) (按行坐标系的正方向), 文字的 em 大小, 以及印章在该处的字符 (空格, 或印章自带的不换行空格).
 */
internal data class LaidSpace(
    val leftX: Double,
    val leftY: Double,
    val rightX: Double,
    val rightY: Double,
    val em: Double,
    val character: Char,
)

/**
 * Restores the spaces the Skia of API 35 drops from the temporary stamp document (roadmap Q192 (c), D60): Skia writes
 * every font of a typeface without font bounds (the variable system fonts of API 35, and a font input) as a Type 3
 * font and drops a font run of blank glyphs as out of bounds, so a space that the font itemization puts into a run of
 * its own (after an emoji, between two Arabic-Indic digits, at the start of a line before CJK text: the cases
 * [StampFonts.noBreakSpaces] cannot cover) leaves no glyph and text extraction loses it. Knowing where each space was
 * laid out ([TextStamp] reports it through [StampFonts.spaceSink]), the glyphs of every stamp page are located; a space
 * with no space glyph near its place gets an invisible one: a text object in rendering mode 3 with a one-glyph Type 3
 * font whose glyph procedure draws nothing (`/ToUnicode` maps it to the space or no-break space), placed by `cm` so
 * that its advance covers the laid out space. The object goes right after the text object (or form XObject) of the
 * glyph that ends where the space starts, else right before the one of the glyph that starts where it ends, else at
 * the end of the page, so extraction in content stream order finds it in place too. Rendering does not change, and a
 * level whose Skia keeps the space glyphs (API 24 to 34) gets nothing.
 * zh-CN: 补回 API 35 的 Skia 从临时印章文档中丢掉的空格 (路线图 Q192 (c), D60): 字体没有字体边界时 (API 35 的可变系统字体, 以及
 * 字体输入), Skia 把它的每个字体都写成 Type 3 字体, 并把只含空白字形的字体段当作越界丢掉, 因此字体分段让其单独成段的空格 (表情之后,
 * 两个阿拉伯-印度数字之间, 行首且后接中日韩文字: [StampFonts.noBreakSpaces] 覆盖不到的情况) 没有字形, 文本提取随之丢失. 既然知道每个
 * 空格的排版位置 ([TextStamp] 经 [StampFonts.spaceSink] 报告), 就定位每张印章页的字形; 附近没有空格字形的空格得到一个不可见的字形:
 * 渲染模式 3 的文本对象, 使用只有一个字形的 Type 3 字体 (字形过程什么也不画, `/ToUnicode` 映射到空格或不换行空格), 由 `cm` 放置,
 * 使其前进宽度覆盖排版时的空格. 该对象紧接在终点位于空格起点的字形所在文本对象 (或表单 XObject) 之后; 没有则紧挨在起点位于空格终点的
 * 字形所在对象之前; 再没有则放在页面末尾, 使按内容流顺序的提取也能在原位找到它. 渲染不变; Skia 保留空格字形的 API 级别 (24 - 34)
 * 不会多出任何东西.
 */
internal object SpaceRepair {

    /** Resource name prefix of the space font. zh-CN: 空格字体资源名的前缀. */
    private const val FONT_PREFIX = "PaperSpace"

    /** Codes of the space font: a space and a no-break space. zh-CN: 空格字体的编码: 空格与不换行空格. */
    private const val CODE_SPACE = 0x20
    private const val CODE_NO_BREAK_SPACE = 0x21

    /** Distances in em: a space glyph matches within half the space plus [MATCH_SLACK]. zh-CN: 以 em 计的距离: 空格字形在半个空格加 [MATCH_SLACK] 内视为匹配. */
    private const val MATCH_SLACK = 0.1
    private const val BASELINE_SLACK = 0.5
    private const val NEIGHBOUR_SLACK = 0.15

    /** A glyph of a stamp page: its text, the ends of its advance (user space) and where an object may go before and after it. */
    private class Glyph(val unicode: String?, val startX: Double, val startY: Double, val endX: Double, val endY: Double, val before: Int, var after: Int)

    /** An invisible space to write: before or after the top-level operator [operator]. zh-CN: 要写入的不可见空格: 在顶层运算符 [operator] 之前或之后. */
    private class Insertion(val operator: Int, val after: Boolean, val matrix: DoubleArray, val code: Int)

    /**
     * Adds the missing spaces of [spaces] (stamp page index to its laid out spaces, canvas coordinates) to [document];
     * answers the spaces added.
     * zh-CN: 把 [spaces] (印章页序号到其排版空格, 画布坐标) 中缺失的空格补进 [document]; 返回补上的空格数.
     */
    fun repair(document: PDDocument, spaces: Map<Int, List<LaidSpace>>): Int {
        var font: PDType3Font? = null
        var added = 0
        for ((index, laid) in spaces) {
            if (laid.isEmpty() || index !in 0 until document.numberOfPages) continue
            val page = document.getPage(index)
            val scan = GlyphScan().apply { processPage(page) }
            val box = page.mediaBox
            val insertions = ArrayList<Insertion>()
            for (space in laid) {
                // Canvas to default user space: Skia flips the page (y up from the bottom of the media box).
                // zh-CN: 画布到默认用户空间: Skia 翻转页面 (y 从媒体框底边向上).
                val lx = box.lowerLeftX + space.leftX
                val ly = box.upperRightY - space.leftY
                val rx = box.lowerLeftX + space.rightX
                val ry = box.upperRightY - space.rightY
                insertion(scan, lx, ly, rx, ry, space)?.let { insertions += it }
            }
            if (insertions.isEmpty()) continue
            val name = page.resourcesOrNew().add(font ?: spaceFont(document).also { font = it })
            write(document, page, insertions, name)
            added += insertions.size
        }
        return added
    }

    private fun PDPage.resourcesOrNew(): PDResources = resources ?: PDResources().also { resources = it }

    /** The insertion for one laid out space, or null when its glyph is there. zh-CN: 一个排版空格的插入项; 其字形存在时为 null. */
    private fun insertion(scan: GlyphScan, lx: Double, ly: Double, rx: Double, ry: Double, space: LaidSpace): Insertion? {
        val width = hypot(rx - lx, ry - ly)
        if (width <= 0.0 || space.em <= 0.0) return null
        val ux = (rx - lx) / width
        val uy = (ry - ly) / width
        val em = space.em
        fun along(x: Double, y: Double, ox: Double, oy: Double) = (x - ox) * ux + (y - oy) * uy
        fun across(x: Double, y: Double, ox: Double, oy: Double) = -(x - ox) * uy + (y - oy) * ux
        val mx = (lx + rx) / 2
        val my = (ly + ry) / 2
        val present = scan.glyphs.any { glyph ->
            isSpace(glyph.unicode) &&
                abs(along((glyph.startX + glyph.endX) / 2, (glyph.startY + glyph.endY) / 2, mx, my)) <= width / 2 + MATCH_SLACK * em &&
                abs(across(glyph.startX, glyph.startY, lx, ly)) <= BASELINE_SLACK * em
        }
        if (present) return null
        val inks = scan.glyphs.filter { !isSpace(it.unicode) && abs(across(it.startX, it.startY, lx, ly)) <= BASELINE_SLACK * em }
        val before = inks.filter { abs(along(it.endX, it.endY, lx, ly)) <= NEIGHBOUR_SLACK * em }.minByOrNull { abs(along(it.endX, it.endY, lx, ly)) }
        val after = inks.filter { abs(along(it.startX, it.startY, rx, ry)) <= NEIGHBOUR_SLACK * em }.minByOrNull { abs(along(it.startX, it.startY, rx, ry)) }
        val (operator, isAfter) = when {
            before != null && before.after >= 0 -> before.after to true
            after != null && after.before >= 0 -> after.before to false
            else -> scan.operators - 1 to true
        }
        // Text space of the space font to user space: x along the space (one unit is its width), y up its line (one em).
        // zh-CN: 空格字体的文字空间到用户空间: x 沿空格方向 (一个单位为其宽度), y 沿行的向上方向 (一个 em).
        val placed = Matrix(
            (ux * width).toFloat(), (uy * width).toFloat(),
            (-uy * em).toFloat(), (ux * em).toFloat(),
            lx.toFloat(), ly.toFloat(),
        )
        val ctm = (if (operator == scan.operators - 1 && isAfter) scan.endCtm else scan.ctm[operator]) ?: Matrix()
        val local = placed.multiply(inverse(ctm) ?: return null)
        val code = if (space.character == StampFonts.NO_BREAK_SPACE) CODE_NO_BREAK_SPACE else CODE_SPACE
        return Insertion(operator.coerceAtLeast(0), isAfter, doubleArrayOf(
            local.scaleX.toDouble(), local.shearY.toDouble(), local.shearX.toDouble(), local.scaleY.toDouble(), local.translateX.toDouble(), local.translateY.toDouble(),
        ), code)
    }

    private fun isSpace(unicode: String?): Boolean = unicode == " " || unicode == StampFonts.NO_BREAK_SPACE.toString()

    private fun inverse(m: Matrix): Matrix? {
        val a = m.scaleX.toDouble()
        val b = m.shearY.toDouble()
        val c = m.shearX.toDouble()
        val d = m.scaleY.toDouble()
        val e = m.translateX.toDouble()
        val f = m.translateY.toDouble()
        val det = a * d - b * c
        if (abs(det) < 1e-12) return null
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Matrix(ia.toFloat(), ib.toFloat(), ic.toFloat(), id.toFloat(), (-(e * ia + f * ic)).toFloat(), (-(e * ib + f * id)).toFloat())
    }

    /** Rewrites the content of [page] with [insertions] placed among its top-level operators. zh-CN: 把 [insertions] 放进 [page] 的顶层运算符之间, 重写其内容. */
    private fun write(document: PDDocument, page: PDPage, insertions: List<Insertion>, font: COSName) {
        val tokens = PDFStreamParser(page).apply { parse() }.tokens
        val beforeOperator = insertions.filter { !it.after }.groupBy { it.operator }
        val afterOperator = insertions.filter { it.after }.groupBy { it.operator }
        val output = ByteArrayOutputStream()
        val writer = ContentStreamWriter(output)
        var operator = 0
        var operands = ArrayList<Any>()
        fun emit(list: List<Insertion>?) = list?.forEach { output.write(snippet(it, font)) }
        for (token in tokens) {
            if (token !is Operator) {
                operands += token
                continue
            }
            emit(beforeOperator[operator])
            writer.writeTokens(operands)
            writer.writeToken(token)
            operands = ArrayList()
            emit(afterOperator[operator])
            operator++
        }
        writer.writeTokens(operands)
        // Insertions past the last operator (an empty page). zh-CN: 位于最后一个运算符之后的插入项 (空页面).
        insertions.filter { it.operator >= operator }.forEach { output.write(snippet(it, font)) }
        page.setContents(PDStream(document, ByteArrayInputStream(output.toByteArray()), COSName.FLATE_DECODE))
    }

    private fun snippet(insertion: Insertion, font: COSName): ByteArray = buildString {
        append("\nq ")
        insertion.matrix.forEach { append(number(it)).append(' ') }
        append("cm BT 3 Tr 100 Tz 0 Tc 0 Tw 0 Ts /").append(font.name).append(" 1 Tf <")
        append(insertion.code.toString(16).uppercase().padStart(2, '0')).append("> Tj ET Q\n")
    }.toByteArray(Charsets.US_ASCII)

    private fun number(value: Double): String =
        BigDecimal(value).setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    /**
     * The invisible space font: two codes (a space and a no-break space) of width 1000 in a 1/1000 font matrix, whose
     * glyph procedures only set the width.
     * zh-CN: 不可见的空格字体: 两个编码 (空格与不换行空格), 在 1/1000 的字体矩阵中宽度为 1000, 字形过程只设置宽度.
     */
    private fun spaceFont(document: PDDocument): PDType3Font {
        fun stream(text: String): COSStream = document.document.createCOSStream().apply {
            createOutputStream().use { it.write(text.toByteArray(Charsets.US_ASCII)) }
        }
        val procedure = stream("1000 0 d0\n")
        val toUnicode = stream(
            "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n" +
                "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n" +
                "/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n" +
                "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n" +
                "2 beginbfchar\n<20> <0020>\n<21> <00A0>\nendbfchar\n" +
                "endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n",
        )
        val space = COSName.getPDFName("space")
        val noBreak = COSName.getPDFName("nbspace")
        val dictionary = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.TYPE3)
            setName(COSName.NAME, FONT_PREFIX)
            setItem(COSName.FONT_BBOX, COSArray().apply { listOf(0, -200, 1000, 800).forEach { add(COSInteger.get(it.toLong())) } })
            setItem(COSName.FONT_MATRIX, COSArray().apply { listOf(0.001f, 0f, 0f, 0.001f, 0f, 0f).forEach { add(COSFloat(it)) } })
            setItem(COSName.CHAR_PROCS, COSDictionary().apply {
                setItem(space, procedure)
                setItem(noBreak, procedure)
            })
            setItem(COSName.ENCODING, COSDictionary().apply {
                setItem(COSName.TYPE, COSName.ENCODING)
                setItem(COSName.DIFFERENCES, COSArray().apply {
                    add(COSInteger.get(CODE_SPACE.toLong()))
                    add(space)
                    add(noBreak)
                })
            })
            setInt(COSName.FIRST_CHAR, CODE_SPACE)
            setInt(COSName.LAST_CHAR, CODE_NO_BREAK_SPACE)
            setItem(COSName.WIDTHS, COSArray().apply { repeat(2) { add(COSInteger.get(1000)) } })
            setItem(COSName.RESOURCES, COSDictionary())
            setItem(COSName.TO_UNICODE, toUnicode)
        }
        return PDType3Font(dictionary)
    }

    /**
     * Locates the glyphs of a stamp page: the unicode, the ends of the advance in default user space, and for each the
     * top-level operator an object may follow (the `ET` of its text object, or the `Do` of the form that holds it) and
     * precede (its `BT`, or that `Do`), with the transformation in force there.
     * zh-CN: 定位印章页的字形: Unicode, 前进宽度在默认用户空间的两端, 以及每个字形之后与之前可放置对象的顶层运算符 (其文本对象的 `ET`
     * 或 `BT`, 或包含它的表单的 `Do`), 连同该处生效的变换.
     */
    private class GlyphScan : PDFStreamEngine() {

        val glyphs = ArrayList<Glyph>()

        /** The CTM before each top-level `BT`, `ET` and `Do`. zh-CN: 每个顶层 `BT`, `ET` 与 `Do` 之前的 CTM. */
        val ctm = HashMap<Int, Matrix>()

        /** The CTM after the last top-level operator. zh-CN: 最后一个顶层运算符之后的 CTM. */
        var endCtm: Matrix? = null
            private set

        /** Top-level operators seen. zh-CN: 已经过的顶层运算符数. */
        var operators = 0
            private set

        private var depth = 0
        private var begin = -1
        private val open = ArrayList<Glyph>()

        init {
            listOf(
                BeginText(), EndText(), SetFontAndSize(), SetCharSpacing(), SetWordSpacing(), SetTextHorizontalScaling(), SetTextLeading(),
                SetTextRenderingMode(), SetTextRise(), MoveText(), MoveTextSetLeading(), NextLine(), SetMatrix(), ShowText(), ShowTextAdjusted(),
                ShowTextLine(), ShowTextLineAndSpace(), Save(), Restore(), Concatenate(), DrawObject(),
            ).forEach(::addOperator)
        }

        override fun processOperator(operator: Operator, operands: List<COSBase>) {
            val top = depth == 0
            val index = operators
            if (top) {
                operators++
                if (operator.name == "BT" || operator.name == "ET" || operator.name == "Do") {
                    ctm[index] = graphicsState.currentTransformationMatrix.clone()
                }
                if (operator.name == "BT") begin = index
            }
            super.processOperator(operator, operands)
            if (top) endCtm = graphicsState.currentTransformationMatrix.clone()
            if (top && operator.name == "ET") {
                open.forEach { it.after = index }
                open.clear()
                begin = -1
            }
        }

        override fun showForm(form: PDFormXObject) {
            depth++
            try {
                super.showForm(form)
            } finally {
                depth--
            }
        }

        override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, displacement: Vector) {
            val m = textRenderingMatrix
            val w = displacement.x.toDouble()
            val glyph = Glyph(
                unicode = runCatching { font.toUnicode(code) }.getOrNull(),
                startX = m.translateX.toDouble(),
                startY = m.translateY.toDouble(),
                endX = m.translateX + m.scaleX * w,
                endY = m.translateY + m.shearY * w,
                // Inside a form the `Do` drawing it (the last top-level operator) stands for the glyph.
                // zh-CN: 在表单内时, 由绘制它的 `Do` (最后一个顶层运算符) 代表该字形.
                before = if (depth == 0) begin else operators - 1,
                after = if (depth == 0) -1 else operators - 1,
            )
            glyphs += glyph
            if (depth == 0) open += glyph
        }

    }

}

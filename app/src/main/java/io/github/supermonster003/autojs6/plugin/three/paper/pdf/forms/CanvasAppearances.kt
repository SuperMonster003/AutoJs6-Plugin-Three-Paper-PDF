package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.FontSubsetting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.GlyphComparer
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.LaidSpace
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.SpaceRepair
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.StampAlign
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.StampFonts
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.TextStamp
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.ToUnicodeRepair
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.Type3Pruning
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max

/**
 * The Android [AppearanceDrawer] (roadmap D62, the Canvas stamp pipeline of D60): every request is one page of a
 * temporary system `PdfDocument`, `ceil(width)` x `ceil(height)` points, drawn with the system text stack (font fallback
 * for CJK and emoji) through [TextStamp]; PDFBox loads that file, the text mapping, Type 3 glyphs, dropped spaces and
 * font subsets are repaired as for stamps, and `LayerUtility.importPageAsForm` turns each page into a form XObject of
 * the target document. A one-line value is centered vertically (automatic size: the content height, shrunk to the
 * content width), a multiline value is broken at the content width from the top (automatic size 12), a list box draws
 * its rows from `/TI` with the selected rows highlighted.
 * zh-CN: Android 端的 [AppearanceDrawer] (路线图 D62, 沿用 D60 的 Canvas 印章管线): 每个请求是临时系统 `PdfDocument` 中的一页,
 * 尺寸 `ceil(width)` x `ceil(height)` 点, 经 [TextStamp] 用系统文字栈绘制 (中日韩文字与表情的字体回退); PDFBox 载入该文件, 像印章那样
 * 修复文本映射, Type 3 字形, 丢失的空格并子集化字体, 再由 `LayerUtility.importPageAsForm` 把每页变成目标文档中的表单 XObject. 单行
 * 值垂直居中 (自动字号: 取内容高度, 放不下时按内容宽度缩小), 多行值从顶部起按内容宽度折行 (自动字号 12), 列表框从 `/TI` 起逐行绘制
 * 并高亮选中行.
 */
internal class CanvasAppearances(private val directory: File, private val fonts: StampFonts = StampFonts()) : AppearanceDrawer {

    override fun draw(document: PDDocument, requests: List<AppearanceRequest>, stop: StopSignal): List<PDFormXObject> {
        if (requests.isEmpty()) return emptyList()
        val file = File.createTempFile("fields-", ".pdf", directory)
        try {
            val texts = ArrayList<List<String>>()
            val spaces = HashMap<Int, List<LaidSpace>>()
            fonts.spacesAsNoBreak = requests.none { request ->
                request.text?.contains(StampFonts.NO_BREAK_SPACE) == true || request.options.any { StampFonts.NO_BREAK_SPACE in it }
            }
            val pdf = PdfDocument()
            try {
                requests.forEachIndexed { index, request ->
                    stop.checkStop()
                    val width = ceil(request.width).toInt().coerceAtLeast(1)
                    val height = ceil(request.height).toInt().coerceAtLeast(1)
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    val laid = ArrayList<LaidSpace>()
                    val drawn = ArrayList<String>()
                    texts += drawn
                    fonts.spaceSink = laid
                    try {
                        paint(page.canvas, request, drawn)
                    } finally {
                        fonts.spaceSink = null
                        pdf.finishPage(page)
                    }
                    if (laid.isNotEmpty()) spaces[index] = laid
                }
                FileOutputStream(file).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
                fonts.releaseBitmaps()
            }
            PDDocument.load(file, MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory)).use { source ->
                if (texts.any { it.isNotEmpty() }) {
                    GlyphComparer(listOf(fonts.typeface(useCustom = false, bold = false, italic = false))).use { ToUnicodeRepair.repair(source, texts, it) }
                }
                Type3Pruning.prune(source)
                SpaceRepair.repair(source, spaces)
                FontSubsetting.subset(source)
                val layers = LayerUtility(document)
                return requests.indices.map { layers.importPageAsForm(source, it) }
            }
        } catch (e: IOException) {
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot draw the form field appearances: ${e.message}")
        } finally {
            file.delete()
        }
    }

    /** Paints the appearance of [request]; [drawn] gets the texts drawn. zh-CN: 绘制 [request] 的外观; [drawn] 收到所画的文字. */
    private fun paint(canvas: Canvas, request: AppearanceRequest, drawn: MutableList<String>) {
        val padding = request.padding
        val contentWidth = max(1.0, request.width - 2 * padding)
        val contentHeight = max(1.0, request.height - 2 * padding)
        val align = when (request.align) {
            1 -> StampAlign.CENTER
            2 -> StampAlign.END
            else -> StampAlign.START
        }
        val anchorX = when (align) {
            StampAlign.START -> 0.0
            StampAlign.CENTER -> 0.5
            StampAlign.END -> 1.0
        }
        val x = padding + anchorX * contentWidth
        val text = request.text
        when {
            text == null -> {
                val size = fontSize(request.fontSize, DEFAULT_SIZE)
                val rowHeight = size * ROW_FACTOR
                val highlight = Paint().apply { color = FieldAppearances.HIGHLIGHT_COLOR }
                var top = padding
                for (row in request.topIndex until request.options.size) {
                    if (top >= request.height) break
                    if (row in request.selected) {
                        canvas.drawRect(padding.toFloat(), top.toFloat(), (request.width - padding).toFloat(), (top + rowHeight).toFloat(), highlight)
                    }
                    val option = request.options[row]
                    if (option.isNotEmpty()) {
                        TextStamp(option, x, top + rowHeight / 2, anchorX, 0.5, size, request.color, align = align).draw(canvas, fonts)
                        drawn += option
                    }
                    top += rowHeight
                }
            }
            text.isEmpty() -> Unit
            request.multiline -> {
                val size = fontSize(request.fontSize, DEFAULT_SIZE)
                val lines = breakLines(text, size, contentWidth)
                TextStamp(lines, x, padding, anchorX, 0.0, size, request.color, align = align).draw(canvas, fonts)
                drawn += lines
            }
            else -> {
                val size = if (request.fontSize > 0) fontSize(request.fontSize, DEFAULT_SIZE) else {
                    val byHeight = fontSize(contentHeight / LINE_FACTOR, DEFAULT_SIZE)
                    val widest = TextStamp(text, 0.0, 0.0, fontSize = byHeight).widestLine(fonts)
                    if (widest > contentWidth) fontSize(byHeight * contentWidth / widest, DEFAULT_SIZE) else byHeight
                }
                TextStamp(text, x, request.height / 2, anchorX, 0.5, size, request.color, align = align).draw(canvas, fonts)
                drawn += text
            }
        }
    }

    /** [text] broken at [width] (breaks kept, trailing spaces of each line dropped). zh-CN: 按 [width] 折行后的 [text] (保留原有换行, 去掉各行末尾空白). */
    private fun breakLines(text: String, size: Double, width: Double): String {
        val paint = TextStamp(text, 0.0, 0.0, fontSize = size).paint(fonts)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        return (0 until layout.lineCount).joinToString("\n") { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd()
        }
    }

    private fun fontSize(size: Double, fallback: Double): Double = when {
        !size.isFinite() || size <= 0 -> fallback
        else -> size.coerceIn(MIN_SIZE, TextStamp.MAX_FONT_SIZE)
    }

    private companion object {

        /** The automatic size of multiline text and list rows, as PDFBox. zh-CN: 多行文本与列表行的自动字号, 与 PDFBox 相同. */
        const val DEFAULT_SIZE = 12.0

        const val MIN_SIZE = 2.0

        /** Line height over font size of an automatic one-line size. zh-CN: 单行自动字号时的行高与字号之比. */
        const val LINE_FACTOR = 1.2

        /** Row height over font size of list rows. zh-CN: 列表行高与字号之比. */
        const val ROW_FACTOR = 1.2

    }

}

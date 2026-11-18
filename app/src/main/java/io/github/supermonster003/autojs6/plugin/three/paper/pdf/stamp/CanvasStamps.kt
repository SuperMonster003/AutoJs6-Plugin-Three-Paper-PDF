package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.pdf.PdfDocument
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.util.Matrix
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The Canvas stamp pipeline (roadmap D60, spike S2): every (page, layer) with stamps gets one page of a temporary system
 * `PdfDocument`, sized to the page as displayed, on which the stamps are drawn with the system text stack (font
 * fallback for CJK and emoji, an optional font input); PDFBox loads that file and `LayerUtility.importPageAsForm`
 * turns each of its pages into a form XObject, drawn on the target page through [StampFrame.matrix] above the content
 * (appended, the content wrapped in `q` / `Q`) or below it (prepended). One `LayerUtility` imports every form, so the
 * font subsets Skia writes once per temporary document are cloned once; equal stamp lists on pages of the same canvas
 * size share a single form. Each stamped page gets its own copy of its resource dictionary (and of its `/XObject`
 * dictionary), so resources shared with other pages or inherited from the page tree stay untouched.
 * zh-CN: Canvas 印章管线 (路线图 D60, spike S2): 每个有印章的 (页, 层) 在临时的系统 `PdfDocument` 里得到一页, 尺寸为该页的显示
 * 尺寸, 印章用系统文字栈画在上面 (中日韩文字与表情的字体回退, 可选的字体输入); PDFBox 载入该文件, 由
 * `LayerUtility.importPageAsForm` 把其中每页变成表单 XObject, 经 [StampFrame.matrix] 画到目标页的内容之上 (追加, 原内容包进
 * `q` / `Q`) 或之下 (前置). 所有表单由同一个 `LayerUtility` 导入, 因此 Skia 在临时文档里各写一次的字体子集只克隆一次; 画布尺寸
 * 相同且印章列表相等的页面共用一个表单. 每个加了印章的页面得到自己的资源字典 (及其 `/XObject` 字典) 副本, 与其他页共用或从页面树
 * 继承的资源保持不动.
 */
internal class CanvasStamps(
    private val directory: File,
    private val fonts: StampFonts = StampFonts(),
    /** Subsets the fonts Skia embeds whole ([FontSubsetting], roadmap Q191 (b)); off only in comparisons. zh-CN: 子集化 Skia 整个嵌入的字体 ([FontSubsetting], 路线图 Q191 (b)); 只在对比测试中关闭. */
    private val subsetFonts: Boolean = true,
) {

    /** The stamps of one page (0-based [pageIndex]). zh-CN: 一页 (从 0 计的 [pageIndex]) 的印章. */
    data class PageStamps(val pageIndex: Int, val stamps: List<Stamp>)

    /**
     * What [apply] did: pages stamped, form XObjects made, bytes of the temporary stamp document, `/ToUnicode` entries
     * repaired ([ToUnicodeRepair]), unused Type 3 glyph procedures dropped ([Type3Pruning]), fonts subset
     * ([FontSubsetting]).
     * zh-CN: [apply] 的结果: 加了印章的页数, 生成的表单 XObject 数, 临时印章文档的字节数, 修复的 `/ToUnicode` 条目数 ([ToUnicodeRepair]),
     * 去掉的未用 Type 3 字形过程数 ([Type3Pruning]), 字体子集化的结果 ([FontSubsetting]).
     */
    data class Summary(
        val pages: Int,
        val forms: Int,
        val stampBytes: Long,
        val repairedMappings: Int,
        val prunedGlyphs: Int,
        val fonts: FontSubsetting.Result = FontSubsetting.Result.NONE,
    )

    /** A distinct stamp page: its canvas size and its stamps. zh-CN: 一张不同的印章页: 画布尺寸与印章. */
    private data class Sheet(val width: Int, val height: Int, val stamps: List<Stamp>)

    private class Placement(val page: PDPage, val frame: StampFrame, val sheet: Int, val layer: StampLayer)

    /**
     * Stamps [document] as [plan] says; [progress] gets (pages placed, pages to place) after drawing and per page.
     * Stamping is cooperative: [stop] is checked per drawn sheet and per placed page.
     * zh-CN: 按 [plan] 给 [document] 加印章; [progress] 在绘制之后与每放置一页时收到 (已放置页数, 待放置页数). 印章操作是协作式的:
     * 每画一张印章页与每放置一页都检查 [stop].
     */
    fun apply(document: PDDocument, plan: List<PageStamps>, stop: StopSignal = StopSignal.NEVER, progress: (Int, Int) -> Unit = { _, _ -> }): Summary {
        val sheets = LinkedHashMap<Sheet, Int>()
        val placements = ArrayList<Placement>()
        for (entry in plan) {
            if (entry.pageIndex !in 0 until document.numberOfPages) {
                throw PdfFailure.pageOutOfRange("page ${entry.pageIndex + 1} is outside 1 - ${document.numberOfPages}")
            }
            val page = document.getPage(entry.pageIndex)
            val frame = StampFrame.of(page)
            for (layer in StampLayer.entries) {
                val stamps = entry.stamps.filter { it.layer == layer }
                if (stamps.isEmpty()) continue
                val sheet = Sheet(frame.canvasWidth, frame.canvasHeight, stamps)
                placements += Placement(page, frame, sheets.getOrPut(sheet) { sheets.size }, layer)
            }
        }
        if (placements.isEmpty()) return Summary(0, 0, 0, 0, 0)
        val file = File.createTempFile("stamps-", ".pdf", directory)
        try {
            draw(sheets.keys, file, stop)
            val stampBytes = file.length()
            progress(0, placements.size)
            PDDocument.load(file, MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory)).use { source ->
                val repaired = repairText(source, sheets.keys)
                val pruned = Type3Pruning.prune(source)
                val subset = if (subsetFonts) FontSubsetting.subset(source) else FontSubsetting.Result.NONE
                val layers = LayerUtility(document)
                val forms = HashMap<Int, PDFormXObject>()
                val owned = Collections.newSetFromMap(IdentityHashMap<PDPage, Boolean>())
                placements.forEachIndexed { index, placement ->
                    stop.checkStop()
                    val form = forms.getOrPut(placement.sheet) { layers.importPageAsForm(source, placement.sheet) }
                    if (owned.add(placement.page)) ownResources(placement.page)
                    place(document, placement, form)
                    progress(index + 1, placements.size)
                }
                return Summary(owned.size, forms.size, stampBytes, repaired, pruned, subset)
            }
        } catch (e: IOException) {
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot stamp the pages: ${e.message}")
        } finally {
            file.delete()
        }
    }

    /** Repairs the text mapping of the drawn text stamps (spike S2). zh-CN: 修复所画文字印章的文本映射 (spike S2). */
    private fun repairText(source: PDDocument, sheets: Collection<Sheet>): Int {
        val texts = sheets.flatMap { it.stamps }.filterIsInstance<TextStamp>()
        if (texts.isEmpty()) return 0
        val drawn = HashSet<Int>()
        texts.forEach { stamp -> stamp.text.codePoints().forEach { drawn += it } }
        val faces = texts.map { fonts.typeface(it.customFont, it.bold, it.italic) }.distinct()
        return GlyphComparer(faces).use { ToUnicodeRepair.repair(source, drawn, it) }
    }

    private fun draw(sheets: Collection<Sheet>, target: File, stop: StopSignal) {
        fonts.spacesAsNoBreak = sheets.none { sheet -> sheet.stamps.any { it is TextStamp && StampFonts.NO_BREAK_SPACE in it.text } }
        val pdf = PdfDocument()
        try {
            sheets.forEachIndexed { index, sheet ->
                stop.checkStop()
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(sheet.width, sheet.height, index + 1).create())
                try {
                    sheet.stamps.forEach { it.draw(page.canvas, fonts) }
                } finally {
                    pdf.finishPage(page)
                }
            }
            FileOutputStream(target).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
            fonts.releaseBitmaps()
        }
    }

    private fun place(document: PDDocument, placement: Placement, form: PDFormXObject) {
        val m = placement.frame.matrix()
        val matrix = Matrix(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(), m[5].toFloat())
        val over = placement.layer == StampLayer.OVER
        val mode = if (over) PDPageContentStream.AppendMode.APPEND else PDPageContentStream.AppendMode.PREPEND
        PDPageContentStream(document, placement.page, mode, true, over).use { stream ->
            stream.saveGraphicsState()
            stream.transform(matrix)
            stream.drawForm(form)
            stream.restoreGraphicsState()
        }
    }

    companion object {

        /**
         * Gives [page] its own resource dictionary and `/XObject` dictionary (shallow copies), so adding a form name
         * changes no other page.
         * zh-CN: 让 [page] 拥有自己的资源字典与 `/XObject` 字典 (浅拷贝), 使添加表单名称不影响其他页面.
         */
        fun ownResources(page: PDPage) {
            val copy = page.resources?.cosObject?.let { COSDictionary(it) } ?: COSDictionary()
            val xObjects = copy.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
            copy.setItem(COSName.XOBJECT, xObjects?.let { COSDictionary(it) } ?: COSDictionary())
            page.resources = PDResources(copy)
        }

    }

}

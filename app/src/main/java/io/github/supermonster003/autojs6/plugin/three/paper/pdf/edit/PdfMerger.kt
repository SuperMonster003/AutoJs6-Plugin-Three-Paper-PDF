package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSourceException
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.absoluteValue

/**
 * Appends documents to [destination] for `merge` (protocol "Run operations", roadmap P2.4) with PDFBox's
 * `PDFMergerUtility`: pages, forms, named destinations and the structure tree are merged by PDFBox, the document
 * information of the first input wins. With `outline` `merge` every input gets one closed outline entry (its display
 * name from the host's `displayNames`, else its document title, else `Document <n>`; appendix D Q31 (b)), leading to its
 * first page and holding its own outline; `none` drops every outline. Sources must stay open until [destination] is
 * written: PDFBox may still read from them.
 * zh-CN: 为 `merge` 把文档追加到 [destination] (协议 "Run operations" 一节, 路线图 P2.4), 使用 PDFBox 的 `PDFMergerUtility`: 页,
 * 表单, 命名目标与结构树由 PDFBox 合并, 文档信息以第一个输入为准. `outline` 为 `merge` 时每个输入得到一个折叠的大纲条目 (标题为
 * 宿主 `displayNames` 中的显示名, 没有时为其文档标题, 再没有时为 `Document <n>`; 附录 D Q31 (b)), 指向其第一页, 其中放该输入自己的
 * 大纲; `none` 丢弃全部大纲. 源文档在 [destination] 写出之前必须保持打开: PDFBox 可能仍会读取它们.
 */
class PdfMerger(private val destination: PDDocument, private val outline: String = PdfContract.OUTLINE_MERGE) {

    private val utility = PDFMergerUtility()

    /** Inputs appended so far. zh-CN: 已追加的输入数. */
    var count = 0
        private set

    init {
        require(outline in PdfContract.OUTLINE_MODES) { "unknown outline mode $outline" }
    }

    /**
     * Appends [source], the next input, titled [displayName] in the outline when it is not blank; an encrypted one must
     * have been opened with its owner password (appendix D Q30).
     * zh-CN: 追加下一个输入 [source], [displayName] 不为空白时作为其大纲标题; 加密的输入必须用所有者密码打开 (附录 D Q30).
     */
    fun append(source: PDDocument, displayName: String? = null) {
        val number = count + 1
        PdfWriting.checkWritable(source, "input $number")
        when (outline) {
            PdfContract.OUTLINE_MERGE -> wrapOutline(source, number, displayName)
            else -> source.documentCatalog.documentOutline = null
        }
        try {
            utility.appendDocument(destination, source)
        } catch (e: IOException) {
            throw failure(e, number)
        }
        count = number
    }

    /** Puts the outline of [source] under one entry for input [number]. zh-CN: 把 [source] 的大纲放到第 [number] 个输入的条目之下. */
    private fun wrapOutline(source: PDDocument, number: Int, displayName: String?) {
        val catalog = source.documentCatalog
        val original = try {
            catalog.documentOutline
        } catch (e: RuntimeException) {
            null
        }
        val entry = PDOutlineItem()
        entry.title = title(source, number, displayName)
        if (source.numberOfPages > 0) entry.destination = PDPageFitDestination().apply { page = source.getPage(0) }
        val first = original?.cosObject?.getDictionaryObject(COSName.FIRST) as? COSDictionary
        if (original != null && first != null) {
            val visited = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
            var child: COSDictionary? = first
            var children = 0
            while (child != null && visited.add(child) && children < MAX_TOP_LEVEL_ENTRIES) {
                child.setItem(COSName.PARENT, entry.cosObject)
                children++
                child = child.getDictionaryObject(COSName.NEXT) as? COSDictionary
            }
            entry.cosObject.setItem(COSName.FIRST, original.cosObject.getItem(COSName.FIRST))
            entry.cosObject.setItem(COSName.LAST, original.cosObject.getItem(COSName.LAST))
            // Closed: Count is minus the entries shown when it opens. zh-CN: 折叠: Count 为展开后显示条目数的相反数.
            entry.cosObject.setInt(COSName.COUNT, -(original.openCount.absoluteValue.takeIf { it > 0 } ?: children))
        }
        catalog.documentOutline = PDDocumentOutline().apply { addLast(entry) }
    }

    private fun title(source: PDDocument, number: Int, displayName: String?): String =
        displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: source.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Document $number"

    private fun failure(error: IOException, number: Int): PdfFailure = when {
        error is PdfSourceException || error.cause is PdfSourceException -> PdfFailure(PdfErrorCodes.IO_ERROR, "input $number: ${error.message}")
        error.message.orEmpty().contains("XFA") -> PdfFailure.unsupported("input $number holds a dynamic XFA form, which cannot be merged")
        else -> PdfFailure(PdfErrorCodes.INVALID_PDF, "input $number cannot be merged: ${error.message}")
    }

    private companion object {
        /** Guards against sibling cycles in damaged outlines. zh-CN: 防止损坏大纲中的兄弟环. */
        const val MAX_TOP_LEVEL_ENTRIES = 100_000
    }

}

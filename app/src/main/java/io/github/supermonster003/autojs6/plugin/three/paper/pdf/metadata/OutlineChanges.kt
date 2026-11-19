package io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadArray
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import org.autojs.plugin.pdf.api.PdfContract
import java.io.File
import java.io.FileOutputStream

/**
 * The `run` operation `setOutline` (roadmap P6.8, D66; protocol "Version 2"): the inputs are the source PDF and the
 * payload `{ "items": [...] }`, a tree of `{ title, page, children }` as `getOutline` reports it (its flat `level` and
 * any other field are ignored). The tree replaces the whole outline; `[]` or null removes it. `title` is required,
 * `page` counts from 1, and an absent, null or 0 `page` makes an entry without a destination (a group); a destination
 * shows the whole page (`/Fit`, like the entries `merge` writes). More than `MAX_OUTLINE_ITEMS` entries over every
 * level or a depth above `MAX_OUTLINE_DEPTH` (top-level entries are depth 1) is `LIMIT_EXCEEDED`, a page outside the
 * document `PAGE_OUT_OF_RANGE`; both are found before anything is written. Entries start closed. The output is written
 * the classic way, unencrypted; an encrypted source needs its owner password (Q30).
 * zh-CN: `run` 操作 `setOutline` (路线图 P6.8, D66; 协议 "Version 2" 一节): 输入为源 PDF 与 payload `{ "items": [...] }`, 即
 * `getOutline` 所报的 `{ title, page, children }` 树 (其扁平的 `level` 与其他字段被忽略). 这棵树整体替换原大纲; `[]` 或 null 删除
 * 大纲. `title` 必需, `page` 从 1 起, `page` 缺失, 为 null 或 0 的条目没有目标 (分组); 目标显示整页 (`/Fit`, 与 `merge` 写的条目
 * 相同). 全部层级合计超过 `MAX_OUTLINE_ITEMS` 个条目或深度超过 `MAX_OUTLINE_DEPTH` (顶层为 1) 为 `LIMIT_EXCEEDED`, 页码不在文档内为
 * `PAGE_OUT_OF_RANGE`; 两者都在写出之前查出. 条目初始为折叠. 输出以经典写法写出, 不加密; 加密的源需要所有者密码 (Q30).
 */
internal object OutlineChanges {

    /** One outline entry; [page] 0 has no destination. zh-CN: 一个大纲条目; [page] 为 0 时没有目标. */
    data class Entry(val title: String, val page: Int, val children: List<Entry>)

    /**
     * The entries of the payload for a document of [pageCount] pages: empty when `items` is `[]` or null.
     * zh-CN: 对 [pageCount] 页的文档读出 payload 中的条目: `items` 为 `[]` 或 null 时为空.
     */
    fun parse(payload: PayloadObject, pageCount: Int): List<Entry> {
        if (!payload.has(PdfContract.FIELD_ITEMS)) throw PdfFailure.invalid("${payload.pathOf(PdfContract.FIELD_ITEMS)} is required (an array, [] or null to remove the outline)")
        val items = payload.optArray(PdfContract.FIELD_ITEMS) ?: return emptyList()
        val counter = intArrayOf(0)
        return entries(items, 1, pageCount, counter)
    }

    private fun entries(items: PayloadArray, depth: Int, pageCount: Int, counter: IntArray): List<Entry> {
        if (items.size == 0) return emptyList()
        if (depth > PdfContract.MAX_OUTLINE_DEPTH) {
            throw PdfFailure.limitExceeded("${items.path} nests the outline ${depth} levels deep, at most ${PdfContract.MAX_OUTLINE_DEPTH}")
        }
        counter[0] += items.size
        if (counter[0] > PdfContract.MAX_OUTLINE_ITEMS) {
            throw PdfFailure.limitExceeded("the outline has more than ${PdfContract.MAX_OUTLINE_ITEMS} entries (counted up to ${items.path})")
        }
        return items.objects().map { item ->
            val page = item.optLong(PdfContract.FIELD_PAGE) ?: 0L
            if (page < 0 || page > pageCount) {
                throw PdfFailure.pageOutOfRange("${item.pathOf(PdfContract.FIELD_PAGE)} $page is outside the document of $pageCount page${if (pageCount == 1) "" else "s"}")
            }
            val children = item.optArray(PdfContract.FIELD_CHILDREN)?.let { entries(it, depth + 1, pageCount, counter) }.orEmpty()
            Entry(item.string(PdfContract.FIELD_TITLE), page.toInt(), children)
        }
    }

    /** Replaces the outline of [document] by [entries] (none removes it). zh-CN: 用 [entries] 替换 [document] 的大纲 (为空时删除大纲). */
    fun apply(document: PDDocument, entries: List<Entry>, stop: StopSignal = StopSignal.NEVER) {
        val catalog = document.documentCatalog
        if (entries.isEmpty()) {
            catalog.cosObject.removeItem(COSName.OUTLINES)
            return
        }
        val root = PDDocumentOutline()
        var added = 0
        fun add(parent: PDOutlineNode, children: List<Entry>) {
            for (entry in children) {
                if (++added and 0xFF == 0) stop.checkStop()
                val item = PDOutlineItem()
                item.title = entry.title
                if (entry.page > 0) item.destination = PDPageFitDestination().apply { page = document.getPage(entry.page - 1) }
                parent.addLast(item)
                add(item, entry.children)
            }
        }
        add(root, entries)
        catalog.documentOutline = root
    }

    fun write(request: InfoChanges.Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val payload = PdfPayload.read(inputs)
        return PdfLoader.load(DescriptorSource.of(inputs[0]), request.password, directory).use { loaded ->
            val document = loaded.document
            PdfWriting.checkWritable(document, "the source")
            val entries = parse(payload, document.numberOfPages)
            operation.progress(PdfContract.STAGE_PROCESSING, 0, 1)
            apply(document, entries, operation.stop)
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
            InfoChanges.result(document.numberOfPages, size, emptyList())
        }
    }

}

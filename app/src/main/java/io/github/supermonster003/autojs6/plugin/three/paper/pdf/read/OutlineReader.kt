package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONObject
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The `getOutline` answer (protocol "Methods", roadmap P2.2, D31): the outline flattened depth first in document order,
 * `level` from 0, `page` from 1 or 0 for an entry without a page destination (an explicit or named destination, or a
 * `GoTo` action). Cycles in damaged outlines are cut and nesting stops at [MAX_DEPTH].
 * zh-CN: `getOutline` 的应答 (协议 "Methods" 一节, 路线图 P2.2, D31): 按文档顺序深度优先展开的大纲, `level` 从 0 起, `page` 从 1
 * 起, 没有页面目标的条目为 0 (页面目标可以是显式或命名目标, 也可以是 `GoTo` 动作). 损坏大纲中的环被截断, 嵌套到 [MAX_DEPTH] 为止.
 */
internal object OutlineReader {

    const val MAX_DEPTH = 64

    data class Entry(val title: String, val page: Int, val level: Int)

    fun read(document: PDDocument, stop: StopSignal, budgetChars: Int = JsonItems.BUDGET_CHARS): Map<String, Any?> {
        val items = JsonItems(budgetChars)
        var hasMore = false
        walk(document, stop) { entry ->
            val item = JSONObject()
                .put(PdfContract.FIELD_TITLE, entry.title)
                .put(PdfContract.FIELD_PAGE, entry.page)
                .put(PdfContract.FIELD_LEVEL, entry.level)
            items.add(item).also { if (!it) hasMore = true }
        }
        return mapOf(PdfContract.KEY_ITEMS to items.toString(), PdfContract.KEY_HAS_MORE to hasMore)
    }

    /** Visits the entries in document order until [visit] answers false. zh-CN: 按文档顺序访问条目, 直到 [visit] 返回 false. */
    fun walk(document: PDDocument, stop: StopSignal, visit: (Entry) -> Boolean) {
        val root = document.documentCatalog.documentOutline ?: return
        val pages by lazy { pageNumbers(document, stop) }
        val visited = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        var steps = 0

        fun pageOf(item: PDOutlineItem): Int {
            val page = runCatching { item.findDestinationPage(document) }.getOrNull() ?: return 0
            return pages[page.cosObject] ?: (runCatching { document.pages.indexOf(page) }.getOrDefault(-1) + 1)
        }

        fun children(node: PDOutlineNode, level: Int): Boolean {
            var item = node.firstChild
            while (item != null && visited.add(item.cosObject)) {
                if (++steps and 0xFF == 0) stop.checkStop()
                if (!visit(Entry(item.title.orEmpty(), pageOf(item), level))) return false
                if (level + 1 < MAX_DEPTH && !children(item, level + 1)) return false
                item = item.nextSibling
            }
            return true
        }

        children(root, 0)
    }

    private fun pageNumbers(document: PDDocument, stop: StopSignal): Map<COSDictionary, Int> {
        val numbers = IdentityHashMap<COSDictionary, Int>()
        for (page in document.pages) {
            if (numbers.size and 0xFF == 0) stop.checkStop()
            numbers[page.cosObject] = numbers.size + 1
        }
        return numbers
    }

}

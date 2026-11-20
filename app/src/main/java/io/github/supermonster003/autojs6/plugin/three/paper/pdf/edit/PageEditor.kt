package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNull
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import org.autojs.plugin.pdf.api.PdfWarningCodes
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Rewrites the pages of a document loaded for writing (roadmap P2.4): the placed pages become the kids of one new page
 * tree root in their new order, with the inheritable attributes (`Resources`, `MediaBox`, `CropBox`, `Rotate`) copied
 * onto each page first, so moving a page between branches keeps its look. When pages go away, whatever would pull them
 * back into the written file is cut: outline entries pointing at them (an entry with kept entries below stays without
 * its target), named destinations, link destinations, the open action, the structure tree, article threads and the page
 * labels (which also go when the order changes). Form fields are left as they are.
 *
 * zh-CN: 改写为写出而加载的文档的页面 (路线图 P2.4): 放置的页按新顺序成为一个新页面树根的子节点, 并先把可继承属性 (`Resources`,
 * `MediaBox`, `CropBox`, `Rotate`) 复制到每页上, 因此页在分支之间移动后外观不变. 有页被移除时, 切断会把它们带回输出文件的引用:
 * 指向它们的大纲条目 (其下仍有保留条目的条目保留, 但去掉目标), 命名目标, 链接目标, 打开动作, 结构树, 文章线程与页标签 (顺序
 * 改变时也删除页标签). 表单域保持原样.
 */
object PageEditor {

    private val INHERITABLE = listOf(COSName.RESOURCES, COSName.MEDIA_BOX, COSName.CROP_BOX, COSName.ROTATE)

    private val GOTO: COSName = COSName.getPDFName("GoTo")

    private val STRUCTURE_ELEMENT: COSName = COSName.getPDFName("SE")

    /** Keys that link an outline item into its tree; a copy gets its own. zh-CN: 把大纲条目连进树的键; 副本使用自己的. */
    private val OUTLINE_LINKS = setOf(COSName.PARENT, COSName.FIRST, COSName.LAST, COSName.NEXT, COSName.PREV, COSName.COUNT, STRUCTURE_ELEMENT)

    /**
     * Applies [placements] (pages of [document] counted from 1, each at most once) and answers warnings about what
     * could not be kept.
     * zh-CN: 应用 [placements] ([document] 的页, 从 1 计数, 每页至多一次), 返回无法保留的内容的警告.
     */
    fun apply(document: PDDocument, placements: List<PagePlacement>, stop: StopSignal): List<PdfWarning> {
        require(placements.isNotEmpty()) { "no page is placed" }
        val catalog = document.documentCatalog.cosObject
        val source = ArrayList<COSDictionary>(document.numberOfPages)
        for (page in document.pages) {
            stop.checkStop()
            source += page.cosObject
        }
        val placed = placements.map { source[it.page - 1] }
        val kept = identitySet<COSDictionary>().apply { addAll(placed) }
        require(kept.size == placed.size) { "a page is placed twice" }
        val removed = identitySet<COSDictionary>().apply { source.filterTo(this) { it !in kept } }
        val warnings = ArrayList<PdfWarning>()

        placed.forEach { inherit(it) }
        if (removed.isNotEmpty()) prune(document, placed, removed, warnings, stop)
        placements.forEachIndexed { index, placement -> if (placement.degrees != 0) rotate(placed[index], placement.degrees) }

        val root = COSDictionary()
        root.setItem(COSName.TYPE, COSName.PAGES)
        val kids = COSArray()
        placed.forEach { page ->
            page.setItem(COSName.PARENT, root)
            kids.add(page)
        }
        root.setItem(COSName.KIDS, kids)
        root.setInt(COSName.COUNT, kids.size())
        catalog.setItem(COSName.PAGES, root)
        if (removed.isNotEmpty() || placements.withIndex().any { (index, placement) -> placement.page != index + 1 }) {
            catalog.removeItem(COSName.PAGE_LABELS)
        }
        return warnings
    }

    /** Copies the inherited attributes onto [page]. zh-CN: 把继承来的属性复制到 [page] 上. */
    private fun inherit(page: COSDictionary) {
        INHERITABLE.forEach { key ->
            if (page.containsKey(key)) return@forEach
            val visited = identitySet<COSDictionary>()
            var node = page.getDictionaryObject(COSName.PARENT) as? COSDictionary
            var depth = 0
            while (node != null && visited.add(node) && depth++ < OutlineReader.MAX_DEPTH) {
                val value = node.getItem(key)
                if (value != null && value.deref() != null) {
                    page.setItem(key, value)
                    return@forEach
                }
                node = node.getDictionaryObject(COSName.PARENT) as? COSDictionary
            }
        }
    }

    private fun rotate(page: COSDictionary, degrees: Int) {
        val current = (page.getDictionaryObject(COSName.ROTATE) as? COSNumber)?.intValue()?.takeIf { it % 90 == 0 } ?: 0
        page.setInt(COSName.ROTATE, Math.floorMod(current + degrees, 360))
    }

    private fun prune(document: PDDocument, placed: List<COSDictionary>, removed: Set<COSDictionary>, warnings: MutableList<PdfWarning>, stop: StopSignal) {
        val catalog = document.documentCatalog.cosObject
        // The outline resolves named destinations, so it goes before them. zh-CN: 大纲要解析命名目标, 因此先于命名目标处理.
        pruneOutline(document, removed, warnings, stop)
        val lostNames = pruneNamedDestinations(catalog, removed)
        val targets = Targets(removed, lostNames)

        placed.forEach { page ->
            stop.checkStop()
            page.removeItem(COSName.B)
            page.removeItem(COSName.STRUCT_PARENTS)
            annotations(page).forEach { annotation ->
                annotation.removeItem(COSName.STRUCT_PARENT)
                if (targets.destination(annotation.getDictionaryObject(COSName.DEST))) annotation.removeItem(COSName.DEST)
                if (targets.action(annotation.getDictionaryObject(COSName.A))) annotation.removeItem(COSName.A)
            }
        }
        removed.forEach { page ->
            page.removeItem(COSName.PARENT)
            annotations(page).forEach { it.removeItem(COSName.P) }
        }
        when (val open = catalog.getDictionaryObject(COSName.OPEN_ACTION)) {
            is COSArray -> if (targets.destination(open)) catalog.removeItem(COSName.OPEN_ACTION)
            is COSDictionary -> if (targets.action(open)) catalog.removeItem(COSName.OPEN_ACTION)
            else -> Unit
        }
        catalog.removeItem(COSName.STRUCT_TREE_ROOT)
        catalog.removeItem(COSName.MARK_INFO)
        catalog.removeItem(COSName.THREADS)
    }

    private fun pruneOutline(document: PDDocument, removed: Set<COSDictionary>, warnings: MutableList<PdfWarning>, stop: StopSignal) {
        val catalog = document.documentCatalog
        val outline = try {
            catalog.documentOutline ?: return
        } catch (e: RuntimeException) {
            catalog.documentOutline = null
            warnings += outlineDamaged()
            return
        }
        try {
            val rebuilt = PDDocumentOutline()
            copyChildren(document, outline, rebuilt, removed, identitySet(), 0, stop)
            catalog.documentOutline = if (rebuilt.hasChildren()) rebuilt else null
        } catch (e: IOException) {
            catalog.documentOutline = null
            warnings += outlineNotAdjusted(e)
        } catch (e: RuntimeException) {
            // Damaged entries (wrong types, broken links); PdfFailure (a stop) is no RuntimeException and passes.
            // zh-CN: 损坏的条目 (类型错误, 链接断开); PdfFailure (停止) 不是 RuntimeException, 照常抛出.
            catalog.documentOutline = null
            warnings += outlineNotAdjusted(e)
        }
    }

    /** The warning of a damaged outline. zh-CN: 大纲损坏时的警告. */
    internal fun outlineDamaged() = PdfWarning("the outline is damaged and was dropped", PdfWarningCodes.OUTLINE_DAMAGED)

    /** The warning of an outline that could not be adjusted because of [e]. zh-CN: 因 [e] 无法调整大纲时的警告. */
    internal fun outlineNotAdjusted(e: Exception) = PdfWarning(
        "the outline could not be adjusted and was dropped: ${e.message}",
        PdfWarningCodes.OUTLINE_NOT_ADJUSTED,
        mapOf(PdfWarningCodes.ARG_REASON to e.message.toString()),
    )

    private fun copyChildren(
        document: PDDocument,
        from: PDOutlineNode,
        to: PDOutlineNode,
        removed: Set<COSDictionary>,
        visited: MutableSet<COSDictionary>,
        depth: Int,
        stop: StopSignal,
    ) {
        var item: PDOutlineItem? = from.firstChild
        while (item != null && visited.add(item.cosObject)) {
            stop.checkStop()
            val target = try {
                item.findDestinationPage(document)
            } catch (e: IOException) {
                null
            }
            val lost = target != null && target.cosObject in removed
            val copy = PDOutlineItem()
            item.cosObject.entrySet().forEach { (key, value) -> if (key !in OUTLINE_LINKS) copy.cosObject.setItem(key, value) }
            if (depth + 1 < OutlineReader.MAX_DEPTH) copyChildren(document, item, copy, removed, visited, depth + 1, stop)
            if (!lost || copy.hasChildren()) {
                if (lost) {
                    copy.cosObject.removeItem(COSName.DEST)
                    copy.cosObject.removeItem(COSName.A)
                }
                if (item.isNodeOpen && copy.hasChildren()) copy.openNode()
                to.addLast(copy)
            }
            item = item.nextSibling
        }
    }

    /**
     * Drops named destinations on removed pages from the `Dests` dictionary and the `Names` > `Dests` tree (rebuilt as
     * one sorted leaf when something goes); answers the dropped names.
     * zh-CN: 从 `Dests` 字典与 `Names` > `Dests` 名称树中删除指向被移除页的命名目标 (有删除时把名称树重建为一个有序叶节点); 返回
     * 被删除的名称.
     */
    private fun pruneNamedDestinations(catalog: COSDictionary, removed: Set<COSDictionary>): Set<String> {
        val lost = HashSet<String>()
        val pages = Targets(removed, emptySet())
        (catalog.getDictionaryObject(COSName.DESTS) as? COSDictionary)?.let { dests ->
            dests.keySet().toList().forEach { key ->
                if (pages.destination(dests.getDictionaryObject(key))) {
                    dests.removeItem(key)
                    lost += key.name
                }
            }
        }
        val names = catalog.getDictionaryObject(COSName.NAMES) as? COSDictionary ?: return lost
        val tree = names.getDictionaryObject(COSName.DESTS) as? COSDictionary ?: return lost
        val entries = ArrayList<Pair<COSString, COSBase>>()
        collectNames(tree, entries, identitySet(), 0)
        val kept = entries.filter { (key, value) ->
            val gone = pages.destination(value.deref())
            if (gone) lost += key.string
            !gone
        }
        if (kept.size == entries.size) return lost
        val array = COSArray()
        kept.sortedWith { a, b -> compareBytes(a.first.bytes, b.first.bytes) }.forEach { (key, value) ->
            array.add(key)
            array.add(value)
        }
        names.setItem(COSName.DESTS, COSDictionary().apply { setItem(COSName.NAMES, array) })
        return lost
    }

    private fun collectNames(node: COSDictionary, out: MutableList<Pair<COSString, COSBase>>, visited: MutableSet<COSDictionary>, depth: Int) {
        if (!visited.add(node) || depth > OutlineReader.MAX_DEPTH) return
        (node.getDictionaryObject(COSName.NAMES) as? COSArray)?.let { array ->
            var index = 0
            while (index + 1 < array.size()) {
                val key = array.getObject(index) as? COSString
                val value = array.get(index + 1)
                if (key != null && value != null) out += key to value
                index += 2
            }
        }
        (node.getDictionaryObject(COSName.KIDS) as? COSArray)?.forEach { kid ->
            (kid.deref() as? COSDictionary)?.let { collectNames(it, out, visited, depth + 1) }
        }
    }

    /** Whether destinations and actions lead to removed pages. zh-CN: 目标与动作是否指向被移除的页. */
    private class Targets(private val removed: Set<COSDictionary>, private val lostNames: Set<String>) {

        fun destination(value: COSBase?): Boolean = when (value) {
            is COSArray -> (value.getObject(0) as? COSDictionary)?.let { it in removed } ?: false
            is COSDictionary -> destination(value.getDictionaryObject(COSName.D))
            is COSString -> value.string in lostNames
            is COSName -> value.name in lostNames
            else -> false
        }

        fun action(value: COSBase?): Boolean {
            val action = value as? COSDictionary ?: return false
            return action.getCOSName(COSName.S) == GOTO && destination(action.getDictionaryObject(COSName.D))
        }

    }

    private fun annotations(page: COSDictionary): List<COSDictionary> =
        (page.getDictionaryObject(COSName.ANNOTS) as? COSArray)?.mapNotNull { it.deref() as? COSDictionary }.orEmpty()

    private fun COSBase?.deref(): COSBase? = when (this) {
        is COSObject -> `object`?.takeUnless { it is COSNull }
        is COSNull -> null
        else -> this
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (index in 0 until minOf(a.size, b.size)) {
            val difference = (a[index].toInt() and 0xFF) - (b[index].toInt() and 0xFF)
            if (difference != 0) return difference
        }
        return a.size - b.size
    }

    private fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())

}

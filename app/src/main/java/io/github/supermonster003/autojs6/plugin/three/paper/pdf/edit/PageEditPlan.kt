package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * One page of an edited document: [page] of the opened document (counted from 1), turned clockwise by [degrees] more
 * (0, 90, 180 or 270).
 * zh-CN: 编辑后文档的一页: 已打开文档的第 [page] 页 (从 1 计数), 再顺时针旋转 [degrees] 度 (0, 90, 180 或 270).
 */
data class PagePlacement(val page: Int, val degrees: Int = 0)

/**
 * Turns `save`, `planSplit` and `split` requests into page plans (protocol "Methods" and "Saving", roadmap P2.4, D32).
 * Every page number counts pages of the opened document; nothing is read from the document except its page count, so
 * the plan is checked before an operation id exists (a request refused up front throws).
 * zh-CN: 把 `save`, `planSplit` 与 `split` 请求转成页面计划 (协议 "Methods" 与 "Saving" 两节, 路线图 P2.4, D32). 所有页码都指
 * 已打开文档的页; 除页数外不读取文档, 因此在产生操作 id 之前就能检查计划 (预先拒绝的请求直接抛出).
 */
object PageEditPlan {

    /**
     * The pages a `save` writes, in order: `deletePages` removed, only `pages` of the rest kept, the kept pages put in
     * the order of `order` (a permutation of the kept page numbers) and `rotatePages` (default every kept page) turned
     * by `degrees`, in that order. Removing every page fails with `INVALID_ARGUMENT`.
     * zh-CN: `save` 写出的页及其顺序: 移除 `deletePages`, 其余页只保留 `pages`, 按 `order` (保留页页码的一个排列) 排列保留页, 再把
     * `rotatePages` (缺省为全部保留页) 旋转 `degrees`, 依次进行. 移除全部页报 `INVALID_ARGUMENT`.
     */
    fun save(options: PdfOptions, pageCount: Int): List<PagePlacement> {
        val deleted = if (options.has(PdfContract.KEY_DELETE_PAGES)) {
            options.pages(pageCount, PdfContract.KEY_DELETE_PAGES).toHashSet()
        } else {
            emptySet()
        }
        val kept = options.pages(pageCount).filterNot { it in deleted }
        if (kept.isEmpty()) throw PdfFailure.invalid("the request removes every page of the document")
        val ordered = options.intArray(PdfContract.KEY_ORDER)?.let { order(it, kept) } ?: kept
        val degrees = options.degrees()
        val rotated = when {
            degrees == 0 -> emptySet()
            options.has(PdfContract.KEY_ROTATE_PAGES) -> options.pages(pageCount, PdfContract.KEY_ROTATE_PAGES).toHashSet()
            else -> kept.toHashSet()
        }
        return ordered.map { PagePlacement(it, if (it in rotated) degrees else 0) }
    }

    /**
     * The parts of a `planSplit` / `split`: exactly one of `ranges` (one page range per part, each used ascending) and
     * `every` (consecutive parts of that many pages, the last one shorter); more than `MAX_SPLIT_OUTPUTS` parts fail
     * with `LIMIT_EXCEEDED`.
     * zh-CN: `planSplit` / `split` 的各部分: `ranges` (每部分一个页码范围, 各自按升序使用) 与 `every` (每部分连续若干页, 最后一部分
     * 可以较短) 恰好给出一个; 超过 `MAX_SPLIT_OUTPUTS` 部分报 `LIMIT_EXCEEDED`.
     */
    fun split(options: PdfOptions, pageCount: Int): List<IntArray> {
        val ranges = options.stringArray(PdfContract.KEY_RANGES)
        val every = options.int(PdfContract.KEY_EVERY)
        if ((ranges == null) == (every == null)) {
            throw PdfFailure.invalid("give exactly one of ${PdfContract.KEY_RANGES} and ${PdfContract.KEY_EVERY}")
        }
        if (pageCount == 0) throw PdfFailure.invalid("the document has no page to split")
        if (ranges != null) {
            if (ranges.isEmpty()) throw PdfFailure.invalid("${PdfContract.KEY_RANGES} is empty")
            checkPartCount(ranges.size.toLong())
            return ranges.mapIndexed { index, range ->
                PageRanges.parse(range ?: throw PdfFailure.invalid("${PdfContract.KEY_RANGES}[$index] is null"), pageCount)
            }
        }
        val size = checkNotNull(every)
        if (size < 1) throw PdfFailure.invalid("${PdfContract.KEY_EVERY} $size is below 1")
        checkPartCount((pageCount.toLong() + size - 1) / size)
        return (0 until pageCount step size).map { start -> IntArray(minOf(size, pageCount - start)) { start + it + 1 } }
    }

    /**
     * The `planSplit` answer: `items` (`index` from 0, `pages` as a range, `pageCount`) as a JSON array.
     * zh-CN: `planSplit` 的应答: `items` (`index` 从 0 起, `pages` 为范围文本, `pageCount`), 为 JSON 数组.
     */
    fun manifest(parts: List<IntArray>): Map<String, Any?> {
        val items = JSONArray()
        parts.forEachIndexed { index, pages ->
            items.put(
                JSONObject()
                    .put(PdfContract.FIELD_INDEX, index)
                    .put(PdfContract.FIELD_PAGES, PageRanges.format(pages))
                    .put(PdfContract.FIELD_PAGE_COUNT, pages.size),
            )
        }
        return mapOf(PdfContract.KEY_ITEMS to items.toString())
    }

    /** Whether [placements] leave every page of a [pageCount]-page document as it is. zh-CN: [placements] 是否让 [pageCount] 页文档的每页保持原样. */
    fun isIdentity(placements: List<PagePlacement>, pageCount: Int): Boolean =
        placements.size == pageCount && placements.withIndex().all { (index, placement) -> placement.page == index + 1 && placement.degrees == 0 }

    private fun order(order: IntArray, kept: List<Int>): List<Int> {
        val expected = kept.toHashSet()
        val seen = HashSet<Int>(order.size)
        order.forEach { page ->
            if (page !in expected) throw PdfFailure.invalid("${PdfContract.KEY_ORDER} names page $page, which is not kept")
            if (!seen.add(page)) throw PdfFailure.invalid("${PdfContract.KEY_ORDER} names page $page twice")
        }
        if (seen.size != expected.size) {
            val missing = PageRanges.format(kept.filterNot { it in seen }.toIntArray())
            throw PdfFailure.invalid("${PdfContract.KEY_ORDER} leaves out the kept pages $missing")
        }
        return order.toList()
    }

    private fun checkPartCount(count: Long) {
        if (count > PdfContract.MAX_SPLIT_OUTPUTS) {
            throw PdfFailure.limitExceeded("the split gives $count parts, more than ${PdfContract.MAX_SPLIT_OUTPUTS}")
        }
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

/**
 * Page ranges of the PDF contract (protocol "Conventions", roadmap D32): comma-separated items `N`, `N-M` (N <= M) or
 * `N-` (to the last page), counted from 1, for example `1-3,5,8-`. Blanks around items and numbers are ignored, the
 * selected pages are used in ascending order without duplicates, and an absent range means every page.
 * zh-CN: PDF 契约的页码范围 (协议 "Conventions" 一节, 路线图 D32): 逗号分隔的 `N`, `N-M` (N <= M) 或 `N-` (到末页), 从 1 计数, 例如
 * `1-3,5,8-`. 忽略项与数字两侧的空白, 选中的页按升序去重使用, 缺省表示全部页.
 */
object PageRanges {

    /**
     * Pages selected by [spec] in a document of [pageCount] pages, ascending and distinct. Malformed text (an empty
     * range, a sign, `-M`, `N-M` with N > M, a number beyond Int) fails with `INVALID_ARGUMENT`; a page outside
     * `1..pageCount` fails with `PAGE_OUT_OF_RANGE`. Empty items between commas are blanks and are skipped.
     * zh-CN: 在 [pageCount] 页的文档中由 [spec] 选中的页, 升序且不重复. 格式错误 (空范围, 符号, `-M`, N > M 的 `N-M`, 超出 Int 的数字)
     * 报 `INVALID_ARGUMENT`; 超出 `1..pageCount` 的页报 `PAGE_OUT_OF_RANGE`. 逗号之间的空项视为空白, 直接跳过.
     */
    fun parse(spec: String?, pageCount: Int): IntArray {
        require(pageCount >= 0) { "pageCount must not be negative: $pageCount" }
        if (spec == null) return IntArray(pageCount) { it + 1 }
        val selected = BooleanArray(pageCount + 1)
        var items = 0
        for (rawItem in spec.split(',')) {
            val item = rawItem.trim()
            if (item.isEmpty()) continue
            items += 1
            val dash = item.indexOf('-')
            val first: Int
            val last: Int
            if (dash < 0) {
                first = number(item, spec)
                last = first
            } else {
                first = number(item.substring(0, dash), spec)
                val tail = item.substring(dash + 1).trim()
                last = if (tail.isEmpty()) maxOf(pageCount, first) else number(tail, spec)
                if (first > last) throw PdfFailure.invalid("page range item $item runs backwards in \"$spec\"")
            }
            if (first < 1 || last > pageCount) {
                val page = if (first < 1) first else last
                throw PdfFailure.pageOutOfRange("page $page of \"$spec\" is outside 1..$pageCount")
            }
            for (page in first..last) selected[page] = true
        }
        if (items == 0) throw PdfFailure.invalid("page range \"$spec\" selects no page")
        return (1..pageCount).filter { selected[it] }.toIntArray()
    }

    /**
     * The shortest range text of [pages] (ascending or not, duplicates allowed), for example `[1, 2, 3, 5]` -> `1-3,5`;
     * the inverse of [parse] for closed ranges. Pages below 1 are refused.
     * zh-CN: [pages] (可无序, 可重复) 的最短范围文本, 例如 `[1, 2, 3, 5]` -> `1-3,5`; 是 [parse] 对闭区间的逆运算. 拒绝小于 1 的页.
     */
    fun format(pages: IntArray): String {
        val sorted = pages.distinct().sorted()
        require(sorted.all { it >= 1 }) { "pages count from 1: ${pages.contentToString()}" }
        val parts = ArrayList<String>()
        var index = 0
        while (index < sorted.size) {
            val start = sorted[index]
            var end = start
            while (index + 1 < sorted.size && sorted[index + 1] == end + 1) {
                index += 1
                end = sorted[index]
            }
            parts += if (start == end) "$start" else "$start-$end"
            index += 1
        }
        return parts.joinToString(",")
    }

    private fun number(text: String, spec: String): Int {
        val digits = text.trim()
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) {
            throw PdfFailure.invalid("page range item \"$text\" is not a page number in \"$spec\"")
        }
        return digits.toIntOrNull() ?: throw PdfFailure.invalid("page number $digits is too large in \"$spec\"")
    }

}

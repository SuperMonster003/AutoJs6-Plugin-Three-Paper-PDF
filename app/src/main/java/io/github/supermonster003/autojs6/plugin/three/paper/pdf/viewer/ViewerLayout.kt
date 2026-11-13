package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import kotlin.math.max
import kotlin.math.min

/**
 * Where the viewer stands: [zoom] relative to the page column fitting the viewport width, and the scroll offsets of the
 * viewport in document pixels at that zoom.
 * zh-CN: 查看器的当前位置: [zoom] 以页面列适配视口宽度时为 1, 滚动偏移为该缩放下视口在文档中的像素位置.
 */
internal data class ViewerPosition(val zoom: Float, val scrollX: Float, val scrollY: Float)

/**
 * A reading position that survives a new viewport (rotation, window resize): the page at the top of the viewport and
 * how far into it the viewport starts, as a fraction of the page height.
 * zh-CN: 视口变化 (旋转, 窗口尺寸) 后仍能恢复的阅读位置: 视口顶部所在的页, 以及视口顶部在该页高度中的比例.
 */
internal data class ViewerAnchor(val page: Int, val fraction: Float, val zoom: Float)

/**
 * Geometry of the continuous vertical page column (roadmap D35): every page is fitted to the viewport width minus
 * [padding] on each side at zoom 1, pages are separated by [gap], and the whole column scales with the zoom. A column
 * narrower or shorter than the viewport is centered. Pages are given as displayed sizes in points (rotation applied,
 * read/PageFrame), so search rectangles map without further turns. Pure arithmetic, tested on the JVM.
 * zh-CN: 连续纵向页面列的几何 (路线图 D35): 缩放为 1 时每页适配视口宽度 (两侧各留 [padding]), 页间隔 [gap], 整列随缩放等比
 * 变化. 比视口窄或矮的列居中. 页面以显示尺寸 (已应用旋转, 见 read/PageFrame) 给出, 单位为点, 因此搜索矩形无需再旋转即可映射.
 * 纯算术, 在 JVM 上测试.
 */
internal class ViewerLayout(
    private val pageWidths: FloatArray,
    private val pageHeights: FloatArray,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val gap: Float,
    val padding: Float,
) {

    init {
        require(pageWidths.size == pageHeights.size) { "page widths and heights differ in count" }
    }

    val pageCount: Int get() = pageWidths.size

    /** Width of every page at zoom 1, in pixels. zh-CN: 缩放为 1 时每页的宽度 (像素). */
    val contentWidth: Float = max(1f, viewportWidth - 2 * padding)

    private val heights = FloatArray(pageCount) { index ->
        val width = pageWidths[index].takeIf { it > 0f } ?: 1f
        max(1f, contentWidth * pageHeights[index] / width)
    }

    private val tops = FloatArray(pageCount)

    /** Height of the column at zoom 1, paddings included. zh-CN: 缩放为 1 时整列的高度, 含上下留白. */
    val columnHeight: Float

    init {
        var y = padding
        for (index in 0 until pageCount) {
            tops[index] = y
            y += heights[index] + if (index == pageCount - 1) 0f else gap
        }
        columnHeight = y + padding
    }

    /** Pixels per point of page [page] at [zoom]. zh-CN: 第 [page] 页在 [zoom] 下每点对应的像素数. */
    fun pageScale(page: Int, zoom: Float): Float = contentWidth * zoom / (pageWidths[page].takeIf { it > 0f } ?: 1f)

    fun pageWidth(zoom: Float): Float = contentWidth * zoom

    fun pageHeight(page: Int, zoom: Float): Float = heights[page] * zoom

    /** Top of page [page] in document pixels at [zoom]. zh-CN: 第 [page] 页在 [zoom] 下的文档像素顶边. */
    fun pageTop(page: Int, zoom: Float): Float = tops[page] * zoom

    /** Left of every page in document pixels at [zoom]. zh-CN: 各页在 [zoom] 下的文档像素左边. */
    fun pageLeft(zoom: Float): Float = padding * zoom

    fun documentWidth(zoom: Float): Float = viewportWidth * zoom

    fun documentHeight(zoom: Float): Float = columnHeight * zoom

    /** Viewport x of the document origin when the column is narrower than the viewport. zh-CN: 列比视口窄时文档原点的视口 x. */
    fun originX(zoom: Float): Float = max(0f, (viewportWidth - documentWidth(zoom)) / 2)

    /** Viewport y of the document origin when the column is shorter than the viewport. zh-CN: 列比视口矮时文档原点的视口 y. */
    fun originY(zoom: Float): Float = max(0f, (viewportHeight - documentHeight(zoom)) / 2)

    fun maxScrollX(zoom: Float): Float = max(0f, documentWidth(zoom) - viewportWidth)

    fun maxScrollY(zoom: Float): Float = max(0f, documentHeight(zoom) - viewportHeight)

    fun clamp(position: ViewerPosition): ViewerPosition {
        val zoom = position.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return ViewerPosition(
            zoom,
            position.scrollX.coerceIn(0f, maxScrollX(zoom)),
            position.scrollY.coerceIn(0f, maxScrollY(zoom)),
        )
    }

    /** Viewport x of document x [x]. zh-CN: 文档 x [x] 对应的视口 x. */
    fun viewportX(x: Float, position: ViewerPosition): Float = originX(position.zoom) + x - position.scrollX

    /** Viewport y of document y [y]. zh-CN: 文档 y [y] 对应的视口 y. */
    fun viewportY(y: Float, position: ViewerPosition): Float = originY(position.zoom) + y - position.scrollY

    /**
     * Pages that intersect the document rows [top] until [bottom] at [zoom]; empty when none does.
     * zh-CN: 在 [zoom] 下与文档行 [top] 到 [bottom] 相交的页; 没有时为空.
     */
    fun pagesIn(top: Float, bottom: Float, zoom: Float): IntRange {
        if (pageCount == 0 || bottom <= top) return IntRange.EMPTY
        val first = lastPageStartingAtOrAbove(top / zoom).let { if (it >= 0 && tops[it] + heights[it] <= top / zoom) it + 1 else max(it, 0) }
        val last = lastPageStartingAtOrAbove(bottom / zoom)
        if (last < 0 || first >= pageCount || first > last) return IntRange.EMPTY
        return first..last
    }

    /** Pages visible at [position]. zh-CN: [position] 下可见的页. */
    fun visiblePages(position: ViewerPosition): IntRange {
        val top = position.scrollY - originY(position.zoom)
        return pagesIn(top, top + viewportHeight, position.zoom)
    }

    /**
     * The page shown as current (index from 0): the page under the middle of the viewport, or the nearest page above
     * it when the middle falls into a gap. The first and last pages win at the ends of the column, so a short last page
     * can become current.
     * zh-CN: 作为当前页显示的页 (从 0 起): 视口中线所在的页; 中线落在页间隙时取其上方最近的页. 列的两端分别取首页与末页, 因此较短的
     * 末页也能成为当前页.
     */
    fun currentPage(position: ViewerPosition): Int {
        if (pageCount == 0) return 0
        val zoom = position.zoom
        if (position.scrollY <= 0f) return 0
        if (position.scrollY >= maxScrollY(zoom) && maxScrollY(zoom) > 0f) return pageCount - 1
        val middle = (position.scrollY - originY(zoom) + viewportHeight / 2) / zoom
        return lastPageStartingAtOrAbove(middle).coerceIn(0, pageCount - 1)
    }

    /**
     * Scroll position that puts the top of page [page] (index from 0) at the top of the viewport, keeping zoom and
     * horizontal offset.
     * zh-CN: 把第 [page] 页 (从 0 起) 的顶边放到视口顶部的滚动位置, 缩放与水平偏移不变.
     */
    fun positionForPage(page: Int, from: ViewerPosition): ViewerPosition {
        val index = page.coerceIn(0, max(0, pageCount - 1))
        val top = if (pageCount == 0) 0f else (tops[index] - padding).coerceAtLeast(0f) * from.zoom
        return clamp(from.copy(scrollY = top))
    }

    /**
     * Scroll position that centers the rectangle [left], [top], [right], [bottom] (points of the displayed page, from its
     * top left corner) of page [page] in the viewport, keeping the zoom.
     * zh-CN: 让第 [page] 页的矩形 [left], [top], [right], [bottom] (显示页面上的点, 从其左上角起) 位于视口中央的滚动位置, 缩放不变.
     */
    fun positionForRect(page: Int, left: Float, top: Float, right: Float, bottom: Float, from: ViewerPosition): ViewerPosition {
        if (pageCount == 0) return from
        val zoom = from.zoom
        val scale = pageScale(page, zoom)
        val centerX = pageLeft(zoom) + (left + right) / 2 * scale
        val centerY = pageTop(page, zoom) + (top + bottom) / 2 * scale
        return clamp(ViewerPosition(zoom, centerX - viewportWidth / 2, centerY - viewportHeight / 2))
    }

    /**
     * The position after zooming from [from] to [zoom] (clamped to [MIN_ZOOM]..[MAX_ZOOM]) around the viewport point
     * [focusX], [focusY], which keeps the document point under it.
     * zh-CN: 以视口点 [focusX], [focusY] 为中心从 [from] 缩放到 [zoom] (限制在 [MIN_ZOOM]..[MAX_ZOOM]) 后的位置, 焦点下的文档点保持不动.
     */
    fun zoomAround(from: ViewerPosition, zoom: Float, focusX: Float, focusY: Float): ViewerPosition {
        val target = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val documentX = (focusX - originX(from.zoom) + from.scrollX) / from.zoom
        val documentY = (focusY - originY(from.zoom) + from.scrollY) / from.zoom
        val scrollX = documentX * target - focusX + originX(target)
        val scrollY = documentY * target - focusY + originY(target)
        return clamp(ViewerPosition(target, scrollX, scrollY))
    }

    /** The anchor of [position], for a later [positionFor] on another layout. zh-CN: [position] 的锚点, 供之后在另一布局上调用 [positionFor]. */
    fun anchorOf(position: ViewerPosition): ViewerAnchor {
        if (pageCount == 0) return ViewerAnchor(0, 0f, position.zoom)
        val zoom = position.zoom
        val top = (position.scrollY - originY(zoom)) / zoom
        val page = lastPageStartingAtOrAbove(top).coerceIn(0, pageCount - 1)
        val fraction = ((top - tops[page]) / heights[page]).coerceIn(-1f, 1f)
        return ViewerAnchor(page, fraction, zoom)
    }

    /** The position of [anchor] on this layout, horizontally centered. zh-CN: [anchor] 在本布局中的位置, 水平居中. */
    fun positionFor(anchor: ViewerAnchor): ViewerPosition {
        if (pageCount == 0) return ViewerPosition(1f, 0f, 0f)
        val zoom = anchor.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val page = anchor.page.coerceIn(0, pageCount - 1)
        val top = (tops[page] + anchor.fraction * heights[page]) * zoom
        return clamp(ViewerPosition(zoom, maxScrollX(zoom) / 2, top))
    }

    /** Index of the last page whose top is at or above [y] (zoom 1), or -1. zh-CN: 顶边不低于 [y] (缩放为 1) 的最后一页的序号, 没有时为 -1. */
    private fun lastPageStartingAtOrAbove(y: Float): Int {
        var low = 0
        var high = pageCount - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (tops[mid] <= y) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {

        const val MIN_ZOOM = 0.5f

        const val MAX_ZOOM = 8f

        /** The zoom a double tap toggles to from zoom 1. zh-CN: 从缩放 1 双击时切换到的缩放. */
        const val DOUBLE_TAP_ZOOM = 2.5f

    }

}

/**
 * The tiles of a zoomed page (roadmap D35): squares of [TILE_SIZE] pixels from the top left corner of the page at the
 * current zoom, clipped to the page; [visible] answers those that touch a viewport rectangle in page pixels.
 * zh-CN: 缩放后页面的分块 (路线图 D35): 从当前缩放下页面左上角起的 [TILE_SIZE] 像素方块, 在页边裁切; [visible] 返回与页面像素坐标下
 * 视口矩形相交的分块.
 */
internal object TileGrid {

    const val TILE_SIZE = 512

    data class Tile(val column: Int, val row: Int, val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Tiles of a page of [pageWidth] x [pageHeight] pixels touching the rectangle [left], [top], [right], [bottom] (page
     * pixels), grown by [margin] tiles on each side.
     * zh-CN: 尺寸为 [pageWidth] x [pageHeight] 像素的页面中, 与矩形 [left], [top], [right], [bottom] (页面像素) 相交的分块, 四周各外扩
     * [margin] 块.
     */
    fun visible(pageWidth: Int, pageHeight: Int, left: Float, top: Float, right: Float, bottom: Float, margin: Int = 0): List<Tile> {
        if (pageWidth <= 0 || pageHeight <= 0) return emptyList()
        val columns = (pageWidth + TILE_SIZE - 1) / TILE_SIZE
        val rows = (pageHeight + TILE_SIZE - 1) / TILE_SIZE
        val firstColumn = (floorDiv(left) - margin).coerceIn(0, columns - 1)
        val lastColumn = (floorDiv(right - 1) + margin).coerceIn(0, columns - 1)
        val firstRow = (floorDiv(top) - margin).coerceIn(0, rows - 1)
        val lastRow = (floorDiv(bottom - 1) + margin).coerceIn(0, rows - 1)
        if (right <= 0f || bottom <= 0f || left >= pageWidth || top >= pageHeight) return emptyList()
        val tiles = ArrayList<Tile>((lastColumn - firstColumn + 1) * (lastRow - firstRow + 1))
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val x = column * TILE_SIZE
                val y = row * TILE_SIZE
                tiles += Tile(column, row, x, y, min(TILE_SIZE, pageWidth - x), min(TILE_SIZE, pageHeight - y))
            }
        }
        return tiles
    }

    private fun floorDiv(value: Float): Int = kotlin.math.floor(value / TILE_SIZE).toInt()

}

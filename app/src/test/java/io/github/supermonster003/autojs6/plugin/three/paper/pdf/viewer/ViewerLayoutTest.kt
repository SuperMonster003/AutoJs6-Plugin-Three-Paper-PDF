package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The viewer's column arithmetic (roadmap P3.1, D35): page placement, clamping, visible and current pages, jumps,
 * zooming around a focus point and anchors that survive a viewport change.
 * zh-CN: 查看器的列布局计算 (路线图 P3.1, D35): 页面位置, 限位, 可见页与当前页, 跳页, 围绕焦点缩放, 以及在视口变化后保持的锚点.
 */
class ViewerLayoutTest {

    // Content width 960: page heights 1280, 640 and 1920, tops 20, 1310 and 1960, column 3900.
    // zh-CN: 内容宽度 960: 页高 1280, 640 与 1920, 顶边 20, 1310 与 1960, 整列 3900.
    private val layout = ViewerLayout(
        pageWidths = floatArrayOf(600f, 600f, 300f),
        pageHeights = floatArrayOf(800f, 400f, 600f),
        viewportWidth = 1000f,
        viewportHeight = 2000f,
        gap = 10f,
        padding = 20f,
    )

    @Test
    fun `pages fill the content width and stack with gaps and paddings`() {
        assertEquals(960f, layout.contentWidth, 0f)
        assertEquals(3900f, layout.columnHeight, 0.01f)
        assertEquals(1310f, layout.pageTop(1, 1f), 0.01f)
        assertEquals(3920f, layout.pageTop(2, 2f), 0.01f)
        assertEquals(1920f, layout.pageHeight(2, 1f), 0.01f)
        assertEquals(1.6f, layout.pageScale(0, 1f), 0.0001f)
        assertEquals(6.4f, layout.pageScale(2, 2f), 0.0001f)
        assertEquals(40f, layout.pageLeft(2f), 0f)
    }

    @Test
    fun `clamping keeps the viewport inside the document and the zoom inside its range`() {
        assertEquals(ViewerPosition(1f, 0f, 1900f), layout.clamp(ViewerPosition(1f, 300f, 99_999f)))
        assertEquals(ViewerPosition(1f, 0f, 0f), layout.clamp(ViewerPosition(1f, -5f, -5f)))
        assertEquals(ViewerLayout.MAX_ZOOM, layout.clamp(ViewerPosition(100f, 0f, 0f)).zoom, 0f)
        val small = layout.clamp(ViewerPosition(0.1f, 50f, 50f))
        assertEquals(ViewerLayout.MIN_ZOOM, small.zoom, 0f)
        assertEquals(0f, small.scrollX, 0f)
        // A document narrower than the viewport is centered. zh-CN: 比视口窄的文档居中.
        assertEquals(250f, layout.originX(0.5f), 0.01f)
        assertEquals(1000f, layout.maxScrollX(2f), 0.01f)
    }

    @Test
    fun `visible pages are those that intersect the viewport`() {
        assertEquals(0..2, layout.visiblePages(ViewerPosition(1f, 0f, 0f)))
        assertEquals(1..2, layout.visiblePages(ViewerPosition(1f, 0f, 1300f)))
        assertEquals(2..2, layout.visiblePages(ViewerPosition(2f, 0f, 4000f)))
    }

    @Test
    fun `the current page is under the middle of the viewport, with the ends of the column winning`() {
        assertEquals(0, layout.currentPage(ViewerPosition(1f, 0f, 0f)))
        assertEquals(1, layout.currentPage(ViewerPosition(1f, 0f, 500f)))
        assertEquals(2, layout.currentPage(ViewerPosition(1f, 0f, 1900f)))
    }

    @Test
    fun `jumping puts the top of a page at the top of the viewport`() {
        assertEquals(ViewerPosition(1f, 0f, 1290f), layout.positionForPage(1, ViewerPosition(1f, 0f, 0f)))
        // The last page cannot reach the top: the position is clamped. zh-CN: 末页到不了顶部: 位置被限位.
        assertEquals(ViewerPosition(1f, 0f, 1900f), layout.positionForPage(2, ViewerPosition(1f, 0f, 0f)))
        assertEquals(ViewerPosition(2f, 300f, 2580f), layout.positionForPage(1, ViewerPosition(2f, 300f, 0f)))
        assertEquals(0f, layout.positionForPage(-3, ViewerPosition(1f, 0f, 700f)).scrollY, 0f)
    }

    @Test
    fun `a search hit is centered in the viewport`() {
        assertEquals(ViewerPosition(2f, 20f, 2100f), layout.positionForRect(1, 100f, 100f, 200f, 200f, ViewerPosition(2f, 0f, 0f)))
    }

    @Test
    fun `zooming keeps the document point under the focus`() {
        val from = ViewerPosition(1f, 0f, 500f)
        val to = layout.zoomAround(from, 2f, 500f, 1000f)
        assertEquals(ViewerPosition(2f, 500f, 2000f), to)
        val before = documentPoint(from, 500f, 1000f)
        val after = documentPoint(to, 500f, 1000f)
        assertEquals(before.first, after.first, 0.01f)
        assertEquals(before.second, after.second, 0.01f)
    }

    @Test
    fun `an anchor survives a viewport change`() {
        val position = ViewerPosition(1.5f, 100f, 2400f)
        val anchor = layout.anchorOf(position)
        assertEquals(1, anchor.page)
        assertEquals((1600f - 1310f) / 640f, anchor.fraction, 0.0001f)
        val rotated = ViewerLayout(floatArrayOf(600f, 600f, 300f), floatArrayOf(800f, 400f, 600f), 2000f, 1000f, 10f, 20f)
        val moved = rotated.positionFor(anchor)
        assertEquals(1.5f, moved.zoom, 0f)
        val back = rotated.anchorOf(moved)
        assertEquals(anchor.page, back.page)
        assertEquals(anchor.fraction, back.fraction, 0.001f)
    }

    @Test
    fun `an empty layout has no visible pages`() {
        val empty = ViewerLayout(FloatArray(0), FloatArray(0), 1000f, 2000f, 10f, 20f)
        assertTrue(empty.visiblePages(ViewerPosition(1f, 0f, 0f)).isEmpty())
        assertEquals(0, empty.currentPage(ViewerPosition(1f, 0f, 0f)))
        assertEquals(ViewerPosition(1f, 0f, 0f), empty.positionFor(ViewerAnchor(3, 0.5f, 1f)))
    }

    @Test
    fun `tiles cover the viewport rectangle and are clipped at the page edges`() {
        val tiles = TileGrid.visible(1200, 700, 0f, 0f, 600f, 600f)
        assertEquals(
            listOf(
                TileGrid.Tile(0, 0, 0, 0, 512, 512),
                TileGrid.Tile(1, 0, 512, 0, 512, 512),
                TileGrid.Tile(0, 1, 0, 512, 512, 188),
                TileGrid.Tile(1, 1, 512, 512, 512, 188),
            ),
            tiles,
        )
        assertEquals(TileGrid.Tile(2, 0, 1024, 0, 176, 512), TileGrid.visible(1200, 700, 1100f, 0f, 1300f, 100f).single())
        assertEquals(6, TileGrid.visible(1200, 700, 0f, 0f, 10f, 10f, margin = 2).size)
        assertTrue(TileGrid.visible(1200, 700, -300f, 0f, 0f, 100f).isEmpty())
        assertTrue(TileGrid.visible(1200, 700, 0f, 700f, 100f, 900f).isEmpty())
        assertTrue(TileGrid.visible(0, 700, 0f, 0f, 100f, 100f).isEmpty())
    }

    private fun documentPoint(position: ViewerPosition, x: Float, y: Float): Pair<Float, Float> = Pair(
        (x - layout.originX(position.zoom) + position.scrollX) / position.zoom,
        (y - layout.originY(position.zoom) + position.scrollY) / position.zoom,
    )

}

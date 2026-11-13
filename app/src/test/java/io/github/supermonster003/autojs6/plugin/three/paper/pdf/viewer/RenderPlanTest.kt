package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the viewer asks the renderer for (roadmap P3.1, D35): base bitmaps in two width buckets with a pixel cap,
 * thumbnails, the detail threshold, enlarged pages for pages without tiles and the tiles of the viewport; plus the
 * name of the copy handed to other apps.
 * zh-CN: 查看器向渲染器请求的内容 (路线图 P3.1, D35): 两档宽度且有像素上限的基础位图, 缩略图, 细节阈值, 不分块页面的放大整页与视口内的
 * 分块; 以及交给其他应用的副本的文件名.
 */
class RenderPlanTest {

    private val layout = ViewerLayout(
        pageWidths = floatArrayOf(600f, 600f, 300f),
        pageHeights = floatArrayOf(800f, 400f, 600f),
        viewportWidth = 1000f,
        viewportHeight = 2000f,
        gap = 10f,
        padding = 20f,
    )

    @Test
    fun `the base bitmap has the page width at zoom 1, or half of it when zoomed out`() {
        assertEquals(RenderKey.Page(0, 960, 1280), RenderPlan.base(layout, 0, 1f))
        assertEquals(RenderKey.Page(0, 960, 1280), RenderPlan.base(layout, 0, 3f))
        assertEquals(RenderKey.Page(0, 480, 640), RenderPlan.base(layout, 0, 0.6f))
        assertEquals(RenderKey.Page(2, 960, 1920), RenderPlan.base(layout, 2, 1f))
    }

    @Test
    fun `large pages are scaled down to the pixel cap, keeping their aspect`() {
        val wide = ViewerLayout(floatArrayOf(600f), floatArrayOf(800f), 2000f, 3000f, 10f, 20f)
        val key = RenderPlan.base(wide, 0, 1f)
        assertTrue(key.width.toLong() * key.height <= RenderPlan.MAX_BASE_PIXELS + key.width + key.height)
        assertEquals(800f / 600f, key.height.toFloat() / key.width, 0.002f)
        assertEquals(key.width.toLong() * key.height * 4, key.bytes)
    }

    @Test
    fun `thumbnails follow the strip width`() {
        assertEquals(RenderKey.Page(0, 120, 160), RenderPlan.thumbnail(layout, 0, 120f))
        assertEquals(RenderKey.Page(2, 120, 240), RenderPlan.thumbnail(layout, 2, 120f))
    }

    @Test
    fun `detail is needed once the page is clearly wider than its base`() {
        assertFalse(RenderPlan.needsDetail(layout, 0, 1f))
        assertFalse(RenderPlan.needsDetail(layout, 0, 1.1f))
        assertTrue(RenderPlan.needsDetail(layout, 0, 1.2f))
        assertNull(RenderPlan.enlarged(layout, 0, 1.1f))
        val enlarged = requireNotNull(RenderPlan.enlarged(layout, 0, 2f))
        assertTrue(enlarged.width.toLong() * enlarged.height <= RenderPlan.MAX_ENLARGED_PIXELS + enlarged.width + enlarged.height)
        assertTrue(enlarged.width > 960)
    }

    @Test
    fun `tiles cover the part of the page in the viewport`() {
        val tiles = RenderPlan.tiles(layout, 0, ViewerPosition(2f, 0f, 0f), tileZoom = 2f)
        // Page 1920 x 2560 at (40, 40): columns 0..1, rows 0..3. zh-CN: 页面 1920 x 2560 位于 (40, 40): 第 0..1 列, 第 0..3 行.
        assertEquals(8, tiles.size)
        assertEquals(RenderKey.Tile(0, 1920, 0, 0, 512, 512), tiles.first())
        assertTrue(tiles.all { it.page == 0 && it.pageWidth == 1920 })
        // Mid-pinch at zoom 4 the tiles stay at zoom 2 and cover a smaller part. zh-CN: 捏合中缩放为 4 时分块仍为 2, 覆盖更小的部分.
        val pinching = RenderPlan.tiles(layout, 0, ViewerPosition(4f, 0f, 0f), tileZoom = 2f)
        assertTrue(pinching.size < tiles.size)
        assertTrue(RenderPlan.tiles(layout, 2, ViewerPosition(2f, 0f, 0f), tileZoom = 2f).isEmpty())
    }

    @Test
    fun `the shared copy is named after the document with unsafe characters replaced`() {
        assertEquals("report.pdf", ViewerViewModel.shareName("report"))
        assertEquals("Report.PDF", ViewerViewModel.shareName(" Report.PDF "))
        assertEquals("a_b_c_.pdf", ViewerViewModel.shareName("a/b:c?"))
        assertEquals("document.pdf", ViewerViewModel.shareName("   "))
        assertEquals(124, ViewerViewModel.shareName("x".repeat(500)).length)
    }

}

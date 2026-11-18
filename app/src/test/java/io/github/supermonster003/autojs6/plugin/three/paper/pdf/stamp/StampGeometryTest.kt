package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StampGeometry]: named positions with their margin (none for a centred column or row), `[x, y]` as the top left
 * corner, offsets, rotated bounds, the tile grid and its ceiling (roadmap Q200), image sizes and the fitted font size.
 * zh-CN: [StampGeometry]: 命名位置及其边距 (居中的列或行不内缩), `[x, y]` 为左上角, 偏移, 旋转后的外接尺寸, 平铺网格及其上限 (路线图
 * Q200), 图片尺寸与自适应字号.
 */
class StampGeometryTest {

    private fun center(position: StampPosition, offsetX: Double = 0.0, offsetY: Double = 0.0) =
        StampGeometry.center(position, 36.0, offsetX, offsetY, 100.0, 20.0, 595.0, 842.0)

    private fun assertPoint(expected: Pair<Double, Double>, actual: Pair<Double, Double>) {
        assertEquals("x of $actual", expected.first, actual.first, 1e-9)
        assertEquals("y of $actual", expected.second, actual.second, 1e-9)
    }

    @Test
    fun `named positions align the box to the page, inset by the margin at the edges`() {
        assertPoint(86.0 to 46.0, center(StampPosition.named("top-left")!!))
        assertPoint(297.5 to 46.0, center(StampPosition.named("top-center")!!))
        assertPoint(509.0 to 46.0, center(StampPosition.named("top-right")!!))
        assertPoint(86.0 to 421.0, center(StampPosition.named("center-left")!!))
        assertPoint(297.5 to 421.0, center(StampPosition.named("center")!!))
        assertPoint(509.0 to 796.0, center(StampPosition.named("bottom-right")!!))
        assertPoint(297.5 to 796.0, center(StampPosition.named("bottom-center")!!))
        assertNull(StampPosition.named("middle"))
    }

    @Test
    fun `a point is the top left corner, offsets come after`() {
        assertPoint(60.0 to 30.0, center(StampPosition.At(10.0, 20.0)))
        assertPoint(55.0 to 40.0, center(StampPosition.At(10.0, 20.0), offsetX = -5.0, offsetY = 10.0))
        assertPoint(306.5 to 416.0, center(StampPosition.Named(1, 1), offsetX = 9.0, offsetY = -5.0))
    }

    @Test
    fun `rotated bounds`() {
        val (w, h) = StampGeometry.rotatedBounds(100.0, 20.0, 90.0)
        assertEquals(20.0, w, 1e-9)
        assertEquals(100.0, h, 1e-9)
        val (w45, h45) = StampGeometry.rotatedBounds(100.0, 100.0, 45.0)
        assertEquals(100.0 * Math.sqrt(2.0), w45, 1e-9)
        assertEquals(w45, h45, 1e-9)
    }

    @Test
    fun `tiles cover the page around the positioned copy`() {
        val copies = StampGeometry.tiles(150.0, 150.0, 100.0, 100.0, 0.0, 0.0, 0.0, 300.0, 300.0, "t")
        assertEquals("a 5 x 5 grid without the copy at the position", 24, copies.size)
        assertTrue(copies.contains(50.0 to 50.0))
        assertTrue(copies.contains(-50.0 to 350.0))
        assertTrue(!copies.contains(150.0 to 150.0))
        val gapped = StampGeometry.tiles(150.0, 150.0, 100.0, 100.0, 0.0, 50.0, 50.0, 300.0, 300.0, "t")
        assertTrue(gapped.contains(0.0 to 0.0) && gapped.contains(300.0 to 300.0))
        assertEquals(8, gapped.size)
        val tiny = assertThrows(PdfFailure::class.java) { StampGeometry.tiles(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 595.0, 842.0, "payload.items[0]") }
        assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, tiny.code)
        assertTrue(tiny.detail!!, tiny.detail!!.startsWith("payload.items[0]"))
    }

    @Test
    fun `images keep 72 dpi, scaled down to fit, or take the given sizes`() {
        assertEquals(595.0 to 297.5, StampGeometry.imageSize(1000, 500, null, null, null, 595.0, 842.0))
        assertEquals(100.0 to 50.0, StampGeometry.imageSize(100, 50, null, null, null, 595.0, 842.0))
        assertEquals(200.0 to 100.0, StampGeometry.imageSize(100, 50, 200.0, null, null, 595.0, 842.0))
        assertEquals(40.0 to 20.0, StampGeometry.imageSize(100, 50, null, 20.0, null, 595.0, 842.0))
        assertEquals(30.0 to 90.0, StampGeometry.imageSize(100, 50, 30.0, 90.0, 3.0, 595.0, 842.0))
        assertEquals(1000.0 to 500.0, StampGeometry.imageSize(500, 250, null, null, 2.0, 595.0, 842.0))
    }

    @Test
    fun `fitted font size spans half the page width`() {
        assertEquals(59.5, StampGeometry.fitFontSize(500.0, 595.0, 1000.0)!!, 1e-9)
        assertEquals(1000.0, StampGeometry.fitFontSize(1.0, 595.0, 1000.0)!!, 0.0)
        assertNull(StampGeometry.fitFontSize(0.0, 595.0, 1000.0))
    }

}

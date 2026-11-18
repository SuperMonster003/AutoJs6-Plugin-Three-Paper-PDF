package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [StampFrame] maps the canvas of a stamp page onto the displayed page for every rotation and crop box offset (roadmap
 * D60): a display point drawn on the canvas comes back at the same display point through [PageFrame], the frame of
 * search hits.
 * zh-CN: [StampFrame] 在每种旋转与裁剪框偏移下都把印章页画布映射到显示页面 (路线图 D60): 画在画布上的显示坐标经 [PageFrame]
 * (搜索命中所用的页框) 换算后回到同一显示坐标.
 */
class StampFrameTest {

    private val points = listOf(0.0 to 0.0, 36.0 to 48.0, 100.5 to 700.25, 594.0 to 1.0, 300.0 to 841.0)

    private fun assertRoundTrip(frame: StampFrame) {
        val display = PageFrame(frame.width, frame.height, frame.rotation)
        for ((x, y) in points) {
            val (userX, userY) = frame.toUser(x, y)
            val (backX, backY) = display.display(userX - frame.cropX, userY - frame.cropY)
            assertEquals("x of ($x, $y) on $frame", x, backX, 1e-9)
            assertEquals("y of ($x, $y) on $frame", y, backY, 1e-9)
        }
    }

    @Test
    fun `display points survive every rotation, crop offset and fractional page size`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            assertRoundTrip(StampFrame(0.0, 0.0, 595.0, 842.0, rotation))
            assertRoundTrip(StampFrame(40.0, 50.0, 595.0, 842.0, rotation))
            assertRoundTrip(StampFrame(-12.5, 7.25, 595.27563, 841.8898, rotation))
            assertRoundTrip(StampFrame(10.0, 20.0, 842.0, 595.0, rotation))
        }
    }

    @Test
    fun `the canvas is the displayed size rounded up to whole points`() {
        val portrait = StampFrame(0.0, 0.0, 595.27563, 841.8898, 0)
        assertEquals(596, portrait.canvasWidth)
        assertEquals(842, portrait.canvasHeight)
        val turned = StampFrame(0.0, 0.0, 842.0, 595.0, 90)
        assertEquals(595, turned.canvasWidth)
        assertEquals(842, turned.canvasHeight)
        // A size a hair above a whole point stays at that point. zh-CN: 只比整点大一丝的尺寸保持为该整点.
        assertEquals(595, StampFrame(0.0, 0.0, 595.0001, 842.0, 0).canvasWidth)
        assertEquals(1, StampFrame(0.0, 0.0, 0.2, 0.2, 0).canvasWidth)
    }

    @Test
    fun `the matrix of each rotation`() {
        assertEquals(listOf(1.0, 0.0, 0.0, 1.0, 40.0, 50.0), StampFrame(40.0, 50.0, 595.0, 842.0, 0).matrix().toList())
        assertEquals(listOf(0.0, 1.0, -1.0, 0.0, 40.0 + 595.0, 50.0), StampFrame(40.0, 50.0, 595.0, 842.0, 90).matrix().toList())
        assertEquals(listOf(-1.0, 0.0, 0.0, -1.0, 40.0 + 595.0, 50.0 + 842.0), StampFrame(40.0, 50.0, 595.0, 842.0, 180).matrix().toList())
        assertEquals(listOf(0.0, -1.0, 1.0, 0.0, 40.0, 50.0 + 842.0), StampFrame(40.0, 50.0, 595.0, 842.0, 270).matrix().toList())
    }

    @Test
    fun `the frame of a page reads its crop box and normalized rotation`() {
        val page = PDPage(PDRectangle(0f, 0f, 700f, 950f)).apply {
            cropBox = PDRectangle(40f, 50f, 595f, 842f)
            rotation = -90
        }
        assertEquals(StampFrame(40.0, 50.0, 595.0, 842.0, 270), StampFrame.of(page))
        page.rotation = 45
        assertEquals(0, StampFrame.of(page).rotation)
        assertThrows(IllegalArgumentException::class.java) { StampFrame(0.0, 0.0, 1.0, 1.0, 45) }
    }

}

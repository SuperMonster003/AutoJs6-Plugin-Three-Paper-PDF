package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlin.math.ceil

/**
 * Where a stamp page lands on a target page (roadmap D60): stamps are drawn on a canvas of [canvasWidth] x
 * [canvasHeight] points whose top left corner is the top left corner of the page as displayed (after `/Rotate`), so a
 * stamp is positioned in the same coordinates as search hits (protocol "Conventions"). The canvas is the displayed size
 * rounded up to whole points, because `PdfDocument` pages have integer sizes; [matrix] maps the user space of that
 * stamp page (origin at its lower left corner, `PdfDocument` flips the canvas) to the user space of the target page,
 * through the crop box offset ([cropX], [cropY]) and the rotation.
 * zh-CN: 印章页在目标页上的位置 (路线图 D60): 印章画在 [canvasWidth] x [canvasHeight] 点的画布上, 画布左上角就是页面按显示方向
 * (应用 `/Rotate` 之后) 的左上角, 因此印章与搜索命中使用同一坐标 (协议 "Conventions" 一节). `PdfDocument` 的页面尺寸是整数,
 * 所以画布是显示尺寸向上取整到整点; [matrix] 把该印章页的用户空间 (原点在左下角, `PdfDocument` 会翻转画布) 经裁剪框偏移
 * ([cropX], [cropY]) 与旋转映射到目标页的用户空间.
 */
internal data class StampFrame(
    val cropX: Double,
    val cropY: Double,
    val width: Double,
    val height: Double,
    val rotation: Int,
) {

    init {
        require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) { "rotation must be 0, 90, 180 or 270, got $rotation" }
    }

    val displayWidth: Double get() = if (rotation == 90 || rotation == 270) height else width

    val displayHeight: Double get() = if (rotation == 90 || rotation == 270) width else height

    val canvasWidth: Int get() = canvasSize(displayWidth)

    val canvasHeight: Int get() = canvasSize(displayHeight)

    /**
     * `[a, b, c, d, e, f]` of the `cm` that draws the stamp page (or its form) onto the target page: a point
     * (x, y) of the stamp page lands at (a x + c y + e, b x + d y + f) of the target page.
     * zh-CN: 把印章页 (或其表单) 画到目标页的 `cm` 的 `[a, b, c, d, e, f]`: 印章页上的点 (x, y) 落在目标页的
     * (a x + c y + e, b x + d y + f).
     */
    fun matrix(): DoubleArray {
        val h = canvasHeight.toDouble()
        return when (rotation) {
            90 -> doubleArrayOf(0.0, 1.0, -1.0, 0.0, cropX + h, cropY)
            180 -> doubleArrayOf(-1.0, 0.0, 0.0, -1.0, cropX + width, cropY + h)
            270 -> doubleArrayOf(0.0, -1.0, 1.0, 0.0, cropX + width - h, cropY + height)
            else -> doubleArrayOf(1.0, 0.0, 0.0, 1.0, cropX, cropY + height - h)
        }
    }

    /**
     * The target page user space point of display point ([x], [y]) (points from the top left corner of the displayed
     * page), through [matrix] as `PdfDocument` maps the canvas.
     * zh-CN: 显示坐标 ([x], [y]) (从显示页面左上角起算的点数) 在目标页用户空间中的位置, 按 `PdfDocument` 映射画布的方式经 [matrix] 换算.
     */
    fun toUser(x: Double, y: Double): Pair<Double, Double> {
        val m = matrix()
        val stampY = canvasHeight - y
        return (m[0] * x + m[2] * stampY + m[4]) to (m[1] * x + m[3] * stampY + m[5])
    }

    companion object {

        /**
         * Sizes within this distance of a whole point are taken as that point, so 595.0001 does not grow a canvas to
         * 596 points.
         * zh-CN: 与整点相差不超过该值的尺寸按该整点计, 使 595.0001 不会把画布放大到 596 点.
         */
        private const val WHOLE_POINT_SLACK = 1e-3

        private fun canvasSize(size: Double): Int = ceil(size - WHOLE_POINT_SLACK).toInt().coerceAtLeast(1)

        /**
         * The frame of [page]: its crop box (clipped to the media box by PDFBox, as pdfium does) and its rotation; a
         * rotation that is not a multiple of 90 counts as 0, as in `PageFrame`.
         * zh-CN: [page] 的页框: 裁剪框 (PDFBox 与 pdfium 一样裁到媒体框之内) 与旋转角度; 不是 90 倍数的旋转按 0 处理, 与 `PageFrame` 相同.
         */
        fun of(page: PDPage): StampFrame {
            val box = page.cropBox
            val rotation = Math.floorMod(page.rotation, 360).let { if (it % 90 == 0) it else 0 }
            return StampFrame(box.lowerLeftX.toDouble(), box.lowerLeftY.toDouble(), box.width.toDouble(), box.height.toDouble(), rotation)
        }

    }

}

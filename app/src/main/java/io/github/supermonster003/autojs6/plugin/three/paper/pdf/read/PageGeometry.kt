package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * A page as displayed: its crop box of [width] x [height] points (before rotation) turned clockwise by [rotation]
 * (0, 90, 180 or 270). [display] maps a point of unrotated user space, relative to the lower left corner of the crop
 * box, to the protocol coordinates: points from the top left corner of the displayed page (protocol "Conventions").
 * zh-CN: 显示时的页面: 旋转前为 [width] x [height] 点的裁剪框, 顺时针旋转 [rotation] (0, 90, 180 或 270). [display] 把相对裁剪框
 * 左下角的未旋转用户空间坐标换算为协议坐标: 从显示页面左上角起算的点数 (协议 "Conventions" 一节).
 */
internal data class PageFrame(val width: Double, val height: Double, val rotation: Int) {

    val displayWidth: Double get() = if (rotation == 90 || rotation == 270) height else width

    val displayHeight: Double get() = if (rotation == 90 || rotation == 270) width else height

    fun display(x: Double, y: Double): Pair<Double, Double> = when (rotation) {
        90 -> y to x
        180 -> (width - x) to y
        270 -> (height - y) to (width - x)
        else -> x to (height - y)
    }

}

/**
 * One glyph in unrotated user space: origin ([x], [y]) on the baseline, unit advance direction ([ux], [uy]), [advance]
 * along it, [ascent] above and [descent] below the baseline (both positive, in points).
 * zh-CN: 未旋转用户空间中的一个字形: 基线上的原点 ([x], [y]), 单位前进方向 ([ux], [uy]), 沿该方向的 [advance], 基线以上的 [ascent]
 * 与基线以下的 [descent] (均为正值, 单位为点).
 */
internal data class Glyph(
    val x: Double,
    val y: Double,
    val ux: Double,
    val uy: Double,
    val advance: Double,
    val ascent: Double,
    val descent: Double,
)

/**
 * The bounding box of glyphs as displayed, clamped to the page: `[left, top, right, bottom]` in points rounded to two
 * decimals.
 * zh-CN: 字形在显示页面上的外接矩形, 限制在页面内: `[left, top, right, bottom]`, 单位为点, 保留两位小数.
 */
internal class DisplayBounds(private val frame: PageFrame) {

    private var left = Double.POSITIVE_INFINITY
    private var top = Double.POSITIVE_INFINITY
    private var right = Double.NEGATIVE_INFINITY
    private var bottom = Double.NEGATIVE_INFINITY

    val isEmpty: Boolean get() = left > right

    fun add(glyph: Glyph): DisplayBounds {
        // The upward direction is the advance direction turned counterclockwise. zh-CN: 向上方向为前进方向逆时针旋转 90 度.
        val vx = -glyph.uy
        val vy = glyph.ux
        for (along in doubleArrayOf(0.0, glyph.advance)) {
            for (across in doubleArrayOf(-glyph.descent, glyph.ascent)) {
                val (x, y) = frame.display(glyph.x + along * glyph.ux + across * vx, glyph.y + along * glyph.uy + across * vy)
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
            }
        }
        return this
    }

    fun toArray(): DoubleArray = doubleArrayOf(
        round(left.coerceIn(0.0, frame.displayWidth)),
        round(top.coerceIn(0.0, frame.displayHeight)),
        round(right.coerceIn(0.0, frame.displayWidth)),
        round(bottom.coerceIn(0.0, frame.displayHeight)),
    )

    private fun round(value: Double): Double = (value * 100).roundToLong() / 100.0

}

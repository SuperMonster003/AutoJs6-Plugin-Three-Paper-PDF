package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * An image stamp (roadmap D61, P6.3): [bitmap] drawn as a box of [width] x [height] points centred on display point
 * ([x], [y]) and turned counterclockwise by [rotation] degrees around that centre, at [opacity] through the paint
 * alpha, so its transparency is kept (Skia writes the alpha channel as a soft mask). [copies] are further centres of
 * the same box (a tiled item). The bitmap belongs to the operation, which recycles it after stamping; equal stamps
 * share the same bitmap object, so pages with equal stamps share one form. [jpeg] is the JPEG the bitmap was decoded
 * from, which [JpegPassThrough] puts back in place of what Skia writes (roadmap Q201 (a)).
 * zh-CN: 图片印章 (路线图 D61, P6.3): 把 [bitmap] 画成以显示坐标 ([x], [y]) 为中心, [width] x [height] 点的方框, 绕该中心逆时针旋转
 * [rotation] 度, 经画笔 alpha 以 [opacity] 绘制, 因此透明通道得以保留 (Skia 把 alpha 通道写成软蒙版). [copies] 是同一方框的其他中心
 * (平铺条目). 位图归操作所有, 由操作在印章完成后回收; 相等的印章使用同一个位图对象, 因此印章相等的页面共用一个表单. [jpeg] 是解码出该
 * 位图的 JPEG, 由 [JpegPassThrough] 放回以取代 Skia 写出的内容 (路线图 Q201 (a)).
 */
internal data class ImageStamp(
    val bitmap: Bitmap,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double = 0.0,
    val opacity: Double = 1.0,
    override val layer: StampLayer = StampLayer.OVER,
    val copies: List<Pair<Double, Double>> = emptyList(),
    val jpeg: JpegData? = null,
) : Stamp {

    init {
        require(width > 0.0 && height > 0.0) { "the image box must not be empty, got $width x $height" }
        require(opacity in 0.0..1.0) { "opacity must be in [0, 1], got $opacity" }
        require(x.isFinite() && y.isFinite() && rotation.isFinite()) { "the position and the rotation must be finite" }
    }

    override fun draw(canvas: Canvas, fonts: StampFonts) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = (255 * opacity).roundToInt().coerceIn(0, 255) }
        val box = RectF((-width / 2).toFloat(), (-height / 2).toFloat(), (width / 2).toFloat(), (height / 2).toFloat())
        for ((atX, atY) in listOf(x to y) + copies) {
            canvas.save()
            try {
                canvas.translate(atX.toFloat(), atY.toFloat())
                // D61 angles turn counterclockwise; canvas angles clockwise on screen. zh-CN: D61 的角度逆时针; 画布角度在屏幕上顺时针.
                canvas.rotate(-rotation.toFloat())
                canvas.drawBitmap(bitmap, null, box, paint)
            } finally {
                canvas.restore()
            }
        }
    }

}

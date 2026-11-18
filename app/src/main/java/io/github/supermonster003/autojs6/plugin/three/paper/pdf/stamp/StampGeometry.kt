package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Placement arithmetic of the `stamp` items (protocol "Stamp items"), in points of the displayed page (origin at its
 * top left corner, y down): where the box of an item lands, the grid of a tiled item, the size of an image item and
 * the fitted font size. Pure functions, tested on the JVM.
 * zh-CN: `stamp` 条目的摆放计算 (协议 "Stamp items" 一节), 以显示页面的点计 (原点在左上角, y 向下): 条目方框的位置, 平铺条目的网格,
 * 图片条目的尺寸与自适应字号. 都是纯函数, 在 JVM 上测试.
 */
internal object StampGeometry {

    /**
     * Copies of one tiled item on one page, the one at the position included. More is `LIMIT_EXCEEDED` (roadmap Q200):
     * a 1 pt text with no gap would otherwise draw hundreds of thousands of copies.
     * zh-CN: 一个平铺条目在一页上的份数上限 (含位于该位置的那一份). 超过为 `LIMIT_EXCEEDED` (路线图 Q200): 否则 1 pt 且无间距的文字会画出
     * 数十万份.
     */
    const val MAX_TILES = 4096

    /** Fitted text spans this fraction of the displayed page width. zh-CN: 自适应字号使文字宽度为显示页宽的这一比例. */
    const val FIT_WIDTH_FRACTION = 0.5

    /**
     * The centre of a box of [boxWidth] x [boxHeight] at [position] on a displayed page of [pageWidth] x [pageHeight]:
     * a named spot aligns the box to that edge or centre of the page, inset by [margin] at the edges (a centred
     * column or row takes no margin); `[x, y]` is the top left corner of the box. The offsets are added after.
     * zh-CN: [boxWidth] x [boxHeight] 的方框在 [pageWidth] x [pageHeight] 的显示页面上位于 [position] 时的中心: 命名位置让方框对齐页面的
     * 相应边或中线, 在边上按 [margin] 内缩 (居中的列或行不内缩); `[x, y]` 是方框的左上角. 之后再加上偏移.
     */
    fun center(
        position: StampPosition,
        margin: Double,
        offsetX: Double,
        offsetY: Double,
        boxWidth: Double,
        boxHeight: Double,
        pageWidth: Double,
        pageHeight: Double,
    ): Pair<Double, Double> {
        val (left, top) = when (position) {
            is StampPosition.At -> position.x to position.y
            is StampPosition.Named -> {
                val x = when (position.column) {
                    0 -> margin
                    1 -> (pageWidth - boxWidth) / 2
                    else -> pageWidth - margin - boxWidth
                }
                val y = when (position.row) {
                    0 -> margin
                    1 -> (pageHeight - boxHeight) / 2
                    else -> pageHeight - margin - boxHeight
                }
                x to y
            }
        }
        return (left + boxWidth / 2 + offsetX) to (top + boxHeight / 2 + offsetY)
    }

    /**
     * The width and height of the box of [boxWidth] x [boxHeight] turned by [rotation] degrees.
     * zh-CN: [boxWidth] x [boxHeight] 的方框旋转 [rotation] 度后的外接宽高.
     */
    fun rotatedBounds(boxWidth: Double, boxHeight: Double, rotation: Double): Pair<Double, Double> {
        val radians = Math.toRadians(rotation)
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        return (boxWidth * c + boxHeight * s) to (boxWidth * s + boxHeight * c)
    }

    /**
     * The centres of a tiled item other than ([centerX], [centerY]): a grid whose step is the rotated bounds of the box
     * plus [gapX] / [gapY], covering every copy that reaches the displayed page. `LIMIT_EXCEEDED` past [MAX_TILES].
     * zh-CN: 平铺条目除 ([centerX], [centerY]) 之外的各份中心: 步长为方框旋转后的外接尺寸加 [gapX] / [gapY] 的网格, 覆盖能碰到显示页面的
     * 每一份. 超过 [MAX_TILES] 为 `LIMIT_EXCEEDED`.
     */
    fun tiles(
        centerX: Double,
        centerY: Double,
        boxWidth: Double,
        boxHeight: Double,
        rotation: Double,
        gapX: Double,
        gapY: Double,
        pageWidth: Double,
        pageHeight: Double,
        label: String,
    ): List<Pair<Double, Double>> {
        val (width, height) = rotatedBounds(boxWidth, boxHeight, rotation)
        val stepX = width + gapX
        val stepY = height + gapY
        if (stepX <= 0.0 || stepY <= 0.0) throw PdfFailure.invalid("$label: a tiled stamp needs a box or a gap that is not empty")
        // Copies whose bounds reach [0, pageWidth] x [0, pageHeight]. zh-CN: 外接框能碰到 [0, pageWidth] x [0, pageHeight] 的各份.
        val firstColumn = ceil((-width / 2 - centerX) / stepX).toLong()
        val lastColumn = floor((pageWidth + width / 2 - centerX) / stepX).toLong()
        val firstRow = ceil((-height / 2 - centerY) / stepY).toLong()
        val lastRow = floor((pageHeight + height / 2 - centerY) / stepY).toLong()
        val columns = (lastColumn - firstColumn + 1).coerceAtLeast(0)
        val rows = (lastRow - firstRow + 1).coerceAtLeast(0)
        if (columns * rows > MAX_TILES) {
            throw PdfFailure(PdfErrorCodes.LIMIT_EXCEEDED, "$label: tiling needs $columns x $rows copies on a page, at most $MAX_TILES; use a larger box or gap")
        }
        val copies = ArrayList<Pair<Double, Double>>((columns * rows).toInt())
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                if (row == 0L && column == 0L) continue
                copies += (centerX + column * stepX) to (centerY + row * stepY)
            }
        }
        return copies
    }

    /**
     * The size in points of an image of [pixelWidth] x [pixelHeight]: [width] and [height] as given, one of them with
     * the aspect ratio kept, else [scale] times its pixel size (one pixel per point at 72 dpi), else its pixel size
     * scaled down, never up, to fit the displayed page of [pageWidth] x [pageHeight].
     * zh-CN: [pixelWidth] x [pixelHeight] 的图片以点计的尺寸: 按给定的 [width] 与 [height]; 只给一个时保持宽高比; 否则为像素尺寸乘以
     * [scale] (72 dpi 下一像素一点); 再否则为像素尺寸, 超出 [pageWidth] x [pageHeight] 的显示页面时等比缩小 (不放大).
     */
    fun imageSize(
        pixelWidth: Int,
        pixelHeight: Int,
        width: Double?,
        height: Double?,
        scale: Double?,
        pageWidth: Double,
        pageHeight: Double,
    ): Pair<Double, Double> {
        val w = pixelWidth.toDouble()
        val h = pixelHeight.toDouble()
        return when {
            width != null && height != null -> width to height
            width != null -> width to width * h / w
            height != null -> height * w / h to height
            scale != null -> w * scale to h * scale
            else -> {
                val fit = minOf(1.0, pageWidth / w, pageHeight / h)
                w * fit to h * fit
            }
        }
    }

    /**
     * The font size that makes the widest line as wide as [FIT_WIDTH_FRACTION] of [pageWidth], from [widestAt100]
     * (the widest line at 100 pt), within (0, [maximum]]; null when the text has no width.
     * zh-CN: 使最宽一行的宽度为 [pageWidth] 的 [FIT_WIDTH_FRACTION] 的字号, 由 [widestAt100] (100 pt 下最宽一行的宽度) 算出, 限制在
     * (0, [maximum]] 内; 文字没有宽度时为 null.
     */
    fun fitFontSize(widestAt100: Double, pageWidth: Double, maximum: Double): Double? {
        if (widestAt100 <= 0.0 || pageWidth <= 0.0) return null
        return (100.0 * pageWidth * FIT_WIDTH_FRACTION / widestAt100).coerceAtMost(maximum)
    }

}

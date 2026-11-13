package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.OperatorProcessor
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Where a page draws images (roadmap D35, appendix D Q3: the night mode of the viewer inverts the page but keeps the
 * images in their colors): the bounding box of every image XObject and inline image as displayed, `[left, top, right,
 * bottom]` in points from the top left corner of the displayed page (the search rectangle convention), clamped to the
 * page. An image is the unit square under the current transformation matrix, so forms (at most
 * [PageImages.MAX_FORM_DEPTH] deep) and rotated placements are covered; at most [MAX_RECTS] boxes per page.
 * zh-CN: 页面绘制图片的位置 (路线图 D35, 附录 D Q3: 查看器夜间模式反色显示页面, 但图片保留原色): 每个图片 XObject 与内联图片在
 * 显示页面上的外接矩形, `[left, top, right, bottom]`, 单位为点, 从显示页面左上角起算 (与搜索矩形相同), 限制在页面内. 图片是当前
 * 变换矩阵下的单位正方形, 因此表单 (至多 [PageImages.MAX_FORM_DEPTH] 层) 与旋转放置都能覆盖; 每页至多 [MAX_RECTS] 个矩形.
 */
internal object ImageBounds {

    const val MAX_RECTS = 512

    fun of(page: PDPage, stop: StopSignal): List<FloatArray> {
        val frame = PageFrame.of(page)
        val box = page.cropBox
        val walker = Walker(stop, frame, box.lowerLeftX.toDouble(), box.lowerLeftY.toDouble())
        try {
            walker.processPage(page)
        } catch (_: Full) {
            // At most MAX_RECTS boxes. zh-CN: 至多 MAX_RECTS 个矩形.
        } catch (e: IOException) {
            // A damaged content stream keeps the boxes found so far. zh-CN: 内容流损坏时保留已找到的矩形.
        }
        return walker.rects
    }

    private object Full : RuntimeException() {
        private fun readResolve(): Any = Full
        override fun fillInStackTrace(): Throwable = this
    }

    private class Walker(
        private val stop: StopSignal,
        private val frame: PageFrame,
        private val originX: Double,
        private val originY: Double,
    ) : PDFStreamEngine() {

        val rects = ArrayList<FloatArray>()
        private var depth = 0
        private var operators = 0

        init {
            addOperator(Concatenate())
            addOperator(Save())
            addOperator(Restore())
            addOperator(DrawObject())
            addOperator(InlineImage())
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (++operators % 256 == 0) stop.checkStop()
            super.processOperator(operator, operands)
        }

        fun addUnitSquare() {
            val matrix = graphicsState.currentTransformationMatrix
            var left = Double.POSITIVE_INFINITY
            var top = Double.POSITIVE_INFINITY
            var right = Double.NEGATIVE_INFINITY
            var bottom = Double.NEGATIVE_INFINITY
            for (corner in CORNERS) {
                val point = matrix.transformPoint(corner.first, corner.second)
                val (x, y) = frame.display(point.x - originX, point.y - originY)
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
            }
            val clamped = floatArrayOf(
                left.coerceIn(0.0, frame.displayWidth).toFloat(),
                top.coerceIn(0.0, frame.displayHeight).toFloat(),
                right.coerceIn(0.0, frame.displayWidth).toFloat(),
                bottom.coerceIn(0.0, frame.displayHeight).toFloat(),
            )
            if (clamped[2] > clamped[0] && clamped[3] > clamped[1]) rects += clamped
            if (rects.size >= MAX_RECTS) throw Full
        }

        inner class DrawObject : OperatorProcessor() {
            override fun getName() = "Do"

            override fun process(operator: Operator, operands: MutableList<COSBase>) {
                val name = operands.firstOrNull() as? COSName ?: return
                when (val xObject = runCatching { context.resources?.getXObject(name) }.getOrNull()) {
                    is PDImageXObject -> addUnitSquare()
                    is PDFormXObject -> {
                        if (depth >= PageImages.MAX_FORM_DEPTH) return
                        depth++
                        try {
                            if (xObject is PDTransparencyGroup) context.showTransparencyGroup(xObject) else context.showForm(xObject)
                        } finally {
                            depth--
                        }
                    }
                    else -> Unit
                }
            }
        }

        inner class InlineImage : OperatorProcessor() {
            override fun getName() = "BI"

            override fun process(operator: Operator, operands: MutableList<COSBase>) {
                if (operator.imageData != null) addUnitSquare()
            }
        }

    }

    private val CORNERS = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)

}

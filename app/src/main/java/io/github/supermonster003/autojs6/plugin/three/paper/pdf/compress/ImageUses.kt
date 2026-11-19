package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.OperatorProcessor
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageImages
import java.io.IOException
import java.util.IdentityHashMap
import kotlin.math.hypot

/**
 * The image XObjects the pages draw (roadmap D64): each stream once, in the order first drawn, with the largest size it
 * is drawn at. An image is the unit square under the current transformation matrix, so its displayed width is the
 * length of the transformed x axis and its height that of the y axis (rotated, skewed and mirrored placements
 * included); forms are followed at most [PageImages.MAX_FORM_DEPTH] deep. Inline images are not image XObjects and
 * are left alone; images only annotations, patterns or Type 3 glyphs draw are not found and stay as they are. A page
 * whose content cannot be parsed keeps the images found before the damage.
 * zh-CN: 页面绘制的图片 XObject (路线图 D64): 每个流一次, 按首次绘制的顺序, 并记录它被绘制的最大尺寸. 图片是当前变换矩阵下的单位
 * 正方形, 因此显示宽度是变换后 x 轴的长度, 高度是 y 轴的长度 (旋转, 倾斜与镜像放置都适用); 表单至多跟随 [PageImages.MAX_FORM_DEPTH]
 * 层. 内联图片不是图片 XObject, 不处理; 只由注释, 图案或 Type 3 字形绘制的图片找不到, 保持原样. 内容无法解析的页面保留损坏之前
 * 找到的图片.
 */
internal object ImageUses {

    /**
     * One image XObject: [page] (from 1) first draws it, [width] x [height] points is the largest size drawn.
     * zh-CN: 一个图片 XObject: 第 [page] 页 (从 1 起) 首次绘制它, [width] x [height] 点是绘制的最大尺寸.
     */
    class Use(val stream: COSStream, val image: PDImageXObject, val page: Int) {

        var width = 0.0
            private set

        var height = 0.0
            private set

        fun drawn(width: Double, height: Double) {
            if (width > this.width) this.width = width
            if (height > this.height) this.height = height
        }

    }

    fun collect(document: PDDocument, stop: StopSignal): List<Use> {
        val uses = Uses()
        var number = 0
        for (page in document.pages) {
            number++
            stop.checkStop()
            try {
                Walker(stop, number, uses).processPage(page)
            } catch (_: IOException) {
                // A damaged content stream keeps the images found so far. zh-CN: 内容流损坏时保留已找到的图片.
            }
        }
        return uses.order
    }

    /** The uses by stream identity, in the order first drawn. zh-CN: 按流的同一性登记的用途, 保持首次绘制的顺序. */
    private class Uses {

        val order = ArrayList<Use>()

        private val byStream = IdentityHashMap<COSStream, Use>()

        fun of(image: PDImageXObject, page: Int): Use = byStream.getOrPut(image.cosObject) { Use(image.cosObject, image, page).also { order += it } }

    }

    private class Walker(private val stop: StopSignal, private val page: Int, private val uses: Uses) : PDFStreamEngine() {

        private var depth = 0
        private var operators = 0

        init {
            addOperator(Concatenate())
            addOperator(Save())
            addOperator(Restore())
            addOperator(DrawObject())
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (++operators % 256 == 0) stop.checkStop()
            super.processOperator(operator, operands)
        }

        inner class DrawObject : OperatorProcessor() {
            override fun getName() = "Do"

            override fun process(operator: Operator, operands: MutableList<COSBase>) {
                val name = operands.firstOrNull() as? COSName ?: return
                when (val xObject = runCatching { context.resources?.getXObject(name) }.getOrNull()) {
                    is PDImageXObject -> {
                        val matrix = graphicsState.currentTransformationMatrix
                        val width = hypot(matrix.getValue(0, 0).toDouble(), matrix.getValue(0, 1).toDouble())
                        val height = hypot(matrix.getValue(1, 0).toDouble(), matrix.getValue(1, 1).toDouble())
                        uses.of(xObject, page).drawn(width, height)
                    }
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

    }

}

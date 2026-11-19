package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.util.Matrix
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import org.autojs.plugin.pdf.api.PdfContract
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One widget appearance the Canvas fallback draws (roadmap D62, D60): a [width] x [height] point box (the widget
 * rectangle, turned for `/MK /R`) with [padding] on every side; a text or combo widget shows [text] (one line unless
 * [multiline]), a list widget the rows [options] from [topIndex] with the [selected] rows highlighted. [fontSize] 0
 * means automatic (fit the height for one line, 12 for several), [color] is ARGB and [align] the `/Q` value (0 left,
 * 1 centered, 2 right).
 * zh-CN: Canvas 回落要画的一个控件外观 (路线图 D62, D60): [width] x [height] 点的方框 (控件矩形, 按 `/MK /R` 转过), 四边留 [padding];
 * 文本或下拉控件显示 [text] (除非 [multiline] 否则只有一行), 列表控件从 [topIndex] 起逐行显示 [options], 高亮 [selected] 行.
 * [fontSize] 为 0 表示自动 (单行时适配高度, 多行时为 12), [color] 为 ARGB, [align] 为 `/Q` 的值 (0 左, 1 居中, 2 右).
 */
internal data class AppearanceRequest(
    val width: Double,
    val height: Double,
    val padding: Double,
    val text: String?,
    val multiline: Boolean,
    val options: List<String>,
    val selected: Set<Int>,
    val topIndex: Int,
    val fontSize: Double,
    val color: Int,
    val align: Int,
)

/**
 * Draws [AppearanceRequest]s into form XObjects of the target document, one per request in order; the Android
 * implementation is [CanvasAppearances], JVM tests pass their own.
 * zh-CN: 把 [AppearanceRequest] 画成目标文档中的表单 XObject, 按顺序每个请求一个; Android 实现是 [CanvasAppearances], JVM 测试传入
 * 自己的实现.
 */
internal fun interface AppearanceDrawer {

    fun draw(document: PDDocument, requests: List<AppearanceRequest>, stop: StopSignal): List<PDFormXObject>

}

/**
 * The Canvas fallback of field appearances (roadmap D62): when PDFBox cannot build the appearance of a text, combo or
 * list field with the form's own fonts (a value its font cannot encode, a missing `/DA` font), every widget of the
 * field gets a normal appearance that keeps the widget's background and border (`/MK /BG`, `/MK /BC`, `/BS /W`) and
 * draws a form XObject made by an [AppearanceDrawer] inside the padding, clipped, under `/Tx BMC`.
 * zh-CN: 字段外观的 Canvas 回落 (路线图 D62): PDFBox 无法用表单自己的字体为文本, 下拉或列表字段生成外观时 (值含字体无法编码的字符,
 * `/DA` 字体缺失), 该字段的每个控件得到新的正常外观: 保留控件的背景与边框 (`/MK /BG`, `/MK /BC`, `/BS /W`), 在内边距之内裁剪并于
 * `/Tx BMC` 中绘制由 [AppearanceDrawer] 生成的表单 XObject.
 */
internal object FieldAppearances {

    /** One widget to draw: the widget, its request and the `/MK /R` rotation. zh-CN: 一个要绘制的控件: 控件, 请求与 `/MK /R` 旋转. */
    class Target(val widget: PDAnnotationWidget, val request: AppearanceRequest, val rotation: Int, val lineWidth: Double)

    /** Highlight of selected list rows, as the PDFBox list appearance. zh-CN: 列表选中行的高亮色, 与 PDFBox 的列表外观相同. */
    const val HIGHLIGHT_COLOR = 0xFF99C1D7.toInt()

    private val OPERATOR_GRAY = setOf("g")

    /**
     * The targets of every widget of [entry] (a text, combo or list field), from its current value.
     * zh-CN: [entry] (文本, 下拉或列表字段) 每个控件的绘制目标, 按其当前值生成.
     */
    fun targets(entry: FormFields.Entry, form: PDAcroForm): List<Target> {
        val dictionary = entry.dictionary
        val value = FormFields.inherited(dictionary, COSName.V)
        var text: String? = null
        var options: List<String> = emptyList()
        var selected: Set<Int> = emptySet()
        var topIndex = 0
        when (entry.type) {
            PdfContract.FORM_FIELD_TEXT -> {
                val raw = FormFields.text(value) ?: ""
                text = when {
                    entry.flag(FormFields.FLAG_PASSWORD) -> "*".repeat(raw.codePointCount(0, raw.length))
                    entry.flag(FormFields.FLAG_MULTILINE) -> raw
                    else -> singleLine(raw)
                }
            }
            PdfContract.FORM_FIELD_COMBO -> {
                val chosen = FormFields.strings(value).firstOrNull()
                text = chosen?.let { export -> FormFields.choiceOptions(entry.field as PDChoice).firstOrNull { it.first == export }?.second ?: export }?.let(::singleLine) ?: ""
            }
            PdfContract.FORM_FIELD_LIST -> {
                val pairs = FormFields.choiceOptions(entry.field as PDChoice)
                options = pairs.map { singleLine(it.second) }
                selected = selectedRows(dictionary, pairs.map { it.first }, FormFields.strings(value))
                topIndex = ((dictionary.getDictionaryObject(COSName.TI) as? COSNumber)?.intValue() ?: 0).coerceIn(0, max(0, options.size - 1))
            }
            else -> return emptyList()
        }
        val multiline = entry.type == PdfContract.FORM_FIELD_TEXT && entry.flag(FormFields.FLAG_MULTILINE) && !entry.flag(FormFields.FLAG_PASSWORD)
        val align = ((FormFields.inherited(dictionary, COSName.Q) as? COSNumber)?.intValue() ?: runCatching { form.q }.getOrDefault(0)).coerceIn(0, 2)
        val fieldAppearance = FormFields.text(FormFields.inherited(dictionary, COSName.DA)) ?: runCatching { form.defaultAppearance }.getOrNull() ?: ""
        return runCatching { entry.field.widgets }.getOrDefault(emptyList()).mapNotNull { widget ->
            val rect = runCatching { widget.rectangle }.getOrNull() ?: return@mapNotNull null
            if (!(rect.width > 0f) || !(rect.height > 0f)) return@mapNotNull null
            val rotation = rotation(widget)
            val turned = rotation == 90 || rotation == 270
            val width = (if (turned) rect.height else rect.width).toDouble()
            val height = (if (turned) rect.width else rect.height).toDouble()
            val lineWidth = lineWidth(widget)
            val appearance = FormFields.text(widget.cosObject.getDictionaryObject(COSName.DA)) ?: fieldAppearance
            val (fontSize, color) = parseAppearance(appearance)
            val request = AppearanceRequest(width, height, 2 * max(1.0, lineWidth), text, multiline, options, selected, topIndex, fontSize, color, align)
            Target(widget, request, rotation, lineWidth)
        }
    }

    /** Line breaks of a one-line value become spaces, as PDFBox does. zh-CN: 单行值中的换行变成空格, 与 PDFBox 相同. */
    fun singleLine(text: String): String {
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\r' && index + 1 < text.length && text[index + 1] == '\n' -> {
                    result.append(' ')
                    index++
                }
                char.code in LINE_BREAKS -> result.append(' ')
                else -> result.append(char)
            }
            index++
        }
        return result.toString()
    }

    /** U+000A - U+000D, U+0085, U+2028, U+2029. zh-CN: 换行类字符. */
    private val LINE_BREAKS = setOf(0x0A, 0x0B, 0x0C, 0x0D, 0x85, 0x2028, 0x2029)

    /** Selected rows: `/I` when it is valid, else the positions of the values among [exports]. zh-CN: 选中的行: `/I` 有效时取它, 否则取各值在 [exports] 中的位置. */
    fun selectedRows(dictionary: COSDictionary, exports: List<String>, values: List<String>): Set<Int> {
        val indices = dictionary.getDictionaryObject(COSName.I) as? COSArray
        if (indices != null && indices.size() > 0) {
            val rows = (0 until indices.size()).mapNotNull { (indices.getObject(it) as? COSNumber)?.intValue() }.filter { it in exports.indices }
            if (rows.size == indices.size()) return rows.toSortedSet()
        }
        return values.mapNotNull { value -> exports.indexOf(value).takeIf { it >= 0 } }.toSortedSet()
    }

    /** `/MK /R` as 0, 90, 180 or 270. zh-CN: `/MK /R`, 取 0, 90, 180 或 270. */
    fun rotation(widget: PDAnnotationWidget): Int {
        val characteristics = widget.cosObject.getDictionaryObject(COSName.MK) as? COSDictionary ?: return 0
        val degrees = (characteristics.getDictionaryObject(COSName.R) as? COSNumber)?.intValue() ?: return 0
        return Math.floorMod(degrees, 360).let { if (it % 90 == 0) it else 0 }
    }

    /** `/BS /W`, 1 without a border style. zh-CN: `/BS /W`, 没有边框样式时为 1. */
    fun lineWidth(widget: PDAnnotationWidget): Double {
        val style = widget.cosObject.getDictionaryObject(COSName.BS) as? COSDictionary ?: return 1.0
        return ((style.getDictionaryObject(COSName.W) as? COSNumber)?.floatValue()?.toDouble() ?: 1.0).coerceIn(0.0, 100.0)
    }

    /**
     * The font size (the operand of `Tf`, 0 for automatic) and the fill color (the last `g`, `rg` or `k`, as ARGB) of
     * a default appearance string.
     * zh-CN: 默认外观字符串中的字号 (`Tf` 的操作数, 0 表示自动) 与填充色 (最后一个 `g`, `rg` 或 `k`, 转成 ARGB).
     */
    fun parseAppearance(appearance: String): Pair<Double, Int> {
        val tokens = appearance.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var fontSize = 0.0
        var color = 0xFF000000.toInt()
        tokens.forEachIndexed { index, token ->
            fun operands(count: Int): List<Double>? {
                if (index < count) return null
                val numbers = tokens.subList(index - count, index).map { it.toDoubleOrNull() ?: return null }
                return numbers.map { it.coerceIn(0.0, 1.0) }
            }
            when (token) {
                "Tf" -> fontSize = tokens.getOrNull(index - 1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
                in OPERATOR_GRAY -> operands(1)?.let { (gray) -> color = argb(gray, gray, gray) }
                "rg" -> operands(3)?.let { (r, g, b) -> color = argb(r, g, b) }
                "k" -> operands(4)?.let { (c, m, y, k) -> color = argb((1 - c) * (1 - k), (1 - m) * (1 - k), (1 - y) * (1 - k)) }
            }
        }
        return fontSize to color
    }

    private fun argb(red: Double, green: Double, blue: Double): Int =
        (0xFF shl 24) or ((red * 255).roundToInt() shl 16) or ((green * 255).roundToInt() shl 8) or (blue * 255).roundToInt()

    /**
     * Gives the widget of [target] a normal appearance drawing [drawn] (a form XObject whose box starts at the origin
     * and is `ceil(height)` high, as the drawer makes it): background, border, then the clipped form under `/Tx BMC`.
     * The new appearance dictionary has the normal appearance only; the bounding box is the request box and
     * `/Matrix` turns it by `/MK /R`.
     * zh-CN: 让 [target] 的控件得到绘制 [drawn] 的正常外观 ([drawn] 是绘制器生成的表单 XObject, 方框从原点开始, 高 `ceil(height)`):
     * 先画背景与边框, 再在 `/Tx BMC` 中裁剪绘制该表单. 新外观字典只有正常外观; 边界框就是请求的方框, `/Matrix` 按 `/MK /R` 旋转.
     */
    fun install(document: PDDocument, target: Target, drawn: PDFormXObject) {
        val request = target.request
        val width = request.width
        val height = request.height
        val stream = PDAppearanceStream(document)
        stream.bBox = PDRectangle(0f, 0f, width.toFloat(), height.toFloat())
        when (target.rotation) {
            90 -> stream.setMatrix(Matrix(0f, 1f, -1f, 0f, height.toFloat(), 0f).createAffineTransform())
            180 -> stream.setMatrix(Matrix(-1f, 0f, 0f, -1f, width.toFloat(), height.toFloat()).createAffineTransform())
            270 -> stream.setMatrix(Matrix(0f, -1f, 1f, 0f, 0f, width.toFloat()).createAffineTransform())
        }
        val resources = PDResources()
        val name = resources.add(drawn)
        stream.resources = resources
        val characteristics = target.widget.cosObject.getDictionaryObject(COSName.MK) as? COSDictionary
        val lineWidth = target.lineWidth
        val content = StringBuilder("q\n")
        colorOperator(characteristics?.getDictionaryObject(COSName.BG) as? COSArray, stroke = false)?.let {
            content.append(it).append("\n0 0 ").append(number(width)).append(' ').append(number(height)).append(" re\nf\n")
        }
        val border = colorOperator(characteristics?.getDictionaryObject(COSName.BC) as? COSArray, stroke = true)
        if (border != null && lineWidth > 0) {
            val half = lineWidth / 2
            content.append(border).append('\n').append(number(lineWidth)).append(" w\n")
                .append(number(half)).append(' ').append(number(half)).append(' ')
                .append(number(width - lineWidth)).append(' ').append(number(height - lineWidth)).append(" re\nS\n")
        }
        val inset = max(1.0, lineWidth)
        content.append("/Tx BMC\nq\n")
            .append(number(inset)).append(' ').append(number(inset)).append(' ')
            .append(number(max(0.0, width - 2 * inset))).append(' ').append(number(max(0.0, height - 2 * inset))).append(" re\nW\nn\n")
            .append("1 0 0 1 0 ").append(number(height - ceil(height))).append(" cm\n")
            .append('/').append(name.name).append(" Do\nQ\nEMC\nQ\n")
        stream.cosObject.createOutputStream().use { it.write(content.toString().toByteArray(Charsets.ISO_8859_1)) }
        target.widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
    }

    /** `g` / `rg` / `k` (or the stroking `G` / `RG` / `K`) for a 1, 3 or 4 component color array. zh-CN: 1, 3 或 4 个分量的颜色数组对应的颜色运算符. */
    private fun colorOperator(array: COSArray?, stroke: Boolean): String? {
        if (array == null) return null
        val components = (0 until array.size()).map { (array.getObject(it) as? COSNumber)?.floatValue()?.toDouble() ?: return null }
        val operator = when (components.size) {
            1 -> "g"
            3 -> "rg"
            4 -> "k"
            else -> return null
        }
        return components.joinToString(" ") { number(it.coerceIn(0.0, 1.0)) } + " " + if (stroke) operator.uppercase(Locale.ROOT) else operator
    }

    private fun number(value: Double): String {
        val text = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }

}

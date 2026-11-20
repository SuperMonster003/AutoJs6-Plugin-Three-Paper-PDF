package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.StampItems
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.TextStamp
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * The options of the watermark tool (roadmap P6.12, D68): text or image, one of nine positions, rotation, opacity and
 * tiling up front; color, font size, image width and layer under "Advanced". The defaults are those of the host's
 * script facade ([ToolPayloads]).
 * zh-CN: 水印工具的选项 (路线图 P6.12, D68): 文字或图片, 九个位置之一, 旋转, 不透明度与平铺放在前面; 颜色, 字号, 图片宽度与图层放在
 * "高级" 中. 默认值与宿主脚本门面相同 ([ToolPayloads]).
 * Every number and the colour also take a typed "Custom" value (appendix D Q265 (b), Q290), kept in `saved`.
 * zh-CN: 每个数值与颜色也接受输入的 "自定义" 值 (附录 D Q265 (b), Q290), 保存在 `saved` 中.
 */
internal class WatermarkState(saved: SavedStateHandle? = null) {

    var type by mutableStateOf(PdfContract.STAMP_TEXT)
        private set
    var text by mutableStateOf("")
    var image by mutableStateOf<ToolTarget?>(null)
    var position by mutableStateOf(PdfContract.POSITION_CENTER)

    /** Counterclockwise degrees. zh-CN: 逆时针角度. */
    val rotation = NumberField("watermark.rotation", ROTATION_RANGE, ToolPayloads.WATERMARK_TEXT_ROTATION.toInt(), saved)

    /** Percent. zh-CN: 百分比. */
    val opacity = NumberField("watermark.opacity", OPACITY_RANGE, (ToolPayloads.WATERMARK_OPACITY * PERCENT).roundToInt(), saved)
    var tile by mutableStateOf(false)
    val color = ColorField("watermark.color", ToolPayloads.WATERMARK_COLOR, saved)

    /** Points; null fits half the page width. zh-CN: 点; null 表示适应半个页宽. */
    val fontSize = NumberField("watermark.fontSize", FONT_SIZE_RANGE, null, saved)

    /** Points; null for the natural size, scaled down to fit the page. zh-CN: 点; null 表示原始尺寸, 超出页面时等比缩小. */
    val width = NumberField("watermark.width", WIDTH_RANGE, null, saved)
    var layer by mutableStateOf(PdfContract.LAYER_OVER)
    var advanced by mutableStateOf(false)

    /** Switches between text and image; the rotation goes back to the default of the new type. zh-CN: 在文字与图片之间切换; 旋转恢复为新类型的默认值. */
    fun selectType(value: String) {
        if (type == value) return
        type = value
        rotation.choose((if (value == PdfContract.STAMP_IMAGE) ToolPayloads.WATERMARK_IMAGE_ROTATION else ToolPayloads.WATERMARK_TEXT_ROTATION).toInt())
    }

    fun textTooLong(): Boolean = type == PdfContract.STAMP_TEXT && text.length > PdfContract.MAX_STAMP_TEXT_LENGTH

    /** A typed value under "Advanced" of the current type is not valid. zh-CN: 当前类型在 "高级" 中输入的值无效. */
    fun advancedInvalid(): Boolean = if (type == PdfContract.STAMP_IMAGE) width.invalid else color.invalid || fontSize.invalid

    /** A typed value the current type uses is not valid. zh-CN: 当前类型用到的输入值无效. */
    fun invalid(): Boolean = rotation.invalid || opacity.invalid || advancedInvalid()

    /**
     * The options for [pages], or null without text or image or while a typed value is not valid.
     * zh-CN: [pages] 对应的选项; 没有文字或图片, 或输入的值无效时为 null.
     */
    fun options(pages: String?): ToolOptions.Watermark? {
        val isImage = type == PdfContract.STAMP_IMAGE
        val picture = image
        if (isImage && picture == null) return null
        if (!isImage && (text.isBlank() || textTooLong())) return null
        if (invalid()) return null
        return ToolOptions.Watermark(
            type = type,
            text = if (isImage) "" else text,
            image = if (isImage) picture else null,
            position = position,
            rotation = checkNotNull(rotation.value()),
            opacity = BigDecimal.valueOf(checkNotNull(opacity.value())).movePointLeft(PERCENT_DIGITS).toDouble(),
            tile = tile,
            color = if (isImage) null else color.value(),
            fontSize = if (isImage) null else fontSize.value(),
            width = if (isImage) width.value() else null,
            layer = layer,
            pages = pages,
        )
    }

    companion object {

        val ROTATIONS = listOf(-45, 0, 45, 90)

        val OPACITIES = listOf(10, 20, 30, 50, 70, 100)

        val FONT_SIZES = listOf(24, 36, 48, 72)

        val WIDTHS = listOf(72, 144, 216, 288)

        /** The engine's rotation limit (`StampItems`). zh-CN: 引擎的旋转上限 (`StampItems`). */
        val ROTATION_RANGE = NumberRange(-StampItems.MAX_ROTATION, StampItems.MAX_ROTATION)

        /** Opacity 0 - 1 of the engine, in percent. zh-CN: 引擎的不透明度 0 - 1, 以百分比表示. */
        val OPACITY_RANGE = NumberRange(0.0, PERCENT)

        /** Greater than 0, at most the engine's `TextStamp.MAX_FONT_SIZE`. zh-CN: 大于 0, 不超过引擎的 `TextStamp.MAX_FONT_SIZE`. */
        val FONT_SIZE_RANGE = NumberRange(0.0, TextStamp.MAX_FONT_SIZE, aboveMinimum = true)

        /** Greater than 0, at most the engine's `StampItems.MAX_LENGTH`. zh-CN: 大于 0, 不超过引擎的 `StampItems.MAX_LENGTH`. */
        val WIDTH_RANGE = NumberRange(0.0, StampItems.MAX_LENGTH, aboveMinimum = true)

        /** Gray, red, blue, black. zh-CN: 灰, 红, 蓝, 黑. */
        val COLORS = listOf(ToolPayloads.WATERMARK_COLOR, "#D32F2F", "#1976D2", "#000000")

        val POSITIONS = listOf(
            PdfContract.POSITION_TOP_LEFT, PdfContract.POSITION_TOP_CENTER, PdfContract.POSITION_TOP_RIGHT,
            PdfContract.POSITION_CENTER_LEFT, PdfContract.POSITION_CENTER, PdfContract.POSITION_CENTER_RIGHT,
            PdfContract.POSITION_BOTTOM_LEFT, PdfContract.POSITION_BOTTOM_CENTER, PdfContract.POSITION_BOTTOM_RIGHT,
        )

        private const val PERCENT = 100.0

        /** Percent to a fraction moves the point two places. zh-CN: 百分比换成小数时小数点左移两位. */
        private const val PERCENT_DIGITS = 2

    }

}

/**
 * The options of the page number tool (roadmap P6.12, D68): the format, its place on the top or bottom edge and the
 * number of the first selected page up front; the header and footer texts and the font size under "Advanced".
 * zh-CN: 页码工具的选项 (路线图 P6.12, D68): 格式, 在上下边缘的位置与所选第一页的编号放在前面; 页眉页脚文字与字号放在 "高级" 中.
 * The font size also takes a typed "Custom" value (appendix D Q265 (b)). zh-CN: 字号也接受输入的 "自定义" 值 (附录 D Q265 (b)).
 */
internal class PageNumbersState(saved: SavedStateHandle? = null) {

    var numbered by mutableStateOf(true)
    var format by mutableStateOf(ToolPayloads.PAGE_NUMBER_FORMAT)
    var position by mutableStateOf(ToolPayloads.PAGE_NUMBER_POSITION)
    var start by mutableStateOf("1")
    var header by mutableStateOf(ToolOptions.Slots())
    var footer by mutableStateOf(ToolOptions.Slots())
    val fontSize = NumberField("numbers.fontSize", WatermarkState.FONT_SIZE_RANGE, ToolPayloads.STAMP_FONT_SIZE.toInt(), saved)
    var advanced by mutableStateOf(false)

    /** The typed font size under "Advanced" is not valid. zh-CN: "高级" 中输入的字号无效. */
    fun advancedInvalid(): Boolean = fontSize.invalid

    fun startInvalid(): Boolean = numbered && startNumber(start) == null

    /** A header or footer text sits where the page number goes. zh-CN: 页码所在位置还有页眉或页脚文字. */
    fun overlaps(): Boolean = numbered && slotAt(position).isNotBlank()

    fun tooLong(): Boolean = listOf(format, header.left, header.center, header.right, footer.left, footer.center, footer.right)
        .any { it.length > PdfContract.MAX_STAMP_TEXT_LENGTH }

    /** The options for [pages], or null while there is nothing to add or a value is not valid. zh-CN: [pages] 对应的选项; 没有要添加的内容或有无效值时为 null. */
    fun options(pages: String?): ToolOptions.PageNumbers? {
        if (tooLong() || fontSize.invalid) return null
        val first = if (numbered) {
            if (format.isBlank()) return null
            startNumber(start) ?: return null
        } else {
            1
        }
        if (!numbered && header.blank && footer.blank) return null
        return ToolOptions.PageNumbers(
            format = format.takeIf { numbered },
            position = position,
            start = first,
            header = header,
            footer = footer,
            fontSize = checkNotNull(fontSize.value()),
            pages = pages,
        )
    }

    private fun slotAt(place: String): String = when (place) {
        PdfContract.POSITION_TOP_LEFT -> header.left
        PdfContract.POSITION_TOP_CENTER -> header.center
        PdfContract.POSITION_TOP_RIGHT -> header.right
        PdfContract.POSITION_BOTTOM_LEFT -> footer.left
        PdfContract.POSITION_BOTTOM_CENTER -> footer.center
        PdfContract.POSITION_BOTTOM_RIGHT -> footer.right
        else -> ""
    }

    companion object {

        /** The edge positions a page number can take. zh-CN: 页码可以使用的边缘位置. */
        val POSITIONS = listOf(
            PdfContract.POSITION_TOP_LEFT, PdfContract.POSITION_TOP_CENTER, PdfContract.POSITION_TOP_RIGHT,
            PdfContract.POSITION_BOTTOM_LEFT, PdfContract.POSITION_BOTTOM_CENTER, PdfContract.POSITION_BOTTOM_RIGHT,
        )

        /** Ready-made formats; the field takes any other. zh-CN: 预设格式; 输入框可以填写其他格式. */
        val FORMATS = listOf("{page}", ToolPayloads.PAGE_NUMBER_FORMAT, "- {page} -")

        val FONT_SIZES = listOf(8, 10, 12, 14)

        /** The largest start number. zh-CN: 最大的起始号. */
        const val MAX_START = 1_000_000

        /** [text] as a start number from 0 to [MAX_START], or null. zh-CN: 把 [text] 读作 0 到 [MAX_START] 的起始号, 否则为 null. */
        fun startNumber(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_START }

        /** What a ready-made [format] shows on page 1 of 12. zh-CN: 预设 [format] 在 12 页中第 1 页的显示. */
        fun sample(format: String): String = format.replace("{page}", "1").replace("{total}", "12")

    }

}

/**
 * The options of the compress tool (roadmap P6.12, D55, D68): one of three presets; its numbers, removing metadata
 * and object streams under "Advanced".
 * zh-CN: 压缩工具的选项 (路线图 P6.12, D55, D68): 三个预设之一; 其数值, 删除元数据与对象流放在 "高级" 中.
 * Both numbers also take a typed "Custom" whole number (appendix D Q265 (b)). zh-CN: 两个数值也接受输入的 "自定义" 整数 (附录 D Q265 (b)).
 */
internal class CompressState(private val saved: SavedStateHandle? = null) {

    var preset by mutableStateOf(saved?.get<String>(KEY_PRESET) ?: PdfContract.DEFAULT_COMPRESS_PRESET)
        private set
    val imageDpi = NumberField("compress.imageDpi", DPI_RANGE, numbersOf(PdfContract.DEFAULT_COMPRESS_PRESET).imageDpi, saved)
    val imageQuality = NumberField("compress.imageQuality", QUALITY_RANGE, numbersOf(PdfContract.DEFAULT_COMPRESS_PRESET).imageQuality, saved)
    var removeMetadata by mutableStateOf(false)
    var objectStreams by mutableStateOf(PdfContract.DEFAULT_OBJECT_STREAMS)
    var advanced by mutableStateOf(false)

    /** Picks [name] and its numbers. zh-CN: 选择 [name] 及其数值. */
    fun selectPreset(name: String) {
        val chosen = numbersOf(name)
        preset = chosen.name
        saved?.set(KEY_PRESET, chosen.name)
        imageDpi.choose(chosen.imageDpi)
        imageQuality.choose(chosen.imageQuality)
    }

    fun advancedInvalid(): Boolean = imageDpi.invalid || imageQuality.invalid

    /** The options, or null while a typed number is not valid. zh-CN: 选项; 输入的数值无效时为 null. */
    fun options(): ToolOptions.Compress? {
        if (advancedInvalid()) return null
        val dpi = checkNotNull(imageDpi.value()).toInt()
        val quality = checkNotNull(imageQuality.value()).toInt()
        return ToolOptions.Compress(preset, dpi, quality, removeMetadata, objectStreams)
    }

    companion object {

        val PRESETS = listOf(PdfContract.COMPRESS_PRESET_LOW, PdfContract.COMPRESS_PRESET_MEDIUM, PdfContract.COMPRESS_PRESET_HIGH)

        val DPI_CHOICES = listOf(72, 96, 150, 220, 300)

        val QUALITY_CHOICES = listOf(40, 60, 75, 85, 95)

        /** The engine's resolution range (`PdfContract`). zh-CN: 引擎的分辨率范围 (`PdfContract`). */
        val DPI_RANGE = NumberRange(PdfContract.MIN_IMAGE_DPI.toDouble(), PdfContract.MAX_IMAGE_DPI.toDouble(), integer = true)

        /** The engine's JPEG quality range (`PdfContract`). zh-CN: 引擎的 JPEG 质量范围 (`PdfContract`). */
        val QUALITY_RANGE = NumberRange(PdfContract.MIN_QUALITY.toDouble(), PdfContract.MAX_QUALITY.toDouble(), integer = true)

        private const val KEY_PRESET = "compress.preset"

        /**
         * The numbers of preset [name], or of the default preset when the name is unknown.
         * zh-CN: 预设 [name] 的数值; 名称未知时取默认预设的数值.
         */
        fun numbersOf(name: String): PdfContract.CompressPreset =
            PdfContract.compressPreset(name) ?: checkNotNull(PdfContract.compressPreset(PdfContract.DEFAULT_COMPRESS_PRESET))

    }

}

/**
 * One option of a radio button, combo box or list box: the exported [value] and the [label] shown.
 * zh-CN: 单选按钮, 组合框或列表框的一个选项: 导出值 [value] 与显示的 [label].
 */
internal data class FormChoice(val value: String, val label: String)

/**
 * One terminal form field as `getFormFields` lists it (roadmap P6.4, D47): [value] is a string or null (text, radio,
 * combo), a boolean (checkbox) or a list of strings (list); [page] counts from 1, 0 when no widget is on a page.
 * zh-CN: `getFormFields` 列出的一个终端表单字段 (路线图 P6.4, D47): [value] 为字符串或 null (文本, 单选, 组合框), 布尔值 (勾选框) 或
 * 字符串列表 (列表框); [page] 从 1 起, 没有控件位于页面上时为 0.
 */
internal data class FormFieldItem(
    val name: String,
    val type: String,
    val value: Any?,
    val label: String?,
    val choices: List<FormChoice>,
    val readOnly: Boolean,
    val required: Boolean,
    val multiline: Boolean,
    val password: Boolean,
    val maxLength: Int?,
    val multiSelect: Boolean,
    val signed: Boolean,
    val page: Int,
) {

    /** The tool can write it: a text, checkbox, radio, combo or list field that is not read-only. zh-CN: 工具可以写入: 非只读的文本, 勾选框, 单选, 组合框或列表框字段. */
    val fillable get() = !readOnly && type in FILLABLE_TYPES

    /** The label shown: the field's tooltip (`/TU`) or its full name. zh-CN: 显示的名称: 字段的提示 (`/TU`) 或全名. */
    val title get() = label ?: name

    companion object {

        private val FILLABLE_TYPES = setOf(
            PdfContract.FORM_FIELD_TEXT, PdfContract.FORM_FIELD_CHECKBOX, PdfContract.FORM_FIELD_RADIO,
            PdfContract.FORM_FIELD_COMBO, PdfContract.FORM_FIELD_LIST,
        )

        /** The fields of one `getFormFields` page ([items] is its JSON array). zh-CN: 一页 `getFormFields` 应答中的字段 ([items] 为其 JSON 数组). */
        fun parse(items: String): List<FormFieldItem> {
            val array = JSONArray(items)
            return (0 until array.length()).map { index -> of(array.getJSONObject(index)) }
        }

        private fun of(item: JSONObject): FormFieldItem {
            val type = item.optString(PdfContract.FIELD_TYPE)
            val choices = item.optJSONArray(PdfContract.FIELD_OPTIONS)?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { option ->
                    val value = option.optString(PdfContract.FIELD_VALUE)
                    FormChoice(value, stringOrNull(option, PdfContract.FIELD_LABEL)?.takeIf { it.isNotEmpty() } ?: value)
                }
            }.orEmpty()
            return FormFieldItem(
                name = item.optString(PdfContract.FIELD_NAME),
                type = type,
                value = valueOf(type, item),
                label = stringOrNull(item, PdfContract.FIELD_LABEL)?.takeIf { it.isNotEmpty() },
                choices = choices,
                readOnly = item.optBoolean(PdfContract.FIELD_READ_ONLY),
                required = item.optBoolean(PdfContract.FIELD_REQUIRED),
                multiline = item.optBoolean(PdfContract.FIELD_MULTILINE),
                password = item.optBoolean(PdfContract.FIELD_PASSWORD),
                maxLength = item.optInt(PdfContract.FIELD_MAX_LENGTH, 0).takeIf { it > 0 },
                multiSelect = item.optBoolean(PdfContract.FIELD_MULTI_SELECT),
                signed = item.optBoolean(PdfContract.FIELD_SIGNED),
                page = item.optInt(PdfContract.FIELD_PAGE, 0),
            )
        }

        private fun valueOf(type: String, item: JSONObject): Any? = when (type) {
            PdfContract.FORM_FIELD_CHECKBOX -> item.optBoolean(PdfContract.FIELD_VALUE)
            PdfContract.FORM_FIELD_LIST -> item.optJSONArray(PdfContract.FIELD_VALUE)?.let { array ->
                (0 until array.length()).map { array.optString(it) }
            } ?: stringOrNull(item, PdfContract.FIELD_VALUE)?.let { listOf(it) }.orEmpty()
            else -> stringOrNull(item, PdfContract.FIELD_VALUE)
        }

        private fun stringOrNull(item: JSONObject, key: String): String? = if (item.isNull(key)) null else item.optString(key)

    }

}

/**
 * Comparing form values the way the form shows them: null and an empty string are the same text, lists compare
 * without order.
 * zh-CN: 按表单显示的方式比较字段值: null 与空字符串是相同的文本, 列表比较时不计顺序.
 */
internal object FormValues {

    fun same(a: Any?, b: Any?): Boolean = when {
        a is Boolean || b is Boolean -> (a as? Boolean ?: false) == (b as? Boolean ?: false)
        a is List<*> || b is List<*> -> (a as? List<*>).orEmpty().map { it.toString() }.sorted() == (b as? List<*>).orEmpty().map { it.toString() }.sorted()
        else -> (a?.toString() ?: "") == (b?.toString() ?: "")
    }

    /** No text, no choice, no list item (a checkbox is never empty). zh-CN: 没有文字, 没有选择, 没有列表项 (勾选框永远不算空). */
    fun empty(value: Any?): Boolean = when (value) {
        null -> true
        is Boolean -> false
        is List<*> -> value.isEmpty()
        else -> value.toString().isEmpty()
    }

}

/**
 * The fill-form tool (roadmap P6.12, D47): the fields read from the document, the values changed on screen (only those
 * that differ from the document) and flattening. Fields are grouped by page and each group can be folded.
 * zh-CN: 表单填写工具 (路线图 P6.12, D47): 从文档读取的字段, 界面上修改过的值 (只保留与文档不同的) 与扁平化. 字段按页分组, 每组可折叠.
 */
internal class FillFormState {

    var loading by mutableStateOf(false)
        internal set

    /** Why the fields cannot be read, a code of `PdfErrorCodes`. zh-CN: 无法读取字段的原因, `PdfErrorCodes` 中的错误码. */
    var error by mutableStateOf<String?>(null)
        internal set

    /** `static`, `dynamic` or null. zh-CN: `static`, `dynamic` 或 null. */
    var xfa by mutableStateOf<String?>(null)
        internal set

    /** More fields exist than were read. zh-CN: 字段多于已读取的数量. */
    var truncated by mutableStateOf(false)
        internal set

    val fields = mutableStateListOf<FormFieldItem>()

    /** Changed values by field name. zh-CN: 按字段名记录的修改值. */
    val edits = mutableStateMapOf<String, Any?>()

    /** Folded page groups. zh-CN: 已折叠的页面分组. */
    val folded = mutableStateMapOf<Int, Boolean>()

    var flatten by mutableStateOf(false)

    fun valueOf(field: FormFieldItem): Any? = if (edits.containsKey(field.name)) edits[field.name] else field.value

    fun set(field: FormFieldItem, value: Any?) {
        if (!field.fillable) return
        if (FormValues.same(value, field.value)) edits.remove(field.name) else edits[field.name] = value
    }

    /** The fields by page, pages in order and fields without a page last. zh-CN: 按页分组的字段, 页按顺序, 没有页面的字段在最后. */
    fun groups(): List<Pair<Int, List<FormFieldItem>>> = fields.groupBy { it.page }.toList().sortedBy { (page, _) -> if (page == 0) Int.MAX_VALUE else page }

    /** Required fillable fields still empty. zh-CN: 仍为空的必填可写字段数. */
    fun missingRequired(): Int = fields.count { it.fillable && it.required && FormValues.empty(valueOf(it)) }

    fun dynamicXfa(): Boolean = xfa == PdfContract.XFA_DYNAMIC

    fun options(): ToolOptions.FillForm? {
        if (loading || error != null || dynamicXfa() || fields.isEmpty()) return null
        if (edits.isEmpty() && !flatten) return null
        return ToolOptions.FillForm(edits.toMap(), flatten)
    }

    /** Back to nothing read. zh-CN: 恢复为未读取的状态. */
    fun reset() {
        loading = false
        error = null
        xfa = null
        truncated = false
        fields.clear()
        edits.clear()
        folded.clear()
        flatten = false
    }

    /** Shows [found]; with many fields every page group but the first starts folded. zh-CN: 显示 [found]; 字段很多时除第一组外都先折叠. */
    fun show(found: List<FormFieldItem>, more: Boolean, xfaKind: String?) {
        reset()
        fields += found
        truncated = more
        xfa = xfaKind
        if (found.size > FOLD_ABOVE) groups().drop(1).forEach { (page, _) -> folded[page] = true }
    }

    companion object {

        /** At most this many fields are read. zh-CN: 至多读取的字段数. */
        const val MAX_FIELDS = 5000

        private const val FOLD_ABOVE = 40

    }

}

/** One outline entry: [level] from 0, [page] from 1 or 0 without a page. zh-CN: 一个书签条目: [level] 从 0 起, [page] 从 1 起, 没有页面时为 0. */
internal data class OutlineItem(val title: String, val page: Int, val level: Int) {

    companion object {

        /** The entries of a `getOutline` answer ([items] is its JSON array). zh-CN: `getOutline` 应答中的条目 ([items] 为其 JSON 数组). */
        fun parse(items: String): List<OutlineItem> {
            val array = JSONArray(items)
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
                OutlineItem(it.optString(PdfContract.FIELD_TITLE), it.optInt(PdfContract.FIELD_PAGE, 0), it.optInt(PdfContract.FIELD_LEVEL, 0))
            }
        }

    }

}

/**
 * The document properties tool (roadmap P6.12, D52): the file facts, the editable title, author, subject and keywords
 * (saved as a new file through `setInfo`), and the outline, read-only with one switch that removes it (editing it is
 * left to scripts).
 * zh-CN: 文档属性工具 (路线图 P6.12, D52): 文件信息, 可编辑的标题, 作者, 主题与关键词 (经 `setInfo` 另存为新文件), 以及只读的书签,
 * 只有一个清除全部书签的开关 (编辑书签交给脚本).
 */
internal class PropertiesState {

    var loading by mutableStateOf(false)
        internal set

    var error by mutableStateOf<String?>(null)
        internal set

    /** The file facts as `getInfo` answers them (name, size, pages, version, ...). zh-CN: `getInfo` 应答的文件信息 (名称, 大小, 页数, 版本等). */
    var facts by mutableStateOf<Map<String, Any?>>(emptyMap())
        internal set

    /** The values read, by `setInfo` key. zh-CN: 读取到的值, 以 `setInfo` 的键索引. */
    var original by mutableStateOf<Map<String, String>>(emptyMap())
        internal set

    /** The values on screen, by `setInfo` key. zh-CN: 界面上的值, 以 `setInfo` 的键索引. */
    val edited = mutableStateMapOf<String, String>()

    val outline = mutableStateListOf<OutlineItem>()

    var outlineMore by mutableStateOf(false)
        internal set

    var clearOutline by mutableStateOf(false)

    fun changes(): Map<String, String?> = changes(original, edited)

    fun tooLong(): Boolean = edited.values.any { it.length > PdfContract.MAX_INFO_VALUE_LENGTH }

    fun options(): ToolOptions.Properties? {
        if (loading || error != null || tooLong()) return null
        val changed = changes()
        if (changed.isEmpty() && !clearOutline) return null
        return ToolOptions.Properties(changed, clearOutline)
    }

    fun reset() {
        loading = false
        error = null
        facts = emptyMap()
        original = emptyMap()
        edited.clear()
        outline.clear()
        outlineMore = false
        clearOutline = false
    }

    fun show(info: Map<String, Any?>, entries: List<OutlineItem>, more: Boolean) {
        reset()
        facts = info
        original = EDITABLE.associateWith { (info[it] as? String).orEmpty() }
        edited.putAll(original)
        outline += entries
        outlineMore = more
    }

    companion object {

        /** The editable `setInfo` keys, in screen order. zh-CN: 可编辑的 `setInfo` 键, 按界面顺序. */
        val EDITABLE = listOf(PdfContract.FIELD_TITLE, PdfContract.FIELD_AUTHOR, PdfContract.FIELD_SUBJECT, PdfContract.FIELD_KEYWORDS)

        /** At most this many outline entries are shown. zh-CN: 至多显示的书签条目数. */
        const val MAX_OUTLINE_SHOWN = 500

        private val ISO_DATE = Regex("""(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}(?::\d{2})?)(.*)""")

        /**
         * A `getInfo` date (`yyyy-MM-ddTHH:mm:ss+hh:mm`) as `yyyy-MM-dd HH:mm:ss +hh:mm` (`Z` as `UTC`); other text as it is.
         * zh-CN: 把 `getInfo` 的日期 (`yyyy-MM-ddTHH:mm:ss+hh:mm`) 显示为 `yyyy-MM-dd HH:mm:ss +hh:mm` (`Z` 显示为 `UTC`); 其他文字原样显示.
         */
        fun displayDate(iso: String): String {
            val match = ISO_DATE.matchEntire(iso.trim()) ?: return iso
            val (day, time, zone) = match.destructured
            val shownZone = when (zone) {
                "" -> ""
                "Z" -> " UTC"
                else -> " $zone"
            }
            return "$day $time$shownZone"
        }

        /**
         * The keys of [edited] that differ from [original]: the new text, or null (remove the key) for a blank one.
         * zh-CN: [edited] 中与 [original] 不同的键: 新文字, 空白时为 null (删除该键).
         */
        fun changes(original: Map<String, String>, edited: Map<String, String>): Map<String, String?> {
            val changed = LinkedHashMap<String, String?>()
            EDITABLE.forEach { key ->
                val now = edited[key] ?: return@forEach
                val before = original[key].orEmpty()
                if (now == before || (now.isBlank() && before.isBlank())) return@forEach
                changed[key] = now.takeIf { it.isNotBlank() }
            }
            return changed
        }

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes

/**
 * Where a stamp item goes on the displayed page (protocol "Stamp items"): one of the nine named spots of
 * `PdfContract.POSITIONS`, inset by the item's margin, or the top left corner of the item's box at a point.
 * zh-CN: 印章条目在显示页面上的位置 (协议 "Stamp items" 一节): `PdfContract.POSITIONS` 的九个命名位置之一 (按条目的 margin 内缩),
 * 或条目方框左上角所在的点.
 */
internal sealed interface StampPosition {

    /** Column 0 / 1 / 2 (left, centre, right) and row 0 / 1 / 2 (top, centre, bottom). zh-CN: 列 0 / 1 / 2 (左, 中, 右) 与行 0 / 1 / 2 (上, 中, 下). */
    data class Named(val column: Int, val row: Int) : StampPosition

    /** The top left corner of the box at display point ([x], [y]). zh-CN: 方框左上角位于显示坐标 ([x], [y]). */
    data class At(val x: Double, val y: Double) : StampPosition

    companion object {

        /** The named spot of [name], or null. zh-CN: 名称 [name] 对应的命名位置, 不认识时为 null. */
        fun named(name: String): Named? {
            val index = POSITION_ORDER.indexOf(name)
            return if (index < 0) null else Named(index % 3, index / 3)
        }

        /** `PdfContract.POSITIONS` from the top left, row by row. zh-CN: `PdfContract.POSITIONS`, 自左上起逐行. */
        private val POSITION_ORDER = listOf(
            PdfContract.POSITION_TOP_LEFT, PdfContract.POSITION_TOP_CENTER, PdfContract.POSITION_TOP_RIGHT,
            PdfContract.POSITION_CENTER_LEFT, PdfContract.POSITION_CENTER, PdfContract.POSITION_CENTER_RIGHT,
            PdfContract.POSITION_BOTTOM_LEFT, PdfContract.POSITION_BOTTOM_CENTER, PdfContract.POSITION_BOTTOM_RIGHT,
        )

    }

}

/**
 * One checked item of a `stamp` payload (roadmap D61, protocol "Stamp items"). [text] holds the placeholders still;
 * [pageOffset] is added to the physical page number `{page}` shows (roadmap Q211 (a)); [fontSize] null fits the text to
 * half the displayed page width; [image] is an index of the image inputs. Sizes and positions are points of the
 * displayed page.
 * zh-CN: `stamp` payload 中一个已检查的条目 (路线图 D61, 协议 "Stamp items" 一节). [text] 仍含占位符; [pageOffset] 加到 `{page}`
 * 显示的物理页码上 (路线图 Q211 (a)); [fontSize] 为 null 时按显示页宽的一半适配; [image] 是图片输入的序号. 尺寸与位置都以显示页面的点计.
 */
internal data class StampItem(
    val isText: Boolean,
    val text: String,
    val pageOffset: Long,
    val image: Int,
    val pages: String?,
    val position: StampPosition,
    val offsetX: Double,
    val offsetY: Double,
    val rotation: Double,
    val opacity: Double,
    val fontSize: Double?,
    val color: Int,
    val bold: Boolean,
    val italic: Boolean,
    val tile: Boolean,
    val gapX: Double,
    val gapY: Double,
    val layer: StampLayer,
    val width: Double?,
    val height: Double?,
    val scale: Double?,
    val margin: Double,
) {

    /** Line alignment: by the column of a named position, left for a point. zh-CN: 行对齐: 命名位置按其所在列, 坐标位置左对齐. */
    val align: StampAlign
        get() = when ((position as? StampPosition.Named)?.column) {
            1 -> StampAlign.CENTER
            2 -> StampAlign.END
            else -> StampAlign.START
        }

}

/**
 * Reads the `items` of a `stamp` payload (protocol "Stamp items", "Version 2 ceilings"): 1 to `MAX_STAMP_ITEMS` items
 * (more is `LIMIT_EXCEEDED`, roadmap Q185 (a)); an unknown `type`, `layer` or position name, a missing `text` or
 * `image`, an `image` outside `0..imageCount-1`, a text longer than `MAX_STAMP_TEXT_LENGTH` (as the host checks it, and
 * as `setInfo` treats `MAX_INFO_VALUE_LENGTH`), a colour that is not `#RRGGBB` / `#AARRGGBB` and a number out of range
 * (`pageOffset` too, checked on image items as well) are `INVALID_ARGUMENT` naming the field path. Fields the reader
 * does not know are ignored.
 * zh-CN: 读取 `stamp` payload 的 `items` (协议 "Stamp items" 与 "Version 2 ceilings" 两节): 1 至 `MAX_STAMP_ITEMS` 个条目 (超过为
 * `LIMIT_EXCEEDED`, 路线图 Q185 (a)); 不认识的 `type`, `layer` 或位置名称, 缺少 `text` 或 `image`, `image` 不在 `0..imageCount-1`,
 * 文字超过 `MAX_STAMP_TEXT_LENGTH` (与宿主的检查相同, 也与 `setInfo` 对 `MAX_INFO_VALUE_LENGTH` 的处理一致), 颜色不是 `#RRGGBB` /
 * `#AARRGGBB`, 以及数值越界 (`pageOffset` 也是, 图片条目同样检查), 都是带字段路径的 `INVALID_ARGUMENT`. 不认识的字段忽略.
 */
internal object StampItems {

    /** Largest offset, gap, margin and image size in points (a 200-inch page and more). zh-CN: 偏移, 间距, 边距与图片尺寸的上限 (点). */
    const val MAX_LENGTH = 14_400.0

    /** Largest image scale. zh-CN: 图片缩放的上限. */
    const val MAX_SCALE = 100.0

    /** Largest rotation either way, in degrees (100 turns). zh-CN: 两个方向旋转角度的上限 (度, 100 圈). */
    const val MAX_ROTATION = 36_000.0

    fun parse(payload: PayloadObject, imageCount: Int): List<StampItem> {
        val items = payload.array(PdfContract.FIELD_ITEMS)
        if (items.size == 0) throw PdfFailure.invalid("${items.path} is empty; give 1 - ${PdfContract.MAX_STAMP_ITEMS} items")
        if (items.size > PdfContract.MAX_STAMP_ITEMS) {
            throw PdfFailure(PdfErrorCodes.LIMIT_EXCEEDED, "${items.path} has ${items.size} items, at most ${PdfContract.MAX_STAMP_ITEMS}")
        }
        return items.objects().map { item(it, imageCount) }
    }

    private fun item(item: PayloadObject, imageCount: Int): StampItem {
        val type = item.string(PdfContract.FIELD_TYPE)
        if (type !in PdfContract.STAMP_TYPES) throw PdfFailure.invalid("${item.pathOf(PdfContract.FIELD_TYPE)} must be one of ${PdfContract.STAMP_TYPES.joinToString(", ")}, not \"$type\"")
        val isText = type == PdfContract.STAMP_TEXT
        val text = if (isText) item.string(PdfContract.FIELD_TEXT) else ""
        if (text.length > PdfContract.MAX_STAMP_TEXT_LENGTH) {
            throw PdfFailure.invalid("${item.pathOf(PdfContract.FIELD_TEXT)} has ${text.length} characters, at most ${PdfContract.MAX_STAMP_TEXT_LENGTH}")
        }
        val image = if (isText) -1 else {
            val index = item.long(PdfContract.FIELD_IMAGE)
            if (index !in 0 until imageCount) {
                throw PdfFailure.invalid("${item.pathOf(PdfContract.FIELD_IMAGE)} is $index, but the request carries ${imageCount} images (indexes from 0)")
            }
            index.toInt()
        }
        val layer = when (val name = item.optString(PdfContract.FIELD_LAYER) ?: PdfContract.LAYER_OVER) {
            PdfContract.LAYER_OVER -> StampLayer.OVER
            PdfContract.LAYER_UNDER -> StampLayer.UNDER
            else -> throw PdfFailure.invalid("${item.pathOf(PdfContract.FIELD_LAYER)} must be ${PdfContract.LAYER_OVER} or ${PdfContract.LAYER_UNDER}, not \"$name\"")
        }
        val length = 0.0..MAX_LENGTH
        val signed = -MAX_LENGTH..MAX_LENGTH
        val offsets = -PdfContract.MAX_PAGE_OFFSET.toLong()..PdfContract.MAX_PAGE_OFFSET.toLong()
        return StampItem(
            isText = isText,
            text = text,
            pageOffset = item.optLong(PdfContract.FIELD_PAGE_OFFSET, offsets) ?: 0L,
            image = image,
            pages = item.optString(PdfContract.FIELD_PAGES),
            position = position(item),
            offsetX = item.optDouble(PdfContract.FIELD_OFFSET_X, signed) ?: 0.0,
            offsetY = item.optDouble(PdfContract.FIELD_OFFSET_Y, signed) ?: 0.0,
            rotation = item.optDouble(PdfContract.FIELD_ROTATION, -MAX_ROTATION..MAX_ROTATION) ?: 0.0,
            opacity = item.optDouble(PdfContract.FIELD_OPACITY, 0.0..1.0) ?: 1.0,
            fontSize = positive(item, PdfContract.FIELD_FONT_SIZE, TextStamp.MAX_FONT_SIZE),
            color = item.optString(PdfContract.FIELD_COLOR)?.let { color(it, item.pathOf(PdfContract.FIELD_COLOR)) } ?: DEFAULT_COLOR,
            bold = item.optBoolean(PdfContract.FIELD_BOLD) ?: false,
            italic = item.optBoolean(PdfContract.FIELD_ITALIC) ?: false,
            tile = item.optBoolean(PdfContract.FIELD_TILE) ?: false,
            gapX = item.optDouble(PdfContract.FIELD_GAP_X, length) ?: PdfContract.DEFAULT_STAMP_TILE_GAP.toDouble(),
            gapY = item.optDouble(PdfContract.FIELD_GAP_Y, length) ?: PdfContract.DEFAULT_STAMP_TILE_GAP.toDouble(),
            layer = layer,
            width = positive(item, PdfContract.FIELD_WIDTH, MAX_LENGTH),
            height = positive(item, PdfContract.FIELD_HEIGHT, MAX_LENGTH),
            scale = positive(item, PdfContract.FIELD_SCALE, MAX_SCALE),
            margin = item.optDouble(PdfContract.FIELD_MARGIN, length) ?: PdfContract.DEFAULT_STAMP_MARGIN.toDouble(),
        )
    }

    /** Black, the default colour. zh-CN: 默认颜色黑色. */
    private const val DEFAULT_COLOR = 0xFF000000.toInt()

    private fun positive(item: PayloadObject, key: String, maximum: Double): Double? {
        val value = item.optDouble(key, 0.0..maximum) ?: return null
        if (value <= 0.0) throw PdfFailure.invalid("${item.pathOf(key)} must be greater than 0, not $value")
        return value
    }

    private fun position(item: PayloadObject): StampPosition {
        val key = PdfContract.FIELD_POSITION
        return when (val value = item.value(key)) {
            null -> StampPosition.Named(1, 1)
            is String -> StampPosition.named(value)
                ?: throw PdfFailure.invalid("${item.pathOf(key)} must be one of ${PdfContract.POSITIONS.joinToString(", ")} or [x, y], not \"$value\"")
            is List<*> -> {
                val point = item.array(key)
                if (point.size != 2) throw PdfFailure.invalid("${point.path} must be [x, y], not ${point.size} numbers")
                val (x, y) = (0 until 2).map { index ->
                    when (val number = point.value(index)) {
                        is Long -> number.toDouble()
                        is Double -> number
                        else -> throw PdfFailure.invalid("${point.pathOf(index)} must be a number")
                    }.also { if (it !in -MAX_LENGTH..MAX_LENGTH) throw PdfFailure.invalid("${point.pathOf(index)} must be in -$MAX_LENGTH - $MAX_LENGTH, not $it") }
                }
                StampPosition.At(x, y)
            }
            else -> throw PdfFailure.invalid("${item.pathOf(key)} must be a position name or [x, y]")
        }
    }

    /**
     * The ARGB colour of `#RRGGBB` or `#AARRGGBB` (hex digits in either case), else `INVALID_ARGUMENT`.
     * zh-CN: `#RRGGBB` 或 `#AARRGGBB` (十六进制数字不分大小写) 对应的 ARGB 颜色, 否则报 `INVALID_ARGUMENT`.
     */
    fun color(text: String, path: String): Int {
        val digits = text.removePrefix("#")
        if (!text.startsWith("#") || (digits.length != 6 && digits.length != 8) || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            throw PdfFailure.invalid("$path must be #RRGGBB or #AARRGGBB, not \"$text\"")
        }
        val value = digits.toLong(16)
        return (if (digits.length == 6) value or 0xFF000000L else value).toInt()
    }

    /**
     * [text] with the placeholders replaced in a single pass (a replacement is not expanded again; other braces stay):
     * `{page}` (the physical [page] plus [pageOffset], shown as it is below 1, roadmap Q211 (a)), `{total}` (the page
     * count, no offset), `{date}` and `{name}`.
     * zh-CN: 单遍替换 [text] 中的占位符 (替换结果不再展开; 其他花括号保持原样): `{page}` (物理页码 [page] 加 [pageOffset], 小于 1 时照样
     * 显示, 路线图 Q211 (a)), `{total}` (总页数, 不加偏移), `{date}` 与 `{name}`.
     */
    fun expand(text: String, page: Int, total: Int, date: String, name: String, pageOffset: Long = 0L): String {
        if (text.indexOf('{') < 0) return text
        val out = StringBuilder(text.length + 8)
        var index = 0
        while (index < text.length) {
            val replacement = when {
                text.startsWith(PdfContract.PLACEHOLDER_PAGE, index) -> PdfContract.PLACEHOLDER_PAGE to (page + pageOffset).toString()
                text.startsWith(PdfContract.PLACEHOLDER_TOTAL, index) -> PdfContract.PLACEHOLDER_TOTAL to total.toString()
                text.startsWith(PdfContract.PLACEHOLDER_DATE, index) -> PdfContract.PLACEHOLDER_DATE to date
                text.startsWith(PdfContract.PLACEHOLDER_NAME, index) -> PdfContract.PLACEHOLDER_NAME to name
                else -> null
            }
            if (replacement == null) {
                out.append(text[index++])
            } else {
                out.append(replacement.second)
                index += replacement.first.length
            }
        }
        return out.toString()
    }

    /** `{name}` of [displayName]: without a trailing `.pdf` in any case, empty without one. zh-CN: [displayName] 对应的 `{name}`: 去掉末尾不分大小写的 `.pdf`, 没有时为空. */
    fun name(displayName: String?): String {
        val name = displayName ?: return ""
        return if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
    }

    /** Whether [text] holds a placeholder that changes from page to page. zh-CN: [text] 是否含逐页变化的占位符. */
    fun variesByPage(text: String): Boolean = PdfContract.PLACEHOLDER_PAGE in text

}

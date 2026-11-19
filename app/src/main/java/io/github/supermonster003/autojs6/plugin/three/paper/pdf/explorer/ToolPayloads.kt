package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JSON payloads of the toolbox tools added in 1.1.0 (roadmap P6.12, D68), built from their [ToolOptions] the way
 * the host's script facade builds them (`PdfEditArguments`), so a tool and the script P6.13 generates from it send the
 * same items: a watermark is one stamp item, centered by default, turned 45 degrees for text and 0 for an image, 0.3
 * opaque and gray (text); page numbers are one text item whose `pageOffset` makes the first selected page show the
 * start number (appendix D Q211), headers and footers one text item per filled slot, all with the shared font size and
 * pages added; `fillForm`, `setInfo` and `setOutline` get their documented payloads.
 * zh-CN: 1.1.0 新增的工具箱工具的 JSON payload (路线图 P6.12, D68), 由各自的 [ToolOptions] 按宿主脚本门面 (`PdfEditArguments`) 的
 * 方式构造, 使工具与 P6.13 由它生成的脚本发送相同的条目: 水印是一个盖章条目, 默认居中, 文字旋转 45 度, 图片 0 度, 不透明度 0.3,
 * 文字为灰色; 页码是一个文本条目, 其 `pageOffset` 使所选的第一页显示起始号 (附录 D Q211); 页眉页脚每个填写的位置一个文本条目, 都带上
 * 共用的字号与页码范围; `fillForm`, `setInfo` 与 `setOutline` 使用文档规定的 payload.
 */
internal object ToolPayloads {

    const val WATERMARK_TEXT_ROTATION = 45f

    const val WATERMARK_IMAGE_ROTATION = 0f

    const val WATERMARK_OPACITY = 0.3f

    const val WATERMARK_COLOR = "#808080"

    /** The font size of page numbers, headers and footers. zh-CN: 页码, 页眉与页脚的字号. */
    const val STAMP_FONT_SIZE = 10f

    const val PAGE_NUMBER_FORMAT = "{page} / {total}"

    const val PAGE_NUMBER_POSITION = PdfContract.POSITION_BOTTOM_CENTER

    private val TOP_ROW = listOf(PdfContract.POSITION_TOP_LEFT, PdfContract.POSITION_TOP_CENTER, PdfContract.POSITION_TOP_RIGHT)

    private val BOTTOM_ROW = listOf(PdfContract.POSITION_BOTTOM_LEFT, PdfContract.POSITION_BOTTOM_CENTER, PdfContract.POSITION_BOTTOM_RIGHT)

    /**
     * The stamp item of a watermark: an image item names image 0 (the first image input after the payload).
     * zh-CN: 水印的盖章条目: 图片条目引用 0 号图片 (payload 之后的第一个图片输入).
     */
    fun watermarkItem(options: ToolOptions.Watermark): JSONObject = JSONObject().apply {
        val image = options.type == PdfContract.STAMP_IMAGE
        put(PdfContract.FIELD_TYPE, options.type)
        if (image) put(PdfContract.FIELD_IMAGE, 0) else put(PdfContract.FIELD_TEXT, options.text)
        put(PdfContract.FIELD_POSITION, options.position)
        put(PdfContract.FIELD_ROTATION, options.rotation.toDouble())
        put(PdfContract.FIELD_OPACITY, options.opacity.toDouble())
        if (options.tile) put(PdfContract.FIELD_TILE, true)
        if (!image) {
            put(PdfContract.FIELD_COLOR, options.color ?: WATERMARK_COLOR)
            options.fontSize?.let { put(PdfContract.FIELD_FONT_SIZE, it.toDouble()) }
        } else {
            options.width?.let { put(PdfContract.FIELD_WIDTH, it.toDouble()) }
        }
        if (options.layer != PdfContract.LAYER_OVER) put(PdfContract.FIELD_LAYER, options.layer)
        options.pages?.let { put(PdfContract.FIELD_PAGES, it) }
    }

    /**
     * The text items of page numbers, headers and footers: the page number first (when [ToolOptions.PageNumbers.format]
     * is set), then the header slots left to right, then the footer slots.
     * zh-CN: 页码, 页眉与页脚的文本条目: 先是页码 (设置了 [ToolOptions.PageNumbers.format] 时), 然后从左到右的页眉位置, 再是页脚位置.
     */
    fun pageNumberItems(options: ToolOptions.PageNumbers): List<JSONObject> {
        val items = ArrayList<JSONObject>()
        options.format?.let { format ->
            items += textItem(format, options.position, options) {
                pageOffset(options.start, options.pages)?.let { put(PdfContract.FIELD_PAGE_OFFSET, it) }
            }
        }
        listOf(options.header to TOP_ROW, options.footer to BOTTOM_ROW).forEach { (slots, row) ->
            listOf(slots.left, slots.center, slots.right).zip(row).forEach { (text, position) ->
                if (text.isNotBlank()) items += textItem(text, position, options) {}
            }
        }
        return items
    }

    /**
     * The `pageOffset` that makes the first page of [pages] (page 1 without a range) show [start], or null for 0; pages
     * after it count on by physical distance (appendix D Q211, as the host's `PdfEditArguments.pageOffset`).
     * zh-CN: 使 [pages] 的第一页 (没有范围时为第 1 页) 显示 [start] 的 `pageOffset`, 为 0 时为 null; 之后的页按物理距离递增 (附录 D
     * Q211, 与宿主的 `PdfEditArguments.pageOffset` 相同).
     */
    fun pageOffset(start: Int, pages: String?): Int? {
        val offset = (start.toLong() - firstPage(pages)).coerceIn(-PdfContract.MAX_PAGE_OFFSET.toLong(), PdfContract.MAX_PAGE_OFFSET.toLong())
        return offset.toInt().takeIf { it != 0 }
    }

    /**
     * The first page a range selects (the smallest start of its items) without the page count; 1 for no range or one
     * that cannot be read (the operation refuses it then).
     * zh-CN: 范围选中的第一页 (各项起点的最小值), 不需要总页数; 没有范围或无法读取时为 1 (此时操作会拒绝该范围).
     */
    fun firstPage(pages: String?): Int {
        val starts = pages?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { it.substringBefore('-').trim().toIntOrNull() }
        if (starts.isNullOrEmpty() || starts.any { it == null }) return 1
        return starts.minOf { it!! }
    }

    /** `{ items }` of `stamp`. zh-CN: `stamp` 的 `{ items }`. */
    fun stamp(items: List<JSONObject>): JSONObject = JSONObject().put(PdfContract.FIELD_ITEMS, JSONArray(items))

    /**
     * `{ values, flatten }` of `fillForm`: a list becomes an array, null restores the default value.
     * zh-CN: `fillForm` 的 `{ values, flatten }`: 列表变为数组, null 恢复默认值.
     */
    fun fill(options: ToolOptions.FillForm): JSONObject {
        val values = JSONObject()
        options.values.forEach { (name, value) ->
            values.put(name, when (value) {
                null -> JSONObject.NULL
                is List<*> -> JSONArray(value)
                else -> value
            })
        }
        return JSONObject().apply {
            put(PdfContract.FIELD_VALUES, values)
            if (options.flatten) put(PdfContract.FIELD_FLATTEN, true)
        }
    }

    /** `{ info }` of `setInfo`: a string sets the key, null removes it. zh-CN: `setInfo` 的 `{ info }`: 字符串设置该键, null 删除它. */
    fun info(changes: Map<String, String?>): JSONObject {
        val info = JSONObject()
        changes.forEach { (key, value) -> info.put(key, value ?: JSONObject.NULL) }
        return JSONObject().put(PdfContract.FIELD_INFO, info)
    }

    /** `{ items: [] }` of `setOutline`, which removes every bookmark. zh-CN: `setOutline` 的 `{ items: [] }`, 删除全部书签. */
    fun clearedOutline(): JSONObject = JSONObject().put(PdfContract.FIELD_ITEMS, JSONArray())

    private fun textItem(text: String, position: String, options: ToolOptions.PageNumbers, own: JSONObject.() -> Unit): JSONObject = JSONObject().apply {
        put(PdfContract.FIELD_TYPE, PdfContract.STAMP_TEXT)
        put(PdfContract.FIELD_TEXT, text)
        put(PdfContract.FIELD_POSITION, position)
        own()
        put(PdfContract.FIELD_FONT_SIZE, options.fontSize.toDouble())
        options.pages?.let { put(PdfContract.FIELD_PAGES, it) }
    }

}

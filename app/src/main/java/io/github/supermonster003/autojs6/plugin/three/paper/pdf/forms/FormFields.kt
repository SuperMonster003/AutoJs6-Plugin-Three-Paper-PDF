package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.JsonItems
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Reading AcroForm fields (roadmap P6.4, D56, D62; protocol "getFormFields"): the terminal fields of the field tree
 * depth-first in document order, each with its type, value, default value, options, flags and widgets. Values are read
 * from the dictionaries (`/V` and `/DV` inherited through `/Parent`), not through the PDFBox getters, so a malformed
 * value reads as absent instead of failing the whole list. The field tree is walked with an explicit stack, an
 * identity set against cycles and at most [MAX_DEPTH] levels; the AcroForm is read as it is (no PDFBox fixup).
 * zh-CN: 读取 AcroForm 字段 (路线图 P6.4, D56, D62; 协议 "getFormFields" 一节): 字段树中的终端字段按深度优先文档顺序列出, 每个带
 * 类型, 值, 默认值, 选项, 标志与控件. 值直接从字典读取 (`/V` 与 `/DV` 沿 `/Parent` 继承), 不经 PDFBox 的 getter, 因此格式不对的值
 * 按缺失处理, 而不会让整个列表失败. 字段树用显式栈遍历, 以对象同一性集合防环, 最多 [MAX_DEPTH] 层; AcroForm 按原样读取 (不做
 * PDFBox 的 fixup).
 */
internal object FormFields {

    /** Levels of the field tree and of `/Parent` chains followed, at most. zh-CN: 字段树与 `/Parent` 链最多跟随的层数. */
    const val MAX_DEPTH = 64

    /** Fields between two cancellation checks. zh-CN: 两次取消检查之间的字段数. */
    private const val STOP_EVERY = 256

    const val FLAG_READ_ONLY = 1
    const val FLAG_REQUIRED = 1 shl 1
    const val FLAG_MULTILINE = 1 shl 12
    const val FLAG_PASSWORD = 1 shl 13
    const val FLAG_EDIT = 1 shl 18
    const val FLAG_MULTI_SELECT = 1 shl 21

    private const val OFF = "Off"

    private val NEEDS_RENDERING: COSName = COSName.getPDFName("NeedsRendering")

    /** A terminal field: its fully qualified name, the PDFBox field and its D62 type. zh-CN: 一个终端字段: 全限定名, PDFBox 字段与 D62 类型. */
    class Entry(val name: String, val field: PDTerminalField, val type: String) {

        val dictionary: COSDictionary get() = this.field.cosObject

        /** The field flags, inherited. zh-CN: 字段标志 (可继承). */
        val flags: Int get() = (inherited(dictionary, COSName.FF) as? COSNumber)?.intValue() ?: 0

        fun flag(mask: Int): Boolean = flags and mask != 0

    }

    /** A check box or radio widget: its on state (the first appearance state other than `Off`) and its export value. zh-CN: 勾选框或单选控件: 开状态 (第一个不是 `Off` 的外观状态) 与导出值. */
    class ButtonWidget(val widget: PDAnnotationWidget, val onState: String?, val export: String?)

    /** The AcroForm of [document], or null without one (or with a malformed one). zh-CN: [document] 的 AcroForm; 没有 (或格式不对) 时为 null. */
    fun acroForm(document: PDDocument): PDAcroForm? = runCatching { document.documentCatalog.getAcroForm(null) }.getOrNull()

    /** The terminal fields of [form] depth-first in document order. zh-CN: [form] 的终端字段, 按深度优先文档顺序. */
    fun terminals(form: PDAcroForm?, stop: StopSignal = StopSignal.NEVER): List<Entry> {
        if (form == null) return emptyList()
        val found = ArrayList<Entry>()
        val seen = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        val stack = ArrayList<Pair<PDField, Int>>()
        runCatching { form.fields }.getOrDefault(emptyList()).asReversed().forEach { stack += it to 0 }
        var visited = 0
        while (stack.isNotEmpty()) {
            val (field, depth) = stack.removeAt(stack.size - 1)
            if (!seen.add(field.cosObject)) continue
            if (++visited % STOP_EVERY == 0) stop.checkStop()
            when (field) {
                is PDNonTerminalField -> if (depth < MAX_DEPTH) {
                    runCatching { field.children }.getOrDefault(emptyList()).asReversed().forEach { stack += it to depth + 1 }
                }
                is PDTerminalField -> found += Entry(runCatching { field.fullyQualifiedName }.getOrNull() ?: "", field, typeOf(field))
            }
        }
        return found
    }

    fun typeOf(field: PDTerminalField): String = when (field) {
        is PDTextField -> PdfContract.FORM_FIELD_TEXT
        is PDCheckBox -> PdfContract.FORM_FIELD_CHECKBOX
        is PDRadioButton -> PdfContract.FORM_FIELD_RADIO
        is PDComboBox -> PdfContract.FORM_FIELD_COMBO
        is PDListBox -> PdfContract.FORM_FIELD_LIST
        is PDSignatureField -> PdfContract.FORM_FIELD_SIGNATURE
        else -> PdfContract.FORM_FIELD_BUTTON
    }

    /**
     * `xfa` of `getInfo`: absent without an `/XFA` entry, `dynamic` when the catalog sets `/NeedsRendering` or the
     * AcroForm has no terminal field (nothing to fill without an XFA engine), else `static`.
     * zh-CN: `getInfo` 的 `xfa`: 没有 `/XFA` 时缺省; 目录设置了 `/NeedsRendering` 或 AcroForm 没有终端字段 (没有 XFA 引擎就无从填写) 时为
     * `dynamic`, 否则为 `static`.
     */
    fun xfa(document: PDDocument, form: PDAcroForm?, terminalCount: Int): String? {
        if (form == null || form.cosObject.getDictionaryObject(COSName.XFA) == null) return null
        val needsRendering = document.documentCatalog.cosObject.getBoolean(NEEDS_RENDERING, false)
        return if (needsRendering || terminalCount == 0) PdfContract.XFA_DYNAMIC else PdfContract.XFA_STATIC
    }

    /** [key] of [dictionary] or of the nearest `/Parent` that has it. zh-CN: [dictionary] 或最近一个带有 [key] 的 `/Parent` 中的值. */
    fun inherited(dictionary: COSDictionary, key: COSName): COSBase? {
        var current: COSDictionary? = dictionary
        var depth = 0
        while (current != null && depth <= MAX_DEPTH) {
            current.getDictionaryObject(key)?.let { return it }
            current = current.getDictionaryObject(COSName.PARENT) as? COSDictionary
            depth++
        }
        return null
    }

    /** A text string, a text stream or a name as text. zh-CN: 文本字符串, 文本流或名称的文字. */
    fun text(base: COSBase?): String? = when (base) {
        is COSString -> base.string
        is COSStream -> runCatching { base.toTextString() }.getOrNull()
        is COSName -> base.name
        else -> null
    }

    /** The strings of a choice value: one string, or the strings of an array. zh-CN: 选择字段值中的字符串: 单个字符串, 或数组中的字符串. */
    fun strings(base: COSBase?): List<String> = when (base) {
        null -> emptyList()
        is COSArray -> (0 until base.size()).mapNotNull { text(base.getObject(it)) }
        else -> listOfNotNull(text(base))
    }

    /** The on state of [widget]: the first key of its normal appearance dictionary other than `Off`. zh-CN: [widget] 的开状态: 正常外观字典中第一个不是 `Off` 的键. */
    fun onState(widget: PDAnnotationWidget): String? {
        val appearance = widget.cosObject.getDictionaryObject(COSName.AP) as? COSDictionary ?: return null
        val normal = appearance.getDictionaryObject(COSName.N) as? COSDictionary ?: return null
        if (normal is COSStream) return null
        return normal.keySet().firstOrNull { it.name != OFF }?.name
    }

    /**
     * The widgets of a check box or radio field with their on states and export values: `/Opt` gives the export value
     * of each widget when it has one entry per widget, else the on state is the export value.
     * zh-CN: 勾选框或单选字段的控件, 附开状态与导出值: `/Opt` 的条目数等于控件数时按序给出各控件的导出值, 否则开状态就是导出值.
     */
    fun buttonWidgets(field: PDButton): List<ButtonWidget> {
        val widgets = runCatching { field.widgets }.getOrDefault(emptyList())
        val options = exportOptions(field)
        return widgets.mapIndexed { index, widget ->
            val on = onState(widget)
            ButtonWidget(widget, on, if (options != null && options.size == widgets.size) options[index] ?: on else on)
        }
    }

    private fun exportOptions(field: PDButton): List<String?>? {
        val array = inherited(field.cosObject, COSName.OPT) as? COSArray ?: return null
        return (0 until array.size()).map { text(array.getObject(it)) }
    }

    /** The export value a radio `/V` stands for, or null for `Off` or none. zh-CN: 单选字段的 `/V` 所代表的导出值; `Off` 或没有时为 null. */
    fun radioValue(field: PDButton, value: COSBase?): String? {
        val state = text(value)?.takeIf { it.isNotEmpty() && it != OFF } ?: return null
        val options = exportOptions(field)
        if (options != null) state.toIntOrNull()?.let { index -> if (index in options.indices) options[index]?.let { return it } }
        buttonWidgets(field).firstOrNull { it.onState == state }?.export?.let { return it }
        return state
    }

    fun checked(value: COSBase?): Boolean = text(value)?.let { it.isNotEmpty() && it != OFF } ?: false

    /** The `{ value, label }` options of a choice field. zh-CN: 选择字段的 `{ value, label }` 选项. */
    fun choiceOptions(field: PDChoice): List<Pair<String, String>> {
        val exports = runCatching { field.optionsExportValues }.getOrNull().orEmpty()
        val labels = runCatching { field.optionsDisplayValues }.getOrNull().orEmpty()
        return exports.mapIndexed { index, export -> export to (labels.getOrNull(index) ?: export) }
    }

    /** The value of [entry] typed per D62, from [base] (`/V` or `/DV`). zh-CN: 按 D62 的类型给出 [entry] 的值, 取自 [base] (`/V` 或 `/DV`). */
    fun typedValue(entry: Entry, base: COSBase?): Any? = when (entry.type) {
        PdfContract.FORM_FIELD_TEXT -> text(base)
        PdfContract.FORM_FIELD_CHECKBOX -> checked(base)
        PdfContract.FORM_FIELD_RADIO -> radioValue(entry.field as PDButton, base)
        PdfContract.FORM_FIELD_COMBO -> strings(base).firstOrNull()
        PdfContract.FORM_FIELD_LIST -> strings(base)
        else -> null
    }

    fun value(entry: Entry): Any? = typedValue(entry, inherited(entry.dictionary, COSName.V))

    fun defaultValue(entry: Entry): Any? = typedValue(entry, inherited(entry.dictionary, COSName.DV))

    /**
     * The pages of the widget annotations of [document]: every `/Annots` entry by identity, to its page (1-based, the
     * first page listing it wins); built on first use.
     * zh-CN: [document] 中控件注释所在的页: 按对象同一性把每个 `/Annots` 条目映射到其页码 (从 1 起, 先列出它的页为准); 首次使用时建立.
     */
    class PageIndex(private val document: PDDocument, private val stop: StopSignal = StopSignal.NEVER) {

        private val pages: Map<COSDictionary, Int> by lazy {
            val map = IdentityHashMap<COSDictionary, Int>()
            var number = 0
            for (page in document.pages) {
                number++
                if (number % STOP_EVERY == 0) stop.checkStop()
                val annotations = page.cosObject.getDictionaryObject(COSName.ANNOTS) as? COSArray ?: continue
                for (index in 0 until annotations.size()) {
                    val annotation = annotations.getObject(index) as? COSDictionary ?: continue
                    map.putIfAbsent(annotation, number)
                }
            }
            map
        }

        private val frames = HashMap<Int, Pair<PageFrame, DoubleArray>>()

        /** The page of [widget], 0 when no page lists it. zh-CN: [widget] 所在的页, 没有页列出它时为 0. */
        fun pageOf(widget: COSDictionary): Int = pages[widget] ?: 0

        /**
         * The rectangle of [widget] on [page] in display points `[left, top, right, bottom]` (two decimals, clamped to
         * the page), or null without a usable `/Rect`.
         * zh-CN: [widget] 在 [page] 上以显示坐标 (点) 表示的矩形 `[left, top, right, bottom]` (保留两位小数, 限制在页内); 没有可用的
         * `/Rect` 时为 null.
         */
        fun displayRect(page: Int, widget: PDAnnotationWidget): DoubleArray? {
            val rect = runCatching { widget.rectangle }.getOrNull() ?: return null
            val (frame, origin) = frames.getOrPut(page) {
                val pdPage = document.getPage(page - 1)
                val box = pdPage.cropBox
                PageFrame.of(pdPage) to doubleArrayOf(box.lowerLeftX.toDouble(), box.lowerLeftY.toDouble())
            }
            var left = Double.POSITIVE_INFINITY
            var top = Double.POSITIVE_INFINITY
            var right = Double.NEGATIVE_INFINITY
            var bottom = Double.NEGATIVE_INFINITY
            for (x in doubleArrayOf(rect.lowerLeftX.toDouble(), rect.upperRightX.toDouble())) {
                for (y in doubleArrayOf(rect.lowerLeftY.toDouble(), rect.upperRightY.toDouble())) {
                    val (displayX, displayY) = frame.display(x - origin[0], y - origin[1])
                    left = min(left, displayX)
                    right = max(right, displayX)
                    top = min(top, displayY)
                    bottom = max(bottom, displayY)
                }
            }
            if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
            return doubleArrayOf(
                round(left.coerceIn(0.0, frame.displayWidth)),
                round(top.coerceIn(0.0, frame.displayHeight)),
                round(right.coerceIn(0.0, frame.displayWidth)),
                round(bottom.coerceIn(0.0, frame.displayHeight)),
            )
        }

        private fun round(value: Double): Double = (value * 100).roundToLong() / 100.0

    }

    /** The `getFormFields` entry of [entry]. zh-CN: [entry] 的 `getFormFields` 条目. */
    fun describe(entry: Entry, pages: PageIndex): JSONObject {
        val item = JSONObject()
        item.put(PdfContract.FIELD_NAME, entry.name)
        item.put(PdfContract.FIELD_TYPE, entry.type)
        item.put(PdfContract.FIELD_VALUE, json(value(entry)))
        item.put(PdfContract.FIELD_DEFAULT_VALUE, json(defaultValue(entry)))
        when (val field = entry.field) {
            is PDRadioButton -> item.put(PdfContract.FIELD_OPTIONS, JSONArray().apply {
                buttonWidgets(field).mapNotNull { it.export }.distinct().forEach { put(option(it, it)) }
            })
            is PDChoice -> item.put(PdfContract.FIELD_OPTIONS, JSONArray().apply { choiceOptions(field).forEach { (value, label) -> put(option(value, label)) } })
        }
        text(entry.dictionary.getDictionaryObject(COSName.TU))?.takeIf { it.isNotEmpty() }?.let { item.put(PdfContract.FIELD_LABEL, it) }
        item.put(PdfContract.FIELD_READ_ONLY, entry.flag(FLAG_READ_ONLY))
        item.put(PdfContract.FIELD_REQUIRED, entry.flag(FLAG_REQUIRED))
        when (entry.type) {
            PdfContract.FORM_FIELD_TEXT -> {
                item.put(PdfContract.FIELD_MULTILINE, entry.flag(FLAG_MULTILINE))
                item.put(PdfContract.FIELD_PASSWORD, entry.flag(FLAG_PASSWORD))
                maxLength(entry)?.let { item.put(PdfContract.FIELD_MAX_LENGTH, it) }
            }
            PdfContract.FORM_FIELD_LIST -> item.put(PdfContract.FIELD_MULTI_SELECT, entry.flag(FLAG_MULTI_SELECT))
            PdfContract.FORM_FIELD_SIGNATURE -> item.put(PdfContract.FIELD_SIGNED, signed(entry))
        }
        val widgets = JSONArray()
        var first: Pair<Int, DoubleArray>? = null
        for (widget in runCatching { entry.field.widgets }.getOrDefault(emptyList())) {
            val page = pages.pageOf(widget.cosObject)
            if (page == 0) continue
            val rect = pages.displayRect(page, widget) ?: continue
            if (first == null) first = page to rect
            widgets.put(JSONObject().put(PdfContract.FIELD_PAGE, page).put(PdfContract.FIELD_RECT, JSONArray(rect.toList())))
        }
        item.put(PdfContract.FIELD_PAGE, first?.first ?: 0)
        first?.let { item.put(PdfContract.FIELD_RECT, JSONArray(it.second.toList())) }
        item.put(PdfContract.FIELD_WIDGETS, widgets)
        return item
    }

    /** `/MaxLen` of a text field when positive. zh-CN: 文本字段的 `/MaxLen` (为正数时). */
    fun maxLength(entry: Entry): Int? = (inherited(entry.dictionary, COSName.MAX_LEN) as? COSNumber)?.intValue()?.takeIf { it > 0 }

    /** Whether a signature field holds a signature dictionary. zh-CN: 签名字段是否带有签名字典. */
    fun signed(entry: Entry): Boolean = inherited(entry.dictionary, COSName.V) is COSDictionary

    private fun option(value: String, label: String) = JSONObject().put(PdfContract.FIELD_VALUE, value).put(PdfContract.FIELD_LABEL, label)

    private fun json(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is List<*> -> JSONArray(value)
        else -> value
    }

    /**
     * The `getFormFields` answer: `offset` entries skipped, at most `limit` (0 or absent means
     * `DEFAULT_FORM_FIELD_LIMIT`, larger is cut to `MAX_FORM_FIELD_ITEMS`) and as many as fit [budgetChars]; an entry
     * that alone does not fit is `LIMIT_EXCEEDED` naming the field.
     * zh-CN: `getFormFields` 的应答: 跳过 `offset` 个条目, 至多 `limit` 个 (0 或缺省为 `DEFAULT_FORM_FIELD_LIMIT`, 更大时截到
     * `MAX_FORM_FIELD_ITEMS`), 并以 [budgetChars] 能容纳的为限; 单个条目就放不下时为 `LIMIT_EXCEEDED` 并点名该字段.
     */
    fun read(document: PDDocument, options: PdfOptions, stop: StopSignal, budgetChars: Int = JsonItems.BUDGET_CHARS): Map<String, Any?> {
        val offset = options.int(PdfContract.KEY_OFFSET, 0..Int.MAX_VALUE, 0)
        val limit = options.int(PdfContract.KEY_LIMIT, 0..Int.MAX_VALUE, 0).let {
            if (it == 0) PdfContract.DEFAULT_FORM_FIELD_LIMIT else it.coerceAtMost(PdfContract.MAX_FORM_FIELD_ITEMS)
        }
        val fields = terminals(acroForm(document), stop)
        val pages = PageIndex(document, stop)
        val items = JsonItems(budgetChars)
        var hasMore = false
        var index = offset
        while (index < fields.size) {
            if (items.count >= limit) {
                hasMore = true
                break
            }
            if ((index - offset) % STOP_EVERY == 0) stop.checkStop()
            if (!items.add(describe(fields[index], pages))) {
                if (items.count == 0) throw PdfFailure.limitExceeded("the form field '${fields[index].name}' alone exceeds the answer size")
                hasMore = true
                break
            }
            index++
        }
        return buildMap {
            put(PdfContract.KEY_ITEMS, items.toString())
            put(PdfContract.KEY_HAS_MORE, hasMore)
            if (hasMore) put(PdfContract.KEY_NEXT_OFFSET, offset + items.count)
        }
    }

}

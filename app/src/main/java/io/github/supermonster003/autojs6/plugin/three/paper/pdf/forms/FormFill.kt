package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.fixup.processor.AcroFormDefaultsProcessor
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata.InfoChanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadArray
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.autojs.plugin.pdf.api.PdfWarningCodes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The run operation `fillForm` (roadmap P6.4, D56, D62, spike S1; protocol "fillForm"): sets AcroForm field values
 * from the payload `values` and optionally flattens fields (`flatten`: `true` for all, an array of names). Every value
 * is checked before anything changes: unknown names (`INVALID_ARGUMENT` listing at most `MAX_LISTED_UNKNOWN_FIELDS`,
 * or one warning with `ignoreMissing`), read-only, push button and signature fields, wrong types, values outside the
 * options, too long text. Values are set on the dictionaries (`/V`, `/I` of list boxes, `/AS` of check boxes and radio
 * buttons); appearances of text, combo and list fields are built by PDFBox with the form's fonts (`/DA`, `/DR`, after
 * `AcroFormDefaultsProcessor`), and a field PDFBox cannot draw (a value its font cannot encode, a missing font) gets
 * the Canvas fallback ([FieldAppearances], [AppearanceDrawer]). `/NeedAppearances` is left as the source has it
 * (roadmap Q242: set, it makes the system `PdfRenderer` of Android 15 redraw every widget with its own fonts, dropping
 * emoji and the look of check boxes and lists).
 * Static XFA is removed (readers would show its stale data), dynamic XFA is `UNSUPPORTED`; form JavaScript is not run
 * and signed signatures stop validating, one warning each.
 * zh-CN: run 操作 `fillForm` (路线图 P6.4, D56, D62, spike S1; 协议 "fillForm" 一节): 按 payload 的 `values` 设置 AcroForm 字段的值,
 * 并可扁平化字段 (`flatten`: `true` 表示全部, 或字段名数组). 在做任何修改之前先检查全部取值: 未知字段名 (`INVALID_ARGUMENT`, 最多列出
 * `MAX_LISTED_UNKNOWN_FIELDS` 个; 带 `ignoreMissing` 时改为一条警告), 只读字段, 按钮与签名字段, 类型不符, 不在选项中的值, 过长的文本.
 * 值直接写入字典 (`/V`, 列表框的 `/I`, 勾选框与单选按钮的 `/AS`); 文本, 下拉与列表字段的外观由 PDFBox 用表单自己的字体 (`/DA`, `/DR`,
 * 先经 `AcroFormDefaultsProcessor`) 生成, PDFBox 画不了的字段 (值含字体无法编码的字符, 字体缺失) 改用 Canvas 回落 ([FieldAppearances],
 * [AppearanceDrawer]). `/NeedAppearances` 保持源文件的状态 (路线图 Q242: 置上后 Android 15 的系统 `PdfRenderer` 会用自己的字体重画全部
 * 控件, 表情丢失, 勾选框与列表的样式也会改变). 静态 XFA 被移除 (否则阅读器会显示其中的旧数据), 动态 XFA 为
 * `UNSUPPORTED`; 表单的 JavaScript 不执行, 已签名的签名不再有效, 各给一条警告.
 */
internal object FormFill {

    /** Options of most "not one of the options" messages. zh-CN: "不在选项中" 消息里最多列出的选项数. */
    private const val MAX_LISTED_OPTIONS = 10

    /** Fields between two cancellation checks. zh-CN: 两次取消检查之间的字段数. */
    private const val STOP_EVERY = 64

    private const val OFF = "Off"

    private const val DEFAULT_ON = "Yes"

    private val OFF_NAME: COSName = COSName.getPDFName(OFF)

    /** The trigger keys of field actions that run on values (keystroke, format, validate, calculate). zh-CN: 作用于取值的字段动作触发键 (按键, 格式, 校验, 计算). */
    private val VALUE_TRIGGERS = listOf(COSName.K, COSName.F, COSName.V, COSName.C)

    class Request(val password: String?, val ignoreMissing: Boolean)

    /** What `flatten` asks for. zh-CN: `flatten` 的要求. */
    sealed interface Flatten {

        object None : Flatten

        object All : Flatten

        class Named(val names: List<String>) : Flatten

    }

    class Payload(val values: PayloadObject, val flatten: Flatten)

    /**
     * What [apply] did: fields given a value, fields flattened, fields drawn by the Canvas fallback, warnings.
     * zh-CN: [apply] 的结果: 被设置值的字段数, 被扁平化的字段数, 由 Canvas 回落绘制的字段数, 警告.
     */
    class Outcome(val filled: Int, val flattened: Int, val fallback: Int, val warnings: List<PdfWarning>)

    /** A checked value. zh-CN: 校验过的取值. */
    private sealed interface Assignment {

        class Text(val text: String) : Assignment

        class Checked(val checked: Boolean) : Assignment

        class Radio(val export: String) : Assignment

        /** A combo value (one) or the list selection (option order). zh-CN: 下拉框的值 (一个) 或列表框的选择 (按选项顺序). */
        class Choice(val values: List<String>) : Assignment

        /** `null`: the default value, or empty without one. zh-CN: `null`: 默认值, 没有默认值时为空. */
        object Restore : Assignment

    }

    fun request(options: PdfOptions, inputCount: Int): Request {
        if (inputCount != 2) throw PdfFailure.invalid("${PdfOps.FILL_FORM} takes the source and the payload (2 inputs), got $inputCount")
        return Request(options.password()?.takeIf { it.isNotEmpty() }, options.boolean(PdfContract.KEY_IGNORE_MISSING, false))
    }

    fun parse(payload: PayloadObject): Payload {
        val values = payload.obj(PdfContract.FIELD_VALUES)
        val flatten = when (val raw = payload.value(PdfContract.FIELD_FLATTEN)) {
            null, false -> Flatten.None
            true -> Flatten.All
            is List<*> -> Flatten.Named(payload.array(PdfContract.FIELD_FLATTEN).strings())
            else -> throw PdfFailure.invalid("${payload.pathOf(PdfContract.FIELD_FLATTEN)} must be a boolean or an array of field names, not ${PdfPayload.kind(raw)}")
        }
        return Payload(values, flatten)
    }

    /**
     * Fills (and flattens) the form of [document] as [payload] says; [drawer] draws the Canvas fallback, [progress] gets
     * (fields done, fields to do).
     * zh-CN: 按 [payload] 填写 (并扁平化) [document] 的表单; [drawer] 绘制 Canvas 回落, [progress] 收到 (已处理字段数, 待处理字段数).
     */
    fun apply(
        document: PDDocument,
        payload: Payload,
        ignoreMissing: Boolean,
        drawer: AppearanceDrawer,
        stop: StopSignal = StopSignal.NEVER,
        progress: (Int, Int) -> Unit = { _, _ -> },
    ): Outcome {
        val form = FormFields.acroForm(document)
        val terminals = FormFields.terminals(form, stop)
        val xfa = FormFields.xfa(document, form, terminals.size)
        if (xfa == PdfContract.XFA_DYNAMIC) {
            throw PdfFailure.unsupported("the form is a dynamic XFA form; ${PdfOps.FILL_FORM} fills AcroForm fields only")
        }
        val byName = LinkedHashMap<String, MutableList<FormFields.Entry>>()
        terminals.forEach { byName.getOrPut(it.name) { ArrayList() } += it }
        val warnings = ArrayList<PdfWarning>()

        val flattenNames = (payload.flatten as? Flatten.Named)?.names.orEmpty()
        val unknown = (payload.values.keys + flattenNames).filter { it !in byName }.distinct()
        if (unknown.isNotEmpty()) {
            val listed = unknown.take(PdfContract.MAX_LISTED_UNKNOWN_FIELDS).joinToString(", ") { "'$it'" }
            val beyond = (unknown.size - PdfContract.MAX_LISTED_UNKNOWN_FIELDS).coerceAtLeast(0)
            val more = if (beyond > 0) " and $beyond more" else ""
            val count = if (unknown.size == 1) "1 field name matches" else "${unknown.size} field names match"
            if (!ignoreMissing) {
                throw PdfFailure.invalid("$count no form field: $listed$more (${PdfContract.KEY_IGNORE_MISSING} skips unknown names)")
            }
            warnings += PdfWarning(
                "$count no form field and ${if (unknown.size == 1) "was" else "were"} ignored: $listed$more",
                PdfWarningCodes.FIELDS_IGNORED,
                mapOf(PdfWarningCodes.ARG_COUNT to unknown.size, PdfWarningCodes.ARG_NAMES to listed, PdfWarningCodes.ARG_MORE to beyond),
            )
        }

        // Check every value before changing anything. zh-CN: 修改之前先检查全部取值.
        val planned = ArrayList<Pair<FormFields.Entry, Assignment>>()
        for (name in payload.values.keys) {
            val entries = byName[name] ?: continue
            val path = payload.values.pathOf(name)
            entries.forEach { planned += it to validate(it, payload.values.value(name), path) }
        }
        val toFlatten: List<FormFields.Entry> = when (val flatten = payload.flatten) {
            Flatten.None -> emptyList()
            Flatten.All -> terminals
            is Flatten.Named -> flatten.names.flatMap { byName[it].orEmpty() }.distinct()
        }
        if (form == null) return Outcome(0, 0, 0, warnings)

        addDefaults(document)
        val fallback = Collections.newSetFromMap(IdentityHashMap<FormFields.Entry, Boolean>())
        val total = planned.size + toFlatten.size
        var done = 0
        progress(0, total)
        for ((entry, assignment) in planned) {
            if (done % STOP_EVERY == 0) stop.checkStop()
            assign(entry, assignment, form, fallback)
            progress(++done, total)
        }
        // A field to flatten needs an appearance on every widget; build the missing ones from its value.
        // zh-CN: 要扁平化的字段的每个控件都需要外观; 缺少的按字段值生成.
        for (entry in toFlatten) {
            if (done % STOP_EVERY == 0) stop.checkStop()
            if (entry.type in REDRAWN && entry !in fallback && widgets(entry).any { it.appearance?.normalAppearance == null }) refresh(entry, form, fallback)
            progress(++done, total)
        }
        if (fallback.isNotEmpty()) drawFallback(document, form, fallback, drawer, stop)

        if (planned.isNotEmpty() && scripted(form, planned.map { it.first })) {
            warnings += PdfWarning(
                "the form has JavaScript actions (format, validation or calculation) that ${PdfOps.FILL_FORM} does not run; values that scripts would compute or reformat stay as they are",
                PdfWarningCodes.FORM_SCRIPTS_NOT_RUN,
            )
        }
        val signed = terminals.count { it.type == PdfContract.FORM_FIELD_SIGNATURE && FormFields.signed(it) }
        if (signed > 0) {
            warnings += PdfWarning(
                "the document has $signed signed signature field${if (signed == 1) "" else "s"}; the filled copy is rewritten, so ${if (signed == 1) "that signature no longer validates" else "those signatures no longer validate"}",
                PdfWarningCodes.SIGNATURES_INVALIDATED,
                mapOf(PdfWarningCodes.ARG_COUNT to signed),
            )
        }
        if (xfa == PdfContract.XFA_STATIC) {
            form.cosObject.removeItem(COSName.XFA)
            warnings += PdfWarning("the static XFA form data was removed, so readers show the AcroForm values", PdfWarningCodes.XFA_REMOVED)
        }

        if (toFlatten.isNotEmpty()) flatten(document, form, toFlatten, stop)
        return Outcome(planned.size, toFlatten.size, fallback.size, warnings)
    }

    /** Field types whose appearance shows the value as text. zh-CN: 外观以文字显示取值的字段类型. */
    private val REDRAWN = setOf(PdfContract.FORM_FIELD_TEXT, PdfContract.FORM_FIELD_COMBO, PdfContract.FORM_FIELD_LIST)

    private fun widgets(entry: FormFields.Entry) = runCatching { entry.field.widgets }.getOrDefault(emptyList())

    /** Checks one payload value against its field. zh-CN: 按字段检查一个 payload 取值. */
    private fun validate(entry: FormFields.Entry, raw: Any?, path: String): Assignment {
        when (entry.type) {
            PdfContract.FORM_FIELD_BUTTON -> throw PdfFailure.invalid("$path: '${entry.name}' is a push button, which holds no value")
            PdfContract.FORM_FIELD_SIGNATURE -> throw PdfFailure.invalid("$path: '${entry.name}' is a signature field, which ${PdfOps.FILL_FORM} cannot sign")
        }
        if (entry.flag(FormFields.FLAG_READ_ONLY)) throw PdfFailure.invalid("$path: '${entry.name}' is read-only")
        if (raw == null) return Assignment.Restore
        return when (entry.type) {
            PdfContract.FORM_FIELD_TEXT -> {
                val text = raw as? String ?: throw wrongType(path, "a string or null", raw)
                FormFields.maxLength(entry)?.let { max ->
                    val length = text.codePointCount(0, text.length)
                    if (length > max) throw PdfFailure.invalid("$path has $length characters, more than the field's maximum length $max")
                }
                Assignment.Text(text)
            }
            PdfContract.FORM_FIELD_CHECKBOX -> Assignment.Checked(raw as? Boolean ?: throw wrongType(path, "a boolean or null", raw))
            PdfContract.FORM_FIELD_RADIO -> {
                val value = raw as? String ?: throw wrongType(path, "a string or null", raw)
                val exports = FormFields.buttonWidgets(entry.field as PDButton).mapNotNull { it.export }.distinct()
                if (value !in exports) throw notAnOption(path, value, exports)
                Assignment.Radio(value)
            }
            PdfContract.FORM_FIELD_COMBO -> {
                val value = raw as? String ?: throw wrongType(path, "a string or null", raw)
                val exports = FormFields.choiceOptions(entry.field as PDChoice).map { it.first }
                if (!entry.flag(FormFields.FLAG_EDIT) && value !in exports) throw notAnOption(path, value, exports)
                Assignment.Choice(listOf(value))
            }
            else -> {
                val values = when (raw) {
                    is String -> listOf(raw)
                    is List<*> -> PayloadArray(path, raw).strings()
                    else -> throw wrongType(path, "a string, an array of strings or null", raw)
                }.distinct()
                if (!entry.flag(FormFields.FLAG_MULTI_SELECT) && values.size > 1) {
                    throw PdfFailure.invalid("$path has ${values.size} values, but '${entry.name}' allows one selection")
                }
                val exports = FormFields.choiceOptions(entry.field as PDChoice).map { it.first }
                values.firstOrNull { it !in exports }?.let { throw notAnOption(path, it, exports) }
                Assignment.Choice(values.sortedBy { exports.indexOf(it) })
            }
        }
    }

    private fun wrongType(path: String, expected: String, raw: Any?) = PdfFailure.invalid("$path must be $expected, not ${PdfPayload.kind(raw)}")

    private fun notAnOption(path: String, value: String, exports: List<String>): PdfFailure {
        val listed = exports.take(MAX_LISTED_OPTIONS).joinToString(", ") { "'$it'" }
        val more = if (exports.size > MAX_LISTED_OPTIONS) ", ..." else ""
        return PdfFailure.invalid("$path must be one of the options ($listed$more), not '$value'")
    }

    /** Sets one checked value and rebuilds the appearance. zh-CN: 设置一个校验过的取值并重建外观. */
    private fun assign(entry: FormFields.Entry, assignment: Assignment, form: PDAcroForm, fallback: MutableSet<FormFields.Entry>) {
        val dictionary = entry.dictionary
        when (entry.type) {
            PdfContract.FORM_FIELD_TEXT -> {
                when (assignment) {
                    is Assignment.Text -> dictionary.setItem(COSName.V, COSString(assignment.text))
                    else -> {
                        val default = FormFields.text(FormFields.inherited(dictionary, COSName.DV))
                        if (default != null) dictionary.setItem(COSName.V, COSString(default)) else clear(dictionary, COSString(""))
                    }
                }
                refresh(entry, form, fallback)
            }
            PdfContract.FORM_FIELD_COMBO, PdfContract.FORM_FIELD_LIST -> {
                val values = when (assignment) {
                    is Assignment.Choice -> assignment.values
                    else -> FormFields.strings(FormFields.inherited(dictionary, COSName.DV))
                }
                setChoice(entry, values)
                refresh(entry, form, fallback)
            }
            PdfContract.FORM_FIELD_CHECKBOX -> {
                val buttons = FormFields.buttonWidgets(entry.field as PDButton)
                val state = when (assignment) {
                    is Assignment.Checked -> if (assignment.checked) buttons.firstNotNullOfOrNull { it.onState } ?: DEFAULT_ON else OFF
                    else -> FormFields.text(FormFields.inherited(dictionary, COSName.DV))?.takeIf { it.isNotEmpty() } ?: OFF
                }
                setState(dictionary, buttons, state)
            }
            PdfContract.FORM_FIELD_RADIO -> {
                val buttons = FormFields.buttonWidgets(entry.field as PDButton)
                val state = when (assignment) {
                    is Assignment.Radio -> buttons.firstOrNull { it.export == assignment.export && it.onState != null }?.onState ?: OFF
                    else -> FormFields.text(FormFields.inherited(dictionary, COSName.DV))?.takeIf { it.isNotEmpty() } ?: OFF
                }
                setState(dictionary, buttons, state)
            }
        }
    }

    /**
     * Removes the own `/V` of [dictionary]; when an ancestor still gives one, [empty] overrides it.
     * zh-CN: 删除 [dictionary] 自己的 `/V`; 祖先仍提供 `/V` 时用 [empty] 覆盖.
     */
    private fun clear(dictionary: COSDictionary, empty: COSBase) {
        dictionary.removeItem(COSName.V)
        if (FormFields.inherited(dictionary, COSName.V) != null) dictionary.setItem(COSName.V, empty)
    }

    /**
     * Sets the selection of a combo or list field: a string `/V` (an array for a multiple selection list), `/I` with
     * the option indices for a multiple selection list and removed otherwise; no values clears the field.
     * zh-CN: 设置下拉或列表字段的选择: `/V` 为字符串 (多选列表为数组), 多选列表的 `/I` 为选项下标, 其他情况删除 `/I`; 没有值时清空字段.
     */
    private fun setChoice(entry: FormFields.Entry, values: List<String>) {
        val dictionary = entry.dictionary
        dictionary.removeItem(COSName.I)
        val multiple = entry.type == PdfContract.FORM_FIELD_LIST && entry.flag(FormFields.FLAG_MULTI_SELECT)
        when {
            values.isEmpty() -> clear(dictionary, if (multiple) COSArray() else COSString(""))
            multiple -> {
                dictionary.setItem(COSName.V, COSArray().apply { values.forEach { add(COSString(it)) } })
                val exports = FormFields.choiceOptions(entry.field as PDChoice).map { it.first }
                val indices = values.map { exports.indexOf(it) }.filter { it >= 0 }.sorted()
                if (indices.isNotEmpty()) dictionary.setItem(COSName.I, COSArray().apply { indices.forEach { add(COSInteger.get(it.toLong())) } })
            }
            else -> dictionary.setItem(COSName.V, COSString(values.first()))
        }
    }

    /** `/V` of a check box or radio field and `/AS` of its widgets. zh-CN: 勾选框或单选字段的 `/V` 与各控件的 `/AS`. */
    private fun setState(dictionary: COSDictionary, buttons: List<FormFields.ButtonWidget>, state: String) {
        dictionary.setItem(COSName.V, COSName.getPDFName(state))
        for (button in buttons) {
            button.widget.cosObject.setItem(COSName.AS, if (state != OFF && button.onState == state) COSName.getPDFName(state) else OFF_NAME)
        }
    }

    /**
     * Rebuilds the appearance of a text, combo or list field with PDFBox; a failure adds the field to [fallback]. A
     * password field is drawn from asterisks and a combo box from the label of its value (PDFBox draws `/V` as it is),
     * with `/V` put back afterwards.
     * zh-CN: 用 PDFBox 重建文本, 下拉或列表字段的外观; 失败时把字段加入 [fallback]. 密码字段按星号绘制, 下拉框按其值对应的显示文字绘制
     * (PDFBox 按原样绘制 `/V`), 之后恢复 `/V`.
     */
    private fun refresh(entry: FormFields.Entry, form: PDAcroForm, fallback: MutableSet<FormFields.Entry>) {
        val dictionary = entry.dictionary
        val own = dictionary.getItem(COSName.V)
        val value = FormFields.inherited(dictionary, COSName.V)
        val shown: COSBase? = when {
            entry.type == PdfContract.FORM_FIELD_TEXT && entry.flag(FormFields.FLAG_PASSWORD) -> {
                val text = FormFields.text(value) ?: ""
                COSString("*".repeat(text.codePointCount(0, text.length)))
            }
            entry.type == PdfContract.FORM_FIELD_COMBO -> FormFields.strings(value).firstOrNull()?.let { export ->
                FormFields.choiceOptions(entry.field as PDChoice).firstOrNull { it.first == export }?.second?.takeIf { it != export }?.let { COSString(it) }
            }
            else -> null
        }
        try {
            if (shown != null) dictionary.setItem(COSName.V, shown)
            form.refreshAppearances(listOf<PDField>(entry.field))
        } catch (_: IOException) {
            fallback += entry
        } catch (_: RuntimeException) {
            fallback += entry
        } catch (_: LinkageError) {
            // The standard 14 font metrics did not load (PDFBox resources missing). zh-CN: 标准 14 字体的度量没能载入 (缺少 PDFBox 资源).
            fallback += entry
        } finally {
            if (shown != null) {
                if (own == null) dictionary.removeItem(COSName.V) else dictionary.setItem(COSName.V, own)
            }
        }
    }

    /**
     * `AcroFormDefaultsProcessor`: `/DA` and `/DR` with `/Helv` and `/ZaDb` where missing, so PDFBox can draw a form
     * that relies on the reader's defaults; a failure leaves the form as it is (its fields then use the Canvas fallback).
     * zh-CN: `AcroFormDefaultsProcessor`: 缺少时补上 `/DA` 以及带 `/Helv` 与 `/ZaDb` 的 `/DR`, 使 PDFBox 能绘制依赖阅读器默认值的表单;
     * 失败时保持表单原样 (其字段随后改用 Canvas 回落).
     */
    private fun addDefaults(document: PDDocument) {
        try {
            AcroFormDefaultsProcessor(document).process()
        } catch (_: IOException) {
        } catch (_: RuntimeException) {
        } catch (_: LinkageError) {
        }
    }

    /** Draws the Canvas fallback of [entries] in one pass. zh-CN: 一次绘制 [entries] 的 Canvas 回落. */
    private fun drawFallback(document: PDDocument, form: PDAcroForm, entries: Collection<FormFields.Entry>, drawer: AppearanceDrawer, stop: StopSignal) {
        val targets = entries.flatMap { FieldAppearances.targets(it, form) }
        if (targets.isEmpty()) return
        val forms = drawer.draw(document, targets.map { it.request }, stop)
        check(forms.size == targets.size) { "the appearance drawer made ${forms.size} forms for ${targets.size} widgets" }
        targets.forEachIndexed { index, target -> FieldAppearances.install(document, target, forms[index]) }
    }

    /** Whether the form or one of [entries] (or an ancestor) has value actions. zh-CN: 表单或 [entries] 之一 (或其祖先) 是否带有作用于取值的动作. */
    private fun scripted(form: PDAcroForm, entries: List<FormFields.Entry>): Boolean {
        val order = form.cosObject.getDictionaryObject(COSName.CO) as? COSArray
        if (order != null && order.size() > 0) return true
        return entries.any { entry ->
            var current: COSDictionary? = entry.dictionary
            var depth = 0
            while (current != null && depth <= FormFields.MAX_DEPTH) {
                val actions = current.getDictionaryObject(COSName.AA) as? COSDictionary
                if (actions != null && VALUE_TRIGGERS.any { actions.containsKey(it) }) return@any true
                current = current.getDictionaryObject(COSName.PARENT) as? COSDictionary
                depth++
            }
            false
        }
    }

    /**
     * Flattens [entries]: every widget gets `/P` of the page listing it (PDFBox flattens the widgets of every field on
     * every page when one widget lacks `/P`), PDFBox draws the appearances into the pages and removes the fields, and
     * non-terminal fields left without kids are removed as well.
     * zh-CN: 扁平化 [entries]: 每个控件的 `/P` 设为列出它的页 (只要有一个控件缺少 `/P`, PDFBox 就会把所有页上所有字段的控件都扁平化),
     * 由 PDFBox 把外观画进页面并移除字段, 之后再移除失去全部子项的非终端字段.
     */
    private fun flatten(document: PDDocument, form: PDAcroForm, entries: List<FormFields.Entry>, stop: StopSignal) {
        if (document.numberOfPages == 0) return
        val pages = FormFields.PageIndex(document, stop)
        for (entry in entries) {
            for (widget in widgets(entry)) {
                val page = pages.pageOf(widget.cosObject)
                if (page > 0) {
                    widget.page = document.getPage(page - 1)
                } else if (widget.cosObject.getDictionaryObject(COSName.P) !is COSDictionary) {
                    widget.page = document.getPage(0)
                }
            }
        }
        stop.checkStop()
        try {
            form.flatten(entries.map { it.field }, false)
        } catch (e: IOException) {
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot flatten the form fields: ${e.message}")
        }
        val fields = form.cosObject.getDictionaryObject(COSName.FIELDS) as? COSArray
        for (entry in entries) {
            var parent = entry.dictionary.getDictionaryObject(COSName.PARENT) as? COSDictionary
            var depth = 0
            while (parent != null && depth <= FormFields.MAX_DEPTH) {
                val kids = parent.getDictionaryObject(COSName.KIDS) as? COSArray
                if (kids == null || kids.size() > 0) break
                val grandparent = parent.getDictionaryObject(COSName.PARENT) as? COSDictionary
                val owner = if (grandparent == null) fields else grandparent.getDictionaryObject(COSName.KIDS) as? COSArray
                owner?.removeObject(parent)
                parent = grandparent
                depth++
            }
        }
    }

    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val payload = parse(PdfPayload.read(inputs))
        return PdfLoader.load(DescriptorSource.of(inputs[0]), request.password, directory).use { loaded ->
            val document = loaded.document
            PdfWriting.checkWritable(document, "the source")
            val outcome = apply(document, payload, request.ignoreMissing, CanvasAppearances(directory), operation.stop) { current, total ->
                operation.progress(PdfContract.STAGE_PROCESSING, current, total)
            }
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
            result(document.numberOfPages, size, outcome)
        }
    }

    fun result(pageCount: Int, sizeBytes: Long, outcome: Outcome): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_FILLED, outcome.filled)
        putAll(InfoChanges.result(pageCount, sizeBytes, outcome.warnings))
    }

}

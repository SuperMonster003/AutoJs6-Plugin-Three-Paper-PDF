package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.assertWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/**
 * `fillForm` on the JVM (roadmap P6.4, D62, spike S1): every value type written and read back (Chinese and emoji text,
 * check box, radio, combo, multiple selection list, `null` back to the default), the checks made before anything changes
 * (unknown names with at most five listed or one warning with `ignoreMissing`, read-only, push button and signature
 * fields, wrong types, values outside the options, the maximum length in code points), the appearance requests of the
 * Canvas fallback and the installed appearance streams, static XFA removed, dynamic XFA `UNSUPPORTED`, and the
 * JavaScript and signature warnings. PDFBox-Android cannot build appearances on the JVM (the standard 14 font metrics
 * are Android assets), so every redrawn field takes the Canvas fallback here, drawn by a recording drawer; the PDFBox
 * appearances, the Canvas drawing, flattening (it measures appearance boxes with `android.graphics`) and the rendering
 * run on devices (`FormFillDeviceTest`).
 * zh-CN: JVM 上的 `fillForm` (路线图 P6.4, D62, spike S1): 各类取值写入后读回 (中文与表情文本, 勾选框, 单选, 下拉, 多选列表, `null`
 * 恢复默认值), 修改之前的检查 (未知字段名最多列出五个, 带 `ignoreMissing` 时改为一条警告; 只读, 按钮与签名字段; 类型不符; 不在选项中的
 * 值; 按码点计的最大长度), Canvas 回落的外观请求与安装后的外观流, 静态 XFA 被移除, 动态 XFA 为 `UNSUPPORTED`, 以及 JavaScript 与签名
 * 警告. PDFBox-Android 在 JVM 上无法生成外观 (标准 14 字体的度量是 Android 资源), 因此这里每个重画的字段都走 Canvas 回落, 由记录请求的
 * 绘制器代替; PDFBox 外观, Canvas 绘制, 扁平化 (它用 `android.graphics` 计算外观框) 与渲染在设备上运行 (`FormFillDeviceTest`).
 */
class FormFillTest {

    /** Records the requests and answers an empty form XObject per request. zh-CN: 记录请求, 并为每个请求返回一个空的表单 XObject. */
    private class RecordingDrawer : AppearanceDrawer {

        val requests = ArrayList<AppearanceRequest>()

        override fun draw(document: PDDocument, requests: List<AppearanceRequest>, stop: StopSignal): List<PDFormXObject> {
            this.requests += requests
            return requests.map { request ->
                PDFormXObject(document).apply { bBox = PDRectangle(0f, 0f, ceil(request.width).toFloat(), ceil(request.height).toFloat()) }
            }
        }

    }

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun payload(json: String) = FormFill.parse(PdfPayload.parse(json.toByteArray(Charsets.UTF_8)))

    private fun code(block: () -> Unit): String = assertThrows(PdfFailure::class.java) { block() }.code

    private fun failure(block: () -> Unit): PdfFailure = assertThrows(PdfFailure::class.java) { block() }

    private fun bytes(document: PDDocument): ByteArray = ByteArrayOutputStream().also { PdfWriting.write(document, it, StopSignal.NEVER) }.toByteArray()

    private class Filled(val bytes: ByteArray, val outcome: FormFill.Outcome, val drawer: RecordingDrawer)

    private fun fill(json: String, source: ByteArray = PdfFixtures.form(), ignoreMissing: Boolean = false, prepare: (PDDocument) -> Unit = {}): Filled {
        val drawer = RecordingDrawer()
        PDDocument.load(source).use { document ->
            prepare(document)
            val outcome = FormFill.apply(document, payload(json), ignoreMissing, drawer)
            return Filled(bytes(document), outcome, drawer)
        }
    }

    private fun <T> reopen(bytes: ByteArray, block: (PDDocument) -> T): T = PDDocument.load(bytes).use(block)

    /** Field values read back as `getFormFields` types them. zh-CN: 按 `getFormFields` 的类型读回的字段值. */
    private fun values(bytes: ByteArray): Map<String, Any?> = reopen(bytes) { document ->
        FormFields.terminals(FormFields.acroForm(document)).associate { it.name to FormFields.value(it) }
    }

    private fun entry(document: PDDocument, name: String): FormFields.Entry = FormFields.terminals(FormFields.acroForm(document)).first { it.name == name }

    private fun widgets(document: PDDocument, name: String): List<PDAnnotationWidget> = entry(document, name).field.widgets

    private fun content(stream: COSStream): String = stream.createInputStream().use { String(it.readBytes(), Charsets.ISO_8859_1) }

    private val chinese = "张三" + String(Character.toChars(0x1F600)) + " Zo" + Char(0xEB)

    @Test
    fun `every value type is written and read back`() {
        val filled = fill(
            """{ "values": { "name": "$chinese", "notes": "第一行\n第二行", "agree": false, "size": "large", "country": "Japan",
               "pin": "9876", "code": "123456", "colors": ["b", "g"], "address.city": "北京" } }""",
        )
        assertEquals(9, filled.outcome.filled)
        assertEquals(0, filled.outcome.flattened)
        assertTrue(filled.outcome.warnings.isEmpty())
        val values = values(filled.bytes)
        assertEquals(chinese, values["name"])
        assertEquals("第一行\n第二行", values["notes"])
        assertEquals(false, values["agree"])
        assertEquals("large", values["size"])
        assertEquals("Japan", values["country"])
        assertEquals("9876", values["pin"])
        assertEquals("123456", values["code"])
        // List values in option order. zh-CN: 列表值按选项顺序.
        assertEquals(listOf("g", "b"), values["colors"])
        assertEquals("北京", values["address.city"])
        // Untouched fields keep their values. zh-CN: 未改动的字段保留原值.
        assertEquals("P-001", values["id"])
        assertNull(values["email"])
        reopen(filled.bytes) { document ->
            val colors = entry(document, "colors").dictionary
            assertEquals(listOf(1, 2), (colors.getDictionaryObject(COSName.I) as COSArray).let { array -> (0 until array.size()).map { (array.getObject(it) as COSInteger).intValue() } })
            assertEquals(COSName.Off, widgets(document, "agree").single().cosObject.getDictionaryObject(COSName.AS))
            assertEquals(listOf("Off", "Off", "large"), widgets(document, "size").map { (it.cosObject.getDictionaryObject(COSName.AS) as COSName).name })
            // Every redrawn widget has a normal appearance. zh-CN: 每个重画的控件都有正常外观.
            listOf("name", "notes", "country", "pin", "code", "colors", "address.city").forEach { name ->
                widgets(document, name).forEach { assertNotNull(name, it.appearance?.normalAppearance?.appearanceStream) }
            }
        }
    }

    @Test
    fun `text Helvetica cannot encode goes to the Canvas fallback without NeedAppearances`() {
        val filled = fill("""{ "values": { "name": "$chinese", "pin": "密码" } }""")
        assertTrue(filled.outcome.fallback >= 2)
        val texts = filled.drawer.requests.map { it.text }
        assertTrue(texts.toString(), chinese in texts)
        // A password is drawn as one asterisk per code point. zh-CN: 密码按每个码点一个星号绘制.
        assertTrue(texts.toString(), "**" in texts)
        assertFalse(texts.contains("密码"))
        reopen(filled.bytes) { document ->
            // Roadmap Q242: readers show the fallback appearance instead of redrawing it. zh-CN: 路线图 Q242: 阅读器显示回落外观, 不重画.
            assertFalse(FormFields.acroForm(document)!!.cosObject.getBoolean(COSName.NEED_APPEARANCES, false))
            val widget = widgets(document, "name").single()
            val stream = widget.appearance.normalAppearance.appearanceStream
            assertArrayEquals(floatArrayOf(0f, 0f, widget.rectangle.width, widget.rectangle.height), stream.bBox.cosArray.toFloatArray(), 0.01f)
            val text = content(stream.cosObject)
            assertTrue(text, text.contains("/Tx BMC"))
            assertTrue(text, Regex("""/\S+ Do""").containsMatchIn(text))
            assertEquals(1, stream.resources.xObjectNames.count())
        }
    }

    @Test
    fun `appearance requests carry the shown text, list rows, alignment and the DA size`() {
        PDDocument.load(PdfFixtures.form()).use { document ->
            val form = FormFields.acroForm(document)!!
            val name = FieldAppearances.targets(entry(document, "name"), form).single().request
            assertEquals("Ada Lovelace", name.text)
            assertFalse(name.multiline)
            assertEquals(300.0, name.width, 0.01)
            assertEquals(20.0, name.height, 0.01)
            assertEquals(10.0, name.fontSize, 0.01)
            assertEquals(0, name.align)
            val notes = FieldAppearances.targets(entry(document, "notes"), form).single().request
            assertEquals("Line 1\nLine 2", notes.text)
            assertTrue(notes.multiline)
            assertEquals("****", FieldAppearances.targets(entry(document, "pin"), form).single().request.text)
            assertEquals("France", FieldAppearances.targets(entry(document, "country"), form).single().request.text)
            val colors = FieldAppearances.targets(entry(document, "colors"), form).single().request
            assertNull(colors.text)
            assertEquals(PdfFixtures.FORM_COLORS.map { it.second }, colors.options)
            assertEquals(setOf(0, 2), colors.selected)
            assertEquals(0, colors.topIndex)
        }
        assertEquals("a b c", FieldAppearances.singleLine("a\r\nb\nc"))
        assertEquals(12.0 to 0xFF000000.toInt(), FieldAppearances.parseAppearance("/Helv 12 Tf 0 g"))
        assertEquals(0.0 to 0xFFFF0000.toInt(), FieldAppearances.parseAppearance("/Helv 0 Tf 1 0 0 rg"))
    }

    @Test
    fun `null restores the default value and clears a field without one`() {
        val filled = fill(
            """{ "values": { "name": null, "notes": null, "agree": null, "size": null, "country": null, "colors": null, "address.city": null } }""",
        )
        assertEquals(7, filled.outcome.filled)
        val values = values(filled.bytes)
        assertEquals("Ada", values["name"])
        assertNull(values["notes"])
        assertEquals(false, values["agree"])
        assertEquals("small", values["size"])
        assertEquals("China", values["country"])
        assertEquals(listOf("g"), values["colors"])
        assertNull(values["address.city"])
        reopen(filled.bytes) { document ->
            assertEquals(listOf("small", "Off", "Off"), widgets(document, "size").map { (it.cosObject.getDictionaryObject(COSName.AS) as COSName).name })
        }
    }

    @Test
    fun `a check box is set to its on state`() {
        val off = fill("""{ "values": { "agree": false } }""")
        val on = fill("""{ "values": { "agree": true } }""", off.bytes)
        assertEquals(true, values(on.bytes)["agree"])
        reopen(on.bytes) { document ->
            assertEquals("Yes", (entry(document, "agree").dictionary.getDictionaryObject(COSName.V) as COSName).name)
            assertEquals("Yes", (widgets(document, "agree").single().cosObject.getDictionaryObject(COSName.AS) as COSName).name)
        }
    }

    @Test
    fun `text length counts code points against the maximum length`() {
        val emoji = String(Character.toChars(0x1F600))
        assertEquals("12345$emoji", values(fill("""{ "values": { "code": "12345$emoji" } }""").bytes)["code"])
        val failure = failure { fill("""{ "values": { "code": "1234567" } }""") }
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, failure.code)
        assertTrue(failure.message!!, failure.message!!.contains("7 characters") && failure.message!!.contains("maximum length 6"))
    }

    @Test
    fun `unknown names fail listing at most five unless ignoreMissing`() {
        val names = (1..7).joinToString(", ") { "\"u$it\": \"x\"" }
        val failure = failure { fill("""{ "values": { "name": "Bob", $names } }""") }
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, failure.code)
        val message = failure.message!!
        assertTrue(message, message.contains("7 field names match no form field: 'u1', 'u2', 'u3', 'u4', 'u5' and 2 more"))
        assertFalse(message, message.contains("'u6'"))
        assertTrue(message, message.contains(PdfContract.KEY_IGNORE_MISSING))
        // Flatten names are checked as well. zh-CN: flatten 中的名称同样检查.
        assertTrue(failure { fill("""{ "values": {}, "flatten": ["nope"] }""") }.message!!.contains("'nope'"))

        val ignored = fill("""{ "values": { "name": "Bob", "u1": "x" }, "flatten": ["u2"] }""", ignoreMissing = true)
        assertEquals(1, ignored.outcome.filled)
        assertEquals(listOf("2 field names match no form field and were ignored: 'u1', 'u2'"), ignored.outcome.warnings.map { it.text })
        assertWarning(ignored.outcome.warnings.single(), PdfWarningCodes.FIELDS_IGNORED)
        assertEquals(
            mapOf(PdfWarningCodes.ARG_COUNT to 2, PdfWarningCodes.ARG_NAMES to "'u1', 'u2'", PdfWarningCodes.ARG_MORE to 0),
            ignored.outcome.warnings.single().args,
        )
        assertEquals("Bob", values(ignored.bytes)["name"])
    }

    @Test
    fun `read-only, push button, signature fields and wrong types are rejected before any change`() {
        fun rejected(json: String, source: ByteArray = PdfFixtures.form()): String {
            val failure = failure { fill(json, source) }
            assertEquals(json, PdfErrorCodes.INVALID_ARGUMENT, failure.code)
            return failure.message!!
        }
        assertTrue(rejected("""{ "values": { "id": "X" } }""").contains("read-only"))
        assertTrue(rejected("""{ "values": { "id": null } }""").contains("read-only"))
        assertTrue(rejected("""{ "values": { "submit": "X" } }""").contains("push button"))
        assertTrue(rejected("""{ "values": { "${PdfFixtures.UNSIGNED_SIGNATURE}": "X" } }""", PdfFixtures.signatureAndXfa()).contains("signature field"))
        assertTrue(rejected("""{ "values": { "name": 1 } }""").contains("must be a string or null"))
        assertTrue(rejected("""{ "values": { "agree": "yes" } }""").contains("must be a boolean or null"))
        assertTrue(rejected("""{ "values": { "size": true } }""").contains("must be a string or null"))
        assertTrue(rejected("""{ "values": { "size": "huge" } }""").contains("one of the options ('small', 'medium', 'large'), not 'huge'"))
        assertTrue(rejected("""{ "values": { "country": "Germany" } }""").contains("not 'Germany'"))
        assertTrue(rejected("""{ "values": { "colors": "x" } }""").contains("not 'x'"))
        assertTrue(rejected("""{ "values": { "colors": ["r", 1] } }""").isNotEmpty())
        assertTrue(rejected("""{ "values": { "colors": { "r": true } } }""").contains("a string, an array of strings or null"))
        assertTrue(rejected("""{ "values": "name" }""").isNotEmpty())
        assertTrue(rejected("""{ "values": {}, "flatten": "all" }""").contains("a boolean or an array of field names"))
        assertTrue(rejected("""{ "values": {}, "flatten": [1] }""").isNotEmpty())
        assertTrue(rejected("""{}""").contains("is required"))

        // The first values are valid; nothing is written when a later one is not. zh-CN: 前面的值有效, 后面的值无效时什么都不写.
        PDDocument.load(PdfFixtures.form()).use { document ->
            assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { FormFill.apply(document, payload("""{ "values": { "name": "Bob", "agree": false, "id": "X" } }"""), false, RecordingDrawer()) })
            assertEquals("Ada Lovelace", FormFields.value(entry(document, "name")))
            assertEquals(true, FormFields.value(entry(document, "agree")))
        }
    }

    @Test
    fun `a single selection list takes one value`() {
        val failure = failure {
            fill("""{ "values": { "colors": ["r", "g"] } }""", prepare = { document ->
                val colors = entry(document, "colors").dictionary
                colors.setInt(COSName.FF, colors.getInt(COSName.FF) and FormFields.FLAG_MULTI_SELECT.inv())
            })
        }
        assertTrue(failure.message!!, failure.message!!.contains("allows one selection"))
        val single = fill("""{ "values": { "colors": "b" } }""", prepare = { document ->
            val colors = entry(document, "colors").dictionary
            colors.setInt(COSName.FF, colors.getInt(COSName.FF) and FormFields.FLAG_MULTI_SELECT.inv())
        })
        assertEquals(listOf("b"), values(single.bytes)["colors"])
        reopen(single.bytes) { document -> assertNull(entry(document, "colors").dictionary.getDictionaryObject(COSName.I)) }
    }

    @Test
    fun `an editable combo box takes a value outside its options`() {
        val filled = fill("""{ "values": { "country": "Atlantis" } }""", prepare = { document ->
            val country = entry(document, "country").dictionary
            country.setInt(COSName.FF, country.getInt(COSName.FF) or FormFields.FLAG_EDIT)
        })
        assertEquals("Atlantis", values(filled.bytes)["country"])
    }

    @Test
    fun `static XFA is removed and a signed signature gives a warning`() {
        val filled = fill("""{ "values": { "name": "Bob" } }""", PdfFixtures.signatureAndXfa())
        assertEquals(2, filled.outcome.warnings.size)
        assertTrue(filled.outcome.warnings[0].text, filled.outcome.warnings[0].text.contains("1 signed signature field"))
        assertTrue(filled.outcome.warnings[1].text, filled.outcome.warnings[1].text.contains("XFA"))
        assertWarning(filled.outcome.warnings[0], PdfWarningCodes.SIGNATURES_INVALIDATED)
        assertEquals(1, filled.outcome.warnings[0].args[PdfWarningCodes.ARG_COUNT])
        assertWarning(filled.outcome.warnings[1], PdfWarningCodes.XFA_REMOVED)
        reopen(filled.bytes) { document ->
            assertNull(FormFields.acroForm(document)!!.cosObject.getDictionaryObject(COSName.XFA))
            assertEquals("Bob", FormFields.value(entry(document, "name")))
        }
    }

    @Test
    fun `dynamic XFA is UNSUPPORTED`() {
        assertEquals(PdfErrorCodes.UNSUPPORTED, code { fill("""{ "values": {} }""", PdfFixtures.dynamicXfa()) })
    }

    @Test
    fun `value actions and a calculation order give the JavaScript warning`() {
        fun warnings(prepare: (PDDocument) -> Unit) = fill("""{ "values": { "name": "Bob" } }""", prepare = prepare).outcome.warnings
        assertTrue(warnings { }.isEmpty())
        val keystroke = warnings { document -> entry(document, "name").dictionary.setItem(COSName.AA, COSDictionary().apply { setItem(COSName.K, COSDictionary()) }) }
        assertTrue(keystroke.toString(), keystroke.single().text.contains("JavaScript"))
        assertWarning(keystroke.single(), PdfWarningCodes.FORM_SCRIPTS_NOT_RUN)
        val calculation = warnings { document -> FormFields.acroForm(document)!!.cosObject.setItem(COSName.CO, COSArray().apply { add(entry(document, "code").dictionary) }) }
        assertTrue(calculation.toString(), calculation.single().text.contains("JavaScript"))
        // Actions of other fields or other triggers do not count. zh-CN: 其他字段的动作或其他触发条件不算.
        assertTrue(warnings { document -> entry(document, "code").dictionary.setItem(COSName.AA, COSDictionary().apply { setItem(COSName.K, COSDictionary()) }) }.isEmpty())
        assertTrue(warnings { document -> entry(document, "name").dictionary.setItem(COSName.AA, COSDictionary().apply { setItem(COSName.getPDFName("Fo"), COSDictionary()) }) }.isEmpty())
    }

    @Test
    fun `a document without a form takes an empty fill`() {
        val filled = fill("""{ "values": {} }""", PdfFixtures.singlePageText())
        assertEquals(0, filled.outcome.filled)
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { fill("""{ "values": { "name": "x" } }""", PdfFixtures.singlePageText()) })
    }

    @Test
    fun `the request takes two inputs and ignoreMissing, and the result has filled`() {
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code { FormFill.request(options(), 1) })
        assertFalse(FormFill.request(options(), 2).ignoreMissing)
        assertTrue(FormFill.request(options(PdfContract.KEY_IGNORE_MISSING to true), 2).ignoreMissing)
        assertNull(FormFill.request(options(PdfContract.KEY_PASSWORD to ""), 2).password)
        val warning = PdfWarning("w", PdfWarningCodes.XFA_REMOVED)
        val result = FormFill.result(2, 100L, FormFill.Outcome(3, 1, 0, listOf(warning)))
        assertEquals(3, result[PdfContract.KEY_FILLED])
        assertEquals(2, result[PdfContract.KEY_PAGE_COUNT])
        assertEquals(100L, result[PdfContract.KEY_SIZE_BYTES])
        assertEquals(listOf(warning), result[PdfContract.KEY_WARNINGS])
        assertFalse(FormFill.result(2, 100L, FormFill.Outcome(0, 0, 0, emptyList())).containsKey(PdfContract.KEY_WARNINGS))
    }

}

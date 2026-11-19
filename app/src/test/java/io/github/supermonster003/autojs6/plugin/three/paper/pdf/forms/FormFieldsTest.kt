package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `getFormFields` and the `getInfo` form keys on the JVM (roadmap P6.4, D62): the entry of every field type of
 * `PdfFixtures.form` (value, default value, options, label, flags, maximum length, page, rectangle and widgets), paging
 * with `offset` / `limit`, the answer budget, and `formFieldCount` / `xfa` of contract version 2 only.
 * zh-CN: JVM 上的 `getFormFields` 与 `getInfo` 表单键 (路线图 P6.4, D62): `PdfFixtures.form` 中每种字段类型的条目 (值, 默认值,
 * 选项, 替代名称, 标志, 最大长度, 页, 矩形与控件), `offset` / `limit` 分页, 应答体积上限, 以及只在契约版本 2 中出现的
 * `formFieldCount` / `xfa`.
 */
class FormFieldsTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun <T> open(bytes: ByteArray, block: (PDDocument) -> T): T = PDDocument.load(bytes).use(block)

    private fun items(answer: Map<String, Any?>): List<JSONObject> = JSONArray(answer[PdfContract.KEY_ITEMS] as String).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun fields(bytes: ByteArray = PdfFixtures.form()): Map<String, JSONObject> = open(bytes) { document ->
        items(FormFields.read(document, options(), StopSignal.NEVER)).associateBy { it.getString(PdfContract.FIELD_NAME) }
    }

    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }

    private fun options(item: JSONObject): List<Pair<String, String>> = item.getJSONArray(PdfContract.FIELD_OPTIONS).let { array ->
        (0 until array.length()).map { array.getJSONObject(it).let { option -> option.getString(PdfContract.FIELD_VALUE) to option.getString(PdfContract.FIELD_LABEL) } }
    }

    @Test
    fun `every terminal field is listed in tree order with its D62 type`() {
        val fields = fields()
        assertEquals(PdfFixtures.FORM_FIELDS.keys.toList(), fields.keys.toList())
        PdfFixtures.FORM_FIELDS.forEach { (name, type) -> assertEquals(name, type, fields.getValue(name).getString(PdfContract.FIELD_TYPE)) }
    }

    @Test
    fun `values and default values are typed per field type`() {
        val fields = fields()
        fun value(name: String, key: String = PdfContract.FIELD_VALUE): Any? = fields.getValue(name).get(key).takeIf { it != JSONObject.NULL }
        assertEquals("Ada Lovelace", value("name"))
        assertEquals("Ada", value("name", PdfContract.FIELD_DEFAULT_VALUE))
        assertEquals("Line 1\nLine 2", value("notes"))
        assertNull(value("notes", PdfContract.FIELD_DEFAULT_VALUE))
        assertEquals(true, value("agree"))
        assertEquals(false, value("agree", PdfContract.FIELD_DEFAULT_VALUE))
        assertEquals("medium", value("size"))
        assertEquals("small", value("size", PdfContract.FIELD_DEFAULT_VALUE))
        assertEquals("France", value("country"))
        assertEquals("China", value("country", PdfContract.FIELD_DEFAULT_VALUE))
        assertEquals(listOf("r", "b"), strings(value("colors") as JSONArray))
        assertEquals(listOf("g"), strings(value("colors", PdfContract.FIELD_DEFAULT_VALUE) as JSONArray))
        assertNull(value("email"))
        assertEquals("1234", value("pin"))
        assertNull(value("submit"))
        assertEquals("Paris", value("address.city"))
    }

    @Test
    fun `options, labels, flags and the maximum length follow the field dictionaries`() {
        val fields = fields()
        assertEquals(PdfFixtures.FORM_SIZES.map { it to it }, options(fields.getValue("size")))
        assertEquals(PdfFixtures.FORM_COUNTRIES.map { it to it }, options(fields.getValue("country")))
        assertEquals(PdfFixtures.FORM_COLORS, options(fields.getValue("colors")))
        assertFalse(fields.getValue("name").has(PdfContract.FIELD_OPTIONS))

        assertEquals(PdfFixtures.FORM_NAME_LABEL, fields.getValue("name").getString(PdfContract.FIELD_LABEL))
        assertEquals("Email address", fields.getValue("email").getString(PdfContract.FIELD_LABEL))
        assertFalse(fields.getValue("notes").has(PdfContract.FIELD_LABEL))

        fun flag(name: String, key: String) = fields.getValue(name).getBoolean(key)
        assertTrue(flag("notes", PdfContract.FIELD_MULTILINE))
        assertFalse(flag("name", PdfContract.FIELD_MULTILINE))
        assertTrue(flag("id", PdfContract.FIELD_READ_ONLY))
        assertFalse(flag("name", PdfContract.FIELD_READ_ONLY))
        assertTrue(flag("email", PdfContract.FIELD_REQUIRED))
        assertFalse(flag("name", PdfContract.FIELD_REQUIRED))
        assertTrue(flag("pin", PdfContract.FIELD_PASSWORD))
        assertFalse(flag("name", PdfContract.FIELD_PASSWORD))
        assertTrue(flag("colors", PdfContract.FIELD_MULTI_SELECT))
        assertEquals(PdfFixtures.FORM_MAX_LENGTH, fields.getValue("code").getInt(PdfContract.FIELD_MAX_LENGTH))
        assertFalse(fields.getValue("name").has(PdfContract.FIELD_MAX_LENGTH))
        // Type-specific keys only on their type. zh-CN: 类型专属的键只出现在对应类型上.
        assertFalse(fields.getValue("agree").has(PdfContract.FIELD_MULTILINE))
        assertFalse(fields.getValue("country").has(PdfContract.FIELD_MULTI_SELECT))
        assertFalse(fields.getValue("name").has(PdfContract.FIELD_SIGNED))
    }

    @Test
    fun `pages and rectangles are in display points with one widget entry per widget`() {
        val fields = fields()
        val name = fields.getValue("name")
        assertEquals(1, name.getInt(PdfContract.FIELD_PAGE))
        val rect = name.getJSONArray(PdfContract.FIELD_RECT)
        assertEquals(4, rect.length())
        // 300 x 20 pt from x 200; the display y grows downwards. zh-CN: 宽 300 高 20 pt, 从 x 200 起; 显示坐标的 y 向下增长.
        assertEquals(200.0, rect.getDouble(0), 0.01)
        assertEquals(500.0, rect.getDouble(2), 0.01)
        assertEquals(20.0, rect.getDouble(3) - rect.getDouble(1), 0.01)
        assertEquals(1, name.getJSONArray(PdfContract.FIELD_WIDGETS).length())
        val size = fields.getValue("size").getJSONArray(PdfContract.FIELD_WIDGETS)
        assertEquals(PdfFixtures.FORM_SIZES.size, size.length())
        (0 until size.length()).forEach { assertEquals(1, size.getJSONObject(it).getInt(PdfContract.FIELD_PAGE)) }
        assertEquals(2, fields.getValue("colors").getInt(PdfContract.FIELD_PAGE))
        assertEquals(2, fields.getValue("address.city").getInt(PdfContract.FIELD_PAGE))
    }

    @Test
    fun `a widget no page lists has page 0 and no rectangle`() {
        val bytes = open(PdfFixtures.form()) { document ->
            val page = document.getPage(1).cosObject
            (page.getDictionaryObject(COSName.ANNOTS) as COSArray).clear()
            java.io.ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        val city = fields(bytes).getValue("address.city")
        assertEquals(0, city.getInt(PdfContract.FIELD_PAGE))
        assertFalse(city.has(PdfContract.FIELD_RECT))
        assertEquals(0, city.getJSONArray(PdfContract.FIELD_WIDGETS).length())
    }

    @Test
    fun `signature fields say whether they are signed`() {
        val fields = fields(PdfFixtures.signatureAndXfa())
        assertEquals(PdfContract.FORM_FIELD_SIGNATURE, fields.getValue(PdfFixtures.UNSIGNED_SIGNATURE).getString(PdfContract.FIELD_TYPE))
        assertFalse(fields.getValue(PdfFixtures.UNSIGNED_SIGNATURE).getBoolean(PdfContract.FIELD_SIGNED))
        assertTrue(fields.getValue(PdfFixtures.SIGNED_SIGNATURE).getBoolean(PdfContract.FIELD_SIGNED))
        // A signature value is not a field value. zh-CN: 签名值不是字段值.
        assertEquals(JSONObject.NULL, fields.getValue(PdfFixtures.SIGNED_SIGNATURE).get(PdfContract.FIELD_VALUE))
    }

    @Test
    fun `offset and limit page the fields and hasMore gives the next offset`() {
        open(PdfFixtures.form()) { document ->
            val first = FormFields.read(document, options(PdfContract.KEY_LIMIT to 5), StopSignal.NEVER)
            assertEquals(PdfFixtures.FORM_FIELDS.keys.take(5), items(first).map { it.getString(PdfContract.FIELD_NAME) })
            assertEquals(true, first[PdfContract.KEY_HAS_MORE])
            assertEquals(5, first[PdfContract.KEY_NEXT_OFFSET])
            val last = FormFields.read(document, options(PdfContract.KEY_OFFSET to 10, PdfContract.KEY_LIMIT to 5), StopSignal.NEVER)
            assertEquals(PdfFixtures.FORM_FIELDS.keys.drop(10), items(last).map { it.getString(PdfContract.FIELD_NAME) })
            assertEquals(false, last[PdfContract.KEY_HAS_MORE])
            assertFalse(last.containsKey(PdfContract.KEY_NEXT_OFFSET))
            val past = FormFields.read(document, options(PdfContract.KEY_OFFSET to 100), StopSignal.NEVER)
            assertTrue(items(past).isEmpty())
            assertEquals(false, past[PdfContract.KEY_HAS_MORE])
        }
    }

    @Test
    fun `the answer budget cuts the page and one field over it is LIMIT_EXCEEDED`() {
        open(PdfFixtures.form()) { document ->
            val one = items(FormFields.read(document, options(PdfContract.KEY_LIMIT to 1), StopSignal.NEVER)).single().toString().length
            val cut = FormFields.read(document, options(), StopSignal.NEVER, budgetChars = one + 2)
            assertEquals(1, items(cut).size)
            assertEquals(true, cut[PdfContract.KEY_HAS_MORE])
            assertEquals(1, cut[PdfContract.KEY_NEXT_OFFSET])
            val failure = assertThrows(PdfFailure::class.java) { FormFields.read(document, options(), StopSignal.NEVER, budgetChars = 10) }
            assertEquals(PdfErrorCodes.LIMIT_EXCEEDED, failure.code)
            assertTrue(failure.message!!, failure.message!!.contains("'name'"))
        }
    }

    @Test
    fun `a document without a form or with a malformed one has no fields`() {
        PDDocument().use { document ->
            document.addPage(PDPage())
            assertTrue(items(FormFields.read(document, options(), StopSignal.NEVER)).isEmpty())
            document.documentCatalog.cosObject.setItem(COSName.ACRO_FORM, COSDictionary().apply { setItem(COSName.FIELDS, COSName.A) })
            assertTrue(items(FormFields.read(document, options(), StopSignal.NEVER)).isEmpty())
        }
    }

    @Test
    fun `a field tree cycle is visited once`() {
        PDDocument.load(PdfFixtures.form()).use { document ->
            val form = FormFields.acroForm(document)!!
            val address = (form.cosObject.getDictionaryObject(COSName.FIELDS) as COSArray).let { fields ->
                (0 until fields.size()).map { fields.getObject(it) as COSDictionary }.first { it.getString(COSName.T) == "address" }
            }
            (address.getDictionaryObject(COSName.KIDS) as COSArray).add(address)
            assertEquals(PdfFixtures.FORM_FIELDS.size, FormFields.terminals(FormFields.acroForm(document)).size)
        }
    }

    @Test
    fun `getInfo of contract version 2 has formFieldCount and xfa, version 1 neither`() {
        fun info(bytes: ByteArray, version: Int) = open(bytes) { DocumentInfoReader.read(it, bytes.size.toLong(), StopSignal.NEVER, version) }
        val form = info(PdfFixtures.form(), PdfContract.CONTRACT_VERSION_2)
        assertEquals(PdfFixtures.FORM_FIELDS.size, form[PdfContract.KEY_FORM_FIELD_COUNT])
        assertFalse(form.containsKey(PdfContract.KEY_XFA))
        val legacy = info(PdfFixtures.form(), PdfContract.CONTRACT_VERSION)
        assertFalse(legacy.containsKey(PdfContract.KEY_FORM_FIELD_COUNT))
        assertFalse(legacy.containsKey(PdfContract.KEY_XFA))
        val static = info(PdfFixtures.signatureAndXfa(), PdfContract.CONTRACT_VERSION_2)
        assertEquals(3, static[PdfContract.KEY_FORM_FIELD_COUNT])
        assertEquals(PdfContract.XFA_STATIC, static[PdfContract.KEY_XFA])
        val dynamic = info(PdfFixtures.dynamicXfa(), PdfContract.CONTRACT_VERSION_2)
        assertEquals(0, dynamic[PdfContract.KEY_FORM_FIELD_COUNT])
        assertEquals(PdfContract.XFA_DYNAMIC, dynamic[PdfContract.KEY_XFA])
        val plain = info(PdfFixtures.singlePageText(), PdfContract.CONTRACT_VERSION_2)
        assertEquals(0, plain[PdfContract.KEY_FORM_FIELD_COUNT])
        assertFalse(plain.containsKey(PdfContract.KEY_XFA))
    }

}

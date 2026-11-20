package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.autojs.plugin.pdf.api.PdfContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen state of the editing tools of roadmap P6.12 (D68): when each tool can run, the start number of appendix D
 * Q211, the form fields of D47 and the document properties of D52.
 * zh-CN: 路线图 P6.12 编辑类工具的界面状态 (D68): 各工具何时可以运行, 附录 D Q211 的起始号, D47 的表单字段以及 D52 的文档属性.
 */
class EditToolStatesTest {

    @Test
    fun `a watermark needs text or an image and switching type resets the rotation`() {
        val state = WatermarkState()
        assertNull(state.options(null))
        state.text = "   "
        assertNull(state.options(null))
        state.text = "DRAFT"
        state.opacity.choose(50)
        state.fontSize.choose(36)
        val text = checkNotNull(state.options("1-3"))
        assertEquals(45.0, text.rotation, 0.0)
        assertEquals(0.5, text.opacity, 0.0)
        assertEquals(36.0, checkNotNull(text.fontSize), 0.0)
        assertEquals("1-3", text.pages)
        assertNull(text.width)

        state.selectType(PdfContract.STAMP_IMAGE)
        assertEquals(0, state.rotation.preset)
        assertNull(state.options(null))
        state.rotation.choose(90)
        state.selectType(PdfContract.STAMP_IMAGE)
        assertEquals(90, state.rotation.preset)
        state.selectType(PdfContract.STAMP_TEXT)
        assertEquals(45, state.rotation.preset)

        state.text = "x".repeat(PdfContract.MAX_STAMP_TEXT_LENGTH + 1)
        assertTrue(state.textTooLong())
        assertNull(state.options(null))
    }

    @Test
    fun `page numbers check the start number and need something to add`() {
        val state = PageNumbersState()
        assertEquals(1, checkNotNull(state.options(null)).start)
        state.start = " 7 "
        assertEquals(7, checkNotNull(state.options(null)).start)
        listOf("", "-1", "abc", (PageNumbersState.MAX_START + 1).toString()).forEach {
            state.start = it
            assertTrue(it, state.startInvalid())
            assertNull(it, state.options(null))
        }
        state.start = "0"
        assertFalse(state.startInvalid())

        state.format = " "
        assertNull(state.options(null))
        state.format = "{page}"

        state.numbered = false
        assertFalse(state.startInvalid())
        assertNull(state.options(null))
        state.footer = ToolOptions.Slots(right = "{date}")
        val slotsOnly = checkNotNull(state.options("2-"))
        assertNull(slotsOnly.format)
        assertEquals(1, slotsOnly.start)
        assertEquals("2-", slotsOnly.pages)
    }

    @Test
    fun `a slot at the page number position overlaps it and long texts are refused`() {
        val state = PageNumbersState()
        assertFalse(state.overlaps())
        state.footer = ToolOptions.Slots(center = "Footer")
        assertTrue(state.overlaps())
        state.position = PdfContract.POSITION_BOTTOM_RIGHT
        assertFalse(state.overlaps())
        state.numbered = false
        state.position = PdfContract.POSITION_BOTTOM_CENTER
        assertFalse(state.overlaps())

        state.header = ToolOptions.Slots(left = "x".repeat(PdfContract.MAX_STAMP_TEXT_LENGTH + 1))
        assertTrue(state.tooLong())
        assertNull(state.options(null))
        assertEquals("1 / 12", PageNumbersState.sample("{page} / {total}"))
        assertEquals(1, PageNumbersState.startNumber("1"))
        assertNull(PageNumbersState.startNumber("1.5"))
    }

    @Test
    fun `compress presets set their numbers and unknown names fall back to the default`() {
        val state = CompressState()
        val medium = CompressState.numbersOf(PdfContract.DEFAULT_COMPRESS_PRESET)
        assertEquals(PdfContract.DEFAULT_COMPRESS_PRESET, state.preset)
        assertEquals(medium.imageDpi, state.imageDpi.preset)
        assertEquals(medium.imageQuality, state.imageQuality.preset)

        state.selectPreset(PdfContract.COMPRESS_PRESET_HIGH)
        val high = CompressState.numbersOf(PdfContract.COMPRESS_PRESET_HIGH)
        assertEquals(PdfContract.COMPRESS_PRESET_HIGH, state.preset)
        assertEquals(high.imageDpi, state.imageDpi.preset)
        // The presets run from the smallest output to the best images. zh-CN: 预设从输出最小排到图片最好.
        assertTrue(high.imageDpi > medium.imageDpi)

        state.imageQuality.choose(40)
        state.removeMetadata = true
        state.objectStreams = false
        assertEquals(ToolOptions.Compress(PdfContract.COMPRESS_PRESET_HIGH, high.imageDpi, 40, true, false), state.options())

        state.selectPreset("unknown")
        assertEquals(PdfContract.DEFAULT_COMPRESS_PRESET, state.preset)
        assertEquals(CompressState.PRESETS.size, CompressState.PRESETS.map { CompressState.numbersOf(it).imageDpi }.distinct().size)
    }

    @Test
    fun `form fields keep their value types, labels and markers`() {
        val fields = FormFieldItem.parse(FORM)
        val byName = fields.associateBy { it.name }
        assertEquals("Ada", byName.getValue("name").value)
        assertTrue(byName.getValue("name").required)
        assertEquals(20, byName.getValue("name").maxLength)
        assertEquals("Full name", byName.getValue("name").title)
        assertEquals(true, byName.getValue("agree").value)
        assertEquals(listOf("red"), byName.getValue("colors").value)
        assertEquals(listOf("b"), byName.getValue("single").value)
        assertNull(byName.getValue("city").value)
        assertEquals(listOf(FormChoice("bj", "Beijing"), FormChoice("sh", "sh")), byName.getValue("city").choices)
        assertEquals("city", byName.getValue("city").title)
        assertFalse(byName.getValue("id").fillable)
        assertFalse(byName.getValue("sig").fillable)
        assertTrue(byName.getValue("sig").signed)
        assertTrue(byName.getValue("colors").multiSelect)
        assertEquals(0, byName.getValue("sig").page)
    }

    @Test
    fun `form values compare the way the form shows them`() {
        assertTrue(FormValues.same(null, ""))
        assertTrue(FormValues.same(null, false))
        assertFalse(FormValues.same(true, null))
        assertTrue(FormValues.same(listOf("b", "a"), listOf("a", "b")))
        assertTrue(FormValues.same(null, emptyList<String>()))
        assertFalse(FormValues.same("a", "b"))
        assertTrue(FormValues.empty(null))
        assertTrue(FormValues.empty(""))
        assertTrue(FormValues.empty(emptyList<String>()))
        assertFalse(FormValues.empty(false))
        assertFalse(FormValues.empty(" "))
    }

    @Test
    fun `filling keeps only changed values, counts missing required fields and groups by page`() {
        val state = FillFormState()
        assertNull(state.options())
        val fields = FormFieldItem.parse(FORM)
        state.show(fields, more = false, xfaKind = null)
        val byName = fields.associateBy { it.name }

        assertEquals(listOf(1, 2, 0), state.groups().map { it.first })
        assertTrue(state.folded.isEmpty())
        assertEquals(0, state.missingRequired())
        assertNull(state.options())

        state.set(byName.getValue("name"), "")
        assertEquals(1, state.missingRequired())
        state.set(byName.getValue("name"), "Ada")
        assertTrue(state.edits.isEmpty())
        state.set(byName.getValue("id"), "X-2")
        assertTrue(state.edits.isEmpty())
        assertEquals("X-1", state.valueOf(byName.getValue("id")))

        state.set(byName.getValue("colors"), listOf("blue", "red"))
        state.set(byName.getValue("city"), "bj")
        val options = checkNotNull(state.options())
        assertEquals(mapOf("colors" to listOf("blue", "red"), "city" to "bj"), options.values)
        assertFalse(options.flatten)

        state.edits.clear()
        state.flatten = true
        assertTrue(checkNotNull(state.options()).values.isEmpty())

        state.xfa = PdfContract.XFA_DYNAMIC
        assertTrue(state.dynamicXfa())
        assertNull(state.options())
        state.xfa = PdfContract.XFA_STATIC
        state.error = "boom"
        assertNull(state.options())
    }

    @Test
    fun `many form fields fold every page group but the first`() {
        val many = (1..41).map { index -> FormFieldItem.parse(FORM).first().copy(name = "f$index", page = (index % 3) + 1) }
        val state = FillFormState()
        state.show(many, more = true, xfaKind = null)
        assertTrue(state.truncated)
        assertEquals(mapOf(2 to true, 3 to true), state.folded.toMap())
        state.reset()
        assertTrue(state.fields.isEmpty() && state.folded.isEmpty() && !state.truncated)
    }

    @Test
    fun `outline entries default to no page and the top level`() {
        val items = OutlineItem.parse("""[{"title":"One","page":1,"level":0},{"title":"Sub"},"skip"]""")
        assertEquals(listOf(OutlineItem("One", 1, 0), OutlineItem("Sub", 0, 0)), items)
    }

    @Test
    fun `properties send only changed keys, a blank one as removal`() {
        val state = PropertiesState()
        assertNull(state.options())
        state.show(mapOf(PdfContract.FIELD_TITLE to "Report", PdfContract.FIELD_AUTHOR to "Ada", "pages" to 3), emptyList(), more = false)
        assertEquals("", state.edited[PdfContract.FIELD_SUBJECT])
        assertNull(state.options())

        state.edited[PdfContract.FIELD_SUBJECT] = "  "
        assertNull(state.options())
        state.edited[PdfContract.FIELD_AUTHOR] = " "
        state.edited[PdfContract.FIELD_TITLE] = "Annual report"
        val options = checkNotNull(state.options())
        assertEquals(listOf(PdfContract.FIELD_TITLE, PdfContract.FIELD_AUTHOR), options.info.keys.toList())
        assertEquals("Annual report", options.info[PdfContract.FIELD_TITLE])
        assertNull(options.info[PdfContract.FIELD_AUTHOR])
        assertFalse(options.clearOutline)

        state.edited[PdfContract.FIELD_KEYWORDS] = "x".repeat(PdfContract.MAX_INFO_VALUE_LENGTH + 1)
        assertTrue(state.tooLong())
        assertNull(state.options())

        state.show(emptyMap(), listOf(OutlineItem("One", 1, 0)), more = true)
        assertNull(state.options())
        state.clearOutline = true
        assertEquals(ToolOptions.Properties(emptyMap(), true), state.options())
        assertTrue(state.outlineMore)
    }

    @Test
    fun `info dates are shown with a space and a readable zone`() {
        assertEquals("2026-11-19 20:30:15 +08:00", PropertiesState.displayDate("2026-11-19T20:30:15+08:00"))
        assertEquals("2026-11-19 12:30:15 UTC", PropertiesState.displayDate("2026-11-19T12:30:15Z"))
        assertEquals("2026-11-19 12:30", PropertiesState.displayDate("2026-11-19T12:30"))
        assertEquals("D:20261119", PropertiesState.displayDate("D:20261119"))
    }

    private companion object {

        val FORM = """
            [
              {"name":"name","type":"text","value":"Ada","label":"Full name","required":true,"maxLength":20,"page":1},
              {"name":"agree","type":"checkbox","value":true,"page":1},
              {"name":"colors","type":"list","value":["red"],"multiSelect":true,"options":[{"value":"red"},{"value":"blue"}],"page":2},
              {"name":"single","type":"list","value":"b","page":2},
              {"name":"city","type":"combo","value":null,"label":"","options":[{"value":"bj","label":"Beijing"},{"value":"sh","label":null}],"page":2},
              {"name":"id","type":"text","value":"X-1","readOnly":true,"page":1},
              {"name":"sig","type":"signature","signed":true}
            ]
        """.trimIndent()

    }

}

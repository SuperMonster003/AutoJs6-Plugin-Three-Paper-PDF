package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import androidx.lifecycle.SavedStateHandle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.StampItems
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.TextStamp
import org.autojs.plugin.pdf.api.PdfContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Custom" values of the editing tools (appendix D Q265 (b), Q290): every number option takes the values the engine
 * takes, boundaries included, and refuses the rest and text that is not a number (the options are then null, so the
 * start button is off); a typed colour is `#RRGGBB` or `#AARRGGBB`; typed values survive the recreation of the state.
 * zh-CN: 编辑类工具的 "自定义" 值 (附录 D Q265 (b), Q290): 每个数值项接受引擎接受的值 (含边界), 拒绝其余的值与非数字 (此时选项为
 * null, 开始按钮不可用); 输入的颜色为 `#RRGGBB` 或 `#AARRGGBB`; 输入的值在状态重建后仍在.
 */
class CustomValuesTest {

    /**
     * One number option: [field] is its field, [option] the value the tool's options carry (null when they are null);
     * [accepted] pairs typed texts with that value, [refused] are texts out of range or not numbers.
     * zh-CN: 一个数值项: [field] 是它的输入字段, [option] 是工具选项携带的值 (选项为 null 时为 null); [accepted] 是输入文字与该值的
     * 对应, [refused] 是越界或不是数字的文字.
     */
    private class Case(val name: String, val field: NumberField, val option: () -> Double?, val accepted: List<Pair<String, Double>>, val refused: List<String>)

    private fun cases(): List<Case> {
        val watermark = WatermarkState().apply { text = "DRAFT" }
        val numbers = PageNumbersState()
        val compress = CompressState()
        val maxRotation = StampItems.MAX_ROTATION
        val maxFont = TextStamp.MAX_FONT_SIZE
        return listOf(
            Case(
                "rotation", watermark.rotation, { watermark.options(null)?.rotation },
                listOf("0" to 0.0, "12.5" to 12.5, "-90" to -90.0, "+30" to 30.0, "36000" to maxRotation, "-36000" to -maxRotation),
                listOf("36000.5", "-36000.01", "1e3", "abc", "", " ", "45deg", "1.2.3", "--5"),
            ),
            Case(
                "opacity", watermark.opacity, { watermark.options(null)?.opacity },
                listOf("0" to 0.0, "33.3" to 0.333, "100" to 1.0, "0.5" to 0.005, "50" to 0.5),
                listOf("100.1", "-0.1", "-1", "50%", "half", ""),
            ),
            Case(
                "watermark font size", watermark.fontSize, { watermark.options(null)?.fontSize },
                listOf("0.5" to 0.5, "37.5" to 37.5, "1000" to maxFont),
                listOf("0", "-1", "1000.01", "1001", "big", ""),
            ),
            Case(
                "page number font size", numbers.fontSize, { numbers.options(null)?.fontSize },
                listOf("0.1" to 0.1, "9.5" to 9.5, "1000" to maxFont),
                listOf("0", "0.0", "-9", "1000.5", "x", ""),
            ),
            Case(
                "image dpi", compress.imageDpi, { compress.options()?.imageDpi?.toDouble() },
                listOf(PdfContract.MIN_IMAGE_DPI.toString() to PdfContract.MIN_IMAGE_DPI.toDouble(), "200" to 200.0, PdfContract.MAX_IMAGE_DPI.toString() to PdfContract.MAX_IMAGE_DPI.toDouble()),
                listOf((PdfContract.MIN_IMAGE_DPI - 1).toString(), (PdfContract.MAX_IMAGE_DPI + 1).toString(), "150.5", "150.", "dpi", ""),
            ),
            Case(
                "image quality", compress.imageQuality, { compress.options()?.imageQuality?.toDouble() },
                listOf(PdfContract.MIN_QUALITY.toString() to PdfContract.MIN_QUALITY.toDouble(), "42" to 42.0, PdfContract.MAX_QUALITY.toString() to PdfContract.MAX_QUALITY.toDouble()),
                listOf((PdfContract.MIN_QUALITY - 1).toString(), (PdfContract.MAX_QUALITY + 1).toString(), "75.5", "high", ""),
            ),
        )
    }

    @Test
    fun `every number option takes its range, boundaries included, and refuses the rest`() {
        cases().forEach { case ->
            case.field.chooseCustom()
            case.accepted.forEach { (text, value) ->
                case.field.text = text
                assertFalse("${case.name} $text", case.field.invalid)
                assertEquals("${case.name} $text", value, checkNotNull(case.option()) { "${case.name} $text" }, 0.0)
            }
            case.refused.forEach { text ->
                case.field.text = text
                assertTrue("${case.name} $text", case.field.invalid)
                assertNull("${case.name} $text", case.option())
            }
            // The cases share their states: leave a valid value for the next one. zh-CN: 各用例共用状态: 给下一个用例留下合法值.
            case.field.text = case.accepted.first().first
        }
    }

    @Test
    fun `an image width takes its range and only counts for image watermarks`() {
        val state = WatermarkState().apply { text = "DRAFT" }
        state.width.chooseCustom()
        state.width.text = "0"
        // A text watermark does not use the width. zh-CN: 文字水印不使用宽度.
        assertFalse(state.invalid())
        assertNull(checkNotNull(state.options(null)).width)
        state.selectType(PdfContract.STAMP_IMAGE)
        listOf("0.5" to 0.5, "144" to 144.0, "14400" to StampItems.MAX_LENGTH).forEach { (text, value) ->
            state.width.text = text
            assertFalse(text, state.invalid())
            assertEquals(text, value, checkNotNull(state.width.value()), 0.0)
        }
        listOf("0", "-1", "14400.5", "wide", "").forEach { text ->
            state.width.text = text
            assertTrue(text, state.invalid())
            assertTrue(text, state.advancedInvalid())
            assertNull(text, state.width.value())
        }
        state.width.choose(null)
        assertFalse(state.invalid())
        assertNull(state.width.value())
    }

    @Test
    fun `numbers may use other digits, a comma or the Arabic decimal separator and a minus sign`() {
        val range = WatermarkState.ROTATION_RANGE
        assertEquals(36.0, checkNotNull(range.parse("\u0663\u0666")), 0.0)
        assertEquals(12.5, checkNotNull(range.parse("12,5")), 0.0)
        assertEquals(12.5, checkNotNull(range.parse("12\u066B5")), 0.0)
        assertEquals(-45.0, checkNotNull(range.parse("\u221245")), 0.0)
        assertEquals(0.5, checkNotNull(range.parse(" .5 ")), 0.0)
        assertEquals(7.0, checkNotNull(range.parse("7.")), 0.0)
        assertNull(range.parse("1,000.5"))
        assertNull(range.parse("NaN"))
        assertNull(range.parse("Infinity"))
        assertNull(range.parse("0x10"))
        assertEquals("12.5", range.format(12.5))
        assertEquals("-36000", range.format(-36000.0))
        assertEquals("0.333", range.format(0.333))
    }

    @Test
    fun `choosing custom starts from the current value and a chip keeps the typed text`() {
        val state = CompressState()
        val field = state.imageQuality
        field.choose(85)
        field.chooseCustom()
        assertEquals("85", field.text)
        assertTrue(field.custom)
        assertFalse(field.selected(85))
        field.text = "50"
        field.choose(60)
        assertEquals(60.0, checkNotNull(field.value()), 0.0)
        assertTrue(field.selected(60))
        field.chooseCustom()
        assertEquals("50", field.text)
        assertEquals(50, checkNotNull(state.options()).imageQuality)
        field.text = "1".repeat(NumberField.MAX_TEXT_LENGTH + 5)
        assertEquals(NumberField.MAX_TEXT_LENGTH, field.text.length)
        // An automatic choice (null) has no number to start from. zh-CN: 自动档 (null) 没有可沿用的数.
        val font = WatermarkState().fontSize
        font.chooseCustom()
        assertEquals("", font.text)
        assertTrue(font.invalid)
    }

    @Test
    fun `a typed colour is RRGGBB or AARRGGBB and reaches the options`() {
        assertEquals("#FF0000", ColorField.parse("#ff0000"))
        assertEquals("#80FF0000", ColorField.parse("80ff0000"))
        assertEquals("#12AB34", ColorField.parse(" 12ab34 "))
        listOf("#FFF", "#FF00000", "#GG0000", "red", "", "##FF0000", "#FF0000FF00").forEach { assertNull(it, ColorField.parse(it)) }

        val state = WatermarkState().apply { text = "DRAFT" }
        assertEquals(ToolPayloads.WATERMARK_COLOR, checkNotNull(state.options(null)).color)
        state.color.chooseCustom()
        assertEquals(ToolPayloads.WATERMARK_COLOR, state.color.text)
        state.color.text = "#4000ff00"
        assertEquals("#4000FF00", checkNotNull(state.options(null)).color)
        state.color.text = "green"
        assertTrue(state.advancedInvalid())
        assertNull(state.options(null))
        // An image watermark has no colour. zh-CN: 图片水印没有颜色.
        state.selectType(PdfContract.STAMP_IMAGE)
        assertFalse(state.advancedInvalid())
        state.selectType(PdfContract.STAMP_TEXT)
        state.color.choose("#000000")
        assertEquals("#000000", checkNotNull(state.options(null)).color)
    }

    @Test
    fun `typed values and chosen chips survive the recreation of the state`() {
        val saved = SavedStateHandle()
        WatermarkState(saved).apply {
            rotation.chooseCustom()
            rotation.text = "-12.5"
            opacity.choose(70)
            fontSize.chooseCustom()
            fontSize.text = "abc"
            color.chooseCustom()
            color.text = "#80112233"
            width.choose(null)
        }
        PageNumbersState(saved).fontSize.apply {
            chooseCustom()
            text = "9.5"
        }
        CompressState(saved).apply {
            selectPreset(PdfContract.COMPRESS_PRESET_LOW)
            imageDpi.chooseCustom()
            imageDpi.text = "123"
        }

        // A new process restores the handle from the values it wrote. zh-CN: 新进程由写入的值恢复 handle.
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val watermark = WatermarkState(restored)
        assertTrue(watermark.rotation.custom)
        assertEquals("-12.5", watermark.rotation.text)
        assertEquals(-12.5, checkNotNull(watermark.rotation.value()), 0.0)
        assertTrue(watermark.opacity.selected(70))
        assertTrue(watermark.fontSize.invalid)
        assertEquals("abc", watermark.fontSize.text)
        assertEquals("#80112233", watermark.color.value())
        assertTrue(watermark.width.selected(null))
        assertEquals(9.5, checkNotNull(PageNumbersState(restored).fontSize.value()), 0.0)
        val compress = CompressState(restored)
        assertEquals(PdfContract.COMPRESS_PRESET_LOW, compress.preset)
        assertEquals(123, checkNotNull(compress.options()).imageDpi)
        assertEquals(CompressState.numbersOf(PdfContract.COMPRESS_PRESET_LOW).imageQuality, checkNotNull(compress.options()).imageQuality)

        // Without saved values every field starts from its default. zh-CN: 没有保存的值时各字段从默认值开始.
        val fresh = WatermarkState(SavedStateHandle())
        assertTrue(fresh.rotation.selected(ToolPayloads.WATERMARK_TEXT_ROTATION.toInt()))
        assertTrue(fresh.fontSize.selected(null))
        assertFalse(fresh.color.custom)
    }

}

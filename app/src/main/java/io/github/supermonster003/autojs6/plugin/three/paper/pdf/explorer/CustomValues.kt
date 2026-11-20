package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import java.math.BigDecimal

/**
 * The range a typed number of a tool option must fall in (appendix D Q265 (b)): the bounds the engine checks, which
 * the script API passes on, so a value the screen takes never fails in the engine. [aboveMinimum] leaves out
 * [minimum] itself (font sizes and image widths must be greater than 0); [integer] refuses a fraction.
 * zh-CN: 工具选项中输入的数值必须落入的范围 (附录 D Q265 (b)): 即引擎检查的范围, 脚本 API 也原样交给引擎, 因此界面接受的值不会在引擎中
 * 失败. [aboveMinimum] 表示不含 [minimum] 本身 (字号与图片宽度必须大于 0); [integer] 表示不接受小数.
 */
internal data class NumberRange(val minimum: Double, val maximum: Double, val aboveMinimum: Boolean = false, val integer: Boolean = false) {

    operator fun contains(value: Double): Boolean = (if (aboveMinimum) value > minimum else value >= minimum) && value <= maximum

    /**
     * [text] as a number in this range, or null: an optional sign, digits of any script and one decimal point (`.`,
     * `,` or the Arabic decimal separator); no exponent, no grouping, no fraction when [integer].
     * zh-CN: 把 [text] 读作此范围内的数, 否则为 null: 可带正负号, 数字可以是任何文字的数字, 小数点可以是 `.`, `,` 或阿拉伯小数点; 不接受
     * 指数与分组, [integer] 时不接受小数.
     */
    fun parse(text: String): Double? {
        val plain = normalize(text) ?: return null
        if (integer && '.' in plain) return null
        val value = plain.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return value.takeIf { it in this }
    }

    /** [value] without trailing zeros or grouping, as the field shows it. zh-CN: 不带末尾零与分组的 [value], 即输入框显示的形式. */
    fun format(value: Double): String = if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    }

    companion object {

        private val NUMBER = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")

        /** U+066B ARABIC DECIMAL SEPARATOR. zh-CN: 阿拉伯小数点 U+066B. */
        private val ARABIC_DECIMAL = Char(0x066B)

        /** U+2212 MINUS SIGN. zh-CN: 减号 U+2212. */
        private val MINUS_SIGN = Char(0x2212)

        /** [text] with ASCII digits, `-` and `.`, or null when it is not a plain number. zh-CN: 换成 ASCII 数字, `-` 与 `.` 的 [text]; 不是普通数字时为 null. */
        fun normalize(text: String): String? {
            val out = StringBuilder(text.length)
            for (char in text.trim()) {
                out.append(
                    when {
                        char.isDigit() -> '0' + char.digitToInt()
                        char == ',' || char == ARABIC_DECIMAL -> '.'
                        char == MINUS_SIGN -> '-'
                        else -> char
                    },
                )
            }
            return out.toString().takeIf { NUMBER.matches(it) }
        }

    }

}

/**
 * A number option picked from ready-made choices or typed in as "Custom" (appendix D Q265 (b)). [preset] is the chip
 * chosen while not [custom] (null is the field's automatic choice, such as the font size that fits the page); the
 * typed [text] keeps its value while a chip is chosen. The three values are written through to [saved] under [key]
 * so they survive a configuration change and the recreation of the process.
 * zh-CN: 从预设档位中选择或作为 "自定义" 输入的数值选项 (附录 D Q265 (b)). [preset] 是未选 [custom] 时选中的档位 (null 为该项的自动
 * 档, 例如适应页面的字号); 选中档位时输入的 [text] 保留. 三个值以 [key] 为前缀写入 [saved], 因此在配置变更与进程重建后仍在.
 */
internal class NumberField(private val key: String, val range: NumberRange, initial: Int?, private val saved: SavedStateHandle? = null) {

    var preset by mutableStateOf(if (saved?.contains("$key.$PRESET") == true) saved.get<Int>("$key.$PRESET") else initial)
        private set

    var custom by mutableStateOf(saved?.get<Boolean>("$key.$CUSTOM") ?: false)
        private set

    private var typed by mutableStateOf(saved?.get<String>("$key.$TEXT") ?: "")

    var text: String
        get() = typed
        set(value) {
            typed = value.take(MAX_TEXT_LENGTH)
            saved?.set("$key.$TEXT", typed)
        }

    /** The typed number, or null when it is not a number in [range]. zh-CN: 输入的数; 不是 [range] 内的数时为 null. */
    val number: Double? get() = range.parse(typed)

    /** "Custom" is chosen and the typed text is not a number in [range]. zh-CN: 选中了 "自定义" 而输入的不是 [range] 内的数. */
    val invalid: Boolean get() = custom && number == null

    /** Chooses the ready-made [value]. zh-CN: 选择预设值 [value]. */
    fun choose(value: Int?) {
        preset = value
        custom = false
        saved?.set("$key.$PRESET", value)
        saved?.set("$key.$CUSTOM", false)
    }

    /** Chooses "Custom"; an empty field starts with the current value. zh-CN: 选择 "自定义"; 输入框为空时先填入当前值. */
    fun chooseCustom() {
        if (!custom && typed.isBlank()) preset?.let { text = range.format(it.toDouble()) }
        custom = true
        saved?.set("$key.$CUSTOM", true)
    }

    fun selected(value: Int?): Boolean = !custom && preset == value

    /** The value in use: the typed number when [custom], else [preset] (null: automatic). zh-CN: 使用中的值: [custom] 时为输入的数, 否则为 [preset] (null 表示自动). */
    fun value(): Double? = if (custom) number else preset?.toDouble()

    companion object {

        /** Longest text the field keeps. zh-CN: 输入框保留的最长文字. */
        const val MAX_TEXT_LENGTH = 12

        private const val PRESET = "preset"
        private const val CUSTOM = "custom"
        private const val TEXT = "text"

    }

}

/**
 * A colour option picked from ready-made colours or typed in as `#RRGGBB` / `#AARRGGBB` (appendix D Q290, the forms
 * the engine and the script API take); otherwise like [NumberField].
 * zh-CN: 从预设颜色中选择或以 `#RRGGBB` / `#AARRGGBB` 输入的颜色选项 (附录 D Q290, 即引擎与脚本 API 接受的形式); 其余与 [NumberField] 相同.
 */
internal class ColorField(private val key: String, initial: String, private val saved: SavedStateHandle? = null) {

    var preset by mutableStateOf(saved?.get<String>("$key.$PRESET") ?: initial)
        private set

    var custom by mutableStateOf(saved?.get<Boolean>("$key.$CUSTOM") ?: false)
        private set

    private var typed by mutableStateOf(saved?.get<String>("$key.$TEXT") ?: "")

    var text: String
        get() = typed
        set(value) {
            typed = value.take(MAX_TEXT_LENGTH)
            saved?.set("$key.$TEXT", typed)
        }

    /** The typed colour as `#` and upper-case hex digits, or null. zh-CN: 输入的颜色 (`#` 加大写十六进制数字), 否则为 null. */
    val color: String? get() = parse(typed)

    val invalid: Boolean get() = custom && color == null

    fun choose(value: String) {
        preset = value
        custom = false
        saved?.set("$key.$PRESET", value)
        saved?.set("$key.$CUSTOM", false)
    }

    fun chooseCustom() {
        if (!custom && typed.isBlank()) text = preset
        custom = true
        saved?.set("$key.$CUSTOM", true)
    }

    fun selected(value: String): Boolean = !custom && preset == value

    fun value(): String? = if (custom) color else preset

    companion object {

        const val MAX_TEXT_LENGTH = 9

        private const val PRESET = "preset"
        private const val CUSTOM = "custom"
        private const val TEXT = "text"

        private val HEX = Regex("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

        /** [text] as `#RRGGBB` / `#AARRGGBB` (the `#` may be left out), or null. zh-CN: 把 [text] 读作 `#RRGGBB` / `#AARRGGBB` (可省略 `#`), 否则为 null. */
        fun parse(text: String): String? = HEX.matchEntire(text.trim())?.let { "#" + it.groupValues[1].uppercase() }

    }

}

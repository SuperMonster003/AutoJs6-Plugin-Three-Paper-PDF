package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Above or below the page content (roadmap D61 `layer`). zh-CN: 在页面内容之上或之下 (路线图 D61 的 `layer`). */
internal enum class StampLayer { OVER, UNDER }

/** Line alignment inside a multi-line text stamp. zh-CN: 多行文字印章内的行对齐. */
internal enum class StampAlign { START, CENTER, END }

/**
 * One stamp drawn by [CanvasStamps] on the canvas of a displayed page (origin at its top left corner, points, y down).
 * Implementations are data classes: equal stamp lists on pages of the same canvas size share one form XObject.
 * zh-CN: 由 [CanvasStamps] 画在显示页面画布上的一个印章 (原点在左上角, 单位为点, y 向下). 实现类都是数据类: 画布尺寸相同的页面上
 * 相等的印章列表共用一个表单 XObject.
 */
internal interface Stamp {

    val layer: StampLayer

    fun draw(canvas: Canvas, fonts: StampFonts)

}

/**
 * The typefaces of one stamping run: the system default (with the font fallback chain of the system, which covers CJK
 * and emoji) and an optional font loaded from a font input (`fontPath`, roadmap D60). [emojiAsBitmaps] says whether
 * emoji are drawn as bitmaps ([EmojiBitmaps]); by default the system backend is probed the first time a stamp holds an
 * emoji.
 * zh-CN: 一次印章操作使用的字体: 系统默认字体 (带系统字体回退链, 覆盖中日韩文字与表情) 与可选的由字体输入载入的字体 (`fontPath`,
 * 路线图 D60). [emojiAsBitmaps] 表示表情是否画成位图 ([EmojiBitmaps]); 缺省时在印章首次含表情时探测系统后端.
 */
internal class StampFonts(private val custom: Typeface? = null, emojiAsBitmaps: Boolean? = null) {

    private val cache = HashMap<Triple<Boolean, Boolean, Boolean>, Typeface>()

    val emojiAsBitmaps: Boolean by lazy { emojiAsBitmaps ?: EmojiBitmaps.embedsWholeEmojiFont }

    private var emoji: EmojiBitmaps? = null

    /**
     * Whether [text] lays spaces out as no-break spaces (see [noBreakSpaces]); [CanvasStamps] turns it off when a text
     * stamp holds a no-break space of its own, so that the text mapping keeps the two apart.
     * zh-CN: [text] 是否把空格排成不换行空格 (见 [noBreakSpaces]); 有文字印章自带不换行空格时由 [CanvasStamps] 关闭, 使文本映射能
     * 区分两者.
     */
    var spacesAsNoBreak: Boolean = true

    /**
     * The text to lay out for [text]: spaces as no-break spaces ([spacesAsNoBreak]), emoji replaced by bitmap spans
     * where needed.
     * zh-CN: 供排版的文本: 空格排成不换行空格 ([spacesAsNoBreak]), 需要时表情替换为位图跨度.
     */
    fun text(text: String): CharSequence {
        val laid = if (spacesAsNoBreak) noBreakSpaces(text) else text
        if (EmojiBitmaps.clusters(laid).isEmpty() || !emojiAsBitmaps) return laid
        return (emoji ?: EmojiBitmaps().also { emoji = it }).spanned(laid)
    }

    /**
     * Recycles the emoji bitmaps; called by [CanvasStamps] once the stamp document is written.
     * zh-CN: 回收表情位图; 由 [CanvasStamps] 在印章文档写出之后调用.
     */
    fun releaseBitmaps() {
        emoji?.close()
        emoji = null
    }

    /** Whether a font input was given. zh-CN: 是否给出了字体输入. */
    val hasCustom: Boolean get() = custom != null

    fun typeface(useCustom: Boolean, bold: Boolean, italic: Boolean): Typeface = cache.getOrPut(Triple(useCustom, bold, italic)) {
        val base = if (useCustom) checkNotNull(custom) { "the stamp asks for the font input, but there is none" } else Typeface.DEFAULT
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }

    companion object {

        /** U+00A0 NO-BREAK SPACE. zh-CN: U+00A0 不换行空格. */
        val NO_BREAK_SPACE = Char(0x00A0)

        /**
         * [text] with its spaces as no-break spaces, except a space between two digits. The font itemization of
         * Android gives a space that follows text of another font (CJK, emoji, Arabic) a font run of its own, and where
         * the typeface has no font bounds (the variable system fonts of API 35, which Skia writes as Type 3 fonts) Skia
         * drops a run of blank glyphs as out of bounds, so text extraction loses that space ("页,共" for "页, 共"). A
         * no-break space stays in the run of the text before it whenever that font has one, and `ToUnicodeRepair` maps
         * it back to the space that was drawn (NFKC). A space between two digits stays a space, since as a common
         * separator a no-break space would join the numbers in right-to-left text (bidi rule W4); between ASCII digits it
         * shares their run anyway. A space right after an emoji, between two Arabic-Indic digits or at the start of a
         * line before CJK text is still lost on API 35.
         * zh-CN: 把 [text] 中的空格换成不换行空格, 两个数字之间的空格除外. Android 的字体分段会让跟在其他字体文字 (中日韩文字, 表情,
         * 阿拉伯文) 之后的空格单独成为一个字体段, 而字体没有字体边界时 (API 35 的可变系统字体, Skia 写成 Type 3 字体), Skia 会把只含
         * 空白字形的段当作越界丢掉, 文本提取随之丢失该空格 ("页, 共" 变成 "页,共"). 只要前面文字的字体含不换行空格, 它就留在该字体段
         * 里, 再由 `ToUnicodeRepair` 映射回实际绘制的空格 (NFKC). 两个数字之间的空格保持为空格, 因为不换行空格属于公共分隔符, 在
         * 从右到左的文字中会把两个数字连成一个 (双向算法规则 W4); ASCII 数字之间的空格本来就与数字同段. 表情之后, 两个阿拉伯-印度数字
         * 之间, 以及行首且后接中日韩文字的空格在 API 35 上仍会丢失.
         */
        fun noBreakSpaces(text: String): String {
            if (text.indexOf(' ') < 0) return text
            val chars = text.toCharArray()
            for (index in chars.indices) {
                if (chars[index] != ' ') continue
                if (index > 0 && index + 1 < chars.size && Character.isDigit(chars[index - 1]) && Character.isDigit(chars[index + 1])) continue
                chars[index] = NO_BREAK_SPACE
            }
            return String(chars)
        }

    }

}

/**
 * A text stamp (roadmap D60, D61): [text] (lines split at `\n`) in a box as wide as its widest line, placed so the
 * point ([anchorX], [anchorY]) of the box (fractions: 0 left / top, 0.5 centre, 1 right / bottom) lies at display point
 * ([x], [y]), then turned counterclockwise by [rotation] degrees around that point. [opacity] multiplies the alpha of
 * [color] (ARGB) and is drawn by the canvas paint, so it holds inside the imported form whatever graphics state the
 * page content leaves. Bold and italic come from the typeface when it has them, else they are synthesized (fake bold,
 * a skew of 0.25).
 * zh-CN: 文字印章 (路线图 D60, D61): [text] (按 `\n` 分行) 放在与最宽一行同宽的方框中, 使方框上的点 ([anchorX], [anchorY]) (比例: 0
 * 为左 / 上, 0.5 为中, 1 为右 / 下) 落在显示坐标 ([x], [y]), 再绕该点逆时针旋转 [rotation] 度. [opacity] 与 [color] (ARGB) 的
 * alpha 相乘并由画布的画笔绘制, 因此无论页面内容留下怎样的图形状态, 在导入的表单内都成立. 字体有粗体与斜体时直接使用, 否则合成
 * (伪粗体, 0.25 的倾斜).
 */
internal data class TextStamp(
    val text: String,
    val x: Double,
    val y: Double,
    val anchorX: Double = 0.0,
    val anchorY: Double = 0.0,
    val fontSize: Double = 12.0,
    val color: Int = 0xFF000000.toInt(),
    val opacity: Double = 1.0,
    val rotation: Double = 0.0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: StampAlign = StampAlign.START,
    val customFont: Boolean = false,
    override val layer: StampLayer = StampLayer.OVER,
) : Stamp {

    init {
        require(fontSize > 0.0 && fontSize <= MAX_FONT_SIZE) { "fontSize must be in (0, $MAX_FONT_SIZE], got $fontSize" }
        require(opacity in 0.0..1.0) { "opacity must be in [0, 1], got $opacity" }
        require(anchorX in 0.0..1.0 && anchorY in 0.0..1.0) { "anchors are fractions of the box, got ($anchorX, $anchorY)" }
        require(x.isFinite() && y.isFinite() && rotation.isFinite()) { "the position and the rotation must be finite" }
    }

    /** The paint of this stamp. zh-CN: 本印章的画笔. */
    fun paint(fonts: StampFonts): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        val face = fonts.typeface(customFont, bold, italic)
        typeface = face
        textSize = fontSize.toFloat()
        color = this@TextStamp.color
        alpha = (Color.alpha(this@TextStamp.color) * opacity).roundToInt().coerceIn(0, 255)
        if (bold && !face.isBold) isFakeBoldText = true
        if (italic && !face.isItalic) textSkewX = -0.25f
    }

    /** The laid out text; its width is the box width. zh-CN: 排好版的文字; 其宽度就是方框宽度. */
    fun layout(paint: TextPaint, fonts: StampFonts): StaticLayout {
        val source = fonts.text(text)
        var widest = 0f
        var start = 0
        while (start <= source.length) {
            val end = source.indexOf('\n', start).let { if (it < 0) source.length else it }
            widest = maxOf(widest, Layout.getDesiredWidth(source, start, end, paint))
            start = end + 1
        }
        // One extra point keeps StaticLayout from breaking the widest line on a rounding difference.
        // zh-CN: 多留一点, 避免 StaticLayout 因舍入差异把最宽的一行折开.
        val width = (ceil(widest).toInt() + 1).coerceAtLeast(1)
        val alignment = when (align) {
            StampAlign.START -> Layout.Alignment.ALIGN_NORMAL
            StampAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            StampAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(source, 0, source.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
    }

    override fun draw(canvas: Canvas, fonts: StampFonts) {
        val layout = layout(paint(fonts), fonts)
        canvas.save()
        try {
            canvas.translate(x.toFloat(), y.toFloat())
            // Canvas angles turn clockwise on screen (y down); D61 angles turn counterclockwise.
            // zh-CN: 画布角度在屏幕上 (y 向下) 顺时针旋转; D61 的角度逆时针旋转.
            canvas.rotate(-rotation.toFloat())
            canvas.translate((-anchorX * layout.width).toFloat(), (-anchorY * layout.height).toFloat())
            layout.draw(canvas)
        } finally {
            canvas.restore()
        }
    }

    companion object {

        /** Largest font size in points, as `fromText`. zh-CN: 最大字号 (磅), 与 `fromText` 相同. */
        const val MAX_FONT_SIZE = 1000.0

    }

}

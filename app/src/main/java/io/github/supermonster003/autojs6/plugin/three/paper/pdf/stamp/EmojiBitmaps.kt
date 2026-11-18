package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import java.io.OutputStream
import java.text.BreakIterator
import kotlin.math.ceil

/**
 * Emoji of text stamps on the older system PDF backend (spike S2): on API 24 and 28 Skia embeds the whole colour emoji
 * font (3.4 MB and 7.3 MB) for a single emoji, maps it to a wrong character (`λ`, `ο`) and on API 24 the system
 * renderer does not draw it. Where [embedsWholeEmojiFont] finds that behaviour, every emoji grapheme cluster of a text
 * stamp is drawn as a bitmap instead (rendered at [SCALE] times the font size, a few KB, cached per cluster and size so
 * pages share one image); such emoji are visible but not extractable. Newer backends (API 33 seen) draw emoji as text.
 * zh-CN: 旧版系统 PDF 后端上文字印章里的表情 (spike S2): 在 API 24 与 28 上, 一个表情就让 Skia 嵌入整个彩色表情字体 (3.4 MB 与
 * 7.3 MB), 并把它映射成错误的字符 (`λ`, `ο`), API 24 的系统渲染器还不绘制它. [embedsWholeEmojiFont] 探测到这种行为时, 文字印章里
 * 的每个表情字素簇改画成位图 (按字号的 [SCALE] 倍渲染, 几 KB, 按字素簇与字号缓存, 各页共用一张图片); 这样的表情可见但不可提取.
 * 较新的后端 (实测 API 33) 把表情当作文字绘制.
 */
internal class EmojiBitmaps : AutoCloseable {

    private val cache = HashMap<Pair<String, Float>, Bitmap>()

    /**
     * [text] with every emoji cluster replaced by a span that draws its bitmap, or [text] itself when it has none.
     * zh-CN: 把 [text] 中每个表情字素簇替换为绘制其位图的跨度; 没有表情时返回 [text] 本身.
     */
    fun spanned(text: String): CharSequence {
        val clusters = clusters(text)
        if (clusters.isEmpty()) return text
        return SpannableString(text).apply {
            for (range in clusters) setSpan(EmojiSpan(text.substring(range.first, range.last + 1)), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun bitmap(cluster: String, paint: Paint): Bitmap = cache.getOrPut(cluster to paint.textSize) {
        val large = TextPaint(paint).apply {
            textSize = paint.textSize * SCALE
            alpha = 255
        }
        val metrics = large.fontMetrics
        val width = ceil(large.measureText(cluster)).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawText(cluster, 0f, -metrics.ascent, large) }
    }

    override fun close() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }

    /** Draws one emoji cluster as its bitmap, as wide as the text it replaces. zh-CN: 把一个表情字素簇画成其位图, 宽度与被替换的文字相同. */
    private inner class EmojiSpan(private val cluster: String) : ReplacementSpan() {

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            fm?.let { paint.getFontMetricsInt(it) }
            return ceil(paint.measureText(cluster)).toInt()
        }

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val metrics = paint.fontMetrics
            val target = RectF(x, y + metrics.ascent, x + paint.measureText(cluster), y + metrics.descent)
            canvas.drawBitmap(bitmap(cluster, paint), null, target, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = paint.alpha })
        }

    }

    companion object {

        /** Bitmap pixels per point of the font size. zh-CN: 每磅字号对应的位图像素. */
        const val SCALE = 4f

        /** A probe document above this size embeds a whole emoji font. zh-CN: 探测文档超过该大小即嵌入了整个表情字体. */
        private const val WHOLE_FONT_BYTES = 512L * 1024

        /**
         * Whether the system `PdfDocument` embeds the whole colour emoji font: one emoji is drawn on a tiny page and the
         * size of the written document (discarded) tells. Probed once per process.
         * zh-CN: 系统 `PdfDocument` 是否嵌入整个彩色表情字体: 在一张很小的页面上画一个表情, 由写出文档 (丢弃) 的大小判断. 每个进程
         * 只探测一次.
         */
        val embedsWholeEmojiFont: Boolean by lazy {
            val pdf = PdfDocument()
            try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(48, 48, 1).create())
                page.canvas.drawText(String(Character.toChars(0x1F600)), 4f, 36f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f })
                pdf.finishPage(page)
                var size = 0L
                pdf.writeTo(object : OutputStream() {
                    override fun write(b: Int) {
                        size++
                    }

                    override fun write(b: ByteArray, off: Int, len: Int) {
                        size += len
                    }
                })
                size > WHOLE_FONT_BYTES
            } finally {
                pdf.close()
            }
        }

        /**
         * The emoji grapheme clusters of [text] (character ranges): clusters holding a code point from U+1F000 on, a
         * variation selector 16 or a combining keycap.
         * zh-CN: [text] 中的表情字素簇 (字符范围): 含 U+1F000 及以上码位, 变体选择符 16 或组合键帽的字素簇.
         */
        fun clusters(text: String): List<IntRange> {
            val result = ArrayList<IntRange>()
            val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                if (text.substring(start, end).codePoints().anyMatch { it >= 0x1F000 || it == 0xFE0F || it == 0x20E3 }) result += start until end
                start = end
                end = iterator.next()
            }
            return result
        }

    }

}

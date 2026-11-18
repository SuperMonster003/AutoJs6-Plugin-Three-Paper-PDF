package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.nio.ByteBuffer

/**
 * Tells whether two code points draw the same glyph with one of [typefaces] (and the system fallback behind it), by
 * comparing their coverage on a small bitmap; a code point no font draws matches nothing. Used by [ToUnicodeRepair] for
 * radicals that share their glyph with an ideograph. [close] recycles the cached bitmaps.
 * zh-CN: 判断两个码位用 [typefaces] 之一 (及其后的系统回退字体) 绘制时是否为同一字形: 比较二者在小位图上的覆盖; 没有字体能绘制的
 * 码位不与任何码位匹配. 供 [ToUnicodeRepair] 处理与表意文字共用字形的部首. [close] 回收缓存的位图.
 */
internal class GlyphComparer(private val typefaces: List<Typeface>) : (Int, Int) -> Boolean, AutoCloseable {

    private val cache = HashMap<Pair<Typeface, Int>, Bitmap?>()

    override fun invoke(first: Int, second: Int): Boolean = typefaces.any { face ->
        val a = glyph(face, first)
        val b = glyph(face, second)
        a != null && b != null && a.sameAs(b)
    }

    private fun glyph(face: Typeface, codePoint: Int): Bitmap? = cache.getOrPut(face to codePoint) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = SIZE.toFloat()
        }
        val bitmap = Bitmap.createBitmap(SIZE * 3 / 2, SIZE * 3 / 2, Bitmap.Config.ALPHA_8)
        Canvas(bitmap).drawText(String(Character.toChars(codePoint)), SIZE / 4f, SIZE.toFloat(), paint)
        val pixels = ByteBuffer.allocate(bitmap.byteCount).also { bitmap.copyPixelsToBuffer(it) }.array()
        if (pixels.all { it.toInt() == 0 }) {
            bitmap.recycle()
            null
        } else {
            bitmap
        }
    }

    override fun close() {
        cache.values.forEach { it?.recycle() }
        cache.clear()
    }

    private companion object {

        /** Text size of the comparison in pixels. zh-CN: 比较时的字号 (像素). */
        const val SIZE = 64

    }

}

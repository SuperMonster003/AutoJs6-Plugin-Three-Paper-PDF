package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [JpegData.of] reads the frame header [JpegPassThrough] needs and refuses what it cannot embed (roadmap Q201 (a)).
 * zh-CN: [JpegData.of] 读取 [JpegPassThrough] 需要的帧头, 拒绝无法嵌入的 JPEG (路线图 Q201 (a)).
 */
class JpegDataTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** SOI, an APP0 segment, fill bytes, then a frame header [sof] of [components] components and [precision] bits. zh-CN: SOI, APP0 段, 填充字节, 再接帧头. */
    private fun jpeg(sof: Int = 0xC0, width: Int = 640, height: Int = 480, components: Int = 3, precision: Int = 8): ByteArray {
        val app0 = bytes(0xFF, 0xE0, 0x00, 0x07, 'J'.code, 'F'.code, 'I'.code, 'F'.code, 0x00)
        val frame = bytes(0xFF, 0xFF, 0xFF, sof, 0x00, 8 + 3 * components, precision, height shr 8, height and 0xFF, width shr 8, width and 0xFF, components) +
            ByteArray(3 * components) + bytes(0xFF, 0xDA, 0x00, 0x02, 0xFF, 0xD9)
        return bytes(0xFF, 0xD8) + app0 + frame
    }

    @Test
    fun `baseline and progressive frames with one or three components are read`() {
        val baseline = JpegData.of(jpeg())
        assertNotNull(baseline)
        assertEquals(640, baseline!!.width)
        assertEquals(480, baseline.height)
        assertEquals(3, baseline.components)
        assertEquals(1, JpegData.of(jpeg(sof = 0xC2, width = 4000, height = 3000, components = 1))!!.components)
        assertEquals(4000, JpegData.of(jpeg(sof = 0xC1, width = 4000, height = 3000))!!.width)
    }

    @Test
    fun `what cannot be embedded is refused`() {
        assertNull("CMYK", JpegData.of(jpeg(components = 4)))
        assertNull("12-bit", JpegData.of(jpeg(precision = 12)))
        assertNull("lossless", JpegData.of(jpeg(sof = 0xC3)))
        assertNull("arithmetic coding", JpegData.of(jpeg(sof = 0xC9)))
        assertNull("no size", JpegData.of(jpeg(width = 0)))
        assertNull("PNG", JpegData.of(bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertNull("scan before the frame", JpegData.of(bytes(0xFF, 0xD8, 0xFF, 0xDA, 0x00, 0x02)))
        assertNull("truncated", JpegData.of(jpeg().copyOf(20)))
        assertNull("empty", JpegData.of(ByteArray(0)))
    }

}

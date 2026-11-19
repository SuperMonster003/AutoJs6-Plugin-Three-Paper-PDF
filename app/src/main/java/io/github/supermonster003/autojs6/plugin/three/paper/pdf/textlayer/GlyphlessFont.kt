package io.github.supermonster003.autojs6.plugin.three.paper.pdf.textlayer

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.BitSet
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * The glyphless Type 0 font of invisible text layers (roadmap D63, spike S3), built by hand once per document: Identity-H
 * codes are UTF-16 code units of the Basic Multilingual Plane, and each other character takes one "slot" code from the
 * surrogate range (unused by valid text), so every character is one code. The `/ToUnicode` CMap maps a code unit to
 * itself (one conforming `bfrange` per high byte the document uses, about 8 bytes compressed each) and a slot code to
 * its character; `/CIDToGIDMap` sends every CID to glyph 1, and the font program ([trueType]) is a TrueType font
 * generated here with two empty glyphs, so no font file ships with the plugin. Ascent 800 and descent -200 make the
 * glyph box of PDFBox (`SearchReader.glyphOf`) equal to the line box when the font size is the line height and the
 * baseline sits at a fifth of it. Widths ([width]) are 1000 for East Asian wide characters and slots, 500 otherwise.
 * zh-CN: 不可见文本层所用的无字形 Type 0 字体 (路线图 D63, spike S3), 每个文档手工构造一份: Identity-H 的编码是基本多文种平面的
 * UTF-16 码元, 其他字符各占代理区 (有效文本不会用到) 的一个 "槽位" 编码, 因此每个字符正好一个编码. `/ToUnicode` 把码元映射为
 * 自身 (文档用到的每个高字节一条合规的 `bfrange`, 压缩后每条约 8 字节), 把槽位映射为它代表的字符; `/CIDToGIDMap` 把所有 CID
 * 映到 1 号字形, 字体程序 ([trueType]) 是在这里生成的只含两个空字形的 TrueType 字体, 因此插件不随包带字体文件. 上伸 800, 下伸
 * -200, 字号取行高并把基线放在行高的五分之一处时, PDFBox 的字形框 (`SearchReader.glyphOf`) 正好等于行框. 宽度 ([width]): 东亚
 * 宽字符与槽位为 1000, 其余为 500.
 */
internal class GlyphlessFont private constructor(
    /** The Type 0 font dictionary to put in page resources. zh-CN: 放进页面资源的 Type 0 字体字典. */
    val dictionary: COSDictionary,
    private val toUnicode: COSStream,
) {

    private val highBytes = BitSet(256)
    private val slots = LinkedHashMap<Int, Int>()
    private val overflow = HashSet<Int>()

    /** Characters beyond the slots, written as U+FFFD. zh-CN: 超出槽位, 写成 U+FFFD 的字符数. */
    val overflowCount: Int get() = overflow.size

    /**
     * The codes of [text], one per character; [text] holds no unpaired surrogate (`TextLayers.clean`).
     * zh-CN: [text] 的编码, 每个字符一个; [text] 不含未配对的代理项 (`TextLayers.clean`).
     */
    fun encode(text: String): IntArray {
        val codes = IntArray(text.length)
        var count = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            index += Character.charCount(point)
            codes[count++] = if (point <= 0xFFFF) point.also { highBytes.set(it ushr 8) } else slot(point)
        }
        return codes.copyOf(count)
    }

    private fun slot(point: Int): Int {
        slots[point]?.let { return it }
        if (slots.size < SLOT_COUNT) return (FIRST_SLOT + slots.size).also { slots[point] = it }
        overflow += point
        highBytes.set(REPLACEMENT ushr 8)
        return REPLACEMENT
    }

    /** Writes the `/ToUnicode` CMap for the codes [encode] gave out. zh-CN: 为 [encode] 给出的编码写出 `/ToUnicode` CMap. */
    fun finish() {
        val ranges = ArrayList<String>()
        var high = highBytes.nextSetBit(0)
        while (high >= 0) {
            val hex = hex2(high)
            ranges += "<${hex}00><${hex}FF><${hex}00>"
            high = highBytes.nextSetBit(high + 1)
        }
        val chars = slots.map { (point, code) -> "<${hex4(code)}><${hex4(Character.highSurrogate(point).code)}${hex4(Character.lowSurrogate(point).code)}>" }
        val cmap = buildString {
            append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
            append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
            append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
            append("1 begincodespacerange\n<0000><FFFF>\nendcodespacerange\n")
            blocks(ranges, "bfrange")
            blocks(chars, "bfchar")
            append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        }
        deflate(toUnicode, cmap.toByteArray(Charsets.US_ASCII))
    }

    private fun StringBuilder.blocks(entries: List<String>, kind: String) {
        // A CMap block holds at most 100 entries. zh-CN: 一个 CMap 块至多 100 条.
        entries.chunked(100).forEach { chunk ->
            append(chunk.size).append(" begin").append(kind).append('\n')
            chunk.forEach { append(it).append('\n') }
            append("end").append(kind).append('\n')
        }
    }

    companion object {

        const val BASE_FONT = "GlyphLessFont"

        /** Font units above the baseline, of 1000. zh-CN: 基线以上的字体单位 (共 1000). */
        const val ASCENT = 800

        /** Font units below the baseline, of 1000. zh-CN: 基线以下的字体单位 (共 1000). */
        const val DESCENT = 200

        const val NARROW = 500
        const val WIDE = 1000

        /** The first slot code; slots run up to 0xDFFF. zh-CN: 第一个槽位编码; 槽位直到 0xDFFF. */
        const val FIRST_SLOT = 0xD800
        const val SLOT_COUNT = 0x800

        const val REPLACEMENT = 0xFFFD

        /**
         * Code ranges of 1000 units (`[first, last]` pairs): Hangul Jamo, the CJK block from the radicals through Yi,
         * Hangul syllables with the slots, compatibility ideographs and forms, full-width forms.
         * zh-CN: 宽 1000 单位的编码区间 (`[首, 末]` 成对): 谚文字母, 从部首到彝文的中日韩区块, 谚文音节连同槽位, 兼容表意文字与兼容形式, 全宽形式.
         */
        private val WIDE_RANGES = intArrayOf(
            0x1100, 0x115F,
            0x2E80, 0xA4CF,
            0xAC00, 0xDFFF,
            0xF900, 0xFAFF,
            0xFE30, 0xFE4F,
            0xFF00, 0xFF60,
            0xFFE0, 0xFFE6,
        )

        /** Width of [code] in units of 1000. zh-CN: [code] 的宽度, 以 1000 为单位. */
        fun width(code: Int): Int {
            for (index in WIDE_RANGES.indices step 2) {
                if (code < WIDE_RANGES[index]) return NARROW
                if (code <= WIDE_RANGES[index + 1]) return WIDE
            }
            return NARROW
        }

        /** Adds a new font to [document]; call [finish] before saving. zh-CN: 为 [document] 新建一份字体; 保存前调用 [finish]. */
        fun create(document: PDDocument): GlyphlessFont {
            val cos = document.document
            val program = cos.createCOSStream().also { stream ->
                val bytes = trueType()
                stream.setInt(COSName.LENGTH1, bytes.size)
                deflate(stream, bytes)
            }
            val descriptor = COSDictionary().apply {
                setItem(COSName.TYPE, COSName.FONT_DESC)
                setName(COSName.FONT_NAME, BASE_FONT)
                setInt(COSName.FLAGS, SYMBOLIC)
                setItem(COSName.FONT_BBOX, integers(0, -DESCENT, WIDE, ASCENT))
                setInt(COSName.ITALIC_ANGLE, 0)
                setInt(COSName.ASCENT, ASCENT)
                setInt(COSName.DESCENT, -DESCENT)
                setInt(COSName.CAP_HEIGHT, CAP_HEIGHT)
                setInt(COSName.STEM_V, STEM_V)
                setItem(COSName.FONT_FILE2, program)
            }
            val cidToGid = cos.createCOSStream().also { stream ->
                val map = ByteArray(0x10000 * 2)
                for (cid in 0 until 0x10000) map[cid * 2 + 1] = 1
                deflate(stream, map)
            }
            val systemInfo = COSDictionary().apply {
                setItem(COSName.REGISTRY, COSString("Adobe"))
                setItem(COSName.ORDERING, COSString("Identity"))
                setInt(COSName.SUPPLEMENT, 0)
                isDirect = true
            }
            val widths = COSArray().apply {
                for (index in WIDE_RANGES.indices step 2) {
                    add(COSInteger.get(WIDE_RANGES[index].toLong()))
                    add(COSInteger.get(WIDE_RANGES[index + 1].toLong()))
                    add(COSInteger.get(WIDE.toLong()))
                }
            }
            val cidFont = COSDictionary().apply {
                setItem(COSName.TYPE, COSName.FONT)
                setItem(COSName.SUBTYPE, COSName.CID_FONT_TYPE2)
                setName(COSName.BASE_FONT, BASE_FONT)
                setItem(COSName.CIDSYSTEMINFO, systemInfo)
                setItem(COSName.FONT_DESC, descriptor)
                setInt(COSName.DW, NARROW)
                setItem(COSName.W, widths)
                setItem(COSName.CID_TO_GID_MAP, cidToGid)
            }
            val toUnicode = cos.createCOSStream()
            val font = COSDictionary().apply {
                setItem(COSName.TYPE, COSName.FONT)
                setItem(COSName.SUBTYPE, COSName.TYPE0)
                setName(COSName.BASE_FONT, BASE_FONT)
                setItem(COSName.ENCODING, COSName.IDENTITY_H)
                setItem(COSName.DESCENDANT_FONTS, COSArray().apply { add(cidFont) })
                setItem(COSName.TO_UNICODE, toUnicode)
            }
            return GlyphlessFont(font, toUnicode)
        }

        /** FontDescriptor flag 3: the font uses characters outside the standard Latin set. zh-CN: FontDescriptor 标志位 3: 字体含标准拉丁字符集之外的字符. */
        private const val SYMBOLIC = 4
        private const val CAP_HEIGHT = 700
        private const val STEM_V = 80

        private fun integers(vararg values: Int) = COSArray().apply { values.forEach { add(COSInteger.get(it.toLong())) } }

        private fun deflate(stream: COSStream, bytes: ByteArray) {
            stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
            stream.createRawOutputStream().use { raw ->
                val deflater = Deflater(Deflater.BEST_COMPRESSION)
                try {
                    DeflaterOutputStream(raw, deflater).use { it.write(bytes) }
                } finally {
                    deflater.end()
                }
            }
        }

        private fun hex2(value: Int) = String.format(Locale.ROOT, "%02X", value)

        private fun hex4(value: Int) = String.format(Locale.ROOT, "%04X", value)

        /**
         * The font program: a TrueType font of 1000 units per em with two empty glyphs (`.notdef` and the glyph every
         * CID maps to), the tables PDFBox and FreeType need (`head`, `hhea`, `maxp`, `hmtx`, `loca`, `glyf`) plus an
         * empty `cmap` and a `post` without glyph names; about 350 bytes, under 200 compressed.
         * zh-CN: 字体程序: 每 em 1000 单位, 只含两个空字形 (`.notdef` 与所有 CID 映到的字形) 的 TrueType 字体, 带 PDFBox 与 FreeType
         * 需要的表 (`head`, `hhea`, `maxp`, `hmtx`, `loca`, `glyf`), 外加空的 `cmap` 与不带字形名的 `post`; 约 350 字节, 压缩后不到 200.
         */
        fun trueType(): ByteArray {
            val tables = sortedMapOf(
                "cmap" to table(36) {
                    putShort(0).putShort(1)
                    putShort(3).putShort(1).putInt(12)
                    putShort(4).putShort(24).putShort(0).putShort(2).putShort(2).putShort(0).putShort(0)
                    putShort(0xFFFF).putShort(0).putShort(0xFFFF).putShort(1).putShort(0)
                },
                // Four bytes of padding: FreeType treats a zero-length table as missing. zh-CN: 四个填充字节: FreeType 把长度为 0 的表视为缺失.
                "glyf" to ByteArray(4),
                "head" to table(54) {
                    putShort(1).putShort(0).putInt(0x00010000).putInt(0).putInt(0x5F0F3CF5)
                    putShort(0x000B).putShort(UNITS_PER_EM).putLong(0).putLong(0)
                    putShort(0).putShort(-DESCENT).putShort(WIDE).putShort(ASCENT)
                    putShort(0).putShort(8).putShort(2).putShort(0).putShort(0)
                },
                "hhea" to table(36) {
                    putShort(1).putShort(0).putShort(ASCENT).putShort(-DESCENT).putShort(0).putShort(NARROW)
                    putShort(0).putShort(0).putShort(0).putShort(1).putShort(0).putShort(0)
                    putLong(0).putShort(0).putShort(GLYPHS)
                },
                "hmtx" to table(4 * GLYPHS) { repeat(GLYPHS) { putShort(NARROW).putShort(0) } },
                "loca" to ByteArray(2 * (GLYPHS + 1)),
                "maxp" to table(32) {
                    putInt(0x00010000).putShort(GLYPHS)
                    repeat(4) { putShort(0) }
                    putShort(2)
                    repeat(8) { putShort(0) }
                },
                "post" to table(32) {
                    putInt(0x00030000).putInt(0).putShort(-100).putShort(50)
                    repeat(5) { putInt(0) }
                },
            )
            val count = tables.size
            var power = 1
            while (power * 2 <= count) power *= 2
            val header = 12 + 16 * count
            val output = ByteArrayOutputStream()
            val directory = ByteBuffer.allocate(header)
            directory.putInt(0x00010000).putShort(count).putShort(power * 16)
                .putShort(Integer.numberOfTrailingZeros(power)).putShort(count * 16 - power * 16)
            var offset = header
            val body = ByteArrayOutputStream()
            var headOffset = 0
            for ((tag, data) in tables) {
                val padded = data.copyOf((data.size + 3) and 3.inv())
                if (tag == "head") headOffset = offset
                directory.put(tag.toByteArray(Charsets.US_ASCII)).putInt(checksum(padded)).putInt(offset).putInt(data.size)
                body.write(padded)
                offset += padded.size
            }
            output.write(directory.array())
            output.write(body.toByteArray())
            val font = output.toByteArray()
            // head.checkSumAdjustment makes the whole font sum to 0xB1B0AFBA. zh-CN: head.checkSumAdjustment 使整个字体的校验和为 0xB1B0AFBA.
            ByteBuffer.wrap(font).putInt(headOffset + 8, (0xB1B0AFBAL - (checksum(font).toLong() and 0xFFFFFFFFL)).toInt())
            return font
        }

        private const val UNITS_PER_EM = 1000
        private const val GLYPHS = 2

        private fun ByteBuffer.putShort(value: Int): ByteBuffer = putShort(value.toShort())

        private fun table(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
            ByteBuffer.allocate(size).apply(fill).also { check(it.position() == size) { "table size ${it.position()}, expected $size" } }.array()

        private fun checksum(data: ByteArray): Int {
            var sum = 0
            val buffer = ByteBuffer.wrap(data)
            while (buffer.remaining() >= 4) sum += buffer.int
            var shift = 24
            while (buffer.hasRemaining()) {
                sum += (buffer.get().toInt() and 0xFF) shl shift
                shift -= 8
            }
            return sum
        }

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.fontbox.ttf.TTFSubsetter
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap

/**
 * Subsets the TrueType fonts Skia embeds whole into the temporary stamp document (roadmap Q191 (b), D60): from API 33
 * the Skia of the system `PdfDocument` writes the static Roboto (about 305 KB of font program) into every stamp
 * document however few of its glyphs a stamp draws. Skia writes such a font as a Type 0 font with `Identity-H`, so the
 * codes in the content are CIDs, mapped to glyph ids by `/CIDToGIDMap` (`/Identity` from Skia). For every font program
 * (`/FontFile2`) the glyph ids its CIDs reach are collected from the shown strings ([ShownStrings]); fontbox's
 * `TTFSubsetter` keeps those glyphs (and the components of composite glyphs) and numbers them anew, and each font gets
 * a `/CIDToGIDMap` stream from its CIDs to the new glyph ids, so the content, the `/W` widths and the `/ToUnicode` map,
 * all keyed by CID, stay as they are: text extraction and rendering do not change. The subset carries the tables a
 * `CIDFontType2` needs, those PDFBox embeds itself, plus a `cmap`, and its names get a six-letter tag unless Skia gave
 * them one. The `cmap` keeps the rendering of unhinted fonts: RobotoStatic has no `fpgm`, `prep` or `cvt`, so the
 * FreeType of the system `PdfRenderer` (pdfium) auto-hints it, and the auto-hinter finds the glyphs it measures (the
 * standard characters and blue-zone strings of each script, the digits) through the Unicode `cmap`; without them the
 * tops and bottoms of glyphs snap to other heights (106 of 1,416 inked pixels of the 50-page sample on API 33). Every
 * code point of a used glyph stays mapped (the auto-hinter assigns a glyph to the first script whose ranges reach it) and
 * the reference glyphs of Latin, and of Greek and Cyrillic when a used glyph belongs to them, are kept ([REFERENCES]).
 * `TTFSubsetter` takes Unicode code points only, so a glyph is added through the code points the font's `cmap` maps to
 * it; a font with a used glyph no code point reaches (a ligature or another substitution), a font that cannot be parsed
 * or subset, and a subset that is not smaller keep the font as Skia wrote it ([Result.skipped]): subsetting never fails
 * the stamp.
 * zh-CN: 对 Skia 整个嵌入临时印章文档的 TrueType 字体做子集化 (路线图 Q191 (b), D60): API 33 起, 系统 `PdfDocument` 的 Skia
 * 不论印章画了几个字形, 都把静态 Roboto (字体程序约 305 KB) 整个写进印章文档. Skia 把这种字体写成 `Identity-H` 的 Type 0 字体,
 * 内容里的编码就是 CID, 经 `/CIDToGIDMap` (Skia 写 `/Identity`) 映射到字形 id. 对每个字体程序 (`/FontFile2`), 从显示的字符串
 * ([ShownStrings]) 收集其 CID 用到的字形 id; fontbox 的 `TTFSubsetter` 保留这些字形 (及复合字形的组件) 并重新编号, 每个字体
 * 得到一个从 CID 到新字形 id 的 `/CIDToGIDMap` 流, 因此都以 CID 为键的内容, `/W` 宽度与 `/ToUnicode` 映射保持不变: 文本提取
 * 与渲染不变. 子集带 `CIDFontType2` 需要的表 (即 PDFBox 自己嵌入时用的那些) 再加 `cmap`, 名称加六个字母的标签 (Skia 已加过的
 * 除外). `cmap` 用来保持无微调指令字体的渲染: RobotoStatic 没有 `fpgm`, `prep` 与 `cvt`, 系统 `PdfRenderer` (pdfium) 的 FreeType
 * 对它做自动微调, 而自动微调器经 Unicode `cmap` 找到它要测量的字形 (各文字的标准字符与 blue zone 字符串, 以及数字); 缺了它们,
 * 字形的顶部与底部会对齐到别的高度 (API 33 上 50 页样本 1,416 个着墨像素中有 106 个不同). 因此用到的字形的全部码位都保留映射
 * (自动微调器把字形归入第一个覆盖它的文字), 并保留拉丁文, 以及用到的字形属于希腊文或西里尔文时这两种文字的参考字形 ([REFERENCES]).
 * `TTFSubsetter` 只接受 Unicode 码位, 所以字形经字体 `cmap` 中映射到它的码位加入; 用到了没有码位可达的字形 (连字或其他替换) 的
 * 字体, 无法解析或子集化的字体, 以及子集不更小的情况, 都保留 Skia 写出的原样 ([Result.skipped]): 子集化从不让印章失败.
 */
internal object FontSubsetting {

    /**
     * Tables of the subset: those `TrueTypeEmbedder` of PDFBox keeps for a `CIDFontType2`, plus `cmap` for the
     * auto-hinter.
     * zh-CN: 子集包含的表: PDFBox 的 `TrueTypeEmbedder` 为 `CIDFontType2` 保留的那些, 再加供自动微调器使用的 `cmap`.
     */
    private val TABLES = listOf("head", "hhea", "loca", "maxp", "cvt ", "prep", "glyf", "hmtx", "fpgm", "gasp", "cmap")

    /**
     * A script of the FreeType auto-hinter that a Skia font may cover: the code point ranges that make a used glyph
     * belong to it, and the characters the hinter measures for it (standard characters and blue-zone strings of
     * `afblue.dat`, for Latin also the digits it compares).
     * zh-CN: Skia 字体可能覆盖的 FreeType 自动微调器的一种文字: 让用到的字形归入它的码位范围, 以及微调器为它测量的字符
     * (`afblue.dat` 的标准字符与 blue zone 字符串, 拉丁文另含它比较的数字).
     */
    private class Script(val ranges: List<IntRange>, val references: String)

    /** Latin first: it is always kept, as digits and punctuation belong to it. zh-CN: 拉丁文在首位且总是保留, 数字与标点都归入它. */
    private val REFERENCES = listOf(
        Script(emptyList(), "THEZOCQS" + "HEZLOCUS" + "fijkdbh" + "uvxzoesc" + "nrxzoesc" + "pqgjy" + "oO" + "0123456789"),
        Script(
            listOf(0x0370..0x03FF, 0x1F00..0x1FFF),
            "\u0393\u0392\u0395\u0396\u0398\u039F\u03A9" +
            "\u0392\u0394\u0396\u039E\u0398\u039F" +
            "\u03B2\u03B8\u03B4\u03B6\u03BB\u03BE" +
            "\u03B1\u03B5\u03B9\u03BF\u03C0\u03C3\u03C4\u03C9" +
            "\u03B2\u03B3\u03B7\u03BC\u03C1\u03C6\u03C7\u03C8",
        ),
        Script(
            listOf(0x0400..0x052F, 0x1C80..0x1C8F, 0x2DE0..0x2DFF, 0xA640..0xA69F),
            "\u0411\u0412\u0415\u041F\u0417\u041E\u0421\u042D" +
            "\u0411\u0412\u0415\u0428\u0417\u041E\u0421\u042D" +
            "\u0445\u043F\u043D\u0448\u0435\u0437\u043E\u0441" +
            "\u0440\u0443\u0444",
        ),
    )

    private val SUBSET_TAG = Regex("^[A-Z]{6}\\+")

    /**
     * What [subset] did: font programs subset, their decoded bytes before and after, and why the others were kept.
     * zh-CN: [subset] 的结果: 子集化的字体程序数, 其解码后字节数的前后值, 以及其余字体保留原样的原因.
     */
    data class Result(val fonts: Int, val bytesBefore: Long, val bytesAfter: Long, val skipped: List<String>) {
        companion object {
            val NONE = Result(0, 0, 0, emptyList())
        }
    }

    /** One Type 0 font that uses a font program, with the CIDs it shows. zh-CN: 使用某个字体程序的一个 Type 0 字体及其显示的 CID. */
    private class User(val descendant: COSDictionary, val cids: Set<Int>, val cidToGid: (Int) -> Int)

    fun subset(document: PDDocument): Result {
        val programs = IdentityHashMap<COSStream, MutableList<User>>()
        val descriptors = IdentityHashMap<COSStream, COSDictionary>()
        val fonts = IdentityHashMap<COSStream, MutableList<COSDictionary>>()
        val skipped = ArrayList<String>()
        for ((font, strings) in ShownStrings.collect(document)) {
            if (COSName.TYPE0 != font.getCOSName(COSName.SUBTYPE)) continue
            val encoding = font.getCOSName(COSName.ENCODING)
            if (encoding != COSName.IDENTITY_H && encoding != COSName.IDENTITY_V) continue
            val descendant = (ShownStrings.resolve(font.getDictionaryObject(COSName.DESCENDANT_FONTS)) as? COSArray)
                ?.let { ShownStrings.resolve(it.getObject(0)) as? COSDictionary } ?: continue
            if (COSName.CID_FONT_TYPE2 != descendant.getCOSName(COSName.SUBTYPE)) continue
            val descriptor = ShownStrings.resolve(descendant.getDictionaryObject(COSName.FONT_DESC)) as? COSDictionary ?: continue
            val program = ShownStrings.resolve(descriptor.getDictionaryObject(COSName.FONT_FILE2)) as? COSStream ?: continue
            val cidToGid = cidToGid(descendant) ?: run {
                skipped += "${name(font)}: unsupported CIDToGIDMap"
                continue
            }
            val cids = HashSet<Int>()
            for (bytes in strings) {
                var index = 0
                while (index + 1 < bytes.size) {
                    cids += ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                    index += 2
                }
            }
            programs.getOrPut(program) { ArrayList() } += User(descendant, cids, cidToGid)
            descriptors[program] = descriptor
            fonts.getOrPut(program) { ArrayList() } += font
        }
        var subset = 0
        var before = 0L
        var after = 0L
        for ((program, users) in programs) {
            val label = name(fonts.getValue(program).first())
            val outcome = try {
                subsetProgram(document, program, descriptors.getValue(program), fonts.getValue(program), users)
            } catch (e: Exception) {
                // Anything fontbox throws on an odd font leaves that font whole. zh-CN: fontbox 对异常字体抛出的任何异常都让该字体保持原样.
                Outcome.Skipped("${e.javaClass.simpleName}: ${e.message}")
            }
            when (outcome) {
                is Outcome.Done -> {
                    subset++
                    before += outcome.before
                    after += outcome.after
                }
                is Outcome.Skipped -> skipped += "$label: ${outcome.reason}"
            }
        }
        return Result(subset, before, after, skipped)
    }

    private sealed interface Outcome {
        class Done(val before: Long, val after: Long) : Outcome
        class Skipped(val reason: String) : Outcome
    }

    private fun subsetProgram(
        document: PDDocument,
        program: COSStream,
        descriptor: COSDictionary,
        fonts: List<COSDictionary>,
        users: List<User>,
    ): Outcome {
        val original = program.createInputStream().use { it.readBytes() }
        val used = sortedSetOf<Int>()
        users.forEach { user -> user.cids.forEach { used += user.cidToGid(it) } }
        used -= 0
        val (bytes, oldToNew) = TTFParser(true).parse(ByteArrayInputStream(original)).use { font ->
            if (used.size >= font.numberOfGlyphs - 1) return Outcome.Skipped("every glyph is used")
            // The lookup TTFSubsetter itself uses. zh-CN: 与 TTFSubsetter 自身使用的相同的查找表.
            val cmap = font.unicodeCmapLookup
            val subsetter = TTFSubsetter(font, TABLES)
            val codes = HashSet<Int>()
            for (gid in used) {
                val mapped = cmap.getCharCodes(gid)?.filter { cmap.getGlyphId(it) == gid }.orEmpty()
                if (mapped.isEmpty()) return Outcome.Skipped("glyph $gid has no code point")
                codes += mapped
            }
            for ((index, script) in REFERENCES.withIndex()) {
                if (index > 0 && codes.none { code -> script.ranges.any { code in it } }) continue
                script.references.codePoints().forEach { if (cmap.getGlyphId(it) != 0) codes += it }
            }
            codes.forEach(subsetter::add)
            val newToOld = subsetter.getGIDMap()
            val oldToNew = HashMap<Int, Int>(newToOld.size * 2)
            newToOld.forEach { (new, old) -> oldToNew[old] = new }
            if (!used.all { it in oldToNew }) return Outcome.Skipped("the subset lost glyphs")
            val out = ByteArrayOutputStream()
            subsetter.writeToStream(out)
            val bytes = out.toByteArray()
            if (bytes.size >= original.size) return Outcome.Skipped("the subset of ${bytes.size} B is not smaller than ${original.size} B")
            // The subset must parse with the glyphs it was given. zh-CN: 子集必须能被解析, 且字形数与给定的一致.
            TTFParser(true).parse(ByteArrayInputStream(bytes)).use { check ->
                if (check.numberOfGlyphs != newToOld.size) return Outcome.Skipped("the subset has ${check.numberOfGlyphs} glyphs, not ${newToOld.size}")
            }
            bytes to oldToNew
        }
        program.createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
        program.setInt(COSName.LENGTH1, bytes.size)
        for (user in users) user.descendant.setItem(COSName.CID_TO_GID_MAP, cidToGidStream(document, user, oldToNew))
        // A CIDSet of the whole font would be stale. zh-CN: 整个字体的 CIDSet 会过时.
        descriptor.removeItem(COSName.CID_SET)
        tag(used, descriptor, fonts, users)
        return Outcome.Done(original.size.toLong(), bytes.size.toLong())
    }

    /**
     * The glyph id of a CID under the `/CIDToGIDMap` of [descendant] (absent or `/Identity`: the CID itself; a stream:
     * two bytes per CID), or null for anything else.
     * zh-CN: 在 [descendant] 的 `/CIDToGIDMap` 下 CID 对应的字形 id (缺省或 `/Identity`: CID 本身; 流: 每个 CID 两字节), 其他形式为 null.
     */
    private fun cidToGid(descendant: COSDictionary): ((Int) -> Int)? = when (val map = ShownStrings.resolve(descendant.getDictionaryObject(COSName.CID_TO_GID_MAP))) {
        null -> { cid -> cid }
        is COSName -> if (map == COSName.IDENTITY) { cid -> cid } else null
        is COSStream -> {
            val table = map.createInputStream().use { it.readBytes() }
            val lookup: (Int) -> Int = { cid -> if (2 * cid + 1 < table.size) ((table[2 * cid].toInt() and 0xFF) shl 8) or (table[2 * cid + 1].toInt() and 0xFF) else 0 }
            lookup
        }
        else -> null
    }

    private fun cidToGidStream(document: PDDocument, user: User, oldToNew: Map<Int, Int>): COSStream {
        val last = user.cids.maxOrNull() ?: 0
        val table = ByteArray(2 * (last + 1))
        for (cid in user.cids) {
            val gid = oldToNew[user.cidToGid(cid)] ?: 0
            table[2 * cid] = (gid shr 8).toByte()
            table[2 * cid + 1] = gid.toByte()
        }
        val stream = document.document.createCOSStream()
        stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(table) }
        return stream
    }

    /**
     * Prefixes the PostScript names with a subset tag derived from the kept glyphs (PDF 32000-1 9.6.4), unless Skia
     * tagged them already.
     * zh-CN: 给 PostScript 名称加上由保留字形导出的子集标签 (PDF 32000-1 9.6.4), Skia 已加过的除外.
     */
    private fun tag(used: Set<Int>, descriptor: COSDictionary, fonts: List<COSDictionary>, users: List<User>) {
        var hash = used.hashCode().toLong() and 0xFFFFFFFFL
        val tag = buildString {
            repeat(6) {
                append('A' + (hash % 26).toInt())
                hash /= 26
            }
        } + "+"
        fun retag(dictionary: COSDictionary, key: COSName) {
            val name = dictionary.getNameAsString(key) ?: return
            if (!SUBSET_TAG.containsMatchIn(name)) dictionary.setName(key, tag + name)
        }
        retag(descriptor, COSName.FONT_NAME)
        fonts.forEach { retag(it, COSName.BASE_FONT) }
        users.forEach { retag(it.descendant, COSName.BASE_FONT) }
    }

    private fun name(font: COSDictionary): String = font.getNameAsString(COSName.BASE_FONT) ?: "unnamed font"

}

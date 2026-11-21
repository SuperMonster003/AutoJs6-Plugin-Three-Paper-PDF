package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.fontbox.cmap.CMapParser
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.IOException
import java.text.Normalizer
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Repairs the `/ToUnicode` maps Skia writes into the temporary stamp document (spike S2): Skia maps each glyph back to
 * one code point of the font's `cmap`, and when several code points share a glyph it takes the lowest, so the CJK
 * ideographs whose glyphs double as radicals come out as radicals (文 as U+2F42 KANGXI RADICAL SCRIPT, 页 as U+2EDA CJK
 * RADICAL C-SIMPLIFIED LEAF) and text extraction or search finds the wrong characters. Knowing what was drawn, an entry
 * whose target was not drawn is mapped to what was: first to its NFKC form when every code point of that form was
 * drawn (Kangxi radicals, ligatures, compatibility characters), then to the one drawn code point that no entry accounts
 * for and that [sameGlyph] says draws the same glyph (CJK radicals supplement, whose code points have no decomposition).
 * Entries with more than one candidate stay as they are. Then glyphs that map to nothing or to U+0000 (a contextual
 * alternate without a code point, roadmap Q322) are placed on the text drawn on their page ([GlyphAlignment]): each text
 * object is one run of drawn text, so its known glyphs locate the run and give the unknown glyph the character drawn
 * there; codes that read differently at two places stay as they are.
 * zh-CN: 修复 Skia 写入临时印章文档的 `/ToUnicode` 映射 (spike S2): Skia 把每个字形映射回字体 `cmap` 中的一个码位, 多个码位共用
 * 一个字形时取最小者, 因此字形兼作部首的中日韩表意文字会被映射成部首 (文 成为 U+2F42 KANGXI RADICAL SCRIPT, 页 成为 U+2EDA CJK
 * RADICAL C-SIMPLIFIED LEAF), 文本提取与搜索得到错误的字符. 既然知道画了什么, 目标字符未被绘制的条目就改映射到实际绘制的字符:
 * 先看其 NFKC 形式, 该形式的每个码位都被绘制时采用它 (康熙部首, 连字, 兼容字符); 否则取没有任何条目对应, 且 [sameGlyph] 判定为
 * 同一字形的那个已绘制码位 (中日韩部首补充, 这些码位没有分解). 候选不止一个的条目保持不变. 然后把映射为空或 U+0000 的字形 (没有码位的
 * 上下文替代字形, 路线图 Q322) 放回其所在页画的文字上 ([GlyphAlignment]): 每个文本对象是所画文字中连续的一段, 其中的已知字形确定这段
 * 文字的位置, 未知字形取该处所画的字符; 在两处读法不同的编码保持不变.
 */
internal object ToUnicodeRepair {

    /** Entries per `bfchar` block (the CMap syntax allows at most 100). zh-CN: 每个 `bfchar` 块的条目数 (CMap 语法上限 100). */
    private const val ENTRIES_PER_BLOCK = 100

    /**
     * Repairs every `/ToUnicode` map reachable from the pages of [document] (page fonts, fonts of form XObjects and of
     * Type 3 glyph resources) for the texts drawn on each page ([texts] by page index; a text may hold several lines);
     * answers how many entries changed.
     * zh-CN: 针对各页所画的文字 ([texts] 以页序号为下标; 一段文字可以有多行), 修复从 [document] 各页可达的全部 `/ToUnicode` 映射
     * (页面字体, 表单 XObject 与 Type 3 字形资源中的字体); 返回改动的条目数.
     */
    fun repair(document: PDDocument, texts: List<Collection<String>>, sameGlyph: (Int, Int) -> Boolean): Int {
        val drawn = HashSet<Int>()
        texts.forEach { page -> page.forEach { text -> text.codePoints().forEach { drawn += it } } }
        val maps = ArrayList<Pair<COSStream, Int>>()
        val seen = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        for (page in document.pages) collectFonts(page.cosObject.getDictionaryObject(COSName.RESOURCES), seen, maps, 0)
        val parsed = maps.map { (stream, codeBytes) -> Triple(stream, codeBytes, read(stream, codeBytes)) }
        val accounted = HashSet<Int>()
        for ((_, _, mappings) in parsed) for (target in mappings.values) target.codePoints().forEach { accounted += it }
        val current = IdentityHashMap<COSStream, Map<Int, String>>()
        val fixes = IdentityHashMap<COSStream, MutableMap<Int, String>>()
        for ((stream, _, mappings) in parsed) {
            val planned = plan(mappings, drawn, accounted, sameGlyph)
            current[stream] = mappings + planned
            if (planned.isNotEmpty()) fixes[stream] = LinkedHashMap(planned)
        }
        if (current.values.any { mappings -> mappings.values.any(::unknown) }) {
            for ((stream, placed) in place(document, texts, current)) fixes.getOrPut(stream) { LinkedHashMap() }.putAll(placed)
        }
        var changed = 0
        for ((stream, codeBytes, mappings) in parsed) {
            val streamFixes = fixes[stream] ?: continue
            write(stream, codeBytes, mappings + streamFixes)
            changed += streamFixes.size
        }
        return changed
    }

    /**
     * A target that names no character: none, empty or control characters only (Skia writes U+0000).
     * zh-CN: 不代表任何字符的目标: 无, 空或只含控制字符 (Skia 写 U+0000).
     */
    fun unknown(target: String?): Boolean = target == null || target.isEmpty() || target.all { it.code < 0x20 }

    /**
     * The glyphs with an [unknown] target placed on the texts of their pages ([GlyphAlignment]), by `/ToUnicode` stream
     * and code; a code that reads differently in two text objects is left out. [current] holds the maps with the fixes
     * of [plan] applied.
     * zh-CN: 把目标 [unknown] 的字形放回其所在页的文字上 ([GlyphAlignment]) 得到的映射, 按 `/ToUnicode` 流与编码组织; 在两个文本对象中
     * 读法不同的编码不在其中. [current] 是已应用 [plan] 修正的映射.
     */
    private fun place(document: PDDocument, texts: List<Collection<String>>, current: Map<COSStream, Map<Int, String>>): Map<COSStream, Map<Int, String>> {
        val readings = IdentityHashMap<COSStream, HashMap<Int, MutableSet<Int>>>()
        document.pages.forEachIndexed { index, page ->
            val lines = texts.getOrNull(index).orEmpty().flatMap { it.split('\n') }.filter { it.isNotEmpty() }.distinct().map { it.codePoints().toArray() }
            if (lines.isEmpty()) return@forEachIndexed
            for (shown in ShownStrings.textObjects(page)) {
                val glyphs = ArrayList<Pair<COSStream, Int>>()
                for (string in shown) {
                    val stream = resolve(string.font.getDictionaryObject(COSName.TO_UNICODE)) as? COSStream ?: break
                    val codeBytes = if (COSName.TYPE0 == string.font.getCOSName(COSName.SUBTYPE)) 2 else 1
                    val bytes = string.bytes
                    var at = 0
                    while (at + codeBytes <= bytes.size) {
                        var code = 0
                        repeat(codeBytes) { code = (code shl 8) or (bytes[at++].toInt() and 0xFF) }
                        glyphs += stream to code
                    }
                }
                val targets = glyphs.map { (stream, code) -> current[stream]?.get(code)?.takeUnless(::unknown) }
                if (targets.none { it == null }) continue
                for ((glyph, codePoint) in GlyphAlignment.place(targets, lines)) {
                    val (stream, code) = glyphs[glyph]
                    readings.getOrPut(stream) { HashMap() }.getOrPut(code) { HashSet() } += codePoint
                }
            }
        }
        val result = IdentityHashMap<COSStream, Map<Int, String>>()
        for ((stream, codes) in readings) {
            val decided = codes.filterValues { it.size == 1 }.mapValues { String(Character.toChars(it.value.first())) }
            if (decided.isNotEmpty()) result[stream] = decided
        }
        return result
    }

    /**
     * The corrected targets of [mappings] (code to target), see the class comment; [accounted] holds every code point
     * that some entry of the document maps to.
     * zh-CN: [mappings] (编码到目标) 中需要更正的目标, 见类注释; [accounted] 是文档中有条目映射到的全部码位.
     */
    fun plan(mappings: Map<Int, String>, drawn: Set<Int>, accounted: Set<Int>, sameGlyph: (Int, Int) -> Boolean): Map<Int, String> {
        val fixes = LinkedHashMap<Int, String>()
        val unaccounted = drawn.filter { it !in accounted && !Character.isWhitespace(it) }
        for ((code, target) in mappings) {
            val points = target.codePoints().toArray()
            if (points.isEmpty() || points.all { it in drawn }) continue
            val normalized = Normalizer.normalize(target, Normalizer.Form.NFKC)
            if (normalized != target && normalized.isNotEmpty() && normalized.codePoints().allMatch { it in drawn }) {
                fixes[code] = normalized
                continue
            }
            if (points.size != 1) continue
            val matches = unaccounted.filter { sameGlyph(points[0], it) }
            if (matches.size == 1) fixes[code] = String(Character.toChars(matches[0]))
        }
        return fixes
    }

    private fun collectFonts(resources: COSBase?, seen: MutableSet<COSBase>, out: MutableList<Pair<COSStream, Int>>, depth: Int) {
        val dictionary = resolve(resources) as? COSDictionary ?: return
        if (depth > 8 || !seen.add(dictionary)) return
        (resolve(dictionary.getDictionaryObject(COSName.FONT)) as? COSDictionary)?.let { fonts ->
            for (key in fonts.keySet()) {
                val font = resolve(fonts.getDictionaryObject(key)) as? COSDictionary ?: continue
                if (!seen.add(font)) continue
                val toUnicode = resolve(font.getDictionaryObject(COSName.TO_UNICODE)) as? COSStream
                // Type 0 fonts of Skia use Identity-H, two bytes per code; simple and Type 3 fonts one byte.
                // zh-CN: Skia 的 Type 0 字体用 Identity-H, 每个编码两字节; 简单字体与 Type 3 字体一字节.
                if (toUnicode != null) out += toUnicode to (if (COSName.TYPE0 == font.getCOSName(COSName.SUBTYPE)) 2 else 1)
                collectFonts(font.getDictionaryObject(COSName.RESOURCES), seen, out, depth + 1)
            }
        }
        (resolve(dictionary.getDictionaryObject(COSName.XOBJECT)) as? COSDictionary)?.let { objects ->
            for (key in objects.keySet()) {
                val form = resolve(objects.getDictionaryObject(key)) as? COSStream ?: continue
                if (COSName.FORM == form.getCOSName(COSName.SUBTYPE)) collectFonts(form.getDictionaryObject(COSName.RESOURCES), seen, out, depth + 1)
            }
        }
    }

    private fun resolve(base: COSBase?): COSBase? = if (base is COSObject) base.getObject() else base

    private fun read(stream: COSStream, codeBytes: Int): Map<Int, String> {
        val cmap = try {
            stream.createInputStream().use { CMapParser(true).parse(it) }
        } catch (e: IOException) {
            return emptyMap()
        }
        val result = LinkedHashMap<Int, String>()
        val last = if (codeBytes == 2) 0xFFFF else 0xFF
        for (code in 0..last) cmap.toUnicode(code)?.let { result[code] = it }
        return result
    }

    private fun write(stream: COSStream, codeBytes: Int, mappings: Map<Int, String>) {
        val digits = codeBytes * 2
        val text = buildString {
            append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
            append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
            append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
            append("1 begincodespacerange\n<").append("0".repeat(digits)).append("> <").append("F".repeat(digits)).append(">\nendcodespacerange\n")
            for (block in mappings.entries.sortedBy { it.key }.chunked(ENTRIES_PER_BLOCK)) {
                append(block.size).append(" beginbfchar\n")
                for ((code, target) in block) {
                    append('<').append(code.toString(16).uppercase().padStart(digits, '0')).append("> <")
                    for (unit in target) append(unit.code.toString(16).uppercase().padStart(4, '0'))
                    append(">\n")
                }
                append("endbfchar\n")
            }
            append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        }
        stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(text.toByteArray(Charsets.US_ASCII)) }
    }

}

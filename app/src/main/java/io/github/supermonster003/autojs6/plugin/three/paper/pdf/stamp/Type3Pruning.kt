package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import java.util.IdentityHashMap

/**
 * Drops the glyph procedures that no text uses from the Type 3 fonts of the temporary stamp document (spike S2): the
 * Skia of API 24 turns CFF glyphs (the system CJK font) into Type 3 fonts that carry the outlines of a whole block of
 * 255 glyphs, so four CJK characters of a page number cost about 580 KB. The codes shown by `Tj`, `TJ`, `'` and `"` on
 * the pages and in their form XObjects are collected per font; a font whose every used code resolves through its
 * `/Differences` keeps only those procedures, any other font is left alone.
 * zh-CN: 从临时印章文档的 Type 3 字体中去掉没有文字用到的字形过程 (spike S2): API 24 的 Skia 把 CFF 字形 (系统中日韩字体) 转成
 * Type 3 字体, 每个字体带一整块 255 个字形的轮廓, 页码里的四个汉字就要约 580 KB. 按字体收集页面及其表单 XObject 中 `Tj`, `TJ`,
 * `'` 与 `"` 显示的编码; 每个用到的编码都能经 `/Differences` 解析的字体只保留这些字形过程, 其他字体不动.
 */
internal object Type3Pruning {

    /** Prunes [document]; answers the number of glyph procedures removed. zh-CN: 精简 [document]; 返回去掉的字形过程数. */
    fun prune(document: PDDocument): Int {
        val used = IdentityHashMap<COSDictionary, MutableSet<Int>>()
        for (page in document.pages) scan(page, page.resources, used, 0)
        var removed = 0
        for ((font, codes) in used) removed += prune(font, codes)
        return removed
    }

    private fun scan(stream: PDContentStream, resources: PDResources?, used: MutableMap<COSDictionary, MutableSet<Int>>, depth: Int) {
        if (depth > 8) return
        val tokens = PDFStreamParser(stream).apply { parse() }.tokens
        val operands = ArrayList<Any>()
        var font: COSDictionary? = null
        fun shown(base: Any?) {
            val target = font ?: return
            if (base is COSString) base.bytes.forEach { used.getOrPut(target) { HashSet() } += it.toInt() and 0xFF }
        }
        for (token in tokens) {
            if (token !is Operator) {
                operands += token
                continue
            }
            when (token.name) {
                "Tf" -> font = (operands.firstOrNull() as? COSName)?.let { name -> resolve(fonts(resources)?.getDictionaryObject(name)) as? COSDictionary }
                "Tj", "'", "\"" -> shown(operands.lastOrNull())
                "TJ" -> (operands.lastOrNull() as? COSArray)?.forEach { shown(it) }
                "Do" -> (operands.firstOrNull() as? COSName)?.let { name ->
                    (resources?.getXObject(name) as? PDFormXObject)?.let { scan(it, it.resources, used, depth + 1) }
                }
            }
            operands.clear()
        }
    }

    private fun fonts(resources: PDResources?): COSDictionary? = resolve(resources?.cosObject?.getDictionaryObject(COSName.FONT)) as? COSDictionary

    private fun resolve(base: COSBase?): COSBase? = if (base is COSObject) base.getObject() else base

    private fun prune(font: COSDictionary, codes: Set<Int>): Int {
        if (COSName.TYPE3 != font.getCOSName(COSName.SUBTYPE)) return 0
        val procedures = resolve(font.getDictionaryObject(COSName.CHAR_PROCS)) as? COSDictionary ?: return 0
        val encoding = resolve(font.getDictionaryObject(COSName.ENCODING)) as? COSDictionary ?: return 0
        val differences = resolve(encoding.getDictionaryObject(COSName.DIFFERENCES)) as? COSArray ?: return 0
        val names = HashMap<Int, COSName>()
        var code = -1
        for (item in differences) {
            when (val value = resolve(item)) {
                is COSNumber -> code = value.intValue()
                is COSName -> if (code >= 0) names[code++] = value
                else -> return 0
            }
        }
        val keep = codes.map { names[it] ?: return 0 }.toSet()
        val unused = procedures.keySet().filter { it !in keep }
        unused.forEach { procedures.removeItem(it) }
        return unused.size
    }

}

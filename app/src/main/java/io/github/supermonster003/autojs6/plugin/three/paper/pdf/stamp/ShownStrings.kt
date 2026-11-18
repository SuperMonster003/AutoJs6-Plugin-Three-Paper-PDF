package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import java.util.IdentityHashMap

/**
 * The strings each font shows in the temporary stamp document (spike S2, roadmap Q191): the operands of `Tj`, `TJ`,
 * `'` and `"` on the pages and in their form XObjects, keyed by the font dictionary `Tf` selected (identity, so two
 * resource names of one font object count once). [Type3Pruning] and [FontSubsetting] read the codes from them.
 * zh-CN: 临时印章文档中每个字体显示的字符串 (spike S2, 路线图 Q191): 页面及其表单 XObject 中 `Tj`, `TJ`, `'` 与 `"` 的操作数,
 * 以 `Tf` 选中的字体字典为键 (按对象身份, 同一字体对象的两个资源名只算一次). [Type3Pruning] 与 [FontSubsetting] 从中读取编码.
 */
internal object ShownStrings {

    /** Nesting of form XObjects followed. zh-CN: 跟随的表单 XObject 嵌套层数. */
    private const val MAX_DEPTH = 8

    fun collect(document: PDDocument): Map<COSDictionary, List<ByteArray>> {
        val shown = IdentityHashMap<COSDictionary, MutableList<ByteArray>>()
        for (page in document.pages) scan(page, page.resources, shown, 0)
        return shown
    }

    private fun scan(stream: PDContentStream, resources: PDResources?, shown: MutableMap<COSDictionary, MutableList<ByteArray>>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val tokens = PDFStreamParser(stream).apply { parse() }.tokens
        val operands = ArrayList<Any>()
        var font: COSDictionary? = null
        fun show(base: Any?) {
            val target = font ?: return
            if (base is COSString) shown.getOrPut(target) { ArrayList() } += base.bytes
        }
        for (token in tokens) {
            if (token !is Operator) {
                operands += token
                continue
            }
            when (token.name) {
                "Tf" -> font = (operands.firstOrNull() as? COSName)?.let { name -> resolve(fonts(resources)?.getDictionaryObject(name)) as? COSDictionary }
                "Tj", "'", "\"" -> show(operands.lastOrNull())
                "TJ" -> (operands.lastOrNull() as? COSArray)?.forEach { show(it) }
                "Do" -> (operands.firstOrNull() as? COSName)?.let { name ->
                    (resources?.getXObject(name) as? PDFormXObject)?.let { scan(it, it.resources, shown, depth + 1) }
                }
            }
            operands.clear()
        }
    }

    private fun fonts(resources: PDResources?): COSDictionary? = resolve(resources?.cosObject?.getDictionaryObject(COSName.FONT)) as? COSDictionary

    fun resolve(base: COSBase?): COSBase? = if (base is COSObject) base.getObject() else base

}

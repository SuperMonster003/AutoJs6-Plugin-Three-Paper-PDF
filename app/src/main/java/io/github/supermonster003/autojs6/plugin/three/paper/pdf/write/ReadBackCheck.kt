package io.github.supermonster003.autojs6.plugin.three.paper.pdf.write

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSBoolean
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNull
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * The PDFBox half of the check of an object stream output (roadmap D65): the written file loads again, has the page
 * count of the source, every page has the same decoded content (SHA-256 of its content streams) and the same resource
 * names, each resolving to a dictionary or stream, and the document information has the same entries. The answer is
 * null when everything matches, else the first difference.
 * zh-CN: 对象流输出校验中由 PDFBox 完成的一半 (路线图 D65): 写出的文件能重新载入, 页数与源文档相同, 每页解码后的内容 (内容流的
 * SHA-256) 相同, 资源名称相同且都解析为字典或流, 文档信息的条目相同. 全部一致时返回 null, 否则返回第一处差异.
 */
internal object ReadBackCheck {

    fun verify(source: PDDocument, file: File, directory: File, stop: StopSignal = StopSignal.NEVER): String? {
        val written = try {
            PDDocument.load(file, MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory))
        } catch (e: IOException) {
            return "PDFBox cannot load the written file: ${e.message}"
        }
        written.use { copy ->
            if (copy.numberOfPages != source.numberOfPages) return "the written file has ${copy.numberOfPages} pages, the source ${source.numberOfPages}"
            for (index in 0 until source.numberOfPages) {
                stop.checkStop()
                val expected = source.getPage(index)
                val actual = copy.getPage(index)
                val difference = try {
                    comparePage(index + 1, expected, actual)
                } catch (e: IOException) {
                    "page ${index + 1} cannot be read: ${e.message}"
                }
                if (difference != null) return difference
            }
            val sourceInfo = source.documentInformation.cosObject
            val writtenInfo = copy.documentInformation.cosObject
            for (key in sourceInfo.keySet() + writtenInfo.keySet()) {
                if (!same(sourceInfo.getDictionaryObject(key), writtenInfo.getDictionaryObject(key))) return "the document information differs at /${key.name}"
            }
        }
        return null
    }

    private fun comparePage(page: Int, expected: PDPage, actual: PDPage): String? {
        val number = "page $page"
        if (!digest(expected.contents).contentEquals(digest(actual.contents))) return "$number content differs"
        val sourceResources = resolve(expected.cosObject.getDictionaryObject(COSName.RESOURCES)) ?: expected.resources?.cosObject
        val writtenResources = resolve(actual.cosObject.getDictionaryObject(COSName.RESOURCES)) ?: actual.resources?.cosObject
        val names = resourceNames(sourceResources as? COSDictionary)
        val written = resourceNames(writtenResources as? COSDictionary)
        if (names.keys != written.keys) return "$number resources differ: ${names.keys} against ${written.keys}"
        for ((name, resolved) in written) if (!resolved) return "$number resource $name does not resolve"
        return null
    }

    /** Resource names (`/Font/F1`) and whether each resolves to a dictionary or stream. zh-CN: 资源名称及其是否解析为字典或流. */
    private fun resourceNames(resources: COSDictionary?): Map<String, Boolean> {
        val result = LinkedHashMap<String, Boolean>()
        if (resources == null) return result
        for (kind in resources.keySet()) {
            val entries = resolve(resources.getDictionaryObject(kind)) as? COSDictionary ?: continue
            for (name in entries.keySet()) result["/${kind.name}/${name.name}"] = resolve(entries.getDictionaryObject(name)) is COSDictionary
        }
        return result
    }

    private fun digest(input: InputStream?): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        input?.use { stream ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun resolve(base: COSBase?): COSBase? = if (base is COSObject) base.getObject() else base

    /** Value equality of document information entries. zh-CN: 文档信息条目的值相等. */
    private fun same(first: COSBase?, second: COSBase?): Boolean {
        val a = resolve(first).let { if (it is COSNull) null else it }
        val b = resolve(second).let { if (it is COSNull) null else it }
        return when {
            a == null || b == null -> a == null && b == null
            a is COSString && b is COSString -> a.bytes.contentEquals(b.bytes)
            a is COSName && b is COSName -> a == b
            a is COSNumber && b is COSNumber -> a.floatValue() == b.floatValue()
            a is COSBoolean && b is COSBoolean -> a.value == b.value
            a is COSArray && b is COSArray -> a.size() == b.size() && (0 until a.size()).all { same(a.get(it), b.get(it)) }
            else -> a.javaClass == b.javaClass
        }
    }

}

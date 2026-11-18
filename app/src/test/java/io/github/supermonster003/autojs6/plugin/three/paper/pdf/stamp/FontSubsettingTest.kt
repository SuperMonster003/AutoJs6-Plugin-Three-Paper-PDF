package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FontSubsetting] only touches Type 0 fonts with an `Identity` encoding and an embedded TrueType program, and a program
 * it cannot parse stays as it was: subsetting never fails the stamp (roadmap Q191 (b)). The subsetting itself needs a
 * real TrueType font and runs in `FontSubsettingDeviceTest`.
 * zh-CN: [FontSubsetting] 只处理 `Identity` 编码且嵌入 TrueType 程序的 Type 0 字体, 无法解析的字体程序保持原样: 子集化从不让印章
 * 失败 (路线图 Q191 (b)). 子集化本身需要真实的 TrueType 字体, 在 `FontSubsettingDeviceTest` 中运行.
 */
class FontSubsettingTest {

    private fun PDDocument.stream(bytes: ByteArray) = PDStream(this).apply { createOutputStream().use { it.write(bytes) } }

    private fun PDDocument.type0(program: ByteArray?, encoding: COSName = COSName.IDENTITY_H): COSDictionary {
        val descriptor = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT_DESC)
            setName(COSName.FONT_NAME, "Sample")
            if (program != null) setItem(COSName.FONT_FILE2, stream(program).cosObject)
        }
        val descendant = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.CID_FONT_TYPE2)
            setName(COSName.BASE_FONT, "Sample")
            setItem(COSName.FONT_DESC, descriptor)
            setItem(COSName.CID_TO_GID_MAP, COSName.IDENTITY)
        }
        return COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.TYPE0)
            setName(COSName.BASE_FONT, "Sample")
            setItem(COSName.ENCODING, encoding)
            setItem(COSName.DESCENDANT_FONTS, COSArray().apply { add(descendant) })
        }
    }

    private fun PDDocument.page(fonts: Map<String, COSDictionary>, content: String) {
        val page = PDPage().apply {
            resources = PDResources().apply {
                cosObject.setItem(COSName.FONT, COSDictionary().apply { fonts.forEach { (name, font) -> setItem(COSName.getPDFName(name), font) } })
            }
        }
        page.setContents(stream(content.toByteArray(Charsets.US_ASCII)))
        addPage(page)
    }

    private fun program(font: COSDictionary): ByteArray {
        val descendant = (font.getDictionaryObject(COSName.DESCENDANT_FONTS) as COSArray).getObject(0) as COSDictionary
        val descriptor = descendant.getDictionaryObject(COSName.FONT_DESC) as COSDictionary
        return descriptor.getCOSStream(COSName.FONT_FILE2).createInputStream().use { it.readBytes() }
    }

    @Test
    fun `a program that does not parse stays as it was`() {
        val garbage = ByteArray(4096) { (it * 31).toByte() }
        PDDocument().use { document ->
            val font = document.type0(garbage)
            document.page(mapOf("F1" to font), "BT /F1 12 Tf <00410042> Tj ET")
            val result = FontSubsetting.subset(document)
            assertEquals(0, result.fonts)
            assertEquals(result.skipped.toString(), 1, result.skipped.size)
            assertTrue(result.skipped.single(), result.skipped.single().startsWith("Sample: "))
            assertArrayEquals(garbage, program(font))
            assertEquals("Sample", font.getNameAsString(COSName.BASE_FONT))
        }
    }

    @Test
    fun `fonts it does not handle are left out`() {
        PDDocument().use { document ->
            val noProgram = document.type0(null)
            val otherEncoding = document.type0(ByteArray(64), encoding = COSName.getPDFName("UniGB-UCS2-H"))
            val unused = document.type0(ByteArray(64))
            document.page(mapOf("F1" to noProgram, "F2" to otherEncoding, "F3" to unused), "BT /F1 12 Tf <0041> Tj /F2 12 Tf <0042> Tj ET")
            val result = FontSubsetting.subset(document)
            assertEquals(FontSubsetting.Result.NONE, result)
            assertArrayEquals(ByteArray(64), program(otherEncoding))
        }
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Type3Pruning] keeps the glyph procedures that page and form content shows and leaves fonts it cannot resolve.
 * zh-CN: [Type3Pruning] 保留页面与表单内容显示的字形过程, 无法解析的字体保持不动.
 */
class Type3PruningTest {

    private fun PDDocument.stream(content: String) = PDStream(this).apply { createOutputStream().use { it.write(content.toByteArray(Charsets.US_ASCII)) } }

    private fun PDDocument.type3(glyphs: Int, differences: Boolean = true): COSDictionary {
        val procedures = COSDictionary()
        val names = COSArray().apply { add(COSInteger.get(1)) }
        for (index in 1..glyphs) {
            val name = COSName.getPDFName("g$index")
            procedures.setItem(name, stream("0 0 d0").cosObject)
            names.add(name)
        }
        return COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setItem(COSName.SUBTYPE, COSName.TYPE3)
            setItem(COSName.CHAR_PROCS, procedures)
            if (differences) setItem(COSName.ENCODING, COSDictionary().apply { setItem(COSName.DIFFERENCES, names) })
        }
    }

    private fun procedures(font: COSDictionary): Set<String> = (font.getDictionaryObject(COSName.CHAR_PROCS) as COSDictionary).keySet().map { it.name }.toSet()

    @Test
    fun `procedures shown by page and form text stay, the rest go`() {
        PDDocument().use { document ->
            val pageFont = document.type3(5)
            val formFont = document.type3(4)
            val unresolved = document.type3(3, differences = false)
            val form = PDFormXObject(document.stream("BT /F2 12 Tf [<02> 10 <03>] TJ /F3 9 Tf <01> Tj ET"))
            form.resources = PDResources().apply {
                cosObject.setItem(COSName.FONT, COSDictionary().apply {
                    setItem(COSName.getPDFName("F2"), formFont)
                    setItem(COSName.getPDFName("F3"), unresolved)
                })
            }
            val resources = PDResources().apply {
                cosObject.setItem(COSName.FONT, COSDictionary().apply { setItem(COSName.getPDFName("F1"), pageFont) })
                cosObject.setItem(COSName.XOBJECT, COSDictionary().apply { setItem(COSName.getPDFName("Fm1"), form) })
            }
            val page = PDPage().apply { this.resources = resources }
            page.setContents(document.stream("BT /F1 12 Tf <0104> Tj 0 -14 Td <04> ' ET /Fm1 Do"))
            document.addPage(page)

            assertEquals(3 + 2, Type3Pruning.prune(document))
            assertEquals(setOf("g1", "g4"), procedures(pageFont))
            assertEquals(setOf("g2", "g3"), procedures(formFont))
            assertEquals(setOf("g1", "g2", "g3"), procedures(unresolved))
        }
    }

}

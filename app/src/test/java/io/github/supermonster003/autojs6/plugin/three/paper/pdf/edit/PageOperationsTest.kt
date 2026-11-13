package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Page operations on generated fixtures (roadmap P2.4): page counts and rotation of extract, delete, reorder, rotate,
 * split and merge, the pruned outline and named destinations, inherited page attributes, kept metadata with the plugin
 * as `producer`, and the owner password rule for encrypted inputs (appendix D Q30).
 * zh-CN: 在生成的夹具上验证页面操作 (路线图 P2.4): 提取, 删除, 重排, 旋转, 拆分与合并的页数与旋转, 修剪后的大纲与命名目标,
 * 继承的页面属性, 保留的元数据与写入插件名的 `producer`, 以及加密输入的所有者密码规则 (附录 D Q30).
 */
class PageOperationsTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    /** Writes [document] like an operation does and parses the result again. zh-CN: 像操作那样写出 [document] 并重新解析结果. */
    private fun written(document: PDDocument): PDDocument {
        val output = ByteArrayOutputStream()
        val size = PdfWriting.write(document, output, StopSignal.NEVER)
        assertEquals(output.size().toLong(), size)
        return PDDocument.load(output.toByteArray())
    }

    private fun save(bytes: ByteArray, vararg pairs: Pair<String, Any?>, password: String? = null): PDDocument =
        PDDocument.load(bytes, password.orEmpty()).use { document ->
            val plan = PageEditPlan.save(options(*pairs), document.numberOfPages)
            PageEditor.apply(document, plan, StopSignal.NEVER)
            written(document)
        }

    private fun PDDocument.rotations() = pages.map { it.rotation }

    private fun PDOutlineNode.titles(): List<String> = children().map { it.title }

    @Test
    fun `extract keeps the pages, their outline entries and the metadata`() {
        save(PdfFixtures.multiPageWithOutline(), PdfContract.KEY_PAGES to "3-6").use { document ->
            assertEquals(4, document.numberOfPages)
            val outline = checkNotNull(document.documentCatalog.documentOutline)
            assertEquals(listOf("Chapter 2", "Chapter 3", PdfFixtures.OUTLINE_WITHOUT_PAGE), outline.titles())
            val chapter = outline.firstChild
            assertEquals(listOf("Chapter 2, section 1"), chapter.titles())
            assertEquals(0, document.pages.indexOf(chapter.findDestinationPage(document)))
            assertEquals(3, document.pages.indexOf(outline.children().elementAt(1).firstChild.findDestinationPage(document)))
            val information = document.documentInformation
            assertEquals(PdfFixtures.TITLE, information.title)
            assertEquals(PdfFixtures.AUTHOR, information.author)
            assertEquals(PdfFixtures.CREATOR, information.creator)
            assertEquals(ThreePaperPdfContract.PRODUCER, information.producer)
            assertTrue(information.modificationDate.after(information.creationDate))
        }
    }

    @Test
    fun `delete drops an outline entry only when nothing under it is kept`() {
        save(PdfFixtures.multiPageWithOutline(), PdfContract.KEY_DELETE_PAGES to "1").use { document ->
            assertEquals(5, document.numberOfPages)
            val outline = checkNotNull(document.documentCatalog.documentOutline)
            assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3", PdfFixtures.OUTLINE_WITHOUT_PAGE), outline.titles())
            // Its page is gone, its section is kept: the entry stays without a target. zh-CN: 其页已删除, 其小节保留: 条目保留但无目标.
            val chapter = outline.firstChild
            assertNull(chapter.destination)
            assertEquals(0, document.pages.indexOf(chapter.firstChild.findDestinationPage(document)))
        }
    }

    @Test
    fun `rotate turns every page or the given pages clockwise`() {
        val bytes = PdfFixtures.rotatedPages()
        save(bytes, PdfContract.KEY_DEGREES to 90).use { assertEquals(listOf(90, 180, 270, 0, 90), it.rotations()) }
        save(bytes, PdfContract.KEY_DEGREES to -90, PdfContract.KEY_ROTATE_PAGES to "1,5").use { assertEquals(listOf(270, 90, 180, 270, 270), it.rotations()) }
    }

    @Test
    fun `reorder moves pages with their boxes and rotation`() {
        save(PdfFixtures.rotatedPages(), PdfContract.KEY_ORDER to intArrayOf(5, 4, 3, 2, 1)).use { document ->
            assertEquals(listOf(0, 270, 180, 90, 0), document.rotations())
            assertEquals(PDRectangle.LETTER.height, document.getPage(0).mediaBox.width, 0.01f)
            assertEquals(PDRectangle.A4.width, document.getPage(4).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun `a save without keys writes an unchanged copy`() {
        save(PdfFixtures.multiPageWithOutline()).use { document ->
            assertEquals(6, document.numberOfPages)
            assertEquals(4, document.documentCatalog.documentOutline.titles().size)
            assertEquals(ThreePaperPdfContract.PRODUCER, document.documentInformation.producer)
        }
    }

    @Test
    fun `split parts hold their pages and only their outline entries`() {
        val bytes = PdfFixtures.multiPageWithOutline()
        val parts = PDDocument.load(bytes).use { PageEditPlan.split(options(PdfContract.KEY_EVERY to 2), it.numberOfPages) }
        assertEquals(3, parts.size)
        parts.forEachIndexed { index, pages ->
            PDDocument.load(bytes).use { document ->
                PageEditor.apply(document, pages.map { PagePlacement(it) }, StopSignal.NEVER)
                written(document).use { part ->
                    assertEquals(2, part.numberOfPages)
                    assertEquals(listOf(PdfFixtures.CHAPTERS[index], PdfFixtures.OUTLINE_WITHOUT_PAGE), part.documentCatalog.documentOutline.titles())
                }
            }
        }
    }

    @Test
    fun `pages keep attributes inherited from the page tree`() {
        val bytes = PDDocument().use { document ->
            repeat(3) { document.addPage(PDPage(PDRectangle.A4)) }
            val root = document.documentCatalog.cosObject.getDictionaryObject(COSName.PAGES) as COSDictionary
            root.setItem(COSName.ROTATE, COSInteger.get(90))
            root.setItem(COSName.MEDIA_BOX, PDRectangle.LETTER.cosArray)
            document.pages.forEach { it.cosObject.removeItem(COSName.MEDIA_BOX) }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        save(bytes, PdfContract.KEY_DELETE_PAGES to "1", PdfContract.KEY_DEGREES to 90, PdfContract.KEY_ROTATE_PAGES to "3").use { document ->
            assertEquals(listOf(90, 180), document.rotations())
            document.pages.forEach { assertEquals(PDRectangle.LETTER.width, it.mediaBox.width, 0.01f) }
        }
    }

    @Test
    fun `named destinations and links to removed pages are dropped`() {
        val bytes = PDDocument().use { document ->
            val pages = List(3) { PDPage(PDRectangle.A4).also(document::addPage) }
            fun target(page: PDPage) = COSArray().apply { add(page.cosObject); add(COSName.getPDFName("Fit")) }
            val names = COSArray()
            pages.forEachIndexed { index, page -> names.add(COSString("p${index + 1}")); names.add(target(page)) }
            val tree = COSDictionary().apply { setItem(COSName.NAMES, names) }
            document.documentCatalog.cosObject.setItem(COSName.NAMES, COSDictionary().apply { setItem(COSName.DESTS, tree) })
            val annotations = COSArray()
            listOf(target(pages[0]), COSString("p1"), COSString("p3")).forEach { destination ->
                annotations.add(
                    COSDictionary().apply {
                        setItem(COSName.TYPE, COSName.ANNOT)
                        setItem(COSName.SUBTYPE, COSName.getPDFName("Link"))
                        setItem(COSName.RECT, PDRectangle(10f, 10f).cosArray)
                        setItem(COSName.DEST, destination)
                    },
                )
            }
            pages[1].cosObject.setItem(COSName.ANNOTS, annotations)
            document.documentCatalog.cosObject.setItem(COSName.OPEN_ACTION, target(pages[0]))
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        save(bytes, PdfContract.KEY_DELETE_PAGES to "1").use { document ->
            assertEquals(2, document.numberOfPages)
            val dests = document.documentCatalog.names.dests
            assertNull(dests.getValue("p1"))
            assertEquals(0, document.pages.indexOf(dests.getValue("p2").page))
            assertEquals(1, document.pages.indexOf(dests.getValue("p3").page))
            val links = document.getPage(0).cosObject.getDictionaryObject(COSName.ANNOTS) as COSArray
            assertEquals(3, links.size())
            assertFalse((links.getObject(0) as COSDictionary).containsKey(COSName.DEST))
            assertFalse((links.getObject(1) as COSDictionary).containsKey(COSName.DEST))
            assertTrue((links.getObject(2) as COSDictionary).containsKey(COSName.DEST))
            assertFalse(document.documentCatalog.cosObject.containsKey(COSName.OPEN_ACTION))
        }
    }

    @Test
    fun `merge appends pages and wraps every outline under one entry`() {
        PDDocument().use { destination ->
            val merger = PdfMerger(destination)
            val sources = listOf(PdfFixtures.multiPageWithOutline(), PdfFixtures.rotatedPages()).map { PDDocument.load(it) }
            try {
                sources.forEach { merger.append(it) }
                assertEquals(2, merger.count)
                written(destination).use { merged ->
                    assertEquals(11, merged.numberOfPages)
                    assertEquals(listOf(0, 0, 0, 0, 0, 0) + PdfFixtures.ROTATIONS, merged.rotations())
                    val outline = checkNotNull(merged.documentCatalog.documentOutline)
                    assertEquals(listOf(PdfFixtures.TITLE, "Document 2"), outline.titles())
                    val first = outline.firstChild
                    assertFalse(first.isNodeOpen)
                    assertEquals(PdfFixtures.CHAPTERS + PdfFixtures.OUTLINE_WITHOUT_PAGE, first.titles())
                    assertEquals(0, merged.pages.indexOf(first.findDestinationPage(merged)))
                    val second = outline.lastChild
                    assertEquals(6, merged.pages.indexOf(second.findDestinationPage(merged)))
                    assertFalse(second.hasChildren())
                    assertEquals(ThreePaperPdfContract.PRODUCER, merged.documentInformation.producer)
                }
            } finally {
                sources.forEach { it.close() }
            }
        }
    }

    @Test
    fun `merge titles the outline entries with the display names, falling back for blank ones`() {
        PDDocument().use { destination ->
            val merger = PdfMerger(destination)
            val sources = listOf(PdfFixtures.multiPageWithOutline(), PdfFixtures.multiPageWithOutline(), PdfFixtures.rotatedPages()).map { PDDocument.load(it) }
            try {
                merger.append(sources[0], "chapter-files 1.2")
                merger.append(sources[1], " ")
                merger.append(sources[2], null)
                written(destination).use { merged ->
                    val outline = checkNotNull(merged.documentCatalog.documentOutline)
                    assertEquals(listOf("chapter-files 1.2", PdfFixtures.TITLE, "Document 3"), outline.titles())
                    assertEquals(PdfFixtures.CHAPTERS + PdfFixtures.OUTLINE_WITHOUT_PAGE, outline.firstChild.titles())
                }
            } finally {
                sources.forEach { it.close() }
            }
        }
    }

    @Test
    fun `merge with outline none drops every outline`() {
        PDDocument().use { destination ->
            val merger = PdfMerger(destination, PdfContract.OUTLINE_NONE)
            val sources = List(2) { PDDocument.load(PdfFixtures.multiPageWithOutline()) }
            try {
                sources.forEach { merger.append(it, "ignored") }
                written(destination).use { merged ->
                    assertEquals(12, merged.numberOfPages)
                    assertNull(merged.documentCatalog.documentOutline?.firstChild)
                }
            } finally {
                sources.forEach { it.close() }
            }
        }
    }

    @Test
    fun `encrypted inputs need the owner password to be written`() {
        val bytes = PdfFixtures.encrypted()
        val denied = assertThrows(PdfFailure::class.java) { save(bytes, password = PdfFixtures.USER_PASSWORD) }
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, denied.code)
        PDDocument.load(bytes, PdfFixtures.USER_PASSWORD).use { source ->
            PDDocument().use { destination ->
                val failure = assertThrows(PdfFailure::class.java) { PdfMerger(destination).append(source) }
                assertEquals(PdfErrorCodes.PERMISSION_DENIED, failure.code)
            }
        }
        save(bytes, PdfContract.KEY_PAGES to "2", password = PdfFixtures.OWNER_PASSWORD).use { document ->
            assertFalse(document.isEncrypted)
            assertEquals(1, document.numberOfPages)
            assertEquals(PdfFixtures.TITLE, document.documentInformation.title)
        }
    }

    @Test
    fun `a cancelled write stops with the stop reason`() {
        PDDocument.load(PdfFixtures.multiPageWithOutline()).use { document ->
            val failure = assertThrows(PdfFailure::class.java) {
                PdfWriting.write(document, ByteArrayOutputStream(), StopSignal { StopSignal.cancelled() })
            }
            assertEquals(PdfErrorCodes.CANCELLED, failure.code)
        }
    }

}

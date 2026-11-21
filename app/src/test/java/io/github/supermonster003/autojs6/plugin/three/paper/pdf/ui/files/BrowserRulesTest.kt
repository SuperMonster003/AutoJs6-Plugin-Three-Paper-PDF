package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentEntry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The rules of the file browser for the recent files and the remembered folders (roadmap P6.15 batch 17d, appendix D
 * Q324 (a)): which modes list the recent files and which entries, how many show before "Show all", one check state per
 * file in the order checked, the picks of a confirmation, the folder remembered and where the browser starts, with the
 * fallbacks for deleted folders and folders outside the volumes.
 * zh-CN: 文件浏览器关于最近文件与记住文件夹的规则 (路线图 P6.15 批次 17d, 附录 D Q324 (a)): 哪些模式列出最近文件以及哪些条目, "全部" 之前显示
 * 几条, 每个文件一个勾选状态且按勾选顺序, 确认时交回的内容, 记住的文件夹与起始文件夹, 以及文件夹已删除或不在存储卷内时的回落.
 */
class BrowserRulesTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `only the PDF modes list the recent files`() {
        assertTrue(BrowserRules.listsRecent(BrowseMode.PDF))
        assertTrue(BrowserRules.listsRecent(BrowseMode.PDFS))
        assertFalse(BrowserRules.listsRecent(BrowseMode.IMAGES))
        assertFalse(BrowserRules.listsRecent(BrowseMode.FOLDER))
        val list = listOf(LOCAL_A, DOCUMENT)
        assertEquals(emptyList<RecentEntry>(), BrowserRules.recent(list, BrowseMode.IMAGES) { true })
        assertEquals(emptyList<RecentEntry>(), BrowserRules.recent(list, BrowseMode.FOLDER) { true })
    }

    @Test
    fun `the list keeps its order and leaves out local files that cannot be read now`() {
        val list = listOf(DOCUMENT, LOCAL_A, LOCAL_B)
        assertEquals(list, BrowserRules.recent(list, BrowseMode.PDFS) { true })
        assertEquals(
            "without storage access only the documents and the readable files stay",
            listOf(DOCUMENT, LOCAL_B),
            BrowserRules.recent(list, BrowseMode.PDF) { it.name == "b.pdf" },
        )
        assertEquals(listOf(DOCUMENT), BrowserRules.recent(list, BrowseMode.PDF) { false })
    }

    @Test
    fun `at most three rows and half the height show before the group is expanded`() {
        // Phone portrait: 560 high, header 56, rows 72: (280 - 56) / 72 = 3.1.
        assertEquals(3, BrowserRules.compactCount(total = 20, viewport = 560f, header = 56f, row = 72f))
        assertEquals("never more than the list", 2, BrowserRules.compactCount(total = 2, viewport = 560f, header = 56f, row = 72f))
        assertEquals("a tall tablet still shows three", BrowserRules.COMPACT_MAX, BrowserRules.compactCount(20, 1200f, 56f, 72f))
        // Phone landscape: 300 high: (150 - 56) / 72 = 1.3.
        assertEquals(1, BrowserRules.compactCount(20, 300f, 56f, 72f))
        assertEquals("at least one row even when nothing fits", 1, BrowserRules.compactCount(20, 80f, 56f, 72f))
        assertEquals(0, BrowserRules.compactCount(0, 560f, 56f, 72f))
    }

    @Test
    fun `checking keeps the order of checking and one state per file`() {
        val local = BrowserRules.keyOf(LOCAL_A)
        assertEquals("a recent file and the same file of the folder share the key", File(LOCAL_A.path).absolutePath, local)
        assertEquals(DOCUMENT.uri, BrowserRules.keyOf(DOCUMENT))
        var checked = BrowserRules.toggle(emptyList(), "/x/b.pdf")
        checked = BrowserRules.toggle(checked, local)
        checked = BrowserRules.toggle(checked, DOCUMENT.uri)
        assertEquals(listOf("/x/b.pdf", local, DOCUMENT.uri), checked)
        assertEquals("checking the same file from the other list unchecks it", listOf("/x/b.pdf", DOCUMENT.uri), BrowserRules.toggle(checked, local))
        assertEquals("checked again it goes last", listOf("/x/b.pdf", DOCUMENT.uri, local), BrowserRules.toggle(BrowserRules.toggle(checked, local), local))
    }

    @Test
    fun `the picks follow the order checked and drop documents no longer listed`() {
        val checked = listOf(DOCUMENT.uri, LOCAL_B.path, GONE.uri, LOCAL_A.path)
        val picks = BrowserRules.picks(checked, listOf(LOCAL_A, DOCUMENT, LOCAL_B))
        assertEquals(
            listOf(BrowserPick.Document(DOCUMENT), BrowserPick.Local(File(LOCAL_B.path)), BrowserPick.Local(File(LOCAL_A.path))),
            picks,
        )
        assertTrue(BrowserRules.isDocumentKey(DOCUMENT.uri))
        assertFalse(BrowserRules.isDocumentKey(LOCAL_A.path))
    }

    @Test
    fun `PDFs share one memory, images have their own and folders none`() {
        assertEquals(ToolboxPreferences.KEY_PDF_FOLDER, BrowserRules.memoryOf(BrowseMode.PDF))
        assertEquals(ToolboxPreferences.KEY_PDF_FOLDER, BrowserRules.memoryOf(BrowseMode.PDFS))
        assertEquals(ToolboxPreferences.KEY_IMAGE_FOLDER, BrowserRules.memoryOf(BrowseMode.IMAGES))
        assertNull(BrowserRules.memoryOf(BrowseMode.FOLDER))
        assertTrue(ToolboxPreferences.KEY_PDF_FOLDER != ToolboxPreferences.KEY_IMAGE_FOLDER)
    }

    @Test
    fun `the folder of the last local pick is remembered, documents keep the memory`() {
        val a = File(temporary.root, "a/one.pdf")
        val b = File(temporary.root, "b/two.pdf")
        assertEquals(b.parentFile, BrowserRules.folderToRemember(listOf(BrowserPick.Local(a), BrowserPick.Document(DOCUMENT), BrowserPick.Local(b))))
        assertEquals(a.parentFile, BrowserRules.folderToRemember(listOf(BrowserPick.Local(a), BrowserPick.Document(DOCUMENT))))
        assertNull(BrowserRules.folderToRemember(listOf(BrowserPick.Document(DOCUMENT))))
        assertNull(BrowserRules.folderToRemember(emptyList()))
    }

    @Test
    fun `the tool's last input wins over the remembered folder`() {
        val volume = temporary.newFolder("volume")
        val input = File(volume, "inputs").apply { mkdirs() }
        val remembered = File(volume, "remembered").apply { mkdirs() }
        assertEquals(input.absolutePath, BrowserRules.startFolder(listOf(input, remembered), listOf(volume)))
        assertEquals("a file starts in its folder", input.absolutePath, BrowserRules.startFolder(listOf(File(input, "x.pdf"), remembered), listOf(volume)))
        assertEquals("no input yet", remembered.absolutePath, BrowserRules.startFolder(listOf(null, remembered), listOf(volume)))
    }

    @Test
    fun `a deleted folder starts from its nearest existing parent`() {
        val volume = temporary.newFolder("volume")
        val parent = File(volume, "work").apply { mkdirs() }
        val deleted = File(parent, "gone/deeper")
        assertEquals(parent.absolutePath, BrowserRules.startFolder(listOf(null, deleted), listOf(volume)))
        parent.deleteRecursively()
        assertEquals("up to the volume", volume.absolutePath, BrowserRules.startFolder(listOf(null, deleted), listOf(volume)))
    }

    @Test
    fun `a folder outside the volumes falls back to the volume or the volume list`() {
        val volume = temporary.newFolder("volume")
        val other = temporary.newFolder("volume2")
        val outside = temporary.newFolder("private")
        assertEquals("the next candidate", File(volume, "x").apply { mkdirs() }.absolutePath, BrowserRules.startFolder(listOf(outside, File(volume, "x")), listOf(volume)))
        assertEquals("the only volume", volume.absolutePath, BrowserRules.startFolder(listOf(outside, null), listOf(volume)))
        assertNull("several volumes: the list of volumes", BrowserRules.startFolder(listOf(outside), listOf(volume, other)))
        assertNull(BrowserRules.startFolder(emptyList(), listOf(volume, other)))
        assertFalse("a sibling with the same prefix is not inside", BrowserRules.inside(File(temporary.root, "volume2"), volume))
        assertTrue(BrowserRules.inside(volume, volume))
    }

    private companion object {
        val LOCAL_A = RecentEntry.Local("/storage/emulated/0/Documents/a.pdf")
        val LOCAL_B = RecentEntry.Local("/storage/emulated/0/Download/b.pdf")
        val DOCUMENT = RecentEntry.Document("content://com.example.documents/document/7", "picked.pdf")
        val GONE = RecentEntry.Document("content://com.example.documents/document/9", "gone.pdf")
    }

}

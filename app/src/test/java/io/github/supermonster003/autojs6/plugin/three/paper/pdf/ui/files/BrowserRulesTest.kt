package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules of the file browser for the recent files (roadmap P6.15 batch 17d, appendix D Q324 (a)): which modes list
 * the recent files and which entries, how many show before "Show all", one check state per file in the order checked and
 * the picks of a confirmation.
 * zh-CN: 文件浏览器关于最近文件的规则 (路线图 P6.15 批次 17d, 附录 D Q324 (a)): 哪些模式列出最近文件以及哪些条目, "全部" 之前显示几条,
 * 每个文件一个勾选状态且按勾选顺序, 以及确认时交回的内容.
 */
class BrowserRulesTest {

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

    private companion object {
        val LOCAL_A = RecentEntry.Local("/storage/emulated/0/Documents/a.pdf")
        val LOCAL_B = RecentEntry.Local("/storage/emulated/0/Download/b.pdf")
        val DOCUMENT = RecentEntry.Document("content://com.example.documents/document/7", "picked.pdf")
        val GONE = RecentEntry.Document("content://com.example.documents/document/9", "gone.pdf")
    }

}

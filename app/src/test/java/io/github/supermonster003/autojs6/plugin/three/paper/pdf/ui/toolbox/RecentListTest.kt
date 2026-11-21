package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list rules of the recent files (roadmap P4.1, appendix D Q106 = B): the stored JSON with both kinds of entry and
 * the paths of the first version, newest first without duplicates, the limit, and the persisted grants to release.
 * zh-CN: 最近文件的列表规则 (路线图 P4.1, 附录 D Q106 = B): 含两种条目的存储 JSON 与第一版的路径, 最新的在前且不重复, 条数上限, 以及应当
 * 释放的持久授权.
 */
class RecentListTest {

    @Test
    fun `both kinds of entry survive a round trip in order`() {
        val list = listOf(LOCAL_A, DOCUMENT_A, LOCAL_B, DOCUMENT_B)
        assertEquals(list, RecentList.parse(RecentList.serialize(list), null))
    }

    @Test
    fun `the paths of the first version read as local entries until entries exist`() {
        val legacy = """["/sdcard/a.pdf","/sdcard/b.pdf","/sdcard/a.pdf",""]"""
        assertEquals(listOf(LOCAL_A, LOCAL_B), RecentList.parse(null, legacy))
        assertEquals("entries win over the legacy paths", listOf(DOCUMENT_A), RecentList.parse(RecentList.serialize(listOf(DOCUMENT_A)), legacy))
        assertEquals(emptyList<RecentEntry>(), RecentList.parse(null, null))
    }

    @Test
    fun `broken or unknown data reads as nothing`() {
        assertEquals(emptyList<RecentEntry>(), RecentList.parse("{not json", null))
        assertEquals(emptyList<RecentEntry>(), RecentList.parse(null, "[broken"))
        assertEquals(listOf(LOCAL_A), RecentList.parse("""[{"other":1},"text",{"path":"/sdcard/a.pdf"}]""", null))
    }

    @Test
    fun `a document stored without a name is named after its uri`() {
        val parsed = RecentList.parse("""[{"uri":"${DOCUMENT_A.uri}"}]""", null)
        assertEquals(listOf(RecentEntry.Document(DOCUMENT_A.uri, "Report 2026+.pdf")), parsed)
        assertEquals("plain.pdf", RecentList.fallbackName("content://provider/document/plain.pdf"))
        assertEquals("trailing slash", "x.pdf", RecentList.fallbackName("content://provider/tree/x.pdf/"))
        assertEquals("a bad escape keeps the raw segment", "50%", RecentList.fallbackName("content://provider/document/50%"))
    }

    @Test
    fun `adding moves an entry first, drops its duplicate and keeps at most the limit`() {
        val list = listOf(LOCAL_A, DOCUMENT_A, LOCAL_B)
        assertEquals(listOf(DOCUMENT_A, LOCAL_A, LOCAL_B), RecentList.add(list, DOCUMENT_A, 20))
        val renamed = DOCUMENT_A.copy(name = "renamed.pdf")
        assertEquals("the same uri replaces the old name", listOf(renamed, LOCAL_A, LOCAL_B), RecentList.add(list, renamed, 20))
        assertEquals("the oldest falls out", listOf(DOCUMENT_B, LOCAL_A, DOCUMENT_A), RecentList.add(list, DOCUMENT_B, 3))

        val full = (1..RecentFiles.MAX).map { RecentEntry.Document("content://provider/document/$it", "$it.pdf") }
        val next = RecentList.add(full, DOCUMENT_A, RecentFiles.MAX)
        assertEquals(RecentFiles.MAX, next.size)
        assertEquals("the evicted document's grant is released", listOf(full.last().uri), RecentList.unheld(full.map { it.uri }, next))
    }

    @Test
    fun `removing drops one entry by key`() {
        val list = listOf(LOCAL_A, DOCUMENT_A, LOCAL_B)
        assertEquals(listOf(LOCAL_A, LOCAL_B), RecentList.remove(list, DOCUMENT_A.key))
        assertEquals(listOf(DOCUMENT_A, LOCAL_B), RecentList.remove(list, LOCAL_A.key))
        assertEquals(list, RecentList.remove(list, "missing"))
    }

    @Test
    fun `only grants that no document holds are released`() {
        val persisted = listOf(DOCUMENT_A.uri, DOCUMENT_B.uri, "content://other/document/stray")
        assertEquals(listOf(DOCUMENT_B.uri, "content://other/document/stray"), RecentList.unheld(persisted, listOf(LOCAL_A, DOCUMENT_A)))
        assertEquals("clearing releases everything", persisted, RecentList.unheld(persisted, emptyList()))
        assertEquals(emptyList<String>(), RecentList.unheld(emptyList(), listOf(DOCUMENT_A)))
    }

    @Test
    fun `local and document keys never collide`() {
        val local = RecentEntry.Local("/sdcard/a.pdf")
        val document = RecentEntry.Document("/sdcard/a.pdf", "a.pdf")
        assertNotEquals(local.key, document.key)
        assertEquals("a.pdf", local.name)
        assertEquals(2, RecentList.add(listOf(local), document, 20).size)
    }

    @Test
    fun `the plugin holds far fewer grants than the system allows`() {
        // 128 before Android 11, 512 from Android 11. zh-CN: Android 11 之前 128, 之后 512.
        assertTrue(RecentFiles.MAX <= 128)
    }

    @Test
    fun `inputs chosen in order end up newest first, a reused file moved up without a duplicate`() {
        // Roadmap P6.15 batch 17d (Q324 (a)): the inputs of a tool are added one by one in the order chosen.
        // zh-CN: 路线图 P6.15 批次 17d (Q324 (a)): 工具的输入按选择的顺序逐个记入.
        var list = listOf<RecentEntry>(LOCAL_B, DOCUMENT_B)
        listOf(LOCAL_A, DOCUMENT_A, LOCAL_B).forEach { list = RecentList.add(list, it, RecentFiles.MAX) }
        assertEquals(listOf(LOCAL_B, DOCUMENT_A, LOCAL_A, DOCUMENT_B), list)
    }

    private companion object {
        val LOCAL_A = RecentEntry.Local("/sdcard/a.pdf")
        val LOCAL_B = RecentEntry.Local("/sdcard/b.pdf")
        val DOCUMENT_A = RecentEntry.Document("content://com.android.externalstorage.documents/document/primary%3ADownload%2FReport%202026%2B.pdf", "Report 2026+.pdf")
        val DOCUMENT_B = RecentEntry.Document("content://com.android.providers.downloads.documents/document/msf%3A42", "b.pdf")
    }

}

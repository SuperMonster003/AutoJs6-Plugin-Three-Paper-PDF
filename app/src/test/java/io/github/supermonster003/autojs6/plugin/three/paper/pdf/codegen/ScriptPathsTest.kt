package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptFixtures.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The paths of generated scripts (roadmap P6.13): `file:` URIs, the external storage provider (shared storage, `home:`,
 * removable volumes, tree URIs), the downloads provider's `raw:` documents, broken escapes and `..` refused, and the
 * folder and guess fallbacks.
 * zh-CN: 生成脚本的路径 (路线图 P6.13): `file:` URI, 外部存储提供方 (共享存储, `home:`, 可移动卷, 树 URI), 下载提供方的 `raw:` 文档,
 * 拒绝不完整的转义与 `..`, 以及文件夹与推测的退路.
 */
class ScriptPathsTest {

    private val storage = "content://com.android.externalstorage.documents"

    @Test
    fun fileUris() {
        assertEquals("/storage/emulated/0/a b.pdf", ScriptPaths.pathOf("file:///storage/emulated/0/a%20b.pdf"))
        assertEquals("/sdcard/x.pdf", ScriptPaths.pathOf("file:/sdcard/x.pdf"))
        assertEquals("/x/y.pdf", ScriptPaths.pathOf("FILE:///x/y.pdf?query#fragment"))
        assertEquals("/path/a.pdf", ScriptPaths.pathOf("file://host/path/a.pdf"))
        assertEquals("/a/b.pdf", ScriptPaths.pathOf("file:///a//b.pdf"))
        assertEquals("/a+b.pdf", ScriptPaths.pathOf("file:///a+b.pdf"))
        assertEquals("/" + text(0x62A5, 0x544A) + ".pdf", ScriptPaths.pathOf("file:///%E6%8A%A5%E5%91%8A.pdf"))
        assertNull(ScriptPaths.pathOf("file:///a/../etc/passwd"))
        assertNull(ScriptPaths.pathOf("file:///a/./b"))
        assertNull(ScriptPaths.pathOf("file:///a%0Ab.pdf"))
        assertNull(ScriptPaths.pathOf("file:///a%2"))
        assertNull(ScriptPaths.pathOf("file:///a%zzb"))
        assertNull(ScriptPaths.pathOf("file:relative.pdf"))
        assertNull(ScriptPaths.pathOf("file:///"))
    }

    @Test
    fun externalStorageDocuments() {
        assertEquals("/storage/emulated/0/Documents/a.pdf", ScriptPaths.pathOf("$storage/document/primary%3ADocuments%2Fa.pdf"))
        assertEquals("/storage/emulated/0/Docs/b.pdf", ScriptPaths.pathOf("$storage/tree/primary%3ADocs/document/primary%3ADocs%2Fb.pdf"))
        assertEquals("/storage/emulated/0/Docs", ScriptPaths.pathOf("$storage/tree/primary%3ADocs"))
        assertEquals("/storage/emulated/0", ScriptPaths.pathOf("$storage/tree/primary%3A"))
        assertEquals("/storage/emulated/0/Documents/x.pdf", ScriptPaths.pathOf("$storage/document/home%3Ax.pdf"))
        assertEquals("/storage/1A2B-3C4D/Music/a.pdf", ScriptPaths.pathOf("$storage/document/1a2b-3c4d%3AMusic%2Fa.pdf"))
        assertEquals("/storage/emulated/0/a+b c.pdf", ScriptPaths.pathOf("$storage/document/primary%3Aa+b%20c.pdf"))
        assertEquals("/storage/emulated/0/Docs/a.pdf", ScriptPaths.pathOf("$storage/document/primary%3ADocs%2F%2Fa.pdf?x=1"))
        assertEquals(
            "/storage/emulated/0/" + text(0x62A5, 0x544A) + " " + text(0x1F4C4) + ".pdf",
            ScriptPaths.pathOf("$storage/document/primary%3A%E6%8A%A5%E5%91%8A%20%F0%9F%93%84.pdf"),
        )
        assertNull(ScriptPaths.pathOf("$storage/document/secondary%3Ax.pdf"))
        assertNull(ScriptPaths.pathOf("$storage/document/primary%3ADocs%2F..%2F..%2Fdata"))
        assertNull(ScriptPaths.pathOf("$storage/document/primary%3A%2"))
        assertNull(ScriptPaths.pathOf("$storage/document/primary%3Aa%0Ab.pdf"))
        assertNull(ScriptPaths.pathOf("$storage/document"))
        assertNull(ScriptPaths.pathOf("$storage/root/primary"))
    }

    @Test
    fun downloadsAndOtherProviders() {
        val downloads = "content://com.android.providers.downloads.documents/document/"
        assertEquals("/storage/emulated/0/Download/a.pdf", ScriptPaths.pathOf(downloads + "raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fa.pdf"))
        assertNull(ScriptPaths.pathOf(downloads + "msf%3A1234"))
        assertNull(ScriptPaths.pathOf(downloads + "1234"))
        assertNull(ScriptPaths.pathOf(downloads + "raw%3Arelative.pdf"))
        assertNull(ScriptPaths.pathOf("content://media/external/file/12"))
        assertNull(ScriptPaths.pathOf("content://com.example.provider/document/primary%3Aa.pdf"))
        assertNull(ScriptPaths.pathOf("https://example.com/a.pdf"))
        assertNull(ScriptPaths.pathOf("/storage/emulated/0/a.pdf"))
        assertNull(ScriptPaths.pathOf(""))
    }

    @Test
    fun selectedFiles() {
        assertEquals(ScriptFile("/storage/emulated/0/Documents/a.pdf"), ScriptPaths.fileOf("$storage/document/primary%3ADocuments%2Fa.pdf", "ignored.pdf", "/elsewhere"))
        assertEquals(ScriptFile("/storage/emulated/0/Books/b.pdf"), ScriptPaths.fileOf("content://media/external/file/12", "b.pdf", " /storage/emulated/0/Books/ "))
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/b.pdf", guessed = true), ScriptPaths.fileOf("content://media/external/file/12", "dir/b.pdf", "Books"))
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/document.pdf", guessed = true), ScriptPaths.fileOf("content://x/y", " ", null))
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/document.pdf", guessed = true), ScriptPaths.fileOf("content://x/y", "..", "/a/../b"))
    }

    @Test
    fun results() {
        assertEquals(ScriptFile("/storage/emulated/0/Documents/3-Paper PDF/out.pdf"), ScriptPaths.outputOf(" /storage/emulated/0/Documents/3-Paper PDF/out.pdf ", "/x", "out.pdf"))
        assertEquals(ScriptFile("/x/out.pdf"), ScriptPaths.outputOf("out.pdf", "/x/", "out.pdf"))
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/out.pdf", guessed = true), ScriptPaths.outputOf(null, null, "out.pdf"))
        assertEquals(ScriptFile("${ScriptPaths.GUESS_FOLDER}/out.pdf", guessed = true), ScriptPaths.outputOf("/a/../b", "relative", "out.pdf"))
    }

}

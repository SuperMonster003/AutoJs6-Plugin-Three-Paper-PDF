package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the file manager tools (roadmap D36, P3.3, appendix D Q75): ids the host accepts, labels present in
 * every language, where each entry appears and what it may do. The Bundle of the catalog is checked on a device
 * (the stubbed android.jar returns empty Bundles here).
 * zh-CN: 文件管理器工具的形态 (路线图 D36, P3.3, 附录 D Q75): 宿主接受的 id, 各语言都有的标签, 每个入口出现在何处以及允许做什么.
 * 目录的 Bundle 在设备上检查 (此处的桩 android.jar 返回空 Bundle).
 */
class ExplorerToolTest {

    @Test
    fun `seven tools with stable ids the host accepts`() {
        assertEquals(
            listOf("open", "merge", "split", "to-images", "extract-text", "encrypt", "decrypt"),
            ExplorerTool.entries.map { it.id },
        )
        assertTrue(ExplorerTool.entries.size <= ExplorerActionProtocol.MAX_ACTIONS_PER_PLUGIN)
        ExplorerTool.entries.forEach { tool ->
            assertTrue(tool.id, ID_PATTERN.matches(tool.id))
            assertSame(tool, ExplorerTool.of(tool.id))
        }
        assertNull(ExplorerTool.of("unknown"))
        assertNull(ExplorerTool.of(null))
    }

    @Test
    fun `opening is the primary read only action and the viewer subclass handles it`() {
        val open = ExplorerTool.OPEN
        assertEquals(ExplorerActionValues.PLACEMENT_PRIMARY, open.placement)
        assertEquals(ExplorerActionValues.CARDINALITY_SINGLE, open.cardinality)
        assertEquals(ExplorerActionValues.ACCESS_READ_ONLY, open.accessMode)
        assertEquals(ExplorerPdfViewerActivity::class.java.name, open.activityClass.name)
    }

    @Test
    fun `merging takes several files from the selection toolbar and the other tools one file from the overflow menu`() {
        val merge = ExplorerTool.MERGE
        assertEquals(ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR, merge.placement)
        assertEquals(ExplorerActionValues.CARDINALITY_MULTIPLE, merge.cardinality)
        val others = ExplorerTool.entries - ExplorerTool.OPEN - merge
        others.forEach { tool ->
            assertEquals(tool.id, ExplorerActionValues.PLACEMENT_OVERFLOW, tool.placement)
            assertEquals(tool.id, ExplorerActionValues.CARDINALITY_SINGLE, tool.cardinality)
        }
        (others + merge).forEach { tool ->
            // Results go next to the sources through the host session. zh-CN: 结果经宿主会话写到源文件旁边.
            assertEquals(tool.id, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, tool.accessMode)
            assertEquals(tool.id, PdfToolActivity::class.java.name, tool.activityClass.name)
        }
        assertEquals("the overflow menu keeps a stable order", others.sortedByDescending { it.priority }, others)
    }

    @Test
    fun `every label exists in every language and the fallback is the English text`() {
        val english = RepositoryFiles.strings("values")
        ExplorerTool.entries.forEach { tool ->
            assertEquals(tool.id, english[tool.labelResourceName], tool.labelFallback)
        }
        LANGUAGE_DIRECTORIES.forEach { directory ->
            val strings = RepositoryFiles.strings(directory)
            ExplorerTool.entries.forEach { tool ->
                assertTrue("$directory ${tool.labelResourceName}", !strings[tool.labelResourceName].isNullOrBlank())
            }
        }
    }

    private companion object {

        /** The id rule of the Explorer Action catalog. zh-CN: Explorer Action 目录的 id 规则. */
        val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]*")

        val LANGUAGE_DIRECTORIES = listOf(
            "values", "values-en", "values-zh", "values-zh-rHK", "values-zh-rTW", "values-ja", "values-ko", "values-fr",
            "values-es", "values-ru", "values-ar",
        )

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a tool screen keeps across the death of its process (E4 R3, appendix D Q326 (a)): everything comes back from the
 * encoded text, warnings with their codes and typed arguments, every kind of summary; text of another version or no
 * state is none; a state too large drops the inputs first, then the warnings.
 * zh-CN: 工具界面在进程死亡后保留的内容 (E4 R3, 附录 D Q326 (a)): 全部内容可由编码后的文字恢复, 警告带代码与有类型的参数, 每种摘要都可恢复;
 * 其他版本的文字或没有状态时为无; 状态过大时先舍弃输入, 再舍弃警告.
 */
class ToolScreenStateTest {

    private fun roundTrip(state: ToolScreenState): ToolScreenState = ToolScreenState.decode(state.encode()) ?: throw AssertionError("the encoded state does not decode")

    private fun assertSameResult(expected: ToolResult?, actual: ToolResult?) {
        if (expected == null) {
            assertNull(actual)
            return
        }
        assertNotNull(actual)
        assertEquals(expected.displayPath, actual!!.displayPath)
        assertEquals(expected.files, actual.files)
        assertEquals(expected.warnings, actual.warnings)
        assertEquals(expected.summary, actual.summary)
    }

    @Test
    fun everythingKeptComesBack() {
        val warnings = listOf(
            ToolWarning("2 images could not be recompressed", "IMAGES_SKIPPED", mapOf("count" to 2L, "reason" to "unsupported")),
            ToolWarning("plain text only"),
            ToolWarning("big number", "SOME_CODE", mapOf("bytes" to 5_000_000_000L)),
        )
        val result = ToolResult("/storage/emulated/0/Documents/3-Paper PDF/encrypted_compressed (1).pdf", 1, warnings, ToolSummary.Compressed(1_234_567L, 345_678L, 3, 4))
        val state = ToolScreenState(
            kind = ToolKind.COMPRESS.name,
            inputs = listOf(
                ToolScreenState.SavedInput("file:///storage/emulated/0/Download/3-paper-e4/encrypted.pdf", "encrypted.pdf"),
                ToolScreenState.SavedInput("content://com.android.providers.downloads.documents/document/msf%3A42", "名称 \"带引号\".pdf"),
            ),
            outputFolder = "/storage/emulated/0/Documents/3-Paper PDF",
            task = ToolScreenState.SavedTask(4321, 7),
            result = result,
        )
        val back = roundTrip(state)
        assertEquals(state.kind, back.kind)
        assertEquals(state.inputs.map { it.uri to it.displayName }, back.inputs.map { it.uri to it.displayName })
        assertEquals(state.outputFolder, back.outputFolder)
        assertEquals(4321, back.task?.process)
        assertEquals(7, back.task?.id)
        assertSameResult(result, back.result)
    }

    @Test
    fun everySummaryComesBack() {
        listOf(
            ToolSummary.Filled(5, true),
            ToolSummary.Filled(0, false),
            ToolSummary.PropertiesSaved(3, false),
            ToolSummary.PropertiesSaved(0, true),
            null,
        ).forEach { summary ->
            val result = ToolResult("/out/a.pdf", 2, emptyList(), summary)
            assertSameResult(result, roundTrip(ToolScreenState(ToolKind.FILL_FORM.name, result = result)).result)
        }
    }

    @Test
    fun aStateWithoutTaskOrResultIsOnlyTheTool() {
        val back = roundTrip(ToolScreenState(ToolKind.MERGE.name))
        assertEquals(ToolKind.MERGE.name, back.kind)
        assertTrue(back.inputs.isEmpty())
        assertNull(back.outputFolder)
        assertNull(back.task)
        assertNull(back.result)
    }

    @Test
    fun noStateOtherVersionsAndOtherTextAreNone() {
        assertNull(ToolScreenState.decode(null))
        assertNull(ToolScreenState.decode(""))
        assertNull(ToolScreenState.decode("not json"))
        assertNull(ToolScreenState.decode("{\"version\":2,\"kind\":\"MERGE\"}"))
        assertNull(ToolScreenState.decode("{\"kind\":\"MERGE\"}"))
        assertNull(ToolScreenState.decode("{\"version\":1}"))
    }

    @Test
    fun aLargeStateDropsTheInputsFirstThenTheWarnings() {
        val inputs = (1..1000).map { ToolScreenState.SavedInput("content://provider/document/${"x".repeat(80)}$it", "file $it.pdf") }
        val few = listOf(ToolWarning("one", "CODE", mapOf("count" to 1L)))
        val withInputs = ToolScreenState(ToolKind.MERGE.name, inputs, "/out", null, ToolResult("/out/merged.pdf", 1, few))
        val encoded = withInputs.encode()
        assertTrue(encoded.length <= ToolScreenState.MAX_CHARS)
        val back = ToolScreenState.decode(encoded)!!
        assertTrue(back.inputs.isEmpty())
        assertEquals("/out", back.outputFolder)
        assertEquals(few, back.result?.warnings)

        val many = (1..2000).map { ToolWarning("warning number $it with a long text ${"y".repeat(40)}", "CODE", mapOf("page" to it.toLong())) }
        val withWarnings = ToolScreenState(ToolKind.MERGE.name, emptyList(), null, null, ToolResult("/out/merged.pdf", 1, many, ToolSummary.Filled(1, false)))
        val trimmed = withWarnings.encode()
        assertTrue(trimmed.length <= ToolScreenState.MAX_CHARS)
        val kept = ToolScreenState.decode(trimmed)!!.result!!
        assertEquals("/out/merged.pdf", kept.displayPath)
        assertTrue(kept.warnings.isEmpty())
        assertEquals(ToolSummary.Filled(1, false), kept.summary)
    }

    @Test
    fun aSmallStateKeepsEverything() {
        val inputs = (1..20).map { ToolScreenState.SavedInput("file:///sdcard/$it.pdf", "$it.pdf") }
        val back = roundTrip(ToolScreenState(ToolKind.MERGE.name, inputs))
        assertEquals(20, back.inputs.size)
        assertEquals("file:///sdcard/20.pdf", back.inputs.last().uri)
    }

}

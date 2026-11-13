package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page plans of `save`, `planSplit` and `split` (protocol "Methods" and "Saving", roadmap P2.4): range parsing, the
 * order of the `save` steps and the refusals before an operation id exists.
 * zh-CN: `save`, `planSplit` 与 `split` 的页面计划 (协议 "Methods" 与 "Saving" 两节, 路线图 P2.4): 范围解析, `save` 各步骤的顺序,
 * 以及产生操作 id 之前的拒绝.
 */
class PageEditPlanTest {

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(PdfFailure::class.java) { block() }.code)
    }

    private fun List<PagePlacement>.pages() = map { it.page }

    @Test
    fun `save without keys keeps every page as it is`() {
        val plan = PageEditPlan.save(options(), 4)
        assertEquals(listOf(1, 2, 3, 4), plan.pages())
        assertTrue(plan.all { it.degrees == 0 })
        assertTrue(PageEditPlan.isIdentity(plan, 4))
    }

    @Test
    fun `save extracts and deletes pages`() {
        assertEquals(listOf(2, 3, 5), PageEditPlan.save(options(PdfContract.KEY_PAGES to "2-3,5"), 6).pages())
        assertEquals(listOf(1, 4, 5, 6), PageEditPlan.save(options(PdfContract.KEY_DELETE_PAGES to "2-3"), 6).pages())
        // deletePages first, then pages of the rest. zh-CN: 先移除 deletePages, 再保留其余页中的 pages.
        assertEquals(listOf(4), PageEditPlan.save(options(PdfContract.KEY_PAGES to "3-4", PdfContract.KEY_DELETE_PAGES to "1-3"), 6).pages())
        assertFalse(PageEditPlan.isIdentity(PageEditPlan.save(options(PdfContract.KEY_PAGES to "1-5"), 6), 6))
    }

    @Test
    fun `save refuses to remove every page`() {
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.save(options(PdfContract.KEY_DELETE_PAGES to "1-3"), 3) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.save(options(PdfContract.KEY_PAGES to "2", PdfContract.KEY_DELETE_PAGES to "2"), 3) }
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { PageEditPlan.save(options(PdfContract.KEY_DELETE_PAGES to "4"), 3) }
    }

    @Test
    fun `save reorders the kept pages by a permutation`() {
        assertEquals(listOf(3, 1, 2), PageEditPlan.save(options(PdfContract.KEY_ORDER to intArrayOf(3, 1, 2)), 3).pages())
        assertEquals(listOf(4, 2), PageEditPlan.save(options(PdfContract.KEY_PAGES to "2,4", PdfContract.KEY_ORDER to intArrayOf(4, 2)), 4).pages())
        assertEquals(listOf(3, 2), PageEditPlan.save(options(PdfContract.KEY_DELETE_PAGES to "1", PdfContract.KEY_ORDER to intArrayOf(3, 2)), 3).pages())
        // A page that is not kept, a page twice, a kept page left out. zh-CN: 未保留的页, 重复的页, 漏掉的保留页.
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.save(options(PdfContract.KEY_DELETE_PAGES to "1", PdfContract.KEY_ORDER to intArrayOf(1, 2, 3)), 3) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.save(options(PdfContract.KEY_ORDER to intArrayOf(1, 1, 2)), 3) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.save(options(PdfContract.KEY_ORDER to intArrayOf(2, 1)), 3) }
    }

    @Test
    fun `save rotates every kept page or rotatePages by degrees`() {
        val all = PageEditPlan.save(options(PdfContract.KEY_DEGREES to 90), 3)
        assertEquals(listOf(90, 90, 90), all.map { it.degrees })
        val some = PageEditPlan.save(options(PdfContract.KEY_DEGREES to -90, PdfContract.KEY_ROTATE_PAGES to "2-3"), 3)
        assertEquals(listOf(0, 270, 270), some.map { it.degrees })
        val reordered = PageEditPlan.save(
            options(PdfContract.KEY_ORDER to intArrayOf(3, 2, 1), PdfContract.KEY_DEGREES to 180, PdfContract.KEY_ROTATE_PAGES to "1"),
            3,
        )
        assertEquals(listOf(PagePlacement(3), PagePlacement(2), PagePlacement(1, 180)), reordered)
        assertEquals(listOf(0, 0), PageEditPlan.save(options(PdfContract.KEY_DEGREES to 360), 2).map { it.degrees })
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.save(options(PdfContract.KEY_DEGREES to 45), 2) }
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { PageEditPlan.save(options(PdfContract.KEY_DEGREES to 90, PdfContract.KEY_ROTATE_PAGES to "3"), 2) }
    }

    @Test
    fun `split by ranges keeps every range ascending`() {
        val parts = PageEditPlan.split(options(PdfContract.KEY_RANGES to arrayOf("1-2", "5,3", "4-")), 6)
        assertEquals(listOf(listOf(1, 2), listOf(3, 5), listOf(4, 5, 6)), parts.map { it.toList() })
        assertCode(PdfErrorCodes.PAGE_OUT_OF_RANGE) { PageEditPlan.split(options(PdfContract.KEY_RANGES to arrayOf("7")), 6) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.split(options(PdfContract.KEY_RANGES to emptyArray<String>()), 6) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.split(options(PdfContract.KEY_RANGES to arrayOf("1", null)), 6) }
    }

    @Test
    fun `split every n pages leaves a shorter last part`() {
        val parts = PageEditPlan.split(options(PdfContract.KEY_EVERY to 2), 5)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), parts.map { it.toList() })
        assertEquals(1, PageEditPlan.split(options(PdfContract.KEY_EVERY to 10), 5).size)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.split(options(PdfContract.KEY_EVERY to 0), 5) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.split(options(PdfContract.KEY_EVERY to 1), 0) }
    }

    @Test
    fun `split needs exactly one of ranges and every`() {
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.split(options(), 5) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageEditPlan.split(options(PdfContract.KEY_EVERY to 1, PdfContract.KEY_RANGES to arrayOf("1")), 5) }
    }

    @Test
    fun `split refuses more parts than MAX_SPLIT_OUTPUTS`() {
        val pages = PdfContract.MAX_SPLIT_OUTPUTS + 1
        assertEquals(PdfContract.MAX_SPLIT_OUTPUTS, PageEditPlan.split(options(PdfContract.KEY_EVERY to 1), pages - 1).size)
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { PageEditPlan.split(options(PdfContract.KEY_EVERY to 1), pages) }
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { PageEditPlan.split(options(PdfContract.KEY_RANGES to Array(pages) { "1" }), 2) }
    }

    @Test
    fun `planSplit answers index, pages and page count`() {
        val manifest = PageEditPlan.manifest(PageEditPlan.split(options(PdfContract.KEY_RANGES to arrayOf("1-3,5", "2")), 5))
        val items = JSONArray(manifest[PdfContract.KEY_ITEMS] as String)
        assertEquals(2, items.length())
        assertEquals(0, items.getJSONObject(0).getInt(PdfContract.FIELD_INDEX))
        assertEquals("1-3,5", items.getJSONObject(0).getString(PdfContract.FIELD_PAGES))
        assertEquals(4, items.getJSONObject(0).getInt(PdfContract.FIELD_PAGE_COUNT))
        assertEquals(1, items.getJSONObject(1).getInt(PdfContract.FIELD_INDEX))
        assertEquals("2", items.getJSONObject(1).getString(PdfContract.FIELD_PAGES))
        assertEquals(1, items.getJSONObject(1).getInt(PdfContract.FIELD_PAGE_COUNT))
    }

    @Test
    fun `merge requests check passwords, display names and outline`() {
        val plain = PageOperations.mergeRequest(options(), 2)
        assertEquals(listOf(null, null), plain.passwords)
        assertEquals(listOf(null, null), plain.displayNames)
        assertEquals(PdfContract.OUTLINE_MERGE, plain.outline)
        // Blank names fall back to the document title. zh-CN: 空白名称回退为文档标题.
        val named = PageOperations.mergeRequest(options(PdfContract.KEY_DISPLAY_NAMES to arrayOf(" Report 2026 ", "", "  ")), 3)
        assertEquals(listOf("Report 2026", null, null), named.displayNames)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageOperations.mergeRequest(options(PdfContract.KEY_DISPLAY_NAMES to arrayOf("a", "b", "c")), 2) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageOperations.mergeRequest(options(PdfContract.KEY_DISPLAY_NAMES to "a"), 1) }
        val given = PageOperations.mergeRequest(options(PdfContract.KEY_PASSWORDS to arrayOf("", "secret"), PdfContract.KEY_OUTLINE to PdfContract.OUTLINE_NONE), 2)
        assertEquals(listOf(null, "secret"), given.passwords)
        assertEquals(PdfContract.OUTLINE_NONE, given.outline)
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageOperations.mergeRequest(options(PdfContract.KEY_PASSWORDS to arrayOf("a")), 2) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageOperations.mergeRequest(options(PdfContract.KEY_PASSWORDS to arrayOf("x".repeat(PdfContract.MAX_PASSWORD_LENGTH + 1))), 1) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageOperations.mergeRequest(options(PdfContract.KEY_OUTLINE to "flat"), 2) }
        assertCode(PdfErrorCodes.INVALID_ARGUMENT) { PageOperations.mergeRequest(options(), 0) }
        assertCode(PdfErrorCodes.LIMIT_EXCEEDED) { PageOperations.mergeRequest(options(), PdfContract.MAX_RUN_INPUTS + 1) }
        // One input is accepted, as the host allows it (appendix D Q32). zh-CN: 接受单个输入, 与宿主一致 (附录 D Q32).
        assertEquals(1, PageOperations.mergeRequest(options(), 1).passwords.size)
    }

}

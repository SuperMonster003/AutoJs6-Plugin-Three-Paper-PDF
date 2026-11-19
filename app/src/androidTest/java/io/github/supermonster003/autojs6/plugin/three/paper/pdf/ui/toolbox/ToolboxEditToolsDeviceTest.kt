package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolTarget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The five editing tools of roadmap P6.12 (D68) once each on a device, from the home screen to the result page: the
 * input is handed to the wizard's view model in place of the file browser, the options are set on the screen, the task
 * runs in the foreground service and writes under the names of appendix D Q261, and the result is read back through
 * the engine's own Binder interface (text, form fields, info and outline). Below API 35 the fill-form result page
 * carries the hint of appendix D Q241.
 * zh-CN: 在设备上把路线图 P6.12 的五个编辑类工具 (D68) 各走一次, 从首页到结果页: 输入代替文件浏览器直接交给向导的视图模型, 选项在界面上
 * 设置, 任务在前台服务中运行并按附录 D Q261 的名称写入, 结果经引擎自己的 Binder 接口读回 (文本, 表单字段, 文档信息与书签). 低于 API 35 时
 * 填写表单的结果页带有附录 D Q241 的提示.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolboxEditToolsDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("toolbox-edit-tools-test")

    private val context get() = environment.context

    private val output by lazy { File(environment.root, "output") }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        environment.prepare()
        output.mkdirs()
        ToolboxPreferences.setOutputFolder(context, output)
    }

    @After
    fun tearDown() {
        environment.restore()
    }

    @Test
    fun watermarkPreviewsOnePageAndStampsTheText() = wizard(ToolKind.WATERMARK) { model ->
        give(model, sample("text.pdf", "Text.pdf"))
        compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement(WATERMARK)
        compose.onNodeWithTag("tool-watermark-rotation-0").performScrollTo().performClick()
        compose.onNodeWithTag("tool-watermark-opacity-50").performScrollTo().performClick()
        // The single-page preview is drawn by the engine after a short pause. zh-CN: 单页预览在短暂停顿后由引擎绘制.
        awaitTag("tool-watermark-preview")
        start()
        awaitTag("tool-done")
        val text = pageText(File(output, "Text_watermarked.pdf"), "1")
        assertTrue(text, text.contains(WATERMARK))
    }

    @Test
    fun pageNumbersStartAtTheGivenNumberOnTheChosenPages() = wizard(ToolKind.PAGE_NUMBERS) { model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("2-")
        compose.onNodeWithTag("tool-numbers-start").performScrollTo().performTextReplacement("5")
        compose.onNodeWithTag("tool-numbers-advanced").performScrollTo().performClick()
        awaitTag("tool-numbers-header-right")
        compose.onNodeWithTag("tool-numbers-header-right").performScrollTo().performTextReplacement(HEADER)
        start()
        awaitTag("tool-done")
        val result = File(output, "Outline_numbered.pdf")
        // Page 2 is the first numbered page and shows 5; {total} stays the page count (appendix D Q211).
        // zh-CN: 第 2 页是第一个加页码的页面, 显示 5; {total} 仍为总页数 (附录 D Q211).
        val second = pageText(result, "2")
        assertTrue(second, second.contains("5/$OUTLINE_PAGES"))
        assertTrue(second, second.contains(HEADER))
        val last = pageText(result, "$OUTLINE_PAGES")
        assertTrue(last, last.contains("${OUTLINE_PAGES + 3}/$OUTLINE_PAGES"))
        val first = pageText(result, "1")
        assertFalse(first, first.contains(HEADER) || first.contains("/$OUTLINE_PAGES"))
    }

    @Test
    fun compressWithTheSmallestPresetDropsTheMetadata() = wizard(ToolKind.COMPRESS) { model ->
        give(model, sample("images.pdf", "Images.pdf"))
        compose.onNodeWithTag("tool-compress-${PdfContract.COMPRESS_PRESET_LOW}").performScrollTo().performClick()
        compose.onNodeWithTag("tool-compress-advanced").performScrollTo().performClick()
        awaitTag("tool-compress-metadata")
        compose.onNodeWithTag("tool-compress-metadata").performScrollTo().performClick()
        start()
        awaitTag("tool-done")
        awaitTag("tool-result-summary")
        opened(File(output, "Images_compressed.pdf")) { document ->
            val info = document.getInfo()
            assertNull(info.getString(PdfContract.KEY_ERROR_CODE))
            assertEquals(1, info.getInt(PdfContract.KEY_PAGE_COUNT))
            assertFalse("the title was kept", info.containsKey(PdfContract.KEY_TITLE))
        }
    }

    @Test
    fun fillFormWritesTheChangedFieldsOnly() = wizard(ToolKind.FILL_FORM) { model ->
        give(model, sample("form.pdf", "Form.pdf"))
        awaitTag("tool-form-field-email")
        // Both page groups are open: the sample has fewer than 40 fields. zh-CN: 样例字段少于 40 个, 两个页面分组都展开.
        listOf("tool-form-page-1", "tool-form-page-2", "tool-form-field-colors").forEach { awaitTag(it) }
        compose.onNodeWithTag("tool-form-field-email").performScrollTo().performTextReplacement(EMAIL)
        compose.onNodeWithTag("tool-form-field-agree").performScrollTo().performClick()
        compose.onNodeWithTag("tool-form-choice-country-2").performScrollTo().performClick()
        val olderViewer = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        if (olderViewer) awaitTag("tool-form-viewer-note")
        start()
        awaitTag("tool-done")
        awaitTag("tool-result-summary")
        // Appendix D Q241: below API 35 the built-in viewer shows filled values only when flattened.
        // zh-CN: 附录 D Q241: 低于 API 35 时内置查看器只显示拼合后的值.
        assertEquals(olderViewer, compose.onAllNodesWithTag("tool-result-viewer-hint").fetchSemanticsNodes().isNotEmpty())
        opened(File(output, "Form_filled.pdf")) { document ->
            val answer = document.getFormFields(request { putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2) })
            assertNull(answer.getString(PdfContract.KEY_ERROR_CODE))
            val items = JSONArray(answer.getString(PdfContract.KEY_ITEMS))
            val fields = List(items.length()) { items.getJSONObject(it) }.associateBy { it.getString(PdfContract.FIELD_NAME) }
            assertEquals(EMAIL, fields.getValue("email").getString(PdfContract.FIELD_VALUE))
            assertFalse(fields.getValue("agree").getBoolean(PdfContract.FIELD_VALUE))
            assertEquals("Japan", fields.getValue("country").getString(PdfContract.FIELD_VALUE))
            // Untouched fields keep their values. zh-CN: 未改动的字段保持原值.
            assertEquals("Ada Lovelace", fields.getValue("name").getString(PdfContract.FIELD_VALUE))
            assertEquals("P-001", fields.getValue("id").getString(PdfContract.FIELD_VALUE))
        }
    }

    @Test
    fun propertiesSaveTheTitleAndClearTheOutline() = wizard(ToolKind.PROPERTIES) { model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        awaitTag("tool-properties-title")
        awaitTag("tool-properties-facts")
        awaitTag("tool-properties-outline")
        compose.onNodeWithTag("tool-properties-title").performScrollTo().performTextReplacement(TITLE)
        compose.onNodeWithTag("tool-properties-clear-outline").performScrollTo().performClick()
        start()
        awaitTag("tool-done")
        awaitTag("tool-result-summary")
        opened(File(output, "Outline_properties.pdf")) { document ->
            assertEquals(TITLE, document.getInfo().getString(PdfContract.KEY_TITLE))
            assertEquals(0, JSONArray(document.getOutline().getString(PdfContract.KEY_ITEMS)).length())
            val text = document.getText(request { putString(PdfContract.KEY_PAGES, "1-") })
            assertEquals(OUTLINE_PAGES, checkNotNull(text.getStringArray(PdfContract.KEY_TEXTS)).size)
        }
    }

    /** Opens the wizard of [kind] from the home screen and runs [block] once it waits for files. zh-CN: 从首页打开 [kind] 的向导, 等它等待文件后运行 [block]. */
    private fun wizard(kind: ToolKind, block: (PdfToolViewModel) -> Unit) {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            awaitTag("paper.home")
            compose.onNodeWithTag("paper.home").performScrollToNode(hasTestTag("paper.home.tool.${kind.id}"))
            compose.onNodeWithTag("paper.home.tool.${kind.id}").performClick()
            awaitTag("tool-no-files")
            compose.waitForIdle()
            lateinit var model: PdfToolViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(checkNotNull(activity.navigation?.currentBackStackEntry))[PdfToolViewModel::class.java]
            }
            block(model)
        }
    }

    /** Hands [target] to the wizard as the file browser does. zh-CN: 像文件浏览器那样把 [target] 交给向导. */
    private fun give(model: PdfToolViewModel, target: ToolTarget) {
        instrumentation.runOnMainSync { model.addTargets(listOf(target)) }
        compose.waitUntil(TIMEOUT_MILLIS) {
            var ready = false
            instrumentation.runOnMainSync { ready = model.inputs.size == 1 && model.inputs.none { it.inspecting } }
            ready
        }
    }

    private fun sample(name: String, displayName: String) = ToolTarget(Uri.fromFile(PdfServiceHarness.sampleFile(name)), displayName)

    private fun start() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        // The bottom bar follows the keyboard, so a click while it moves can miss: click until the button goes.
        // zh-CN: 底栏跟随键盘移动, 移动中的点击可能落空: 点击直到按钮消失.
        repeat(START_ATTEMPTS) {
            if (compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().isEmpty()) return
            compose.onNodeWithTag("tool-start").performClick()
            val gone = runCatching {
                compose.waitUntil(START_WAIT_MILLIS) { compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().isEmpty() }
            }.isSuccess
            if (gone) return
        }
        fail("the start button took no click")
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Opens [file] in the engine (contract version 2) for [block]. zh-CN: 在引擎中打开 [file] (契约版本 2) 交给 [block]. */
    private fun <T> opened(file: File, block: (IPdfDocument) -> T): T {
        assertTrue("${file.name} was not written", file.isFile)
        return withPdfPlugin { plugin ->
            val handle = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
                plugin.open(source, request { putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2) })
            }
            val document = IPdfDocument.Stub.asInterface(MarshallingBinder(handle.asBinder()))
            try {
                block(document)
            } finally {
                document.close()
            }
        }
    }

    /** The engine's text of [pages] of [file] without white space. zh-CN: 引擎抽取的 [file] 中 [pages] 的文本, 去掉空白. */
    private fun pageText(file: File, pages: String): String = opened(file) { document ->
        val answer = document.getText(request { putString(PdfContract.KEY_PAGES, pages) })
        assertNull(answer.getString(PdfContract.KEY_ERROR_MESSAGE), answer.getString(PdfContract.KEY_ERROR_CODE))
        checkNotNull(answer.getStringArray(PdfContract.KEY_TEXTS)).joinToString("\n").filterNot { it.isWhitespace() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val START_ATTEMPTS = 3
        const val START_WAIT_MILLIS = 5_000L
        const val OUTLINE_PAGES = 6
        const val WATERMARK = "CONFIDENTIAL"
        const val HEADER = "Quarterly"
        const val EMAIL = "ada@example.com"
        const val TITLE = "Annual report"
    }

}

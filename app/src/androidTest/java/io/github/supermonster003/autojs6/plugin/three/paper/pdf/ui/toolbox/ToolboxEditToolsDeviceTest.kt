package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.DeviceShots
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PagePreview
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolTarget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerDocument
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
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The five editing tools of roadmap P6.12 (D68) once each on a device, from the home screen to the result page: the
 * input is handed to the wizard's view model in place of the file browser, the options are set on the screen, the task
 * runs in the foreground service and writes under the names of appendix D Q261, and the result is read back through
 * the engine's own Binder interface (text, form fields, info, outline and images). Below API 35 the fill-form result page
 * carries the hint of appendix D Q241. The "Custom" values of appendix D Q265 (b) and Q290 run once per tool (watermark,
 * page numbers, compress): a refused text keeps the start button off, and the typed numbers show in the output (glyph
 * size and angle read with PDFBox, colour and opacity from the rendered page, image size and JPEG quality through the
 * engine). The single-page preview of the watermark (E4 feedback F4) turns through the pages, follows a change of the range
 * (appendix D Q344 (a)), draws a page outside the range as it is and shows what the result has on that page; the page
 * numbers tool has the same preview (E4 feedback F5).
 * zh-CN: 在设备上把路线图 P6.12 的五个编辑类工具 (D68) 各走一次, 从首页到结果页: 输入代替文件浏览器直接交给向导的视图模型, 选项在界面上
 * 设置, 任务在前台服务中运行并按附录 D Q261 的名称写入, 结果经引擎自己的 Binder 接口读回 (文本, 表单字段, 文档信息, 书签与图片). 低于
 * API 35 时填写表单的结果页带有附录 D Q241 的提示. 附录 D Q265 (b) 与 Q290 的 "自定义" 值在每个工具 (水印, 页码, 压缩) 上各走一次: 被拒的
 * 文字让开始按钮保持不可用, 输入的数值体现在输出中 (字形大小与角度用 PDFBox 读取, 颜色与不透明度取自渲染出的页面, 图片尺寸与 JPEG
 * 质量经引擎读回). 水印的单页预览 (E4 反馈 F4) 可以翻页, 跟随范围的修改 (附录 D Q344 (a)), 范围外的页按原样绘制, 并显示结果在该页上的样子; 页码工具有
 * 相同的预览 (E4 反馈 F5).
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
    fun theWatermarkPreviewTurnsThePagesFollowsTheRangeAndMatchesTheResult() = wizard(ToolKind.WATERMARK) { model ->
        // E4 feedback F4, appendix D Q341 (a) and Q344 (a). zh-CN: E4 反馈 F4, 附录 D Q341 (a) 与 Q344 (a).
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement("P{page}/{total}")
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("2-3")
        // The preview starts on the first page of the range. zh-CN: 预览从范围的第一页开始.
        awaitPreview(model, WATERMARK_PREVIEW, page = 2, stamped = true)
        turn(WATERMARK_PREVIEW, next = true)
        awaitPreview(model, WATERMARK_PREVIEW, page = 3, stamped = true)
        turn(WATERMARK_PREVIEW, next = true)
        awaitPreview(model, WATERMARK_PREVIEW, page = 4, stamped = false)
        shot(WATERMARK_PREVIEW, "F4-watermark-preview-outside")
        // A range without the page moves to its first page; one with it, or one that is not valid, keeps the page.
        // zh-CN: 不含当前页的范围转到其第一页; 包含当前页的范围或无效的范围保持当前页.
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("5-")
        awaitPreview(model, WATERMARK_PREVIEW, page = 5, stamped = true)
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("")
        awaitPreview(model, WATERMARK_PREVIEW, page = 5, stamped = true)
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("9-")
        awaitPreview(model, WATERMARK_PREVIEW, page = 5, stamped = true)
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("2-3")
        awaitPreview(model, WATERMARK_PREVIEW, page = 2, stamped = true)
        turn(WATERMARK_PREVIEW, next = true)
        val shown = awaitPreview(model, WATERMARK_PREVIEW, page = 3, stamped = true)
        shot(WATERMARK_PREVIEW, "F4-watermark-preview-page-3")
        start()
        awaitTag("tool-done")
        assertSamePage(shown, File(output, "Outline_watermarked.pdf"), 3)
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
    fun thePageNumbersPreviewShowsWhatTheResultHasOnThePage() = wizard(ToolKind.PAGE_NUMBERS) { model ->
        // E4 feedback F5, appendix D Q342 (a): the number counts from the start, the header from the first page.
        // zh-CN: E4 反馈 F5, 附录 D Q342 (a): 页码从起始数字计, 页眉从第一页计.
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.onNodeWithTag("tool-pages").performScrollTo().performTextReplacement("2-")
        compose.onNodeWithTag("tool-numbers-start").performScrollTo().performTextReplacement("5")
        compose.onNodeWithTag("tool-numbers-advanced").performScrollTo().performClick()
        awaitTag("tool-numbers-header-right")
        compose.onNodeWithTag("tool-numbers-header-right").performScrollTo().performTextReplacement("$HEADER {page}")
        awaitPreview(model, NUMBERS_PREVIEW, page = 2, stamped = true)
        turn(NUMBERS_PREVIEW, next = false)
        awaitPreview(model, NUMBERS_PREVIEW, page = 1, stamped = false)
        shot(NUMBERS_PREVIEW, "F5-numbers-preview-outside")
        repeat(3) { turn(NUMBERS_PREVIEW, next = true) }
        val shown = awaitPreview(model, NUMBERS_PREVIEW, page = 4, stamped = true)
        shot(NUMBERS_PREVIEW, "F5-numbers-preview-page-4")
        start()
        awaitTag("tool-done")
        val result = File(output, "Outline_numbered.pdf")
        val text = pageText(result, "4")
        assertTrue(text, text.contains("7/$OUTLINE_PAGES"))
        assertTrue(text, text.contains("${HEADER}4"))
        assertSamePage(shown, result, 4)
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
    fun watermarkTakesCustomRotationOpacityFontSizeAndColour() = wizard(ToolKind.WATERMARK) { model ->
        give(model, sample("text.pdf", "Text.pdf"))
        compose.onNodeWithTag("tool-watermark-text").performScrollTo().performTextReplacement(WATERMARK)
        custom("tool-watermark-rotation", "$CUSTOM_ROTATION")
        custom("tool-watermark-opacity", "$CUSTOM_OPACITY_PERCENT")
        compose.onNodeWithTag("tool-watermark-advanced").performScrollTo().performClick()
        awaitTag("tool-watermark-font-custom")
        custom("tool-watermark-font", "$CUSTOM_WATERMARK_FONT")
        compose.onNodeWithTag("tool-watermark-color-custom").performScrollTo().performClick()
        awaitTag("tool-watermark-color-value")
        // A colour that is not hex stops the start button. zh-CN: 不是十六进制的颜色让开始按钮不可用.
        compose.onNodeWithTag("tool-watermark-color-value").performScrollTo().performTextReplacement("red")
        assertTrue("the start button took a colour that is not hex", startDisabled())
        compose.onNodeWithTag("tool-watermark-color-value").performScrollTo().performTextReplacement(CUSTOM_COLOR)
        awaitTag("tool-watermark-preview")
        start()
        awaitTag("tool-done")
        val result = File(output, "Text_watermarked.pdf")
        val text = pageText(result, "1")
        assertTrue(text, text.contains(WATERMARK))
        val glyphs = glyphRun(result, 1, WATERMARK)
        glyphs.forEach { assertEquals("glyph size of '${it.unicode}'", CUSTOM_WATERMARK_FONT, sizeOf(it), SIZE_TOLERANCE) }
        // D61 angles turn counterclockwise, so the baseline rises in user space. zh-CN: D61 的角度逆时针, 因此基线在用户空间中上升.
        val first = glyphs.first().textMatrix
        val last = glyphs.last().textMatrix
        val angle = Math.toDegrees(atan2((last.translateY - first.translateY).toDouble(), (last.translateX - first.translateX).toDouble()))
        assertEquals("baseline angle", CUSTOM_ROTATION, angle, ANGLE_TOLERANCE)
        // Pure red at 42.5 % over white: green and blue fall to about 255 * 0.575 where the glyphs are fully covered.
        // zh-CN: 纯红色以 42.5 % 叠在白色上: 字形完全覆盖处的绿与蓝降到约 255 * 0.575.
        val bitmap = render(result, RENDER_SCALE)
        try {
            var reddish = 0
            var deepest = 255
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
                row.forEach { pixel ->
                    val red = Color.red(pixel)
                    if (red > 200 && red - Color.green(pixel) > 60 && red - Color.blue(pixel) > 60) {
                        reddish++
                        deepest = minOf(deepest, Color.green(pixel))
                    }
                }
            }
            assertTrue("only $reddish reddish pixels", reddish > MIN_STAMP_PIXELS)
            val expected = (255 * (1 - CUSTOM_OPACITY_PERCENT / 100)).toInt()
            assertTrue("deepest green $deepest for an opacity of $CUSTOM_OPACITY_PERCENT %, expected about $expected", deepest in expected - 12..expected + 12)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun pageNumbersTakeACustomFontSize() = wizard(ToolKind.PAGE_NUMBERS) { model ->
        give(model, sample("outline.pdf", "Outline.pdf"))
        compose.onNodeWithTag("tool-numbers-advanced").performScrollTo().performClick()
        awaitTag("tool-numbers-font-custom")
        // Zero is below the range (greater than 0): the hint stays under the field and the start button off.
        // zh-CN: 0 在范围之外 (须大于 0): 提示留在输入框下方, 开始按钮不可用.
        custom("tool-numbers-font", "0")
        awaitTag("tool-numbers-font-range")
        assertTrue("the start button took a font size of 0", startDisabled())
        compose.onNodeWithTag("tool-numbers-font-value").performScrollTo().performTextReplacement("$CUSTOM_NUMBER_FONT")
        start()
        awaitTag("tool-done")
        val result = File(output, "Outline_numbered.pdf")
        val text = pageText(result, "1")
        assertTrue(text, text.contains("1/$OUTLINE_PAGES"))
        glyphRun(result, 1, "1/$OUTLINE_PAGES").forEach {
            assertEquals("glyph size of '${it.unicode}'", CUSTOM_NUMBER_FONT, sizeOf(it), SIZE_TOLERANCE)
        }
    }

    @Test
    fun compressTakesACustomResolutionAndQuality() = wizard(ToolKind.COMPRESS) { model ->
        give(model, sample("images.pdf", "Images.pdf"))
        compose.onNodeWithTag("tool-compress-advanced").performScrollTo().performClick()
        awaitTag("tool-compress-dpi-custom")
        custom("tool-compress-dpi", "abc")
        awaitTag("tool-compress-dpi-range")
        assertTrue("the start button took text for the resolution", startDisabled())
        compose.onNodeWithTag("tool-compress-dpi-value").performScrollTo().performTextReplacement("${PdfContract.MIN_IMAGE_DPI - 1}")
        assertTrue("the start button took a resolution below the range", startDisabled())
        compose.onNodeWithTag("tool-compress-dpi-value").performScrollTo().performTextReplacement("$CUSTOM_DPI")
        custom("tool-compress-quality", "$CUSTOM_QUALITY")
        start()
        awaitTag("tool-done")
        awaitTag("tool-result-summary")
        val photo = File(output, "photo.jpg")
        opened(File(output, "Images_compressed.pdf")) { document ->
            val info = document.getInfo()
            assertNull(info.getString(PdfContract.KEY_ERROR_CODE))
            assertEquals(1, info.getInt(PdfContract.KEY_PAGE_COUNT))
            val plan = document.planImages(request())
            assertNull(plan.getString(PdfContract.KEY_ERROR_CODE))
            val items = JSONArray(plan.getString(PdfContract.KEY_ITEMS))
            val sizes = List(items.length()) { items.getJSONObject(it).let { item -> item.getInt(PdfContract.FIELD_WIDTH) to item.getInt(PdfContract.FIELD_HEIGHT) } }
            // Im1 is 160 x 120 pixels on 240 x 180 points (48 dpi): at 40 dpi it takes ceil(133.3) x 100 pixels. Im2 (64 pixels on
            // 128 points, 36 dpi) is not above 40 dpi and stays.
            // zh-CN: Im1 为 160 x 120 像素, 占 240 x 180 点 (48 dpi): 40 dpi 时为 ceil(133.3) x 100 像素. Im2 (64 像素占 128 点, 36 dpi)
            // 不高于 40 dpi, 保持不变.
            assertEquals(listOf(134 to 100, 64 to 64), sizes)
            assertEquals(PdfContract.FORMAT_JPEG, items.getJSONObject(0).getString(PdfContract.FIELD_FORMAT))
            ParcelFileDescriptor.open(photo, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE).use {
                val answer = document.extractImage(request { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_INDEX, 0) }, it)
                assertNull(answer.getString(PdfContract.KEY_ERROR_CODE))
            }
        }
        // The first quantization value of the luminance table is the DC step libjpeg derives from the quality.
        // zh-CN: 亮度量化表的第一个值是 libjpeg 由质量推出的直流步长.
        assertEquals(dcStep(CUSTOM_QUALITY), firstQuantizationValue(photo.readBytes()))
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
        switchOn("tool-properties-clear-outline")
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

    /**
     * Clicks the switch [tag] until it is on: a click while the keyboard of the previous field still moves the layout
     * can miss, and then the run used the old value (the clear outline switch failed this way in one of four runs).
     * zh-CN: 点击开关 [tag] 直到它打开: 上一个输入框的键盘仍在移动布局时点击可能落空, 运行随之沿用旧值 (清除大纲开关曾在四次运行里失败一次).
     */
    private fun switchOn(tag: String) {
        fun on() = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }
        repeat(START_ATTEMPTS) {
            if (on()) return
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            if (runCatching { compose.waitUntil(START_WAIT_MILLIS) { on() } }.isSuccess) return
        }
        if (!on()) fail("the switch $tag took no click")
    }

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

    /**
     * Waits for a node tagged [tag] in the unmerged tree: the range line under a text field is merged into the field.
     * zh-CN: 在未合并的语义树中等待标记为 [tag] 的节点: 输入框下方的范围提示会被合并进输入框.
     */
    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
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

    /**
     * Chooses the "Custom" chip of the option [prefix] and types [text]. The chip is clicked until its field shows: like
     * the switches of [switchOn], a click while the keyboard of the previous field still moves the layout can miss (the
     * rotation chip of the watermark failed this way in one of three runs).
     * zh-CN: 选择选项 [prefix] 的 "自定义" 并输入 [text]. 芯片点击到其输入框出现为止: 与 [switchOn] 的开关一样, 上一个输入框的键盘仍在移动布局时点击可能落空
     * (水印的旋转芯片曾在三次运行里失败一次).
     */
    private fun custom(prefix: String, text: String) {
        fun shown() = compose.onAllNodesWithTag("$prefix-value", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        repeat(START_ATTEMPTS) {
            if (shown()) return@repeat
            compose.onNodeWithTag("$prefix-custom").performScrollTo().performClick()
            runCatching { compose.waitUntil(START_WAIT_MILLIS) { shown() } }
        }
        if (!shown()) fail("the chip $prefix-custom took no click")
        compose.onNodeWithTag("$prefix-value").performScrollTo().performTextReplacement(text)
    }

    /** A screenshot with the arrows of the preview [tag] in view. zh-CN: 让预览 [tag] 的箭头可见后截图. */
    private fun shot(tag: String, name: String) {
        compose.onNodeWithTag("$tag-page").performScrollTo()
        compose.waitForIdle()
        DeviceShots.take(name)
    }

    /** Clicks the next or previous arrow of the preview [tag]. zh-CN: 点击预览 [tag] 的下一页或上一页箭头. */
    private fun turn(tag: String, next: Boolean) {
        compose.onNodeWithTag(if (next) "$tag-next" else "$tag-previous").performScrollTo().performClick()
    }

    /**
     * Waits until the preview shows [page], with the stamp or as it is, its arrows say so and the outside note shows
     * for a page drawn as it is; answers a copy of the drawn page.
     * zh-CN: 等待预览显示第 [page] 页 (带盖章或按原样), 箭头之间显示该页, 按原样绘制的页显示范围外提示; 返回所绘页面的副本.
     */
    private fun awaitPreview(model: PdfToolViewModel, tag: String, page: Int, stamped: Boolean): Bitmap {
        var count = 0
        instrumentation.runOnMainSync { count = model.previewPageCount() }
        compose.waitUntil(TIMEOUT_MILLIS) {
            var done = false
            instrumentation.runOnMainSync {
                val preview = model.preview
                done = model.previewPage == page && preview != null && !preview.busy && preview.bitmap != null && preview.page == page &&
                    preview.stamped == stamped
            }
            done
        }
        val label = context.getString(R.string.tool_preview_page_of, page, count)
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("$tag-page").fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("$tag-outside", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() == !stamped
        }
        lateinit var copy: Bitmap
        instrumentation.runOnMainSync { copy = checkNotNull(model.preview?.bitmap).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false) }
        return copy
    }

    /**
     * The preview [shown] equals page [page] of [file] drawn the same way (the viewer's renderer at the preview width):
     * fewer than 1 % of the pixels differ by more than 40 in a channel.
     * zh-CN: 预览 [shown] 与用相同方式 (查看器的渲染器, 预览宽度) 绘制的 [file] 第 [page] 页相同: 某通道相差超过 40 的像素少于 1 %.
     */
    private fun assertSamePage(shown: Bitmap, file: File, page: Int) {
        assertTrue("${file.name} was not written", file.isFile)
        val scratch = File(environment.root, "render").apply { mkdirs() }
        val expected = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val document = ViewerDocument.open(descriptor, null, scratch)
            try {
                val frame = document.pages[page - 1]
                val height = (PagePreview.WIDTH_PX * frame.height / frame.width).roundToInt()
                Bitmap.createBitmap(PagePreview.WIDTH_PX, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    document.render(page - 1, it)
                }
            } finally {
                document.close()
            }
        }
        try {
            assertEquals("width", expected.width, shown.width)
            assertEquals("height", expected.height, shown.height)
            val a = IntArray(expected.width)
            val b = IntArray(expected.width)
            var differing = 0
            for (y in 0 until expected.height) {
                expected.getPixels(a, 0, a.size, 0, y, a.size, 1)
                shown.getPixels(b, 0, b.size, 0, y, b.size, 1)
                for (x in a.indices) {
                    val p = a[x]
                    val q = b[x]
                    val delta = maxOf(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
                    if (delta > PIXEL_TOLERANCE) differing++
                }
            }
            val share = differing.toDouble() / (expected.width * expected.height)
            assertTrue("$differing pixels (${"%.3f".format(share * 100)} %) of the preview differ from page $page of the result", share < MAX_DIFFERING_SHARE)
        } finally {
            expected.recycle()
            shown.recycle()
        }
    }

    private fun startDisabled(): Boolean = compose.onAllNodesWithTag("tool-start").fetchSemanticsNodes().let { nodes ->
        nodes.isNotEmpty() && nodes.all { it.config.getOrNull(SemanticsProperties.Disabled) != null }
    }

    /**
     * The glyphs of the last run of [text] on page [page] of [file] in content order, white space left out, read with
     * PDFBox.
     * zh-CN: 用 PDFBox 读取 [file] 第 [page] 页上 [text] 最后一次出现的字形, 按内容流顺序, 不含空白.
     */
    private fun glyphRun(file: File, page: Int, text: String): List<TextPosition> {
        val owners = ArrayList<TextPosition>()
        val chars = StringBuilder()
        PDDocument.load(file).use { document ->
            object : PDFTextStripper() {
                override fun processTextPosition(position: TextPosition) {
                    position.unicode.orEmpty().filterNot { it.isWhitespace() }.forEach {
                        chars.append(it)
                        owners += position
                    }
                }
            }.apply {
                startPage = page
                endPage = page
            }.getText(document)
        }
        val start = chars.lastIndexOf(text)
        assertTrue("'$text' is not among the glyphs of page $page: $chars", start >= 0)
        return owners.subList(start, start + text.length).distinct()
    }

    /** The glyph size in user space, as the engine's search measures it. zh-CN: 用户空间中的字形大小, 与引擎搜索的度量相同. */
    private fun sizeOf(position: TextPosition): Double = position.textMatrix.let { hypot(it.shearX.toDouble(), it.scaleY.toDouble()) }

    private fun render(file: File, scale: Int): Bitmap = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    /** The DC step of the libjpeg luminance table at [quality] (`jpeg_set_quality`, baseline). zh-CN: libjpeg 在 [quality] 下亮度表的直流步长. */
    private fun dcStep(quality: Int): Int {
        val scale = if (quality < 50) 5000 / quality else 200 - 2 * quality
        return ((JPEG_LUMINANCE_DC * scale + 50) / 100).coerceIn(1, 255)
    }

    /** The first value of the first 8-bit quantization table (DQT) of [jpeg]. zh-CN: [jpeg] 第一个 8 位量化表 (DQT) 的第一个值. */
    private fun firstQuantizationValue(jpeg: ByteArray): Int {
        for (index in 0 until jpeg.size - 5) {
            if (jpeg[index] != 0xFF.toByte() || jpeg[index + 1] != 0xDB.toByte()) continue
            assertEquals("the table is not 8-bit", 0, (jpeg[index + 4].toInt() shr 4) and 0x0F)
            return jpeg[index + 5].toInt() and 0xFF
        }
        fail("no quantization table")
        return -1
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
        const val CUSTOM_ROTATION = 30.5
        const val CUSTOM_OPACITY_PERCENT = 42.5
        const val CUSTOM_WATERMARK_FONT = 37.5
        const val CUSTOM_COLOR = "#FF0000"
        const val CUSTOM_NUMBER_FONT = 14.5
        const val CUSTOM_DPI = 40
        const val CUSTOM_QUALITY = 33
        const val SIZE_TOLERANCE = 0.05
        const val ANGLE_TOLERANCE = 0.2
        const val RENDER_SCALE = 2
        const val MIN_STAMP_PIXELS = 500
        const val JPEG_LUMINANCE_DC = 16
        const val PIXEL_TOLERANCE = 40
        const val MAX_DIFFERING_SHARE = 0.01
        const val WATERMARK_PREVIEW = "tool-watermark-preview"
        const val NUMBERS_PREVIEW = "tool-numbers-preview"

        @BeforeClass
        @JvmStatic
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }

}

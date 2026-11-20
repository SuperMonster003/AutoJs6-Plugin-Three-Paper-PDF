package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionJavaScript
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolTarget
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.ToolboxTestEnvironment
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.AppearancePreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

/**
 * The warning codes of appendix D Q264 (b) on the result page of two tools, from the home screen to the result page in a
 * language other than English: the input is handed to the wizard's view model in place of the file browser, the task runs
 * at contract version 2 in the foreground service, and each warning shows the text of the plugin's resources in the screen
 * language instead of the engine's English. Filling a form with a keystroke script and a signed signature field gives
 * `FORM_SCRIPTS_NOT_RUN` and `SIGNATURES_INVALIDATED` (a count, Russian "one" form); saving the title of a document with
 * an XMP packet that cannot be parsed gives `XMP_REMOVED` (Japanese). The inputs are written by the test with PDFBox.
 * zh-CN: 附录 D Q264 (b) 的警告代码在两个工具结果页上的表现, 以非英文界面语言从首页走到结果页: 输入代替文件浏览器直接交给向导的视图模型, 任务在
 * 前台服务中以契约版本 2 运行, 每条警告显示插件资源中界面语言的文字, 而不是引擎的英文. 填写带按键脚本与已签名签名字段的表单得到
 * `FORM_SCRIPTS_NOT_RUN` 与 `SIGNATURES_INVALIDATED` (计数参数, 俄语 "one" 形式); 给 XMP 数据包无法解析的文档保存标题得到 `XMP_REMOVED`
 * (日语). 输入由测试用 PDFBox 生成.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolboxWarningsDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val environment = ToolboxTestEnvironment("toolbox-warnings-test")

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
    fun fillFormShowsTheScriptAndSignatureWarningsInRussian() {
        val source = File(environment.root, "Signed.pdf").also { signedForm(it) }
        language("ru")
        wizard(ToolKind.FILL_FORM) { model ->
            give(model, ToolTarget(Uri.fromFile(source), source.name))
            awaitTag("tool-form-field-$TEXT_FIELD")
            compose.onNodeWithTag("tool-form-field-$TEXT_FIELD").performScrollTo().performTextReplacement(VALUE)
            start()
            awaitTag("tool-done")
            awaitTag("tool-result-summary")
            assertTrue("Signed_filled.pdf was not written", File(output, "Signed_filled.pdf").isFile)
            val russian = resources("ru-RU")
            val expected = listOf(
                russian.getString(R.string.tool_warning_form_scripts_not_run),
                russian.resources.getQuantityString(R.plurals.tool_warning_signatures_invalidated, 1, 1L),
            )
            assertEquals(expected, warnings())
            val english = resources("en-US")
            assertNotEquals(english.getString(R.string.tool_warning_form_scripts_not_run), expected[0])
            assertNotEquals(english.resources.getQuantityString(R.plurals.tool_warning_signatures_invalidated, 1, 1L), expected[1])
            // The Russian "one" form, not "few" or "many". zh-CN: 俄语 "one" 形式, 不是 "few" 或 "many".
            assertNotEquals(russian.resources.getQuantityString(R.plurals.tool_warning_signatures_invalidated, 2, 1L), expected[1])
        }
    }

    @Test
    fun propertiesShowTheXmpWarningInJapanese() {
        val source = File(environment.root, "Broken.pdf").also { brokenXmp(it) }
        language("ja")
        wizard(ToolKind.PROPERTIES) { model ->
            give(model, ToolTarget(Uri.fromFile(source), source.name))
            awaitTag("tool-properties-title")
            compose.onNodeWithTag("tool-properties-title").performScrollTo().performTextReplacement(TITLE)
            start()
            awaitTag("tool-done")
            awaitTag("tool-result-summary")
            assertTrue("Broken_properties.pdf was not written", File(output, "Broken_properties.pdf").isFile)
            val expected = resources("ja-JP").getString(R.string.tool_warning_xmp_removed)
            assertEquals(listOf(expected), warnings())
            assertNotEquals(resources("en-US").getString(R.string.tool_warning_xmp_removed), expected)
        }
    }

    /** Sets the plugin's screen language before the home screen starts. zh-CN: 在首页启动前设置插件的界面语言. */
    private fun language(tag: String) {
        assertTrue(AppearancePreferences(language = tag).save(context))
    }

    /** The plugin's resources in [tag]. zh-CN: [tag] 语言的插件资源. */
    private fun resources(tag: String): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    /** The texts of the warnings on the result page, in order. zh-CN: 结果页上警告的文字, 按顺序. */
    private fun warnings(): List<String> {
        awaitTag("tool-result-warning")
        return compose.onAllNodesWithTag("tool-result-warning", useUnmergedTree = true).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
        }
    }

    /**
     * A one-page form with the text field [TEXT_FIELD], whose keystroke action is JavaScript, and a signature field whose
     * value is a signature dictionary (a signed field as the engine counts it).
     * zh-CN: 单页表单: 文本字段 [TEXT_FIELD] 的按键动作是 JavaScript; 签名字段的值是签名字典 (引擎按已签名字段计数).
     */
    private fun signedForm(file: File) = PDDocument().use { document ->
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val form = PDAcroForm(document)
        document.documentCatalog.acroForm = form
        form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
        form.defaultAppearance = "/Helv 0 Tf 0 g"
        val text = PDTextField(form).apply {
            partialName = TEXT_FIELD
            defaultAppearance = "/Helv 12 Tf 0 g"
            cosObject.setItem(COSName.AA, COSDictionary().apply { setItem(COSName.K, PDActionJavaScript(KEYSTROKE_SCRIPT).cosObject) })
        }
        text.widgets.single().apply {
            rectangle = PDRectangle(50f, 700f, 200f, 20f)
            setPage(page)
            page.annotations.add(this)
        }
        val signature = PDSignatureField(form).apply {
            partialName = SIGNATURE_FIELD
            cosObject.setItem(COSName.V, COSDictionary().apply {
                setItem(COSName.TYPE, COSName.SIG)
                setItem(COSName.FILTER, COSName.getPDFName("Adobe.PPKLite"))
            })
        }
        signature.widgets.single().apply {
            rectangle = PDRectangle(50f, 600f, 200f, 40f)
            setPage(page)
            page.annotations.add(this)
        }
        form.fields.add(text)
        form.fields.add(signature)
        document.save(file)
    }

    /** A one-page document whose XMP packet is not closed, so it cannot be parsed. zh-CN: 单页文档, XMP 数据包未闭合, 无法解析. */
    private fun brokenXmp(file: File) = PDDocument().use { document ->
        document.addPage(PDPage(PDRectangle.A4))
        val stream = PDStream(document, ByteArrayInputStream(BROKEN_XMP.toByteArray())).cosObject
        stream.setItem(COSName.TYPE, COSName.METADATA)
        stream.setItem(COSName.SUBTYPE, COSName.getPDFName("XML"))
        document.documentCatalog.cosObject.setItem(COSName.METADATA, stream)
        document.save(file)
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
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val START_ATTEMPTS = 3
        const val START_WAIT_MILLIS = 5_000L
        const val TEXT_FIELD = "amount"
        const val SIGNATURE_FIELD = "approval"
        const val VALUE = "42"
        const val TITLE = "Annual report"
        const val KEYSTROKE_SCRIPT = "AFNumber_Keystroke(2, 0, 0, 0, \"\", true);"
        const val BROKEN_XMP = "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"

        @BeforeClass
        @JvmStatic
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }

}

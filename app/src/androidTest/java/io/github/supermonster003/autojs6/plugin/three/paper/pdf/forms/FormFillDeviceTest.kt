package io.github.supermonster003.autojs6.plugin.three.paper.pdf.forms

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.MarshallingBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sampleFile
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * `getFormFields`, `getInfo` and `fillForm` through Binder (roadmap P6.4, D56, D62, spike S1) on the `form.pdf` sample:
 * after filling, the values read back as written (Chinese and emoji text, multiline text, check box, radio group, combo
 * box, multi-select list, `null` restoring the default); Latin values get a PDFBox appearance, values the standard
 * fonts cannot show get the Canvas fallback, and `/NeedAppearances` stays unset (roadmap Q242); the Chinese widget area
 * of the flattened copy has ink in the `PdfRenderer` rendering (all API levels; on API 35 and later every widget of the
 * filled copy renders within a pixel threshold of the flattened copy, and the S1 probe of a copy with
 * `/NeedAppearances` set, which the renderer redraws with its own appearances, is logged); flattening leaves no field;
 * unknown names, read-only fields, push buttons, signature fields and mismatched values fail, `ignoreMissing` turns
 * unknown names into a warning; a dynamic XFA form is `UNSUPPORTED`, a static one loses its XFA data with a warning,
 * like a signed signature field. Numbers go to logcat under `PaperForms`.
 * zh-CN: 经 Binder 在 `form.pdf` 样例上验证 `getFormFields`, `getInfo` 与 `fillForm` (路线图 P6.4, D56, D62, spike S1): 填写后读回的值与
 * 填入的相同 (中文与表情文本, 多行文本, 勾选框, 单选组, 下拉框, 多选列表, `null` 恢复默认值); 拉丁文字的值由 PDFBox 生成外观, 标准字体
 * 显示不了的值走 Canvas 回落, 不置 `/NeedAppearances` (路线图 Q242); 扁平化副本的中文控件区域在 `PdfRenderer` 渲染中有墨迹 (全部 API
 * 级别; API 35 及以上填写后副本的每个控件与扁平化副本的渲染差在像素阈值内, 并把 S1 探针写入日志: 置上 `/NeedAppearances` 的副本由渲染器
 * 按自己的外观重画); 扁平化后不剩字段; 未知字段名, 只读字段,
 * 按钮, 签名字段与类型不符的值失败, `ignoreMissing` 把未知字段名变成警告; 动态 XFA 表单为 `UNSUPPORTED`, 静态 XFA 删除 XFA 数据并给出
 * 警告, 已签名的签名字段同样给出警告. 数值以 `PaperForms` 写入 logcat.
 */
@RunWith(AndroidJUnit4::class)
class FormFillDeviceTest {

    private val directory = File(context.cacheDir, "fill-form-op")

    @After
    fun removeOutputs() {
        if (InstrumentationRegistry.getArguments().getString("paperKeep") != "true") directory.deleteRecursively()
    }

    private fun log(message: String) {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} ${Build.MODEL}: $message")
    }

    /** Records the end of one operation. zh-CN: 记录一个操作的结束. */
    private class Recorder : IPdfProgressCallback.Stub() {

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        private val ended = CountDownLatch(1)

        override fun onProgress(opId: Int, progress: Bundle) = Unit

        override fun onComplete(opId: Int, result: Bundle) {
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            this.error = error
            ended.countDown()
        }

        fun await(): Recorder {
            assertTrue("the operation did not end in time", ended.await(300, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

        /** The error code and message of a failed operation. zh-CN: 失败操作的错误码与消息. */
        fun failure(): Pair<String, String> {
            val error = checkNotNull(error) { "the operation completed: $result" }
            return error.getString(PdfContract.KEY_ERROR_CODE).orEmpty() to error.getString(PdfContract.KEY_ERROR_MESSAGE).orEmpty()
        }

    }

    /** Pixels of one rendered page, 2 pixels per point. zh-CN: 一页的渲染像素, 每点 2 像素. */
    private class Rendering(val width: Int, val pixels: IntArray)

    private fun file(name: String): File = File(directory, name).also { it.parentFile?.mkdirs() }

    private fun read(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun write(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)

    private fun payload(values: JSONObject, flatten: Any? = null): String = JSONObject().put(PdfContract.FIELD_VALUES, values).apply {
        if (flatten != null) put(PdfContract.FIELD_FLATTEN, flatten)
    }.toString()

    private fun IPdfPlugin.run(source: File, payload: String, target: File, ignoreMissing: Boolean = false): Recorder {
        val recorder = Recorder()
        val data = file("payload-${SystemClock.elapsedRealtimeNanos()}.json").apply { writeText(payload) }
        read(source).use { input ->
            read(data).use { values ->
                write(target).use { output ->
                    val request = Bundle().apply {
                        putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
                        putString(PdfContract.KEY_OP, PdfOps.FILL_FORM)
                        if (ignoreMissing) putBoolean(PdfContract.KEY_IGNORE_MISSING, true)
                    }
                    run(request, arrayOf(input, values), output, recorder)
                }
            }
        }
        return recorder.await()
    }

    private fun <T> IPdfPlugin.opened(file: File, block: (IPdfDocument) -> T): T {
        val handle = read(file).use { open(it, request { putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2) }) }
        val document = IPdfDocument.Stub.asInterface(MarshallingBinder(handle.asBinder()))
        try {
            return block(document)
        } finally {
            document.close()
        }
    }

    private fun Bundle.succeeded(): Bundle {
        assertNull(getString(PdfContract.KEY_ERROR_MESSAGE), getString(PdfContract.KEY_ERROR_CODE))
        return this
    }

    /** One page of `getFormFields`. zh-CN: `getFormFields` 的一页. */
    private fun IPdfDocument.page(offset: Int = 0, limit: Int = 0): Pair<List<JSONObject>, Bundle> {
        val answer = getFormFields(request {
            putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
            if (offset > 0) putInt(PdfContract.KEY_OFFSET, offset)
            if (limit > 0) putInt(PdfContract.KEY_LIMIT, limit)
        }).succeeded()
        val items = JSONArray(answer.getString(PdfContract.KEY_ITEMS))
        return List(items.length()) { items.getJSONObject(it) } to answer
    }

    /** Every field by name. zh-CN: 按名称列出全部字段. */
    private fun IPdfDocument.fields(): Map<String, JSONObject> = page().first.associateBy { it.getString(PdfContract.FIELD_NAME) }

    private fun IPdfDocument.info(): Bundle = getInfo().succeeded()

    private fun JSONObject.strings(key: String): List<String> = getJSONArray(key).let { array -> List(array.length()) { array.getString(it) } }

    private fun JSONObject.rect(): DoubleArray = getJSONArray(PdfContract.FIELD_RECT).let { array -> DoubleArray(4) { array.getDouble(it) } }

    /** The normal appearance content of the first widget of [name] in [file]. zh-CN: [file] 中 [name] 首个控件的正常外观内容. */
    private fun appearance(file: File, name: String): String = PDDocument.load(file).use { document ->
        val stream = document.documentCatalog.acroForm.getField(name).widgets.first().appearance.normalAppearance.appearanceStream
        String(stream.cosObject.createInputStream().use { it.readBytes() }, Charsets.ISO_8859_1)
    }

    private fun needAppearances(file: File): Boolean = PDDocument.load(file).use { it.documentCatalog.acroForm?.needAppearances == true }

    /** Page [index] of [file] rendered by `PdfRenderer` at 144 dpi. zh-CN: `PdfRenderer` 以 144 dpi 渲染的 [file] 第 [index] 页. */
    private fun render(file: File, index: Int): Rendering = read(file).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * SCALE, page.height * SCALE, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, Matrix().apply { setScale(SCALE.toFloat(), SCALE.toFloat()) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                Rendering(bitmap.width, pixels).also { bitmap.recycle() }
            }
        }
    }

    /** Dark pixels inside [rect] (display points). zh-CN: [rect] (显示点) 内的深色像素数. */
    private fun Rendering.ink(rect: DoubleArray): Int {
        val height = pixels.size / width
        var count = 0
        for (y in (rect[1] * SCALE).toInt().coerceIn(0, height) until (rect[3] * SCALE).toInt().coerceIn(0, height)) {
            for (x in (rect[0] * SCALE).toInt().coerceIn(0, width) until (rect[2] * SCALE).toInt().coerceIn(0, width)) {
                val pixel = pixels[y * width + x]
                if ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < DARK) count++
            }
        }
        return count
    }

    /** Pixels inside [rect] (display points) whose channels differ by more than [CHANNEL_TOLERANCE]. zh-CN: [rect] (显示点) 内通道差超过 [CHANNEL_TOLERANCE] 的像素数. */
    private fun differing(a: Rendering, b: Rendering, rect: DoubleArray): Int {
        assertEquals(a.pixels.size, b.pixels.size)
        val height = a.pixels.size / a.width
        var count = 0
        for (y in (rect[1] * SCALE).toInt().coerceIn(0, height) until (rect[3] * SCALE).toInt().coerceIn(0, height)) {
            for (x in (rect[0] * SCALE).toInt().coerceIn(0, a.width) until (rect[2] * SCALE).toInt().coerceIn(0, a.width)) {
                val p = a.pixels[y * a.width + x]
                val q = b.pixels[y * a.width + x]
                if (max(max(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q))), abs(Color.blue(p) - Color.blue(q))) > CHANNEL_TOLERANCE) count++
            }
        }
        return count
    }

    /** Every widget of [document]: field name, page index and rectangle. zh-CN: [document] 的每个控件: 字段名, 页索引与矩形. */
    private fun widgets(document: IPdfDocument): List<Triple<String, Int, DoubleArray>> = document.fields().values.flatMap { field ->
        val array = field.getJSONArray(PdfContract.FIELD_WIDGETS)
        List(array.length()) { index ->
            val widget = array.getJSONObject(index)
            Triple(field.getString(PdfContract.FIELD_NAME), widget.getInt(PdfContract.FIELD_PAGE) - 1, widget.rect())
        }
    }

    /**
     * One A4 page with the text field `name` ("Ada", `/MaxLen` 6): [signed] adds the signed signature field `author` (`/SigFlags` 3),
     * [xfa] an `/XFA` stream; a dynamic XFA form has no fields and sets `/NeedsRendering`.
     * zh-CN: 一页 A4, 有文本字段 `name` ("Ada", `/MaxLen` 为 6): [signed] 另加已签名的签名字段 `author` (`/SigFlags` 为 3), [xfa] 另加 `/XFA` 流; 动态
     * XFA 表单没有字段并设置 `/NeedsRendering`.
     */
    private fun buildForm(target: File, signed: Boolean, xfa: String?): File {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val form = PDAcroForm(document)
            document.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.HELV, PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 0 Tf 0 g"
            val annotations = ArrayList<PDAnnotation>()
            if (xfa != PdfContract.XFA_DYNAMIC) {
                val text = PDTextField(form).apply {
                    partialName = "name"
                    maxLen = 6
                }
                text.widgets.single().apply {
                    rectangle = PDRectangle(200f, 700f, 300f, 20f)
                    setPage(page)
                    annotations += this
                }
                form.fields.add(text)
                if (signed) {
                    val author = PDSignatureField(form).apply { partialName = "author" }
                    author.widgets.single().apply {
                        rectangle = PDRectangle(200f, 650f, 300f, 20f)
                        setPage(page)
                        annotations += this
                    }
                    val signature = PDSignature().apply {
                        setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                        setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                        setName("Tester")
                    }
                    author.cosObject.setItem(COSName.V, signature.cosObject)
                    form.fields.add(author)
                    form.cosObject.setInt(COSName.SIG_FLAGS, 3)
                }
                page.annotations = annotations
                text.value = "Ada"
            }
            if (xfa != null) {
                val render = if (xfa == PdfContract.XFA_DYNAMIC) "<acrobat><acrobat7><dynamicRender>required</dynamicRender></acrobat7></acrobat>" else ""
                val xml = "<xdp:xdp xmlns:xdp='http://ns.adobe.com/xdp/'><config xmlns='http://www.xfa.org/schema/xci/3.0/'>$render</config>" +
                    "<template xmlns='http://www.xfa.org/schema/xfa-template/3.3/'><subform name='form1'><field name='name'/></subform></template></xdp:xdp>"
                form.cosObject.setItem(COSName.XFA, PDStream(document, ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), COSName.FLATE_DECODE).cosObject)
                if (xfa == PdfContract.XFA_DYNAMIC) document.documentCatalog.cosObject.setBoolean(COSName.getPDFName("NeedsRendering"), true)
            }
            document.save(target)
        }
        return target
    }

    private val emoji = String(Character.toChars(0x1F600))

    /** Chinese, an emoji and Latin with a diacritic: the standard 14 fonts show none of it. zh-CN: 中文, 表情与带变音符的拉丁文: 标准 14 字体都显示不了. */
    private val chinese = "张三 $emoji Zo" + Char(0xEB)

    @Test
    fun everyTypeReadsBackAsFilled() = withPdfPlugin { plugin ->
        val source = sampleFile("form.pdf")
        val target = file("filled.pdf")
        val notes = "第一行 line one" + Char(0x0A) + "第二行 $emoji"
        val values = JSONObject()
            .put("name", chinese)
            .put("notes", notes)
            .put("agree", false)
            .put("size", "large")
            .put("country", "Japan")
            .put("colors", JSONArray().put("b").put("g"))
            .put("email", "ada@example.com")
            .put("pin", "4321")
            .put("address.city", "北京")
        val started = SystemClock.elapsedRealtime()
        val result = plugin.run(source, payload(values), target).completed()
        val elapsed = SystemClock.elapsedRealtime() - started
        assertEquals(9, result.getInt(PdfContract.KEY_FILLED))
        assertEquals(target.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
        assertEquals(2, result.getInt(PdfContract.KEY_PAGE_COUNT))
        assertFalse(result.containsKey(PdfContract.KEY_WARNINGS))
        plugin.opened(target) { document ->
            val info = document.info()
            assertEquals(11, info.getInt(PdfContract.KEY_FORM_FIELD_COUNT))
            assertFalse(info.containsKey(PdfContract.KEY_XFA))
            val fields = document.fields()
            assertEquals(11, fields.size)
            assertEquals(chinese, fields.getValue("name").getString(PdfContract.FIELD_VALUE))
            assertEquals(notes, fields.getValue("notes").getString(PdfContract.FIELD_VALUE))
            assertEquals(false, fields.getValue("agree").getBoolean(PdfContract.FIELD_VALUE))
            assertEquals("large", fields.getValue("size").getString(PdfContract.FIELD_VALUE))
            assertEquals("Japan", fields.getValue("country").getString(PdfContract.FIELD_VALUE))
            assertEquals(listOf("g", "b"), fields.getValue("colors").strings(PdfContract.FIELD_VALUE))
            assertEquals("ada@example.com", fields.getValue("email").getString(PdfContract.FIELD_VALUE))
            assertEquals("4321", fields.getValue("pin").getString(PdfContract.FIELD_VALUE))
            assertEquals("北京", fields.getValue("address.city").getString(PdfContract.FIELD_VALUE))
            assertEquals("P-001", fields.getValue("id").getString(PdfContract.FIELD_VALUE))
            // Paging: 11 fields, 5 from offset 10 are the last one. zh-CN: 分页: 共 11 个字段, 从偏移 10 起取 5 个只剩最后 1 个.
            val (first, firstAnswer) = document.page(limit = 5)
            assertEquals(5, first.size)
            assertTrue(firstAnswer.getBoolean(PdfContract.KEY_HAS_MORE))
            assertEquals(5, firstAnswer.getInt(PdfContract.KEY_NEXT_OFFSET))
            val (last, lastAnswer) = document.page(offset = 10, limit = 5)
            assertEquals(listOf(fields.keys.last()), last.map { it.getString(PdfContract.FIELD_NAME) })
            assertFalse(lastAnswer.getBoolean(PdfContract.KEY_HAS_MORE))
        }
        // The Canvas fallback drew name, notes and city; PDFBox drew the Latin values; readers keep them (roadmap Q242).
        // zh-CN: Canvas 回落绘制了 name, notes 与 city; 拉丁文字的值由 PDFBox 绘制; 阅读器沿用这些外观 (路线图 Q242).
        assertFalse(needAppearances(target))
        for (name in listOf("name", "notes", "address.city")) {
            val content = appearance(target, name)
            assertTrue("$name: $content", "/Tx BMC" in content && " Do" in content)
        }
        for ((name, shown) in listOf("country" to "Japan", "email" to "ada@example.com", "pin" to "****")) {
            val content = appearance(target, name)
            assertTrue("$name: $content", shown in content && " Do" !in content)
        }
        log("form.pdf: 9 values in $elapsed ms, ${source.length()} -> ${target.length()} bytes")
    }

    @Test
    fun latinValuesNeedNoFallback() = withPdfPlugin { plugin ->
        val source = sampleFile("form.pdf")
        val target = file("latin.pdf")
        val values = JSONObject().put("name", "Grace Hopper").put("agree", true).put("size", "small").put("colors", JSONArray().put("r"))
        assertEquals(4, plugin.run(source, payload(values), target).completed().getInt(PdfContract.KEY_FILLED))
        assertFalse(needAppearances(target))
        assertTrue("Grace Hopper" in appearance(target, "name"))
        plugin.opened(target) { document ->
            val fields = document.fields()
            assertEquals("Grace Hopper", fields.getValue("name").getString(PdfContract.FIELD_VALUE))
            assertEquals(true, fields.getValue("agree").getBoolean(PdfContract.FIELD_VALUE))
            assertEquals("small", fields.getValue("size").getString(PdfContract.FIELD_VALUE))
            assertEquals(listOf("r"), fields.getValue("colors").strings(PdfContract.FIELD_VALUE))
        }
    }

    @Test
    fun nullRestoresTheDefaultValue() = withPdfPlugin { plugin ->
        val source = sampleFile("form.pdf")
        val target = file("defaults.pdf")
        val values = JSONObject()
            .put("name", JSONObject.NULL)
            .put("size", JSONObject.NULL)
            .put("country", JSONObject.NULL)
            .put("colors", JSONObject.NULL)
            .put("address.city", JSONObject.NULL)
        assertEquals(5, plugin.run(source, payload(values), target).completed().getInt(PdfContract.KEY_FILLED))
        plugin.opened(target) { document ->
            val fields = document.fields()
            assertEquals("Ada", fields.getValue("name").getString(PdfContract.FIELD_VALUE))
            assertEquals("small", fields.getValue("size").getString(PdfContract.FIELD_VALUE))
            assertEquals("China", fields.getValue("country").getString(PdfContract.FIELD_VALUE))
            assertEquals(listOf("g"), fields.getValue("colors").strings(PdfContract.FIELD_VALUE))
            assertTrue(fields.getValue("address.city").isNull(PdfContract.FIELD_VALUE))
        }
    }

    @Test
    fun flattenedValuesRenderAndLeaveNoField() = withPdfPlugin { plugin ->
        val source = sampleFile("form.pdf")
        val filled = file("filled.pdf")
        val flat = file("flat.pdf")
        val blank = file("blank-flat.pdf")
        val named = file("named-flat.pdf")
        val values = JSONObject().put("name", chinese).put("address.city", "北京市海淀区")
        plugin.run(source, payload(values), filled).completed()
        val started = SystemClock.elapsedRealtime()
        assertEquals(2, plugin.run(source, payload(values, flatten = true), flat).completed().getInt(PdfContract.KEY_FILLED))
        val elapsed = SystemClock.elapsedRealtime() - started
        plugin.run(source, payload(JSONObject().put("name", "").put("address.city", ""), flatten = true), blank).completed()
        plugin.run(source, payload(values, flatten = JSONArray().put("name").put("address.city")), named).completed()
        plugin.opened(flat) { document ->
            assertEquals(0, document.info().getInt(PdfContract.KEY_FORM_FIELD_COUNT))
            assertEquals(0, document.fields().size)
        }
        plugin.opened(named) { document ->
            assertEquals(9, document.info().getInt(PdfContract.KEY_FORM_FIELD_COUNT))
            val names = document.fields().keys
            assertFalse("name" in names || "address.city" in names)
        }
        val (nameRect, cityRect) = plugin.opened(filled) { document -> document.fields().let { it.getValue("name").rect() to it.getValue("address.city").rect() } }
        // Every API level: the flattened Chinese values are page content. zh-CN: 全部 API 级别: 扁平化后的中文值成为页面内容.
        val flatName = render(flat, 0).ink(nameRect)
        val flatCity = render(flat, 1).ink(cityRect)
        val blankName = render(blank, 0).ink(nameRect)
        val blankCity = render(blank, 1).ink(cityRect)
        assertTrue("name: $flatName dark pixels flattened, $blankName when empty", flatName >= blankName + MIN_INK)
        assertTrue("city: $flatCity dark pixels flattened, $blankCity when empty", flatCity >= blankCity + MIN_INK)
        val filledName = render(filled, 0).ink(nameRect)
        var comparison = "name ink $filledName in the filled copy (the system PdfRenderer draws no widget below API 35, roadmap Q241)"
        if (Build.VERSION.SDK_INT >= 35) {
            // The filled copy draws its widgets as the flattened copy draws the page; spike S1 probes a copy with
            // /NeedAppearances set, which the renderer redraws with its own appearances (roadmap Q242).
            // zh-CN: 填写后的副本绘制控件, 与扁平化副本绘制页面内容一致; spike S1 探测置上 /NeedAppearances 的副本, 渲染器会按自己的外观
            // 重画 (路线图 Q242).
            val needing = file("filled-need-appearances.pdf")
            PDDocument.load(filled).use { document ->
                document.documentCatalog.acroForm.needAppearances = true
                document.save(needing)
            }
            val renderings = listOf(filled, flat, needing).map { file -> List(2) { render(file, it) } }
            val rows = plugin.opened(filled) { widgets(it) }.map { (name, page, rect) ->
                val (a, b, c) = renderings.map { it[page] }
                val area = ((rect[2] - rect[0]) * (rect[3] - rect[1]) * SCALE * SCALE).toInt()
                val differing = differing(a, b, rect)
                assertTrue("$name: $differing of $area pixels differ from the flattened copy", differing <= area * MAX_DIFFERING)
                "$name $differing/${differing(a, c, rect)} ink ${a.ink(rect)}/${b.ink(rect)}/${c.ink(rect)}"
            }
            comparison = "name ink $filledName filled; widget differing pixels filled vs flattened / vs /NeedAppearances set, ink filled / flattened / NeedAppearances set: $rows"
        }
        log("flatten: 2 values in $elapsed ms, name ink $flatName (empty $blankName), city ink $flatCity (empty $blankCity); $comparison")
    }

    @Test
    fun unknownNamesFailUnlessIgnored() = withPdfPlugin { plugin ->
        val source = sampleFile("form.pdf")
        val target = file("unknown.pdf")
        val values = JSONObject().put("name", "Ada")
        repeat(7) { values.put("missing$it", "x") }
        val (code, message) = plugin.run(source, payload(values), target).failure()
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, code)
        assertTrue(message, "7 field names match no form field" in message && "'missing4'" in message && "'missing5'" !in message && "and 2 more" in message)
        val result = plugin.run(source, payload(values), target, ignoreMissing = true).completed()
        assertEquals(1, result.getInt(PdfContract.KEY_FILLED))
        val warnings = result.getStringArray(PdfContract.KEY_WARNINGS)!!.toList()
        assertEquals(warnings.toString(), 1, warnings.size)
        assertTrue(warnings.single(), "were ignored" in warnings.single())
        plugin.opened(target) { document -> assertEquals("Ada", document.fields().getValue("name").getString(PdfContract.FIELD_VALUE)) }
    }

    @Test
    fun readOnlyButtonsSignaturesAndMismatchesFail() = withPdfPlugin { plugin ->
        val source = sampleFile("form.pdf")
        val target = file("rejected.pdf")
        val signed = buildForm(file("signed.pdf"), signed = true, xfa = null)
        val cases = listOf(
            Triple(source, JSONObject().put("id", "P-002"), "is read-only"),
            Triple(source, JSONObject().put("submit", "go"), "is a push button"),
            Triple(signed, JSONObject().put("author", "Ada"), "is a signature field"),
            Triple(source, JSONObject().put("agree", "yes"), "must be a boolean or null"),
            Triple(source, JSONObject().put("size", "huge"), "must be one of the options"),
            Triple(source, JSONObject().put("country", JSONArray().put("China").put("Japan")), "must be a string or null"),
            Triple(source, JSONObject().put("colors", JSONArray().put("r").put("x")), "must be one of the options"),
            Triple(signed, JSONObject().put("name", "1234567"), "more than the field's maximum length 6"),
            Triple(source, JSONObject().put("name", 42), "must be a string or null"),
        )
        for ((input, values, expected) in cases) {
            val (code, message) = plugin.run(input, payload(values), target).failure()
            assertEquals(message, PdfErrorCodes.INVALID_ARGUMENT, code)
            assertTrue("$values: $message", expected in message)
        }
    }

    @Test
    fun xfaFormsAndSignatures() = withPdfPlugin { plugin ->
        val dynamic = buildForm(file("dynamic.pdf"), signed = false, xfa = PdfContract.XFA_DYNAMIC)
        plugin.opened(dynamic) { document ->
            val info = document.info()
            assertEquals(PdfContract.XFA_DYNAMIC, info.getString(PdfContract.KEY_XFA))
            assertEquals(0, info.getInt(PdfContract.KEY_FORM_FIELD_COUNT))
        }
        val target = file("xfa-filled.pdf")
        assertEquals(PdfErrorCodes.UNSUPPORTED, plugin.run(dynamic, payload(JSONObject()), target).failure().first)

        val static = buildForm(file("static.pdf"), signed = true, xfa = PdfContract.XFA_STATIC)
        plugin.opened(static) { document ->
            val info = document.info()
            assertEquals(PdfContract.XFA_STATIC, info.getString(PdfContract.KEY_XFA))
            assertEquals(2, info.getInt(PdfContract.KEY_FORM_FIELD_COUNT))
        }
        val result = plugin.run(static, payload(JSONObject().put("name", "Grace")), target).completed()
        val warnings = result.getStringArray(PdfContract.KEY_WARNINGS)!!.toList()
        assertEquals(warnings.toString(), 2, warnings.size)
        assertTrue(warnings.toString(), warnings.any { "signed signature field" in it } && warnings.any { "XFA" in it })
        plugin.opened(target) { document ->
            assertFalse(document.info().containsKey(PdfContract.KEY_XFA))
            val fields = document.fields()
            assertEquals("Grace", fields.getValue("name").getString(PdfContract.FIELD_VALUE))
            assertTrue(fields.getValue("author").getBoolean(PdfContract.FIELD_SIGNED))
        }
        log("XFA: dynamic UNSUPPORTED, static removed with ${warnings.size} warnings")
    }

    companion object {

        private const val TAG = "PaperForms"

        private const val SCALE = 2

        /** Average channel below which a pixel counts as ink. zh-CN: 平均通道值低于此值的像素算作墨迹. */
        private const val DARK = 160

        /** Dark pixels a drawn value adds to its widget area at 144 dpi. zh-CN: 144 dpi 下绘制的值至少为控件区域增加的深色像素数. */
        private const val MIN_INK = 40

        private const val CHANNEL_TOLERANCE = 64

        /** Share of a widget's pixels the filled and the flattened rendering may differ in. zh-CN: 填写与扁平化两种渲染在一个控件内允许不同的像素占比. */
        private const val MAX_DIFFERING = 0.005

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

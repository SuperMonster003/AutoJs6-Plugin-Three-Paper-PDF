package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PaperPrintCallbacks
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.autojs.plugin.pdf.api.PdfWarningCodes
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * `fromHtml` (roadmap D8, appendix D Q5 (a)): an off-screen WebView created on the main thread loads the HTML, then its
 * print adapter lays it out on pages of `pageSize` with `margin` and writes the PDF to a file of the run directory,
 * through the callbacks of [PaperPrintCallbacks]; PDFBox then sets `title` (the title of the page), `producer` and the
 * dates. JavaScript runs unless `javaScript` is false (appendix D Q35 (c), Q61); nothing remote loads (`blockNetworkLoads`, and every http / https / ftp / ws request is
 * answered empty and counted in `warnings`), file and content URLs are off, and navigation away from the page is
 * refused. `timeout` (milliseconds) covers the whole conversion; without it the operation waits until the page has
 * loaded or the operation is cancelled (appendix D Q7). Whatever the device refuses on the way (no WebView, hidden API
 * policy, a failed layout or write) ends with `UNSUPPORTED`.
 * zh-CN: `fromHtml` (路线图 D8, 附录 D Q5 (a)): 在主线程创建的离屏 WebView 载入 HTML, 然后由其打印适配器按 `pageSize` 与 `margin`
 * 分页排版, 经 [PaperPrintCallbacks] 的回调把 PDF 写到 run 目录的文件; 之后由 PDFBox 写入 `title` (页面标题), `producer` 与日期.
 * 除非 `javaScript` 为 false, 否则执行 JavaScript (附录 D Q35 (c), Q61); 不加载任何远程内容 (`blockNetworkLoads`, 且每个 http / https / ftp / ws 请求都以空内容应答并计入
 * `warnings`), 关闭 file 与 content URL, 拒绝离开该页面的导航. `timeout` (毫秒) 覆盖整个转换; 未给出时一直等到页面载入完成或操作
 * 被取消 (附录 D Q7). 途中被设备拒绝的情况 (没有 WebView, 隐藏 API 策略, 排版或写出失败) 都以 `UNSUPPORTED` 结束.
 */
internal class HtmlToPdf(private val context: Context) {

    /** A checked `fromHtml` request. zh-CN: 已检查的 `fromHtml` 请求. */
    class Request(
        val page: PageLayout.PageSize.Fixed,
        val margin: Float,
        val baseUrl: String?,
        val timeoutMs: Long?,
        val warnings: List<PdfWarning>,
        val javaScript: Boolean = PdfContract.DEFAULT_JAVA_SCRIPT,
    )

    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        val deadline = request.timeoutMs?.let { System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(it) }
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val html = TextToPdf.normalize(String(InputBytes.read(inputs.single(), PdfContract.MAX_TEXT_INPUT_BYTES, "the HTML"), Charsets.UTF_8))
        val printed = File(directory, "html.pdf")
        val conversion = Conversion(request, html, printed)
        main.post(conversion::start)
        try {
            while (!conversion.done.await(WAIT_STEP_MS, TimeUnit.MILLISECONDS)) {
                operation.stop.checkStop()
                if (deadline != null && System.nanoTime() - deadline > 0) {
                    throw PdfFailure(PdfErrorCodes.TIMEOUT, "fromHtml did not finish within ${request.timeoutMs} ms (${conversion.stage.get()})")
                }
                if (conversion.loaded.get()) operation.progress(PdfContract.STAGE_PROCESSING, 0, 1)
            }
        } finally {
            main.post(conversion::release)
        }
        conversion.failure.get()?.let { throw it }
        operation.stop.checkStop()
        val warnings = ArrayList(request.warnings)
        blockedWarning(conversion.blocked.get())?.let { warnings += it }
        PDDocument.load(printed, MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory)).use { document ->
            return GeneratedDocuments.write(document, conversion.title.get(), operation, warnings, request.page)
        }
    }

    /**
     * One conversion; everything but the fields read after [done] runs on the main thread.
     * zh-CN: 一次转换; 除 [done] 之后读取的字段外都在主线程上运行.
     */
    private inner class Conversion(private val request: Request, private val html: String, private val target: File) {

        val done = CountDownLatch(1)
        val failure = AtomicReference<PdfFailure?>()
        val title = AtomicReference<String?>()
        val blocked = AtomicInteger()
        val loaded = java.util.concurrent.atomic.AtomicBoolean(false)
        val stage = AtomicReference("loading")

        private val cancellation = CancellationSignal()
        private var webView: WebView? = null
        private var adapter: PrintDocumentAdapter? = null
        private var output: ParcelFileDescriptor? = null
        private var printing = false

        @SuppressLint("SetJavaScriptEnabled")
        fun start() {
            if (done.count == 0L) return
            try {
                val view = WebView(context)
                webView = view
                view.settings.apply {
                    javaScriptEnabled = request.javaScript
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                }
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url.scheme?.lowercase() ?: return null
                        if (scheme !in REMOTE_SCHEMES) return null
                        blocked.incrementAndGet()
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (printing || done.count == 0L) return
                        printing = true
                        title.set(view.title?.takeIf { it.isNotBlank() && it != url && it != "about:blank" })
                        loaded.set(true)
                        print(view)
                    }
                }
                val widthPixels = (request.page.width * VIEW_DPI / POINTS_PER_INCH).roundToInt()
                val heightPixels = (request.page.height * VIEW_DPI / POINTS_PER_INCH).roundToInt()
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, widthPixels, heightPixels)
                view.loadDataWithBaseURL(request.baseUrl, html, "text/html", "utf-8", null)
            } catch (e: Throwable) {
                fail(unsupported("cannot create the WebView", e))
            }
        }

        private fun print(view: WebView) {
            try {
                stage.set("layout")
                val printAdapter = view.createPrintDocumentAdapter("fromHtml")
                adapter = printAdapter
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize(MEDIA_ID, MEDIA_ID, mils(request.page.width), mils(request.page.height)))
                    .setResolution(PrintAttributes.Resolution(MEDIA_ID, MEDIA_ID, PRINT_DPI, PRINT_DPI))
                    .setMinMargins(mils(request.margin).let { PrintAttributes.Margins(it, it, it, it) })
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()
                printAdapter.onStart()
                printAdapter.onLayout(null, attributes, cancellation, PaperPrintCallbacks.layout(LayoutListener(printAdapter)), Bundle())
            } catch (e: Throwable) {
                fail(unsupported("the print adapter refused the layout", e))
            }
        }

        private inner class LayoutListener(private val printAdapter: PrintDocumentAdapter) : PaperPrintCallbacks.LayoutListener {
            override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                if (done.count == 0L) return
                try {
                    stage.set("write")
                    val descriptor = ParcelFileDescriptor.open(
                        target,
                        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                    )
                    output = descriptor
                    printAdapter.onWrite(arrayOf(PageRange.ALL_PAGES), descriptor, cancellation, PaperPrintCallbacks.write(WriteListener()))
                } catch (e: Throwable) {
                    fail(unsupported("the print adapter refused the write", e))
                }
            }

            override fun onLayoutFailed(error: CharSequence?) = fail(unsupported("the print layout failed: $error", null))

            override fun onLayoutCancelled() = fail(PdfFailure(PdfErrorCodes.CANCELLED, "the print layout was cancelled"))
        }

        private inner class WriteListener : PaperPrintCallbacks.WriteListener {
            override fun onWriteFinished(pages: Array<out PageRange>) {
                closeOutput()
                done.countDown()
            }

            override fun onWriteFailed(error: CharSequence?) = fail(unsupported("the print write failed: $error", null))

            override fun onWriteCancelled() = fail(PdfFailure(PdfErrorCodes.CANCELLED, "the print write was cancelled"))
        }

        private fun fail(error: PdfFailure) {
            if (done.count == 0L) return
            failure.compareAndSet(null, error)
            closeOutput()
            done.countDown()
        }

        private fun closeOutput() {
            output?.let { runCatching { it.close() } }
            output = null
        }

        /** Ends whatever is still running and destroys the WebView. zh-CN: 结束仍在运行的部分并销毁 WebView. */
        fun release() {
            if (done.count != 0L) {
                failure.compareAndSet(null, PdfFailure(PdfErrorCodes.CANCELLED, "fromHtml was stopped"))
                done.countDown()
            }
            runCatching { cancellation.cancel() }
            closeOutput()
            adapter?.let { runCatching { it.onFinish() } }
            adapter = null
            webView?.let { view ->
                runCatching { view.stopLoading() }
                runCatching { view.destroy() }
            }
            webView = null
        }
    }

    companion object {

        private val main by lazy { Handler(Looper.getMainLooper()) }

        private const val WAIT_STEP_MS = 100L
        private const val MEDIA_ID = "paper"
        private const val PRINT_DPI = 300
        private const val VIEW_DPI = 160f
        private const val POINTS_PER_INCH = 72f
        private val REMOTE_SCHEMES = setOf("http", "https", "ftp", "ws", "wss")

        fun request(options: PdfOptions, inputCount: Int): Request {
            if (inputCount != 1) throw PdfFailure.invalid("${PdfOps.FROM_HTML} takes one HTML input, got $inputCount")
            if (!isAvailable) throw PdfFailure.unsupported("${PdfContract.FEATURE_GENERATE_HTML} is not available on this device")
            val warnings = ArrayList<PdfWarning>()
            val page = PageLayout.documentPageSize(options, PdfOps.FROM_HTML, warnings)
            val margin = PageLayout.margin(options, PageLayout.DEFAULT_DOCUMENT_MARGIN)
            PageLayout.contentBox(page, margin, minimum = MIN_CONTENT_POINTS)
            val timeout = options.long(PdfContract.KEY_TIMEOUT)
            if (timeout != null && timeout <= 0) throw PdfFailure.invalid("${PdfContract.KEY_TIMEOUT} must be greater than 0, got $timeout")
            val baseUrl = options.string(PdfContract.KEY_BASE_URL)?.takeIf { it.isNotEmpty() }
            return Request(page, margin, baseUrl, timeout, warnings, options.boolean(PdfContract.KEY_JAVA_SCRIPT, PdfContract.DEFAULT_JAVA_SCRIPT))
        }

        /** Smallest box inside the margins the print adapter gets. zh-CN: 交给打印适配器的页边距以内最小方框. */
        private const val MIN_CONTENT_POINTS = 72f

        /**
         * Whether `generate.html` is declared: the hidden callback constructors are reachable and, from API 26, a WebView
         * provider is installed (API 24 / 25 find out when the WebView is created). `WebViewCompat` is not needed: the
         * platform call runs from API 26 only.
         * zh-CN: 是否声明 `generate.html`: 隐藏的回调构造器可访问, 且自 API 26 起已安装 WebView 提供方 (API 24 / 25 在创建 WebView
         * 时才知道). 不需要 `WebViewCompat`: 平台调用只在 API 26 起执行.
         */
        val isAvailable: Boolean
            @SuppressLint("WebViewApiAvailability")
            get() = PaperPrintCallbacks.isAvailable && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false))

        /**
         * The warning that [blocked] remote resources were not loaded, or null when there were none.
         * zh-CN: 有 [blocked] 项远程资源没有加载时的警告, 没有时为 null.
         */
        internal fun blockedWarning(blocked: Int): PdfWarning? = if (blocked <= 0) null else PdfWarning(
            "$blocked remote resource${if (blocked == 1) " was" else "s were"} not loaded",
            PdfWarningCodes.REMOTE_RESOURCES_BLOCKED,
            mapOf(PdfWarningCodes.ARG_COUNT to blocked),
        )

        private fun mils(points: Float): Int = (points * 1000f / POINTS_PER_INCH).roundToInt()

        private fun unsupported(detail: String, cause: Throwable?): PdfFailure =
            PdfFailure.unsupported(if (cause == null) detail else "$detail: ${cause.javaClass.simpleName}: ${cause.message}")
    }

}

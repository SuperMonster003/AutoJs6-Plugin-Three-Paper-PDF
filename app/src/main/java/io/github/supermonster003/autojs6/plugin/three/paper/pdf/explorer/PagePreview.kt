package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp.StampItems
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfContract
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The single-page preview of the stamping tools (roadmap P6.12, E4 feedback F4 / F5): the previewed page is cut out once
 * (an organize run keeping that page), each new set of options is stamped on it through the same runner and `stamp`
 * request the tool sends, and the result is drawn by the viewer's document ([ViewerDocument]). A page outside the chosen
 * range is drawn as it is. Requests wait [DELAY_MS] for the next change and a newer one replaces an older one. The
 * cut-out has one page, so the placeholders that count pages are written out before stamping as the whole document
 * would show them on that page ([stampFor]; appendix D Q263, Q211, Q262).
 * zh-CN: 盖章类工具的单页预览 (路线图 P6.12, E4 反馈 F4 / F5): 预览页只切出一次 (只保留该页的整理操作), 每组新选项都经工具所用的同一个
 * 执行器与 `stamp` 请求盖到这一页上, 结果由查看器的文档 ([ViewerDocument]) 绘制. 不在所选范围内的页按原样绘制. 请求会等待 [DELAY_MS] 以合并
 * 后续修改, 新请求取代旧请求. 切出的文件只有一页, 所以盖章前把计页的占位符写成整个文档在该页上显示的值 ([stampFor]; 附录 D Q263, Q211,
 * Q262).
 */
internal class PagePreview(
    context: Context,
    private val plugin: IPdfPlugin,
    private val source: ToolTarget,
    private val password: String?,
    private val pageCount: Int,
) : AutoCloseable {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "3-paper-preview").apply { isDaemon = true }
    }
    private val directory = File(app.cacheDir, "toolbox/preview/${System.nanoTime()}")
    private val token = Any()

    /** Cut-out pages by page number (executor thread). zh-CN: 按页码记录的切出页 (执行线程). */
    private val cut = HashMap<Int, File>()

    /** The newest request (main thread). zh-CN: 最新的请求 (主线程). */
    private var generation = 0

    @Volatile
    private var latest = 0

    @Volatile
    private var running: PdfToolRunner? = null

    @Volatile
    private var closed = false

    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    /** The page the preview shows (from 1). zh-CN: 预览显示的页 (从 1 起). */
    var page by mutableIntStateOf(0)
        private set

    /** Whether the page shown carries the stamp; false for a page drawn as it is. zh-CN: 显示的页是否带有盖章; 按原样绘制的页为 false. */
    var stamped by mutableStateOf(false)
        private set

    /**
     * Previews [options] (a watermark or page numbers) on [page] (from 1); null draws the page as it is (outside the
     * range, or nothing to stamp yet).
     * zh-CN: 在第 [page] 页 (从 1 起) 预览 [options] (水印或页码); 为 null 时按原样绘制该页 (不在范围内, 或尚无可盖的内容).
     */
    fun request(options: ToolOptions?, page: Int) {
        if (closed) return
        val ticket = ++generation
        latest = ticket
        main.removeCallbacksAndMessages(token)
        running?.cancel()
        busy = true
        val stamp = options?.let { stampFor(it, page, pageCount) }
        main.postAtTime({ if (!closed) executor.execute { draw(ticket, stamp, page) } }, token, SystemClock.uptimeMillis() + DELAY_MS)
    }

    override fun close() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(token)
        running?.cancel()
        bitmap = null
        executor.execute { directory.deleteRecursively() }
        executor.shutdown()
    }

    private fun draw(ticket: Int, stamp: ToolOptions?, page: Int) {
        if (closed || ticket != latest) return
        val drawn = try {
            val one = cut[page] ?: cutOut(page).also { cut[page] = it }
            if (closed || ticket != latest) return
            if (stamp == null) {
                render(one)
            } else {
                val stamped = run(File(directory, "stamp-$ticket"), ToolInput(ToolTarget(Uri.fromFile(one), source.displayName), null), stamp)
                try {
                    if (closed || ticket != latest) return
                    render(stamped)
                } finally {
                    stamped.parentFile?.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            if (ticket != latest) return
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        main.post {
            if (closed || ticket != generation) {
                drawn?.recycle()
                return@post
            }
            busy = false
            failed = drawn == null
            if (drawn != null) {
                bitmap = drawn.asImageBitmap()
                this.page = page
                this.stamped = stamp != null
            }
        }
    }

    /** A one-page copy of [page], readable without a password. zh-CN: 第 [page] 页的单页副本, 无需密码即可读取. */
    private fun cutOut(page: Int): File =
        run(File(directory, "page-$page"), ToolInput(source, password), ToolOptions.Organize(listOf(OrganizedPage(page, 0))))

    private fun run(folder: File, input: ToolInput, options: ToolOptions): File {
        folder.mkdirs()
        val runner = PdfToolRunner(plugin, ::open, LocalFolderOutput(folder), workDir = File(directory, "work"))
        running = runner
        try {
            return File(runner.run(listOf(input), options).displayPath)
        } finally {
            running = null
        }
    }

    private fun render(file: File): Bitmap? {
        // The engine keeps its scratch file in this folder and refuses one that does not exist (PDFBox ScratchFile).
        // zh-CN: 引擎把暂存文件放在这个文件夹, 文件夹不存在时拒绝打开 (PDFBox ScratchFile).
        val scratch = File(directory, "render").apply { mkdirs() }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val document = ViewerDocument.open(descriptor, null, scratch)
            try {
                val frame = document.pages.firstOrNull() ?: return null
                val height = (WIDTH_PX * frame.height / frame.width).roundToInt().coerceIn(1, WIDTH_PX * MAX_ASPECT)
                return createBitmap(WIDTH_PX, height).also {
                    it.eraseColor(Color.WHITE)
                    document.render(0, it)
                }
            } finally {
                runCatching { document.close() }
            }
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun open(uri: Uri): ParcelFileDescriptor =
        app.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())

    companion object {

        /** Waiting for the next change before drawing. zh-CN: 绘制前等待下一次修改的时间. */
        private const val DELAY_MS = 500L

        /** The width of the drawn page in pixels. zh-CN: 绘制页面的宽度 (像素). */
        const val WIDTH_PX = 720

        private const val MAX_ASPECT = 3

        /**
         * [options] for the one-page cut-out of [page] in a document of [total] pages: the same stamp the tool puts on
         * [page], with `{page}` and `{total}` written out in one pass as the engine would ([StampItems.expand]) and the
         * range dropped. `{date}` and `{name}` stay for the engine, which gets the source's name. A watermark counts
         * from the first page of the file (appendix D Q263); the page number counts from its start number through the
         * `pageOffset` the tool sends ([ToolPayloads.pageOffset], appendix D Q211), headers and footers from the first
         * page of the file (appendix D Q262). The start goes back to 1 without a range, so the cut-out gets no offset.
         * zh-CN: 适用于 [total] 页文档中第 [page] 页单页切出文件的 [options]: 与工具盖在 [page] 上的内容相同, `{page}` 与 `{total}` 按引擎的方式
         * 单遍写出 ([StampItems.expand]), 去掉页码范围. `{date}` 与 `{name}` 留给引擎处理, 引擎会拿到源文件的名称. 水印从文件第一页开始计数
         * (附录 D Q263); 页码经工具发送的 `pageOffset` 从起始数字开始计数 ([ToolPayloads.pageOffset], 附录 D Q211), 页眉与页脚从文件第一页
         * 开始计数 (附录 D Q262). 起始数字改回 1 且不带范围, 因此切出文件没有偏移.
         */
        fun stampFor(options: ToolOptions, page: Int, total: Int): ToolOptions = when (options) {
            is ToolOptions.Watermark ->
                if (options.type == PdfContract.STAMP_TEXT) options.copy(text = counted(options.text, page, total, 0L), pages = null) else options.copy(pages = null)
            is ToolOptions.PageNumbers -> {
                val offset = ToolPayloads.pageOffset(options.start, options.pages)?.toLong() ?: 0L
                options.copy(
                    format = options.format?.let { counted(it, page, total, offset) },
                    header = slots(options.header, page, total),
                    footer = slots(options.footer, page, total),
                    start = 1,
                    pages = null,
                )
            }
            else -> throw IllegalArgumentException("no preview for ${options.javaClass.simpleName}")
        }

        private fun slots(slots: ToolOptions.Slots, page: Int, total: Int): ToolOptions.Slots =
            ToolOptions.Slots(counted(slots.left, page, total, 0L), counted(slots.center, page, total, 0L), counted(slots.right, page, total, 0L))

        /** [text] with only the page placeholders written out. zh-CN: 只写出计页占位符的 [text]. */
        private fun counted(text: String, page: Int, total: Int, offset: Long): String =
            StampItems.expand(text, page, total, PdfContract.PLACEHOLDER_DATE, PdfContract.PLACEHOLDER_NAME, offset)

    }

}

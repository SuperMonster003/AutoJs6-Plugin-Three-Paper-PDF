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
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfContract
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The single-page preview of the watermark tool (roadmap P6.12): the previewed page is cut out once (an organize run
 * keeping that page), each new set of options is stamped on it by the same `stamp` request the tool sends, and the
 * result is drawn by the viewer's document ([ViewerDocument]). Requests wait [DELAY_MS] for the next change and a newer
 * one replaces an older one. The cut-out has one page, so `{page}` and `{total}` are written as the previewed page and
 * [pageCount] before stamping (appendix D Q263).
 * zh-CN: 水印工具的单页预览 (路线图 P6.12): 预览页只切出一次 (只保留该页的整理操作), 每组新选项都用工具发送的同一个 `stamp` 请求盖到这一页上,
 * 结果由查看器的文档 ([ViewerDocument]) 绘制. 请求会等待 [DELAY_MS] 以合并后续修改, 新请求取代旧请求. 切出的文件只有一页, 所以盖章前
 * 把 `{page}` 与 `{total}` 写成预览页的页码与 [pageCount] (附录 D Q263).
 */
internal class WatermarkPreview(
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

    /**
     * Previews [options] on [page] (from 1); null options (no text or image yet) keep the last preview.
     * zh-CN: 在第 [page] 页 (从 1 起) 预览 [options]; options 为 null (尚无文字或图片) 时保留上一次的预览.
     */
    fun request(options: ToolOptions.Watermark?, page: Int) {
        if (closed) return
        val ticket = ++generation
        latest = ticket
        main.removeCallbacksAndMessages(token)
        running?.cancel()
        if (options == null) {
            busy = false
            return
        }
        busy = true
        main.postAtTime({ if (!closed) executor.execute { draw(ticket, options.copy(pages = null), page) } }, token, SystemClock.uptimeMillis() + DELAY_MS)
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

    private fun draw(ticket: Int, options: ToolOptions.Watermark, page: Int) {
        if (closed || ticket != latest) return
        val drawn = try {
            val one = cut[page] ?: cutOut(page).also { cut[page] = it }
            if (closed || ticket != latest) return
            val stamped = run(File(directory, "stamp-$ticket"), ToolInput(ToolTarget(Uri.fromFile(one), source.displayName), null), numbered(options, page))
            try {
                if (closed || ticket != latest) return
                render(stamped)
            } finally {
                stamped.parentFile?.deleteRecursively()
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
            }
        }
    }

    /**
     * [options] with `{page}` and `{total}` written out, as the whole document would show them on [page].
     * zh-CN: 把 `{page}` 与 `{total}` 写成整个文档在第 [page] 页上显示的值后的 [options].
     */
    private fun numbered(options: ToolOptions.Watermark, page: Int): ToolOptions.Watermark =
        if (options.type != PdfContract.STAMP_TEXT) options
        else options.copy(text = options.text.replace(PdfContract.PLACEHOLDER_PAGE, page.toString()).replace(PdfContract.PLACEHOLDER_TOTAL, pageCount.toString()))

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
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val document = ViewerDocument.open(descriptor, null, File(directory, "render"))
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

    private companion object {

        /** Waiting for the next change before drawing. zh-CN: 绘制前等待下一次修改的时间. */
        const val DELAY_MS = 500L

        const val WIDTH_PX = 720

        const val MAX_ASPECT = 3

    }

}

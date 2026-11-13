package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerDocument
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Page thumbnails of the organize tool (roadmap P4.2), drawn by the viewer's document ([ViewerDocument]) on one
 * background thread. At most [MAX_CACHED] bitmaps are kept; a page scrolled away is drawn again when it comes back.
 * A document the viewer cannot open just has no thumbnails ([failed]).
 * zh-CN: 页面整理的缩略图 (路线图 P4.2), 由查看器的文档 ([ViewerDocument]) 在一个后台线程上绘制. 至多保留 [MAX_CACHED] 张位图;
 * 滚出的页面回来时重新绘制. 查看器无法打开的文档只是没有缩略图 ([failed]).
 */
internal class PageThumbnails(context: Context, private val uri: Uri, private val password: String?) : AutoCloseable {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "3-paper-thumbs").apply { isDaemon = true }
    }
    private val directory = File(app.cacheDir, "toolbox/thumbs/${System.nanoTime()}")

    private val bitmaps = mutableStateMapOf<Int, ImageBitmap>()

    /** Pages drawn or waiting, oldest first (main thread). zh-CN: 已绘制或等待中的页面, 最早的在前 (主线程). */
    private val order = LinkedHashSet<Int>()

    private var descriptor: ParcelFileDescriptor? = null
    private var document: ViewerDocument? = null

    @Volatile
    private var closed = false

    var failed by mutableStateOf(false)
        private set

    init {
        executor.execute {
            try {
                directory.mkdirs()
                val source = app.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("no descriptor")
                descriptor = source
                document = ViewerDocument.open(source, password, directory)
            } catch (e: Exception) {
                main.post { failed = true }
            }
        }
    }

    /** The thumbnail of [page] (from 1), or null while it is drawn. zh-CN: 第 [page] 页 (从 1 起) 的缩略图; 绘制期间为 null. */
    fun bitmap(page: Int): ImageBitmap? = bitmaps[page]

    /** Asks for [page] (from 1); call it from an effect of the page's item. zh-CN: 请求第 [page] 页 (从 1 起); 在该页条目的副作用中调用. */
    fun request(page: Int) {
        if (closed || !order.add(page)) return
        while (order.size > MAX_CACHED) {
            val eldest = order.first()
            order.remove(eldest)
            bitmaps.remove(eldest)
        }
        executor.execute {
            val drawn = draw(page) ?: return@execute
            main.post {
                if (!closed && page in order) bitmaps[page] = drawn.asImageBitmap() else drawn.recycle()
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        bitmaps.clear()
        order.clear()
        executor.execute {
            runCatching { document?.close() }
            runCatching { descriptor?.close() }
            document = null
            descriptor = null
            directory.deleteRecursively()
        }
        executor.shutdown()
    }

    private fun draw(page: Int): Bitmap? {
        if (closed) return null
        val opened = document ?: return null
        val frame = opened.pages.getOrNull(page - 1) ?: return null
        val width = WIDTH_PX
        val height = (width * frame.height / frame.width).roundToInt().coerceIn(1, width * MAX_ASPECT)
        return try {
            createBitmap(width, height).also {
                it.eraseColor(Color.WHITE)
                opened.render(page - 1, it)
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {

        const val WIDTH_PX = 180

        const val MAX_ASPECT = 3

        const val MAX_CACHED = 90

    }

}

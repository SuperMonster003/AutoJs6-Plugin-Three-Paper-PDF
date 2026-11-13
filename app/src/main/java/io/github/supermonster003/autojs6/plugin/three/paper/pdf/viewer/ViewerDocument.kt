package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.LoadedPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.ImageBounds
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.SearchReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.PageRenderer
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.json.JSONArray
import java.io.File

/**
 * One page as the viewer shows it: the displayed size in points (crop box after rotation) and whether its tiles can be
 * drawn with a transform on this device ([PageRenderer.regionSafe]).
 * zh-CN: 查看器显示的一页: 显示尺寸 (旋转后的裁剪框, 单位为点), 以及在本设备上能否用变换绘制其分块 ([PageRenderer.regionSafe]).
 */
internal data class ViewerPage(val frame: PageFrame, val regionSafe: Boolean) {
    val width: Float get() = frame.displayWidth.toFloat()
    val height: Float get() = frame.displayHeight.toFloat()
}

/**
 * One search hit of the viewer: page from 0 and the rectangles in points of the displayed page (roadmap D31).
 * zh-CN: 查看器的一个搜索命中: 页码从 0 起, 矩形为显示页面上的点坐标 (路线图 D31).
 */
internal class ViewerHit(val page: Int, val rects: List<FloatArray>)

/**
 * The document of one viewer (roadmap P3.1, D35): PDFBox for the structure (page sizes, outline, search, image areas)
 * and the system renderer for the pixels, the same pair the plugin service uses. An encrypted document is opened with
 * its password and the renderer reads a copy saved without security in the viewer's directory (roadmap D30). PDFBox
 * calls and renderer calls are each serialized on their own lock, so the render thread and the PDFBox thread of the
 * viewer run side by side. The source descriptor stays with the caller and must stay open until [close].
 * zh-CN: 一个查看器的文档 (路线图 P3.1, D35): 结构 (页面尺寸, 大纲, 搜索, 图片区域) 由 PDFBox 提供, 像素由系统渲染器提供, 与插件服务
 * 的组合相同. 加密文档用密码打开, 渲染器读取在查看器目录中去掉安全设置后保存的副本 (路线图 D30). PDFBox 调用与渲染器调用各自在
 * 自己的锁上串行, 因此查看器的渲染线程与 PDFBox 线程可以并行. 源描述符仍归调用方所有, 在 [close] 之前必须保持打开.
 */
internal class ViewerDocument private constructor(
    private val loaded: LoadedPdf,
    private val renderer: PageRenderer,
    val pages: List<ViewerPage>,
) : AutoCloseable {

    private val pdfLock = Any()
    private val renderLock = Any()

    @Volatile
    private var closed = false

    val pageCount: Int get() = pages.size

    /** The outline entries in document order, at most [limit]. zh-CN: 按文档顺序的大纲条目, 至多 [limit] 个. */
    fun outline(stop: StopSignal, limit: Int = MAX_OUTLINE_ENTRIES): List<OutlineReader.Entry> = synchronized(pdfLock) {
        checkOpen()
        val entries = ArrayList<OutlineReader.Entry>()
        OutlineReader.walk(loaded.document, stop) { entry ->
            entries += entry
            entries.size < limit
        }
        entries
    }

    /** Image areas of page [page] (from 0), see [ImageBounds]. zh-CN: 第 [page] 页 (从 0 起) 的图片区域, 见 [ImageBounds]. */
    fun imageRects(page: Int, stop: StopSignal): List<FloatArray> = synchronized(pdfLock) {
        checkOpen()
        ImageBounds.of(loaded.document.getPage(page), stop)
    }

    /**
     * Hits of [keyword] on pages [first] to [last] (from 0, inclusive), at most [limit]; the pair's second value tells
     * whether the limit cut the pages short. Matching is the plugin's `search` (case-insensitive, not whole words).
     * zh-CN: [keyword] 在第 [first] 到 [last] 页 (从 0 起, 含两端) 的命中, 至多 [limit] 个; 返回值的第二项表示是否因达到上限而没有
     * 搜完这些页. 匹配规则与插件的 `search` 相同 (不区分大小写, 不要求整词).
     */
    fun search(keyword: String, first: Int, last: Int, limit: Int, stop: StopSignal): Pair<List<ViewerHit>, Boolean> = synchronized(pdfLock) {
        checkOpen()
        val options = PdfOptions(
            MapValues(
                mapOf(
                    PdfContract.KEY_KEYWORD to keyword,
                    PdfContract.KEY_PAGES to "${first + 1}-${last + 1}",
                    PdfContract.KEY_LIMIT to limit.coerceIn(1, PdfContract.MAX_SEARCH_HITS),
                ),
            ),
        )
        val answer = SearchReader.read(loaded.document, options, stop, budgetChars = SEARCH_BUDGET_CHARS)
        val items = JSONArray(answer[PdfContract.KEY_ITEMS] as String)
        val hits = List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val rects = item.getJSONArray(PdfContract.FIELD_RECTS)
            ViewerHit(
                page = item.getInt(PdfContract.FIELD_PAGE) - 1,
                rects = List(rects.length()) { r ->
                    val rect = rects.getJSONArray(r)
                    FloatArray(4) { rect.getDouble(it).toFloat() }
                },
            )
        }
        hits to (answer[PdfContract.KEY_HAS_MORE] == true)
    }

    /** Draws page [page] (from 0) to fill [bitmap]. zh-CN: 把第 [page] 页 (从 0 起) 画满 [bitmap]. */
    fun render(page: Int, bitmap: Bitmap) = synchronized(renderLock) {
        checkOpen()
        renderer.draw(page + 1, pages[page].frame, bitmap)
    }

    /**
     * Draws the part of page [page] (from 0) drawn [pageWidth] pixels wide that starts at [left], [top] onto [bitmap];
     * only for a [ViewerPage.regionSafe] page.
     * zh-CN: 第 [page] 页 (从 0 起) 按 [pageWidth] 像素宽绘制时, 把从 [left], [top] 开始的部分画到 [bitmap] 上; 只用于
     * [ViewerPage.regionSafe] 的页面.
     */
    fun renderRegion(page: Int, pageWidth: Int, left: Int, top: Int, bitmap: Bitmap) = synchronized(renderLock) {
        checkOpen()
        renderer.drawRegion(page + 1, pageWidth / pages[page].width, left.toFloat(), top.toFloat(), bitmap)
    }

    override fun close() {
        closed = true
        synchronized(renderLock) {
            synchronized(pdfLock) {
                try {
                    renderer.close()
                } finally {
                    loaded.close()
                }
            }
        }
    }

    private fun checkOpen() = check(!closed) { "the viewer document is closed" }

    companion object {

        /** Outline entries the drawer lists. zh-CN: 大纲抽屉列出的条目数. */
        const val MAX_OUTLINE_ENTRIES = 5000

        /** Characters of hit JSON per search chunk. zh-CN: 每段搜索的命中 JSON 字符数. */
        private const val SEARCH_BUDGET_CHARS = 2_000_000

        /**
         * Opens the seekable regular file [source] with [password] (`PASSWORD_REQUIRED` / `WRONG_PASSWORD` as
         * [io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure] like the service) and keeps temp
         * files in [directory].
         * zh-CN: 用 [password] 打开可定位的普通文件 [source] (与服务一样以 `PdfFailure` 报 `PASSWORD_REQUIRED` / `WRONG_PASSWORD`),
         * 临时文件放在 [directory].
         */
        fun open(source: ParcelFileDescriptor, password: String?, directory: File): ViewerDocument {
            val loaded = PdfLoader.load(DescriptorSource.of(source), password, directory)
            try {
                val document = loaded.document
                val pages = document.pages.map { page ->
                    val frame = PageFrame.of(page)
                    ViewerPage(frame, PageRenderer.regionSafe(frame.rotation, page.cropBox.corners(), page.mediaBox.corners()))
                }
                val renderer = if (!document.isEncrypted) {
                    PageRenderer.of(source.dup())
                } else {
                    val copy = File(directory, PageRenderer.DECRYPTED_NAME)
                    try {
                        PdfLoader.load(DescriptorSource.of(source), password, directory).use { separate ->
                            separate.document.isAllSecurityToBeRemoved = true
                            separate.document.save(copy)
                        }
                    } catch (t: Throwable) {
                        copy.delete()
                        throw t
                    }
                    PageRenderer.of(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY), copy)
                }
                return ViewerDocument(loaded, renderer, pages)
            } catch (t: Throwable) {
                runCatching { loaded.close() }
                throw t
            }
        }

        private fun PDRectangle.corners() = floatArrayOf(lowerLeftX, lowerLeftY, upperRightX, upperRightY)

    }

}

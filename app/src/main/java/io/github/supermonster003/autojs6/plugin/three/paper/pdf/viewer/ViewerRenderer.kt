package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A bitmap the viewer draws: a whole page at some width ([Page], also the thumbnails) or one tile of a zoomed page
 * ([Tile], roadmap D35). Pages count from 0.
 * zh-CN: 查看器绘制的位图: 某一宽度下的整页 ([Page], 缩略图也是) 或缩放后页面的一个分块 ([Tile], 路线图 D35). 页码从 0 起.
 */
internal sealed interface RenderKey {

    val page: Int

    /** Estimated bytes of the ARGB_8888 bitmap. zh-CN: ARGB_8888 位图的估算字节数. */
    val bytes: Long

    data class Page(override val page: Int, val width: Int, val height: Int) : RenderKey {
        override val bytes: Long get() = width.toLong() * height * 4
    }

    data class Tile(
        override val page: Int,
        val pageWidth: Int,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) : RenderKey {
        override val bytes: Long get() = width.toLong() * height * 4
    }

}

/**
 * Renders the bitmaps the viewer wants on one background thread, in the order of the latest [request], into an LRU
 * cache capped at an eighth of the heap (roadmap D35). The order puts what is on screen first, so when the list costs
 * more than the cache can hold the tail is dropped instead of evicting what was just drawn. Evicted bitmaps are left to
 * the garbage collector rather than recycled, because a frame being drawn may still hold one. [onRendered] runs on the
 * main thread, at most once per frame's worth of renders.
 * zh-CN: 在一个后台线程上按最近一次 [request] 的顺序渲染查看器需要的位图, 放入上限为堆内存八分之一的 LRU 缓存 (路线图 D35). 顺序把
 * 屏幕上的内容放在最前, 因此列表超出缓存容量时丢弃尾部, 而不是逐出刚画好的位图. 被逐出的位图交给垃圾回收器, 不主动回收, 因为正在
 * 绘制的帧可能仍持有它. [onRendered] 在主线程运行, 合并连续的渲染结果.
 */
internal class ViewerRenderer(private val document: ViewerDocument, private val onRendered: () -> Unit) : AutoCloseable {

    val budgetBytes: Long = Runtime.getRuntime().maxMemory() / 8

    private val cache = object : LruCache<RenderKey, Bitmap>(budgetBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
        override fun sizeOf(key: RenderKey, value: Bitmap): Int = value.allocationByteCount
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var wanted: List<RenderKey> = emptyList()
    private val failed = HashSet<RenderKey>()
    private var closed = false

    private val main = Handler(Looper.getMainLooper())
    private val notifyPending = AtomicBoolean(false)

    private val thread = Thread(::loop, "3-paper-viewer-render").apply {
        isDaemon = true
        start()
    }

    /** The cached bitmap of [key], or null. zh-CN: [key] 的缓存位图, 没有时为 null. */
    fun get(key: RenderKey): Bitmap? = cache.get(key)

    /** Whether [key] cannot be rendered. zh-CN: [key] 是否无法渲染. */
    fun failed(key: RenderKey): Boolean = lock.withLock { key in failed }

    /**
     * Replaces the wanted list: [keys] in priority order, cut where their bytes pass [FILL_RATIO] of the cache.
     * zh-CN: 替换需求列表: [keys] 按优先级排列, 字节数累计超过缓存的 [FILL_RATIO] 处截断.
     */
    fun request(keys: List<RenderKey>) {
        var total = 0L
        val limit = (budgetBytes * FILL_RATIO).toLong()
        val kept = ArrayList<RenderKey>(keys.size)
        for (key in keys) {
            if (key in kept) continue
            total += key.bytes
            if (total > limit && kept.isNotEmpty()) break
            kept += key
        }
        lock.withLock {
            wanted = kept
            changed.signalAll()
        }
    }

    /** Stops the thread; [join] waits for a render in progress. zh-CN: 停止线程; [join] 等待进行中的渲染结束. */
    override fun close() {
        lock.withLock {
            closed = true
            wanted = emptyList()
            changed.signalAll()
        }
        cache.evictAll()
    }

    fun join(millis: Long) = thread.join(millis)

    private fun loop() {
        while (true) {
            val key = lock.withLock {
                var next: RenderKey? = null
                while (!closed) {
                    next = wanted.firstOrNull { it !in failed && cache.get(it) == null }
                    if (next != null) break
                    changed.awaitUninterruptibly()
                }
                if (closed) return
                next!!
            }
            val bitmap = try {
                render(key)
            } catch (_: OutOfMemoryError) {
                // Free half of the cache and give up on this key, so the loop cannot spin on it.
                // zh-CN: 释放一半缓存并放弃此键, 避免循环卡在它上面.
                cache.trimToSize(cache.size() / 2)
                lock.withLock { failed += key }
                null
            } catch (_: Exception) {
                lock.withLock { failed += key }
                null
            }
            if (bitmap != null) {
                lock.withLock {
                    if (closed) return
                    cache.put(key, bitmap)
                }
            }
            if (notifyPending.compareAndSet(false, true)) {
                main.post {
                    notifyPending.set(false)
                    onRendered()
                }
            }
        }
    }

    private fun render(key: RenderKey): Bitmap = when (key) {
        is RenderKey.Page -> createBitmap(key.width, key.height).also {
            it.eraseColor(Color.WHITE)
            document.render(key.page, it)
        }
        is RenderKey.Tile -> createBitmap(key.width, key.height).also {
            it.eraseColor(Color.WHITE)
            document.renderRegion(key.page, key.pageWidth, key.left, key.top, it)
        }
    }

    companion object {
        /** Part of the cache the wanted list may fill. zh-CN: 需求列表可占用的缓存比例. */
        const val FILL_RATIO = 0.8
    }

}

/**
 * Which bitmaps a viewer frame needs (roadmap D35), as pure arithmetic over [ViewerLayout]: a base bitmap per page
 * (half width below [HALF_WIDTH_ZOOM], capped at [MAX_BASE_PIXELS]), tiles at the settled zoom once the page is shown
 * more than [TILE_THRESHOLD] times larger than its base, and for pages whose tiles cannot be drawn on this device an
 * enlarged whole page capped at [MAX_ENLARGED_PIXELS].
 * zh-CN: 查看器一帧需要哪些位图 (路线图 D35), 基于 [ViewerLayout] 的纯算术: 每页一张基础位图 (缩放低于 [HALF_WIDTH_ZOOM] 时为半宽,
 * 上限 [MAX_BASE_PIXELS] 像素); 页面显示尺寸超过基础位图 [TILE_THRESHOLD] 倍后, 按稳定后的缩放比例分块; 本设备无法分块绘制的页面
 * 改为放大的整页, 上限 [MAX_ENLARGED_PIXELS] 像素.
 */
internal object RenderPlan {

    const val MAX_BASE_PIXELS = 2_500_000L
    const val MAX_ENLARGED_PIXELS = 4_000_000L
    const val HALF_WIDTH_ZOOM = 0.75f
    const val TILE_THRESHOLD = 1.15f

    fun base(layout: ViewerLayout, page: Int, zoom: Float): RenderKey.Page {
        val bucket = if (zoom < HALF_WIDTH_ZOOM) 0.5f else 1f
        return pageKey(page, layout.pageWidth(bucket), layout.pageHeight(page, bucket), MAX_BASE_PIXELS)
    }

    fun thumbnail(layout: ViewerLayout, page: Int, width: Float): RenderKey.Page {
        val height = layout.pageHeight(page, 1f) * width / layout.pageWidth(1f)
        return pageKey(page, width, height, MAX_BASE_PIXELS / 8)
    }

    /** The enlarged whole page for a page without tiles, or null when the base suffices. zh-CN: 不分块页面的放大整页, 基础位图够用时为 null. */
    fun enlarged(layout: ViewerLayout, page: Int, zoom: Float): RenderKey.Page? {
        val key = pageKey(page, layout.pageWidth(zoom), layout.pageHeight(page, zoom), MAX_ENLARGED_PIXELS)
        return key.takeIf { it.width > base(layout, page, zoom).width * TILE_THRESHOLD }
    }

    /** Whether page [page] at [zoom] needs more detail than its base. zh-CN: 第 [page] 页在 [zoom] 下是否需要比基础位图更多的细节. */
    fun needsDetail(layout: ViewerLayout, page: Int, zoom: Float): Boolean = layout.pageWidth(zoom) > base(layout, page, zoom).width * TILE_THRESHOLD

    /**
     * The tiles of page [page] drawn at [tileZoom] that the viewport of [position] shows, plus [margin] tiles around.
     * zh-CN: 按 [tileZoom] 绘制的第 [page] 页中, [position] 的视口显示的分块, 外加四周 [margin] 块.
     */
    fun tiles(layout: ViewerLayout, page: Int, position: ViewerPosition, tileZoom: Float, margin: Int = 0): List<RenderKey.Tile> {
        val zoom = position.zoom
        val pageWidth = layout.pageWidth(tileZoom).roundToInt()
        val pageHeight = layout.pageHeight(page, tileZoom).roundToInt()
        if (pageWidth <= 0 || pageHeight <= 0) return emptyList()
        // Viewport in page pixels at the current zoom, then scaled to the tile zoom.
        // zh-CN: 先求当前缩放下以页面像素表示的视口, 再换算到分块的缩放比例.
        val pageLeft = layout.viewportX(layout.pageLeft(zoom), position)
        val pageTop = layout.viewportY(layout.pageTop(page, zoom), position)
        val ratio = tileZoom / zoom
        val left = (0f - pageLeft) * ratio
        val top = (0f - pageTop) * ratio
        val right = (layout.viewportWidth - pageLeft) * ratio
        val bottom = (layout.viewportHeight - pageTop) * ratio
        return TileGrid.visible(pageWidth, pageHeight, left, top, right, bottom, margin).map { tile ->
            RenderKey.Tile(page, pageWidth, tile.left, tile.top, tile.width, tile.height)
        }
    }

    private fun pageKey(page: Int, width: Float, height: Float, maxPixels: Long): RenderKey.Page {
        var w = max(1f, width)
        var h = max(1f, height)
        val pixels = w.toDouble() * h
        if (pixels > maxPixels) {
            val factor = sqrt(maxPixels / pixels).toFloat()
            w *= factor
            h *= factor
        }
        return RenderKey.Page(page, max(1, w.roundToInt()), max(1, h.roundToInt()))
    }

}

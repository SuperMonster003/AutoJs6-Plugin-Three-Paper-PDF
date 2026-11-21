package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.OutlineReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/**
 * What one viewer shows (roadmap P3.1): [key] identifies the request, so a recreated Activity keeps its document;
 * [open] and [name] run on the viewer's background thread. [session] is the claimed viewer session of a script request
 * (P3.2).
 * zh-CN: 一个查看器要显示的内容 (路线图 P3.1): [key] 标识请求, 因此重建的 Activity 保留其文档; [open] 与 [name] 在查看器的后台
 * 线程上运行. [session] 是脚本请求已认领的查看器会话 (P3.2).
 */
internal class ViewerRequest(
    val key: String,
    val open: (Context) -> ParcelFileDescriptor,
    val name: (Context) -> String?,
    val password: String? = null,
    val page: Int = 0,
    val keyword: String? = null,
    val session: ViewerSession? = null,
)

/**
 * The state of one viewer (roadmap P3.1, D35), kept across configuration changes: loading and the password prompt,
 * the document, scroll and zoom, render requests, search, outline and night mode. PDFBox work runs on one background
 * thread, rendering on the [ViewerRenderer] thread; results come back to the main thread, where all state lives.
 *
 * A script request comes with its viewer session (P3.2): the model is the session's [ViewerController], reports the
 * current page, page count and zoom to it whenever the position changes, and ends it when the document is replaced
 * (`replaced` by another script request, `closed` by any other request, roadmap appendix D Q72) or the viewer closes
 * for good ([onCleared]: `closed`).
 * zh-CN: 一个查看器的状态 (路线图 P3.1, D35), 在配置变更后保留: 加载与密码提示, 文档, 滚动与缩放, 渲染请求, 搜索, 大纲与夜间模式.
 * PDFBox 工作在一个后台线程上运行, 渲染在 [ViewerRenderer] 的线程上; 结果回到主线程, 全部状态都在主线程上.
 *
 * 脚本请求附带其查看器会话 (P3.2): 本模型是会话的 [ViewerController], 每次位置改变时向会话报告当前页, 页数与缩放, 并在文档被替换时
 * (被另一个脚本请求替换为 `replaced`, 被其他请求替换为 `closed`, 路线图附录 D Q72) 或查看器彻底关闭时 ([onCleared]: `closed`) 结束会话.
 */
internal class ViewerViewModel(application: Application) : AndroidViewModel(application), ViewerController {

    sealed interface Phase {
        data object Loading : Phase
        data class Password(val wrong: Boolean) : Phase
        data object Ready : Phase
        data class Failed(@param:StringRes val message: Int) : Phase
    }

    private val main = Handler(Looper.getMainLooper())
    private val pdf: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "3-paper-viewer-pdf").apply { isDaemon = true }
    }
    private val generation = AtomicInteger()
    private val searchGeneration = AtomicInteger()
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val directory = File(File(application.cacheDir, DIRECTORY), UUID.randomUUID().toString())

    private var shownKey: String? = null
    private var request: ViewerRequest? = null
    private var session: ViewerSession? = null
    private var finisher: (() -> Unit)? = null
    private var finishPending = false
    private var resumed = false
    private var source: ParcelFileDescriptor? = null
    private var pendingPage = 0
    private var pendingHit = -1

    var phase by mutableStateOf<Phase>(Phase.Loading)
        private set
    var displayName by mutableStateOf("")
        private set
    var document by mutableStateOf<ViewerDocument?>(null)
        private set
    var renderer: ViewerRenderer? = null
        private set
    var layout by mutableStateOf<ViewerLayout?>(null)
        private set
    private var positionState by mutableStateOf(ViewerPosition(1f, 0f, 0f))

    var position: ViewerPosition
        get() = positionState
        private set(value) {
            positionState = value
            report()
        }

    /** The zoom tiles are drawn at; follows [position] once a gesture settles. zh-CN: 分块所用的缩放比例; 手势稳定后跟随 [position]. */
    var tileZoom by mutableFloatStateOf(1f)
        private set

    /** Bumped when bitmaps or image areas arrive, so the canvas redraws. zh-CN: 位图或图片区域到达时递增, 使画布重绘. */
    var renderVersion by mutableIntStateOf(0)
        private set

    var chromeVisible by mutableStateOf(true)
    var outlineOpen by mutableStateOf(false)
    var jumpOpen by mutableStateOf(false)

    var nightMode by mutableStateOf(preferences.getBoolean(KEY_NIGHT, false))
        private set

    var outline by mutableStateOf<List<OutlineReader.Entry>?>(null)
        private set

    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
    val hits = mutableStateListOf<ViewerHit>()
    var hitIndex by mutableIntStateOf(-1)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchTruncated by mutableStateOf(false)
        private set

    /** The keyword of the last search, null before any. zh-CN: 上一次搜索的关键词, 尚未搜索时为 null. */
    var lastKeyword by mutableStateOf<String?>(null)
        private set

    /** Pages of the thumbnail strip on screen. zh-CN: 缩略图栏中在屏幕上的页. */
    var thumbnailPages: IntRange = IntRange.EMPTY
    var thumbnailWidth = 0f

    val currentPage by derivedStateOf { layout?.currentPage(position) ?: 0 }

    private val imageRects = HashMap<Int, List<FloatArray>>()
    private val imageRequests = HashSet<Int>()
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var insets = ViewerInsets.NONE
    private var density = 1f

    @Volatile
    private var cleared = false

    /**
     * Shows the request identified by [key] unless it is the one already shown (the Activity was recreated); [build]
     * creates it only then, so a session token is claimed once. A null request shows [missing].
     * zh-CN: 显示由 [key] 标识的请求, 除非它就是当前已显示的请求 (Activity 被重建); 只有此时才调用 [build] 创建请求, 因此会话令牌只被认领
     * 一次. 请求为 null 时显示 [missing].
     */
    fun start(key: String, @StringRes missing: Int, build: () -> ViewerRequest?) {
        if (shownKey == key) return
        endSession(if (key.startsWith(SESSION_KEY_PREFIX)) PdfContract.REASON_REPLACED else PdfContract.REASON_CLOSED)
        reset()
        shownKey = key
        val next = build()
        if (next == null) {
            phase = Phase.Failed(missing)
            return
        }
        request = next
        session = next.session?.also { it.visible(resumed) }
        pendingPage = max(0, next.page)
        searchQuery = next.keyword.orEmpty()
        val current = generation.get()
        val context = getApplication<Application>()
        pdf.execute {
            try {
                directory.mkdirs()
                pruneStale(context)
                val name = runCatching { next.name(context) }.getOrNull()
                val prepared = prepare(next.open(context))
                post(current, { runCatching { prepared.close() } }) {
                    source = prepared
                    displayName = name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.viewer_untitled)
                }
                load(current, prepared, next.password)
            } catch (t: Throwable) {
                post(current) { phase = Phase.Failed(messageOf(t)) }
            }
        }
    }

    /** Opens the document again with [password]. zh-CN: 用 [password] 重新打开文档. */
    fun submitPassword(password: String) {
        val prepared = source ?: return
        phase = Phase.Loading
        val current = generation.get()
        pdf.execute { load(current, prepared, password) }
    }

    fun onViewport(width: Float, height: Float, density: Float) {
        if (width <= 0f || height <= 0f || (width == viewportWidth && height == viewportHeight && density == this.density)) return
        viewportWidth = width
        viewportHeight = height
        this.density = density
        rebuildLayout()
    }

    /**
     * The edges the bars may cover (E4 feedback F1); they change with the window (rotation, cutout), not with the bars
     * showing or hiding, so the pages do not move when the bars do.
     * zh-CN: 各栏可能遮住的边缘 (E4 反馈 F1); 它们随窗口 (旋转, 刘海挖孔) 变化, 不随各栏显示或隐藏变化, 因此各栏出现与消失时页面不动.
     */
    fun onInsets(next: ViewerInsets) {
        if (next == insets) return
        insets = next
        rebuildLayout()
    }

    // Scroll and zoom. zh-CN: 滚动与缩放.

    /** Moves by [dx], [dy] pixels; answers whether the position changed. zh-CN: 移动 [dx], [dy] 像素; 返回位置是否改变. */
    fun scrollBy(dx: Float, dy: Float): Boolean {
        val current = layout ?: return false
        val next = current.clamp(position.copy(scrollX = position.scrollX + dx, scrollY = position.scrollY + dy))
        if (next == position) return false
        position = next
        return true
    }

    fun zoomTo(zoom: Float, focusX: Float, focusY: Float) {
        val current = layout ?: return
        position = current.clamp(current.zoomAround(position, zoom, focusX, focusY))
    }

    fun moveTo(next: ViewerPosition) {
        val current = layout ?: return
        position = current.clamp(next)
    }

    /** The gesture ended: tiles follow the zoom now. zh-CN: 手势结束: 分块此时跟随缩放比例. */
    fun settle() {
        tileZoom = position.zoom
    }

    fun goToPage(page: Int) {
        val current = layout ?: return
        position = current.clamp(current.positionForPage(page, position))
        settle()
    }

    // Viewer session (P3.2). zh-CN: 查看器会话 (P3.2).

    /** The session's host moves the viewer; before the layout exists the page waits for it. zh-CN: 会话的宿主移动查看器; 布局建立之前该页等待布局. */
    override fun goTo(page: Int) {
        if (layout == null) {
            pendingPage = max(0, page)
            pendingHit = -1
            return
        }
        goToPage(page)
    }

    /** The session's host finishes the viewer. zh-CN: 会话的宿主结束查看器. */
    override fun finish() {
        val target = finisher
        if (target == null) finishPending = true else target()
    }

    /**
     * The Activity that finishes when the host asks for it ([finish]); a request that arrived while no Activity was
     * attached (recreation) runs now.
     * zh-CN: 宿主要求结束时 ([finish]) 结束的 Activity; 没有 Activity 挂接期间 (重建中) 到达的请求在此时执行.
     */
    fun attach(finish: (() -> Unit)?) {
        finisher = finish
        if (finish != null && finishPending) {
            finishPending = false
            finish()
        }
    }

    /** Whether the viewer Activity is resumed (`visible` of the session state). zh-CN: 查看器 Activity 是否处于 resumed 状态 (会话状态的 `visible`). */
    fun onResumed(resumed: Boolean) {
        this.resumed = resumed
        session?.visible(resumed)
    }

    private fun report() {
        val target = session ?: return
        val current = layout ?: return
        val opened = document ?: return
        target.update(current.currentPage(positionState), opened.pageCount, positionState.zoom)
    }

    private fun endSession(reason: String) {
        session?.end(reason)
        session = null
    }

    fun toggleNightMode() {
        nightMode = !nightMode
        preferences.edit { putBoolean(KEY_NIGHT, nightMode) }
        renderVersion++
    }

    // Rendering. zh-CN: 渲染.

    /**
     * Sends the render requests of the current frame: visible pages, their detail, neighbors, then thumbnails.
     * zh-CN: 发送当前帧的渲染请求: 可见页, 其细节, 相邻页, 然后是缩略图.
     */
    fun plan() {
        val current = layout ?: return
        val target = renderer ?: return
        val document = document ?: return
        val visible = current.visiblePages(position)
        val keys = ArrayList<RenderKey>()
        for (page in visible) keys += RenderPlan.base(current, page, position.zoom)
        for (page in visible) {
            if (!RenderPlan.needsDetail(current, page, tileZoom)) continue
            if (document.pages[page].regionSafe) {
                keys += RenderPlan.tiles(current, page, position, tileZoom)
            } else {
                RenderPlan.enlarged(current, page, tileZoom)?.let { keys += it }
            }
        }
        if (!visible.isEmpty()) {
            for (offset in 1..PREFETCH_PAGES) {
                if (visible.last + offset < current.pageCount) keys += RenderPlan.base(current, visible.last + offset, position.zoom)
                if (visible.first - offset >= 0) keys += RenderPlan.base(current, visible.first - offset, position.zoom)
            }
        }
        if (chromeVisible && thumbnailWidth > 0f) {
            for (page in thumbnailPages) if (page in 0 until current.pageCount) keys += RenderPlan.thumbnail(current, page, thumbnailWidth)
        }
        target.request(keys)
        if (nightMode) for (page in visible) requestImageRects(page)
    }

    /** Image areas of [page] once known, for night mode. zh-CN: 已知时返回 [page] 的图片区域, 供夜间模式使用. */
    fun imageRectsOf(page: Int): List<FloatArray>? = imageRects[page]

    private fun requestImageRects(page: Int) {
        val document = document ?: return
        if (page in imageRects || !imageRequests.add(page)) return
        val current = generation.get()
        val stop = stopFor(current)
        pdf.execute {
            val rects = try {
                document.imageRects(page, stop)
            } catch (_: Exception) {
                emptyList()
            }
            post(current) {
                imageRects[page] = rects
                renderVersion++
            }
        }
    }

    // Search. zh-CN: 搜索.

    fun openSearch() {
        searchOpen = true
        chromeVisible = true
    }

    fun closeSearch() {
        searchOpen = false
        searchGeneration.incrementAndGet()
        searching = false
        lastKeyword = null
        hits.clear()
        hitIndex = -1
    }

    /**
     * Searches the whole document for [searchQuery] in chunks of [SEARCH_CHUNK_PAGES] pages, so hits appear while it
     * runs, and stops at `MAX_SEARCH_HITS` hits. It jumps to the first hit at or after the current page (the start page
     * of a script's request), or to the first hit when there is none after it.
     * zh-CN: 以每段 [SEARCH_CHUNK_PAGES] 页在全文中搜索 [searchQuery], 命中边搜边显示, 达到 `MAX_SEARCH_HITS` 个命中时停止. 跳到当前页
     * (脚本请求的起始页) 或其后的第一个命中, 其后没有命中时跳到第一个命中.
     */
    fun search() {
        val document = document ?: return
        val keyword = searchQuery.trim().take(PdfContract.MAX_KEYWORD_LENGTH)
        val current = searchGeneration.incrementAndGet()
        hits.clear()
        hitIndex = -1
        pendingHit = -1
        searchTruncated = false
        if (keyword.isEmpty()) {
            searching = false
            lastKeyword = null
            return
        }
        searching = true
        lastKeyword = keyword
        val startPage = if (layout != null) currentPage else pendingPage
        var jumped = false
        val documentGeneration = generation.get()
        val stop = StopSignal {
            if (cleared || searchGeneration.get() != current || generation.get() != documentGeneration) StopSignal.cancelled() else null
        }
        pdf.execute {
            var found = 0
            var truncated = false
            try {
                var first = 0
                while (first < document.pageCount) {
                    val last = min(first + SEARCH_CHUNK_PAGES - 1, document.pageCount - 1)
                    val (chunk, more) = document.search(keyword, first, last, PdfContract.MAX_SEARCH_HITS - found, stop)
                    found += chunk.size
                    if (chunk.isNotEmpty()) {
                        postSearch(current) {
                            hits.addAll(chunk)
                            if (!jumped) {
                                val index = hits.indexOfFirst { it.page >= startPage }
                                if (index >= 0) {
                                    jumped = true
                                    showHit(index)
                                }
                            }
                        }
                    }
                    if (more || found >= PdfContract.MAX_SEARCH_HITS) {
                        truncated = true
                        break
                    }
                    first = last + 1
                }
            } catch (_: Exception) {
                // Cancelled by a newer search or a damaged page: keep the hits found so far.
                // zh-CN: 被新的搜索取消或遇到损坏的页面: 保留已找到的命中.
            }
            postSearch(current) {
                searching = false
                searchTruncated = truncated
                if (!jumped && hits.isNotEmpty()) {
                    jumped = true
                    showHit(0)
                }
            }
        }
    }

    fun showHit(index: Int) {
        if (hits.isEmpty()) return
        val wrapped = Math.floorMod(index, hits.size)
        hitIndex = wrapped
        val current = layout
        if (current == null) {
            // Not measured yet: the layout shows the hit once it exists. zh-CN: 尚未测量: 布局建立后再显示该命中.
            pendingHit = wrapped
            return
        }
        val hit = hits[wrapped]
        val rects = hit.rects
        position = if (rects.isEmpty()) {
            current.clamp(current.positionForPage(hit.page, position))
        } else {
            val left = rects.minOf { it[0] }
            val top = rects.minOf { it[1] }
            val right = rects.maxOf { it[2] }
            val bottom = rects.maxOf { it[3] }
            current.clamp(current.positionForRect(hit.page, left, top, right, bottom, position))
        }
        settle()
    }

    // Share. zh-CN: 分享.

    /**
     * Copies the source to the share directory of the cache, for the FileProvider, and hands the file to [done] on the
     * main thread (null on failure). Copies older than a day are removed first.
     * zh-CN: 把源复制到缓存的分享目录供 FileProvider 使用, 在主线程把文件交给 [done] (失败时为 null). 先删除超过一天的副本.
     */
    fun exportCopy(done: (File?) -> Unit) {
        val prepared = source ?: return done(null)
        val current = generation.get()
        val context = getApplication<Application>()
        val name = shareName(displayName)
        pdf.execute {
            val file = try {
                val dir = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
                val cutoff = System.currentTimeMillis() - STALE_MILLIS
                dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
                val target = File(File(dir, UUID.randomUUID().toString()).apply { mkdirs() }, name)
                val input = DescriptorSource.of(prepared)
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var position = 0L
                    while (position < input.length) {
                        val read = input.read(position, buffer, 0, buffer.size)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        position += read
                    }
                }
                target
            } catch (_: Exception) {
                null
            }
            post(current) { done(file) }
        }
    }

    override fun onCleared() {
        cleared = true
        endSession(PdfContract.REASON_CLOSED)
        reset()
        pdf.shutdown()
    }

    // Internals. zh-CN: 内部实现.

    private fun load(current: Int, prepared: ParcelFileDescriptor, password: String?) {
        val opened = try {
            ViewerDocument.open(prepared, password, directory)
        } catch (failure: PdfFailure) {
            post(current) {
                phase = when (failure.code) {
                    PdfErrorCodes.PASSWORD_REQUIRED -> Phase.Password(wrong = false)
                    PdfErrorCodes.WRONG_PASSWORD -> Phase.Password(wrong = true)
                    else -> Phase.Failed(messageOf(failure))
                }
            }
            return
        } catch (t: Throwable) {
            post(current) { phase = Phase.Failed(messageOf(t)) }
            return
        }
        post(current, { runCatching { opened.close() } }) {
            document = opened
            renderer = ViewerRenderer(opened) { renderVersion++ }
            phase = if (opened.pageCount == 0) Phase.Failed(R.string.viewer_error_empty) else Phase.Ready
            rebuildLayout()
            loadOutline(current, opened)
            if (searchQuery.isNotBlank()) {
                searchOpen = true
                search()
            }
        }
    }

    private fun loadOutline(current: Int, opened: ViewerDocument) {
        val stop = stopFor(current)
        pdf.execute {
            val entries = try {
                opened.outline(stop)
            } catch (_: Exception) {
                emptyList()
            }
            post(current) { outline = entries }
        }
    }

    private fun rebuildLayout() {
        val opened = document ?: return
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        val previous = layout
        val anchor = previous?.anchorOf(position)
        val next = ViewerLayout(
            pageWidths = FloatArray(opened.pageCount) { opened.pages[it].width },
            pageHeights = FloatArray(opened.pageCount) { opened.pages[it].height },
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            gap = GAP_DP * density,
            padding = PADDING_DP * density,
            insets = insets,
        )
        layout = next
        position = when {
            anchor != null -> next.clamp(next.positionFor(anchor))
            else -> next.clamp(next.positionForPage(pendingPage.coerceAtMost(opened.pageCount - 1), ViewerPosition(1f, 0f, 0f)))
        }
        settle()
        if (pendingHit >= 0 && pendingHit < hits.size) showHit(pendingHit)
        pendingHit = -1
    }

    private fun reset() {
        generation.incrementAndGet()
        searchGeneration.incrementAndGet()
        val oldRenderer = renderer
        val oldDocument = document
        val oldSource = source
        renderer = null
        document = null
        source = null
        layout = null
        request = null
        outline = null
        hits.clear()
        hitIndex = -1
        pendingHit = -1
        searching = false
        searchOpen = false
        lastKeyword = null
        outlineOpen = false
        jumpOpen = false
        imageRects.clear()
        imageRequests.clear()
        phase = Phase.Loading
        oldRenderer?.close()
        if (oldRenderer != null || oldDocument != null || oldSource != null) {
            val dir = directory
            thread(name = "3-paper-viewer-close", isDaemon = true) {
                runCatching { oldRenderer?.join(CLOSE_WAIT_MILLIS) }
                runCatching { oldDocument?.close() }
                runCatching { oldSource?.close() }
                if (cleared) dir.deleteRecursively()
            }
        } else if (cleared) {
            directory.deleteRecursively()
        }
    }

    /**
     * A seekable regular file for the source: [raw] itself, or a copy in the viewer directory when it is a pipe or a
     * socket (a provider that streams).
     * zh-CN: 可定位的普通文件形式的源: 就是 [raw], 或者当它是管道或套接字 (流式提供方) 时, 复制到查看器目录.
     */
    private fun prepare(raw: ParcelFileDescriptor): ParcelFileDescriptor {
        if (runCatching { DescriptorSource.of(raw) }.isSuccess) return raw
        val copy = File(directory, SOURCE_COPY)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(raw).use { input -> FileOutputStream(copy).use { input.copyTo(it, COPY_BUFFER) } }
            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (t: Throwable) {
            copy.delete()
            throw t
        }
    }

    private fun pruneStale(context: Context) {
        val cutoff = System.currentTimeMillis() - STALE_MILLIS
        File(context.cacheDir, DIRECTORY).listFiles()?.forEach { dir ->
            if (dir != directory && dir.lastModified() < cutoff) dir.deleteRecursively()
        }
    }

    private fun stopFor(current: Int) = StopSignal {
        if (cleared || generation.get() != current) StopSignal.cancelled() else null
    }

    /**
     * Runs [action] on the main thread while [current] is still the shown request; otherwise runs [discard] there.
     * zh-CN: 在主线程运行 [action], 前提是 [current] 仍是当前显示的请求; 否则在主线程运行 [discard].
     */
    private fun post(current: Int, discard: () -> Unit = {}, action: () -> Unit) {
        main.post { if (!cleared && generation.get() == current) action() else discard() }
    }

    private fun postSearch(current: Int, action: () -> Unit) {
        main.post { if (!cleared && searchGeneration.get() == current) action() }
    }

    companion object {

        /** Key prefix of script requests ([start] tells `replaced` from `closed` by it). zh-CN: 脚本请求的键前缀 ([start] 据此区分 `replaced` 与 `closed`). */
        const val SESSION_KEY_PREFIX = "token:"

        const val PREFERENCES = "viewer"
        const val KEY_NIGHT = "night_mode"

        /** Directories of open viewers under the cache directory. zh-CN: 缓存目录下打开中的查看器目录. */
        const val DIRECTORY = "viewer"

        /** Copies handed to other apps, under the cache directory (see `res/xml/viewer_file_paths.xml`). zh-CN: 交给其他应用的副本, 位于缓存目录下 (见 `res/xml/viewer_file_paths.xml`). */
        const val SHARE_DIRECTORY = "viewer-share"

        const val SOURCE_COPY = "source.pdf"
        const val GAP_DP = 8f
        const val PADDING_DP = 8f
        const val PREFETCH_PAGES = 1
        const val SEARCH_CHUNK_PAGES = 8
        const val COPY_BUFFER = 64 * 1024
        const val CLOSE_WAIT_MILLIS = 5_000L
        const val STALE_MILLIS = 24L * 60 * 60 * 1000

        @StringRes
        fun messageOf(error: Throwable): Int = when ((error as? PdfFailure)?.code) {
            PdfErrorCodes.INVALID_PDF -> R.string.viewer_error_invalid
            else -> if (error is SecurityException) R.string.viewer_error_denied else R.string.viewer_error_read
        }

        /** A file name for the shared copy: the display name with `.pdf`, unsafe characters replaced. zh-CN: 分享副本的文件名: 显示名称加 `.pdf`, 替换不安全的字符. */
        fun shareName(displayName: String): String {
            val base = displayName.trim().replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").take(120).ifBlank { "document" }
            return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
        }

    }

}

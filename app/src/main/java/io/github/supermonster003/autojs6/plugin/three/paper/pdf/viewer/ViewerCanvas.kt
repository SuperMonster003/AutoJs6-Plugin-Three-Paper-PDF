package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.core.graphics.withClip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The page column of the viewer (roadmap P3.1, D35): continuous vertical scrolling with fling, pinch zoom around the
 * fingers, double tap between fit width and [ViewerLayout.DOUBLE_TAP_ZOOM], a single tap toggling the bars. It draws
 * each visible page from its base bitmap, then the tiles of the settled zoom, then search highlights; night mode draws
 * the page through an inverting color filter and draws the image areas again without it (appendix D Q3).
 * zh-CN: 查看器的页面列 (路线图 P3.1, D35): 连续纵向滚动并支持惯性滑动, 围绕手指双指缩放, 双击在适配宽度与
 * [ViewerLayout.DOUBLE_TAP_ZOOM] 之间切换, 单击显示或隐藏工具栏. 每个可见页先画基础位图, 再画稳定缩放比例下的分块, 最后画搜索高亮;
 * 夜间模式用反色滤镜绘制页面, 再不带滤镜重画图片区域 (附录 D Q3).
 */
@Composable
internal fun ViewerCanvas(model: ViewerViewModel, background: Color, insets: ViewerInsets, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Offset>()
    val jobs = remember { arrayOfNulls<Job>(1) }
    val painter = remember { PagePainter() }

    SideEffect { model.onInsets(insets) }

    LaunchedEffect(model) {
        snapshotFlow { listOf(model.layout, model.position, model.tileZoom, model.nightMode, model.chromeVisible, model.document) }
            .collect { model.plan() }
    }

    Canvas(
        modifier = modifier
            .testTag(VIEWER_CANVAS_TAG)
            .onSizeChanged { model.onViewport(it.width.toFloat(), it.height.toFloat(), density) }
            .pointerInput(model) {
                detectTapGestures(
                    onTap = { model.chromeVisible = !model.chromeVisible },
                    onDoubleTap = { focus ->
                        jobs[0]?.cancel()
                        jobs[0] = scope.launch {
                            val from = model.position.zoom
                            val to = if (from < (1f + ViewerLayout.DOUBLE_TAP_ZOOM) / 2) ViewerLayout.DOUBLE_TAP_ZOOM else 1f
                            try {
                                animate(from, to, animationSpec = spring(stiffness = 600f)) { value, _ -> model.zoomTo(value, focus.x, focus.y) }
                            } finally {
                                model.settle()
                            }
                        }
                    },
                )
            }
            .pointerInput(model) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    jobs[0]?.cancel()
                    val tracker = VelocityTracker()
                    var trackedId = down.id
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moving = false
                    var zoomed = false
                    var pan = Offset.Zero
                    var scale = 1f
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (!moving) {
                            pan += panChange
                            scale *= zoomChange
                            if (pan.getDistance() > slop || abs(1f - scale) > ZOOM_SLOP) moving = true
                        }
                        if (moving) {
                            if (pressed.size >= 2 && zoomChange != 1f) {
                                val centroid = event.calculateCentroid(useCurrent = true)
                                model.zoomTo(model.position.zoom * zoomChange, centroid.x, centroid.y)
                                zoomed = true
                            }
                            if (panChange != Offset.Zero) model.scrollBy(-panChange.x, -panChange.y)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        val tracked = pressed.firstOrNull { it.id == trackedId }
                        if (tracked == null || pressed.size > 1) {
                            tracker.resetTracking()
                            trackedId = pressed.first().id
                        } else {
                            tracker.addPosition(tracked.uptimeMillis, tracked.position)
                        }
                    }
                    if (!moving) return@awaitEachGesture
                    val velocity = tracker.calculateVelocity()
                    val start = Offset(velocity.x, velocity.y)
                    if (start.getDistance() < MIN_FLING_VELOCITY * density) {
                        model.settle()
                        return@awaitEachGesture
                    }
                    if (zoomed) model.settle()
                    jobs[0] = scope.launch {
                        var last = Offset.Zero
                        try {
                            Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(start, decay) {
                                val delta = value - last
                                last = value
                                model.scrollBy(-delta.x, -delta.y)
                            }
                        } finally {
                            model.settle()
                        }
                    }
                }
            },
    ) {
        model.renderVersion
        val layout = model.layout ?: return@Canvas
        val renderer = model.renderer ?: return@Canvas
        val document = model.document ?: return@Canvas
        drawRect(background)
        drawIntoCanvas { canvas ->
            painter.draw(canvas.nativeCanvas, model, layout, renderer, document)
        }
    }
}

internal const val VIEWER_CANVAS_TAG = "viewer-canvas"

/** Movement in the zoom factor that starts a pinch. zh-CN: 开始双指缩放所需的缩放系数变化. */
private const val ZOOM_SLOP = 0.04f

/** Slowest fling, in dp per second. zh-CN: 惯性滑动的最低速度, 单位 dp/s. */
private const val MIN_FLING_VELOCITY = 50f

/**
 * Draws the visible pages onto the native canvas; holds the paints so frames allocate nothing.
 * zh-CN: 把可见页画到原生画布上; 持有画笔, 使每帧不分配对象.
 */
private class PagePainter {

    private val plain = Paint(Paint.FILTER_BITMAP_FLAG)
    private val inverted = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(NIGHT_MATRIX)) }
    private val paper = Paint().apply { color = android.graphics.Color.WHITE }
    private val nightPaper = Paint().apply { color = NIGHT_PAPER }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT }
    private val currentHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CURRENT_HIGHLIGHT }
    private val page = RectF()
    private val part = RectF()
    private val clip = Path()

    fun draw(canvas: android.graphics.Canvas, model: ViewerViewModel, layout: ViewerLayout, renderer: ViewerRenderer, document: ViewerDocument) {
        val position = model.position
        val zoom = position.zoom
        val night = model.nightMode
        val bitmapPaint = if (night) inverted else plain
        val current = model.hits.getOrNull(model.hitIndex)
        for (index in layout.visiblePages(position)) {
            val left = layout.viewportX(layout.pageLeft(zoom), position)
            val top = layout.viewportY(layout.pageTop(index, zoom), position)
            page.set(left, top, left + layout.pageWidth(zoom), top + layout.pageHeight(index, zoom))
            canvas.drawRect(page, if (night) nightPaper else paper)
            drawLayers(canvas, model, layout, renderer, document, index, bitmapPaint)
            val scale = page.width() / document.pages[index].width
            if (night) {
                val rects = model.imageRectsOf(index)
                if (!rects.isNullOrEmpty()) {
                    clip.rewind()
                    for (rect in rects) clip.addRect(left + rect[0] * scale, top + rect[1] * scale, left + rect[2] * scale, top + rect[3] * scale, Path.Direction.CW)
                    canvas.withClip(clip) { drawLayers(canvas, model, layout, renderer, document, index, plain) }
                }
            }
            for (hit in model.hits) {
                if (hit.page != index) continue
                val paint = if (hit === current) currentHighlight else highlight
                for (rect in hit.rects) {
                    part.set(left + rect[0] * scale, top + rect[1] * scale, left + rect[2] * scale, top + rect[3] * scale)
                    canvas.drawRect(part, paint)
                }
            }
        }
    }

    private fun drawLayers(
        canvas: android.graphics.Canvas,
        model: ViewerViewModel,
        layout: ViewerLayout,
        renderer: ViewerRenderer,
        document: ViewerDocument,
        index: Int,
        paint: Paint,
    ) {
        val position = model.position
        val zoom = position.zoom
        val safe = document.pages[index].regionSafe
        val base = bestBase(model, layout, renderer, index, zoom, safe)
        if (base != null) canvas.drawBitmap(base, null, page, paint)
        if (!safe || !RenderPlan.needsDetail(layout, index, model.tileZoom)) return
        for (tile in RenderPlan.tiles(layout, index, position, model.tileZoom)) {
            val bitmap = renderer.get(tile) ?: continue
            val scale = page.width() / tile.pageWidth
            part.set(
                page.left + tile.left * scale,
                page.top + tile.top * scale,
                page.left + (tile.left + tile.width) * scale,
                page.top + (tile.top + tile.height) * scale,
            )
            canvas.drawBitmap(bitmap, null, part, paint)
        }
    }

    private fun bestBase(model: ViewerViewModel, layout: ViewerLayout, renderer: ViewerRenderer, index: Int, zoom: Float, safe: Boolean): Bitmap? {
        if (!safe) RenderPlan.enlarged(layout, index, model.tileZoom)?.let { key -> renderer.get(key)?.let { return it } }
        renderer.get(RenderPlan.base(layout, index, zoom))?.let { return it }
        val other = if (zoom < RenderPlan.HALF_WIDTH_ZOOM) 1f else RenderPlan.HALF_WIDTH_ZOOM / 2
        renderer.get(RenderPlan.base(layout, index, other))?.let { return it }
        return if (model.thumbnailWidth > 0f) renderer.get(RenderPlan.thumbnail(layout, index, model.thumbnailWidth)) else null
    }

    companion object {

        /**
         * Night colors: each channel becomes `240 - 0.88 * value`, so white paper turns near black (16) and black text
         * light gray (240) rather than full contrast.
         * zh-CN: 夜间颜色: 每个通道变为 `240 - 0.88 * value`, 白纸变为接近黑色 (16), 黑字变为浅灰 (240), 不用最大对比度.
         */
        val NIGHT_MATRIX = floatArrayOf(
            -0.88f, 0f, 0f, 0f, 240f,
            0f, -0.88f, 0f, 0f, 240f,
            0f, 0f, -0.88f, 0f, 240f,
            0f, 0f, 0f, 1f, 0f,
        )

        val NIGHT_PAPER = Color(0xFF101010).toArgb()
        val HIGHLIGHT = Color(0x66FFD54F).toArgb()
        val CURRENT_HIGHLIGHT = Color(0x99FF9800).toArgb()

    }

}

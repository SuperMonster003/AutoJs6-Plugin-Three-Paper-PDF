package io.github.supermonster003.autojs6.plugin.three.paper.pdf.render

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSession
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.RenderPool
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract

/**
 * `render` and `renderToFile` (roadmap P2.3, D9, D17, D30, protocol "Rendering"). The request is checked and the
 * bitmap size worked out (at most `MAX_RENDER_PIXELS`, else `LIMIT_EXCEEDED` with a downsampling hint) before a slot of
 * the render pool is taken; the page is drawn by the system renderer on a white or transparent ARGB_8888 bitmap, which
 * is recycled when the answer is ready. Calls on one document are already serialized by the session.
 * zh-CN: `render` 与 `renderToFile` (路线图 P2.3, D9, D17, D30, 协议 "Rendering" 一节). 先检查请求并算出位图尺寸 (至多
 * `MAX_RENDER_PIXELS`, 否则报带降采样建议的 `LIMIT_EXCEEDED`), 再占用渲染池的名额; 页面由系统渲染器画在白底或透明的
 * ARGB_8888 位图上, 应答就绪后回收位图. 同一文档的调用已由会话串行化.
 */
internal object PageRendering {

    /**
     * `render`: a `sharedMemory` answer on API 27+ unless the host asks for `file` or the region cannot be created,
     * else a PNG of at most `MAX_RENDER_FILE_BYTES` in [fallbackOutput]. [keepUntilReplied] receives the region, which
     * must stay open until the answer is written.
     * zh-CN: `render`: API 27+ 上返回 `sharedMemory` 应答, 除非宿主要求 `file` 或无法创建区域; 否则把不超过
     * `MAX_RENDER_FILE_BYTES` 的 PNG 写入 [fallbackOutput]. [keepUntilReplied] 接收区域, 区域须保持打开直到应答写出.
     */
    fun render(
        call: PdfSession.Call,
        pool: RenderPool,
        options: PdfOptions,
        fallbackOutput: ParcelFileDescriptor?,
        keepUntilReplied: (AutoCloseable) -> Unit,
    ): Map<String, Any?> {
        val transport = checkNotNull(options.choice(PdfContract.KEY_TRANSPORT, PdfContract.TRANSPORTS, PdfContract.TRANSPORT_SHARED_MEMORY))
        val target = target(call, options)
        val sharedMemory = transport == PdfContract.TRANSPORT_SHARED_MEMORY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        if (!sharedMemory) requireFallback(fallbackOutput)
        return pool.run(call.stop) {
            draw(call, target) { bitmap ->
                if (sharedMemory) {
                    BitmapTransport.region(bitmap)?.let { region ->
                        keepUntilReplied(region)
                        return@draw mapOf(
                            PdfContract.KEY_TRANSPORT to PdfContract.TRANSPORT_SHARED_MEMORY,
                            PdfContract.KEY_SHARED_MEMORY to region,
                            PdfContract.KEY_WIDTH to bitmap.width,
                            PdfContract.KEY_HEIGHT to bitmap.height,
                            PdfContract.KEY_ROW_BYTES to bitmap.rowBytes,
                        )
                    }
                }
                val sizeBytes = BitmapTransport.encode(
                    bitmap, Bitmap.CompressFormat.PNG, PdfContract.MAX_QUALITY, requireFallback(fallbackOutput), PdfContract.MAX_RENDER_FILE_BYTES, call.stop,
                )
                mapOf(
                    PdfContract.KEY_TRANSPORT to PdfContract.TRANSPORT_FILE,
                    PdfContract.KEY_WIDTH to bitmap.width,
                    PdfContract.KEY_HEIGHT to bitmap.height,
                    PdfContract.KEY_SIZE_BYTES to sizeBytes,
                )
            }
        }
    }

    /**
     * `renderToFile`: the page encoded as `format` (`png` by default; `quality` applies to `jpeg` and `webp`) into
     * [output]. `jpeg` has no alpha channel, so it refuses `transparent` (appendix D Q12).
     * zh-CN: `renderToFile`: 把页面按 `format` (默认 `png`; `quality` 用于 `jpeg` 与 `webp`) 编码写入 [output]. `jpeg` 没有 alpha
     * 通道, 因此拒绝 `transparent` (附录 D Q12).
     */
    fun renderToFile(call: PdfSession.Call, pool: RenderPool, options: PdfOptions, output: ParcelFileDescriptor?): Map<String, Any?> {
        val format = checkNotNull(options.choice(PdfContract.KEY_FORMAT, PdfContract.RENDER_FORMATS, PdfContract.FORMAT_PNG))
        val quality = options.quality()
        val target = target(call, options)
        if (target.transparent && format == PdfContract.FORMAT_JPEG) {
            throw PdfFailure.invalid("${PdfContract.FORMAT_JPEG} has no alpha channel; drop ${PdfContract.KEY_TRANSPARENT} or use ${PdfContract.FORMAT_PNG} or ${PdfContract.FORMAT_WEBP}")
        }
        val destination = output ?: throw PdfFailure.invalid("renderToFile needs an output")
        return pool.run(call.stop) {
            draw(call, target) { bitmap ->
                val sizeBytes = BitmapTransport.encode(bitmap, compressFormat(format), quality, destination, Long.MAX_VALUE, call.stop)
                mapOf(
                    PdfContract.KEY_WIDTH to bitmap.width,
                    PdfContract.KEY_HEIGHT to bitmap.height,
                    PdfContract.KEY_FORMAT to format,
                    PdfContract.KEY_SIZE_BYTES to sizeBytes,
                )
            }
        }
    }

    /** What to draw: page [page] (from 1) of [frame] on a [width] x [height] bitmap. zh-CN: 要画的内容: 页框为 [frame] 的第 [page] 页 (从 1 起), 位图 [width] x [height]. */
    private class Target(val page: Int, val frame: PageFrame, val width: Int, val height: Int, val transparent: Boolean)

    private fun target(call: PdfSession.Call, options: PdfOptions): Target {
        val pdf = call.pdf
        val page = options.page(pdf.numberOfPages)
        val size = options.renderSize()
        val transparent = options.boolean(PdfContract.KEY_TRANSPARENT, false)
        val frame = PageFrame.of(pdf.getPage(page - 1))
        val (width, height) = size.pixels(frame.displayWidth, frame.displayHeight)
        return Target(page, frame, width, height, transparent)
    }

    private fun <T> draw(call: PdfSession.Call, target: Target, use: (Bitmap) -> T): T {
        val renderer = call.resource(PageRenderer.RESOURCE) { PageRenderer.open(call) }
        val bitmap = createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(if (target.transparent) Color.TRANSPARENT else Color.WHITE)
            renderer.draw(target.page, target.frame, bitmap)
            call.stop.checkStop()
            return use(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun requireFallback(fallbackOutput: ParcelFileDescriptor?): ParcelFileDescriptor =
        fallbackOutput ?: throw PdfFailure.invalid("render needs fallbackOutput for the ${PdfContract.TRANSPORT_FILE} transport")

    /**
     * Encoder of a `renderToFile` format; below API 30 `WEBP` is lossy except at quality 100.
     * zh-CN: `renderToFile` 格式对应的编码器; API 30 以下的 `WEBP` 除质量 100 外均为有损.
     */
    private fun compressFormat(format: String): Bitmap.CompressFormat = when (format) {
        PdfContract.FORMAT_JPEG -> Bitmap.CompressFormat.JPEG
        PdfContract.FORMAT_WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        else -> Bitmap.CompressFormat.PNG
    }

}

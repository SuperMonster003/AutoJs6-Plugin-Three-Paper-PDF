package io.github.supermonster003.autojs6.plugin.three.paper.pdf.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfSession
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.io.IOException

/**
 * The system `PdfRenderer` of one document (roadmap D9, D30): it reads a duplicate of the source, or, for an encrypted
 * document, a decrypted copy in the document's temp directory that [close] deletes. It lives as a session resource
 * ([RESOURCE]), so calls on the document reach it one at a time and an idle release (appendix D Q10) closes it.
 * zh-CN: 一个文档的系统 `PdfRenderer` (路线图 D9, D30): 读取源的副本; 加密文档则读取文档临时目录中的解密副本, 由 [close] 删除.
 * 它作为会话资源 ([RESOURCE]) 存在, 因此对文档的调用逐个到达, 空闲释放 (附录 D Q10) 会关闭它.
 */
internal class PageRenderer private constructor(private val renderer: PdfRenderer, private val decrypted: File?) : AutoCloseable {

    /**
     * Draws page [number] (from 1) of [frame] onto [bitmap], scaled so the page as displayed fills the bitmap. The
     * bitmap is ARGB_8888 and already holds the background.
     * zh-CN: 把页框为 [frame] 的第 [number] 页 (从 1 起) 画到 [bitmap] 上, 缩放到显示后的页面填满位图. 位图为 ARGB_8888 且已
     * 填好背景.
     */
    fun draw(number: Int, frame: PageFrame, bitmap: Bitmap) {
        if (number > renderer.pageCount) {
            throw PdfFailure(PdfErrorCodes.INVALID_PDF, "the system renderer finds ${renderer.pageCount} pages, page $number cannot be drawn")
        }
        renderer.openPage(number - 1).use { page ->
            // The transform maps the page as displayed, in points from its top left corner, to the bitmap; the float
            // size keeps the scale exact where the renderer's integer page size would round it. Older renderers apply
            // a transform to the unrotated, uncropped page, so there the renderer fits the displayed page itself.
            // zh-CN: 变换把显示后的页面 (从左上角起算的点) 映射到位图; 使用浮点尺寸, 避免渲染器的整数页面尺寸带来舍入. 旧版渲染器
            // 把变换作用于未旋转, 未裁剪的页面, 因此在那里由渲染器自己把显示后的页面铺满位图.
            val transform = if (Build.VERSION.SDK_INT >= TRANSFORM_ON_DISPLAYED_PAGE) {
                Matrix().apply { setScale((bitmap.width / frame.displayWidth).toFloat(), (bitmap.height / frame.displayHeight).toFloat()) }
            } else {
                null
            }
            page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    override fun close() {
        try {
            renderer.close()
        } finally {
            decrypted?.delete()
        }
    }

    companion object {

        /** Session resource key. zh-CN: 会话资源键. */
        const val RESOURCE = "renderer"

        /** Name of the decrypted copy in the document's temp directory. zh-CN: 文档临时目录中解密副本的文件名. */
        const val DECRYPTED_NAME = "render-decrypted.pdf"

        /**
         * The first level whose renderer applies a transform to the page as displayed (rotation and crop box applied).
         * Android 7 maps a transform from the unrotated page in user space, which draws rotated pages turned and
         * cropped pages shifted; below this level [draw] passes no transform and lets the renderer fit the displayed
         * page into the bitmap. Verified on API 24, 25, 27, 28 and 33 (roadmap P2.3).
         * zh-CN: 渲染器把变换作用于显示后页面 (已应用旋转与裁剪框) 的首个级别. Android 7 从未旋转页面的用户空间映射变换, 旋转页
         * 会画歪, 裁剪页会错位; 低于此级别时 [draw] 不传变换, 由渲染器把显示后的页面铺满位图. 已在 API 24, 25, 27, 28 与 33
         * 核对 (路线图 P2.3).
         */
        private const val TRANSFORM_ON_DISPLAYED_PAGE = Build.VERSION_CODES.P

        /**
         * The renderer of the document [call] holds. The system renderer cannot open a password-protected document, so an
         * encrypted one is parsed again with its password and saved without security first (roadmap D30); the session's
         * own document keeps its encryption state.
         * zh-CN: [call] 所持文档的渲染器. 系统渲染器无法打开受密码保护的文档, 因此加密文档先用其密码重新解析, 去掉安全设置后另存
         * (路线图 D30); 会话自身的文档保持加密状态不变.
         */
        fun open(call: PdfSession.Call): PageRenderer {
            if (!call.pdf.isEncrypted) return PageRenderer(create(call.duplicateSource()), decrypted = null)
            val copy = File(call.session.directory, DECRYPTED_NAME)
            try {
                call.loadSeparately().use { loaded ->
                    loaded.document.isAllSecurityToBeRemoved = true
                    loaded.document.save(copy)
                }
                call.stop.checkStop()
                return PageRenderer(create(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)), copy)
            } catch (t: Throwable) {
                copy.delete()
                throw t
            }
        }

        /** Takes [descriptor] over: the renderer closes it, or this does when the renderer refuses it. zh-CN: 接管 [descriptor]: 由渲染器关闭, 渲染器拒绝时由本函数关闭. */
        private fun create(descriptor: ParcelFileDescriptor): PdfRenderer = try {
            PdfRenderer(descriptor)
        } catch (e: IOException) {
            runCatching { descriptor.close() }
            throw PdfFailure(PdfErrorCodes.INVALID_PDF, "the system renderer cannot read the document: ${e.message}")
        } catch (e: SecurityException) {
            runCatching { descriptor.close() }
            throw PdfFailure(PdfErrorCodes.INVALID_PDF, "the system renderer refuses the document: ${e.message}")
        } catch (t: Throwable) {
            runCatching { descriptor.close() }
            throw t
        }

    }

}

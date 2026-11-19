package io.github.supermonster003.autojs6.plugin.three.paper.pdf.write

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.CountingOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Checks that the system renderer opens a written file: [check] answers null when it does, else what failed.
 * zh-CN: 检查系统渲染器能否打开写出的文件: [check] 能打开时返回 null, 否则返回失败的原因.
 */
internal fun interface RenderCheck {

    fun check(file: File, pageCount: Int): String?

    companion object {

        /** Gives the JVM tests no renderer. zh-CN: 供 JVM 测试使用的空检查. */
        val NONE = RenderCheck { _, _ -> null }

    }

}

/**
 * The renderer half of the check (roadmap D65): the system `PdfRenderer` opens the file, finds every page and draws
 * the first one at most [MAX_SIDE] pixels on its longer side.
 * zh-CN: 校验中由渲染器完成的一半 (路线图 D65): 系统 `PdfRenderer` 打开文件, 识别出全部页面并绘制第一页, 长边至多 [MAX_SIDE] 像素.
 */
internal object PdfRendererCheck : RenderCheck {

    private const val MAX_SIDE = 512

    override fun check(file: File, pageCount: Int): String? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount != pageCount) return "the system renderer finds ${renderer.pageCount} pages instead of $pageCount"
                renderer.openPage(0).use { page ->
                    val scale = MAX_SIDE.toFloat() / max(page.width, page.height).coerceAtLeast(1)
                    val width = ceil(page.width * scale).toInt().coerceAtLeast(1)
                    val height = ceil(page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        null
    } catch (e: IOException) {
        "the system renderer cannot open the written file: ${e.message}"
    } catch (e: RuntimeException) {
        "the system renderer fails on the written file: ${e.message}"
    }

}

/**
 * The writing of `compress` (roadmap D48, D65, P6.7; wired by P6.6): [write] tries the [ObjectStreamWriter] into a
 * temporary file of [directory], checks it ([ReadBackCheck], then [render]) and copies it to the output; when writing
 * or checking fails, or [objectStreams] is false, the document is written by `PDDocument.save` (classic
 * cross-reference table) and, after a failure, a warning says why. The output is never encrypted (`PdfWriting`, Q30).
 * Cancellation ([stop]) is not a failure: it ends the call. [onVerifying] tells the caller the check starts (the
 * `verifying` stage of contract version 2); [onlyProducer] keeps `producer` alone in the document information
 * (`removeMetadata`, `PdfWriting.stamp`); [damage] is a test hook that may change the written file before the check.
 * zh-CN: `compress` 的写出 (路线图 D48, D65, P6.7; 由 P6.6 接线): [write] 先用 [ObjectStreamWriter] 写到 [directory] 的临时文件,
 * 校验 ([ReadBackCheck], 再 [render]) 后复制到输出; 写出或校验失败, 或 [objectStreams] 为 false 时改用 `PDDocument.save` (经典交叉
 * 引用表) 写出, 失败时另给一条说明原因的警告. 输出从不加密 (`PdfWriting`, Q30). 取消 ([stop]) 不算失败, 它直接结束调用.
 * [onVerifying] 通知调用方开始校验 (契约版本 2 的 `verifying` 阶段); [onlyProducer] 使文档信息只保留 `producer` (`removeMetadata`,
 * `PdfWriting.stamp`); [damage] 是测试钩子, 可在校验前改动写出的文件.
 */
internal object CompactWriting {

    /** The written size, whether object streams were used, and the warnings. zh-CN: 写出的大小, 是否使用对象流, 以及警告. */
    data class Result(val sizeBytes: Long, val objectStreams: Boolean, val warnings: List<String>)

    fun write(
        document: PDDocument,
        output: OutputStream,
        directory: File,
        stop: StopSignal = StopSignal.NEVER,
        objectStreams: Boolean = true,
        render: RenderCheck = PdfRendererCheck,
        onVerifying: () -> Unit = {},
        onlyProducer: Boolean = false,
        damage: ((File) -> Unit)? = null,
    ): Result {
        PdfWriting.checkWritable(document)
        if (!objectStreams) return Result(PdfWriting.write(document, output, stop, onlyProducer), false, emptyList())
        if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
        PdfWriting.stamp(document, onlyProducer)
        val file = File.createTempFile("objstm-", ".pdf", directory)
        try {
            val reason = try {
                FileOutputStream(file).use { stream ->
                    BufferedOutputStream(stream, BUFFER).use { ObjectStreamWriter(document, stop).write(it) }
                }
                damage?.invoke(file)
                onVerifying()
                ReadBackCheck.verify(document, file, directory, stop) ?: render.check(file, document.numberOfPages)
            } catch (e: IOException) {
                "the writer failed: ${e.message}"
            } catch (e: RuntimeException) {
                "the writer failed: $e"
            }
            if (reason == null) return Result(copy(file, output, stop), true, emptyList())
            val warning = "object streams were not used because $reason; the output has a classic cross-reference table"
            return Result(PdfWriting.write(document, output, stop, onlyProducer), false, listOf(warning))
        } finally {
            file.delete()
        }
    }

    private fun copy(file: File, output: OutputStream, stop: StopSignal): Long {
        val counting = CountingOutputStream(output, Long.MAX_VALUE, stop)
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                counting.write(buffer, 0, read)
            }
        }
        counting.flush()
        return counting.count
    }

    private const val BUFFER = 64 * 1024

}

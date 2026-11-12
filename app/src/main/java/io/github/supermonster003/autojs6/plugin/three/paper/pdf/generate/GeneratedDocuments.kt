package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import org.autojs.plugin.pdf.api.PdfContract
import java.io.FileOutputStream
import java.util.Calendar
import kotlin.math.abs

/**
 * Writes a document made by a generation operation to the single output of the `run` operation: `title` when given,
 * `creationDate`, then [PdfWriting] (`producer`, `modificationDate`). [pageSize] restores the exact page size where the
 * producer could only give whole points (`PdfDocument`) or mils (the print adapter). The completion holds `pageCount`,
 * `sizeBytes` and `warnings`.
 * zh-CN: 把生成操作产生的文档写到 `run` 操作唯一的输出: 给出时写 `title`, 写 `creationDate`, 然后交给 [PdfWriting] (`producer`,
 * `modificationDate`). 生成方只能给出整磅 (`PdfDocument`) 或密耳 (打印适配器) 时, [pageSize] 恢复精确的页面尺寸. 完成结果含
 * `pageCount`, `sizeBytes` 与 `warnings`.
 */
internal object GeneratedDocuments {

    fun write(
        document: PDDocument,
        title: String?,
        operation: Operations.Operation,
        warnings: Collection<String>,
        pageSize: PageLayout.PageSize.Fixed? = null,
    ): Map<String, Any?> {
        operation.stop.checkStop()
        operation.progress(PdfContract.STAGE_WRITING, 0, 1)
        if (pageSize != null) {
            for (page in document.pages) {
                val box = page.mediaBox
                // Only rounding is undone; a page the content sized itself (CSS @page) stays as it is.
                // zh-CN: 只撤销取整; 由内容自定尺寸的页面 (CSS @page) 保持原样.
                if (abs(box.width - pageSize.width) < 1f && abs(box.height - pageSize.height) < 1f) {
                    page.mediaBox = PDRectangle(pageSize.width, pageSize.height)
                    page.cropBox = null
                }
            }
        }
        val information = document.documentInformation
        if (!title.isNullOrEmpty()) information.title = title
        information.creationDate = Calendar.getInstance()
        document.documentInformation = information
        val size = PdfWriting.write(document, outputStream(operation.outputs.single()), operation.stop)
        return buildMap {
            put(PdfContract.KEY_PAGE_COUNT, document.numberOfPages)
            put(PdfContract.KEY_SIZE_BYTES, size)
            if (warnings.isNotEmpty()) put(PdfContract.KEY_WARNINGS, warnings.distinct().toTypedArray())
        }
    }

    private fun outputStream(output: ParcelFileDescriptor) = FileOutputStream(output.fileDescriptor)

}

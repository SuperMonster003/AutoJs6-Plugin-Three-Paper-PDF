package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File

/**
 * A parsed document with the PDFBox objects it owns; closing it keeps the source open.
 * zh-CN: 已解析的文档及其持有的 PDFBox 对象; 关闭它不关闭源.
 */
class LoadedPdf internal constructor(val document: PDDocument, private val scratch: ScratchFile) : AutoCloseable {

    override fun close() {
        try {
            document.close()
        } finally {
            scratch.close()
        }
    }

}

/**
 * Opens documents with PDFBox from a [PdfSource] (roadmap D12): a missing password is `PASSWORD_REQUIRED`, a wrong one
 * `WRONG_PASSWORD`, a read failure of the source `IO_ERROR` and anything PDFBox cannot parse `INVALID_PDF`. Object
 * streams above [mainMemoryBytes] spill into a scratch file in the temp directory of the document.
 * zh-CN: 用 PDFBox 从 [PdfSource] 打开文档 (路线图 D12): 缺少密码报 `PASSWORD_REQUIRED`, 密码错误报 `WRONG_PASSWORD`, 源读取
 * 失败报 `IO_ERROR`, PDFBox 无法解析的报 `INVALID_PDF`. 超过 [mainMemoryBytes] 的对象流溢出到文档临时目录中的暂存文件.
 */
object PdfLoader {

    const val DEFAULT_MAIN_MEMORY_BYTES = 16L * 1024 * 1024

    fun load(source: PdfSource, password: String?, tempDir: File?, mainMemoryBytes: Long = DEFAULT_MAIN_MEMORY_BYTES): LoadedPdf {
        val setting = MemoryUsageSetting.setupMixed(mainMemoryBytes).let { if (tempDir != null) it.setTempDir(tempDir) else it }
        val scratch = ScratchFile(setting)
        val input = PdfSourceRead(source)
        try {
            val parser = PDFParser(input, password.orEmpty(), scratch)
            parser.parse()
            return LoadedPdf(parser.pdDocument, scratch)
        } catch (e: Throwable) {
            runCatching { input.close() }
            runCatching { scratch.close() }
            throw mapFailure(e, password)
        }
    }

    internal fun mapFailure(error: Throwable, password: String?): Throwable = when (error) {
        is PdfFailure, is Error -> error
        is InvalidPasswordException -> if (password.isNullOrEmpty()) {
            PdfFailure(PdfErrorCodes.PASSWORD_REQUIRED, "the document is encrypted and needs a password")
        } else {
            PdfFailure(PdfErrorCodes.WRONG_PASSWORD, "the password does not open the document")
        }
        is PdfSourceException -> PdfFailure(PdfErrorCodes.IO_ERROR, error.message)
        else -> PdfFailure(PdfErrorCodes.INVALID_PDF, listOfNotNull(error::class.java.simpleName, error.message).joinToString(": "))
    }

}

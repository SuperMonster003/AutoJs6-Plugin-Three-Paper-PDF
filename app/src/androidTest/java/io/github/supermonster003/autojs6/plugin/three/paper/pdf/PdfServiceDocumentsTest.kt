package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.eventually
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.openSample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.PdfCapabilityKeys
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The document model of the PDF service through Binder (roadmap P2.1): binding, `getInfo` / capabilities, opening a
 * sample, the error codes of `open`, at most `MAX_DOCUMENTS_PER_HOST` handles per host process, `SESSION_CLOSED` after
 * `close()`, and the temp directory `cache/pdf/<uid>-<pid>/` that disappears with the last binding.
 * zh-CN: 经 Binder 验证 PDF 服务的文档模型 (路线图 P2.1): 绑定, `getInfo` / 能力, 打开样例, `open` 的错误码, 每个宿主进程至多
 * `MAX_DOCUMENTS_PER_HOST` 个句柄, `close()` 之后的 `SESSION_CLOSED`, 以及随最后一个绑定消失的临时目录 `cache/pdf/<uid>-<pid>/`.
 */
@RunWith(AndroidJUnit4::class)
class PdfServiceDocumentsTest {

    private val hostDirectory: File
        get() = File(File(context.cacheDir, PdfEngine.ROOT_NAME), "${Process.myUid()}-${Process.myPid()}")

    @Test
    fun bindingAnswersIdentityAndCeilings() = withPdfPlugin { plugin ->
        assertEquals(ThreePaperPdfContract.PLUGIN_ID, plugin.info.id)
        val capabilities = plugin.capabilities
        assertEquals(32, capabilities.getInt(PdfCapabilityKeys.MAX_OPEN_DOCUMENTS))
        assertEquals(16_000_000L, capabilities.getLong(PdfCapabilityKeys.MAX_RENDER_PIXELS))
        assertEquals(2, capabilities.getInt(PdfCapabilityKeys.RENDER_CONCURRENCY))
        assertEquals("2.0.27.0", capabilities.getString(PdfCapabilityKeys.PDFBOX_VERSION))
    }

    @Test
    fun opensASampleAndAnswersSessionClosedAfterClose() = withPdfPlugin { plugin ->
        val document = plugin.openSample("text.pdf")
        assertEquals(1, hostDirectory.listFiles()?.size)
        document.close()
        val answer = document.getText(request { putString(PdfContract.KEY_REQUEST_ID, "after-close") })
        assertEquals(PdfErrorCodes.SESSION_CLOSED, answer.getString(PdfContract.KEY_ERROR_CODE))
        assertEquals("after-close", answer.getString(PdfContract.KEY_REQUEST_ID))
        assertEquals(PdfContract.CONTRACT_VERSION, answer.getInt(PdfContract.KEY_CONTRACT_VERSION))
        assertEquals(PdfErrorCodes.SESSION_CLOSED, document.info.getString(PdfContract.KEY_ERROR_CODE))
        // close() is idempotent and the temp directory of the document is gone. zh-CN: close() 幂等, 文档的临时目录已删除.
        document.close()
        assertTrue(eventually { !hostDirectory.exists() })
    }

    @Test
    fun theDocumentAfterTheLimitOfAHostIsRefused() = withPdfPlugin { plugin ->
        val documents = ArrayList<IPdfDocument>()
        try {
            repeat(PdfContract.MAX_DOCUMENTS_PER_HOST) { documents += plugin.openSample("text.pdf") }
            assertEquals(PdfContract.MAX_DOCUMENTS_PER_HOST, hostDirectory.listFiles()?.size)
            assertFailure(PdfErrorCodes.LIMIT_EXCEEDED, IllegalStateException::class.java) { plugin.openSample("text.pdf") }
            documents.removeAt(0).close()
            documents += plugin.openSample("text.pdf")
        } finally {
            documents.forEach { it.close() }
        }
        assertTrue(eventually { !hostDirectory.exists() })
    }

    @Test
    fun openReportsPasswordFormatAndRequestErrors() = withPdfPlugin { plugin ->
        assertFailure(PdfErrorCodes.PASSWORD_REQUIRED, IllegalStateException::class.java) { plugin.openSample("encrypted.pdf") }
        assertFailure(PdfErrorCodes.WRONG_PASSWORD, IllegalStateException::class.java) { plugin.openSample("encrypted.pdf", "wrong") }
        plugin.openSample("encrypted.pdf", "paper-user").close()
        plugin.openSample("encrypted.pdf", "paper-owner").close()

        val damaged = File(context.cacheDir, "samples/damaged.pdf").apply {
            parentFile?.mkdirs()
            writeText("%PDF-1.7\nthis is not a PDF body\n")
        }
        ParcelFileDescriptor.open(damaged, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
            assertFailure(PdfErrorCodes.INVALID_PDF, IllegalStateException::class.java) { plugin.open(source, request()) }
        }
        sample("text.pdf").use { source ->
            assertFailure(PdfErrorCodes.INVALID_ARGUMENT, IllegalArgumentException::class.java) { plugin.open(source, Bundle()) }
        }
        sample("text.pdf").use { source ->
            val future = request { putInt(PdfContract.KEY_CONTRACT_VERSION, 99) }
            assertFailure(PdfErrorCodes.UNSUPPORTED, IllegalStateException::class.java) { plugin.open(source, future) }
        }
        sample("text.pdf").use { source ->
            val long = request { putString(PdfContract.KEY_PASSWORD, "x".repeat(PdfContract.MAX_PASSWORD_LENGTH + 1)) }
            assertFailure(PdfErrorCodes.INVALID_ARGUMENT, IllegalArgumentException::class.java) { plugin.open(source, long) }
        }
        // A pipe cannot be read at positions (protocol: sources are seekable). zh-CN: 管道不能按位置读取 (协议: 源须可定位).
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            assertFailure(PdfErrorCodes.INVALID_ARGUMENT, IllegalArgumentException::class.java) { plugin.open(pipe[0], request()) }
        } finally {
            pipe.forEach { it.close() }
        }
        assertTrue("failed opens leave no temp directory", eventually { !hostDirectory.exists() })
    }

    @Test
    fun theLastUnbindClosesEveryDocumentAndRemovesItsTempFiles() {
        withPdfPlugin { plugin ->
            plugin.openSample("text.pdf")
            plugin.openSample("outline.pdf")
            assertEquals(2, hostDirectory.listFiles()?.size)
        }
        assertTrue("the host directory goes with the last binding", eventually { !hostDirectory.exists() })
    }

    @Test
    fun runRejectsUnknownOperations() = withPdfPlugin { plugin ->
        assertFalse("no operation id is known", plugin.cancel(1))
        val unknown = request { putString(PdfContract.KEY_OP, "noSuchOp") }
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, IllegalArgumentException::class.java) { plugin.run(unknown, emptyArray(), null, null) }
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, IllegalArgumentException::class.java) { plugin.run(Bundle(), emptyArray(), null, null) }
    }

    private fun <T : RuntimeException> assertFailure(code: String, type: Class<T>, call: () -> Unit) {
        val error = assertThrows(type) { call() }
        assertEquals(error.message, code, PdfErrorCodes.decode(error.message)?.first)
    }

}

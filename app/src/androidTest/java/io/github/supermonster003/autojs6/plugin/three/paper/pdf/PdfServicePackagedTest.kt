package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.os.Binder
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
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfPackagedService
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfService
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfCapabilityKeys
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The packaged-app entry of the PDF service through Binder (roadmap Q145 (b), D43, D44): the same engine answers open,
 * render and run; its callers keep their own temp directory `cache/pdf/k<uid>-<pid>/` and lose it with the last binding
 * of that entry only; a call through the entry from a uid without `org.autojs.plugin.pdf.permission.RUNTIME`, and a
 * handle call from another uid, end with `PERMISSION_DENIED`. Another uid is simulated with
 * `Binder.restoreCallingIdentity`, since the service runs in the test process.
 * zh-CN: 经 Binder 验证 PDF 服务的打包应用入口 (路线图 Q145 (b), D43, D44): 同一引擎响应 open, render 与 run; 其调用方有自己的
 * 临时目录 `cache/pdf/k<uid>-<pid>/`, 只随该入口的最后一个绑定删除; 没有 `org.autojs.plugin.pdf.permission.RUNTIME` 的 uid
 * 经该入口的调用, 以及其他 uid 对句柄的调用, 都以 `PERMISSION_DENIED` 结束. 由于服务运行在测试进程中, 其他 uid 用
 * `Binder.restoreCallingIdentity` 模拟.
 */
@RunWith(AndroidJUnit4::class)
class PdfServicePackagedTest {

    private val packaged = ThreePaperPdfPackagedService::class.java

    private val pdfRoot: File
        get() = File(context.cacheDir, PdfEngine.ROOT_NAME)

    private val packagedDirectory: File
        get() = File(pdfRoot, "k${Process.myUid()}-${Process.myPid()}")

    private val signedDirectory: File
        get() = File(pdfRoot, "${Process.myUid()}-${Process.myPid()}")

    private lateinit var outputs: File

    @Before
    fun setUp() {
        outputs = File(context.cacheDir, "packaged-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        outputs.deleteRecursively()
    }

    @Test
    fun thePackagedEntryOpensRendersAndRunsWithItsOwnTempDirectory() {
        withPdfPlugin(packaged) { plugin ->
            assertEquals(ThreePaperPdfContract.PLUGIN_ID, plugin.info.id)
            assertEquals(2, plugin.capabilities.getInt(PdfCapabilityKeys.RENDER_CONCURRENCY))
            val document = plugin.openSample("outline.pdf")
            assertEquals(1, packagedDirectory.listFiles()?.size)
            assertFalse("the signed entry has no directory of this caller", signedDirectory.exists())
            val info = document.info
            assertNull(info.getString(PdfContract.KEY_ERROR_CODE))
            assertEquals(6, info.getInt(PdfContract.KEY_PAGE_COUNT))

            val png = File(outputs, "page.png")
            val rendered = ParcelFileDescriptor.open(png, WRITE).use { output ->
                document.renderToFile(request { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_DPI, 72) }, output)
            }
            assertNull(rendered.getString(PdfContract.KEY_ERROR_MESSAGE), rendered.getString(PdfContract.KEY_ERROR_CODE))
            assertTrue("a PNG was written", png.length() > 8 && png.readBytes().copyOfRange(1, 4).decodeToString() == "PNG")

            val merged = File(outputs, "merged.pdf")
            val recorder = Recorder()
            listOf(sample("text.pdf"), sample("outline.pdf")).let { inputs ->
                try {
                    ParcelFileDescriptor.open(merged, WRITE).use { output ->
                        assertTrue(plugin.run(request { putString(PdfContract.KEY_OP, PdfOps.MERGE) }, inputs.toTypedArray(), output, recorder) > 0)
                    }
                } finally {
                    inputs.forEach { it.close() }
                }
            }
            assertTrue("the merge did not end in time", recorder.ended.await(60, TimeUnit.SECONDS))
            assertNull(recorder.error?.getString(PdfContract.KEY_ERROR_CODE))
            assertEquals(7, recorder.result!!.getInt(PdfContract.KEY_PAGE_COUNT))
            assertTrue(merged.length() > 0)

            document.close()
            assertTrue(eventually { !packagedDirectory.exists() })
        }
    }

    @Test
    fun eachEntryClosesOnlyItsOwnCallersWhenItsLastBindingGoes() {
        withPdfPlugin { signedPlugin ->
            val signedDocument = signedPlugin.openSample("text.pdf")
            try {
                withPdfPlugin(packaged) { packagedPlugin ->
                    packagedPlugin.openSample("text.pdf")
                    packagedPlugin.openSample("outline.pdf")
                    assertEquals(2, packagedDirectory.listFiles()?.size)
                    assertEquals(1, signedDirectory.listFiles()?.size)
                }
                assertTrue("the packaged directory goes with the last packaged binding", eventually { !packagedDirectory.exists() })
                assertTrue("the signed caller keeps its document", signedDirectory.isDirectory)
                assertNull(signedDocument.info.getString(PdfContract.KEY_ERROR_CODE))
            } finally {
                signedDocument.close()
            }
        }
        assertTrue(eventually { !signedDirectory.exists() })
    }

    @Test
    fun thePackagedEntryRefusesCallersWithoutTheRuntimePermission() {
        withPdfPlugin(packaged) { plugin ->
            asOtherUid { assertPermissionDenied { plugin.info } }
            // The plugin's own uid passes (the file manager tools, these tests). zh-CN: 插件自身 uid 放行 (文件管理器工具, 本测试).
            assertEquals(ThreePaperPdfContract.PLUGIN_ID, plugin.info.id)
        }
        // The signed entry leaves the check to its manifest permission. zh-CN: 签名入口把检查留给其清单权限.
        withPdfPlugin { plugin -> asOtherUid { assertEquals(ThreePaperPdfContract.PLUGIN_ID, plugin.info.id) } }
    }

    @Test
    fun documentsAnswerOnlyTheUidThatCreatedThem() {
        // Handles bind their creator uid on both entries (roadmap D44); operations are covered in PdfEngineDeviceTest.
        // zh-CN: 两个入口的句柄都绑定创建者 uid (路线图 D44); 操作在 PdfEngineDeviceTest 中覆盖.
        listOf(ThreePaperPdfService::class.java, packaged).forEach { entry ->
            withPdfPlugin(entry) { plugin ->
                val document = plugin.openSample("text.pdf")
                try {
                    asOtherUid {
                        assertPermissionDenied { document.info }
                        assertPermissionDenied { document.getText(request()) }
                    }
                    assertNull("the creator is still served", document.info.getString(PdfContract.KEY_ERROR_CODE))
                } finally {
                    document.close()
                }
            }
        }
    }

    private class Recorder : IPdfProgressCallback.Stub() {

        val ended = CountDownLatch(1)

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        override fun onProgress(opId: Int, progress: Bundle) = Unit

        override fun onComplete(opId: Int, result: Bundle) {
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            this.error = error
            ended.countDown()
        }

    }

    /**
     * Runs [block] as if called from an app uid that holds no permission; the token packs the uid above the pid as
     * `IPCThreadState` does.
     * zh-CN: 像来自一个不持有任何权限的应用 uid 那样运行 [block]; 令牌按 `IPCThreadState` 的方式把 uid 放在 pid 之上.
     */
    private fun <T> asOtherUid(block: () -> T): T {
        val own = Binder.clearCallingIdentity()
        Binder.restoreCallingIdentity((OTHER_UID.toLong() shl 32) or Process.myPid().toLong())
        try {
            assertEquals(OTHER_UID, Binder.getCallingUid())
            return block()
        } finally {
            Binder.restoreCallingIdentity(own)
        }
    }

    private fun assertPermissionDenied(call: () -> Unit) {
        val error = assertThrows(SecurityException::class.java) { call() }
        assertEquals(error.message, PdfErrorCodes.PERMISSION_DENIED, PdfErrorCodes.decode(error.message)?.first)
    }

    private companion object {

        /** The last app uid, installed on no test device. zh-CN: 最后一个应用 uid, 测试设备上没有安装. */
        const val OTHER_UID = Process.LAST_APPLICATION_UID

        const val WRITE = ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE

    }

}

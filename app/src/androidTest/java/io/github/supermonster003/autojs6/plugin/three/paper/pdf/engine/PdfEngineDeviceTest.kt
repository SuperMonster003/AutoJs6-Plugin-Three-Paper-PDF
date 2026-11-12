package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The engine pieces of roadmap P2.1 that need Android (descriptors, `Os`, Binder callbacks): asynchronous operations
 * (progress throttled to 250 ms, completion after flushing, cancellation truncating the output), the idle release of
 * parsed state (appendix D Q10) and `close()` ending a call in flight with `SESSION_CLOSED`.
 * zh-CN: 路线图 P2.1 中需要 Android (描述符, `Os`, Binder 回调) 的引擎部分: 异步操作 (进度节流 250 ms, 刷新后完成, 取消时截断
 * 输出), 解析状态的空闲释放 (附录 D Q10), 以及 `close()` 使在途调用以 `SESSION_CLOSED` 结束.
 */
@RunWith(AndroidJUnit4::class)
class PdfEngineDeviceTest {

    private lateinit var root: File
    private lateinit var executor: ExecutorService
    private val host = HostKey(1, 2)

    @Before
    fun setUp() {
        root = File(context.cacheDir, "engine-test").apply { deleteRecursively() }
        executor = Executors.newCachedThreadPool()
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
        root.deleteRecursively()
    }

    private class Events : IPdfProgressCallback.Stub() {

        val progress = CopyOnWriteArrayList<Pair<Long, Bundle>>()
        val ended = CountDownLatch(1)
        val completed = AtomicReference<Bundle>()
        val failed = AtomicReference<Bundle>()

        override fun onProgress(opId: Int, event: Bundle) {
            progress += System.nanoTime() / 1_000_000 to event
        }

        override fun onComplete(opId: Int, result: Bundle) {
            completed.set(result)
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            failed.set(error)
            ended.countDown()
        }

    }

    private fun output(name: String): Pair<File, ParcelFileDescriptor> {
        val file = File(root.apply { mkdirs() }, name)
        return file to ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
    }

    @Test
    fun aCompletedOperationFlushesItsOutputAndAnswersOnce() {
        val operations = Operations(executor)
        val (file, descriptor) = output("done.bin")
        val events = Events()
        val id = descriptor.use {
            operations.start(host, PdfContract.CONTRACT_VERSION, "merge", listOf(it), events) { op ->
                FileOutputStream(op.outputs.single().fileDescriptor).write("hello".toByteArray())
                op.progress(PdfContract.STAGE_WRITING, 1, 1)
                mapOf(PdfContract.KEY_PAGE_COUNT to 1)
            }
        }
        assertTrue(id > 0)
        assertTrue(events.ended.await(10, TimeUnit.SECONDS))
        val result = events.completed.get()
        assertNotNull(result)
        assertNull(events.failed.get())
        assertEquals(PdfContract.CONTRACT_VERSION, result.getInt(PdfContract.KEY_CONTRACT_VERSION))
        assertEquals(1, result.getInt(PdfContract.KEY_PAGE_COUNT))
        assertEquals("hello", file.readText())
        val event = events.progress.single().second
        assertEquals("merge", event.getString(PdfContract.KEY_OP))
        assertEquals(100, event.getInt(PdfContract.KEY_PERCENT))
        assertEquals(PdfContract.STAGE_WRITING, event.getString(PdfContract.KEY_STAGE))
        assertFalse("an ended operation cannot be cancelled", operations.cancel(id))
        assertEquals(0, operations.count())
    }

    @Test
    fun cancellingALongOperationTruncatesItsOutput() {
        val operations = Operations(executor)
        val (file, descriptor) = output("cancelled.bin")
        val events = Events()
        val started = CountDownLatch(1)
        val id = descriptor.use {
            operations.start(host, PdfContract.CONTRACT_VERSION, "merge", listOf(it), events) { op ->
                val stream = FileOutputStream(op.outputs.single().fileDescriptor)
                val chunk = ByteArray(64 * 1024) { 0x25 }
                var step = 0
                while (true) {
                    op.stop.checkStop()
                    stream.write(chunk)
                    op.progress(PdfContract.STAGE_PROCESSING, ++step, 10_000)
                    if (step == 20) started.countDown()
                    Thread.sleep(10)
                }
                @Suppress("UNREACHABLE_CODE")
                emptyMap()
            }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        assertTrue("written before the cancel", file.length() > 0)
        Thread.sleep(600)
        assertTrue(operations.cancel(id))
        assertTrue(events.ended.await(10, TimeUnit.SECONDS))
        assertNull(events.completed.get())
        assertEquals(PdfErrorCodes.CANCELLED, events.failed.get().getString(PdfContract.KEY_ERROR_CODE))
        assertEquals("the partial output is truncated", 0L, file.length())
        // At most one report per 250 ms (protocol), measured on arrival. zh-CN: 每 250 ms 至多一次报告 (协议), 按到达时间测量.
        val times = events.progress.map { it.first }
        assertTrue("several reports: ${times.size}", times.size >= 3)
        times.zipWithNext().forEach { (a, b) -> assertTrue("reports ${b - a} ms apart", b - a >= PdfContract.PROGRESS_THROTTLE_MS - 5) }
        assertFalse(operations.cancel(id))
    }

    @Test
    fun aFailingOperationAnswersItsErrorCode() {
        val operations = Operations(executor)
        val events = Events()
        operations.start(host, PdfContract.CONTRACT_VERSION, "merge", emptyList(), events) { throw PdfFailure.pageOutOfRange("page 9 of 2") }
        assertTrue(events.ended.await(10, TimeUnit.SECONDS))
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, events.failed.get().getString(PdfContract.KEY_ERROR_CODE))
        assertEquals("page 9 of 2", events.failed.get().getString(PdfContract.KEY_ERROR_MESSAGE))
    }

    @Test
    fun idleDocumentsReleaseParsedStateAndStayOpen() {
        val registry = DocumentRegistry(root)
        val session = sample("outline.pdf").use { registry.open(host, PdfContract.CONTRACT_VERSION, it, null, "outline.pdf") }
        try {
            assertTrue(session.hasParsedState)
            assertEquals(0, registry.sweep(idleMillis = 60_000))
            Thread.sleep(20)
            assertEquals(1, registry.sweep(idleMillis = 10))
            assertFalse(session.hasParsedState)
            // The next call parses the source again. zh-CN: 下一次调用重新解析源.
            assertEquals(6, session.call { it.pdf.numberOfPages })
            assertTrue(session.hasParsedState)
        } finally {
            session.close()
        }
        assertFalse(session.directory.exists())
        assertEquals(0, registry.count())
    }

    @Test
    fun closeEndsACallInFlightWithSessionClosed() {
        val registry = DocumentRegistry(root)
        val session = sample("text.pdf").use { registry.open(host, PdfContract.CONTRACT_VERSION, it, null, null) }
        val inside = CountDownLatch(1)
        val outcome = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                session.call { call ->
                    inside.countDown()
                    while (true) {
                        call.stop.checkStop()
                        Thread.sleep(5)
                    }
                }
            } catch (e: Throwable) {
                outcome.set(e)
            }
        }.apply { start() }
        assertTrue(inside.await(5, TimeUnit.SECONDS))
        assertTrue(session.close())
        assertFalse("close() is idempotent", session.close())
        worker.join(5_000)
        assertEquals(PdfErrorCodes.SESSION_CLOSED, (outcome.get() as PdfFailure).code)
        // The call released the resources on its way out. zh-CN: 调用退出时释放了资源.
        assertFalse(session.directory.exists())
        val failure = runCatching { session.call { it.pdf } }.exceptionOrNull() as PdfFailure
        assertEquals(PdfErrorCodes.SESSION_CLOSED, failure.code)
    }

}

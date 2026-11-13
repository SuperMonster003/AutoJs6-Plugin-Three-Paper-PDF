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
 * parsed state (appendix D Q10), `close()` ending a call in flight with `SESSION_CLOSED`, and the callers of the
 * packaged entry (roadmap D44): dead callers reaped, one entry closed apart from the other, operations bound to their uid.
 * zh-CN: 路线图 P2.1 中需要 Android (描述符, `Os`, Binder 回调) 的引擎部分: 异步操作 (进度节流 250 ms, 刷新后完成, 取消时截断
 * 输出), 解析状态的空闲释放 (附录 D Q10), `close()` 使在途调用以 `SESSION_CLOSED` 结束, 以及打包入口的调用方 (路线图 D44):
 * 回收已死调用方, 单独关闭一个入口, 操作绑定其 uid.
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
        assertTrue(operations.cancel(id))
        assertTrue(events.ended.await(10, TimeUnit.SECONDS))
        assertNull(events.completed.get())
        assertEquals(PdfErrorCodes.CANCELLED, events.failed.get().getString(PdfContract.KEY_ERROR_CODE))
        assertEquals("the partial output is truncated", 0L, file.length())
        // The first report passes at once and later ones keep their order; the 250 ms rate itself is checked with a
        // fake clock in ConcurrencyTest, since arrival times on a device depend on scheduling (paper-p4).
        // zh-CN: 首次报告立即放行, 后续报告保持顺序; 250 ms 的频率本身由 ConcurrencyTest 用假时钟检查, 因为设备上的到达时间取决于调度 (paper-p4).
        val steps = events.progress.map { it.second.getInt(PdfContract.KEY_CURRENT) }
        assertTrue("the first report arrives", steps.isNotEmpty())
        assertEquals("the first report is the first step", 1, steps.first())
        steps.zipWithNext().forEach { (a, b) -> assertTrue("reports in order: $a then $b", b > a) }
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

    @Test
    fun deadCallersLoseTheirDocumentsAndTempDirectories() {
        // Roadmap D44: the sweeper reaps callers whose process is gone; the liveness check is injected here.
        // zh-CN: 路线图 D44: 清扫器回收进程已不存在的调用方; 此处注入存活判断.
        val dead = HostKey(1, 2, packaged = true)
        val alive = HostKey(1, 3, packaged = true)
        val reaped = CopyOnWriteArrayList<HostKey>()
        val engine = PdfEngine(root, onHostDead = { reaped += it }, isAlive = { it != dead })
        try {
            repeat(2) { sample("text.pdf").use { engine.documents.open(dead, PdfContract.CONTRACT_VERSION, it, null, null) } }
            val kept = sample("text.pdf").use { engine.documents.open(alive, PdfContract.CONTRACT_VERSION, it, null, null) }
            assertEquals("k1-2", engine.documents.hostDirectory(dead).name)
            assertEquals(2, engine.documents.hostDirectory(dead).listFiles()?.size)
            assertEquals(listOf(dead), engine.reapDeadHosts())
            assertEquals(listOf(dead), reaped.toList())
            assertEquals(0, engine.documents.count(dead))
            assertFalse("the temp directory of the dead caller is gone", engine.documents.hostDirectory(dead).exists())
            assertEquals(1, engine.documents.count(alive))
            assertTrue(kept.call { it.pdf.numberOfPages } > 0)
            assertTrue("nothing left to reap", engine.reapDeadHosts().isEmpty())
        } finally {
            engine.shutdown()
        }
    }

    @Test
    fun closingOneEntryKeepsTheCallersOfTheOther() {
        val engine = PdfEngine(root, isAlive = { true })
        try {
            val signed = HostKey(1, 2)
            val packaged = HostKey(1, 2, packaged = true)
            sample("text.pdf").use { engine.documents.open(signed, PdfContract.CONTRACT_VERSION, it, null, null) }
            sample("text.pdf").use { engine.documents.open(packaged, PdfContract.CONTRACT_VERSION, it, null, null) }
            engine.closeEntry(packaged = true)
            assertEquals(0, engine.documents.count(packaged))
            assertFalse(engine.documents.hostDirectory(packaged).exists())
            assertEquals(1, engine.documents.count(signed))
            engine.closeEntry(packaged = false)
            assertEquals(0, engine.documents.count())
        } finally {
            engine.shutdown()
        }
    }

    @Test
    fun anOperationAnswersOnlyTheUidThatStartedIt() {
        val operations = Operations(executor)
        val events = Events()
        val started = CountDownLatch(1)
        val id = operations.start(HostKey(10_100, 2, packaged = true), PdfContract.CONTRACT_VERSION, "merge", emptyList(), events) { op ->
            started.countDown()
            while (true) {
                op.stop.checkStop()
                Thread.sleep(5)
            }
            @Suppress("UNREACHABLE_CODE")
            emptyMap()
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val refusal = runCatching { operations.cancel(id, callerUid = 10_101) }.exceptionOrNull() as PdfFailure
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, refusal.code)
        assertEquals(1, operations.count())
        assertFalse("an unknown id answers false", operations.cancel(Int.MAX_VALUE, callerUid = 10_101))
        assertTrue(operations.cancel(id, callerUid = 10_100))
        assertTrue(events.ended.await(10, TimeUnit.SECONDS))
        assertEquals(PdfErrorCodes.CANCELLED, events.failed.get().getString(PdfContract.KEY_ERROR_CODE))
    }

}

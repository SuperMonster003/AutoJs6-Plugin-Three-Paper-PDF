package io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Progress throttling and the render pool of roadmap P2.1.
 * zh-CN: 路线图 P2.1 的进度节流与渲染池.
 */
class ConcurrencyTest {

    @Test
    fun `progress passes at most once per interval`() {
        var now = 1_000L
        val throttle = ProgressThrottle(intervalMillis = 250, clock = { now })
        assertTrue("the first report passes at once", throttle.tryPass())
        now += 100
        assertFalse(throttle.tryPass())
        now += 149
        assertFalse(throttle.tryPass())
        now += 1
        assertTrue(throttle.tryPass())
        now += 249
        assertFalse(throttle.tryPass())
    }

    @Test
    fun `the render pool runs two renders at once and queues the rest`() {
        val pool = RenderPool(permits = 2)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val done = AtomicInteger()
        val executor = Executors.newFixedThreadPool(6)
        try {
            repeat(6) {
                executor.execute {
                    pool.run(StopSignal.NEVER) {
                        val now = active.incrementAndGet()
                        peak.accumulateAndGet(now, ::maxOf)
                        Thread.sleep(60)
                        active.decrementAndGet()
                        done.incrementAndGet()
                    }
                }
            }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
        assertEquals(6, done.get())
        assertEquals(2, peak.get())
    }

    @Test
    fun `a queued render leaves when its document closes`() {
        val pool = RenderPool(permits = 1)
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            pool.run(StopSignal.NEVER) {
                holding.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }.apply { start() }
        try {
            assertTrue(holding.await(5, TimeUnit.SECONDS))
            val closed = AtomicBoolean(false)
            val stop = StopSignal { if (closed.get()) StopSignal.closed() else null }
            Thread {
                Thread.sleep(120)
                closed.set(true)
            }.start()
            val failure = assertThrows(PdfFailure::class.java) { pool.run(stop) { error("must not run") } }
            assertEquals(PdfErrorCodes.SESSION_CLOSED, failure.code)
        } finally {
            release.countDown()
            holder.join(5_000)
        }
        // The permit came back: the next render runs. zh-CN: 许可已归还: 下一次渲染可以执行.
        assertEquals(7, pool.run(StopSignal.NEVER) { 7 })
    }

}

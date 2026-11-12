package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.eventually
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.openSample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Page operations through Binder (roadmap P2.4): `run` `merge` of two samples, `planSplit` + `split` back into them,
 * `save` with extract, reorder and rotate, the refusals before an id exists, an encrypted input opened with its user
 * password (appendix D Q30) and the temp directory of `run` that disappears with the operation.
 * zh-CN: 经 Binder 验证页面操作 (路线图 P2.4): 用 `run` `merge` 合并两个样例, 用 `planSplit` + `split` 拆分回去, 带提取, 重排与
 * 旋转的 `save`, 产生 id 之前的拒绝, 用用户密码打开的加密输入 (附录 D Q30), 以及随操作消失的 `run` 临时目录.
 */
@RunWith(AndroidJUnit4::class)
class PdfServicePageOperationsTest {

    private val outputs = File(context.cacheDir, "page-operations")

    private val hostDirectory: File
        get() = File(File(context.cacheDir, PdfEngine.ROOT_NAME), "${Process.myUid()}-${Process.myPid()}")

    @After
    fun removeOutputs() {
        outputs.deleteRecursively()
    }

    /** Records the callbacks of one operation. zh-CN: 记录一个操作的回调. */
    private class Recorder : IPdfProgressCallback.Stub() {

        val progress: MutableList<Bundle> = Collections.synchronizedList(ArrayList())

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        @Volatile
        var endedId = 0

        private val ended = CountDownLatch(1)

        override fun onProgress(opId: Int, progress: Bundle) {
            this.progress += progress
        }

        override fun onComplete(opId: Int, result: Bundle) {
            endedId = opId
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            endedId = opId
            this.error = error
            ended.countDown()
        }

        fun await(): Recorder {
            assertTrue("the operation did not end in time", ended.await(60, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

    }

    private fun output(name: String): File = File(outputs, name).also { it.parentFile?.mkdirs() }

    private fun writable(file: File): ParcelFileDescriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
    )

    private fun IPdfPlugin.openFile(file: File): IPdfDocument = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
        IPdfDocument.Stub.asInterface(MarshallingBinder(open(source, request()).asBinder()))
    }

    private fun IPdfPlugin.merge(target: File, vararg names: String, fill: Bundle.() -> Unit = {}): Recorder {
        val recorder = Recorder()
        val inputs = names.map { sample(it) }
        try {
            writable(target).use { output ->
                val request = request {
                    putString(PdfContract.KEY_OP, PdfOps.MERGE)
                    fill()
                }
                val id = run(request, inputs.toTypedArray(), output, recorder)
                assertTrue(id > 0)
            }
        } finally {
            inputs.forEach { it.close() }
        }
        return recorder.await()
    }

    private fun IPdfDocument.rotations(): IntArray = info.getIntArray(PdfContract.KEY_PAGE_ROTATIONS)!!

    private fun IPdfDocument.outlineTitles(): List<String> {
        val items = JSONArray(outline.getString(PdfContract.KEY_ITEMS))
        return List(items.length()) { items.getJSONObject(it).getString(PdfContract.FIELD_TITLE) }
    }

    @Test
    fun mergesTwoSamplesAndSplitsThemBack() = withPdfPlugin { plugin ->
        val (outlineRotations, sampleRotations) = listOf("outline.pdf", "rotated.pdf").map { name ->
            plugin.openSample(name).let { document -> document.rotations().also { document.close() } }
        }
        val merged = output("merged.pdf")
        val merge = plugin.merge(merged, "outline.pdf", "rotated.pdf").completed()
        assertEquals(11, merge.getInt(PdfContract.KEY_PAGE_COUNT))
        assertEquals(merged.length(), merge.getLong(PdfContract.KEY_SIZE_BYTES))
        assertTrue(eventually { hostDirectory.listFiles().isNullOrEmpty() })

        val document = plugin.openFile(merged)
        try {
            val info = document.info
            assertEquals(11, info.getInt(PdfContract.KEY_PAGE_COUNT))
            assertEquals(ThreePaperPdfContract.PRODUCER, info.getString(PdfContract.KEY_PRODUCER))
            assertArrayEquals(outlineRotations + sampleRotations, document.rotations())
            val titles = document.outlineTitles()
            assertEquals("Outline sample", titles.first())
            assertTrue(titles.contains("Chapter 1"))
            assertTrue(titles.contains("Rotated sample"))

            val ranges = arrayOf("1-6", "7-11")
            val plan = document.planSplit(request { putStringArray(PdfContract.KEY_RANGES, ranges) })
            val planned = JSONArray(plan.getString(PdfContract.KEY_ITEMS))
            assertEquals(2, planned.length())
            assertEquals("7-11", planned.getJSONObject(1).getString(PdfContract.FIELD_PAGES))

            val parts = listOf(output("part-1.pdf"), output("part-2.pdf"))
            val recorder = Recorder()
            val descriptors = parts.map { writable(it) }
            try {
                assertTrue(document.split(request { putStringArray(PdfContract.KEY_RANGES, ranges) }, descriptors.toTypedArray(), recorder) > 0)
            } finally {
                descriptors.forEach { it.close() }
            }
            val split = recorder.await().completed()
            val items = JSONArray(split.getString(PdfContract.KEY_ITEMS))
            assertEquals(listOf(6, 5), List(2) { items.getJSONObject(it).getInt(PdfContract.FIELD_PAGE_COUNT) })
            assertEquals(parts.map { it.length() }, List(2) { items.getJSONObject(it).getLong(PdfContract.FIELD_SIZE_BYTES) })
            assertTrue(recorder.progress.all { it.getString(PdfContract.KEY_OP) == PdfOps.SPLIT })

            listOf(outlineRotations, sampleRotations).forEachIndexed { index, rotations ->
                val part = plugin.openFile(parts[index])
                try {
                    assertEquals(rotations.size, part.info.getInt(PdfContract.KEY_PAGE_COUNT))
                    assertArrayEquals(rotations, part.rotations())
                } finally {
                    part.close()
                }
            }
            assertTrue(plugin.openFile(parts[0]).let { part -> part.outlineTitles().also { part.close() } }.contains("Chapter 3"))
        } finally {
            document.close()
        }
    }

    @Test
    fun saveExtractsReordersAndRotates() = withPdfPlugin { plugin ->
        val document = plugin.openSample("rotated.pdf")
        val target = output("saved.pdf")
        try {
            val original = document.rotations()
            val recorder = Recorder()
            writable(target).use { output ->
                val request = request {
                    putString(PdfContract.KEY_PAGES, "2-4")
                    putIntArray(PdfContract.KEY_ORDER, intArrayOf(4, 3, 2))
                    putInt(PdfContract.KEY_DEGREES, 90)
                }
                assertTrue(document.save(request, output, recorder) > 0)
            }
            val result = recorder.await().completed()
            assertEquals(3, result.getInt(PdfContract.KEY_PAGE_COUNT))
            val expected = intArrayOf(4, 3, 2).map { (original[it - 1] + 90) % 360 }.toIntArray()
            val saved = plugin.openFile(target)
            try {
                assertArrayEquals(expected, saved.rotations())
            } finally {
                saved.close()
            }
            // The document stays usable after the operation. zh-CN: 操作之后文档仍可使用.
            assertEquals(original.size, document.info.getInt(PdfContract.KEY_PAGE_COUNT))
            assertFalse(plugin.cancel(recorder.endedId))
        } finally {
            document.close()
        }
    }

    @Test
    fun requestsRefusedUpFrontThrow() = withPdfPlugin { plugin ->
        val document = plugin.openSample("outline.pdf")
        try {
            writable(output("refused.pdf")).use { output ->
                assertThrows(IllegalArgumentException::class.java) { document.save(request(), output, null) }
                assertThrows(IllegalArgumentException::class.java) { document.save(request { putString(PdfContract.KEY_DELETE_PAGES, "1-") }, output, Recorder()) }
                assertThrows(IllegalArgumentException::class.java) { document.save(request { putString(PdfContract.KEY_PAGES, "9") }, output, Recorder()) }
                assertThrows(IllegalArgumentException::class.java) {
                    document.split(request { putInt(PdfContract.KEY_EVERY, 2) }, arrayOf(output), Recorder())
                }
                assertThrows(IllegalArgumentException::class.java) {
                    plugin.run(request { putString(PdfContract.KEY_OP, PdfOps.MERGE) }, emptyArray(), output, Recorder())
                }
            }
            val plan = document.planSplit(request { putInt(PdfContract.KEY_EVERY, 0) })
            assertEquals(PdfErrorCodes.INVALID_ARGUMENT, plan.getString(PdfContract.KEY_ERROR_CODE))
        } finally {
            document.close()
        }
    }

    @Test
    fun anInputOpenedWithItsUserPasswordCannotBeWritten() = withPdfPlugin { plugin ->
        val target = output("denied.pdf")
        val denied = plugin.merge(target, "text.pdf", "encrypted.pdf") {
            putStringArray(PdfContract.KEY_PASSWORDS, arrayOf("", "paper-user"))
        }
        assertNotNull(denied.error)
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, denied.error!!.getString(PdfContract.KEY_ERROR_CODE))
        assertEquals("a failed operation leaves an empty output", 0L, target.length())

        val merged = plugin.merge(output("owner.pdf"), "text.pdf", "encrypted.pdf") {
            putStringArray(PdfContract.KEY_PASSWORDS, arrayOf("", "paper-owner"))
        }.completed()
        assertEquals(3, merged.getInt(PdfContract.KEY_PAGE_COUNT))

        val document = plugin.openSample("encrypted.pdf", "paper-user")
        try {
            writable(output("user.pdf")).use { output ->
                assertThrows(SecurityException::class.java) { document.save(request(), output, Recorder()) }
            }
        } finally {
            document.close()
        }
        assertTrue(eventually { !hostDirectory.exists() })
    }

    /**
     * Appendix F "100-page merge": four A4 text documents of 25 pages, merged through Binder once to warm up and then
     * three times; logcat tag `PaperMergePerf` gives the median.
     * zh-CN: 附录 F "100 页合并": 四个 25 页的 A4 文本文档, 经 Binder 合并一次预热, 再合并三次; logcat 标签 `PaperMergePerf` 给出中位数.
     */
    @Test
    fun timingOfAHundredPageMerge(): Unit = withPdfPlugin { plugin ->
        val inputs = List(4) { index -> textDocument(output("input-${index + 1}.pdf"), 25, index + 1) }
        val times = ArrayList<Double>()
        var size = 0L
        repeat(4) { round ->
            val target = output("merged-$round.pdf")
            val descriptors = inputs.map { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            val recorder = Recorder()
            val start = SystemClock.elapsedRealtimeNanos()
            try {
                writable(target).use { output ->
                    plugin.run(request { putString(PdfContract.KEY_OP, PdfOps.MERGE) }, descriptors.toTypedArray(), output, recorder)
                }
            } finally {
                descriptors.forEach { it.close() }
            }
            val result = recorder.await().completed()
            val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            assertEquals(100, result.getInt(PdfContract.KEY_PAGE_COUNT))
            size = result.getLong(PdfContract.KEY_SIZE_BYTES)
            if (round > 0) times += elapsed
            target.delete()
        }
        Log.i(
            PERF_TAG,
            "device=${Build.MODEL} api=${Build.VERSION.SDK_INT} inputs=4x25 pages=100 input=${inputs.sumOf { it.length() }} B " +
                "output=$size B median=${"%.0f".format(times.sorted()[1])} ms runs=${times.joinToString { "%.0f".format(it) }}",
        )
    }

    private fun textDocument(file: File, pages: Int, number: Int): File {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 11f }
        try {
            repeat(pages) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                repeat(60) { line ->
                    page.canvas.drawText("Document $number, page ${index + 1}, line ${line + 1}: the quick brown fox jumps over the lazy dog.", 48f, 60f + line * 12f, paint)
                }
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return file
    }

    private companion object {
        const val PERF_TAG = "PaperMergePerf"
    }

}

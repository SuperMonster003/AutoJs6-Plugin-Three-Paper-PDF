package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.openSample
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.PageRenderer
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

/**
 * Rendering through Binder (roadmap P2.3): the default 144 dpi A4 size, white and transparent backgrounds, every size
 * key, the 16 M pixel ceiling with its hint, rotated and encrypted pages, both transports of `render` (SharedMemory on
 * API 27+, the PNG fallback everywhere), `renderToFile` in its three formats, and the timing of appendix F.
 * zh-CN: 经 Binder 渲染 (路线图 P2.3): 默认 144 dpi 的 A4 尺寸, 白底与透明背景, 各个尺寸键, 16 M 像素上限及其提示, 旋转页与加密
 * 文档, `render` 的两种传输 (API 27+ 的 SharedMemory, 所有级别的 PNG 回落), `renderToFile` 的三种格式, 以及附录 F 的耗时.
 */
@RunWith(AndroidJUnit4::class)
class PdfServiceRenderingTest {

    private val outputDirectory = File(context.cacheDir, "render-test").apply { mkdirs() }

    private val sharedMemorySupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    @After
    fun deleteOutputs() {
        outputDirectory.deleteRecursively()
    }

    private fun <T> withSample(name: String, password: String? = null, block: (IPdfDocument) -> T): T = withPdfPlugin { plugin ->
        val document = plugin.openSample(name, password)
        try {
            block(document)
        } finally {
            document.close()
        }
    }

    private fun Bundle.succeeded(): Bundle {
        assertNull(getString(PdfContract.KEY_ERROR_MESSAGE), getString(PdfContract.KEY_ERROR_CODE))
        assertEquals(PdfContract.CONTRACT_VERSION, getInt(PdfContract.KEY_CONTRACT_VERSION))
        return this
    }

    private fun assertFailure(code: String, answer: Bundle) =
        assertEquals(answer.getString(PdfContract.KEY_ERROR_MESSAGE), code, answer.getString(PdfContract.KEY_ERROR_CODE))

    private fun outputFile(name: String) = File(outputDirectory, name).apply { delete() }

    private fun <T> withOutput(file: File, block: (ParcelFileDescriptor) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE).use(block)

    /** `render` with a fresh fallback file, as the host calls it. zh-CN: 像宿主那样带着新的回落文件调用 `render`. */
    private fun IPdfDocument.renderWithFallback(fallback: File = outputFile("fallback.png"), fill: Bundle.() -> Unit): Bundle =
        withOutput(fallback) { render(request(fill), it) }

    private fun IPdfDocument.renderToFile(file: File, fill: Bundle.() -> Unit): Bundle = withOutput(file) { renderToFile(request(fill), it) }

    /**
     * The region of a `sharedMemory` answer. It lives in a nested object because API 24 - 26 cannot load a test class
     * whose method signatures name `SharedMemory`: JUnit lists the declared methods of the class.
     * zh-CN: `sharedMemory` 应答中的区域. 放在嵌套对象里, 因为 API 24 - 26 加载不了方法签名中出现 `SharedMemory` 的测试类:
     * JUnit 会列出类中声明的方法.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private object Regions {

        fun of(answer: Bundle): SharedMemory? {
            answer.classLoader = SharedMemory::class.java.classLoader
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                answer.getParcelable(PdfContract.KEY_SHARED_MEMORY, SharedMemory::class.java)
            } else {
                @Suppress("DEPRECATION")
                answer.getParcelable(PdfContract.KEY_SHARED_MEMORY)
            }
        }

    }

    /**
     * The transport the plugin should answer: SharedMemory on API 27+ unless [file] is asked.
     * zh-CN: 插件应当返回的传输方式: API 27+ 上为 SharedMemory, 除非要求 [file].
     */
    private fun expectedTransport(file: Boolean = false) =
        if (!file && sharedMemorySupported) PdfContract.TRANSPORT_SHARED_MEMORY else PdfContract.TRANSPORT_FILE

    /**
     * The bitmap of a `render` answer, received like the host does: the region is checked, copied and closed, or the
     * PNG in [fallback] is checked against `sizeBytes` and decoded.
     * zh-CN: 像宿主那样接收 `render` 应答的位图: 检查, 拷贝并关闭区域, 或按 `sizeBytes` 检查 [fallback] 中的 PNG 后解码.
     */
    private fun receive(answer: Bundle, fallback: File): Bitmap {
        val width = answer.getInt(PdfContract.KEY_WIDTH)
        val height = answer.getInt(PdfContract.KEY_HEIGHT)
        return when (answer.getString(PdfContract.KEY_TRANSPORT)) {
            PdfContract.TRANSPORT_SHARED_MEMORY -> {
                assertTrue(sharedMemorySupported)
                receiveRegion(answer, width, height)
            }
            PdfContract.TRANSPORT_FILE -> {
                val bytes = checkPng(answer, fallback, width, height)
                checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).also {
                    assertEquals(width, it.width)
                    assertEquals(height, it.height)
                }
            }
            else -> throw AssertionError("unknown transport ${answer.getString(PdfContract.KEY_TRANSPORT)}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun receiveRegion(answer: Bundle, width: Int, height: Int): Bitmap {
        val region = checkNotNull(Regions.of(answer)) { "a sharedMemory answer without a region" }
        try {
            val rowBytes = answer.getInt(PdfContract.KEY_ROW_BYTES)
            assertEquals(width * 4, rowBytes)
            assertTrue(region.size >= rowBytes * height)
            // The region is read-only (protocol "Rendering"). zh-CN: 区域是只读的 (协议 "Rendering" 一节).
            val writable = runCatching { region.mapReadWrite() }
            writable.getOrNull()?.let { SharedMemory.unmap(it) }
            assertTrue("the render region can be mapped writable", writable.isFailure)
            val buffer = region.mapReadOnly()
            try {
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(buffer) }
            } finally {
                SharedMemory.unmap(buffer)
            }
        } finally {
            region.close()
        }
    }

    /** The fallback PNG: declared size, signature and header size. zh-CN: 回落 PNG: 声明的大小, 签名与头部尺寸. */
    private fun checkPng(answer: Bundle, file: File, width: Int, height: Int): ByteArray {
        val bytes = file.readBytes()
        assertEquals(answer.getLong(PdfContract.KEY_SIZE_BYTES), bytes.size.toLong())
        assertTrue(bytes.size <= PdfContract.MAX_RENDER_FILE_BYTES)
        assertTrue("not a PNG", bytes.take(8) == PNG_SIGNATURE)
        assertEquals(width, bytes.int(16))
        assertEquals(height, bytes.int(20))
        return bytes
    }

    private fun ByteArray.int(offset: Int) = (offset until offset + 4).fold(0) { value, index -> (value shl 8) or (this[index].toInt() and 0xFF) }

    private fun Bitmap.darkPixels(): Int {
        var count = 0
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val pixel = getPixel(x, y)
            if (Color.alpha(pixel) > 200 && Color.red(pixel) < 100) count++
        }
        return count
    }

    @Test
    fun theDefaultRenderIsA4At144DpiOnWhite() = withSample("text.pdf") { document ->
        val fallback = outputFile("default.png")
        val answer = document.renderWithFallback(fallback) { putInt(PdfContract.KEY_PAGE, 1) }.succeeded()
        assertEquals(expectedTransport(), answer.getString(PdfContract.KEY_TRANSPORT))
        // 595 x 842 points at 2 pixels per point. zh-CN: 595 x 842 点, 每点 2 像素.
        assertEquals(1190, answer.getInt(PdfContract.KEY_WIDTH))
        assertEquals(1684, answer.getInt(PdfContract.KEY_HEIGHT))
        val bitmap = receive(answer, fallback)
        assertEquals(Color.WHITE, bitmap.getPixel(4, 4))
        assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width - 4, bitmap.height - 4))
        assertTrue("the text is drawn", bitmap.darkPixels() > 100)
        bitmap.recycle()
    }

    @Test
    fun aTransparentRenderLeavesTheMarginsClear() = withSample("text.pdf") { document ->
        val fallback = outputFile("transparent.png")
        val answer = document.renderWithFallback(fallback) {
            putInt(PdfContract.KEY_PAGE, 1)
            putBoolean(PdfContract.KEY_TRANSPARENT, true)
        }.succeeded()
        val bitmap = receive(answer, fallback)
        assertEquals(0, Color.alpha(bitmap.getPixel(4, 4)))
        assertTrue("the text is drawn", bitmap.darkPixels() > 100)
        bitmap.recycle()
    }

    @Test
    fun theFileTransportWritesAPngOfTheDeclaredSize() = withSample("text.pdf") { document ->
        val fallback = outputFile("file.png")
        val answer = document.renderWithFallback(fallback) {
            putInt(PdfContract.KEY_PAGE, 1)
            putString(PdfContract.KEY_TRANSPORT, PdfContract.TRANSPORT_FILE)
        }.succeeded()
        assertEquals(PdfContract.TRANSPORT_FILE, answer.getString(PdfContract.KEY_TRANSPORT))
        assertFalse(answer.containsKey(PdfContract.KEY_SHARED_MEMORY))
        val bitmap = receive(answer, fallback)
        assertEquals(Color.WHITE, bitmap.getPixel(4, 4))
        assertTrue(bitmap.darkPixels() > 100)
        bitmap.recycle()
    }

    @Test
    fun sizeKeysScaleThePageAndExcludeEachOther() = withSample("text.pdf") { document ->
        fun size(fill: Bundle.() -> Unit): Pair<Int, Int> = document.renderWithFallback {
            putInt(PdfContract.KEY_PAGE, 1)
            putString(PdfContract.KEY_TRANSPORT, PdfContract.TRANSPORT_FILE)
            fill()
        }.succeeded().let { it.getInt(PdfContract.KEY_WIDTH) to it.getInt(PdfContract.KEY_HEIGHT) }
        assertEquals(595 to 842, size { putInt(PdfContract.KEY_DPI, 72) })
        assertEquals(600 to 849, size { putInt(PdfContract.KEY_WIDTH, 600) })
        assertEquals(298 to 421, size { putInt(PdfContract.KEY_HEIGHT, 421) })
        assertEquals(298 to 421, size { putDouble(PdfContract.KEY_SCALE, 0.5) })
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.renderWithFallback {
            putInt(PdfContract.KEY_PAGE, 1)
            putInt(PdfContract.KEY_DPI, 72)
            putInt(PdfContract.KEY_WIDTH, 600)
        })
    }

    @Test
    fun the16MPixelCeilingHoldsAndItsFailureSuggestsASmallerSize(): Unit = withSample("text.pdf") { document ->
        val over = document.renderWithFallback {
            putInt(PdfContract.KEY_PAGE, 1)
            putInt(PdfContract.KEY_DPI, 407)
        }
        assertFailure(PdfErrorCodes.LIMIT_EXCEEDED, over)
        val message = over.getString(PdfContract.KEY_ERROR_MESSAGE).orEmpty()
        assertTrue(message, message.contains("dpi <= 406") && message.contains("width <= 3362") && message.contains("height <= 4758"))
        // The largest size that fits renders; only its size is checked to spare memory. zh-CN: 放得下的最大尺寸可以渲染; 为节省内存只检查尺寸.
        val fallback = outputFile("largest.png")
        val answer = document.renderWithFallback(fallback) {
            putInt(PdfContract.KEY_PAGE, 1)
            putInt(PdfContract.KEY_DPI, 406)
        }.succeeded()
        assertEquals(3355, answer.getInt(PdfContract.KEY_WIDTH))
        assertEquals(4748, answer.getInt(PdfContract.KEY_HEIGHT))
        assertTrue(3355L * 4748 <= PdfContract.MAX_RENDER_PIXELS)
        if (answer.getString(PdfContract.KEY_TRANSPORT) == PdfContract.TRANSPORT_FILE) {
            checkPng(answer, fallback, 3355, 4748)
        } else if (sharedMemorySupported) {
            checkNotNull(Regions.of(answer)).use { region ->
                assertEquals(3355 * 4, answer.getInt(PdfContract.KEY_ROW_BYTES))
                assertTrue(region.size >= 3355 * 4 * 4748)
            }
        }
    }

    /**
     * Each page carries a 144 x 36 pt black bar 36 pt from the left and top edges of the unrotated page (fixtures.json),
     * so the bar shows where the page turned: its center is checked dark and the point opposite it white.
     * zh-CN: 每页在未旋转页面距左边与上边 36 pt 处有一条 144 x 36 pt 的黑条 (fixtures.json), 由此可见页面转到了哪里: 检查黑条
     * 中心为深色, 与之相对的点为白色.
     */
    @Test
    fun rotatedPagesRenderAsDisplayed() = withSample("rotated.pdf") { document ->
        // Page, displayed size in pixels at 144 dpi, center of the bar in pixels. zh-CN: 页码, 144 dpi 下显示尺寸 (像素), 黑条中心 (像素).
        val pages = listOf(
            Triple(1, 1190 to 1684, 216 to 108),
            Triple(2, 1684 to 1190, 1576 to 216),
            Triple(3, 1190 to 1684, 974 to 1576),
            Triple(4, 1684 to 1190, 108 to 974),
            Triple(5, 1584 to 1224, 216 to 108),
        )
        for ((page, size, bar) in pages) {
            val fallback = outputFile("rotated-$page.png")
            val answer = document.renderWithFallback(fallback) {
                putInt(PdfContract.KEY_PAGE, page)
                putString(PdfContract.KEY_TRANSPORT, PdfContract.TRANSPORT_FILE)
            }.succeeded()
            assertEquals("page $page", size, answer.getInt(PdfContract.KEY_WIDTH) to answer.getInt(PdfContract.KEY_HEIGHT))
            val bitmap = receive(answer, fallback)
            val (x, y) = bar
            assertEquals("page $page bar", Color.BLACK, bitmap.getPixel(x, y))
            assertEquals("page $page opposite", Color.WHITE, bitmap.getPixel(bitmap.width - 1 - x, bitmap.height - 1 - y))
            bitmap.recycle()
        }
    }

    @Test
    fun anEncryptedDocumentRendersFromADecryptedCopyThatCloseDeletes() {
        val renderDirectory = File(context.cacheDir, "pdf")
        fun copies() = renderDirectory.walkTopDown().filter { it.name == PageRenderer.DECRYPTED_NAME }.toList()
        withSample("encrypted.pdf", "paper-user") { document ->
            val info = document.info
            val fallback = outputFile("encrypted.png")
            val answer = document.renderWithFallback(fallback) { putInt(PdfContract.KEY_PAGE, 1) }.succeeded()
            assertEquals((info.getFloatArray(PdfContract.KEY_PAGE_WIDTHS)!![0] * 2).roundToInt(), answer.getInt(PdfContract.KEY_WIDTH))
            assertEquals((info.getFloatArray(PdfContract.KEY_PAGE_HEIGHTS)!![0] * 2).roundToInt(), answer.getInt(PdfContract.KEY_HEIGHT))
            val bitmap = receive(answer, fallback)
            assertTrue("the text is drawn", bitmap.darkPixels() > 10)
            bitmap.recycle()
            assertEquals(1, copies().size)
            // The session's own document keeps its encryption state. zh-CN: 会话自身的文档保持加密状态.
            assertEquals(true, document.info.getBoolean(PdfContract.KEY_ENCRYPTED))
        }
        assertTrue("the decrypted copy outlives close", PdfServiceHarness.eventually { copies().isEmpty() })
    }

    @Test
    fun renderToFileWritesPngJpegAndWebp() = withSample("text.pdf") { document ->
        val signatures = mapOf(
            PdfContract.FORMAT_PNG to PNG_SIGNATURE,
            PdfContract.FORMAT_JPEG to listOf<Byte>(-1, -40, -1),
            PdfContract.FORMAT_WEBP to "RIFF".toByteArray().toList(),
        )
        for ((format, signature) in signatures) {
            val file = outputFile("page.$format")
            val answer = document.renderToFile(file) {
                putInt(PdfContract.KEY_PAGE, 1)
                putInt(PdfContract.KEY_DPI, 72)
                if (format != PdfContract.FORMAT_PNG) putString(PdfContract.KEY_FORMAT, format)
                if (format == PdfContract.FORMAT_JPEG) putInt(PdfContract.KEY_QUALITY, 80)
            }.succeeded()
            assertEquals(format, answer.getString(PdfContract.KEY_FORMAT))
            assertEquals(595, answer.getInt(PdfContract.KEY_WIDTH))
            assertEquals(842, answer.getInt(PdfContract.KEY_HEIGHT))
            val bytes = file.readBytes()
            assertEquals(answer.getLong(PdfContract.KEY_SIZE_BYTES), bytes.size.toLong())
            assertEquals(format, signature, bytes.take(signature.size))
            if (format == PdfContract.FORMAT_WEBP) assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
            val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "$format cannot be decoded" }
            assertEquals(595 to 842, decoded.width to decoded.height)
            decoded.recycle()
        }
        // webp keeps the alpha channel. zh-CN: webp 保留 alpha 通道.
        val webp = outputFile("transparent.webp")
        document.renderToFile(webp) {
            putInt(PdfContract.KEY_PAGE, 1)
            putInt(PdfContract.KEY_DPI, 72)
            putString(PdfContract.KEY_FORMAT, PdfContract.FORMAT_WEBP)
            putBoolean(PdfContract.KEY_TRANSPARENT, true)
        }.succeeded()
        val decoded = checkNotNull(BitmapFactory.decodeFile(webp.path))
        assertTrue(Color.alpha(decoded.getPixel(4, 4)) < 16)
        decoded.recycle()
    }

    @Test
    fun renderToFileRefusesBadOptionsAndLeavesAnEmptyOutput() = withSample("text.pdf") { document ->
        fun refused(fill: Bundle.() -> Unit) {
            val file = outputFile("refused.out")
            assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.renderToFile(file) { putInt(PdfContract.KEY_PAGE, 1); fill() })
            assertEquals(0L, file.length())
        }
        refused { putString(PdfContract.KEY_FORMAT, "gif") }
        refused { putInt(PdfContract.KEY_QUALITY, 0) }
        refused { putInt(PdfContract.KEY_QUALITY, 101) }
        // jpeg has no alpha channel (appendix D Q12). zh-CN: jpeg 没有 alpha 通道 (附录 D Q12).
        refused { putString(PdfContract.KEY_FORMAT, PdfContract.FORMAT_JPEG); putBoolean(PdfContract.KEY_TRANSPARENT, true) }
    }

    @Test
    fun renderChecksThePageAndTheTransportAndEndsOnAClosedDocument() = withPdfPlugin { plugin ->
        val document = plugin.openSample("text.pdf")
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.renderWithFallback { })
        assertFailure(PdfErrorCodes.PAGE_OUT_OF_RANGE, document.renderWithFallback { putInt(PdfContract.KEY_PAGE, 2) })
        assertFailure(PdfErrorCodes.INVALID_ARGUMENT, document.renderWithFallback {
            putInt(PdfContract.KEY_PAGE, 1)
            putString(PdfContract.KEY_TRANSPORT, "pipe")
        })
        document.close()
        assertFailure(PdfErrorCodes.SESSION_CLOSED, document.renderWithFallback { putInt(PdfContract.KEY_PAGE, 1) })
    }

    @Test
    fun renderRegionsDoNotLeakDescriptors() {
        if (!sharedMemorySupported) return
        withSample("text.pdf") { document ->
            fun renderOnce() {
                val answer = document.renderWithFallback { putInt(PdfContract.KEY_PAGE, 1); putInt(PdfContract.KEY_DPI, 72) }.succeeded()
                assertEquals(PdfContract.TRANSPORT_SHARED_MEMORY, answer.getString(PdfContract.KEY_TRANSPORT))
                checkNotNull(Regions.of(answer)).close()
            }
            renderOnce()
            val before = openDescriptors()
            repeat(20) { renderOnce() }
            // From API 33 a received Bundle keeps a lazy copy of its parcel, with a duplicate of every descriptor in
            // it, until that copy is collected; the test side counts after a collection there, the plugin side closes
            // its region when the reply is written either way.
            // zh-CN: 自 API 33 起, 收到的 Bundle 保留其 Parcel 的惰性副本 (含其中每个描述符的副本), 直到副本被回收; 因此在
            // 那里测试端回收之后再计数, 插件端无论如何都在回复写出后关闭自己的区域.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                repeat(3) { Runtime.getRuntime().gc(); System.runFinalization() }
            }
            val grown = openDescriptors().mapValues { (target, count) -> count - (before[target] ?: 0) }.filterValues { it > 0 }
            assertTrue("descriptors grew: $grown", grown.values.sum() <= 2)
        }
    }

    /** Open descriptors of this process by target. zh-CN: 本进程打开的描述符, 按指向分组计数. */
    private fun openDescriptors(): Map<String, Int> = File("/proc/self/fd").listFiles().orEmpty()
        .map { runCatching { Os.readlink(it.path) }.getOrDefault("?").replace(Regex("[0-9]+"), "#") }
        .groupingBy { it }.eachCount()

    /**
     * Appendix F: an A4 text page at 144 dpi, the Binder call and the host side copy into a bitmap, median of ten after
     * two warm-up calls. The numbers go to logcat (tag [PERF_TAG]).
     * zh-CN: 附录 F: 144 dpi 的 A4 文本页, Binder 调用与宿主侧拷入位图, 两次预热后十次的中位数. 结果写入 logcat (标签 [PERF_TAG]).
     */
    @Test
    fun timingOfAnA4TextPageAt144Dpi(): Unit = withSample("text.pdf") { document ->
        val fallback = outputFile("timing.png")
        val calls = ArrayList<Double>()
        val receives = ArrayList<Double>()
        var transport: String? = null
        repeat(12) { round ->
            val start = SystemClock.elapsedRealtimeNanos()
            val answer = document.renderWithFallback(fallback) { putInt(PdfContract.KEY_PAGE, 1) }.succeeded()
            val called = SystemClock.elapsedRealtimeNanos()
            receive(answer, fallback).recycle()
            val received = SystemClock.elapsedRealtimeNanos()
            transport = answer.getString(PdfContract.KEY_TRANSPORT)
            if (round >= 2) {
                calls += (called - start) / 1e6
                receives += (received - called) / 1e6
            }
        }
        val median = { values: List<Double> -> values.sorted().let { (it[it.size / 2 - 1] + it[it.size / 2]) / 2 } }
        Log.i(
            PERF_TAG,
            "device=${Build.MODEL} api=${Build.VERSION.SDK_INT} transport=$transport size=1190x1684 " +
                "call median=${"%.1f".format(median(calls))} ms min=${"%.1f".format(calls.min())} max=${"%.1f".format(calls.max())}; " +
                "receive median=${"%.1f".format(median(receives))} ms",
        )
    }

    private companion object {
        const val PERF_TAG = "PaperRenderPerf"
        val PNG_SIGNATURE = listOf<Byte>(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfService
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfContract
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Binds the PDF service as the host does and opens the device samples (`samples/fixtures/`, androidTest assets). Every
 * Binder object is wrapped in a [MarshallingBinder], so calls cross real Parcels although the service runs in the
 * test process.
 * zh-CN: 像宿主那样绑定 PDF 服务并打开设备样例 (`samples/fixtures/`, androidTest 资产). 每个 Binder 对象都包上
 * [MarshallingBinder], 因此尽管服务在测试进程中运行, 调用仍经过真实的 Parcel.
 */
object PdfServiceHarness {

    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Binds [ThreePaperPdfService] explicitly, runs [block] and unbinds. zh-CN: 显式绑定 [ThreePaperPdfService], 执行 [block] 后解绑. */
    fun <T> withPdfPlugin(block: (IPdfPlugin) -> T): T {
        val binder = AtomicReference<IBinder>()
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder.set(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val intent = Intent().setComponent(ComponentName(context, ThreePaperPdfService::class.java))
        assertTrue("bindService returned false", context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("timed out binding the PDF service", connected.await(10, TimeUnit.SECONDS))
            return block(IPdfPlugin.Stub.asInterface(MarshallingBinder(binder.get())))
        } finally {
            context.unbindService(connection)
        }
    }

    /** `open` with a marshalled document handle. zh-CN: 返回经编组的文档句柄的 `open`. */
    fun IPdfPlugin.openSample(name: String, password: String? = null): IPdfDocument = sample(name).use { source ->
        val handle = open(source, request { password?.let { putString(PdfContract.KEY_PASSWORD, it) } })
        IPdfDocument.Stub.asInterface(MarshallingBinder(handle.asBinder()))
    }

    /** A request carrying the contract version. zh-CN: 携带契约版本的请求. */
    fun request(fill: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION)
        fill()
    }

    /** A read-only descriptor of an androidTest asset, copied to the cache once. zh-CN: androidTest 资产的只读描述符, 只复制到缓存一次. */
    fun sample(name: String): ParcelFileDescriptor = ParcelFileDescriptor.open(sampleFile(name), ParcelFileDescriptor.MODE_READ_ONLY)

    fun sampleFile(name: String): File {
        val file = File(context.cacheDir, "samples/$name")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        return file
    }

    /** Waits up to [timeoutMillis] for [condition]. zh-CN: 至多等待 [timeoutMillis] 直到 [condition] 成立. */
    fun eventually(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

}

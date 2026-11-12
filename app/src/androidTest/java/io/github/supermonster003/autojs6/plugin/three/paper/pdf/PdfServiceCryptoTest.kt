package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.request
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.sampleFile
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness.withPdfPlugin
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfEncryption
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfCapabilityKeys
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.security.cert.CertificateFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * `encrypt` and `decrypt` through Binder (roadmap P2.6): the user password gets exactly the requested permissions and
 * the owner password all of them, a wrong password is `WRONG_PASSWORD`, re-encryption and `decrypt` need the owner
 * password, `crypto.aes256` is declared where the platform providers have AES-256, and the provider spike of appendix
 * D Q4 (`PaperCryptoSpike` lines: the providers serving PDFBox without any registration, and the side effects of option
 * (a), registering Bouncy Castle at position 1, on the same process).
 * zh-CN: 经 Binder 验证 `encrypt` 与 `decrypt` (路线图 P2.6): 用户密码恰好得到所请求的权限位, 所有者密码得到全部权限, 密码错误报
 * `WRONG_PASSWORD`, 重新加密与 `decrypt` 需要所有者密码, 平台 provider 支持 AES-256 时声明 `crypto.aes256`, 以及附录 D Q4 的
 * provider spike (`PaperCryptoSpike` 行: 不注册任何 provider 时为 PDFBox 服务的 provider, 以及选项 (a) 即在位置 1 注册 Bouncy
 * Castle 对同一进程的副作用).
 */
@RunWith(AndroidJUnit4::class)
class PdfServiceCryptoTest {

    private val outputs = File(context.cacheDir, "crypto")

    @After
    fun removeOutputs() {
        outputs.deleteRecursively()
    }

    /** Records the callbacks of one operation. zh-CN: 记录一个操作的回调. */
    private class Recorder : IPdfProgressCallback.Stub() {

        @Volatile
        var result: Bundle? = null

        @Volatile
        var error: Bundle? = null

        private val ended = CountDownLatch(1)

        override fun onProgress(opId: Int, progress: Bundle) = Unit

        override fun onComplete(opId: Int, result: Bundle) {
            this.result = result
            ended.countDown()
        }

        override fun onError(opId: Int, error: Bundle) {
            this.error = error
            ended.countDown()
        }

        fun await(): Recorder {
            assertTrue("the operation did not end in time", ended.await(60, TimeUnit.SECONDS))
            return this
        }

        fun completed(): Bundle = checkNotNull(result) { "the operation failed: ${error?.getString(PdfContract.KEY_ERROR_CODE)} ${error?.getString(PdfContract.KEY_ERROR_MESSAGE)}" }

        fun failedWith(): String? = error?.getString(PdfContract.KEY_ERROR_CODE)

    }

    private fun output(name: String): File = File(outputs, name).also { it.parentFile?.mkdirs() }

    private fun IPdfPlugin.runOn(op: String, input: File, target: File, fill: Bundle.() -> Unit = {}): Recorder {
        val recorder = Recorder()
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
            ParcelFileDescriptor.open(
                target,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { output ->
                run(request { putString(PdfContract.KEY_OP, op); fill() }, arrayOf(source), output, recorder)
            }
        }
        return recorder.await()
    }

    private fun IPdfPlugin.openFile(file: File, password: String?): IPdfDocument =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
            IPdfDocument.Stub.asInterface(MarshallingBinder(open(source, request { password?.let { putString(PdfContract.KEY_PASSWORD, it) } }).asBinder()))
        }

    /** The error code a refused call throws. zh-CN: 被拒绝的调用抛出的错误码. */
    private fun codeOf(block: () -> Unit): String? {
        try {
            block()
        } catch (e: Exception) {
            return PdfErrorCodes.decode(e.message)?.first
        }
        fail("the call was not refused")
        return null
    }

    private fun IPdfPlugin.infoOf(file: File, password: String?): Bundle {
        val document = openFile(file, password)
        try {
            return document.info
        } finally {
            document.close()
        }
    }

    private fun IPdfPlugin.textOf(file: File, password: String?): String {
        val document = openFile(file, password)
        try {
            val answer = document.getText(request { putString(PdfContract.KEY_PAGES, "1-") })
            assertNull(answer.getString(PdfContract.KEY_ERROR_CODE))
            return checkNotNull(answer.getStringArray(PdfContract.KEY_TEXTS)).joinToString("\n")
        } finally {
            document.close()
        }
    }

    private fun IPdfPlugin.encryptOutline(target: File, keyLength: Int): Recorder = runOn(PdfOps.ENCRYPT, sampleFile("outline.pdf"), target) {
        putString(PdfContract.KEY_USER_PASSWORD, USER)
        putString(PdfContract.KEY_OWNER_PASSWORD, OWNER)
        putStringArray(PdfContract.KEY_PERMISSIONS, GRANTED)
        putInt(PdfContract.KEY_KEY_LENGTH, keyLength)
    }

    @Test
    fun encryptedOutputsKeepTheRequestedPermissionsAndDecryptNeedsTheOwner(): Unit = withPdfPlugin { plugin ->
        val features = plugin.capabilities.getStringArray(PdfCapabilityKeys.FEATURES).orEmpty()
        assertEquals(PdfEncryption.aes256Available, features.contains(PdfContract.FEATURE_CRYPTO_AES256))
        val original = plugin.textOf(sampleFile("outline.pdf"), null)
        val keyLengths = if (PdfEncryption.aes256Available) listOf(256, 128) else listOf(128)
        for (keyLength in keyLengths) {
            val encrypted = output("encrypted-$keyLength.pdf")
            val result = plugin.encryptOutline(encrypted, keyLength).completed()
            assertEquals(6, result.getInt(PdfContract.KEY_PAGE_COUNT))
            assertEquals(encrypted.length(), result.getLong(PdfContract.KEY_SIZE_BYTES))
            assertNull(result.getStringArray(PdfContract.KEY_WARNINGS))
            assertEquals(PdfErrorCodes.PASSWORD_REQUIRED, codeOf { plugin.openFile(encrypted, null) })
            assertEquals(PdfErrorCodes.WRONG_PASSWORD, codeOf { plugin.openFile(encrypted, "wrong") })
            val user = plugin.infoOf(encrypted, USER)
            assertTrue(user.getBoolean(PdfContract.KEY_ENCRYPTED))
            assertArrayEquals("$keyLength bits", GRANTED, user.getStringArray(PdfContract.KEY_PERMISSIONS))
            assertArrayEquals(PdfContract.PERMISSIONS.toTypedArray(), plugin.infoOf(encrypted, OWNER).getStringArray(PdfContract.KEY_PERMISSIONS))
            // Appendix D Q11: reading does not enforce the permissions. zh-CN: 附录 D Q11: 读取不执行权限位.
            assertEquals(original, plugin.textOf(encrypted, USER))

            val denied = plugin.runOn(PdfOps.DECRYPT, encrypted, output("denied-$keyLength.pdf")) { putString(PdfContract.KEY_PASSWORD, USER) }
            assertEquals(PdfErrorCodes.PERMISSION_DENIED, denied.failedWith())
            val decrypted = output("decrypted-$keyLength.pdf")
            val plain = plugin.runOn(PdfOps.DECRYPT, encrypted, decrypted) { putString(PdfContract.KEY_PASSWORD, OWNER) }.completed()
            assertEquals(decrypted.length(), plain.getLong(PdfContract.KEY_SIZE_BYTES))
            assertFalse(plugin.infoOf(decrypted, null).getBoolean(PdfContract.KEY_ENCRYPTED))
            assertEquals(original, plugin.textOf(decrypted, null))
        }
        if (!PdfEncryption.aes256Available) {
            assertEquals(PdfErrorCodes.UNSUPPORTED, codeOf { plugin.encryptOutline(output("unsupported.pdf"), 256) })
        }
    }

    @Test
    fun anEncryptedSampleIsEncryptedAgainWithItsOwnerPasswordOnly(): Unit = withPdfPlugin { plugin ->
        val sample = sampleFile("encrypted.pdf")
        val refused = plugin.runOn(PdfOps.ENCRYPT, sample, output("refused.pdf")) {
            putString(PdfContract.KEY_PASSWORD, "paper-user")
            putString(PdfContract.KEY_USER_PASSWORD, USER)
        }
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, refused.failedWith())
        val again = output("again.pdf")
        plugin.runOn(PdfOps.ENCRYPT, sample, again) {
            putString(PdfContract.KEY_PASSWORD, "paper-owner")
            putString(PdfContract.KEY_USER_PASSWORD, USER)
            putString(PdfContract.KEY_OWNER_PASSWORD, OWNER)
            putInt(PdfContract.KEY_KEY_LENGTH, 128)
        }.completed()
        assertEquals(PdfErrorCodes.WRONG_PASSWORD, codeOf { plugin.openFile(again, "paper-user") })
        assertArrayEquals(PdfContract.PERMISSIONS.toTypedArray(), plugin.infoOf(again, USER).getStringArray(PdfContract.KEY_PERMISSIONS))
        assertEquals(plugin.textOf(sample, "paper-owner"), plugin.textOf(again, USER))
        // decrypt of an unencrypted document copies it with a warning. zh-CN: 对未加密文档 decrypt 会复制并给出警告.
        val copy = plugin.runOn(PdfOps.DECRYPT, sampleFile("text.pdf"), output("copy.pdf")).completed()
        assertEquals(1, copy.getStringArray(PdfContract.KEY_WARNINGS)?.size)
    }

    @Test
    fun requestsAreCheckedBeforeAnOperationStarts(): Unit = withPdfPlugin { plugin ->
        val target = output("checked.pdf")
        assertEquals(PdfErrorCodes.INVALID_ARGUMENT, codeOf { plugin.runOn(PdfOps.ENCRYPT, sampleFile("text.pdf"), target) })
        assertEquals(
            PdfErrorCodes.INVALID_ARGUMENT,
            codeOf {
                plugin.runOn(PdfOps.ENCRYPT, sampleFile("text.pdf"), target) {
                    putString(PdfContract.KEY_USER_PASSWORD, USER)
                    putInt(PdfContract.KEY_KEY_LENGTH, 192)
                }
            },
        )
        assertEquals(
            PdfErrorCodes.INVALID_ARGUMENT,
            codeOf {
                plugin.runOn(PdfOps.ENCRYPT, sampleFile("text.pdf"), target) {
                    putString(PdfContract.KEY_USER_PASSWORD, USER)
                    putStringArray(PdfContract.KEY_PERMISSIONS, arrayOf("export"))
                }
            },
        )
    }

    /** The median of [runs] timed runs of [block] in milliseconds. zh-CN: [block] 计时 [runs] 次的中位数 (毫秒). */
    private fun medianMillis(runs: Int = 3, block: () -> Unit): Long {
        block()
        return List(runs) {
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
        }.sorted()[runs / 2]
    }

    private fun defaultProviders(): String = listOf(
        "AES/CBC/PKCS5Padding" to { Cipher.getInstance("AES/CBC/PKCS5Padding").provider },
        "AES/ECB/NoPadding" to { Cipher.getInstance("AES/ECB/NoPadding").provider },
        "SHA-256" to { MessageDigest.getInstance("SHA-256").provider },
        "SHA-512" to { MessageDigest.getInstance("SHA-512").provider },
        "X.509" to { CertificateFactory.getInstance("X.509").provider },
        "TLS" to { SSLContext.getInstance("TLS").provider },
    ).joinToString(", ") { (name, provider) -> "$name=${runCatching { provider().name }.getOrElse { it.javaClass.simpleName }}" }

    /** SHA-256 of 8 MiB, in milliseconds. zh-CN: 8 MiB 数据的 SHA-256 耗时 (毫秒). */
    private fun sha256Millis(): Long {
        val data = ByteArray(8 * 1024 * 1024) { it.toByte() }
        return medianMillis { MessageDigest.getInstance("SHA-256").digest(data) }
    }

    @Test
    fun providerSpike(): Unit = withPdfPlugin { plugin ->
        val device = "device=${Build.MODEL} api=${Build.VERSION.SDK_INT}"
        val providers = Security.getProviders().toList()
        val bc = providers.indexOfFirst { it.name == "BC" }
        Log.i(SPIKE_TAG, "$device providers=${providers.mapIndexed { index, provider -> "${index + 1}:${provider.name}" }.joinToString(" ")}")
        Log.i(SPIKE_TAG, "$device system BC position=${bc + 1} class=${providers.getOrNull(bc)?.javaClass?.name} aes256=${PdfEncryption.aes256Available}")
        // (c) No registration: what PDFBox's standard security handler gets. zh-CN: (c) 不注册: PDFBox 标准安全处理器实际用到的 provider.
        Log.i(SPIKE_TAG, "$device (c) ${defaultProviders()}")
        val keyLengths = if (PdfEncryption.aes256Available) listOf(256, 128) else listOf(128)
        val unregistered = keyLengths.map { keyLength ->
            "aes$keyLength=${medianMillis { plugin.encryptOutline(output("spike-c-$keyLength.pdf"), keyLength).completed() }} ms"
        }
        val sha256 = sha256Millis()
        Log.i(SPIKE_TAG, "$device (c) encrypt outline.pdf ${unregistered.joinToString(" ")} sha256(8 MiB)=$sha256 ms https=${HttpsURLConnection.getDefaultSSLSocketFactory().javaClass.name}")

        // (a) The full Bouncy Castle replaces the system BC at position 1, in this process (the service runs here too).
        // zh-CN: (a) 完整的 Bouncy Castle 替换系统 BC 并放在位置 1, 作用于本进程 (服务也运行在本进程).
        val full = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider").getDeclaredConstructor().newInstance() as Provider
        val system = providers.getOrNull(bc)
        try {
            Security.removeProvider("BC")
            Security.insertProviderAt(full, 1)
            Log.i(SPIKE_TAG, "$device (a) ${defaultProviders()}")
            val registered = keyLengths.map { keyLength ->
                val target = output("spike-a-$keyLength.pdf")
                val elapsed = medianMillis { plugin.encryptOutline(target, keyLength).completed() }
                assertArrayEquals(GRANTED, plugin.infoOf(target, USER).getStringArray(PdfContract.KEY_PERMISSIONS))
                "aes$keyLength=$elapsed ms"
            }
            Log.i(SPIKE_TAG, "$device (a) encrypt outline.pdf ${registered.joinToString(" ")} sha256(8 MiB)=${sha256Millis()} ms https=${HttpsURLConnection.getDefaultSSLSocketFactory().javaClass.name}")
        } finally {
            Security.removeProvider("BC")
            if (system != null) Security.insertProviderAt(system, bc + 1)
        }
        assertEquals(providers.map { it.name }, Security.getProviders().map { it.name })
    }

    private companion object {
        const val SPIKE_TAG = "PaperCryptoSpike"
        const val USER = "spike-user"
        const val OWNER = "spike-owner"
        val GRANTED = arrayOf(PdfContract.PERMISSION_PRINT, PdfContract.PERMISSION_COPY, PdfContract.PERMISSION_FILL_FORMS)
    }

}

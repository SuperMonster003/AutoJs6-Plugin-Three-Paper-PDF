package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.PDEncryption
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The `run` operations `encrypt` and `decrypt` (roadmap P2.6, D12, D34). Encryption uses the PDFBox standard security
 * handler with the platform JCA providers only: no BouncyCastle provider is registered (appendix D Q4 (c), the spike of
 * P2.6). `encrypt` writes the permissions as given (appendix D Q11); an encrypted input must be opened with its owner
 * password, its old security is replaced. `decrypt` writes an unencrypted copy and needs the owner password as well.
 * zh-CN: `run` 操作 `encrypt` 与 `decrypt` (路线图 P2.6, D12, D34). 加密使用 PDFBox 标准安全处理器, 只用平台 JCA provider, 不注册
 * BouncyCastle provider (附录 D Q4 (c), P2.6 的 spike). `encrypt` 照实写入给定的权限位 (附录 D Q11); 已加密的输入必须用所有者密码
 * 打开, 其原有加密被替换. `decrypt` 写出不加密的副本, 同样需要所有者密码.
 */
object PdfEncryption {

    /** Random bytes of a generated owner password (32 hex characters). zh-CN: 生成的所有者密码的随机字节数 (32 个十六进制字符). */
    private const val GENERATED_OWNER_PASSWORD_BYTES = 16

    /**
     * The transformations of the standard security handler at revision 6 (AES-256): content with PKCS #5 padding, the
     * hash of ISO 32000-2 algorithm 2.B and the `Perms` entry without padding.
     * zh-CN: 标准安全处理器第 6 版 (AES-256) 用到的变换: 内容使用 PKCS #5 填充, ISO 32000-2 算法 2.B 的散列与 `Perms` 条目不填充.
     */
    private val AES_TRANSFORMATIONS = listOf("AES/CBC/PKCS5Padding", "AES/CBC/NoPadding", "AES/ECB/NoPadding")

    /**
     * Whether this device encrypts and decrypts with 256-bit AES keys through the platform providers (feature
     * `crypto.aes256`); probed once.
     * zh-CN: 本设备能否经平台 provider 以 256 位 AES 密钥加解密 (特性 `crypto.aes256`); 只探测一次.
     */
    val aes256Available: Boolean by lazy { probeAes256() }

    internal fun probeAes256(): Boolean = runCatching {
        val key = SecretKeySpec(ByteArray(32), "AES")
        AES_TRANSFORMATIONS.forEach { transformation ->
            val cipher = Cipher.getInstance(transformation)
            if (transformation.contains("/ECB/")) {
                cipher.init(Cipher.ENCRYPT_MODE, key)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(ByteArray(16)))
            }
            cipher.doFinal(ByteArray(16))
        }
        listOf("SHA-256", "SHA-384", "SHA-512").forEach { MessageDigest.getInstance(it).digest(ByteArray(1)) }
        true
    }.getOrDefault(false)

    /**
     * A checked `encrypt` request: [password] opens an encrypted input; [ownerPassword] is null when none was given.
     * zh-CN: 已检查的 `encrypt` 请求: [password] 用于打开已加密的输入; 未给出所有者密码时 [ownerPassword] 为 null.
     */
    class EncryptRequest(
        val password: String?,
        val userPassword: String,
        val ownerPassword: String?,
        val permissions: Set<String>,
        val keyLength: Int,
    )

    /** A checked `decrypt` request. zh-CN: 已检查的 `decrypt` 请求. */
    class DecryptRequest(val password: String?)

    /**
     * Reads the `encrypt` request for [inputCount] inputs: `password`, `userPassword`, `ownerPassword` (at least one of
     * the two not empty), `permissions` (granted names, all when absent) and `keyLength` (128 or 256, default 256;
     * `UNSUPPORTED` when 256 is asked and [aes256] is false).
     * zh-CN: 读取 [inputCount] 个输入的 `encrypt` 请求: `password`, `userPassword`, `ownerPassword` (两者至少一个非空), `permissions`
     * (授予的名称, 缺省为全部) 与 `keyLength` (128 或 256, 默认 256; 要求 256 而 [aes256] 为 false 时报 `UNSUPPORTED`).
     */
    fun encryptRequest(options: PdfOptions, inputCount: Int, aes256: Boolean = aes256Available): EncryptRequest {
        checkSingleInput(PdfOps.ENCRYPT, inputCount)
        val password = options.password()?.takeIf { it.isNotEmpty() }
        val user = options.password(PdfContract.KEY_USER_PASSWORD).orEmpty()
        val owner = options.password(PdfContract.KEY_OWNER_PASSWORD)?.takeIf { it.isNotEmpty() }
        if (user.isEmpty() && owner == null) {
            throw PdfFailure.invalid("${PdfOps.ENCRYPT} needs a non-empty ${PdfContract.KEY_USER_PASSWORD} or ${PdfContract.KEY_OWNER_PASSWORD}")
        }
        val permissions = options.stringArray(PdfContract.KEY_PERMISSIONS)?.mapTo(LinkedHashSet()) { name ->
            if (name == null || name !in PdfContract.PERMISSIONS) {
                throw PdfFailure.invalid("unknown permission \"$name\" in ${PdfContract.KEY_PERMISSIONS}; known: ${PdfContract.PERMISSIONS.joinToString(", ")}")
            }
            name
        } ?: PdfContract.PERMISSIONS
        val keyLength = options.int(PdfContract.KEY_KEY_LENGTH) ?: PdfContract.DEFAULT_ENCRYPTION_KEY_LENGTH
        if (keyLength !in PdfContract.ENCRYPTION_KEY_LENGTHS) {
            throw PdfFailure.invalid("${PdfContract.KEY_KEY_LENGTH} must be 128 or 256, got $keyLength")
        }
        if (keyLength == PdfContract.ENCRYPTION_KEY_LENGTH_256 && !aes256) {
            throw PdfFailure.unsupported("AES-256 is not available on this device (feature ${PdfContract.FEATURE_CRYPTO_AES256}); use ${PdfContract.KEY_KEY_LENGTH} 128")
        }
        return EncryptRequest(password, user, owner, permissions, keyLength)
    }

    /** Reads the `decrypt` request for [inputCount] inputs. zh-CN: 读取 [inputCount] 个输入的 `decrypt` 请求. */
    fun decryptRequest(options: PdfOptions, inputCount: Int): DecryptRequest {
        checkSingleInput(PdfOps.DECRYPT, inputCount)
        return DecryptRequest(options.password()?.takeIf { it.isNotEmpty() })
    }

    private fun checkSingleInput(op: String, inputCount: Int) {
        if (inputCount != 1) throw PdfFailure.invalid("$op takes exactly one input, got $inputCount")
    }

    /**
     * `encrypt`: opens the single input, sets the security of [request] and writes it to the single output.
     * zh-CN: `encrypt`: 打开唯一的输入, 设置 [request] 指定的加密并写到唯一的输出.
     */
    fun encrypt(request: EncryptRequest, input: ParcelFileDescriptor, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_PROCESSING, 0, 1)
        return PdfLoader.load(DescriptorSource.of(input), request.password, directory).use { loaded ->
            val warnings = protect(loaded.document, request)
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.writeProtected(loaded.document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
            result(loaded.document.numberOfPages, size, warnings)
        }
    }

    /**
     * `decrypt`: opens the single input and writes an unencrypted copy; the owner password is needed
     * (`PERMISSION_DENIED` otherwise), an unencrypted input is copied with a warning.
     * zh-CN: `decrypt`: 打开唯一的输入并写出不加密的副本; 需要所有者密码 (否则报 `PERMISSION_DENIED`), 未加密的输入照样复制并给出警告.
     */
    fun decrypt(request: DecryptRequest, input: ParcelFileDescriptor, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_PROCESSING, 0, 1)
        return PdfLoader.load(DescriptorSource.of(input), request.password, directory).use { loaded ->
            val warnings = decryptWarnings(loaded.document)
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(loaded.document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
            result(loaded.document.numberOfPages, size, warnings)
        }
    }

    /** The warnings of `decrypt` on [document]. zh-CN: `decrypt` 对 [document] 给出的警告. */
    fun decryptWarnings(document: PDDocument): List<String> =
        if (document.isEncrypted) emptyList() else listOf("the input is not encrypted; it was copied unchanged")

    /**
     * Sets the security of [request] on [document] (the old security of an encrypted input is replaced, which needs its
     * owner password) and answers the warnings. Without an owner password the user password opens as owner when every
     * permission is granted; otherwise a random owner password keeps the permissions in force (appendix D Q36 (a)).
     * zh-CN: 在 [document] 上设置 [request] 指定的加密 (已加密输入的原有加密被替换, 这需要其所有者密码) 并返回警告. 未给出所有者密码时,
     * 若授予全部权限则用户密码以所有者身份打开; 否则使用随机所有者密码使权限位生效 (附录 D Q36 (a)).
     */
    fun protect(document: PDDocument, request: EncryptRequest): List<String> {
        if (document.isEncrypted) {
            if (!document.currentAccessPermission.isOwnerPermission) {
                throw PdfFailure(PdfErrorCodes.PERMISSION_DENIED, "the input was opened with its user password; changing its security needs the owner password")
            }
            // A fresh dictionary: protect() would otherwise rewrite the old one and keep its other entries.
            // zh-CN: 使用新的字典: 否则 protect() 会改写旧字典并保留其中的其他条目.
            document.setEncryptionDictionary(PDEncryption())
        }
        val warnings = ArrayList<String>()
        val restricted = request.permissions != PdfContract.PERMISSIONS
        val owner = when {
            request.ownerPassword != null -> request.ownerPassword
            restricted -> {
                warnings += "no ${PdfContract.KEY_OWNER_PASSWORD} was given; a random owner password keeps the permissions in force, so they cannot be lifted and decrypt cannot remove the security later"
                generatedOwnerPassword()
            }
            else -> request.userPassword
        }
        if (restricted && owner == request.userPassword) {
            warnings += "${PdfContract.KEY_USER_PASSWORD} equals ${PdfContract.KEY_OWNER_PASSWORD}; the user password opens the document as owner, so the permissions are not in force"
        }
        val policy = StandardProtectionPolicy(owner, request.userPassword, accessPermission(request.permissions))
        policy.encryptionKeyLength = request.keyLength
        policy.isPreferAES = true
        document.protect(policy)
        return warnings
    }

    /**
     * The access permission granting exactly [granted] (the inverse of `DocumentInfoReader.permissions`).
     * zh-CN: 恰好授予 [granted] 的访问权限 (`DocumentInfoReader.permissions` 的逆映射).
     */
    fun accessPermission(granted: Set<String>): AccessPermission = AccessPermission().apply {
        setCanPrint(PdfContract.PERMISSION_PRINT in granted)
        setCanExtractContent(PdfContract.PERMISSION_COPY in granted)
        setCanModify(PdfContract.PERMISSION_MODIFY in granted)
        setCanModifyAnnotations(PdfContract.PERMISSION_ANNOTATE in granted)
        setCanFillInForm(PdfContract.PERMISSION_FILL_FORMS in granted)
        setCanExtractForAccessibility(PdfContract.PERMISSION_EXTRACT_FOR_ACCESSIBILITY in granted)
        setCanAssembleDocument(PdfContract.PERMISSION_ASSEMBLE in granted)
        setCanPrintFaithful(PdfContract.PERMISSION_PRINT_HIGH_QUALITY in granted)
    }

    private fun generatedOwnerPassword(): String {
        val bytes = ByteArray(GENERATED_OWNER_PASSWORD_BYTES).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun result(pageCount: Int, sizeBytes: Long, warnings: Collection<String>): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_PAGE_COUNT, pageCount)
        put(PdfContract.KEY_SIZE_BYTES, sizeBytes)
        if (warnings.isNotEmpty()) put(PdfContract.KEY_WARNINGS, warnings.toTypedArray())
    }

}

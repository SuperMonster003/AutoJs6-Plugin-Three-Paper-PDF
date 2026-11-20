package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.FileSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures.PdfFixtures
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.DocumentInfoReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.MapValues
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.assertWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * `encrypt` and `decrypt` on generated fixtures (roadmap P2.6): the request checks, a missing password is
 * `PASSWORD_REQUIRED` and a wrong one `WRONG_PASSWORD`, the user password gets exactly the requested permissions and the
 * owner password all of them (AES-128 and AES-256), re-encryption and `decrypt` need the owner password, and the rules
 * without an owner password (appendix D Q36).
 * zh-CN: 在生成的夹具上验证 `encrypt` 与 `decrypt` (路线图 P2.6): 请求检查, 缺少密码报 `PASSWORD_REQUIRED`, 密码错误报
 * `WRONG_PASSWORD`, 用户密码恰好得到所请求的权限位, 所有者密码得到全部权限 (AES-128 与 AES-256), 重新加密与 `decrypt` 需要所有者
 * 密码, 以及未给出所有者密码时的规则 (附录 D Q36).
 */
class PdfEncryptionTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("paper-encryption").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun options(vararg pairs: Pair<String, Any?>) = PdfOptions(MapValues(mapOf(*pairs)))

    private class Encrypted(val bytes: ByteArray, val warnings: List<PdfWarning>)

    /** Encrypts [bytes] (opened with [password]) like the operation does. zh-CN: 像操作那样加密 [bytes] (用 [password] 打开). */
    private fun encrypt(bytes: ByteArray, vararg pairs: Pair<String, Any?>, password: String? = null): Encrypted {
        val request = PdfEncryption.encryptRequest(options(*pairs), 1, aes256 = true)
        return PDDocument.load(bytes, password.orEmpty()).use { document ->
            val warnings = PdfEncryption.protect(document, request)
            val output = ByteArrayOutputStream()
            val size = PdfWriting.writeProtected(document, output, StopSignal.NEVER)
            assertEquals(output.size().toLong(), size)
            Encrypted(output.toByteArray(), warnings)
        }
    }

    private fun <T> opened(bytes: ByteArray, password: String?, block: (PDDocument) -> T): T {
        val file = File(directory, "source.pdf").apply { writeBytes(bytes) }
        return FileSource(RandomAccessFile(file, "r")).use { source ->
            PdfLoader.load(source, password, directory).use { block(it.document) }
        }
    }

    private fun failureOf(bytes: ByteArray, password: String?): String {
        val file = File(directory, "source.pdf").apply { writeBytes(bytes) }
        return FileSource(RandomAccessFile(file, "r")).use { source ->
            assertThrows(PdfFailure::class.java) { PdfLoader.load(source, password, directory).close() }.code
        }
    }

    private fun permissionsOf(document: PDDocument) = DocumentInfoReader.permissions(document.currentAccessPermission).toList()

    private fun inContractOrder(vararg names: String) = PdfContract.PERMISSIONS.filter { it in names }

    private fun pageContents(document: PDDocument) = document.pages.map { it.contents.readBytes() }

    private fun assertInvalid(code: String, block: () -> Unit) = assertEquals(code, assertThrows(PdfFailure::class.java) { block() }.code)

    @Test
    fun `the encrypt request checks its inputs, passwords, permissions and key length`() {
        val user = PdfContract.KEY_USER_PASSWORD to "u"
        val invalid = PdfErrorCodes.INVALID_ARGUMENT
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(user), 0) }
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(user), 2) }
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(), 1) }
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(PdfContract.KEY_USER_PASSWORD to ""), 1) }
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(user, PdfContract.KEY_PERMISSIONS to arrayOf("print", "export")), 1) }
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(user, PdfContract.KEY_KEY_LENGTH to 192), 1) }
        val tooLong = "x".repeat(PdfContract.MAX_PASSWORD_LENGTH + 1)
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(PdfContract.KEY_OWNER_PASSWORD to tooLong), 1) }
        assertInvalid(invalid) { PdfEncryption.encryptRequest(options(user, PdfContract.KEY_PASSWORD to tooLong), 1) }
        // AES-256 needs the feature crypto.aes256; 128 bits work without it. zh-CN: AES-256 需要特性 crypto.aes256; 128 位不需要.
        assertInvalid(PdfErrorCodes.UNSUPPORTED) { PdfEncryption.encryptRequest(options(user), 1, aes256 = false) }
        assertEquals(128, PdfEncryption.encryptRequest(options(user, PdfContract.KEY_KEY_LENGTH to 128), 1, aes256 = false).keyLength)

        val defaults = PdfEncryption.encryptRequest(options(user, PdfContract.KEY_OWNER_PASSWORD to "", PdfContract.KEY_PASSWORD to ""), 1, aes256 = true)
        assertEquals(PdfContract.DEFAULT_ENCRYPTION_KEY_LENGTH, defaults.keyLength)
        assertEquals(PdfContract.PERMISSIONS, defaults.permissions)
        assertNull("an empty owner password counts as none", defaults.ownerPassword)
        assertNull(defaults.password)
        val owner = PdfEncryption.encryptRequest(options(PdfContract.KEY_OWNER_PASSWORD to "o", PdfContract.KEY_PERMISSIONS to arrayOf<String>()), 1, aes256 = true)
        assertEquals("", owner.userPassword)
        assertEquals(emptySet<String>(), owner.permissions)
    }

    @Test
    fun `the decrypt request takes one input`() {
        assertInvalid(PdfErrorCodes.INVALID_ARGUMENT) { PdfEncryption.decryptRequest(options(), 0) }
        assertInvalid(PdfErrorCodes.INVALID_ARGUMENT) { PdfEncryption.decryptRequest(options(), 2) }
        assertEquals("p", PdfEncryption.decryptRequest(options(PdfContract.KEY_PASSWORD to "p"), 1).password)
        assertNull(PdfEncryption.decryptRequest(options(), 1).password)
    }

    @Test
    fun `a wrong password is WRONG_PASSWORD and the user password gets the requested permissions`() {
        val original = PdfFixtures.multiPageWithOutline()
        val contents = PDDocument.load(original).use { pageContents(it) }
        val granted = arrayOf(PdfContract.PERMISSION_FILL_FORMS, PdfContract.PERMISSION_PRINT, PdfContract.PERMISSION_COPY)
        for ((keyLength, version, revision) in listOf(Triple(256, 5, 6), Triple(128, 4, 4))) {
            val encrypted = encrypt(
                original,
                PdfContract.KEY_USER_PASSWORD to "user-$keyLength",
                PdfContract.KEY_OWNER_PASSWORD to "owner-$keyLength",
                PdfContract.KEY_PERMISSIONS to granted,
                PdfContract.KEY_KEY_LENGTH to keyLength,
            )
            assertEquals(emptyList<String>(), encrypted.warnings)
            val bytes = encrypted.bytes
            assertEquals(PdfErrorCodes.PASSWORD_REQUIRED, failureOf(bytes, null))
            assertEquals(PdfErrorCodes.WRONG_PASSWORD, failureOf(bytes, "wrong"))
            opened(bytes, "user-$keyLength") { document ->
                assertTrue(document.isEncrypted)
                assertFalse(document.currentAccessPermission.isOwnerPermission)
                assertEquals("$keyLength bits", inContractOrder(*granted), permissionsOf(document))
                assertEquals(keyLength, document.encryption.length)
                assertEquals(version, document.encryption.version)
                assertEquals(revision, document.encryption.revision)
                // AES, not RC4, for 128 bits too. zh-CN: 128 位时同样是 AES 而非 RC4.
                assertEquals(if (keyLength == 256) "AESV3" else "AESV2", document.encryption.stdCryptFilterDictionary.cryptFilterMethod.name)
                assertEquals(contents.map { it.toList() }, pageContents(document).map { it.toList() })
                assertEquals(PdfFixtures.TITLE, document.documentInformation.title)
                assertEquals(ThreePaperPdfContract.PRODUCER, document.documentInformation.producer)
            }
            opened(bytes, "owner-$keyLength") { document ->
                assertTrue(document.currentAccessPermission.isOwnerPermission)
                assertEquals(PdfContract.PERMISSIONS.toList(), permissionsOf(document))
            }
        }
    }

    @Test
    fun `an encrypted input is encrypted again only with its owner password`() {
        val original = PdfFixtures.encrypted(keyLength = 128)
        val contents = PDDocument.load(original, PdfFixtures.OWNER_PASSWORD).use { pageContents(it) }
        val request = PdfEncryption.encryptRequest(options(PdfContract.KEY_USER_PASSWORD to "new-user", PdfContract.KEY_OWNER_PASSWORD to "new-owner"), 1, aes256 = true)
        PDDocument.load(original, PdfFixtures.USER_PASSWORD).use { document ->
            assertInvalid(PdfErrorCodes.PERMISSION_DENIED) { PdfEncryption.protect(document, request) }
        }
        val encrypted = encrypt(
            original,
            PdfContract.KEY_USER_PASSWORD to "new-user",
            PdfContract.KEY_OWNER_PASSWORD to "new-owner",
            PdfContract.KEY_PERMISSIONS to arrayOf(PdfContract.PERMISSION_ASSEMBLE),
            password = PdfFixtures.OWNER_PASSWORD,
        ).bytes
        assertEquals(PdfErrorCodes.WRONG_PASSWORD, failureOf(encrypted, PdfFixtures.USER_PASSWORD))
        assertEquals(PdfErrorCodes.WRONG_PASSWORD, failureOf(encrypted, PdfFixtures.OWNER_PASSWORD))
        opened(encrypted, "new-user") { document ->
            assertEquals(listOf(PdfContract.PERMISSION_ASSEMBLE), permissionsOf(document))
            assertEquals(6, document.encryption.revision)
            assertEquals(contents.map { it.toList() }, pageContents(document).map { it.toList() })
        }
    }

    @Test
    fun `without an owner password the permissions stay in force unless all are granted`() {
        val original = PdfFixtures.singlePageText()
        // Every permission granted: the user password opens as owner. zh-CN: 授予全部权限: 用户密码以所有者身份打开.
        val open = encrypt(original, PdfContract.KEY_USER_PASSWORD to "only-user")
        assertEquals(emptyList<String>(), open.warnings)
        opened(open.bytes, "only-user") { assertTrue(it.currentAccessPermission.isOwnerPermission) }

        // Restricted: a random owner password, so the user password gets the permissions (appendix D Q36 (a)).
        // zh-CN: 有限制: 使用随机所有者密码, 因此用户密码得到所请求的权限位 (附录 D Q36 (a)).
        val restricted = encrypt(original, PdfContract.KEY_USER_PASSWORD to "only-user", PdfContract.KEY_PERMISSIONS to arrayOf(PdfContract.PERMISSION_PRINT))
        assertEquals(1, restricted.warnings.size)
        assertTrue(restricted.warnings.single().text, restricted.warnings.single().text.contains("random owner password"))
        assertWarning(restricted.warnings.single(), PdfWarningCodes.OWNER_PASSWORD_RANDOM)
        opened(restricted.bytes, "only-user") { document ->
            assertFalse(document.currentAccessPermission.isOwnerPermission)
            assertEquals(listOf(PdfContract.PERMISSION_PRINT), permissionsOf(document))
            // decrypt cannot remove that security. zh-CN: decrypt 无法去除这种加密.
            assertInvalid(PdfErrorCodes.PERMISSION_DENIED) { PdfWriting.write(document, ByteArrayOutputStream(), StopSignal.NEVER) }
        }

        // Only an owner password: anyone opens it with the permissions, the owner gets all.
        // zh-CN: 只有所有者密码: 任何人都能以受限权限打开, 所有者得到全部权限.
        val ownerOnly = encrypt(original, PdfContract.KEY_OWNER_PASSWORD to "only-owner", PdfContract.KEY_PERMISSIONS to arrayOf(PdfContract.PERMISSION_COPY))
        assertEquals(emptyList<String>(), ownerOnly.warnings)
        opened(ownerOnly.bytes, null) { document ->
            assertTrue(document.isEncrypted)
            assertEquals(listOf(PdfContract.PERMISSION_COPY), permissionsOf(document))
        }
        opened(ownerOnly.bytes, "only-owner") { assertTrue(it.currentAccessPermission.isOwnerPermission) }

        // The same password for both with restrictions: a warning, the permissions are not in force.
        // zh-CN: 两者密码相同且有限制: 给出警告, 权限位不生效.
        val same = encrypt(
            original,
            PdfContract.KEY_USER_PASSWORD to "same",
            PdfContract.KEY_OWNER_PASSWORD to "same",
            PdfContract.KEY_PERMISSIONS to arrayOf(PdfContract.PERMISSION_PRINT),
        )
        assertEquals(1, same.warnings.size)
        assertTrue(same.warnings.single().text, same.warnings.single().text.contains("not in force"))
        assertWarning(same.warnings.single(), PdfWarningCodes.USER_PASSWORD_IS_OWNER)
        opened(same.bytes, "same") { assertTrue(it.currentAccessPermission.isOwnerPermission) }
    }

    @Test
    fun `decrypt writes an unencrypted copy with the owner password only`() {
        val original = PdfFixtures.encrypted()
        PDDocument.load(original, PdfFixtures.USER_PASSWORD).use { document ->
            assertEquals(emptyList<String>(), PdfEncryption.decryptWarnings(document))
            assertInvalid(PdfErrorCodes.PERMISSION_DENIED) { PdfWriting.write(document, ByteArrayOutputStream(), StopSignal.NEVER) }
        }
        val decrypted = PDDocument.load(original, PdfFixtures.OWNER_PASSWORD).use { document ->
            ByteArrayOutputStream().also { PdfWriting.write(document, it, StopSignal.NEVER) }.toByteArray()
        }
        opened(decrypted, null) { document ->
            assertFalse(document.isEncrypted)
            assertEquals(2, document.numberOfPages)
            assertEquals(PdfFixtures.TITLE, document.documentInformation.title)
        }
        PDDocument.load(PdfFixtures.singlePageText()).use { document ->
            assertEquals(1, PdfEncryption.decryptWarnings(document).size)
            assertWarning(PdfEncryption.decryptWarnings(document).single(), PdfWarningCodes.NOT_ENCRYPTED)
        }
    }

    @Test
    fun `the JVM has AES-256 and the permission mapping is the inverse of the reader`() {
        assertTrue(PdfEncryption.probeAes256())
        for (name in PdfContract.PERMISSIONS) {
            assertArrayEquals(name, arrayOf(name), DocumentInfoReader.permissions(PdfEncryption.accessPermission(setOf(name))))
        }
        assertArrayEquals(PdfContract.PERMISSIONS.toTypedArray(), DocumentInfoReader.permissions(PdfEncryption.accessPermission(PdfContract.PERMISSIONS)))
    }

}

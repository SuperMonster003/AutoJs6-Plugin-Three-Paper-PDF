package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfEncryption
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
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
import kotlin.math.abs

/**
 * The file manager tools on a device without the host (roadmap P3.3, appendix D Q74 / Q76): each tool runs on the
 * shared engine and writes through [FakeHostSession] the way it does through the host session, with the result names,
 * the ` (1)` of a taken name, the owner password rule of encrypted inputs (Q30), a cancel and failures that leave
 * nothing behind.
 * zh-CN: 不经宿主在设备上运行文件管理器工具 (路线图 P3.3, 附录 D Q74 / Q76): 每个工具都在共用引擎上运行, 并像经宿主会话那样经
 * [FakeHostSession] 写入; 覆盖结果名称, 名称被占用时的 ` (1)`, 加密输入的所有者密码规则 (Q30), 取消, 以及不留下任何内容的失败.
 */
@RunWith(AndroidJUnit4::class)
class PdfToolRunnerDeviceTest {

    private val context get() = PdfServiceHarness.context

    private val root by lazy { File(context.cacheDir, "explorer-runner-test") }

    private lateinit var host: FakeHostSession

    private lateinit var plugin: IPdfPlugin

    @Before
    fun setUp() {
        host = FakeHostSession(root)
        plugin = ToolEngine.acquire(context)
    }

    @After
    fun tearDown() {
        ToolEngine.release()
        root.deleteRecursively()
    }

    @Test
    fun mergeKeepsEachInputAsAnOutlineEntryAndNumbersATakenName() {
        File(host.directory, "Outline_merged.pdf").writeText("taken")
        val result = runner().run(ExplorerTool.MERGE, listOf(input("outline.pdf", "Outline.pdf"), input("text.pdf", "Text.pdf")), ToolOptions.Merge(true))
        assertEquals("${FakeHostSession.PARENT_PATH}/Outline_merged (1).pdf", result.displayPath)
        assertEquals(1, result.files)
        PDDocument.load(output("Outline_merged (1).pdf")).use { merged ->
            assertEquals(7, merged.numberOfPages)
            assertEquals(listOf("Outline", "Text"), merged.documentCatalog.documentOutline.children().map { it.title })
        }
        assertEquals(listOf(ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME), host.conflictModes)
        assertTrue(host.stagingEmpty())
    }

    @Test
    fun mergeWithoutOutlinesDropsTheBookmarks() {
        runner().run(ExplorerTool.MERGE, listOf(input("text.pdf", "Text.pdf"), input("outline.pdf", "Outline.pdf")), ToolOptions.Merge(false))
        PDDocument.load(output("Text_merged.pdf")).use { merged ->
            assertEquals(7, merged.numberOfPages)
            assertTrue(merged.documentCatalog.documentOutline?.children()?.none() ?: true)
        }
    }

    @Test
    fun anEncryptedInputNeedsItsOwnerPasswordToBeWrittenUnencrypted() {
        listOf(USER_PASSWORD to PdfErrorCodes.PERMISSION_DENIED, "wrong" to PdfErrorCodes.WRONG_PASSWORD).forEach { (password, code) ->
            val failure = assertThrows(ToolFailure::class.java) {
                runner().run(ExplorerTool.MERGE, listOf(input("encrypted.pdf", password = password), input("text.pdf")), ToolOptions.Merge(true))
            }
            assertEquals(password, code, failure.code)
            assertEquals(emptyList<String>(), host.files())
            assertTrue(host.stagingEmpty())
        }
        runner().run(ExplorerTool.MERGE, listOf(input("encrypted.pdf", password = OWNER_PASSWORD), input("text.pdf")), ToolOptions.Merge(true))
        PDDocument.load(output("encrypted_merged.pdf")).use { merged ->
            assertEquals(3, merged.numberOfPages)
            assertFalse(merged.isEncrypted)
        }
    }

    @Test
    fun splitWritesOneFolderOfPartsByCountOrByRanges() {
        val every = runner().run(ExplorerTool.SPLIT, listOf(input("outline.pdf", "Outline.pdf")), ToolOptions.Split(2, null))
        assertEquals("${FakeHostSession.PARENT_PATH}/Outline_split", every.displayPath)
        assertEquals(3, every.files)
        val ranges = runner().run(ExplorerTool.SPLIT, listOf(input("outline.pdf", "Outline.pdf")), ToolOptions.Split(null, listOf("1-2", "5-")))
        assertEquals("${FakeHostSession.PARENT_PATH}/Outline_split (1)", ranges.displayPath)
        assertEquals(
            listOf(
                "Outline_split (1)/Outline-01.pdf", "Outline_split (1)/Outline-02.pdf",
                "Outline_split/Outline-01.pdf", "Outline_split/Outline-02.pdf", "Outline_split/Outline-03.pdf",
            ),
            host.files(),
        )
        host.files().forEach { path -> PDDocument.load(output(path)).use { assertEquals(path, 2, it.numberOfPages) } }
        PDDocument.load(output("Outline_split (1)/Outline-02.pdf")).use { part ->
            val first = PDFTextStripper().apply { endPage = 1 }.getText(part)
            assertTrue(first, first.contains("Page 5 of 6"))
        }
    }

    @Test
    fun imagesAreOnePicturePerSelectedPageAtTheChosenResolution() {
        val png = runner().run(ExplorerTool.TO_IMAGES, listOf(input("outline.pdf", "Outline.pdf")), ToolOptions.ToImages(PdfContract.FORMAT_PNG, 72, "2-3"))
        assertEquals(2, png.files)
        runner().run(ExplorerTool.TO_IMAGES, listOf(input("text.pdf", "Text.pdf")), ToolOptions.ToImages(PdfContract.FORMAT_JPEG, 144, null))
        assertEquals(listOf("Outline_images/Outline-p02.png", "Outline_images/Outline-p03.png", "Text_images/Text-p01.jpg"), host.files())
        bounds("Outline_images/Outline-p02.png").let { (width, height, type) ->
            assertEquals("image/png", type)
            assertNear(A4_WIDTH_POINTS, width)
            assertNear(A4_HEIGHT_POINTS, height)
        }
        bounds("Text_images/Text-p01.jpg").let { (width, _, type) ->
            assertEquals("image/jpeg", type)
            assertNear(A4_WIDTH_POINTS * 2, width)
        }
    }

    @Test
    fun extractedTextFollowsThePageOrderWithABlankLineBetweenPages() {
        runner().run(ExplorerTool.EXTRACT_TEXT, listOf(input("outline.pdf", "Outline.pdf")), ToolOptions.ExtractText("2-3"))
        val text = output("Outline_text.txt").readText()
        assertFalse(text, text.contains("Page 1 of 6"))
        assertTrue(text, text.indexOf("Page 2 of 6") in 0 until text.indexOf("Page 3 of 6"))
        assertTrue(text, text.contains("\n\n"))
        runner().run(ExplorerTool.EXTRACT_TEXT, listOf(input("text.pdf", "Text.pdf")), ToolOptions.ExtractText(null))
        assertTrue(output("Text_text.txt").readText().contains("Search target: needle"))
    }

    @Test
    fun encryptSetsTheOpenPasswordAndDecryptRemovesIt() {
        runner().run(ExplorerTool.ENCRYPT, listOf(input("text.pdf", "Text.pdf")), ToolOptions.Encrypt("s3cret"))
        val encrypted = output("Text_encrypted.pdf")
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(encrypted).close() }
        PDDocument.load(encrypted, "s3cret").use { document ->
            assertTrue(document.isEncrypted)
            assertEquals(if (PdfEncryption.aes256Available) 256 else 128, document.encryption.length)
            // The owner password is the open password, so the file stays fully usable (Q76). zh-CN: 所有者密码即打开密码 (Q76).
            assertTrue(document.currentAccessPermission.isOwnerPermission)
        }
        val target = ToolTarget(Uri.fromFile(encrypted), encrypted.name)
        runner().run(ExplorerTool.DECRYPT, listOf(ToolInput(target, "s3cret")), ToolOptions.Decrypt)
        PDDocument.load(output("Text_encrypted_decrypted.pdf")).use { assertFalse(it.isEncrypted) }

        val failure = assertThrows(ToolFailure::class.java) {
            runner().run(ExplorerTool.DECRYPT, listOf(input("encrypted.pdf", password = USER_PASSWORD)), ToolOptions.Decrypt)
        }
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, failure.code)
        runner().run(ExplorerTool.DECRYPT, listOf(input("encrypted.pdf", password = OWNER_PASSWORD)), ToolOptions.Decrypt)
        PDDocument.load(output("encrypted_decrypted.pdf")).use { decrypted ->
            assertFalse(decrypted.isEncrypted)
            assertEquals(2, decrypted.numberOfPages)
        }
    }

    @Test
    fun cancellingStopsAtTheNextPageAndRemovesThePartialFolder() {
        lateinit var job: PdfToolRunner
        job = runner { job.cancel() }
        val failure = assertThrows(ToolFailure::class.java) {
            job.run(ExplorerTool.TO_IMAGES, listOf(input(manyPages(), "Many.pdf")), ToolOptions.ToImages(PdfContract.FORMAT_PNG, 72, null))
        }
        assertEquals(PdfErrorCodes.CANCELLED, failure.code)
        assertEquals(emptyList<String>(), host.files())
        assertTrue(host.stagingEmpty())
        assertEquals(1, host.aborted)
    }

    @Test
    fun badPagesAndMissingFilesFailBeforeAnythingIsCreated() {
        val pages = assertThrows(ToolFailure::class.java) {
            runner().run(ExplorerTool.TO_IMAGES, listOf(input("outline.pdf")), ToolOptions.ToImages(PdfContract.FORMAT_PNG, 72, "9"))
        }
        assertEquals(PdfErrorCodes.PAGE_OUT_OF_RANGE, pages.code)
        val missing = ToolTarget(Uri.fromFile(File(root, "missing.pdf")), "missing.pdf")
        val notFound = assertThrows(ToolFailure::class.java) { runner().run(ExplorerTool.EXTRACT_TEXT, listOf(ToolInput(missing, null)), ToolOptions.ExtractText(null)) }
        assertEquals(PdfErrorCodes.FILE_NOT_FOUND, notFound.code)
        assertEquals(emptyList<String>(), host.files())
        assertEquals(0, host.aborted)
        assertNull(host.conflictModes.firstOrNull())
    }

    private fun runner(progress: (Float) -> Unit = {}) = PdfToolRunner(
        plugin = plugin,
        open = { uri -> context.contentResolver.openFileDescriptor(uri, "r") ?: error("no descriptor for $uri") },
        output = HostSessionOutput(host),
        progress = progress,
    )

    private fun input(sample: String, name: String = sample, password: String? = null) =
        input(PdfServiceHarness.sampleFile(sample), name, password)

    private fun input(file: File, name: String, password: String? = null) = ToolInput(ToolTarget(Uri.fromFile(file), name), password)

    private fun output(path: String) = File(host.directory, path)

    private fun bounds(path: String): Triple<Int, Int, String?> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output(path).path, options)
        return Triple(options.outWidth, options.outHeight, options.outMimeType)
    }

    /** Pixel sizes round either way. zh-CN: 像素尺寸可能向任一方向取整. */
    private fun assertNear(expected: Int, actual: Int) {
        assertTrue("$actual is not about $expected", abs(expected - actual) <= 1)
    }

    /** A text document of [MANY] pages, generated once into the cache. zh-CN: [MANY] 页的文本文档, 只生成一次到缓存. */
    private fun manyPages(): File {
        val file = File(context.cacheDir, "samples/explorer-many-pages.pdf")
        if (file.exists()) return file
        file.parentFile?.mkdirs()
        PDDocument().use { document ->
            repeat(MANY) { index ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 28f)
                    stream.newLineAtOffset(72f, 760f)
                    stream.showText("Page ${index + 1} of $MANY")
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    private companion object {
        const val USER_PASSWORD = "paper-user"
        const val OWNER_PASSWORD = "paper-owner"
        const val A4_WIDTH_POINTS = 595
        const val A4_HEIGHT_POINTS = 842
        const val MANY = 40
    }

}

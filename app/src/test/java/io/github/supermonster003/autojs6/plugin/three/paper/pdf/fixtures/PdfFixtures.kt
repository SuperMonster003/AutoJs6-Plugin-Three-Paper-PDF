package io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO

/**
 * PDF fixtures of the JVM tests, generated at run time with PDFBox and never committed (roadmap D39): single-page text,
 * pages with an outline, encrypted, image-only "scan", pages with JPEG and PNG images, rotated pages. Device regression
 * samples come from `.python/generate_fixtures.py` instead (`samples/fixtures/`).
 *
 * On the JVM PDFBox-Android reads the Standard 14 font metrics and CMaps through `PDFBoxResourceLoader`, which needs an
 * Android `AssetManager`; the fixtures therefore write the Helvetica font dictionary and the content streams at the COS
 * level and embed images from encoded bytes (JPEG from `javax.imageio`, Flate for the PNG-like image), so no font,
 * Bitmap or asset is touched. Text extraction of these documents needs a device (or the assets) for the font metrics.
 *
 * zh-CN: JVM 测试用的 PDF 夹具, 运行时由 PDFBox 生成, 不入库 (路线图 D39): 单页文本, 带大纲的多页, 加密, 纯图片 "扫描件", 含 JPEG 与 PNG
 * 图片, 旋转页. 设备回归样例改由 `.python/generate_fixtures.py` 生成 (`samples/fixtures/`).
 *
 * JVM 上 PDFBox-Android 经 `PDFBoxResourceLoader` 读取 Standard 14 字体度量与 CMap, 需要 Android 的 `AssetManager`; 因此夹具在 COS
 * 层写 Helvetica 字体字典与内容流, 图片从已编码的字节嵌入 (JPEG 来自 `javax.imageio`, 类 PNG 图片用 Flate), 不触及字体, Bitmap 与
 * 资源. 这些文档的文本提取需要设备 (或资源文件) 提供字体度量.
 */
object PdfFixtures {

    const val USER_PASSWORD = "paper-user"
    const val OWNER_PASSWORD = "paper-owner"

    const val TITLE = "3-Paper PDF fixture"
    const val AUTHOR = "AutoJs6"
    const val SUBJECT = "Generated test document"
    const val KEYWORDS = "pdf, fixture, test"
    const val CREATOR = "PdfFixtures"
    const val PRODUCER = "PDFBox-Android"

    val TEXT_LINES = listOf("3-Paper PDF fixture", "Hello, PDF.", "The quick brown fox jumps over the lazy dog.")

    /** Chapter titles of [multiPageWithOutline]; each chapter has two pages and one child entry. zh-CN: [multiPageWithOutline] 的章节标题; 每章两页并有一个子条目. */
    val CHAPTERS = listOf("Chapter 1", "Chapter 2", "Chapter 3")

    /** Outline entry without a page destination (`getOutline` answers page 0). zh-CN: 没有页面目标的大纲条目 (`getOutline` 返回 page 0). */
    const val OUTLINE_WITHOUT_PAGE = "Notes"

    /** `/Rotate` of the pages of [rotatedPages]; the last page is a landscape Letter page. zh-CN: [rotatedPages] 各页的 `/Rotate`; 末页为横向 Letter. */
    val ROTATIONS = listOf(0, 90, 180, 270, 0)

    val JPEG_SIZE = 160 to 120
    val PNG_SIZE = 96 to 96
    val SCAN_SIZE = 600 to 848

    private const val FONT = "F1"
    private val FIXED_DATE: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 12, 8, 0, 0)
    }

    fun singlePageText(): ByteArray = document {
        documentInformation = information()
        textPage(TEXT_LINES)
    }

    fun multiPageWithOutline(): ByteArray = document {
        documentInformation = information()
        val outline = PDDocumentOutline()
        CHAPTERS.forEachIndexed { index, chapter ->
            val first = textPage(listOf(chapter, "Page ${index * 2 + 1}"))
            val second = textPage(listOf("$chapter, section 1", "Page ${index * 2 + 2}"))
            val entry = outlineItem(chapter, first)
            entry.addLast(outlineItem("$chapter, section 1", second))
            outline.addLast(entry)
        }
        outline.addLast(PDOutlineItem().apply { title = OUTLINE_WITHOUT_PAGE })
        outline.openNode()
        documentCatalog.documentOutline = outline
    }

    /**
     * Two text pages protected with [USER_PASSWORD] / [OWNER_PASSWORD]; [keyLength] 128 is AES-128, 256 AES-256. The
     * user password grants printing only.
     * zh-CN: 以 [USER_PASSWORD] / [OWNER_PASSWORD] 保护的两页文本; [keyLength] 128 为 AES-128, 256 为 AES-256. 用户密码只授予打印权限.
     */
    fun encrypted(keyLength: Int = 128): ByteArray = document {
        documentInformation = information()
        textPage(listOf("Encrypted page 1"))
        textPage(listOf("Encrypted page 2"))
        val permissions = AccessPermission().apply {
            setCanPrint(true)
            setCanModify(false)
            setCanExtractContent(false)
            setCanModifyAnnotations(false)
            setCanFillInForm(false)
            setCanExtractForAccessibility(false)
            setCanAssembleDocument(false)
            setCanPrintFaithful(false)
        }
        protect(StandardProtectionPolicy(OWNER_PASSWORD, USER_PASSWORD, permissions).apply {
            encryptionKeyLength = keyLength
            isPreferAES = true
        })
    }

    /** One A4 page holding only a grey "scanned" image of text lines, no text layer. zh-CN: 一张只有灰度 "扫描" 文本行图片的 A4 页, 没有文本层. */
    fun imageOnly(): ByteArray = document {
        val (width, height) = SCAN_SIZE
        val pixels = ByteArray(width * height) { 0xF4.toByte() }
        // Dark bars stand in for text lines. zh-CN: 用深色条代替文本行.
        for (line in 0 until 12) {
            val top = 80 + line * 56
            val length = 380 + (line * 37) % 120
            for (y in top until top + 18) for (x in 60 until 60 + length) pixels[y * width + x] = 0x30
        }
        val scan = PDImageXObject(this, ByteArrayInputStream(deflate(pixels)), COSName.FLATE_DECODE, width, height, 8, PDDeviceGray.INSTANCE)
        val page = PDPage(PDRectangle.A4)
        val resources = PDResources()
        val name = resources.add(scan)
        page.resources = resources
        val box = page.mediaBox
        setContents(page, "q ${box.width} 0 0 ${box.height} 0 0 cm /${name.name} Do Q\n")
        addPage(page)
    }

    /**
     * One text page with a JPEG image (`DCTDecode`, extracted as stored) and a PNG-like RGB image with an alpha soft
     * mask (`FlateDecode` + `SMask`, extracted as PNG), roadmap D33.
     * zh-CN: 一页文本加一张 JPEG 图片 (`DCTDecode`, 原样导出) 与一张带 alpha 软蒙版的类 PNG RGB 图片 (`FlateDecode` + `SMask`, 导出为 PNG),
     * 路线图 D33.
     */
    fun withImages(): ByteArray = document {
        val page = textPage(listOf("Page with images"))
        val resources = page.resources
        val (jpegWidth, jpegHeight) = JPEG_SIZE
        val photo = PDImageXObject(this, ByteArrayInputStream(jpeg(jpegWidth, jpegHeight)), COSName.DCT_DECODE, jpegWidth, jpegHeight, 8, PDDeviceRGB.INSTANCE)
        val (pngWidth, pngHeight) = PNG_SIZE
        val rgb = ByteArray(pngWidth * pngHeight * 3)
        val alpha = ByteArray(pngWidth * pngHeight)
        for (y in 0 until pngHeight) for (x in 0 until pngWidth) {
            val index = y * pngWidth + x
            rgb[index * 3] = (x * 255 / (pngWidth - 1)).toByte()
            rgb[index * 3 + 1] = 0x60
            rgb[index * 3 + 2] = (y * 255 / (pngHeight - 1)).toByte()
            val dx = x - pngWidth / 2
            val dy = y - pngHeight / 2
            alpha[index] = if (dx * dx + dy * dy <= (pngWidth / 2) * (pngWidth / 2)) 0xFF.toByte() else 0
        }
        val badge = PDImageXObject(this, ByteArrayInputStream(deflate(rgb)), COSName.FLATE_DECODE, pngWidth, pngHeight, 8, PDDeviceRGB.INSTANCE)
        val mask = PDImageXObject(this, ByteArrayInputStream(deflate(alpha)), COSName.FLATE_DECODE, pngWidth, pngHeight, 8, PDDeviceGray.INSTANCE)
        badge.cosObject.setItem(COSName.SMASK, mask)
        val photoName = resources.add(photo)
        val badgeName = resources.add(badge)
        val text = page.contentStreams.next().toByteArray().toString(Charsets.ISO_8859_1)
        setContents(
            page,
            text + "q 240 0 0 180 72 500 cm /${photoName.name} Do Q\nq 144 0 0 144 360 500 cm /${badgeName.name} Do Q\n",
        )
    }

    /** Pages rotated by [ROTATIONS]; every page names its rotation in its text. zh-CN: 按 [ROTATIONS] 旋转的页; 每页文本写明自己的旋转角度. */
    fun rotatedPages(): ByteArray = document {
        ROTATIONS.forEachIndexed { index, rotation ->
            val landscape = index == ROTATIONS.lastIndex
            val box = if (landscape) PDRectangle(PDRectangle.LETTER.height, PDRectangle.LETTER.width) else PDRectangle.A4
            textPage(listOf("Rotate $rotation", if (landscape) "Landscape Letter" else "Portrait A4"), box).rotation = rotation
        }
    }

    /** Every fixture by its file name. zh-CN: 按文件名列出全部夹具. */
    fun all(): Map<String, ByteArray> = linkedMapOf(
        "text.pdf" to singlePageText(),
        "outline.pdf" to multiPageWithOutline(),
        "encrypted.pdf" to encrypted(),
        "scanned.pdf" to imageOnly(),
        "images.pdf" to withImages(),
        "rotated.pdf" to rotatedPages(),
    )

    private fun document(build: PDDocument.() -> Unit): ByteArray = PDDocument().use { document ->
        document.build()
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun information() = PDDocumentInformation().apply {
        title = TITLE
        author = AUTHOR
        subject = SUBJECT
        keywords = KEYWORDS
        creator = CREATOR
        producer = PRODUCER
        creationDate = FIXED_DATE.clone() as Calendar
        modificationDate = FIXED_DATE.clone() as Calendar
    }

    private fun PDDocument.textPage(lines: List<String>, box: PDRectangle = PDRectangle.A4): PDPage {
        val page = PDPage(box)
        val fonts = COSDictionary().apply { setItem(COSName.getPDFName(FONT), helvetica()) }
        page.resources = PDResources().apply { cosObject.setItem(COSName.FONT, fonts) }
        val content = buildString {
            append("BT\n/$FONT 18 Tf\n24 TL\n72 ${box.height - 96} Td\n")
            for (line in lines) append('(').append(escape(line)).append(") Tj T*\n")
            append("ET\n")
        }
        setContents(page, content)
        addPage(page)
        return page
    }

    private fun PDDocument.setContents(page: PDPage, content: String) {
        page.setContents(PDStream(this, ByteArrayInputStream(content.toByteArray(Charsets.ISO_8859_1)), COSName.FLATE_DECODE))
    }

    private fun outlineItem(title: String, page: PDPage) = PDOutlineItem().apply {
        this.title = title
        destination = PDPageFitDestination().apply { this.page = page }
    }

    private fun helvetica() = COSDictionary().apply {
        setItem(COSName.TYPE, COSName.FONT)
        setItem(COSName.SUBTYPE, COSName.TYPE1)
        setItem(COSName.BASE_FONT, COSName.getPDFName("Helvetica"))
        setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING)
    }

    private fun escape(text: String): String {
        require(text.all { it.code in 0x20..0x7E }) { "fixture text is printable ASCII: $text" }
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            image.setRGB(x, y, (x * 255 / (width - 1) shl 16) or (y * 255 / (height - 1) shl 8) or 0x80)
        }
        return ByteArrayOutputStream().also { check(ImageIO.write(image, "jpg", it)) { "no JPEG writer" } }.toByteArray()
    }

    private fun deflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(bytes) }
    }.toByteArray()

}

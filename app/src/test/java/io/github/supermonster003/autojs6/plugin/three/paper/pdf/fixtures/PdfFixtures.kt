package io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
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
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO

/**
 * PDF fixtures of the JVM tests, generated at run time with PDFBox and never committed (roadmap D39): single-page text,
 * pages with an outline, encrypted, image-only "scan", pages with JPEG and PNG images, rotated pages; for 1.1.0 (P6.2)
 * an AcroForm form, a multi-page 300 dpi scan, a file with object streams, XMP metadata, static XFA with signature
 * fields, dynamic XFA and a text-type multi-page sample. Device regression samples come from
 * `.python/generate_fixtures.py` instead (`samples/fixtures/`).
 *
 * On the JVM PDFBox-Android reads the Standard 14 font metrics and CMaps through `PDFBoxResourceLoader`, which needs an
 * Android `AssetManager`; the fixtures therefore write the Helvetica font dictionary and the content streams at the COS
 * level and embed images from encoded bytes (JPEG from `javax.imageio`, Flate for the PNG-like image), so no font,
 * Bitmap or asset is touched. Text extraction of these documents needs a device (or the assets) for the font metrics.
 *
 * zh-CN: JVM 测试用的 PDF 夹具, 运行时由 PDFBox 生成, 不入库 (路线图 D39): 单页文本, 带大纲的多页, 加密, 纯图片 "扫描件", 含 JPEG 与 PNG
 * 图片, 旋转页; 1.1.0 (P6.2) 另有 AcroForm 表单, 300 dpi 多页扫描件, 使用对象流的文件, XMP 元数据, 带签名字段的静态 XFA, 动态 XFA
 * 与文字型多页样本. 设备回归样例改由 `.python/generate_fixtures.py` 生成 (`samples/fixtures/`).
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

    /**
     * Terminal fields of [form] by fully qualified name, with their roadmap D62 type, in field tree order.
     * zh-CN: [form] 的终端字段, 按全限定名列出, 附路线图 D62 的类型, 顺序同字段树.
     */
    val FORM_FIELDS: Map<String, String> = linkedMapOf(
        "name" to "text",
        "notes" to "text",
        "agree" to "checkbox",
        "size" to "radio",
        "country" to "combo",
        "id" to "text",
        "email" to "text",
        "pin" to "text",
        "code" to "text",
        "colors" to "list",
        "submit" to "button",
        "address.city" to "text",
    )

    /** `/V` of the fields of [form] that have one; choices list their export values. zh-CN: [form] 中带 `/V` 的字段的值; 选项列出导出值. */
    val FORM_VALUES: Map<String, Any> = linkedMapOf(
        "name" to "Ada Lovelace",
        "notes" to "Line 1\nLine 2",
        "agree" to "Yes",
        "size" to "medium",
        "country" to listOf("France"),
        "id" to "P-001",
        "pin" to "1234",
        "code" to "100080",
        "colors" to listOf("r", "b"),
        "address.city" to "Paris",
    )

    /** `/DV` of the fields of [form] that have one. zh-CN: [form] 中带 `/DV` 的字段的默认值. */
    val FORM_DEFAULTS: Map<String, String> = linkedMapOf("name" to "Ada", "agree" to "Off", "size" to "small", "country" to "China", "colors" to "g")

    /** Alternate name (`/TU`) of the field `name` of [form]. zh-CN: [form] 中字段 `name` 的替代名称 (`/TU`). */
    const val FORM_NAME_LABEL = "Full name"
    const val FORM_MAX_LENGTH = 6
    val FORM_SIZES = listOf("small", "medium", "large")
    val FORM_COUNTRIES = listOf("China", "France", "Japan")
    val FORM_COLORS = listOf("r" to "Red", "g" to "Green", "b" to "Blue")

    /** Pixel size of the pages of [scannedMultiPage]: A4 at 300 dpi. zh-CN: [scannedMultiPage] 各页的像素尺寸: 300 dpi 的 A4. */
    val SCAN_300_DPI_SIZE = 2480 to 3508
    const val SCAN_PAGES = 3

    val OBJECT_STREAM_PAGES = listOf("Object stream page 1", "Object stream page 2")
    const val OBJECT_STREAM_OUTLINE = "Object streams"

    /** Strings the XMP packet of [withXmp] contains. zh-CN: [withXmp] 的 XMP 包含有的字符串. */
    val XMP_MARKERS = listOf("<dc:title>", "<xmp:CreatorTool>$CREATOR</xmp:CreatorTool>", "<pdf:Producer>$PRODUCER</pdf:Producer>")

    const val UNSIGNED_SIGNATURE = "approval"
    const val SIGNED_SIGNATURE = "author"

    const val MULTI_PAGE_COUNT = 30
    const val MULTI_PAGE_LINES = 48

    private const val FIELD_X = 200f
    private const val FIELD_WIDTH = 300f
    private const val FIELD_HEIGHT = 20f
    private const val ON_STATE = "Yes"

    // Field flags, ISO 32000-1 tables 221, 226, 228 and 230. zh-CN: 字段标志, ISO 32000-1 表 221, 226, 228 与 230.
    private const val FLAG_READ_ONLY = 1
    private const val FLAG_REQUIRED = 1 shl 1
    private const val FLAG_MULTILINE = 1 shl 12
    private const val FLAG_PASSWORD = 1 shl 13
    private const val FLAG_NO_TOGGLE_TO_OFF = 1 shl 14
    private const val FLAG_RADIO = 1 shl 15
    private const val FLAG_PUSH_BUTTON = 1 shl 16
    private const val FLAG_COMBO = 1 shl 17
    private const val FLAG_MULTI_SELECT = 1 shl 21

    private const val BOX = "0 g\n1 w\n0.5 0.5 13 13 re\nS\n"
    private const val CHECK = "3 7 m\n6 3 l\n11 11 l\nS\n"
    private const val DOT = "4 4 6 6 re\nf\n"

    /** `/ID` of [withObjectStreams]: "Paper Fixture ID" in hex. zh-CN: [withObjectStreams] 的 `/ID`: "Paper Fixture ID" 的十六进制. */
    private const val FIXED_ID = "50617065722046697874757265204944"

    private val WORDS = (
        "the of and to in is for on with as by at from that this be are was it an or which page text line word document " +
            "file reader writer object stream table index content font image outline form field value object number " +
            "version section chapter paper print scan search render merge split compress quick brown fox lazy dog"
        ).split(' ')

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

    /**
     * Two pages of AcroForm fields with appearance streams (roadmap D62, P6.4): [FORM_VALUES] holds the value of every
     * terminal field, [FORM_DEFAULTS] the fields with a default value. Page 1 has a text field with an alternate name,
     * a multiline text, a check box, a radio group of three widgets, a combo box, a read-only, a required, a password
     * and a `/MaxLen` text field; page 2 a multi-select list box with export values, a push button and the text field
     * `city` under the non-terminal field `address`.
     * zh-CN: 两页带外观流的 AcroForm 字段 (路线图 D62, P6.4): [FORM_VALUES] 是每个终端字段的值, [FORM_DEFAULTS] 是带默认值的字段.
     * 第 1 页有带替代名称的文本字段, 多行文本, 勾选框, 三个控件的单选组, 下拉框, 只读, 必填, 密码与带 `/MaxLen` 的文本字段; 第 2 页有
     * 带导出值的多选列表, 一个按钮, 以及非终端字段 `address` 下的文本字段 `city`.
     */
    fun form(): ByteArray = document {
        documentInformation = information()
        val helv = helvetica()
        val fonts = COSDictionary().apply {
            setItem(COSName.HELV, helv)
            setItem(COSName.ZA_DB, zapfDingbats())
        }
        val resources = COSDictionary().apply { setItem(COSName.FONT, fonts) }
        val first = labelPage(listOf("Name", "Notes", "Agree", "Size", "Country", "ID", "Email", "PIN", "Code"))
        val second = labelPage(listOf("Colors", "", "Submit", "City"))
        val fields = COSArray()
        fun row(index: Int, height: Float = FIELD_HEIGHT) = PDRectangle(FIELD_X, labelY(index) - 6, FIELD_WIDTH, height)

        fields.add(textField(first, "name", row(0), resources, "Ada Lovelace", default = "Ada") { setString(COSName.TU, FORM_NAME_LABEL) })
        fields.add(textField(first, "notes", PDRectangle(FIELD_X, labelY(1) - 26, FIELD_WIDTH, 40f), resources, "Line 1\nLine 2", flags = FLAG_MULTILINE))
        fields.add(checkBox(first, "agree", PDRectangle(FIELD_X, labelY(2) - 4, 14f, 14f), checked = true))
        fields.add(radioGroup(first, "size", labelY(3) - 4, FORM_SIZES, selected = "medium", default = "small"))
        fields.add(choiceField(first, "country", row(4), resources, FLAG_COMBO, COSArray().apply { FORM_COUNTRIES.forEach { add(COSString(it)) } }, listOf("France"), default = "China"))
        fields.add(textField(first, "id", row(5), resources, "P-001", flags = FLAG_READ_ONLY))
        fields.add(textField(first, "email", row(6), resources, null, flags = FLAG_REQUIRED) { setString(COSName.TU, "Email address") })
        fields.add(textField(first, "pin", row(7), resources, "1234", flags = FLAG_PASSWORD))
        fields.add(textField(first, "code", row(8), resources, "100080") { setInt(COSName.MAX_LEN, FORM_MAX_LENGTH) })

        val colors = COSArray().apply { FORM_COLORS.forEach { (value, label) -> add(COSArray().apply { add(COSString(value)); add(COSString(label)) }) } }
        fields.add(choiceField(second, "colors", PDRectangle(FIELD_X, labelY(0) + 14 - 54, FIELD_WIDTH, 54f), resources, FLAG_MULTI_SELECT, colors, listOf("r", "b"), default = "g") {
            setItem(COSName.I, COSArray().apply { add(COSInteger.get(0)); add(COSInteger.get(2)) })
        })
        fields.add(pushButton(second, "submit", PDRectangle(FIELD_X, labelY(2) - 6, 80f, FIELD_HEIGHT), resources))
        val address = COSDictionary().apply { setString(COSName.T, "address") }
        val city = textField(second, "city", row(3), resources, "Paris")
        city.setItem(COSName.PARENT, address)
        address.setItem(COSName.KIDS, COSArray().apply { add(city) })
        fields.add(address)

        documentCatalog.cosObject.setItem(COSName.ACRO_FORM, COSDictionary().apply {
            setItem(COSName.FIELDS, fields)
            setString(COSName.DA, "/Helv 0 Tf 0 g")
            setItem(COSName.DR, resources)
            setBoolean(COSName.NEED_APPEARANCES, false)
        })
    }

    /**
     * Three A4 pages, each one full-page grey JPEG at 300 dpi ([SCAN_300_DPI_SIZE], `DCTDecode`) of word-like dark
     * blocks, no text layer; built once per test JVM and copied on every call.
     * zh-CN: 三页 A4, 每页一张 300 dpi 整页灰度 JPEG ([SCAN_300_DPI_SIZE], `DCTDecode`), 内容是类似单词的深色块, 没有文本层; 每个测试
     * JVM 只生成一次, 每次调用返回副本.
     */
    fun scannedMultiPage(): ByteArray = scannedMultiPageBytes.copyOf()

    private val scannedMultiPageBytes: ByteArray by lazy {
        document {
            val (width, height) = SCAN_300_DPI_SIZE
            repeat(SCAN_PAGES) { index ->
                val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
                val pixels = (image.raster.dataBuffer as DataBufferByte).data
                pixels.fill(0xF4.toByte())
                var seed = 17 + index * 31
                for (line in 0 until 40) {
                    val top = 300 + line * 75
                    val end = 250 + 1500 + ((line + index) * 137) % 500
                    var x = 250
                    while (x < end) {
                        seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                        val word = minOf(60 + seed % 200, end - x)
                        for (y in top until top + 36) pixels.fill(0x30, y * width + x, y * width + x + word)
                        x += word + 30
                    }
                }
                val bytes = ByteArrayOutputStream().also { check(ImageIO.write(image, "jpg", it)) { "no JPEG writer" } }.toByteArray()
                val scan = PDImageXObject(this, ByteArrayInputStream(bytes), COSName.DCT_DECODE, width, height, 8, PDDeviceGray.INSTANCE)
                val page = PDPage(PDRectangle.A4)
                val resources = PDResources()
                val name = resources.add(scan)
                page.resources = resources
                val box = page.mediaBox
                setContents(page, "q ${box.width} 0 0 ${box.height} 0 0 cm /${name.name} Do Q\n")
                addPage(page)
            }
        }
    }

    /**
     * Two text pages and an outline written by hand with PDF 1.5 object streams: every dictionary is inside one
     * `/Type /ObjStm` stream and a `/Type /XRef` stream replaces the cross-reference table and the trailer.
     * zh-CN: 手写的两页文本加大纲, 使用 PDF 1.5 对象流: 全部字典都在一个 `/Type /ObjStm` 流里, `/Type /XRef` 流代替交叉引用表与 trailer.
     */
    fun withObjectStreams(): ByteArray {
        val output = ByteArrayOutputStream()
        val offsets = HashMap<Int, Int>()
        fun ascii(text: String) = output.write(text.toByteArray(Charsets.ISO_8859_1))
        fun stream(number: Int, dictionary: String, data: ByteArray) {
            offsets[number] = output.size()
            ascii("$number 0 obj\n<< $dictionary /Length ${data.size} >>\nstream\n")
            output.write(data)
            ascii("\nendstream\nendobj\n")
        }
        fun page(contents: Int) = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 7 0 R >> >> /Contents $contents 0 R >>"
        ascii("%PDF-1.5\n%")
        output.write(byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        val compressed = linkedMapOf(
            1 to "<< /Type /Catalog /Pages 2 0 R /Outlines 9 0 R >>",
            2 to "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>",
            3 to page(4),
            5 to page(6),
            7 to "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            8 to "<< /Title (${escape(TITLE)}) /Author (${escape(AUTHOR)}) /Producer (${escape(PRODUCER)}) /CreationDate (D:20261112080000Z) >>",
            9 to "<< /Type /Outlines /First 10 0 R /Last 10 0 R /Count 1 >>",
            10 to "<< /Title (${escape(OBJECT_STREAM_OUTLINE)}) /Parent 9 0 R /Dest [3 0 R /Fit] >>",
        )
        OBJECT_STREAM_PAGES.forEachIndexed { index, text ->
            stream(4 + index * 2, "/Filter /FlateDecode", deflate("BT\n/F1 18 Tf\n72 746 Td\n(${escape(text)}) Tj\nET\n".toByteArray(Charsets.ISO_8859_1)))
        }
        val bodies = ByteArrayOutputStream()
        val header = compressed.entries.joinToString(" ", postfix = "\n") { (number, body) ->
            "$number ${bodies.size()}".also { bodies.write("$body\n".toByteArray(Charsets.ISO_8859_1)) }
        }.toByteArray(Charsets.ISO_8859_1)
        stream(11, "/Type /ObjStm /N ${compressed.size} /First ${header.size} /Filter /FlateDecode", deflate(header + bodies.toByteArray()))
        offsets[12] = output.size()
        val rows = ByteArrayOutputStream()
        fun row(type: Int, second: Int, third: Int) {
            rows.write(type)
            for (shift in 3 downTo 0) rows.write((second ushr (8 * shift)) and 0xFF)
            rows.write((third ushr 8) and 0xFF)
            rows.write(third and 0xFF)
        }
        for (number in 0..12) when (number) {
            0 -> row(0, 0, 65535)
            in compressed -> row(2, 11, compressed.keys.indexOf(number))
            else -> row(1, offsets.getValue(number), 0)
        }
        val xref = offsets.getValue(12)
        stream(12, "/Type /XRef /Size 13 /W [1 4 2] /Root 1 0 R /Info 8 0 R /ID [<$FIXED_ID> <$FIXED_ID>] /Filter /FlateDecode", deflate(rows.toByteArray()))
        ascii("startxref\n$xref\n%%EOF\n")
        return output.toByteArray()
    }

    /**
     * One text page whose catalog has an uncompressed XMP metadata stream ([XMP_MARKERS]) next to the same document
     * information as [singlePageText].
     * zh-CN: 一页文本, 目录带未压缩的 XMP 元数据流 ([XMP_MARKERS]), 文档信息与 [singlePageText] 相同.
     */
    fun withXmp(): ByteArray = document {
        documentInformation = information()
        textPage(listOf("Page with XMP metadata"))
        val metadata = PDStream(this, ByteArrayInputStream(xmp().toByteArray(Charsets.UTF_8))).cosObject
        metadata.setItem(COSName.TYPE, COSName.METADATA)
        metadata.setItem(COSName.SUBTYPE, COSName.getPDFName("XML"))
        documentCatalog.cosObject.setItem(COSName.METADATA, metadata)
    }

    /**
     * A static XFA form (roadmap D62): the AcroForm has the text field `name`, the unsigned signature field
     * [UNSIGNED_SIGNATURE] and the signed signature field [SIGNED_SIGNATURE], `/SigFlags` 3 and an `/XFA` array of
     * packets. The signature dictionary only marks the field as signed: its byte range and contents sign nothing.
     * zh-CN: 静态 XFA 表单 (路线图 D62): AcroForm 有文本字段 `name`, 未签名的签名字段 [UNSIGNED_SIGNATURE] 与已签名的签名字段
     * [SIGNED_SIGNATURE], `/SigFlags` 为 3, 并有 `/XFA` 包数组. 签名字典只是把字段标为已签名, 其字节范围与内容不签署任何东西.
     */
    fun signatureAndXfa(): ByteArray = document {
        documentInformation = information()
        val resources = COSDictionary().apply { setItem(COSName.FONT, COSDictionary().apply { setItem(COSName.HELV, helvetica()) }) }
        val page = labelPage(listOf("Name", "Approval", "Author"))
        val signature = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.SIG)
            setItem(COSName.FILTER, COSName.ADOBE_PPKLITE)
            setItem(COSName.SUB_FILTER, COSName.ADBE_PKCS7_DETACHED)
            setString(COSName.NAME, CREATOR)
            setString(COSName.M, "D:20261112080000Z")
            setItem(COSName.BYTERANGE, COSArray().apply { repeat(4) { add(COSInteger.ZERO) } })
            setItem(COSName.CONTENTS, COSString(ByteArray(32)).apply { setForceHexForm(true) })
        }
        val fields = COSArray().apply {
            add(textField(page, "name", PDRectangle(FIELD_X, labelY(0) - 6, FIELD_WIDTH, FIELD_HEIGHT), resources, "Ada"))
            add(signatureField(page, UNSIGNED_SIGNATURE, PDRectangle(FIELD_X, labelY(1) - 6, FIELD_WIDTH, FIELD_HEIGHT), null))
            add(signatureField(page, SIGNED_SIGNATURE, PDRectangle(FIELD_X, labelY(2) - 6, FIELD_WIDTH, FIELD_HEIGHT), signature))
        }
        documentCatalog.cosObject.setItem(COSName.ACRO_FORM, COSDictionary().apply {
            setItem(COSName.FIELDS, fields)
            setString(COSName.DA, "/Helv 0 Tf 0 g")
            setItem(COSName.DR, resources)
            setInt(COSName.SIG_FLAGS, 3)
            setItem(COSName.XFA, xfa(dynamic = false))
        })
    }

    /**
     * A dynamic XFA form: the AcroForm has no fields and an `/XFA` array whose configuration requires dynamic rendering,
     * and the catalog sets `/NeedsRendering`; the page only says to open the form in an XFA viewer.
     * zh-CN: 动态 XFA 表单: AcroForm 没有字段, `/XFA` 包数组的配置要求动态渲染, 目录设置 `/NeedsRendering`; 页面只提示用 XFA 查看器打开.
     */
    fun dynamicXfa(): ByteArray = document {
        documentInformation = information()
        textPage(listOf("Please open this form in an XFA viewer."))
        documentCatalog.cosObject.setBoolean(COSName.getPDFName("NeedsRendering"), true)
        documentCatalog.cosObject.setItem(COSName.ACRO_FORM, COSDictionary().apply {
            setItem(COSName.FIELDS, COSArray())
            setItem(COSName.XFA, xfa(dynamic = true))
        })
    }

    /**
     * [MULTI_PAGE_COUNT] pages of 11 pt text, [MULTI_PAGE_LINES] lines each from a fixed word list, one shared font and
     * an outline entry per page: the text-type multi-page sample of roadmap P6.7 (size of the object stream output).
     * zh-CN: [MULTI_PAGE_COUNT] 页 11 pt 文本, 每页 [MULTI_PAGE_LINES] 行, 文字取自固定词表, 共用一个字体, 每页一个大纲条目: 路线图 P6.7
     * 的文字型多页样本 (用于对象流输出的体积).
     */
    fun multiPageText(): ByteArray = document {
        documentInformation = information()
        val font = helvetica()
        val outline = PDDocumentOutline()
        var seed = 7
        for (number in 1..MULTI_PAGE_COUNT) {
            val lines = ArrayList<String>()
            lines += "Page $number of $MULTI_PAGE_COUNT"
            repeat(MULTI_PAGE_LINES - 1) {
                val line = StringBuilder()
                while (line.length < 72) {
                    seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(WORDS[seed % WORDS.size])
                }
                lines += line.toString()
            }
            val page = textPage(lines, font = font, size = 11, leading = 14)
            outline.addLast(outlineItem("Page $number", page))
        }
        documentCatalog.documentOutline = outline
    }

    /** Every fixture by its file name. zh-CN: 按文件名列出全部夹具. */
    fun all(): Map<String, ByteArray> = linkedMapOf(
        "text.pdf" to singlePageText(),
        "outline.pdf" to multiPageWithOutline(),
        "encrypted.pdf" to encrypted(),
        "scanned.pdf" to imageOnly(),
        "images.pdf" to withImages(),
        "rotated.pdf" to rotatedPages(),
        "form.pdf" to form(),
        "scanned-multi.pdf" to scannedMultiPage(),
        "object-streams.pdf" to withObjectStreams(),
        "xmp.pdf" to withXmp(),
        "signature-xfa.pdf" to signatureAndXfa(),
        "dynamic-xfa.pdf" to dynamicXfa(),
        "multi-page-text.pdf" to multiPageText(),
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

    private fun PDDocument.textPage(
        lines: List<String>,
        box: PDRectangle = PDRectangle.A4,
        font: COSDictionary = helvetica(),
        size: Int = 18,
        leading: Int = 24,
    ): PDPage {
        val page = PDPage(box)
        val fonts = COSDictionary().apply { setItem(COSName.getPDFName(FONT), font) }
        page.resources = PDResources().apply { cosObject.setItem(COSName.FONT, fonts) }
        val content = buildString {
            append("BT\n/$FONT $size Tf\n$leading TL\n72 ${box.height - 96} Td\n")
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

    private fun zapfDingbats() = COSDictionary().apply {
        setItem(COSName.TYPE, COSName.FONT)
        setItem(COSName.SUBTYPE, COSName.TYPE1)
        setItem(COSName.BASE_FONT, COSName.getPDFName("ZapfDingbats"))
    }

    private fun labelY(index: Int) = 760f - index * 52f

    /** An A4 page with one 11 pt label per row; empty labels leave their row blank. zh-CN: 每行一个 11 pt 标签的 A4 页; 空标签留空该行. */
    private fun PDDocument.labelPage(labels: List<String>): PDPage {
        val page = PDPage(PDRectangle.A4)
        val fonts = COSDictionary().apply { setItem(COSName.getPDFName(FONT), helvetica()) }
        page.resources = PDResources().apply { cosObject.setItem(COSName.FONT, fonts) }
        val content = buildString {
            labels.forEachIndexed { index, label ->
                if (label.isNotEmpty()) append("BT\n/$FONT 11 Tf\n72 ${labelY(index)} Td\n(${escape(label)}) Tj\nET\n")
            }
        }
        setContents(page, content)
        addPage(page)
        return page
    }

    private fun PDDocument.appearance(rect: PDRectangle, content: String, resources: COSDictionary? = null): COSStream =
        PDStream(this, ByteArrayInputStream(content.toByteArray(Charsets.ISO_8859_1)), COSName.FLATE_DECODE).cosObject.apply {
            setItem(COSName.TYPE, COSName.XOBJECT)
            setItem(COSName.SUBTYPE, COSName.FORM)
            setItem(COSName.BBOX, PDRectangle(rect.width, rect.height).cosArray)
            if (resources != null) setItem(COSName.RESOURCES, resources)
        }

    /** A widget annotation on [page] (added to its `/Annots`) with the normal appearance [normal]. zh-CN: [page] 上的控件注释. */
    private fun widget(page: PDPage, rect: PDRectangle, normal: COSBase, state: String? = null) = COSDictionary().apply {
        setItem(COSName.TYPE, COSName.ANNOT)
        setItem(COSName.SUBTYPE, COSName.WIDGET)
        setItem(COSName.RECT, rect.cosArray)
        setItem(COSName.P, page.cosObject)
        setInt(COSName.F, 4)
        setItem(COSName.AP, COSDictionary().apply { setItem(COSName.N, normal) })
        if (state != null) setName(COSName.AS, state)
        val annotations = page.cosObject.getDictionaryObject(COSName.ANNOTS) as? COSArray ?: COSArray().also { page.cosObject.setItem(COSName.ANNOTS, it) }
        annotations.add(this)
    }

    /** A text field merged with its widget; a password value shows as asterisks. zh-CN: 与控件合并的文本字段; 密码值显示为星号. */
    private fun PDDocument.textField(
        page: PDPage,
        name: String,
        rect: PDRectangle,
        resources: COSDictionary,
        value: String?,
        flags: Int = 0,
        default: String? = null,
        extra: COSDictionary.() -> Unit = {},
    ): COSDictionary {
        val shown = when {
            value == null -> emptyList()
            flags and FLAG_PASSWORD != 0 -> listOf("*".repeat(value.length))
            else -> value.split('\n')
        }
        val content = buildString {
            append("/Tx BMC\nq\nBT\n/Helv 10 Tf\n0 g\n12 TL\n2 ${rect.height - 13} Td\n")
            shown.forEach { append('(').append(escape(it)).append(") Tj T*\n") }
            append("ET\nQ\nEMC\n")
        }
        return widget(page, rect, appearance(rect, content, resources)).apply {
            setItem(COSName.FT, COSName.TX)
            setString(COSName.T, name)
            setString(COSName.DA, "/Helv 10 Tf 0 g")
            if (flags != 0) setInt(COSName.FF, flags)
            if (value != null) setString(COSName.V, value)
            if (default != null) setString(COSName.DV, default)
            extra()
        }
    }

    private fun PDDocument.checkBox(page: PDPage, name: String, rect: PDRectangle, checked: Boolean): COSDictionary {
        val states = COSDictionary().apply {
            setItem(COSName.getPDFName(ON_STATE), appearance(rect, BOX + CHECK))
            setItem(COSName.getPDFName("Off"), appearance(rect, BOX))
        }
        val state = if (checked) ON_STATE else "Off"
        return widget(page, rect, states, state).apply {
            setItem(COSName.FT, COSName.BTN)
            setString(COSName.T, name)
            setName(COSName.V, state)
            setName(COSName.DV, "Off")
        }
    }

    /** A radio group with one 14 pt widget per option at [y], 60 pt apart. zh-CN: 单选组, 每个选项一个 14 pt 控件, 位于 [y], 间隔 60 pt. */
    private fun PDDocument.radioGroup(page: PDPage, name: String, y: Float, options: List<String>, selected: String, default: String): COSDictionary {
        val group = COSDictionary().apply {
            setItem(COSName.FT, COSName.BTN)
            setString(COSName.T, name)
            setInt(COSName.FF, FLAG_RADIO or FLAG_NO_TOGGLE_TO_OFF)
            setName(COSName.V, selected)
            setName(COSName.DV, default)
        }
        val kids = COSArray()
        options.forEachIndexed { index, option ->
            val rect = PDRectangle(FIELD_X + index * 60, y, 14f, 14f)
            val states = COSDictionary().apply {
                setItem(COSName.getPDFName(option), appearance(rect, BOX + DOT))
                setItem(COSName.getPDFName("Off"), appearance(rect, BOX))
            }
            kids.add(widget(page, rect, states, if (option == selected) option else "Off").apply { setItem(COSName.PARENT, group) })
        }
        group.setItem(COSName.KIDS, kids)
        return group
    }

    /**
     * A combo box (shows the label of the first value) or a list box (one 16 pt row per option, selected rows
     * highlighted); [options] holds strings or `[export label]` pairs.
     * zh-CN: 下拉框 (显示第一个值的标签) 或列表框 (每个选项一行 16 pt, 选中行高亮); [options] 为字符串或 `[导出值 标签]` 对.
     */
    private fun PDDocument.choiceField(
        page: PDPage,
        name: String,
        rect: PDRectangle,
        resources: COSDictionary,
        flags: Int,
        options: COSArray,
        values: List<String>,
        default: String?,
        extra: COSDictionary.() -> Unit = {},
    ): COSDictionary {
        val pairs = (0 until options.size()).map { index ->
            when (val item = options.getObject(index)) {
                is COSArray -> (item.getObject(0) as COSString).string to (item.getObject(1) as COSString).string
                else -> (item as COSString).string.let { it to it }
            }
        }
        val content = buildString {
            append("/Tx BMC\nq\n")
            if (flags and FLAG_COMBO != 0) {
                val label = pairs.first { it.first == values.first() }.second
                append("BT\n/Helv 10 Tf\n0 g\n2 ${rect.height - 13} Td\n(${escape(label)}) Tj\nET\n")
            } else {
                pairs.forEachIndexed { index, (export, _) ->
                    if (export in values) append("0.6 0.75 0.9 rg\n0 ${rect.height - (index + 1) * 16} ${rect.width} 16 re\nf\n")
                }
                append("BT\n/Helv 10 Tf\n0 g\n16 TL\n2 ${rect.height - 12} Td\n")
                pairs.forEach { (_, label) -> append('(').append(escape(label)).append(") Tj T*\n") }
                append("ET\n")
            }
            append("Q\nEMC\n")
        }
        return widget(page, rect, appearance(rect, content, resources)).apply {
            setItem(COSName.FT, COSName.CH)
            setString(COSName.T, name)
            setString(COSName.DA, "/Helv 10 Tf 0 g")
            setInt(COSName.FF, flags)
            setItem(COSName.OPT, options)
            if (values.size == 1) setString(COSName.V, values.single()) else setItem(COSName.V, COSArray().apply { values.forEach { add(COSString(it)) } })
            if (default != null) setString(COSName.DV, default)
            extra()
        }
    }

    private fun PDDocument.pushButton(page: PDPage, name: String, rect: PDRectangle, resources: COSDictionary): COSDictionary {
        val content = "q\n0.85 g\n0 0 ${rect.width} ${rect.height} re\nf\nQ\nBT\n/Helv 10 Tf\n0 g\n18 6 Td\n(Submit) Tj\nET\n"
        return widget(page, rect, appearance(rect, content, resources)).apply {
            setItem(COSName.FT, COSName.BTN)
            setString(COSName.T, name)
            setString(COSName.DA, "/Helv 10 Tf 0 g")
            setInt(COSName.FF, FLAG_PUSH_BUTTON)
            setItem(COSName.MK, COSDictionary().apply { setString(COSName.CA, "Submit") })
        }
    }

    private fun PDDocument.signatureField(page: PDPage, name: String, rect: PDRectangle, signature: COSDictionary?): COSDictionary {
        val content = "q\n0.5 G\n0.5 0.5 ${rect.width - 1} ${rect.height - 1} re\nS\nQ\n"
        return widget(page, rect, appearance(rect, content)).apply {
            setItem(COSName.FT, COSName.SIG)
            setString(COSName.T, name)
            if (signature != null) setItem(COSName.V, signature)
        }
    }

    /**
     * The `/XFA` packet array (preamble, config, template, datasets, postamble); a [dynamic] configuration requires
     * dynamic rendering and lays the subform out top to bottom.
     * zh-CN: `/XFA` 包数组 (前导, 配置, 模板, 数据集, 结尾); [dynamic] 的配置要求动态渲染, 子表单自上而下排布.
     */
    private fun PDDocument.xfa(dynamic: Boolean): COSArray {
        val render = if (dynamic) "<acrobat><acrobat7><dynamicRender>required</dynamicRender></acrobat7></acrobat>" else ""
        val packets = linkedMapOf(
            "preamble" to "<xdp:xdp xmlns:xdp='http://ns.adobe.com/xdp/'>",
            "config" to "<config xmlns='http://www.xfa.org/schema/xci/3.0/'><present><pdf><version>1.7</version></pdf></present>$render</config>",
            "template" to "<template xmlns='http://www.xfa.org/schema/xfa-template/3.3/'><subform name='form1' layout='${if (dynamic) "tb" else "position"}'>" +
                "<field name='name'/></subform></template>",
            "datasets" to "<xfa:datasets xmlns:xfa='http://www.xfa.org/schema/xfa-data/1.0/'><xfa:data><form1><name>Ada</name></form1></xfa:data></xfa:datasets>",
            "postamble" to "</xdp:xdp>",
        )
        return COSArray().apply {
            packets.forEach { (name, xml) ->
                add(COSString(name))
                add(PDStream(this@xfa, ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), COSName.FLATE_DECODE).cosObject)
            }
        }
    }

    private fun xmp(): String = buildString {
        append("<?xpacket begin='").append(Char(0xFEFF)).append("' id='W5M0MpCehiHzreSzNTczkc9d'?>\n")
        append("<x:xmpmeta xmlns:x='adobe:ns:meta/'>\n")
        append("<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>\n")
        append("<rdf:Description rdf:about='' xmlns:dc='http://purl.org/dc/elements/1.1/' xmlns:xmp='http://ns.adobe.com/xap/1.0/'")
        append(" xmlns:pdf='http://ns.adobe.com/pdf/1.3/'>\n")
        append("<dc:format>application/pdf</dc:format>\n")
        append("<dc:title><rdf:Alt><rdf:li xml:lang='x-default'>$TITLE</rdf:li></rdf:Alt></dc:title>\n")
        append("<dc:creator><rdf:Seq><rdf:li>$AUTHOR</rdf:li></rdf:Seq></dc:creator>\n")
        append("<dc:description><rdf:Alt><rdf:li xml:lang='x-default'>$SUBJECT</rdf:li></rdf:Alt></dc:description>\n")
        append("<pdf:Keywords>$KEYWORDS</pdf:Keywords>\n")
        append("<pdf:Producer>$PRODUCER</pdf:Producer>\n")
        append("<xmp:CreatorTool>$CREATOR</xmp:CreatorTool>\n")
        append("<xmp:CreateDate>2026-11-12T08:00:00Z</xmp:CreateDate>\n")
        append("<xmp:ModifyDate>2026-11-12T08:00:00Z</xmp:ModifyDate>\n")
        append("</rdf:Description>\n</rdf:RDF>\n</x:xmpmeta>\n")
        append("<?xpacket end='w'?>")
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

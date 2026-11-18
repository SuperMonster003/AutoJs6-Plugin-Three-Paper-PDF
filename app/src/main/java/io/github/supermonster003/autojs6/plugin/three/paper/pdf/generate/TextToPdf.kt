package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * `fromText` (roadmap D22): the UTF-8 text is laid out by `StaticLayout` on `PdfDocument` pages and broken across
 * pages by whole lines; the font is the optional second input (TrueType / OpenType, a collection uses its first face),
 * the system default font otherwise. The text goes in chunks of at most [CHUNK_CHARS] characters, cut after a line
 * break when one is near, else after a space, so a very long paragraph gets one extra line break per chunk. PDFBox then
 * sets `title`, `producer` and the dates.
 * zh-CN: `fromText` (路线图 D22): UTF-8 文本由 `StaticLayout` 排版到 `PdfDocument` 页面上, 按整行分页; 字体是可选的第二个输入
 * (TrueType / OpenType, 字体集合取第一个字面), 否则使用系统默认字体. 文本按至多 [CHUNK_CHARS] 个字符分块, 附近有换行时在换行后切,
 * 否则在空格后切, 因此极长的段落每块多一个换行. 之后由 PDFBox 写入 `title`, `producer` 与日期.
 */
internal object TextToPdf {

    /** Characters laid out at once. zh-CN: 一次排版的字符数. */
    const val CHUNK_CHARS = 8192

    /** Default font size in points. zh-CN: 默认字号 (磅). */
    const val DEFAULT_FONT_SIZE = 12f

    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

    /** Largest font size and line spacing accepted. zh-CN: 接受的最大字号与行距倍数. */
    const val MAX_FONT_SIZE = 1000f
    const val MAX_LINE_SPACING = 10f

    /** A checked `fromText` request. zh-CN: 已检查的 `fromText` 请求. */
    class Request(
        val page: PageLayout.PageSize.Fixed,
        val margin: Float,
        val fontSize: Float,
        val lineSpacing: Float,
        val title: String?,
        val hasFont: Boolean,
        val warnings: List<String>,
    )

    fun request(options: PdfOptions, inputCount: Int): Request {
        if (inputCount !in 1..2) throw PdfFailure.invalid("${PdfOps.FROM_TEXT} takes the text and an optional font, got $inputCount inputs")
        val warnings = ArrayList<String>()
        val page = PageLayout.documentPageSize(options, PdfOps.FROM_TEXT, warnings)
        val margin = PageLayout.margin(options, PageLayout.DEFAULT_DOCUMENT_MARGIN)
        val fontSize = positive(options, PdfContract.KEY_FONT_SIZE, MAX_FONT_SIZE, DEFAULT_FONT_SIZE)
        PageLayout.contentBox(page, margin, minimum = fontSize)
        return Request(
            page = page,
            margin = margin,
            fontSize = fontSize,
            lineSpacing = positive(options, PdfContract.KEY_LINE_SPACING, MAX_LINE_SPACING, 1f),
            title = options.string(PdfContract.KEY_TITLE),
            hasFont = inputCount == 2,
            warnings = warnings,
        )
    }

    private fun positive(options: PdfOptions, key: String, maximum: Float, default: Float): Float {
        val value = options.float(key, 0f..maximum, default)
        if (value <= 0f) throw PdfFailure.invalid("$key must be greater than 0, got $value")
        return value
    }

    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val text = normalize(String(InputBytes.read(inputs[0], PdfContract.MAX_TEXT_INPUT_BYTES, "the text"), Charsets.UTF_8))
        val typeface = if (request.hasFont) font(inputs[1], directory) else Typeface.DEFAULT
        val laidOut = File(directory, "text.pdf")
        layout(text, typeface, request, laidOut, operation)
        operation.stop.checkStop()
        PDDocument.load(laidOut, MemoryUsageSetting.setupMixed(PdfLoader.DEFAULT_MAIN_MEMORY_BYTES).setTempDir(directory)).use { document ->
            return GeneratedDocuments.write(document, request.title, operation, request.warnings, request.page)
        }
    }

    /** CR LF and CR become LF, a leading byte order mark goes. zh-CN: CR LF 与 CR 变为 LF, 去掉开头的字节顺序标记. */
    internal fun normalize(text: String): String = text.removePrefix(BYTE_ORDER_MARK).replace("\r\n", "\n").replace('\r', '\n')

    /**
     * Cuts [text] into chunks of at most [limit] characters: after the last line break of the window when it lies in its
     * second half, else after the last space there, else at the limit (never inside a surrogate pair). A cut after a line
     * break drops that line break, since every chunk starts a new line anyway.
     * zh-CN: 把 [text] 切成至多 [limit] 个字符的块: 窗口后半段有换行时在最后一个换行后切, 否则在后半段最后一个空格后切, 都没有时在
     * 上限处切 (不切开代理对). 在换行后切时去掉该换行, 因为每块本来就从新行开始.
     */
    internal fun chunks(text: String, limit: Int = CHUNK_CHARS): List<String> {
        if (text.length <= limit) return listOf(text)
        val result = ArrayList<String>()
        var start = 0
        while (text.length - start > limit) {
            val end = start + limit
            val half = start + limit / 2
            val newline = text.lastIndexOf('\n', end - 1)
            val cut: Int
            val next: Int
            if (newline >= half) {
                cut = newline
                next = newline + 1
            } else {
                val space = text.lastIndexOf(' ', end - 1)
                cut = if (space >= half) space + 1 else if (Character.isHighSurrogate(text[end - 1])) end - 1 else end
                next = cut
            }
            result += text.substring(start, cut)
            start = next
        }
        result += text.substring(start)
        return result
    }

    /**
     * Loads the font input: copied to [directory], checked by its magic number (`00 01 00 00`, `true`, `OTTO`, `ttcf`),
     * then `Typeface.createFromFile`; `INVALID_ARGUMENT` when it is not a font the system loads. The Canvas stamp
     * pipeline (roadmap D60) loads its font input the same way.
     * zh-CN: 载入字体输入: 复制到 [directory], 按魔数 (`00 01 00 00`, `true`, `OTTO`, `ttcf`) 检查, 再经 `Typeface.createFromFile`
     * 载入; 不是系统能载入的字体时报 `INVALID_ARGUMENT`. Canvas 印章管线 (路线图 D60) 以同样方式载入其字体输入.
     */
    internal fun font(input: ParcelFileDescriptor, directory: File): Typeface {
        val bytes = InputBytes.read(input, PdfContract.MAX_TEXT_INPUT_BYTES, "the font")
        if (!isFont(bytes)) throw PdfFailure.invalid("the font input is not a TrueType or OpenType font")
        val file = File(directory, "font")
        try {
            file.writeBytes(bytes)
        } catch (e: IOException) {
            throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot copy the font: ${e.message}")
        }
        val typeface = try {
            Typeface.createFromFile(file)
        } catch (e: RuntimeException) {
            throw PdfFailure.invalid("the system cannot load the font: ${e.message}")
        }
        if (typeface == null || typeface == Typeface.DEFAULT) throw PdfFailure.invalid("the system cannot load the font")
        return typeface
    }

    internal fun isFont(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val magic = ((bytes[0].toLong() and 0xFF) shl 24) or ((bytes[1].toLong() and 0xFF) shl 16) or ((bytes[2].toLong() and 0xFF) shl 8) or (bytes[3].toLong() and 0xFF)
        return magic == 0x0001_0000L || magic == 0x7472_7565L || magic == 0x4F54_544FL || magic == 0x7474_6366L
    }

    /**
     * Lays [text] out into [target]: runs of whole lines per page, each drawn through a clip so `StaticLayout.draw` only
     * emits the lines of that page. A line taller than the page box gets a page of its own, clipped. Empty text gives one
     * blank page.
     * zh-CN: 把 [text] 排版到 [target]: 每页放若干整行, 经裁剪区绘制, 使 `StaticLayout.draw` 只输出该页的行. 比页面方框还高的行独占
     * 一页并被裁剪. 空文本得到一个空白页.
     */
    private fun layout(text: String, typeface: Typeface, request: Request, target: File, operation: Operations.Operation) {
        val box = PageLayout.contentBox(request.page, request.margin, minimum = request.fontSize)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = request.fontSize
            color = android.graphics.Color.BLACK
        }
        val pageWidth = request.page.width.toInt().coerceAtLeast(1)
        val pageHeight = request.page.height.toInt().coerceAtLeast(1)
        // PDF space has its origin at the lower left; the canvas of PdfDocument at the upper left.
        // zh-CN: PDF 坐标原点在左下, PdfDocument 的画布原点在左上.
        val left = box.x
        val top = request.page.height - box.y - box.height
        val document = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            var used = 0f
            fun newPage(): PdfDocument.Page {
                page?.let { document.finishPage(it) }
                return document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()).also {
                    page = it
                    used = 0f
                }
            }
            val pieces = chunks(text)
            var done = 0
            for (piece in pieces) {
                operation.stop.checkStop()
                operation.progress(PdfContract.STAGE_PROCESSING, done, text.length.coerceAtLeast(1))
                val layout = staticLayout(piece, paint, box.width.toInt().coerceAtLeast(1), request.lineSpacing)
                var line = 0
                while (line < layout.lineCount) {
                    val current = page ?: newPage()
                    val first = line
                    val firstTop = layout.getLineTop(first)
                    var last = first
                    while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - firstTop <= box.height - used) last++
                    val height = (layout.getLineBottom(last) - firstTop).toFloat()
                    if (height > box.height - used && used > 0f) {
                        newPage()
                        continue
                    }
                    current.canvas.withClip(left, top + used, left + box.width, top + used + minOf(height, box.height - used)) {
                        translate(left, top + used - firstTop)
                        layout.draw(this)
                    }
                    used += height
                    line = last + 1
                    if (line < layout.lineCount) newPage()
                }
                done += piece.length
            }
            if (page == null) newPage()
            page?.let { document.finishPage(it) }
            operation.stop.checkStop()
            try {
                FileOutputStream(target).use { document.writeTo(it) }
            } catch (e: IOException) {
                throw PdfFailure(PdfErrorCodes.IO_ERROR, "cannot write the laid out text: ${e.message}")
            }
        } finally {
            document.close()
        }
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true)
            .build()

}

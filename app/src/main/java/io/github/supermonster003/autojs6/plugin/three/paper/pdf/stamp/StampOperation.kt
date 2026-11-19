package io.github.supermonster003.autojs6.plugin.three.paper.pdf.stamp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDPage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.ImagesToPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.InputBytes
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.TextToPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfOps
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The `run` operation `stamp` (roadmap P6.3, D60, D61; protocol "Version 2", "Stamp items"): watermarks, page numbers,
 * headers and footers. The inputs are the source PDF, the payload (`items`), `imageCount` images and, with `hasFont`,
 * a font; every item is drawn on the pages of its range, in payload order, above (`over`) or below (`under`) the
 * content, through the Canvas stamp pipeline ([CanvasStamps]), so text gets the system font fallback (or the font
 * input) and images keep their transparency. Placeholders are replaced per page, a missing `fontSize` fits the text to
 * half the displayed page width on each page, and a tiled item repeats its rotated box over the page. Progress counts
 * the pages placed; cancelling leaves no output (the operation truncates it). An encrypted source must be opened with
 * its owner password (`PERMISSION_DENIED` otherwise, appendix D Q30); the output is unencrypted.
 * zh-CN: `run` 操作 `stamp` (路线图 P6.3, D60, D61; 协议 "Version 2" 与 "Stamp items" 两节): 水印, 页码, 页眉页脚. 输入依次为源
 * PDF, payload (`items`), `imageCount` 张图片, 以及 `hasFont` 时的字体; 每个条目按 payload 顺序画在其页范围内的页上, 位于内容之上
 * (`over`) 或之下 (`under`), 经 Canvas 印章管线 ([CanvasStamps]) 绘制, 因此文字得到系统字体回退 (或字体输入), 图片保留透明通道.
 * 占位符逐页替换, 缺少 `fontSize` 时逐页按显示页宽的一半自适应, 平铺条目把旋转后的方框铺满整页. 进度按已放置的页数计; 取消后不留
 * 输出 (由操作截断). 加密的源必须用所有者密码打开 (否则为 `PERMISSION_DENIED`, 附录 D Q30); 输出不加密.
 */
internal object StampOperation {

    /** A checked `stamp` request. zh-CN: 已检查的 `stamp` 请求. */
    class Request(val password: String?, val displayName: String?, val imageCount: Int, val hasFont: Boolean)

    /**
     * Reads the `stamp` request for [inputCount] inputs: `imageCount` (0 to `MAX_STAMP_IMAGES`, more is
     * `LIMIT_EXCEEDED`), `hasFont`, `displayName`, `password`; the inputs must number `2 + imageCount + (hasFont ? 1 : 0)`.
     * zh-CN: 读取 [inputCount] 个输入的 `stamp` 请求: `imageCount` (0 至 `MAX_STAMP_IMAGES`, 超过为 `LIMIT_EXCEEDED`), `hasFont`,
     * `displayName`, `password`; 输入数必须为 `2 + imageCount + (hasFont ? 1 : 0)`.
     */
    fun request(options: PdfOptions, inputCount: Int): Request {
        val imageCount = options.int(PdfContract.KEY_IMAGE_COUNT) ?: 0
        if (imageCount < 0) throw PdfFailure.invalid("${PdfContract.KEY_IMAGE_COUNT} must not be negative, got $imageCount")
        if (imageCount > PdfContract.MAX_STAMP_IMAGES) {
            throw PdfFailure.limitExceeded("${PdfOps.STAMP} takes at most ${PdfContract.MAX_STAMP_IMAGES} images, got ${PdfContract.KEY_IMAGE_COUNT} $imageCount")
        }
        val hasFont = options.boolean(PdfContract.KEY_HAS_FONT, false)
        val expected = 2 + imageCount + if (hasFont) 1 else 0
        if (inputCount != expected) {
            throw PdfFailure.invalid(
                "${PdfOps.STAMP} takes the source, the payload, $imageCount images" + (if (hasFont) " and a font" else "") + " ($expected inputs), got $inputCount",
            )
        }
        return Request(options.password()?.takeIf { it.isNotEmpty() }, options.string(PdfContract.KEY_DISPLAY_NAME), imageCount, hasFont)
    }

    /** The images of one operation, decoded once and recycled at the end. zh-CN: 一次操作的图片, 只解码一次, 结束时回收. */
    private class Images(private val inputs: List<ParcelFileDescriptor>) : AutoCloseable {

        private val decoded = HashMap<Int, Bitmap>()

        private val jpegs = HashMap<Int, JpegData?>()

        fun get(index: Int): Bitmap = decoded.getOrPut(index) { decode(inputs[2 + index], index) }

        /** The JPEG [get] decoded image [index] from, if it is one [JpegPassThrough] can embed. zh-CN: [get] 解码图片 [index] 所用的 JPEG (若 [JpegPassThrough] 能嵌入). */
        fun jpeg(index: Int): JpegData? = jpegs[index]

        override fun close() {
            decoded.values.forEach { it.recycle() }
            decoded.clear()
            jpegs.clear()
        }

        private fun decode(input: ParcelFileDescriptor, index: Int): Bitmap {
            val label = "image $index"
            val bytes = InputBytes.read(input, ImagesToPdf.MAX_IMAGE_INPUT_BYTES, label)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PdfFailure.invalid("$label is not an image the system decodes (PNG, JPEG or WebP)")
            if (bounds.outWidth.toLong() * bounds.outHeight > PdfContract.MAX_RENDER_PIXELS) {
                throw PdfFailure.limitExceeded("$label has ${bounds.outWidth} x ${bounds.outHeight} pixels, at most ${PdfContract.MAX_RENDER_PIXELS}")
            }
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw PdfFailure.invalid("$label cannot be decoded")
            if (bounds.outMimeType == "image/jpeg") jpegs[index] = JpegData.of(bytes)
            return bitmap
        }

    }

    /**
     * `stamp`: reads the payload, opens the source, stamps the pages and writes the single output.
     * zh-CN: `stamp`: 读取 payload, 打开源文档, 加印章并写到唯一的输出.
     */
    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }.format(Date())
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val items = StampItems.parse(PdfPayload.read(inputs), request.imageCount)
        val font = if (request.hasFont) TextToPdf.font(inputs.last(), directory) else null
        Images(inputs).use { images ->
            return PdfLoader.load(DescriptorSource.of(inputs[0]), request.password, directory).use { loaded ->
                val document = loaded.document
                PdfWriting.checkWritable(document, "the source")
                val fonts = StampFonts(font)
                val plan = plan(document.numberOfPages, items, images, fonts, request, date) { document.getPage(it) }
                operation.progress(PdfContract.STAGE_PROCESSING, 0, plan.size)
                CanvasStamps(directory, fonts).apply(document, plan, operation.stop) { placed, total -> operation.progress(PdfContract.STAGE_PROCESSING, placed, total) }
                operation.progress(PdfContract.STAGE_WRITING, 0, 1)
                val size = PdfWriting.write(document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
                mapOf(PdfContract.KEY_PAGE_COUNT to document.numberOfPages, PdfContract.KEY_SIZE_BYTES to size)
            }
        }
    }

    /**
     * The stamps of every page: for each item and each page of its range, the text with its placeholders replaced (a
     * blank text draws nothing) or the image, sized, placed and tiled on that page as displayed. Equal results on pages
     * of the same displayed size are worked out once.
     * zh-CN: 每页的印章: 对每个条目及其页范围内的每一页, 取替换占位符后的文字 (空白文字不画) 或图片, 按该页的显示方向确定尺寸, 位置与
     * 平铺. 显示尺寸相同的页面上结果相同的条目只计算一次.
     */
    private fun plan(
        pageCount: Int,
        items: List<StampItem>,
        images: Images,
        fonts: StampFonts,
        request: Request,
        date: String,
        page: (Int) -> PDPage,
    ): List<CanvasStamps.PageStamps> {
        val ranges = items.mapIndexed { index, item ->
            val selected = try {
                PageRanges.parse(item.pages, pageCount)
            } catch (e: PdfFailure) {
                throw PdfFailure(e.code, "payload.items[$index].pages: ${e.detail}")
            }
            BooleanArray(pageCount + 1).apply { selected.forEach { this[it] = true } }
        }
        val name = StampItems.name(request.displayName)
        // A no-break space of a stamp's own keeps spaces as spaces, as CanvasStamps decides; measure the same way.
        // zh-CN: 印章自带不换行空格时空格保持为空格, 与 CanvasStamps 的判断相同; 按同样方式测量.
        fonts.spacesAsNoBreak = items.none { it.isText && StampFonts.NO_BREAK_SPACE in it.text }
        val cache = HashMap<List<Any>, Stamp?>()
        val plan = ArrayList<CanvasStamps.PageStamps>()
        for (index in 0 until pageCount) {
            val number = index + 1
            val frame = StampFrame.of(page(index))
            val width = frame.displayWidth
            val height = frame.displayHeight
            val stamps = ArrayList<Stamp>()
            items.forEachIndexed { itemIndex, item ->
                if (!ranges[itemIndex][number]) return@forEachIndexed
                val label = "payload.items[$itemIndex]"
                val stamp = if (item.isText) {
                    val text = StampItems.expand(item.text, number, pageCount, date, name, item.pageOffset)
                    cache.getOrPut(listOf(itemIndex, text, width, height)) { textStamp(item, text, fonts, request.hasFont, width, height, label) }
                } else {
                    cache.getOrPut(listOf(itemIndex, width, height)) { imageStamp(item, images.get(item.image), images.jpeg(item.image), width, height, label) }
                }
                stamp?.let { stamps += it }
            }
            if (stamps.isNotEmpty()) plan += CanvasStamps.PageStamps(index, stamps)
        }
        return plan
    }

    private fun textStamp(item: StampItem, text: String, fonts: StampFonts, custom: Boolean, pageWidth: Double, pageHeight: Double, label: String): TextStamp? {
        if (text.isBlank()) return null
        fun stamp(size: Double) = TextStamp(
            text = text,
            x = 0.0,
            y = 0.0,
            anchorX = 0.5,
            anchorY = 0.5,
            fontSize = size,
            color = item.color,
            opacity = item.opacity,
            rotation = item.rotation,
            bold = item.bold,
            italic = item.italic,
            align = item.align,
            customFont = custom,
            layer = item.layer,
        )
        val size = item.fontSize ?: StampGeometry.fitFontSize(stamp(FIT_PROBE_SIZE).widestLine(fonts), pageWidth, TextStamp.MAX_FONT_SIZE) ?: return null
        val probe = stamp(size)
        val (boxWidth, boxHeight) = probe.box(fonts)
        val (x, y) = StampGeometry.center(item.position, item.margin, item.offsetX, item.offsetY, boxWidth, boxHeight, pageWidth, pageHeight)
        val copies = if (item.tile) StampGeometry.tiles(x, y, boxWidth, boxHeight, item.rotation, item.gapX, item.gapY, pageWidth, pageHeight, label) else emptyList()
        return probe.copy(x = x, y = y, copies = copies)
    }

    private fun imageStamp(item: StampItem, bitmap: Bitmap, jpeg: JpegData?, pageWidth: Double, pageHeight: Double, label: String): ImageStamp {
        val (width, height) = StampGeometry.imageSize(bitmap.width, bitmap.height, item.width, item.height, item.scale, pageWidth, pageHeight)
        val (x, y) = StampGeometry.center(item.position, item.margin, item.offsetX, item.offsetY, width, height, pageWidth, pageHeight)
        val copies = if (item.tile) StampGeometry.tiles(x, y, width, height, item.rotation, item.gapX, item.gapY, pageWidth, pageHeight, label) else emptyList()
        return ImageStamp(bitmap, x, y, width, height, item.rotation, item.opacity, item.layer, copies, jpeg)
    }

    /** The font size a fitted text is measured at. zh-CN: 测量自适应文字时用的字号. */
    private const val FIT_PROBE_SIZE = 100.0

}

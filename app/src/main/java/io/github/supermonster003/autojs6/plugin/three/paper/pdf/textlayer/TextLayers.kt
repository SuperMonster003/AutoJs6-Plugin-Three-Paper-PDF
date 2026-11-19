package io.github.supermonster003.autojs6.plugin.three.paper.pdf.textlayer

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.text.TextPosition
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata.InfoChanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageFrame
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.read.PageSweep
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadArray
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfOps
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToLong

/**
 * The `run` operation `addTextLayer` (roadmap P6.5, D54, D63; protocol "Version 2"): the inputs are the source PDF and
 * the payload `{ "layers": [{ "page", "width", "height", "lines": [{ "text", "bounds" }] }] }`, where `bounds` is
 * `[left, top, right, bottom]` in the pixels of a `width` x `height` image of the displayed page. Each line becomes one
 * invisible text object (rendering mode 3) in the glyphless font ([GlyphlessFont]): the font size is the line height,
 * the baseline sits at a fifth of it above the bottom, and horizontal scaling stretches the estimated text width to the
 * line width, so `search` answers the line box. A page that already carries text is left alone unless `force` is true.
 * The new content is appended as its own stream after a `q` / `Q` pair around the old content, so the rendering stays
 * pixel for pixel the same. Lines are cleaned first ([clean]); a line with no text or an empty box after clipping to
 * the image is skipped. Empty `layers` write the source unchanged (Q230 A). The output is written the classic way
 * (`PdfWriting.write`), unencrypted; an encrypted source needs its owner password (Q30).
 * zh-CN: `run` 操作 `addTextLayer` (路线图 P6.5, D54, D63; 协议 "Version 2" 一节): 输入为源 PDF 与 payload
 * `{ "layers": [{ "page", "width", "height", "lines": [{ "text", "bounds" }] }] }`, 其中 `bounds` 是显示页面的 `width` x `height`
 * 图像上的像素 `[left, top, right, bottom]`. 每行写成无字形字体 ([GlyphlessFont]) 中的一个不可见文本对象 (渲染模式 3): 字号取行高,
 * 基线位于底边以上行高的五分之一处, 水平缩放把估算的文本宽度拉伸到行宽, 因此 `search` 返回的正是行框. 已带文本的页保持不变,
 * 除非 `force` 为 true. 新内容作为独立的流追加在包住原内容的 `q` / `Q` 之后, 因此渲染结果逐像素不变. 各行先经清理 ([clean]);
 * 没有文本或裁到图像之内后框为空的行被跳过. `layers` 为空时原样写出源文件 (Q230 A). 输出以经典写法写出 (`PdfWriting.write`),
 * 不加密; 加密的源需要所有者密码 (Q30).
 */
internal object TextLayers {

    /** A checked request: the password and `force`. zh-CN: 已检查的请求: 密码与 `force`. */
    class Request(val password: String?, val force: Boolean)

    /** A line in image pixels. zh-CN: 以图像像素表示的一行. */
    class Line(val text: String, val left: Double, val top: Double, val right: Double, val bottom: Double)

    /** One payload layer; [path] names it in messages. zh-CN: 一个 payload 图层; [path] 用于在消息中指明它. */
    class Layer(val path: String, val page: Long, val width: Double, val height: Double, val lines: List<Line>)

    /** What [apply] did. zh-CN: [apply] 的结果. */
    class Outcome(val layerPages: Int, val skippedPages: Int, val lines: Int, val warnings: List<String>)

    fun request(options: PdfOptions, inputCount: Int): Request {
        if (inputCount != 2) throw PdfFailure.invalid("${PdfOps.ADD_TEXT_LAYER} takes the source and the payload (2 inputs), got $inputCount")
        return Request(options.password()?.takeIf { it.isNotEmpty() }, options.boolean(PdfContract.KEY_FORCE, false))
    }

    /**
     * The layers of [payload], checked except for page numbers, which need the document: more than
     * `MAX_TEXT_LAYER_LINES` lines in total is `LIMIT_EXCEEDED`, a page listed twice or a non-positive size
     * `INVALID_ARGUMENT`.
     * zh-CN: [payload] 中的图层, 除页码 (需要文档) 外均已检查: 行数合计超过 `MAX_TEXT_LAYER_LINES` 为 `LIMIT_EXCEEDED`, 同一页列出两次或
     * 尺寸不为正数为 `INVALID_ARGUMENT`.
     */
    fun parse(payload: PayloadObject): List<Layer> {
        val array = payload.array(PdfContract.FIELD_LAYERS)
        val objects = array.objects()
        val lineArrays = objects.map { it.array(PdfContract.FIELD_LINES) }
        val total = lineArrays.sumOf { it.size.toLong() }
        if (total > PdfContract.MAX_TEXT_LAYER_LINES) {
            throw PdfFailure.limitExceeded("${array.path} has $total lines in total, at most ${PdfContract.MAX_TEXT_LAYER_LINES}")
        }
        val seen = HashMap<Long, String>()
        return objects.mapIndexed { index, layer ->
            val page = layer.long(PdfContract.FIELD_PAGE)
            seen.put(page, layer.path)?.let { first -> throw PdfFailure.invalid("${layer.pathOf(PdfContract.FIELD_PAGE)} $page is listed twice (also $first)") }
            val width = positive(layer, PdfContract.FIELD_WIDTH)
            val height = positive(layer, PdfContract.FIELD_HEIGHT)
            val lines = lineArrays[index].objects().map { line ->
                val (left, top, right, bottom) = bounds(line)
                Line(clean(line.string(PdfContract.FIELD_TEXT)), left, top, right, bottom)
            }
            Layer(layer.path, page, width, height, lines)
        }
    }

    private fun positive(layer: PayloadObject, key: String): Double {
        val value = layer.double(key)
        if (!(value > 0) || value.isInfinite()) throw PdfFailure.invalid("${layer.pathOf(key)} must be a positive number, not $value")
        return value
    }

    /**
     * `bounds` as `[left, top, right, bottom]`; an object with those keys is accepted as well.
     * zh-CN: `bounds` 为 `[left, top, right, bottom]`; 也接受带这些键的对象.
     */
    private fun bounds(line: PayloadObject): DoubleArray {
        if (line.value(PdfContract.FIELD_BOUNDS) is Map<*, *>) {
            val box = line.obj(PdfContract.FIELD_BOUNDS)
            return doubleArrayOf(box.double(LEFT), box.double(TOP), box.double(RIGHT), box.double(BOTTOM))
        }
        val box = line.array(PdfContract.FIELD_BOUNDS)
        if (box.size != 4) throw PdfFailure.invalid("${box.path} must hold 4 numbers [left, top, right, bottom], not ${box.size}")
        return DoubleArray(4) { number(box, it) }
    }

    private fun number(array: PayloadArray, index: Int): Double = when (val value = array.value(index)) {
        is Long -> value.toDouble()
        is Double -> value
        else -> throw PdfFailure.invalid("${array.pathOf(index)} must be a number, not ${PdfPayload.kind(value)}")
    }

    private const val LEFT = "left"
    private const val TOP = "top"
    private const val RIGHT = "right"
    private const val BOTTOM = "bottom"

    /**
     * [text] without its outer white space, with control characters as spaces and unpaired surrogates as U+FFFD.
     * zh-CN: 去掉首尾空白的 [text], 控制字符换成空格, 未配对的代理项换成 U+FFFD.
     */
    fun clean(text: String): String {
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> {
                    builder.append(char).append(text[index + 1])
                    index++
                }
                Character.isSurrogate(char) -> builder.append(GlyphlessFont.REPLACEMENT.toChar())
                Character.isISOControl(char) -> builder.append(' ')
                else -> builder.append(char)
            }
            index++
        }
        return builder.toString().trim()
    }

    /**
     * Writes [layers] into [document]; [progress] gets the pages done of all.
     * zh-CN: 把 [layers] 写入 [document]; [progress] 收到已完成页数与总页数.
     */
    fun apply(document: PDDocument, layers: List<Layer>, force: Boolean, stop: StopSignal, progress: (Int, Int) -> Unit): Outcome {
        val pageCount = document.numberOfPages
        for (layer in layers) {
            if (layer.page !in 1..pageCount) {
                throw PdfFailure.pageOutOfRange("${layer.path}.${PdfContract.FIELD_PAGE} ${layer.page} is outside the document of $pageCount page${if (pageCount == 1) "" else "s"}")
            }
        }
        val ordered = layers.sortedBy { it.page }
        val texted = if (force || ordered.isEmpty()) emptySet() else pagesWithText(document, IntArray(ordered.size) { ordered[it].page.toInt() }, stop)
        val writer = LayerWriter(document, stop)
        var layerPages = 0
        var skippedPages = 0
        var lines = 0
        ordered.forEachIndexed { index, layer ->
            progress(index, ordered.size)
            stop.checkStop()
            if (layer.page.toInt() in texted) {
                skippedPages++
            } else {
                val written = writer.write(document.getPage(layer.page.toInt() - 1), layer)
                if (written > 0) {
                    layerPages++
                    lines += written
                }
            }
        }
        progress(ordered.size, ordered.size)
        val warnings = writer.finish()
        return Outcome(layerPages, skippedPages, lines, warnings)
    }

    /** The [pages] (ascending) that carry any text, visible or not. zh-CN: [pages] (升序) 中带有任何文本 (可见与否) 的页. */
    private fun pagesWithText(document: PDDocument, pages: IntArray, stop: StopSignal): Set<Int> {
        val found = HashSet<Int>()
        object : PageSweep(stop, PdfContract.LAYOUT_RAW) {
            private var text = false

            override fun writeString(string: String, positions: List<TextPosition>) {
                if (!text && string.isNotBlank()) text = true
            }

            override fun onPage(number: Int, page: PDPage?): Boolean {
                if (text) found += number
                text = false
                return true
            }
        }.sweep(document, pages)
        return found
    }

    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val layers = parse(PdfPayload.read(inputs))
        return PdfLoader.load(DescriptorSource.of(inputs[0]), request.password, directory).use { loaded ->
            val document = loaded.document
            PdfWriting.checkWritable(document, "the source")
            val outcome = apply(document, layers, request.force, operation.stop) { current, total ->
                operation.progress(PdfContract.STAGE_PROCESSING, current, total)
            }
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
            result(document.numberOfPages, size, outcome)
        }
    }

    fun result(pageCount: Int, sizeBytes: Long, outcome: Outcome): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_LAYER_PAGES, outcome.layerPages)
        put(PdfContract.KEY_SKIPPED_PAGES, outcome.skippedPages)
        put(PdfContract.KEY_LINES, outcome.lines)
        putAll(InfoChanges.result(pageCount, sizeBytes, outcome.warnings))
    }

    /**
     * Writes layers page by page; the font and the shared `q` stream are created with the first line.
     * zh-CN: 逐页写入图层; 字体与共用的 `q` 流在写第一行时创建.
     */
    private class LayerWriter(private val document: PDDocument, private val stop: StopSignal) {

        private var font: GlyphlessFont? = null
        private var opening: COSStream? = null
        private var checks = 0

        /** Writes [layer] on [page]; answers the lines written. zh-CN: 把 [layer] 写到 [page] 上; 返回写入的行数. */
        fun write(page: PDPage, layer: Layer): Int {
            val frame = PageFrame.of(page)
            val crop = page.cropBox
            val originX = crop.lowerLeftX.toDouble()
            val originY = crop.lowerLeftY.toDouble()
            val scaleX = frame.displayWidth / layer.width
            val scaleY = frame.displayHeight / layer.height
            val body = StringBuilder()
            var name: COSName? = null
            var count = 0
            for (line in layer.lines) {
                if (++checks and 0xFF == 0) stop.checkStop()
                if (line.text.isEmpty()) continue
                val left = line.left.coerceIn(0.0, layer.width) * scaleX
                val right = line.right.coerceIn(0.0, layer.width) * scaleX
                val top = line.top.coerceIn(0.0, layer.height) * scaleY
                val bottom = line.bottom.coerceIn(0.0, layer.height) * scaleY
                val size = bottom - top
                val span = right - left
                if (!(size >= MIN_EXTENT && span >= MIN_EXTENT)) continue
                val font = font ?: GlyphlessFont.create(document).also { font = it }
                val codes = font.encode(line.text)
                if (codes.isEmpty()) continue
                val units = codes.sumOf { GlyphlessFont.width(it) }
                val fontName = name ?: fontName(page).also { name = it }
                // The baseline in display coordinates, then in user space. zh-CN: 基线先取显示坐标, 再换算到用户空间.
                val baseX = left
                val baseY = bottom - size * GlyphlessFont.DESCENT / 1000
                val x: Double
                val y: Double
                when (frame.rotation) {
                    90 -> { x = baseY; y = baseX }
                    180 -> { x = frame.width - baseX; y = baseY }
                    270 -> { x = frame.width - baseY; y = frame.height - baseX }
                    else -> { x = baseX; y = frame.height - baseY }
                }
                val matrix = MATRICES.getValue(frame.rotation)
                body.append("BT /").append(fontName.name).append(' ').number(size).append(" Tf ")
                    .number(100.0 * span / (units / 1000.0 * size)).append(" Tz ")
                for (value in matrix) body.append(value).append(' ')
                body.number(x + originX).append(' ').number(y + originY).append(" Tm <")
                for (code in codes) {
                    body.append(HEX[code ushr 12 and 0xF]).append(HEX[code ushr 8 and 0xF]).append(HEX[code ushr 4 and 0xF]).append(HEX[code and 0xF])
                }
                body.append("> Tj ET\n")
                count++
            }
            val chosen = name
            if (count == 0 || chosen == null) return 0
            attach(page, chosen, body)
            return count
        }

        /** A font resource name the page does not use yet. zh-CN: 页面尚未使用的字体资源名. */
        private fun fontName(page: PDPage): COSName {
            val fonts = page.resources?.cosObject?.getDictionaryObject(COSName.FONT) as? COSDictionary
            var index = 0
            while (true) {
                val name = COSName.getPDFName("GL$index")
                if (fonts == null || !fonts.containsKey(name)) return name
                index++
            }
        }

        private fun attach(page: PDPage, name: COSName, body: CharSequence) {
            val dictionary = page.cosObject
            val entries = ArrayList<COSBase>()
            when (val contents = dictionary.getDictionaryObject(COSName.CONTENTS)) {
                is COSStream -> entries += dictionary.getItem(COSName.CONTENTS)
                is COSArray -> for (index in 0 until contents.size()) contents.get(index)?.let { entries += it }
            }
            val stream = document.document.createCOSStream()
            stream.createOutputStream(COSName.FLATE_DECODE).use { output ->
                val prefix = if (entries.isEmpty()) "" else "Q\n"
                output.write("${prefix}q 3 Tr\n$body".toByteArray(Charsets.ISO_8859_1))
                output.write("Q\n".toByteArray(Charsets.US_ASCII))
            }
            val array = COSArray()
            if (entries.isNotEmpty()) {
                // Saves the initial graphics state around the old content. zh-CN: 在原内容外保存初始图形状态.
                array.add(opening ?: document.document.createCOSStream().also { opening = it; it.createOutputStream().use { output -> output.write("q\n".toByteArray(Charsets.US_ASCII)) } })
                entries.forEach { array.add(it) }
            }
            array.add(stream)
            dictionary.setItem(COSName.CONTENTS, array)
            val resources = page.resources?.cosObject?.let { COSDictionary(it) } ?: COSDictionary()
            val fonts = (resources.getDictionaryObject(COSName.FONT) as? COSDictionary)?.let { COSDictionary(it) } ?: COSDictionary()
            fonts.setItem(name, checkNotNull(font).dictionary)
            resources.setItem(COSName.FONT, fonts)
            page.resources = PDResources(resources)
        }

        /** Finishes the font; answers the warnings. zh-CN: 完成字体; 返回警告. */
        fun finish(): List<String> {
            val font = font ?: return emptyList()
            font.finish()
            val overflow = font.overflowCount
            if (overflow == 0) return emptyList()
            return listOf("$overflow distinct characters beyond the first ${GlyphlessFont.SLOT_COUNT} outside the Basic Multilingual Plane were written as U+FFFD")
        }

    }

    /** The smallest line height or width written, in points. zh-CN: 写入的最小行高或行宽, 单位为点. */
    private const val MIN_EXTENT = 0.001

    /** Text matrices `a b c d` by page rotation. zh-CN: 按页面旋转角度给出的文本矩阵 `a b c d`. */
    private val MATRICES = mapOf(
        0 to intArrayOf(1, 0, 0, 1),
        90 to intArrayOf(0, 1, -1, 0),
        180 to intArrayOf(-1, 0, 0, -1),
        270 to intArrayOf(0, -1, 1, 0),
    )

    private val HEX = "0123456789ABCDEF".toCharArray()

    /** Appends [value] with at most three decimals. zh-CN: 追加 [value], 至多保留三位小数. */
    private fun StringBuilder.number(value: Double): StringBuilder {
        var scaled = (value * 1000).roundToLong()
        if (scaled < 0) {
            append('-')
            scaled = -scaled
        }
        append(scaled / 1000)
        var fraction = (scaled % 1000).toInt()
        if (fraction != 0) {
            var digits = 3
            while (fraction % 10 == 0) {
                fraction /= 10
                digits--
            }
            append('.')
            val text = fraction.toString()
            repeat(digits - text.length) { append('0') }
            append(text)
        }
        return this
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.zip.Deflater

/**
 * The stream part of `compress` (roadmap D64), over the objects reachable from the trailer's `/Root` and `/Info` (what
 * the writers write): with `removeMetadata` every `/Metadata` stream entry (the XMP of the catalog, pages, images,
 * fonts) and every page thumbnail (`/Thumb`) is removed; then every stream without a filter gets `FlateDecode` when that
 * makes it smaller. XMP streams that stay keep no filter (PDF/A asks for unfiltered metadata), cross-reference and
 * object streams of the source are never written again, and external streams (`/F`) have no data here.
 * zh-CN: `compress` 的流部分 (路线图 D64), 范围是从 trailer 的 `/Root` 与 `/Info` 可达的对象 (即写出器写出的对象): `removeMetadata`
 * 时删除每个 `/Metadata` 流条目 (目录, 页面, 图片, 字体的 XMP) 与每个页面缩略图 (`/Thumb`); 然后给每个没有过滤器的流加上
 * `FlateDecode` (变小时才加). 保留的 XMP 流不加过滤器 (PDF/A 要求元数据不经过滤), 源文档的交叉引用流与对象流不会再被写出, 外部流
 * (`/F`) 在这里没有数据.
 */
internal object StreamCleanup {

    /** What [apply] did. zh-CN: [apply] 所做的事. */
    data class Summary(val deflatedStreams: Int, val savedBytes: Long, val removedMetadata: Int, val removedThumbnails: Int)

    private val NOT_DEFLATED = setOf(COSName.METADATA, COSName.XREF, COSName.OBJ_STM)

    fun apply(document: PDDocument, removeMetadata: Boolean, stop: StopSignal): Summary {
        val trailer = document.document.trailer
        val seen = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
        val queue = ArrayDeque<COSBase>()
        val streams = ArrayList<COSStream>()
        var removedMetadata = 0
        var removedThumbnails = 0
        fun offer(value: COSBase?) {
            val resolved = (if (value is COSObject) value.`object` else value) ?: return
            if ((resolved is COSDictionary || resolved is COSArray) && seen.add(resolved)) queue.add(resolved)
        }
        offer(trailer.getItem(COSName.ROOT))
        offer(trailer.getItem(COSName.INFO))
        var steps = 0
        while (queue.isNotEmpty()) {
            if (++steps and 0xFF == 0) stop.checkStop()
            when (val current = queue.poll()) {
                is COSDictionary -> {
                    if (removeMetadata) {
                        if (current.getDictionaryObject(COSName.METADATA) is COSStream) {
                            current.removeItem(COSName.METADATA)
                            removedMetadata++
                        }
                        if (current.getCOSName(COSName.TYPE) == COSName.PAGE && current.containsKey(COSName.THUMB)) {
                            current.removeItem(COSName.THUMB)
                            removedThumbnails++
                        }
                    }
                    if (current is COSStream) streams += current
                    current.values.forEach(::offer)
                }
                is COSArray -> for (index in 0 until current.size()) offer(current.get(index))
            }
        }
        var deflated = 0
        var saved = 0L
        for (stream in streams) {
            stop.checkStop()
            val gain = deflate(stream)
            if (gain > 0) {
                deflated++
                saved += gain
            }
        }
        return Summary(deflated, saved, removedMetadata, removedThumbnails)
    }

    /**
     * Gives [stream] `FlateDecode` when it has no filter and the deflated data is smaller; answers the bytes saved (0
     * when left alone).
     * zh-CN: [stream] 没有过滤器且压缩后更小时加上 `FlateDecode`; 返回节省的字节数 (未处理时为 0).
     */
    internal fun deflate(stream: COSStream): Long {
        if (stream.getDictionaryObject(COSName.FILTER) != null || stream.containsKey(COSName.F)) return 0
        if (stream.getCOSName(COSName.TYPE) in NOT_DEFLATED) return 0
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val output = ByteArrayOutputStream()
        var raw = 0L
        try {
            stream.createRawInputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                val chunk = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    raw += n
                    deflater.setInput(buffer, 0, n)
                    while (!deflater.needsInput()) output.write(chunk, 0, deflater.deflate(chunk))
                    // Give up early once the output is not smaller. zh-CN: 输出已不更小时提前放弃.
                    if (output.size() > raw + BUFFER) return 0
                }
                deflater.finish()
                while (!deflater.finished()) output.write(chunk, 0, deflater.deflate(chunk))
            }
        } catch (_: IOException) {
            return 0
        } finally {
            deflater.end()
        }
        if (raw == 0L || output.size() >= raw) return 0
        stream.createRawOutputStream().use { output.writeTo(it) }
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        stream.removeItem(COSName.DECODE_PARMS)
        return raw - output.size()
    }

    private const val BUFFER = 64 * 1024

}

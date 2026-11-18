package io.github.supermonster003.autojs6.plugin.three.paper.pdf.write

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSBoolean
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNull
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfwriter.COSWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Serializes a `PDDocument` with object streams and a cross-reference stream (roadmap D48, D65, spike S4), which the
 * locked PDFBox-Android 2.0.27.0 cannot write: every non-stream indirect object goes into a Flate compressed
 * `/Type /ObjStm` stream of at most [objectsPerStream] objects, stream objects stay at the top level, and a
 * `/Type /XRef` stream replaces the cross-reference table and the trailer. The output is PDF 1.5 or later and never
 * encrypted (`compress` writes unencrypted copies, appendix D Q30), so an encrypted input must have been opened with
 * its owner password and marked [PDDocument.isAllSecurityToBeRemoved] (`PdfWriting.checkWritable`).
 *
 * Which objects are indirect follows `COSWriter` of the same PDFBox, so the object graph matches the classic output:
 * the trailer's `/Root` and `/Info`, every dictionary that is not marked direct (parsed inline dictionaries are),
 * every stream, and every `COSObject` reference to a dictionary; references to other values are written inline, and
 * the `/XObject` and `/Resources` dictionaries inside a dictionary value are written inline as `COSWriter` makes them
 * direct (without changing their direct flag here). Objects are renumbered from 1 with generation 0 in breadth-first
 * order from the root, so only reachable objects are written; primitive values are written by PDFBox's own
 * `writePDF` / `COSWriter.writeString`. A stream's `/Length` is written from its raw data, which is checked while it
 * is copied. Cycles through direct objects and nesting deeper than [MAX_DEPTH] end with an [IOException] (the caller
 * falls back to the classic writer), as does any value type PDFBox does not define.
 * zh-CN: 用对象流与交叉引用流序列化 `PDDocument` (路线图 D48, D65, spike S4), 锁定的 PDFBox-Android 2.0.27.0 写不了这种格式: 每个
 * 非流间接对象进入一个 Flate 压缩, 至多 [objectsPerStream] 个对象的 `/Type /ObjStm` 流, 流对象留在顶层, `/Type /XRef` 流代替交叉
 * 引用表与 trailer. 输出为 PDF 1.5 及以上且从不加密 (`compress` 写不加密的副本, 附录 D Q30), 因此加密的输入必须已用所有者密码
 * 打开并标记 [PDDocument.isAllSecurityToBeRemoved] (`PdfWriting.checkWritable`).
 *
 * 哪些对象是间接对象沿用同一 PDFBox 的 `COSWriter`, 使对象图与经典写法一致: trailer 的 `/Root` 与 `/Info`, 每个未标记为直接对象的
 * 字典 (解析得到的内联字典已标记), 每个流, 以及每个指向字典的 `COSObject` 引用; 指向其他值的引用内联写出, 字典值内部的 `/XObject`
 * 与 `/Resources` 字典按 `COSWriter` 的做法内联 (这里不改它们的 direct 标记). 对象从根开始按广度优先重新编号, 从 1 起, 代号 0,
 * 因此只写出可达对象; 基本值由 PDFBox 自己的 `writePDF` / `COSWriter.writeString` 写出. 流的 `/Length` 按其原始数据写出, 复制时
 * 核对. 经直接对象形成的环, 嵌套深于 [MAX_DEPTH] 以及 PDFBox 未定义的值类型都以 [IOException] 结束 (调用方回落到经典写法).
 */
internal class ObjectStreamWriter(
    private val document: PDDocument,
    private val stop: StopSignal = StopSignal.NEVER,
    private val objectsPerStream: Int = MAX_OBJECTS_PER_STREAM,
) {

    /**
     * What [write] wrote: [bytes] in all, [objects] indirect objects (the object and cross-reference streams included),
     * [compressedObjects] of them inside [objectStreams] object streams.
     * zh-CN: [write] 写出的内容: 共 [bytes] 字节, [objects] 个间接对象 (含对象流与交叉引用流), 其中 [compressedObjects] 个位于
     * [objectStreams] 个对象流中.
     */
    data class Summary(val bytes: Long, val objects: Int, val compressedObjects: Int, val objectStreams: Int)

    private class Position(private val target: OutputStream) : OutputStream() {

        var position = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            position++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            position += len
        }

        override fun flush() = target.flush()

        fun ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))

    }

    private val numbers = IdentityHashMap<COSBase, Int>()
    private val queue = ArrayDeque<COSBase>()
    private val inline = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
    private val path = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())

    /** Cross-reference entries by object number: type, second and third field (ISO 32000-1 7.5.8.3). zh-CN: 交叉引用条目. */
    private val entries = ArrayList<LongArray?>()
    private var next = 1

    private val pendingNumbers = ArrayList<Int>()
    private val pendingOffsets = ArrayList<Int>()
    private val pendingBodies = ByteArrayOutputStream()
    private var objectStreams = 0
    private var compressedObjects = 0

    init {
        require(objectsPerStream in 1..MAX_OBJECTS_PER_STREAM) { "objectsPerStream must be in 1 - $MAX_OBJECTS_PER_STREAM" }
    }

    /** Writes the document to [output] (not closed). zh-CN: 把文档写到 [output] (不关闭). */
    fun write(output: OutputStream): Summary {
        check(!document.isEncrypted || document.isAllSecurityToBeRemoved) { "the object stream writer writes unencrypted documents only" }
        val out = Position(output)
        val trailer = document.document.trailer
        val root = resolve(trailer.getDictionaryObject(COSName.ROOT)) as? COSDictionary ?: throw IOException("the trailer has no /Root dictionary")
        val info = resolve(trailer.getDictionaryObject(COSName.INFO)) as? COSDictionary
        val version = maxOf(document.version, MIN_VERSION)
        out.ascii("%PDF-" + String.format(Locale.ROOT, "%.1f", version) + "\n")
        out.write(BINARY_COMMENT)
        val rootNumber = number(root)
        val infoNumber = info?.let { number(it) }
        while (queue.isNotEmpty()) {
            stop.checkStop()
            val base = queue.removeFirst()
            val number = numbers.getValue(base)
            if (base is COSStream) {
                writeStream(out, number, base)
            } else {
                val body = ByteArrayOutputStream()
                dictionary(base as COSDictionary, body, 0)
                addToObjectStream(out, number, body.toByteArray())
            }
        }
        flushObjectStream(out)
        writeCrossReference(out, rootNumber, infoNumber, identifier(trailer, info))
        out.flush()
        return Summary(out.position, next - 1, compressedObjects, objectStreams)
    }

    private fun resolve(base: COSBase?): COSBase? = if (base is COSObject) base.getObject() else base

    private fun entry(number: Int, type: Long, second: Long, third: Long) {
        while (entries.size <= number) entries += null
        entries[number] = longArrayOf(type, second, third)
    }

    private fun number(base: COSBase): Int = numbers.getOrPut(base) {
        queue.addLast(base)
        next++
    }

    private fun OutputStream.ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))

    private fun reference(base: COSBase, out: OutputStream) = out.ascii("${number(base)} 0 R")

    private fun value(base: COSBase?, out: OutputStream, depth: Int) {
        if (depth > MAX_DEPTH) throw IOException("objects are nested deeper than $MAX_DEPTH levels")
        when (base) {
            null, is COSNull -> out.ascii("null")
            is COSObject -> {
                val target = base.getObject()
                when (target) {
                    null, is COSNull -> out.ascii("null")
                    is COSDictionary -> reference(target, out)
                    else -> value(target, out, depth + 1)
                }
            }
            is COSStream -> reference(base, out)
            is COSDictionary -> if (base.isDirect || base in inline) dictionary(base, out, depth + 1) else reference(base, out)
            is COSArray -> enter(base) {
                out.write('['.code)
                base.forEachIndexed { index, item ->
                    if (index > 0) out.write(' '.code)
                    value(item, out, depth + 1)
                }
                out.write(']'.code)
            }
            is COSString -> COSWriter.writeString(base, out)
            is COSName -> base.writePDF(out)
            is COSInteger -> base.writePDF(out)
            is COSFloat -> base.writePDF(out)
            is COSBoolean -> base.writePDF(out)
            else -> throw IOException("cannot write a ${base.javaClass.simpleName}")
        }
    }

    /**
     * Writes [dictionary] as a dictionary value, without [skip]; [extra] adds entries before the closing `>>`.
     * zh-CN: 把 [dictionary] 写成字典值, 不含 [skip]; [extra] 在结尾的 `>>` 之前追加条目.
     */
    private fun dictionary(dictionary: COSDictionary, out: OutputStream, depth: Int, skip: COSName? = null, extra: (OutputStream) -> Unit = {}) {
        enter(dictionary) {
            out.ascii("<<")
            for ((key, item) in dictionary.entrySet()) {
                if (key == skip || item == null) continue
                if (item is COSDictionary) {
                    // COSWriter makes these direct; the flag stays untouched here. zh-CN: COSWriter 把它们设为直接对象; 这里不改标记.
                    for (name in DIRECTED) {
                        if (key != name) item.getItem(name)?.let { inline += it }
                    }
                }
                out.write(' '.code)
                key.writePDF(out)
                out.write(' '.code)
                value(item, out, depth + 1)
            }
            extra(out)
            out.ascii(" >>")
        }
    }

    /** Runs [block] with [base] on the path of direct objects, refusing a cycle. zh-CN: 在直接对象路径上执行 [block], 拒绝成环. */
    private inline fun enter(base: COSBase, block: () -> Unit) {
        if (!path.add(base)) throw IOException("direct objects refer to themselves")
        try {
            block()
        } finally {
            path.remove(base)
        }
    }

    private fun writeStream(out: Position, number: Int, stream: COSStream) {
        val length = stream.length
        val head = ByteArrayOutputStream()
        dictionary(stream, head, 0, skip = COSName.LENGTH) { it.ascii(" /Length $length") }
        entry(number, 1, out.position, 0)
        out.ascii("$number 0 obj\n")
        out.write(head.toByteArray())
        out.ascii("\nstream\r\n")
        var copied = 0L
        stream.createRawInputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                stop.checkStop()
                out.write(buffer, 0, read)
                copied += read
            }
        }
        if (copied != length) throw IOException("object $number: the stream holds $copied bytes, its /Length says $length")
        out.ascii("\r\nendstream\nendobj\n")
    }

    private fun addToObjectStream(out: Position, number: Int, body: ByteArray) {
        pendingNumbers += number
        pendingOffsets += pendingBodies.size()
        pendingBodies.write(body)
        pendingBodies.write('\n'.code)
        if (pendingNumbers.size >= objectsPerStream) flushObjectStream(out)
    }

    private fun flushObjectStream(out: Position) {
        if (pendingNumbers.isEmpty()) return
        val number = next++
        val header = pendingNumbers.indices.joinToString(" ", postfix = "\n") { "${pendingNumbers[it]} ${pendingOffsets[it]}" }.toByteArray(Charsets.ISO_8859_1)
        val data = deflate(header + pendingBodies.toByteArray())
        entry(number, 1, out.position, 0)
        out.ascii("$number 0 obj\n<< /Type /ObjStm /N ${pendingNumbers.size} /First ${header.size} /Filter /FlateDecode /Length ${data.size} >>\nstream\r\n")
        out.write(data)
        out.ascii("\r\nendstream\nendobj\n")
        pendingNumbers.forEachIndexed { index, member -> entry(member, 2, number.toLong(), index.toLong()) }
        objectStreams++
        compressedObjects += pendingNumbers.size
        pendingNumbers.clear()
        pendingOffsets.clear()
        pendingBodies.reset()
    }

    /**
     * The `/ID` of the output: the trailer's when it has two strings, else a new one from the time (or
     * `PDDocument.documentId`) and the document information, as `COSWriter` makes it.
     * zh-CN: 输出的 `/ID`: trailer 中有两个字符串时沿用, 否则按 `COSWriter` 的做法由时间 (或 `PDDocument.documentId`) 与文档信息生成.
     */
    private fun identifier(trailer: COSDictionary, info: COSDictionary?): COSArray {
        val existing = resolve(trailer.getDictionaryObject(COSName.ID)) as? COSArray
        if (existing != null && existing.size() == 2 && existing.all { resolve(it) is COSString }) return COSArray().apply { existing.forEach { add(resolve(it)) } }
        val digest = MessageDigest.getInstance("MD5")
        digest.update((document.documentId ?: System.currentTimeMillis()).toString().toByteArray(Charsets.ISO_8859_1))
        info?.values?.forEach { digest.update(it.toString().toByteArray(Charsets.ISO_8859_1)) }
        val id = COSString(digest.digest())
        return COSArray().apply {
            add(id)
            add(id)
        }
    }

    private fun writeCrossReference(out: Position, root: Int, info: Int?, identifier: COSArray) {
        val number = next++
        val offset = out.position
        entry(number, 1, offset, 0)
        entry(0, 0, 0, 65535)
        val size = next
        val second = bytesFor(entries.maxOf { it?.get(1) ?: 0L })
        val third = bytesFor(entries.maxOf { it?.get(2) ?: 0L })
        val rows = ByteArrayOutputStream(size * (1 + second + third))
        for (index in 0 until size) {
            val row = entries.getOrNull(index) ?: throw IOException("object $index was numbered but never written")
            rows.write(row[0].toInt())
            for (shift in second - 1 downTo 0) rows.write((row[1] ushr (8 * shift)).toInt() and 0xFF)
            for (shift in third - 1 downTo 0) rows.write((row[2] ushr (8 * shift)).toInt() and 0xFF)
        }
        val data = deflate(rows.toByteArray())
        val head = ByteArrayOutputStream()
        head.ascii("<< /Type /XRef /Size $size /W [1 $second $third] /Root $root 0 R")
        if (info != null) head.ascii(" /Info $info 0 R")
        head.ascii(" /ID ")
        value(identifier, head, 0)
        head.ascii(" /Filter /FlateDecode /Length ${data.size} >>")
        out.ascii("$number 0 obj\n")
        out.write(head.toByteArray())
        out.ascii("\nstream\r\n")
        out.write(data)
        out.ascii("\r\nendstream\nendobj\nstartxref\n$offset\n%%EOF\n")
    }

    private fun bytesFor(value: Long): Int {
        var bytes = 1
        while (bytes < 8 && value ushr (8 * bytes) != 0L) bytes++
        return bytes
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            return ByteArrayOutputStream(bytes.size / 2 + 64).also { output ->
                DeflaterOutputStream(output, deflater).use { it.write(bytes) }
            }.toByteArray()
        } finally {
            deflater.end()
        }
    }

    companion object {

        /** Objects per object stream (roadmap D65). zh-CN: 每个对象流的对象数 (路线图 D65). */
        const val MAX_OBJECTS_PER_STREAM = 200

        /** Deepest nesting of direct values. zh-CN: 直接值的最大嵌套深度. */
        const val MAX_DEPTH = 512

        /** Object streams need PDF 1.5. zh-CN: 对象流需要 PDF 1.5. */
        const val MIN_VERSION = 1.5f

        private const val COPY_BUFFER = 64 * 1024

        private val DIRECTED = listOf(COSName.XOBJECT, COSName.RESOURCES)

        /** `%` and four bytes above 127, telling transfer tools the file is binary. zh-CN: `%` 加四个大于 127 的字节, 表明文件为二进制. */
        private val BINARY_COMMENT = byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte())

    }

}

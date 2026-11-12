package io.github.supermonster003.autojs6.plugin.three.paper.pdf.read

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import org.autojs.plugin.pdf.api.PdfContract
import java.util.Calendar
import java.util.Locale

/**
 * The `getInfo` answer (protocol "Methods", roadmap P2.2, D31): page count, crop box size and rotation of every page,
 * the document information with the XMP `dc:title` as the fallback title, the PDF version, encryption with the
 * permissions the password used grants, and the size of the source. Metadata keys are absent when the document has
 * none; dates are ISO 8601 with a numeric offset.
 * zh-CN: `getInfo` 的应答 (协议 "Methods" 一节, 路线图 P2.2, D31): 页数, 每页裁剪框的尺寸与旋转, 文档信息 (标题缺失时取 XMP 的
 * `dc:title`), PDF 版本, 是否加密及所用密码授予的权限, 以及源的大小. 文档没有的元数据不带对应的键; 日期为带数字时区偏移的 ISO 8601.
 */
internal object DocumentInfoReader {

    /** XMP packets read for the fallback title, at most. zh-CN: 为后备标题读取的 XMP 数据包上限. */
    private const val MAX_XMP_BYTES = 1 shl 20

    private val XMP_TITLE = Regex("<dc:title\\b[^>]*>.*?<rdf:li\\b[^>]*>(.*?)</rdf:li>", RegexOption.DOT_MATCHES_ALL)

    private val XMP_ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|amp|quot|apos);")

    fun read(document: PDDocument, sizeBytes: Long, stop: StopSignal): Map<String, Any?> {
        val count = document.numberOfPages
        val widths = FloatArray(count)
        val heights = FloatArray(count)
        val rotations = IntArray(count)
        var index = 0
        for (page in document.pages) {
            if (index == count) break
            if (index and 0xFF == 0) stop.checkStop()
            val box = page.cropBox
            widths[index] = box.width
            heights[index] = box.height
            rotations[index] = Math.floorMod(page.rotation, 360)
            index++
        }
        val information = document.documentInformation
        val answer = linkedMapOf<String, Any?>(
            PdfContract.KEY_PAGE_COUNT to count,
            PdfContract.KEY_PAGE_WIDTHS to widths,
            PdfContract.KEY_PAGE_HEIGHTS to heights,
            PdfContract.KEY_PAGE_ROTATIONS to rotations,
            PdfContract.KEY_TITLE to (information.title.orNullIfEmpty() ?: xmpTitle(document)),
            PdfContract.KEY_AUTHOR to information.author.orNullIfEmpty(),
            PdfContract.KEY_SUBJECT to information.subject.orNullIfEmpty(),
            PdfContract.KEY_KEYWORDS to information.keywords.orNullIfEmpty(),
            PdfContract.KEY_CREATOR to information.creator.orNullIfEmpty(),
            PdfContract.KEY_PRODUCER to information.producer.orNullIfEmpty(),
            PdfContract.KEY_CREATION_DATE to runCatching { information.creationDate }.getOrNull()?.let(::isoDate),
            PdfContract.KEY_MODIFICATION_DATE to runCatching { information.modificationDate }.getOrNull()?.let(::isoDate),
            PdfContract.KEY_PDF_VERSION to String.format(Locale.ROOT, "%.1f", document.version),
            PdfContract.KEY_ENCRYPTED to document.isEncrypted,
            PdfContract.KEY_PERMISSIONS to permissions(document.currentAccessPermission),
            PdfContract.KEY_SIZE_BYTES to sizeBytes,
        )
        return answer.filterValues { it != null }
    }

    /** The permission names of [access] in the order of the contract. zh-CN: [access] 的权限名, 按契约中的顺序. */
    fun permissions(access: AccessPermission): Array<String> = buildList {
        if (access.canPrint()) add(PdfContract.PERMISSION_PRINT)
        if (access.canExtractContent()) add(PdfContract.PERMISSION_COPY)
        if (access.canModify()) add(PdfContract.PERMISSION_MODIFY)
        if (access.canModifyAnnotations()) add(PdfContract.PERMISSION_ANNOTATE)
        if (access.canFillInForm()) add(PdfContract.PERMISSION_FILL_FORMS)
        if (access.canExtractForAccessibility()) add(PdfContract.PERMISSION_EXTRACT_FOR_ACCESSIBILITY)
        if (access.canAssembleDocument()) add(PdfContract.PERMISSION_ASSEMBLE)
        if (access.canPrintFaithful()) add(PdfContract.PERMISSION_PRINT_HIGH_QUALITY)
    }.toTypedArray()

    /**
     * `yyyy-MM-ddTHH:mm:ss+hh:mm` in the zone of [calendar] (PDF dates carry their own offset; none means UTC).
     * zh-CN: 按 [calendar] 的时区格式化为 `yyyy-MM-ddTHH:mm:ss+hh:mm` (PDF 日期自带偏移; 没有偏移时为 UTC).
     */
    fun isoDate(calendar: Calendar): String {
        val offsetMinutes = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 60_000
        val sign = if (offsetMinutes < 0) '-' else '+'
        val absolute = Math.abs(offsetMinutes)
        return String.format(
            Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:%02d%c%02d:%02d",
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND),
            sign, absolute / 60, absolute % 60,
        )
    }

    /**
     * The first `dc:title` entry of the XMP metadata, read as text without an XML parser (no external entities).
     * zh-CN: XMP 元数据中 `dc:title` 的第一项, 不经 XML 解析器按文本读取 (不解析外部实体).
     */
    fun xmpTitle(document: PDDocument): String? = runCatching {
        val metadata = document.documentCatalog.metadata ?: return null
        val bytes = metadata.createInputStream().use { input ->
            val buffer = ByteArray(MAX_XMP_BYTES)
            var length = 0
            while (length < buffer.size) {
                val read = input.read(buffer, length, buffer.size - length)
                if (read < 0) break
                length += read
            }
            buffer.copyOf(length)
        }
        xmpTitle(String(bytes, Charsets.UTF_8))
    }.getOrNull()

    fun xmpTitle(packet: String): String? {
        val raw = XMP_TITLE.find(packet)?.groupValues?.get(1) ?: return null
        return XMP_ENTITY.replace(raw) { match ->
            when (val entity = match.groupValues[1]) {
                "lt" -> "<"
                "gt" -> ">"
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val code = if (entity.startsWith("#x")) entity.substring(2).toIntOrNull(16) else entity.substring(1).toIntOrNull()
                    if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
                }
            }
        }.trim().orNullIfEmpty()
    }

    private fun String?.orNullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

}

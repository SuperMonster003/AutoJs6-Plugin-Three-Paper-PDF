package io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata

import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfWriting
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.DescriptorSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.Operations
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PayloadObject
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfPayload
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfWarning
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfWarningCodes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The `run` operation `setInfo` (roadmap P6.8, D66; protocol "Version 2"): the inputs are the source PDF and the
 * payload `{ "info": { ... } }`. Each of `title`, `author`, `subject`, `keywords` and `creator` given as a string is
 * set, given as null is removed, and left out is kept; other fields are ignored like every unknown payload field, and
 * `producer` and `modificationDate` stay the plugin's (`PdfWriting.stamp`). A value longer than
 * `MAX_INFO_VALUE_LENGTH` characters or not a string is `INVALID_ARGUMENT`. A catalog XMP stream gets the matching
 * properties ([XmpSync]); when it cannot be parsed it is removed with a warning, so readers do not show the old values.
 * The output is written the classic way (`PdfWriting.write`), unencrypted; an encrypted source needs its owner password
 * (`PERMISSION_DENIED` otherwise, Q30).
 * zh-CN: `run` 操作 `setInfo` (路线图 P6.8, D66; 协议 "Version 2" 一节): 输入为源 PDF 与 payload `{ "info": { ... } }`. `title`,
 * `author`, `subject`, `keywords`, `creator` 中给出字符串的被设置, 给出 null 的被删除, 未给出的保持; 其他字段与所有不认识的 payload
 * 字段一样被忽略, `producer` 与 `modificationDate` 仍由插件写 (`PdfWriting.stamp`). 值超过 `MAX_INFO_VALUE_LENGTH` 个字符或不是字符串
 * 为 `INVALID_ARGUMENT`. 目录的 XMP 流同步对应属性 ([XmpSync]); 无法解析时删除它并给一条警告, 以免读者看到旧值. 输出以经典写法写出
 * (`PdfWriting.write`), 不加密; 加密的源需要所有者密码 (否则为 `PERMISSION_DENIED`, Q30).
 */
internal object InfoChanges {

    const val TITLE = PdfContract.KEY_TITLE
    const val AUTHOR = PdfContract.KEY_AUTHOR
    const val SUBJECT = PdfContract.KEY_SUBJECT
    const val KEYWORDS = PdfContract.KEY_KEYWORDS
    const val CREATOR = PdfContract.KEY_CREATOR

    /** The keys `setInfo` changes, in document information order. zh-CN: `setInfo` 修改的键, 按文档信息的顺序. */
    val KEYS = listOf(TITLE, AUTHOR, SUBJECT, KEYWORDS, CREATOR)

    /** A checked request of `setInfo` or `setOutline`: the password. zh-CN: 已检查的 `setInfo` 或 `setOutline` 请求: 密码. */
    class Request(val password: String?)

    /** Reads the request of the payload operation [op] for [inputCount] inputs. zh-CN: 读取 [inputCount] 个输入的 payload 操作 [op] 的请求. */
    fun request(op: String, options: PdfOptions, inputCount: Int): Request {
        if (inputCount != 2) throw PdfFailure.invalid("$op takes the source and the payload (2 inputs), got $inputCount")
        return Request(options.password()?.takeIf { it.isNotEmpty() })
    }

    /**
     * The changes of the payload: key to the new value or null to remove it; keys left out are absent.
     * zh-CN: payload 中的改动: 键到新值, 或到 null 表示删除; 未给出的键不出现.
     */
    fun parse(payload: PayloadObject): Map<String, String?> {
        val info = payload.obj(PdfContract.FIELD_INFO)
        val changes = LinkedHashMap<String, String?>()
        for (key in KEYS) {
            if (!info.has(key)) continue
            val value = info.optString(key)
            if (value != null && value.length > PdfContract.MAX_INFO_VALUE_LENGTH) {
                throw PdfFailure.invalid("${info.pathOf(key)} has ${value.length} characters, at most ${PdfContract.MAX_INFO_VALUE_LENGTH}")
            }
            changes[key] = value
        }
        return changes
    }

    /**
     * Applies [changes] to the document information and the catalog XMP of [document]; answers the warnings.
     * zh-CN: 把 [changes] 应用到 [document] 的文档信息与目录 XMP; 返回警告.
     */
    fun apply(document: PDDocument, changes: Map<String, String?>): List<PdfWarning> {
        val information = document.documentInformation
        for ((key, value) in changes) {
            when (key) {
                TITLE -> information.title = value
                AUTHOR -> information.author = value
                SUBJECT -> information.subject = value
                KEYWORDS -> information.keywords = value
                CREATOR -> information.creator = value
            }
        }
        document.documentInformation = information
        if (changes.isEmpty()) return emptyList()
        val catalog = document.documentCatalog.cosObject
        val metadata = catalog.getDictionaryObject(COSName.METADATA) as? COSStream ?: return emptyList()
        val packet = try {
            metadata.createInputStream().use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
        val synced = packet?.let { XmpSync.apply(it, changes) }
        if (synced == null) {
            catalog.removeItem(COSName.METADATA)
            return listOf(PdfWarning("the XMP metadata could not be parsed and was removed, so readers do not show its old values", PdfWarningCodes.XMP_REMOVED))
        }
        metadata.createOutputStream().use { it.write(synced) }
        return emptyList()
    }

    fun write(request: Request, inputs: List<ParcelFileDescriptor>, directory: File, operation: Operations.Operation): Map<String, Any?> {
        operation.progress(PdfContract.STAGE_OPENING, 0, 1)
        val changes = parse(PdfPayload.read(inputs))
        return PdfLoader.load(DescriptorSource.of(inputs[0]), request.password, directory).use { loaded ->
            val document = loaded.document
            PdfWriting.checkWritable(document, "the source")
            operation.progress(PdfContract.STAGE_PROCESSING, 0, 1)
            val warnings = apply(document, changes)
            operation.progress(PdfContract.STAGE_WRITING, 0, 1)
            val size = PdfWriting.write(document, FileOutputStream(operation.outputs.single().fileDescriptor), operation.stop)
            result(document.numberOfPages, size, warnings)
        }
    }

    fun result(pageCount: Int, sizeBytes: Long, warnings: List<PdfWarning>): Map<String, Any?> = buildMap {
        put(PdfContract.KEY_PAGE_COUNT, pageCount)
        put(PdfContract.KEY_SIZE_BYTES, sizeBytes)
        if (warnings.isNotEmpty()) put(PdfContract.KEY_WARNINGS, warnings)
    }

}

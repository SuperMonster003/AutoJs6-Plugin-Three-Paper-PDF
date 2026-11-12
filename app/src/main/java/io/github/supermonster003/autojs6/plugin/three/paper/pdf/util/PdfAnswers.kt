package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.IOException

/**
 * Bundle answers of the PDF contract as ordered maps ([PdfBundles.toBundle] makes the Bundle): every answer carries
 * `contractVersion` (the version of the request) and echoes `requestId`; a failed call carries `errorCode` and an
 * `errorMessage` cut to `MAX_ERROR_MESSAGE_BYTES` UTF-8 bytes at a code point boundary (protocol "Conventions").
 * zh-CN: PDF 契约的 Bundle 应答, 以有序 Map 表示 (由 [PdfBundles.toBundle] 生成 Bundle): 每个应答都带 `contractVersion` (请求的版本)
 * 并回显 `requestId`; 失败的调用带 `errorCode` 与按码点边界截到 `MAX_ERROR_MESSAGE_BYTES` 个 UTF-8 字节的 `errorMessage`
 * (协议 "Conventions" 一节).
 */
object PdfAnswers {

    /**
     * A successful answer with [results]; null results are left out (absent keys mean "none" in the contract).
     * zh-CN: 带 [results] 的成功应答; 值为 null 的结果不写入 (契约里缺省的键表示 "没有").
     */
    fun success(version: Int, results: Map<String, Any?> = emptyMap(), requestId: String? = null): Map<String, Any?> =
        envelope(version, requestId).apply {
            for ((key, value) in results) {
                require(key !in ENVELOPE_KEYS) { "result key $key is reserved for the answer envelope" }
                if (value != null) put(key, value)
            }
        }

    fun failure(version: Int, failure: PdfFailure, requestId: String? = null): Map<String, Any?> =
        envelope(version, requestId).apply {
            put(PdfContract.KEY_ERROR_CODE, failure.code)
            put(PdfContract.KEY_ERROR_MESSAGE, truncateUtf8(failure.detail?.trim().orEmpty(), PdfContract.MAX_ERROR_MESSAGE_BYTES))
        }

    /**
     * The error code and message of an answer, or null when it succeeded. A code the contract does not know is
     * reported as `INTERNAL` with the original code in the message.
     * zh-CN: 应答的错误码与消息, 成功时为 null. 契约不认识的码按 `INTERNAL` 报告, 原码写进消息.
     */
    fun errorOf(answer: PdfValues): Pair<String, String>? {
        val code = answer[PdfContract.KEY_ERROR_CODE] as? String ?: return null
        val message = answer[PdfContract.KEY_ERROR_MESSAGE] as? String ?: ""
        return if (PdfErrorCodes.isKnown(code)) code to message else PdfErrorCodes.INTERNAL to "$code: $message".trimEnd(' ', ':')
    }

    /**
     * The contract failure for an exception raised while serving a call: [PdfFailure] as it is, otherwise a code by
     * the exception type (`IO_ERROR`, `PERMISSION_DENIED`, `LIMIT_EXCEEDED` for running out of memory, else `INTERNAL`).
     * zh-CN: 处理调用时抛出的异常对应的契约失败: [PdfFailure] 原样返回, 其余按异常类型取码 (`IO_ERROR`, `PERMISSION_DENIED`,
     * 内存不足为 `LIMIT_EXCEEDED`, 其余为 `INTERNAL`).
     */
    fun failureOf(error: Throwable): PdfFailure = when (error) {
        is PdfFailure -> error
        is IOException -> PdfFailure(PdfErrorCodes.IO_ERROR, describe(error))
        is SecurityException -> PdfFailure(PdfErrorCodes.PERMISSION_DENIED, describe(error))
        is OutOfMemoryError -> PdfFailure.limitExceeded("out of memory: ${error.message.orEmpty()}".trimEnd(' ', ':'))
        else -> PdfFailure(PdfErrorCodes.INTERNAL, describe(error))
    }

    /**
     * [text] cut to at most [maxBytes] UTF-8 bytes without splitting a code point (the host cuts the same way).
     * zh-CN: 把 [text] 截到至多 [maxBytes] 个 UTF-8 字节, 不拆开码点 (与宿主的截断方式相同).
     */
    fun truncateUtf8(text: String, maxBytes: Int): String {
        require(maxBytes >= 0) { "maxBytes must not be negative: $maxBytes" }
        var bytes = 0
        var end = 0
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            val width = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (bytes + width > maxBytes) break
            bytes += width
            end += Character.charCount(codePoint)
        }
        return if (end == text.length) text else text.substring(0, end)
    }

    private val ENVELOPE_KEYS = setOf(
        PdfContract.KEY_CONTRACT_VERSION,
        PdfContract.KEY_REQUEST_ID,
        PdfContract.KEY_ERROR_CODE,
        PdfContract.KEY_ERROR_MESSAGE,
    )

    private fun envelope(version: Int, requestId: String?) = LinkedHashMap<String, Any?>().apply {
        put(PdfContract.KEY_CONTRACT_VERSION, version)
        if (requestId != null) put(PdfContract.KEY_REQUEST_ID, requestId)
    }

    private fun describe(error: Throwable): String =
        listOfNotNull(error::class.java.simpleName, error.message?.takeIf { it.isNotBlank() }).joinToString(": ")

}

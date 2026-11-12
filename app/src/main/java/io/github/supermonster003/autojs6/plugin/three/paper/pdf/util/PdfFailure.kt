package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfErrorCodes

/**
 * A failure of a contract call, carrying a [PdfErrorCodes] code and a detail. Bundle answers write it as
 * `errorCode` + `errorMessage` ([PdfAnswers.failure]); methods without a Bundle answer throw [toBinderException], whose
 * message is `CODE: detail` (protocol "Conventions"). Host-only codes are refused, the plugin never reports them.
 * zh-CN: 契约调用的一次失败, 带 [PdfErrorCodes] 码与说明. Bundle 应答写成 `errorCode` + `errorMessage` ([PdfAnswers.failure]);
 * 没有 Bundle 应答的方法抛出 [toBinderException], 消息为 `CODE: detail` (协议 "Conventions" 一节). 拒绝宿主专属的码, 插件从不报告它们.
 */
class PdfFailure(val code: String, val detail: String? = null) : Exception(PdfErrorCodes.encode(code, detail)) {

    init {
        require(code !in PdfErrorCodes.HOST_ONLY) { "Host-only PDF error code: $code" }
    }

    /**
     * The exception thrown across Binder: one of the three types contract version 1 allows. The host decodes the code
     * from the message whatever the class, so the class only follows the usual Java meaning.
     * zh-CN: 跨 Binder 抛出的异常: 契约版本 1 允许的三种类型之一. 宿主无论类型都从消息解出错误码, 类型只沿用 Java 的惯常含义.
     */
    fun toBinderException(): RuntimeException {
        val text = message.orEmpty()
        return when (code) {
            PdfErrorCodes.PERMISSION_DENIED, PdfErrorCodes.STORAGE_PERMISSION_DENIED -> SecurityException(text)
            PdfErrorCodes.INVALID_ARGUMENT, PdfErrorCodes.PAGE_OUT_OF_RANGE -> IllegalArgumentException(text)
            else -> IllegalStateException(text)
        }
    }

    companion object {

        fun invalid(detail: String) = PdfFailure(PdfErrorCodes.INVALID_ARGUMENT, detail)

        fun pageOutOfRange(detail: String) = PdfFailure(PdfErrorCodes.PAGE_OUT_OF_RANGE, detail)

        fun limitExceeded(detail: String) = PdfFailure(PdfErrorCodes.LIMIT_EXCEEDED, detail)

        fun unsupported(detail: String) = PdfFailure(PdfErrorCodes.UNSUPPORTED, detail)

    }

}

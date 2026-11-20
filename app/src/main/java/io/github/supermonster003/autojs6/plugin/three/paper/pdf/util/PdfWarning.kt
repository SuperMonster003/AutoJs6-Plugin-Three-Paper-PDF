package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfWarningCodes

/**
 * One warning of an operation (protocol "Warning codes", roadmap appendix D Q264 (b)): the English [text] every
 * contract version answers under `warnings`, the [code] of [PdfWarningCodes] and the named [args] of that code (Int
 * or Long for the integer arguments, String for [PdfWarningCodes.STRING_ARGS]), which the completions of version-2
 * requests add under `warningCodes` / `warningArgs` ([PdfWarnings.expand]).
 * zh-CN: 操作的一条警告 (协议 "Warning codes" 一节, 路线图附录 D Q264 (b)): 各契约版本都在 `warnings` 下给出的英文 [text],
 * [PdfWarningCodes] 中的 [code] 及该代码的具名参数 [args] (整数参数为 Int 或 Long, [PdfWarningCodes.STRING_ARGS] 为 String);
 * 版本 2 请求的完成包另在 `warningCodes` / `warningArgs` 下给出后两者 ([PdfWarnings.expand]).
 */
data class PdfWarning(val text: String, val code: String, val args: Map<String, Any> = emptyMap()) {

    /**
     * [args] as one compact JSON object, keys in the order of [PdfWarningCodes.ARGS] (`{}` without arguments).
     * zh-CN: [args] 写成一个紧凑的 JSON 对象, 键按 [PdfWarningCodes.ARGS] 的顺序 (没有参数时为 `{}`).
     */
    fun argsJson(): String {
        val order = PdfWarningCodes.ARGS[code].orEmpty()
        val keys = order.filter { it in args } + args.keys.filter { it !in order }
        return keys.joinToString(",", "{", "}") { key -> quote(key) + ":" + value(args.getValue(key)) }
    }

    override fun toString(): String = text

    private companion object {

        private val BACKSLASH = 0x5C.toChar()

        fun value(value: Any): String = when (value) {
            is Int, is Long -> value.toString()
            is String -> quote(value)
            else -> throw IllegalArgumentException("a warning argument is an integer or a string: ${value.javaClass.name}")
        }

        fun quote(text: String): String = buildString(text.length + 2) {
            append('"')
            for (c in text) {
                when {
                    c == '"' || c == BACKSLASH -> append(BACKSLASH).append(c)
                    c.code < 0x20 -> append(BACKSLASH).append('u').append(c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
            }
            append('"')
        }

    }

}

/**
 * The warnings of a completion. zh-CN: 完成包里的警告.
 */
object PdfWarnings {

    /**
     * [answer] with its warnings written for a request of [contractVersion]: a collection of [PdfWarning] under
     * `warnings` becomes the String array of the English texts; for version 2 `warningCodes` and `warningArgs` of the
     * same length and order follow it (never for version 1). An empty collection is left out, other values stay.
     * zh-CN: 按 [contractVersion] 版本的请求写出 [answer] 中的警告: `warnings` 下的 [PdfWarning] 集合变为英文原文的 String 数组;
     * 版本 2 时随后写入长度与顺序相同的 `warningCodes` 与 `warningArgs` (版本 1 一律不写). 空集合不写入, 其他值保持不变.
     */
    fun expand(answer: Map<String, Any?>, contractVersion: Int): Map<String, Any?> {
        val warnings = answer[PdfContract.KEY_WARNINGS] as? Collection<*> ?: return answer
        val items = warnings.map { it as? PdfWarning ?: throw IllegalArgumentException("warnings holds ${it?.javaClass?.name}") }
        return LinkedHashMap<String, Any?>(answer.size + 2).apply {
            for ((key, value) in answer) {
                if (key != PdfContract.KEY_WARNINGS) {
                    put(key, value)
                } else if (items.isNotEmpty()) {
                    put(PdfContract.KEY_WARNINGS, items.map { it.text }.toTypedArray())
                    if (contractVersion >= PdfContract.CONTRACT_VERSION_2) {
                        put(PdfContract.KEY_WARNING_CODES, items.map { it.code }.toTypedArray())
                        put(PdfContract.KEY_WARNING_ARGS, items.map { it.argsJson() }.toTypedArray())
                    }
                }
            }
        }
    }

    /**
     * Q304: the completion [answer] within [limit] bytes as [size] measures it: above it `warningArgs` is left out
     * first, then `warningCodes`, and readers show the English entries; `warnings` itself stays. Answers without
     * codes are not measured.
     * zh-CN: Q304: 让完成包 [answer] (按 [size] 计量) 不超过 [limit] 字节: 超过时先去掉 `warningArgs`, 仍超再去掉 `warningCodes`,
     * 读取方随之显示英文条目; `warnings` 本身保留. 不带代码的应答不计量.
     */
    fun fit(answer: Map<String, Any?>, limit: Int = PdfContract.MAX_EVENT_BYTES, size: (Map<String, Any?>) -> Int): Map<String, Any?> {
        if (PdfContract.KEY_WARNING_CODES !in answer && PdfContract.KEY_WARNING_ARGS !in answer) return answer
        if (size(answer) <= limit) return answer
        val withoutArgs = answer - PdfContract.KEY_WARNING_ARGS
        if (size(withoutArgs) <= limit) return withoutArgs
        return withoutArgs - PdfContract.KEY_WARNING_CODES
    }

}

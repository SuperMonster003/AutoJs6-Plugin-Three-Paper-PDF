package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.json.JSONException
import org.json.JSONObject

/**
 * One warning of a tool result (protocol "Warning codes", appendix D Q264 (b)): the engine's English [text], and its
 * [code] and named [args] when the completion carried them ([args] is null when the entry was missing or not a JSON
 * object). Integer arguments are Long, the arguments of [PdfWarningCodes.STRING_ARGS] are String.
 * zh-CN: 工具结果的一条警告 (协议 "Warning codes" 一节, 附录 D Q264 (b)): 引擎的英文 [text], 以及完成包带有时的 [code] 与具名参数
 * [args] (该项缺失或不是 JSON 对象时 [args] 为 null). 整数参数为 Long, [PdfWarningCodes.STRING_ARGS] 中的参数为 String.
 */
internal data class ToolWarning(val text: String, val code: String? = null, val args: Map<String, Any>? = null) {

    /**
     * Whether the result page shows this warning localized: a known code whose arguments are all present with the
     * right type; anything else is shown in English.
     * zh-CN: 结果页是否以本地化文字显示这条警告: 代码已知且参数齐全, 类型正确; 否则显示英文.
     */
    val isLocalizable: Boolean
        get() {
            val code = code ?: return false
            val args = args ?: return false
            val names = PdfWarningCodes.ARGS[code] ?: return false
            return names.all { name ->
                when (args[name]) {
                    is String -> name in PdfWarningCodes.STRING_ARGS
                    is Long -> name !in PdfWarningCodes.STRING_ARGS
                    else -> false
                }
            }
        }

    /** The text in the language of [strings], or [text] when not [isLocalizable]. zh-CN: [strings] 所用语言的文字, 不能本地化时为 [text]. */
    fun localized(strings: ToolWarningStrings): String {
        if (!isLocalizable) return text
        val args = args!!
        fun number(name: String) = args.getValue(name) as Long
        fun count(name: String) = number(name).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        fun string(name: String) = args.getValue(name) as String
        return when (code) {
            PdfWarningCodes.IMAGES_UNDECODABLE -> strings.plural(R.plurals.tool_warning_images_undecodable, count(ARG_COUNT), number(ARG_COUNT))
            PdfWarningCodes.IMAGES_TOO_LARGE -> strings.plural(R.plurals.tool_warning_images_too_large, count(ARG_COUNT), number(ARG_COUNT))
            PdfWarningCodes.OBJECT_STREAMS_NOT_USED -> strings.string(R.string.tool_warning_object_streams_not_used, string(PdfWarningCodes.ARG_REASON))
            PdfWarningCodes.OUTPUT_NOT_SMALLER -> strings.string(
                R.string.tool_warning_output_not_smaller,
                strings.size(number(PdfWarningCodes.ARG_SIZE_BYTES)),
                strings.size(number(PdfWarningCodes.ARG_SOURCE_BYTES)),
            )
            PdfWarningCodes.OUTLINE_DAMAGED -> strings.string(R.string.tool_warning_outline_damaged)
            PdfWarningCodes.OUTLINE_NOT_ADJUSTED -> strings.string(R.string.tool_warning_outline_not_adjusted, string(PdfWarningCodes.ARG_REASON))
            PdfWarningCodes.OWNER_PASSWORD_RANDOM -> strings.string(R.string.tool_warning_owner_password_random)
            PdfWarningCodes.USER_PASSWORD_IS_OWNER -> strings.string(R.string.tool_warning_user_password_is_owner)
            PdfWarningCodes.NOT_ENCRYPTED -> strings.string(R.string.tool_warning_not_encrypted)
            PdfWarningCodes.FIELDS_IGNORED -> {
                val more = count(PdfWarningCodes.ARG_MORE)
                val names = string(PdfWarningCodes.ARG_NAMES)
                val listed = if (more > 0) strings.plural(R.plurals.tool_warning_fields_ignored_more, more, names, number(PdfWarningCodes.ARG_MORE)) else names
                strings.plural(R.plurals.tool_warning_fields_ignored, count(ARG_COUNT), number(ARG_COUNT), listed)
            }
            PdfWarningCodes.FORM_SCRIPTS_NOT_RUN -> strings.string(R.string.tool_warning_form_scripts_not_run)
            PdfWarningCodes.SIGNATURES_INVALIDATED -> strings.plural(R.plurals.tool_warning_signatures_invalidated, count(ARG_COUNT), number(ARG_COUNT))
            PdfWarningCodes.XFA_REMOVED -> strings.string(R.string.tool_warning_xfa_removed)
            PdfWarningCodes.REMOTE_RESOURCES_BLOCKED -> strings.plural(R.plurals.tool_warning_remote_resources_blocked, count(ARG_COUNT), number(ARG_COUNT))
            PdfWarningCodes.FIT_PAGE_SIZE_USED_A4 -> strings.string(R.string.tool_warning_fit_page_size_used_a4)
            PdfWarningCodes.IMAGE_DOWNSAMPLED -> strings.string(
                R.string.tool_warning_image_downsampled,
                number(PdfWarningCodes.ARG_IMAGE),
                number(PdfWarningCodes.ARG_WIDTH),
                number(PdfWarningCodes.ARG_HEIGHT),
                number(PdfWarningCodes.ARG_MAX_PIXELS),
                number(PdfWarningCodes.ARG_DECODED_WIDTH),
                number(PdfWarningCodes.ARG_DECODED_HEIGHT),
            )
            PdfWarningCodes.XMP_REMOVED -> strings.string(R.string.tool_warning_xmp_removed)
            PdfWarningCodes.CHARACTERS_REPLACED -> strings.plural(
                R.plurals.tool_warning_characters_replaced,
                count(ARG_COUNT),
                number(ARG_COUNT),
                number(PdfWarningCodes.ARG_LIMIT),
            )
            // A code added to PdfWarningCodes before its text here. zh-CN: 代码已加入 PdfWarningCodes 而这里还没有文字.
            else -> text
        }
    }

    companion object {

        private const val ARG_COUNT = PdfWarningCodes.ARG_COUNT

        /** The warnings of an engine [answer]. zh-CN: 引擎应答 [answer] 中的警告. */
        fun of(answer: Bundle?): List<ToolWarning> = answer?.let {
            parse(it.getStringArray(PdfContract.KEY_WARNINGS), it.getStringArray(PdfContract.KEY_WARNING_CODES), it.getStringArray(PdfContract.KEY_WARNING_ARGS))
        }.orEmpty()

        /**
         * The warnings of the three arrays of a completion, by the reading rules of the protocol: codes through
         * [PdfWarningCodes.align]; arguments missing as a whole when [args] is absent or of another length, and per entry
         * when it is not a JSON object; a JSON number that is not an integer is left out (and so missing).
         * zh-CN: 按协议的读取规则由完成包的三个数组得到警告: 代码经 [PdfWarningCodes.align] 对齐; [args] 缺失或长度不同时参数整体缺失,
         * 某项不是 JSON 对象时该项缺失; 不是整数的 JSON 数值被略去 (因而算作缺失).
         */
        fun parse(warnings: Array<String?>?, codes: Array<String?>?, args: Array<String?>?): List<ToolWarning> {
            warnings ?: return emptyList()
            val aligned = PdfWarningCodes.align(codes, warnings.size)
            val alignedArgs = args?.takeIf { it.size == warnings.size }
            return warnings.withIndex().mapNotNull { (index, text) ->
                text ?: return@mapNotNull null
                ToolWarning(text, aligned.getOrNull(index), alignedArgs?.get(index)?.let(::argsOf))
            }
        }

        /** The arguments of one entry, or null when [json] is not a JSON object. zh-CN: 一项的参数; [json] 不是 JSON 对象时为 null. */
        fun argsOf(json: String): Map<String, Any>? {
            val parsed = try {
                JSONObject(json)
            } catch (_: JSONException) {
                return null
            }
            return buildMap {
                for (key in parsed.keys()) {
                    when (val value = parsed.opt(key)) {
                        is String -> put(key, value)
                        is Int -> put(key, value.toLong())
                        is Long -> put(key, value)
                        else -> Unit
                    }
                }
            }
        }

    }

}

/**
 * The strings a [ToolWarning] is localized with; [of] reads the resources of a context.
 * zh-CN: 本地化 [ToolWarning] 所用的字符串; [of] 读取上下文的资源.
 */
internal interface ToolWarningStrings {

    fun string(id: Int, vararg args: Any): String

    fun plural(id: Int, quantity: Int, vararg args: Any): String

    /** A file size as the file manager shows it. zh-CN: 文件管理器显示的文件大小. */
    fun size(bytes: Long): String

    companion object {

        fun of(context: Context): ToolWarningStrings = object : ToolWarningStrings {
            override fun string(id: Int, vararg args: Any): String = context.getString(id, *args)
            override fun plural(id: Int, quantity: Int, vararg args: Any): String = context.resources.getQuantityString(id, quantity, *args)
            override fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
        }

    }

}

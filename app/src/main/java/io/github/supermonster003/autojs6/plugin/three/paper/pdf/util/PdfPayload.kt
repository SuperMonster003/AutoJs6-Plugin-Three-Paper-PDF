package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.InputBytes
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * The JSON payload of the version 2 `run` operations (roadmap D59, P6.2; protocol "Version 2", "Payload"): the input
 * at [PAYLOAD_INPUT_INDEX] holds one UTF-8 JSON object without a byte order mark, at most [MAX_PAYLOAD_BYTES]. A larger
 * input is `LIMIT_EXCEEDED`; bytes that are not UTF-8, text that is not JSON (RFC 8259, read strictly: no comments, no
 * trailing commas, no single quotes, no leading zeros, no duplicate keys, no unpaired surrogate escapes), nesting
 * deeper than [MAX_DEPTH] and a top-level value that is not an object are `INVALID_ARGUMENT`, and so are a missing
 * required field and a field of the wrong type, which the accessors of [PayloadObject] and [PayloadArray] report with
 * the path of the field (`payload.items[2].opacity`). Unknown fields are ignored, as Bundle keys are. The parser is
 * written here rather than taken from `org.json`, so the JVM tests and every Android version read the same way
 * (Android's `org.json` accepts comments, single quotes and unquoted keys).
 * zh-CN: 版本 2 `run` 操作的 JSON payload (路线图 D59, P6.2; 协议 "Version 2", "Payload"): 位于 [PAYLOAD_INPUT_INDEX] 的输入是一个
 * 不带字节顺序标记的 UTF-8 JSON 对象, 至多 [MAX_PAYLOAD_BYTES]. 更大的输入为 `LIMIT_EXCEEDED`; 不是 UTF-8 的字节, 不是 JSON 的文本
 * (按 RFC 8259 严格读取: 不允许注释, 尾随逗号, 单引号, 前导零, 重复键与不成对的代理项转义), 嵌套深于 [MAX_DEPTH], 以及顶层不是
 * 对象, 都为 `INVALID_ARGUMENT`; 缺少必需字段与字段类型不符同样如此, 由 [PayloadObject] 与 [PayloadArray] 的访问方法报告, 并附字段
 * 路径 (`payload.items[2].opacity`). 未知字段被忽略, 与 Bundle 键相同. 解析器在此自写而不用 `org.json`, 使 JVM 测试与各 Android
 * 版本的读法一致 (Android 的 `org.json` 接受注释, 单引号与不带引号的键).
 */
internal object PdfPayload {

    /** TODO(P6.2 row 1): `PdfContract.PAYLOAD_INPUT_INDEX` once the contract version 2 AAR is staged. zh-CN: 换入契约版本 2 的 AAR 后改指契约常量. */
    const val PAYLOAD_INPUT_INDEX = 1

    /** TODO(P6.2 row 1): `PdfContract.MAX_PAYLOAD_BYTES` once the contract version 2 AAR is staged. zh-CN: 换入契约版本 2 的 AAR 后改指契约常量. */
    const val MAX_PAYLOAD_BYTES = 16L * 1024 * 1024

    /**
     * Deepest nesting of arrays and objects, above the 32 outline levels of `setOutline` (two per level).
     * zh-CN: 数组与对象的最大嵌套深度, 高于 `setOutline` 的 32 层大纲 (每层两级).
     */
    const val MAX_DEPTH = 128

    /** The root path of the messages. zh-CN: 消息中的根路径. */
    const val ROOT = "payload"

    /** Reads the payload input of a version 2 `run` operation. zh-CN: 读取版本 2 `run` 操作的 payload 输入. */
    fun read(inputs: List<ParcelFileDescriptor>): PayloadObject {
        val input = inputs.getOrNull(PAYLOAD_INPUT_INDEX) ?: throw PdfFailure.invalid("the operation has no payload input (input ${PAYLOAD_INPUT_INDEX + 1})")
        return parse(InputBytes.read(input, MAX_PAYLOAD_BYTES, "the payload"))
    }

    /** Parses payload bytes; [limit] is [MAX_PAYLOAD_BYTES] outside tests. zh-CN: 解析 payload 字节; 测试之外 [limit] 为 [MAX_PAYLOAD_BYTES]. */
    fun parse(bytes: ByteArray, limit: Long = MAX_PAYLOAD_BYTES): PayloadObject {
        if (bytes.size > limit) throw PdfFailure.limitExceeded("the payload has ${bytes.size} bytes, above $limit")
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            throw PdfFailure.invalid("the payload starts with a byte order mark")
        }
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw PdfFailure.invalid("the payload is not UTF-8")
        }
        val root = Parser(text).document()
        if (root !is Map<*, *>) throw PdfFailure.invalid("the payload is ${kind(root)}, not an object")
        @Suppress("UNCHECKED_CAST")
        return PayloadObject(ROOT, root as Map<String, Any?>)
    }

    /** What a parsed value is, for messages. zh-CN: 解析所得值的种类, 用于消息. */
    internal fun kind(value: Any?): String = when (value) {
        null -> "null"
        is String -> "a string"
        is Boolean -> "a boolean"
        is Long, is Double -> "a number"
        is List<*> -> "an array"
        is Map<*, *> -> "an object"
        else -> value.javaClass.simpleName
    }

    /** `path.key`, or `path["key"]` when the key is not an identifier. zh-CN: 键不是标识符时写成 `path["key"]`. */
    internal fun child(path: String, key: String): String =
        if (IDENTIFIER.matches(key)) "$path.$key" else path + "[" + quote(key) + "]"

    private fun quote(text: String): String = buildString {
        append('"')
        for (char in text) {
            when {
                char == '"' || char == '\\' -> append('\\').append(char)
                char < ' ' -> append(String.format(java.util.Locale.ROOT, "\\u%04x", char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * A strict RFC 8259 reader: objects become `LinkedHashMap<String, Any?>` (field order kept), arrays `List<Any?>`,
     * integral numbers without fraction or exponent that fit `Long` become `Long`, other numbers finite `Double`,
     * `null` becomes Kotlin `null`.
     * zh-CN: 严格的 RFC 8259 读取器: 对象为 `LinkedHashMap<String, Any?>` (保持字段顺序), 数组为 `List<Any?>`, 不带小数与指数且能放进
     * `Long` 的整数为 `Long`, 其他数字为有限的 `Double`, `null` 为 Kotlin 的 `null`.
     */
    private class Parser(private val text: String) {

        private var position = 0

        fun document(): Any? {
            skipWhitespace()
            if (position >= text.length) throw failure("the payload is empty")
            val value = value(1)
            skipWhitespace()
            if (position < text.length) throw failure("unexpected text after the JSON value")
            return value
        }

        private fun failure(message: String) = PdfFailure.invalid("the payload is not valid JSON: $message (character ${position + 1})")

        private fun skipWhitespace() {
            while (position < text.length) {
                when (text[position]) {
                    ' ', '\t', '\n', '\r' -> position++
                    else -> return
                }
            }
        }

        private fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw PdfFailure.invalid("the payload is nested deeper than $MAX_DEPTH levels")
            if (position >= text.length) throw failure("a value is missing")
            return when (val char = text[position]) {
                '{' -> objectValue(depth)
                '[' -> arrayValue(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char in '0'..'9') number() else throw failure("unexpected '${printable(char)}'")
            }
        }

        private fun printable(char: Char) = if (char < ' ') String.format(java.util.Locale.ROOT, "\\u%04x", char.code) else char.toString()

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, position)) throw failure("unexpected '${printable(text[position])}'")
            position += word.length
            return value
        }

        private fun objectValue(depth: Int): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            position++
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw failure("a field name in double quotes is expected")
                val key = string()
                skipWhitespace()
                if (peek() != ':') throw failure("':' is expected after the field name")
                position++
                skipWhitespace()
                val item = value(depth + 1)
                if (result.containsKey(key)) throw PdfFailure.invalid("the payload has the field ${quote(key)} twice in one object")
                result[key] = item
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> throw failure("',' or '}' is expected")
                }
            }
        }

        private fun arrayValue(depth: Int): List<Any?> {
            val result = ArrayList<Any?>()
            position++
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                result += value(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> throw failure("',' or ']' is expected")
                }
            }
        }

        private fun peek(): Char? = text.getOrNull(position)

        private fun string(): String {
            val builder = StringBuilder()
            position++
            while (true) {
                if (position >= text.length) throw failure("a string is not closed")
                val char = text[position++]
                when {
                    char == '"' -> return builder.toString()
                    char == '\\' -> escape(builder)
                    char < ' ' -> {
                        position--
                        throw failure("a control character must be escaped in a string")
                    }
                    else -> builder.append(char)
                }
            }
        }

        private fun escape(builder: StringBuilder) {
            if (position >= text.length) throw failure("a string is not closed")
            when (val char = text[position++]) {
                '"', '\\', '/' -> builder.append(char)
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    val unit = hex()
                    when {
                        Character.isHighSurrogate(unit) -> {
                            if (!text.startsWith("\\u", position)) throw failure("an unpaired surrogate escape")
                            position += 2
                            val low = hex()
                            if (!Character.isLowSurrogate(low)) throw failure("an unpaired surrogate escape")
                            builder.append(unit).append(low)
                        }
                        Character.isLowSurrogate(unit) -> throw failure("an unpaired surrogate escape")
                        else -> builder.append(unit)
                    }
                }
                else -> {
                    position--
                    throw failure("an unknown escape '\\${printable(char)}'")
                }
            }
        }

        private fun hex(): Char {
            if (position + 4 > text.length) throw failure("a \\u escape needs four hexadecimal digits")
            var code = 0
            repeat(4) {
                val digit = Character.digit(text[position], 16)
                if (digit < 0) throw failure("a \\u escape needs four hexadecimal digits")
                code = code * 16 + digit
                position++
            }
            return code.toChar()
        }

        private fun number(): Any {
            val start = position
            if (peek() == '-') position++
            when (peek()) {
                '0' -> position++
                in '1'..'9' -> while (peek() in '0'..'9') position++
                else -> throw failure("a digit is expected")
            }
            if (peek() in '0'..'9') throw failure("a number cannot have leading zeros")
            var integral = true
            if (peek() == '.') {
                integral = false
                position++
                if (peek() !in '0'..'9') throw failure("a digit is expected after '.'")
                while (peek() in '0'..'9') position++
            }
            if (peek() == 'e' || peek() == 'E') {
                integral = false
                position++
                if (peek() == '+' || peek() == '-') position++
                if (peek() !in '0'..'9') throw failure("a digit is expected in the exponent")
                while (peek() in '0'..'9') position++
            }
            val literal = text.substring(start, position)
            if (integral) literal.toLongOrNull()?.let { return it }
            val value = literal.toDouble()
            if (value.isInfinite()) throw failure("the number $literal is out of range")
            return value
        }

    }

}

/**
 * A payload object with typed accessors; [path] names it in messages. A field that is absent or `null` counts as not
 * given for the `opt` accessors, which answer null; [has] tells the two apart where `null` means something (`fillForm`
 * restores a default for a `null` value).
 * zh-CN: 带类型访问方法的 payload 对象; [path] 用于在消息中指明它. 对 `opt` 访问方法而言, 字段不存在或为 `null` 都算未给出, 返回
 * null; 在 `null` 有含义的场合 (`fillForm` 中 `null` 恢复默认值) 用 [has] 区分两者.
 */
internal class PayloadObject(val path: String, private val entries: Map<String, Any?>) {

    /** Field names in payload order. zh-CN: 按 payload 中的顺序列出的字段名. */
    val keys: Set<String> get() = entries.keys

    fun has(key: String): Boolean = entries.containsKey(key)

    /** The raw value: `String`, `Boolean`, `Long`, `Double`, `List`, `Map` or null. zh-CN: 原始值. */
    fun value(key: String): Any? = entries[key]

    fun pathOf(key: String): String = PdfPayload.child(path, key)

    private fun missing(key: String) = PdfFailure.invalid("${pathOf(key)} is required")

    private fun wrong(key: String, expected: String): PdfFailure = PdfFailure.invalid("${pathOf(key)} must be $expected, not ${PdfPayload.kind(entries[key])}")

    fun string(key: String): String = optString(key) ?: throw missing(key)

    fun optString(key: String): String? = when (val value = entries[key]) {
        null -> null
        is String -> value
        else -> throw wrong(key, "a string")
    }

    fun boolean(key: String): Boolean = optBoolean(key) ?: throw missing(key)

    fun optBoolean(key: String): Boolean? = when (val value = entries[key]) {
        null -> null
        is Boolean -> value
        else -> throw wrong(key, "a boolean")
    }

    /** A whole number in [range]; `2.0` and `2e0` count as 2. zh-CN: [range] 内的整数; `2.0` 与 `2e0` 算作 2. */
    fun long(key: String, range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Long = optLong(key, range) ?: throw missing(key)

    fun optLong(key: String, range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Long? {
        val value = when (val raw = entries[key]) {
            null -> return null
            is Long -> raw
            is Double -> if (raw == Math.floor(raw) && raw >= -9.0E15 && raw <= 9.0E15) raw.toLong() else throw wrong(key, "a whole number")
            else -> throw wrong(key, "a whole number")
        }
        if (value !in range) throw PdfFailure.invalid("${pathOf(key)} must be in ${range.first} - ${range.last}, not $value")
        return value
    }

    fun int(key: String, range: IntRange): Int = long(key, range.first.toLong()..range.last.toLong()).toInt()

    fun optInt(key: String, range: IntRange): Int? = optLong(key, range.first.toLong()..range.last.toLong())?.toInt()

    fun double(key: String, range: ClosedFloatingPointRange<Double> = -Double.MAX_VALUE..Double.MAX_VALUE): Double =
        optDouble(key, range) ?: throw missing(key)

    fun optDouble(key: String, range: ClosedFloatingPointRange<Double> = -Double.MAX_VALUE..Double.MAX_VALUE): Double? {
        val value = when (val raw = entries[key]) {
            null -> return null
            is Long -> raw.toDouble()
            is Double -> raw
            else -> throw wrong(key, "a number")
        }
        if (value !in range) throw PdfFailure.invalid("${pathOf(key)} must be in ${range.start} - ${range.endInclusive}, not $value")
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun optObject(key: String): PayloadObject? = when (val value = entries[key]) {
        null -> null
        is Map<*, *> -> PayloadObject(pathOf(key), value as Map<String, Any?>)
        else -> throw wrong(key, "an object")
    }

    fun obj(key: String): PayloadObject = optObject(key) ?: throw missing(key)

    fun optArray(key: String): PayloadArray? = when (val value = entries[key]) {
        null -> null
        is List<*> -> PayloadArray(pathOf(key), value)
        else -> throw wrong(key, "an array")
    }

    fun array(key: String): PayloadArray = optArray(key) ?: throw missing(key)

}

/**
 * A payload array with typed accessors by index; [path] names it in messages.
 * zh-CN: 按序号提供类型访问方法的 payload 数组; [path] 用于在消息中指明它.
 */
internal class PayloadArray(val path: String, private val items: List<Any?>) {

    val size: Int get() = items.size

    fun value(index: Int): Any? = items[index]

    fun pathOf(index: Int): String = "$path[$index]"

    private fun wrong(index: Int, expected: String): PdfFailure = PdfFailure.invalid("${pathOf(index)} must be $expected, not ${PdfPayload.kind(items[index])}")

    fun string(index: Int): String = items[index] as? String ?: throw wrong(index, "a string")

    @Suppress("UNCHECKED_CAST")
    fun obj(index: Int): PayloadObject = (items[index] as? Map<*, *>)?.let { PayloadObject(pathOf(index), it as Map<String, Any?>) } ?: throw wrong(index, "an object")

    fun array(index: Int): PayloadArray = (items[index] as? List<*>)?.let { PayloadArray(pathOf(index), it) } ?: throw wrong(index, "an array")

    fun strings(): List<String> = List(size) { string(it) }

    fun objects(): List<PayloadObject> = List(size) { obj(it) }

}

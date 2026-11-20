package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import java.math.BigDecimal

/**
 * JavaScript literals and the layout of generated scripts (roadmap P6.13, D69). Values are [Map] (keys in their order),
 * [List], [String], [Number], [Boolean], null and [Raw] code; a value is written on one line when it fits in [WIDTH]
 * columns, otherwise objects and arrays get one entry per line. No trailing commas, so the code parses in the host's
 * Rhino as well as in Node.js and Bun.
 * zh-CN: 生成脚本的 JavaScript 字面量与排版 (路线图 P6.13, D69). 值为 [Map] (按键的顺序), [List], [String], [Number], [Boolean], null 与
 * [Raw] 代码; 能在 [WIDTH] 列内写下时写成一行, 否则对象与数组每项一行. 不写尾随逗号, 因此代码在宿主的 Rhino 与 Node.js, Bun 中都能解析.
 */
internal object Js {

    const val WIDTH = 100

    const val INDENT = "    "

    /** Code written as is: an identifier or an expression. zh-CN: 原样写出的代码: 标识符或表达式. */
    class Raw(val code: String) {
        override fun equals(other: Any?) = other is Raw && other.code == code
        override fun hashCode() = code.hashCode()
        override fun toString() = code
    }

    private val IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

    // Reserved words are quoted as keys, for older parsers. zh-CN: 保留字作为键时加引号, 照顾较旧的解析器.
    private val RESERVED = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum",
        "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new",
        "null", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while",
        "with", "yield", "await", "implements", "interface", "package", "private", "protected", "public", "__proto__",
    )

    /**
     * A single-quoted string literal. Quotes, backslashes, line and paragraph separators, control and format characters
     * (bidirectional overrides included) and unpaired surrogates are escaped; other text, Chinese and emoji included, is
     * kept as it reads.
     * zh-CN: 单引号字符串字面量. 转义引号, 反斜杠, 行与段落分隔符, 控制与格式字符 (含双向覆盖字符) 以及不成对的代理项; 其余文字 (含中文与
     * emoji) 原样保留.
     */
    fun string(text: String): String = buildString(text.length + 2) {
        append('\'')
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\'' -> append("\\'")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> {
                    val codePoint = text.codePointAt(index)
                    if (escaped(codePoint)) append(unicode(char)).append(unicode(text[index + 1])) else append(char).append(text[index + 1])
                    index += 1
                }
                Character.isSurrogate(char) || escaped(char.code) -> append(unicode(char))
                else -> append(char)
            }
            index += 1
        }
        append('\'')
    }

    /** A number: integers as they are, decimals without trailing zeros or exponents. zh-CN: 数字: 整数原样, 小数不带尾随零与指数. */
    fun number(value: Number): String = when (value) {
        is Int, is Long, is Short, is Byte -> value.toString()
        else -> {
            val double = value.toDouble()
            require(double.isFinite()) { "not a finite number: $value" }
            val text = BigDecimal(if (value is Float) value.toString() else double.toString()).stripTrailingZeros().toPlainString()
            if (text == "-0") "0" else text
        }
    }

    /** An object key: a bare identifier when it is one, otherwise a string literal. zh-CN: 对象的键: 是标识符时直接写, 否则写成字符串字面量. */
    fun key(name: String): String = if (IDENTIFIER.matches(name) && name !in RESERVED) name else string(name)

    /** [value] on one line. zh-CN: 写成一行的 [value]. */
    fun inline(value: Any?): String = when (value) {
        null -> "null"
        is Raw -> value.code
        is String -> string(value)
        is Boolean -> value.toString()
        is Number -> number(value)
        is Map<*, *> -> if (value.isEmpty()) "{}" else value.entries.joinToString(", ", "{ ", " }") { (name, item) -> key(name as String) + ": " + inline(item) }
        is List<*> -> value.joinToString(", ", "[", "]") { inline(it) }
        else -> throw IllegalArgumentException("unsupported value ${value.javaClass.name}")
    }

    /**
     * [value] laid out from [column] (the characters already on its first line) at nesting [indent], leaving [trail]
     * characters after it on its last line.
     * zh-CN: 从第 [column] 列 (首行已有的字符数) 开始, 以嵌套层级 [indent] 排版的 [value], 其末行之后还要留 [trail] 个字符.
     */
    fun format(value: Any?, indent: Int, column: Int, trail: Int = 0): String {
        val flat = inline(value)
        if (column + flat.length + trail <= WIDTH) return flat
        val inner = INDENT.repeat(indent + 1)
        val outer = INDENT.repeat(indent)
        return when {
            value is Map<*, *> && value.isNotEmpty() -> value.entries.joinToString(",\n", "{\n", "\n$outer}") { (name, item) ->
                val head = inner + key(name as String) + ": "
                head + format(item, indent + 1, head.length, 1)
            }
            value is List<*> && value.isNotEmpty() && value.all { it is Number } -> packed(value, inner, outer)
            value is List<*> && value.isNotEmpty() -> value.joinToString(",\n", "[\n", "\n$outer]") { item ->
                inner + format(item, indent + 1, inner.length, 1)
            }
            else -> flat
        }
    }

    /**
     * A call statement `prefix callee(arguments);` at nesting [indent]: on one line when it fits, otherwise with the last
     * argument laid out over several lines, or one argument per line.
     * zh-CN: 嵌套层级 [indent] 上的调用语句 `prefix callee(arguments);`: 放得下时写一行, 否则把最后一个参数排成多行, 或每个参数一行.
     */
    fun call(prefix: String, callee: String, arguments: List<Any?>, indent: Int): String {
        val pad = INDENT.repeat(indent)
        val head = "$pad$prefix$callee("
        val flat = arguments.joinToString(", ") { inline(it) }
        if (head.length + flat.length + 2 <= WIDTH) return "$head$flat);"
        val leading = arguments.dropLast(1).joinToString("") { inline(it) + ", " }
        val last = arguments.lastOrNull()
        if ((last is Map<*, *> || last is List<*>) && head.length + leading.length + 1 <= WIDTH && leading.lines().size == 1) {
            return head + leading + format(last, indent, head.length + leading.length, 2) + ");"
        }
        val inner = INDENT.repeat(indent + 1)
        return arguments.joinToString(",\n", "$head\n", "\n$pad);") { inner + format(it, indent + 1, inner.length, 1) }
    }

    /** A long list of numbers (a page order), as many per line as fit. zh-CN: 较长的数字列表 (页面顺序), 每行放尽可能多的项. */
    private fun packed(items: List<*>, inner: String, outer: String): String {
        val lines = ArrayList<String>()
        val line = StringBuilder(inner)
        items.forEachIndexed { index, item ->
            val text = inline(item) + if (index < items.size - 1) "," else ""
            if (line.length > inner.length && line.length + 1 + text.length > WIDTH) {
                lines += line.toString()
                line.setLength(0)
                line.append(inner)
            }
            if (line.length > inner.length) line.append(' ')
            line.append(text)
        }
        lines += line.toString()
        return lines.joinToString("\n", "[\n", "\n$outer]")
    }

    private fun escaped(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.UNASSIGNED, Character.PRIVATE_USE -> true
        else -> codePoint == 0xFEFF
    }

    private fun unicode(char: Char): String = "\\u%04X".format(char.code)

}

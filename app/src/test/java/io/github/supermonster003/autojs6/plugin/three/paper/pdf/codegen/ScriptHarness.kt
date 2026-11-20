package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.TopLevel

/**
 * Runs generated scripts against stubs (roadmap P6.13, E1): the stubs stand for `pdf`, the document handle `pdf.open`
 * answers, `files`, `console`, the `fs` of Node.js and `Bun.write`, and record every call with its arguments. Rhino
 * scripts run in the host's Rhino ([runRhino]); the Node.js runner reuses [STUBS] with promises. Calls are compared in
 * a normalized form ([norm]): numbers as [Double], objects as maps, arrays as lists.
 * zh-CN: 在桩对象上运行生成的脚本 (路线图 P6.13, E1): 桩对象代替 `pdf`, `pdf.open` 返回的文档句柄, `files`, `console`, Node.js 的 `fs`
 * 与 `Bun.write`, 并记录每次调用及其参数. Rhino 脚本在宿主的 Rhino 中运行 ([runRhino]); Node.js 运行器以 Promise 方式复用 [STUBS].
 * 调用按规范形式比较 ([norm]): 数字为 [Double], 对象为 map, 数组为列表.
 */
internal object ScriptHarness {

    /** The page count of the document stub. zh-CN: 文档桩对象的页数. */
    const val PAGE_COUNT = 12

    /** The texts `pdf.text` answers. zh-CN: `pdf.text` 返回的文字. */
    val TEXTS = listOf("page one", "page two")

    /** One recorded call. zh-CN: 记录的一次调用. */
    data class Call(val name: String, val args: List<Any?>) {
        override fun toString() = "$name$args"
    }

    /**
     * `__makeStubs(record, wrap, textResult)`: [wrap] turns a result into what the call answers (itself for Rhino, a
     * promise for Node.js and Bun), `textResult` the texts into the answer of `pdf.text`. File writes of every style
     * are recorded as `write(path, text)`.
     * zh-CN: `__makeStubs(record, wrap, textResult)`: [wrap] 把结果转为调用的返回值 (Rhino 为结果本身, Node.js 与 Bun 为 Promise),
     * `textResult` 把文字转为 `pdf.text` 的返回值. 各写法的文件写入都记录为 `write(path, text)`.
     */
    val STUBS = """
        |function __makeStubs(record, wrap, textResult) {
        |    var result = function (name, path) { return { path: path, warnings: ['w-' + name] }; };
        |    var pdf = {};
        |    ['merge', 'fromImages', 'encrypt', 'decrypt', 'rotate', 'watermark', 'stamp', 'compress', 'fillForm', 'setInfo', 'setOutline'].forEach(function (name) {
        |        pdf[name] = function () { record('pdf.' + name, arguments); return wrap(result(name, arguments[1])); };
        |    });
        |    pdf.split = function () {
        |        record('pdf.split', arguments);
        |        return wrap({ paths: [arguments[1] + '/part-1.pdf', arguments[1] + '/part-2.pdf'], warnings: ['w-split'] });
        |    };
        |    pdf.text = function () { record('pdf.text', arguments); return wrap(textResult(['${TEXTS[0]}', '${TEXTS[1]}'])); };
        |    pdf.open = function () {
        |        record('pdf.open', arguments);
        |        return wrap({
        |            pageCount: $PAGE_COUNT,
        |            renderToFile: function () { record('doc.renderToFile', arguments); return wrap({ path: arguments[1] }); },
        |            save: function () { record('doc.save', arguments); return wrap(result('save', arguments[0])); },
        |            close: function () { record('doc.close', arguments); return wrap(undefined); }
        |        });
        |    };
        |    var console = {};
        |    ['log', 'warn', 'error'].forEach(function (name) {
        |        console[name] = function () { record('console.' + name, arguments); };
        |    });
        |    var write = function (path, text) { record('write', [path, text]); return wrap(undefined); };
        |    return {
        |        pdf: pdf,
        |        console: console,
        |        files: { ensureDir: function () { record('files.ensureDir', arguments); return true; }, write: write },
        |        fs: { promises: { mkdir: function () { record('fs.mkdir', arguments); return wrap(undefined); }, writeFile: write } },
        |        Bun: { write: write }
        |    };
        |}
    """.trimMargin()

    private val RHINO_PRELUDE = STUBS + "\n" + """
        |var __calls = [];
        |var __stubs = __makeStubs(function (name, args) {
        |    __calls.push({ name: name, args: Array.prototype.slice.call(args) });
        |}, function (value) { return value; }, function (texts) { return texts; });
        |var pdf = __stubs.pdf;
        |var files = __stubs.files;
        |var console = __stubs.console;
    """.trimMargin()

    private val PLACEHOLDER = Regex("let (\\w*[Pp]assword\\d*) = null;")

    /** The test value a password placeholder is filled with. zh-CN: 填入密码占位的测试值. */
    fun passwordOf(name: String) = "test-$name"

    /** [code] with its password placeholders filled with [passwordOf]. zh-CN: 密码占位已填入 [passwordOf] 的 [code]. */
    fun fill(code: String): String = PLACEHOLDER.replace(code) { match ->
        val name = match.groupValues[1]
        "let $name = ${Js.string(passwordOf(name))};"
    }

    /** The placeholders of [code]. zh-CN: [code] 中的密码占位. */
    fun placeholdersOf(code: String): List<String> = PLACEHOLDER.findAll(code).map { it.groupValues[1] }.toList()

    /**
     * Runs Rhino [code] against the stubs and answers its calls; a script error is thrown with the calls made until
     * then in [ScriptFailure].
     * zh-CN: 在桩对象上运行 Rhino [code] 并返回其调用; 脚本出错时抛出 [ScriptFailure], 其中带有出错前的调用.
     */
    fun runRhino(code: String, name: String = "script.js"): List<Call> = rhino { context, scope ->
        context.evaluateString(scope, RHINO_PRELUDE, "prelude.js", 1, null)
        val failure = runCatching { context.evaluateString(scope, code, name, 1, null) }.exceptionOrNull()
        val calls = callsOf(Context.toString(context.evaluateString(scope, "JSON.stringify(__calls)", "calls.js", 1, null)))
        if (failure != null) throw ScriptFailure(failure, calls)
        calls
    }

    /** Evaluates [code] in a fresh Rhino scope. zh-CN: 在新的 Rhino 作用域中求值 [code]. */
    fun evalRhino(code: String): Any? = rhino { context, scope -> context.evaluateString(scope, code, "eval.js", 1, null) }

    /** A script error with the calls made before it. zh-CN: 脚本错误, 带有出错前的调用. */
    class ScriptFailure(cause: Throwable, val calls: List<Call>) : RuntimeException(cause.message, cause)

    private fun <T> rhino(block: (Context, TopLevel) -> T): T {
        val context = ContextFactory.getGlobal().enterContext()
        try {
            context.languageVersion = Context.VERSION_ES6
            context.setInterpretedMode(true)
            return block(context, context.initStandardObjects())
        } finally {
            Context.exit()
        }
    }

    /** Calls from the JSON `[{ name, args }]`. zh-CN: 由 JSON `[{ name, args }]` 得到的调用. */
    fun callsOf(json: String): List<Call> = callsOf(JSONArray(json))

    fun callsOf(array: JSONArray): List<Call> = List(array.length()) { index ->
        val item = array.getJSONObject(index)
        @Suppress("UNCHECKED_CAST")
        Call(item.getString("name"), norm(item.getJSONArray("args")) as List<Any?>)
    }

    /** A call with normalized arguments. zh-CN: 参数已规范化的调用. */
    fun call(name: String, vararg args: Any?): Call {
        @Suppress("UNCHECKED_CAST")
        return Call(name, norm(args.toList()) as List<Any?>)
    }

    /**
     * The normalized form of [value]: JSON and Kotlin objects as maps, arrays as lists, numbers as [Double] (a float by
     * its decimal text), `JSONObject.NULL` as null.
     * zh-CN: [value] 的规范形式: JSON 与 Kotlin 对象为 map, 数组为列表, 数字为 [Double] (float 按其十进制文本), `JSONObject.NULL` 为 null.
     */
    fun norm(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { norm(value.opt(it)) }
        is JSONArray -> List(value.length()) { norm(value.opt(it)) }
        is Map<*, *> -> value.entries.associate { (key, item) -> key as String to norm(item) }
        is List<*> -> value.map { norm(it) }
        is IntArray -> value.map { it.toDouble() }
        is Array<*> -> value.map { norm(it) }
        is Float -> value.toString().toDouble()
        is Number -> value.toDouble()
        is Js.Raw -> throw IllegalArgumentException("raw code in an expectation: $value")
        else -> value
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What a tool screen keeps across the death of its process (E4 R3, roadmap appendix D Q326 (a)): the tool, the toolbox
 * inputs and output folder of this run, the toolbox task it showed and the result it showed. Inputs are kept by URI and
 * name only, never with a password: an encrypted input asks for its password again. The task is valid only in the
 * process that wrote it ([SavedTask.process]); after a new process the saved [result] stands in for it. Encoded as JSON
 * ([encode]) and kept below [MAX_CHARS]: the inputs go first, then the warnings, when the state would be larger.
 * zh-CN: 工具界面在进程死亡后保留的内容 (E4 R3, 路线图附录 D Q326 (a)): 工具, 本次的工具箱输入与输出目录, 界面显示的工具箱任务与结果.
 * 输入只按 URI 与名称保留, 从不带密码: 加密的输入会再次询问密码. 任务只在写入它的进程中有效 ([SavedTask.process]); 进程重建后由保存的
 * [result] 代替. 编码为 JSON ([encode]) 并保持在 [MAX_CHARS] 以内: 状态过大时先舍弃输入, 再舍弃警告.
 */
internal class ToolScreenState(
    val kind: String,
    val inputs: List<SavedInput> = emptyList(),
    val outputFolder: String? = null,
    val task: SavedTask? = null,
    val result: ToolResult? = null,
) {

    /** A toolbox input: its URI (as text) and the name shown for it. zh-CN: 一个工具箱输入: 其 URI (文本) 与显示的名称. */
    class SavedInput(val uri: String, val displayName: String)

    /** The toolbox task [id] of the process [process] (its pid). zh-CN: 进程 [process] (其 pid) 中的工具箱任务 [id]. */
    class SavedTask(val process: Int, val id: Int)

    fun encode(): String {
        val full = json(inputs, result).toString()
        if (full.length <= MAX_CHARS) return full
        val withoutInputs = json(emptyList(), result).toString()
        if (withoutInputs.length <= MAX_CHARS) return withoutInputs
        return json(emptyList(), result?.let { ToolResult(it.displayPath, it.files, emptyList(), it.summary) }).toString()
    }

    private fun json(inputs: List<SavedInput>, result: ToolResult?) = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        put(KEY_KIND, kind)
        if (inputs.isNotEmpty()) {
            put(KEY_INPUTS, JSONArray().apply { inputs.forEach { put(JSONObject().put(KEY_URI, it.uri).put(KEY_NAME, it.displayName)) } })
        }
        outputFolder?.let { put(KEY_OUTPUT_FOLDER, it) }
        task?.let { put(KEY_TASK, JSONObject().put(KEY_PROCESS, it.process).put(KEY_ID, it.id)) }
        result?.let { put(KEY_RESULT, resultJson(it)) }
    }

    companion object {

        const val VERSION = 1

        /** The largest encoded state, in characters (the saved state of a screen should stay small). zh-CN: 编码后状态的最大字符数 (界面保存的状态应当很小). */
        const val MAX_CHARS = 64 * 1024

        private const val KEY_VERSION = "version"
        private const val KEY_KIND = "kind"
        private const val KEY_INPUTS = "inputs"
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
        private const val KEY_OUTPUT_FOLDER = "outputFolder"
        private const val KEY_TASK = "task"
        private const val KEY_PROCESS = "process"
        private const val KEY_ID = "id"
        private const val KEY_RESULT = "result"
        private const val KEY_DISPLAY_PATH = "displayPath"
        private const val KEY_FILES = "files"
        private const val KEY_WARNINGS = "warnings"
        private const val KEY_TEXT = "text"
        private const val KEY_CODE = "code"
        private const val KEY_ARGS = "args"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_TYPE = "type"
        private const val TYPE_COMPRESSED = "compressed"
        private const val TYPE_FILLED = "filled"
        private const val TYPE_PROPERTIES = "properties"
        private const val KEY_SOURCE_BYTES = "sourceBytes"
        private const val KEY_SIZE_BYTES = "sizeBytes"
        private const val KEY_IMAGES_RECOMPRESSED = "imagesRecompressed"
        private const val KEY_IMAGES_TOTAL = "imagesTotal"
        private const val KEY_FILLED = "filled"
        private const val KEY_FLATTENED = "flattened"
        private const val KEY_INFO_CHANGED = "infoChanged"
        private const val KEY_OUTLINE_CLEARED = "outlineCleared"

        /** The state [encode] wrote, or null for none, another version or text that is not one. zh-CN: [encode] 写出的状态; 没有, 版本不同或不是状态的文字时为 null. */
        fun decode(text: String?): ToolScreenState? {
            text ?: return null
            return try {
                val json = JSONObject(text)
                if (json.optInt(KEY_VERSION) != VERSION) return null
                val inputs = json.optJSONArray(KEY_INPUTS)?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { SavedInput(it.getString(KEY_URI), it.getString(KEY_NAME)) }
                    }
                }.orEmpty()
                ToolScreenState(
                    kind = json.getString(KEY_KIND),
                    inputs = inputs,
                    outputFolder = json.optString(KEY_OUTPUT_FOLDER).ifEmpty { null },
                    task = json.optJSONObject(KEY_TASK)?.let { SavedTask(it.getInt(KEY_PROCESS), it.getInt(KEY_ID)) },
                    result = json.optJSONObject(KEY_RESULT)?.let(::resultOf),
                )
            } catch (_: JSONException) {
                null
            }
        }

        private fun resultJson(result: ToolResult) = JSONObject().apply {
            put(KEY_DISPLAY_PATH, result.displayPath)
            put(KEY_FILES, result.files)
            put(KEY_WARNINGS, JSONArray().apply {
                result.warnings.forEach { warning ->
                    put(JSONObject().apply {
                        put(KEY_TEXT, warning.text)
                        warning.code?.let { put(KEY_CODE, it) }
                        warning.args?.let { put(KEY_ARGS, JSONObject(it).toString()) }
                    })
                }
            })
            when (val summary = result.summary) {
                is ToolSummary.Compressed -> put(KEY_SUMMARY, JSONObject().put(KEY_TYPE, TYPE_COMPRESSED)
                    .put(KEY_SOURCE_BYTES, summary.sourceBytes).put(KEY_SIZE_BYTES, summary.sizeBytes)
                    .put(KEY_IMAGES_RECOMPRESSED, summary.imagesRecompressed).put(KEY_IMAGES_TOTAL, summary.imagesTotal))
                is ToolSummary.Filled -> put(KEY_SUMMARY, JSONObject().put(KEY_TYPE, TYPE_FILLED)
                    .put(KEY_FILLED, summary.filled).put(KEY_FLATTENED, summary.flattened))
                is ToolSummary.PropertiesSaved -> put(KEY_SUMMARY, JSONObject().put(KEY_TYPE, TYPE_PROPERTIES)
                    .put(KEY_INFO_CHANGED, summary.infoChanged).put(KEY_OUTLINE_CLEARED, summary.outlineCleared))
                null -> Unit
            }
        }

        private fun resultOf(json: JSONObject): ToolResult {
            val warnings = json.optJSONArray(KEY_WARNINGS)?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { entry ->
                        ToolWarning(
                            entry.getString(KEY_TEXT),
                            entry.optString(KEY_CODE).ifEmpty { null },
                            entry.optString(KEY_ARGS).ifEmpty { null }?.let { ToolWarning.argsOf(it) },
                        )
                    }
                }
            }.orEmpty()
            val summary = json.optJSONObject(KEY_SUMMARY)?.let { entry ->
                when (entry.optString(KEY_TYPE)) {
                    TYPE_COMPRESSED -> ToolSummary.Compressed(
                        entry.getLong(KEY_SOURCE_BYTES), entry.getLong(KEY_SIZE_BYTES), entry.getInt(KEY_IMAGES_RECOMPRESSED), entry.getInt(KEY_IMAGES_TOTAL),
                    )
                    TYPE_FILLED -> ToolSummary.Filled(entry.getInt(KEY_FILLED), entry.getBoolean(KEY_FLATTENED))
                    TYPE_PROPERTIES -> ToolSummary.PropertiesSaved(entry.getInt(KEY_INFO_CHANGED), entry.getBoolean(KEY_OUTLINE_CLEARED))
                    else -> null
                }
            }
            return ToolResult(json.getString(KEY_DISPLAY_PATH), json.getInt(KEY_FILES), warnings, summary)
        }

    }

}

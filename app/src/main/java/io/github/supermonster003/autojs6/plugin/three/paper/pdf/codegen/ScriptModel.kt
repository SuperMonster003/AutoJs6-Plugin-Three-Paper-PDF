package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions

/**
 * The three ways a script can be written (roadmap P6.13, D51): the host's Rhino with synchronous `pdf.xxx(...)` calls,
 * Node.js with the promises of `require('pdf')`, and Bun with `host.pdf` of the host client. [id] is the stable name
 * the result page remembers.
 * zh-CN: 脚本的三种写法 (路线图 P6.13, D51): 宿主 Rhino 的同步 `pdf.xxx(...)` 调用, Node.js 的 `require('pdf')` Promise 写法, 以及
 * Bun 经宿主客户端的 `host.pdf`. [id] 是结果页记住的固定名称.
 */
internal enum class ScriptStyle(val id: String) {

    RHINO("rhino"),
    NODE("node"),
    BUN("bun"),
    ;

    companion object {

        fun of(id: String?): ScriptStyle? = entries.firstOrNull { it.id == id }

    }

}

/**
 * A path written into a script: [path] is absolute; [guessed] when the real path could not be known (a document
 * provider without one), so the script asks to check it.
 * zh-CN: 写入脚本的路径: [path] 为绝对路径; 无法得知真实路径时 (文档提供方未给出) [guessed] 为 true, 脚本会提示核对.
 */
internal data class ScriptFile(val path: String, val guessed: Boolean = false)

/**
 * One input of a run: its [file], the [name] the tool showed (result names derive from it), and whether it was opened
 * with a password ([password]; the password itself never reaches a script, roadmap P6.13).
 * zh-CN: 一次执行的一个输入: [file], 工具显示的 [name] (结果名称由它派生), 以及是否用密码打开 ([password]; 密码本身绝不进入脚本, 路线图
 * P6.13).
 */
internal data class ScriptInput(val file: ScriptFile, val name: String, val password: Boolean)

/**
 * The tool request a script is generated from (roadmap D69): the tool, its inputs in order, the options the user
 * chose (as the tool ran them, Item 1 custom values included), the [output] (a file, or the folder of split parts and
 * page images) and, for an image watermark, the [image]. [keyLength] is the AES key length encryption used. Built only
 * by [of], which drops the password an encryption carries.
 * zh-CN: 生成脚本所依据的工具请求 (路线图 D69): 工具, 按顺序排列的输入, 用户选择的选项 (与工具执行时相同, 含第 1 项的自定义值),
 * [output] (一个文件, 或拆分部分与页面图片所在的文件夹), 以及图片水印的 [image]. [keyLength] 是加密所用的 AES 密钥长度. 只能由 [of]
 * 构造, 它会去掉加密选项携带的密码.
 */
internal class ScriptRequest private constructor(
    val kind: ToolKind,
    val inputs: List<ScriptInput>,
    val options: ToolOptions,
    val output: ScriptFile,
    val image: ScriptFile?,
    val keyLength: Int,
) {

    companion object {

        /** The AES key length of a new encryption when the platform has AES-256. zh-CN: 平台支持 AES-256 时新加密所用的 AES 密钥长度. */
        const val KEY_LENGTH_256 = 256

        fun of(kind: ToolKind, inputs: List<ScriptInput>, options: ToolOptions, output: ScriptFile, image: ScriptFile? = null, keyLength: Int = KEY_LENGTH_256): ScriptRequest {
            require(inputs.isNotEmpty()) { "no inputs" }
            require(matches(kind, options)) { "options ${options.javaClass.simpleName} do not belong to ${kind.id}" }
            val kept = when (options) {
                is ToolOptions.Encrypt -> ToolOptions.Encrypt("")
                is ToolOptions.Watermark -> options.copy(image = null)
                else -> options
            }
            return ScriptRequest(kind, inputs.toList(), kept, output, image, keyLength)
        }

        private fun matches(kind: ToolKind, options: ToolOptions): Boolean = when (kind) {
            ToolKind.MERGE -> options is ToolOptions.Merge
            ToolKind.SPLIT -> options is ToolOptions.Split
            ToolKind.TO_IMAGES -> options is ToolOptions.ToImages
            ToolKind.FROM_IMAGES -> options is ToolOptions.FromImages
            ToolKind.ENCRYPT -> options is ToolOptions.Encrypt
            ToolKind.DECRYPT -> options is ToolOptions.Decrypt
            ToolKind.EXTRACT_TEXT -> options is ToolOptions.ExtractText
            ToolKind.ORGANIZE -> options is ToolOptions.Organize
            ToolKind.WATERMARK -> options is ToolOptions.Watermark
            ToolKind.PAGE_NUMBERS -> options is ToolOptions.PageNumbers
            ToolKind.COMPRESS -> options is ToolOptions.Compress
            ToolKind.FILL_FORM -> options is ToolOptions.FillForm
            ToolKind.PROPERTIES -> options is ToolOptions.Properties
        }

    }

}

/**
 * A generated script: its [code], the [fileName] it is saved and opened under, and for Node.js the `project.json` of
 * the project folder it is saved as ([projectJson], null otherwise).
 * zh-CN: 生成的脚本: [code], 保存与打开所用的 [fileName], 以及 Node.js 写法保存为项目文件夹时的 `project.json` ([projectJson], 其他写法为
 * null).
 */
internal class GeneratedScript(val style: ScriptStyle, val code: String, val fileName: String, val projectJson: String?) {

    /** The project folder of a Node.js script, else null. zh-CN: Node.js 脚本的项目文件夹名, 其他写法为 null. */
    val projectName: String? get() = if (projectJson != null) fileName.removeSuffix(NODE_SUFFIX) else null

    companion object {

        const val RHINO_SUFFIX = ".js"

        const val NODE_SUFFIX = ".node.js"

        const val BUN_SUFFIX = ".bun.js"

        /** The entry file of a saved Node.js project. zh-CN: 保存的 Node.js 项目的入口文件. */
        const val NODE_MAIN = "main.js"

        const val PROJECT_JSON = "project.json"

    }

}

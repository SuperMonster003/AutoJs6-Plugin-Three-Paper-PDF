package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions

/**
 * One file of a run as the tool saw it: its URI as text, the name the tool showed, and whether a password opened it
 * (only that it did: the password stays with the tool).
 * zh-CN: 工具所见的一次执行中的一个文件: 文本形式的 URI, 工具显示的名称, 以及是否用密码打开 (只记录是否用了密码, 密码留在工具中).
 */
internal data class ScriptSource(val uri: String, val name: String, val password: Boolean = false)

/**
 * What a run asked for, kept from its start so the result page can write it as a script (roadmap P6.13, D69): the
 * tool, its inputs in order, its options without the new password of an encryption or the image of a watermark (kept as
 * [image]), and the folder the file manager showed ([parentPath], the fallback for paths a provider does not reveal).
 * [requestFor] adds the result path once the run is done.
 * zh-CN: 一次执行所请求的内容, 从开始时保存, 供结果页写成脚本 (路线图 P6.13, D69): 工具, 按顺序排列的输入, 去掉加密的新密码与水印图片
 * (另存为 [image]) 的选项, 以及文件管理器显示的文件夹 ([parentPath], 提供方不给出路径时的退路). 执行完成后由 [requestFor] 加上结果路径.
 */
internal class ScriptRun private constructor(
    val kind: ToolKind,
    val sources: List<ScriptSource>,
    val options: ToolOptions,
    val image: ScriptSource?,
    val parentPath: String?,
) {

    /**
     * The request of this run with the result at [displayPath] (the path the result page shows) and the AES
     * [keyLength] an encryption used; null when the run cannot be written as a script.
     * zh-CN: 结果位于 [displayPath] (结果页显示的路径), 加密使用 AES 密钥长度 [keyLength] 时本次执行的请求; 无法写成脚本时为 null.
     */
    fun requestFor(displayPath: String?, keyLength: Int = ScriptRequest.KEY_LENGTH_256): ScriptRequest? {
        if (sources.isEmpty()) return null
        val inputs = sources.map { ScriptInput(ScriptPaths.fileOf(it.uri, it.name, parentPath), it.name, it.password) }
        val name = displayPath?.trim()?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: DEFAULT_OUTPUT
        val output = ScriptPaths.outputOf(displayPath, parentPath, name)
        val image = image?.let { ScriptPaths.fileOf(it.uri, it.name, parentPath) }
        return runCatching { ScriptRequest.of(kind, inputs, options, output, image, keyLength) }.getOrNull()
    }

    companion object {

        /** The result name when the run gave none. zh-CN: 执行未给出结果名称时使用的名称. */
        const val DEFAULT_OUTPUT = "output.pdf"

        fun of(kind: ToolKind, sources: List<ScriptSource>, options: ToolOptions, image: ScriptSource? = null, parentPath: String? = null): ScriptRun {
            val kept = when (options) {
                is ToolOptions.Encrypt -> ToolOptions.Encrypt("")
                is ToolOptions.Watermark -> options.copy(image = null)
                else -> options
            }
            return ScriptRun(kind, sources.toList(), kept, image, parentPath?.trim()?.takeIf { it.isNotEmpty() })
        }

    }

}

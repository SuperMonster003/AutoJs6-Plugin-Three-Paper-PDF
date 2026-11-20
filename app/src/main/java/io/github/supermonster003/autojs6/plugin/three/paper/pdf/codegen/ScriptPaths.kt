package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import java.io.ByteArrayOutputStream

/**
 * The paths a generated script uses (roadmap P6.13): scripts take file paths, while the tools read URIs. A `file:` URI
 * gives its path; the external storage provider gives one for the shared storage (`primary:`, `home:`) and removable
 * volumes (`XXXX-XXXX:`), the downloads provider for its `raw:` documents. Otherwise the folder the file manager named
 * is used, and as the last resort the Download folder with the file's name, marked as guessed so the script asks to
 * check it. Plain JVM code, without `android.net.Uri`, for the unit tests.
 * zh-CN: 生成脚本使用的路径 (路线图 P6.13): 脚本使用文件路径, 而工具读取 URI. `file:` URI 给出其路径; 外部存储提供方对共享存储
 * (`primary:`, `home:`) 与可移动卷 (`XXXX-XXXX:`) 给出路径, 下载提供方对其 `raw:` 文档给出路径. 否则使用文件管理器给出的文件夹,
 * 最后退而使用 Download 文件夹加文件名, 并标记为推测, 脚本会提示核对. 为单元测试使用纯 JVM 代码, 不用 `android.net.Uri`.
 */
internal object ScriptPaths {

    /** The shared storage of the primary user. zh-CN: 主用户的共享存储. */
    const val SHARED_STORAGE = "/storage/emulated/0"

    /** The folder of a guessed path. zh-CN: 推测路径所在的文件夹. */
    const val GUESS_FOLDER = "$SHARED_STORAGE/Download"

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    private const val DOWNLOADS = "com.android.providers.downloads.documents"

    private val VOLUME = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")

    /**
     * The path of a selected file: from [uri] when it tells, else in [parentPath] (the file manager's folder, when
     * absolute), else guessed in [GUESS_FOLDER].
     * zh-CN: 选中文件的路径: [uri] 能给出时取之, 否则在 [parentPath] (文件管理器的文件夹, 须为绝对路径) 中, 否则推测在 [GUESS_FOLDER] 中.
     */
    fun fileOf(uri: String, displayName: String, parentPath: String?): ScriptFile {
        pathOf(uri)?.let { return ScriptFile(it) }
        val name = nameOf(displayName)
        folderOf(parentPath)?.let { return ScriptFile(join(it, name)) }
        return ScriptFile(join(GUESS_FOLDER, name), guessed = true)
    }

    /**
     * The path of a result: [displayPath] when it is absolute (the toolbox writes real files, the file manager shows
     * its path), else [name] in [parentPath], else guessed in [GUESS_FOLDER].
     * zh-CN: 结果的路径: [displayPath] 为绝对路径时取之 (工具箱写入真实文件, 文件管理器显示其路径), 否则为 [parentPath] 中的 [name],
     * 否则推测在 [GUESS_FOLDER] 中.
     */
    fun outputOf(displayPath: String?, parentPath: String?, name: String): ScriptFile {
        folderOf(displayPath)?.let { return ScriptFile(it) }
        folderOf(parentPath)?.let { return ScriptFile(join(it, name)) }
        return ScriptFile(join(GUESS_FOLDER, name), guessed = true)
    }

    /** The file path [uri] names, or null when it names none. zh-CN: [uri] 指向的文件路径, 无法得知时为 null. */
    fun pathOf(uri: String): String? {
        val scheme = uri.substringBefore(':', "").lowercase()
        val rest = uri.substringAfter(':', "").substringBefore('?').substringBefore('#')
        return when (scheme) {
            "file" -> clean(decode(if (rest.startsWith("//")) rest.removePrefix("//").let { "/" + it.substringAfter('/', "") } else rest))
            "content" -> documentPath(rest.removePrefix("//"))
            else -> null
        }
    }

    private fun documentPath(rest: String): String? {
        val authority = rest.substringBefore('/')
        val segments = rest.substringAfter('/', "").split('/').map { decode(it) ?: return null }
        val marker = segments.lastIndexOf("document").takeIf { it >= 0 } ?: segments.lastIndexOf("tree").takeIf { it >= 0 } ?: return null
        val id = segments.getOrNull(marker + 1) ?: return null
        return when (authority) {
            EXTERNAL_STORAGE -> {
                val volume = id.substringBefore(':', "")
                val relative = id.substringAfter(':', "")
                val root = when {
                    volume == "primary" -> SHARED_STORAGE
                    volume == "home" -> "$SHARED_STORAGE/Documents"
                    VOLUME.matches(volume) -> "/storage/${volume.uppercase()}"
                    else -> return null
                }
                clean(if (relative.isEmpty()) root else "$root/$relative")
            }
            DOWNLOADS -> id.takeIf { it.startsWith("raw:") }?.removePrefix("raw:")?.let(::clean)
            else -> null
        }
    }

    private fun folderOf(path: String?): String? = path?.trim()?.let(::clean)

    /** An absolute path without empty, `.` or `..` segments, or null. zh-CN: 不含空段, `.` 或 `..` 的绝对路径, 否则为 null. */
    private fun clean(path: String?): String? {
        if (path == null || !path.startsWith("/") || path.any { it.code < 0x20 || it == '\u007F' }) return null
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
        return segments.joinToString("/", "/")
    }

    private fun nameOf(displayName: String): String =
        displayName.substringAfterLast('/').trim().takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: "document.pdf"

    private fun join(folder: String, name: String) = "$folder/$name"

    /** Percent-decoding as UTF-8 (a `+` stays a plus), or null for a broken escape. zh-CN: 以 UTF-8 解码百分号转义 (`+` 保持为加号), 转义不完整时为 null. */
    private fun decode(text: String): String? {
        if ('%' !in text) return text
        val bytes = ByteArrayOutputStream(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '%') {
                val high = Character.digit(text.getOrElse(index + 1) { ' ' }, 16)
                val low = Character.digit(text.getOrElse(index + 2) { ' ' }, 16)
                if (high < 0 || low < 0) return null
                bytes.write(high * 16 + low)
                index += 3
            } else {
                val end = text.offsetByCodePoints(index, 1)
                bytes.write(text.substring(index, end).toByteArray(Charsets.UTF_8))
                index = end
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

}

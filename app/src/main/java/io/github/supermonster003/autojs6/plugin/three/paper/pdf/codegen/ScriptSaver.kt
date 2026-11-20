package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import java.io.File
import java.io.IOException

/**
 * "Save as .js" of the result page (roadmap P6.13): scripts go to [FOLDER] under the output folder of the settings. A
 * Rhino or Bun script is one file; a Node.js script becomes a project folder with [GeneratedScript.NODE_MAIN] and the
 * `project.json` that declares its capabilities. Names already taken get ` (1)`, ` (2)`, ... before the whole suffix
 * (`3-paper-merge (1).bun.js`), so the engine suffix stays; nothing is overwritten.
 * zh-CN: 结果页的 "保存为 .js" (路线图 P6.13): 脚本保存到设置中输出文件夹下的 [FOLDER]. Rhino 与 Bun 脚本是一个文件; Node.js 脚本保存为项目
 * 文件夹, 内含 [GeneratedScript.NODE_MAIN] 与声明其能力的 `project.json`. 名称已被占用时在整个后缀之前加 ` (1)`, ` (2)`, ...
 * (`3-paper-merge (1).bun.js`), 引擎后缀因此保留; 不覆盖任何文件.
 */
internal object ScriptSaver {

    const val FOLDER = "scripts"

    /** Tries per name before giving up. zh-CN: 放弃之前尝试的名称数. */
    const val MAX_TRIES = 1000

    private val SUFFIXES = listOf(GeneratedScript.NODE_SUFFIX, GeneratedScript.BUN_SUFFIX, GeneratedScript.RHINO_SUFFIX)

    /**
     * Saves [script] under [outputFolder]/[FOLDER] and answers the file or project folder written.
     * zh-CN: 把 [script] 保存到 [outputFolder]/[FOLDER] 下, 返回写入的文件或项目文件夹.
     */
    fun save(outputFolder: File, script: GeneratedScript): File {
        val folder = File(outputFolder, FOLDER)
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) throw IOException("cannot create $folder")
        val projectJson = script.projectJson
        if (projectJson == null) {
            val file = claim(folder, script.fileName) { it.createNewFile() }
            try {
                file.writeText(script.code, Charsets.UTF_8)
            } catch (e: IOException) {
                file.delete()
                throw e
            }
            return file
        }
        val project = claim(folder, checkNotNull(script.projectName)) { it.mkdir() }
        try {
            File(project, GeneratedScript.NODE_MAIN).writeText(script.code, Charsets.UTF_8)
            File(project, GeneratedScript.PROJECT_JSON).writeText(projectJson, Charsets.UTF_8)
        } catch (e: IOException) {
            project.deleteRecursively()
            throw e
        }
        return project
    }

    /** The name of the [index]th try for [name]: the name itself, then ` (index)` before its suffix. zh-CN: [name] 的第 [index] 次尝试所用的名称. */
    fun nameOf(name: String, index: Int): String {
        if (index == 0) return name
        val suffix = SUFFIXES.firstOrNull { name.endsWith(it) && name.length > it.length }.orEmpty()
        return name.removeSuffix(suffix) + " ($index)" + suffix
    }

    /** The first name [create] succeeds with (it creates the file or folder atomically). zh-CN: [create] 第一个成功的名称 (它以原子方式建立文件或文件夹). */
    private fun claim(folder: File, name: String, create: (File) -> Boolean): File {
        for (index in 0 until MAX_TRIES) {
            val file = File(folder, nameOf(name, index))
            if (create(file)) return file
            if (!file.exists()) throw IOException("cannot create $file")
        }
        throw IOException("no free name for $name in $folder")
    }

}

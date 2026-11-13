package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.json.JSONArray
import java.io.File

/**
 * The toolbox settings (roadmap D37, P4.1): the output folder (default `Documents/3-Paper PDF/` of the primary
 * storage) and the default resolution of PDF to images. Only the toolbox reads them; scripts choose their own outputs.
 * zh-CN: 工具箱设置 (路线图 D37, P4.1): 输出目录 (默认为主存储的 `Documents/3-Paper PDF/`) 与 PDF 转图片的默认分辨率. 只有工具箱读取它们;
 * 脚本自行决定输出位置.
 */
internal object ToolboxPreferences {

    const val FILE = "toolbox"

    const val KEY_OUTPUT_FOLDER = "outputFolder"

    const val KEY_DEFAULT_DPI = "defaultDpi"

    const val DEFAULT_DPI = 144

    val DPI_CHOICES = listOf(72, 144, 300)

    /** The folder name under `Documents`. zh-CN: `Documents` 下的文件夹名. */
    const val DEFAULT_FOLDER_NAME = "3-Paper PDF"

    fun defaultOutputFolder(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DEFAULT_FOLDER_NAME)

    /** The chosen folder, null for the default. zh-CN: 选定的文件夹, 默认时为 null. */
    fun chosenOutputFolder(context: Context): File? = prefs(context).getString(KEY_OUTPUT_FOLDER, null)?.takeIf { it.isNotBlank() }?.let(::File)

    fun outputFolder(context: Context): File = chosenOutputFolder(context) ?: defaultOutputFolder()

    /** Null restores the default; answers the real result of the write. zh-CN: null 恢复默认; 返回写入的真实结果. */
    @SuppressLint("UseKtx")
    fun setOutputFolder(context: Context, folder: File?): Boolean {
        val editor = prefs(context).edit()
        if (folder == null) editor.remove(KEY_OUTPUT_FOLDER) else editor.putString(KEY_OUTPUT_FOLDER, folder.absolutePath)
        return editor.commit()
    }

    fun defaultDpi(context: Context): Int = prefs(context).getInt(KEY_DEFAULT_DPI, DEFAULT_DPI).takeIf { it in DPI_CHOICES } ?: DEFAULT_DPI

    // The KTX edit drops the result of commit, which this answers. zh-CN: KTX 的 edit 丢弃 commit 的结果, 而这里要返回它.
    @SuppressLint("UseKtx")
    fun setDefaultDpi(context: Context, dpi: Int): Boolean {
        require(dpi in DPI_CHOICES)
        return prefs(context).edit().putInt(KEY_DEFAULT_DPI, dpi).commit()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

}

/**
 * The files the toolbox opened or wrote, newest first, at most [MAX] (roadmap P4.1); files that no longer exist are
 * left out when read.
 * zh-CN: 工具箱打开或写出的文件, 最新的在前, 至多 [MAX] 个 (路线图 P4.1); 读取时略去已不存在的文件.
 */
internal object RecentFiles {

    const val FILE = "recent-files"

    private const val KEY_PATHS = "paths"

    const val MAX = 20

    fun read(context: Context): List<File> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_PATHS, "[]"))
        List(array.length()) { File(array.getString(it)) }.filter { it.isFile }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(context: Context, file: File) {
        val paths = (listOf(file) + read(context)).map { it.absolutePath }.distinct().take(MAX)
        prefs(context).edit { putString(KEY_PATHS, JSONArray(paths).toString()) }
    }

    @Synchronized
    fun remove(context: Context, file: File) {
        val paths = read(context).map { it.absolutePath }.filter { it != file.absolutePath }
        prefs(context).edit { putString(KEY_PATHS, JSONArray(paths).toString()) }
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_PATHS) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

}

/**
 * All files access of the toolbox (roadmap D7, P4.1): `MANAGE_EXTERNAL_STORAGE` on Android 11+, the storage runtime
 * permissions before. Script operations never need it.
 * zh-CN: 工具箱的所有文件访问权限 (路线图 D7, P4.1): Android 11 及以上为 `MANAGE_EXTERNAL_STORAGE`, 之前为存储运行时权限. 脚本操作从不需要它.
 */
internal object StorageAccess {

    val legacyPermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun granted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        legacyPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

}

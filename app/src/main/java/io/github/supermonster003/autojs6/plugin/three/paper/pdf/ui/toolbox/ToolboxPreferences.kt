package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.io.File

/**
 * The toolbox settings (roadmap D37, P4.1): the output folder (default `Documents/3-Paper PDF/` of the primary
 * storage) and the default resolution of PDF to images, plus the folders the file browser last picked PDFs and images in
 * (roadmap P6.15 batch 17d). Only the toolbox reads them; scripts choose their own outputs.
 * zh-CN: 工具箱设置 (路线图 D37, P4.1): 输出目录 (默认为主存储的 `Documents/3-Paper PDF/`) 与 PDF 转图片的默认分辨率, 以及文件浏览器上次
 * 选取 PDF 与图片所在的文件夹 (路线图 P6.15 批次 17d). 只有工具箱读取它们; 脚本自行决定输出位置.
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

    /**
     * The folder the file browser last picked PDFs in (the toolbox inputs and "Open PDF" of the home screen share it,
     * roadmap P6.15 batch 17d, appendix D Q324 (a)). zh-CN: 文件浏览器上次选取 PDF 所在的文件夹 (工具箱输入与首页 "打开 PDF" 共用, 路线图 P6.15
     * 批次 17d, 附录 D Q324 (a)).
     */
    const val KEY_PDF_FOLDER = "browsePdfFolder"

    /** The folder the file browser last picked images in (Images to PDF). zh-CN: 文件浏览器上次选取图片所在的文件夹 (图片转 PDF). */
    const val KEY_IMAGE_FOLDER = "browseImageFolder"

    /** The folder remembered under [key] ([KEY_PDF_FOLDER] or [KEY_IMAGE_FOLDER]), null when none. zh-CN: [key] 下记住的文件夹, 没有时为 null. */
    fun browseFolder(context: Context, key: String): File? = prefs(context).getString(key, null)?.takeIf { it.isNotBlank() }?.let(::File)

    fun setBrowseFolder(context: Context, key: String, folder: File) = prefs(context).edit { putString(key, folder.absolutePath) }

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

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Opening and sharing toolbox results (roadmap P4.2, appendix D Q104). A PDF opens in the plugin's viewer by its path;
 * other files go to other apps through the FileProvider: files on the primary shared storage in place, the others
 * (another volume) as copies in the share directory of the cache, made on a background thread.
 * zh-CN: 打开与分享工具箱结果 (路线图 P4.2, 附录 D Q104). PDF 按路径在插件自己的查看器中打开; 其他文件经 FileProvider 交给其他应用:
 * 主共享存储上的文件原地交出, 其余 (其他存储卷) 在后台线程复制到缓存的分享目录后交出.
 */
internal object ToolboxFiles {

    /** The share directory of the viewer (see `viewer_file_paths.xml`). zh-CN: 查看器的分享目录 (见 `viewer_file_paths.xml`). */
    private const val SHARE_DIRECTORY = "viewer-share"

    private const val STALE_MILLIS = 24L * 60 * 60 * 1000

    private const val MIME_PDF = "application/pdf"

    private val main = Handler(Looper.getMainLooper())

    /** The image extensions images to PDF accepts (BitmapFactory). zh-CN: 图片转 PDF 接受的图片扩展名 (BitmapFactory). */
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    fun isPdf(file: File) = file.extension.equals("pdf", ignoreCase = true)

    fun isImage(file: File) = file.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

    fun mimeOf(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
        "pdf" -> MIME_PDF
        "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> "application/octet-stream"
    }

    /** The files of a result path: the file itself, or the files of a folder by name. zh-CN: 结果路径中的文件: 文件本身, 或文件夹中按名称排序的文件. */
    fun filesOf(path: File): List<File> = when {
        path.isFile -> listOf(path)
        path.isDirectory -> path.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
        else -> emptyList()
    }

    /** Opens [file] in the plugin's viewer (same package, so a `file:` URI stays inside). zh-CN: 在插件查看器中打开 [file] (同一应用内, `file:` URI 不外泄). */
    fun openInViewer(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.fromFile(file), MIME_PDF)
            .setComponent(ComponentName(context, ThreePaperPdfViewerActivity::class.java))
        start(context, intent)
    }

    /**
     * Opens a document picked with the system picker in the plugin's viewer, passing the read grant on.
     * zh-CN: 在插件查看器中打开用系统选择器选取的文档, 并转交读取授权.
     */
    fun openUriInViewer(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, MIME_PDF)
            .setComponent(ComponentName(context, ThreePaperPdfViewerActivity::class.java))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, intent)
    }

    /** Opens [file]: a PDF in the viewer, anything else with another app. zh-CN: 打开 [file]: PDF 用查看器, 其他用其他应用. */
    fun open(context: Context, file: File) {
        if (isPdf(file)) {
            openInViewer(context, file)
            return
        }
        uris(context, listOf(file)) { uris ->
            val uri = uris?.singleOrNull() ?: return@uris toast(context, R.string.viewer_share_failed)
            val target = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            start(context, Intent.createChooser(target, context.getString(R.string.viewer_open_with_title)))
        }
    }

    /** Shares [files] (one or more) with another app. zh-CN: 把 [files] (一个或多个) 分享给其他应用. */
    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return toast(context, R.string.viewer_share_failed)
        uris(context, files) { uris ->
            if (uris == null) return@uris toast(context, R.string.viewer_share_failed)
            val types = files.map(::mimeOf).distinct()
            val type = types.singleOrNull() ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
            val target = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            target.clipData = ClipData.newRawUri(files.first().name, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            start(context, Intent.createChooser(target, context.getString(R.string.viewer_share_title)))
        }
    }

    /** Content URIs of [files] on the main thread, or null when one cannot be handed over. zh-CN: 在主线程给出 [files] 的内容 URI; 有文件无法交出时为 null. */
    private fun uris(context: Context, files: List<File>, done: (List<Uri>?) -> Unit) {
        val app = context.applicationContext
        val direct = files.map { file -> runCatching { FileProvider.getUriForFile(app, ThreePaperPdfViewerActivity.authorityOf(app), file) }.getOrNull() }
        if (direct.all { it != null }) return done(direct.filterNotNull())
        Thread({
            val copied = try {
                val dir = File(app.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
                val cutoff = System.currentTimeMillis() - STALE_MILLIS
                dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
                val batch = File(dir, UUID.randomUUID().toString()).apply { mkdirs() }
                files.mapIndexed { index, file ->
                    direct[index] ?: run {
                        val copy = File(batch, file.name)
                        file.copyTo(copy, overwrite = true)
                        FileProvider.getUriForFile(app, ThreePaperPdfViewerActivity.authorityOf(app), copy)
                    }
                }
            } catch (e: Exception) {
                null
            }
            main.post { done(copied) }
        }, "3-paper-share").start()
    }

    private fun start(context: Context, intent: Intent) {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, R.string.viewer_no_app)
        }
    }

    private fun toast(context: Context, message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}

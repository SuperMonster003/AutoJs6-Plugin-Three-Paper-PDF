package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.StandaloneActivity
import org.autojs.plugin.pdf.api.PdfActions
import java.io.File
import java.io.FileNotFoundException

/**
 * What an intent asks the viewer to show: [key] identifies it across Activity recreation, [build] creates the request
 * (claiming a session token at most once) and [missing] is the message when it answers null.
 * zh-CN: intent 要查看器显示的内容: [key] 在 Activity 重建后仍标识它, [build] 创建请求 (会话令牌至多认领一次), 它返回 null 时显示
 * [missing].
 */
internal class ViewerEntry(val key: String, @param:StringRes val missing: Int, val build: () -> ViewerRequest?)

/**
 * The viewer (roadmap 5.5, D21, D35, P3.1). Every entry shares this Activity: `org.autojs.plugin.PDF_VIEWER_OPEN`
 * from the host with a session token (claimed from [ViewerSessions]), `ACTION_VIEW` of a `content:` or `file:` PDF from
 * the launcher, other apps and the toolbox result page, and the file manager's primary action (a subclass that
 * overrides [entryOf]). A new intent replaces the document on screen; share and open with hand a copy to other apps
 * through the FileProvider. Like the other standalone screens it follows the plugin's appearance settings (roadmap
 * P4.1): language, night mode and theme color.
 * zh-CN: 查看器 (路线图 5.5, D21, D35, P3.1). 所有入口共用此 Activity: 宿主带会话令牌发来的 `org.autojs.plugin.PDF_VIEWER_OPEN`
 * (从 [ViewerSessions] 认领), 启动器, 其他应用与工具箱结果页发来的 `content:` 或 `file:` PDF 的 `ACTION_VIEW`, 以及文件管理器的主
 * 操作 (重写 [entryOf] 的子类). 新的 intent 替换屏幕上的文档; 分享与用其他应用打开通过 FileProvider 把副本交给其他应用. 与其他独立界面一样遵循插件的外观设置 (路线图 P4.1): 语言, 夜间模式与主题色.
 */
open class ThreePaperPdfViewerActivity : StandaloneActivity() {

    private val model: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show(intent)
        model.attach(::finish)
    }

    @Composable
    override fun Content() {
        ViewerScreen(
            model = model,
            onClose = ::finish,
            onShare = { handOff(send = true) },
            onOpenWith = { handOff(send = false) },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    override fun onResume() {
        super.onResume()
        model.onResumed(true)
    }

    override fun onPause() {
        model.onResumed(false)
        super.onPause()
    }

    override fun onDestroy() {
        model.attach(null)
        super.onDestroy()
    }

    /**
     * The entry of [intent]; subclasses for other entries override it.
     * zh-CN: [intent] 的入口; 其他入口的子类重写它.
     */
    internal open fun entryOf(intent: Intent): ViewerEntry {
        if (intent.action == PdfActions.VIEWER_ACTIVITY_ACTION) {
            val token = intent.getStringExtra(PdfActions.EXTRA_SESSION_TOKEN)
            val key = ViewerViewModel.SESSION_KEY_PREFIX + token
            return ViewerEntry(key, R.string.viewer_error_expired) {
                ViewerSessions.claim(token, model)?.let { claimed ->
                    val claim = claimed.claim
                    ViewerRequest(
                        key = key,
                        open = { claim.source },
                        name = { claim.displayName },
                        password = claim.password,
                        page = claimed.session.startPage,
                        keyword = claim.keyword,
                        session = claimed.session,
                    )
                }
            }
        }
        val uri = intent.data
        return ViewerEntry("uri:$uri", R.string.viewer_error_no_source) { uri?.let { requestOf(it) } }
    }

    private fun show(intent: Intent) {
        val entry = entryOf(intent)
        model.start(entry.key, entry.missing, entry.build)
    }

    private fun handOff(send: Boolean) {
        model.exportCopy { file ->
            if (isFinishing || isDestroyed) return@exportCopy
            if (file == null) {
                toast(R.string.viewer_share_failed)
                return@exportCopy
            }
            val uri = try {
                FileProvider.getUriForFile(this, authorityOf(this), file)
            } catch (_: IllegalArgumentException) {
                toast(R.string.viewer_share_failed)
                return@exportCopy
            }
            val target = if (send) {
                Intent(Intent.ACTION_SEND).setType(MIME_PDF).putExtra(Intent.EXTRA_STREAM, uri).also { it.clipData = ClipData.newRawUri(file.name, uri) }
            } else {
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME_PDF)
            }
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(target, getString(if (send) R.string.viewer_share_title else R.string.viewer_open_with_title))
            if (!send) {
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, ThreePaperPdfViewerActivity::class.java)))
            }
            try {
                startActivity(chooser)
            } catch (_: ActivityNotFoundException) {
                toast(R.string.viewer_no_app)
            }
        }
    }

    private fun toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {

        const val MIME_PDF = "application/pdf"

        /** The FileProvider of shared copies (see the Manifest). zh-CN: 分享副本的 FileProvider (见 Manifest). */
        fun authorityOf(context: Context) = "${context.packageName}.viewer.files"

        /**
         * A request for [uri]: a `content:` URI through the content resolver (the name from `OpenableColumns`), a
         * `file:` URI by its path.
         * zh-CN: [uri] 的请求: `content:` URI 经内容解析器打开 (名称取自 `OpenableColumns`), `file:` URI 按路径打开.
         */
        internal fun requestOf(uri: Uri): ViewerRequest? = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> ViewerRequest(
                key = "uri:$uri",
                open = { context ->
                    context.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())
                },
                name = { context -> displayNameOf(context, uri) },
            )
            ContentResolver.SCHEME_FILE -> uri.path?.let { path ->
                ViewerRequest(
                    key = "uri:$uri",
                    open = { ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) },
                    name = { File(path).name },
                )
            }
            else -> null
        }

        internal fun displayNameOf(context: Context, uri: Uri): String? {
            val queried = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                }
            }.getOrNull()
            return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/')
        }

    }

}

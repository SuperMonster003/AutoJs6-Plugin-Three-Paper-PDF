package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Intent
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerEntry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerRequest

/**
 * The file manager's primary action (roadmap D36, appendix D Q75): tapping a PDF in the AutoJs6 file manager opens it
 * here, in the plugin viewer. Only the host may start it (`org.autojs.permission.PLUGIN`); it reads the first selected
 * file through the URI the host granted and shows the name the file manager shows.
 * zh-CN: 文件管理器的主操作 (路线图 D36, 附录 D Q75): 在 AutoJs6 文件管理器中点按 PDF 时在此, 即插件查看器中打开. 只有宿主可以启动
 * 它 (`org.autojs.permission.PLUGIN`); 它经宿主授权的 URI 读取第一个选中的文件, 并显示文件管理器显示的名称.
 */
class ExplorerPdfViewerActivity : ThreePaperPdfViewerActivity() {

    override fun entryOf(intent: Intent): ViewerEntry {
        val target = ToolRequest.of(intent)?.targets?.firstOrNull()
        val uri = target?.uri ?: intent.data
        val key = "explorer:$uri"
        return ViewerEntry(key, R.string.viewer_error_no_source) {
            uri?.let { requestOf(it) }?.let { request ->
                val name = target?.displayName
                ViewerRequest(
                    key = key,
                    open = request.open,
                    name = { context -> name ?: request.name(context) },
                )
            }
        }
    }

}

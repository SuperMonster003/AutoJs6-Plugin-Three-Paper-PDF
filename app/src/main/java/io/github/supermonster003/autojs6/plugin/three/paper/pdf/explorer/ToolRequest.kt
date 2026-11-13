package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.explorer.api.IExplorerActionHostSession

/** One selected file: its readable URI and the name the file manager shows. zh-CN: 一个选中的文件: 可读的 URI 与文件管理器显示的名称. */
internal class ToolTarget(val uri: Uri, val displayName: String)

/**
 * What the file manager asked for (`org.autojs.plugin.EXPLORER_ACTION_EXECUTE`, protocol 28): the tool, the selected
 * files in selection order (at most `MAX_TARGETS_PER_REQUEST`), the folder they are in and the host session that
 * creates the results there. Only URIs the host granted (the intent data and its clip) are used; a target without a
 * name gets the last segment of its URI.
 * zh-CN: 文件管理器的请求 (`org.autojs.plugin.EXPLORER_ACTION_EXECUTE`, 协议 28): 工具, 按选择顺序排列的选中文件 (至多
 * `MAX_TARGETS_PER_REQUEST` 个), 它们所在的文件夹, 以及在那里创建结果的宿主会话. 只使用宿主授权的 URI (intent 的 data 与其 clip);
 * 没有名称的目标取其 URI 的最后一段.
 */
internal class ToolRequest(
    val tool: ExplorerTool,
    val targets: List<ToolTarget>,
    val parentDisplayPath: String?,
    val session: IBinder?,
) {

    companion object {

        /** The request of [intent], or null when it names no known tool or no granted file. zh-CN: [intent] 的请求; 未指明已知工具或没有已授权文件时为 null. */
        fun of(intent: Intent): ToolRequest? {
            val tool = ExplorerTool.of(intent.getStringExtra(ExplorerActionIntentExtras.ACTION_ID)) ?: return null
            val granted = grantedUris(intent)
            val described = IntentCompat.getParcelableArrayListExtra(intent, ExplorerActionIntentExtras.TARGETS, Bundle::class.java).orEmpty()
            val targets = if (described.isNotEmpty()) {
                described.mapNotNull { bundle ->
                    val uri = BundleCompat.getParcelable(bundle, ExplorerActionTargetKeys.URI, Uri::class.java)?.takeIf { it in granted }
                    uri?.let { ToolTarget(it, nameOf(it, bundle.getString(ExplorerActionTargetKeys.DISPLAY_NAME))) }
                }
            } else {
                // Before protocol 4 the request names one file. zh-CN: 协议 4 之前请求只指明一个文件.
                listOfNotNull(intent.data?.let { ToolTarget(it, nameOf(it, intent.getStringExtra(ExplorerActionIntentExtras.DISPLAY_NAME))) })
            }
            if (targets.isEmpty()) return null
            val session = IntentCompat.getParcelableExtra(intent, ExplorerActionIntentExtras.HOST_SESSION, Bundle::class.java)
                ?.getBinder(ExplorerActionHostSessionKeys.BINDER)
            return ToolRequest(
                tool = tool,
                targets = targets.distinctBy { it.uri }.take(ExplorerActionProtocol.MAX_TARGETS_PER_REQUEST),
                parentDisplayPath = intent.getStringExtra(ExplorerActionIntentExtras.PARENT_DISPLAY_PATH)?.takeIf { it.isNotBlank() },
                session = session,
            )
        }

        /**
         * The host session of [binder] when it really is one (the descriptor check of 3-Stack), else null.
         * zh-CN: [binder] 确实是宿主会话时返回之 (与 3-Stack 相同的描述符检查), 否则为 null.
         */
        fun hostSessionOf(binder: IBinder?): IExplorerActionHostSession? {
            if (binder == null) return null
            val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
            if (descriptor != IExplorerActionHostSession.DESCRIPTOR) return null
            return IExplorerActionHostSession.Stub.asInterface(binder)
        }

        private fun grantedUris(intent: Intent): Set<Uri> {
            val uris = LinkedHashSet<Uri>()
            intent.data?.let(uris::add)
            val clip = intent.clipData
            if (clip != null) {
                for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
            }
            return uris
        }

        private fun nameOf(uri: Uri, given: String?): String =
            given?.trim()?.takeIf { it.isNotEmpty() } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: "document.pdf"

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.os.ParcelFileDescriptor

/**
 * What a viewer session hands the Activity that claims it (protocol "Viewer Sessions"): the source duplicated by the
 * plugin, the display name, the password, the start page (from 0) and the keyword to search for.
 * zh-CN: 查看器会话交给认领它的 Activity 的内容 (协议 "Viewer Sessions" 一节): 插件复制的源, 显示名称, 密码, 起始页 (从 0 起) 与
 * 要搜索的关键词.
 */
internal class ViewerClaim(
    val token: String,
    val source: ParcelFileDescriptor,
    val displayName: String?,
    val password: String?,
    val page: Int,
    val keyword: String?,
)

/**
 * The viewer sessions of the plugin process, keyed by their random token (protocol "Viewer Sessions"). `openViewer`
 * registers one and the viewer Activity claims it once by the token in its intent; an unknown or already claimed token
 * answers null, which the Activity shows as an expired request.
 * zh-CN: 插件进程中的查看器会话, 以随机令牌为键 (协议 "Viewer Sessions" 一节). `openViewer` 登记会话, 查看器 Activity 用 intent 中的
 * 令牌认领一次; 未知或已被认领的令牌返回 null, Activity 将其显示为请求已过期.
 */
internal object ViewerSessions {

    private val pending = HashMap<String, ViewerClaim>()

    fun register(claim: ViewerClaim) = synchronized(pending) {
        pending.put(claim.token, claim)?.let { runCatching { it.source.close() } }
    }

    /** The session of [token], removed from the registry; null when unknown or claimed. zh-CN: [token] 的会话并从登记表移除; 未知或已认领时为 null. */
    fun claim(token: String?): ViewerClaim? = if (token == null) null else synchronized(pending) { pending.remove(token) }

}

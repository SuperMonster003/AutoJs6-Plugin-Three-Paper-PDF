package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfActions
import org.autojs.plugin.pdf.api.PdfErrorCodes

/**
 * Who may call a PDF service entry and its handles (roadmap D43, D44, as the 3-Tally Sheet `CallerGuard`). The signed
 * entry relies on its manifest permission `org.autojs.permission.PLUGIN`. The packaged entry serves any app holding the
 * normal permission `org.autojs.plugin.pdf.permission.RUNTIME`, checked again on every call ([checkEntry]) because a
 * Binder object can be handed on; calls from the plugin's own uid (the file manager tools, the instrumentation tests)
 * pass. Every document, viewer session and operation answers only the uid that created it ([checkOwner]).
 * zh-CN: 谁可以调用 PDF 服务入口及其句柄 (路线图 D43, D44, 同 3-Tally Sheet 的 `CallerGuard`). 签名入口依靠其清单权限
 * `org.autojs.permission.PLUGIN`. 打包入口服务任何持有普通权限 `org.autojs.plugin.pdf.permission.RUNTIME` 的应用, 由于 Binder
 * 对象可以被转交, 每次调用都再次检查 ([checkEntry]); 来自插件自身 uid 的调用 (文件管理器工具, 仪器测试) 放行. 每个文档,
 * 查看器会话与操作只响应创建它的 uid ([checkOwner]).
 */
internal class PdfCallerGuard(private val context: Context, val packaged: Boolean) {

    /**
     * Must run on the Binder thread of the call, before any dispatch, so the calling uid is the caller's.
     * zh-CN: 必须在该调用的 Binder 线程上, 在任何分派之前运行, 这样调用方 uid 才是调用方的.
     */
    fun checkEntry() {
        val uid = Binder.getCallingUid()
        entryRefusal(packaged, uid, Process.myUid()) {
            context.checkPermission(PdfActions.PACKAGED_PERMISSION, Binder.getCallingPid(), uid) == PackageManager.PERMISSION_GRANTED
        }?.let { throw it.toBinderException() }
    }

    companion object {

        /**
         * The refusal of a call through an entry, or null when it may proceed: the signed entry and the plugin's own
         * uid always pass, other callers of the packaged entry need [hasPermission].
         * zh-CN: 经某一入口的调用的拒绝原因, 可以继续时为 null: 签名入口与插件自身 uid 总是放行, 打包入口的其他调用方需要
         * [hasPermission].
         */
        fun entryRefusal(packaged: Boolean, callingUid: Int, ownUid: Int, hasPermission: () -> Boolean): PdfFailure? = when {
            !packaged || callingUid == ownUid -> null
            hasPermission() -> null
            else -> PdfFailure(PdfErrorCodes.PERMISSION_DENIED, "the caller lacks ${PdfActions.PACKAGED_PERMISSION}")
        }

        /**
         * The refusal of a handle call from [callingUid] when [ownerUid] created the handle, or null.
         * zh-CN: [ownerUid] 创建的句柄收到 [callingUid] 的调用时的拒绝原因, 或 null.
         */
        fun ownerRefusal(ownerUid: Int, callingUid: Int): PdfFailure? =
            if (callingUid == ownerUid) null else PdfFailure(PdfErrorCodes.PERMISSION_DENIED, "the handle belongs to another caller")

        /**
         * Throws the refusal of a contract transaction ([code] between `FIRST_CALL_TRANSACTION` and
         * `LAST_CALL_TRANSACTION`) on a handle of [ownerUid] from another uid, as the `CODE: detail` exception of the
         * protocol; framework transactions (interface, ping, dump) pass.
         * zh-CN: 其他 uid 对 [ownerUid] 的句柄发起契约事务 ([code] 介于 `FIRST_CALL_TRANSACTION` 与 `LAST_CALL_TRANSACTION`)
         * 时, 以协议的 `CODE: detail` 异常抛出拒绝; 框架事务 (接口, ping, dump) 放行.
         */
        fun checkOwner(code: Int, ownerUid: Int) {
            if (code !in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) return
            ownerRefusal(ownerUid, Binder.getCallingUid())?.let { throw it.toBinderException() }
        }

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerSessions

/**
 * Answers `org.autojs.plugin.PDF` (category `pdf`), guarded by `org.autojs.permission.PLUGIN` (roadmap P2.1), with
 * [PdfPluginBinder]: identity and capabilities, `open` through the [PdfEngine] document registry (at most
 * `MAX_DOCUMENTS_PER_HOST` per host process), `run` ([RunOperations]), `cancel` of asynchronous operations and
 * `openViewer` ([ViewerSessions], P3.2). Operations not implemented yet answer `UNSUPPORTED` with the detail
 * `NOT_IMPLEMENTED: <op>`. The host holds one binding per open document, run and viewer, so when the last binding goes
 * ([onUnbind]) every document of this entry is closed and its temp files removed; viewer sessions end through the
 * viewer, their callback or the claim timeout. [ThreePaperPdfPackagedService] is the packaged-app entry; both share one
 * engine per process ([SharedPdfEngine], roadmap D44).
 * zh-CN: 响应 `org.autojs.plugin.PDF` (category `pdf`), 受 `org.autojs.permission.PLUGIN` 保护 (路线图 P2.1), 由
 * [PdfPluginBinder] 作答: 身份与能力, 经 [PdfEngine] 文档登记表的 `open` (每个宿主进程至多 `MAX_DOCUMENTS_PER_HOST` 个),
 * `run` ([RunOperations]), 异步操作的 `cancel`, 以及 `openViewer` ([ViewerSessions], P3.2). 尚未实现的操作以 `UNSUPPORTED`
 * 与详情 `NOT_IMPLEMENTED: <op>` 作答. 宿主为每个打开的文档, 任务与查看器各持有一个绑定, 因此最后一个绑定解除时 ([onUnbind])
 * 关闭本入口的全部文档并删除其临时文件; 查看器会话经查看器, 其回调或认领超时结束. [ThreePaperPdfPackagedService] 是打包应用
 * 入口; 两者共用进程内的一个引擎 ([SharedPdfEngine], 路线图 D44).
 */
open class ThreePaperPdfService : Service() {

    /** True for the packaged-app entry (roadmap D43). zh-CN: 打包应用入口为 true (路线图 D43). */
    protected open val packagedEntry: Boolean = false

    private lateinit var engine: PdfEngine

    private lateinit var binder: PdfPluginBinder

    override fun onCreate() {
        super.onCreate()
        engine = SharedPdfEngine.acquire(applicationContext)
        binder = PdfPluginBinder(this, engine, RunOperations(engine, this), PdfCallerGuard(applicationContext, packagedEntry))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * The last binding of this entry is gone, so no host of this entry holds a document, run or viewer any more; the
     * other entry keeps its callers (roadmap D44).
     * zh-CN: 本入口的最后一个绑定已解除, 因此本入口不再有宿主持有文档, 任务或查看器; 另一入口的调用方不受影响 (路线图 D44).
     */
    override fun onUnbind(intent: Intent?): Boolean {
        engine.closeEntry(packagedEntry)
        return false
    }

    override fun onDestroy() {
        SharedPdfEngine.release(engine)
        super.onDestroy()
    }

}

/**
 * The packaged-app entry (roadmap Q145 (b), D43): `org.autojs.plugin.PDF_PACKAGED` behind the normal permission
 * `org.autojs.plugin.pdf.permission.RUNTIME`, checked again on every call; its callers are keyed apart from the signed
 * entry and its handles answer only their creator uid (D44).
 * zh-CN: 打包应用入口 (路线图 Q145 (b), D43): 受普通权限 `org.autojs.plugin.pdf.permission.RUNTIME` 保护的
 * `org.autojs.plugin.PDF_PACKAGED`, 每次调用再次检查该权限; 其调用方与签名入口分开记录, 其句柄只响应创建者 uid (D44).
 */
class ThreePaperPdfPackagedService : ThreePaperPdfService() {

    override val packagedEntry: Boolean = true

}

/**
 * The engine both service entries share (roadmap D44): created by the first service, so its start-up wipe of
 * `cache/pdf/` never removes the files of the other entry, and shut down when the last service is destroyed. Viewer
 * sessions of callers the engine finds dead are detached.
 * zh-CN: 两个服务入口共用的引擎 (路线图 D44): 由第一个服务创建, 因此其启动时对 `cache/pdf/` 的清空不会删掉另一入口的文件,
 * 最后一个服务销毁时关闭. 引擎判为死亡的调用方的查看器会话被断开.
 */
internal object SharedPdfEngine {

    private var engine: PdfEngine? = null

    private var users = 0

    @Synchronized
    fun acquire(context: Context): PdfEngine {
        users++
        return engine ?: PdfEngine(context.cacheDir, onHostDead = ViewerSessions::detachHost).also { engine = it }
    }

    @Synchronized
    fun release(released: PdfEngine) {
        if (released !== engine) return
        users--
        if (users <= 0) {
            users = 0
            engine = null
            released.shutdown()
        }
    }

}

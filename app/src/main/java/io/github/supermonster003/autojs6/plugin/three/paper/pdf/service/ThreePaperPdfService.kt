package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.app.Service
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
 * ([onUnbind]) every document is closed and its temp files removed; viewer sessions end through the viewer, their
 * callback or the claim timeout.
 * zh-CN: 响应 `org.autojs.plugin.PDF` (category `pdf`), 受 `org.autojs.permission.PLUGIN` 保护 (路线图 P2.1), 由
 * [PdfPluginBinder] 作答: 身份与能力, 经 [PdfEngine] 文档登记表的 `open` (每个宿主进程至多 `MAX_DOCUMENTS_PER_HOST` 个),
 * `run` ([RunOperations]), 异步操作的 `cancel`, 以及 `openViewer` ([ViewerSessions], P3.2). 尚未实现的操作以 `UNSUPPORTED`
 * 与详情 `NOT_IMPLEMENTED: <op>` 作答. 宿主为每个打开的文档, 任务与查看器各持有一个绑定, 因此最后一个绑定解除时 ([onUnbind])
 * 关闭全部文档并删除其临时文件; 查看器会话经查看器, 其回调或认领超时结束.
 */
class ThreePaperPdfService : Service() {

    private lateinit var engine: PdfEngine

    private lateinit var binder: PdfPluginBinder

    override fun onCreate() {
        super.onCreate()
        engine = PdfEngine(cacheDir)
        binder = PdfPluginBinder(this, engine, RunOperations(engine, this))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * The last binding is gone, so no host holds a document, run or viewer any more.
     * zh-CN: 最后一个绑定已解除, 因此不再有宿主持有文档, 任务或查看器.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        engine.closeAll()
        return false
    }

    override fun onDestroy() {
        engine.shutdown()
        super.onDestroy()
    }

}

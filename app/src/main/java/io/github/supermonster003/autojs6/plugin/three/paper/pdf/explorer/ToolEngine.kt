package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Context
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.PdfEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.PdfPluginBinder
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.RunOperations
import org.autojs.plugin.pdf.api.IPdfPlugin
import java.io.File

/**
 * The engine of the file manager tools (roadmap P3.3): one [PdfEngine] for the plugin process, shared by the open tool
 * screens and called in process through [PdfPluginBinder], so the tools take the code paths of the scripts. It does not
 * bind [io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfService]: the service closes
 * every document when its last binding goes, and its temp files stay apart (`<cacheDir>/tools/pdf/`). The engine
 * starts with the first [acquire] and shuts down with the last [release].
 * zh-CN: 文件管理器工具的引擎 (路线图 P3.3): 插件进程内一个 [PdfEngine], 由打开的工具界面共用, 经 [PdfPluginBinder] 在进程内调用,
 * 因此工具走脚本的代码路径. 不绑定 [io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfService]:
 * 该服务在最后一个绑定解除时关闭全部文档, 两者的临时文件也分开存放 (`<cacheDir>/tools/pdf/`). 第一次 [acquire] 时启动引擎, 最后
 * 一次 [release] 时关闭.
 */
internal object ToolEngine {

    private const val DIRECTORY = "tools"

    private var engine: PdfEngine? = null

    private var plugin: IPdfPlugin? = null

    private var holders = 0

    /** The plugin binder of the shared engine; pair every call with [release]. zh-CN: 共用引擎的插件 Binder; 每次调用都要配对 [release]. */
    @Synchronized
    fun acquire(context: Context): IPdfPlugin {
        val current = plugin ?: run {
            val app = context.applicationContext
            val started = PdfEngine(File(app.cacheDir, DIRECTORY))
            engine = started
            PdfPluginBinder(app, started, RunOperations(started, app)).also { plugin = it }
        }
        holders += 1
        return current
    }

    /** Gives up one [acquire]; the last one shuts the engine down. zh-CN: 归还一次 [acquire]; 最后一次归还时关闭引擎. */
    @Synchronized
    fun release() {
        if (holders == 0) return
        holders -= 1
        if (holders > 0) return
        engine?.shutdown()
        engine = null
        plugin = null
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTaskService

/**
 * The application. PDFBox-Android reads its fonts, glyph lists and color profiles through the asset manager, so the
 * loader is initialized before any service or screen touches a document (roadmap P0.1); the notification channel of
 * the toolbox tasks is created (again, to follow the language) on every start (P4.2).
 * zh-CN: 应用类. PDFBox-Android 经资源管理器读取字体, 字形表与色彩配置, 因此在任何服务或界面接触文档之前先初始化加载器
 * (路线图 P0.1); 每次启动都 (重新) 创建工具箱任务的通知渠道, 使其名称跟随语言 (P4.2).
 */
class ThreePaperPdfApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        ToolTaskService.createChannel(this)
    }

}

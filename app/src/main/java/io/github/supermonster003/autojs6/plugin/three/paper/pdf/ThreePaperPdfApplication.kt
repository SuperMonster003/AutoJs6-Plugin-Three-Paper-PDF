package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * The application. PDFBox-Android reads its fonts, glyph lists and color profiles through the asset manager, so the
 * loader is initialized before any service or screen touches a document (roadmap P0.1). Roadmap P2.1 adds the task
 * runner and the progress notification channel.
 * zh-CN: 应用类. PDFBox-Android 经资源管理器读取字体, 字形表与色彩配置, 因此在任何服务或界面接触文档之前先初始化加载器
 * (路线图 P0.1). 路线图 P2.1 加入任务执行器与进度通知渠道.
 */
class ThreePaperPdfApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }

}

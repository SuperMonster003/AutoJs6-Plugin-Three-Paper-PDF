package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ExplorerCatalog
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.pluginRuntimeFields
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.toPluginInfo
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.explorer.api.IExplorerActionPlugin
import org.autojs.plugin.explorer.api.IExplorerArchiveSession

/**
 * The Explorer Action service of roadmap D36 (`org.autojs.plugin.EXPLORER_ACTION`, engine `explorer-action`): its
 * catalog ([ExplorerCatalog], roadmap P3.3) opens a PDF in the plugin viewer when it is tapped and adds the tools merge,
 * split, to images, extract text, encrypt and decrypt, each started by the host as a protected Activity of the plugin.
 * It opens no archive and describes no file.
 * zh-CN: 路线图 D36 的 Explorer Action 服务 (`org.autojs.plugin.EXPLORER_ACTION`, engine `explorer-action`): 其目录
 * ([ExplorerCatalog], 路线图 P3.3) 让点按 PDF 时用插件查看器打开, 并加入合并, 拆分, 转图片, 提取文本, 加密与解密工具, 每项都由宿主
 * 启动插件的受保护 Activity. 不打开压缩包, 也不描述文件.
 */
class ThreePaperPdfExplorerActionService : Service() {

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IExplorerActionPlugin.Stub() {

        override fun getInfo(): PluginInfo {
            enforcePluginPermission()
            return applicationContext.explorerPluginInfo()
        }

        override fun getActionCatalog(): Bundle {
            enforcePluginPermission()
            return catalog()
        }

        override fun openArchive(source: ParcelFileDescriptor?, request: Bundle?): IExplorerArchiveSession? {
            enforcePluginPermission()
            source?.close()
            return null
        }

        override fun openArchiveV11(source: ParcelFileDescriptor?, request: Bundle?): Bundle? {
            enforcePluginPermission()
            source?.close()
            return null
        }

        override fun getHostFileInfo(source: ParcelFileDescriptor?, request: Bundle?): Bundle? {
            enforcePluginPermission()
            source?.close()
            return null
        }

    }

    private fun enforcePluginPermission() = enforceCallingOrSelfPermission(
        ThreePaperPdfContract.PLUGIN_PERMISSION,
        "3-Paper PDF file menu entries require the AutoJs6 plugin permission",
    )

    internal companion object {

        /** The catalog ([ExplorerCatalog]). zh-CN: 目录 ([ExplorerCatalog]). */
        fun catalog(): Bundle = ExplorerCatalog.catalog()

        private fun capabilities(): Bundle = Bundle().apply {
            putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, ThreePaperPdfContract.REQUIRED_HOST_VERSION)
            putInt(ExplorerActionCapabilityKeys.PROTOCOL_VERSION, ThreePaperPdfContract.EXPLORER_PROTOCOL_VERSION)
        }

        /**
         * The plugin identity with the Explorer Action engine (the AutoJs4 Runtime form).
         * zh-CN: 带 Explorer Action engine 的插件身份 (AutoJs4 Runtime 同款).
         */
        fun Context.explorerPluginInfo(): PluginInfo = pluginRuntimeFields().toPluginInfo().apply {
            engine = ExplorerActionPluginIds.ENGINE
            supportedAbis = emptyArray()
            capabilities = capabilities()
        }

    }

}

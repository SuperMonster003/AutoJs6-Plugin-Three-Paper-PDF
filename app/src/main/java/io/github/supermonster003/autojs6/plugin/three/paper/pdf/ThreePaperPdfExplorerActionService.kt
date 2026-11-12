package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.pluginRuntimeFields
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.toPluginInfo
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.explorer.api.IExplorerActionPlugin
import org.autojs.plugin.explorer.api.IExplorerArchiveSession

/**
 * The Explorer Action service of roadmap D36 (`org.autojs.plugin.EXPLORER_ACTION`, engine `explorer-action`). The
 * skeleton declares an empty catalog, so the file manager shows no entry yet; roadmap P3.3 adds the overflow tools
 * (merge, split, to images, encrypt, decrypt, extract text). It opens no archive and describes no file.
 * zh-CN: 路线图 D36 的 Explorer Action 服务 (`org.autojs.plugin.EXPLORER_ACTION`, engine `explorer-action`). 骨架声明空目录,
 * 文件管理器暂不显示任何菜单项; 路线图 P3.3 加入溢出工具 (合并, 拆分, 转图片, 加密, 解密, 提取文本). 不打开压缩包, 也不描述文件.
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

        /**
         * The catalog: the protocol version and no action until roadmap P3.3.
         * zh-CN: 目录: 协议版本, 路线图 P3.3 之前没有动作.
         */
        fun catalog(): Bundle = Bundle().apply {
            putInt(ExplorerActionCatalogKeys.PROTOCOL_VERSION, ThreePaperPdfContract.EXPLORER_PROTOCOL_VERSION)
            putParcelableArrayList(ExplorerActionCatalogKeys.ACTIONS, ArrayList<Bundle>())
        }

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

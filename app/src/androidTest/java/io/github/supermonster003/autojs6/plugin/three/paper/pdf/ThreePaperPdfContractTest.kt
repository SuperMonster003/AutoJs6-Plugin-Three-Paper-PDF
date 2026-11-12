package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfInfoService
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.ThreePaperPdfService
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.explorer.api.IExplorerActionPlugin
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfCapabilityKeys
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The host-facing activation and discovery contract against the installed APK (roadmap P0.3): the Wake activity, the
 * launcher aliases, the viewer entries, discovery of exactly one service per action and category, explicit binding with
 * the expected Binder descriptors, `getInfo()` marshalled through a real Parcel with the transaction code the host uses,
 * and the skeleton answers of `IPdfPlugin` (capabilities, `UNSUPPORTED: NOT_IMPLEMENTED` for every operation until P2).
 *
 * The services run in the plugin's own process, so the calls below use `transact` with explicit Parcels or a
 * [MarshallingBinder] instead of plain `asInterface`, which would return the local object and skip marshalling.
 *
 * zh-CN: 针对已安装 APK 验证面向宿主的激活与发现契约 (路线图 P0.3): Wake Activity, 启动器别名, 阅读器入口, 每组 action 与
 * category 恰好发现一个服务, 显式绑定与 Binder 描述符, 以宿主使用的事务号经真实 Parcel 编组的 `getInfo()`, 以及 `IPdfPlugin`
 * 骨架的应答 (能力; P2 之前每个操作都返回 `UNSUPPORTED: NOT_IMPLEMENTED`). 服务与测试同进程, 因此下文用显式 Parcel 调用
 * `transact` 或经 [MarshallingBinder], 不直接用会返回本地对象而跳过编组的 `asInterface`.
 */
@RunWith(AndroidJUnit4::class)
class ThreePaperPdfContractTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val packageName: String
        get() = context.packageName

    @Test
    fun wakeActivityFollowsTheHostActivationContract() {
        val metaData = requireNotNull(context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData)
        assertEquals(".WakeActivity", metaData.getString("org.autojs.plugin.WAKE_ACTIVITY"))
        assertEquals(context.getString(R.string.plugin_author), metaData.getString("org.autojs.plugin.info.AUTHOR"))
        assertEquals(16384, metaData.getInt(PluginCapabilityKeys.NATIVE_PAGE_ALIGNMENT))

        val component = ComponentName(packageName, "$packageName.WakeActivity")
        val activityInfo = context.packageManager.getActivityInfo(component, 0)
        assertTrue(activityInfo.exported)
        assertTrue(activityInfo.enabled)
        assertEquals(ThreePaperPdfContract.PLUGIN_PERMISSION, activityInfo.permission)
        assertEquals(android.R.style.Theme_NoDisplay, activityInfo.theme)
        assertTrue(activityInfo.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        assertTrue(activityInfo.flags and ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH != 0)

        val matches = queryActivities(Intent("org.autojs.plugin.action.WAKE").addCategory(Intent.CATEGORY_DEFAULT).setPackage(packageName))
        assertEquals("the WAKE action resolves exactly one activity", listOf(component.className), matches)
    }

    @Test
    fun launcherResolvesExactlyOneIconAlias() {
        // Roadmap D24, P0.2: exactly one of the four aliases is enabled; a fresh install enables the automatic one.
        // zh-CN: 路线图 D24, P0.2: 四个别名中恰好启用一个; 全新安装启用自动模式.
        val aliases = listOf("AdaptiveAutoIconAlias", "AdaptiveLightIconAlias", "AdaptiveDarkIconAlias", "TransparentIconAlias")
            .map { "$packageName.launcher.$it" }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val matches = queryActivities(launcher)
        assertEquals("one launcher entry: $matches", 1, matches.size)
        assertTrue(matches.single() in aliases)
    }

    @Test
    fun viewerAcceptsTheHostActionAndPdfFiles() {
        val viewer = ThreePaperPdfViewerActivity::class.java.name
        val hostOpen = Intent(ThreePaperPdfContract.VIEWER_ACTION).addCategory(Intent.CATEGORY_DEFAULT).setPackage(packageName)
        assertEquals(listOf(viewer), queryActivities(hostOpen))
        for (scheme in listOf("content", "file")) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("$scheme://example/document.pdf"), ThreePaperPdfContract.PDF_MIME_TYPE)
                .setPackage(packageName)
            assertEquals(scheme, listOf(viewer), queryActivities(view))
        }
        val activityInfo = context.packageManager.getActivityInfo(ComponentName(packageName, viewer), 0)
        assertTrue(activityInfo.exported)
        // Other apps open PDFs with it, so it cannot require the plugin permission (roadmap 5.5).
        // zh-CN: 其他应用会用它打开 PDF, 因此不能要求插件权限 (路线图 5.5 节).
        assertNull(activityInfo.permission)
    }

    @Test
    fun infoServiceAnswersThePdfIdentity() {
        val serviceInfo = discoverSingleService(
            ThreePaperPdfContract.INFO_ACTION, ThreePaperPdfContract.INFO_CATEGORY, ThreePaperPdfInfoService::class.java.name,
        )
        assertRequiresHostVersion(serviceInfo)
        withBoundService(serviceInfo) { binder ->
            assertEquals(IPluginInfoProvider.DESCRIPTOR, binder.interfaceDescriptor)
            val info = transactPluginInfo(binder)
            assertIdentity(info)
            assertStaticCapabilities(requireNotNull(info.capabilities))
        }
    }

    @Test
    fun pdfServiceAnswersTheContract() {
        withPdfPlugin { plugin ->
            assertIdentity(plugin.info)
            val capabilities = plugin.capabilities
            assertEquals(ThreePaperPdfContract.CONTRACT_VERSION, capabilities.getInt(PdfContract.KEY_CONTRACT_VERSION))
            assertEquals(STATIC_CAPABILITY_KEYS + PdfContract.KEY_CONTRACT_VERSION, capabilities.keySet())
            assertStaticCapabilities(capabilities)
        }
    }

    @Test
    fun pdfServiceRejectsEveryOperationUntilTheEngineLands() {
        withPdfPlugin { plugin ->
            assertNotImplemented(PdfOps.INFO) { sampleDescriptor().use { plugin.open(it, Bundle()) } }
            assertNotImplemented(PdfOps.OPEN_VIEWER) { sampleDescriptor().use { plugin.openViewer(it, Bundle(), null) } }
            (PdfOps.RUN_OPS - ThreePaperPdfContract.IMPLEMENTED_OPS.toSet()).forEach { op ->
                val request = Bundle().apply {
                    putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION)
                    putString(PdfContract.KEY_OP, op)
                }
                assertNotImplemented(op) { sampleDescriptor().use { plugin.run(request, arrayOf(it), null, null) } }
            }
            // Nothing runs yet, so every operation id is unknown. zh-CN: 尚无任务运行, 因此任何操作 id 都是未知的.
            assertFalse(plugin.cancel(1))
        }
    }

    @Test
    fun explorerActionServiceAnswersAnEmptyCatalog() {
        val serviceInfo = discoverSingleService(
            ThreePaperPdfContract.EXPLORER_ACTION, Intent.CATEGORY_DEFAULT, ThreePaperPdfExplorerActionService::class.java.name,
        )
        assertRequiresHostVersion(serviceInfo)
        withBoundService(serviceInfo) { binder ->
            assertEquals(IExplorerActionPlugin.DESCRIPTOR, binder.interfaceDescriptor)
            val plugin = IExplorerActionPlugin.Stub.asInterface(MarshallingBinder(binder))
            // The service checks the plugin permission itself; the test process holds it only when a host with the same
            // signature defines it (the CI emulator has no host). zh-CN: 服务自行检查插件权限; 只有安装了同签名宿主 (定义该权限)
            // 时测试进程才持有它 (CI 模拟器没有宿主).
            if (context.checkSelfPermission(ThreePaperPdfContract.PLUGIN_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                val info = plugin.info
                assertEquals(ThreePaperPdfContract.PLUGIN_ID, info.id)
                assertEquals(ExplorerActionPluginIds.ENGINE, info.engine)
                val catalog = plugin.actionCatalog
                assertEquals(ThreePaperPdfContract.EXPLORER_PROTOCOL_VERSION, catalog.getInt(ExplorerActionCatalogKeys.PROTOCOL_VERSION))
                @Suppress("DEPRECATION")
                assertEquals(0, catalog.getParcelableArrayList<Bundle>(ExplorerActionCatalogKeys.ACTIONS)?.size)
            } else {
                assertThrows(SecurityException::class.java) { plugin.actionCatalog }
            }
        }
    }

    private fun assertNotImplemented(op: String, call: () -> Unit) {
        val error = assertThrows(IllegalStateException::class.java) { call() }
        assertEquals(PdfErrorCodes.UNSUPPORTED to "${ThreePaperPdfContract.NOT_IMPLEMENTED}: $op", PdfErrorCodes.decode(error.message))
    }

    /** A real file descriptor, which the skeleton must accept and close. zh-CN: 真实文件描述符, 骨架须接收并关闭. */
    private fun sampleDescriptor(): ParcelFileDescriptor {
        val file = context.cacheDir.resolve("contract-sample.pdf")
        if (!file.exists()) {
            InstrumentationRegistry.getInstrumentation().context.assets.open("text.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun withPdfPlugin(block: (IPdfPlugin) -> Unit) {
        val serviceInfo = discoverSingleService(
            ThreePaperPdfContract.SERVICE_ACTION, ThreePaperPdfContract.SERVICE_CATEGORY, ThreePaperPdfService::class.java.name,
        )
        assertRequiresHostVersion(serviceInfo)
        withBoundService(serviceInfo) { binder ->
            assertEquals(IPdfPlugin.DESCRIPTOR, binder.interfaceDescriptor)
            block(IPdfPlugin.Stub.asInterface(MarshallingBinder(binder)))
        }
    }

    private fun assertStaticCapabilities(capabilities: Bundle) {
        assertTrue(capabilities.keySet().containsAll(STATIC_CAPABILITY_KEYS))
        assertEquals(ThreePaperPdfContract.REQUIRED_HOST_VERSION, capabilities.getLong(PdfCapabilityKeys.REQUIRES_HOST_VERSION))
        assertEquals(ThreePaperPdfContract.CONTRACT_VERSION, capabilities.getInt(PdfCapabilityKeys.CONTRACT_VERSION))
        assertArrayEquals(ThreePaperPdfContract.FEATURES.toTypedArray(), capabilities.getStringArray(PdfCapabilityKeys.FEATURES))
    }

    private fun assertRequiresHostVersion(serviceInfo: ServiceInfo) {
        assertEquals(
            ThreePaperPdfContract.REQUIRED_HOST_VERSION.toString(),
            serviceInfo.metaData?.get(PluginCapabilityKeys.REQUIRES_HOST_VERSION)?.toString(),
        )
    }

    private fun assertIdentity(info: PluginInfo) {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        assertEquals("3-Paper PDF", info.name)
        assertEquals(context.getString(R.string.plugin_name), info.name)
        assertEquals(context.getString(R.string.plugin_description), info.description)
        assertEquals(ThreePaperPdfContract.INSTRUCTION_REFERENCE, info.instruction)
        assertTrue("the instruction resource exists", context.resources.getIdentifier("plugin_instruction", "raw", packageName) != 0)
        assertEquals("SuperMonster003", info.author)
        assertEquals(ThreePaperPdfContract.PLUGIN_ID, info.id)
        assertEquals(context.getString(R.string.plugin_id), info.id)
        assertEquals(ThreePaperPdfContract.ENGINE, info.engine)
        assertEquals(ThreePaperPdfContract.VARIANT, info.variant)
        assertEquals(packageInfo.versionName, info.versionName)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        assertEquals(versionCode, info.versionCode)
        assertEquals(context.getString(R.string.plugin_version_date), info.versionDate)
        // One universal APK, so an explicit empty array (never null). zh-CN: 单一 universal APK, 因此是显式空数组 (不为 null).
        assertArrayEquals(emptyArray<String>(), requireNotNull(info.supportedAbis))
    }

    /** `getInfo()` is the first transaction of IPluginInfoProvider. zh-CN: `getInfo()` 是 IPluginInfoProvider 的第一个事务. */
    private fun transactPluginInfo(binder: IBinder): PluginInfo {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(IPluginInfoProvider.DESCRIPTOR)
            assertTrue("getInfo() transaction must be handled", binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0))
            reply.readException()
            assertTrue("getInfo() must not return null", reply.readInt() != 0)
            return pluginInfoCreator.createFromParcel(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun queryActivities(intent: Intent): List<String> {
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }

    private fun discoverSingleService(action: String, category: String, expectedClassName: String): ServiceInfo {
        val intent = Intent(action).addCategory(category).setPackage(packageName)
        @Suppress("DEPRECATION")
        val matches = context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        assertEquals("$action / $category must resolve exactly one service: ${matches.map { it.serviceInfo.name }}", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        assertEquals(packageName, serviceInfo.packageName)
        assertEquals(expectedClassName, serviceInfo.name)
        assertTrue("$expectedClassName must be exported", serviceInfo.exported)
        assertTrue("$expectedClassName must be enabled", serviceInfo.enabled)
        assertEquals(ThreePaperPdfContract.PLUGIN_PERMISSION, serviceInfo.permission)
        assertEquals("services run in the plugin's default process", packageName, serviceInfo.processName)
        return serviceInfo
    }

    private fun withBoundService(serviceInfo: ServiceInfo, block: (IBinder) -> Unit) {
        val binderReference = AtomicReference<IBinder>()
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binderReference.set(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit

            override fun onNullBinding(name: ComponentName) {
                connected.countDown()
            }
        }
        val explicit = Intent().setComponent(ComponentName(serviceInfo.packageName, serviceInfo.name))
        assertTrue("bindService returned false", context.bindService(explicit, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("timed out waiting for ${serviceInfo.name}", connected.await(10, TimeUnit.SECONDS))
            block(requireNotNull(binderReference.get()) { "${serviceInfo.name} returned a null binding" })
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {

        val STATIC_CAPABILITY_KEYS = setOf(
            PdfCapabilityKeys.REQUIRES_HOST_VERSION,
            PdfCapabilityKeys.CONTRACT_VERSION,
            PdfCapabilityKeys.FEATURES,
        )

        @Suppress("UNCHECKED_CAST")
        val pluginInfoCreator: Parcelable.Creator<PluginInfo> =
            PluginInfo::class.java.getField("CREATOR").get(null) as Parcelable.Creator<PluginInfo>

    }

}

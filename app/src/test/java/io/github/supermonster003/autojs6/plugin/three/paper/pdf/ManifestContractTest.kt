package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles.android
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles.androidOrNull
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles.child
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.RepositoryFiles.children
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.LauncherIconMode
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps `AndroidManifest.xml` and [ThreePaperPdfContract] from drifting apart: the host discovers the plugin through
 * the Manifest (Wake activity, INFO, PDF and EXPLORER_ACTION services, the viewer, the file manager tools), while the
 * services and the tests use the Kotlin constants (roadmap 5.5, D7, D21, D26, D35, D36).
 * zh-CN: 防止 `AndroidManifest.xml` 与 [ThreePaperPdfContract] 漂移: 宿主经 Manifest 发现插件 (Wake Activity, INFO, PDF 与
 * EXPLORER_ACTION 服务, 查看器, 文件管理器工具), 服务与测试则使用 Kotlin 常量 (路线图 5.5, D7, D21, D26, D35, D36).
 */
class ManifestContractTest {

    private val manifest: Element by lazy { RepositoryFiles.xml("app/src/main/AndroidManifest.xml") }

    private val application: Element get() = manifest.child("application")

    @Test
    fun `the permissions are the plugin permission, storage, the reserved task set and the network`() {
        assertEquals(
            listOf(
                PLUGIN_PERMISSION,
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
            ),
            manifest.children("uses-permission").map { it.android("name") },
        )
        val permissions = manifest.children("uses-permission").associateBy { it.android("name") }
        assertEquals("32", permissions.getValue("android.permission.READ_EXTERNAL_STORAGE").android("maxSdkVersion"))
        assertEquals("29", permissions.getValue("android.permission.WRITE_EXTERNAL_STORAGE").android("maxSdkVersion"))
        assertTrue("no uses-sdk override: SDK levels come from version.properties", manifest.children("uses-sdk").isEmpty())
        assertTrue("no feature requirements", manifest.children("uses-feature").isEmpty())
    }

    @Test
    fun `package queries name the host only`() {
        val queries = manifest.child("queries")
        assertEquals(listOf("org.autojs.autojs6"), queries.children("package").map { it.android("name") })
        assertTrue(queries.children("intent").isEmpty())
    }

    @Test
    fun `the application disables backups and uses the generated title`() {
        assertEquals("false", application.android("allowBackup"))
        assertEquals("false", application.android("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.android("dataExtractionRules"))
        assertEquals("@string/app_name", application.android("label"))
        assertEquals("true", application.android("supportsRtl"))
        assertEquals("true", application.android("requestLegacyExternalStorage"))
        assertEquals(".ThreePaperPdfApplication", application.android("name"))
        // Written by `.python/generate_icon_studio.py` (roadmap D24, P0.2). zh-CN: 由 Icon Studio 生成脚本写入.
        assertEquals("@mipmap/ic_icon_studio_application", application.android("icon"))
        assertEquals("@mipmap/ic_icon_studio_application", application.android("roundIcon"))
        assertNull("16 KB aligned libraries stay uncompressed in the APK (roadmap Q9)", application.androidOrNull("extractNativeLibs"))
    }

    @Test
    fun `application meta-data names the wake activity, the author and the 16 KB page alignment`() {
        val metaData = application.children("meta-data").associate { it.android("name") to it.android("value") }
        assertEquals(
            mapOf(
                PluginCapabilityKeys.NATIVE_PAGE_ALIGNMENT to "16384",
                "org.autojs.plugin.WAKE_ACTIVITY" to ".WakeActivity",
                "org.autojs.plugin.info.AUTHOR" to "@string/plugin_author",
            ),
            metaData,
        )
    }

    @Test
    fun `the wake activity follows the activation contract`() {
        val wake = activity(".WakeActivity")
        assertEquals("true", wake.android("exported"))
        assertEquals("true", wake.android("excludeFromRecents"))
        assertEquals("true", wake.android("finishOnTaskLaunch"))
        assertEquals(PLUGIN_PERMISSION, wake.android("permission"))
        assertEquals("@android:style/Theme.NoDisplay", wake.android("theme"))
        assertFilter(wake.child("intent-filter"), action = "org.autojs.plugin.action.WAKE", categories = listOf(DEFAULT_CATEGORY))
    }

    @Test
    fun `the activities are the wake, home, viewer and file manager tool screens`() {
        assertEquals(
            listOf(
                ".WakeActivity",
                ".ui.home.HomeActivity",
                ".viewer.ThreePaperPdfViewerActivity",
                ".explorer.ExplorerPdfViewerActivity",
                ".explorer.PdfToolActivity",
            ),
            application.children("activity").map { it.android("name") },
        )
        val home = activity(".ui.home.HomeActivity")
        assertEquals("only the aliases are exported to the launcher", "false", home.android("exported"))
        assertEquals("singleTask", home.android("launchMode"))
        assertTrue("the launcher reaches the home screen through the aliases only", home.children("intent-filter").isEmpty())
    }

    @Test
    fun `four launcher aliases point at the home screen and only the automatic one is enabled`() {
        val aliases = application.children("activity-alias")
        assertEquals(
            listOf(
                ".launcher.AdaptiveLightIconAlias" to "@mipmap/ic_launcher_system_light",
                ".launcher.AdaptiveDarkIconAlias" to "@mipmap/ic_launcher_system",
                ".launcher.AdaptiveAutoIconAlias" to "@mipmap/ic_launcher_system_auto",
                ".launcher.TransparentIconAlias" to "@mipmap/ic_launcher",
            ),
            aliases.map { it.android("name") to it.android("icon") },
        )
        aliases.forEach { alias ->
            assertEquals(".ui.home.HomeActivity", alias.android("targetActivity"))
            assertEquals(alias.android("icon"), alias.android("roundIcon"))
            assertEquals("@string/app_name", alias.android("label"))
            assertEquals("true", alias.android("exported"))
            // The launcher must start them, so the aliases carry no permission. zh-CN: 启动器要能启动它们, 不带权限.
            assertNull(alias.androidOrNull("permission"))
            assertEquals(listOf("android.intent.action.MAIN"), alias.child("intent-filter").children("action").map { it.android("name") })
            assertEquals(listOf(LAUNCHER_CATEGORY), alias.child("intent-filter").children("category").map { it.android("name") })
        }
        assertEquals(listOf(".launcher.AdaptiveAutoIconAlias"), aliases.filter { it.android("enabled") == "true" }.map { it.android("name") })
        assertEquals(
            "the aliases and LauncherIcons name the same components",
            aliases.map { it.android("name").removePrefix(".launcher.") },
            LauncherIconMode.entries.sortedBy { ALIAS_ORDER.indexOf(it) }.map { it.alias },
        )
    }

    @Test
    fun `the viewer accepts the host action and PDF files from other apps`() {
        val viewer = activity(".viewer.ThreePaperPdfViewerActivity")
        assertEquals("true", viewer.android("exported"))
        // Other apps open PDF files with ACTION_VIEW, so the viewer cannot require the plugin permission; the session
        // token of roadmap P3.2 guards the script channel instead.
        assertNull(viewer.androidOrNull("permission"))
        // A new request (a later session of the host, another file) replaces the document on screen (roadmap P3.1, P3.2).
        assertEquals("singleTop", viewer.android("launchMode"))
        val (host, view) = viewer.children("intent-filter").also { assertEquals(2, it.size) }
        assertFilter(host, action = ThreePaperPdfContract.VIEWER_ACTION, categories = listOf(DEFAULT_CATEGORY))
        assertTrue(host.children("data").isEmpty())
        assertEquals(listOf("android.intent.action.VIEW"), view.children("action").map { it.android("name") })
        assertEquals(listOf(DEFAULT_CATEGORY, "android.intent.category.BROWSABLE"), view.children("category").map { it.android("name") })
        assertEquals(
            listOf("content", "file", ThreePaperPdfContract.PDF_MIME_TYPE),
            view.children("data").map { it.androidOrNull("scheme") ?: it.android("mimeType") },
        )
    }

    @Test
    fun `the file manager tools accept only the host's execute action`() {
        val viewer = activity(".explorer.ExplorerPdfViewerActivity")
        val tools = activity(".explorer.PdfToolActivity")
        listOf(viewer, tools).forEach { activity ->
            assertEquals("true", activity.android("exported"))
            // Only the host starts them, with the selected files as content URIs (roadmap D36, P3.3).
            assertEquals(PLUGIN_PERMISSION, activity.android("permission"))
            assertFilter(activity.child("intent-filter"), action = ExplorerActionPluginActions.EXECUTE, categories = listOf(DEFAULT_CATEGORY))
            assertTrue(activity.child("intent-filter").children("data").isEmpty())
        }
        // Opening another PDF from the file manager replaces the document on screen, like the viewer. zh-CN: 同查看器.
        assertEquals("singleTop", viewer.android("launchMode"))
        assertNull("a tool screen finishes the request it started with", tools.androidOrNull("launchMode"))
        assertEquals("adjustResize", tools.android("windowSoftInputMode"))
    }

    @Test
    fun `the INFO, PDF and EXPLORER_ACTION services match the discovery constants`() {
        assertEquals(
            listOf(".service.ThreePaperPdfInfoService", ".service.ThreePaperPdfService", ".ThreePaperPdfExplorerActionService"),
            application.children("service").map { it.android("name") },
        )
        val info = service(".service.ThreePaperPdfInfoService")
        assertFilter(info.child("intent-filter"), action = PluginActions.INFO, categories = listOf(ThreePaperPdfContract.INFO_CATEGORY))
        assertEquals(ThreePaperPdfContract.INFO_ACTION, PluginActions.INFO)

        val capability = service(".service.ThreePaperPdfService")
        assertFilter(
            capability.child("intent-filter"),
            action = ThreePaperPdfContract.SERVICE_ACTION,
            categories = listOf(ThreePaperPdfContract.SERVICE_CATEGORY),
        )

        val explorer = service(".ThreePaperPdfExplorerActionService")
        assertFilter(explorer.child("intent-filter"), action = ThreePaperPdfContract.EXPLORER_ACTION, categories = listOf(DEFAULT_CATEGORY))

        listOf(info, capability, explorer).forEach { service ->
            assertRequiresHostVersion(service)
            assertEquals("true", service.android("exported"))
            assertEquals(PLUGIN_PERMISSION, service.android("permission"))
            assertNull("services run in the plugin's default process", service.androidOrNull("process"))
            // The toolbox foreground service arrives with roadmap P4.1.
            assertNull("discovery services are not foreground services", service.androidOrNull("foregroundServiceType"))
        }
    }

    @Test
    fun `every exported component except the viewer and the launcher aliases requires the plugin permission`() {
        val components = listOf("activity", "service", "receiver", "provider").flatMap { application.children(it) }
        val exported = components.filter { it.androidOrNull("exported") == "true" }
        assertEquals(
            listOf(".viewer.ThreePaperPdfViewerActivity"),
            exported.filter { it.androidOrNull("permission") != PLUGIN_PERMISSION }.map { it.android("name") },
        )
        assertTrue("no receivers", application.children("receiver").isEmpty())
    }

    @Test
    fun `the only provider is the private FileProvider of the viewer's shared copies`() {
        val provider = application.children("provider").single()
        assertEquals("androidx.core.content.FileProvider", provider.android("name"))
        assertEquals("\${applicationId}.viewer.files", provider.android("authorities"))
        assertEquals("false", provider.android("exported"))
        assertEquals("true", provider.android("grantUriPermissions"))
        val paths = provider.child("meta-data")
        assertEquals("android.support.FILE_PROVIDER_PATHS", paths.android("name"))
        assertEquals("@xml/viewer_file_paths", paths.android("resource"))
        val shared = RepositoryFiles.xml("app/src/main/res/xml/viewer_file_paths.xml").children("cache-path").single()
        // FileProvider reads plain attributes here. zh-CN: FileProvider 在此读取不带命名空间的属性.
        assertEquals("viewer-share/", shared.getAttribute("path"))
    }

    private fun activity(name: String): Element = application.children("activity").single { it.android("name") == name }

    private fun service(name: String): Element = application.children("service").single { it.android("name") == name }

    private fun assertFilter(filter: Element, action: String, categories: List<String>) {
        assertEquals(listOf(action), filter.children("action").map { it.android("name") })
        assertEquals(categories, filter.children("category").map { it.android("name") })
    }

    private fun assertRequiresHostVersion(service: Element) {
        val metaData = service.children("meta-data").associate { it.android("name") to it.android("value") }
        assertEquals(mapOf(PluginCapabilityKeys.REQUIRES_HOST_VERSION to "@string/plugin_requires_host_version"), metaData)
    }

    private companion object {
        const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
        const val DEFAULT_CATEGORY = "android.intent.category.DEFAULT"
        const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"
        val ALIAS_ORDER = listOf(LauncherIconMode.LIGHT, LauncherIconMode.DARK, LauncherIconMode.AUTO, LauncherIconMode.TRANSPARENT)
    }

}

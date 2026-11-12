package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.service.pluginRuntimeFields
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.pdf.api.PdfActions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfIds
import org.autojs.plugin.pdf.api.PdfOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure PluginInfo field mapping and the identity published by the build, the Manifest and the README sources
 * (roadmap 5.5, D26, D27, D41). The Android-backed `toPluginInfo()` / `capabilities()` mapping is exercised by the
 * device contract test.
 * zh-CN: 纯数据的 PluginInfo 字段映射, 以及构建, Manifest 与 README 源发布的身份 (路线图 5.5, D26, D27, D41). 依赖 Android
 * 的 `toPluginInfo()` / `capabilities()` 映射由设备契约测试覆盖.
 */
class PluginRuntimeInfoTest {

    @Test
    fun `runtime fields keep the identity, the package version and no ABI`() {
        val fields = pluginRuntimeFields(
            name = "3-Paper PDF",
            description = "PDF",
            author = "SuperMonster003",
            id = "three-paper-pdf",
            engine = "pdf",
            variant = "default",
            versionName = "1.0.0",
            versionCode = 3L,
            versionDate = "Nov 12, 2026",
        )
        assertEquals("3-Paper PDF", fields.name)
        assertEquals("PDF", fields.description)
        assertEquals("@raw/plugin_instruction", fields.instruction)
        assertEquals(ThreePaperPdfContract.INSTRUCTION_REFERENCE, fields.instruction)
        assertEquals("SuperMonster003", fields.author)
        assertEquals("three-paper-pdf", fields.id)
        assertEquals("pdf", fields.engine)
        assertEquals("default", fields.variant)
        assertEquals("1.0.0", fields.versionName)
        assertEquals(3L, fields.versionCode)
        assertEquals("Nov 12, 2026", fields.versionDate)
        // Roadmap D26 / Q9: the Compose library exists for all four ABIs, so the universal APK reports an empty list.
        assertEquals(emptyList<String>(), fields.supportedAbis)
        // Roadmap D41 / Q8: the local build number of host 6.8.1.
        assertEquals(6641L, fields.requiredHostVersion)
        assertEquals(ThreePaperPdfContract.REQUIRED_HOST_VERSION, fields.requiredHostVersion)
        assertEquals(1, fields.contractVersion)
        // Roadmap P2.1: the ceilings of contract version 1 and the PDFBox version of the catalog (Q1).
        assertEquals(32, fields.maxOpenDocuments)
        assertEquals(PdfContract.MAX_DOCUMENTS_PER_HOST, fields.maxOpenDocuments)
        assertEquals(16_000_000L, fields.maxRenderPixels)
        assertEquals(PdfContract.MAX_RENDER_PIXELS, fields.maxRenderPixels)
        assertEquals(2, fields.renderConcurrency)
        assertEquals("2.0.27.0", fields.pdfboxVersion)
    }

    @Test
    fun `identity constants follow roadmap 5_5 and the host contract`() {
        assertEquals("three-paper-pdf", ThreePaperPdfContract.PLUGIN_ID)
        assertEquals("pdf", ThreePaperPdfContract.ENGINE)
        assertEquals("default", ThreePaperPdfContract.VARIANT)
        assertEquals("org.autojs.plugin.INFO", ThreePaperPdfContract.INFO_ACTION)
        assertEquals(ThreePaperPdfContract.PLUGIN_ID, ThreePaperPdfContract.INFO_CATEGORY)
        assertEquals("org.autojs.plugin.PDF", ThreePaperPdfContract.SERVICE_ACTION)
        assertEquals(PdfActions.SERVICE_ACTION, ThreePaperPdfContract.SERVICE_ACTION)
        assertEquals("pdf", ThreePaperPdfContract.SERVICE_CATEGORY)
        assertEquals("org.autojs.plugin.PDF_VIEWER_OPEN", ThreePaperPdfContract.VIEWER_ACTION)
        assertEquals("org.autojs.plugin.EXPLORER_ACTION", ThreePaperPdfContract.EXPLORER_ACTION)
        assertEquals(ExplorerActionProtocol.VERSION, ThreePaperPdfContract.EXPLORER_PROTOCOL_VERSION)
        assertEquals(1, ThreePaperPdfContract.CONTRACT_VERSION)
        assertEquals(PdfContract.CONTRACT_VERSION, ThreePaperPdfContract.CONTRACT_VERSION)
        assertTrue(PdfOps.ALL.containsAll(ThreePaperPdfContract.IMPLEMENTED_OPS))
        assertEquals("roadmap P2.2 implements reading", listOf(PdfOps.INFO, PdfOps.OUTLINE, PdfOps.TEXT, PdfOps.SEARCH), ThreePaperPdfContract.IMPLEMENTED_OPS)
        assertTrue(PdfContract.FEATURES.containsAll(ThreePaperPdfContract.FEATURES))
        assertEquals("no optional feature is declared before roadmap P2", emptyList<String>(), ThreePaperPdfContract.FEATURES)
        assertEquals(PdfIds.PLUGIN_ID, ThreePaperPdfContract.PLUGIN_ID)
        assertEquals(PdfIds.CATEGORY, ThreePaperPdfContract.SERVICE_CATEGORY)
        assertTrue(
            "the host refuses a plugin that requires less than its own floor",
            ThreePaperPdfContract.REQUIRED_HOST_VERSION >= PdfIds.REQUIRED_HOST_VERSION_CODE,
        )
        assertEquals("NOT_IMPLEMENTED", ThreePaperPdfContract.NOT_IMPLEMENTED)
    }

    @Test
    fun `the build publishes the same identity as the constants and builds one universal APK`() {
        val build = RepositoryFiles.text("app/build.gradle.kts")
        assertTrue(build.contains("val globalApplicationId = \"$PACKAGE_NAME\""))
        assertTrue(build.contains("namespace = globalApplicationId"))
        assertTrue(build.contains("applicationId = globalApplicationId"))
        assertTrue(build.contains("resValue(\"string\", \"app_name\", \"3-Paper PDF\")"))
        assertTrue(build.contains("resValue(\"string\", \"plugin_name\", \"3-Paper PDF\")"))
        assertTrue(build.contains("resValue(\"string\", \"plugin_author\", \"SuperMonster003\")"))
        assertTrue(build.contains("resValue(\"string\", \"plugin_id\", \"${ThreePaperPdfContract.PLUGIN_ID}\")"))
        assertTrue(build.contains("resValue(\"string\", \"plugin_engine\", \"${ThreePaperPdfContract.ENGINE}\")"))
        assertTrue(build.contains("resValue(\"string\", \"plugin_variant\", \"${ThreePaperPdfContract.VARIANT}\")"))
        assertTrue(build.contains("resValue(\"string\", \"plugin_requires_host_version\", \"\$requiredHostVersion\")"))
        // The minimum host build is changed in the build script, the constant and the README sources together.
        assertTrue(build.contains("val requiredHostVersion = ${ThreePaperPdfContract.REQUIRED_HOST_VERSION}\n"))
        // Roadmap D26: one universal APK; the release task allows exactly the Compose library, stored uncompressed.
        assertFalse("no ABI splits (roadmap D26)", build.contains("splits {"))
        assertTrue(build.contains("abi.enableSplit = false"))
        assertTrue(build.contains("register(\"appendDigestToReleasedFiles\")"))
        assertTrue(
            build.contains(
                "val allowedNativeLibraries = listOf(\"arm64-v8a\", \"armeabi-v7a\", \"x86_64\", \"x86\").map { \"lib/\$it/libandroidx.graphics.path.so\" }",
            ),
        )
        assertTrue(build.contains("nativeAlignment { expectNoNativeLibraries.set(false) }"))
        assertTrue(build.contains("check(natives.all { it.method == ZipEntry.STORED })"))
        assertTrue(build.contains("ApkVerifier.Builder(apk).build().verify()"))
        // The runtime dependency lock gates `check` and release builds.
        assertTrue(build.contains("named(\"check\") { dependsOn(\"verifyRuntimeDependencyLock\") }"))
        assertTrue(build.contains("matching { it.name == \"preReleaseBuild\" }.configureEach { dependsOn(\"verifyRuntimeDependencyLock\") }"))
        // Roadmap Q1: PDFBox-Android is pinned to 2.0.27.0.
        assertTrue(RepositoryFiles.text("gradle/libs.versions.toml").contains("pdfbox-android = \"2.0.27.0\""))

        val settings = RepositoryFiles.text("settings.gradle.kts")
        assertTrue(settings.contains("rootProject.name = \"autojs6-plugin-three-paper-pdf\""))
    }

    @Test
    fun `the README sources publish the same identity as the constants`() {
        val common = RepositoryFiles.text(".readme/common.json")
        mapOf(
            "plugin_application_id" to PACKAGE_NAME,
            "plugin_id" to ThreePaperPdfContract.PLUGIN_ID,
            "plugin_engine" to ThreePaperPdfContract.ENGINE,
            "plugin_variant" to ThreePaperPdfContract.VARIANT,
            "info_action" to ThreePaperPdfContract.INFO_ACTION,
            "info_category" to ThreePaperPdfContract.INFO_CATEGORY,
            "discovery_action" to ThreePaperPdfContract.SERVICE_ACTION,
            "discovery_category" to ThreePaperPdfContract.SERVICE_CATEGORY,
            "explorer_action" to ThreePaperPdfContract.EXPLORER_ACTION,
            "viewer_action" to ThreePaperPdfContract.VIEWER_ACTION,
            "required_host_version_code" to ThreePaperPdfContract.REQUIRED_HOST_VERSION.toString(),
            "app_name" to "3-Paper PDF",
        ).forEach { (key, value) ->
            assertTrue(".readme/common.json must publish $key = $value", common.contains("\"$key\": \"$value\""))
        }
    }

    @Test
    fun `the package of the Kotlin sources is the application id`() {
        assertEquals(PACKAGE_NAME, ThreePaperPdfContract::class.java.packageName)
    }

    private companion object {
        const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.paper.pdf"
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.pdf.api.PdfCapabilityKeys

/**
 * The PluginInfo fields as plain values, so JVM tests can freeze the mapping without Android.
 * zh-CN: 以纯值表示的 PluginInfo 字段, 使 JVM 测试无需 Android 即可冻结映射.
 */
internal data class PluginRuntimeFields(
    val name: String,
    val description: String,
    val instruction: String,
    val author: String,
    val id: String,
    val engine: String,
    val variant: String,
    val versionName: String,
    val versionCode: Long,
    val versionDate: String,
    /**
     * Always empty: the only native library is the ABI split `libandroidx.graphics.path.so` of Compose, present for all
     * four ABIs, so the universal APK runs on every ABI (roadmap D26, appendix D Q9).
     * zh-CN: 恒为空: 唯一的原生库是 Compose 按 ABI 拆分的 `libandroidx.graphics.path.so`, 四个 ABI 齐全, 因此 universal APK
     * 可在所有 ABI 上运行 (路线图 D26, 附录 D Q9).
     */
    val supportedAbis: List<String>,
    val requiredHostVersion: Long,
    /** PDF contract version (roadmap D27). zh-CN: PDF 契约版本 (路线图 D27). */
    val contractVersion: Int,
    /** The `pdfFeatures` capability values. zh-CN: 能力 `pdfFeatures` 的取值. */
    val features: List<String>,
    /** `pdfMaxOpenDocuments`: documents per host process. zh-CN: `pdfMaxOpenDocuments`: 每个宿主进程的文档数. */
    val maxOpenDocuments: Int,
    /** `pdfMaxRenderPixels`: pixels of one rendered page. zh-CN: `pdfMaxRenderPixels`: 单页渲染像素上限. */
    val maxRenderPixels: Long,
    /** `pdfRenderConcurrency`: renders at once across documents. zh-CN: `pdfRenderConcurrency`: 跨文档同时渲染数. */
    val renderConcurrency: Int,
    /** `pdfPdfboxVersion` (roadmap Q1). zh-CN: `pdfPdfboxVersion` (路线图 Q1). */
    val pdfboxVersion: String,
)

/**
 * Assembles the fields; [id], [engine] and [variant] come from the build resources, which the JVM tests keep equal to
 * [ThreePaperPdfContract] and the README sources.
 * zh-CN: 组装字段; [id], [engine] 与 [variant] 来自构建资源, JVM 测试保证其与 [ThreePaperPdfContract] 及 README 源一致.
 */
internal fun pluginRuntimeFields(
    name: String,
    description: String,
    author: String,
    id: String,
    engine: String,
    variant: String,
    versionName: String,
    versionCode: Long,
    versionDate: String,
): PluginRuntimeFields = PluginRuntimeFields(
    name = name,
    description = description,
    instruction = ThreePaperPdfContract.INSTRUCTION_REFERENCE,
    author = author,
    id = id,
    engine = engine,
    variant = variant,
    versionName = versionName,
    versionCode = versionCode,
    versionDate = versionDate,
    supportedAbis = emptyList(),
    requiredHostVersion = ThreePaperPdfContract.REQUIRED_HOST_VERSION,
    contractVersion = ThreePaperPdfContract.CONTRACT_VERSION,
    features = ThreePaperPdfContract.FEATURES,
    maxOpenDocuments = ThreePaperPdfContract.MAX_OPEN_DOCUMENTS,
    maxRenderPixels = ThreePaperPdfContract.MAX_RENDER_PIXELS,
    renderConcurrency = ThreePaperPdfContract.RENDER_CONCURRENCY,
    pdfboxVersion = ThreePaperPdfContract.PDFBOX_VERSION,
)

/**
 * The capability bundle of PluginInfo and of `IPdfPlugin.getCapabilities` (roadmap P2.1): host floor, contract version,
 * the ceilings (`pdfMaxOpenDocuments`, `pdfMaxRenderPixels`, `pdfRenderConcurrency`), the PDFBox version and the
 * `pdfFeatures` values that work end to end (`render.sharedMemory`, `generate.html`, `crypto.aes256`, `viewer` are
 * added as the engine implements them).
 * zh-CN: PluginInfo 与 `IPdfPlugin.getCapabilities` 共用的能力 Bundle (路线图 P2.1): 宿主下限, 契约版本, 上限
 * (`pdfMaxOpenDocuments`, `pdfMaxRenderPixels`, `pdfRenderConcurrency`), PDFBox 版本, 以及端到端可用的 `pdfFeatures` 取值
 * (`render.sharedMemory`, `generate.html`, `crypto.aes256`, `viewer` 随引擎实现逐个加入).
 */
internal fun PluginRuntimeFields.capabilities(): Bundle = Bundle().apply {
    putLong(PdfCapabilityKeys.REQUIRES_HOST_VERSION, requiredHostVersion)
    putInt(PdfCapabilityKeys.CONTRACT_VERSION, contractVersion)
    putStringArray(PdfCapabilityKeys.FEATURES, features.toTypedArray())
    putInt(PdfCapabilityKeys.MAX_OPEN_DOCUMENTS, maxOpenDocuments)
    putLong(PdfCapabilityKeys.MAX_RENDER_PIXELS, maxRenderPixels)
    putInt(PdfCapabilityKeys.RENDER_CONCURRENCY, renderConcurrency)
    putString(PdfCapabilityKeys.PDFBOX_VERSION, pdfboxVersion)
}

/**
 * The identity answered by the INFO service and by `IPdfPlugin.getInfo`.
 * zh-CN: INFO 服务与 `IPdfPlugin.getInfo` 返回的身份.
 */
internal fun PluginRuntimeFields.toPluginInfo(): PluginInfo = PluginInfo().also { info ->
    info.name = name
    info.description = description
    info.instruction = instruction
    info.author = author
    info.id = id
    info.engine = engine
    info.variant = variant
    info.versionName = versionName
    info.versionCode = versionCode
    info.versionDate = versionDate
    // ABI independent: an explicit empty array, never null (new repository specification 5.5, roadmap D26).
    // zh-CN: 与 ABI 无关: 显式空数组, 不为 null (新仓库规范 5.5, 路线图 D26).
    info.supportedAbis = supportedAbis.toTypedArray()
    info.capabilities = capabilities()
}

/**
 * Reads the localized description, build resources and the installed package version.
 * zh-CN: 读取本地化描述, 构建资源与已安装包的版本.
 */
internal fun Context.pluginRuntimeFields(): PluginRuntimeFields {
    val appContext = applicationContext
    val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    return pluginRuntimeFields(
        name = appContext.getString(R.string.plugin_name),
        description = appContext.getString(R.string.plugin_description),
        author = appContext.getString(R.string.plugin_author),
        id = appContext.getString(R.string.plugin_id),
        engine = appContext.getString(R.string.plugin_engine),
        variant = appContext.getString(R.string.plugin_variant),
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = packageInfo.versionCodeCompat(),
        versionDate = appContext.getString(R.string.plugin_version_date),
    )
}

private fun PackageInfo.versionCodeCompat(): Long {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return longVersionCode
    @Suppress("DEPRECATION")
    return versionCode.toLong()
}

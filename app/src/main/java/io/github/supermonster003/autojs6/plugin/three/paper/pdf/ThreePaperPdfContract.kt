package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import android.os.Build
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfEncryption
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.HtmlToPdf
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.pdf.api.PdfActions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfIds
import org.autojs.plugin.pdf.api.PdfOps

/**
 * Identity and contract constants of the plugin (roadmap 5.5). JVM tests keep them equal to the build resources, the
 * Manifest and the README sources.
 * zh-CN: 插件的身份与契约常量 (路线图 5.5 节). JVM 测试保证其与构建资源, Manifest 及 README 源一致.
 */
object ThreePaperPdfContract {

    const val PLUGIN_ID = PdfIds.PLUGIN_ID
    const val ENGINE = PdfIds.ENGINE
    const val VARIANT = PdfIds.VARIANT_DEFAULT

    const val INFO_ACTION = PdfActions.INFO
    const val INFO_CATEGORY = PLUGIN_ID

    const val SERVICE_ACTION = PdfActions.SERVICE_ACTION
    const val SERVICE_CATEGORY = PdfActions.SERVICE_CATEGORY
    const val PLUGIN_PERMISSION = PdfActions.PLUGIN_PERMISSION

    const val VIEWER_ACTION = PdfActions.VIEWER_ACTIVITY_ACTION

    /** The packaged-app entry (roadmap Q145 (b), D43). zh-CN: 打包应用入口 (路线图 Q145 (b), D43). */
    const val PACKAGED_SERVICE_ACTION = PdfActions.PACKAGED_SERVICE_ACTION
    const val PACKAGED_PERMISSION = PdfActions.PACKAGED_PERMISSION
    const val PACKAGED_VIEWER_ACTION = PdfActions.PACKAGED_VIEWER_ACTIVITY_ACTION
    const val PDF_MIME_TYPE = "application/pdf"

    const val EXPLORER_ACTION = ExplorerActionPluginActions.EXPLORER_ACTION
    const val EXPLORER_PROTOCOL_VERSION = ExplorerActionProtocol.VERSION

    /**
     * The host build the plugin requires: temporary 6641, the first local build after the baseline 6640; roadmap P1.6
     * checks it and P5.4 writes the 6.8.1 local build with every host item (roadmap D41, Q8).
     * zh-CN: 插件要求的宿主构建号: 临时值 6641 (基线 6640 之后的第一个本地构建号); 路线图 P1.6 核对, P5.4 回填含全部宿主条目
     * 的 6.8.1 本地构建号 (路线图 D41, Q8).
     */
    const val REQUIRED_HOST_VERSION = 6641L

    /**
     * The baseline PDF contract version, declared as `pdfContractVersion` (roadmap D27). It stays 1 in 1.1.0: a host of
     * contract version 1 (6.8.0, 6.8.1 builds before P6.1) refuses a plugin whose baseline is above its own (roadmap D58).
     * zh-CN: 基线 PDF 契约版本, 声明为 `pdfContractVersion` (路线图 D27). 1.1.0 仍为 1: 契约版本 1 的宿主 (6.8.0, P6.1 之前的
     * 6.8.1 构建) 会拒绝基线高于自身的插件 (路线图 D58).
     */
    const val CONTRACT_VERSION = PdfContract.CONTRACT_VERSION

    /**
     * The newest PDF contract version the plugin implements, declared as `pdfMaxContractVersion` (roadmap D58, P6.2 row
     * 1); the host negotiates the smaller of it and its own maximum. Version-2 operations answer `NOT_IMPLEMENTED: <op>`
     * until their roadmap items land ([IMPLEMENTED_OPS]).
     * zh-CN: 插件实现的最新 PDF 契约版本, 声明为 `pdfMaxContractVersion` (路线图 D58, P6.2 第 1 行); 宿主取它与自身最大版本中较小的
     * 一个协商. 版本 2 的操作在各自路线图条目落地前返回 `NOT_IMPLEMENTED: <op>` ([IMPLEMENTED_OPS]).
     */
    const val MAX_CONTRACT_VERSION = PdfContract.CONTRACT_VERSION_2

    /**
     * Operations the plugin implements end to end; every other operation answers `UNSUPPORTED` with the detail
     * `NOT_IMPLEMENTED: <op>` until its roadmap item lands (P2.2: reading, P2.3: rendering, P2.4: page operations, P2.5:
     * generation and images, P2.6: encryption, P3.2: viewer sessions; version 2: P6.3 `stamp`). `fromHtml` is listed on every device; where
     * `generate.html` is not declared it answers `UNSUPPORTED` (protocol "Run operations").
     * zh-CN: 插件端到端实现的操作; 其他操作在对应的路线图条目落地之前返回详情为 `NOT_IMPLEMENTED: <op>` 的 `UNSUPPORTED`
     * (P2.2: 读取, P2.3: 渲染, P2.4: 页面操作, P2.5: 生成与图片, P2.6: 加密, P3.2: 查看器会话; 版本 2: P6.3 `stamp`). `fromHtml` 在所有设备上都列出;
     * 未声明 `generate.html` 的设备上它返回 `UNSUPPORTED` (协议 "Run operations" 一节).
     */
    val IMPLEMENTED_OPS: List<String> = listOf(
        PdfOps.INFO, PdfOps.OUTLINE, PdfOps.TEXT, PdfOps.SEARCH, PdfOps.RENDER, PdfOps.RENDER_TO_FILE,
        PdfOps.MERGE, PdfOps.PLAN_SPLIT, PdfOps.SPLIT, PdfOps.SAVE,
        PdfOps.FROM_IMAGES, PdfOps.FROM_TEXT, PdfOps.FROM_HTML, PdfOps.PLAN_IMAGES, PdfOps.EXTRACT_IMAGE,
        PdfOps.ENCRYPT, PdfOps.DECRYPT, PdfOps.OPEN_VIEWER,
        PdfOps.STAMP,
    )

    /**
     * `producer` of every document the plugin writes (roadmap P2.4: the metadata of the input is kept, `producer` names
     * the plugin).
     * zh-CN: 插件写出的每个文档的 `producer` (路线图 P2.4: 保留输入的元数据, `producer` 写插件名).
     */
    const val PRODUCER = "3-Paper PDF ${BuildConfig.VERSION_NAME}"

    /**
     * Optional features of contract version 1 the plugin declares in `pdfFeatures` on this device ([features]).
     * zh-CN: 插件在本设备上于 `pdfFeatures` 里声明的契约版本 1 可选特性 ([features]).
     */
    val FEATURES: List<String> get() = features(Build.VERSION.SDK_INT, HtmlToPdf.isAvailable, PdfEncryption.aes256Available)

    /**
     * Optional features on API level [sdkInt]: `render.sharedMemory` needs `SharedMemory` (API 27, roadmap P2.3),
     * `generate.html` the print callbacks and a WebView provider ([htmlAvailable], roadmap P2.5, appendix D Q5),
     * `crypto.aes256` 256-bit AES through the platform providers ([aes256Available], roadmap P2.6, appendix D Q4), `viewer`
     * the viewer sessions of every API level (roadmap P3.2).
     * zh-CN: API 级别 [sdkInt] 上的可选特性: `render.sharedMemory` 需要 `SharedMemory` (API 27, 路线图 P2.3), `generate.html`
     * 需要打印回调与 WebView 提供方 ([htmlAvailable], 路线图 P2.5, 附录 D Q5), `crypto.aes256` 需要平台 provider 支持 256 位 AES
     * ([aes256Available], 路线图 P2.6, 附录 D Q4), `viewer` 是所有 API 级别都有的查看器会话 (路线图 P3.2).
     */
    fun features(sdkInt: Int, htmlAvailable: Boolean = false, aes256Available: Boolean = false): List<String> = buildList {
        if (sdkInt >= Build.VERSION_CODES.O_MR1) add(PdfContract.FEATURE_RENDER_SHARED_MEMORY)
        if (htmlAvailable) add(PdfContract.FEATURE_GENERATE_HTML)
        if (aes256Available) add(PdfContract.FEATURE_CRYPTO_AES256)
        add(PdfContract.FEATURE_VIEWER)
    }

    /**
     * Documents one host process may keep open (capability `pdfMaxOpenDocuments`, protocol "Ceilings"); the eight per
     * script are enforced by the host.
     * zh-CN: 一个宿主进程可同时打开的文档数 (能力 `pdfMaxOpenDocuments`, 协议 "Ceilings" 一节); 每个脚本 8 个由宿主执行.
     */
    const val MAX_OPEN_DOCUMENTS = PdfContract.MAX_DOCUMENTS_PER_HOST

    /** Pixels of one rendered page (capability `pdfMaxRenderPixels`). zh-CN: 单页渲染的像素上限 (能力 `pdfMaxRenderPixels`). */
    const val MAX_RENDER_PIXELS = PdfContract.MAX_RENDER_PIXELS

    /** Renders running at once across documents (capability `pdfRenderConcurrency`). zh-CN: 跨文档同时进行的渲染数 (能力 `pdfRenderConcurrency`). */
    const val RENDER_CONCURRENCY = 2

    /** The PDFBox-Android version (capability `pdfPdfboxVersion`, roadmap Q1). zh-CN: PDFBox-Android 版本 (能力 `pdfPdfboxVersion`, 路线图 Q1). */
    const val PDFBOX_VERSION = BuildConfig.PDFBOX_VERSION

    /** Detail of operations not implemented yet. zh-CN: 尚未实现的操作的详情. */
    const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"

    /** The plugin instruction resource of the Plugin Center. zh-CN: 插件中心使用的插件说明资源. */
    const val INSTRUCTION_REFERENCE = "@raw/plugin_instruction"

}

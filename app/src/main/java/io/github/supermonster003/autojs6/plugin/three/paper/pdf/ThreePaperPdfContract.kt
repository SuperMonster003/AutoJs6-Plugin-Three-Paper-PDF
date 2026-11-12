package io.github.supermonster003.autojs6.plugin.three.paper.pdf

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

    /** PDF contract version (roadmap D27). zh-CN: PDF 契约版本 (路线图 D27). */
    const val CONTRACT_VERSION = PdfContract.CONTRACT_VERSION

    /**
     * Operations the plugin implements end to end; every other operation answers `UNSUPPORTED` with the detail
     * `NOT_IMPLEMENTED: <op>` until its roadmap item lands (P2.2: reading).
     * zh-CN: 插件端到端实现的操作; 其他操作在对应的路线图条目落地之前返回详情为 `NOT_IMPLEMENTED: <op>` 的 `UNSUPPORTED`
     * (P2.2: 读取).
     */
    val IMPLEMENTED_OPS: List<String> = listOf(PdfOps.INFO, PdfOps.OUTLINE, PdfOps.TEXT, PdfOps.SEARCH)

    /**
     * Optional features of contract version 1 the plugin declares in `pdfFeatures`; empty until roadmap P2 implements them.
     * zh-CN: 插件在 `pdfFeatures` 里声明的契约版本 1 可选特性; 路线图 P2 实现之前为空.
     */
    val FEATURES: List<String> = emptyList()

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

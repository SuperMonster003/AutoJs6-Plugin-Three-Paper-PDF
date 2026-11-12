package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.pdf.api.PdfActions
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfIds

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
     * Operations the plugin implements; empty until roadmap P2 lands the engine, so every operation answers
     * `UNSUPPORTED: NOT_IMPLEMENTED`.
     * zh-CN: 插件已实现的操作; 路线图 P2 落地引擎之前为空, 因此每个操作都返回 `UNSUPPORTED: NOT_IMPLEMENTED`.
     */
    val IMPLEMENTED_OPS: List<String> = emptyList()

    /**
     * Optional features of contract version 1 the plugin declares in `pdfFeatures`; empty until roadmap P2 implements them.
     * zh-CN: 插件在 `pdfFeatures` 里声明的契约版本 1 可选特性; 路线图 P2 实现之前为空.
     */
    val FEATURES: List<String> = emptyList()

    /** Detail of operations not implemented yet. zh-CN: 尚未实现的操作的详情. */
    const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"

    /** The plugin instruction resource of the Plugin Center. zh-CN: 插件中心使用的插件说明资源. */
    const val INSTRUCTION_REFERENCE = "@raw/plugin_instruction"

}

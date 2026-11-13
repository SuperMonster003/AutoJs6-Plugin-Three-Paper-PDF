package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Bundle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionValues

/**
 * The file manager tools (roadmap D36, P3.3, appendix D Q70 / Q74 / Q75): the id, the label, where the host shows the
 * entry and what it may do. Tools needing a password or options open [PdfToolActivity], which shows progress itself.
 * zh-CN: 文件管理器工具 (路线图 D36, P3.3, 附录 D Q70 / Q74 / Q75): id, 标签, 宿主在何处显示入口以及允许它做什么. 需要密码或
 * 选项的工具打开 [PdfToolActivity], 由它自己显示进度.
 */
internal enum class ExplorerTool(
    val id: String,
    val labelResourceName: String,
    val labelFallback: String,
    val placement: Int,
    val cardinality: Int,
    val accessMode: Int,
    val priority: Int,
) {

    /**
     * The primary action: tapping a PDF in the file manager opens it in the plugin viewer (Q75).
     * zh-CN: 主操作: 在文件管理器中点按 PDF 时用插件查看器打开 (Q75).
     */
    OPEN(
        "open", "explorer_action_open", "View PDF",
        ExplorerActionValues.PLACEMENT_PRIMARY, ExplorerActionValues.CARDINALITY_SINGLE, ExplorerActionValues.ACCESS_READ_ONLY, 100,
    ),

    /** Merges two or more selected PDFs (the host shows it from two targets on). zh-CN: 合并两个或更多选中的 PDF (宿主从两个目标起显示). */
    MERGE(
        "merge", "explorer_action_merge", "Merge PDF files",
        ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR, ExplorerActionValues.CARDINALITY_MULTIPLE, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, 100,
    ),

    SPLIT(
        "split", "explorer_action_split", "Split PDF",
        ExplorerActionValues.PLACEMENT_OVERFLOW, ExplorerActionValues.CARDINALITY_SINGLE, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, 90,
    ),

    TO_IMAGES(
        "to-images", "explorer_action_to_images", "Convert PDF to images",
        ExplorerActionValues.PLACEMENT_OVERFLOW, ExplorerActionValues.CARDINALITY_SINGLE, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, 80,
    ),

    EXTRACT_TEXT(
        "extract-text", "explorer_action_extract_text", "Extract text from PDF",
        ExplorerActionValues.PLACEMENT_OVERFLOW, ExplorerActionValues.CARDINALITY_SINGLE, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, 70,
    ),

    ENCRYPT(
        "encrypt", "explorer_action_encrypt", "Encrypt PDF",
        ExplorerActionValues.PLACEMENT_OVERFLOW, ExplorerActionValues.CARDINALITY_SINGLE, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, 60,
    ),

    DECRYPT(
        "decrypt", "explorer_action_decrypt", "Remove PDF password",
        ExplorerActionValues.PLACEMENT_OVERFLOW, ExplorerActionValues.CARDINALITY_SINGLE, ExplorerActionValues.ACCESS_CREATE_IN_PARENT, 50,
    ),
    ;

    /** The Activity the host starts for this tool. zh-CN: 宿主为此工具启动的 Activity. */
    val activityClass: Class<*> get() = if (this == OPEN) ExplorerPdfViewerActivity::class.java else PdfToolActivity::class.java

    companion object {

        fun of(id: String?): ExplorerTool? = entries.firstOrNull { it.id == id }

    }

}

/**
 * The Explorer Action catalog of the plugin (protocol 28): the menu group named after the plugin and one PDF action per
 * [ExplorerTool]. Every action targets files matched by MIME type or extension and writes nothing through the host's
 * sibling output (`OUTPUT_NONE`); tools create their results through the host session.
 * zh-CN: 插件的 Explorer Action 目录 (协议 28): 以插件命名的菜单组, 每个 [ExplorerTool] 一个 PDF 动作. 每个动作按 MIME 类型或扩展名
 * 匹配文件, 不经宿主的同级输出写入 (`OUTPUT_NONE`); 工具经宿主会话创建结果.
 */
internal object ExplorerCatalog {

    const val MENU_GROUP_LABEL_RESOURCE_NAME = "app_name"

    const val MENU_GROUP_LABEL_FALLBACK = "3-Paper PDF"

    const val MIME_PDF = "application/pdf"

    const val EXTENSION_PDF = "pdf"

    fun catalog(): Bundle = Bundle().apply {
        putInt(ExplorerActionCatalogKeys.PROTOCOL_VERSION, ThreePaperPdfContract.EXPLORER_PROTOCOL_VERSION)
        putString(ExplorerActionCatalogKeys.MENU_GROUP_LABEL_RESOURCE_NAME, MENU_GROUP_LABEL_RESOURCE_NAME)
        putString(ExplorerActionCatalogKeys.MENU_GROUP_LABEL_FALLBACK, MENU_GROUP_LABEL_FALLBACK)
        putParcelableArrayList(ExplorerActionCatalogKeys.ACTIONS, ArrayList(ExplorerTool.entries.map(::action)))
    }

    fun action(tool: ExplorerTool): Bundle = Bundle().apply {
        putString(ExplorerActionCatalogKeys.ID, tool.id)
        putString(ExplorerActionCatalogKeys.LABEL_RESOURCE_NAME, tool.labelResourceName)
        putString(ExplorerActionCatalogKeys.LABEL_FALLBACK, tool.labelFallback)
        putString(ExplorerActionCatalogKeys.ACTIVITY_CLASS_NAME, tool.activityClass.name)
        putInt(ExplorerActionCatalogKeys.PRESENTATION, ExplorerActionValues.PRESENTATION_ACTIVITY)
        putInt(ExplorerActionCatalogKeys.PRIORITY, tool.priority)
        putInt(ExplorerActionCatalogKeys.TARGET_KIND, ExplorerActionValues.TARGET_FILE)
        putInt(ExplorerActionCatalogKeys.CARDINALITY, tool.cardinality)
        putInt(ExplorerActionCatalogKeys.ACCESS_MODE, tool.accessMode)
        putInt(ExplorerActionCatalogKeys.PLACEMENT, tool.placement)
        putStringArrayList(ExplorerActionCatalogKeys.MIME_TYPES, arrayListOf(MIME_PDF))
        putStringArrayList(ExplorerActionCatalogKeys.EXTENSIONS, arrayListOf(EXTENSION_PDF))
        putInt(ExplorerActionCatalogKeys.OUTPUT_MODE, ExplorerActionValues.OUTPUT_NONE)
    }

}

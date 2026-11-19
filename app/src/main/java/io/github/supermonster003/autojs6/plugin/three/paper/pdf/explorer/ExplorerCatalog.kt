package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Bundle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostEngineIds
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
 * File manager entries the host runs with its own code (`presentation = hostEngine`, protocol 28, roadmap D67 / P6.11):
 * the plugin only declares the label and where the entry appears; the host dispatches the id to its command of the same
 * name ([ExplorerActionHostEngineIds]) and starts no plugin Activity. The host accepts these ids only from the official
 * package, as a single-target, read-only overflow action on files without output; a host that does not know a command
 * drops that one action and keeps the rest of the catalog.
 * zh-CN: 由宿主用自己的代码执行的文件管理器入口 (`presentation = hostEngine`, 协议 28, 路线图 D67 / P6.11): 插件只声明标签与入口位置; 宿主把 id
 * 分派到同名命令 ([ExplorerActionHostEngineIds]), 不启动插件 Activity. 宿主只接受官方包声明的这些 id, 且须为单目标, 只读, 溢出菜单, 作用于文件,
 * 不带输出; 不认识某个命令的宿主只丢弃该动作, 目录其余部分照常.
 */
internal enum class ExplorerHostCommand(
    val id: String,
    val labelResourceName: String,
    val labelFallback: String,
    val priority: Int,
) {

    /**
     * Adds a text layer to a scanned PDF with the host's OCR plugins and `pdf.makeSearchable`, writing
     * `<name>-searchable.pdf` next to the source (D67).
     * zh-CN: 用宿主的 OCR 插件与 `pdf.makeSearchable` 给扫描版 PDF 加文本层, 在源文件旁写出 `<名称>-searchable.pdf` (D67).
     */
    MAKE_SEARCHABLE(ExplorerActionHostEngineIds.PDF_MAKE_SEARCHABLE, "explorer_action_make_searchable", "Make searchable PDF", 65),
    ;

    companion object {

        fun of(id: String?): ExplorerHostCommand? = entries.firstOrNull { it.id == id }

    }

}

/**
 * The Explorer Action catalog of the plugin (protocol 28): the menu group named after the plugin, one PDF action per
 * [ExplorerTool] and one per [ExplorerHostCommand]. Every action targets files matched by MIME type or extension and
 * writes nothing through the host's sibling output (`OUTPUT_NONE`); tools create their results through the host session.
 * zh-CN: 插件的 Explorer Action 目录 (协议 28): 以插件命名的菜单组, 每个 [ExplorerTool] 与每个 [ExplorerHostCommand] 各一个 PDF 动作.
 * 每个动作按 MIME 类型或扩展名匹配文件, 不经宿主的同级输出写入 (`OUTPUT_NONE`); 工具经宿主会话创建结果.
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
        putParcelableArrayList(
            ExplorerActionCatalogKeys.ACTIONS,
            ArrayList(ExplorerTool.entries.map(::action) + ExplorerHostCommand.entries.map(::action)),
        )
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

    /**
     * The action of a host command: no Activity, the `hostEngine` presentation and the only shape the host accepts for
     * it (single PDF file, read-only, overflow menu, no output).
     * zh-CN: 宿主命令的动作: 没有 Activity, 呈现方式 `hostEngine`, 且是宿主接受的唯一形态 (单个 PDF 文件, 只读, 溢出菜单, 不带输出).
     */
    fun action(command: ExplorerHostCommand): Bundle = Bundle().apply {
        putString(ExplorerActionCatalogKeys.ID, command.id)
        putString(ExplorerActionCatalogKeys.LABEL_RESOURCE_NAME, command.labelResourceName)
        putString(ExplorerActionCatalogKeys.LABEL_FALLBACK, command.labelFallback)
        putInt(ExplorerActionCatalogKeys.PRESENTATION, ExplorerActionValues.PRESENTATION_HOST_ENGINE)
        putInt(ExplorerActionCatalogKeys.PRIORITY, command.priority)
        putInt(ExplorerActionCatalogKeys.TARGET_KIND, ExplorerActionValues.TARGET_FILE)
        putInt(ExplorerActionCatalogKeys.CARDINALITY, ExplorerActionValues.CARDINALITY_SINGLE)
        putInt(ExplorerActionCatalogKeys.ACCESS_MODE, ExplorerActionValues.ACCESS_READ_ONLY)
        putInt(ExplorerActionCatalogKeys.PLACEMENT, ExplorerActionValues.PLACEMENT_OVERFLOW)
        putStringArrayList(ExplorerActionCatalogKeys.MIME_TYPES, arrayListOf(MIME_PDF))
        putStringArrayList(ExplorerActionCatalogKeys.EXTENSIONS, arrayListOf(EXTENSION_PDF))
        putInt(ExplorerActionCatalogKeys.OUTPUT_MODE, ExplorerActionValues.OUTPUT_NONE)
    }

}

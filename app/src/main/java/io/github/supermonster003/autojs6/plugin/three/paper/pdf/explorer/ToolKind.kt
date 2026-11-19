package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R

/**
 * The tools of the tool screen (roadmap P3.3, P4.2, P6.12): the six file manager tools, the two the toolbox adds,
 * images to PDF and organizing pages, and the five of 1.1.0 (watermark, page numbers, compress, fill form, properties;
 * the file manager also offers compress and watermark). [id] is the tool's stable name, also used by the tool requests
 * scripts are generated from (roadmap D69). [inputs] says what a tool takes: [Inputs.ONE_PDF], [Inputs.PDFS] (two or
 * more, in order) or [Inputs.IMAGES] (one or more, in order).
 * zh-CN: 工具界面的工具 (路线图 P3.3, P4.2, P6.12): 六个文件管理器工具, 工具箱新增的图片转 PDF 与页面整理, 以及 1.1.0 的五个工具
 * (水印, 页码, 压缩, 填写表单, 文档属性; 文件管理器也提供压缩与水印). [id] 是工具的固定名称, 也供由工具请求生成脚本时使用 (路线图
 * D69). [inputs] 表示工具接受的输入:
 * [Inputs.ONE_PDF], [Inputs.PDFS] (两个或更多, 有顺序) 或 [Inputs.IMAGES] (一个或更多, 有顺序).
 */
internal enum class ToolKind(
    val id: String,
    val inputs: Inputs,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:DrawableRes val icon: Int,
) {

    MERGE("merge", Inputs.PDFS, R.string.explorer_action_merge, R.string.toolbox_merge_summary, R.drawable.ic_layers_24),
    SPLIT("split", Inputs.ONE_PDF, R.string.explorer_action_split, R.string.toolbox_split_summary, R.drawable.ic_content_cut_24),
    TO_IMAGES("to-images", Inputs.ONE_PDF, R.string.explorer_action_to_images, R.string.toolbox_to_images_summary, R.drawable.ic_image_24),
    FROM_IMAGES("from-images", Inputs.IMAGES, R.string.toolbox_from_images, R.string.toolbox_from_images_summary, R.drawable.ic_description_24),
    ENCRYPT("encrypt", Inputs.ONE_PDF, R.string.explorer_action_encrypt, R.string.toolbox_encrypt_summary, R.drawable.ic_lock_24),
    DECRYPT("decrypt", Inputs.ONE_PDF, R.string.explorer_action_decrypt, R.string.toolbox_decrypt_summary, R.drawable.ic_lock_open_24),
    EXTRACT_TEXT("extract-text", Inputs.ONE_PDF, R.string.explorer_action_extract_text, R.string.toolbox_extract_text_summary, R.drawable.ic_text_fields_24),
    ORGANIZE("organize", Inputs.ONE_PDF, R.string.toolbox_organize, R.string.toolbox_organize_summary, R.drawable.ic_rotate_right_24),
    WATERMARK("watermark", Inputs.ONE_PDF, R.string.explorer_action_watermark, R.string.toolbox_watermark_summary, R.drawable.ic_branding_watermark_24),
    PAGE_NUMBERS("page-numbers", Inputs.ONE_PDF, R.string.toolbox_page_numbers, R.string.toolbox_page_numbers_summary, R.drawable.ic_format_list_numbered_24),
    COMPRESS("compress", Inputs.ONE_PDF, R.string.explorer_action_compress, R.string.toolbox_compress_summary, R.drawable.ic_compress_24),
    FILL_FORM("fill-form", Inputs.ONE_PDF, R.string.toolbox_fill_form, R.string.toolbox_fill_form_summary, R.drawable.ic_edit_note_24),
    PROPERTIES("properties", Inputs.ONE_PDF, R.string.toolbox_properties, R.string.toolbox_properties_summary, R.drawable.ic_info_24),
    ;

    enum class Inputs { ONE_PDF, PDFS, IMAGES }

    companion object {

        fun of(id: String?): ToolKind? = entries.firstOrNull { it.id == id }

    }

}

/** The tool a file manager action runs; null for opening. zh-CN: 文件管理器动作执行的工具; 打开时为 null. */
internal val ExplorerTool.kind: ToolKind?
    get() = when (this) {
        ExplorerTool.OPEN -> null
        ExplorerTool.MERGE -> ToolKind.MERGE
        ExplorerTool.SPLIT -> ToolKind.SPLIT
        ExplorerTool.TO_IMAGES -> ToolKind.TO_IMAGES
        ExplorerTool.EXTRACT_TEXT -> ToolKind.EXTRACT_TEXT
        ExplorerTool.ENCRYPT -> ToolKind.ENCRYPT
        ExplorerTool.DECRYPT -> ToolKind.DECRYPT
    }

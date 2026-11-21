package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentEntry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import java.io.File

/**
 * What the file browser hands back (roadmap P6.15 batch 17d, appendix D Q324 (a)): a [Local] file, or the folder of
 * [BrowseMode.FOLDER], or a [Document] of the recent files, picked with the system file picker before and checked
 * under its persisted read grant.
 * zh-CN: 文件浏览器交回的内容 (路线图 P6.15 批次 17d, 附录 D Q324 (a)): 本地文件 [Local] (或 [BrowseMode.FOLDER] 的文件夹), 或最近文件中
 * 此前用系统文件选择器选取的文档 [Document], 已凭持久读取授权核对过.
 */
internal sealed interface BrowserPick {

    data class Local(val file: File) : BrowserPick

    data class Document(val entry: RecentEntry.Document) : BrowserPick

}

/**
 * The rules of the file browser that need no Android, so the JVM tests cover them (roadmap P6.15 batch 17d, appendix
 * D Q324 (a)): which recent files it lists and how many while not expanded, the order of checked files, the picks of a
 * confirmation, the folder it remembers and the folder it starts in.
 * zh-CN: 文件浏览器中不依赖 Android 的规则, 以便 JVM 测试覆盖 (路线图 P6.15 批次 17d, 附录 D Q324 (a)): 列出哪些最近文件以及未展开时显示几条,
 * 勾选的顺序, 确认时交回的内容, 记住的文件夹, 以及从哪个文件夹开始.
 */
internal object BrowserRules {

    /** Recent files shown at most while the group is not expanded. zh-CN: 分组未展开时至多显示的最近文件条数. */
    const val COMPACT_MAX = 3

    /** The share of the list height the group may take while not expanded. zh-CN: 分组未展开时至多占列表高度的比例. */
    const val COMPACT_SHARE = 0.5f

    /** Whether [mode] lists the recent files: they hold PDFs only, so not images and not folders. zh-CN: [mode] 是否列出最近文件: 最近文件只有 PDF, 所以图片与文件夹模式不列. */
    fun listsRecent(mode: BrowseMode): Boolean = mode == BrowseMode.PDF || mode == BrowseMode.PDFS

    /**
     * The recent files [mode] lists, in the order of [list] (newest first): documents, which are checked when picked,
     * and local files that [readable] says can be opened now; without storage access the files of other apps drop out
     * until it is granted, so the group never shows an entry that fails when tapped.
     * zh-CN: [mode] 列出的最近文件, 顺序同 [list] (最新在前): 文档 (选取时核对), 以及 [readable] 认为现在能打开的本地文件; 没有存储访问权限时,
     * 其他应用的文件在授权之前不显示, 因此分组里不会有点了打不开的条目.
     */
    fun recent(list: List<RecentEntry>, mode: BrowseMode, readable: (File) -> Boolean): List<RecentEntry> =
        if (!listsRecent(mode)) emptyList() else list.filter { it !is RecentEntry.Local || readable(it.file) }

    /**
     * How many of [total] recent files show while the group is not expanded: the header and the rows take at most
     * [COMPACT_SHARE] of [viewport], at least one row and at most [COMPACT_MAX]; all heights in the same unit.
     * zh-CN: 分组未展开时 [total] 条最近文件显示几条: 表头与各行至多占 [viewport] 的 [COMPACT_SHARE], 至少一条, 至多 [COMPACT_MAX] 条; 各高度单位相同.
     */
    fun compactCount(total: Int, viewport: Float, header: Float, row: Float): Int {
        if (total <= 0) return 0
        val fits = if (row > 0f) ((viewport * COMPACT_SHARE - header) / row).toInt() else COMPACT_MAX
        return fits.coerceIn(1, COMPACT_MAX).coerceAtMost(total)
    }

    /** [key] checked after the others, or unchecked; the order is the order of checking. zh-CN: 勾选 [key] (排在已勾选的之后) 或取消勾选; 顺序即勾选的顺序. */
    fun toggle(checked: List<String>, key: String): List<String> = if (key in checked) checked - key else checked + key

    /** The key that checks a recent file: the path of a local file, as for the folder rows, or the uri of a document. zh-CN: 勾选最近文件所用的键: 本地文件为路径 (与目录行相同), 文档为 uri. */
    fun keyOf(entry: RecentEntry): String = when (entry) {
        is RecentEntry.Local -> File(entry.path).absolutePath
        is RecentEntry.Document -> entry.uri
    }

    /**
     * The picks of the [checked] keys in the order checked: a document key gives the document of [recent], a document
     * no longer listed drops out; every other key is a local path.
     * zh-CN: 已勾选的键按勾选顺序对应的内容: 文档键对应 [recent] 中的文档, 已不在列表中的文档略去; 其余的键都是本地路径.
     */
    fun picks(checked: List<String>, recent: List<RecentEntry>): List<BrowserPick> {
        val documents = recent.filterIsInstance<RecentEntry.Document>().associateBy { it.uri }
        return checked.mapNotNull { key ->
            if (isDocumentKey(key)) documents[key]?.let { BrowserPick.Document(it) } else BrowserPick.Local(File(key))
        }
    }

    fun isDocumentKey(key: String): Boolean = key.startsWith(CONTENT_SCHEME)

    /**
     * The preference that remembers the folder of [mode] (the toolbox preferences): PDFs, home included, share one, images
     * have their own, folders (the output folder) none.
     * zh-CN: 记住 [mode] 文件夹的偏好键 (工具箱偏好): PDF (含首页) 共用一个, 图片单独一个, 文件夹 (输出目录) 不记.
     */
    fun memoryOf(mode: BrowseMode): String? = when (mode) {
        BrowseMode.PDF, BrowseMode.PDFS -> ToolboxPreferences.KEY_PDF_FOLDER
        BrowseMode.IMAGES -> ToolboxPreferences.KEY_IMAGE_FOLDER
        BrowseMode.FOLDER -> null
    }

    /**
     * The folder to remember after [picks]: the folder of the last local file, a file from "Recent" included, or null when
     * only documents were picked (their folder is unknown), which keeps the memory as it is.
     * zh-CN: 选定 [picks] 后要记住的文件夹: 最后一个本地文件 (含 "最近使用" 中的) 所在的文件夹; 只选了文档时 (不知道其文件夹) 为 null, 记忆保持不变.
     */
    fun folderToRemember(picks: List<BrowserPick>): File? = picks.filterIsInstance<BrowserPick.Local>().lastOrNull()?.file?.absoluteFile?.parentFile

    /**
     * The folder the browser starts in: of [candidates] in order (the folder of the tool's last input, then the
     * remembered folder), the first whose nearest existing folder lies in one of the [roots]; else the only volume, or
     * null, which lists the volumes. A deleted folder starts from its nearest existing parent.
     * zh-CN: 浏览器的起始文件夹: 依次取 [candidates] (本工具上一个输入所在的文件夹, 然后是记住的文件夹) 中第一个其最近的已存在文件夹位于某个 [roots]
     * 之内的; 都不是时取唯一的存储卷, 或为 null (列出各存储卷). 已删除的文件夹从其最近的已存在上级开始.
     */
    fun startFolder(candidates: List<File?>, roots: List<File>): String? {
        for (start in candidates) {
            var candidate = start
            while (candidate != null && !candidate.isDirectory) candidate = candidate.parentFile
            val inside = candidate?.takeIf { dir -> roots.any { inside(dir, it) } }
            if (inside != null) return inside.absolutePath
        }
        return roots.singleOrNull()?.absolutePath
    }

    /** Whether [dir] is [root] or lies in it. zh-CN: [dir] 是否为 [root] 或位于其中. */
    fun inside(dir: File, root: File): Boolean = dir.absolutePath == root.absolutePath || dir.absolutePath.startsWith(root.absolutePath + File.separator)

    private const val CONTENT_SCHEME = "content:"

}

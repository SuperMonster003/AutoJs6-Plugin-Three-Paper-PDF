package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentEntry
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
 * D Q324 (a)): which recent files it lists and how many while not expanded, the order of checked files and the picks
 * of a confirmation.
 * zh-CN: 文件浏览器中不依赖 Android 的规则, 以便 JVM 测试覆盖 (路线图 P6.15 批次 17d, 附录 D Q324 (a)): 列出哪些最近文件以及未展开时显示几条,
 * 勾选的顺序, 以及确认时交回的内容.
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

    /** Whether [dir] is [root] or lies in it. zh-CN: [dir] 是否为 [root] 或位于其中. */
    fun inside(dir: File, root: File): Boolean = dir.absolutePath == root.absolutePath || dir.absolutePath.startsWith(root.absolutePath + File.separator)

    private const val CONTENT_SCHEME = "content:"

}

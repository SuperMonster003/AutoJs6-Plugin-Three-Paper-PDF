package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.EmptyAction
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.EmptyState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.PaperDialog
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.PaperScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.RowDefaults
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.TopBarAction
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What the browser picks. zh-CN: 浏览器选择的内容. */
internal enum class BrowseMode {
    /** One PDF, picked by a tap. zh-CN: 一个 PDF, 点按即选. */
    PDF,

    /** PDFs, checked and then added in the order checked. zh-CN: 多个 PDF, 勾选后按勾选顺序添加. */
    PDFS,

    /** Images, checked and then added in the order checked. zh-CN: 多张图片, 勾选后按勾选顺序添加. */
    IMAGES,

    /** A folder, the one shown when confirmed; folders can be created. zh-CN: 一个文件夹, 即确认时所在的文件夹; 可以新建文件夹. */
    FOLDER,
}

/**
 * The plugin's own file browser (roadmap D37, P4.1): the shared storage volumes through `java.io.File`, folders first,
 * names without case, hidden entries left out. The toolbox picks its inputs and the output folder with it; the
 * system picker ([onSystemPicker]) remains for inputs from other places. A folder it cannot list (no all files access)
 * offers [onRequestAccess]. Test tags: `browser.entry.<name>`, `browser.confirm`, `browser.system`, `browser.newFolder`,
 * `browser.close`, `browser.path`.
 * zh-CN: 插件自带的文件浏览器 (路线图 D37, P4.1): 经 `java.io.File` 浏览共享存储卷, 文件夹在前, 名称不区分大小写, 略去隐藏条目.
 * 工具箱用它选择输入与输出目录; 其他位置的输入仍可用系统选择器 ([onSystemPicker]). 无法列出的文件夹 (没有所有文件访问权限) 提供
 * [onRequestAccess]. 测试标签: `browser.entry.<名称>`, `browser.confirm`, `browser.system`, `browser.newFolder`, `browser.close`,
 * `browser.path`.
 */
@Composable
internal fun FileBrowser(
    mode: BrowseMode,
    title: String,
    onDismiss: () -> Unit,
    onPick: (List<File>) -> Unit,
    start: File? = null,
    onSystemPicker: (() -> Unit)? = null,
    onRequestAccess: (() -> Unit)? = null,
    accessGeneration: Int = 0,
) {
    val context = LocalContext.current
    val roots = remember { storageRoots(context) }
    var path by rememberSaveable { mutableStateOf(initialPath(start, roots)) }
    var checked by rememberSaveable { mutableStateOf<List<String>>(ArrayList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val directory = path?.let(::File)
    val root = directory?.let { dir -> roots.firstOrNull { dir.absolutePath == it.absolutePath || dir.absolutePath.startsWith(it.absolutePath + File.separator) } }

    val up: () -> Unit = {
        when {
            directory == null -> onDismiss()
            root != null && directory.absolutePath == root.absolutePath -> if (roots.size > 1) path = null else onDismiss()
            else -> path = directory.parentFile?.absolutePath
        }
    }
    BackHandler(onBack = up)

    val listing by produceState<Listing?>(null, path, refresh, accessGeneration) {
        value = null
        value = withContext(Dispatchers.IO) { directory?.let { list(it, mode) } }
    }

    PaperScreen(
        title = title,
        onBack = up,
        singleLineTitle = true,
        actions = {
            if (mode == BrowseMode.FOLDER && directory != null) {
                TopBarAction(R.drawable.ic_add_24, stringResource(R.string.browser_new_folder), "browser.newFolder") { creating = true }
            }
            if (onSystemPicker != null) {
                TopBarAction(R.drawable.ic_folder_open_24, stringResource(R.string.browser_system_picker), "browser.system", onSystemPicker)
            }
            TopBarAction(R.drawable.ic_close_24, stringResource(R.string.viewer_close), "browser.close", onDismiss)
        },
        bottomBar = {
            val confirm: (() -> Unit)? = when (mode) {
                BrowseMode.PDFS, BrowseMode.IMAGES -> { { onPick(checked.map(::File)) } }
                BrowseMode.FOLDER -> directory?.let { dir -> { onPick(listOf(dir)) } }
                BrowseMode.PDF -> null
            }
            val label = when (mode) {
                BrowseMode.FOLDER -> stringResource(R.string.browser_use_folder)
                else -> stringResource(R.string.browser_add, checked.size)
            }
            if (mode != BrowseMode.PDF) {
                Surface(tonalElevation = 3.dp) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                        Button(
                            onClick = { confirm?.invoke() },
                            enabled = confirm != null && (mode == BrowseMode.FOLDER || checked.isNotEmpty()),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("browser.confirm"),
                        ) { Text(label) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = if (directory == null) stringResource(R.string.browser_storage) else breadcrumb(context, directory, root),
                modifier = Modifier.fillMaxWidth().padding(horizontal = RowDefaults.horizontalPadding, vertical = 4.dp).testTag("browser.path"),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (directory == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(roots, key = { it.absolutePath }) { volume ->
                        EntryRow(
                            name = volumeName(context, volume),
                            summary = volume.absolutePath,
                            icon = R.drawable.ic_storage_24,
                            tag = "browser.entry.${volume.name}",
                            onClick = { path = volume.absolutePath },
                        )
                    }
                }
                return@Column
            }
            val current = listing
            when {
                current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                current.entries == null -> EmptyState(
                    title = stringResource(R.string.browser_unreadable),
                    body = stringResource(R.string.browser_unreadable_body),
                    icon = R.drawable.ic_folder_24,
                    primary = onRequestAccess?.let { EmptyAction(stringResource(R.string.home_storage_grant), it, "browser.grant") },
                )
                current.entries.isEmpty() -> EmptyState(
                    title = stringResource(if (mode == BrowseMode.FOLDER) R.string.browser_no_folders else R.string.browser_empty),
                    icon = R.drawable.ic_folder_open_24,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(current.entries, key = { it.file.absolutePath }) { entry ->
                        val file = entry.file
                        val multiple = mode == BrowseMode.PDFS || mode == BrowseMode.IMAGES
                        val isChecked = file.absolutePath in checked
                        EntryRow(
                            name = file.name,
                            summary = entry.summary(context),
                            icon = when {
                                entry.directory -> R.drawable.ic_folder_24
                                ToolboxFiles.isImage(file) -> R.drawable.ic_image_24
                                else -> R.drawable.ic_file_24
                            },
                            tag = "browser.entry.${file.name}",
                            checked = if (multiple && !entry.directory) isChecked else null,
                            onClick = {
                                when {
                                    entry.directory -> path = file.absolutePath
                                    multiple -> checked = ArrayList(if (isChecked) checked - file.absolutePath else checked + file.absolutePath)
                                    mode == BrowseMode.PDF -> onPick(listOf(file))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (creating && directory != null) {
        NewFolderDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                val folder = File(directory, name)
                if (folder.mkdir() || folder.isDirectory) {
                    creating = false
                    path = folder.absolutePath
                    refresh++
                    true
                } else {
                    false
                }
            },
        )
    }
}

@Composable
private fun EntryRow(name: String, summary: String?, @DrawableRes icon: Int, tag: String, checked: Boolean? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = if (checked != null) Role.Checkbox else Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .defaultMinSize(minHeight = RowDefaults.rowMinHeight)
            .padding(horizontal = RowDefaults.horizontalPadding, vertical = RowDefaults.verticalPadding)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RowDefaults.iconSlot)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(RowDefaults.iconSize))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            if (!summary.isNullOrEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (checked != null) {
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var failed by rememberSaveable { mutableStateOf(false) }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty() && trimmed != "." && trimmed != ".." && trimmed.none { it in INVALID_NAME_CHARS }
    PaperDialog(
        title = stringResource(R.string.browser_new_folder),
        onDismissRequest = onDismiss,
        confirmText = stringResource(R.string.ui_dialog_confirm),
        onConfirm = { failed = !onCreate(trimmed) },
        dismissText = stringResource(R.string.ui_dialog_cancel),
        confirmEnabled = valid,
        tag = "paper.dialog.newFolder",
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                failed = false
            },
            modifier = Modifier.fillMaxWidth().testTag("browser.newFolder.name"),
            label = { Text(stringResource(R.string.browser_new_folder_name)) },
            isError = failed,
            supportingText = if (failed) ({ Text(stringResource(R.string.browser_new_folder_failed)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

/** A listed entry; files carry their size and date. zh-CN: 列出的条目; 文件带有大小与日期. */
private class Entry(val file: File, val directory: Boolean, val size: Long, val modified: Long) {

    fun summary(context: Context): String? {
        if (directory) return null
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(modified))
        return "${Formatter.formatShortFileSize(context, size)} · $date"
    }

}

/** The entries of a folder, or null entries when it cannot be listed. zh-CN: 文件夹的条目; 无法列出时 entries 为 null. */
private class Listing(val entries: List<Entry>?)

private fun list(directory: File, mode: BrowseMode): Listing {
    val children = directory.listFiles() ?: return Listing(null)
    val entries = children.asSequence()
        .filter { !it.name.startsWith(".") }
        .mapNotNull { file ->
            val folder = file.isDirectory
            val wanted = folder || when (mode) {
                BrowseMode.PDF, BrowseMode.PDFS -> ToolboxFiles.isPdf(file)
                BrowseMode.IMAGES -> ToolboxFiles.isImage(file)
                BrowseMode.FOLDER -> false
            }
            if (wanted) Entry(file, folder, if (folder) 0 else file.length(), file.lastModified()) else null
        }
        .sortedWith(compareBy<Entry> { !it.directory }.thenBy { it.file.name.lowercase(Locale.ROOT) }.thenBy { it.file.name })
        .toList()
    return Listing(entries)
}

/** The primary shared storage, then the other volumes (from the app-specific folders). zh-CN: 主共享存储, 然后是其他存储卷 (由应用专属目录推得). */
internal fun storageRoots(context: Context): List<File> {
    val primary = Environment.getExternalStorageDirectory()
    val others = context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
        dir.absolutePath.substringBefore("/Android/data/", "").takeIf { it.isNotEmpty() }?.let(::File)
    }
    return (listOf(primary) + others).distinctBy { it.absolutePath }
}

private fun initialPath(start: File?, roots: List<File>): String? {
    var candidate = start
    while (candidate != null && !candidate.isDirectory) candidate = candidate.parentFile
    val inside = candidate?.takeIf { dir -> roots.any { dir.absolutePath == it.absolutePath || dir.absolutePath.startsWith(it.absolutePath + File.separator) } }
    return inside?.absolutePath ?: roots.singleOrNull()?.absolutePath
}

private fun volumeName(context: Context, volume: File): String =
    runCatching { context.getSystemService(StorageManager::class.java)?.getStorageVolume(volume)?.getDescription(context) }.getOrNull()
        ?: volume.name

private fun breadcrumb(context: Context, directory: File, root: File?): String {
    if (root == null) return directory.absolutePath
    val relative = directory.absolutePath.removePrefix(root.absolutePath).trim(File.separatorChar)
    val parts = listOf(volumeName(context, root)) + relative.split(File.separatorChar).filter { it.isNotEmpty() }
    return parts.joinToString(" / ")
}

private const val INVALID_NAME_CHARS = "/\\:*?\"<>|"

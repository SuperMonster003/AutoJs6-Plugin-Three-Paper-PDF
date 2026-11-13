package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.BuildConfig
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.messageOf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.StandaloneActivity
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.Hairline
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.PaperScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.SectionHeader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.SettingsRow
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.TopBarAction
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files.BrowseMode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files.FileBrowser
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.release.ReleaseHistoryAssets
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.release.ReleaseHistoryParser
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.release.ReleaseHistoryScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.settings.SettingsScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTask
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTasks
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.rememberStorageAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.update.AppUpdateController
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.update.AppUpdateStore
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.update.UpdateDialogs
import java.io.File
import java.text.NumberFormat

/**
 * The standalone toolbox (roadmap P4.1, P4.2, D37, appendix D Q70 = A): one activity whose navigation graph holds the
 * home screen (all files access guide, the toolbox tasks, the tools, opening a PDF and the recent files), the settings,
 * the release history and one wizard per tool. The wizards are the screen of [PdfToolScreen] in toolbox mode, the same
 * code as the file manager entry `PdfToolActivity`. Update results show over every destination.
 * zh-CN: 独立工具箱 (路线图 P4.1, P4.2, D37, 附录 D Q70 = A): 一个 Activity, 其导航图包含首页 (所有文件访问引导, 工具箱任务, 各工具,
 * 打开 PDF 与最近文件), 设置, 发行历史, 以及每个工具一个向导. 向导是工具箱模式下的 [PdfToolScreen], 与文件管理器入口
 * `PdfToolActivity` 共用同一套代码. 更新结果显示在任何目的地之上.
 */
class HomeActivity : StandaloneActivity() {

    private lateinit var updates: AppUpdateController

    private var settingsHolds = false

    private var route: String? = null

    /** The navigation of the current content; device tests reach the wizard's view model through it. zh-CN: 当前内容的导航; 设备测试经它取得向导的视图模型. */
    @VisibleForTesting
    internal var navigation: NavHostController? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        updates = AppUpdateController(AppUpdateStore.of(this), BuildConfig.VERSION_CODE.toLong())
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        if (isFinishing) updates.cancel()
        super.onDestroy()
    }

    // A settings dialog, an update dialog or a wizard is not interrupted by an appearance change.
    // zh-CN: 设置对话框, 更新对话框或向导不因外观变化而被打断.
    override fun holdsAppearance(): Boolean = settingsHolds || updates.state != null || route?.startsWith(ROUTE_WIZARD) == true

    @Composable
    override fun Content() {
        val navigation = rememberNavController().also { this.navigation = it }
        val entry by navigation.currentBackStackEntryAsState()
        route = entry?.destination?.route
        NavHost(navController = navigation, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                // The automatic update check, at most every 12 hours (roadmap D37). zh-CN: 自动更新检查, 每 12 小时至多一次 (路线图 D37).
                LaunchedEffect(Unit) { updates.checkAutomaticallyIfDue() }
                HomeScreen(
                    onTool = { kind -> navigation.navigate("$ROUTE_WIZARD/${kind.id}") { launchSingleTop = true } },
                    onSettings = { navigation.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    activity = this@HomeActivity,
                    updates = updates,
                    onBack = dropUnlessResumed { navigation.popBackStack() },
                    onReleaseHistory = { navigation.navigate(ROUTE_HISTORY) { launchSingleTop = true } },
                    onHoldChange = { settingsHolds = it },
                )
            }
            composable(ROUTE_HISTORY) {
                val entries = remember { releaseHistory() }
                ReleaseHistoryScreen(entries, onBack = dropUnlessResumed { navigation.popBackStack() })
            }
            composable("$ROUTE_WIZARD/{$ARGUMENT_KIND}") { backStackEntry ->
                val kind = ToolKind.of(backStackEntry.arguments?.getString(ARGUMENT_KIND))
                val close = dropUnlessResumed { navigation.popBackStack() }
                if (kind == null) {
                    LaunchedEffect(Unit) { navigation.popBackStack() }
                    return@composable
                }
                // The view model lives as long as the wizard's back stack entry. zh-CN: 视图模型与向导的返回栈条目同寿命.
                val model: PdfToolViewModel = viewModel()
                LaunchedEffect(model) { model.startToolbox(kind) }
                PdfToolScreen(model = model, onClose = close)
            }
        }
        UpdateDialogs(
            updates,
            onOpenRelease = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
            onReleaseHistory = { navigation.openOnce(ROUTE_HISTORY) },
        )
    }

    private fun releaseHistory() = runCatching {
        assets.open(ReleaseHistoryAssets.assetFor(resources.configuration.locales[0])).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()?.let(ReleaseHistoryParser::parse).orEmpty()

    private fun NavHostController.openOnce(route: String) {
        if (currentBackStackEntry?.destination?.route != route) navigate(route) { launchSingleTop = true }
    }

    internal companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_HISTORY = "history"
        const val ROUTE_WIZARD = "wizard"
        private const val ARGUMENT_KIND = "kind"
    }

}

/**
 * The home screen: the all files access card while it is missing, the toolbox tasks (newest first), opening a PDF and
 * the eight tools, then the recent files. The lists reload on every resume and when a task finishes.
 * zh-CN: 首页: 缺少所有文件访问权限时显示授权卡片, 工具箱任务 (最新的在前), 打开 PDF 与八个工具, 然后是最近文件. 每次恢复时以及任务结束时
 * 重新加载列表.
 */
@Composable
private fun HomeScreen(onTool: (ToolKind) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val storage = rememberStorageAccess()
    var browsing by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        reload++
        onPauseOrDispose { }
    }
    val tasks = ToolTasks.tasks.reversed()
    val finished = tasks.count { it.finished }
    val recents = remember(reload, finished, storage.generation) { RecentFiles.read(context) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ToolboxFiles.openUriInViewer(context, uri)
    }

    if (browsing) {
        FileBrowser(
            mode = BrowseMode.PDF,
            title = stringResource(R.string.home_open_pdf),
            onDismiss = { browsing = false },
            onPick = { files ->
                browsing = false
                files.firstOrNull()?.let { file ->
                    RecentFiles.add(context, file)
                    reload++
                    ToolboxFiles.openInViewer(context, file)
                }
            },
            start = recents.firstOrNull()?.parentFile,
            onSystemPicker = {
                browsing = false
                runCatching { pickPdf.launch(arrayOf(MIME_PDF)) }
            },
            onRequestAccess = storage.request,
            accessGeneration = storage.generation,
        )
        return
    }

    PaperScreen(
        title = stringResource(R.string.app_name),
        actions = { TopBarAction(R.drawable.ic_settings_24, stringResource(R.string.settings_title), "paper.home.settings", onSettings) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).testTag("paper.home")) {
            if (!storage.granted) {
                item(key = "storage") { StorageCard(storage.request) }
            }
            if (tasks.isNotEmpty()) {
                item(key = "tasks") { SectionHeader(stringResource(R.string.home_tasks)) }
                items(tasks, key = { "task-${it.id}" }) { task -> TaskCard(task) }
            }
            item(key = "tools") { SectionHeader(stringResource(R.string.home_tools)) }
            item(key = "open") {
                SettingsRow(stringResource(R.string.home_open_pdf), summary = stringResource(R.string.home_open_pdf_summary), icon = R.drawable.ic_folder_open_24,
                    onClick = { browsing = true }, modifier = Modifier.testTag("paper.home.open"))
            }
            items(ToolKind.entries, key = { "tool-${it.id}" }) { kind ->
                Hairline()
                SettingsRow(stringResource(kind.title), summary = stringResource(kind.summary), icon = kind.icon,
                    onClick = { onTool(kind) }, modifier = Modifier.testTag("paper.home.tool.${kind.id}"))
            }
            item(key = "recent") {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionHeader(stringResource(R.string.home_recent), modifier = Modifier.weight(1f))
                    if (recents.isNotEmpty()) {
                        TextButton(onClick = { RecentFiles.clear(context); reload++ }, modifier = Modifier.padding(end = 8.dp).testTag("paper.home.recent.clear")) {
                            Text(stringResource(R.string.home_recent_clear))
                        }
                    }
                }
            }
            if (recents.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        text = stringResource(R.string.home_recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).testTag("paper.home.recent.empty"),
                    )
                }
            }
            items(recents, key = { "recent-${it.absolutePath}" }) { file ->
                RecentRow(
                    file = file,
                    onOpen = {
                        RecentFiles.add(context, file)
                        reload++
                        ToolboxFiles.openInViewer(context, file)
                    },
                    onRemove = {
                        RecentFiles.remove(context, file)
                        reload++
                    },
                )
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StorageCard(onGrant: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag("paper.home.storage"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_storage_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.home_storage_denied), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant, modifier = Modifier.testTag("paper.home.storage.grant")) { Text(stringResource(R.string.home_storage_grant)) }
        }
    }
}

/** One toolbox task: progress and cancel while it runs, open / share / remove when done. zh-CN: 一个工具箱任务: 运行时显示进度与取消, 结束后可打开 / 分享 / 移除. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(task: ToolTask) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().testTag("paper.home.task.${task.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(task.kind.title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (task.inputCount > 1) stringResource(R.string.home_task_inputs, task.title, task.inputCount - 1) else task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            when (val state = task.state) {
                ToolTask.State.Queued, ToolTask.State.Running -> {
                    val running = state == ToolTask.State.Running
                    Spacer(Modifier.height(4.dp))
                    if (running) {
                        LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (running) NumberFormat.getPercentInstance().format(task.progress.toDouble()) else stringResource(R.string.toolbox_queued),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).testTag("paper.home.task.${task.id}.status"),
                        )
                        TextButton(onClick = task::cancel, modifier = Modifier.testTag("paper.home.task.${task.id}.cancel")) {
                            Text(stringResource(R.string.viewer_cancel))
                        }
                    }
                }
                is ToolTask.State.Done -> {
                    val files = remember(state) { ToolboxFiles.filesOf(File(state.result.displayPath)) }
                    Text(
                        text = state.result.displayPath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.testTag("paper.home.task.${task.id}.path"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (files.size == 1) {
                            TextButton(onClick = { ToolboxFiles.open(context, files.single()) }, modifier = Modifier.testTag("paper.home.task.${task.id}.open")) {
                                Text(stringResource(R.string.toolbox_open))
                            }
                        }
                        if (files.isNotEmpty()) {
                            TextButton(onClick = { ToolboxFiles.share(context, files) }, modifier = Modifier.testTag("paper.home.task.${task.id}.share")) {
                                Text(stringResource(if (files.size == 1) R.string.toolbox_share else R.string.toolbox_share_all))
                            }
                        }
                        TextButton(onClick = { ToolTasks.dismiss(context, task) }, modifier = Modifier.testTag("paper.home.task.${task.id}.dismiss")) {
                            Text(stringResource(R.string.home_task_dismiss))
                        }
                    }
                }
                is ToolTask.State.Failed -> {
                    val code = state.failure.code
                    Text(
                        text = messageOf(code)?.let { stringResource(it) } ?: stringResource(R.string.tool_error_generic, code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("paper.home.task.${task.id}.error"),
                    )
                    TextButton(onClick = { ToolTasks.dismiss(context, task) }, modifier = Modifier.testTag("paper.home.task.${task.id}.dismiss")) {
                        Text(stringResource(R.string.home_task_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(file: File, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag("paper.home.recent.${file.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_description_24), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                text = file.parent.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.testTag("paper.home.recent.${file.name}.remove")) {
            Icon(painterResource(R.drawable.ic_close_24), contentDescription = stringResource(R.string.home_recent_remove, file.name))
        }
    }
}

private const val MIME_PDF = "application/pdf"

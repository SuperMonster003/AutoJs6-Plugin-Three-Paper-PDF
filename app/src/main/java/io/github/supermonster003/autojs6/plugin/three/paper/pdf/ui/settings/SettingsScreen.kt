package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.BuildConfig
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.StandaloneActivity
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.AppearancePreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.HostAppearanceReader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.LauncherIconMode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.LauncherIcons
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.ThemeColorValue
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.Choice
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.ChoiceDialog
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.Hairline
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.MultiChoiceDialog
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.PaperScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.SectionHeader
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.SettingsRow
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.SwitchRow
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.ThemeColorDialog
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files.BrowseMode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files.FileBrowser
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.NotificationAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTaskService
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.rememberStorageAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.update.AppUpdateController
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.update.AppUpdateStore
import java.text.NumberFormat
import java.util.Locale

private enum class SettingsDialog { LANGUAGE, DARK_MODE, THEME_COLOR, LAUNCHER_ICON, OUTPUT_FOLDER, DPI, IGNORED }

/**
 * The settings destination of the toolbox (roadmap P4.1, D37, standalone settings specification, precedent 3-Slate
 * Studio): appearance (language, night mode, theme color, launcher icon; the first three follow AutoJs6 by default,
 * appendix D Q102), toolbox (the output folder, `Documents/3-Paper PDF/` by default, and the default resolution of PDF to
 * images; scripts choose their own outputs), permissions (all files access of D7, notifications, the task notification
 * channel) and about (version, release history, the update check with its automatic check and ignored releases).
 * [onHoldChange] tells the activity while a dialog or the folder browser is open, so an appearance change does not
 * recreate the screen under it.
 * zh-CN: 工具箱的设置目的地 (路线图 P4.1, D37, 独立设置规范, 先例 3-Slate Studio): 外观 (语言, 夜间模式, 主题色, 启动器图标; 前三项默认跟随
 * AutoJs6, 附录 D Q102), 工具箱 (输出目录, 默认为 `Documents/3-Paper PDF/`, 以及 PDF 转图片的默认分辨率; 脚本自行决定输出位置), 权限
 * (D7 的所有文件访问, 通知, 任务通知渠道) 与关于 (版本, 发行历史, 更新检查及其自动检查与已忽略版本). 对话框或目录浏览器打开期间
 * 经 [onHoldChange] 告知 Activity, 外观变化不会在其下重建界面.
 */
@Composable
internal fun SettingsScreen(
    activity: StandaloneActivity,
    updates: AppUpdateController,
    onBack: () -> Unit,
    onReleaseHistory: () -> Unit,
    onHoldChange: (Boolean) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    val storage = rememberStorageAccess()
    var notificationsGranted by remember { mutableStateOf(ToolTaskService.canNotify(activity)) }
    // A denial without rationale means "don't ask again": the next tap opens the app settings.
    // zh-CN: 无理由的拒绝即 "不再询问": 下次点按打开应用设置.
    var notificationsBlocked by remember { mutableStateOf(false) }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        notificationsBlocked = !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !activity.shouldShowRequestPermissionRationale(NotificationAccess.PERMISSION)
    }
    LifecycleResumeEffect(Unit) {
        // Back from the system pages: read the notification permission again. zh-CN: 从系统页面返回: 重新读取通知权限.
        notificationsGranted = ToolTaskService.canNotify(activity)
        onPauseOrDispose { }
    }
    LaunchedEffect(dialog, browsing) { onHoldChange(dialog != null || browsing) }
    DisposableEffect(Unit) { onDispose { onHoldChange(false) } }
    val pending = message
    LaunchedEffect(pending) {
        if (pending != null) {
            snackbar.showSnackbar(pending)
            message = null
        }
    }
    val actions = SettingsActions(activity) { message = it }

    if (browsing) {
        FileBrowser(
            mode = BrowseMode.FOLDER,
            title = stringResource(R.string.settings_output_folder),
            onDismiss = { browsing = false },
            onPick = { folders ->
                browsing = false
                val folder = folders.firstOrNull() ?: return@FileBrowser
                if (ToolboxPreferences.setOutputFolder(activity, folder)) revision++ else message = activity.getString(R.string.settings_save_failed)
            },
            start = ToolboxPreferences.outputFolder(activity),
            onRequestAccess = storage.request,
            accessGeneration = storage.generation,
        )
        return
    }

    val appearancePreferences = remember(revision) { AppearancePreferences.read(activity) }
    val labels = SettingsLabels(activity)
    PaperScreen(title = stringResource(R.string.settings_title), onBack = onBack, snackbarHostState = snackbar) { padding ->
        // A saved choice bumps the revision and rebuilds the rows from the stored values. zh-CN: 保存后修订号增加, 各行按存储值重建.
        key(revision) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("paper.settings")) {
                SectionHeader(stringResource(R.string.settings_appearance))
                SettingsRow(stringResource(R.string.settings_language), summary = labels.languageSummary(appearancePreferences.language), icon = R.drawable.ic_language_24,
                    onClick = { dialog = SettingsDialog.LANGUAGE }, modifier = Modifier.testTag("paper.settings.language"))
                Hairline()
                SettingsRow(stringResource(R.string.settings_night_mode), summary = labels.darkModeSummary(appearancePreferences.darkMode), icon = R.drawable.ic_dark_mode_24,
                    onClick = { dialog = SettingsDialog.DARK_MODE }, modifier = Modifier.testTag("paper.settings.night"))
                Hairline()
                SettingsRow(stringResource(R.string.settings_theme_color), summary = labels.colorSummary(appearancePreferences.color), icon = R.drawable.ic_palette_24,
                    onClick = { dialog = SettingsDialog.THEME_COLOR }, modifier = Modifier.testTag("paper.settings.color"))
                Hairline()
                SettingsRow(stringResource(R.string.settings_launcher_icon), summary = stringResource(iconLabel(LauncherIcons.current(activity))), icon = R.drawable.ic_apps_24,
                    onClick = { dialog = SettingsDialog.LAUNCHER_ICON }, modifier = Modifier.testTag("paper.settings.icon"))

                SectionHeader(stringResource(R.string.settings_toolbox))
                val chosenFolder = ToolboxPreferences.chosenOutputFolder(activity)
                SettingsRow(stringResource(R.string.settings_output_folder), summary = chosenFolder?.absolutePath ?: stringResource(R.string.settings_output_folder_default),
                    icon = R.drawable.ic_folder_24, onClick = { dialog = SettingsDialog.OUTPUT_FOLDER }, modifier = Modifier.testTag("paper.settings.outputFolder"))
                Hairline()
                SettingsRow(stringResource(R.string.settings_default_dpi), summary = dpiLabel(ToolboxPreferences.defaultDpi(activity)), icon = R.drawable.ic_image_24,
                    onClick = { dialog = SettingsDialog.DPI }, modifier = Modifier.testTag("paper.settings.dpi"))

                SectionHeader(stringResource(R.string.settings_permissions))
                SettingsRow(stringResource(R.string.settings_storage_permission),
                    summary = stringResource(if (storage.granted) R.string.settings_permission_granted else R.string.settings_storage_missing),
                    summaryColor = if (storage.granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    icon = R.drawable.ic_storage_24, showChevron = !storage.granted,
                    onClick = if (storage.granted) null else storage.request, modifier = Modifier.testTag("paper.settings.storagePermission"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Hairline()
                    SettingsRow(stringResource(R.string.settings_notification_permission),
                        summary = stringResource(if (notificationsGranted) R.string.settings_permission_granted else R.string.settings_notification_missing),
                        summaryColor = if (notificationsGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        icon = R.drawable.ic_notifications_24, showChevron = !notificationsGranted,
                        onClick = if (notificationsGranted) null else {
                            {
                                NotificationAccess.markAsked(activity)
                                if (notificationsBlocked) actions.openAppSettings() else notificationRequest.launch(NotificationAccess.PERMISSION)
                            }
                        },
                        modifier = Modifier.testTag("paper.settings.notificationPermission"))
                }
                Hairline()
                SettingsRow(stringResource(R.string.settings_task_notification), summary = stringResource(R.string.settings_task_notification_summary),
                    icon = R.drawable.ic_notifications_24, onClick = actions::openNotificationSettings, modifier = Modifier.testTag("paper.settings.taskNotification"))

                SectionHeader(stringResource(R.string.settings_about))
                SettingsRow(stringResource(R.string.settings_version),
                    summary = stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, stringResource(R.string.plugin_version_date)),
                    icon = R.drawable.ic_info_24, showChevron = false, modifier = Modifier.testTag("paper.settings.version"))
                Hairline()
                SettingsRow(stringResource(R.string.release_history_title), summary = stringResource(R.string.settings_release_history_summary), icon = R.drawable.ic_history_24,
                    onClick = onReleaseHistory, modifier = Modifier.testTag("paper.settings.releaseHistory"))
                Hairline()
                SettingsRow(stringResource(R.string.update_check), summary = stringResource(R.string.settings_update_summary), icon = R.drawable.ic_update_24,
                    onClick = updates::checkManually, modifier = Modifier.testTag("paper.settings.update"))
                Hairline()
                val store = remember { AppUpdateStore.of(activity) }
                var automatic by remember { mutableStateOf(store.automaticChecks) }
                SwitchRow(stringResource(R.string.update_automatic), automatic, { automatic = it; store.automaticChecks = it },
                    summary = stringResource(R.string.update_automatic_summary), icon = R.drawable.ic_notifications_24, modifier = Modifier.testTag("paper.settings.autoUpdate"))
                Hairline()
                val ignored = store.ignoredTags.size
                SettingsRow(stringResource(R.string.update_ignored),
                    summary = if (ignored == 0) stringResource(R.string.update_ignored_none) else stringResource(R.string.update_ignored_count, ignored),
                    icon = R.drawable.ic_close_24, enabled = ignored > 0, onClick = { dialog = SettingsDialog.IGNORED }, modifier = Modifier.testTag("paper.settings.ignored"))
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val close = { dialog = null }
    val saveAppearance: (AppearancePreferences) -> Unit = { next ->
        if (next != AppearancePreferences.read(activity)) {
            if (next.save(activity)) {
                revision++
                activity.applyAppearanceChange()
            } else {
                message = activity.getString(R.string.settings_save_failed)
            }
        }
    }
    when (dialog) {
        null -> Unit
        SettingsDialog.LANGUAGE -> {
            val current = AppearancePreferences.read(activity)
            ChoiceDialog(
                title = stringResource(R.string.settings_language),
                options = AppearancePreferences.LANGUAGES.map { Choice(labels.languageLabel(it), labels.followNote(it == AppearancePreferences.FOLLOW_HOST)) },
                selectedIndex = AppearancePreferences.LANGUAGES.indexOf(current.language).coerceAtLeast(0),
                onDismissRequest = close,
                onConfirm = { index -> close(); saveAppearance(current.copy(language = AppearancePreferences.LANGUAGES[index])) },
                tag = "paper.dialog.language",
            )
        }
        SettingsDialog.DARK_MODE -> {
            val current = AppearancePreferences.read(activity)
            ChoiceDialog(
                title = stringResource(R.string.settings_night_mode),
                options = AppearancePreferences.DARK_MODES.map { Choice(labels.darkModeLabel(it), labels.followNote(it == AppearancePreferences.FOLLOW_HOST)) },
                selectedIndex = AppearancePreferences.DARK_MODES.indexOf(current.darkMode).coerceAtLeast(0),
                onDismissRequest = close,
                onConfirm = { index -> close(); saveAppearance(current.copy(darkMode = AppearancePreferences.DARK_MODES[index])) },
                tag = "paper.dialog.night",
            )
        }
        SettingsDialog.THEME_COLOR -> {
            val current = AppearancePreferences.read(activity)
            ThemeColorDialog(
                currentColor = current.color,
                hostColor = HostAppearanceReader.cached()?.primary ?: AppearancePreferences.DEFAULT_COLOR,
                onDismissRequest = close,
                onConfirm = { color -> close(); saveAppearance(current.copy(color = color)) },
            )
        }
        SettingsDialog.LAUNCHER_ICON -> ChoiceDialog(
            title = stringResource(R.string.settings_launcher_icon),
            options = LauncherIconMode.entries.map { mode ->
                Choice(stringResource(iconLabel(mode)), when (mode) {
                    LauncherIconMode.AUTO -> stringResource(R.string.launcher_icon_auto_note)
                    LauncherIconMode.TRANSPARENT -> stringResource(R.string.launcher_icon_transparent_note)
                    else -> null
                })
            },
            selectedIndex = LauncherIcons.current(activity).ordinal,
            onDismissRequest = close,
            onConfirm = { index ->
                close()
                val result = runCatching { LauncherIcons.select(activity, LauncherIconMode.entries[index]) }
                revision++
                message = activity.getString(if (result.isSuccess) R.string.launcher_icon_applied_note else R.string.launcher_icon_failed)
            },
            tag = "paper.dialog.icon",
        )
        SettingsDialog.OUTPUT_FOLDER -> {
            val chosen = ToolboxPreferences.chosenOutputFolder(activity)
            ChoiceDialog(
                title = stringResource(R.string.settings_output_folder),
                message = stringResource(R.string.settings_output_folder_message),
                options = listOf(
                    Choice(stringResource(R.string.settings_output_folder_reset), ToolboxPreferences.defaultOutputFolder().absolutePath),
                    Choice(stringResource(R.string.settings_output_folder_choose), chosen?.absolutePath),
                ),
                selectedIndex = if (chosen == null) 0 else 1,
                onDismissRequest = close,
                onConfirm = { index ->
                    close()
                    if (index == 0) {
                        if (ToolboxPreferences.setOutputFolder(activity, null)) revision++ else message = activity.getString(R.string.settings_save_failed)
                    } else {
                        browsing = true
                    }
                },
                tag = "paper.dialog.outputFolder",
            )
        }
        SettingsDialog.DPI -> {
            val choices = ToolboxPreferences.DPI_CHOICES
            ChoiceDialog(
                title = stringResource(R.string.settings_default_dpi),
                message = stringResource(R.string.settings_default_dpi_message),
                options = choices.map { dpi -> Choice(dpiLabel(dpi), stringResource(dpiNote(dpi))) },
                selectedIndex = choices.indexOf(ToolboxPreferences.defaultDpi(activity)).coerceAtLeast(0),
                onDismissRequest = close,
                onConfirm = { index ->
                    close()
                    if (ToolboxPreferences.setDefaultDpi(activity, choices[index])) revision++ else message = activity.getString(R.string.settings_save_failed)
                },
                tag = "paper.dialog.dpi",
            )
        }
        SettingsDialog.IGNORED -> {
            val store = AppUpdateStore.of(activity)
            val tags = store.ignoredTags.sorted()
            MultiChoiceDialog(
                title = stringResource(R.string.update_ignored),
                message = stringResource(R.string.update_ignored_message),
                options = tags.map { Choice(it) },
                selected = emptySet(),
                onDismissRequest = close,
                onConfirm = { chosen -> close(); store.stopIgnoring(chosen.map(tags::get)); revision++ },
                confirmText = stringResource(R.string.update_stop_ignoring),
                requireOne = true,
                tag = "paper.dialog.ignored",
            )
        }
    }
}

/** The system pages the settings open; a failure becomes a message. zh-CN: 设置页打开的系统页面; 失败时给出提示. */
private class SettingsActions(private val activity: StandaloneActivity, private val onMessage: (String) -> Unit) {

    fun openAppSettings() {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
    }

    /** The settings of the task channel (API 26+), else the app details. zh-CN: 任务通知渠道的设置 (API 26+), 否则为应用详情. */
    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ToolTaskService.CHANNEL_TASKS))
        } else {
            openAppSettings()
        }
    }

    private fun open(intent: Intent) {
        runCatching { activity.startActivity(intent) }.onFailure {
            Log.w(TAG, "Cannot open ${intent.action}", it)
            onMessage(activity.getString(R.string.settings_open_failed))
        }
    }

    private companion object {
        const val TAG = "PaperSettings"
    }

}

/** Summaries and labels of the appearance rows. zh-CN: 外观各行的摘要与标签. */
private class SettingsLabels(private val activity: StandaloneActivity) {

    @Composable
    fun followNote(isFollow: Boolean): String? =
        if (isFollow && HostAppearanceReader.cached() == null) stringResource(R.string.settings_follow_autojs6_unavailable) else null

    @Composable
    fun languageSummary(value: String): String = when (value) {
        AppearancePreferences.FOLLOW_HOST -> stringResource(R.string.settings_follow_autojs6_summary, languageLabel(activity.appearance.language))
        else -> languageLabel(value)
    }

    @Composable
    fun darkModeSummary(value: String): String = when (value) {
        AppearancePreferences.FOLLOW_HOST -> stringResource(
            R.string.settings_follow_autojs6_summary,
            stringResource(if (activity.appearance.dark) R.string.settings_dark_mode_dark else R.string.settings_dark_mode_light),
        )
        else -> darkModeLabel(value)
    }

    @Composable
    fun colorSummary(color: Int?): String =
        if (color == null) stringResource(R.string.settings_follow_autojs6_summary, ThemeColorValue.hex(activity.appearance.primary)) else ThemeColorValue.hex(color)

    @Composable
    fun languageLabel(value: String): String = when (value) {
        AppearancePreferences.FOLLOW_HOST -> stringResource(R.string.settings_follow_autojs6)
        AppearancePreferences.FOLLOW_SYSTEM -> stringResource(R.string.settings_follow_system)
        "zh-Hans" -> stringResource(R.string.settings_language_zh_hans)
        "zh-Hant-HK" -> stringResource(R.string.settings_language_zh_hant_hk)
        "zh-Hant-TW" -> stringResource(R.string.settings_language_zh_hant_tw)
        "en" -> stringResource(R.string.settings_language_en)
        "fr" -> stringResource(R.string.settings_language_fr)
        "es" -> stringResource(R.string.settings_language_es)
        "ja" -> stringResource(R.string.settings_language_ja)
        "ko" -> stringResource(R.string.settings_language_ko)
        "ru" -> stringResource(R.string.settings_language_ru)
        "ar" -> stringResource(R.string.settings_language_ar)
        else -> Locale.forLanguageTag(value).getDisplayName(Locale.forLanguageTag(value)).ifBlank { value }
    }

    @Composable
    fun darkModeLabel(value: String): String = when (value) {
        AppearancePreferences.FOLLOW_SYSTEM -> stringResource(R.string.settings_follow_system)
        AppearancePreferences.LIGHT -> stringResource(R.string.settings_dark_mode_light)
        AppearancePreferences.DARK -> stringResource(R.string.settings_dark_mode_dark)
        else -> stringResource(R.string.settings_follow_autojs6)
    }

}

@Composable
private fun dpiLabel(dpi: Int): String = stringResource(R.string.settings_dpi_value, NumberFormat.getIntegerInstance().format(dpi))

@StringRes
private fun dpiNote(dpi: Int): Int = when {
    dpi < ToolboxPreferences.DEFAULT_DPI -> R.string.settings_dpi_screen_note
    dpi > ToolboxPreferences.DEFAULT_DPI -> R.string.settings_dpi_print_note
    else -> R.string.settings_dpi_default_note
}

@StringRes
private fun iconLabel(mode: LauncherIconMode): Int = when (mode) {
    LauncherIconMode.LIGHT -> R.string.launcher_icon_light
    LauncherIconMode.DARK -> R.string.launcher_icon_dark
    LauncherIconMode.AUTO -> R.string.launcher_icon_auto
    LauncherIconMode.TRANSPARENT -> R.string.launcher_icon_transparent
}

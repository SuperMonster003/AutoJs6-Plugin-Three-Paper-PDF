package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * All files access as screen state (roadmap D7, P4.1): [granted] is checked again on every resume, [generation] grows
 * whenever it changes (so lists depending on it reload), [request] opens the all files access page on Android 11+
 * (the page of this app, else the general list) or asks for the storage permissions before.
 * zh-CN: 作为界面状态的所有文件访问权限 (路线图 D7, P4.1): 每次恢复时重新检查 [granted], 其变化时 [generation] 递增 (依赖它的列表随之
 * 重新加载), [request] 在 Android 11 及以上打开所有文件访问页面 (本应用的页面, 否则为总列表), 之前的版本请求存储权限.
 */
internal class StorageAccessState(val granted: Boolean, val generation: Int, val request: () -> Unit)

@Composable
internal fun rememberStorageAccess(): StorageAccessState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.granted(context)) }
    var generation by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = {
        val now = StorageAccess.granted(context)
        if (now != granted) {
            granted = now
            generation++
        }
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val request: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val own = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
            try {
                settings.launch(own)
            } catch (e: ActivityNotFoundException) {
                runCatching { settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            runtime.launch(StorageAccess.legacyPermissions)
        }
    }
    return StorageAccessState(granted, generation, request)
}

/**
 * The notification permission of Android 13+ for the task notifications (roadmap D37, P4.2): asked once, when the first
 * toolbox task starts; afterwards only the settings screen leads to it.
 * zh-CN: Android 13 及以上任务通知所需的通知权限 (路线图 D37, P4.2): 只在第一个工具箱任务开始时询问一次; 之后只能从设置页前往.
 */
internal object NotificationAccess {

    private const val FILE = "toolbox"

    private const val KEY_ASKED = "notificationsAsked"

    fun shouldAsk(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !ToolTaskService.canNotify(context) &&
        !context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
    }

    /** `Manifest.permission.POST_NOTIFICATIONS`, spelled out for API levels before 33. zh-CN: 写成字面量, 以便在 API 33 之前的版本使用. */
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

}

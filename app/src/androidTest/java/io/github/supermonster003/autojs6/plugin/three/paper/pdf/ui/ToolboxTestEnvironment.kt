package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.PdfServiceHarness
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.AppearancePreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.NotificationAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.RecentFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTasks
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.update.AppUpdateStore
import java.io.File

/**
 * The settings and tasks the home screen device tests touch (roadmap P4.1 / P4.2): [prepare] saves the toolbox,
 * recent files, update and appearance preferences, turns the automatic update check off, grants the notification
 * permission (API 33+, so a start does not ask) and makes an empty working folder; [restore] stops and removes the
 * toolbox tasks, puts the preferences back and deletes the folder. The notification permission stays granted: revoking
 * a runtime permission kills the process under test.
 * zh-CN: 首页设备测试会触及的设置与任务 (路线图 P4.1 / P4.2): [prepare] 保存工具箱, 最近文件, 更新与外观偏好, 关闭自动更新检查, 授予通知
 * 权限 (API 33+, 使开始时不再询问), 并建立空的工作目录; [restore] 停止并移除工具箱任务, 还原偏好并删除该目录. 通知权限保持授予: 撤销运行
 * 时权限会结束被测进程.
 */
internal class ToolboxTestEnvironment(name: String) {

    val context: Context get() = PdfServiceHarness.context

    /** The working folder of the test, under the cache. zh-CN: 测试的工作目录, 位于缓存下. */
    val root = File(PdfServiceHarness.context.cacheDir, name)

    private var saved: Map<String, Map<String, Any?>> = emptyMap()

    fun prepare() {
        saved = FILES.associateWith { file -> prefs(file).all.toMap() }
        AppUpdateStore.of(context).automaticChecks = false
        NotificationAccess.markAsked(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, NotificationAccess.PERMISSION)
        }
        root.deleteRecursively()
        root.mkdirs()
    }

    fun restore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ToolTasks.active.forEach { it.cancel() } }
        PdfServiceHarness.eventually(TASKS_STOP_MILLIS) {
            var idle = false
            instrumentation.runOnMainSync { idle = ToolTasks.active.isEmpty() }
            idle
        }
        instrumentation.runOnMainSync { ToolTasks.tasks.toList().forEach { ToolTasks.dismiss(instrumentation.targetContext, it) } }
        FILES.forEach { file ->
            val editor = prefs(file).edit().clear()
            saved[file].orEmpty().forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        }
        root.deleteRecursively()
    }

    private fun prefs(file: String) = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    private companion object {
        val FILES = listOf(ToolboxPreferences.FILE, RecentFiles.FILE, AppUpdateStore.FILE, AppearancePreferences.FILE)
        const val TASKS_STOP_MILLIS = 20_000L
    }

}

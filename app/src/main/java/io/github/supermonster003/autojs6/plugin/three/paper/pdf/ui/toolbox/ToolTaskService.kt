package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.messageOf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.home.HomeActivity
import kotlin.math.roundToInt

/**
 * The data sync foreground service of the toolbox (roadmap D37, P4.2): [ToolTasks] starts it with the first task; it
 * shows the running task with its progress, a cancel action and the queued count (updated at most every
 * [UPDATE_INTERVAL_MILLIS]), posts one notification per finished task (removed when the task is dismissed in the app)
 * and stops itself when the queue is empty. When Android refuses the start the tasks still run while the process lives.
 * zh-CN: 工具箱的 dataSync 前台服务 (路线图 D37, P4.2): [ToolTasks] 在第一个任务时启动它; 它显示运行中的任务及其进度, 取消动作与
 * 排队数 (至多每 [UPDATE_INTERVAL_MILLIS] 更新一次), 每个结束的任务发一条通知 (在应用内移除该任务时撤下), 队列为空时自行停止.
 * Android 拒绝启动时, 只要进程存活, 任务仍会执行.
 */
class ToolTaskService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val tick = Runnable { update() }

    private val observer: () -> Unit = { changed() }

    private var foreground = false

    private var lastUpdate = 0L

    private val announced = HashSet<Int>()

    override fun onCreate() {
        super.onCreate()
        // Tasks that ended before the service came are not announced again. zh-CN: 服务启动前已结束的任务不再通知.
        ToolTasks.tasks.filter { it.finished }.forEach { announced += it.id }
        ToolTasks.observe(observer)
    }

    // ServiceCompat drops the service type below API 29, where FOREGROUND_SERVICE_TYPE_DATA_SYNC does not exist.
    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            ToolTasks.find(intent.getIntExtra(EXTRA_TASK, -1))?.cancel()
            if (!foreground) stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, progressNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            foreground = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        changed()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 15 limits data sync services to 6 hours a day; the tasks continue in the process. zh-CN: Android 15 限制 dataSync 服务每天 6 小时; 任务在进程中继续. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        ToolTasks.stopObserving(observer)
        super.onDestroy()
    }

    private fun changed() {
        ToolTasks.tasks.filter { it.finished && announced.add(it.id) }.forEach(::announce)
        if (ToolTasks.active.isEmpty()) {
            finish()
            return
        }
        val wait = lastUpdate + UPDATE_INTERVAL_MILLIS - SystemClock.uptimeMillis()
        handler.removeCallbacks(tick)
        if (wait <= 0) update() else handler.postDelayed(tick, wait)
    }

    private fun update() {
        lastUpdate = SystemClock.uptimeMillis()
        if (foreground && canNotify(this)) runCatching { manager(this)?.notify(NOTIFICATION_ID, progressNotification()) }
    }

    private fun finish() {
        handler.removeCallbacks(tick)
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun progressNotification(): Notification {
        val active = ToolTasks.active
        val current = active.firstOrNull { it.state == ToolTask.State.Running } ?: active.firstOrNull()
        val builder = NotificationCompat.Builder(this, CHANNEL_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentIntent(openApp(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (current == null) {
            return builder.setContentTitle(getString(R.string.notification_tasks_title)).setProgress(0, 0, true).build()
        }
        builder.setContentTitle(getString(current.kind.title))
            .setContentText(current.title)
            .setProgress(PERCENT, (current.progress * PERCENT).roundToInt().coerceIn(0, PERCENT), current.state != ToolTask.State.Running)
        val queued = active.size - 1
        if (queued > 0) builder.setSubText(getString(R.string.notification_tasks_queued, queued))
        val cancel = PendingIntent.getService(
            this,
            current.id,
            Intent(this, ToolTaskService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_TASK, current.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(0, getString(R.string.ui_dialog_cancel), cancel)
        return builder.build()
    }

    private fun announce(task: ToolTask) {
        if (!canNotify(this)) return
        val state = task.state
        val text = when (state) {
            is ToolTask.State.Done -> state.result.displayPath
            is ToolTask.State.Failed -> messageOf(state.failure.code)?.let(::getString) ?: getString(R.string.tool_error_generic, state.failure.code)
            else -> return
        }
        val title = getString(if (state is ToolTask.State.Done) R.string.notification_task_done else R.string.notification_task_failed, getString(task.kind.title))
        val notification = NotificationCompat.Builder(this, CHANNEL_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(this))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { manager(this)?.notify(finishedId(task.id), notification) }
    }

    internal companion object {

        private const val TAG = "PaperToolbox"

        /** The channel of the toolbox tasks. zh-CN: 工具箱任务的通知渠道. */
        const val CHANNEL_TASKS = "toolbox_tasks"

        const val NOTIFICATION_ID = 4_100

        private const val FINISHED_ID_BASE = 4_200

        const val UPDATE_INTERVAL_MILLIS = 500L

        private const val PERCENT = 100

        private const val ACTION_CANCEL = "io.github.supermonster003.autojs6.plugin.three.paper.pdf.action.CANCEL_TOOL_TASK"

        private const val EXTRA_TASK = "task"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ToolTaskService::class.java))
            } catch (e: RuntimeException) {
                // ForegroundServiceStartNotAllowedException on Android 12+ when the plugin is in the background.
                // zh-CN: 插件处于后台时, Android 12 及以上会抛 ForegroundServiceStartNotAllowedException.
                Log.w(TAG, "cannot start the foreground service", e)
            }
        }

        /** Creates the channel; creating it again only refreshes its localized name. zh-CN: 创建通知渠道; 重复创建只会刷新其本地化名称. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_TASKS, context.getString(R.string.notification_channel_tasks), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            manager(context)?.createNotificationChannel(channel)
        }

        /** Removes the finished notification of task [id]. zh-CN: 撤下任务 [id] 的结束通知. */
        fun withdraw(context: Context, id: Int) {
            runCatching { manager(context)?.cancel(finishedId(id)) }
        }

        fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun finishedId(id: Int) = FINISHED_ID_BASE + id

        private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    }

}

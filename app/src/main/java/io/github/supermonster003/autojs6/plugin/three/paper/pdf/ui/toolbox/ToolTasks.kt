package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.LocalFolderOutput
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolRunner
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolEngine
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolInput
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolResult
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/**
 * One toolbox run (roadmap P4.2): the tool, its inputs and options and where the result goes. [state] and [progress]
 * change on the main thread.
 * zh-CN: 一次工具箱执行 (路线图 P4.2): 工具, 输入与选项, 以及结果的位置. [state] 与 [progress] 在主线程上变化.
 */
internal class ToolTask(
    val id: Int,
    val kind: ToolKind,
    /** The first input's name, shown by the home card and the notification. zh-CN: 第一个输入的名称, 首页卡片与通知显示它. */
    val title: String,
    val inputCount: Int,
    internal val inputs: List<ToolInput>,
    internal val options: ToolOptions,
    val folder: File,
) {

    sealed interface State {
        data object Queued : State
        data object Running : State
        class Done(val result: ToolResult) : State
        class Failed(val failure: ToolFailure) : State
    }

    var state by mutableStateOf<State>(State.Queued)
        internal set

    var progress by mutableFloatStateOf(0f)
        internal set

    val finished: Boolean get() = state is State.Done || state is State.Failed

    @Volatile
    internal var cancelled = false
        private set

    @Volatile
    internal var runner: PdfToolRunner? = null

    /** Stops the task; a queued task fails with `CANCELLED` without starting. zh-CN: 停止任务; 排队中的任务不启动, 直接以 `CANCELLED` 失败. */
    fun cancel() {
        cancelled = true
        runner?.cancel()
    }

}

/**
 * The toolbox task queue (roadmap P4.2, appendix D Q103): one task at a time, in the order started, on one background
 * thread of the plugin process, kept running by [ToolTaskService] after the wizard is left. Each task holds the shared
 * engine ([ToolEngine]) while it runs and writes through a [LocalFolderOutput]; a single PDF result joins the recent
 * files. [tasks] (main thread) lists the tasks until the home screen or the wizard dismisses them.
 * zh-CN: 工具箱任务队列 (路线图 P4.2, 附录 D Q103): 在插件进程的一个后台线程上按开始顺序一次执行一个任务, 离开向导后由
 * [ToolTaskService] 保持运行. 每个任务运行期间持有共用引擎 ([ToolEngine]), 经 [LocalFolderOutput] 写入; 单个 PDF 结果加入最近文件.
 * [tasks] (主线程) 列出任务, 直到首页或向导将其移除.
 */
internal object ToolTasks {

    private val main = Handler(Looper.getMainLooper())

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "3-paper-toolbox").apply { isDaemon = true }
    }

    private var nextId = 1

    private val observers = LinkedHashSet<() -> Unit>()

    val tasks = mutableStateListOf<ToolTask>()

    val active: List<ToolTask> get() = tasks.filter { !it.finished }

    /** The folder of the intermediate files of organizing pages. zh-CN: 页面整理中间文件的目录. */
    fun workDirectory(context: Context): File = File(context.applicationContext.cacheDir, "toolbox/organize")

    /** Queues a task (main thread) and makes sure the foreground service runs. zh-CN: 排入一个任务 (主线程) 并确保前台服务在运行. */
    fun submit(context: Context, kind: ToolKind, inputs: List<ToolInput>, options: ToolOptions, folder: File): ToolTask {
        val app = context.applicationContext
        val task = ToolTask(nextId++, kind, inputs.first().target.displayName, inputs.size, inputs, options, folder)
        tasks += task
        changed()
        ToolTaskService.start(app)
        executor.execute { execute(app, task) }
        return task
    }

    fun find(id: Int): ToolTask? = tasks.firstOrNull { it.id == id }

    /** Removes a finished task from the list with its notification. zh-CN: 从列表中移除一个已结束的任务及其通知. */
    fun dismiss(context: Context, task: ToolTask) {
        if (!task.finished || !tasks.remove(task)) return
        ToolTaskService.withdraw(context.applicationContext, task.id)
        changed()
    }

    /** Calls [observer] on the main thread after every change of a task. zh-CN: 每次任务变化后在主线程上调用 [observer]. */
    fun observe(observer: () -> Unit) {
        observers += observer
    }

    fun stopObserving(observer: () -> Unit) {
        observers -= observer
    }

    private fun changed() {
        observers.toList().forEach { it() }
    }

    private fun post(block: () -> Unit) {
        main.post {
            block()
            changed()
        }
    }

    private fun execute(app: Context, task: ToolTask) {
        if (task.cancelled) {
            post { task.state = ToolTask.State.Failed(ToolFailure(PdfErrorCodes.CANCELLED, null)) }
            return
        }
        post { task.state = ToolTask.State.Running }
        val engine = ToolEngine.acquire(app)
        val outcome = try {
            val open: (android.net.Uri) -> ParcelFileDescriptor = { uri ->
                app.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())
            }
            val runner = PdfToolRunner(engine, open, LocalFolderOutput(task.folder), { fraction ->
                main.post {
                    if (task.state == ToolTask.State.Running) {
                        task.progress = fraction.coerceIn(0f, 1f)
                        changed()
                    }
                }
            }, workDirectory(app))
            task.runner = runner
            if (task.cancelled) runner.cancel()
            val result = runner.run(task.inputs, task.options)
            val file = File(result.displayPath)
            if (file.isFile && file.name.endsWith(PDF_EXTENSION, ignoreCase = true)) RecentFiles.add(app, file)
            ToolTask.State.Done(result)
        } catch (e: ToolFailure) {
            ToolTask.State.Failed(e)
        } catch (e: Exception) {
            ToolTask.State.Failed(ToolFailure(PdfErrorCodes.INTERNAL, e.message ?: e.javaClass.simpleName))
        } finally {
            task.runner = null
            ToolEngine.release()
        }
        post {
            task.progress = if (outcome is ToolTask.State.Done) 1f else task.progress
            task.state = outcome
        }
    }

    private const val PDF_EXTENSION = ".pdf"

}

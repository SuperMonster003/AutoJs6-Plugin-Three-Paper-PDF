package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.app.Application
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The state of one file manager tool screen (roadmap P3.3, appendix D Q70), kept across configuration changes: reading
 * the selected files (page count, password), the options of the tool, progress and the outcome. Engine and host calls
 * run on one worker thread; the state lives on the main thread.
 * zh-CN: 一个文件管理器工具界面的状态 (路线图 P3.3, 附录 D Q70), 在配置变更后保留: 读取选中的文件 (页数, 密码), 工具的选项, 进度与
 * 结果. 引擎与宿主调用在一个工作线程上运行; 状态都在主线程上.
 */
internal class PdfToolViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface Phase {
        data object Inspecting : Phase
        data object Ready : Phase
        data object Running : Phase
        class Done(val result: ToolResult) : Phase
        class Failed(val failure: ToolFailure) : Phase
    }

    enum class SplitMode { EVERY, RANGES }

    /** One selected file and what reading it found. zh-CN: 一个选中的文件及读取它的结果. */
    class Input(val target: ToolTarget) {

        var pageCount by mutableIntStateOf(0)
            internal set

        var encrypted by mutableStateOf(false)
            internal set

        /** It opens only with a password not given yet. zh-CN: 需要尚未给出的密码才能打开. */
        var locked by mutableStateOf(false)
            internal set

        var wrongPassword by mutableStateOf(false)
            internal set

        var unlocking by mutableStateOf(false)
            internal set

        /** The password it opened with. zh-CN: 打开它所用的密码. */
        var password by mutableStateOf<String?>(null)
            internal set

        /** Why it cannot be read, a code of `PdfErrorCodes`. zh-CN: 无法读取的原因, `PdfErrorCodes` 中的错误码. */
        var error by mutableStateOf<String?>(null)
            internal set

        val usable get() = !locked && error == null

    }

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "3-paper-tool").apply { isDaemon = true }
    }

    private var started = false
    private var acquired = false

    @Volatile
    private var plugin: IPdfPlugin? = null

    @Volatile
    private var session: IExplorerActionHostSession? = null

    private var runner: PdfToolRunner? = null

    var tool by mutableStateOf<ExplorerTool?>(null)
        private set
    var parentPath by mutableStateOf<String?>(null)
        private set
    val inputs = mutableStateListOf<Input>()
    var phase by mutableStateOf<Phase>(Phase.Inspecting)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    var keepOutline by mutableStateOf(true)
    var splitMode by mutableStateOf(SplitMode.EVERY)
    var splitEvery by mutableStateOf("1")
    var splitRanges by mutableStateOf("")
    var imageFormat by mutableStateOf(PdfContract.FORMAT_PNG)
    var imageDpi by mutableIntStateOf(DEFAULT_DPI)
    var pages by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var decryptPassword by mutableStateOf("")

    /**
     * Starts the tool of [request] once; a recreated Activity calls it again and keeps the state. Without a usable
     * request the screen fails with [NO_FILES].
     * zh-CN: 为 [request] 启动工具, 只执行一次; 重建的 Activity 再次调用时保留状态. 没有可用的请求时界面以 [NO_FILES] 失败.
     */
    fun start(request: ToolRequest?) {
        if (started) return
        started = true
        if (request == null || request.tool == ExplorerTool.OPEN) {
            phase = Phase.Failed(ToolFailure(NO_FILES, null))
            return
        }
        tool = request.tool
        parentPath = request.parentDisplayPath
        val entries = request.targets.map(::Input)
        inputs += entries
        acquired = true
        worker.execute {
            val engine = ToolEngine.acquire(getApplication())
            plugin = engine
            val host = ToolRequest.hostSessionOf(request.session)
            // The host aborts what this process left open if it dies. zh-CN: 本进程死亡时宿主中止它留下的未完成输出.
            host?.let { runCatching { it.attachClient(Binder()) } }
            session = host
            entries.forEach { input ->
                val found = inspect(engine, input.target, null)
                main.post { apply(input, found, null) }
            }
            main.post { if (phase == Phase.Inspecting) phase = Phase.Ready }
        }
    }

    /** Tries [password] on the locked input [index]. zh-CN: 用 [password] 尝试打开被锁定的第 [index] 个输入. */
    fun unlock(index: Int, password: String) {
        val input = inputs.getOrNull(index) ?: return
        val engine = plugin ?: return
        input.unlocking = true
        worker.execute {
            val found = inspect(engine, input.target, password)
            main.post {
                input.unlocking = false
                apply(input, found, password)
            }
        }
    }

    /** Moves input [index] by [delta] places (the merge order). zh-CN: 把第 [index] 个输入移动 [delta] 位 (合并顺序). */
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (phase != Phase.Ready || index !in inputs.indices || target !in inputs.indices) return
        inputs.add(target, inputs.removeAt(index))
    }

    /** The options the screen shows, or null while they are incomplete or invalid. zh-CN: 界面上的选项; 不完整或无效时为 null. */
    fun options(): ToolOptions? {
        val first = inputs.firstOrNull() ?: return null
        return when (tool) {
            ExplorerTool.MERGE -> ToolOptions.Merge(keepOutline).takeIf { inputs.size >= 2 }
            ExplorerTool.SPLIT -> when (splitMode) {
                SplitMode.EVERY -> splitEveryValue()?.let { ToolOptions.Split(it, null) }
                SplitMode.RANGES -> splitRangeList()?.let { ToolOptions.Split(null, it) }
            }
            ExplorerTool.TO_IMAGES -> pagesOrAll(first.pageCount)?.let { ToolOptions.ToImages(imageFormat, imageDpi, it.value) }
            ExplorerTool.EXTRACT_TEXT -> pagesOrAll(first.pageCount)?.let { ToolOptions.ExtractText(it.value) }
            ExplorerTool.ENCRYPT -> ToolOptions.Encrypt(newPassword).takeIf { encryptPasswordProblem() == null }
            ExplorerTool.DECRYPT -> ToolOptions.Decrypt.takeIf { first.encrypted }
            ExplorerTool.OPEN, null -> null
        }
    }

    /** The page range typed for the images and text tools is not valid. zh-CN: 为图片与文本工具输入的页码范围无效. */
    fun pagesInvalid(): Boolean = pagesOrAll(inputs.firstOrNull()?.pageCount ?: 0) == null

    fun splitEveryInvalid(): Boolean = splitEvery.isNotBlank() && splitEveryValue() == null

    fun splitRangesInvalid(): Boolean = splitRanges.isNotBlank() && splitRangeList() == null

    /** Why the new password cannot be used: `EMPTY`, `TOO_LONG`, `MISMATCH` or null. zh-CN: 新密码不能使用的原因: `EMPTY`, `TOO_LONG`, `MISMATCH` 或 null. */
    fun encryptPasswordProblem(): String? = when {
        newPassword.isEmpty() -> PASSWORD_EMPTY
        newPassword.length > PdfContract.MAX_PASSWORD_LENGTH -> PASSWORD_TOO_LONG
        newPassword != confirmPassword -> PASSWORD_MISMATCH
        else -> null
    }

    fun canStart(): Boolean = phase == Phase.Ready && inputs.isNotEmpty() && inputs.all { it.usable } && session != null && options() != null

    fun run() {
        val tool = tool ?: return
        val engine = plugin ?: return
        val options = options() ?: return
        if (phase != Phase.Ready) return
        val host = session
        if (host == null) {
            phase = Phase.Failed(ToolFailure(ToolFailure.OUTPUT_FAILED, "no host session"))
            return
        }
        val jobInputs = inputs.map { input ->
            val password = if (tool == ExplorerTool.DECRYPT) decryptPassword.ifEmpty { null } ?: input.password else input.password
            ToolInput(input.target, password)
        }
        val job = PdfToolRunner(engine, ::open, HostSessionOutput(host)) { fraction ->
            main.post { if (phase == Phase.Running) progress = fraction.coerceIn(0f, 1f) }
        }
        runner = job
        progress = 0f
        phase = Phase.Running
        worker.execute {
            val outcome = try {
                Phase.Done(job.run(tool, jobInputs, options))
            } catch (e: ToolFailure) {
                Phase.Failed(e)
            } catch (e: Exception) {
                Phase.Failed(ToolFailure(PdfErrorCodes.INTERNAL, e.message ?: e.javaClass.simpleName))
            }
            main.post {
                if (runner === job) {
                    runner = null
                    phase = outcome
                }
            }
        }
    }

    fun cancel() {
        runner?.cancel()
    }

    /**
     * Back to the options after a failure. When a password or the permissions stopped the tool, the encrypted inputs
     * ask for a password again, so the owner password can be given (an input opened with the open password may not be
     * split or re-encrypted, roadmap appendix D Q30).
     * zh-CN: 失败后回到选项. 若是密码或权限使工具停止, 加密的输入重新询问密码, 以便给出所有者密码 (用打开密码打开的输入不能被拆分或
     * 重新加密, 路线图附录 D Q30).
     */
    fun retry() {
        val failed = phase as? Phase.Failed ?: return
        if (inputs.isEmpty()) return
        val code = failed.failure.code
        val password = code == PdfErrorCodes.PERMISSION_DENIED || code == PdfErrorCodes.WRONG_PASSWORD || code == PdfErrorCodes.PASSWORD_REQUIRED
        if (password && tool != ExplorerTool.DECRYPT) {
            inputs.filter { it.encrypted && it.error == null }.forEach { input ->
                input.locked = true
                input.wrongPassword = false
            }
        }
        phase = Phase.Ready
    }

    override fun onCleared() {
        runner?.cancel()
        runner = null
        if (acquired) {
            // After a running tool stops: close the host session, then give the engine back. zh-CN: 等运行中的工具停止后关闭宿主会话, 再归还引擎.
            worker.execute {
                session?.let { runCatching { it.close() } }
                ToolEngine.release()
            }
        }
        worker.shutdown()
    }

    private fun apply(input: Input, found: Inspection, password: String?) {
        when (found) {
            is Inspection.Opened -> {
                input.pageCount = found.pageCount
                input.encrypted = found.encrypted
                input.password = password
                input.locked = false
                input.wrongPassword = false
                input.error = null
                if (tool == ExplorerTool.DECRYPT && password != null && decryptPassword.isEmpty()) decryptPassword = password
            }
            is Inspection.Locked -> {
                input.encrypted = true
                input.locked = true
                input.wrongPassword = password != null
            }
            is Inspection.Unreadable -> input.error = found.code
        }
    }

    private fun inspect(engine: IPdfPlugin, target: ToolTarget, password: String?): Inspection {
        val source = try {
            open(target.uri)
        } catch (e: FileNotFoundException) {
            return Inspection.Unreadable(PdfErrorCodes.FILE_NOT_FOUND)
        } catch (e: SecurityException) {
            return Inspection.Unreadable(PdfErrorCodes.PERMISSION_DENIED)
        } catch (e: Exception) {
            return Inspection.Unreadable(PdfErrorCodes.IO_ERROR)
        }
        val options = Bundle().apply {
            putInt(PdfContract.KEY_CONTRACT_VERSION, ThreePaperPdfContract.CONTRACT_VERSION)
            password?.let { putString(PdfContract.KEY_PASSWORD, it) }
            putString(PdfContract.KEY_DISPLAY_NAME, target.displayName)
        }
        // open() closes the source. zh-CN: open() 会关闭源.
        val document = try {
            engine.open(source, options)
        } catch (e: RuntimeException) {
            val code = PdfErrorCodes.decode(e.message)?.first ?: PdfErrorCodes.INTERNAL
            return if (code == PdfErrorCodes.PASSWORD_REQUIRED || code == PdfErrorCodes.WRONG_PASSWORD) Inspection.Locked else Inspection.Unreadable(code)
        }
        return try {
            val info = document.info
            val code = info?.getString(PdfContract.KEY_ERROR_CODE)
            if (info == null || code != null) {
                Inspection.Unreadable(code ?: PdfErrorCodes.INTERNAL)
            } else {
                Inspection.Opened(info.getInt(PdfContract.KEY_PAGE_COUNT), info.getBoolean(PdfContract.KEY_ENCRYPTED))
            }
        } catch (e: RuntimeException) {
            Inspection.Unreadable(PdfErrorCodes.decode(e.message)?.first ?: PdfErrorCodes.INTERNAL)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun open(uri: Uri): ParcelFileDescriptor =
        getApplication<Application>().contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())

    private fun splitEveryValue(): Int? {
        val count = inputs.firstOrNull()?.pageCount ?: return null
        return splitEvery.trim().toIntOrNull()?.takeIf { it in 1..maxOf(1, count) }
    }

    private fun splitRangeList(): List<String>? {
        val count = inputs.firstOrNull()?.pageCount ?: return null
        val ranges = splitRanges.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (ranges.isEmpty() || ranges.size > PdfContract.MAX_SPLIT_OUTPUTS) return null
        return ranges.takeIf { list -> list.all { validRange(it, count) } }
    }

    /** The typed page range: a holder of null for every page, or null when it is not valid. zh-CN: 输入的页码范围: 全部页时为装着 null 的容器, 无效时为 null. */
    private fun pagesOrAll(pageCount: Int): Holder<String?>? {
        val spec = pages.trim()
        if (spec.isEmpty()) return Holder(null)
        return if (validRange(spec, pageCount)) Holder(spec) else null
    }

    private fun validRange(spec: String, pageCount: Int): Boolean = try {
        PageRanges.parse(spec, pageCount).isNotEmpty()
    } catch (e: PdfFailure) {
        false
    }

    private class Holder<T>(val value: T)

    private sealed interface Inspection {
        class Opened(val pageCount: Int, val encrypted: Boolean) : Inspection
        data object Locked : Inspection
        class Unreadable(val code: String) : Inspection
    }

    companion object {

        /** No usable file arrived with the request. zh-CN: 请求没有带来可用的文件. */
        const val NO_FILES = "NO_FILES"

        const val PASSWORD_EMPTY = "EMPTY"

        const val PASSWORD_TOO_LONG = "TOO_LONG"

        const val PASSWORD_MISMATCH = "MISMATCH"

        const val DEFAULT_DPI = 144

        val DPI_CHOICES = listOf(72, 144, 300)

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.app.Application
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptRequest
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptRun
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptSource
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfEncryption
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTask
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolTasks
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The state of one tool screen, kept across configuration changes: reading the input files (page count, password), the
 * options of the tool, progress and the outcome. The file manager starts it with a request (roadmap P3.3, appendix D
 * Q70; results go next to the selected files through the host session, run here); the toolbox starts it with a tool
 * (roadmap P4.2; the user adds the files, results go to the output folder, run by [ToolTasks] in the foreground
 * service so leaving the screen does not stop them). Both share the options, the checks and [PdfToolRunner]. Engine
 * and host calls run on one worker thread; the state lives on the main thread.
 * zh-CN: 一个工具界面的状态, 在配置变更后保留: 读取输入文件 (页数, 密码), 工具的选项, 进度与结果. 文件管理器用请求启动它 (路线图 P3.3,
 * 附录 D Q70; 结果经宿主会话写到选中文件旁边, 在这里执行); 工具箱用工具启动它 (路线图 P4.2; 由用户添加文件, 结果写入输出目录, 由
 * [ToolTasks] 在前台服务中执行, 离开界面也不会停止). 两者共用选项, 校验与 [PdfToolRunner]. 引擎与宿主调用在一个工作线程上运行;
 * 状态都在主线程上.
 */
internal class PdfToolViewModel(application: Application, saved: SavedStateHandle) : AndroidViewModel(application) {

    sealed interface Phase {
        data object Inspecting : Phase
        data object Ready : Phase
        data object Running : Phase
        class Done(val result: ToolResult) : Phase
        class Failed(val failure: ToolFailure) : Phase
    }

    enum class SplitMode { EVERY, RANGES }

    /** One input file and what reading it found. zh-CN: 一个输入文件及读取它的结果. */
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

        /** Still being read (toolbox inputs added later). zh-CN: 仍在读取中 (工具箱中后来添加的输入). */
        var inspecting by mutableStateOf(false)
            internal set

        /** The password it opened with. zh-CN: 打开它所用的密码. */
        var password by mutableStateOf<String?>(null)
            internal set

        /**
         * What is typed in its password field before unlocking: kept here, in memory only, so it survives a rotation
         * without entering the saved state (E4 feedback F3).
         * zh-CN: 解锁前在其密码框中输入的内容: 只保存在内存中的这里, 因此旋转后仍在, 但不进入保存的状态 (E4 反馈 F3).
         */
        var passwordDraft by mutableStateOf("")

        /** Why it cannot be read, a code of `PdfErrorCodes`. zh-CN: 无法读取的原因, `PdfErrorCodes` 中的错误码. */
        var error by mutableStateOf<String?>(null)
            internal set

        val usable get() = !locked && error == null && !inspecting

    }

    /** One page of the organize tool: [page] of the source (from 1) and the clockwise turn added to it. zh-CN: 页面整理中的一页: 源文档的第 [page] 页 (从 1 起) 及为它追加的顺时针旋转. */
    class Page(val page: Int) {

        var rotation by mutableIntStateOf(0)
            internal set

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

    var kind by mutableStateOf<ToolKind?>(null)
        private set

    /** The toolbox started the screen: inputs can be added and removed, results go to [outputFolder]. zh-CN: 由工具箱启动: 可以增删输入, 结果写入 [outputFolder]. */
    var toolbox by mutableStateOf(false)
        private set

    /** Where the toolbox writes the results, for this run only. zh-CN: 工具箱写入结果的位置, 只对本次执行有效. */
    var outputFolder by mutableStateOf<File?>(null)

    var parentPath by mutableStateOf<String?>(null)
        private set
    val inputs = mutableStateListOf<Input>()

    private var localPhase by mutableStateOf<Phase>(Phase.Inspecting)
    private var localProgress by mutableFloatStateOf(0f)

    /** The toolbox task of the last start, while the screen shows it. zh-CN: 最近一次开始的工具箱任务, 界面显示它期间保留. */
    var task by mutableStateOf<ToolTask?>(null)
        private set

    /**
     * What the last start asked for, so the result page can write it as a script (roadmap P6.13); null before a start
     * and after the screen was recreated from a saved state (the result page then shows no script).
     * zh-CN: 最近一次开始所请求的内容, 供结果页写成脚本 (路线图 P6.13); 开始之前, 以及界面从保存的状态重建之后为 null (此时结果页不显示脚本).
     */
    var scriptRun by mutableStateOf<ScriptRun?>(null)
        private set

    val phase: Phase by derivedStateOf {
        when (val state = task?.state) {
            null -> localPhase
            ToolTask.State.Queued, ToolTask.State.Running -> Phase.Running
            is ToolTask.State.Done -> Phase.Done(state.result)
            is ToolTask.State.Failed -> Phase.Failed(state.failure)
        }
    }

    val progress: Float get() = task?.progress ?: localProgress

    /** The toolbox task waits for an earlier one (appendix D Q103). zh-CN: 工具箱任务在等待更早的任务 (附录 D Q103). */
    val queued: Boolean get() = task?.state == ToolTask.State.Queued

    var keepOutline by mutableStateOf(true)
    var splitMode by mutableStateOf(SplitMode.EVERY)
    var splitEvery by mutableStateOf("1")
    var splitRanges by mutableStateOf("")
    var imageFormat by mutableStateOf(PdfContract.FORMAT_PNG)
    var imageDpi by mutableIntStateOf(ToolboxPreferences.DEFAULT_DPI)
    var pages by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var decryptPassword by mutableStateOf("")
    var pageSize by mutableStateOf(PdfContract.PAGE_SIZE_FIT)
    var orientation by mutableStateOf(PdfContract.ORIENTATION_AUTO)
    var margin by mutableIntStateOf(MARGIN_CHOICES.first())

    /** The pages of the organize tool in their new order; removed pages are left out. zh-CN: 页面整理中按新顺序排列的页; 删除的页不在其中. */
    val organized = mutableStateListOf<Page>()

    /** The thumbnails of the organize tool, while its input is open. zh-CN: 页面整理的缩略图, 在其输入打开期间存在. */
    var thumbnails by mutableStateOf<PageThumbnails?>(null)
        private set

    /** The options of the tools added in 1.1.0 (roadmap P6.12). zh-CN: 1.1.0 新增工具的选项 (路线图 P6.12). */
    // Their typed "Custom" values live in [saved] (appendix D Q265 (b)). zh-CN: 其输入的 "自定义" 值保存在 [saved] 中 (附录 D Q265 (b)).
    val watermark = WatermarkState(saved)
    val pageNumbers = PageNumbersState(saved)
    val compress = CompressState(saved)

    /** The form fields of the fill-form tool, read when its input opens. zh-CN: 表单填写工具的字段, 在其输入打开时读取. */
    val fillForm = FillFormState()

    /** The facts, metadata and outline of the properties tool, read when its input opens. zh-CN: 文档属性工具的文件信息, 元数据与书签, 在其输入打开时读取. */
    val properties = PropertiesState()

    /** The single-page preview, while the input of the watermark or page numbers tool is open. zh-CN: 单页预览, 在水印或页码工具的输入打开期间存在. */
    var preview by mutableStateOf<PagePreview?>(null)
        private set

    /** The page the preview shows (from 1), turned with the arrows under it (E4 feedback F4). zh-CN: 预览显示的页 (从 1 起), 用其下方的箭头翻页 (E4 反馈 F4). */
    var previewPage by mutableIntStateOf(1)
        private set

    /** The range text the preview page last followed, null before the first. zh-CN: 预览页上一次跟随的范围文字, 第一次之前为 null. */
    private var previewRange: String? = null

    /** The newest reading of form fields or properties (main thread). zh-CN: 最新一次读取表单字段或文档属性 (主线程). */
    private var detailsGeneration = 0

    /**
     * Starts the tool of the file manager [request] once; a recreated Activity calls it again and keeps the state.
     * Without a usable request the screen fails with [NO_FILES].
     * zh-CN: 为文件管理器的 [request] 启动工具, 只执行一次; 重建的 Activity 再次调用时保留状态. 没有可用的请求时界面以 [NO_FILES] 失败.
     */
    fun start(request: ToolRequest?) {
        if (started) return
        started = true
        val requested = request?.tool?.kind
        if (request == null || requested == null) {
            localPhase = Phase.Failed(ToolFailure(NO_FILES, null))
            return
        }
        kind = requested
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
            main.post { if (localPhase == Phase.Inspecting) localPhase = Phase.Ready }
        }
    }

    /**
     * Starts the toolbox wizard of [tool] once (roadmap P4.2): no inputs yet, the output folder and resolution from the
     * toolbox settings.
     * zh-CN: 启动 [tool] 的工具箱向导, 只执行一次 (路线图 P4.2): 尚无输入, 输出目录与分辨率取自工具箱设置.
     */
    fun startToolbox(tool: ToolKind) {
        if (started) return
        started = true
        toolbox = true
        kind = tool
        val app = getApplication<Application>()
        outputFolder = ToolboxPreferences.outputFolder(app)
        imageDpi = ToolboxPreferences.defaultDpi(app)
        acquired = true
        worker.execute {
            plugin = ToolEngine.acquire(app)
            main.post { if (localPhase == Phase.Inspecting) localPhase = Phase.Ready }
        }
    }

    /**
     * Adds toolbox inputs: a one-file tool replaces its input, the others append the new files in order (at most
     * `MAX_RUN_INPUTS`). PDF inputs are read in the background; images are checked by the operation.
     * zh-CN: 添加工具箱输入: 单文件工具替换其输入, 其他工具按顺序追加新文件 (至多 `MAX_RUN_INPUTS` 个). PDF 输入在后台读取; 图片由操作本身校验.
     */
    fun addTargets(targets: List<ToolTarget>) {
        val tool = kind ?: return
        if (!toolbox || phase != Phase.Ready || targets.isEmpty()) return
        val fresh = if (tool.inputs == ToolKind.Inputs.ONE_PDF) {
            closeThumbnails()
            closeDetails()
            organized.clear()
            inputs.clear()
            pages = ""
            decryptPassword = ""
            listOf(targets.first())
        } else {
            val known = inputs.map { it.target.uri }.toMutableSet()
            targets.filter { known.add(it.uri) }.take(maxOf(0, PdfContract.MAX_RUN_INPUTS - inputs.size))
        }
        val entries = fresh.map(::Input)
        inputs += entries
        if (tool.inputs == ToolKind.Inputs.IMAGES) return
        entries.forEach { it.inspecting = true }
        worker.execute {
            val engine = plugin ?: return@execute
            entries.forEach { input ->
                val found = inspect(engine, input.target, null)
                main.post {
                    input.inspecting = false
                    apply(input, found, null)
                }
            }
        }
    }

    /** Removes toolbox input [index]. zh-CN: 移除第 [index] 个工具箱输入. */
    fun remove(index: Int) {
        if (!toolbox || phase != Phase.Ready || index !in inputs.indices) return
        inputs.removeAt(index)
        if (inputs.isEmpty()) {
            closeThumbnails()
            closeDetails()
            organized.clear()
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

    /** Moves input [index] by [delta] places (the merge and images order). zh-CN: 把第 [index] 个输入移动 [delta] 位 (合并与图片的顺序). */
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (phase != Phase.Ready || index !in inputs.indices || target !in inputs.indices) return
        inputs.add(target, inputs.removeAt(index))
    }

    /** Moves organized page [from] to position [to]. zh-CN: 把整理中的第 [from] 页移到位置 [to]. */
    fun movePage(from: Int, to: Int) {
        if (phase != Phase.Ready || from !in organized.indices || to !in organized.indices || from == to) return
        organized.add(to, organized.removeAt(from))
    }

    /** Turns organized page [index] a quarter clockwise. zh-CN: 把整理中的第 [index] 页顺时针转四分之一圈. */
    fun rotatePage(index: Int) {
        val page = organized.getOrNull(index) ?: return
        if (phase != Phase.Ready) return
        page.rotation = (page.rotation + QUARTER) % FULL_TURN
    }

    /** Turns every organized page a quarter clockwise. zh-CN: 把整理中的每一页顺时针转四分之一圈. */
    fun rotateAll() {
        if (phase != Phase.Ready) return
        organized.forEach { it.rotation = (it.rotation + QUARTER) % FULL_TURN }
    }

    fun removePage(index: Int) {
        if (phase != Phase.Ready || index !in organized.indices) return
        organized.removeAt(index)
    }

    /** Back to every page in the source order, unturned. zh-CN: 恢复为按源顺序排列的全部页面, 不旋转. */
    fun resetPages() {
        if (phase != Phase.Ready) return
        organized.clear()
        organized += (1..(inputs.firstOrNull()?.pageCount ?: 0)).map(::Page)
    }

    /** The organized pages differ from the source. zh-CN: 整理后的页面与源文档不同. */
    fun pagesChanged(): Boolean {
        val count = inputs.firstOrNull()?.pageCount ?: return false
        return organized.size != count || organized.withIndex().any { (index, page) -> page.page != index + 1 || page.rotation != 0 }
    }

    /** The options the screen shows, or null while they are incomplete or invalid. zh-CN: 界面上的选项; 不完整或无效时为 null. */
    fun options(): ToolOptions? {
        val first = inputs.firstOrNull() ?: return null
        return when (kind) {
            ToolKind.MERGE -> ToolOptions.Merge(keepOutline).takeIf { inputs.size >= 2 }
            ToolKind.SPLIT -> when (splitMode) {
                SplitMode.EVERY -> splitEveryValue()?.let { ToolOptions.Split(it, null) }
                SplitMode.RANGES -> splitRangeList()?.let { ToolOptions.Split(null, it) }
            }
            ToolKind.TO_IMAGES -> pagesOrAll(first.pageCount)?.let { ToolOptions.ToImages(imageFormat, imageDpi, it.value) }
            ToolKind.FROM_IMAGES -> ToolOptions.FromImages(pageSize, orientation, margin.toFloat())
            ToolKind.EXTRACT_TEXT -> pagesOrAll(first.pageCount)?.let { ToolOptions.ExtractText(it.value) }
            ToolKind.ENCRYPT -> ToolOptions.Encrypt(newPassword).takeIf { encryptPasswordProblem() == null }
            ToolKind.DECRYPT -> ToolOptions.Decrypt.takeIf { first.encrypted }
            ToolKind.ORGANIZE -> ToolOptions.Organize(organized.map { OrganizedPage(it.page, it.rotation) }).takeIf { organized.isNotEmpty() && pagesChanged() }
            ToolKind.WATERMARK -> pagesOrAll(first.pageCount)?.let { watermark.options(it.value) }
            ToolKind.PAGE_NUMBERS -> pagesOrAll(first.pageCount)?.let { pageNumbers.options(it.value) }
            ToolKind.COMPRESS -> compress.options()
            ToolKind.FILL_FORM -> fillForm.options()
            ToolKind.PROPERTIES -> properties.options()
            null -> null
        }
    }

    /** The page count of the previewed input, 0 without one. zh-CN: 预览输入的页数, 没有输入时为 0. */
    fun previewPageCount(): Int = inputs.firstOrNull()?.pageCount ?: 0

    /** The typed range when it is valid, else null (every page). zh-CN: 输入的范围有效时为该范围, 否则为 null (全部页). */
    fun previewRangeSpec(): String? = pagesOrAll(previewPageCount())?.value

    /** Whether the tool stamps page [page] (from 1) with the typed range. zh-CN: 按输入的范围, 工具是否在第 [page] 页 (从 1 起) 盖章. */
    fun previewInRange(page: Int): Boolean = previewSelection().contains(page)

    /**
     * Follows the typed range (appendix D Q344 (a)): the preview stays on its page while the new range keeps it and
     * goes to the first page of the new range otherwise; a range that is not valid counts as every page, so the page
     * stays. The first call for an input starts on the first page of the range.
     * zh-CN: 跟随输入的范围 (附录 D Q344 (a)): 新范围仍包含当前页时预览留在该页, 否则转到新范围的第一页; 无效的范围视为全部页, 因此页面不变.
     * 某个输入的第一次调用从范围的第一页开始.
     */
    fun followPreviewRange() {
        val typed = pages
        if (typed == previewRange) return
        previewRange = typed
        val selection = previewSelection()
        if (selection.isNotEmpty() && previewPage !in selection) previewPage = selection.first()
    }

    /** Turns the preview by [delta] pages within the document. zh-CN: 在文档范围内把预览翻动 [delta] 页. */
    fun turnPreview(delta: Int) {
        val count = previewPageCount()
        if (count > 0) previewPage = (previewPage + delta).coerceIn(1, count)
    }

    /** The pages of the typed range; every page for none or one that is not valid. zh-CN: 输入范围中的页; 没有范围或范围无效时为全部页. */
    private fun previewSelection(): IntArray {
        val count = previewPageCount()
        return PageRanges.parse(pagesOrAll(count)?.value, count)
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

    fun canStart(): Boolean {
        if (phase != Phase.Ready || inputs.isEmpty() || !inputs.all { it.usable }) return false
        val destination = if (toolbox) outputFolder != null && plugin != null else session != null
        return destination && options() != null
    }

    fun run() {
        val tool = kind ?: return
        val options = options() ?: return
        if (phase != Phase.Ready) return
        val jobInputs = inputs.map { input ->
            val password = if (tool == ToolKind.DECRYPT) decryptPassword.ifEmpty { null } ?: input.password else input.password
            ToolInput(input.target, password)
        }
        scriptRun = ScriptRun.of(
            tool,
            jobInputs.map { ScriptSource(it.target.uri.toString(), it.target.displayName, password = it.password != null) },
            options,
            (options as? ToolOptions.Watermark)?.image?.let { ScriptSource(it.uri.toString(), it.displayName) },
            parentPath,
        )
        if (toolbox) {
            val folder = outputFolder ?: return
            task = ToolTasks.submit(getApplication(), tool, jobInputs, options, folder)
            return
        }
        val engine = plugin ?: return
        val host = session
        if (host == null) {
            localPhase = Phase.Failed(ToolFailure(ToolFailure.OUTPUT_FAILED, "no host session"))
            return
        }
        val job = PdfToolRunner(engine, ::open, HostSessionOutput(host), { fraction ->
            main.post { if (localPhase == Phase.Running) localProgress = fraction.coerceIn(0f, 1f) }
        }, ToolTasks.workDirectory(getApplication()))
        runner = job
        localProgress = 0f
        localPhase = Phase.Running
        worker.execute {
            val outcome = try {
                Phase.Done(job.run(jobInputs, options))
            } catch (e: ToolFailure) {
                Phase.Failed(e)
            } catch (e: Exception) {
                Phase.Failed(ToolFailure(PdfErrorCodes.INTERNAL, e.message ?: e.javaClass.simpleName))
            }
            main.post {
                if (runner === job) {
                    runner = null
                    localPhase = outcome
                }
            }
        }
    }

    fun cancel() {
        val current = task
        if (current != null) current.cancel() else runner?.cancel()
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
        if (password && kind != ToolKind.DECRYPT) {
            inputs.filter { it.encrypted && it.error == null }.forEach { input ->
                input.locked = true
                input.wrongPassword = false
                input.passwordDraft = ""
            }
        }
        task?.let { ToolTasks.dismiss(getApplication(), it) }
        task = null
        localPhase = Phase.Ready
    }

    /**
     * Back from the result or failure page to the options (roadmap P6.15, E4 feedback F3): the inputs and everything
     * chosen or typed stay as they were (passwords only in this model, never in the saved state), so a change and a new
     * start write another result. That result does not replace the first one: the toolbox numbers a taken name
     * ([LocalFolderOutput]) and the host does so for the file manager (`OUTPUT_CONFLICT_AUTO_RENAME`, one host session
     * takes any number of outputs). A failure goes back as [retry] does. Answers false when there is no page to go
     * back from (the screen then closes): while inspecting, on the options, while running, or after a failure that left
     * no input.
     * zh-CN: 从结果页或失败页回到选项 (路线图 P6.15, E4 反馈 F3): 输入以及选择或填写的全部内容保持原样 (密码只在本模型中, 从不进入保存的
     * 状态), 因此修改后再次开始会写出另一个结果. 该结果不会取代第一个: 工具箱为已被占用的名称编号 ([LocalFolderOutput]), 文件管理器由宿主
     * 编号 (`OUTPUT_CONFLICT_AUTO_RENAME`, 一个宿主会话可以创建任意数量的输出). 失败时与 [retry] 相同. 没有可返回的页面时 (读取中, 选项页,
     * 运行中, 或失败后没有任何输入) 返回 false, 此时界面关闭.
     */
    fun backToOptions(): Boolean = when (phase) {
        is Phase.Done -> {
            // The outcome was seen: its home card and notification go, as with the finish button.
            // zh-CN: 结果已被看到: 与完成按钮一样移除其首页卡片与通知.
            task?.let { ToolTasks.dismiss(getApplication(), it) }
            task = null
            localPhase = Phase.Ready
            true
        }
        is Phase.Failed -> if (inputs.isEmpty()) {
            false
        } else {
            retry()
            true
        }
        else -> false
    }

    /**
     * The last start written as a script request, with the result at [result]'s path and the AES key length the
     * runner picked for an encryption; null when there is no start to write.
     * zh-CN: 把最近一次开始写成脚本请求, 结果位于 [result] 的路径, 加密时使用执行器选定的 AES 密钥长度; 没有可写的开始时为 null.
     */
    fun scriptRequestOf(result: ToolResult): ScriptRequest? {
        val run = scriptRun ?: return null
        val keyLength = if (PdfEncryption.aes256Available) PdfContract.ENCRYPTION_KEY_LENGTH_256 else PdfContract.ENCRYPTION_KEY_LENGTH_128
        return run.requestFor(result.displayPath, keyLength)
    }

    /** The user saw the toolbox outcome: drop its card from the home screen. zh-CN: 用户已看到工具箱结果: 从首页移除其卡片. */
    fun acknowledge() {
        val current = task ?: return
        if (current.finished) ToolTasks.dismiss(getApplication(), current)
    }

    override fun onCleared() {
        runner?.cancel()
        runner = null
        closeThumbnails()
        closeDetails()
        if (acquired) {
            // After a running tool stops: close the host session, then give the engine back. A toolbox task holds the
            // engine on its own and keeps running. zh-CN: 等运行中的工具停止后关闭宿主会话, 再归还引擎. 工具箱任务自行持有引擎, 继续运行.
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
                if (kind == ToolKind.DECRYPT && password != null && decryptPassword.isEmpty()) decryptPassword = password
                if (inputs.firstOrNull() === input) {
                    when (kind) {
                        ToolKind.ORGANIZE -> openPages(input)
                        ToolKind.WATERMARK, ToolKind.PAGE_NUMBERS -> openPreview(input)
                        ToolKind.FILL_FORM, ToolKind.PROPERTIES -> loadDetails(input)
                        else -> Unit
                    }
                }
            }
            is Inspection.Locked -> {
                input.encrypted = true
                input.locked = true
                input.wrongPassword = password != null
            }
            is Inspection.Unreadable -> input.error = found.code
        }
    }

    private fun openPages(input: Input) {
        closeThumbnails()
        organized.clear()
        organized += (1..input.pageCount).map(::Page)
        thumbnails = PageThumbnails(getApplication(), input.target.uri, input.password)
    }

    private fun closeThumbnails() {
        thumbnails?.close()
        thumbnails = null
    }

    private fun openPreview(input: Input) {
        val engine = plugin ?: return
        preview?.close()
        preview = PagePreview(getApplication(), engine, input.target, input.password, input.pageCount)
        previewPage = 1
        previewRange = null
    }

    /** Closes the preview and forgets what was read of the input. zh-CN: 关闭预览并丢弃从输入读取的内容. */
    private fun closeDetails() {
        preview?.close()
        preview = null
        detailsGeneration++
        fillForm.reset()
        properties.reset()
    }

    /**
     * Reads the form fields (fill form) or the facts and outline (properties) of [input] on the worker; a newer input
     * makes the answer stale.
     * zh-CN: 在工作线程上读取 [input] 的表单字段 (表单填写) 或文件信息与书签 (文档属性); 有更新的输入时应答作废.
     */
    private fun loadDetails(input: Input) {
        val engine = plugin ?: return
        val tool = kind ?: return
        val ticket = ++detailsGeneration
        fillForm.reset()
        properties.reset()
        if (tool == ToolKind.FILL_FORM) fillForm.loading = true else properties.loading = true
        val target = input.target
        val password = input.password
        worker.execute {
            val outcome: Any = try {
                readDetails(engine, tool, target, password)
            } catch (e: ToolFailure) {
                e.code
            } catch (e: RuntimeException) {
                PdfErrorCodes.decode(e.message)?.first ?: PdfErrorCodes.INTERNAL
            } catch (e: Exception) {
                PdfErrorCodes.IO_ERROR
            }
            main.post {
                if (ticket != detailsGeneration) return@post
                when (outcome) {
                    is Details -> if (tool == ToolKind.FILL_FORM) {
                        fillForm.show(outcome.fields, outcome.fieldsMore, outcome.facts[PdfContract.KEY_XFA] as? String)
                    } else {
                        properties.show(outcome.facts, outcome.outline, outcome.outlineMore)
                    }
                    else -> if (tool == ToolKind.FILL_FORM) {
                        fillForm.loading = false
                        fillForm.error = outcome as String
                    } else {
                        properties.loading = false
                        properties.error = outcome as String
                    }
                }
            }
        }
    }

    private fun readDetails(engine: IPdfPlugin, tool: ToolKind, target: ToolTarget, password: String?): Details {
        val options = Bundle().apply {
            putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
            password?.let { putString(PdfContract.KEY_PASSWORD, it) }
            putString(PdfContract.KEY_DISPLAY_NAME, target.displayName)
        }
        // open() closes the source. zh-CN: open() 会关闭源.
        val document = engine.open(open(target.uri), options)
        try {
            val info = checked(document.info)
            val facts = LinkedHashMap<String, Any?>()
            facts[PdfContract.KEY_DISPLAY_NAME] = target.displayName
            FACT_KEYS.forEach { key -> valueOf(info, key)?.let { facts[key] = it } }
            if (tool == ToolKind.FILL_FORM) {
                val fields = ArrayList<FormFieldItem>()
                var offset = 0
                var more = true
                while (more && fields.size < FillFormState.MAX_FIELDS) {
                    val request = Bundle().apply {
                        putInt(PdfContract.KEY_CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2)
                        putInt(PdfContract.KEY_OFFSET, offset)
                        putInt(PdfContract.KEY_LIMIT, PdfContract.MAX_FORM_FIELD_ITEMS)
                    }
                    val page = checked(document.getFormFields(request))
                    val found = FormFieldItem.parse(page.getString(PdfContract.KEY_ITEMS) ?: "[]")
                    fields += found
                    more = page.getBoolean(PdfContract.KEY_HAS_MORE)
                    val next = page.getInt(PdfContract.KEY_NEXT_OFFSET, offset + found.size)
                    if (more && (found.isEmpty() || next <= offset)) break
                    offset = next
                }
                val truncated = more || fields.size > FillFormState.MAX_FIELDS
                return Details(facts, fields.take(FillFormState.MAX_FIELDS), truncated, emptyList(), false)
            }
            val outline = checked(document.outline)
            val entries = OutlineItem.parse(outline.getString(PdfContract.KEY_ITEMS) ?: "[]")
            val more = outline.getBoolean(PdfContract.KEY_HAS_MORE) || entries.size > PropertiesState.MAX_OUTLINE_SHOWN
            return Details(facts, emptyList(), false, entries.take(PropertiesState.MAX_OUTLINE_SHOWN), more)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun checked(answer: Bundle?): Bundle {
        val result = answer ?: throw ToolFailure(PdfErrorCodes.INTERNAL, "no answer")
        result.getString(PdfContract.KEY_ERROR_CODE)?.let { throw ToolFailure(it, result.getString(PdfContract.KEY_ERROR_MESSAGE)) }
        return result
    }

    @Suppress("DEPRECATION")
    private fun valueOf(bundle: Bundle, key: String): Any? = bundle.get(key)

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

    /** What the fill-form or properties tool read. zh-CN: 表单填写或文档属性工具读取到的内容. */
    private class Details(
        val facts: Map<String, Any?>,
        val fields: List<FormFieldItem>,
        val fieldsMore: Boolean,
        val outline: List<OutlineItem>,
        val outlineMore: Boolean,
    )

    companion object {

        /** No usable file arrived with the request. zh-CN: 请求没有带来可用的文件. */
        const val NO_FILES = "NO_FILES"

        const val PASSWORD_EMPTY = "EMPTY"

        const val PASSWORD_TOO_LONG = "TOO_LONG"

        const val PASSWORD_MISMATCH = "MISMATCH"

        val DPI_CHOICES = ToolboxPreferences.DPI_CHOICES

        /** Page margins of images to PDF in points: none, narrow (1/4 inch), wide (1/2 inch). zh-CN: 图片转 PDF 的页边距 (点): 无, 窄 (1/4 英寸), 宽 (1/2 英寸). */
        val MARGIN_CHOICES = listOf(0, 18, 36)

        /**
         * The `getInfo` keys the properties tool shows or edits (with [PdfContract.KEY_DISPLAY_NAME] added first).
         * zh-CN: 文档属性工具显示或编辑的 `getInfo` 键 (另在最前面加上 [PdfContract.KEY_DISPLAY_NAME]).
         */
        val FACT_KEYS = listOf(
            PdfContract.KEY_SIZE_BYTES, PdfContract.KEY_PAGE_COUNT, PdfContract.KEY_PDF_VERSION, PdfContract.KEY_ENCRYPTED,
            PdfContract.KEY_FORM_FIELD_COUNT, PdfContract.KEY_PRODUCER, PdfContract.KEY_CREATOR,
            PdfContract.KEY_CREATION_DATE, PdfContract.KEY_MODIFICATION_DATE, PdfContract.KEY_XFA,
            PdfContract.KEY_TITLE, PdfContract.KEY_AUTHOR, PdfContract.KEY_SUBJECT, PdfContract.KEY_KEYWORDS,
        )

        private const val QUARTER = 90

        private const val FULL_TURN = 360

    }

}

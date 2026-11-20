package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PdfEncryption
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.IPdfDocument
import org.autojs.plugin.pdf.api.IPdfPlugin
import org.autojs.plugin.pdf.api.IPdfProgressCallback
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.autojs.plugin.pdf.api.PdfOps
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Why a tool stopped: a code of `PdfErrorCodes`, or [OUTPUT_FAILED] when the file manager could not create or publish
 * the result, with the detail of the cause.
 * zh-CN: 工具停止的原因: `PdfErrorCodes` 中的错误码, 或文件管理器无法创建或发布结果时的 [OUTPUT_FAILED], 以及原因的详情.
 */
internal class ToolFailure(val code: String, val detail: String?) : Exception("$code: $detail") {

    companion object {
        const val OUTPUT_FAILED = "OUTPUT_FAILED"
    }

}

/** What the user chose for a tool. zh-CN: 用户为工具选择的选项. */
internal sealed interface ToolOptions {

    /** [keepOutline]: keep the bookmarks, one per file, named after it. zh-CN: [keepOutline]: 保留书签, 每个文件一个并以其命名. */
    data class Merge(val keepOutline: Boolean) : ToolOptions

    /** Exactly one of [every] and [ranges]. zh-CN: [every] 与 [ranges] 恰好给出一个. */
    data class Split(val every: Int?, val ranges: List<String>?) : ToolOptions

    /** [format] is `png` or `jpeg`; [pages] is a page range or null for every page. zh-CN: [format] 为 `png` 或 `jpeg`; [pages] 为页码范围, null 表示全部页. */
    data class ToImages(val format: String, val dpi: Int, val pages: String?) : ToolOptions

    data class ExtractText(val pages: String?) : ToolOptions

    /** The password that opens the result; every permission stays granted (appendix D Q76). zh-CN: 打开结果所用的密码; 保留全部权限 (附录 D Q76). */
    data class Encrypt(val password: String) : ToolOptions

    data object Decrypt : ToolOptions

    /**
     * Images to one PDF (roadmap P4.2): [pageSize] is `fit`, `a4` or `letter`, [orientation] `auto`, `portrait` or
     * `landscape`, [margin] in points.
     * zh-CN: 图片转为一个 PDF (路线图 P4.2): [pageSize] 为 `fit`, `a4` 或 `letter`, [orientation] 为 `auto`, `portrait` 或
     * `landscape`, [margin] 以点为单位.
     */
    data class FromImages(val pageSize: String, val orientation: String, val margin: Float) : ToolOptions

    /** The pages to keep, in their new order, each turned clockwise as given (roadmap P4.2). zh-CN: 要保留的页, 按新顺序排列, 各自按给定角度顺时针旋转 (路线图 P4.2). */
    data class Organize(val pages: List<OrganizedPage>) : ToolOptions

    /**
     * A text or image watermark through `stamp` (roadmap P6.12, D68), with the keys of a stamp item: [type] is
     * `text` or `image` ([image] then names the picture), [position] one of the nine positions, [rotation] counterclockwise
     * degrees, [opacity] 0 to 1, [tile] repeats it over the page, [color] (`#RRGGBB`) and [fontSize] (null fits half the
     * page width) are for text, [width] (points, null for the natural size) for an image, [layer] `over` or `under` the
     * content, [pages] a page range or null for every page.
     * zh-CN: 经 `stamp` 添加文字或图片水印 (路线图 P6.12, D68), 字段与盖章条目的键相同: [type] 为 `text` 或 `image` (此时 [image]
     * 指图片), [position] 为九个位置之一, [rotation] 为逆时针角度, [opacity] 为 0 到 1, [tile] 在整页平铺, [color] (`#RRGGBB`) 与
     * [fontSize] (null 时适应半个页宽) 用于文字, [width] (点, null 为原始尺寸) 用于图片, [layer] 为内容之上 `over` 或之下 `under`,
     * [pages] 为页码范围, null 表示全部页.
     */
    data class Watermark(
        val type: String,
        val text: String,
        val image: ToolTarget?,
        val position: String,
        val rotation: Double,
        val opacity: Double,
        val tile: Boolean,
        val color: String?,
        val fontSize: Double?,
        val width: Double?,
        val layer: String,
        val pages: String?,
    ) : ToolOptions

    /**
     * Page numbers, headers and footers through `stamp` (roadmap P6.12, D68): [format] (null for no page number) at
     * [position], the first page of [pages] numbered [start] (appendix D Q211), [header] and [footer] texts at the left,
     * center and right of the top and bottom edges, all in [fontSize] points.
     * zh-CN: 经 `stamp` 添加页码, 页眉与页脚 (路线图 P6.12, D68): [format] (null 表示不加页码) 位于 [position], [pages] 的第一页编为
     * [start] (附录 D Q211); [header] 与 [footer] 是上下边缘左, 中, 右的文字; 字号都为 [fontSize] 点.
     */
    data class PageNumbers(
        val format: String?,
        val position: String,
        val start: Int,
        val header: Slots,
        val footer: Slots,
        val fontSize: Double,
        val pages: String?,
    ) : ToolOptions

    /** The left, center and right texts of an edge; blank for none. zh-CN: 一条边缘左, 中, 右的文字; 空白表示没有. */
    data class Slots(val left: String = "", val center: String = "", val right: String = "") {
        val blank get() = left.isBlank() && center.isBlank() && right.isBlank()
    }

    /**
     * `compress` with the numbers of [preset] (D55) unless changed under "Advanced": [imageDpi] and [imageQuality] of
     * the recompressed images, [removeMetadata], [objectStreams].
     * zh-CN: 以 [preset] (D55) 的数值执行 `compress`, "高级" 中可以修改: 重新压缩图片的 [imageDpi] 与 [imageQuality], [removeMetadata],
     * [objectStreams].
     */
    data class Compress(val preset: String, val imageDpi: Int, val imageQuality: Int, val removeMetadata: Boolean, val objectStreams: Boolean) : ToolOptions

    /**
     * `fillForm` (D47) with the changed [values] (by full field name: a string, a boolean, a list of strings, or null for
     * the default value) and [flatten] for every field.
     * zh-CN: 以修改过的 [values] (按字段全名: 字符串, 布尔值, 字符串列表, 或 null 表示默认值) 执行 `fillForm` (D47), [flatten] 扁平化全部字段.
     */
    data class FillForm(val values: Map<String, Any?>, val flatten: Boolean) : ToolOptions

    /**
     * The document properties (D52): `setInfo` with the changed [info] keys (null removes one), then `setOutline` with
     * no items when [clearOutline].
     * zh-CN: 文档属性 (D52): 以修改过的 [info] 键执行 `setInfo` (null 删除该键), [clearOutline] 时再以空条目执行 `setOutline`.
     */
    data class Properties(val info: Map<String, String?>, val clearOutline: Boolean) : ToolOptions

}

/**
 * What a tool reports beyond its warnings, for its result page.
 * zh-CN: 工具在警告之外报告的内容, 供结果页显示.
 */
internal sealed interface ToolSummary {

    /** [sourceBytes] became [sizeBytes]; [imagesRecompressed] of [imagesTotal] images were recompressed. zh-CN: [sourceBytes] 变为 [sizeBytes]; [imagesTotal] 张图片中重新压缩了 [imagesRecompressed] 张. */
    data class Compressed(val sourceBytes: Long, val sizeBytes: Long, val imagesRecompressed: Int, val imagesTotal: Int) : ToolSummary

    /** [filled] values were written, and every field was [flattened] or not. zh-CN: 写入了 [filled] 个值, 以及是否 [flattened] 扁平化了全部字段. */
    data class Filled(val filled: Int, val flattened: Boolean) : ToolSummary

    /** [infoChanged] properties were changed and the outline [outlineCleared] or not. zh-CN: 修改了 [infoChanged] 项属性, 以及是否 [outlineCleared] 清除了书签. */
    data class PropertiesSaved(val infoChanged: Int, val outlineCleared: Boolean) : ToolSummary

}

/** A page of the source ([page] from 1) and the clockwise turn added to it (0, 90, 180 or 270). zh-CN: 源文档的一页 ([page] 从 1 起) 及为它追加的顺时针旋转 (0, 90, 180 或 270). */
internal data class OrganizedPage(val page: Int, val rotation: Int)

/** A selected file and the password it opens with, if any. zh-CN: 选中的文件及打开它所用的密码 (如有). */
internal class ToolInput(val target: ToolTarget, val password: String?)

/**
 * The published result: the path the file manager shows, the files written, the engine's warnings (with their codes,
 * Q264 b) and what the tool reports beyond them.
 * zh-CN: 已发布的结果: 文件管理器显示的路径, 写入的文件数, 引擎的警告 (带代码, Q264 b), 以及工具在警告之外报告的内容.
 */
internal class ToolResult(val displayPath: String, val files: Int, val warnings: List<ToolWarning>, val summary: ToolSummary? = null)

/**
 * Runs one file manager tool (roadmap P3.3) through the in-process PDF plugin ([ToolEngine]), the same requests a
 * script sends: `merge`, `encrypt` and `decrypt` as `run`, `split` through `planSplit` and `split` in batches of
 * [SPLIT_BATCH] parts, to images through `renderToFile` page by page and extract text through paged `getText`; the
 * tools of 1.1.0 (roadmap P6.12) run `stamp`, `compress`, `fillForm`, `setInfo` and `setOutline` with contract version 2
 * and their JSON payload ([ToolPayloads]) in a temporary file of the work folder. The result is written into a
 * [ToolOutput] and published only when complete; a failure or [cancel] removes it. Blocking: call [run] off the main
 * thread.
 * zh-CN: 经进程内的 PDF 插件 ([ToolEngine]) 执行一个文件管理器工具 (路线图 P3.3), 发送与脚本相同的请求: `merge`, `encrypt` 与
 * `decrypt` 走 `run`, 拆分经 `planSplit` 与按每批 [SPLIT_BATCH] 部分执行的 `split`, 转图片逐页经 `renderToFile`, 提取文本经分页的
 * `getText`; 1.1.0 的工具 (路线图 P6.12) 以契约版本 2 执行 `stamp`, `compress`, `fillForm`, `setInfo` 与 `setOutline`, 其 JSON
 * payload ([ToolPayloads]) 放在工作目录的临时文件中. 结果写入 [ToolOutput], 完成后才发布; 失败或 [cancel] 时删除. 会阻塞: 在主线程
 * 以外调用 [run].
 */
internal class PdfToolRunner(
    private val plugin: IPdfPlugin,
    private val open: (Uri) -> ParcelFileDescriptor,
    private val output: ToolOutput,
    private val progress: (Float) -> Unit = {},
    private val workDir: File? = null,
) {

    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var operation = NO_OPERATION

    /** Stops the tool at its next step; a running operation ends with `CANCELLED`. zh-CN: 让工具在下一步停止; 运行中的操作以 `CANCELLED` 结束. */
    fun cancel() {
        cancelled.set(true)
        val id = operation
        if (id != NO_OPERATION) runCatching { plugin.cancel(id) }
    }

    fun run(inputs: List<ToolInput>, options: ToolOptions): ToolResult {
        require(inputs.isNotEmpty()) { "a tool needs at least one input" }
        return when (options) {
            is ToolOptions.Merge -> merge(inputs, options)
            is ToolOptions.FromImages -> fromImages(inputs, options)
            is ToolOptions.Organize -> organize(inputs.first(), options)
            is ToolOptions.Split -> split(inputs.first(), options)
            is ToolOptions.ToImages -> toImages(inputs.first(), options)
            is ToolOptions.ExtractText -> extractText(inputs.first(), options)
            is ToolOptions.Encrypt -> encrypt(inputs.first(), options)
            ToolOptions.Decrypt -> decrypt(inputs.first())
            is ToolOptions.Watermark -> {
                val input = inputs.first()
                stamp(input, listOf(ToolPayloads.watermarkItem(options)), listOfNotNull(options.image), OutputNames.watermarked(input.target.displayName))
            }
            is ToolOptions.PageNumbers -> {
                val input = inputs.first()
                stamp(input, ToolPayloads.pageNumberItems(options), emptyList(), OutputNames.numbered(input.target.displayName))
            }
            is ToolOptions.Compress -> compress(inputs.first(), options)
            is ToolOptions.FillForm -> fillForm(inputs.first(), options)
            is ToolOptions.Properties -> properties(inputs.first(), options)
        }
    }

    /**
     * `stamp` of [items] (roadmap P6.12): the inputs are the source, the payload and [images] (named by their index in
     * the items), the request names the source for `{name}`.
     * zh-CN: 以 [items] 执行 `stamp` (路线图 P6.12): 输入依次为源文档, payload 与 [images] (条目按序号引用), 请求为 `{name}` 提供源文件名.
     */
    private fun stamp(input: ToolInput, items: List<JSONObject>, images: List<ToolTarget>, name: String): ToolResult {
        if (items.isEmpty()) throw ToolFailure(PdfErrorCodes.INVALID_ARGUMENT, "nothing to add")
        val request = request(PdfOps.STAMP, PdfContract.CONTRACT_VERSION_2) {
            input.password?.let { putString(PdfContract.KEY_PASSWORD, it) }
            putString(PdfContract.KEY_DISPLAY_NAME, input.target.displayName)
            if (images.isNotEmpty()) putInt(PdfContract.KEY_IMAGE_COUNT, images.size)
        }
        return file(name, MIME_PDF) { descriptor ->
            withPayload(ToolPayloads.stamp(items), descriptor) { payload ->
                runSources(request, listOf({ openSource(input.target) }, payload) + images.map { { openSource(it) } }, descriptor)
            }
        }
    }

    private fun compress(input: ToolInput, options: ToolOptions.Compress): ToolResult {
        val request = request(PdfOps.COMPRESS, PdfContract.CONTRACT_VERSION_2) {
            input.password?.let { putString(PdfContract.KEY_PASSWORD, it) }
            putInt(PdfContract.KEY_IMAGE_DPI, options.imageDpi)
            putInt(PdfContract.KEY_IMAGE_QUALITY, options.imageQuality)
            putBoolean(PdfContract.KEY_REMOVE_METADATA, options.removeMetadata)
            putBoolean(PdfContract.KEY_OBJECT_STREAMS, options.objectStreams)
        }
        return file(OutputNames.compressed(input.target.displayName), MIME_PDF, { answer ->
            ToolSummary.Compressed(
                sourceBytes = number(answer, PdfContract.KEY_SOURCE_BYTES).toLong(),
                sizeBytes = number(answer, PdfContract.KEY_SIZE_BYTES).toLong(),
                imagesRecompressed = number(answer, PdfContract.KEY_IMAGES_RECOMPRESSED).toInt(),
                imagesTotal = number(answer, PdfContract.KEY_IMAGES_TOTAL).toInt(),
            )
        }) { descriptor -> runSources(request, listOf { openSource(input.target) }, descriptor) }
    }

    private fun fillForm(input: ToolInput, options: ToolOptions.FillForm): ToolResult {
        val request = request(PdfOps.FILL_FORM, PdfContract.CONTRACT_VERSION_2) {
            input.password?.let { putString(PdfContract.KEY_PASSWORD, it) }
        }
        return file(OutputNames.filled(input.target.displayName), MIME_PDF, { answer ->
            ToolSummary.Filled(number(answer, PdfContract.KEY_FILLED).toInt(), options.flatten)
        }) { descriptor ->
            withPayload(ToolPayloads.fill(options), descriptor) { payload ->
                runSources(request, listOf({ openSource(input.target) }, payload), descriptor)
            }
        }
    }

    /**
     * `setInfo` and then `setOutline` (D52); when both run, the first writes a temporary file in [workDir] that the second
     * reads (it is no longer encrypted, as every written output).
     * zh-CN: 依次执行 `setInfo` 与 `setOutline` (D52); 两者都执行时, 前者写入 [workDir] 中的临时文件供后者读取 (与所有写出的结果一样,
     * 它已不再加密).
     */
    private fun properties(input: ToolInput, options: ToolOptions.Properties): ToolResult {
        val steps = buildList {
            if (options.info.isNotEmpty()) add(PdfOps.SET_INFO to ToolPayloads.info(options.info))
            if (options.clearOutline) add(PdfOps.SET_OUTLINE to ToolPayloads.clearedOutline())
        }
        if (steps.isEmpty()) throw ToolFailure(PdfErrorCodes.INVALID_ARGUMENT, "nothing to change")
        return file(OutputNames.properties(input.target.displayName), MIME_PDF, { ToolSummary.PropertiesSaved(options.info.size, options.clearOutline) }) { descriptor ->
            val directory = workDir
            if (steps.size > 1 && directory == null) throw ToolFailure(PdfErrorCodes.INTERNAL, "no work folder for the intermediate files")
            var temp: File? = null
            try {
                var answer: Bundle? = null
                steps.forEachIndexed { index, (op, payload) ->
                    checkCancelled()
                    val last = index == steps.lastIndex
                    val source = temp
                    val target = if (last) {
                        descriptor
                    } else {
                        directory!!.mkdirs()
                        val created = File.createTempFile("properties-", ".pdf", directory).also { temp = it }
                        ParcelFileDescriptor.open(created, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
                    }
                    val request = request(op, PdfContract.CONTRACT_VERSION_2) {
                        if (source == null) input.password?.let { putString(PdfContract.KEY_PASSWORD, it) }
                    }
                    val opener: () -> ParcelFileDescriptor = if (source == null) {
                        { openSource(input.target) }
                    } else {
                        { ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY) }
                    }
                    val step = steps.size
                    answer = withPayload(payload, target) { data ->
                        runSources(request, listOf(opener, data), target) { (index + it) / step }
                    }
                }
                answer
            } finally {
                temp?.delete()
            }
        }
    }

    private fun merge(inputs: List<ToolInput>, options: ToolOptions.Merge): ToolResult {
        val request = request(PdfOps.MERGE) {
            putStringArray(PdfContract.KEY_PASSWORDS, inputs.map { it.password.orEmpty() }.toTypedArray())
            if (options.keepOutline) {
                putString(PdfContract.KEY_OUTLINE, PdfContract.OUTLINE_MERGE)
                putStringArray(PdfContract.KEY_DISPLAY_NAMES, inputs.map { OutputNames.stem(it.target.displayName) }.toTypedArray())
            } else {
                putString(PdfContract.KEY_OUTLINE, PdfContract.OUTLINE_NONE)
            }
        }
        return file(OutputNames.merged(inputs.first().target.displayName), MIME_PDF) { descriptor -> runOperation(request, inputs, descriptor) }
    }

    private fun encrypt(input: ToolInput, options: ToolOptions.Encrypt): ToolResult {
        val request = request(PdfOps.ENCRYPT) {
            input.password?.let { putString(PdfContract.KEY_PASSWORD, it) }
            putString(PdfContract.KEY_USER_PASSWORD, options.password)
            val keyLength = if (PdfEncryption.aes256Available) PdfContract.ENCRYPTION_KEY_LENGTH_256 else PdfContract.ENCRYPTION_KEY_LENGTH_128
            putInt(PdfContract.KEY_KEY_LENGTH, keyLength)
        }
        return file(OutputNames.encrypted(input.target.displayName), MIME_PDF) { descriptor -> runOperation(request, listOf(input), descriptor) }
    }

    private fun decrypt(input: ToolInput): ToolResult {
        val request = request(PdfOps.DECRYPT) { input.password?.let { putString(PdfContract.KEY_PASSWORD, it) } }
        return file(OutputNames.decrypted(input.target.displayName), MIME_PDF) { descriptor -> runOperation(request, listOf(input), descriptor) }
    }

    private fun fromImages(inputs: List<ToolInput>, options: ToolOptions.FromImages): ToolResult {
        val request = request(PdfOps.FROM_IMAGES) {
            putString(PdfContract.KEY_PAGE_SIZE, options.pageSize)
            putString(PdfContract.KEY_ORIENTATION, options.orientation)
            putFloat(PdfContract.KEY_MARGIN, options.margin)
        }
        return file(OutputNames.imagesPdf(inputs.first().target.displayName), MIME_PDF) { descriptor -> runOperation(request, inputs, descriptor) }
    }

    /**
     * Organizes the pages through `save` (roadmap P4.2): the first save keeps and orders the pages and turns those of one
     * angle; each further angle needs one more save of the previous result (a save turns its pages by one angle), kept
     * in [workDir] until the last save writes the result.
     * zh-CN: 经 `save` 整理页面 (路线图 P4.2): 第一次保存保留并排列页面, 同时旋转一种角度的页; 每多一种角度就需要对上一次的结果再保存一次
     * (一次保存只按一种角度旋转), 中间结果放在 [workDir], 最后一次保存写出结果.
     */
    private fun organize(input: ToolInput, options: ToolOptions.Organize): ToolResult {
        val placed = options.pages
        if (placed.isEmpty()) throw ToolFailure(PdfErrorCodes.INVALID_ARGUMENT, "no page is kept")
        val angles = placed.map { Math.floorMod(it.rotation, FULL_TURN) }.filter { it != 0 }.distinct().sorted()
        val rounds = maxOf(1, angles.size)
        return file(OutputNames.edited(input.target.displayName), MIME_PDF) { descriptor ->
            val directory = workDir
            if (rounds > 1 && directory == null) throw ToolFailure(PdfErrorCodes.INTERNAL, "no work folder for the intermediate files")
            val temps = ArrayList<File>()
            try {
                var source = input
                var answer: Bundle? = null
                for (round in 0 until rounds) {
                    checkCancelled()
                    val last = round == rounds - 1
                    val target = if (last) {
                        descriptor
                    } else {
                        directory!!.mkdirs()
                        val temp = File.createTempFile("organize-", ".pdf", directory).also { temps += it }
                        ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
                    }
                    val angle = angles.getOrNull(round)
                    val request = request {
                        if (round == 0) {
                            putString(PdfContract.KEY_PAGES, PageRanges.format(placed.map { it.page }.sorted().toIntArray()))
                            putIntArray(PdfContract.KEY_ORDER, placed.map { it.page }.toIntArray())
                        }
                        if (angle != null) {
                            // The first save names source pages, the later ones positions of the result. zh-CN: 第一次保存指源文档的页码, 之后指结果中的位置.
                            val turned = placed.withIndex().filter { Math.floorMod(it.value.rotation, FULL_TURN) == angle }
                                .map { if (round == 0) it.value.page else it.index + 1 }
                            putString(PdfContract.KEY_ROTATE_PAGES, PageRanges.format(turned.sorted().toIntArray()))
                            putInt(PdfContract.KEY_DEGREES, angle)
                        }
                    }
                    val base = round.toFloat() / rounds
                    answer = try {
                        withDocument(source) { document ->
                            // save() closes the output. zh-CN: save() 会关闭输出.
                            await({ callback -> document.save(request, target, callback) }) { base + it / rounds }
                        }
                    } catch (e: Throwable) {
                        if (!last) runCatching { target.close() }
                        throw e
                    }
                    if (!last) source = ToolInput(ToolTarget(Uri.fromFile(temps.last()), input.target.displayName), null)
                }
                answer
            } finally {
                temps.forEach { it.delete() }
            }
        }
    }

    private fun split(input: ToolInput, options: ToolOptions.Split): ToolResult = withDocument(input) { document ->
        val selection = request {
            options.every?.let { putInt(PdfContract.KEY_EVERY, it) }
            options.ranges?.let { putStringArray(PdfContract.KEY_RANGES, it.toTypedArray()) }
        }
        val items = JSONArray(checked(document.planSplit(selection)).getString(PdfContract.KEY_ITEMS) ?: "[]")
        val parts = List(items.length()) { items.getJSONObject(it).getString(PdfContract.FIELD_PAGES) }
        val name = input.target.displayName
        folder(OutputNames.splitFolder(name)) { folder ->
            var done = 0
            parts.chunked(SPLIT_BATCH).forEach { batch ->
                checkCancelled()
                val descriptors = ArrayList<ParcelFileDescriptor>(batch.size)
                try {
                    batch.indices.forEach { descriptors += hostCall { folder.openFile(OutputNames.splitPart(name, done + it + 1, parts.size)) } }
                } catch (e: Throwable) {
                    descriptors.forEach { runCatching { it.close() } }
                    throw e
                }
                val request = request { putStringArray(PdfContract.KEY_RANGES, batch.toTypedArray()) }
                val start = done
                await({ callback -> document.split(request, descriptors.toTypedArray(), callback) }) { fraction ->
                    (start + fraction * batch.size) / parts.size
                }
                done += batch.size
                progress(done.toFloat() / parts.size)
            }
            parts.size
        }
    }

    private fun toImages(input: ToolInput, options: ToolOptions.ToImages): ToolResult = withDocument(input) { document ->
        val pageCount = checked(document.info).getInt(PdfContract.KEY_PAGE_COUNT)
        val pages = pagesOf(options.pages, pageCount)
        val name = input.target.displayName
        val extension = if (options.format == PdfContract.FORMAT_JPEG) EXTENSION_JPEG else EXTENSION_PNG
        folder(OutputNames.imagesFolder(name)) { folder ->
            pages.forEachIndexed { index, page ->
                checkCancelled()
                val descriptor = hostCall { folder.openFile(OutputNames.imagePage(name, page, pageCount, extension)) }
                val request = request {
                    putInt(PdfContract.KEY_PAGE, page)
                    putInt(PdfContract.KEY_DPI, options.dpi)
                    putString(PdfContract.KEY_FORMAT, options.format)
                    if (options.format == PdfContract.FORMAT_JPEG) putInt(PdfContract.KEY_QUALITY, JPEG_QUALITY)
                }
                // renderToFile closes the descriptor. zh-CN: renderToFile 会关闭描述符.
                checked(document.renderToFile(request, descriptor))
                progress((index + 1).toFloat() / pages.size)
            }
            pages.size
        }
    }

    private fun extractText(input: ToolInput, options: ToolOptions.ExtractText): ToolResult = withDocument(input) { document ->
        val pageCount = checked(document.info).getInt(PdfContract.KEY_PAGE_COUNT)
        val pages = pagesOf(options.pages, pageCount)
        file(OutputNames.text(input.target.displayName), MIME_TEXT) { descriptor ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).bufferedWriter(Charsets.UTF_8).use { writer ->
                var next: Pair<Int, Int>? = null
                var lastPage = 0
                do {
                    checkCancelled()
                    val request = request {
                        options.pages?.let { putString(PdfContract.KEY_PAGES, it) }
                        next?.let { (page, offset) ->
                            putInt(PdfContract.KEY_PAGE, page)
                            putInt(PdfContract.KEY_OFFSET, offset)
                        }
                    }
                    val answer = checked(document.getText(request))
                    val texts = answer.getStringArray(PdfContract.KEY_TEXTS).orEmpty()
                    val numbers = answer.getIntArray(PdfContract.KEY_PAGE_NUMBERS) ?: IntArray(0)
                    texts.forEachIndexed { index, text ->
                        val number = numbers.getOrElse(index) { lastPage }
                        // A blank line between pages; a page continued from the last answer goes on. zh-CN: 页与页之间空一行; 接续上一次应答的页直接续写.
                        if (lastPage != 0 && number != lastPage) writer.write("\n")
                        writer.write(text.orEmpty())
                        lastPage = number
                    }
                    if (lastPage != 0) progress((pages.indexOf(lastPage) + 1).toFloat() / pages.size.coerceAtLeast(1))
                    next = if (answer.getBoolean(PdfContract.KEY_HAS_MORE)) {
                        answer.getInt(PdfContract.KEY_NEXT_PAGE) to answer.getInt(PdfContract.KEY_NEXT_OFFSET)
                    } else {
                        null
                    }
                } while (next != null)
            }
            null
        }
    }

    // Steps. zh-CN: 步骤.

    /** Runs [request] on [inputs] into [descriptor] (consumed) and waits for it. zh-CN: 对 [inputs] 执行 [request], 写入 [descriptor] (被接管) 并等待完成. */
    private fun runOperation(request: Bundle, inputs: List<ToolInput>, descriptor: ParcelFileDescriptor): Bundle =
        runSources(request, inputs.map { input -> { openSource(input.target) } }, descriptor)

    /**
     * Runs [request] on the inputs [openers] open, in order, into [descriptor] (consumed) and waits for it, reporting its
     * progress mapped by [overall]; when one cannot be opened, the opened ones and [descriptor] are closed.
     * zh-CN: 对 [openers] 依次打开的输入执行 [request], 写入 [descriptor] (被接管) 并等待完成, 进度经 [overall] 换算后报告; 某个输入无法
     * 打开时, 关闭已打开的输入与 [descriptor].
     */
    private fun runSources(request: Bundle, openers: List<() -> ParcelFileDescriptor>, descriptor: ParcelFileDescriptor, overall: (Float) -> Float = { it }): Bundle {
        val sources = ArrayList<ParcelFileDescriptor>(openers.size)
        try {
            openers.forEach { sources += it() }
        } catch (e: Throwable) {
            sources.forEach { runCatching { it.close() } }
            runCatching { descriptor.close() }
            throw e
        }
        // run() closes the inputs and the output it was given. zh-CN: run() 会关闭传入的输入与输出.
        return await({ callback -> plugin.run(request, sources.toTypedArray(), descriptor, callback) }, overall)
    }

    /**
     * Writes [payload] to a temporary file in [workDir] and gives [use] an opener of it, deleting the file afterwards; when
     * it cannot be written, [descriptor] (the output [use] would consume) is closed.
     * zh-CN: 把 [payload] 写入 [workDir] 中的临时文件, 把打开它的函数交给 [use], 之后删除该文件; 无法写入时关闭 [descriptor] (本应由
     * [use] 接管的输出).
     */
    private fun <T> withPayload(payload: JSONObject, descriptor: ParcelFileDescriptor, use: (() -> ParcelFileDescriptor) -> T): T {
        val file = try {
            val directory = workDir ?: throw ToolFailure(PdfErrorCodes.INTERNAL, "no work folder for the payload")
            directory.mkdirs()
            File.createTempFile("payload-", ".json", directory).apply { writeText(payload.toString(), Charsets.UTF_8) }
        } catch (e: Throwable) {
            runCatching { descriptor.close() }
            throw e as? ToolFailure ?: ToolFailure(PdfErrorCodes.IO_ERROR, e.message)
        }
        try {
            return use { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        } finally {
            file.delete()
        }
    }

    /**
     * Writes one file: [write] gets the descriptor (and closes it, or hands it to a call that does) and answers the
     * engine result, whose warnings are kept and which [summarize] reads; the file is published when it returns and
     * removed when it throws.
     * zh-CN: 写入一个文件: [write] 获得描述符 (由它关闭, 或交给会关闭它的调用) 并返回引擎结果, 其中的警告被保留, 并由 [summarize] 读取;
     * [write] 返回时发布文件, 抛出异常时删除.
     */
    private fun file(name: String, mimeType: String, summarize: (Bundle) -> ToolSummary? = { null }, write: (ParcelFileDescriptor) -> Bundle?): ToolResult {
        checkCancelled()
        val pending = hostCall { output.createFile(name, mimeType) }
        try {
            val answer = write(pending.descriptor)
            checkCancelled()
            val path = hostCall { pending.commit() }
            return ToolResult(path, 1, ToolWarning.of(answer), summarize(answer ?: Bundle()))
        } catch (e: Throwable) {
            runCatching { pending.descriptor.close() }
            pending.abort()
            throw e
        }
    }

    /** Writes one folder: [write] creates its files and answers their count. zh-CN: 写入一个文件夹: [write] 创建其中的文件并返回文件数. */
    private fun folder(name: String, write: (ToolOutput.PendingFolder) -> Int): ToolResult {
        checkCancelled()
        val pending = hostCall { output.createFolder(name) }
        try {
            val files = write(pending)
            checkCancelled()
            return ToolResult(hostCall { pending.commit() }, files, emptyList())
        } catch (e: Throwable) {
            pending.abort()
            throw e
        }
    }

    private fun <T> withDocument(input: ToolInput, use: (IPdfDocument) -> T): T {
        checkCancelled()
        val source = openSource(input.target)
        val options = request {
            input.password?.let { putString(PdfContract.KEY_PASSWORD, it) }
            putString(PdfContract.KEY_DISPLAY_NAME, input.target.displayName)
        }
        // open() closes the source. zh-CN: open() 会关闭源.
        val document = engineCall { plugin.open(source, options) }
        try {
            return use(document)
        } finally {
            runCatching { document.close() }
        }
    }

    /**
     * Starts an operation through [start] and waits for its end, reporting its progress mapped by [overall].
     * zh-CN: 经 [start] 启动操作并等待其结束, 进度经 [overall] 换算后报告.
     */
    private fun await(start: (IPdfProgressCallback) -> Int, overall: (Float) -> Float): Bundle {
        val done = CountDownLatch(1)
        var outcome: Bundle? = null
        var failed = false
        val callback = object : IPdfProgressCallback.Stub() {
            override fun onProgress(opId: Int, event: Bundle?) {
                val percent = event?.getInt(PdfContract.KEY_PERCENT) ?: return
                progress(overall(percent / PERCENT))
            }

            override fun onComplete(opId: Int, result: Bundle?) {
                outcome = result
                done.countDown()
            }

            override fun onError(opId: Int, error: Bundle?) {
                outcome = error
                failed = true
                done.countDown()
            }
        }
        val id = engineCall { start(callback) }
        operation = id
        if (cancelled.get()) runCatching { plugin.cancel(id) }
        try {
            done.await()
        } finally {
            operation = NO_OPERATION
        }
        val answer = outcome ?: Bundle()
        if (failed) throw failureOf(answer)
        // The engine drops reports inside its throttle interval, the last one included. zh-CN: 引擎丢弃节流间隔内的报告, 包括最后一次.
        progress(overall(1f))
        return answer
    }

    private fun openSource(target: ToolTarget): ParcelFileDescriptor = try {
        open(target.uri)
    } catch (e: FileNotFoundException) {
        throw ToolFailure(PdfErrorCodes.FILE_NOT_FOUND, e.message)
    } catch (e: SecurityException) {
        throw ToolFailure(PdfErrorCodes.PERMISSION_DENIED, e.message)
    } catch (e: Exception) {
        throw ToolFailure(PdfErrorCodes.IO_ERROR, e.message)
    }

    private fun pagesOf(spec: String?, pageCount: Int): IntArray = try {
        PageRanges.parse(spec, pageCount)
    } catch (e: PdfFailure) {
        throw ToolFailure(e.code, e.detail)
    }

    private fun checkCancelled() {
        if (cancelled.get()) throw ToolFailure(PdfErrorCodes.CANCELLED, null)
    }

    private fun checked(answer: Bundle?): Bundle {
        val result = answer ?: throw ToolFailure(PdfErrorCodes.INTERNAL, "no answer")
        if (result.getString(PdfContract.KEY_ERROR_CODE) != null) throw failureOf(result)
        return result
    }

    private fun failureOf(answer: Bundle): ToolFailure {
        val code = answer.getString(PdfContract.KEY_ERROR_CODE) ?: PdfErrorCodes.INTERNAL
        return ToolFailure(if (cancelled.get()) PdfErrorCodes.CANCELLED else code, answer.getString(PdfContract.KEY_ERROR_MESSAGE))
    }

    /** An engine call that throws `CODE: detail`. zh-CN: 以 `CODE: detail` 形式抛出异常的引擎调用. */
    private fun <T> engineCall(block: () -> T): T = try {
        block()
    } catch (e: ToolFailure) {
        throw e
    } catch (e: RuntimeException) {
        val decoded = PdfErrorCodes.decode(e.message)
        throw ToolFailure(decoded?.first ?: PdfErrorCodes.INTERNAL, decoded?.second ?: e.message)
    }

    /** A call to the file manager's host session. zh-CN: 对文件管理器宿主会话的调用. */
    private fun <T> hostCall(block: () -> T): T = try {
        block()
    } catch (e: ToolFailure) {
        throw e
    } catch (e: Exception) {
        throw ToolFailure(ToolFailure.OUTPUT_FAILED, e.message ?: e.javaClass.simpleName)
    }

    /**
     * A request of [version]: the operations added in 1.1.0 (`stamp`, `compress`, `fillForm`, `setInfo`, `setOutline`)
     * need `CONTRACT_VERSION_2`, and every tool asks with the highest version so that its warnings carry their codes
     * for the result page (Q264 b).
     * zh-CN: [version] 版本的请求: 1.1.0 新增的操作 (`stamp`, `compress`, `fillForm`, `setInfo`, `setOutline`) 需要 `CONTRACT_VERSION_2`;
     * 所有工具都以最高版本请求, 使警告带上代码供结果页使用 (Q264 b).
     */
    private fun request(op: String? = null, version: Int = ThreePaperPdfContract.MAX_CONTRACT_VERSION, fill: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        putInt(PdfContract.KEY_CONTRACT_VERSION, version)
        op?.let { putString(PdfContract.KEY_OP, it) }
        fill()
    }

    /** A number of an engine answer, 0 when absent. zh-CN: 引擎应答中的数值, 缺少时为 0. */
    @Suppress("DEPRECATION")
    private fun number(answer: Bundle, key: String): Number = answer.get(key) as? Number ?: 0

    companion object {

        /** Parts per `split` call, so a split of up to 1000 parts never holds many descriptors. zh-CN: 每次 `split` 调用的部分数, 使至多 1000 部分的拆分不会同时持有大量描述符. */
        const val SPLIT_BATCH = 32

        const val MIME_PDF = "application/pdf"

        const val MIME_TEXT = "text/plain"

        const val JPEG_QUALITY = 90

        private const val EXTENSION_PNG = "png"

        private const val EXTENSION_JPEG = "jpg"

        private const val PERCENT = 100f

        private const val NO_OPERATION = -1

        private const val FULL_TURN = 360

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptHarness.Call
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptHarness.call
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptHarness.passwordOf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.CompressState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.OutputNames
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolRunner
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolPayloads
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PageRanges
import org.autojs.plugin.pdf.api.PdfContract

/**
 * The calls a script of a request must make (roadmap P6.13, E1), built from the request and the tool's own builders
 * (`ToolPayloads`, `OutputNames`, `PageRanges`) rather than from the generator, in the canonical form of [canonical]:
 * a watermark as the stamp item the tool sends, page numbers, headers and footers expanded into the stamp items the
 * host makes of the shorthand keys (`PdfEditArguments` of the host), compression with the numbers it runs with.
 * zh-CN: 一个请求的脚本必须发出的调用 (路线图 P6.13, E1), 依据请求与工具自身的构造代码 (`ToolPayloads`, `OutputNames`, `PageRanges`)
 * 得出, 不依据生成器, 采用 [canonical] 的规范形式: 水印为工具发送的盖章条目, 页码, 页眉与页脚展开为宿主由简写键生成的盖章条目 (宿主的
 * `PdfEditArguments`), 压缩为实际执行的数值.
 */
internal object ScriptExpectations {

    private val TOP_ROW = listOf(PdfContract.POSITION_TOP_LEFT, PdfContract.POSITION_TOP_CENTER, PdfContract.POSITION_TOP_RIGHT)

    private val BOTTOM_ROW = listOf(PdfContract.POSITION_BOTTOM_LEFT, PdfContract.POSITION_BOTTOM_CENTER, PdfContract.POSITION_BOTTOM_RIGHT)

    private const val DEFAULT_FONT_SIZE = 10.0

    /** The calls of [request], file writes as `write(path, text)` (Rhino also ensures the folder). zh-CN: [request] 的调用. */
    fun of(request: ScriptRequest): List<Call> {
        val input = request.inputs.first()
        val inputPath = input.file.path
        val output = request.output.path
        val password: Map<String, Any?> = if (input.password) mapOf("password" to passwordOf("password")) else emptyMap()
        val calls = ArrayList<Call>()
        fun add(name: String, vararg args: Any?) {
            calls += call(name, *args)
        }
        fun finish(operation: String) {
            add("console.warn", "w-$operation")
            add("console.log", output)
        }
        fun open() = if (input.password) add("pdf.open", inputPath, password) else add("pdf.open", inputPath)
        when (val options = request.options) {
            is ToolOptions.Merge -> {
                val opts = linkedMapOf<String, Any?>("outline" to if (options.keepOutline) PdfContract.OUTLINE_MERGE else PdfContract.OUTLINE_NONE)
                if (options.keepOutline) opts["displayNames"] = request.inputs.map { OutputNames.stem(it.name) }
                if (request.inputs.any { it.password }) {
                    opts["passwords"] = request.inputs.mapIndexed { index, item -> if (item.password) passwordOf("password${index + 1}") else null }
                }
                add("pdf.merge", request.inputs.map { it.file.path }, output, opts)
                finish("merge")
            }
            is ToolOptions.Split -> {
                val opts = LinkedHashMap<String, Any?>()
                options.every?.let { opts["every"] = it }
                options.ranges?.let { opts["ranges"] = it }
                opts += password
                add("pdf.split", inputPath, output, opts)
                add("console.warn", "w-split")
                add("console.log", "$output/part-1.pdf")
                add("console.log", "$output/part-2.pdf")
            }
            is ToolOptions.ToImages -> {
                open()
                val jpeg = options.format == PdfContract.FORMAT_JPEG
                val render = linkedMapOf<String, Any?>("dpi" to options.dpi, "format" to options.format)
                if (jpeg) render["quality"] = PdfToolRunner.JPEG_QUALITY
                PageRanges.parse(options.pages, ScriptHarness.PAGE_COUNT).forEach { page ->
                    val path = output + "/" + OutputNames.imagePage(input.name, page, ScriptHarness.PAGE_COUNT, if (jpeg) "jpg" else "png")
                    add("doc.renderToFile", page, path, render)
                    add("console.log", path)
                }
                add("doc.close")
            }
            is ToolOptions.FromImages -> {
                add("pdf.fromImages", request.inputs.map { it.file.path }, output, mapOf("pageSize" to options.pageSize, "orientation" to options.orientation, "margin" to options.margin))
                finish("fromImages")
            }
            is ToolOptions.Encrypt -> {
                add("pdf.encrypt", inputPath, output, password + mapOf("userPassword" to passwordOf("newPassword"), "keyLength" to request.keyLength))
                finish("encrypt")
            }
            ToolOptions.Decrypt -> {
                if (input.password) add("pdf.decrypt", inputPath, output, password) else add("pdf.decrypt", inputPath, output)
                finish("decrypt")
            }
            is ToolOptions.ExtractText -> {
                val opts = LinkedHashMap<String, Any?>()
                options.pages?.let { opts["pages"] = it }
                opts += password
                if (opts.isEmpty()) add("pdf.text", inputPath) else add("pdf.text", inputPath, opts)
                add("files.ensureDir", output)
                add("write", output, ScriptHarness.TEXTS.joinToString(Char(10).toString()))
                add("console.log", output)
            }
            is ToolOptions.Organize -> {
                open()
                val placed = options.pages
                val angles = placed.map { Math.floorMod(it.rotation, 360) }.filter { it != 0 }.distinct().sorted()
                fun turned(angle: Int, page: (Int, Int) -> Int) = PageRanges.format(
                    placed.withIndex().filter { Math.floorMod(it.value.rotation, 360) == angle }.map { page(it.index, it.value.page) }.sorted().toIntArray(),
                )
                // As the tool's rounds: the first save names source pages, later turns positions of the result.
                // zh-CN: 与工具的各轮相同: 第一次保存指源文档的页码, 之后的旋转指结果中的位置.
                val save = linkedMapOf<String, Any?>("pages" to PageRanges.format(placed.map { it.page }.sorted().toIntArray()), "order" to placed.map { it.page })
                angles.firstOrNull()?.let { angle -> save["rotate"] = mapOf("degrees" to angle, "pages" to turned(angle) { _, page -> page }) }
                add("doc.save", output, save)
                add("doc.close")
                add("console.warn", "w-save")
                angles.drop(1).forEach { angle ->
                    add("pdf.rotate", output, output, angle, mapOf("pages" to turned(angle) { index, _ -> index + 1 }))
                    add("console.warn", "w-rotate")
                }
                add("console.log", output)
            }
            is ToolOptions.Watermark -> {
                val image = if (options.type == PdfContract.STAMP_IMAGE) (request.image?.path ?: ScriptPaths.GUESS_FOLDER + "/image.png") else null
                add("pdf.watermark", inputPath, output, mapOf("item" to ToolPayloads.watermarkItem(options), "image" to image, "password" to password["password"]))
                finish("watermark")
            }
            is ToolOptions.PageNumbers -> {
                add("pdf.stamp", inputPath, output, mapOf("items" to ToolPayloads.pageNumberItems(options), "password" to password["password"]))
                finish("stamp")
            }
            is ToolOptions.Compress -> {
                add(
                    "pdf.compress", inputPath, output,
                    mapOf(
                        "preset" to options.preset, "imageDpi" to options.imageDpi, "imageQuality" to options.imageQuality,
                        "removeMetadata" to options.removeMetadata, "objectStreams" to options.objectStreams, "password" to password["password"],
                    ),
                )
                finish("compress")
            }
            is ToolOptions.FillForm -> {
                val values = options.values.filterKeys { it != "onProgress" }
                val opts = LinkedHashMap<String, Any?>()
                if (options.flatten) opts["flatten"] = true
                opts += password
                if (opts.isEmpty()) add("pdf.fillForm", inputPath, output, values) else add("pdf.fillForm", inputPath, output, values, opts)
                finish("fillForm")
            }
            is ToolOptions.Properties -> {
                if (options.info.isNotEmpty() || !options.clearOutline) {
                    if (input.password) add("pdf.setInfo", inputPath, output, options.info, password) else add("pdf.setInfo", inputPath, output, options.info)
                    add("console.warn", "w-setInfo")
                    if (options.clearOutline) {
                        add("pdf.setOutline", output, output, emptyList<Any>())
                        add("console.warn", "w-setOutline")
                    }
                } else {
                    if (input.password) add("pdf.setOutline", inputPath, output, emptyList<Any>(), password) else add("pdf.setOutline", inputPath, output, emptyList<Any>())
                    add("console.warn", "w-setOutline")
                }
                add("console.log", output)
            }
        }
        return calls
    }

    /**
     * [calls] in the canonical form of [of]: the options of `pdf.watermark` as `{ item, image, password }`, those of
     * `pdf.stamp` as `{ items, password }` and those of `pdf.compress` with the numbers it runs with.
     * zh-CN: [of] 规范形式的 [calls]: `pdf.watermark` 的选项为 `{ item, image, password }`, `pdf.stamp` 的选项为 `{ items, password }`,
     * `pdf.compress` 的选项为实际执行的数值.
     */
    fun canonical(calls: List<Call>): List<Call> = calls.map { call ->
        val opts = call.args.getOrNull(2) as? Map<*, *>
        when {
            opts == null -> call
            call.name == "pdf.watermark" -> call.copy(args = call.args.take(2) + listOf(watermark(opts)))
            call.name == "pdf.stamp" -> call.copy(args = call.args.take(2) + listOf(stamp(opts)))
            call.name == "pdf.compress" -> call.copy(args = call.args.take(2) + listOf(compress(opts)))
            else -> call
        }
    }

    private fun watermark(opts: Map<*, *>): Map<String, Any?> {
        val item = LinkedHashMap<String, Any?>()
        opts.forEach { (key, value) -> if (key != "password" && key != "image") item[key as String] = value }
        val image = opts["image"]
        if (image != null) {
            item["type"] = PdfContract.STAMP_IMAGE
            item["image"] = 0.0
        } else {
            item["type"] = PdfContract.STAMP_TEXT
        }
        return mapOf("item" to item, "image" to image, "password" to opts["password"])
    }

    /** The stamp items of the options, as the host expands the shorthand keys. zh-CN: 选项对应的盖章条目, 与宿主展开简写键的方式相同. */
    private fun stamp(opts: Map<*, *>): Map<String, Any?> {
        val items = ArrayList<Any?>()
        val pages = opts["pages"] as String?
        val shared = LinkedHashMap<String, Any?>()
        shared["fontSize"] = opts["fontSize"] ?: DEFAULT_FONT_SIZE
        pages?.let { shared["pages"] = it }
        fun text(text: Any?, position: Any?, own: Map<String, Any?> = emptyMap()) {
            items += linkedMapOf<String, Any?>("type" to PdfContract.STAMP_TEXT, "text" to text, "position" to position) + own + shared
        }
        (opts["pageNumbers"] as Map<*, *>?)?.let { numbers ->
            val start = (numbers["start"] as Double?)?.toInt() ?: 1
            val offset = start - firstPage(pages)
            text(
                numbers["format"] ?: ToolPayloads.PAGE_NUMBER_FORMAT,
                numbers["position"] ?: ToolPayloads.PAGE_NUMBER_POSITION,
                if (offset != 0) mapOf("pageOffset" to offset.toDouble()) else emptyMap(),
            )
        }
        listOf("header" to TOP_ROW, "footer" to BOTTOM_ROW).forEach { (key, row) ->
            val slots = when (val value = opts[key]) {
                null -> emptyList()
                is String -> listOf(null, value, null)
                is Map<*, *> -> listOf(value["left"], value["center"], value["right"])
                else -> throw AssertionError("$key is $value")
            }
            slots.zip(row).forEach { (slot, position) -> if (slot != null) text(slot, position) }
        }
        (opts["items"] as List<*>?)?.let { items.addAll(it) }
        return mapOf("items" to items, "password" to opts["password"])
    }

    private fun compress(opts: Map<*, *>): Map<String, Any?> {
        val numbers = CompressState.numbersOf(opts["preset"] as String)
        return mapOf(
            "preset" to opts["preset"],
            "imageDpi" to (opts["imageDpi"] ?: numbers.imageDpi.toDouble()),
            "imageQuality" to (opts["imageQuality"] ?: numbers.imageQuality.toDouble()),
            "removeMetadata" to opts["removeMetadata"],
            "objectStreams" to opts["objectStreams"],
            "password" to opts["password"],
        )
    }

    /** The first page of a range: the smallest start of its items, 1 without a range. zh-CN: 范围的第一页. */
    private fun firstPage(pages: String?): Int =
        pages?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.minOfOrNull { it.substringBefore('-').trim().toInt() } ?: 1

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.CompressState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.OrganizedPage
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolKind
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.ToolOptions
import org.autojs.plugin.pdf.api.PdfContract

/**
 * The tool requests the code generator tests run (roadmap P6.13): every tool with all its fields and with only the
 * required ones, plus variants that take other branches (image watermark, page numbers from 0, properties that only
 * clear the outline). Texts and names carry quotes, backslashes, line breaks, U+2028 / U+2029, Chinese and an emoji;
 * they are built from code points so the source stays plain ASCII. [SECRETS] are the passwords the requests are built
 * with: none of them may reach a script.
 * zh-CN: 代码生成测试使用的工具请求 (路线图 P6.13): 每个工具各一个全字段请求与一个只含必填项的请求, 另加走其他分支的变体 (图片水印,
 * 从 0 起的页码, 只清除书签的文档属性). 文字与名称含引号, 反斜杠, 换行, U+2028 / U+2029, 中文与 emoji; 它们由码位拼成, 源码因此只含
 * ASCII. [SECRETS] 是构造请求时用的密码: 都不得进入脚本.
 */
internal object ScriptFixtures {

    /** Text from code points. zh-CN: 由码位拼成的文字. */
    fun text(vararg codePoints: Int): String = codePoints.joinToString("") { String(Character.toChars(it)) }

    /** Quotes, backslash, line breaks and separators, Chinese, an emoji and a bidi override. zh-CN: 引号, 反斜杠, 换行与分隔符, 中文, emoji 与双向覆盖字符. */
    val TRICKY: String = "It's \"q\" " + text(0x5C) + " a" + text(0x5C, 0x5C) + "b" + text(0x0A) + "c" + text(0x0D, 0x09) + "d " +
        text(0x2028) + " e " + text(0x2029) + " " + text(0x4E2D, 0x6587, 0x6C34, 0x5370) + " " + text(0x1F600) + " " + text(0x202E) + "f" + text(0x202C) + " ${'$'}{x} `t` </script>"

    /** A file name with a quote, Chinese and an emoji. zh-CN: 含引号, 中文与 emoji 的文件名. */
    val NAME: String = "Report 'Q3' " + text(0x62A5, 0x544A) + " " + text(0x1F4C4) + ".pdf"

    const val ROOT = "/storage/emulated/0/Documents/3-Paper PDF"

    val SECRETS = listOf("Pw-Input-7f3a", "Pw-New-91c2", "Pw-Merge-2b8d")

    fun input(name: String = NAME, password: Boolean = false, guessed: Boolean = false) =
        ScriptInput(ScriptFile((if (guessed) ScriptPaths.GUESS_FOLDER else ROOT) + "/" + name, guessed), name, password)

    fun output(name: String) = ScriptFile("$ROOT/out/$name")

    /** A named request: [name] tells the case in failures. zh-CN: 带名称的请求: [name] 用于失败信息. */
    class Case(val name: String, val request: ScriptRequest)

    private fun case(name: String, kind: ToolKind, options: ToolOptions, inputs: List<ScriptInput> = listOf(input()), output: ScriptFile = output("$name.pdf"), image: ScriptFile? = null, keyLength: Int = ScriptRequest.KEY_LENGTH_256) =
        Case(name, ScriptRequest.of(kind, inputs, options, output, image, keyLength))

    private val watermarkText = ToolOptions.Watermark(
        type = PdfContract.STAMP_TEXT, text = TRICKY, image = null, position = PdfContract.POSITION_CENTER, rotation = 12.5, opacity = 0.333, tile = true,
        color = "#80112233", fontSize = 37.5, width = null, layer = PdfContract.LAYER_UNDER, pages = "1-2, 4",
    )

    private val watermarkImage = ToolOptions.Watermark(
        type = PdfContract.STAMP_IMAGE, text = "", image = null, position = PdfContract.POSITION_TOP_RIGHT, rotation = -36000.0, opacity = 1.0, tile = false,
        color = null, fontSize = null, width = 144.5, layer = PdfContract.LAYER_OVER, pages = null,
    )

    private val watermarkMinimal = ToolOptions.Watermark(
        type = PdfContract.STAMP_TEXT, text = "DRAFT", image = null, position = PdfContract.POSITION_CENTER, rotation = 45.0, opacity = 0.3, tile = false,
        color = null, fontSize = null, width = null, layer = PdfContract.LAYER_OVER, pages = null,
    )

    private val numbersFull = ToolOptions.PageNumbers(
        format = "Page {page} of {total} " + TRICKY, position = PdfContract.POSITION_BOTTOM_RIGHT, start = 5,
        header = ToolOptions.Slots(left = TRICKY, center = " ", right = "R"), footer = ToolOptions.Slots(center = "F"), fontSize = 9.5, pages = "2-",
    )

    private val low = CompressState.numbersOf(PdfContract.COMPRESS_PRESET_LOW)

    private val medium = CompressState.numbersOf(PdfContract.COMPRESS_PRESET_MEDIUM)

    /** Every case; the full ones first. zh-CN: 全部用例; 全字段的在前. */
    val ALL: List<Case> = listOf(
        case(
            "merge-full", ToolKind.MERGE, ToolOptions.Merge(keepOutline = true),
            inputs = listOf(input(), input("b" + text(0x0A) + "c.pdf", password = true), input("guess.pdf", guessed = true)),
        ),
        case("merge-minimal", ToolKind.MERGE, ToolOptions.Merge(keepOutline = false), inputs = listOf(input("a.pdf"), input("b.pdf"))),
        case("split-full", ToolKind.SPLIT, ToolOptions.Split(every = null, ranges = listOf("1-3", "5-", "2")), inputs = listOf(input(password = true)), output = ScriptFile("$ROOT/out/parts")),
        case("split-minimal", ToolKind.SPLIT, ToolOptions.Split(every = 2, ranges = null), output = ScriptFile("$ROOT/out")),
        case("to-images-full", ToolKind.TO_IMAGES, ToolOptions.ToImages(PdfContract.FORMAT_JPEG, 300, " 2-3, ,5,12- "), inputs = listOf(input(password = true)), output = ScriptFile("$ROOT/out/images")),
        case("to-images-minimal", ToolKind.TO_IMAGES, ToolOptions.ToImages(PdfContract.FORMAT_PNG, 144, null), output = ScriptFile("$ROOT/out")),
        case(
            "from-images-full", ToolKind.FROM_IMAGES, ToolOptions.FromImages(PdfContract.PAGE_SIZE_A4, PdfContract.ORIENTATION_LANDSCAPE, 12.5f),
            inputs = listOf(input("a.jpg"), input(text(0x56FE) + "'2'.png"), input("c.webp", guessed = true)),
        ),
        case("from-images-minimal", ToolKind.FROM_IMAGES, ToolOptions.FromImages(PdfContract.PAGE_SIZE_FIT, PdfContract.ORIENTATION_AUTO, 0f), inputs = listOf(input("a.png"))),
        case("encrypt-full", ToolKind.ENCRYPT, ToolOptions.Encrypt(SECRETS[1]), inputs = listOf(input(password = true)), keyLength = 128),
        case("encrypt-minimal", ToolKind.ENCRYPT, ToolOptions.Encrypt(SECRETS[1])),
        case("decrypt-full", ToolKind.DECRYPT, ToolOptions.Decrypt, inputs = listOf(input(password = true))),
        case("decrypt-minimal", ToolKind.DECRYPT, ToolOptions.Decrypt),
        case("extract-text-full", ToolKind.EXTRACT_TEXT, ToolOptions.ExtractText("1, 3-"), inputs = listOf(input(password = true)), output = output("text.txt")),
        case("extract-text-minimal", ToolKind.EXTRACT_TEXT, ToolOptions.ExtractText(null), output = output("text.txt")),
        case(
            "organize-full", ToolKind.ORGANIZE,
            ToolOptions.Organize(listOf(OrganizedPage(3, 90), OrganizedPage(1, 0), OrganizedPage(2, 180), OrganizedPage(5, 270), OrganizedPage(4, -90), OrganizedPage(7, 450))),
            inputs = listOf(input(password = true)),
        ),
        case("organize-minimal", ToolKind.ORGANIZE, ToolOptions.Organize(listOf(OrganizedPage(2, 0), OrganizedPage(1, 0)))),
        case("watermark-full", ToolKind.WATERMARK, watermarkText, inputs = listOf(input(password = true))),
        case("watermark-image", ToolKind.WATERMARK, watermarkImage, image = ScriptFile("$ROOT/" + text(0x5370) + " 'seal'.png")),
        case("watermark-minimal", ToolKind.WATERMARK, watermarkMinimal),
        case("page-numbers-full", ToolKind.PAGE_NUMBERS, numbersFull, inputs = listOf(input(password = true))),
        case("page-numbers-from-zero", ToolKind.PAGE_NUMBERS, numbersFull.copy(start = 0, pages = "3, 5-6")),
        case("page-numbers-header-only", ToolKind.PAGE_NUMBERS, numbersFull.copy(format = null, footer = ToolOptions.Slots(), pages = null)),
        case(
            "page-numbers-minimal", ToolKind.PAGE_NUMBERS,
            ToolOptions.PageNumbers("{page}", PdfContract.POSITION_BOTTOM_CENTER, 1, ToolOptions.Slots(), ToolOptions.Slots(), 10.0, null),
        ),
        case("compress-full", ToolKind.COMPRESS, ToolOptions.Compress(PdfContract.COMPRESS_PRESET_LOW, 123, 42, removeMetadata = true, objectStreams = true), inputs = listOf(input(password = true))),
        case("compress-preset", ToolKind.COMPRESS, ToolOptions.Compress(PdfContract.COMPRESS_PRESET_LOW, low.imageDpi, low.imageQuality, removeMetadata = false, objectStreams = true)),
        case("compress-minimal", ToolKind.COMPRESS, ToolOptions.Compress(PdfContract.COMPRESS_PRESET_MEDIUM, medium.imageDpi, medium.imageQuality, removeMetadata = false, objectStreams = false)),
        case(
            "fill-form-full", ToolKind.FILL_FORM,
            ToolOptions.FillForm(
                linkedMapOf("name" to TRICKY, "agree" to true, "off" to false, "choices" to listOf("a", TRICKY), "reset" to null, "__proto__" to "proto", "onProgress" to "skipped", "a.b[0]" to "dotted"),
                flatten = true,
            ),
            inputs = listOf(input(password = true)),
        ),
        case("fill-form-minimal", ToolKind.FILL_FORM, ToolOptions.FillForm(mapOf("field" to "value"), flatten = false)),
        case(
            "properties-full", ToolKind.PROPERTIES,
            ToolOptions.Properties(linkedMapOf("title" to TRICKY, "author" to null, "subject" to "S", "keywords" to "k1, k2", "creator" to "C"), clearOutline = true),
            inputs = listOf(input(password = true)),
        ),
        case("properties-outline-only", ToolKind.PROPERTIES, ToolOptions.Properties(emptyMap(), clearOutline = true), inputs = listOf(input(password = true))),
        case("properties-minimal", ToolKind.PROPERTIES, ToolOptions.Properties(mapOf("title" to "T"), clearOutline = false)),
    )

}

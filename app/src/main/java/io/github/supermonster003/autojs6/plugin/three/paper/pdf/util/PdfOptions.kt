package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import kotlin.math.roundToLong

/**
 * Typed and validated access to the keys of one request (protocol "Methods"). Every getter fails with
 * `INVALID_ARGUMENT` when the key carries a value of the wrong type or outside its range; absent keys yield the
 * documented default. Keys the plugin does not know are ignored, so later contract versions can add optional keys.
 * zh-CN: 一个请求各键的类型化与校验访问 (协议 "Methods" 一节). 键的值类型不对或超出范围时, 各读取方法报 `INVALID_ARGUMENT`; 缺省的键
 * 取文档规定的默认值. 忽略插件不认识的键, 以便后续契约版本追加可选键.
 */
class PdfOptions(private val values: PdfValues) {

    /**
     * The contract version of the request: absent is `INVALID_ARGUMENT` (the host writes it into every request), a
     * version the plugin does not implement is `UNSUPPORTED` (`PLUGIN_INCOMPATIBLE` is reported by the host only).
     * zh-CN: 请求的契约版本: 缺失报 `INVALID_ARGUMENT` (宿主在每个请求里都写入), 插件未实现的版本报 `UNSUPPORTED` (`PLUGIN_INCOMPATIBLE`
     * 只由宿主报告).
     */
    fun contractVersion(): Int {
        val version = int(PdfContract.KEY_CONTRACT_VERSION) ?: throw PdfFailure.invalid("request carries no ${PdfContract.KEY_CONTRACT_VERSION}")
        if (!PdfContract.supportsContractVersion(version)) {
            throw PdfFailure.unsupported(
                "contract version $version is outside ${PdfContract.MIN_CONTRACT_VERSION}..${PdfContract.MAX_CONTRACT_VERSION}",
            )
        }
        return version
    }

    fun string(key: String): String? = typed(key, "a String")

    fun boolean(key: String, default: Boolean): Boolean = typed<Boolean>(key, "a Boolean") ?: default

    fun int(key: String): Int? = typed(key, "an Int")

    fun int(key: String, range: IntRange, default: Int): Int {
        val value = int(key) ?: return default
        if (value !in range) throw PdfFailure.invalid("$key $value is outside ${range.first}..${range.last}")
        return value
    }

    fun double(key: String): Double? {
        val value = typed<Double>(key, "a Double") ?: return null
        if (!value.isFinite()) throw PdfFailure.invalid("$key must be finite: $value")
        return value
    }

    fun stringArray(key: String): Array<String?>? {
        val value = values[key] ?: return null
        if (value !is Array<*> || !value.isArrayOf<String>()) throw PdfFailure.invalid("$key must be a String array")
        @Suppress("UNCHECKED_CAST")
        return value as Array<String?>
    }

    /**
     * A String of [allowed] (a closed value set of [PdfContract]), or [default] when absent.
     * zh-CN: 取自 [allowed] ([PdfContract] 的封闭取值集合) 的 String, 缺省时为 [default].
     */
    fun choice(key: String, allowed: Set<String>, default: String?): String? {
        val value = string(key) ?: return default
        if (value !in allowed) throw PdfFailure.invalid("$key \"$value\" is not one of ${allowed.sorted().joinToString(", ")}")
        return value
    }

    /** `page`: one page counted from 1, `PAGE_OUT_OF_RANGE` outside the document. zh-CN: `page`: 从 1 计数的一页, 超出文档报 `PAGE_OUT_OF_RANGE`. */
    fun page(pageCount: Int, key: String = PdfContract.KEY_PAGE, default: Int? = null): Int {
        val page = int(key) ?: default ?: throw PdfFailure.invalid("request carries no $key")
        if (page !in 1..pageCount) throw PdfFailure.pageOutOfRange("$key $page is outside 1..$pageCount")
        return page
    }

    /** A page range key ([PageRanges]); absent means every page. zh-CN: 页码范围键 ([PageRanges]); 缺省表示全部页. */
    fun pages(pageCount: Int, key: String = PdfContract.KEY_PAGES): IntArray = PageRanges.parse(string(key), pageCount)

    /** `password`: at most `MAX_PASSWORD_LENGTH` characters. zh-CN: `password`: 至多 `MAX_PASSWORD_LENGTH` 个字符. */
    fun password(key: String = PdfContract.KEY_PASSWORD): String? {
        val password = string(key) ?: return null
        if (password.length > PdfContract.MAX_PASSWORD_LENGTH) {
            throw PdfFailure.invalid("$key is longer than ${PdfContract.MAX_PASSWORD_LENGTH} characters")
        }
        return password
    }

    /** `quality` of `renderToFile`: 1 to 100, `DEFAULT_QUALITY` when absent. zh-CN: `renderToFile` 的 `quality`: 1 到 100, 缺省为 `DEFAULT_QUALITY`. */
    fun quality(): Int = int(PdfContract.KEY_QUALITY, PdfContract.MIN_QUALITY..PdfContract.MAX_QUALITY, PdfContract.DEFAULT_QUALITY)

    /** `degrees` of `save`: a multiple of 90, normalized to 0, 90, 180 or 270. zh-CN: `save` 的 `degrees`: 90 的倍数, 归一到 0, 90, 180 或 270. */
    fun degrees(): Int {
        val degrees = int(PdfContract.KEY_DEGREES) ?: return 0
        if (!PdfContract.isValidDegrees(degrees)) throw PdfFailure.invalid("${PdfContract.KEY_DEGREES} $degrees is not a multiple of 90")
        return Math.floorMod(degrees, 360)
    }

    /**
     * The size of a `render` / `renderToFile` request: at most one of `dpi`, `scale`, `width`, `height`
     * (`INVALID_ARGUMENT` for two), `DEFAULT_DPI` when none is given.
     * zh-CN: `render` / `renderToFile` 请求的尺寸: `dpi`, `scale`, `width`, `height` 至多一个 (两个报 `INVALID_ARGUMENT`), 都没有时为 `DEFAULT_DPI`.
     */
    fun renderSize(): RenderSize {
        val given = SIZE_KEYS.filter { values.contains(it) && values[it] != null }
        if (given.size > 1) throw PdfFailure.invalid("render size keys ${given.joinToString(", ")} exclude each other")
        return when (given.firstOrNull()) {
            PdfContract.KEY_SCALE -> {
                val scale = checkNotNull(double(PdfContract.KEY_SCALE))
                val dpi = scale * POINTS_PER_INCH
                if (dpi < PdfContract.MIN_DPI || dpi > PdfContract.MAX_DPI) {
                    throw PdfFailure.invalid("${PdfContract.KEY_SCALE} $scale gives $dpi dpi, outside ${PdfContract.MIN_DPI}..${PdfContract.MAX_DPI}")
                }
                RenderSize.Scale(scale)
            }
            PdfContract.KEY_WIDTH -> RenderSize.Width(int(PdfContract.KEY_WIDTH, 1..Int.MAX_VALUE, 0))
            PdfContract.KEY_HEIGHT -> RenderSize.Height(int(PdfContract.KEY_HEIGHT, 1..Int.MAX_VALUE, 0))
            else -> RenderSize.Dpi(int(PdfContract.KEY_DPI, PdfContract.MIN_DPI..PdfContract.MAX_DPI, PdfContract.DEFAULT_DPI))
        }
    }

    private inline fun <reified T : Any> typed(key: String, description: String): T? {
        val value = values[key] ?: return null
        return value as? T ?: throw PdfFailure.invalid("$key must be $description, not ${value::class.java.simpleName}")
    }

    companion object {

        const val POINTS_PER_INCH = 72.0

        private val SIZE_KEYS = listOf(PdfContract.KEY_DPI, PdfContract.KEY_SCALE, PdfContract.KEY_WIDTH, PdfContract.KEY_HEIGHT)

    }

}

/**
 * The requested size of a rendered page; [pixels] turns it into a bitmap size for a page as displayed.
 * zh-CN: 渲染页的请求尺寸; [pixels] 按页面的显示尺寸换算成位图尺寸.
 */
sealed interface RenderSize {

    /** Pixels per point. zh-CN: 每点的像素数. */
    fun scaleFor(widthPoints: Double, heightPoints: Double): Double

    data class Dpi(val dpi: Int) : RenderSize {
        override fun scaleFor(widthPoints: Double, heightPoints: Double) = dpi / PdfOptions.POINTS_PER_INCH
    }

    data class Scale(val scale: Double) : RenderSize {
        override fun scaleFor(widthPoints: Double, heightPoints: Double) = scale
    }

    data class Width(val width: Int) : RenderSize {
        override fun scaleFor(widthPoints: Double, heightPoints: Double) = width / widthPoints
    }

    data class Height(val height: Int) : RenderSize {
        override fun scaleFor(widthPoints: Double, heightPoints: Double) = height / heightPoints
    }

    /**
     * Bitmap width and height for a page of [widthPoints] x [heightPoints] as displayed (after its rotation), each at
     * least 1 pixel; more than `MAX_RENDER_PIXELS` fails with `LIMIT_EXCEEDED` (roadmap D17).
     * zh-CN: 显示尺寸 (旋转之后) 为 [widthPoints] x [heightPoints] 的页对应的位图宽高, 每边至少 1 像素; 超过 `MAX_RENDER_PIXELS` 报
     * `LIMIT_EXCEEDED` (路线图 D17).
     */
    fun pixels(widthPoints: Double, heightPoints: Double): Pair<Int, Int> {
        require(widthPoints > 0 && heightPoints > 0) { "page size must be positive: $widthPoints x $heightPoints" }
        val scale = scaleFor(widthPoints, heightPoints)
        val width = (widthPoints * scale).roundToLong().coerceAtLeast(1)
        val height = (heightPoints * scale).roundToLong().coerceAtLeast(1)
        if (width > PdfContract.MAX_RENDER_PIXELS / height) {
            throw PdfFailure(PdfErrorCodes.LIMIT_EXCEEDED, "${width} x ${height} pixels exceed ${PdfContract.MAX_RENDER_PIXELS}")
        }
        return width.toInt() to height.toInt()
    }

}

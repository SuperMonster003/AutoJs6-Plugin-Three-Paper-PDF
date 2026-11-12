package io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfOptions
import org.autojs.plugin.pdf.api.PdfContract
import kotlin.math.max
import kotlin.math.min

/**
 * The page geometry of the generation operations (roadmap D18, D22, D8), in points: `pageSize` (`fit`, `A4`, `Letter`
 * or `[width, height]`), `margin`, and for `fromImages` `fit` (`contain` / `cover`) and `orientation`. `fromText` and
 * `fromHtml` have no image to fit, so `pageSize` `fit` gives them A4 with a warning (appendix D Q33).
 * zh-CN: 生成操作的页面几何 (路线图 D18, D22, D8), 单位为磅: `pageSize` (`fit`, `A4`, `Letter` 或 `[width, height]`), `margin`,
 * 以及 `fromImages` 的 `fit` (`contain` / `cover`) 与 `orientation`. `fromText` 与 `fromHtml` 没有可适配的图片, 因此
 * `pageSize` 为 `fit` 时使用 A4 并给出警告 (附录 D Q33).
 */
internal object PageLayout {

    val A4 = PageSize.Fixed(595.27563f, 841.8898f)
    val LETTER = PageSize.Fixed(612f, 792f)

    /**
     * Longest page side: 14,400 points (200 inches), the largest page PDF viewers display without `UserUnit`.
     * zh-CN: 页面最长边: 14,400 磅 (200 英寸), 即 PDF 阅读器无需 `UserUnit` 即可显示的最大页面.
     */
    const val MAX_PAGE_SIDE = 14_400f

    /** Default margin of `fromText` and `fromHtml`: half an inch. zh-CN: `fromText` 与 `fromHtml` 的默认页边距: 半英寸. */
    const val DEFAULT_DOCUMENT_MARGIN = 36f

    /** Default margin of `fromImages`: none. zh-CN: `fromImages` 的默认页边距: 无. */
    const val DEFAULT_IMAGE_MARGIN = 0f

    /** The requested page size. zh-CN: 请求的页面尺寸. */
    sealed interface PageSize {
        data object Fit : PageSize
        data class Fixed(val width: Float, val height: Float) : PageSize
    }

    /** Where an image goes: the page and the box the image fills, clipped to [clip] when set. zh-CN: 图片的位置: 页面, 图片填充的方框, 设置 [clip] 时裁剪到该区域. */
    data class Placement(
        val pageWidth: Float,
        val pageHeight: Float,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val clip: Box?,
    )

    /** A rectangle in PDF points from the lower left corner. zh-CN: 以左下角为原点的 PDF 磅矩形. */
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float)

    fun pageSize(options: PdfOptions): PageSize? = when (val value = options.value(PdfContract.KEY_PAGE_SIZE)) {
        null -> null
        is String -> when (value) {
            PdfContract.PAGE_SIZE_FIT -> PageSize.Fit
            PdfContract.PAGE_SIZE_A4 -> A4
            PdfContract.PAGE_SIZE_LETTER -> LETTER
            else -> throw PdfFailure.invalid("${PdfContract.KEY_PAGE_SIZE} \"$value\" is not one of ${PdfContract.PAGE_SIZES.sorted().joinToString(", ")} or [width, height]")
        }
        is FloatArray -> {
            if (value.size != 2) throw PdfFailure.invalid("${PdfContract.KEY_PAGE_SIZE} must hold [width, height], got ${value.size} numbers")
            val (width, height) = value
            for (side in value) {
                if (!side.isFinite() || side < 1f || side > MAX_PAGE_SIDE) {
                    throw PdfFailure.invalid("${PdfContract.KEY_PAGE_SIZE} sides must be 1 to ${MAX_PAGE_SIDE.toInt()} points, got [$width, $height]")
                }
            }
            PageSize.Fixed(width, height)
        }
        else -> throw PdfFailure.invalid("${PdfContract.KEY_PAGE_SIZE} must be a String or a float array, not ${value::class.java.simpleName}")
    }

    /**
     * The page size of `fromText` / `fromHtml`: A4 when absent; `fit` is A4 too and adds a warning to [warnings].
     * zh-CN: `fromText` / `fromHtml` 的页面尺寸: 缺省为 A4; `fit` 也取 A4, 并向 [warnings] 追加警告.
     */
    fun documentPageSize(options: PdfOptions, op: String, warnings: MutableCollection<String>): PageSize.Fixed = when (val size = pageSize(options)) {
        null -> A4
        PageSize.Fit -> {
            warnings += "$op has no image to fit; ${PdfContract.KEY_PAGE_SIZE} \"${PdfContract.PAGE_SIZE_FIT}\" used A4"
            A4
        }
        is PageSize.Fixed -> size
    }

    fun margin(options: PdfOptions, default: Float): Float = options.float(PdfContract.KEY_MARGIN, 0f..MAX_PAGE_SIDE, default)

    /**
     * The box inside [margin] of [page]; `INVALID_ARGUMENT` when the margins leave less than [minimum] points either way.
     * zh-CN: [page] 去掉 [margin] 后的方框; 页边距使任一方向剩余不足 [minimum] 磅时报 `INVALID_ARGUMENT`.
     */
    fun contentBox(page: PageSize.Fixed, margin: Float, minimum: Float = 1f): Box {
        val width = page.width - 2 * margin
        val height = page.height - 2 * margin
        if (width < minimum || height < minimum) {
            throw PdfFailure.invalid("${PdfContract.KEY_MARGIN} $margin leaves no room on a ${page.width} x ${page.height} point page")
        }
        return Box(margin, margin, width, height)
    }

    /**
     * Places an image of [imageWidth] x [imageHeight] (as displayed, after its EXIF orientation) on a page:
     * - `fit` page size: the page is the image in points (one pixel per point) plus [margin] on each side, scaled down
     *   so no side passes [MAX_PAGE_SIDE];
     * - a fixed size: [orientation] `auto` turns the page to the orientation of the image, `portrait` / `landscape`
     *   force it; the image is scaled to fit inside ([fit] `contain`) or to cover ([fit] `cover`, clipped) the box
     *   inside the margins, centered.
     * zh-CN: 把 [imageWidth] x [imageHeight] (按 EXIF 方向纠正后的显示尺寸) 的图片放到页面上:
     * - `fit` 页面尺寸: 页面为图片 (一像素一磅) 四周加 [margin], 任一边超过 [MAX_PAGE_SIDE] 时等比缩小;
     * - 固定尺寸: [orientation] 为 `auto` 时页面方向随图片, `portrait` / `landscape` 强制方向; 图片在页边距以内的方框中等比缩放, 完整放入
     *   ([fit] `contain`) 或铺满 ([fit] `cover`, 超出部分裁掉), 居中.
     */
    fun place(imageWidth: Int, imageHeight: Int, size: PageSize, fit: String, orientation: String, margin: Float): Placement {
        require(imageWidth > 0 && imageHeight > 0) { "image size $imageWidth x $imageHeight" }
        if (size is PageSize.Fit) {
            val scale = min(1f, min((MAX_PAGE_SIDE - 2 * margin) / imageWidth, (MAX_PAGE_SIDE - 2 * margin) / imageHeight))
            if (scale <= 0f) throw PdfFailure.invalid("${PdfContract.KEY_MARGIN} $margin leaves no room for the image")
            val width = imageWidth * scale
            val height = imageHeight * scale
            return Placement(width + 2 * margin, height + 2 * margin, margin, margin, width, height, clip = null)
        }
        val fixed = size as PageSize.Fixed
        val page = oriented(fixed, orientation, landscapeImage = imageWidth > imageHeight)
        val box = contentBox(page, margin)
        val scale = when (fit) {
            PdfContract.FIT_COVER -> max(box.width / imageWidth, box.height / imageHeight)
            else -> min(box.width / imageWidth, box.height / imageHeight)
        }
        val width = imageWidth * scale
        val height = imageHeight * scale
        val x = box.x + (box.width - width) / 2
        val y = box.y + (box.height - height) / 2
        val clip = if (fit == PdfContract.FIT_COVER && (width > box.width || height > box.height)) box else null
        return Placement(page.width, page.height, x, y, width, height, clip)
    }

    private fun oriented(page: PageSize.Fixed, orientation: String, landscapeImage: Boolean): PageSize.Fixed {
        val landscape = when (orientation) {
            PdfContract.ORIENTATION_PORTRAIT -> false
            PdfContract.ORIENTATION_LANDSCAPE -> true
            else -> if (page.width == page.height) return page else landscapeImage
        }
        val long = max(page.width, page.height)
        val short = min(page.width, page.height)
        return if (landscape) PageSize.Fixed(long, short) else PageSize.Fixed(short, long)
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.fixtures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The committed samples (`samples/fixtures/`, packaged as androidTest assets) on a device (roadmap P0.3): PDFBox-Android
 * extracts the manifest text with its font resources, and the system `PdfRenderer` opens and draws every unencrypted
 * sample, which the later render tests (P2.3) build on.
 * zh-CN: 已提交样例 (`samples/fixtures/`, 作为 androidTest 资产打包) 的设备验证 (路线图 P0.3): PDFBox-Android 借助字体资源提取
 * 清单中的文本, 系统 `PdfRenderer` 打开并绘制每个未加密样例, 后续渲染测试 (P2.3) 以此为基础.
 */
@RunWith(AndroidJUnit4::class)
class SampleFixturesDeviceTest {

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val manifest: JSONObject by lazy { JSONObject(asset("fixtures.json").toString(Charsets.UTF_8)) }

    private val fixtures: JSONObject get() = manifest.getJSONObject("fixtures")

    private fun asset(name: String): ByteArray = testContext.assets.open(name).use { it.readBytes() }

    private fun names(): List<String> = fixtures.keys().asSequence().toList()

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    /** Opens with the owner password where there is one, so text extraction is allowed. zh-CN: 有所有者密码时用它打开, 以允许提取文本. */
    private fun <T> open(name: String, check: (PDDocument) -> T): T {
        val password = fixtures.getJSONObject(name).optString("ownerPassword")
        val bytes = asset(name)
        return (if (password.isEmpty()) PDDocument.load(bytes) else PDDocument.load(bytes, password)).use(check)
    }

    /**
     * Without position sorting the stripper breaks lines inside words on quarter turn pages ("R|o|tate 90" on 90 degrees,
     * "R|o|ta|te| 2|70" on 270 degrees), so the text extraction of P2.2 sorts by position as well.
     * zh-CN: 不按位置排序时, 提取器在转 90 / 270 度的页面上会在词中断行 (90 度为 "R|o|tate 90", 270 度为 "R|o|ta|te| 2|70"),
     * 因此 P2.2 的文本提取同样按位置排序.
     */
    private fun pageText(document: PDDocument, page: Int): List<String> {
        val stripper = PDFTextStripper().apply {
            sortByPosition = true
            startPage = page
            endPage = page
        }
        return stripper.getText(document).lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Copies a sample to the cache, as the host hands the plugin a seekable file descriptor. zh-CN: 把样例复制到缓存, 如同宿主交给插件可随机读取的文件描述符. */
    private fun descriptor(name: String): ParcelFileDescriptor {
        val file = File(targetContext.cacheDir, "sample-$name")
        file.writeBytes(asset(name))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Renders one page on white at [scale] (points to pixels). zh-CN: 以 [scale] (点到像素) 在白底上渲染一页. */
    private fun render(name: String, index: Int, scale: Double): Bitmap = descriptor(name).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    private fun assertColour(message: String, expected: IntArray, actual: Int, tolerance: Int) {
        val channels = intArrayOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        assertTrue("$message: expected ${expected.toList()}, got ${channels.toList()}", expected.indices.all { abs(expected[it] - channels[it]) <= tolerance })
    }

    @Test
    fun pdfBoxExtractsTheManifestText() {
        names().forEach { name ->
            val expected = fixtures.getJSONObject(name)
            open(name) { document ->
                assertEquals(name, expected.getInt("pages"), document.numberOfPages)
                val text = expected.getJSONArray("text")
                for (page in 1..document.numberOfPages) {
                    assertEquals("$name page $page", text.getJSONArray(page - 1).strings(), pageText(document, page))
                }
            }
        }
    }

    @Test
    fun pdfBoxFindsTheSearchTermOnTheListedPages() {
        val search = fixtures.getJSONObject("outline.pdf").getJSONObject("search")
        open("outline.pdf") { document ->
            search.keys().forEach { term ->
                val pages = (1..document.numberOfPages).filter { page -> pageText(document, page).any { it.contains(term) } }
                assertEquals(term, search.getJSONArray(term).let { array -> List(array.length()) { array.getInt(it) } }, pages)
            }
        }
    }

    @Test
    fun storedJpegDecodesWithTheSystemCodec() {
        val photo = fixtures.getJSONObject("images.pdf").getJSONArray("images").getJSONObject(0)
        open("images.pdf") { document ->
            val resources = document.getPage(0).resources
            val image = resources.getXObject(COSName.getPDFName(photo.getString("name"))) as PDImageXObject
            val stored = image.cosObject.createRawInputStream().use { it.readBytes() }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(stored, 0, stored.size))
            assertEquals(photo.getInt("width") to photo.getInt("height"), bitmap.width to bitmap.height)
            val centre = photo.getJSONArray("centre").let { array -> IntArray(array.length()) { array.getInt(it) } }
            assertColour("JPEG centre", centre, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), 12)
        }
    }

    @Test
    fun greyScanJpegsDecodeWithTheSystemCodec() {
        val expected = fixtures.getJSONObject("scanned-multi.pdf")
        val image = expected.getJSONObject("image")
        val glyphs = expected.getJSONObject("glyphs")
        val origin = glyphs.getJSONArray("origin")
        val dot = glyphs.getInt("dot")
        open("scanned-multi.pdf") { document ->
            for (index in 0 until document.numberOfPages) {
                val resources = document.getPage(index).resources
                val picture = resources.getXObject(COSName.getPDFName(image.getString("name"))) as PDImageXObject
                val stored = picture.cosObject.createRawInputStream().use { it.readBytes() }
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(stored, 0, stored.size)) { "page ${index + 1}" }
                try {
                    assertEquals(image.getInt("width") to image.getInt("height"), bitmap.width to bitmap.height)
                    val paper = image.getInt("paper")
                    val ink = image.getInt("ink")
                    assertColour("paper on page ${index + 1}", intArrayOf(paper, paper, paper), bitmap.getPixel(8, 8), 3)
                    assertColour("ink on page ${index + 1}", intArrayOf(ink, ink, ink), bitmap.getPixel(origin.getInt(0) + dot / 2, origin.getInt(1) + dot / 2), 3)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun pdfRendererOpensEveryUnencryptedSampleWithItsDisplayedPageSizes() {
        names().filter { !fixtures.getJSONObject(it).has("userPassword") }.forEach { name ->
            val expected = fixtures.getJSONObject(name)
            val sizes = expected.optJSONArray("mediaBoxes")
            val rotations = expected.optJSONArray("rotations")
            descriptor(name).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    assertEquals(name, expected.getInt("pages"), renderer.pageCount)
                    for (index in 0 until renderer.pageCount) {
                        val size = (sizes?.getJSONArray(index) ?: expected.getJSONArray("pageSize")).let { it.getInt(0) to it.getInt(1) }
                        // PdfRenderer reports the displayed size, so quarter turns swap width and height.
                        // zh-CN: PdfRenderer 报告显示尺寸, 因此转 90 / 270 度时宽高互换.
                        val quarterTurn = (rotations?.getInt(index) ?: 0) % 180 != 0
                        val displayed = if (quarterTurn) size.second to size.first else size
                        renderer.openPage(index).use { page -> assertEquals("$name page ${index + 1}", displayed, page.width to page.height) }
                    }
                }
            }
        }
    }

    @Test
    fun pdfRendererDrawsTheScanAndTheImages() {
        // Half of the 150 dpi scan: a glyph dot of 10 image pixels becomes 5 bitmap pixels. zh-CN: 150 dpi 扫描图的一半: 10 像素的字形点变成 5 像素.
        val scanned = fixtures.getJSONObject("scanned.pdf").getJSONObject("image")
        val scan = render("scanned.pdf", 0, scale = scanned.getInt("width") / 2.0 / 595)
        val paper = scanned.getInt("paper")
        val ink = scanned.getInt("ink")
        assertColour("scan paper", intArrayOf(paper, paper, paper), scan.getPixel(10, 10), 8)
        // The top left dot of the first glyph row with ink in "S": image pixels 130..139 x 160..169.
        // zh-CN: "S" 第一行有墨迹的左上点: 图像像素 130..139 x 160..169.
        assertColour("scan ink", intArrayOf(ink, ink, ink), scan.getPixel(67, 82), 24)

        // Page points to bitmap pixels at 1:1, y from the top. zh-CN: 页面点与位图像素 1:1, y 从顶部算起.
        val page = render("images.pdf", 0, scale = 1.0)
        val images = fixtures.getJSONObject("images.pdf").getJSONArray("images")
        val masked = images.getJSONObject(1)
        val placement = masked.getJSONArray("placement").let { array -> IntArray(4) { array.getInt(it) } }
        val quadrants = masked.getJSONArray("quadrants")
        val (left, bottom, width, height) = placement.toList()
        val top = 842 - bottom - height
        listOf(width / 4 to height / 4, width * 3 / 4 to height / 4, width / 4 to height * 3 / 4, width * 3 / 4 to height * 3 / 4).forEachIndexed { index, (x, y) ->
            val colour = quadrants.getJSONArray(index).let { array -> IntArray(3) { array.getInt(it) } }
            assertColour("quadrant $index", colour, page.getPixel(left + x, top + y), 8)
        }
        // The soft mask leaves the corners transparent, so the white page shows. zh-CN: 软蒙版使四角透明, 露出白色页面.
        assertColour("masked corner", intArrayOf(255, 255, 255), page.getPixel(left + 1, top + 1), 8)
        // The photo is a blue to orange gradient; its top left corner is blue. zh-CN: 照片是蓝到橙的渐变, 左上角为蓝色.
        val photo = images.getJSONObject(0).getJSONArray("placement").let { array -> IntArray(4) { array.getInt(it) } }
        assertColour("photo corner", intArrayOf(37, 92, 214), page.getPixel(photo[0] + 8, 842 - photo[1] - photo[3] + 8), 24)
    }

    companion object {

        @JvmStatic
        @BeforeClass
        fun loadPdfBoxResources() {
            PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }

    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Names of the file manager tool results (appendix D Q74): the stem, the suffixes, the padded numbers and the byte
 * limit of a file name.
 * zh-CN: 文件管理器工具结果的名称 (附录 D Q74): 主干, 后缀, 补零的序号以及文件名的字节上限.
 */
class OutputNamesTest {

    @Test
    fun `the stem drops the pdf extension in any case and keeps other dots`() {
        assertEquals("Report", OutputNames.stem("Report.pdf"))
        assertEquals("Report", OutputNames.stem("Report.PDF"))
        assertEquals("v1.2 notes", OutputNames.stem("  v1.2 notes.pdf  "))
        assertEquals("archive.tar", OutputNames.stem("archive.tar"))
        // A name that is only the extension keeps it, so the result is not an empty stem. zh-CN: 只有扩展名时保留它, 主干不为空.
        assertEquals(".pdf", OutputNames.stem(".pdf"))
    }

    @Test
    fun `separators and control characters become underscores and empty stems fall back to document`() {
        assertEquals("a_b_c_", OutputNames.stem("a/b\\c\u0001.pdf"))
        assertEquals("document", OutputNames.stem("   "))
        assertEquals("document", OutputNames.stem("...."))
    }

    @Test
    fun `the stem is cut to 200 bytes of UTF-8 without splitting a surrogate pair`() {
        assertEquals("\u4e2d".repeat(66), OutputNames.stem("\u4e2d".repeat(100) + ".pdf"))
        val emoji = "\uD83D\uDE00"
        assertEquals("a" + emoji.repeat(49), OutputNames.stem("a" + emoji.repeat(60)))
        assertEquals("x".repeat(OutputNames.MAX_STEM_BYTES), OutputNames.stem("x".repeat(300)))
        // A cut that ends in blanks drops them. zh-CN: 截断后末尾的空白被去掉.
        assertEquals("x".repeat(199), OutputNames.stem("x".repeat(199) + " ".repeat(5) + "y"))
    }

    @Test
    fun `single file results carry the tool suffix`() {
        assertEquals("Report_merged.pdf", OutputNames.merged("Report.pdf"))
        assertEquals("Report_encrypted.pdf", OutputNames.encrypted("Report.pdf"))
        assertEquals("Report_decrypted.pdf", OutputNames.decrypted("Report.pdf"))
        assertEquals("Report_text.txt", OutputNames.text("Report.pdf"))
        assertEquals("Report_split", OutputNames.splitFolder("Report.pdf"))
        assertEquals("Report_images", OutputNames.imagesFolder("Report.pdf"))
    }

    @Test
    fun `the toolbox results take the first image name and the edited suffix`() {
        assertEquals("scan.pdf", OutputNames.imagesPdf("scan.jpg"))
        assertEquals("photo.2026.pdf", OutputNames.imagesPdf("photo.2026.PNG"))
        assertEquals(".hidden.pdf", OutputNames.imagesPdf(".hidden"))
        assertEquals("document.pdf", OutputNames.imagesPdf("  "))
        assertEquals("report_edited.pdf", OutputNames.edited("report.pdf"))
    }

    @Test
    fun `numbers are padded to the widest with at least two digits`() {
        assertEquals("Report-03.pdf", OutputNames.splitPart("Report.pdf", 3, 9))
        assertEquals("Report-12.pdf", OutputNames.splitPart("Report.pdf", 12, 12))
        assertEquals("Report-003.pdf", OutputNames.splitPart("Report.pdf", 3, 120))
        assertEquals("Report-p01.png", OutputNames.imagePage("Report.pdf", 1, 1, "png"))
        assertEquals("Report-p0007.jpg", OutputNames.imagePage("Report.pdf", 7, 1000, "jpg"))
    }

    @Test
    fun `every result name of a long source stays within the 255 bytes of a file name`() {
        val source = "\u4e2d".repeat(200) + ".pdf"
        listOf(
            OutputNames.merged(source),
            OutputNames.encrypted(source),
            OutputNames.decrypted(source),
            OutputNames.text(source),
            OutputNames.splitFolder(source),
            OutputNames.splitPart(source, 1000, 1000),
            OutputNames.imagesFolder(source),
            OutputNames.imagePage(source, 100_000, 100_000, "jpg"),
            OutputNames.imagesPdf("$source.jpeg"),
            OutputNames.edited(source),
        ).forEach { name ->
            assertTrue(name, name.toByteArray(Charsets.UTF_8).size <= MAX_FILE_NAME_BYTES)
        }
    }

    private companion object {

        const val MAX_FILE_NAME_BYTES = 255

    }

}

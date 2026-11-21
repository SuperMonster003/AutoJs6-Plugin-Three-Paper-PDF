package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Names of toolbox results that land where an earlier result already is (roadmap P4.2; E4 feedback F3: back from the
 * result page, a second run of the same options writes next to the first result instead of over it).
 * zh-CN: 工具箱结果落在已有结果之处时的名称 (路线图 P4.2; E4 反馈 F3: 从结果页返回后, 用相同选项第二次运行写在第一个结果旁边, 不覆盖它).
 */
class LocalFolderOutputTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a free name is kept and a taken one gets the next number before the extension`() {
        val root = folder.root
        assertEquals("Text_watermarked.pdf", LocalFolderOutput.available(root, "Text_watermarked.pdf").name)
        folder.newFile("Text_watermarked.pdf")
        assertEquals("Text_watermarked (1).pdf", LocalFolderOutput.available(root, "Text_watermarked.pdf").name)
        folder.newFile("Text_watermarked (1).pdf")
        assertEquals("Text_watermarked (2).pdf", LocalFolderOutput.available(root, "Text_watermarked.pdf").name)
    }

    @Test
    fun `folders, names without an extension and names with several dots number the same way`() {
        val root = folder.root
        folder.newFolder("Report_split")
        assertEquals("Report_split (1)", LocalFolderOutput.available(root, "Report_split").name)
        folder.newFile("v1.2 notes_text.txt")
        assertEquals("v1.2 notes_text (1).txt", LocalFolderOutput.available(root, "v1.2 notes_text.txt").name)
        // A leading dot is not an extension. zh-CN: 开头的点不是扩展名.
        folder.newFile(".pdf")
        assertEquals(".pdf (1)", LocalFolderOutput.available(root, ".pdf").name)
    }

}

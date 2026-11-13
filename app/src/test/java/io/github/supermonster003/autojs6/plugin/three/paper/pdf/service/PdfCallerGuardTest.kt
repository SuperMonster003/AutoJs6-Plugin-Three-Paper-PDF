package io.github.supermonster003.autojs6.plugin.three.paper.pdf.service

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.HostKey
import org.autojs.plugin.pdf.api.PdfActions
import org.autojs.plugin.pdf.api.PdfErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Who may call the PDF entries and their handles (roadmap Q145 (b), D43, D44). The Binder checks themselves run on a
 * device (`PdfServicePackagedTest`).
 * zh-CN: 谁可以调用 PDF 入口及其句柄 (路线图 Q145 (b), D43, D44). Binder 上的检查本身在设备上运行 (`PdfServicePackagedTest`).
 */
class PdfCallerGuardTest {

    @Test
    fun `the signed entry never asks for the packaged permission`() {
        var asked = false
        assertNull(PdfCallerGuard.entryRefusal(packaged = false, callingUid = 10_100, ownUid = 10_200) { asked = true; false })
        assertFalse("the signed entry relies on its manifest permission", asked)
    }

    @Test
    fun `the packaged entry passes the plugin's own uid without asking`() {
        var asked = false
        assertNull(PdfCallerGuard.entryRefusal(packaged = true, callingUid = 10_200, ownUid = 10_200) { asked = true; false })
        assertFalse(asked)
    }

    @Test
    fun `the packaged entry passes other callers holding the runtime permission`() {
        assertNull(PdfCallerGuard.entryRefusal(packaged = true, callingUid = 10_100, ownUid = 10_200) { true })
    }

    @Test
    fun `the packaged entry refuses other callers without the runtime permission`() {
        val refusal = PdfCallerGuard.entryRefusal(packaged = true, callingUid = 10_100, ownUid = 10_200) { false }!!
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, refusal.code)
        assertEquals("the caller lacks ${PdfActions.PACKAGED_PERMISSION}", refusal.detail)
    }

    @Test
    fun `handles answer only the uid that created them`() {
        assertNull(PdfCallerGuard.ownerRefusal(ownerUid = 10_100, callingUid = 10_100))
        val refusal = PdfCallerGuard.ownerRefusal(ownerUid = 10_100, callingUid = 10_101)!!
        assertEquals(PdfErrorCodes.PERMISSION_DENIED, refusal.code)
        assertEquals("the handle belongs to another caller", refusal.detail)
    }

    @Test
    fun `packaged callers get their own temp directory names`() {
        assertEquals("10100-321", HostKey(10_100, 321).directoryName)
        assertEquals("k10100-321", HostKey(10_100, 321, packaged = true).directoryName)
        assertFalse(HostKey(10_100, 321) == HostKey(10_100, 321, packaged = true))
    }

}

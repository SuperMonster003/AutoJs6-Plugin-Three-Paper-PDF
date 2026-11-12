package io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ThreePaperPdfContract
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.engine.StopSignal
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.render.CountingOutputStream
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.util.PdfFailure
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.OutputStream
import java.util.Calendar

/**
 * Writes documents produced by page operations (roadmap P2.4): the metadata of the input is kept, `producer` names the
 * plugin and `modificationDate` is the time of writing. The written copy carries no encryption, so an encrypted input
 * must have been opened with its owner password (appendix D Q30 (a), the rule of `decrypt`); a user password alone
 * ends with `PERMISSION_DENIED`.
 * zh-CN: 写出页面操作产生的文档 (路线图 P2.4): 保留输入的元数据, `producer` 写插件名, `modificationDate` 为写出时间. 写出的副本
 * 不带加密, 因此加密的输入必须用所有者密码打开 (附录 D Q30 (a), 与 `decrypt` 的规则相同); 只有用户密码时报 `PERMISSION_DENIED`.
 */
object PdfWriting {

    /**
     * Fails with `PERMISSION_DENIED` when [document] is encrypted and was not opened with its owner password; [label]
     * names it in the message.
     * zh-CN: [document] 已加密且不是用所有者密码打开时报 `PERMISSION_DENIED`; [label] 用于在消息中指明它.
     */
    fun checkWritable(document: PDDocument, label: String = "the document") {
        if (document.isEncrypted && !document.currentAccessPermission.isOwnerPermission) {
            throw PdfFailure(PdfErrorCodes.PERMISSION_DENIED, "$label was opened with its user password; writing an unencrypted copy needs the owner password")
        }
    }

    /** Sets `producer` and `modificationDate`, keeping the rest. zh-CN: 设置 `producer` 与 `modificationDate`, 其余保持不变. */
    fun stamp(document: PDDocument) {
        val information = document.documentInformation
        information.producer = ThreePaperPdfContract.PRODUCER
        information.modificationDate = Calendar.getInstance()
        document.documentInformation = information
    }

    /**
     * Writes [document] to [output] without closing it and answers the bytes written; [stop] is checked on every write.
     * zh-CN: 把 [document] 写入 [output] (不关闭它) 并返回写出的字节数; 每次写入都检查 [stop].
     */
    fun write(document: PDDocument, output: OutputStream, stop: StopSignal): Long {
        checkWritable(document)
        if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
        return save(document, output, stop)
    }

    /**
     * Writes [document] encrypted with the policy set by `PDDocument.protect` (`encrypt`, roadmap P2.6), otherwise like
     * [write].
     * zh-CN: 按 `PDDocument.protect` 设置的策略加密写出 [document] (`encrypt`, 路线图 P2.6), 其余与 [write] 相同.
     */
    fun writeProtected(document: PDDocument, output: OutputStream, stop: StopSignal): Long {
        check(document.encryption?.securityHandler?.hasProtectionPolicy() == true) { "the document carries no protection policy" }
        return save(document, output, stop)
    }

    private fun save(document: PDDocument, output: OutputStream, stop: StopSignal): Long {
        stamp(document)
        // PDDocument.save closes the stream it is given; CountingOutputStream ignores close, so the output stays open.
        // zh-CN: PDDocument.save 会关闭传入的流; CountingOutputStream 忽略 close, 因此输出保持打开.
        val counting = CountingOutputStream(output, Long.MAX_VALUE, stop)
        try {
            document.save(counting)
            counting.flush()
        } catch (e: Throwable) {
            throw counting.failure ?: e
        }
        return counting.count
    }

}

package io.github.supermonster003.autojs6.plugin.three.paper.pdf.util

import io.github.supermonster003.autojs6.plugin.three.paper.pdf.compress.ImageCompression
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.edit.PageEditor
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.GeneratedDocuments
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.HtmlToPdf
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.generate.ImagesToPdf
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfOps
import org.autojs.plugin.pdf.api.PdfWarningCodes
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The warnings of the completions (appendix D Q264 (b), Q304): the codes and arguments of the sites the engine tests do
 * not reach, the expansion into the three arrays by contract version, and the size limit.
 * zh-CN: 完成包里的警告 (附录 D Q264 (b), Q304): 引擎测试覆盖不到的警告点的代码与参数, 按契约版本展开成三个数组, 以及体积上限.
 */
class PdfWarningsTest {

    @Test
    fun `the sites outside the engine tests carry their code and arguments`() {
        val skipped = ImageCompression.warningsOf(mapOf(ImageCompression.Skip.UNDECODABLE to 1, ImageCompression.Skip.TOO_LARGE to 2))
        assertWarning(skipped[0], PdfWarningCodes.IMAGES_UNDECODABLE)
        assertWarning(skipped[1], PdfWarningCodes.IMAGES_TOO_LARGE)
        assertEquals(2, skipped[1].args[PdfWarningCodes.ARG_COUNT])
        assertTrue(skipped[1].text, skipped[1].text.startsWith("2 images are too large"))

        assertWarning(PageEditor.outlineDamaged(), PdfWarningCodes.OUTLINE_DAMAGED)
        val notAdjusted = PageEditor.outlineNotAdjusted(IllegalStateException("a loop"))
        assertWarning(notAdjusted, PdfWarningCodes.OUTLINE_NOT_ADJUSTED)
        assertEquals("a loop", notAdjusted.args[PdfWarningCodes.ARG_REASON])
        assertEquals("the outline could not be adjusted and was dropped: a loop", notAdjusted.text)

        assertNull(HtmlToPdf.blockedWarning(0))
        val one = HtmlToPdf.blockedWarning(1)!!
        val three = HtmlToPdf.blockedWarning(3)!!
        assertWarning(three, PdfWarningCodes.REMOTE_RESOURCES_BLOCKED)
        assertEquals("1 remote resource was not loaded", one.text)
        assertEquals("3 remote resources were not loaded", three.text)
        assertEquals(3, three.args[PdfWarningCodes.ARG_COUNT])

        val downsampled = ImagesToPdf.downsampledWarning(2, 9000, 8000, 4500, 4000)
        assertWarning(downsampled, PdfWarningCodes.IMAGE_DOWNSAMPLED)
        // Typed as Any, so the literals stay Int as the factory stores them. zh-CN: 声明为 Any, 字面量与工厂存放的一样保持 Int.
        assertEquals(
            mapOf<String, Any>(
                PdfWarningCodes.ARG_IMAGE to 2, PdfWarningCodes.ARG_WIDTH to 9000, PdfWarningCodes.ARG_HEIGHT to 8000,
                PdfWarningCodes.ARG_DECODED_WIDTH to 4500, PdfWarningCodes.ARG_DECODED_HEIGHT to 4000,
                PdfWarningCodes.ARG_MAX_PIXELS to PdfContract.MAX_RENDER_PIXELS,
            ),
            downsampled.args,
        )
        assertEquals("image 2 of 9000 x 8000 was decoded at 4500 x 4000 (above ${PdfContract.MAX_RENDER_PIXELS} pixels)", downsampled.text)
    }

    @Test
    fun `the argument object is compact JSON in the order of the contract`() {
        assertEquals("{}", PdfWarning("t", PdfWarningCodes.XFA_REMOVED).argsJson())
        val fields = PdfWarning(
            "t", PdfWarningCodes.FIELDS_IGNORED,
            linkedMapOf(PdfWarningCodes.ARG_MORE to 0, PdfWarningCodes.ARG_NAMES to "'a'", PdfWarningCodes.ARG_COUNT to 1),
        )
        assertEquals("{\"count\":1,\"names\":\"'a'\",\"more\":0}", fields.argsJson())
        val tricky = "q\"b" + 0x5C.toChar() + "n" + '\n' + 0x01.toChar() + "中"
        val reason = PdfWarning("t", PdfWarningCodes.OBJECT_STREAMS_NOT_USED, mapOf(PdfWarningCodes.ARG_REASON to tricky))
        assertEquals(tricky, JSONObject(reason.argsJson()).getString(PdfWarningCodes.ARG_REASON))
        val large = PdfWarning("t", PdfWarningCodes.OUTPUT_NOT_SMALLER, mapOf(PdfWarningCodes.ARG_SIZE_BYTES to 5_000_000_000L, PdfWarningCodes.ARG_SOURCE_BYTES to 1L))
        assertEquals(5_000_000_000L, JSONObject(large.argsJson()).getLong(PdfWarningCodes.ARG_SIZE_BYTES))
        assertThrows(IllegalArgumentException::class.java) {
            PdfWarning("t", PdfWarningCodes.IMAGES_TOO_LARGE, mapOf(PdfWarningCodes.ARG_COUNT to 1.5)).argsJson()
        }
    }

    @Test
    fun `version 1 answers the texts only`() {
        val expanded = PdfWarnings.expand(answer(WARNINGS), PdfContract.CONTRACT_VERSION)
        assertArrayEquals(WARNINGS.map { it.text }.toTypedArray(), expanded[PdfContract.KEY_WARNINGS] as Array<*>)
        assertFalse(PdfContract.KEY_WARNING_CODES in expanded)
        assertFalse(PdfContract.KEY_WARNING_ARGS in expanded)
        assertEquals(listOf(PdfContract.KEY_PAGE_COUNT, PdfContract.KEY_WARNINGS, PdfContract.KEY_SIZE_BYTES), expanded.keys.toList())
    }

    @Test
    fun `version 2 adds the codes and arguments of the same length and order`() {
        val expanded = PdfWarnings.expand(answer(WARNINGS), PdfContract.CONTRACT_VERSION_2)
        val texts = expanded[PdfContract.KEY_WARNINGS] as Array<*>
        val codes = expanded[PdfContract.KEY_WARNING_CODES] as Array<*>
        val args = expanded[PdfContract.KEY_WARNING_ARGS] as Array<*>
        assertEquals(WARNINGS.size, texts.size)
        assertEquals(texts.size, codes.size)
        assertEquals(texts.size, args.size)
        assertArrayEquals(WARNINGS.map { it.code }.toTypedArray(), codes)
        assertArrayEquals(WARNINGS.map { it.argsJson() }.toTypedArray(), args)
        assertEquals(
            listOf(PdfContract.KEY_PAGE_COUNT, PdfContract.KEY_WARNINGS, PdfContract.KEY_WARNING_CODES, PdfContract.KEY_WARNING_ARGS, PdfContract.KEY_SIZE_BYTES),
            expanded.keys.toList(),
        )
    }

    @Test
    fun `no warnings, no arrays, and anything but warnings fails`() {
        listOf(PdfContract.CONTRACT_VERSION, PdfContract.CONTRACT_VERSION_2).forEach { version ->
            val expanded = PdfWarnings.expand(answer(emptyList()), version)
            assertEquals(listOf(PdfContract.KEY_PAGE_COUNT, PdfContract.KEY_SIZE_BYTES), expanded.keys.toList())
            val plain = mapOf<String, Any?>(PdfContract.KEY_PAGE_COUNT to 1)
            assertSame(plain, PdfWarnings.expand(plain, version))
            assertThrows(IllegalArgumentException::class.java) { PdfWarnings.expand(answer(listOf("text")), version) }
        }
    }

    @Test
    fun `the generated documents keep one entry per text in all three arrays`() {
        val warnings = ArrayList<PdfWarning>()
        warnings += ImagesToPdf.downsampledWarning(1, 9000, 8000, 4500, 4000)
        warnings += ImagesToPdf.downsampledWarning(1, 9000, 8000, 4500, 4000)
        warnings += ImagesToPdf.downsampledWarning(2, 9000, 8000, 4500, 4000)
        warnings += PdfWarning("no image to fit, A4 was used for ${PdfOps.FROM_IMAGES}", PdfWarningCodes.FIT_PAGE_SIZE_USED_A4, mapOf(PdfWarningCodes.ARG_OP to PdfOps.FROM_IMAGES))
        warnings += ImagesToPdf.downsampledWarning(1, 9000, 8000, 4500, 4000)
        val distinct = GeneratedDocuments.distinct(warnings)
        assertEquals(3, distinct.size)
        val expanded = PdfWarnings.expand(answer(distinct), PdfContract.CONTRACT_VERSION_2)
        val texts = expanded[PdfContract.KEY_WARNINGS] as Array<*>
        val codes = expanded[PdfContract.KEY_WARNING_CODES] as Array<*>
        val args = expanded[PdfContract.KEY_WARNING_ARGS] as Array<*>
        assertEquals(3, texts.size)
        assertEquals(3, codes.size)
        assertEquals(3, args.size)
        assertEquals(PdfWarningCodes.FIT_PAGE_SIZE_USED_A4, codes[2])
        assertEquals(2, JSONObject(args[1] as String).getInt(PdfWarningCodes.ARG_IMAGE))
    }

    @Test
    fun `an oversized completion drops the arguments first, then the codes`() {
        val expanded = PdfWarnings.expand(answer(WARNINGS), PdfContract.CONTRACT_VERSION_2)
        val weights = mapOf(PdfContract.KEY_PAGE_COUNT to 10, PdfContract.KEY_SIZE_BYTES to 10, PdfContract.KEY_WARNINGS to 100, PdfContract.KEY_WARNING_CODES to 50, PdfContract.KEY_WARNING_ARGS to 200)
        var measured = 0
        val size: (Map<String, Any?>) -> Int = { map -> measured++; map.keys.sumOf { weights.getValue(it) } }

        assertSame(expanded, PdfWarnings.fit(expanded, 370, size))
        assertEquals(1, measured)

        val withoutArgs = PdfWarnings.fit(expanded, 369, size)
        assertFalse(PdfContract.KEY_WARNING_ARGS in withoutArgs)
        assertTrue(PdfContract.KEY_WARNING_CODES in withoutArgs)
        assertSame(expanded[PdfContract.KEY_WARNINGS], withoutArgs[PdfContract.KEY_WARNINGS])

        val textsOnly = PdfWarnings.fit(expanded, 169, size)
        assertFalse(PdfContract.KEY_WARNING_ARGS in textsOnly)
        assertFalse(PdfContract.KEY_WARNING_CODES in textsOnly)
        assertEquals(listOf(PdfContract.KEY_PAGE_COUNT, PdfContract.KEY_WARNINGS, PdfContract.KEY_SIZE_BYTES), textsOnly.keys.toList())

        // Answers without codes (version 1) are not measured. zh-CN: 不带代码的应答 (版本 1) 不计量.
        val version1 = PdfWarnings.expand(answer(WARNINGS), PdfContract.CONTRACT_VERSION)
        measured = 0
        assertSame(version1, PdfWarnings.fit(version1, 0, size))
        assertEquals(0, measured)
        assertEquals(16384, PdfContract.MAX_EVENT_BYTES)
    }

    /**
     * What a reader of the contract (`PdfWarningCodes.align` of the host) makes of the completions, the compatibility
     * checkpoints of Q264 (b): version 2 gives the codes item by item, all known (c); version 1, which is also all a 1.0.x
     * plugin sends, gives null for every warning and the same length (a, b); a well-formed code the contract does not list
     * still reaches the script (d).
     * zh-CN: 契约的读方 (宿主的 `PdfWarningCodes.align`) 看到的完成包, 即 Q264 (b) 的兼容检查点: 版本 2 逐项给出代码且都认识 (c); 版本 1 (也是 1.0.x 插件
     * 发出的全部内容) 每条都是 null, 长度不变 (a, b); 契约没列出但格式正确的代码照样交给脚本 (d).
     */
    @Test
    fun `a reader of the contract sees the codes item by item, or null for each warning`() {
        val version2 = PdfWarnings.expand(answer(WARNINGS), PdfContract.CONTRACT_VERSION_2)
        @Suppress("UNCHECKED_CAST")
        val codes = version2[PdfContract.KEY_WARNING_CODES] as Array<String?>
        val aligned = PdfWarningCodes.align(codes, (version2[PdfContract.KEY_WARNINGS] as Array<*>).size)
        assertEquals(WARNINGS.map { it.code }, aligned)
        assertTrue(aligned.all { PdfWarningCodes.isKnown(it) })

        val version1 = PdfWarnings.expand(answer(WARNINGS), PdfContract.CONTRACT_VERSION)
        @Suppress("UNCHECKED_CAST")
        val absent = version1[PdfContract.KEY_WARNING_CODES] as Array<String?>?
        assertEquals(List(WARNINGS.size) { null }, PdfWarningCodes.align(absent, (version1[PdfContract.KEY_WARNINGS] as Array<*>).size))

        val newer = arrayOf<String?>(PdfWarningCodes.XFA_REMOVED, "A_LATER_CODE")
        assertEquals(listOf(PdfWarningCodes.XFA_REMOVED, "A_LATER_CODE"), PdfWarningCodes.align(newer, 2))
        assertFalse(PdfWarningCodes.isKnown("A_LATER_CODE"))
    }

    private fun answer(warnings: List<Any>): Map<String, Any?> = linkedMapOf(
        PdfContract.KEY_PAGE_COUNT to 2,
        PdfContract.KEY_WARNINGS to warnings,
        PdfContract.KEY_SIZE_BYTES to 100L,
    )

    private companion object {
        val WARNINGS = listOf(
            PdfWarning("the static XFA form data was removed", PdfWarningCodes.XFA_REMOVED),
            PdfWarning("1 remote resource was not loaded", PdfWarningCodes.REMOTE_RESOURCES_BLOCKED, mapOf(PdfWarningCodes.ARG_COUNT to 1)),
            PdfWarning("object streams were not used because x", PdfWarningCodes.OBJECT_STREAMS_NOT_USED, mapOf(PdfWarningCodes.ARG_REASON to "x")),
        )
    }

}

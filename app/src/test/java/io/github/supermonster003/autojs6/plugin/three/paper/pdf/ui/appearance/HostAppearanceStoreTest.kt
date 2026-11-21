package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The stored copy of the host appearance (E4 A19): a screen created before the first read of a process starts from it
 * instead of the system language and night mode; a missing or malformed part means no copy.
 * zh-CN: 宿主外观的保存副本 (E4 A19): 进程首次读取之前创建的界面从它开始, 而不是系统语言与夜间模式; 缺少或格式不对的部分视为没有副本.
 */
class HostAppearanceStoreTest {

    private val complete = mapOf("language" to "zh-Hans-CN", "dark" to false, "primary" to 0x00FFDEAD, "accent" to -0x10000)

    @Test
    fun `a complete copy gives the host appearance with opaque colors`() {
        assertEquals(HostAppearance("zh-Hans-CN", false, -0x2153, -0x10000), HostAppearanceReader.fromStored(complete))
        assertEquals(HostAppearance("en", true, -0x2153, -0x10000), HostAppearanceReader.fromStored(complete + mapOf("language" to "en", "dark" to true)))
    }

    @Test
    fun `a missing or malformed part means no copy`() {
        assertNull("empty", HostAppearanceReader.fromStored(emptyMap<String, Any>()))
        listOf("language", "dark", "primary", "accent").forEach { key -> assertNull(key, HostAppearanceReader.fromStored(complete - key)) }
        assertNull("dark as a string", HostAppearanceReader.fromStored(complete + ("dark" to "false")))
        assertNull("primary as a long", HostAppearanceReader.fromStored(complete + ("primary" to 1L)))
        listOf("", "x", "en_US", "en--US", "zh-Hans-", "a".repeat(81), "123").forEach { tag ->
            assertNull(tag, HostAppearanceReader.fromStored(complete + ("language" to tag)))
        }
    }
}

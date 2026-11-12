package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolution of the persisted launcher alias state (roadmap D24, P0.2): an explicit choice wins, a mixed or empty state
 * falls back to the automatic default that the Manifest enables.
 * zh-CN: 启动器 alias 持久状态的解析 (路线图 D24, P0.2): 显式选择优先, 混合或空状态回落到 Manifest 默认启用的自动模式.
 */
class LauncherIconStatePolicyTest {

    @Test
    fun `an explicit choice wins and an empty state means the automatic default`() {
        assertEquals(LauncherIconMode.AUTO, LauncherIconStatePolicy.resolve(states(DEFAULT, DEFAULT, DEFAULT, DEFAULT)))
        assertEquals(LauncherIconMode.DARK, LauncherIconStatePolicy.resolve(states(DISABLED, ENABLED, DISABLED, DISABLED)))
        assertEquals("a mixed state keeps the explicit Auto", LauncherIconMode.AUTO, LauncherIconStatePolicy.resolve(states(ENABLED, DISABLED, ENABLED, DISABLED)))
        assertEquals(LauncherIconMode.TRANSPARENT, LauncherIconStatePolicy.resolve(states(DISABLED, DISABLED, DEFAULT, ENABLED)))
        assertEquals("every alias disabled", LauncherIconMode.AUTO, LauncherIconStatePolicy.resolve(states(DISABLED, DISABLED, DISABLED, DISABLED)))
    }

    @Test
    fun `only the automatic alias counts as enabled in its default state`() {
        assertTrue(LauncherIconStatePolicy.enabled(LauncherIconMode.AUTO, DEFAULT))
        listOf(LauncherIconMode.LIGHT, LauncherIconMode.DARK, LauncherIconMode.TRANSPARENT).forEach {
            assertFalse(it.name, LauncherIconStatePolicy.enabled(it, DEFAULT))
            assertTrue(it.name, LauncherIconStatePolicy.enabled(it, ENABLED))
        }
        assertFalse(LauncherIconStatePolicy.enabled(LauncherIconMode.AUTO, DISABLED))
    }

    private fun states(light: Int, dark: Int, auto: Int, transparent: Int) = mapOf(
        LauncherIconMode.LIGHT to light, LauncherIconMode.DARK to dark,
        LauncherIconMode.AUTO to auto, LauncherIconMode.TRANSPARENT to transparent,
    )

    private companion object {
        const val DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        const val ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        const val DISABLED = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

}

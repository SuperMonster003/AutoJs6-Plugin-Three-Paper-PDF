package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full-screen settings of the viewer (roadmap P6.15, E4 feedback F2, appendix D Q343 (a)): both start on, each is
 * kept on its own, and turning one off keeps that element as it was before full-screen reading.
 * zh-CN: 查看器的全屏设置 (路线图 P6.15, E4 反馈 F2, 附录 D Q343 (a)): 两项默认开启, 各自独立保存, 关闭其中一项时该元素保持全屏阅读之前的样子.
 */
class ViewerPreferencesTest {

    @Test
    fun `both settings start on`() {
        val preferences = MemoryPreferences()
        assertEquals(ViewerPreferences.Fullscreen(hidePageIndicator = true, hideSystemBars = true), ViewerPreferences.fullscreen(preferences))
        assertTrue(preferences.values.isEmpty())
    }

    @Test
    fun `each setting is kept on its own and next to the night mode`() {
        val preferences = MemoryPreferences()
        preferences.values[ViewerViewModel.KEY_NIGHT] = true
        assertTrue(ViewerPreferences.setHidePageIndicator(preferences, false))
        assertEquals(ViewerPreferences.Fullscreen(hidePageIndicator = false, hideSystemBars = true), ViewerPreferences.fullscreen(preferences))
        assertTrue(ViewerPreferences.setHideSystemBars(preferences, false))
        assertTrue(ViewerPreferences.setHidePageIndicator(preferences, true))
        assertEquals(ViewerPreferences.Fullscreen(hidePageIndicator = true, hideSystemBars = false), ViewerPreferences.fullscreen(preferences))
        assertEquals(
            mapOf(ViewerViewModel.KEY_NIGHT to true, "fullscreen_hide_page_indicator" to true, "fullscreen_hide_system_bars" to false),
            preferences.values,
        )
        assertEquals("viewer", ViewerPreferences.FILE)
    }

    @Test
    fun `a failed write is reported`() {
        val preferences = MemoryPreferences(commits = false)
        assertFalse(ViewerPreferences.setHideSystemBars(preferences, false))
    }

    @Test
    fun `full screen hides only what is switched on`() {
        val both = ViewerPreferences.Fullscreen(hidePageIndicator = true, hideSystemBars = true)
        assertFalse(both.pageIndicatorVisible(fullscreen = true))
        assertTrue(both.systemBarsHidden(fullscreen = true))
        // Out of full screen everything shows. zh-CN: 不在全屏时全部显示.
        assertTrue(both.pageIndicatorVisible(fullscreen = false))
        assertFalse(both.systemBarsHidden(fullscreen = false))
        val none = ViewerPreferences.Fullscreen(hidePageIndicator = false, hideSystemBars = false)
        assertTrue(none.pageIndicatorVisible(fullscreen = true))
        assertFalse(none.systemBarsHidden(fullscreen = true))
        val barsOnly = ViewerPreferences.Fullscreen(hidePageIndicator = false, hideSystemBars = true)
        assertTrue(barsOnly.pageIndicatorVisible(fullscreen = true))
        assertTrue(barsOnly.systemBarsHidden(fullscreen = true))
    }

    /** Booleans in memory; [commits] false makes every commit fail. zh-CN: 内存中的布尔值; [commits] 为 false 时每次提交都失败. */
    private class MemoryPreferences(private val commits: Boolean = true) : SharedPreferences {

        val values = HashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(values)
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = values }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { values.clear() }
            override fun commit(): Boolean {
                if (!commits) return false
                values.putAll(pending)
                return true
            }
            override fun apply() {
                commit()
            }
        }

    }

}

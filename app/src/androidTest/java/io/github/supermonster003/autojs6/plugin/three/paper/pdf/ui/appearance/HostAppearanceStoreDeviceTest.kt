package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The host appearance the screens start from does not expire and is stored for the next process (E4 A19: a recreate
 * later than 30 seconds after the last read showed the system appearance first, a black flash where the system is dark
 * and AutoJs6 is light). The state before the test is put back afterwards.
 * zh-CN: 界面起始所用的宿主外观不会过期, 并保存供下一个进程使用 (E4 A19: 距上次读取超过 30 秒的重建先显示系统外观, 系统为深色而 AutoJs6 为
 * 浅色时表现为黑色闪烁). 测试结束后恢复测试前的状态.
 */
@RunWith(AndroidJUnit4::class)
class HostAppearanceStoreDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var before: HostAppearance? = null

    @Before
    fun remember() {
        before = HostAppearanceReader.cached(context)
    }

    @After
    fun restore() {
        HostAppearanceReader.accept(context, before)
    }

    @Test
    fun theAcceptedAppearanceStaysAndIsStoredAndAMissingHostClearsBoth() {
        val host = HostAppearance("ar-EG", true, -0x2153, -0x10000)
        HostAppearanceReader.accept(context, host)
        assertEquals(host, HostAppearanceReader.cached(context))
        assertEquals(host, HostAppearanceReader.fromStored(stored()))

        HostAppearanceReader.accept(context, null)
        assertNull(HostAppearanceReader.cached(context))
        assertTrue(stored().toString(), stored().isEmpty())
    }

    private fun stored(): Map<String, *> {
        // apply() writes the memory copy at once, so a fresh read of the same file sees it.
        // zh-CN: apply() 立即写入内存副本, 再次读取同一文件即可看到.
        return context.getSharedPreferences(HostAppearanceReader.FILE, Context.MODE_PRIVATE).all
    }
}

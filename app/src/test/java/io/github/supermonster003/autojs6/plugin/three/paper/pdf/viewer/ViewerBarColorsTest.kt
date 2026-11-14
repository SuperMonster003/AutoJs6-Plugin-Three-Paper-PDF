package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.ColorPolicy
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.HostAppearance
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.appearance.PaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The viewer's translucent bars keep readable content on both themes (2026-11-14 device re-check: on the dark theme the
 * title and icons fell back to black on the dark bar).
 * zh-CN: 查看器的半透明工具栏在两种主题下内容都可读 (2026-11-14 真机复验: 深色主题下标题与图标退回黑色, 叠在深色栏上).
 */
class ViewerBarColorsTest {

    @Test
    fun `the bar content color is the scheme's on-surface color because a translucent surface matches no role`() {
        for (dark in listOf(false, true)) {
            val scheme = palette(dark).colorScheme()
            val bars = ViewerBarColors.of(scheme)
            assertEquals(Color.Unspecified, scheme.contentColorFor(bars.container))
            assertEquals(scheme.onSurface, bars.content)
            assertTrue(bars.container.alpha in 0.9f..1f)
        }
    }

    @Test
    fun `bar content reaches 4_5 to 1 over every backdrop the bar can cover`() {
        for (dark in listOf(false, true)) {
            val palette = palette(dark)
            val bars = ViewerBarColors.of(palette.colorScheme())
            // White and night pages, the black scrim and the canvas background. zh-CN: 白色页面, 夜间页面, 黑色遮罩与画布背景.
            val backdrops = listOf(-1, 0xff101010.toInt(), -0x1000000, palette.surfaceVariant)
            for (backdrop in backdrops) {
                val bar = ColorPolicy.blend(backdrop, bars.container.copy(alpha = 1f).toArgb(), bars.container.alpha.toDouble())
                val ratio = ColorPolicy.contrastRatio(bars.content.toArgb(), bar)
                assertTrue("dark=$dark backdrop=${Integer.toHexString(backdrop)} ratio=$ratio", ratio >= 4.5)
            }
        }
    }

    private fun palette(dark: Boolean) = PaperPalette.resolve(HostAppearance("", dark, 0xff3f51b5.toInt(), 0xffff4081.toInt()))
}

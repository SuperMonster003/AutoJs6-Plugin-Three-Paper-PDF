package io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theme of the skeleton screens: dynamic colors on Android 12+, the baseline schemes below. Roadmap P4.1
 * replaces it with the fixed HCT accent of the standalone settings specification.
 * zh-CN: 骨架界面的 Material 3 主题: Android 12 及以上用动态取色, 以下用基线配色. 路线图 P4.1 换成独立设置规范的固定 HCT 主题色.
 */
@Composable
internal fun PaperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

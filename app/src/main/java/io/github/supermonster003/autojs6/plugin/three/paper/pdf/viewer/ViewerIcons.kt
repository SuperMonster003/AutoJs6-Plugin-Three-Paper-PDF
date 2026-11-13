package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The viewer's toolbar icons, drawn here as simple strokes on a 24 x 24 grid (the plugin has no icon library).
 * zh-CN: 查看器工具栏图标, 在 24 x 24 网格上用简单线条绘制 (插件不带图标库).
 */
internal object ViewerIcons {

    val Back: ImageVector by lazy { icon("back", "M19,12H5 M11,6L5,12L11,18", autoMirror = true) }
    val Search: ImageVector by lazy { icon("search", "M16,10.5A5.5,5.5 0 1,1 5,10.5A5.5,5.5 0 1,1 16,10.5Z M14.5,14.5L20,20") }
    val Outline: ImageVector by lazy { icon("outline", "M9,7H20 M9,12H20 M9,17H20 M4.5,7H4.6 M4.5,12H4.6 M4.5,17H4.6", autoMirror = true) }
    val More: ImageVector by lazy { icon("more", "M12,5.5H12.1 M12,12H12.1 M12,18.5H12.1", width = 3f) }
    val Close: ImageVector by lazy { icon("close", "M6,6L18,18 M18,6L6,18") }
    val Up: ImageVector by lazy { icon("up", "M6,15L12,9L18,15") }
    val Down: ImageVector by lazy { icon("down", "M6,9L12,15L18,9") }

    private fun icon(name: String, path: String, autoMirror: Boolean = false, width: Float = 2f): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

}

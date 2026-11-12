package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.PaperTheme

/**
 * The viewer entry (roadmap 5.5): `org.autojs.plugin.PDF_VIEWER_OPEN` from the host with a session token, and
 * `ACTION_VIEW` + `application/pdf` from the launcher and other apps. The skeleton only tells that the viewer comes in a
 * later build; roadmap P3.1 lands the Compose viewer and P3.2 the session claim.
 * zh-CN: 查看器入口 (路线图 5.5): 宿主带会话令牌发来的 `org.autojs.plugin.PDF_VIEWER_OPEN`, 以及启动器与其他应用发来的
 * `ACTION_VIEW` + `application/pdf`. 骨架只提示查看器将在后续构建中提供; 路线图 P3.1 落地 Compose 查看器, P3.2 落地会话认领.
 */
class ThreePaperPdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PaperTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Content),
                        )
                        Text(text = stringResource(R.string.viewer_skeleton_note), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = ::finish) { Text(text = stringResource(R.string.viewer_close)) }
                    }
                }
            }
        }
    }

}

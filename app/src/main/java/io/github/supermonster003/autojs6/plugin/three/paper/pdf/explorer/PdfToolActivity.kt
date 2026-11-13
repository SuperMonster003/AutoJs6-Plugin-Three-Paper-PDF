package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.PaperTheme

/**
 * The file manager tools other than opening (roadmap D36, P3.3, appendix D Q70): merge, split, to images, extract
 * text, encrypt and remove the password. Only the host may start it (`org.autojs.permission.PLUGIN`); it asks for the
 * options and passwords, shows the progress and writes the result next to the selected files through the host
 * session. A new request while a tool is open is ignored: the screen finishes the request it started with.
 * zh-CN: 打开以外的文件管理器工具 (路线图 D36, P3.3, 附录 D Q70): 合并, 拆分, 转图片, 提取文本, 加密与移除密码. 只有宿主可以启动它
 * (`org.autojs.permission.PLUGIN`); 它询问选项与密码, 显示进度, 并经宿主会话把结果写到选中文件旁边. 工具界面打开期间的新请求被忽略:
 * 界面完成它开始时的请求.
 */
class PdfToolActivity : ComponentActivity() {

    private val model: PdfToolViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model.start(ToolRequest.of(intent))
        setContent {
            PaperTheme {
                PdfToolScreen(model = model, onClose = ::finish)
            }
        }
    }

}

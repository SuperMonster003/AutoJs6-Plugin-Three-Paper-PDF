package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.StandaloneActivity

/**
 * The file manager tools other than opening (roadmap D36, P3.3, appendix D Q70): merge, split, to images, extract
 * text, encrypt and remove the password. Only the host may start it (`org.autojs.permission.PLUGIN`); it asks for the
 * options and passwords, shows the progress and writes the result next to the selected files through the host
 * session. A new request while a tool is open is ignored: the screen finishes the request it started with. The screen
 * follows the plugin's appearance settings like the other standalone screens (roadmap P4.1).
 * zh-CN: 打开以外的文件管理器工具 (路线图 D36, P3.3, 附录 D Q70): 合并, 拆分, 转图片, 提取文本, 加密与移除密码. 只有宿主可以启动它
 * (`org.autojs.permission.PLUGIN`); 它询问选项与密码, 显示进度, 并经宿主会话把结果写到选中文件旁边. 工具界面打开期间的新请求被忽略:
 * 界面完成它开始时的请求. 与其他独立界面一样遵循插件的外观设置 (路线图 P4.1).
 */
class PdfToolActivity : StandaloneActivity() {

    private val model: PdfToolViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.start(ToolRequest.of(intent))
    }

    @Composable
    override fun Content() {
        PdfToolScreen(model = model, onClose = ::finish)
    }

    // A running tool or a form being filled is not interrupted by an appearance change. zh-CN: 运行中的工具或正在填写的表单不因外观变化而被打断.
    override fun holdsAppearance(): Boolean = model.phase != PdfToolViewModel.Phase.Inspecting

}

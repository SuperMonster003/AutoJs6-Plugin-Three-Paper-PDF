package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.AutoJs6Scripts
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.GeneratedScript
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptGenerator
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptRequest
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptSaver
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.codegen.ScriptStyle
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.StorageAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.StorageAccessState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The script of a finished run on the result page (roadmap P6.13, D51): the same task written for the host's Rhino,
 * Node.js or Bun, shown, copied, saved under [ScriptSaver.FOLDER] of the output folder in the settings (a Node.js script
 * as a project folder), or opened in the AutoJs6 editor through `CREATE_SCRIPT` ([AutoJs6Scripts]; the button is hidden
 * when the host cannot take scripts or this device cannot run the style). Passwords never reach the script; notes say
 * when passwords or paths have to be filled in. Test tags: `tool-script`, `tool-script-style-<id>`, `tool-script-code`,
 * `tool-script-copy`, `tool-script-save`, `tool-script-open`, `tool-script-passwords`, `tool-script-guessed`,
 * `tool-script-bun-note`.
 * zh-CN: 结果页上已完成执行的脚本 (路线图 P6.13, D51): 同一任务写成宿主 Rhino, Node.js 或 Bun 脚本, 可以查看, 复制, 保存到设置中输出
 * 文件夹下的 [ScriptSaver.FOLDER] (Node.js 脚本保存为项目文件夹), 或经 `CREATE_SCRIPT` 在 AutoJs6 编辑器中打开 ([AutoJs6Scripts];
 * 宿主不能接收脚本或本设备不能运行该写法时隐藏按钮). 密码绝不进入脚本; 需要补填密码或核对路径时有提示. 测试标签见上.
 */
@Composable
internal fun ScriptBlock(request: ScriptRequest, snackbar: SnackbarHostState, storage: StorageAccessState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var styleId by rememberSaveable { mutableStateOf(ScriptStyle.RHINO.id) }
    val style = ScriptStyle.of(styleId) ?: ScriptStyle.RHINO
    val script = remember(request, style) { ScriptGenerator.generate(request, style) }
    val passwords = request.kind == ToolKind.ENCRYPT || request.inputs.any { it.password }
    val guessed = remember(request) { (request.inputs.map { it.file } + listOfNotNull(request.output, request.image)).any { it.guessed } }
    Column(modifier = Modifier.fillMaxWidth().testTag("tool-script"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tool_script_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Note(stringResource(R.string.tool_script_hint))
        Chips {
            ScriptStyle.entries.forEach { entry ->
                FilterChip(
                    selected = entry == style,
                    onClick = { styleId = entry.id },
                    label = { Text(labelOf(entry)) },
                    modifier = Modifier.testTag("tool-script-style-${entry.id}"),
                )
            }
        }
        if (style == ScriptStyle.BUN && !AutoJs6Scripts.supports(style)) Note(stringResource(R.string.tool_script_bun_note), "tool-script-bun-note")
        if (passwords) Note(stringResource(R.string.tool_script_passwords), "tool-script-passwords")
        if (guessed) Note(stringResource(R.string.tool_script_guessed), "tool-script-guessed")
        // Code reads left to right in every language. zh-CN: 代码在任何语言下都从左到右排列.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = script.code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    softWrap = false,
                    modifier = Modifier
                        .heightIn(max = CODE_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                        .testTag("tool-script-code"),
                )
            }
        }
        Chips {
            ScriptButton(R.drawable.ic_content_copy_24, stringResource(R.string.tool_script_copy), "tool-script-copy") {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(script.fileName, script.code))
                // Android 13 and later confirm a copy themselves. zh-CN: Android 13 及以上由系统自己提示已复制.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbar.showInstead(resources.getString(R.string.tool_script_copied)) }
            }
            ScriptButton(R.drawable.ic_save_24, stringResource(R.string.tool_script_save), "tool-script-save") {
                scope.launch {
                    val folder = ToolboxPreferences.outputFolder(context)
                    val saved = withContext(Dispatchers.IO) {
                        runCatching { ScriptSaver.save(folder, script) }.onFailure { Log.w(TAG, "cannot save the script", it) }.getOrNull()
                    }
                    when {
                        saved != null -> snackbar.showInstead(resources.getString(R.string.tool_script_saved, saved.path))
                        !StorageAccess.granted(context) -> {
                            val answer = snackbar.showInstead(resources.getString(R.string.tool_script_save_access), resources.getString(R.string.home_storage_grant))
                            if (answer == SnackbarResult.ActionPerformed) storage.request()
                        }
                        else -> snackbar.showInstead(resources.getString(R.string.tool_script_save_failed, File(folder, ScriptSaver.FOLDER).path))
                    }
                }
            }
            if (AutoJs6Scripts.supports(style)) OpenInAutoJs6Button(script, snackbar)
        }
    }
}

/**
 * "Open in AutoJs6" (roadmap P6.13, D51; the pattern of 3-Slate Studio): shown only when the host is installed and new
 * enough ([AutoJs6Scripts.isAvailable]); large code goes through the cache and a content URI, deleted when the host
 * answers. The host shows its own message when it refuses a script; [snackbar] reports what fails before that.
 * zh-CN: "在 AutoJs6 中打开" (路线图 P6.13, D51; 沿用 3-Slate Studio 的做法): 只在宿主已安装且版本足够时显示
 * ([AutoJs6Scripts.isAvailable]); 大代码经缓存与 content URI 发送, 宿主应答后删除. 宿主拒绝脚本时会自己提示; [snackbar] 报告此前的失败.
 */
@Composable
private fun OpenInAutoJs6Button(script: GeneratedScript, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val available = remember { AutoJs6Scripts.isAvailable(context) }
    if (!available) return
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // The host answers once the editor has the code (or with the reason it refused). zh-CN: 编辑器拿到代码后宿主应答 (或给出拒绝原因).
        val error = result.data?.getStringExtra(AutoJs6Scripts.EXTRA_ERROR)
        Log.i(TAG, "CREATE_SCRIPT answered: " + (error ?: if (result.resultCode == Activity.RESULT_OK) "accepted" else "cancelled"))
        AutoJs6Scripts.cleanUp(context, pending)
        pending = null
    }
    val failed = stringResource(R.string.tool_script_open_failed)
    val tooLarge = stringResource(R.string.tool_script_open_too_large)
    val source = stringResource(R.string.app_name)
    ScriptButton(R.drawable.ic_open_in_new_24, stringResource(R.string.tool_script_open), "tool-script-open") {
        // Prepared on an IO thread, launched on the main thread. zh-CN: 在 IO 线程准备, 在主线程启动.
        scope.launch(Dispatchers.Main.immediate) {
            val request = withContext(Dispatchers.IO) { runCatching { AutoJs6Scripts.intentOf(context, script, source) } }
                .getOrElse {
                    Log.w(TAG, "cannot prepare the script", it)
                    snackbar.showInstead(failed)
                    return@launch
                }
            when (request) {
                is AutoJs6Scripts.Request.TooLarge -> {
                    Log.w(TAG, "CREATE_SCRIPT not sent: ${request.bytes} bytes")
                    snackbar.showInstead(tooLarge)
                }
                is AutoJs6Scripts.Request.Ready -> try {
                    pending = request.file
                    launcher.launch(request.intent)
                    Log.i(TAG, "CREATE_SCRIPT sent: " + if (request.file == null) "inline" else "content URI")
                } catch (e: RuntimeException) {
                    // ActivityNotFoundException or SecurityException (no PLUGIN permission) above all.
                    // zh-CN: 主要是找不到入口 (ActivityNotFoundException) 或缺少 PLUGIN 权限 (SecurityException).
                    Log.w(TAG, "CREATE_SCRIPT not sent", e)
                    AutoJs6Scripts.cleanUp(context, request.file)
                    pending = null
                    snackbar.showInstead(failed)
                }
            }
        }
    }
}

@Composable
private fun ScriptButton(@DrawableRes icon: Int, label: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun Note(text: String, tag: String? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    )
}

/** The names of the runtimes, the same in every language. zh-CN: 运行时的名称, 在任何语言下都相同. */
private fun labelOf(style: ScriptStyle): String = when (style) {
    ScriptStyle.RHINO -> "Rhino"
    ScriptStyle.NODE -> "Node.js"
    ScriptStyle.BUN -> "Bun"
}

/** The tallest the code gets before it scrolls on its own. zh-CN: 代码区在自身滚动之前的最大高度. */
private const val CODE_MAX_HEIGHT = 320

private const val TAG = "PaperScripts"

/**
 * Shows [message] in place of the one on screen, so a second tap reports at once instead of after the first message.
 * zh-CN: 替换屏幕上的消息显示 [message], 再次点击时立即报告, 不排在上一条之后.
 */
private suspend fun SnackbarHostState.showInstead(message: String, actionLabel: String? = null): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel)
}

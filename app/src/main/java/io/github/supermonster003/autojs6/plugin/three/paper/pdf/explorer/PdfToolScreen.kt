package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel.Phase
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ViewerIcons
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.text.NumberFormat

/**
 * The tool screen (roadmap P3.3, appendix D Q70): the selected files, the options, then progress with cancel and the
 * outcome. Test tags: `tool-start`, `tool-cancel`, `tool-done`, `tool-error`, `tool-password-<n>`, `tool-unlock-<n>`.
 * zh-CN: 工具界面 (路线图 P3.3, 附录 D Q70): 选中的文件, 选项, 然后是可取消的进度与结果. 测试标签: `tool-start`, `tool-cancel`,
 * `tool-done`, `tool-error`, `tool-password-<n>`, `tool-unlock-<n>`.
 */
@Composable
internal fun PdfToolScreen(model: PdfToolViewModel, onClose: () -> Unit) {
    val phase = model.phase
    BackHandler(enabled = phase == Phase.Running) { model.cancel() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (phase == Phase.Running) model.cancel() else onClose() }) {
                    Icon(ViewerIcons.Close, contentDescription = stringResource(R.string.viewer_close))
                }
                Text(
                    text = stringResource(titleOf(model.tool)),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (phase) {
                Phase.Inspecting -> Centered {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.tool_inspecting), style = MaterialTheme.typography.bodyLarge)
                }
                Phase.Ready -> Options(model)
                Phase.Running -> Centered {
                    LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth())
                    Text(NumberFormat.getPercentInstance().format(model.progress.toDouble()), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tool_running), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = model::cancel, modifier = Modifier.testTag("tool-cancel")) { Text(stringResource(R.string.viewer_cancel)) }
                }
                is Phase.Done -> Centered {
                    Text(stringResource(R.string.tool_done_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.tool_done_saved, phase.result.displayPath),
                        modifier = Modifier.testTag("tool-done"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (phase.result.files > 1) Text(stringResource(R.string.tool_done_files, phase.result.files), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onClose) { Text(stringResource(R.string.tool_done)) }
                }
                is Phase.Failed -> Centered {
                    val failure = phase.failure
                    Text(stringResource(R.string.tool_failed_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = messageOf(failure.code)?.let { stringResource(it) } ?: stringResource(R.string.tool_error_generic, failure.code),
                        modifier = Modifier.testTag("tool-error"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    failure.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (model.inputs.isNotEmpty()) {
                            OutlinedButton(onClick = model::retry, modifier = Modifier.testTag("tool-retry")) { Text(stringResource(R.string.tool_retry)) }
                        }
                        Button(onClick = onClose) { Text(stringResource(R.string.viewer_close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Options(model: PdfToolViewModel) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val merge = model.tool == ExplorerTool.MERGE
        Text(stringResource(R.string.tool_files), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (merge) Text(stringResource(R.string.tool_merge_order), style = MaterialTheme.typography.bodyMedium)
        model.inputs.forEachIndexed { index, input -> InputRow(model, index, input, reorder = merge) }
        HorizontalDivider()
        when (model.tool) {
            ExplorerTool.MERGE -> MergeOptions(model)
            ExplorerTool.SPLIT -> SplitOptions(model)
            ExplorerTool.TO_IMAGES -> ImageOptions(model)
            ExplorerTool.EXTRACT_TEXT -> PagesField(model)
            ExplorerTool.ENCRYPT -> EncryptOptions(model)
            ExplorerTool.DECRYPT -> DecryptOptions(model)
            ExplorerTool.OPEN, null -> Unit
        }
        Text(
            text = model.parentPath?.let { stringResource(R.string.tool_output_in, it) } ?: stringResource(R.string.tool_output_here),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        onClick = model::run,
        enabled = model.canStart(),
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("tool-start"),
    ) {
        Text(stringResource(R.string.tool_start))
    }
}

@Composable
private fun InputRow(model: PdfToolViewModel, index: Int, input: PdfToolViewModel.Input, reorder: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(input.target.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when {
                    input.error != null -> messageOf(input.error!!)?.let { stringResource(it) } ?: stringResource(R.string.tool_error_generic, input.error!!)
                    input.locked -> stringResource(R.string.tool_file_locked)
                    input.encrypted -> stringResource(R.string.tool_file_pages_encrypted, input.pageCount)
                    else -> stringResource(R.string.tool_file_pages, input.pageCount)
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (input.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reorder) {
                IconButton(onClick = { model.move(index, -1) }, enabled = index > 0) {
                    Icon(ViewerIcons.Up, contentDescription = stringResource(R.string.tool_move_up))
                }
                IconButton(onClick = { model.move(index, 1) }, enabled = index < model.inputs.lastIndex) {
                    Icon(ViewerIcons.Down, contentDescription = stringResource(R.string.tool_move_down))
                }
            }
        }
        if (input.locked) {
            var password by rememberSaveable(input.target.uri.toString()) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.weight(1f).testTag("tool-password-$index"),
                    label = { Text(stringResource(R.string.viewer_password_hint)) },
                    isError = input.wrongPassword,
                    supportingText = if (input.wrongPassword) ({ Text(stringResource(R.string.viewer_password_wrong)) }) else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
                TextButton(
                    onClick = { model.unlock(index, password) },
                    enabled = password.isNotEmpty() && !input.unlocking,
                    modifier = Modifier.testTag("tool-unlock-$index"),
                ) {
                    Text(stringResource(R.string.tool_unlock))
                }
            }
        }
    }
}

@Composable
private fun MergeOptions(model: PdfToolViewModel) {
    if (model.inputs.size < 2) Text(stringResource(R.string.tool_merge_need_two), color = MaterialTheme.colorScheme.error)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tool_merge_outline), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = model.keepOutline, onCheckedChange = { model.keepOutline = it }, modifier = Modifier.testTag("tool-merge-outline"))
    }
}

@Composable
private fun SplitOptions(model: PdfToolViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = model.splitMode == PdfToolViewModel.SplitMode.EVERY,
            onClick = { model.splitMode = PdfToolViewModel.SplitMode.EVERY },
            label = { Text(stringResource(R.string.tool_split_every)) },
        )
        FilterChip(
            selected = model.splitMode == PdfToolViewModel.SplitMode.RANGES,
            onClick = { model.splitMode = PdfToolViewModel.SplitMode.RANGES },
            label = { Text(stringResource(R.string.tool_split_ranges)) },
            modifier = Modifier.testTag("tool-split-ranges"),
        )
    }
    val pageCount = model.inputs.firstOrNull()?.pageCount ?: 0
    if (model.splitMode == PdfToolViewModel.SplitMode.EVERY) {
        OutlinedTextField(
            value = model.splitEvery,
            onValueChange = { model.splitEvery = it.filter(Char::isDigit).take(MAX_NUMBER_DIGITS) },
            modifier = Modifier.fillMaxWidth().testTag("tool-split-every"),
            label = { Text(stringResource(R.string.tool_split_every_label)) },
            isError = model.splitEveryInvalid(),
            supportingText = if (model.splitEveryInvalid()) ({ Text(stringResource(R.string.tool_invalid_pages, pageCount)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    } else {
        OutlinedTextField(
            value = model.splitRanges,
            onValueChange = { model.splitRanges = it },
            modifier = Modifier.fillMaxWidth().testTag("tool-split-ranges-field"),
            label = { Text(stringResource(R.string.tool_split_ranges_label)) },
            isError = model.splitRangesInvalid(),
            supportingText = {
                Text(stringResource(if (model.splitRangesInvalid()) R.string.tool_invalid_pages else R.string.tool_split_ranges_hint, pageCount))
            },
        )
    }
}

@Composable
private fun ImageOptions(model: PdfToolViewModel) {
    Text(stringResource(R.string.tool_images_format), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PdfContract.FORMAT_PNG to "PNG", PdfContract.FORMAT_JPEG to "JPEG").forEach { (format, label) ->
            FilterChip(
                selected = model.imageFormat == format,
                onClick = { model.imageFormat = format },
                label = { Text(label) },
                modifier = Modifier.testTag("tool-format-$format"),
            )
        }
    }
    Text(stringResource(R.string.tool_images_dpi), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PdfToolViewModel.DPI_CHOICES.forEach { dpi ->
            FilterChip(
                selected = model.imageDpi == dpi,
                onClick = { model.imageDpi = dpi },
                label = { Text(NumberFormat.getIntegerInstance().format(dpi)) },
                modifier = Modifier.testTag("tool-dpi-$dpi"),
            )
        }
    }
    PagesField(model)
}

@Composable
private fun PagesField(model: PdfToolViewModel) {
    val pageCount = model.inputs.firstOrNull()?.pageCount ?: 0
    OutlinedTextField(
        value = model.pages,
        onValueChange = { model.pages = it },
        modifier = Modifier.fillMaxWidth().testTag("tool-pages"),
        label = { Text(stringResource(R.string.tool_pages_label)) },
        isError = model.pagesInvalid(),
        supportingText = { Text(stringResource(if (model.pagesInvalid()) R.string.tool_invalid_pages else R.string.tool_pages_hint, pageCount)) },
        singleLine = true,
    )
}

@Composable
private fun EncryptOptions(model: PdfToolViewModel) {
    val problem = model.encryptPasswordProblem()
    PasswordField(model.newPassword, { model.newPassword = it }, R.string.tool_encrypt_password, "tool-new-password")
    PasswordField(
        value = model.confirmPassword,
        onChange = { model.confirmPassword = it },
        label = R.string.tool_encrypt_confirm,
        tag = "tool-confirm-password",
        error = when (problem) {
            PdfToolViewModel.PASSWORD_MISMATCH -> if (model.confirmPassword.isNotEmpty()) stringResource(R.string.tool_encrypt_mismatch) else null
            PdfToolViewModel.PASSWORD_TOO_LONG -> stringResource(R.string.tool_encrypt_too_long, PdfContract.MAX_PASSWORD_LENGTH)
            else -> null
        },
    )
    Text(stringResource(R.string.tool_encrypt_note), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DecryptOptions(model: PdfToolViewModel) {
    val input = model.inputs.firstOrNull() ?: return
    if (input.usable && !input.encrypted) {
        Text(stringResource(R.string.tool_decrypt_not_encrypted), modifier = Modifier.testTag("tool-not-encrypted"), color = MaterialTheme.colorScheme.error)
        return
    }
    PasswordField(model.decryptPassword, { model.decryptPassword = it }, R.string.viewer_password_hint, "tool-decrypt-password")
    Text(stringResource(R.string.tool_decrypt_note), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, @StringRes label: Int, tag: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@StringRes
private fun titleOf(tool: ExplorerTool?): Int = when (tool) {
    ExplorerTool.OPEN -> R.string.explorer_action_open
    ExplorerTool.MERGE -> R.string.explorer_action_merge
    ExplorerTool.SPLIT -> R.string.explorer_action_split
    ExplorerTool.TO_IMAGES -> R.string.explorer_action_to_images
    ExplorerTool.EXTRACT_TEXT -> R.string.explorer_action_extract_text
    ExplorerTool.ENCRYPT -> R.string.explorer_action_encrypt
    ExplorerTool.DECRYPT -> R.string.explorer_action_decrypt
    null -> R.string.tool_title
}

/** The message of a failure code, or null for the generic message. zh-CN: 错误码对应的消息; 使用通用消息时为 null. */
@StringRes
internal fun messageOf(code: String): Int? = when (code) {
    PdfErrorCodes.PASSWORD_REQUIRED, PdfErrorCodes.WRONG_PASSWORD -> R.string.tool_error_password
    PdfErrorCodes.PERMISSION_DENIED -> R.string.tool_error_permission
    PdfErrorCodes.INVALID_PDF -> R.string.tool_error_invalid
    PdfErrorCodes.FILE_NOT_FOUND -> R.string.tool_error_not_found
    PdfErrorCodes.PAGE_OUT_OF_RANGE, PdfErrorCodes.INVALID_ARGUMENT -> R.string.tool_error_pages
    PdfErrorCodes.LIMIT_EXCEEDED -> R.string.tool_error_limit
    PdfErrorCodes.UNSUPPORTED -> R.string.tool_error_unsupported
    PdfErrorCodes.CANCELLED -> R.string.tool_cancelled
    ToolFailure.OUTPUT_FAILED -> R.string.tool_error_output
    PdfToolViewModel.NO_FILES -> R.string.tool_error_no_files
    else -> null
}

private const val MAX_NUMBER_DIGITS = 4

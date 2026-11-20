package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer.PdfToolViewModel.Phase
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.components.PaperScreen
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files.BrowseMode
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.files.FileBrowser
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.NotificationAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.StorageAccessState
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.ToolboxFiles
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.ui.toolbox.rememberStorageAccess
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer.ThreePaperPdfViewerActivity
import kotlinx.coroutines.launch
import org.autojs.plugin.pdf.api.PdfContract
import org.autojs.plugin.pdf.api.PdfErrorCodes
import java.io.File
import java.text.NumberFormat

/**
 * The tool screen, shared by the file manager tools (roadmap P3.3, appendix D Q70; [PdfToolActivity]) and the toolbox
 * wizards (roadmap P4.2; a destination of the home screen): the input files, the options, then progress with cancel
 * and the outcome. The toolbox adds choosing and ordering the inputs (the built-in file browser or the system picker),
 * the output folder, the organize grid and, for its outcome, opening and sharing the results. Test tags: `tool-start`,
 * `tool-cancel`, `tool-done`, `tool-error`, `tool-retry`, `tool-password-<n>`, `tool-unlock-<n>`, `tool-add`,
 * `tool-remove-<n>`, `tool-output`, `tool-open`, `tool-share`, `tool-finish`, `tool-page-<page>`; the options of the editing
 * tools and the result summary are in `PdfEditToolOptions.kt`.
 * zh-CN: 工具界面, 由文件管理器工具 (路线图 P3.3, 附录 D Q70; [PdfToolActivity]) 与工具箱向导 (路线图 P4.2; 首页的一个目的地) 共用:
 * 输入文件, 选项, 然后是可取消的进度与结果. 工具箱另有选择与排列输入 (自带文件浏览器或系统选择器), 输出目录, 页面整理网格, 以及
 * 结果的打开与分享. 测试标签见上; 编辑类工具的选项与结果摘要在 `PdfEditToolOptions.kt` 中.
 */
@Composable
internal fun PdfToolScreen(model: PdfToolViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val phase = model.phase
    val storage = rememberStorageAccess()
    val snackbar = remember { SnackbarHostState() }
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.addTargets(listOf(targetOf(context, it))) }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        model.addTargets(uris.map { targetOf(context, it) })
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val inputs = model.kind?.inputs ?: ToolKind.Inputs.ONE_PDF

    when (browsing) {
        BROWSE_INPUTS -> {
            val mode = when (inputs) {
                ToolKind.Inputs.ONE_PDF -> BrowseMode.PDF
                ToolKind.Inputs.PDFS -> BrowseMode.PDFS
                ToolKind.Inputs.IMAGES -> BrowseMode.IMAGES
            }
            FileBrowser(
                mode = mode,
                title = stringResource(if (mode == BrowseMode.PDF) R.string.toolbox_choose_file else R.string.toolbox_add_files),
                onDismiss = { browsing = null },
                onPick = { files ->
                    browsing = null
                    model.addTargets(files.map { ToolTarget(Uri.fromFile(it), it.name) })
                },
                start = model.inputs.lastOrNull()?.target?.uri?.takeIf { it.scheme == "file" }?.path?.let { File(it).parentFile },
                onSystemPicker = {
                    browsing = null
                    when (inputs) {
                        ToolKind.Inputs.ONE_PDF -> pickOne.launch(arrayOf(MIME_PDF))
                        ToolKind.Inputs.PDFS -> pickMany.launch(arrayOf(MIME_PDF))
                        ToolKind.Inputs.IMAGES -> pickMany.launch(arrayOf(MIME_IMAGES))
                    }
                },
                onRequestAccess = storage.request,
                accessGeneration = storage.generation,
            )
            return
        }
        BROWSE_OUTPUT -> {
            FileBrowser(
                mode = BrowseMode.FOLDER,
                title = stringResource(R.string.toolbox_output_folder),
                onDismiss = { browsing = null },
                onPick = { folders ->
                    browsing = null
                    folders.firstOrNull()?.let { model.outputFolder = it }
                },
                start = model.outputFolder,
                onRequestAccess = storage.request,
                accessGeneration = storage.generation,
            )
            return
        }
    }

    val finish: () -> Unit = {
        model.acknowledge()
        onClose()
    }
    // The file manager tool stops when left while running; a toolbox task goes on in the foreground service.
    // zh-CN: 文件管理器工具运行中离开即停止; 工具箱任务在前台服务中继续.
    BackHandler(enabled = !model.toolbox && phase == Phase.Running) { model.cancel() }
    val start: () -> Unit = {
        if (model.toolbox && NotificationAccess.shouldAsk(context)) {
            NotificationAccess.markAsked(context)
            askNotifications.launch(NotificationAccess.PERMISSION)
        }
        model.run()
    }
    PaperScreen(
        title = stringResource(model.kind?.title ?: R.string.tool_title),
        singleLineTitle = true,
        onBack = { if (!model.toolbox && phase == Phase.Running) model.cancel() else onClose() },
        snackbarHostState = snackbar,
        bottomBar = {
            if (phase == Phase.Ready) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = start,
                        enabled = model.canStart(),
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("tool-start"),
                    ) {
                        Text(stringResource(R.string.tool_start))
                    }
                }
            }
        },
    ) { padding ->
        when (phase) {
            Phase.Inspecting -> Centered(padding) {
                CircularProgressIndicator()
                Text(stringResource(R.string.tool_inspecting), style = MaterialTheme.typography.bodyLarge)
            }
            Phase.Ready -> if (model.kind == ToolKind.ORGANIZE) {
                OrganizeOptions(model, padding, storage, { browsing = BROWSE_INPUTS }, { browsing = BROWSE_OUTPUT })
            } else {
                Options(model, padding, storage, { browsing = BROWSE_INPUTS }, { browsing = BROWSE_OUTPUT })
            }
            Phase.Running -> Centered(padding) {
                LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth())
                Text(NumberFormat.getPercentInstance().format(model.progress.toDouble()), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (model.queued) R.string.toolbox_queued else R.string.tool_running), style = MaterialTheme.typography.bodyLarge)
                if (model.toolbox) {
                    Text(
                        text = stringResource(R.string.toolbox_background_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                OutlinedButton(onClick = model::cancel, modifier = Modifier.testTag("tool-cancel")) { Text(stringResource(R.string.viewer_cancel)) }
            }
            is Phase.Done -> if (model.toolbox) {
                ToolboxOutcome(phase.result, padding, finish) { ResultScript(model, phase.result, snackbar, storage) }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.tool_done_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.tool_done_saved, phase.result.displayPath),
                        modifier = Modifier.testTag("tool-done"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (phase.result.files > 1) Text(stringResource(R.string.tool_done_files, phase.result.files), style = MaterialTheme.typography.bodyMedium)
                    ToolSummaryBlock(phase.result)
                    ResultScript(model, phase.result, snackbar, storage)
                    Button(onClick = onClose, modifier = Modifier.testTag("tool-finish")) { Text(stringResource(R.string.tool_done)) }
                }
            }
            is Phase.Failed -> Centered(padding) {
                val failure = phase.failure
                Text(stringResource(R.string.tool_failed_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = messageOf(failure.code)?.let { stringResource(it) } ?: stringResource(R.string.tool_error_generic, failure.code),
                    modifier = Modifier.testTag("tool-error"),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                failure.detail?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (model.inputs.isNotEmpty()) {
                        OutlinedButton(onClick = model::retry, modifier = Modifier.testTag("tool-retry")) { Text(stringResource(R.string.tool_retry)) }
                    }
                    Button(onClick = finish) { Text(stringResource(R.string.viewer_close)) }
                }
            }
        }
    }
}

@Composable
private fun Options(model: PdfToolViewModel, padding: PaddingValues, storage: StorageAccessState, onBrowseInputs: () -> Unit, onBrowseOutput: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (model.toolbox && !storage.granted) StorageHint(storage)
        InputsSection(model, onBrowseInputs)
        HorizontalDivider()
        when (model.kind) {
            ToolKind.MERGE -> MergeOptions(model)
            ToolKind.SPLIT -> SplitOptions(model)
            ToolKind.TO_IMAGES -> ImageOptions(model)
            ToolKind.FROM_IMAGES -> FromImagesOptions(model)
            ToolKind.EXTRACT_TEXT -> PagesField(model)
            ToolKind.ENCRYPT -> EncryptOptions(model)
            ToolKind.DECRYPT -> DecryptOptions(model)
            ToolKind.WATERMARK -> WatermarkOptions(model)
            ToolKind.PAGE_NUMBERS -> PageNumbersOptions(model)
            ToolKind.COMPRESS -> CompressOptions(model)
            ToolKind.FILL_FORM -> FillFormOptions(model)
            ToolKind.PROPERTIES -> PropertiesOptions(model)
            ToolKind.ORGANIZE, null -> Unit
        }
        OutputSection(model, onBrowseOutput)
    }
}

@Composable
private fun StorageHint(storage: StorageAccessState) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.toolbox_storage_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = storage.request, modifier = Modifier.align(Alignment.End).testTag("tool-storage-grant")) {
                Text(stringResource(R.string.home_storage_grant))
            }
        }
    }
}

@Composable
private fun InputsSection(model: PdfToolViewModel, onBrowse: () -> Unit) {
    val kind = model.kind
    val reorder = kind == ToolKind.MERGE || kind == ToolKind.FROM_IMAGES
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.tool_files),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (model.toolbox) {
            val one = kind?.inputs == ToolKind.Inputs.ONE_PDF
            TextButton(onClick = onBrowse, modifier = Modifier.testTag("tool-add")) {
                Icon(painterResource(if (one) R.drawable.ic_folder_open_24 else R.drawable.ic_add_24), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (one) R.string.toolbox_choose_file else R.string.toolbox_add_files), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (model.inputs.size > 1) {
        when (kind) {
            ToolKind.MERGE -> Text(stringResource(R.string.tool_merge_order), style = MaterialTheme.typography.bodyMedium)
            ToolKind.FROM_IMAGES -> Text(stringResource(R.string.toolbox_images_order), style = MaterialTheme.typography.bodyMedium)
            else -> Unit
        }
    }
    if (model.toolbox && model.inputs.isEmpty()) {
        Text(
            text = stringResource(R.string.toolbox_no_files),
            modifier = Modifier.testTag("tool-no-files"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    model.inputs.forEachIndexed { index, input -> InputRow(model, index, input, reorder) }
}

@Composable
private fun OutputSection(model: PdfToolViewModel, onBrowse: () -> Unit) {
    if (!model.toolbox) {
        Text(
            text = model.parentPath?.let { stringResource(R.string.tool_output_in, it) } ?: stringResource(R.string.tool_output_here),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.toolbox_output_folder), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = model.outputFolder?.absolutePath.orEmpty(),
                modifier = Modifier.testTag("tool-output"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onBrowse, modifier = Modifier.testTag("tool-output-change")) { Text(stringResource(R.string.toolbox_output_change)) }
    }
}

@Composable
private fun InputRow(model: PdfToolViewModel, index: Int, input: PdfToolViewModel.Input, reorder: Boolean) {
    val images = model.kind?.inputs == ToolKind.Inputs.IMAGES
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(input.target.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when {
                    images -> null
                    input.inspecting -> stringResource(R.string.tool_inspecting)
                    input.error != null -> messageOf(input.error!!)?.let { stringResource(it) } ?: stringResource(R.string.tool_error_generic, input.error!!)
                    input.locked -> stringResource(R.string.tool_file_locked)
                    input.encrypted -> stringResource(R.string.tool_file_pages_encrypted, input.pageCount)
                    else -> stringResource(R.string.tool_file_pages, input.pageCount)
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (input.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (reorder) {
                RowIcon(R.drawable.ic_arrow_upward_24, stringResource(R.string.tool_move_up), "tool-up-$index", enabled = index > 0) { model.move(index, -1) }
                RowIcon(R.drawable.ic_arrow_downward_24, stringResource(R.string.tool_move_down), "tool-down-$index", enabled = index < model.inputs.lastIndex) {
                    model.move(index, 1)
                }
            }
            if (model.toolbox) {
                RowIcon(R.drawable.ic_close_24, stringResource(R.string.toolbox_remove_file), "tool-remove-$index") { model.remove(index) }
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
private fun RowIcon(@DrawableRes icon: Int, description: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Icon(painterResource(icon), contentDescription = description)
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
    Chips {
        FilterChip(
            selected = model.splitMode == PdfToolViewModel.SplitMode.EVERY,
            onClick = { model.splitMode = PdfToolViewModel.SplitMode.EVERY },
            label = { Text(stringResource(R.string.tool_split_every)) },
            modifier = Modifier.testTag("tool-split-every-mode"),
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
    Chips {
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
    Chips {
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
private fun FromImagesOptions(model: PdfToolViewModel) {
    Text(stringResource(R.string.toolbox_page_size), style = MaterialTheme.typography.titleSmall)
    Chips {
        listOf(
            PdfContract.PAGE_SIZE_FIT to stringResource(R.string.toolbox_page_size_fit),
            PdfContract.PAGE_SIZE_A4 to "A4",
            PdfContract.PAGE_SIZE_LETTER to "Letter",
        ).forEach { (size, label) ->
            FilterChip(
                selected = model.pageSize == size,
                onClick = { model.pageSize = size },
                label = { Text(label) },
                modifier = Modifier.testTag("tool-page-size-$size"),
            )
        }
    }
    val fixed = model.pageSize != PdfContract.PAGE_SIZE_FIT
    Text(stringResource(R.string.toolbox_orientation), style = MaterialTheme.typography.titleSmall)
    Chips {
        listOf(
            PdfContract.ORIENTATION_AUTO to R.string.toolbox_orientation_auto,
            PdfContract.ORIENTATION_PORTRAIT to R.string.toolbox_orientation_portrait,
            PdfContract.ORIENTATION_LANDSCAPE to R.string.toolbox_orientation_landscape,
        ).forEach { (orientation, label) ->
            FilterChip(
                selected = model.orientation == orientation,
                onClick = { model.orientation = orientation },
                label = { Text(stringResource(label)) },
                enabled = fixed,
                modifier = Modifier.testTag("tool-orientation-$orientation"),
            )
        }
    }
    if (!fixed) {
        Text(stringResource(R.string.toolbox_orientation_fit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.toolbox_margin), style = MaterialTheme.typography.titleSmall)
    Chips {
        PdfToolViewModel.MARGIN_CHOICES.zip(listOf(R.string.toolbox_margin_none, R.string.toolbox_margin_narrow, R.string.toolbox_margin_wide)).forEach { (points, label) ->
            FilterChip(
                selected = model.margin == points,
                onClick = { model.margin = points },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("tool-margin-$points"),
            )
        }
    }
}

@Composable
internal fun PagesField(model: PdfToolViewModel) {
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

/**
 * The organize tool (roadmap P4.2): the page grid scrolls with the input and output rows above it. A long press picks
 * a page up; dragging it over another page moves it there, near an edge the grid scrolls. Each page turns or goes away
 * by its buttons, and screen readers get move actions instead of the drag.
 * zh-CN: 页面整理 (路线图 P4.2): 页面网格与上方的输入行, 输出行一起滚动. 长按拿起一页, 拖到另一页上即移到该处, 靠近边缘时网格滚动.
 * 每页可用按钮旋转或删除, 读屏器以移动操作代替拖拽.
 */
@Composable
private fun OrganizeOptions(model: PdfToolViewModel, padding: PaddingValues, storage: StorageAccessState, onBrowseInputs: () -> Unit, onBrowseOutput: () -> Unit) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { DRAG_EDGE.toPx() }
    val step = with(LocalDensity.current) { DRAG_SCROLL_STEP.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val input = model.inputs.firstOrNull()
    val thumbnails = model.thumbnails
    fun pageAt(position: Offset): LazyGridItemInfo? = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
        item.index >= HEADER_ITEMS &&
            position.x >= item.offset.x && position.x < item.offset.x + item.size.width &&
            position.y >= item.offset.y && position.y < item.offset.y + item.size.height
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PAGE_CELL_MIN),
        state = grid,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp)
            .pointerInput(model) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        pageAt(position)?.let {
                            dragging = it.index - HEADER_ITEMS
                            dragOffset = Offset.Zero
                        }
                    },
                    onDrag = { change, amount ->
                        val from = dragging ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragOffset += amount
                        val current = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from + HEADER_ITEMS } ?: return@detectDragGesturesAfterLongPress
                        val center = Offset(
                            current.offset.x + current.size.width / 2f + dragOffset.x,
                            current.offset.y + current.size.height / 2f + dragOffset.y,
                        )
                        val target = pageAt(center)?.takeIf { it.index != current.index }
                        if (target != null) {
                            val to = target.index - HEADER_ITEMS
                            model.movePage(from, to)
                            dragOffset += (current.offset - target.offset).let { Offset(it.x.toFloat(), it.y.toFloat()) }
                            dragging = to
                        }
                        val y = change.position.y
                        when {
                            y < edge -> scope.launch { grid.scrollBy(-step) }
                            y > size.height - edge -> scope.launch { grid.scrollBy(step) }
                        }
                    },
                    onDragEnd = {
                        dragging = null
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        dragging = null
                        dragOffset = Offset.Zero
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!storage.granted) StorageHint(storage)
                InputsSection(model, onBrowseInputs)
                HorizontalDivider()
                OutputSection(model, onBrowseOutput)
                if (input != null && input.usable && (model.organized.isNotEmpty() || model.pagesChanged())) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.toolbox_pages_kept, model.organized.size, input.pageCount),
                            modifier = Modifier.weight(1f).testTag("tool-pages-kept"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        TextButton(onClick = model::rotateAll, modifier = Modifier.testTag("tool-rotate-all")) { Text(stringResource(R.string.toolbox_rotate_all)) }
                        TextButton(onClick = model::resetPages, enabled = model.pagesChanged(), modifier = Modifier.testTag("tool-reset-pages")) {
                            Text(stringResource(R.string.toolbox_reset_pages))
                        }
                    }
                    Text(
                        text = stringResource(if (model.pagesChanged()) R.string.toolbox_organize_hint else R.string.toolbox_organize_unchanged),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (thumbnails?.failed == true) {
                        Text(stringResource(R.string.toolbox_thumbnails_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        itemsIndexed(model.organized, key = { _, page -> "page-${page.page}" }) { index, page ->
            val lifted = dragging == index
            val placement = if (lifted) {
                Modifier.zIndex(1f).graphicsLayer {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = LIFTED_SCALE
                    scaleY = LIFTED_SCALE
                }
            } else {
                Modifier.animateItem()
            }
            PageCell(model, index, page, lifted, placement)
        }
    }
}

@Composable
private fun PageCell(model: PdfToolViewModel, index: Int, page: PdfToolViewModel.Page, lifted: Boolean, modifier: Modifier) {
    val thumbnails = model.thumbnails
    val earlier = stringResource(R.string.toolbox_move_earlier)
    val later = stringResource(R.string.toolbox_move_later)
    val last = model.organized.lastIndex
    LaunchedEffect(thumbnails, page.page) { thumbnails?.request(page.page) }
    Surface(
        modifier = modifier
            .testTag("tool-page-${page.page}")
            .semantics {
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(earlier) { model.movePage(index, index - 1); true })
                    if (index < last) add(CustomAccessibilityAction(later) { model.movePage(index, index + 1); true })
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = if (lifted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (lifted) 6.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                val bitmap = thumbnails?.bitmap(page.page)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.viewer_page_thumbnail, page.page),
                        modifier = Modifier.fillMaxSize().rotate(page.rotation.toFloat()),
                        contentScale = ContentScale.Fit,
                    )
                } else if (thumbnails?.failed == true || thumbnails == null) {
                    Icon(painterResource(R.drawable.ic_description_24), contentDescription = stringResource(R.string.viewer_page_thumbnail, page.page))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = NumberFormat.getIntegerInstance().format(page.page),
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                IconButton(onClick = { model.rotatePage(index) }, modifier = Modifier.size(40.dp).testTag("tool-rotate-page-${page.page}")) {
                    Icon(painterResource(R.drawable.ic_rotate_right_24), contentDescription = stringResource(R.string.toolbox_rotate_page, page.page))
                }
                IconButton(onClick = { model.removePage(index) }, modifier = Modifier.size(40.dp).testTag("tool-delete-page-${page.page}")) {
                    Icon(painterResource(R.drawable.ic_delete_24), contentDescription = stringResource(R.string.toolbox_delete_page, page.page))
                }
            }
        }
    }
}

/** The toolbox outcome: where the results went, each result to open, and opening or sharing them. zh-CN: 工具箱结果: 结果位置, 可逐个打开的结果, 以及打开或分享. */
@Composable
private fun ToolboxOutcome(result: ToolResult, padding: PaddingValues, onFinish: () -> Unit, script: @Composable () -> Unit) {
    val context = LocalContext.current
    val files = remember(result) { ToolboxFiles.filesOf(File(result.displayPath)) }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_check_circle_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.tool_done_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.tool_done_saved, result.displayPath),
            modifier = Modifier.testTag("tool-done"),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (result.files > 1) Text(stringResource(R.string.tool_done_files, result.files), style = MaterialTheme.typography.bodyMedium)
        ToolSummaryBlock(result)
        if (files.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.toolbox_result_files), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                files.take(MAX_LISTED_RESULTS).forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ToolboxFiles.open(context, file) }
                            .padding(vertical = 10.dp)
                            .testTag("tool-result-$index"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = when {
                            ToolboxFiles.isPdf(file) -> R.drawable.ic_description_24
                            ToolboxFiles.isImage(file) -> R.drawable.ic_image_24
                            else -> R.drawable.ic_file_24
                        }
                        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = file.name,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
                if (files.size > MAX_LISTED_RESULTS) {
                    Text(
                        text = stringResource(R.string.toolbox_result_more, files.size - MAX_LISTED_RESULTS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Chips {
            val single = files.singleOrNull()
            if (single != null) {
                Button(onClick = { ToolboxFiles.open(context, single) }, modifier = Modifier.testTag("tool-open")) { Text(stringResource(R.string.toolbox_open)) }
            }
            if (files.isNotEmpty()) {
                OutlinedButton(onClick = { ToolboxFiles.share(context, files) }, modifier = Modifier.testTag("tool-share")) {
                    Text(stringResource(if (files.size > 1) R.string.toolbox_share_all else R.string.toolbox_share))
                }
            }
            TextButton(onClick = onFinish, modifier = Modifier.testTag("tool-finish")) { Text(stringResource(R.string.tool_done)) }
        }
        script()
    }
}

/**
 * The run written as a script under the result (roadmap P6.13); nothing when the screen no longer knows the run (it
 * was recreated from a saved state).
 * zh-CN: 结果下方写成脚本的本次执行 (路线图 P6.13); 界面已不知道本次执行 (从保存的状态重建) 时不显示.
 */
@Composable
private fun ResultScript(model: PdfToolViewModel, result: ToolResult, snackbar: SnackbarHostState, storage: StorageAccessState) {
    val request = remember(result, model.scriptRun) { model.scriptRequestOf(result) } ?: return
    ScriptBlock(request, snackbar, storage)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** A file from the system picker: its URI and the name the provider gives. zh-CN: 来自系统选择器的文件: URI 及提供者给出的名称. */
internal fun targetOf(context: Context, uri: Uri): ToolTarget =
    ToolTarget(uri, ThreePaperPdfViewerActivity.displayNameOf(context, uri) ?: context.getString(R.string.viewer_untitled))

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

private const val BROWSE_INPUTS = "inputs"

private const val BROWSE_OUTPUT = "output"

private const val MIME_PDF = "application/pdf"

internal const val MIME_IMAGES = "image/*"

/** The header item above the pages of the organize grid. zh-CN: 整理网格中页面之上的标题项. */
private const val HEADER_ITEMS = 1

private const val LIFTED_SCALE = 1.05f

private const val MAX_LISTED_RESULTS = 20

private val PAGE_CELL_MIN = 128.dp

private val DRAG_EDGE = 56.dp

private val DRAG_SCROLL_STEP = 24.dp

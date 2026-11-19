package io.github.supermonster003.autojs6.plugin.three.paper.pdf.explorer

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import java.text.NumberFormat
import org.autojs.plugin.pdf.api.PdfContract

/*
 * The options of the editing tools added in 1.1.0 (roadmap P6.12, D68) and the summary on the result page of every
 * tool. Uncommon options sit under a folded "Advanced" row. Test tags: `tool-watermark-type-<type>`,
 * `tool-watermark-text`, `tool-watermark-image`, `tool-watermark-choose-image`, `tool-watermark-position-<position>`,
 * `tool-watermark-rotation-<degrees>`, `tool-watermark-opacity-<percent>`, `tool-watermark-tile`,
 * `tool-watermark-advanced`, `tool-watermark-color-<n>`, `tool-watermark-font-<auto|points>`,
 * `tool-watermark-width-<fit|points>`, `tool-watermark-layer-<layer>`, `tool-watermark-preview`,
 * `tool-numbers-switch`, `tool-numbers-format-<n>`, `tool-numbers-format`, `tool-numbers-position-<position>`,
 * `tool-numbers-start`, `tool-numbers-advanced`, `tool-numbers-<header|footer>-<left|center|right>`,
 * `tool-numbers-font-<points>`, `tool-compress-<preset>`, `tool-compress-advanced`, `tool-compress-dpi-<dpi>`,
 * `tool-compress-quality-<quality>`, `tool-compress-metadata`, `tool-compress-object-streams`, `tool-form-loading`,
 * `tool-form-error`, `tool-form-none`, `tool-form-xfa`, `tool-form-page-<page>`, `tool-form-field-<name>`,
 * `tool-form-choice-<name>-<n>`, `tool-form-flatten`, `tool-form-viewer-note`, `tool-properties-facts`,
 * `tool-properties-<key>`, `tool-properties-outline`, `tool-properties-clear-outline`, `tool-result-summary`.
 * zh-CN: 1.1.0 新增的编辑类工具的选项 (路线图 P6.12, D68), 以及所有工具结果页上的摘要. 不常用的选项放在折叠的 "高级" 行下. 测试标签见上.
 */

/** Text or image, where, how and on which pages, with a one-page preview. zh-CN: 文字或图片, 位置, 样式与页码范围, 附单页预览. */
@Composable
internal fun WatermarkOptions(model: PdfToolViewModel) {
    val state = model.watermark
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { state.image = targetOf(context, it) }
    }
    val image = state.type == PdfContract.STAMP_IMAGE
    Chips {
        listOf(PdfContract.STAMP_TEXT to R.string.tool_watermark_type_text, PdfContract.STAMP_IMAGE to R.string.tool_watermark_type_image).forEach { (type, label) ->
            FilterChip(
                selected = state.type == type,
                onClick = { state.selectType(type) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("tool-watermark-type-$type"),
            )
        }
    }
    if (image) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.image?.displayName ?: stringResource(R.string.tool_watermark_no_image),
                modifier = Modifier.weight(1f).testTag("tool-watermark-image"),
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.image == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { pickImage.launch(arrayOf(MIME_IMAGES)) }, modifier = Modifier.testTag("tool-watermark-choose-image")) {
                Text(stringResource(R.string.tool_watermark_choose_image))
            }
        }
    } else {
        OutlinedTextField(
            value = state.text,
            onValueChange = { state.text = it },
            modifier = Modifier.fillMaxWidth().testTag("tool-watermark-text"),
            label = { Text(stringResource(R.string.tool_watermark_text)) },
            isError = state.textTooLong(),
            supportingText = {
                Text(
                    if (state.textTooLong()) {
                        stringResource(R.string.tool_text_too_long, PdfContract.MAX_STAMP_TEXT_LENGTH)
                    } else {
                        stringResource(R.string.tool_stamp_placeholders)
                    },
                )
            },
        )
    }
    Label(R.string.tool_position)
    PositionGrid(WatermarkState.POSITIONS, state.position, "tool-watermark-position") { state.position = it }
    Label(R.string.tool_watermark_rotation)
    Chips {
        WatermarkState.ROTATIONS.forEach { degrees ->
            FilterChip(
                selected = state.rotation == degrees,
                onClick = { state.rotation = degrees },
                label = { Text(stringResource(R.string.tool_degrees, degrees)) },
                modifier = Modifier.testTag("tool-watermark-rotation-$degrees"),
            )
        }
    }
    Label(R.string.tool_watermark_opacity)
    Chips {
        WatermarkState.OPACITIES.forEach { percent ->
            FilterChip(
                selected = state.opacity == percent,
                onClick = { state.opacity = percent },
                label = { Text(NumberFormat.getPercentInstance().format(percent / PERCENT)) },
                modifier = Modifier.testTag("tool-watermark-opacity-$percent"),
            )
        }
    }
    SwitchRow(R.string.tool_watermark_tile, state.tile, "tool-watermark-tile") { state.tile = it }
    AdvancedRow(state.advanced, "tool-watermark-advanced") { state.advanced = it }
    if (state.advanced) {
        if (image) {
            Label(R.string.tool_watermark_width)
            Chips {
                FilterChip(
                    selected = state.width == null,
                    onClick = { state.width = null },
                    label = { Text(stringResource(R.string.tool_watermark_width_original)) },
                    modifier = Modifier.testTag("tool-watermark-width-fit"),
                )
                WatermarkState.WIDTHS.forEach { points ->
                    FilterChip(
                        selected = state.width == points,
                        onClick = { state.width = points },
                        label = { Text(stringResource(R.string.tool_points, points)) },
                        modifier = Modifier.testTag("tool-watermark-width-$points"),
                    )
                }
            }
        } else {
            Label(R.string.tool_color)
            Chips {
                WatermarkState.COLORS.zip(COLOR_NAMES).forEachIndexed { index, (color, name) ->
                    FilterChip(
                        selected = state.color == color,
                        onClick = { state.color = color },
                        label = { Text(stringResource(name)) },
                        leadingIcon = { Box(Modifier.size(16.dp).background(Color(color.toColorInt()), CircleShape)) },
                        modifier = Modifier.testTag("tool-watermark-color-$index"),
                    )
                }
            }
            Label(R.string.tool_font_size)
            Chips {
                FilterChip(
                    selected = state.fontSize == null,
                    onClick = { state.fontSize = null },
                    label = { Text(stringResource(R.string.tool_font_size_auto)) },
                    modifier = Modifier.testTag("tool-watermark-font-auto"),
                )
                WatermarkState.FONT_SIZES.forEach { points ->
                    FilterChip(
                        selected = state.fontSize == points,
                        onClick = { state.fontSize = points },
                        label = { Text(stringResource(R.string.tool_points, points)) },
                        modifier = Modifier.testTag("tool-watermark-font-$points"),
                    )
                }
            }
        }
        Label(R.string.tool_watermark_layer)
        Chips {
            listOf(PdfContract.LAYER_OVER to R.string.tool_watermark_layer_over, PdfContract.LAYER_UNDER to R.string.tool_watermark_layer_under).forEach { (layer, label) ->
                FilterChip(
                    selected = state.layer == layer,
                    onClick = { state.layer = layer },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("tool-watermark-layer-$layer"),
                )
            }
        }
        if (state.layer == PdfContract.LAYER_UNDER) Note(R.string.tool_watermark_layer_under_note)
    }
    PagesField(model)
    WatermarkPreviewCard(model)
}

@Composable
private fun WatermarkPreviewCard(model: PdfToolViewModel) {
    val preview = model.preview ?: return
    val options = model.watermark.options(null)
    val page = model.previewPage()
    LaunchedEffect(preview, options, page) { preview.request(options, page) }
    val bitmap = preview.bitmap
    Label(R.string.tool_watermark_preview)
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = PREVIEW_MIN_HEIGHT), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.tool_watermark_preview_page, preview.page),
                modifier = Modifier
                    .widthIn(max = PREVIEW_MAX_WIDTH)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .testTag("tool-watermark-preview"),
                contentScale = ContentScale.FillWidth,
            )
            preview.failed -> Note(R.string.tool_watermark_preview_failed)
            !preview.busy -> Note(R.string.tool_watermark_preview_empty)
        }
        if (preview.busy) CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
    if (bitmap != null) {
        Note(R.string.tool_watermark_preview_page, preview.page)
        Note(R.string.tool_watermark_preview_note)
    }
}

/** A page number, header and footer texts. zh-CN: 页码, 页眉与页脚文字. */
@Composable
internal fun PageNumbersOptions(model: PdfToolViewModel) {
    val state = model.pageNumbers
    SwitchRow(R.string.tool_page_numbers_add, state.numbered, "tool-numbers-switch") { state.numbered = it }
    if (state.numbered) {
        Label(R.string.tool_page_numbers_format)
        Chips {
            PageNumbersState.FORMATS.forEachIndexed { index, format ->
                FilterChip(
                    selected = state.format == format,
                    onClick = { state.format = format },
                    label = { Text(PageNumbersState.sample(format)) },
                    modifier = Modifier.testTag("tool-numbers-format-$index"),
                )
            }
        }
        OutlinedTextField(
            value = state.format,
            onValueChange = { state.format = it },
            modifier = Modifier.fillMaxWidth().testTag("tool-numbers-format"),
            label = { Text(stringResource(R.string.tool_page_numbers_format_custom)) },
            isError = state.format.isBlank(),
            supportingText = { Text(stringResource(R.string.tool_stamp_placeholders)) },
            singleLine = true,
        )
        Label(R.string.tool_position)
        PositionGrid(PageNumbersState.POSITIONS, state.position, "tool-numbers-position", splitRows = true) { state.position = it }
        OutlinedTextField(
            value = state.start,
            onValueChange = { state.start = it.filter(Char::isDigit).take(START_DIGITS) },
            modifier = Modifier.fillMaxWidth().testTag("tool-numbers-start"),
            label = { Text(stringResource(R.string.tool_page_numbers_start)) },
            isError = state.startInvalid(),
            supportingText = {
                Text(
                    if (state.startInvalid()) {
                        stringResource(R.string.tool_page_numbers_start_invalid, PageNumbersState.MAX_START)
                    } else {
                        stringResource(R.string.tool_page_numbers_start_hint)
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    // Without a page number the header and footer are all there is, so they are not folded.
    // zh-CN: 不加页码时只剩页眉与页脚, 因此不折叠.
    if (state.numbered) AdvancedRow(state.advanced, "tool-numbers-advanced") { state.advanced = it }
    if (state.advanced || !state.numbered) {
        Label(R.string.tool_page_numbers_header)
        SlotFields(state.header, "tool-numbers-header") { state.header = it }
        Label(R.string.tool_page_numbers_footer)
        SlotFields(state.footer, "tool-numbers-footer") { state.footer = it }
        Note(R.string.tool_page_numbers_slots_note)
        Label(R.string.tool_font_size)
        Chips {
            PageNumbersState.FONT_SIZES.forEach { points ->
                FilterChip(
                    selected = state.fontSize == points,
                    onClick = { state.fontSize = points },
                    label = { Text(stringResource(R.string.tool_points, points)) },
                    modifier = Modifier.testTag("tool-numbers-font-$points"),
                )
            }
        }
    }
    if (state.overlaps()) Warning(R.string.tool_page_numbers_overlap, "tool-numbers-overlap")
    if (state.tooLong()) Warning(R.string.tool_text_too_long, "tool-numbers-too-long", PdfContract.MAX_STAMP_TEXT_LENGTH)
    if (!state.numbered && state.header.blank && state.footer.blank) Note(R.string.tool_page_numbers_nothing)
    PagesField(model)
}

@Composable
private fun SlotFields(slots: ToolOptions.Slots, tag: String, onChange: (ToolOptions.Slots) -> Unit) {
    listOf(
        Triple(R.string.tool_slot_left, slots.left, "left"),
        Triple(R.string.tool_slot_center, slots.center, "center"),
        Triple(R.string.tool_slot_right, slots.right, "right"),
    ).forEach { (label, value, place) ->
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                onChange(
                    when (place) {
                        "left" -> slots.copy(left = text)
                        "center" -> slots.copy(center = text)
                        else -> slots.copy(right = text)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("$tag-$place"),
            label = { Text(stringResource(label)) },
            singleLine = true,
        )
    }
}

/** One of three presets; their numbers and the structure options under "Advanced". zh-CN: 三个预设之一; 其数值与结构选项在 "高级" 中. */
@Composable
internal fun CompressOptions(model: PdfToolViewModel) {
    val state = model.compress
    Label(R.string.tool_compress_level)
    Column(modifier = Modifier.selectableGroup()) {
        CompressState.PRESETS.zip(PRESET_NAMES).forEach { (preset, name) ->
            val numbers = CompressState.numbersOf(preset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = state.preset == preset, onClick = { state.selectPreset(preset) }, role = Role.RadioButton)
                    .padding(vertical = 6.dp)
                    .testTag("tool-compress-$preset"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.preset == preset, onClick = null)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(stringResource(name), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.tool_compress_preset_detail, numbers.imageDpi, numbers.imageQuality),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Note(R.string.tool_compress_note)
    AdvancedRow(state.advanced, "tool-compress-advanced") { state.advanced = it }
    if (state.advanced) {
        Label(R.string.tool_compress_dpi)
        Chips {
            CompressState.DPI_CHOICES.forEach { dpi ->
                FilterChip(
                    selected = state.imageDpi == dpi,
                    onClick = { state.imageDpi = dpi },
                    label = { Text(NumberFormat.getIntegerInstance().format(dpi)) },
                    modifier = Modifier.testTag("tool-compress-dpi-$dpi"),
                )
            }
        }
        Label(R.string.tool_compress_quality)
        Chips {
            CompressState.QUALITY_CHOICES.forEach { quality ->
                FilterChip(
                    selected = state.imageQuality == quality,
                    onClick = { state.imageQuality = quality },
                    label = { Text(NumberFormat.getIntegerInstance().format(quality)) },
                    modifier = Modifier.testTag("tool-compress-quality-$quality"),
                )
            }
        }
        SwitchRow(R.string.tool_compress_remove_metadata, state.removeMetadata, "tool-compress-metadata", R.string.tool_compress_remove_metadata_note) {
            state.removeMetadata = it
        }
        SwitchRow(R.string.tool_compress_object_streams, state.objectStreams, "tool-compress-object-streams", R.string.tool_compress_object_streams_note) {
            state.objectStreams = it
        }
    }
}

/**
 * The form fields grouped by page (each group folds), required and read-only ones marked, then flattening.
 * zh-CN: 按页分组的表单字段 (每组可折叠), 必填与只读字段有标记, 然后是扁平化.
 */
@Composable
internal fun FillFormOptions(model: PdfToolViewModel) {
    val state = model.fillForm
    val error = state.error
    when {
        state.loading -> Loading(R.string.tool_form_loading, "tool-form-loading")
        error != null -> Warning(messageOf(error) ?: R.string.tool_details_failed, "tool-form-error")
        state.dynamicXfa() -> Warning(R.string.tool_form_xfa_dynamic, "tool-form-xfa")
        state.fields.isEmpty() -> if (model.inputs.firstOrNull()?.usable == true) Note(R.string.tool_form_none, tag = "tool-form-none")
        else -> {
            val missing = state.missingRequired()
            if (missing > 0) Warning(R.string.tool_form_missing_required, "tool-form-missing", missing)
            if (state.xfa == PdfContract.XFA_STATIC) Note(R.string.tool_form_xfa_static)
            state.groups().forEach { (page, fields) ->
                val folded = state.folded[page] == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { state.folded[page] = !folded }
                        .padding(vertical = 8.dp)
                        .testTag("tool-form-page-$page"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_right_24), contentDescription = null, modifier = Modifier.rotate(if (folded) 0f else EXPANDED_DEGREES))
                    Text(
                        text = if (page > 0) stringResource(R.string.tool_form_page, page) else stringResource(R.string.tool_form_no_page),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.tool_form_field_count, fields.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!folded) fields.forEach { FormFieldEditor(state, it) }
            }
            if (state.truncated) Note(R.string.tool_form_truncated, FillFormState.MAX_FIELDS)
            SwitchRow(R.string.tool_form_flatten, state.flatten, "tool-form-flatten", R.string.tool_form_flatten_note) { state.flatten = it }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) Note(R.string.tool_form_viewer_note, tag = "tool-form-viewer-note")
            if (state.edits.isEmpty() && !state.flatten) Note(R.string.tool_form_nothing)
        }
    }
}

@Composable
private fun FormFieldEditor(state: FillFormState, field: FormFieldItem) {
    val tag = "tool-form-field-${field.name}"
    val value = state.valueOf(field)
    val marks = listOfNotNull(
        stringResource(R.string.tool_form_required).takeIf { field.required },
        stringResource(R.string.tool_form_read_only).takeIf { field.readOnly },
        stringResource(R.string.tool_form_signed).takeIf { field.signed },
    ).joinToString(", ")
    val enabled = field.fillable
    when {
        field.type == PdfContract.FORM_FIELD_TEXT || (field.type == PdfContract.FORM_FIELD_COMBO && field.choices.isEmpty()) -> OutlinedTextField(
            value = value as? String ?: "",
            onValueChange = { text -> state.set(field, field.maxLength?.let { text.take(it) } ?: text) },
            modifier = Modifier.fillMaxWidth().testTag(tag),
            enabled = enabled,
            label = { Text(field.title) },
            supportingText = if (marks.isNotEmpty()) ({ Text(marks) }) else null,
            isError = field.required && enabled && FormValues.empty(value),
            singleLine = !field.multiline,
            minLines = if (field.multiline) MULTILINE_ROWS else 1,
            visualTransformation = if (field.password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (field.password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        )
        field.type == PdfContract.FORM_FIELD_CHECKBOX -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = value == true, enabled = enabled, role = Role.Checkbox, onValueChange = { state.set(field, it) })
                .padding(vertical = 4.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = value == true, onCheckedChange = null, enabled = enabled)
            FieldTitle(field, marks, Modifier.weight(1f).padding(start = 12.dp))
        }
        field.type == PdfContract.FORM_FIELD_RADIO || field.type == PdfContract.FORM_FIELD_COMBO || field.type == PdfContract.FORM_FIELD_LIST -> Column(
            modifier = Modifier.fillMaxWidth().testTag(tag),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FieldTitle(field, marks, Modifier)
            val multi = field.type == PdfContract.FORM_FIELD_LIST && field.multiSelect
            val selected: List<String> = when (value) {
                is List<*> -> value.map { it.toString() }
                null -> emptyList()
                else -> listOf(value.toString())
            }
            val choose: (FormChoice) -> Unit = { choice ->
                val next: Any? = when {
                    multi -> if (choice.value in selected) selected - choice.value else selected + choice.value
                    field.type == PdfContract.FORM_FIELD_LIST -> listOf(choice.value)
                    else -> choice.value
                }
                state.set(field, next)
            }
            if (field.choices.size > MAX_CHOICE_CHIPS) {
                ChoiceMenu(field, selected, enabled, multi, tag, choose)
            } else {
                Chips {
                    field.choices.forEachIndexed { index, choice ->
                        FilterChip(
                            selected = choice.value in selected,
                            onClick = { choose(choice) },
                            label = { Text(choice.label) },
                            enabled = enabled,
                            modifier = Modifier.testTag("tool-form-choice-${field.name}-$index"),
                        )
                    }
                }
            }
        }
        else -> Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag)) {
            Text(field.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(if (field.signed) R.string.tool_form_signed_note else R.string.tool_form_not_fillable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldTitle(field: FormFieldItem, marks: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = field.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (field.fillable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (marks.isNotEmpty()) {
            Text(
                text = marks,
                style = MaterialTheme.typography.bodySmall,
                color = if (field.required && field.fillable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The choices of a long list in a menu; a multi-select list keeps the menu open. zh-CN: 长列表的选项放在菜单中; 多选列表保持菜单打开. */
@Composable
private fun ChoiceMenu(field: FormFieldItem, selected: List<String>, enabled: Boolean, multi: Boolean, tag: String, choose: (FormChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = field.choices.filter { it.value in selected }.joinToString(", ") { it.label }.ifEmpty { stringResource(R.string.tool_form_choose) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("$tag-menu")) {
            Text(shown, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(painterResource(R.drawable.ic_chevron_right_24), contentDescription = null, modifier = Modifier.rotate(EXPANDED_DEGREES))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            field.choices.forEachIndexed { index, choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        choose(choice)
                        if (!multi) open = false
                    },
                    leadingIcon = if (choice.value in selected) ({ Icon(painterResource(R.drawable.ic_check_24), contentDescription = null) }) else null,
                    modifier = Modifier.testTag("tool-form-choice-${field.name}-$index"),
                )
            }
        }
    }
}

/**
 * The file facts as an aligned block, the editable title, author, subject and keywords, and the outline with a switch
 * that removes it.
 * zh-CN: 对齐的文件信息块, 可编辑的标题, 作者, 主题与关键词, 以及书签与清除书签的开关.
 */
@Composable
internal fun PropertiesOptions(model: PdfToolViewModel) {
    val state = model.properties
    val error = state.error
    when {
        state.loading -> {
            Loading(R.string.tool_properties_loading, "tool-properties-loading")
            return
        }
        error != null -> {
            Warning(messageOf(error) ?: R.string.tool_details_failed, "tool-properties-error")
            return
        }
        state.facts.isEmpty() -> return
    }
    Label(R.string.tool_properties_file)
    KeyValueBlock(factRows(state.facts), Modifier.testTag("tool-properties-facts"))
    Label(R.string.tool_properties_edit)
    PropertiesState.EDITABLE.forEach { key ->
        val value = state.edited[key].orEmpty()
        OutlinedTextField(
            value = value,
            onValueChange = { state.edited[key] = it },
            modifier = Modifier.fillMaxWidth().testTag("tool-properties-$key"),
            label = { Text(stringResource(INFO_LABELS.getValue(key))) },
            isError = value.length > PdfContract.MAX_INFO_VALUE_LENGTH,
            supportingText = if (value.length > PdfContract.MAX_INFO_VALUE_LENGTH) ({ Text(stringResource(R.string.tool_text_too_long, PdfContract.MAX_INFO_VALUE_LENGTH)) }) else null,
            singleLine = key != PdfContract.FIELD_SUBJECT,
        )
    }
    Note(R.string.tool_properties_empty_removes)
    Label(R.string.tool_properties_outline)
    if (state.outline.isEmpty()) {
        Note(R.string.tool_properties_outline_none, tag = "tool-properties-outline-none")
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("tool-properties-outline")) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.outline.forEach { item ->
                    Row(modifier = Modifier.padding(start = OUTLINE_INDENT * item.level.coerceAtMost(MAX_OUTLINE_INDENT))) {
                        Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.page > 0) {
                            Text(
                                text = NumberFormat.getIntegerInstance().format(item.page),
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (state.outlineMore) Note(R.string.tool_properties_outline_more, PropertiesState.MAX_OUTLINE_SHOWN)
        SwitchRow(R.string.tool_properties_clear_outline, state.clearOutline, "tool-properties-clear-outline") { state.clearOutline = it }
    }
    Note(R.string.tool_properties_outline_script)
    if (!state.tooLong() && state.options() == null) Note(R.string.tool_properties_nothing)
}

@Composable
private fun factRows(facts: Map<String, Any?>): List<Pair<String, String>> {
    val context = LocalContext.current
    val resources = LocalResources.current
    val integers = NumberFormat.getIntegerInstance()
    val rows = ArrayList<Pair<String, String>>()
    fun add(@StringRes label: Int, text: String?) {
        if (!text.isNullOrBlank()) rows += resources.getString(label) to text
    }
    add(R.string.tool_info_name, facts[PdfContract.KEY_DISPLAY_NAME] as? String)
    add(R.string.tool_info_size, (facts[PdfContract.KEY_SIZE_BYTES] as? Number)?.let { Formatter.formatShortFileSize(context, it.toLong()) })
    add(R.string.tool_info_pages, (facts[PdfContract.KEY_PAGE_COUNT] as? Number)?.let { integers.format(it.toLong()) })
    add(R.string.tool_info_version, facts[PdfContract.KEY_PDF_VERSION] as? String)
    add(R.string.tool_info_encrypted, (facts[PdfContract.KEY_ENCRYPTED] as? Boolean)?.let { resources.getString(if (it) R.string.tool_yes else R.string.tool_no) })
    add(R.string.tool_info_form_fields, (facts[PdfContract.KEY_FORM_FIELD_COUNT] as? Number)?.let { integers.format(it.toLong()) })
    add(R.string.tool_info_title, facts[PdfContract.KEY_TITLE] as? String)
    add(R.string.tool_info_author, facts[PdfContract.KEY_AUTHOR] as? String)
    add(R.string.tool_info_producer, facts[PdfContract.KEY_PRODUCER] as? String)
    add(R.string.tool_info_creator, facts[PdfContract.KEY_CREATOR] as? String)
    add(R.string.tool_info_created, (facts[PdfContract.KEY_CREATION_DATE] as? String)?.let(PropertiesState::displayDate))
    add(R.string.tool_info_modified, (facts[PdfContract.KEY_MODIFICATION_DATE] as? String)?.let(PropertiesState::displayDate))
    return rows
}

/**
 * Keys and values in two columns; the key column is as wide as the widest key, at most [MAX_KEY_SHARE] of the width.
 * zh-CN: 两列的键与值; 键列与最宽的键一样宽, 至多占宽度的 [MAX_KEY_SHARE].
 */
@Composable
private fun KeyValueBlock(rows: List<Pair<String, String>>, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Layout(
            content = {
                rows.forEach { (key, value) ->
                    Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) { measurables, constraints ->
            val gap = KEY_GAP.roundToPx()
            val rowGap = ROW_GAP.roundToPx()
            val width = constraints.maxWidth
            val keys = measurables.filterIndexed { index, _ -> index % 2 == 0 }
            val values = measurables.filterIndexed { index, _ -> index % 2 == 1 }
            val keyWidth = (keys.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0).coerceAtMost((width * MAX_KEY_SHARE).toInt())
            val valueWidth = (width - keyWidth - gap).coerceAtLeast(0)
            val keyPlaceables = keys.map { it.measure(Constraints(maxWidth = keyWidth)) }
            val valuePlaceables = values.map { it.measure(Constraints(maxWidth = valueWidth)) }
            val heights = keyPlaceables.indices.map { maxOf(keyPlaceables[it].height, valuePlaceables[it].height) }
            val total = heights.sum() + rowGap * (heights.size - 1).coerceAtLeast(0)
            layout(width, total) {
                var y = 0
                heights.forEachIndexed { index, height ->
                    keyPlaceables[index].placeRelative(0, y)
                    valuePlaceables[index].placeRelative(keyWidth + gap, y)
                    y += height + rowGap
                }
            }
        }
    }
}

/**
 * What a tool reports on its result page: the new size of a compressed file, the filled fields, the changed
 * properties, and the operation's warnings (in English, as the engine gives them; appendix D Q264).
 * zh-CN: 工具在结果页上报告的内容: 压缩后的大小, 填写的字段数, 修改的属性, 以及操作的警告 (英文, 按引擎原文; 附录 D Q264).
 */
@Composable
internal fun ToolSummaryBlock(result: ToolResult) {
    val summary = result.summary
    if (summary == null && result.warnings.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().testTag("tool-result-summary"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (summary) {
            is ToolSummary.Compressed -> {
                if (summary.sizeBytes in 1 until summary.sourceBytes) {
                    val saved = (summary.sourceBytes - summary.sizeBytes).toDouble() / summary.sourceBytes
                    SummaryLine(
                        stringResource(
                            R.string.tool_result_compressed,
                            Formatter.formatShortFileSize(context, summary.sourceBytes),
                            Formatter.formatShortFileSize(context, summary.sizeBytes),
                            NumberFormat.getPercentInstance().format(saved),
                        ),
                    )
                } else {
                    SummaryLine(stringResource(R.string.tool_result_not_smaller, Formatter.formatShortFileSize(context, summary.sizeBytes)))
                }
                if (summary.imagesTotal > 0) SummaryLine(stringResource(R.string.tool_result_images, summary.imagesRecompressed, summary.imagesTotal))
            }
            is ToolSummary.Filled -> {
                SummaryLine(stringResource(R.string.tool_result_filled, summary.filled))
                if (summary.flattened) {
                    SummaryLine(stringResource(R.string.tool_result_flattened))
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    Note(R.string.tool_result_viewer_hint, tag = "tool-result-viewer-hint")
                }
            }
            is ToolSummary.PropertiesSaved -> {
                if (summary.infoChanged > 0) SummaryLine(stringResource(R.string.tool_result_properties, summary.infoChanged))
                if (summary.outlineCleared) SummaryLine(stringResource(R.string.tool_result_outline_cleared))
            }
            null -> Unit
        }
        if (result.warnings.isNotEmpty()) {
            Text(stringResource(R.string.tool_result_warnings), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            result.warnings.forEach { warning ->
                Text(warning, modifier = Modifier.testTag("tool-result-warning"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/**
 * Cells for [positions], three per row in page order (left to right whatever the layout direction); [splitRows] leaves
 * a gap between the top and bottom rows of the edge positions.
 * zh-CN: [positions] 的格子, 每行三个, 按页面方向排列 (不随布局方向镜像); [splitRows] 在上下边缘两行之间留出间隔.
 */
@Composable
private fun PositionGrid(positions: List<String>, selected: String, tag: String, splitRows: Boolean = false, onSelect: (String) -> Unit) {
    val names = POSITION_NAMES.mapValues { stringResource(it.value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(6.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    positions.chunked(GRID_COLUMNS).forEachIndexed { row, cells ->
                        if (splitRows && row > 0) Spacer(Modifier.size(GRID_CELL))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cells.forEach { position ->
                                val on = position == selected
                                Box(
                                    modifier = Modifier
                                        .size(GRID_CELL)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .selectable(selected = on, onClick = { onSelect(position) }, role = Role.RadioButton)
                                        .semantics { contentDescription = names[position].orEmpty() }
                                        .testTag("$tag-$position"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Icon(painterResource(R.drawable.ic_check_24), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(names[selected].orEmpty(), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AdvancedRow(open: Boolean, tag: String, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = open, onValueChange = onToggle)
            .padding(vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.tool_advanced), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Icon(painterResource(R.drawable.ic_chevron_right_24), contentDescription = null, modifier = Modifier.rotate(if (open) COLLAPSE_DEGREES else EXPANDED_DEGREES))
    }
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, tag: String, @StringRes note: Int? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Label(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Note(@StringRes text: Int, vararg args: Any, tag: String? = null) {
    Text(
        text = stringResource(text, *args),
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Warning(@StringRes text: Int, tag: String, vararg args: Any) {
    Text(stringResource(text, *args), modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Loading(@StringRes text: Int, tag: String) {
    Row(modifier = Modifier.testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

private const val PERCENT = 100.0

private const val START_DIGITS = 7

private const val MULTILINE_ROWS = 3

/** More choices than this go into a menu. zh-CN: 选项多于此数时放进菜单. */
private const val MAX_CHOICE_CHIPS = 8

private const val GRID_COLUMNS = 3

private const val EXPANDED_DEGREES = 90f

private const val COLLAPSE_DEGREES = 270f

private const val MAX_KEY_SHARE = 0.45f

private const val MAX_OUTLINE_INDENT = 6

private val GRID_CELL = 36.dp

private val KEY_GAP = 16.dp

private val ROW_GAP = 6.dp

private val OUTLINE_INDENT = 16.dp

private val PREVIEW_MIN_HEIGHT = 120.dp

private val PREVIEW_MAX_WIDTH = 360.dp

private val COLOR_NAMES = listOf(R.string.tool_color_gray, R.string.tool_color_red, R.string.tool_color_blue, R.string.tool_color_black)

private val PRESET_NAMES = listOf(R.string.tool_compress_low, R.string.tool_compress_medium, R.string.tool_compress_high)

private val INFO_LABELS = mapOf(
    PdfContract.FIELD_TITLE to R.string.tool_info_title,
    PdfContract.FIELD_AUTHOR to R.string.tool_info_author,
    PdfContract.FIELD_SUBJECT to R.string.tool_info_subject,
    PdfContract.FIELD_KEYWORDS to R.string.tool_info_keywords,
)

private val POSITION_NAMES = mapOf(
    PdfContract.POSITION_TOP_LEFT to R.string.tool_position_top_left,
    PdfContract.POSITION_TOP_CENTER to R.string.tool_position_top_center,
    PdfContract.POSITION_TOP_RIGHT to R.string.tool_position_top_right,
    PdfContract.POSITION_CENTER_LEFT to R.string.tool_position_center_left,
    PdfContract.POSITION_CENTER to R.string.tool_position_center,
    PdfContract.POSITION_CENTER_RIGHT to R.string.tool_position_center_right,
    PdfContract.POSITION_BOTTOM_LEFT to R.string.tool_position_bottom_left,
    PdfContract.POSITION_BOTTOM_CENTER to R.string.tool_position_bottom_center,
    PdfContract.POSITION_BOTTOM_RIGHT to R.string.tool_position_bottom_right,
)

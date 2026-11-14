package io.github.supermonster003.autojs6.plugin.three.paper.pdf.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.supermonster003.autojs6.plugin.three.paper.pdf.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The viewer screen (roadmap P3.1): the page canvas under a top bar (title, search, outline, menu with night mode,
 * share and open with), a page indicator that opens the jump dialog, a thumbnail strip, the outline panel and the
 * password prompt. A single tap on the pages hides or shows the bars.
 * zh-CN: 查看器界面 (路线图 P3.1): 页面画布, 上方是顶栏 (标题, 搜索, 大纲, 菜单含夜间模式, 分享与用其他应用打开), 页码指示 (点按打开
 * 跳页对话框), 缩略图栏, 大纲面板与密码提示. 单击页面隐藏或显示工具栏.
 */
@Composable
internal fun ViewerScreen(model: ViewerViewModel, onClose: () -> Unit, onShare: () -> Unit, onOpenWith: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(colors.surfaceVariant)) {
        when (val phase = model.phase) {
            ViewerViewModel.Phase.Loading -> Loading()
            is ViewerViewModel.Phase.Password -> PasswordDialog(model.displayName, phase.wrong, onSubmit = model::submitPassword, onDismiss = onClose)
            is ViewerViewModel.Phase.Failed -> Failure(phase.message, onClose)
            ViewerViewModel.Phase.Ready -> Ready(model, onClose, onShare, onOpenWith)
        }
    }
}

@Composable
private fun Ready(model: ViewerViewModel, onClose: () -> Unit, onShare: () -> Unit, onOpenWith: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bars = ViewerBarColors.of(colors)
    BackHandler(enabled = model.outlineOpen || model.searchOpen || model.jumpOpen) {
        when {
            model.jumpOpen -> model.jumpOpen = false
            model.outlineOpen -> model.outlineOpen = false
            else -> model.closeSearch()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        ViewerCanvas(model, background = colors.surfaceVariant, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(visible = model.chromeVisible || model.searchOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = bars.container, contentColor = bars.content, shadowElevation = 2.dp) {
                if (model.searchOpen) SearchBar(model) else TopBar(model, onClose, onShare, onOpenWith)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageIndicator(model)
            AnimatedVisibility(visible = model.chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                Surface(color = bars.container, contentColor = bars.content, shadowElevation = 2.dp) {
                    ThumbnailStrip(model, modifier = Modifier.navigationBarsPadding())
                }
            }
            if (!model.chromeVisible) Spacer(modifier = Modifier.navigationBarsPadding())
        }
        AnimatedVisibility(visible = model.outlineOpen, enter = fadeIn(), exit = fadeOut()) {
            OutlinePanel(model)
        }
        if (model.jumpOpen) JumpDialog(model)
    }
}

@Composable
private fun TopBar(model: ViewerViewModel, onClose: () -> Unit, onShare: () -> Unit, onOpenWith: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(BAR_HEIGHT).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(ViewerIcons.Back, contentDescription = stringResource(R.string.viewer_back)) }
        Text(
            text = model.displayName,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = model::openSearch, modifier = Modifier.testTag("viewer-search")) {
            Icon(ViewerIcons.Search, contentDescription = stringResource(R.string.viewer_search))
        }
        IconButton(onClick = { model.outlineOpen = true }, modifier = Modifier.testTag("viewer-outline")) {
            Icon(ViewerIcons.Outline, contentDescription = stringResource(R.string.viewer_outline))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(ViewerIcons.More, contentDescription = stringResource(R.string.viewer_more)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_night_mode)) },
                    trailingIcon = { Checkbox(checked = model.nightMode, onCheckedChange = null) },
                    onClick = {
                        menuOpen = false
                        model.toggleNightMode()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_share)) },
                    onClick = {
                        menuOpen = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_open_with)) },
                    onClick = {
                        menuOpen = false
                        onOpenWith()
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(model: ViewerViewModel) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (model.hits.isEmpty()) focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(BAR_HEIGHT).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = model::closeSearch) { Icon(ViewerIcons.Close, contentDescription = stringResource(R.string.viewer_search_close)) }
        val hint = stringResource(R.string.viewer_search_hint)
        BasicTextField(
            value = model.searchQuery,
            onValueChange = { model.searchQuery = it },
            modifier = Modifier.weight(1f).focusRequester(focus).testTag("viewer-search-field").semantics { contentDescription = hint },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    model.search()
                },
            ),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (model.searchQuery.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    field()
                }
            },
        )
        val status = when {
            model.hits.isNotEmpty() -> stringResource(
                if (model.searchTruncated) R.string.viewer_search_count_limited else R.string.viewer_search_count,
                model.hitIndex + 1,
                model.hits.size,
            )
            model.searching -> stringResource(R.string.viewer_search_running)
            model.lastKeyword != null -> stringResource(R.string.viewer_search_none)
            else -> ""
        }
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 4.dp).testTag("viewer-search-status"),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        IconButton(onClick = { model.showHit(model.hitIndex - 1) }, enabled = model.hits.isNotEmpty()) {
            Icon(ViewerIcons.Up, contentDescription = stringResource(R.string.viewer_search_previous))
        }
        IconButton(onClick = { model.showHit(model.hitIndex + 1) }, enabled = model.hits.isNotEmpty()) {
            Icon(ViewerIcons.Down, contentDescription = stringResource(R.string.viewer_search_next))
        }
    }
}

@Composable
private fun PageIndicator(model: ViewerViewModel) {
    val count = model.document?.pageCount ?: return
    Surface(
        modifier = Modifier.padding(bottom = 8.dp).testTag("viewer-page-indicator").clickable { model.jumpOpen = true },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text = stringResource(R.string.viewer_page_indicator, model.currentPage + 1, count),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ThumbnailStrip(model: ViewerViewModel, modifier: Modifier = Modifier) {
    val count = model.document?.pageCount ?: return
    val state = rememberLazyListState(initialFirstVisibleItemIndex = max(0, model.currentPage - 2))
    val width = with(LocalDensity.current) { THUMBNAIL_WIDTH.toPx() }
    SideEffect { model.thumbnailWidth = width }
    LaunchedEffect(state) {
        snapshotFlow {
            val items = state.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) IntRange.EMPTY else items.first().index..items.last().index
        }.distinctUntilChanged().collect { range ->
            model.thumbnailPages = range
            model.plan()
        }
    }
    LaunchedEffect(model.currentPage) {
        val items = state.layoutInfo.visibleItemsInfo
        if (items.isNotEmpty() && model.currentPage !in items.first().index + 1 until items.last().index) {
            state.animateScrollToItem(max(0, model.currentPage - 2))
        }
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().height(THUMBNAIL_BOX + 16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(count = count) { page -> Thumbnail(model, page) }
    }
}

@Composable
private fun Thumbnail(model: ViewerViewModel, page: Int) {
    val colors = MaterialTheme.colorScheme
    val selected = page == model.currentPage
    val description = stringResource(R.string.viewer_page_thumbnail, page + 1)
    val filter = remember { ColorFilter.colorMatrix(ColorMatrix(NIGHT_MATRIX)) }
    Canvas(
        modifier = Modifier
            .size(THUMBNAIL_WIDTH, THUMBNAIL_BOX)
            .semantics { contentDescription = description }
            .clickable { model.goToPage(page) },
    ) {
        model.renderVersion
        val layout = model.layout ?: return@Canvas
        val aspect = layout.pageHeight(page, 1f) / layout.pageWidth(1f)
        var w = size.width
        var h = w * aspect
        if (h > size.height) {
            h = size.height
            w = h / aspect
        }
        val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
        drawRect(if (model.nightMode) Color(0xFF101010) else Color.White, topLeft, Size(w, h))
        val key = RenderPlan.thumbnail(layout, page, model.thumbnailWidth.takeIf { it > 0f } ?: size.width)
        model.renderer?.get(key)?.let { bitmap ->
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                colorFilter = if (model.nightMode) filter else null,
            )
        }
        drawRect(
            color = if (selected) colors.primary else colors.outline,
            topLeft = topLeft,
            size = Size(w, h),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (selected) 2.dp else 0.5.dp).toPx()),
        )
    }
}

@Composable
private fun OutlinePanel(model: ViewerViewModel) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { model.outlineOpen = false },
        )
        Surface(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(OUTLINE_WIDTH_FRACTION).widthIn(max = 380.dp).testTag("viewer-outline-panel"),
            color = colors.surface,
            tonalElevation = 1.dp,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
                Text(
                    text = stringResource(R.string.viewer_outline),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                val entries = model.outline
                when {
                    entries == null -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    entries.isEmpty() -> Text(
                        text = stringResource(R.string.viewer_outline_empty),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    else -> LazyColumn {
                        items(entries) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = entry.page > 0) {
                                        model.outlineOpen = false
                                        model.goToPage(entry.page - 1)
                                    }
                                    .padding(start = 20.dp + OUTLINE_INDENT * min(entry.level, MAX_INDENT_LEVEL), end = 20.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry.title,
                                    modifier = Modifier.weight(1f),
                                    style = if (entry.level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.page > 0) {
                                    Text(
                                        text = entry.page.toString(),
                                        modifier = Modifier.padding(start = 12.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpDialog(model: ViewerViewModel) {
    val count = model.document?.pageCount ?: return
    var text by rememberSaveable { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..count }
    val submit = {
        if (page != null) {
            model.jumpOpen = false
            model.goToPage(page - 1)
        }
    }
    AlertDialog(
        onDismissRequest = { model.jumpOpen = false },
        title = { Text(stringResource(R.string.viewer_jump_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(9) },
                modifier = Modifier.testTag("viewer-jump-field"),
                label = { Text(stringResource(R.string.viewer_jump_hint, count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = page != null, modifier = Modifier.testTag("viewer-jump-go")) {
                Text(stringResource(R.string.viewer_jump_go))
            }
        },
        dismissButton = { TextButton(onClick = { model.jumpOpen = false }) { Text(stringResource(R.string.viewer_cancel)) } },
    )
}

@Composable
private fun PasswordDialog(displayName: String, wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.viewer_password_message, displayName))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.testTag("viewer-password-field"),
                    label = { Text(stringResource(R.string.viewer_password_hint)) },
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(R.string.viewer_password_wrong)) }) else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onSubmit(password) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.viewer_password_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_cancel)) } },
    )
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.viewer_loading),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Failure(message: Int, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.viewer_error_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(message), modifier = Modifier.testTag("viewer-error"), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClose) { Text(text = stringResource(R.string.viewer_close)) }
    }
}

/**
 * Colors of the translucent top bar and thumbnail bar. `Surface` derives its content color only from a color equal to a
 * scheme role, and a translucent surface equals none, so the content would fall back to black and be unreadable on the dark
 * theme; the content color is therefore given explicitly.
 * zh-CN: 半透明顶栏与缩略图栏的颜色. `Surface` 只能从与配色方案角色相等的颜色推出内容色, 半透明表面与任何角色都不相等, 内容色会退回黑色,
 * 在深色主题下看不清; 因此显式给出内容色.
 */
internal data class ViewerBarColors(val container: Color, val content: Color) {
    companion object {
        fun of(scheme: ColorScheme) = ViewerBarColors(scheme.surface.copy(alpha = BAR_ALPHA), scheme.onSurface)
    }
}

private val BAR_HEIGHT = 56.dp
private const val BAR_ALPHA = 0.96f
private val THUMBNAIL_WIDTH = 44.dp
private val THUMBNAIL_BOX = 60.dp
private val OUTLINE_INDENT = 16.dp
private const val MAX_INDENT_LEVEL = 6
private const val OUTLINE_WIDTH_FRACTION = 0.86f

/** Same colors as the page canvas (see `PagePainter`). zh-CN: 与页面画布相同的颜色 (见 `PagePainter`). */
private val NIGHT_MATRIX = floatArrayOf(
    -0.88f, 0f, 0f, 0f, 240f,
    0f, -0.88f, 0f, 0f, 240f,
    0f, 0f, -0.88f, 0f, 240f,
    0f, 0f, 0f, 1f, 0f,
)

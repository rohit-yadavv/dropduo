package app.dropduo.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.ThumbnailUtils
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Actions(val scan: () -> Unit, val connect: () -> Unit, val files: () -> Unit, val save: (Transfer) -> Unit, val open: (Transfer) -> Unit, val installUpdate: () -> Unit)

private const val UNPAIRED_STATUS = "Pair your Mac to get started"
private val ACTIVE = listOf("Preparing", "Sending", "Receiving")
private enum class Screen { Pairing, Home, Settings }

@Composable fun DropDuoApp(actions: Actions) {
    val state by AppState.ui.collectAsState()
    var settings by rememberSaveable { mutableStateOf(false) }
    var codeSheet by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Clear the error first so the same message can be shown again; the snackbar outlives this effect.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        AppState.update { it.copy(error = null) }
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message, duration = SnackbarDuration.Long) }
    }
    LaunchedEffect(state.device) { if (state.device == null) settings = false }
    BackHandler(enabled = settings) { settings = false }
    DropDuoTheme {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            val screen = when { state.device == null -> Screen.Pairing; settings -> Screen.Settings; else -> Screen.Home }
            AnimatedContent(screen, label = "screen", transitionSpec = {
                // Settings pushes in from the side like a page; everything else crossfades.
                val forward = targetState == Screen.Settings
                if (initialState == Screen.Pairing || targetState == Screen.Pairing) fadeIn(tween(260)) togetherWith fadeOut(tween(160))
                else (slideInHorizontally(tween(340, easing = EaseOutQuart)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(340, easing = EaseOutQuart)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            }) { target ->
                when (target) {
                    Screen.Pairing -> Pairing(state.status, actions.scan, actions.installUpdate) { codeSheet = true }
                    Screen.Home -> Home(state, actions) { settings = true }
                    Screen.Settings -> Settings(state, actions, onBack = { settings = false }) { codeSheet = true }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { data ->
                Surface(color = colors.solid, contentColor = colors.onSolid, shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(data.visuals.message, style = Type.body, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
                }
            }
        }
        if (codeSheet) PairCodeSheet(onPair = { AppState.pair(it); actions.connect() }) { codeSheet = false }
    }
}

// Pairing

@Composable private fun Pairing(status: String, onScan: () -> Unit, onInstall: () -> Unit, onCode: () -> Unit) {
    // "Disconnected" arrives from the stopping service after Forget; it isn't a pairing problem.
    val problem = status.takeUnless { it == UNPAIRED_STATUS || it.startsWith("Disconnected") }
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Brand(Modifier.padding(top = 14.dp))
            Column(Modifier.padding(vertical = 32.dp)) {
                DuoLink(Link.Searching, flow = 0, tile = 64.dp, onHero = false, mark = true)
                Spacer(Modifier.height(44.dp))
                Text("Pair with your Mac", style = Type.display, color = colors.text)
                Spacer(Modifier.height(12.dp))
                Text("Open DropDuo on your Mac to show its pairing code.", style = Type.body, color = colors.secondary)
                AnimatedVisibility(problem != null) {
                    Notice(problem.orEmpty(), R.drawable.ic_alert, colors.danger, colors.dangerSoft, Modifier.padding(top = 24.dp))
                }
            }
            Column(Modifier.padding(bottom = 12.dp)) {
                UpdateCard(onInstall, Modifier.padding(bottom = 16.dp))
                PrimaryButton("Scan pairing code", R.drawable.ic_scan, onClick = onScan)
                Spacer(Modifier.height(4.dp))
                QuietButton("Enter code instead", onCode)
                Text("Both devices need to be on the same Wi-Fi.", style = Type.caption, color = colors.tertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

// Home

@Composable private fun Home(state: UiState, actions: Actions, onSettings: () -> Unit) {
    var textSheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val days = remember(state.history) { state.history.groupBy { startOfDay(it.time) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 32.dp)) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Glyph(R.drawable.ic_settings, "Settings", colors.text) }
            }
        }
        item(key = "update") { UpdateCard(actions.installUpdate, Modifier.padding(bottom = 12.dp)) }
        item(key = "device") { DeviceCard(state, actions.connect) }
        item(key = "send") { SendActions(actions.files) { textSheet = true } }
        item(key = "recent") { Text("Recent", style = Type.title, color = colors.text, modifier = Modifier.padding(start = 6.dp, top = 32.dp)) }
        if (state.history.isEmpty()) item(key = "empty") { EmptyRecent() }
        days.forEach { (day, rows) ->
            item(key = "day-$day") { Text(dayLabel(context, day), style = Type.captionStrong, color = colors.secondary, modifier = Modifier.animateItem().padding(start = 6.dp, top = 18.dp, bottom = 8.dp)) }
            itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                TransferRow(row, first = index == 0, last = index == rows.lastIndex, Modifier.animateItem()) { selected = row.id }
            }
        }
    }
    if (textSheet) TextSheet(state.connected) { textSheet = false }
    state.history.firstOrNull { it.id == selected }?.let { row -> TransferSheet(row, actions) { selected = null } }
}

@Composable private fun DeviceCard(state: UiState, onConnect: () -> Unit) {
    val pending = !state.connected && state.status.endsWith("…")
    val active = state.history.firstOrNull { it.state in ACTIVE }.takeIf { state.connected }
    val link = when { state.connected -> Link.On; pending -> Link.Searching; else -> Link.Off }
    val label = when {
        active != null -> if (active.state == "Preparing") "Preparing…" else "${active.state} ${percent(active)}%"
        state.connected -> "Connected"
        state.status == UNPAIRED_STATUS -> "Not connected"
        else -> state.status
    }
    val dot by animateColorAsState(if (state.connected) colors.positive else if (pending) colors.pending else colors.heroSecondary, label = "dot")
    Surface(color = colors.hero, contentColor = colors.onHero, shape = RoundedCornerShape(28.dp), border = if (colors.dark) BorderStroke(1.dp, colors.hairline) else null) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            DuoLink(link, flow = when { active == null -> 0; active.direction == "Sent" -> 1; else -> -1 }, tile = 44.dp, onHero = true)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.device.orEmpty(), style = Type.title, color = colors.onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        StatusDot(dot, pulse = pending, Modifier.padding(top = 5.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = Type.caption, color = colors.heroSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!state.connected && !pending) {
                    Spacer(Modifier.width(12.dp))
                    TapSurface(onConnect, shape = CircleShape, color = colors.onHero, contentColor = colors.hero) {
                        Text("Connect", style = Type.bodyStrong, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun SendActions(onFiles: () -> Unit, onText: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val stacked = maxWidth < 360.dp || largeText
        @Composable fun Files(modifier: Modifier) {
            TapSurface(onFiles, modifier, RoundedCornerShape(24.dp), colors.accent, colors.onAccent) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(colors.onAccent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(R.drawable.ic_plus, null, colors.onAccent, size = 22.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Send files", style = Type.headline, color = colors.onAccent, modifier = Modifier.weight(1f))
                }
            }
        }
        @Composable fun TextAction(modifier: Modifier) {
            TapSurface(onText, modifier, RoundedCornerShape(24.dp), colors.surface, colors.text) {
                if (stacked) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(R.drawable.ic_text, null, colors.text, size = 22.dp)
                        Spacer(Modifier.width(22.dp))
                        Text("Send text", style = Type.headline, color = colors.text)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                        Glyph(R.drawable.ic_text, null, colors.text, size = 22.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Text", style = Type.headline, color = colors.text)
                    }
                }
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Files(Modifier.fillMaxWidth().heightIn(min = 88.dp))
                TextAction(Modifier.fillMaxWidth().heightIn(min = 88.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Files(Modifier.weight(1.7f).heightIn(min = 88.dp))
                TextAction(Modifier.weight(1f).heightIn(min = 88.dp))
            }
        }
    }
}

@Composable private fun EmptyRecent() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(colors.surface, RoundedCornerShape(24.dp)).padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            listOf(R.drawable.ic_image to -10f, R.drawable.ic_file to 0f, R.drawable.ic_link to 10f).forEachIndexed { i, (icon, angle) ->
                Box(Modifier.padding(top = if (i == 1) 0.dp else 6.dp).rotate(angle).size(44.dp).background(colors.surfaceHigh, RoundedCornerShape(14.dp))
                    .border(2.dp, colors.surface, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Glyph(icon, null, if (i == 1) colors.accentText else colors.secondary, size = 20.dp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Nothing shared yet", style = Type.headline, color = colors.text)
    }
}

@Composable private fun TransferRow(row: Transfer, first: Boolean, last: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val active = row.state in ACTIVE
    Row(modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 2.dp).clip(segment(first, last)).background(colors.surface).clickable(onClick = onClick)
        .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(row, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title(row), style = Type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            AnimatedVisibility(active) {
                val progress by animateFloatAsState(row.progress.toFloat().coerceIn(0f, 1f), label = "progress")
                LinearProgressIndicator(progress = { progress }, color = colors.accent, trackColor = colors.surfaceHigh, gapSize = 0.dp, drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 6.dp).height(4.dp).clip(CircleShape))
            }
            StatusLine(row)
        }
        when {
            active -> IconButton(onClick = { AppState.cancel(row) }) { Glyph(R.drawable.ic_close, "Cancel transfer", colors.secondary, size = 20.dp) }
            canRetry(row) -> IconButton(onClick = { AppState.retry(row) }) { Glyph(R.drawable.ic_retry, "Retry", colors.accentText, size = 20.dp) }
            else -> Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable private fun StatusLine(row: Transfer, maxLines: Int = 1) {
    val context = LocalContext.current
    val tone = tone(row)
    val color = when (tone) { Tone.Waiting -> colors.pending; Tone.Problem -> colors.danger; Tone.Normal -> colors.secondary }
    val icon = when (tone) {
        Tone.Waiting -> R.drawable.ic_clock
        Tone.Problem -> R.drawable.ic_alert
        Tone.Normal -> if (row.direction == "Sent") R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
    }
    Row {
        Glyph(icon, null, if (tone == Tone.Normal) colors.tertiary else color, Modifier.padding(top = 2.dp), size = 14.dp)
        Spacer(Modifier.width(5.dp))
        Text(statusText(context, row), style = Type.caption, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

// The phone and the Mac

private enum class Link { On, Searching, Off }

/** This phone and the Mac joined by a dotted line. It turns cobalt when connected and runs toward whichever side is receiving. */
@Composable private fun DuoLink(link: Link, flow: Int, tile: Dp, onHero: Boolean, modifier: Modifier = Modifier, mark: Boolean = false) {
    val idle = if (onHero) colors.heroLine else colors.tertiary.copy(alpha = 0.55f)
    val lit = if (onHero) colors.heroAccent else colors.accent
    val line by animateColorAsState(if (link == Link.On) lit else idle, tween(400), label = "line")
    val moving = link == Link.Searching || flow != 0
    val period = with(LocalDensity.current) { 10.dp.toPx() }
    val offset = if (moving) rememberInfiniteTransition(label = "link")
        .animateFloat(0f, period, infiniteRepeatable(tween(if (link == Link.Searching) 1100 else 420, easing = LinearEasing)), label = "phase").value else 0f
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun RowScope.Dots() = Canvas(Modifier.weight(1f).height(tile).padding(horizontal = 12.dp)) {
            val y = size.height / 2
            // Dash phase grows toward the start of the line, so negate it to run left to right.
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, period), if (flow < 0) offset else -offset))
        }
        DeviceTile(R.drawable.ic_phone, "This phone", tile, onHero, lit = false)
        Dots()
        if (mark) {
            Box(Modifier.size(tile * 0.7f).shadow(if (colors.dark) 0.dp else 6.dp, CircleShape).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_dropduo), null, Modifier.size(tile * 0.42f))
            }
            Dots()
        }
        DeviceTile(R.drawable.ic_laptop, "Mac", tile, onHero, lit = link == Link.On)
    }
}

@Composable private fun DeviceTile(icon: Int, description: String, size: Dp, onHero: Boolean, lit: Boolean) {
    val shape = RoundedCornerShape(size * 0.32f)
    val edge by animateColorAsState(when { lit -> colors.heroAccent.copy(alpha = 0.7f); onHero -> Color.Transparent; else -> colors.hairline }, label = "tile")
    Box(Modifier.size(size).background(if (onHero) colors.heroTile else colors.surface, shape).border(1.5.dp, edge, shape).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Glyph(icon, null, if (onHero) colors.onHero else colors.text, size = size * 0.48f)
    }
}

@Composable private fun StatusDot(color: Color, pulse: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (pulse) rememberInfiniteTransition(label = "pulse")
        .animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha").value else 1f
    Box(modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
}

// Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface,
        contentColor = colors.text, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 18.dp).size(36.dp, 4.dp).background(colors.hairline, CircleShape))
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 20.dp), content = content)
    }
}

@Composable private fun TextSheet(connected: Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Sheet(onDismiss) {
        Text("Send text", style = Type.title, color = colors.text)
        if (!connected) {
            Spacer(Modifier.height(8.dp))
            Text("Sends when your Mac reconnects.", style = Type.caption, color = colors.pending)
        }
        Spacer(Modifier.height(20.dp))
        Field(text, { text = it }, "Paste a link or type a note", minLines = 4)
        PasteChip(visible = text.isEmpty()) { text = it }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Send", R.drawable.ic_arrow_up, enabled = text.isNotBlank()) { AppState.sendText(text) { text = ""; onDismiss() } }
    }
}

@Composable private fun PairCodeSheet(onPair: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Sheet(onDismiss) {
        Text("Enter pairing code", style = Type.title, color = colors.text)
        Spacer(Modifier.height(4.dp))
        Text("Copy the code from DropDuo on your Mac.", style = Type.caption, color = colors.secondary)
        Spacer(Modifier.height(20.dp))
        Field(code, { code = it }, "Pairing code", minLines = 3, monospace = true)
        PasteChip(visible = code.isEmpty()) { code = it }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Pair", enabled = code.isNotBlank()) { onPair(code); onDismiss() }
    }
}

@Composable private fun TransferSheet(row: Transfer, actions: Actions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preview = rememberThumbnail(row, 720)
    val link = row.text?.let(::linkOf)
    Sheet(onDismiss) {
        if (preview != null) {
            Image(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth()
                .aspectRatio((preview.width.toFloat() / preview.height).coerceIn(1.1f, 2f)).clip(RoundedCornerShape(20.dp)).background(colors.surfaceHigh))
            Spacer(Modifier.height(18.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (preview == null) { Thumb(row, 52.dp); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(when { link != null -> "Link"; row.text != null -> "Text"; else -> row.name }, style = Type.headline, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                StatusLine(row, maxLines = 3)
            }
        }
        row.text?.let { text ->
            Spacer(Modifier.height(18.dp))
            Surface(color = colors.surfaceHigh, shape = RoundedCornerShape(18.dp)) {
                SelectionContainer(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = Type.body, color = if (link != null) colors.accentText else colors.text, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
        fun act(block: () -> Unit) = { block(); onDismiss() }
        val buttons = buildList {
            link?.let { uri -> add(SheetButton(R.drawable.ic_open, "Open link", act {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { AppState.error("No app on this phone can open that link.") }
            })) }
            row.text?.let { text -> add(SheetButton(R.drawable.ic_copy, "Copy", act { copy(context, text) })) }
            if (row.state == "Complete" && row.direction == "Received" && row.path != null) {
                // No app can open a folder in DropDuo's storage, so folders are saved somewhere first.
                if (!row.folder) add(SheetButton(R.drawable.ic_open, "Open", act { actions.open(row) }))
                add(SheetButton(R.drawable.ic_save, if (row.folder) "Save to a folder" else "Save a copy", act { actions.save(row) }))
            }
            if (canRetry(row)) add(0, SheetButton(R.drawable.ic_retry, "Retry now", act { AppState.retry(row) }))
        }
        if (buttons.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            val largeText = LocalDensity.current.fontScale > 1.2f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (buttons.size > 2 || maxWidth < 320.dp || largeText) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        buttons.forEachIndexed { i, button -> ActionButton(button, primary = i == 0, Modifier.fillMaxWidth()) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        buttons.forEachIndexed { i, button -> ActionButton(button, primary = i == 0, Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (row.state in ACTIVE || (canRetry(row) && row.autoRetry)) {
            Spacer(Modifier.height(if (buttons.isEmpty()) 20.dp else 6.dp))
            QuietButton(if (row.state in ACTIVE) "Cancel transfer" else "Don't send", act { AppState.cancel(row) }, colors.danger)
        }
    }
}

private class SheetButton(val icon: Int, val label: String, val onClick: () -> Unit)

@Composable private fun ActionButton(button: SheetButton, primary: Boolean, modifier: Modifier) {
    TapSurface(button.onClick, modifier.heightIn(min = 56.dp), RoundedCornerShape(18.dp), if (primary) colors.accent else colors.surfaceHigh, if (primary) colors.onAccent else colors.text) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(button.icon), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(button.label, style = Type.bodyStrong)
        }
    }
}

@Composable private fun PasteChip(visible: Boolean, onPaste: (String) -> Unit) {
    val context = LocalContext.current
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        TapSurface({ clipboardText(context)?.let(onPaste) ?: AppState.error("The clipboard is empty.") }, Modifier.padding(top = 10.dp), CircleShape, colors.surfaceHigh, colors.text) {
            Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_paste, null, colors.secondary, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Paste", style = Type.captionStrong, color = colors.text)
            }
        }
    }
}

@Composable private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int, monospace: Boolean = false) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val edge by animateColorAsState(if (focused) colors.accent else Color.Transparent, label = "focus")
    val style = Type.body.copy(color = colors.text, fontFamily = if (monospace) FontFamily.Monospace else Geist)
    BasicTextField(value, onChange, textStyle = style, minLines = minLines, maxLines = 8, cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = placeholder }, decorationBox = { inner ->
            Box(Modifier.background(colors.surfaceHigh, RoundedCornerShape(18.dp)).border(1.5.dp, edge, RoundedCornerShape(18.dp)).padding(16.dp)) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.tertiary, fontFamily = Geist))
                inner()
            }
        })
}

// Settings

@Composable private fun Settings(state: UiState, actions: Actions, onBack: () -> Unit, onCode: () -> Unit) {
    var confirmForget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val running = state.connected || state.status.endsWith("…")
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Glyph(R.drawable.ic_back, "Back", colors.text) }
        Text("Settings", style = Type.display, color = colors.text, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 22.dp))
        Row(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(colors.surfaceHigh, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Glyph(R.drawable.ic_laptop, null, colors.text) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.device.orEmpty(), style = Type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when { state.connected -> "Connected"; running -> "Connecting…"; else -> "Not connected" }, style = Type.caption, color = colors.secondary)
            }
            Spacer(Modifier.width(10.dp))
            TapSurface({ if (running) AppState.stop(context) else actions.connect() }, shape = CircleShape, color = colors.surfaceHigh, contentColor = colors.text) {
                Text(if (running) "Disconnect" else "Connect", style = Type.captionStrong, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        SectionTitle("Sharing")
        Group({
            GroupRow("Accept files and text", it) {
                Switch(checked = state.receiving, onCheckedChange = AppState::setReceiving, colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent, checkedBorderColor = Color.Transparent,
                    uncheckedTrackColor = colors.surfaceHigh, uncheckedThumbColor = colors.tertiary, uncheckedBorderColor = colors.tertiary.copy(alpha = 0.5f)))
            }
        })
        SectionTitle("Pairing")
        Group(
            { GroupRow("Scan a new pairing code", it, icon = R.drawable.ic_scan, opens = true, onClick = actions.scan) },
            { GroupRow("Enter a pairing code", it, icon = R.drawable.ic_keyboard, opens = true, onClick = onCode) },
        )
        SectionTitle("Storage")
        Group({ GroupRow("Clear recent history", it, if (state.history.isEmpty()) "Nothing to clear" else "Received files are kept", icon = R.drawable.ic_trash,
            onClick = AppState::clearHistory.takeIf { state.history.isNotEmpty() }) })
        Text("Use Save a copy to keep files after uninstalling DropDuo.",
            style = Type.caption, color = colors.secondary, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp))
        SectionTitle("Updates")
        UpdateCard(actions.installUpdate, alwaysVisible = true)
        Spacer(Modifier.height(28.dp))
        Group({ GroupRow("Forget this Mac", it, danger = true, onClick = { confirmForget = true }) })
    }
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, containerColor = colors.surface, shape = RoundedCornerShape(28.dp),
        title = { Text("Forget ${state.device ?: "this Mac"}?", style = Type.title, color = colors.text) },
        text = { Text("You'll need to pair again to share. Received files stay on this phone.", style = Type.body, color = colors.secondary) },
        confirmButton = { TextButton(onClick = { AppState.forget(context); confirmForget = false }) { Text("Forget", color = colors.danger, style = Type.bodyStrong) } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel", color = colors.text, style = Type.bodyStrong) } })
}

/** Settings shows this always; elsewhere it appears only while an update is available, downloading or ready. */
@Composable private fun UpdateCard(onInstall: () -> Unit, modifier: Modifier = Modifier, alwaysVisible: Boolean = false) {
    val update by AppUpdater.ui.collectAsState()
    val state by AppState.ui.collectAsState()
    var notes by rememberSaveable { mutableStateOf(false) }
    if (!alwaysVisible && (update.release == null || (update.dismissed && !update.downloading && !update.ready))) return
    val active = state.history.any { it.state in ACTIVE }
    val banner = !alwaysVisible
    val title = if (banner) "Update available" else "DropDuo ${BuildConfig.VERSION_NAME}"
    val status = when {
        update.downloading -> "Downloading · ${(update.progress * 100).toInt()}%"
        update.checking -> "Checking…"
        update.ready && active -> "Installs when transfers finish"
        update.ready -> "Ready to install"
        update.release != null -> "Version ${update.release?.version}"
        update.message == "You're up to date." -> "Up to date"
        else -> null
    }
    Surface(color = if (banner) colors.accentSoft else colors.surface, shape = RoundedCornerShape(22.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (banner) {
                    Box(Modifier.size(34.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) { Glyph(R.drawable.ic_arrow_down, null, colors.onAccent, size = 18.dp) }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = Type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    status?.let { Text(it, style = Type.caption, color = colors.secondary) }
                }
                if (update.downloading) {
                    TextButton(onClick = AppUpdater::cancel, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Cancel", color = colors.text) }
                } else if (update.release != null) {
                    TextButton(onClick = { notes = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.semantics { contentDescription = "What's new" }) {
                        Text("Details", color = colors.secondary, style = Type.caption)
                    }
                    val enabled = !update.checking && (!update.ready || !active)
                    TapSurface({ if (update.ready) onInstall() else AppUpdater.download() }, shape = CircleShape, color = if (enabled) colors.accent else colors.surfaceHigh,
                        contentColor = if (enabled) colors.onAccent else colors.tertiary, enabled = enabled) {
                        Text(if (update.ready) "Install" else "Update", style = Type.captionStrong, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                    if (banner && !update.ready) IconButton(onClick = AppUpdater::dismiss, modifier = Modifier.size(40.dp)) { Glyph(R.drawable.ic_close, "Later", colors.secondary, size = 16.dp) }
                    else Spacer(Modifier.width(8.dp))
                } else if (alwaysVisible) {
                    TextButton(onClick = { AppUpdater.check(manual = true) }, enabled = !update.checking, contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.semantics { contentDescription = "Check for updates" }) { Text("Check", color = colors.accentText, style = Type.bodyStrong) }
                }
            }
            if (update.downloading) {
                val progress by animateFloatAsState(update.progress.coerceIn(0f, 1f), label = "download")
                LinearProgressIndicator(progress = { progress }, color = colors.accent, trackColor = if (banner) colors.surface else colors.surfaceHigh, gapSize = 0.dp, drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 6.dp).height(4.dp).clip(CircleShape))
            }
            update.message?.takeUnless { it == "You're up to date." }?.let {
                Text(it, style = Type.caption, color = colors.secondary, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
            }
        }
    }
    if (notes) Sheet(onDismiss = { notes = false }) {
        Text("What's new in ${update.release?.version}", style = Type.title, color = colors.text)
        Spacer(Modifier.height(16.dp))
        SelectionContainer { Text(update.release?.notes?.ifBlank { "No release notes were provided." }.orEmpty(), style = Type.body, color = colors.secondary,
            modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = Type.captionStrong, color = colors.secondary, modifier = Modifier.padding(start = 8.dp, top = 28.dp, bottom = 8.dp))
}

/** Rows of one group sit 2dp apart, rounded only on the outside, so a group reads as one piece. */
@Composable private fun Group(vararg rows: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, row ->
            row(Modifier.padding(bottom = if (i == rows.lastIndex) 0.dp else 2.dp).clip(segment(i == 0, i == rows.lastIndex)).background(colors.surface))
        }
    }
}

private fun segment(first: Boolean, last: Boolean): RoundedCornerShape {
    val outer = 22.dp; val inner = 6.dp
    return RoundedCornerShape(topStart = if (first) outer else inner, topEnd = if (first) outer else inner, bottomStart = if (last) outer else inner, bottomEnd = if (last) outer else inner)
}

@Composable private fun GroupRow(title: String, modifier: Modifier, subtitle: String? = null, icon: Int? = null, danger: Boolean = false, opens: Boolean = false,
                                 onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Glyph(it, null, if (danger) colors.danger else colors.secondary, size = 22.dp); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = if (danger) colors.danger else colors.text)
            subtitle?.let { Text(it, style = Type.caption, color = colors.secondary) }
        }
        when {
            trailing != null -> { Spacer(Modifier.width(12.dp)); trailing() }
            opens -> Glyph(R.drawable.ic_chevron, null, colors.tertiary, size = 18.dp)
        }
    }
}

// Shared pieces

@Composable private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Mark(28.dp)
        Spacer(Modifier.width(10.dp))
        Text("DropDuo", style = Type.headline, color = colors.text)
    }
}

/** Full-color marks sit on white so dark mode gets a small white tile. */
@Composable private fun Mark(size: Dp) {
    val tile = if (colors.dark) Modifier.background(Color.White, RoundedCornerShape(size * 0.28f)).padding(size * 0.06f) else Modifier
    Image(painterResource(R.drawable.ic_dropduo), contentDescription = null, modifier = Modifier.size(size).then(tile))
}

@Composable private fun Notice(text: String, icon: Int, tint: Color, background: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(background, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Glyph(icon, null, tint, Modifier.padding(top = 1.dp), size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.caption, color = tint)
    }
}

@Composable private fun Thumb(row: Transfer, size: Dp) {
    val image = rememberThumbnail(row, with(LocalDensity.current) { size.roundToPx() })
    val link = kindOf(row) == Kind.Link
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(if (link) colors.accentSoft else colors.surfaceHigh), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Glyph(kindIcon(row), null, if (link) colors.accentText else colors.text, size = size * 0.48f)
    }
}

@Composable private fun Glyph(icon: Int, description: String?, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = modifier.size(size))
}

/** A surface that dips slightly while pressed. */
@Composable private fun TapSurface(onClick: () -> Unit, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape, color: Color, contentColor: Color,
                                   enabled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.minimumInteractiveComponentSize().semantics { role = Role.Button }.graphicsLayer { scaleX = scale; scaleY = scale }, shape = shape, color = color,
        contentColor = contentColor, interactionSource = interaction, content = content)
}

@Composable private fun PrimaryButton(text: String, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    TapSurface(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), RoundedCornerShape(18.dp), if (enabled) colors.accent else colors.surfaceHigh,
        if (enabled) colors.onAccent else colors.tertiary, enabled) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(painterResource(it), null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = Type.bodyStrong, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun QuietButton(text: String, onClick: () -> Unit, color: Color = colors.text) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text, style = Type.bodyStrong, color = color)
    }
}

// Transfer details

private enum class Kind { Text, Link, Image, Video, Folder, File }
private enum class Tone { Normal, Waiting, Problem }
private val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff")
private val VIDEOS = setOf("mp4", "mov", "m4v", "mkv", "webm", "avi", "3gp")

private fun kindOf(row: Transfer) = when {
    row.text != null -> if (linkOf(row.text) != null) Kind.Link else Kind.Text
    row.folder -> Kind.Folder
    row.name.substringAfterLast('.', "").lowercase() in IMAGES -> Kind.Image
    row.name.substringAfterLast('.', "").lowercase() in VIDEOS -> Kind.Video
    else -> Kind.File
}
private fun kindIcon(row: Transfer) = when (kindOf(row)) {
    Kind.Text -> R.drawable.ic_text; Kind.Link -> R.drawable.ic_link; Kind.Image -> R.drawable.ic_image; Kind.Video -> R.drawable.ic_video; Kind.Folder -> R.drawable.ic_folder; Kind.File -> R.drawable.ic_file
}
/** A shared text counts as a link only when it is a single web address. */
private fun linkOf(text: String): Uri? = text.trim().takeIf { it.none(Char::isWhitespace) && (it.startsWith("https://", true) || it.startsWith("http://", true)) }
    ?.let(Uri::parse)?.takeIf { !it.host.isNullOrEmpty() }
private fun title(row: Transfer) = when {
    row.text != null -> linkOf(row.text)?.let { "${it.host?.removePrefix("www.")}${it.path?.takeIf { p -> p != "/" }.orEmpty()}" }
        ?: row.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Text"
    else -> row.name
}
private fun canRetry(row: Transfer) = row.state == "Interrupted" && row.direction == "Sent" && (row.path != null || row.text != null)
private fun percent(row: Transfer) = (row.progress.coerceIn(0.0, 1.0) * 100).toInt()
private fun tone(row: Transfer) = when {
    row.state == "Interrupted" && row.autoRetry -> Tone.Waiting
    row.state == "Interrupted" -> Tone.Problem
    row.error != null && row.state != "Complete" && row.state != "Cancelled" -> Tone.Problem
    else -> Tone.Normal
}
private fun statusText(context: Context, row: Transfer) = when (row.state) {
    "Complete" -> listOfNotNull(if (row.direction == "Sent") "Sent" else "Received", row.time.takeIf { it > 0 }?.let { DateFormat.getTimeFormat(context).format(Date(it)) }).joinToString(" · ")
    "Preparing" -> "Preparing…"
    "Sending", "Receiving" -> "${row.state} · ${percent(row)}%"
    "Interrupted" -> row.error ?: "Didn't finish"
    "Cancelled" -> "Cancelled"
    else -> row.error ?: row.state
}
private fun startOfDay(time: Long): Long {
    if (time <= 0) return 0
    return Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
}
private fun dayLabel(context: Context, day: Long) = when {
    day == 0L -> "Earlier"
    DateUtils.isToday(day) -> "Today"
    DateUtils.isToday(day + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> DateUtils.formatDateTime(context, day, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_NO_YEAR)
}
private fun copy(context: Context, text: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DropDuo", text))
private fun clipboardText(context: Context) =
    context.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

/** Thumbnails for received photos and videos, decoded off the main thread and kept for the session. */
private object Thumbnails {
    private val cache = LruCache<String, ImageBitmap>(48)
    fun cached(path: String, px: Int): ImageBitmap? = cache.get("$px:$path")
    fun load(path: String, video: Boolean, px: Int): ImageBitmap? {
        cached(path, px)?.let { return it }
        val file = File(path).takeIf { it.exists() } ?: return null
        val bitmap = runCatching { if (video) ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null) else ThumbnailUtils.createImageThumbnail(file, Size(px, px), null) }.getOrNull()
        return bitmap?.asImageBitmap()?.also { cache.put("$px:$path", it) }
    }
}

@Composable private fun rememberThumbnail(row: Transfer, px: Int): ImageBitmap? {
    val kind = kindOf(row)
    val path = row.path.takeIf { row.state == "Complete" && row.direction == "Received" && (kind == Kind.Image || kind == Kind.Video) }
    val image by produceState(path?.let { Thumbnails.cached(it, px) }, path, px) {
        if (path != null && value == null) value = withContext(Dispatchers.IO) { Thumbnails.load(path, kind == Kind.Video, px) }
    }
    return image
}

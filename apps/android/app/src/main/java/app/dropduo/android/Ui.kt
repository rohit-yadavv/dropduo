package app.dropduo.android

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class Actions(val scan: () -> Unit, val connect: () -> Unit, val files: () -> Unit, val save: (Transfer) -> Unit, val open: (Transfer) -> Unit)

private const val UNPAIRED_STATUS = "Pair your Mac to get started"
private val ACTIVE = listOf("Preparing", "Sending", "Receiving")

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
            Crossfade(targetState = if (state.device == null) 0 else if (settings) 2 else 1, animationSpec = tween(220), label = "screen") { screen ->
                when (screen) {
                    0 -> Onboarding(state.status, actions.scan) { codeSheet = true }
                    1 -> Home(state, actions) { settings = true }
                    else -> Settings(state, actions, onBack = { settings = false }) { codeSheet = true }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { data ->
                Surface(color = colors.solid, contentColor = colors.onSolid, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(data.visuals.message, style = Type.body, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
                }
            }
        }
        if (codeSheet) PairCodeSheet(onPair = { AppState.pair(it); actions.connect() }) { codeSheet = false }
    }
}

// Onboarding

@Composable private fun Onboarding(status: String, onScan: () -> Unit, onCode: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Brand(Modifier.padding(top = 16.dp))
            Column(Modifier.padding(vertical = 40.dp)) {
                Text("Pair with\nyour Mac", style = Type.display, color = colors.text)
                Spacer(Modifier.height(12.dp))
                Text("Send photos, files and links between your phone and Mac over your local network.", style = Type.body, color = colors.secondary)
                Spacer(Modifier.height(36.dp))
                listOf("Open DropDuo on your Mac", "Choose Pair a Device", "Scan the code it shows").forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).border(1.dp, colors.hairline, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = Type.caption, color = colors.secondary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(step, style = Type.body, color = colors.text)
                    }
                }
            }
            Column(Modifier.padding(bottom = 16.dp)) {
                if (status != UNPAIRED_STATUS) {
                    Text(status, style = Type.caption, color = colors.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                }
                PrimaryButton("Scan pairing code", R.drawable.ic_scan, onClick = onScan)
                Spacer(Modifier.height(4.dp))
                QuietButton("Enter code instead", onCode)
                Text("Both devices need to be on the same network.", style = Type.caption, color = colors.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

// Home

@Composable private fun Home(state: UiState, actions: Actions, onSettings: () -> Unit) {
    var textSheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom + 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Glyph(R.drawable.ic_settings, "Settings", colors.text) }
            }
        }
        item { DeviceCard(state, actions.connect) }
        item {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(R.drawable.ic_file, "Files", "Photos, videos, docs", state.connected, primary = true, onClick = actions.files, modifier = Modifier.weight(1f))
                ActionTile(R.drawable.ic_text, "Text", "Links and notes", state.connected, primary = false, onClick = { textSheet = true }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Text("Or use Share → DropDuo in any app.", style = Type.caption, color = colors.tertiary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 36.dp))
        }
        item { SectionLabel("Recent") }
        if (state.history.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing shared yet", style = Type.bodyStrong, color = colors.text)
                Spacer(Modifier.height(4.dp))
                Text("Files and text you send or receive appear here.", style = Type.caption, color = colors.secondary, textAlign = TextAlign.Center)
            }
        }
        items(state.history, key = { it.id }) { row -> TransferRow(row) { selected = row.id } }
    }
    if (textSheet) TextSheet(state.connected) { textSheet = false }
    state.history.firstOrNull { it.id == selected }?.let { row -> TransferSheet(row, actions) { selected = null } }
}

@Composable private fun DeviceCard(state: UiState, onConnect: () -> Unit) {
    val pending = !state.connected && state.status.endsWith("…")
    val label = when { state.connected -> "Connected"; state.status == UNPAIRED_STATUS -> "Not connected"; else -> state.status }
    Surface(color = colors.surface, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(colors.background, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_laptop, null, colors.text)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.device ?: "", style = Type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (state.connected) colors.positive else if (pending) colors.pending else colors.tertiary, pulse = pending)
                    Spacer(Modifier.width(7.dp))
                    Text(label, style = Type.caption, color = colors.secondary, maxLines = 2)
                }
            }
            if (!state.connected && !pending) {
                Spacer(Modifier.width(12.dp))
                Surface(onClick = onConnect, shape = CircleShape, color = colors.solid, contentColor = colors.onSolid) {
                    Text("Connect", style = Type.bodyStrong, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable private fun StatusDot(color: Color, pulse: Boolean) {
    val alpha = if (pulse) rememberInfiniteTransition(label = "pulse")
        .animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha").value else 1f
    Box(Modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
}

@Composable private fun ActionTile(icon: Int, title: String, subtitle: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val background = if (primary) colors.accent else colors.surface
    val content = if (primary) colors.onAccent else colors.text
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.height(136.dp).alpha(if (enabled) 1f else 0.4f), shape = RoundedCornerShape(24.dp), color = background) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(40.dp).background(if (primary) content.copy(alpha = 0.16f) else colors.background, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Glyph(icon, null, content, 22.dp)
            }
            Column {
                Text(title, style = Type.headline, color = content)
                Text(subtitle, style = Type.caption, color = if (primary) content.copy(alpha = 0.75f) else colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun TransferRow(row: Transfer, onClick: () -> Unit) {
    val active = row.state in ACTIVE
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(colors.surface, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Glyph(kindIcon(row), null, colors.text, 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title(row), style = Type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (active) LinearProgressIndicator(progress = { row.progress.toFloat().coerceIn(0f, 1f) }, color = colors.accent, trackColor = colors.surfaceHigh,
                gapSize = 0.dp, drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(3.dp).clip(CircleShape))
            else Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(if (row.direction == "Sent") R.drawable.ic_arrow_up else R.drawable.ic_arrow_down, null, colors.tertiary, 13.dp)
                Spacer(Modifier.width(4.dp))
                Text(subtitle(row), style = Type.caption, color = if (row.error != null && !row.autoRetry) colors.danger else colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            active -> IconButton(onClick = { AppState.cancel(row) }) { Glyph(R.drawable.ic_close, "Cancel transfer", colors.secondary, 20.dp) }
            canRetry(row) -> IconButton(onClick = { AppState.retry(row) }) { Glyph(R.drawable.ic_retry, "Retry", colors.accent, 20.dp) }
        }
    }
}

private fun canRetry(row: Transfer) = row.state == "Interrupted" && row.direction == "Sent" && row.path != null
private fun title(row: Transfer) = row.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: row.name
private fun subtitle(row: Transfer) = when (row.state) {
    "Complete" -> row.direction
    in ACTIVE -> "${row.state} · ${(row.progress.coerceIn(0.0, 1.0) * 100).toInt()}%"
    "Interrupted" -> row.error ?: "Interrupted"
    else -> row.error ?: row.state
}
private fun kindIcon(row: Transfer) = when {
    row.text != null -> R.drawable.ic_text
    row.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tiff") -> R.drawable.ic_image
    row.name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mov", "m4v", "mkv", "webm", "avi", "3gp") -> R.drawable.ic_video
    else -> R.drawable.ic_file
}

// Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 18.dp).size(36.dp, 4.dp).background(colors.hairline, CircleShape))
        }) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp), content = content)
    }
}

@Composable private fun TextSheet(connected: Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Sheet(onDismiss) {
        Text("Send text", style = Type.title, color = colors.text)
        Spacer(Modifier.height(4.dp))
        Text(if (connected) "A link or a note, straight to your Mac." else "Connect to your Mac to send text.", style = Type.caption, color = colors.secondary)
        Spacer(Modifier.height(20.dp))
        Field(text, { text = it }, "Paste a link or type a note", minLines = 4)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Send", enabled = connected && text.isNotBlank()) { AppState.sendText(text) { text = ""; onDismiss() } }
    }
}

@Composable private fun PairCodeSheet(onPair: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Sheet(onDismiss) {
        Text("Enter pairing code", style = Type.title, color = colors.text)
        Spacer(Modifier.height(4.dp))
        Text("On your Mac, choose Pair a Device, then Copy Code and paste it here.", style = Type.caption, color = colors.secondary)
        Spacer(Modifier.height(20.dp))
        Field(code, { code = it }, "Pairing code", minLines = 3, monospace = true)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Pair", enabled = code.isNotBlank()) { onPair(code); onDismiss() }
    }
}

@Composable private fun TransferSheet(row: Transfer, actions: Actions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(colors.surface, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Glyph(kindIcon(row), null, colors.text) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (row.text != null) "Text" else row.name, style = Type.headline, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle(row), style = Type.caption, color = colors.secondary)
            }
        }
        row.text?.let { text ->
            Spacer(Modifier.height(16.dp))
            Surface(color = colors.surface, shape = RoundedCornerShape(16.dp)) {
                SelectionContainer(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = Type.body, color = colors.text, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        fun act(block: () -> Unit) = { block(); onDismiss() }
        row.text?.let { text -> SheetAction(R.drawable.ic_copy, "Copy text", act {
            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DropDuo", text))
        }) }
        if (row.state == "Complete" && row.direction == "Received" && row.path != null) {
            SheetAction(R.drawable.ic_open, "Open", act { actions.open(row) })
            SheetAction(R.drawable.ic_save, "Save a copy", act { actions.save(row) })
        }
        if (canRetry(row)) SheetAction(R.drawable.ic_retry, "Retry", act { AppState.retry(row) })
        if (row.state in ACTIVE || (canRetry(row) && row.autoRetry)) SheetAction(R.drawable.ic_close, "Cancel transfer", act { AppState.cancel(row) }, danger = true)
    }
}

@Composable private fun SheetAction(icon: Int, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) colors.danger else colors.text
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, null, tint, 22.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = Type.bodyStrong, color = tint)
    }
}

@Composable private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int, monospace: Boolean = false) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val style = Type.body.copy(color = colors.text, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
    BasicTextField(value, onChange, textStyle = style, minLines = minLines, maxLines = 8, cursorBrush = SolidColor(colors.accent),
        modifier = Modifier.fillMaxWidth().focusRequester(focus), decorationBox = { inner ->
            Box(Modifier.background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp)) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.tertiary, fontFamily = FontFamily.Default))
                inner()
            }
        })
}

// Settings

@Composable private fun Settings(state: UiState, actions: Actions, onBack: () -> Unit, onCode: () -> Unit) {
    var confirmForget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val running = state.connected || state.status.endsWith("…")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(horizontal = 20.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp).offset(x = (-8).dp)) { Glyph(R.drawable.ic_back, "Back", colors.text) }
        Text("Settings", style = Type.display, color = colors.text, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        SectionLabel("Your Mac")
        Group {
            GroupRow(state.device ?: "", "Trusted · pairing stored securely on this phone", icon = R.drawable.ic_laptop)
            GroupRow("Accept files and text", "Turn off to pause receiving") {
                Switch(checked = state.receiving, onCheckedChange = AppState::setReceiving, colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent, uncheckedTrackColor = colors.surfaceHigh,
                    uncheckedThumbColor = colors.background, uncheckedBorderColor = Color.Transparent))
            }
            if (running) GroupRow("Disconnect", onClick = { AppState.stop(context) }) else GroupRow("Connect", onClick = actions.connect)
            GroupRow("Forget this Mac", danger = true, onClick = { confirmForget = true })
        }
        SectionLabel("Pairing")
        Group {
            GroupRow("Scan a new pairing code", icon = R.drawable.ic_scan, onClick = actions.scan)
            GroupRow("Enter a pairing code", icon = R.drawable.ic_keyboard, onClick = onCode)
        }
        SectionLabel("Storage")
        Group { GroupRow("Clear recent history", "Received files are kept", onClick = AppState::clearHistory) }
        Text("Received files stay in DropDuo's app storage. Use Save a copy to keep them in a folder you choose. Uninstalling DropDuo removes its stored files.",
            style = Type.caption, color = colors.secondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
        Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mark(24.dp)
            Spacer(Modifier.height(10.dp))
            Text("DropDuo ${BuildConfig.VERSION_NAME}", style = Type.caption, color = colors.secondary)
            Text("Local network only. No accounts. No analytics.", style = Type.caption, color = colors.tertiary, textAlign = TextAlign.Center)
        }
    }
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, containerColor = colors.background, shape = RoundedCornerShape(28.dp),
        title = { Text("Forget this Mac?", style = Type.title, color = colors.text) },
        text = { Text("You will need to pair again. Received files stay on this phone.", style = Type.body, color = colors.secondary) },
        confirmButton = { TextButton(onClick = { AppState.forget(context); confirmForget = false }) { Text("Forget", color = colors.danger, style = Type.bodyStrong) } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel", color = colors.text, style = Type.bodyStrong) } })
}

@Composable private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = colors.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(bottom = 28.dp)) { Column(content = content) }
}

@Composable private fun GroupRow(title: String, subtitle: String? = null, icon: Int? = null, danger: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Glyph(it, null, colors.text, 22.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = if (danger) colors.danger else colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Type.caption, color = colors.secondary) }
        }
        when {
            trailing != null -> { Spacer(Modifier.width(12.dp)); trailing() }
            onClick != null && icon != null -> Glyph(R.drawable.ic_chevron, null, colors.tertiary, 18.dp)
        }
    }
}

// Shared pieces

@Composable private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Mark(30.dp)
        Spacer(Modifier.width(10.dp))
        Text("DropDuo", style = Type.headline, color = colors.text)
    }
}

/** Full-color marks sit on white (branding/README.md), so dark mode gets a small white tile. */
@Composable private fun Mark(size: Dp) {
    val tile = if (colors.dark) Modifier.background(Color.White, RoundedCornerShape(size * 0.28f)).padding(size * 0.06f) else Modifier
    Image(painterResource(R.drawable.ic_dropduo), contentDescription = null, modifier = Modifier.size(size).then(tile))
}

@Composable private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = Type.label, color = colors.tertiary, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
}

@Composable private fun Glyph(icon: Int, description: String?, tint: Color, size: Dp = 24.dp) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

@Composable private fun PrimaryButton(text: String, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.solid, contentColor = colors.onSolid, disabledContainerColor = colors.surfaceHigh, disabledContentColor = colors.tertiary)) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = Type.bodyStrong)
    }
}

@Composable private fun QuietButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text, style = Type.bodyStrong, color = colors.text)
    }
}

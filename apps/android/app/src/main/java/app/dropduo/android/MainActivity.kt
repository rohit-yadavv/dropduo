package app.dropduo.android

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var exportSource: File? = null
    private val scan = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { AppState.pair(it); connect() }
    }
    private val files = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) AppState.importAndSend(uris) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { AppState.start(this) }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = exportSource; exportSource = null
        if (uri != null && source != null) AppState.scope.launchIo {
            runCatching { contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: throw IllegalStateException("Could not save file") }
                .onFailure { AppState.error(it.message ?: "Could not save file") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { DropDuoScreen(onScan = { scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the code shown on your Mac").setBeepEnabled(false).setOrientationLocked(false)) },
            onConnect = ::connect, onFiles = { files.launch(arrayOf("*/*")) }, onSave = { row -> row.path?.let { exportSource = File(it); export.launch(row.name) } }, onOpen = ::openFile) }
        if (AppState.ticket != null) connect()
        handleShare(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    private fun connect() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else AppState.start(this)
    }
    private fun handleShare(intent: Intent) {
        @Suppress("DEPRECATION")
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) AppState.importAndSend(listOf(uri))
                else intent.getStringExtra(Intent.EXTRA_TEXT)?.let { AppState.sendText(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { AppState.importAndSend(it) }
        }
    }
    private fun openFile(row: Transfer) {
        try {
            val file = File(row.path ?: return); require(file.exists()) { "File is no longer available" }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { AppState.error("No app could open this file. Use Save a copy instead.") }
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchIo(block: suspend () -> Unit) = this.launch { block() }

@Composable fun DropDuoScreen(onScan: () -> Unit, onConnect: () -> Unit, onFiles: () -> Unit, onSave: (Transfer) -> Unit, onOpen: (Transfer) -> Unit) {
    val state by AppState.ui.collectAsState()
    var tab by remember { mutableStateOf("Share") }
    var text by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF91B6FF)) else lightColorScheme(primary = Color(0xFF246BFD), background = Color(0xFFF8F9FC), surface = Color.White)) {
        Scaffold(bottomBar = {
            NavigationBar { listOf("Share", "Recent", "Device").forEach { name ->
                NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = { Text(if (name == "Share") "↗" else if (name == "Recent") "◷" else "▣", style = MaterialTheme.typography.titleLarge) }, label = { Text(name) })
            } }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.ic_dropduo), contentDescription = null,
                        modifier = Modifier.size(40.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(3.dp))
                    Text("DropDuo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
                Text(when (tab) { "Recent" -> "What moved between your devices."; "Device" -> "Pair once. Keep sharing."; else -> "Your phone and Mac. A little closer." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConnectionCard(state, onConnect)
                when (tab) {
                    "Share" -> {
                        if (state.device == null) {
                            Card { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Connect your Mac", style = MaterialTheme.typography.titleLarge)
                                Text("Open DropDuo on your Mac and choose Devices → Pair. Connect both devices to the same local network.")
                                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan pairing code") }
                                TextButton(onClick = { showCode = true }) { Text("Paste a pairing code") }
                            } }
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Send something", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Photos, videos, documents. Original quality.")
                                Button(onClick = onFiles, enabled = state.connected, modifier = Modifier.fillMaxWidth()) { Text("Choose files") }
                                Text("You can also select content in another app and choose Share → DropDuo.", style = MaterialTheme.typography.bodySmall)
                            } }
                            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Link or text") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            Button(onClick = { AppState.sendText(text) { text = "" } }, enabled = state.connected && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send text") }
                        }
                        state.history.firstOrNull()?.let { TransferCard(it, onSave, onOpen) }
                    }
                    "Recent" -> {
                        if (state.history.isEmpty()) Text("Nothing shared yet. Your recent transfers will appear here.")
                        state.history.forEach { TransferCard(it, onSave, onOpen) }
                    }
                    "Device" -> {
                        Text(state.device ?: "No Mac paired", style = MaterialTheme.typography.titleLarge)
                        if (state.device != null) {
                            Text("Trusted device. Pairing is stored securely on this phone.")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Accept files and text", modifier = Modifier.weight(1f).padding(top = 12.dp)); Switch(checked = state.receiving, onCheckedChange = AppState::setReceiving) }
                            OutlinedButton(onClick = { AppState.stop(context) }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
                            TextButton(onClick = { confirmForget = true }) { Text("Forget this Mac") }
                        }
                        TextButton(onClick = onScan) { Text("Scan a new pairing code") }
                        TextButton(onClick = { showCode = true }) { Text("Paste a pairing code") }
                        HorizontalDivider()
                        Text("Received files stay in DropDuo's app storage. Use Save a copy to keep them in a folder you choose. Uninstalling DropDuo removes its stored files.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = AppState::clearHistory) { Text("Clear recent history") }
                        Text("0.1.0 alpha · Local network only\nNo accounts. No analytics.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (showCode) AlertDialog(onDismissRequest = { showCode = false }, title = { Text("Pair your Mac") }, text = {
            OutlinedTextField(code, { code = it }, label = { Text("Pairing code") }, maxLines = 5)
        }, confirmButton = { TextButton(onClick = { AppState.pair(code); onConnect(); code = ""; showCode = false }) { Text("Pair") } }, dismissButton = { TextButton(onClick = { showCode = false }) { Text("Cancel") } })
        if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, title = { Text("Forget this Mac?") }, text = { Text("You will need to pair again. Received files stay on this phone.") },
            confirmButton = { TextButton(onClick = { AppState.forget(context); confirmForget = false }) { Text("Forget") } }, dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } })
        state.error?.let { message -> AlertDialog(onDismissRequest = { AppState.update { it.copy(error = null) } }, title = { Text("DropDuo") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { AppState.update { it.copy(error = null) } }) { Text("OK") } }) }
    }
}
@Composable private fun ConnectionCard(state: UiState, onConnect: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(if (state.connected) "Connected" else "Not connected", fontWeight = FontWeight.Bold); Text(state.status, style = MaterialTheme.typography.bodySmall) }
            if (!state.connected && state.device != null) TextButton(onClick = onConnect) { Text("Connect") }
        }
    }
}
@Composable private fun TransferCard(row: Transfer, onSave: (Transfer) -> Unit, onOpen: (Transfer) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.name, fontWeight = FontWeight.Bold, maxLines = 2)
            Text("${row.direction} · ${row.state}", style = MaterialTheme.typography.bodySmall)
            if (row.state in listOf("Preparing", "Sending", "Receiving")) {
                LinearProgressIndicator(progress = { row.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { AppState.cancel(row) }) { Text("Cancel") }
            }
            row.text?.let { text -> Text(text, maxLines = 4); TextButton(onClick = { context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DropDuo", text)) }) { Text("Copy") } }
            if (row.state == "Complete" && row.direction == "Received" && row.path != null) Row {
                TextButton(onClick = { onOpen(row) }) { Text("Open") }; TextButton(onClick = { onSave(row) }) { Text("Save a copy") }
            }
            if (row.state == "Interrupted" && row.direction == "Sent" && row.path != null) TextButton(onClick = { AppState.retry(row) }) { Text("Retry") }
            row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

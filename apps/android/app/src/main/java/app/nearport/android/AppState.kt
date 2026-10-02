package app.nearport.android

import android.app.Application
import android.content.*
import android.net.Uri
import android.provider.OpenableColumns
import app.nearport.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID

class NearportApplication : Application() { override fun onCreate() { super.onCreate(); AppState.init(this) } }
data class Transfer(val id: String, val name: String, val direction: String, val state: String, val progress: Double = 0.0,
    val path: String? = null, val text: String? = null, val error: String? = null)
data class UiState(val device: String? = null, val connected: Boolean = false, val status: String = "Pair your Mac to get started",
    val receiving: Boolean = true, val history: List<Transfer> = emptyList(), val error: String? = null)
object AppState {
    lateinit var context: Application
    lateinit var secure: SecureStore
    val ui = MutableStateFlow(UiState())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile var ticket: Ticket? = null
    @Volatile var engine: PeerEngine? = null
    private val sending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingPair = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pairingGate = Any()
    private fun historyFile() = File(context.filesDir, "history.json")
    fun init(app: Application) {
        context = app; secure = SecureStore(app)
        ticket = runCatching { secure.load() }.getOrNull()
        val history = runCatching { Wire.gson.fromJson(historyFile().readText(), Array<Transfer>::class.java).toList() }.getOrDefault(emptyList())
            .map { if (it.state in listOf("Preparing", "Sending", "Receiving")) it.copy(state = "Interrupted") else it }
        ui.value = UiState(device = ticket?.name, history = history, receiving = app.getSharedPreferences("settings", 0).getBoolean("receiving", true))
    }
    @Synchronized fun update(transform: (UiState) -> UiState) { ui.value = transform(ui.value) }
    fun error(message: String) = update { it.copy(error = message) }
    fun pair(code: String) {
        scope.launch {
            try {
                val next = Ticket.parse(code.trim())
                require(next.expires > System.currentTimeMillis()/1000) { "Pairing code expired. Generate a new code on your Mac." }
                synchronized(pairingGate) { ticket = next; pendingPair.set(true) }
                engine?.close(); engine = null
                update { it.copy(device = next.name, connected = false, status = "Approve pairing on your Mac…") }
            } catch (e: Exception) { error(e.message ?: "Invalid pairing code") }
        }
    }
    fun pairConfirmed(value: Ticket) {
        synchronized(pairingGate) { if (ticket?.pairID != value.pairID) return; secure.save(value); pendingPair.set(false) }
    }
    fun pairFailed() {
        if (pendingPair.compareAndSet(true, false)) {
            ticket = runCatching { secure.load() }.getOrNull()
            update { it.copy(device = ticket?.name, status = "Pairing failed. Generate a new code and try again.") }
        }
    }
    fun start(context: Context) { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java)) }
    fun stop(context: Context) { context.stopService(Intent(context, ConnectionService::class.java)); engine?.close(); engine = null; update { it.copy(connected = false, status = "Disconnected") } }
    fun forget(context: Context) { stop(context); ticket = null; secure.remove(); update { it.copy(device = null, status = "Pair your Mac to get started") } }
    fun setReceiving(value: Boolean) {
        engine?.receivingEnabled = value
        context.getSharedPreferences("settings", 0).edit().putBoolean("receiving", value).apply()
        update { it.copy(receiving = value) }
    }
    @Synchronized fun record(event: PeerEvent) {
        val old = ui.value.history.firstOrNull { it.id == event.id }
        val row = Transfer(event.id, old?.name ?: event.name, old?.direction ?: event.direction, event.state, event.progress,
            event.path ?: old?.path, event.text ?: old?.text, event.error)
        val history = (listOf(row) + ui.value.history.filter { it.id != event.id }).take(100)
        ui.value = ui.value.copy(history = history)
        if (event.state in listOf("Complete", "Interrupted", "Cancelled")) saveHistory(history)
    }
    private fun saveHistory(history: List<Transfer>) {
        runCatching { val temp = File(context.filesDir, "history.tmp"); temp.writeText(Wire.gson.toJson(history)); check(temp.renameTo(historyFile())) }
            .onFailure { error("Could not save transfer history") }
    }
    fun sendText(text: String) { scope.launch { try { val peer = engine ?: throw IllegalStateException("Mac is offline. Connect first."); peer.sendText(text) } catch (e: Exception) { error(e.message ?: "Could not send text") } } }
    fun importAndSend(uris: List<Uri>) {
        scope.launch {
            for (uri in uris) {
                val id = UUID.randomUUID().toString()
                var file: File? = null
                try {
                    var name = "Shared file"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                    val dir = File(context.filesDir, "outgoing").apply { mkdirs() }; file = File(dir, id)
                    record(PeerEvent(id, name, "Sent", "Preparing", path = file.path))
                    context.contentResolver.openInputStream(uri)?.use { source -> file.outputStream().use { out ->
                        val buffer = ByteArray(Wire.CHUNK_SIZE); var total = 0L
                        while (true) { val n = source.read(buffer); if (n < 0) break; total += n; require(total <= Wire.MAX_FILE && context.filesDir.usableSpace > n + 1024*1024) { "Not enough space or file too large" }; out.write(buffer, 0, n) }
                    } } ?: throw IllegalStateException("Could not read selected file")
                    sendStored(id, name, file)
                } catch (e: Exception) {
                    record(PeerEvent(id, "Shared file", "Sent", "Interrupted", path = file?.path, error = e.message)); error(e.message ?: "Could not send file")
                }
            }
        }
    }
    private fun sendStored(id: String, name: String, file: File) {
        check(sending.add(id)) { "Transfer already active" }
        try { val peer = engine ?: throw IllegalStateException("Mac is offline. Connect and retry."); peer.sendFile(file, id, name); file.delete() }
        finally { sending.remove(id) }
    }
    fun retry(row: Transfer) { scope.launch { try { val file = File(row.path ?: throw IllegalStateException("Source unavailable")); require(file.exists()) { "Source unavailable. Share it again." }; sendStored(row.id, row.name, file) } catch (e: Exception) { error(e.message ?: "Retry failed") } } }
    fun cancel(row: Transfer) { scope.launch { engine?.cancel(row.id); if (row.direction == "Sent") row.path?.let { File(it).delete() } } }
    fun clearHistory() { synchronized(this) { val active = ui.value.history.filter { it.state in listOf("Preparing", "Sending", "Receiving") }; ui.value = ui.value.copy(history = active); saveHistory(active) } }
    fun inbox() = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Nearport").apply { mkdirs() }
}

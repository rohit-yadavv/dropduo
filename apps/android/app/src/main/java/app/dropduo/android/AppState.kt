package app.dropduo.android

import android.app.Application
import android.content.*
import android.net.Uri
import android.provider.OpenableColumns
import app.dropduo.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID

class DropDuoApplication : Application() { override fun onCreate() { super.onCreate(); AppState.init(this); AppUpdater.init(this) } }
data class Transfer(val id: String, val name: String, val direction: String, val state: String, val progress: Double = 0.0,
    val path: String? = null, val text: String? = null, val error: String? = null, val autoRetry: Boolean = false)
data class UiState(val device: String? = null, val connected: Boolean = false, val status: String = "Pair your Mac to get started",
    val receiving: Boolean = true, val history: List<Transfer> = emptyList(), val error: String? = null)
const val WAITING_FOR_MAC = "Waiting for your Mac"
object AppState {
    lateinit var context: Application
    lateinit var secure: SecureStore
    val ui = MutableStateFlow(UiState())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile var ticket: Ticket? = null
    @Volatile var engine: PeerEngine? = null
    @Volatile var installingUpdate = false
    val hasActiveTransfers get() = sending.isNotEmpty() || importing.isNotEmpty() || ui.value.history.any { it.state in listOf("Preparing", "Sending", "Receiving") }
    @Synchronized fun beginUpdateInstallation(): Boolean {
        if (hasActiveTransfers || installingUpdate) return false
        installingUpdate = true; engine?.receivingEnabled = false
        return true
    }
    @Synchronized fun finishUpdateInstallation() { installingUpdate = false; engine?.receivingEnabled = ui.value.receiving; resumePending() }
    private val importing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cancelledImports = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingPair = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pairingGate = Any()
    private fun historyFile() = File(context.filesDir, "history.json")
    fun init(app: Application) {
        context = app; secure = SecureStore(app)
        ticket = runCatching { secure.load() }.getOrNull()
        val history = runCatching { Wire.gson.fromJson(historyFile().readText(), Array<Transfer>::class.java).toList() }.getOrDefault(emptyList())
            // "Sending" rows finished importing, so their stored copy is complete and safe to resume.
            .map { if (it.state in listOf("Preparing", "Sending", "Receiving")) it.copy(state = "Interrupted", autoRetry = it.direction == "Sent" && it.state == "Sending" && it.path != null) else it }
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
    fun pairFailed(attempted: Ticket) {
        if (ticket?.pairID != attempted.pairID) return
        if (pendingPair.compareAndSet(true, false)) {
            ticket = runCatching { secure.load() }.getOrNull()
            update { it.copy(device = ticket?.name, status = "Pairing failed. Generate a new code and try again.") }
        }
    }
    fun start(context: Context) { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java)) }
    fun stop(context: Context) { context.stopService(Intent(context, ConnectionService::class.java)); engine?.close(); engine = null; update { it.copy(connected = false, status = "Disconnected") } }
    fun forget(context: Context) { stop(context); ticket = null; secure.remove(); update { it.copy(device = null, status = "Pair your Mac to get started") } }
    fun setReceiving(value: Boolean) {
        engine?.receivingEnabled = value && !installingUpdate
        context.getSharedPreferences("settings", 0).edit().putBoolean("receiving", value).apply()
        update { it.copy(receiving = value) }
    }
    @Synchronized fun record(event: PeerEvent, autoRetry: Boolean = false) {
        val old = ui.value.history.firstOrNull { it.id == event.id }
        val row = Transfer(event.id, old?.name ?: event.name, old?.direction ?: event.direction, event.state, event.progress,
            event.path ?: old?.path, event.text ?: old?.text, event.error, autoRetry)
        val history = (listOf(row) + ui.value.history.filter { it.id != event.id }).take(100)
        ui.value = ui.value.copy(history = history)
        if (event.state in listOf("Complete", "Interrupted", "Cancelled")) saveHistory(history)
    }
    private fun saveHistory(history: List<Transfer>) {
        runCatching { val temp = File(context.filesDir, "history.tmp"); temp.writeText(Wire.gson.toJson(history)); check(temp.renameTo(historyFile())) }
            .onFailure { error("Could not save transfer history") }
    }
    /** Sends now, or holds the text until the Mac reconnects. */
    fun sendText(text: String, onSuccess: () -> Unit = {}) { scope.launch { try { PeerEngine.validateText(text); sendStored(UUID.randomUUID().toString(), "Text", text = text); withContext(Dispatchers.Main) { onSuccess() } } catch (e: Exception) { error(e.message ?: "Could not send text") } } }
    fun importAndSend(uris: List<Uri>) {
        scope.launch {
            for (uri in uris) {
                val id = UUID.randomUUID().toString()
                var file: File? = null
                var imported = false
                synchronized(this@AppState) {
                    if (installingUpdate) { error("DropDuo is installing an update. Send again after it reopens."); return@launch }
                    importing.add(id)
                }
                try {
                    var name = "Shared file"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                    val dir = File(context.filesDir, "outgoing").apply { mkdirs() }; file = File(dir, id)
                    record(PeerEvent(id, name, "Sent", "Preparing", path = file.path))
                    context.contentResolver.openInputStream(uri)?.use { source -> file.outputStream().use { out ->
                        val buffer = ByteArray(Wire.CHUNK_SIZE); var total = 0L
                        while (true) { check(!cancelledImports.contains(id)) { "Transfer cancelled" }; val n = source.read(buffer); if (n < 0) break; total += n; require(total <= Wire.MAX_FILE) { "Files larger than 32 GB can't be sent" }; require(context.filesDir.usableSpace > n + 1024*1024) { "Not enough free space on this phone to prepare the file" }; out.write(buffer, 0, n) }
                    } } ?: throw IllegalStateException("Couldn't read that file. Try sharing it again from the original app.")
                    check(!cancelledImports.contains(id)) { "Transfer cancelled" }
                    imported = true
                    sendStored(id, name, file = file)
                } catch (e: Exception) {
                    if (!imported) file?.delete()
                    val cancelled = cancelledImports.contains(id) || ui.value.history.firstOrNull { it.id == id }?.state == "Cancelled"
                    record(PeerEvent(id, "Shared file", "Sent", if (cancelled) "Cancelled" else "Interrupted", path = if (imported) file?.path else null, error = e.message))
                    if (!cancelled) error(e.message ?: "Could not send file")
                } finally { importing.remove(id); cancelledImports.remove(id) }
            }
        }
    }
    /** Sends a stored file copy or text; if the Mac is offline or the connection drops, it waits for reconnect instead of failing. */
    private fun sendStored(id: String, name: String, file: File? = null, text: String? = null) {
        synchronized(this) {
            check(!installingUpdate) { "DropDuo is installing an update. Send again after it reopens." }
            check(sending.add(id)) { "Transfer already active" }
        }
        var peer: PeerEngine? = null
        try {
            peer = engine ?: throw Disconnected("Mac is offline")
            if (file != null) { peer.sendFile(file, id, name); file.delete() } else peer.sendText(text!!, id)
        } catch (e: java.io.IOException) {
            record(PeerEvent(id, name, "Sent", "Interrupted", path = file?.path, text = text, error = if (peer == null) WAITING_FOR_MAC else "Resumes when your Mac reconnects"), autoRetry = true)
        } finally { sending.remove(id) }
        // The Mac may have reconnected while this attempt was still failing on the old connection.
        val current = engine
        if (current != null && current !== peer && ui.value.history.firstOrNull { it.id == id }?.autoRetry == true) resumePending()
    }
    /** Called after each connection to send held items and resume ones cut off by a lost connection, oldest first. */
    fun resumePending() {
        if (installingUpdate) return
        val rows = ui.value.history.filter { it.direction == "Sent" && it.state == "Interrupted" && it.autoRetry && (it.path != null || it.text != null) && it.id !in sending }.reversed()
        if (rows.isNotEmpty()) scope.launch {
            for (row in rows) try {
                if (ui.value.history.firstOrNull { it.id == row.id }?.autoRetry != true) continue
                resend(row)
            } catch (e: Exception) { record(PeerEvent(row.id, row.name, "Sent", "Interrupted", path = row.path, text = row.text, error = e.message)) }
        }
    }
    private fun resend(row: Transfer) {
        if (row.text != null) return sendStored(row.id, row.name, text = row.text)
        val file = File(row.path ?: throw IllegalStateException("Source unavailable")); require(file.exists()) { "Source unavailable. Share it again." }
        sendStored(row.id, row.name, file = file)
    }
    fun retry(row: Transfer) { scope.launch { try { resend(row) } catch (e: Exception) { error(e.message ?: "Retry failed") } } }
    fun cancel(row: Transfer) {
        if (importing.contains(row.id)) cancelledImports.add(row.id)
        scope.launch { engine?.cancel(row.id); if (row.direction == "Sent" && !importing.contains(row.id)) row.path?.let { File(it).delete() }; record(PeerEvent(row.id, row.name, row.direction, "Cancelled")) }
    }
    /** Keeps active and waiting sends, so clearing history never drops something still on its way. */
    fun clearHistory() { synchronized(this) { val (kept, cleared) = ui.value.history.partition { it.state in listOf("Preparing", "Sending", "Receiving") || it.autoRetry }; cleared.filter { it.direction == "Sent" }.forEach { row -> row.path?.let { path -> val file = File(path); if (file.parentFile == File(context.filesDir, "outgoing")) file.delete() } }; ui.value = ui.value.copy(history = kept); saveHistory(kept) } }
    fun inbox() = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "DropDuo").apply { mkdirs() }
}

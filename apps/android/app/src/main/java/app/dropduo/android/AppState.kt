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
    val path: String? = null, val text: String? = null, val error: String? = null, val autoRetry: Boolean = false,
    /** When the transfer started, in epoch milliseconds; 0 for history saved before times were recorded. */
    val time: Long = 0,
    /** The pairing a send is meant for, so a queued send never goes to a different Mac; null until one is paired. */
    val pairID: String? = null,
    /** A folder from the Mac; once received, [path] is the unpacked folder. */
    val folder: Boolean = false,
    /** Where a received file or folder was saved for other apps (see [SharedStorage.save]); [path] is gone once it's set. */
    val saved: String? = null,
    /** Live estimates use a monotonic clock and are never restored from history. */
    @Transient val stats: TransferProgress? = null)
/**
 * [askBackground] is set while Android may stop DropDuo in the background and the user hasn't said no; [reveal] is a transfer to show;
 * [unreachable] is set while the paired Mac can't be reached.
 */
data class UiState(val device: String? = null, val connected: Boolean = false, val status: String = "Pair your Mac to get started",
    val receiving: Boolean = true, val history: List<Transfer> = emptyList(), val error: String? = null,
    val askBackground: Boolean = false, val reveal: String? = null, val unreachable: Boolean = false)
const val WAITING_FOR_MAC = "Waiting for your Mac"
private val ONGOING = listOf("Preparing", "Sending", "Receiving", "Verifying")
private const val OTHER_MAC = "Not sent. Retry to send it to your current Mac."
object AppState {
    lateinit var context: Application
    lateinit var secure: SecureStore
    val ui = MutableStateFlow(UiState())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile var ticket: Ticket? = null
    @Volatile var engine: PeerEngine? = null
    @Volatile var installingUpdate = false
    /** DropDuo is on screen, so arrivals show in its history instead of a notification. */
    @Volatile var visible = false
    val hasActiveTransfers get() = sending.isNotEmpty() || importing.isNotEmpty() || ui.value.history.any { it.state in ONGOING }
    @Synchronized fun beginUpdateInstallation(): Boolean {
        if (hasActiveTransfers || installingUpdate) return false
        installingUpdate = true; engine?.receivingEnabled = false
        return true
    }
    @Synchronized fun finishUpdateInstallation() { installingUpdate = false; engine?.receivingEnabled = ui.value.receiving; resumePending() }
    private val importing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cancelledImports = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val saving = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pendingPair = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pairingGate = Any()
    private fun historyFile() = File(context.filesDir, "history.json")
    private fun outgoing() = File(context.filesDir, "outgoing")
    fun init(app: Application) {
        context = app; secure = SecureStore(app)
        ticket = runCatching { secure.load() }.getOrNull()
        val history = runCatching { Wire.gson.fromJson(historyFile().readText(), Array<Transfer>::class.java).toList() }.getOrDefault(emptyList())
            // Sends saved before they were tied to a Mac were meant for the one paired then.
            .map { if (it.direction == "Sent" && it.pairID == null) it.copy(pairID = ticket?.pairID) else it }
            // An import's copy appears under its final name only once complete, so an existing copy is safe to resume.
            .map { if (it.state in ONGOING) it.copy(state = "Interrupted", autoRetry = it.direction == "Sent" && (it.text != null || it.path?.let { path -> File(path).isFile } == true)) else it }
        // Copies left by imports that never finished, or by rows no longer in history.
        val kept = history.mapNotNull { it.path }.toSet()
        outgoing().listFiles()?.filter { it.path !in kept }?.forEach { it.delete() }
        ui.value = UiState(device = ticket?.name, history = history, receiving = app.getSharedPreferences("settings", 0).getBoolean("receiving", true))
        // Files received before they were saved to shared storage, or whose save failed, move there now.
        history.filter { it.direction == "Received" && it.state == "Complete" && it.saved == null && it.path?.let { path -> File(path).exists() } == true }
            .forEach { save(it, notify = false) }
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
        synchronized(pairingGate) {
            if (ticket?.pairID != value.pairID) return; secure.save(value)
            if (pendingPair.getAndSet(false)) releaseQueued { it != value.pairID }
        }
    }
    /** Stops waiting sends meant for another pairing; they stay in history for a manual retry. */
    private fun releaseQueued(other: (String) -> Boolean) {
        synchronized(this) {
            val history = ui.value.history.map { if (it.autoRetry && it.state == "Interrupted" && it.pairID?.let(other) == true) it.copy(autoRetry = false, error = OTHER_MAC) else it }
            ui.value = ui.value.copy(history = history); saveHistory(history)
        }
    }
    fun pairFailed(attempted: Ticket) {
        if (ticket?.pairID != attempted.pairID) return
        if (pendingPair.compareAndSet(true, false)) {
            ticket = runCatching { secure.load() }.getOrNull()
            update { it.copy(device = ticket?.name, status = "Pairing failed. Generate a new code and try again.") }
        }
    }
    fun start(context: Context) { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java)) }
    fun stop(context: Context) { context.stopService(Intent(context, ConnectionService::class.java)); engine?.close(); engine = null; update { it.copy(connected = false, unreachable = false, status = "Disconnected") } }
    fun forget(context: Context) {
        val forgotten = ticket?.pairID
        stop(context); ticket = null; secure.remove(); update { it.copy(device = null, status = "Pair your Mac to get started") }
        if (forgotten != null) releaseQueued { it == forgotten }
    }
    /** Remembers that the user doesn't want DropDuo kept running in the background. */
    fun declineBackground() { context.getSharedPreferences("settings", 0).edit().putBoolean("declinedBackground", true).apply(); update { it.copy(askBackground = false) } }
    fun checkBackground() {
        val allowed = context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        update { it.copy(askBackground = !allowed && !context.getSharedPreferences("settings", 0).getBoolean("declinedBackground", false)) }
    }
    fun setReceiving(value: Boolean) {
        engine?.receivingEnabled = value && !installingUpdate
        context.getSharedPreferences("settings", 0).edit().putBoolean("receiving", value).apply()
        update { it.copy(receiving = value) }
    }
    /** [pairID] is the Mac the transfer is with, when known; it replaces the one stored on the row. */
    @Synchronized fun record(event: PeerEvent, autoRetry: Boolean = false, pairID: String? = null) {
        val old = ui.value.history.firstOrNull { it.id == event.id }
        val total = event.totalBytes; val bytes = event.transferredBytes
        val stats = if (total != null && bytes != null) {
            old?.stats?.takeIf { it.totalBytes == total && old.state in listOf("Sending", "Receiving", "Verifying") }
                ?.advance(bytes, event.sampledAt) ?: TransferProgress(total, bytes, event.sampledAt)
        } else null
        val row = Transfer(event.id, old?.name ?: event.name, old?.direction ?: event.direction, event.state, event.progress,
            event.path ?: old?.path, event.text ?: old?.text, event.error, autoRetry, old?.time ?: System.currentTimeMillis(), pairID ?: old?.pairID,
            event.folder || old?.folder == true, old?.saved, stats)
        val history = limit(listOf(row) + ui.value.history.filter { it.id != event.id })
        ui.value = ui.value.copy(history = history)
        // New rows are saved right away so a send survives the app closing before it finishes.
        if (old == null || event.state in listOf("Complete", "Interrupted", "Cancelled")) saveHistory(history)
        // A text or file the Mac sends again after the connection dropped arrives only once.
        if (row.direction == "Received" && row.state == "Complete") {
            if (row.text != null) { if (old?.state != "Complete") ReceivedNotifier.show(context, row) }
            else if (row.saved == null && row.path != null) save(row, notify = true)
        }
    }
    /** Copies a received file or folder to shared storage and drops DropDuo's own copy. Runs in the background so the connection keeps going. */
    private fun save(row: Transfer, notify: Boolean) {
        val source = File(row.path ?: return)
        if (!saving.add(row.id)) return
        scope.launch {
            var saved: String? = null
            try {
                saved = SharedStorage.save(context, source, row.name, row.folder)
                synchronized(this@AppState) {
                    val history = ui.value.history.map { if (it.id == row.id) it.copy(saved = saved) else it }
                    ui.value = ui.value.copy(history = history); saveHistory(history)
                }
                source.deleteRecursively()
            } catch (e: Exception) {
                // DropDuo keeps its copy, so the file still opens here, and saving is tried again next time the app starts.
                error("Couldn't save ${row.name} to this phone's storage. It's still in DropDuo.")
            } finally { saving.remove(row.id) }
            if (notify) ReceivedNotifier.show(context, row.copy(saved = saved))
        }
    }
    /** History is also the send queue, so only finished rows count toward its limit. */
    private fun limit(history: List<Transfer>): List<Transfer> { var finished = 0; return history.filter { it.state in ONGOING || it.autoRetry || ++finished <= 100 } }
    private fun cancelled(id: String) = ui.value.history.firstOrNull { it.id == id }?.state == "Cancelled"
    private fun saveHistory(history: List<Transfer>) {
        runCatching { val temp = File(context.filesDir, "history.tmp"); temp.writeText(Wire.gson.toJson(history)); check(temp.renameTo(historyFile())) }
            .onFailure { error("Could not save transfer history") }
    }
    /** Sends now, or holds the text until the Mac reconnects. */
    fun sendText(text: String, onSuccess: () -> Unit = {}) {
        val pairID = ticket?.pairID
        scope.launch { try { PeerEngine.validateText(text); sendStored(UUID.randomUUID().toString(), "Text", pairID, text = text); withContext(Dispatchers.Main) { onSuccess() } } catch (e: Exception) { error(e.message ?: "Could not send text") } }
    }
    fun importAndSend(uris: List<Uri>) {
        val pairID = ticket?.pairID
        scope.launch {
            for (uri in uris) {
                val id = UUID.randomUUID().toString()
                var file: File? = null
                var part: File? = null
                var imported = false
                synchronized(this@AppState) {
                    if (installingUpdate) { error("DropDuo is installing an update. Send again after it reopens."); return@launch }
                    importing.add(id)
                }
                try {
                    var name = "Shared file"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                    val dir = outgoing().apply { mkdirs() }; file = File(dir, id); part = File(dir, "$id.part")
                    record(PeerEvent(id, name, "Sent", "Preparing", path = file.path), pairID = pairID)
                    context.contentResolver.openInputStream(uri)?.use { source -> part.outputStream().use { out ->
                        val buffer = ByteArray(Wire.CHUNK_SIZE); var total = 0L
                        while (true) { check(!cancelledImports.contains(id)) { "Transfer cancelled" }; val n = source.read(buffer); if (n < 0) break; total += n; require(total <= Wire.MAX_FILE) { "Files larger than 32 GB can't be sent" }; require(context.filesDir.usableSpace > n + 1024*1024) { "Not enough free space on this phone to prepare the file" }; out.write(buffer, 0, n) }
                    } } ?: throw IllegalStateException("Couldn't read that file. Try sharing it again from the original app.")
                    check(!cancelledImports.contains(id)) { "Transfer cancelled" }
                    // The copy appears under its final name only once complete, which marks it resumable after a restart.
                    check(part.renameTo(file)) { "Couldn't prepare the file. Try sharing it again." }
                    imported = true
                    sendStored(id, name, pairID, file = file)
                } catch (e: Exception) {
                    part?.delete(); if (!imported) file?.delete()
                    val cancelled = cancelledImports.contains(id) || ui.value.history.firstOrNull { it.id == id }?.state == "Cancelled"
                    record(PeerEvent(id, "Shared file", "Sent", if (cancelled) "Cancelled" else "Interrupted", path = if (imported) file?.path else null, error = e.message))
                    if (!cancelled) error(e.message ?: "Could not send file")
                } finally { importing.remove(id); cancelledImports.remove(id) }
            }
        }
    }
    /**
     * Sends a stored file copy or text; if the Mac is offline or the connection drops, it waits for reconnect instead of failing.
     * [pairID] is the Mac the send is meant for; null sends to whichever Mac is paired.
     */
    private fun sendStored(id: String, name: String, pairID: String?, file: File? = null, text: String? = null) {
        synchronized(this) {
            check(!installingUpdate) { "DropDuo is installing an update. Send again after it reopens." }
            check(sending.add(id)) { "Transfer already active" }
        }
        var peer: PeerEngine? = null
        try {
            val target = ticket?.pairID
            peer = engine?.takeIf { pairID == null || pairID == target } ?: throw Disconnected("Mac is offline")
            if (file != null) { peer.sendFile(file, id, name); file.delete() }
            else { record(PeerEvent(id, name, "Sent", "Sending", text = text), pairID = target); peer.sendText(text!!, id) }
        } catch (e: Exception) {
            if (cancelled(id)) return
            if (e !is java.io.IOException) { record(PeerEvent(id, name, "Sent", "Interrupted", path = file?.path, text = text, error = e.message)); throw e }
            record(PeerEvent(id, name, "Sent", "Interrupted", path = file?.path, text = text, error = if (peer == null) WAITING_FOR_MAC else "Resumes when your Mac reconnects"), autoRetry = true, pairID = pairID)
        } finally { sending.remove(id) }
        // The Mac may have reconnected while this attempt was still failing on the old connection.
        val current = engine
        if (current != null && current !== peer && ui.value.history.firstOrNull { it.id == id }?.autoRetry == true) resumePending()
    }
    /** Called after each connection to send held items and resume ones cut off by a lost connection, oldest first. */
    fun resumePending() {
        if (installingUpdate) return
        val current = ticket?.pairID ?: return
        val rows = ui.value.history.filter { it.direction == "Sent" && it.state == "Interrupted" && it.autoRetry && (it.pairID == null || it.pairID == current) && (it.path != null || it.text != null) && it.id !in sending }.reversed()
        if (rows.isNotEmpty()) scope.launch {
            for (row in rows) try {
                if (ui.value.history.firstOrNull { it.id == row.id }?.autoRetry != true) continue
                resend(row)
            } catch (e: Exception) { record(PeerEvent(row.id, row.name, "Sent", "Interrupted", path = row.path, text = row.text, error = e.message)) }
        }
    }
    private fun resend(row: Transfer, pairID: String? = row.pairID) {
        if (row.text != null) return sendStored(row.id, row.name, pairID, text = row.text)
        val file = File(row.path ?: throw IllegalStateException("Source unavailable")); require(file.exists()) { "Source unavailable. Share it again." }
        sendStored(row.id, row.name, pairID, file = file)
    }
    /** Retrying by hand sends to the Mac paired now, even if the row was meant for an earlier one. */
    fun retry(row: Transfer) { val pairID = ticket?.pairID; scope.launch { try { resend(row, pairID) } catch (e: Exception) { error(e.message ?: "Retry failed") } } }
    fun cancel(row: Transfer) {
        if (importing.contains(row.id)) cancelledImports.add(row.id)
        scope.launch { engine?.cancel(row.id); if (row.direction == "Sent" && !importing.contains(row.id)) row.path?.let { File(it).delete() }; record(PeerEvent(row.id, row.name, row.direction, "Cancelled")) }
    }
    /** Keeps active and waiting sends, so clearing history never drops something still on its way. */
    fun clearHistory() { synchronized(this) { val (kept, cleared) = ui.value.history.partition { it.state in ONGOING || it.autoRetry }; cleared.filter { it.direction == "Sent" }.forEach { row -> row.path?.let { path -> val file = File(path); if (file.parentFile == outgoing()) file.delete() } }; ui.value = ui.value.copy(history = kept); saveHistory(kept) } }
    fun inbox() = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "DropDuo").apply { mkdirs() }
    /**
     * Debug builds only: sample state for UI review without pairing or touching saved history, opened with
     * `adb shell am start -n app.dropduo.android/.MainActivity --es preview <home|offline|empty|onboarding|failed>`.
     * Put an image at `<inbox>/preview/IMG_2031.jpg` to see a received-photo thumbnail.
     */
    fun preview(scene: String) {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis(); val minute = 60_000L; val day = 86_400_000L
        val history = if (scene == "empty") emptyList() else listOf(
            Transfer("preview-1", "IMG_2048.heic", "Sent", "Sending", 0.62, time = now,
                stats = TransferProgress(340_000_000, 200_800_000, TransferProgress.now() - 2).advance(210_800_000)),
            Transfer("preview-2", "Text", "Received", "Complete", 1.0, text = "https://maps.app.goo.gl/dinner-friday", time = now - 4 * minute),
            Transfer("preview-3", "Pick up the printer cable", "Sent", "Interrupted", text = "Pick up the printer cable on the way home", error = WAITING_FOR_MAC, autoRetry = true, time = now - 26 * minute),
            Transfer("preview-4", "Lease agreement.pdf", "Received", "Complete", 1.0, path = File(inbox(), "preview/Lease agreement.pdf").path, time = now - 48 * minute),
            Transfer("preview-8", "Wedding photos", "Received", "Complete", 1.0, path = File(inbox(), "preview/Wedding photos").path, time = now - 50 * minute, folder = true),
            Transfer("preview-5", "Beach sunset.mov", "Sent", "Interrupted", 0.3, error = "Connection lost. Retry when your Mac is back.", time = now - day),
            Transfer("preview-6", "IMG_2031.jpg", "Received", "Complete", 1.0, path = File(inbox(), "preview/IMG_2031.jpg").path, time = now - day - 3 * 60 * minute),
            Transfer("preview-7", "Text", "Sent", "Complete", 1.0, text = "Meeting notes are in the shared folder, check the second page.", time = now - 3 * day),
        )
        ui.value = when (scene) {
            "onboarding" -> UiState()
            "failed" -> UiState(status = "Pairing failed. Generate a new code and try again.")
            "offline" -> UiState(device = "MacBook Pro", status = "Can't reach MacBook Pro. Retrying…", unreachable = true, history = history.filter { it.state !in listOf("Sending", "Preparing") })
            else -> UiState(device = "MacBook Pro", connected = true, status = "Connected to MacBook Pro", history = history)
        }
    }
}

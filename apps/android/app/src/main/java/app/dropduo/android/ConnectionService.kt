package app.dropduo.android

import android.app.*
import android.content.Intent
import android.net.nsd.*
import android.os.*
import app.dropduo.core.*
import java.util.concurrent.Executors

class ConnectionService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var discovery: NsdManager.DiscoveryListener? = null
    private val nsd by lazy { getSystemService(NsdManager::class.java) }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("connection", "Device connection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ConnectionService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "connection").setSmallIcon(app.dropduo.android.R.drawable.ic_dropduo_notification)
            .setContentTitle("DropDuo is ready to share").setContentText("Local device connection is active. Tap to manage.")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Disconnect", stop).build()).build()
        startForeground(1, notification)
        startDiscovery()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (!running) { running = true; worker.execute { connectionLoop() } }
        return START_NOT_STICKY
    }
    private fun connectionLoop() {
        while (running) {
            val ticket = AppState.ticket
            if (ticket != null) {
                try {
                    AppState.update { it.copy(status = "Connecting to ${ticket.name}…") }
                    val channel = SecureChannel.connect(ticket, Build.MODEL.take(60))
                    AppState.pairConfirmed(ticket)
                    val peer = PeerEngine(channel, Inbox(AppState.inbox().resolve(ticket.pairID)).also { it.cleanExpired() }, AppState::record)
                    if (!running || AppState.ticket?.pairID != ticket.pairID) { peer.close(); continue }
                    peer.receivingEnabled = AppState.ui.value.receiving
                    AppState.engine = peer; AppState.update { it.copy(connected = true, device = ticket.name, status = "Connected to ${ticket.name}") }
                    AppState.resumePending()
                    peer.run()
                } catch (e: Exception) {
                    if (running) { AppState.pairFailed(ticket); AppState.update { it.copy(connected = false, status = "Mac not found. Is it awake with DropDuo open, on this Wi-Fi? Retrying…") } }
                } finally { AppState.engine?.close(); AppState.engine = null; AppState.update { it.copy(connected = false) } }
            }
            try { Thread.sleep(4000) } catch (_: InterruptedException) { break }
        }
    }
    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName != AppState.ticket?.discoveryName) return
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val current = AppState.ticket ?: return
                        val host = info.host?.hostAddress ?: return
                        if (info.serviceName == current.discoveryName) AppState.ticket = current.copy(host = host, port = info.port)
                    }
                })
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices("_dropduo._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
    }
    override fun onDestroy() {
        running = false; AppState.engine?.close(); AppState.engine = null; worker.shutdownNow()
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        AppState.update { it.copy(connected = false, status = "Disconnected. Open DropDuo to reconnect.") }
        super.onDestroy()
    }
}

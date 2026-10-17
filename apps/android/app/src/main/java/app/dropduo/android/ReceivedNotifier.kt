package app.dropduo.android

import android.app.*
import android.content.*
import android.os.Build
import android.widget.Toast

/**
 * Tells the user what arrived while DropDuo is out of sight. Arrivals share one notification, so a burst of photos
 * alerts once and then updates quietly.
 */
object ReceivedNotifier {
    private const val ID = 2 // 1 is the connection notification.
    private val unseen = mutableListOf<Transfer>()
    private var alerted = 0L

    @Synchronized fun show(context: Context, row: Transfer) {
        if (AppState.visible) return
        unseen.removeAll { it.id == row.id }; unseen.add(0, row)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("received", "Received files and text", NotificationManager.IMPORTANCE_DEFAULT))
        val device = AppState.ui.value.device ?: "your Mac"
        val single = unseen.singleOrNull()
        // One item opens directly; several open DropDuo's history.
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.TRANSFER, single?.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val quiet = System.currentTimeMillis() - alerted < 10_000
        if (!quiet) alerted = System.currentTimeMillis()
        val builder = Notification.Builder(context, "received").setSmallIcon(R.drawable.ic_dropduo_notification).setColor(0xFF2558DD.toInt())
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(quiet).setDeleteIntent(action(context, "dismiss", null))
        when {
            single == null -> builder.setContentTitle("${unseen.size} items from $device").setContentText(unseen.joinToString { title(it) })
                .setStyle(Notification.InboxStyle().also { style -> unseen.take(6).forEach { style.addLine(title(it)) } })
            single.text != null -> builder.setContentTitle("${if (linkOf(single.text) != null) "Link" else "Text"} from $device")
                .setContentText(single.text).setStyle(Notification.BigTextStyle().bigText(single.text))
                .addAction(Notification.Action.Builder(null, "Copy", action(context, "copy", single.id)).build())
            else -> builder.setContentTitle("${single.name} from $device").setContentText(SharedStorage.place(single)?.let { place ->
                if (single.folder) "Saved to $place" else "Saved to $place. Tap to open." } ?: "Tap to open it in DropDuo.")
        }
        manager.notify(ID, builder.build())
    }

    /** DropDuo is in front, so its history shows what arrived. */
    @Synchronized fun clear(context: Context) { unseen.clear(); context.getSystemService(NotificationManager::class.java).cancel(ID) }

    @Synchronized fun dismissed() = unseen.clear()

    private fun action(context: Context, name: String, id: String?) = PendingIntent.getBroadcast(context, name.hashCode(),
        Intent(context, NotificationActions::class.java).setAction(name).putExtra(MainActivity.TRANSFER, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class NotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "copy" -> AppState.ui.value.history.firstOrNull { it.id == intent.getStringExtra(MainActivity.TRANSFER) }?.text?.let { text ->
                copy(context, text)
                // Android 13 and later confirm copies on their own.
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                ReceivedNotifier.clear(context)
            }
            "dismiss" -> ReceivedNotifier.dismissed()
        }
    }
}

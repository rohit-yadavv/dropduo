package app.dropduo.android

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        /** The transfer a received notification opens. */
        const val TRANSFER = "transfer"
    }
    private val previewMode get() = BuildConfig.DEBUG && intent.hasExtra("preview")
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) installUpdate()
        else AppUpdater.message("Allow installs from DropDuo to install this update. You can try again when ready.")
    }
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        AppState.finishUpdateInstallation()
        AppUpdater.message("If you cancelled installation, tap Install to try again.")
    }
    private val scan = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { AppState.pair(it); connect() }
    }
    private val files = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) AppState.importAndSend(uris) }
    private val photos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> if (uris.isNotEmpty()) AppState.importAndSend(uris) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { AppState.start(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if (previewMode) {
            AppState.stop(this)
            intent.getStringExtra("preview")?.let(AppState::preview)
        }
        setContent { DropDuoApp(Actions(scan = { scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the code shown on your Mac").setBeepEnabled(false).setOrientationLocked(true)) },
            connect = ::connect, files = { files.launch(arrayOf("*/*")) }, photos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            open = ::openFile, keepConnected = ::keepConnected, installUpdate = ::installUpdate)) }
        if (!previewMode) {
            if (AppState.ticket != null) connect()
            // A recreated activity or one reopened from Recents carries a share or notification tap that was already handled.
            if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) { handleShare(intent); handleNotification(intent) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent); handleNotification(intent) }
    // onStop can come a while after leaving the app; arrivals in that gap would get no notification.
    override fun onResume() {
        super.onResume(); AppState.visible = true; ReceivedNotifier.clear(this)
        if (!previewMode) { AppUpdater.check(); AppState.checkBackground() }
    }
    override fun onPause() { super.onPause(); AppState.visible = false }
    private fun installUpdate() {
        if (!AppUpdater.ui.value.ready) return
        if (AppState.hasActiveTransfers) { AppUpdater.message("Finish or cancel your transfers before installing the update."); return }
        if (!packageManager.canRequestPackageInstalls()) {
            try { installPermission.launch(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { AppUpdater.message("Open Android Settings and allow installs from DropDuo, then try again.") }
            return
        }
        if (!AppState.beginUpdateInstallation()) return
        lifecycleScope.launch {
            try {
                val file = AppUpdater.verifiedApk()
                // Receiving was paused before validation. A receive already accepted may have appeared meanwhile.
                if (AppState.hasActiveTransfers) { AppUpdater.message("Finish or cancel your transfers before installing the update."); AppState.finishUpdateInstallation(); return@launch }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true))
            } catch (e: Exception) {
                AppState.finishUpdateInstallation()
                AppUpdater.discardDownload()
                AppUpdater.message(e.message ?: "Couldn't install the update. Download it again and retry.")
            }
        }
    }
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
    /** Opens what a received notification shows: a file or link directly, anything else in DropDuo. */
    private fun handleNotification(intent: Intent) {
        val id = intent.getStringExtra(TRANSFER) ?: return
        intent.removeExtra(TRANSFER)
        val row = AppState.ui.value.history.firstOrNull { it.id == id } ?: return
        val link = row.text?.let(::linkOf)
        when {
            link != null -> openLink(this, link)
            row.text == null && !row.folder -> openFile(row)
            else -> AppState.update { it.copy(reveal = id) }
        }
    }
    private fun openFile(row: Transfer) {
        val uri = try {
            row.saved?.let(Uri::parse)?.also { contentResolver.openFileDescriptor(it, "r")?.close() }
                ?: FileProvider.getUriForFile(this, "$packageName.files", File(row.path ?: return).also { require(it.exists()) })
        } catch (e: Exception) { AppState.error("This file was moved or deleted."); return }
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, SharedStorage.mime(row.name) ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (e: ActivityNotFoundException) { AppState.error("No app on this phone can open this file.") }
    }
    /** Asks Android to let DropDuo run in the background, so the Mac can reach the phone while the app is closed. */
    private fun keepConnected() {
        try { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
        catch (e: Exception) { runCatching { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }
}

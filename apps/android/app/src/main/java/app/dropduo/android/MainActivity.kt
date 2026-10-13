package app.dropduo.android

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) installUpdate()
        else AppUpdater.message("Allow installs from DropDuo to install this update. You can try again when ready.")
    }
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        AppState.finishUpdateInstallation()
        AppUpdater.message("If you cancelled installation, tap Install to try again.")
    }
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
        setContent { DropDuoApp(Actions(scan = { scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the code shown on your Mac").setBeepEnabled(false).setOrientationLocked(true)) },
            connect = ::connect, files = { files.launch(arrayOf("*/*")) }, save = { row -> row.path?.let { exportSource = File(it); export.launch(row.name) } }, open = ::openFile, installUpdate = ::installUpdate)) }
        if (AppState.ticket != null) connect()
        handleShare(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    override fun onResume() { super.onResume(); AppUpdater.check() }
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

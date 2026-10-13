package app.dropduo.android

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.OutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.MessageDigest

data class UpdateState(val release: UpdateRelease? = null, val checking: Boolean = false, val downloading: Boolean = false,
    val progress: Float = 0f, val ready: Boolean = false, val dismissed: Boolean = false, val message: String? = null)

/** App-owned downloads; Android's installer remains responsible for confirming and applying updates. */
object AppUpdater {
    val ui = MutableStateFlow(UpdateState())
    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operation: Job? = null
    private var readyHash: String? = null
    private val directory get() = File(app.cacheDir, "updates").apply { mkdirs() }
    private val apk get() = File(directory, "update.apk")

    fun init(app: Application) {
        this.app = app
        val cached = app.getSharedPreferences("updates", 0).getString("release", null)
        if (cached != null) {
            val release = runCatching { UpdateRelease.parse(cached, BuildConfig.VERSION_NAME) }.getOrNull()
            val dismissed = app.getSharedPreferences("updates", 0).getString("dismissedVersion", null) == release?.version
            ui.update { it.copy(release = release, dismissed = dismissed) }
        }
    }

    fun check(manual: Boolean = false) {
        if (operation?.isActive == true || ui.value.downloading || ui.value.ready) return
        val preferences = app.getSharedPreferences("updates", 0)
        val now = System.currentTimeMillis()
        val last = preferences.getLong("lastAttempt", 0)
        if (!manual && now >= last && now - last < 86_400_000) return
        preferences.edit().putLong("lastAttempt", now).apply()
        ui.update { it.copy(checking = true, message = null) }
        operation = scope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val data = java.io.ByteArrayOutputStream()
                    fetch(UpdateRelease.API, data, 2L * 1024 * 1024)
                    val json = data.toString(Charsets.UTF_8.name())
                    val parsed = UpdateRelease.parse(json, BuildConfig.VERSION_NAME)
                    preferences.edit().putString("release", if (parsed == null) null else json).apply()
                    parsed
                }
                ui.update { it.copy(release = release, dismissed = if (it.release?.version == release?.version) it.dismissed && !manual else false,
                    message = if (manual && release == null) "You're up to date." else null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (manual) message("Couldn't check for updates. Check your internet connection and try again.") }
            finally { ui.update { it.copy(checking = false) } }
        }
    }

    fun download() {
        val release = ui.value.release ?: return
        if (operation?.isActive == true || ui.value.ready) return
        ui.update { it.copy(downloading = true, progress = 0f, message = null) }
        operation = scope.launch {
            val partial = File(directory, "update.part")
            try {
                withContext(Dispatchers.IO) {
                    apk.delete()
                    val sums = java.io.ByteArrayOutputStream()
                    fetch(release.checksumsUrl, sums, 64 * 1024L)
                    val checksum = UpdateRelease.checksum(sums.toString(Charsets.UTF_8.name()), release.filename)
                    partial.outputStream().use { output ->
                        val bytes = fetch(release.apkUrl, output, release.size) { total -> ui.update { it.copy(progress = total.toFloat() / release.size) } }
                        require(bytes == release.size) { "The update download was incomplete. Try again." }
                    }
                    verify(partial, release, checksum)
                    ensureActive()
                    check(partial.renameTo(apk)) { "Couldn't save the update. Check available storage and try again." }
                    readyHash = checksum
                }
                ui.update { it.copy(ready = true, progress = 1f) }
            } catch (e: CancellationException) {
                message("Download cancelled.")
            } catch (e: Exception) {
                message(if (e is IllegalArgumentException || e is IllegalStateException) e.message ?: "Couldn't verify the update." else "Couldn't download the update. Check your internet connection and available storage, then retry.")
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { partial.delete() }
                ui.update { it.copy(downloading = false) }
            }
        }
    }

    fun cancel() { operation?.cancel() }
    fun dismiss() {
        app.getSharedPreferences("updates", 0).edit().putString("dismissedVersion", ui.value.release?.version).apply()
        ui.update { it.copy(dismissed = true) }
    }
    fun message(text: String) { ui.update { it.copy(message = text) } }
    fun discardDownload() { readyHash = null; ui.update { it.copy(ready = false, progress = 0f) } }

    /** Recheck immediately before handing the file to the system installer. */
    suspend fun verifiedApk(): File = withContext(Dispatchers.IO) {
        val release = ui.value.release ?: error("Check for updates first.")
        val checksum = readyHash ?: error("Download the update first.")
        verify(apk, release, checksum)
        apk
    }

    @Suppress("DEPRECATION")
    internal fun verify(file: File, release: UpdateRelease, checksum: String) {
        require(file.length() == release.size) { "The update download is incomplete. Download it again." }
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) } }
        require(hash.digest().joinToString("") { "%02x".format(it) } == checksum) { "The update couldn't be verified. Download it again." }
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = app.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("The update APK is invalid.")
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        require(archive.packageName == app.packageName && archive.versionName == release.version && archive.longVersionCode > installed.longVersionCode) { "This update doesn't match DropDuo or isn't newer than your installed build." }
        require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "This update requires a newer Android version." }
        val expected = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val actual = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        require(expected.isNotEmpty() && actual == expected) { "This update uses a different signing key and can't replace this build. Your pairing and files have been kept." }
    }

    /** Bounded streaming with HTTPS-only redirects to GitHub's release storage. */
    private suspend fun fetch(address: String, output: OutputStream, limit: Long, progress: (Long) -> Unit = {}): Long {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") && url.userInfo == null && (url.port == -1 || url.port == 443)) { "Unexpected update download location" }
            val connection = url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "DropDuo")
            connection.setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            try {
                currentCoroutineContext().ensureActive()
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) { url = URL(url, connection.getHeaderField("Location") ?: error("Missing download redirect")); return@repeat }
                check(status == 200) { "The update server couldn't provide the download. Try again later." }
                require(connection.contentLengthLong <= limit) { "The update response is too large." }
                var total = 0L
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        require(total <= limit) { "The update response is too large." }
                        output.write(buffer, 0, count); progress(total)
                    }
                }
                return total
            } finally { connection.disconnect() }
        }
        error("Too many update redirects.")
    }
}

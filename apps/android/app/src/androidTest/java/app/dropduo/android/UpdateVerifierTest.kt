package app.dropduo.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class UpdateVerifierTest {
    @Test fun realApksRequireTheSameSigningKeyNewerBuildAndMatchingChecksum() {
        assumeTrue("Run scripts/check-updates-emulator", InstrumentationRegistry.getArguments().getString("dropduo.updates") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "update-fixtures")
        val good = File(directory, "good.apk")
        val otherKey = File(directory, "other-key.apk")
        fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        fun release(file: File) = UpdateRelease("99.0.0", "Fixture", "", file.length(), "")
        AppUpdater.verify(good, release(good), hash(good))
        assertThrows(IllegalArgumentException::class.java) { AppUpdater.verify(good, release(good), "0".repeat(64)) }
        val rejected = assertThrows(IllegalArgumentException::class.java) { AppUpdater.verify(otherKey, release(otherKey), hash(otherKey)) }
        assertTrue(rejected.message.orEmpty().contains("different signing key"))
        val installed = File(context.applicationInfo.sourceDir)
        assertThrows(IllegalArgumentException::class.java) { AppUpdater.verify(installed, release(installed).copy(version = BuildConfig.VERSION_NAME), hash(installed)) }
    }
}

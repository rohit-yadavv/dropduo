package app.nearport.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nearport.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceInteropTest {
    @Test fun nativeServicePairingAndBidirectionalFiles() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Run scripts/check-emulator", args.getString("nearport.interop") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val code = File(context.getExternalFilesDir(null), "interop-ticket.txt").readText()
        val ticket = Ticket.parse(code)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(1000)
        AppState.pair(code)
        Thread.sleep(250)
        AppState.start(context)
        val deadline = System.currentTimeMillis() + 30000
        while (!AppState.ui.value.connected && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(AppState.ui.value.status, AppState.ui.value.connected)
        assertEquals(ticket.pairID, AppState.secure.load()?.pairID)
        val source = File(context.cacheDir, "android.bin").apply { writeBytes(ByteArray(800123) { (it % 251).toByte() }) }
        val peer = AppState.engine ?: error("No peer")
        peer.sendFile(source, "33333333-3333-4333-8333-333333333333")
        peer.sendText("Android to Mac text")
        while (AppState.ui.value.history.none { it.direction == "Received" && it.text == "Interop complete" } && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val file = AppState.ui.value.history.firstOrNull { it.id == "22222222-2222-4222-8222-222222222222" && it.state == "Complete" }
        assertNotNull("Mac file received", file)
        assertEquals(Wire.hash(source), Wire.hash(File(file!!.path!!)))
        assertTrue(AppState.ui.value.history.any { it.text == "Mac to Android text" })
        assertTrue("Swift verified both directions before disconnect", AppState.ui.value.history.any { it.text == "Interop complete" })
        AppState.setReceiving(false); assertFalse(peer.receivingEnabled)
        AppState.setReceiving(true)
        source.delete()
        AppState.forget(context)
        AppState.clearHistory()
        AppState.inbox().resolve(ticket.pairID).deleteRecursively()
    }
}

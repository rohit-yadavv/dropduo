package app.dropduo.core

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InteropTest {
    @Test fun bidirectionalResumedTransfersWithSwift() {
        val path = System.getProperty("dropduo.interop", "")
        assumeTrue("Run scripts/interop to enable the real Swift/JVM test", path.isNotEmpty())
        val dir = File(path)
        val ticket = Ticket.parse(File(dir, "ticket.txt").readText())
        assertTrue(runCatching { SecureChannel.connect(ticket.copy(secret = Wire.b64(ByteArray(32) { 9 })), "Wrong key") }.isFailure)
        val source = File(dir, "android.bin").apply { writeBytes(ByteArray(800123) { (it % 251).toByte() }) }
        val expectedHash = Wire.hash(source)
        val inbox = Inbox(File(dir, "android-received"))
        val offer = Message("offer", "22222222-2222-4222-8222-222222222222", "mac.bin", source.length(), expectedHash)
        inbox.prepare(offer); inbox.append(offer, 0, source.readBytes().copyOf(120_000))
        val received = CountDownLatch(2)
        val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
        val peer = PeerEngine(SecureChannel.connect(ticket, "JVM Android"), inbox) { event ->
            if (event.state == "Complete" && event.direction == "Received") {
                if (event.path != null) { if (Wire.hash(File(event.path)) != expectedHash) errors.add("Checksum mismatch"); received.countDown() }
                if (event.text == "Mac to Android text") received.countDown()
            }
        }
        val reader = Thread { try { peer.run() } catch (_: Exception) {} }.apply { isDaemon = true; start() }
        try {
            peer.sendFile(source, "33333333-3333-4333-8333-333333333333")
            peer.sendText("Android to Mac text")
            assertTrue("Mac file and text arrived", received.await(30, TimeUnit.SECONDS))
            assertTrue(errors.toString(), errors.isEmpty())
            val deadline = System.currentTimeMillis() + 10000
            while (!File(dir, "passed.txt").exists() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue("Swift verified its received files", File(dir, "passed.txt").exists())
            assertEquals(source.length(), inbox.destination(offer).length())
            assertEquals(expectedHash, Wire.hash(inbox.destination(offer)))
        } finally { peer.close(); reader.join(2000) }
    }
}

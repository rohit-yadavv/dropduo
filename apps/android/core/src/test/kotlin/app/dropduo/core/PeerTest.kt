package app.dropduo.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class PeerTest {
    @Test fun invalidChunkStopsReceivingProgressAndReportsFailure() {
        val root = Files.createTempDirectory("dropduo-peer").toFile()
        val listener = ServerSocket(0)
        val socket = Socket("127.0.0.1", listener.localPort)
        val remote = listener.accept()
        listener.close()
        socket.soTimeout = 5000
        val client = SecureChannel(socket, ByteArray(32) { 1 }, ByteArray(32) { 2 })
        val events = CopyOnWriteArrayList<PeerEvent>()
        val peer = PeerEngine(SecureChannel(remote, ByteArray(32) { 2 }, ByteArray(32) { 1 }), Inbox(root), events::add)
        val reader = thread { runCatching { peer.run() } }
        try {
            val source = File(root, "source").apply { writeText("abc") }
            val id = UUID.randomUUID().toString()
            client.send(Message("offer", id, "file.txt", 3, Wire.hash(source)))
            assertEquals("accept", client.receive().type)
            client.send(Message("chunk", id, offset = Long.MAX_VALUE, data = Wire.b64(byteArrayOf(1))))
            assertEquals("error", client.receive().type)
            assertTrue(events.any { it.id == id && it.state == "Interrupted" })
            assertFalse(File(root, "$id-file.txt").exists())
        } finally {
            client.close(); peer.close(); reader.join(5000); root.deleteRecursively()
        }
        assertFalse("Reader must stop on disconnect", reader.isAlive)
    }
}

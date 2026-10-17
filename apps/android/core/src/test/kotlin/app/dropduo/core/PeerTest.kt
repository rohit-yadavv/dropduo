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
    @Test fun resumedAndEmptySendsReportBytesAndVerifyBeforeCompleting() {
        for (size in listOf(0, Wire.CHUNK_SIZE + 17)) {
            val root = Files.createTempDirectory("dropduo-progress").toFile()
            val (local, remote) = sockets()
            val sent = CopyOnWriteArrayList<PeerEvent>()
            val received = CopyOnWriteArrayList<PeerEvent>()
            val source = File(root, "source.bin").apply { writeBytes(ByteArray(size) { (it % 251).toByte() }) }
            val id = UUID.randomUUID().toString()
            val inbox = Inbox(File(root, "received"))
            val offer = Message("offer", id, source.name, source.length(), Wire.hash(source))
            inbox.prepare(offer)
            val offset = if (size == 0) 0L else 100L
            if (offset > 0) inbox.append(offer, 0, source.readBytes().copyOf(offset.toInt()))
            val sender = PeerEngine(SecureChannel(local, ByteArray(32) { 1 }, ByteArray(32) { 2 }), Inbox(File(root, "sent")), sent::add)
            val receiver = PeerEngine(SecureChannel(remote, ByteArray(32) { 2 }, ByteArray(32) { 1 }), inbox, received::add)
            val sendReader = thread { runCatching { sender.run() } }
            val receiveReader = thread { runCatching { receiver.run() } }
            try {
                sender.sendFile(source, id)
                for ((events, phase) in listOf(sent to "Sending", received to "Receiving")) {
                    val initial = events.first { it.state == phase }
                    assertEquals(offset, initial.transferredBytes)
                    assertEquals(size.toLong(), initial.totalBytes)
                    assertEquals(if (size == 0) 1.0 else offset.toDouble()/size, initial.progress, 0.0)
                    val verifying = events.indexOfFirst { it.state == "Verifying" }
                    val complete = events.indexOfFirst { it.state == "Complete" }
                    assertTrue(events.toString(), verifying >= 0 && complete > verifying)
                    assertEquals(size.toLong(), events[verifying].transferredBytes)
                }
                assertArrayEquals(source.readBytes(), inbox.destination(offer).readBytes())
            } finally {
                sender.close(); receiver.close(); sendReader.join(5000); receiveReader.join(5000); root.deleteRecursively()
            }
            assertFalse(sendReader.isAlive)
            assertFalse(receiveReader.isAlive)
        }
    }

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
    @Test fun savedFileIsRecordedCompleteWhenTheReplyFails() {
        val root = Files.createTempDirectory("dropduo-peer").toFile()
        val (local, remote) = sockets()
        val client = SecureChannel(remote, ByteArray(32) { 2 }, ByteArray(32) { 1 })
        val events = CopyOnWriteArrayList<PeerEvent>()
        val peer = PeerEngine(SecureChannel(local, ByteArray(32) { 1 }, ByteArray(32) { 2 }), Inbox(root), events::add)
        val reader = thread { runCatching { peer.run() } }
        try {
            val source = File(root, "source").apply { writeText("abc") }
            val id = UUID.randomUUID().toString()
            client.send(Message("offer", id, "file.txt", 3, Wire.hash(source)))
            assertEquals("accept", client.receive().type)
            client.send(Message("chunk", id, offset = 0, data = Wire.b64("abc".toByteArray())))
            assertEquals("ack", client.receive().type)
            local.shutdownOutput()
            client.send(Message("finish", id))
            reader.join(5000)
            assertTrue(events.toString(), events.any { it.id == id && it.state == "Complete" && it.path != null })
        } finally { client.close(); peer.close(); reader.join(5000); root.deleteRecursively() }
    }
    @Test fun cancelledTextStopsWaitingAndIsNotConfirmed() {
        val root = Files.createTempDirectory("dropduo-peer").toFile()
        val (local, remote) = sockets()
        val events = CopyOnWriteArrayList<PeerEvent>()
        val peer = PeerEngine(SecureChannel(local, ByteArray(32) { 1 }, ByteArray(32) { 2 }), Inbox(root), events::add)
        val id = UUID.randomUUID().toString()
        var failure: Throwable? = null
        val sender = thread { failure = runCatching { peer.sendText("hello", id) }.exceptionOrNull() }
        try {
            Thread.sleep(200); peer.cancel(id); sender.join(5000)
            assertFalse("Cancelled text must stop waiting", sender.isAlive)
            assertNotNull(failure)
            assertTrue(events.toString(), events.any { it.id == id && it.state == "Cancelled" })
            assertFalse(events.any { it.id == id && it.state == "Complete" })
        } finally { remote.close(); peer.close(); root.deleteRecursively() }
    }
    private fun sockets(): Pair<Socket, Socket> {
        val listener = ServerSocket(0)
        val local = Socket("127.0.0.1", listener.localPort)
        val remote = listener.accept(); listener.close()
        remote.soTimeout = 5000
        return local to remote
    }
}

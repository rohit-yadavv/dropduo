package app.dropduo.core

import java.io.*
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SecureChannel(val socket: Socket, private val sendKey: ByteArray, private val receiveKey: ByteArray) : Closeable {
    private val sender = FrameCipher(sendKey)
    private val receiver = FrameCipher(receiveKey)
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    @Synchronized fun send(message: Message) { Wire.writeFrame(output, sender.seal(Wire.gson.toJson(message).toByteArray())) }
    fun receive(): Message = Wire.gson.fromJson(String(receiver.open(Wire.readFrame(input))), Message::class.java)
    override fun close() { socket.close() }
    companion object {
        fun connect(ticket: Ticket, name: String, host: String = ticket.host, port: Int = ticket.port): SecureChannel {
            ticket.validate(); require(name.toByteArray().size <= 128)
            val socket = Socket()
            try {
                socket.connect(java.net.InetSocketAddress(host, port), 8000); socket.soTimeout = 120_000; socket.tcpNoDelay = true
                val input = DataInputStream(socket.getInputStream()); val output = DataOutputStream(socket.getOutputStream())
                val client = Wire.random(32); val hello = Hello(pairID = ticket.pairID, name = name, nonce = Wire.b64(client))
                val secret = Wire.decode(ticket.secret); hello.proof = Wire.hmac(secret, hello.transcript())
                Wire.writeFrame(output, Wire.gson.toJson(hello).toByteArray())
                val welcome = Wire.gson.fromJson(String(Wire.readFrame(input, 4096)), Welcome::class.java)
                val server = Wire.decode(welcome.nonce)
                require(welcome.version == 1 && server.size == 32 && Wire.verify(welcome.proof, secret,
                    "dropduo/1/welcome|${ticket.pairID}|${hello.nonce}|${welcome.nonce}")) { "Mac authentication failed" }
                socket.soTimeout = 0
                return SecureChannel(socket, Wire.derive(secret, client, server, "c2s"), Wire.derive(secret, client, server, "s2c"))
            } catch (error: Exception) { socket.close(); throw error }
        }
    }
}
/** The connection dropped mid-transfer; the sender resumes automatically on reconnect. */
class Disconnected(message: String) : IOException(message)
data class PeerEvent(val id: String, val name: String, val direction: String, val state: String,
    val progress: Double = 0.0, val path: String? = null, val text: String? = null, val error: String? = null)
const val CONNECTION_LOST = "Connection lost. It resumes when the devices reconnect."
private const val PAUSED = "Receiving is paused on the other device. Turn it back on there, then retry."
private const val TEXT_TOO_LONG = "Text is too long to send. Share it as a file instead."
class PeerEngine(private val channel: SecureChannel, private val inbox: Inbox, private val event: (PeerEvent) -> Unit) : Closeable {
    private val incoming = ConcurrentHashMap<String, Message>()
    private val responses = ConcurrentHashMap<String, LinkedBlockingQueue<Message>>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val active = ConcurrentHashMap.newKeySet<String>()
    @Volatile var receivingEnabled = true
    @Volatile private var alive = true
    fun run() { try { while (alive) handle(channel.receive()) } finally { alive = false; incoming.values.forEach { event(PeerEvent(it.id!!, it.name!!, "Received", "Interrupted", error = "Connection lost. The sender can retry when the devices reconnect.")) }; incoming.clear(); channel.close() } }
    override fun close() { alive = false; channel.close() }
    fun cancel(id: String) {
        if (!active.contains(id) && !incoming.containsKey(id)) return
        cancelled.add(id); incoming.remove(id); runCatching { inbox.cancel(id) }; runCatching { channel.send(Message("cancel", id)) }
        event(PeerEvent(id, "Transfer", "", "Cancelled")); if (!active.contains(id)) cancelled.remove(id)
    }
    private fun wait(id: String): Message {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (alive && System.nanoTime() < deadline) {
            check(!cancelled.contains(id)) { "Transfer cancelled" }
            val reply = responses.getValue(id).poll(100, TimeUnit.MILLISECONDS) ?: continue
            check(reply.type != "error") { reply.error ?: "Transfer rejected" }; return reply
        }
        throw Disconnected(CONNECTION_LOST)
    }
    fun sendFile(file: File, id: String = UUID.randomUUID().toString(), displayName: String = file.name) {
        check(active.size < 4 && active.add(id)) { "Transfer already active" }
        cancelled.remove(id); responses[id] = LinkedBlockingQueue(8)
        try {
            event(PeerEvent(id, displayName, "Sent", "Preparing"))
            require(file.isFile) { "Only files can be sent, not folders" }; require(file.length() <= Wire.MAX_FILE) { "Files larger than 32 GB can't be sent" }
            val size = file.length(); val offer = Message("offer", id, displayName, size, Wire.hash(file))
            check(!cancelled.contains(id)) { "Transfer cancelled" }
            Inbox.validate(offer); channel.send(offer)
            val reply = wait(id)
            check(reply.type == "accept" && reply.offset != null && reply.offset in 0..size) { "Invalid resume response" }
            var sent = reply.offset!!
            RandomAccessFile(file, "r").use { source ->
                source.seek(sent); val buffer = ByteArray(Wire.CHUNK_SIZE)
                while (sent < size) {
                    check(!cancelled.contains(id)) { "Transfer cancelled" }
                    val count = source.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt())
                    check(count > 0) { "Source file changed" }
                    channel.send(Message("chunk", id, offset = sent, data = Wire.b64(buffer.copyOf(count))))
                    val ack = wait(id); check(ack.type == "ack" && ack.offset == sent + count) { "Invalid transfer acknowledgement" }
                    sent += count; event(PeerEvent(id, displayName, "Sent", "Sending", if (size == 0L) 1.0 else sent.toDouble()/size))
                }
            }
            channel.send(Message("finish", id)); check(wait(id).type == "complete") { "File was not confirmed" }
            event(PeerEvent(id, displayName, "Sent", "Complete", 1.0, file.path))
        } catch (error: Exception) {
            val lost = !cancelled.contains(id) && (!alive || error is java.io.IOException || error is Disconnected)
            event(PeerEvent(id, displayName, "Sent", if (cancelled.contains(id)) "Cancelled" else "Interrupted", path = file.path, error = if (lost) CONNECTION_LOST else error.message)); throw error
        } finally { active.remove(id); cancelled.remove(id); responses.remove(id) }
    }
    fun sendText(text: String) {
        require(text.isNotEmpty()) { "Type something to send" }; require(text.toByteArray().size <= 64000) { TEXT_TOO_LONG }
        val id = UUID.randomUUID().toString(); responses[id] = LinkedBlockingQueue(8)
        try {
            channel.send(Message("text", id, text = text)); check(wait(id).type == "complete") { "Text was not confirmed" }
            event(PeerEvent(id, "Text", "Sent", "Complete", 1.0, text = text))
        } finally { responses.remove(id) }
    }
    private fun handle(message: Message) {
        val id = message.id ?: error("Missing message ID")
        require(UUID.fromString(id).toString().equals(id, true)) { "Invalid message ID" }
        when (message.type) {
            "accept", "ack", "complete", "error" -> responses[id]?.offer(message)
            "cancel" -> { if (active.contains(id) || incoming.containsKey(id)) { cancelled.add(id); incoming.remove(id); inbox.cancel(id); event(PeerEvent(id, "Transfer", "", "Cancelled")); if (!active.contains(id)) cancelled.remove(id) } }
            else -> try {
                when (message.type) {
                    "offer" -> {
                        check(receivingEnabled) { PAUSED }; check(incoming.size < 4) { "The other device is already receiving 4 files. Retry when they finish." }
                        val offset = inbox.prepare(message); incoming[id] = message
                        event(PeerEvent(id, message.name!!, "Received", "Receiving")); channel.send(Message("accept", id, offset = offset))
                    }
                    "chunk" -> {
                        val offer = incoming[id] ?: error("Unknown transfer")
                        val next = inbox.append(offer, message.offset ?: error("Missing offset"), Wire.decode(message.data ?: error("Missing chunk")))
                        channel.send(Message("ack", id, offset = next))
                        event(PeerEvent(id, offer.name!!, "Received", "Receiving", next.toDouble()/maxOf(1L, offer.size!!)))
                    }
                    "finish" -> {
                        val offer = incoming[id] ?: error("Unknown transfer")
                        val file = inbox.finish(offer); incoming.remove(id); channel.send(Message("complete", id))
                        event(PeerEvent(id, offer.name!!, "Received", "Complete", 1.0, file.path))
                    }
                    "text" -> {
                        check(receivingEnabled) { PAUSED }; check(message.text != null && message.text.toByteArray().size <= 64000) { TEXT_TOO_LONG }
                        event(PeerEvent(id, "Text", "Received", "Complete", 1.0, text = message.text)); channel.send(Message("complete", id))
                    }
                    else -> error("Unsupported message")
                }
            } catch (error: Exception) {
                incoming.remove(id)?.let { event(PeerEvent(id, it.name!!, "Received", "Interrupted", error = error.message)) }
                channel.send(Message("error", id, error = error.message ?: "Transfer failed"))
            }
        }
    }
}

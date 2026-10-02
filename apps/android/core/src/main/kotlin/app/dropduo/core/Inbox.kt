package app.dropduo.core

import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class Inbox(val root: File) {
    private val partials = File(root, ".partial").apply { mkdirs() }
    companion object {
        fun validate(offer: Message) {
            require(offer.id != null && UUID.fromString(offer.id).toString().equals(offer.id, true)) { "Invalid transfer ID" }
            require(!offer.name.isNullOrEmpty() && offer.name != "." && offer.name != ".." && offer.name.toByteArray().size <= 218 &&
                !offer.name.contains('/') && !offer.name.contains('\\') && offer.name.none { it.code < 32 }) { "Invalid filename" }
            require(offer.size != null && offer.size in 0..Wire.MAX_FILE && offer.sha256?.matches(Regex("[0-9a-f]{64}")) == true) { "Invalid file metadata" }
        }
    }
    private fun part(id: String, suffix: String): File {
        require(UUID.fromString(id).toString().equals(id, true)); return File(partials, id + suffix)
    }
    fun destination(offer: Message) = File(root, "${offer.id}-${offer.name}")
    @Synchronized fun prepare(offer: Message): Long {
        validate(offer)
        val target = destination(offer)
        if (target.exists()) { require(target.length() == offer.size && Wire.hash(target) == offer.sha256) { "Completed file changed" }; return offer.size!! }
        val meta = part(offer.id!!, ".json"); val file = part(offer.id, ".part")
        if (meta.exists()) {
            val old = Wire.gson.fromJson(meta.readText(), Message::class.java)
            require(old.name == offer.name && old.size == offer.size && old.sha256 == offer.sha256) { "Resume metadata mismatch" }
        } else meta.writeText(Wire.gson.toJson(offer))
        if (!file.exists()) file.createNewFile()
        val offset = file.length()
        require(offset <= offer.size!!) { "Invalid partial size" }
        require(offer.size - offset < root.usableSpace) { "Not enough storage" }
        return offset
    }
    @Synchronized fun append(offer: Message, offset: Long, data: ByteArray): Long {
        require(data.isNotEmpty() && data.size <= Wire.CHUNK_SIZE && offset >= 0 && offset <= offer.size!! && data.size <= offer.size - offset) { "Invalid chunk" }
        RandomAccessFile(part(offer.id!!, ".part"), "rw").use {
            require(it.length() == offset) { "Chunk offset mismatch" }; it.seek(offset); it.write(data); it.fd.sync()
        }
        return offset + data.size
    }
    @Synchronized fun finish(offer: Message): File {
        val target = destination(offer); if (target.exists()) { require(target.length() == offer.size && Wire.hash(target) == offer.sha256) { "Completed file changed" }; return target }
        val file = part(offer.id!!, ".part")
        if (file.length() != offer.size || Wire.hash(file) != offer.sha256) { cancel(offer.id); error("Integrity verification failed; retry the file") }
        require(file.renameTo(target)) { "Could not save received file" }; part(offer.id, ".json").delete(); return target
    }
    @Synchronized fun cancel(id: String) { part(id, ".part").delete(); part(id, ".json").delete() }
    fun cleanExpired() { partials.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 7L * 86400_000 }?.forEach { it.delete() } }
}

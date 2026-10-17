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
            require(offer.kind == null || offer.kind == "folder") { "This version of DropDuo can't receive this. Update it, then try again." }
        }
    }
    private fun part(id: String, suffix: String): File {
        require(UUID.fromString(id).toString().equals(id, true)); return File(partials, id + suffix)
    }
    /** Where a received file is saved; a received folder is unpacked into a directory here. */
    fun destination(offer: Message) = File(root, "${offer.id}-${offer.name}")
    /** A folder appears at its destination only once fully unpacked, so one already there is complete. */
    private fun completed(offer: Message, target: File) =
        if (offer.kind == "folder") require(target.isDirectory) { "Completed folder changed" }
        else require(target.length() == offer.size && Wire.hash(target) == offer.sha256) { "Completed file changed" }
    /** Finishing leaves this marker, so an offer sent again after the file moved elsewhere completes without arriving twice. */
    private fun delivered(offer: Message): Boolean {
        val marker = part(offer.id!!, ".done"); if (!marker.exists()) return false
        require(marker.readText() == offer.sha256) { "Completed file changed" }; return true
    }
    @Synchronized fun prepare(offer: Message): Long {
        validate(offer)
        val target = destination(offer)
        if (target.exists()) { completed(offer, target); return offer.size!! }
        if (delivered(offer)) return offer.size!!
        val meta = part(offer.id!!, ".json"); val file = part(offer.id, ".part")
        if (meta.exists()) {
            val old = Wire.gson.fromJson(meta.readText(), Message::class.java)
            require(old.name == offer.name && old.size == offer.size && old.sha256 == offer.sha256) { "Resume metadata mismatch" }
        } else meta.writeText(Wire.gson.toJson(offer))
        if (!file.exists()) file.createNewFile()
        val offset = file.length()
        require(offset <= offer.size!!) { "Invalid partial size" }
        // A folder needs room for its archive and, briefly, its unpacked copy.
        require(offer.size - offset + (if (offer.kind == "folder") offer.size else 0) < root.usableSpace) { "Not enough free space on the receiving device" }
        return offset
    }
    @Synchronized fun append(offer: Message, offset: Long, data: ByteArray): Long {
        require(data.isNotEmpty() && data.size <= Wire.CHUNK_SIZE && offset >= 0 && offset <= offer.size!! && data.size <= offer.size - offset) { "Invalid chunk" }
        RandomAccessFile(part(offer.id!!, ".part"), "rw").use {
            require(it.length() == offset) { "Chunk offset mismatch" }; it.seek(offset); it.write(data); it.fd.sync()
        }
        return offset + data.size
    }
    /** Returns the received file or folder, or null when it already arrived and was moved elsewhere. */
    @Synchronized fun finish(offer: Message): File? {
        val target = destination(offer); if (target.exists()) { completed(offer, target); return target }
        if (delivered(offer)) return null
        val file = part(offer.id!!, ".part")
        if (file.length() != offer.size || Wire.hash(file) != offer.sha256) { cancel(offer.id); error("Integrity verification failed; retry the file") }
        if (offer.kind == "folder") {
            val unpacked = part(offer.id, ".folder").apply { deleteRecursively() }
            try { FolderArchive.extract(file, unpacked) } catch (e: Exception) { cancel(offer.id); throw e }
            require(unpacked.renameTo(target)) { "Could not save the received folder" }; file.delete()
        } else require(file.renameTo(target)) { "Could not save received file" }
        part(offer.id, ".done").writeText(offer.sha256!!); part(offer.id, ".json").delete(); return target
    }
    @Synchronized fun cancel(id: String) { part(id, ".part").delete(); part(id, ".json").delete(); part(id, ".folder").deleteRecursively() }
    fun cleanExpired() { partials.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 7L * 86400_000 }?.forEach { it.deleteRecursively() } }
}

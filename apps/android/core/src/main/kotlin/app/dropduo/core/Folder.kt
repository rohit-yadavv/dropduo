package app.dropduo.core

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

/**
 * Unpacks a folder that arrived as one archive (the Mac's `Payload`): "DDF1", then for each item a kind byte
 * (1 folder, 2 file), a 2-byte path length and the UTF-8 path inside the folder, "/"-separated; files add an
 * 8-byte size and their contents. A 0 byte ends it. Integers are big-endian.
 */
object FolderArchive {
    private const val MAX_PATH = 2048
    private const val INVALID = "The folder's contents are damaged. Send it again."

    /** Writes the folder's contents into [into], refusing any path that would land outside it. */
    fun extract(archive: File, into: File) {
        require(into.isDirectory || into.mkdirs()) { "Could not save the received folder" }
        val root = into.canonicalFile
        DataInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16)).use { input ->
            try {
                require(ByteArray(4).also { input.readFully(it) }.contentEquals("DDF1".toByteArray())) { INVALID }
                val buffer = ByteArray(Wire.CHUNK_SIZE)
                while (true) {
                    val kind = input.readUnsignedByte()
                    if (kind == 0) break
                    require(kind == 1 || kind == 2) { INVALID }
                    val target = resolve(root, ByteArray(input.readUnsignedShort()).also { input.readFully(it) })
                    if (kind == 1) { require(target.isDirectory || target.mkdirs()) { "Could not save the received folder" }; continue }
                    var left = input.readLong()
                    require(left >= 0 && !target.exists()) { INVALID }
                    require(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "Could not save the received folder" }
                    target.outputStream().use { out ->
                        while (left > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                            if (count < 0) throw EOFException()
                            out.write(buffer, 0, count); left -= count
                        }
                    }
                }
                require(input.read() == -1) { INVALID }
            } catch (_: EOFException) { throw IllegalArgumentException(INVALID) }
        }
    }

    private fun resolve(root: File, bytes: ByteArray): File {
        require(bytes.size in 1..MAX_PATH) { INVALID }
        val path = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() } catch (_: CharacterCodingException) { throw IllegalArgumentException(INVALID) }
        val parts = path.split('/')
        require(parts.all { it.isNotEmpty() && it != "." && it != ".." && it.none { c -> c.code < 32 } && it.toByteArray().size <= 255 }) { INVALID }
        return File(root, parts.joinToString(File.separator)).also { require(it.canonicalPath.startsWith(root.path + File.separator)) { INVALID } }
    }
}

package app.dropduo.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class CoreTest {
    @Test fun replayAndTamperRejected() {
        val key = ByteArray(32) { it.toByte() }
        val plain = "DropDuo".toByteArray()
        val frame = FrameCipher(key).seal(plain)
        val receiver = FrameCipher(key)
        assertArrayEquals(plain, receiver.open(frame))
        assertTrue(runCatching { receiver.open(frame) }.isFailure)
        val damaged = frame.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertTrue(runCatching { FrameCipher(key).open(damaged) }.isFailure)
    }
    @Test fun worstCaseChunkFitsInFrame() {
        // 0xFF bytes encode to base64 "/" only, the character JSON encoders may escape.
        val chunk = Message("chunk", UUID.randomUUID().toString(), offset = Long.MAX_VALUE, data = Wire.b64(ByteArray(Wire.CHUNK_SIZE) { -1 }))
        assertTrue(FrameCipher(ByteArray(32)).seal(Wire.gson.toJson(chunk).toByteArray()).size <= Wire.MAX_FRAME)
    }
    @Test fun resumedFileAndPathValidation() {
        val dir = Files.createTempDirectory("dropduo-test").toFile()
        try {
            val source = File(dir, "source").apply { writeText("abcdef") }
            val offer = Message("offer", UUID.randomUUID().toString(), "file.txt", 6, Wire.hash(source))
            val inbox = Inbox(dir)
            assertEquals(0, inbox.prepare(offer)); assertEquals(3, inbox.append(offer, 0, "abc".toByteArray()))
            assertEquals(3, Inbox(dir).prepare(offer)); inbox.append(offer, 3, "def".toByteArray())
            assertEquals("abcdef", inbox.finish(offer).readText())
            assertTrue(runCatching { inbox.prepare(offer.copy(name = "../escape")) }.isFailure)
            assertTrue(runCatching { inbox.prepare(offer.copy(size = -1)) }.isFailure)
        } finally { dir.deleteRecursively() }
    }
    @Test fun emptyFilesIdempotenceCancellationAndFramingLimits() {
        val dir = Files.createTempDirectory("dropduo-edge").toFile()
        try {
            val source = File(dir, "empty").apply { writeBytes(byteArrayOf()) }
            val offer = Message("offer", UUID.randomUUID().toString(), "empty.txt", 0, Wire.hash(source))
            val inbox = Inbox(dir)
            assertEquals(0L, inbox.prepare(offer)); val target = inbox.finish(offer)
            assertTrue(runCatching { inbox.append(offer, Long.MAX_VALUE, byteArrayOf(1)) }.isFailure)
            assertTrue(runCatching { inbox.prepare(offer.copy(name = "a".repeat(219))) }.isFailure)
            assertEquals(target, inbox.finish(offer)); assertEquals(0L, inbox.prepare(offer))
            assertTrue(runCatching { inbox.prepare(offer.copy(size = 1)) }.isFailure)
            val other = offer.copy(id = UUID.randomUUID().toString()); inbox.prepare(other); inbox.cancel(other.id!!)
            assertEquals(0L, inbox.prepare(other))
            val bad = java.io.ByteArrayOutputStream().also { java.io.DataOutputStream(it).writeInt(Wire.MAX_FRAME + 1) }
            assertTrue(runCatching { Wire.readFrame(java.io.DataInputStream(bad.toByteArray().inputStream())) }.isFailure)
            assertTrue(runCatching { FrameCipher(ByteArray(32)).open(ByteArray(35)) }.isFailure)
        } finally { dir.deleteRecursively() }
    }
    /** Builds a folder archive the way the Mac packs one. */
    private class Archive {
        val bytes = java.io.ByteArrayOutputStream().apply { write("DDF1".toByteArray()) }
        private val out = java.io.DataOutputStream(bytes)
        fun folder(path: String) = apply { out.writeByte(1); path(path.toByteArray()) }
        fun file(path: String, contents: ByteArray) = apply { out.writeByte(2); path(path.toByteArray()); out.writeLong(contents.size.toLong()); out.write(contents) }
        fun path(raw: ByteArray) = apply { out.writeShort(raw.size); out.write(raw) }
        fun end() = apply { out.writeByte(0) }
    }
    @Test fun receivedFolderIsUnpackedAndResumable() {
        val dir = Files.createTempDirectory("dropduo-folder").toFile()
        try {
            val big = ByteArray(300_000) { (it % 253).toByte() }
            val archive = Archive().file("a.txt", "abc".toByteArray()).folder("empty").file("empty.txt", ByteArray(0))
                .file("naïve ü.txt", "ü".toByteArray()).folder("sub").file("sub/b.bin", big).end().bytes.toByteArray()
            val source = File(dir, "archive").apply { writeBytes(archive) }
            val offer = Message("offer", UUID.randomUUID().toString(), "Album", archive.size.toLong(), Wire.hash(source), kind = "folder")
            val inbox = Inbox(File(dir, "inbox"))
            assertEquals(0L, inbox.prepare(offer))
            assertEquals(1000L, inbox.append(offer, 0, archive.copyOf(1000)))
            assertEquals(1000L, Inbox(File(dir, "inbox")).prepare(offer))
            var offset = 1000L
            while (offset < archive.size) offset = inbox.append(offer, offset, archive.copyOfRange(offset.toInt(), minOf(archive.size, offset.toInt() + Wire.CHUNK_SIZE)))
            val folder = inbox.finish(offer)
            assertEquals(inbox.destination(offer), folder)
            assertTrue(folder.isDirectory)
            assertEquals("abc", File(folder, "a.txt").readText())
            assertTrue(File(folder, "empty").isDirectory && File(folder, "empty.txt").length() == 0L)
            assertEquals("ü", File(folder, "naïve ü.txt").readText())
            assertArrayEquals(big, File(folder, "sub/b.bin").readBytes())
            assertEquals(listOf("a.txt", "empty", "empty.txt", "naïve ü.txt", "sub"), folder.list()!!.sorted())
            // Only the unpacked folder is left, and a repeated offer or finish finds it complete.
            assertEquals(emptyList<String>(), File(dir, "inbox/.partial").list()!!.toList())
            assertEquals(archive.size.toLong(), inbox.prepare(offer))
            assertEquals(folder, inbox.finish(offer))
            assertTrue(runCatching { inbox.prepare(offer.copy(id = UUID.randomUUID().toString(), kind = "other")) }.isFailure)
        } finally { dir.deleteRecursively() }
    }
    @Test fun damagedOrEscapingFolderArchivesAreRejected() {
        val dir = Files.createTempDirectory("dropduo-folder-bad").toFile()
        try {
            val bad = listOf(
                Archive().file("../escape.txt", "x".toByteArray()).end(),
                Archive().file("/etc/escape.txt", "x".toByteArray()).end(),
                Archive().file("sub/./x", "x".toByteArray()).end(),
                Archive().file("a//b", "x".toByteArray()).end(),
                Archive().file("line\nbreak", "x".toByteArray()).end(),
                Archive().file("twice", "x".toByteArray()).file("twice", "y".toByteArray()).end(),
                Archive().apply { bytes.write(2) }.path(byteArrayOf(0xC3.toByte(), 0x28)).apply { bytes.write(ByteArray(8)) }.end(),
                Archive().file("short", "x".toByteArray()),
                Archive().file("extra", "x".toByteArray()).end().apply { bytes.write(1) },
                Archive().apply { bytes.write(7) },
            )
            bad.forEachIndexed { i, archive ->
                val into = File(dir, "out$i")
                assertTrue("archive $i", runCatching { FolderArchive.extract(File(dir, "a$i").apply { writeBytes(archive.bytes.toByteArray()) }, into) }.isFailure)
                assertFalse("archive $i escaped", File(dir, "escape.txt").exists())
            }
            assertTrue(runCatching { FolderArchive.extract(File(dir, "plain").apply { writeText("PK not ours") }, File(dir, "out-plain")) }.isFailure)
            // A damaged folder is discarded so a retry starts over.
            val archive = bad[0].bytes.toByteArray()
            val source = File(dir, "escaping").apply { writeBytes(archive) }
            val offer = Message("offer", UUID.randomUUID().toString(), "Album", archive.size.toLong(), Wire.hash(source), kind = "folder")
            val inbox = Inbox(File(dir, "inbox"))
            inbox.prepare(offer); inbox.append(offer, 0, archive)
            assertTrue(runCatching { inbox.finish(offer) }.isFailure)
            assertFalse(inbox.destination(offer).exists())
            assertEquals(0L, inbox.prepare(offer))
        } finally { dir.deleteRecursively() }
    }
}

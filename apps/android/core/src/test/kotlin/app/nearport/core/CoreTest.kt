package app.nearport.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class CoreTest {
    @Test fun swiftCompatibilityAndReplay() {
        val file = File(System.getProperty("nearport.fixtures"), "crypto.json")
        val fixture = Wire.gson.fromJson(file.readText(), Map::class.java)
        fun field(name: String) = Wire.decode(fixture[name] as String)
        val key = Wire.derive(field("secret"), field("client"), field("server"), "c2s")
        assertArrayEquals(field("key"), key)
        assertArrayEquals(field("frame"), FrameCipher(key).seal(field("plain"), ByteArray(12) { 3 }))
        val receiver = FrameCipher(key)
        assertArrayEquals(field("plain"), receiver.open(field("frame")))
        assertTrue(runCatching { receiver.open(field("frame")) }.isFailure)
        assertEquals(fixture["helloProof"], Wire.hmac(field("secret"), "nearport/1/hello|fixture|nonce|Android"))
        val damaged = field("frame").also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertTrue(runCatching { FrameCipher(key).open(damaged) }.isFailure)
    }
    @Test fun resumedFileAndPathValidation() {
        val dir = Files.createTempDirectory("nearport-test").toFile()
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
}

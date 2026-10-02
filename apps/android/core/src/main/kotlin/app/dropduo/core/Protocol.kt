package app.dropduo.core

import com.google.gson.Gson
import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Wire {
    const val MAX_FRAME = 400_000
    const val CHUNK_SIZE = 196_608
    const val MAX_FILE = 32L * 1024 * 1024 * 1024
    val gson = Gson()
    fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun b64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)
    fun decode(data: String): ByteArray = Base64.getDecoder().decode(data)
    fun mac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
    }
    fun hmac(key: ByteArray, text: String) = b64(mac(key, text.toByteArray(Charsets.UTF_8)))
    fun verify(proof: String, key: ByteArray, text: String) = runCatching {
        MessageDigest.isEqual(decode(proof), mac(key, text.toByteArray(Charsets.UTF_8)))
    }.getOrDefault(false)
    fun derive(secret: ByteArray, client: ByteArray, server: ByteArray, direction: String): ByteArray {
        val prk = mac(client + server, secret)
        return mac(prk, "dropduo/1/$direction".toByteArray() + byteArrayOf(1))
    }
    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(CHUNK_SIZE); while (true) {
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun readFrame(input: DataInputStream, max: Int = MAX_FRAME): ByteArray {
        val size = input.readInt(); require(size in 1..max) { "Invalid frame size" }
        return ByteArray(size).also { input.readFully(it) }
    }
    fun writeFrame(output: DataOutputStream, bytes: ByteArray) {
        require(bytes.size in 1..MAX_FRAME); output.writeInt(bytes.size); output.write(bytes); output.flush()
    }
}
data class Ticket(val version: Int = 1, val pairID: String, val host: String, val port: Int,
                  val secret: String, val name: String, val expires: Long, val discoveryName: String? = null) {
    fun validate() {
        require(version == 1 && UUID.fromString(pairID).toString().equals(pairID, true) && host.isNotBlank() && port in 1..65535 && Wire.decode(secret).size == 32 && name.toByteArray().size <= 128) { "Invalid pairing code" }
    }
    fun code() = "dropduo://pair/" + Wire.b64(Wire.gson.toJson(this).toByteArray())
    companion object {
        fun parse(code: String): Ticket {
            require(code.startsWith("dropduo://pair/") && code.length < 4096) { "Invalid pairing code" }
            return Wire.gson.fromJson(String(Wire.decode(code.removePrefix("dropduo://pair/"))), Ticket::class.java).also { it.validate() }
        }
    }
}
data class Hello(val version: Int = 1, val pairID: String, val name: String, val nonce: String, var proof: String = "") {
    fun transcript() = "dropduo/1/hello|$pairID|$nonce|$name"
}
data class Welcome(val version: Int = 1, val nonce: String, val proof: String)
data class Message(val type: String, val id: String? = null, val name: String? = null, val size: Long? = null,
    val sha256: String? = null, val offset: Long? = null, val data: String? = null, val text: String? = null, val error: String? = null)
class FrameCipher(private val key: ByteArray) {
    private var sequence = 0L
    fun seal(plain: ByteArray, nonce: ByteArray = Wire.random(12)): ByteArray {
        require(plain.size <= Wire.MAX_FRAME - 36 && sequence >= 0)
        val aad = ByteBuffer.allocate(8).putLong(sequence).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); cipher.updateAAD(aad)
        val result = aad + nonce + cipher.doFinal(plain); sequence++; return result
    }
    fun open(frame: ByteArray): ByteArray {
        require(frame.size in 36..Wire.MAX_FRAME && ByteBuffer.wrap(frame, 0, 8).long == sequence) { "Invalid frame sequence or length" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, frame.copyOfRange(8, 20)))
        cipher.updateAAD(frame.copyOfRange(0, 8))
        val plain = cipher.doFinal(frame.copyOfRange(20, frame.size)); sequence++; return plain
    }
}

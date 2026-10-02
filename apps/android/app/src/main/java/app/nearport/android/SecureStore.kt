package app.nearport.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.nearport.core.*
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val file = File(context.filesDir, "pair.enc")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nearport.pair", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("nearport.pair", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    @Synchronized fun save(ticket: Ticket) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(Wire.gson.toJson(ticket).toByteArray())
        val tmp = File(file.parentFile, "pair.tmp"); tmp.writeBytes(bytes); check(tmp.renameTo(file)) { "Could not save pairing" }
    }
    @Synchronized fun load(): Ticket? {
        if (!file.exists()) return null
        val bytes = file.readBytes(); require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return Wire.gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size))), Ticket::class.java).also { it.validate() }
    }
    @Synchronized fun remove() { file.delete() }
}

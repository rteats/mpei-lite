package com.rteats.mpeineo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Used only for a fresh login after the OWA cookie session expires. */
internal data class MailSavedCredentials(val username: String, val password: String) {
    fun serialize(): ByteArray = JSONObject()
        .put("v", 1)
        .put("username", username)
        .put("password", password)
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun deserialize(bytes: ByteArray): MailSavedCredentials {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            require(json.getInt("v") == 1)
            return MailSavedCredentials(
                json.getString("username"), json.getString("password"),
            ).also {
                require(it.username.isNotBlank() && it.password.isNotBlank())
            }
        }
    }
}

/**
 * Device-only saved mail credentials. Ciphertext is stored in noBackupFilesDir,
 * encrypted with an AES-256/GCM key held in Android Keystore.
 * No unencrypted credentials are written to disk, backup or diagnostics.
 */
internal class MailCredentialStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "mail_login_v1.bin"))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun save(credentials: MailSavedCredentials) {
        require(credentials.username.isNotBlank() && credentials.password.isNotBlank())
        val plaintext = credentials.serialize()
        require(plaintext.size <= MAX_PLAINTEXT_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(data)
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }

    /** Invalidated/corrupt ciphertext is deleted rather than leaking or crashing. */
    fun load(): MailSavedCredentials? {
        if (!file.baseFile.exists()) return null
        return try {
            require(file.baseFile.length() in 30L..(MAX_PLAINTEXT_SIZE + 64L))
            val encrypted = file.openRead().use { it.readBytes() }
            require(encrypted[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, encrypted.copyOfRange(1, 13)),
            )
            val raw = cipher.doFinal(encrypted.copyOfRange(13, encrypted.size))
            require(raw.size <= MAX_PLAINTEXT_SIZE)
            MailSavedCredentials.deserialize(raw)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun hasSavedLogin(): Boolean = file.baseFile.exists()

    fun clear() = file.delete()

    companion object {
        private const val KEY_ALIAS = "mpei_lite_mail_login_v1"
        private const val MAX_PLAINTEXT_SIZE = 8_192
    }
}

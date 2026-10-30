package com.rteats.mpeineo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class BarsSavedSession(
    val savedAt: Long,
    val mainUrl: String,
    val summaryUrl: String,
    val cookieHeaders: List<String>,
)

/** Protect BARS session state with Android Keystore; never use ordinary preferences. */
internal class BarsSessionStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "bars_session_v1.bin"))

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun write(session: BarsSavedSession) {
        require(session.cookieHeaders.isNotEmpty())
        val json = JSONObject().apply {
            put("version", 1)
            put("savedAt", session.savedAt)
            put("mainUrl", session.mainUrl)
            put("summaryUrl", session.summaryUrl)
            put("cookies", JSONArray(session.cookieHeaders))
        }.toString().toByteArray(Charsets.UTF_8)
        require(json.size <= MAX_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val output = byteArrayOf(1) + cipher.iv + cipher.doFinal(json)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(output)
            file.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) file.failWrite(stream)
            throw e
        }
    }

    fun read(): BarsSavedSession? {
        if (!file.baseFile.exists()) return null
        return try {
            require(file.baseFile.length() in 30L..(MAX_SIZE + 128L))
            val data = file.openRead().use { it.readBytes() }
            require(data[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(128, data.copyOfRange(1, 13)))
            val json = JSONObject(
                String(cipher.doFinal(data.copyOfRange(13, data.size)), Charsets.UTF_8),
            )
            require(json.getInt("version") == 1)
            val savedAt = json.getLong("savedAt")
            require(System.currentTimeMillis() - savedAt in 0L..MAX_AGE)
            val items = json.getJSONArray("cookies")
            BarsSavedSession(
                savedAt, json.getString("mainUrl"), json.getString("summaryUrl"),
                (0 until items.length()).map { items.getString(it) },
            ).takeIf { it.cookieHeaders.isNotEmpty() }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() = file.delete()

    companion object {
        private const val KEY_ALIAS = "mpei_lite_bars_session_v1"
        private const val MAX_SIZE = 32768
        private const val MAX_AGE = 30L * 24 * 60 * 60 * 1000
    }
}

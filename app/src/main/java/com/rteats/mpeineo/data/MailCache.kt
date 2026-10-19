package com.rteats.mpeineo.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private encrypted IMAP metadata and message-body cache, excluded from backups. */
internal class MailCache(private val context: Context) {
    private val gson = Gson()
    private val alias = context.packageName + ".mail.cache.aes.v1"
    private val folder get() = File(context.noBackupFilesDir, "mail-cache").apply { mkdirs() }
    private fun keyForAccount(username: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(username.trim().lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun file(username: String, key: String) =
        File(folder, keyForAccount(username) + "_" + key + ".bin")

    @Synchronized fun inbox(username: String): MailInboxSnapshot? =
        read(file(username, "inbox"), CachedInbox::class.java)
            ?.let { MailInboxSnapshot(it.validity, it.items.take(MAX_OWA_HEADERS)) }

    @Synchronized fun saveInbox(username: String, snapshot: MailInboxSnapshot) {
        write(file(username, "inbox"), CachedInbox(snapshot.uidValidity, snapshot.messages.take(60)))
    }

    /**
     * OWA paging and background polling share one encrypted inbox. A fresh head page
     * must not truncate previously downloaded conversations; older pages append.
     * Keep writes atomic under the cache lock and bound metadata storage.
     */
    @Synchronized fun saveOwaPage(username: String, page: List<MailSummary>, offset: Int) {
        synchronized(sharedOwaCacheLock) {
            // The notification worker constructs a separate repository/cache
            // instance. Guard the read-merge-write across both instances.
            val old = inbox(username)?.messages.orEmpty()
            val merged = if (offset == 0) page + old else old + page
            val unique = merged.distinctBy { it.uidValidity to it.uid }.take(MAX_OWA_HEADERS)
            write(file(username, "inbox"), CachedInbox(0x4F5741L, unique))
        }
    }

    /** Sent mail has its own encrypted pages so inbox notifications and cache never mix. */
    @Synchronized fun sent(username: String): MailInboxSnapshot? =
        read(file(username, "sent"), CachedInbox::class.java)
            ?.let { MailInboxSnapshot(it.validity, it.items.take(MAX_OWA_HEADERS)) }

    @Synchronized fun saveOwaSentPage(username: String, page: List<MailSummary>, offset: Int) {
        synchronized(sharedOwaCacheLock) {
            val old = sent(username)?.messages.orEmpty()
            val oldById = old.associateBy { it.uidValidity to it.uid }
            val merged = (if (offset == 0) page + old else old + page)
                .distinctBy { it.uidValidity to it.uid }
                .take(MAX_OWA_HEADERS)
                .map { message ->
                    if (!message.recipients.isNullOrEmpty()) message
                    else oldById[message.uidValidity to message.uid]
                        ?.recipients?.takeIf { it.isNotEmpty() }
                        ?.let { message.copy(recipients = it) } ?: message
                }
            write(file(username, "sent"), CachedInbox(0x4F5741L, merged))
        }
    }

    @Synchronized fun updateSentRecipients(username: String, updates: Map<Long, List<String>>) {
        if (updates.isEmpty()) return
        synchronized(sharedOwaCacheLock) {
            val old = sent(username) ?: return
            val revised = old.messages.map { summary ->
                updates[summary.uid]?.let { summary.copy(recipients = it) } ?: summary
            }
            if (revised != old.messages) {
                write(file(username, "sent"), CachedInbox(old.uidValidity, revised))
            }
        }
    }

    /** Keep the offline inbox's unread state consistent with an acknowledged server update. */
    @Synchronized fun markRead(username: String, uid: Long, validity: Long) {
        synchronized(sharedOwaCacheLock) {
            val snapshot = inbox(username) ?: return
            if (snapshot.uidValidity != validity) return
            val updated = markMailRead(snapshot.messages, uid, validity)
            if (updated != snapshot.messages) {
                write(file(username, "inbox"), CachedInbox(validity, updated))
            }
        }
    }

    @Synchronized fun message(username: String, uid: Long, validity: Long): MailDetail? =
        read(file(username, "body_${validity}_${uid}"), CachedBody::class.java)
            ?.takeIf { it.validity == validity && it.detail.uid == uid }?.detail

    @Synchronized fun saveMessage(username: String, uid: Long, validity: Long, detail: MailDetail) {
        write(file(username, "body_${validity}_${uid}"), CachedBody(validity, detail))
        val prefix = keyForAccount(username) + "_body_"
        val files = folder.listFiles()?.filter { it.name.startsWith(prefix) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        files.forEachIndexed { index, item ->
            if (index >= 24 || item.lastModified() < cutoff) item.delete()
        }
    }

    /** Unsent OWA draft; encrypted using the same Android Keystore key as mail cache. */
    @Synchronized fun draft(username: String): MailDraftSnapshot? =
        read(file(username, "draft"), CachedDraft::class.java)?.let { cached ->
            MailDraftSnapshot(
                mail = cached.mail,
                files = cached.files.take(8).mapNotNull { attachment ->
                    runCatching {
                        OutgoingFile(
                            uri = Uri.parse(attachment.uri), name = attachment.name,
                            mimeType = attachment.mimeType, size = attachment.size,
                        )
                    }.getOrNull()
                },
            )
        }

    @Synchronized fun saveDraft(username: String, draft: MailDraftSnapshot) {
        if (draft.isEmpty()) {
            clearDraft(username)
            return
        }
        write(file(username, "draft"), CachedDraft(
            draft.mail, draft.files.map { file ->
                CachedDraftFile(file.uri.toString(), file.name, file.mimeType, file.size)
            },
        ))
    }

    @Synchronized fun clearDraft(username: String) {
        val target = file(username, "draft")
        target.delete()
        File(target.path + ".bak").delete()
    }

    @Synchronized fun clear(username: String) {
        val prefix = keyForAccount(username) + "_"
        folder.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private fun aesKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
    }

    private fun <T> read(file: File, cls: Class<T>): T? = runCatching {
        // AtomicFile repairs an interrupted write using the last good copy.
        val atomic = AtomicFile(file)
        if (!atomic.baseFile.exists() && !File(file.path + ".bak").exists()) return@runCatching null
        val bytes = atomic.openRead().use { stream ->
            val max = 3_000_000
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > max) return@runCatching null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.size <= 12) return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), cls)
    }.getOrNull()

    private fun write(file: File, value: Any) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey())
            val payload = cipher.iv + cipher.doFinal(gson.toJson(value).toByteArray(Charsets.UTF_8))
            val atomic = AtomicFile(file)
            var stream: java.io.FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(payload)
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                stream?.let(atomic::failWrite)
                throw error
            }
        }
    }

    private companion object {
        const val MAX_OWA_HEADERS = 2000
        val sharedOwaCacheLock = Any()
    }

    private data class CachedInbox(val validity: Long, val items: List<MailSummary>)
    private data class CachedBody(val validity: Long, val detail: MailDetail)
    private data class CachedDraft(val mail: OutgoingMail, val files: List<CachedDraftFile>)
    private data class CachedDraftFile(val uri: String, val name: String,
        val mimeType: String, val size: Long)
}

/** Only one locally stored unsent message is supported per authenticated account. */
internal data class MailDraftSnapshot(
    val mail: OutgoingMail = OutgoingMail(),
    val files: List<OutgoingFile> = emptyList(),
) {
    fun isEmpty() = mail.to.isBlank() && mail.cc.isBlank() && mail.bcc.isBlank() &&
        mail.subject.isBlank() && mail.body.isBlank() && files.isEmpty()
}

/** Only unseen headers are fetched; existing envelopes reuse cached metadata. */
internal fun mergeMailHeaders(
    cached: List<MailSummary>,
    liveFlags: List<Pair<Long, Boolean>>,
    incoming: List<MailSummary>,
    limit: Int = 60,
): List<MailSummary> {
    val byUid = liveFlags.toMap()
    return (incoming + cached.mapNotNull { mail ->
        byUid[mail.uid]?.let { unread -> mail.copy(unread = unread) }
    }).distinctBy { it.uid }.sortedByDescending { it.uid }.take(limit)
}

/** Only the matching UID in the same mailbox generation can become read. */
internal fun markMailRead(
    messages: List<MailSummary>,
    uid: Long,
    validity: Long,
): List<MailSummary> = messages.map { message ->
    if (message.uid == uid && message.uidValidity == validity && message.unread) {
        message.copy(unread = false)
    } else message
}

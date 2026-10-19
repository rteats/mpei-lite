package com.rteats.mpeineo.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentUris
import java.security.MessageDigest

/**
 * Connects an Exchange attachment ID to an actual locally readable content URI.
 * Keys include the mailbox identity, then are hashed; source document URIs
 * never enter mail message caches or diagnostics.
 */
internal class MailAttachmentLocalStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val prefs = context.applicationContext.getSharedPreferences(
        "owa_attachment_locations_v1", Context.MODE_PRIVATE,
    )

    fun remember(account: String?, attachmentId: String?, uri: Uri) {
        val key = attachmentLocationKey(account, attachmentId) ?: return
        if (uri.scheme != "content") return
        prefs.edit().putString(key, uri.toString()).apply()
    }

    /** Drop stored links and their persisted SAF grants when the user signs out. */
    fun clear() {
        prefs.all.values.mapNotNull { it as? String }
            .distinct()
            .forEach { stored ->
                val uri = runCatching { Uri.parse(stored) }.getOrNull()
                if (uri?.scheme == "content") {
                    runCatching {
                        resolver.releasePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        prefs.edit().clear().apply()
    }

    /**
     * Only report files which can actually be opened now. If a document moved,
     * its permission was revoked, or a download was deleted, hide Open.
     *
     * Old downloads in our own MPEI Neo folder are recovered by unambiguous
     * filename + byte size. Never guess between two matching files.
     * Call on Dispatchers.IO, not from composition.
     */
    fun findReadable(account: String?, attachment: MailAttachment): Uri? {
        val key = attachmentLocationKey(account, attachment.owaAttachmentId) ?: return null
        val remembered = prefs.getString(key, null)
        if (remembered != null) {
            val uri = runCatching { Uri.parse(remembered) }.getOrNull()
            if (uri != null && canRead(uri)) return uri
            prefs.edit().remove(key).apply()
        }
        val existing = findUniquePriorDownload(attachment) ?: return null
        if (!canRead(existing)) return null
        remember(account, attachment.owaAttachmentId, existing)
        return existing
    }

    private fun canRead(uri: Uri): Boolean =
        uri.scheme == "content" &&
            runCatching { resolver.openFileDescriptor(uri, "r")?.use { true } == true }
                .getOrDefault(false)

    private fun findUniquePriorDownload(attachment: MailAttachment): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || attachment.size <= 0) return null
        val name = attachmentDownloadName(attachment.name)
        val directory = Environment.DIRECTORY_DOWNLOADS + "/MPEI Neo/"
        val matches = ArrayList<Uri>(2)
        // Restrict recovery to the folder in which THIS app previously saved files.
        runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " +
                    MediaStore.Downloads.SIZE + "=? AND " +
                    MediaStore.Downloads.RELATIVE_PATH + "=? AND " +
                    MediaStore.Downloads.IS_PENDING + "=0",
                arrayOf(name, attachment.size.toString(), directory),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext() && matches.size < 2) {
                    matches.add(ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn)))
                }
            }
        }
        return matches.singleOrNull()
    }
}

/** Pure helpers for attachment indexing and legacy filename recovery. */
internal fun attachmentLocationKey(account: String?, attachmentId: String?): String? {
    if (account.isNullOrBlank() || attachmentId.isNullOrBlank()) return null
    val digest = MessageDigest.getInstance("SHA-256")
        .digest((account + "\u0000" + attachmentId).toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal fun attachmentDownloadName(raw: String): String =
    raw.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\u0000-\u001f]"), "").take(160).ifBlank { "attachment" }

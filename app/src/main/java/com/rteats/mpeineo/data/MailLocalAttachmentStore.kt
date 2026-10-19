package com.rteats.mpeineo.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, account-scoped links to files already present on this device.
 * Never tries to manufacture a filesystem path from an OWA filename.
 */
internal class MailLocalAttachmentStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(
        "owa_attachment_locations_v1", Context.MODE_PRIVATE,
    )
    private val resolver get() = context.contentResolver

    fun persistPickedDocument(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        // Some providers grant only transient access. Readability is rechecked
        // every time before exposing an Open action.
    }

    fun isReadable(uri: Uri): Boolean = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { true } == true
    }.getOrDefault(false)

    fun rememberDownload(account: String?, attachment: MailAttachment, uri: Uri) {
        val id = attachment.owaAttachmentId ?: return
        if (account.isNullOrBlank()) return
        val key = "download_" + localAttachmentDigest("$account|$id")
        prefs.edit().putString(key, uri.toString()).apply()
    }

    fun rememberUpload(
        account: String?,
        draft: OutgoingMail,
        files: List<LocalUploadFile>,
    ) {
        if (account.isNullOrBlank() || files.isEmpty()) return
        val encoded = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("subject", localSubjectDigest(draft.subject))
            .put("files", JSONArray().apply {
                files.forEach { file ->
                    put(JSONObject()
                        .put("name", file.name)
                        .put("size", file.size)
                        .put("uri", file.uri)
                        .put("owa", file.owaAttachmentId ?: ""))
                }
            })
        val key = "uploads_" + localAttachmentDigest(account)
        val previous = readUploads(key)
        val retained = JSONArray().put(encoded)
        for (i in 0 until minOf(previous.length(), 47)) {
            retained.put(previous.optJSONObject(i))
        }
        prefs.edit().putString(key, retained.toString()).apply()
    }

    fun resolve(
        account: String?,
        attachment: MailAttachment,
        sent: Boolean,
        detail: MailDetail?,
    ): Uri? {
        if (account.isNullOrBlank()) return null
        val id = attachment.owaAttachmentId
        if (!id.isNullOrBlank()) {
            val downloaded = prefs.getString(
                "download_" + localAttachmentDigest("$account|$id"), null,
            )
            readableUri(downloaded)?.let { return it }
        }

        // Sent-only: do not associate an arbitrary incoming message with a
        // previously uploaded private file just because the name matches.
        if (sent && detail != null) {
            val source = matchingOriginal(account, attachment, detail)
            readableUri(source)?.let { return it }
        }
        // Files downloaded by earlier app versions have no saved URI mapping.
        // On Android 10+, recognize only one exact name+size match in the
        // app's own Downloads/MPEI Neo directory (never another Downloads file).
        return existingDownload(attachment)
    }

    private fun matchingOriginal(
        account: String, attachment: MailAttachment, detail: MailDetail,
    ): String? {
        val entries = readUploads("uploads_" + localAttachmentDigest(account))
        val snapshots = mutableListOf<LocalSentUpload>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val encodedFiles = entry.optJSONArray("files") ?: continue
            val files = (0 until encodedFiles.length()).mapNotNull { index ->
                encodedFiles.optJSONObject(index)?.let {
                    LocalUploadFile(
                        name = it.optString("name"),
                        size = it.optLong("size", -1L),
                        uri = it.optString("uri"),
                        owaAttachmentId = it.optString("owa").ifBlank { null },
                    )
                }
            }
            snapshots += LocalSentUpload(
                subjectDigest = entry.optString("subject"),
                sentAt = entry.optLong("time", 0L),
                files = files,
            )
        }
        // Exact Exchange attachment IDs take precedence; not all Exchange
        // servers preserve IDs when a draft moves to Sent.
        val exact = snapshots.flatMap { it.files }.filter {
            it.owaAttachmentId == attachment.owaAttachmentId &&
                !attachment.owaAttachmentId.isNullOrBlank() &&
                it.name == attachment.name && it.size == attachment.size.toLong()
        }
        if (exact.size == 1) return exact.single().uri

        // A conversation containing multiple messages has attachment entries
        // from different messages. Do not try to infer which upload they belong to.
        if (detail.threadMessages.size != 1) return null
        val matched = matchSentUpload(
            subject = detail.subject,
            sentAt = detail.sentAt,
            attachmentNamesAndSizes = detail.attachments.map {
                it.name to it.size.toLong()
            },
            candidates = snapshots,
        ) ?: return null
        val same = matched.files.filter {
            it.name == attachment.name && it.size == attachment.size.toLong()
        }
        // Two attachments named the same size cannot be distinguished.
        return same.singleOrNull()?.uri
    }

    private fun existingDownload(attachment: MailAttachment): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            attachment.size < 0 || attachment.name.isBlank()) return null
        val expectedDir = (Environment.DIRECTORY_DOWNLOADS + "/MPEI Neo")
            .trimEnd('/')
        return runCatching {
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val candidates = mutableListOf<Uri>()
            val columns = arrayOf(
                MediaStore.Downloads._ID, MediaStore.Downloads.RELATIVE_PATH,
            )
            resolver.query(
                uri, columns,
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND " +
                    "${MediaStore.Downloads.SIZE}=? AND " +
                    "${MediaStore.Downloads.IS_PENDING}=0",
                arrayOf(attachment.name, attachment.size.toString()),
                null,
            )?.use { cursor ->
                val pathColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Downloads.RELATIVE_PATH,
                )
                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Downloads._ID,
                )
                while (cursor.moveToNext()) {
                    if (cursor.getString(pathColumn)?.trimEnd('/') != expectedDir) continue
                    candidates += ContentUris.withAppendedId(uri, cursor.getLong(idColumn))
                    if (candidates.size > 1) break
                }
            }
            candidates.singleOrNull()?.takeIf(::isReadable)
        }.getOrNull()
    }

    private fun readableUri(value: String?): Uri? {
        if (value.isNullOrBlank()) return null
        return runCatching { Uri.parse(value) }.getOrNull()
            ?.takeIf { it.scheme == "content" && isReadable(it) }
    }

    private fun readUploads(key: String): JSONArray = runCatching {
        JSONArray(prefs.getString(key, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    fun clearAssociations() {
        prefs.edit().clear().apply()
    }
}

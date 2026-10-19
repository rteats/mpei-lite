package com.rteats.mpeineo.data

import java.security.MessageDigest
import kotlin.math.abs

/** A locally selected upload, never inferred from a remotely supplied filename alone. */
internal data class LocalUploadFile(
    val name: String,
    val size: Long,
    val uri: String,
    val owaAttachmentId: String?,
)

internal data class LocalSentUpload(
    val subjectDigest: String,
    val sentAt: Long,
    val files: List<LocalUploadFile>,
)

internal fun localSubjectDigest(subject: String): String = localAttachmentDigest(subject.trim())

internal fun localAttachmentDigest(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * Only accept an unambiguous and recent sent-message match when OWA has
 * rewritten attachment IDs after moving a draft into Sent Items.
 * Name/size matches alone are not sufficient to associate a private document.
 */
internal fun matchSentUpload(
    subject: String,
    sentAt: Long,
    attachmentNamesAndSizes: List<Pair<String, Long>>,
    candidates: List<LocalSentUpload>,
): LocalSentUpload? {
    if (sentAt <= 0 || attachmentNamesAndSizes.isEmpty() ||
        attachmentNamesAndSizes.any { it.second < 0 }) return null
    val signature = attachmentNamesAndSizes.sortedWith(compareBy({ it.first }, { it.second }))
    val possible = candidates.filter { sent ->
        sent.subjectDigest == localSubjectDigest(subject) &&
            abs(sentAt - sent.sentAt) <= 2L * 60_000L &&
            sent.files.size == signature.size &&
            sent.files.map { it.name to it.size }
                .sortedWith(compareBy({ it.first }, { it.second })) == signature
    }
    return possible.singleOrNull()
}

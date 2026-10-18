package com.rteats.mpeineo.data

/** Shared mail data model for OWA inbox, Sent, notifications, and encrypted cache. */
data class MailSummary(
    val uid: Long,
    val uidValidity: Long,
    val sender: String,
    val subject: String,
    val sentAt: Long,
    val unread: Boolean,
    val hasAttachment: Boolean,
    val owaConversationId: String? = null,
    val recipients: List<String>? = null,
)
data class MailInboxSnapshot(val uidValidity: Long, val messages: List<MailSummary>)
data class MailAttachment(
    val name: String,
    val mimeType: String,
    val size: Int,
    val partPath: List<Int>,
    val owaAttachmentId: String? = null,
)
data class MailThreadMessage(
    val sender: String,
    val subject: String,
    val sentAt: Long,
    val body: String,
)

data class MailDetail(
    val uid: Long,
    val sender: String,
    val subject: String,
    val sentAt: Long,
    val body: String,
    val attachments: List<MailAttachment>,
    val threadMessages: List<MailThreadMessage> = emptyList(),
    val recipients: List<String>? = null,
)


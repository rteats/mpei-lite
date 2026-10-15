package com.rteats.mpeineo.data

import android.net.Uri
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Sending schema observed in the MPEI Exchange 2013 OWA service capture. */
internal data class OutgoingMail(
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
)

internal data class OutgoingFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
)

internal object OwaComposeProtocol {
    fun recipients(input: String): List<String> =
        input.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty)

    fun validRecipients(draft: OutgoingMail): Boolean {
        val lists = listOf(recipients(draft.to), recipients(draft.cc), recipients(draft.bcc))
        val address = Regex("^[^\\s@<>;,]+@[^\\s@<>;,]+\\.[^\\s@<>;,]+$")
        return lists[0].isNotEmpty() && lists.flatten().size <= 100 &&
            lists.flatten().all { address.matches(it) }
    }

    private fun mailbox(address: String) = JSONObject()
        .put("Name", address)
        .put("EmailAddress", address)
        .put("RoutingType", "SMTP")
        .put("MailboxType", "Mailbox")
        .put("OriginalDisplayName", address)
        .put("SipUri", "")

    private fun mailboxes(input: String) = JSONArray().apply {
        recipients(input).forEach { put(mailbox(it)) }
    }

    private fun bodyContent(text: String) = JSONObject()
        .put("__type", "BodyContentType:#Exchange")
        .put("BodyType", "HTML")
        .put("Value", "<div>" + text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "<br>") + "</div>")

    private fun message(draft: OutgoingMail) = JSONObject()
        .put("__type", "Message:#Exchange")
        .put("Subject", draft.subject)
        .put("Body", bodyContent(draft.body))
        .put("Importance", "Normal")
        .put("From", JSONObject.NULL)
        .put("ToRecipients", mailboxes(draft.to))
        .put("CcRecipients", mailboxes(draft.cc))
        .put("BccRecipients", mailboxes(draft.bcc))
        .put("Sensitivity", "Normal")
        .put("IsDeliveryReceiptRequested", false)
        .put("IsReadReceiptRequested", false)

    fun create(draft: OutgoingMail, withAttachments: Boolean): JSONObject = JSONObject()
        .put("__type", "CreateItemRequest:#Exchange")
        .put("Items", JSONArray().put(message(draft)))
        .put("ClientSupportsIrm", true)
        .put("OutboundCharset", "AutoDetect")
        .put("MessageDisposition", if (withAttachments) "SaveOnly" else "SendAndSaveCopy")
        .put("ComposeOperation", "newMail")

    fun attach(id: String, changeKey: String, file: OutgoingFile, bytes: ByteArray): JSONObject =
        JSONObject()
            .put("__type", "CreateAttachmentRequest:#Exchange")
            .put("ParentItemId", itemId(id, changeKey))
            .put("Attachments", JSONArray().put(JSONObject()
                .put("__type", "FileAttachment:#Exchange")
                .put("Content", Base64.getEncoder().encodeToString(bytes))
                .put("IsContactPhoto", false)
                .put("ContentType", file.mimeType.ifBlank { "application/octet-stream" })
                .put("IsInline", false)
                .put("Name", file.name)
                .put("Size", bytes.size)))
            .put("RequireImageType", false)
            .put("IncludeContentIdInResponse", false)
            .put("ClientSupportsIrm", true)
            .put("CancellationId", JSONObject.NULL)

    private fun itemId(id: String, changeKey: String) = JSONObject()
        .put("__type", "ItemId:#Exchange")
        .put("Id", id)
        .put("ChangeKey", changeKey)

    private fun field(name: String) = JSONObject()
        .put("__type", "PropertyUri:#Exchange")
        .put("FieldURI", name)

    private fun setField(name: String, value: Any): JSONObject = JSONObject()
        .put("__type", "SetItemField:#Exchange")
        .put("Path", field(name))
        .put("Item", JSONObject().put("__type", "Message:#Exchange").put(name, value))

    fun sendDraft(id: String, changeKey: String, draft: OutgoingMail): JSONObject {
        val updates = JSONArray()
            .put(JSONObject().put("__type", "DeleteItemField:#Exchange")
                .put("Path", JSONObject()
                    .put("__type", "ExtendedPropertyUri:#Exchange")
                    .put("PropertyId", 35356)
                    .put("DistinguishedPropertySetId", "Sharing")
                    .put("PropertyType", "CLSID")))
            .put(JSONObject().put("__type", "DeleteItemField:#Exchange").put("Path", field("From")))
            .put(setField("CcRecipients", mailboxes(draft.cc)))
            .put(setField("BccRecipients", mailboxes(draft.bcc)))
            .put(setField("ToRecipients", mailboxes(draft.to)))
            .put(setField("IsReadReceiptRequested", false))
            .put(setField("IsDeliveryReceiptRequested", false))
            .put(setField("Subject", draft.subject))
            .put(setField("Importance", "Normal"))
            .put(setField("Body", bodyContent(draft.body)))
            .put(setField("Sensitivity", "Normal"))
        return JSONObject()
            .put("__type", "UpdateItemRequest:#Exchange")
            .put("ItemChanges", JSONArray().put(JSONObject()
                .put("__type", "ItemChange:#Exchange")
                .put("Updates", updates)
                .put("ItemId", itemId(id, changeKey))))
            .put("ConflictResolution", "AlwaysOverwrite")
            .put("ClientSupportsIrm", true)
            .put("SendCalendarInvitationsOrCancellations", "SendToNone")
            .put("MessageDisposition", "SendAndSaveCopy")
            .put("SuppressReadReceipts", false)
            .put("ComposeOperation", "newMail")
            .put("OutboundCharset", "AutoDetect")
            .put("PromoteInlineAttachments", false)
            .put("SendOnNotFoundError", false)
            .put("InternetMessageId", "<" + UUID.randomUUID() + "@mpei.ru>")
    }

    fun confirmedResponse(response: JSONObject): JSONObject {
        val item = response.optJSONObject("Body")
            ?.optJSONObject("ResponseMessages")?.optJSONArray("Items")?.optJSONObject(0)
            ?: throw IllegalStateException("missing_send_confirmation")
        if (item.optString("ResponseCode") != "NoError" ||
            item.optString("ResponseClass") != "Success") {
            throw IllegalStateException("send_rejected_" +
                item.optString("ResponseCode").take(70))
        }
        return item
    }
}

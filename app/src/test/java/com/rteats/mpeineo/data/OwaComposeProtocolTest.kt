package com.rteats.mpeineo.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OwaComposeProtocolTest {
    private val draft = OutgoingMail(
        to = "first@example.com; second@example.com",
        cc = "copy@example.com",
        bcc = "hidden@example.com",
        subject = "A < B",
        body = "First line\n<tag> & second line",
    )

    @Test fun validatesRecipientsBeforeSending() {
        assertTrue(OwaComposeProtocol.validRecipients(draft))
        assertFalse(OwaComposeProtocol.validRecipients(draft.copy(to = "")))
        assertFalse(OwaComposeProtocol.validRecipients(draft.copy(cc = "bad recipient")))
        assertEquals(2, OwaComposeProtocol.recipients(draft.to).size)
    }

    @Test fun sendsTextDirectlyAndEscapesHtml() {
        val request = OwaComposeProtocol.create(draft, withAttachments = false)
        assertEquals("CreateItemRequest:#Exchange", request.getString("__type"))
        assertEquals("SendAndSaveCopy", request.getString("MessageDisposition"))
        val message = request.getJSONArray("Items").getJSONObject(0)
        assertEquals(2, message.getJSONArray("ToRecipients").length())
        assertEquals(1, message.getJSONArray("CcRecipients").length())
        assertEquals(1, message.getJSONArray("BccRecipients").length())
        assertEquals("SMTP", message.getJSONArray("ToRecipients")
            .getJSONObject(0).getString("RoutingType"))
        val body = message.getJSONObject("Body")
        assertEquals("HTML", body.getString("BodyType"))
        assertTrue(body.getString("Value").contains("&lt;tag&gt; &amp;"))
        assertTrue(body.getString("Value").contains("<br>"))
        assertFalse(body.getString("Value").contains("<tag>"))
    }

    @Test fun attachmentFlowSavesDraftAndThenUpdatesOriginalItem() {
        assertEquals(
            "SaveOnly",
            OwaComposeProtocol.create(draft, withAttachments = true)
                .getString("MessageDisposition"),
        )
        val update = OwaComposeProtocol.sendDraft("draft-id", "change-key", draft)
        assertEquals("SendAndSaveCopy", update.getString("MessageDisposition"))
        val change = update.getJSONArray("ItemChanges").getJSONObject(0)
        assertEquals("draft-id", change.getJSONObject("ItemId").getString("Id"))
        assertEquals("change-key", change.getJSONObject("ItemId").getString("ChangeKey"))
        val updates = change.getJSONArray("Updates")
        assertEquals(11, updates.length())
        assertEquals("DeleteItemField:#Exchange", updates.getJSONObject(0).getString("__type"))
        val fields = (0 until updates.length()).mapNotNull {
            updates.getJSONObject(it).getJSONObject("Path").optString("FieldURI")
                .takeIf(String::isNotBlank)
        }
        assertTrue(fields.containsAll(listOf("From", "Body", "Subject",
            "ToRecipients", "CcRecipients", "BccRecipients")))
        assertTrue(update.getString("InternetMessageId").startsWith("<"))
    }

    @Test fun cannotReportSuccessWithoutAnExchangeConfirmation() {
        assertThrows(IllegalStateException::class.java) {
            OwaComposeProtocol.confirmedResponse(JSONObject())
        }
        val rejected = JSONObject().put("Body", JSONObject()
            .put("ResponseMessages", JSONObject()
                .put("Items", JSONArray().put(JSONObject()
                    .put("ResponseCode", "ErrorAccessDenied")
                    .put("ResponseClass", "Error")))))
        assertThrows(IllegalStateException::class.java) {
            OwaComposeProtocol.confirmedResponse(rejected)
        }
        val accepted = JSONObject().put("Body", JSONObject()
            .put("ResponseMessages", JSONObject()
                .put("Items", JSONArray().put(JSONObject()
                    .put("ResponseCode", "NoError")
                    .put("ResponseClass", "Success")))))
        assertEquals("NoError",
            OwaComposeProtocol.confirmedResponse(accepted).getString("ResponseCode"))
    }
}

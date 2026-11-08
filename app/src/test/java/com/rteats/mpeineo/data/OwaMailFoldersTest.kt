package com.rteats.mpeineo.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwaMailFoldersTest {
    @Test fun itemFoldersUseCapturedFindItemShapeAndIndependentIds() {
        val drafts = OwaMailFolders.findItemRequest(OwaMailFolder.DRAFTS, 0, 60)
        assertEquals("FindItemRequest:#Exchange", drafts.getString("__type"))
        assertEquals("drafts", drafts.getJSONArray("ParentFolderIds")
            .getJSONObject(0).getString("Id"))
        assertEquals("DateTimeCreated", drafts.getJSONArray("SortOrder")
            .getJSONObject(0).getJSONObject("Path").getString("FieldURI"))
        val deleted = OwaMailFolders.findItemRequest(OwaMailFolder.DELETED, 60, 60)
        assertEquals("DateTimeReceived", deleted.getJSONArray("SortOrder")
            .getJSONObject(0).getJSONObject("Path").getString("FieldURI"))
        assertEquals(60, deleted.getJSONObject("Paging").getInt("Offset"))
        assertFalse(OwaMailFolder.DELETED == OwaMailFolder.INBOX)
    }

    @Test fun itemResponseKeepsSenderAddressForPersonaPhoto() {
        val message = JSONObject().put("ItemId", JSONObject().put("Id", "opaque-item"))
            .put("Subject", "Example").put("IsRead", false)
            .put("HasAttachments", true)
            .put("Sender", JSONObject().put("Mailbox", JSONObject()
                .put("Name", "Colleague").put("EmailAddress", "person@example.org")))
        val response = JSONObject().put("Body", JSONObject().put("ResponseMessages",
            JSONObject().put("Items", JSONArray().put(JSONObject().put("RootFolder",
                JSONObject().put("Items", JSONArray().put(message)))))))
        val row = OwaMailFolders.parseItems(response, 77L, { 42L }, { 123L }).single()
        assertEquals("opaque-item", row.owaItemId)
        assertEquals("person@example.org", row.senderEmail)
        assertEquals("Colleague", row.sender)
        assertEquals(123L, row.sentAt)
        assertTrue(row.unread)
        assertTrue(row.hasAttachment)
    }

    @Test fun folderDiscoveryKeepsOnlyServerMailFoldersAndOpaqueIds() {
        val custom = JSONObject().put("FolderId", JSONObject().put("Id", "opaque-folder"))
            .put("DisplayName", "Projects").put("FolderClass", "IPF.Note")
        val system = JSONObject().put("FolderId", JSONObject().put("Id", "draft-folder"))
            .put("DisplayName", "Черновики").put("FolderClass", "IPF.Note")
        val calendar = JSONObject().put("FolderId", JSONObject().put("Id", "calendar-folder"))
            .put("DisplayName", "Calendar").put("FolderClass", "IPF.Appointment")
        val hidden = JSONObject().put("FolderId", JSONObject().put("Id", "hidden-folder"))
            .put("DisplayName", "Hidden")
            .put("ExtendedProperty", JSONArray().put(JSONObject()
                .put("ExtendedFieldUri", JSONObject().put("PropertyTag", "0x10f4"))
                .put("Value", "true")))
        val internal = JSONObject().put("FolderId", JSONObject().put("Id", "internal-folder"))
            .put("DisplayName", "Conversation Action Settings")
        val outbox = JSONObject().put("FolderId", JSONObject().put("Id", "outbox-folder"))
            .put("DisplayName", "Outbox")
        val root = JSONObject().put("Folders", JSONArray().put(custom).put(system)
            .put(calendar).put(hidden).put(internal).put(outbox))
        val responses = JSONArray().put(JSONObject().put("RootFolder", root))
        val response = JSONObject().put("Body", JSONObject().put("ResponseMessages",
            JSONObject().put("Items", responses)))
        val discovered = OwaMailFolders.parseFolders(response)
        assertEquals(2, discovered.size)
        assertEquals("Projects", discovered[0].title)
        assertEquals("opaque-folder", discovered[0].folderId)
        assertTrue(discovered[0].name.startsWith("CUSTOM_"))
        assertEquals(OwaMailFolder.DRAFTS, discovered[1])
        assertEquals(listOf(OwaMailFolder.INBOX, OwaMailFolder.SENT),
            OwaMailFolder.defaults)
        assertEquals("Deep", OwaMailFolders.findFolderRequest().getString("Traversal"))
        assertEquals("0x10f4", OwaMailFolders.findFolderRequest()
            .getJSONObject("FolderShape").getJSONArray("AdditionalProperties")
            .getJSONObject(0).getString("PropertyTag"))
    }
}

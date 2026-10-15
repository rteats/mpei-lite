package com.rteats.mpeineo.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwaRecipientDirectoryTest {
    @Test
    fun directoryQueryUsesExchange2013AndBoundedPage() {
        val request = OwaRecipientDirectory.searchRequest("Иван")
        assertEquals("FindPeopleRequest:#Exchange", request.getString("__type"))
        assertEquals("Иван", request.getString("QueryString"))
        assertEquals("directory", request.getJSONObject("ParentFolderId")
            .getJSONObject("BaseFolderId").getString("Id"))
        assertEquals(12, request.getJSONObject("IndexedPageItemView")
            .getInt("MaxEntriesReturned"))
    }

    @Test
    fun extractsValidMailboxesAndRemovesDuplicates() {
        val response = JSONObject().put("Body", JSONObject().put("ResultSet", JSONArray()
            .put(JSONObject().put("DisplayName", "Иван").put("EmailAddress",
                JSONObject().put("EmailAddress", "ivan@mpei.ru")))
            .put(JSONObject().put("DisplayName", "Copy").put("EmailAddress", "IVAN@mpei.ru"))
            .put(JSONObject().put("DisplayName", "Invalid").put("EmailAddress", "invalid"))))
        val results = OwaRecipientDirectory.parse(response)
        assertEquals(1, results.size)
        assertEquals("Иван", results.single().name)
        assertEquals("ivan@mpei.ru", results.single().email)
    }

    @Test
    fun multipleRecipientsOnlyReplaceTheLastUnfinishedAddress() {
        val previous = "one@mpei.ru; two@mpei.ru, iv"
        assertEquals("iv", OwaRecipientDirectory.token(previous))
        assertEquals("one@mpei.ru; two@mpei.ru, ivan@mpei.ru; ",
            OwaRecipientDirectory.replaceToken(previous, "ivan@mpei.ru"))
        assertEquals("ivan@mpei.ru; ",
            OwaRecipientDirectory.replaceToken("iv", "ivan@mpei.ru"))
    }

    @Test
    fun cacheFallbackOnlySuggestsValidMatchingAddresses() {
        val result = OwaRecipientDirectory.cachedAddresses(
            "mpei", listOf("John", " Ivan@MPEI.RU ", "invalid@", "ivan@mpei.ru"),
        )
        assertEquals(1, result.size)
        assertEquals("Ivan@MPEI.RU", result.single().email)
        assertTrue(OwaRecipientDirectory.cachedAddresses("i", listOf("ivan@mpei.ru")).isEmpty())
    }

    @Test
    fun folderIdsAreDistinctToProtectInboxCache() {
        assertEquals("inbox", OwaMailFolder.INBOX.distinguishedId)
        assertEquals("sentitems", OwaMailFolder.SENT.distinguishedId)
        assertFalse(OwaMailFolder.INBOX == OwaMailFolder.SENT)
    }
}

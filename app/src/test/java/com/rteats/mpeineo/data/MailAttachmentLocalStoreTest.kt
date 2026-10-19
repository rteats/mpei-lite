package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailAttachmentLocalStoreTest {
    @Test fun attachmentLocationKeysAreAccountScopedAndDoNotLeakIdentifiers() {
        val alice = attachmentLocationKey("alice", "exchange-attachment-123")!!
        val bob = attachmentLocationKey("bob", "exchange-attachment-123")!!
        val another = attachmentLocationKey("alice", "exchange-attachment-456")!!
        assertNotEquals(alice, bob)
        assertNotEquals(alice, another)
        assertEquals(64, alice.length)
        assertTrue(alice.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(alice.contains("exchange"))
        assertFalse(alice.contains("alice"))
    }

    @Test fun missingAttachmentOrAccountHasNoLocalIndexKey() {
        assertNull(attachmentLocationKey(null, "id"))
        assertNull(attachmentLocationKey("account", ""))
        assertNull(attachmentLocationKey("", "id"))
        assertNull(attachmentLocationKey("account", null))
    }

    @Test fun historicDownloadLookupUsesExactlyTheDownloadFilenameNormalization() {
        assertEquals("report.pdf", attachmentDownloadName("report.pdf"))
        assertEquals("report.pdf", attachmentDownloadName("file/path/report.pdf"))
        assertEquals("report.pdf", attachmentDownloadName("C:\\Users\\Documents\\report.pdf"))
        assertEquals("report.pdf", attachmentDownloadName("report\u0001.pdf"))
        assertEquals("attachment", attachmentDownloadName(""))
        assertEquals(160, attachmentDownloadName("x".repeat(200)).length)
    }
}

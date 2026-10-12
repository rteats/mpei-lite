package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailSeenFlagTest {
    private fun summary(uid: Long, validity: Long, unread: Boolean = true) =
        MailSummary(uid, validity, "sender", "subject", 0L, unread, false)

    @Test fun markReadUpdatesOnlyRequestedUid() {
        val messages = listOf(summary(10, 80), summary(11, 80), summary(12, 80, unread = false))
        val changed = markMailRead(messages, 11, 80)
        assertTrue(changed[0].unread)
        assertFalse(changed[1].unread)
        assertFalse(changed[2].unread)
    }

    @Test fun sameUidAfterUidValidityResetMustNotBeChanged() {
        val cached = listOf(summary(10, 80), summary(10, 81))
        val changed = markMailRead(cached, 10, 80)
        assertFalse(changed[0].unread)
        assertTrue(changed[1].unread)
    }

    @Test fun missingOrAlreadyReadMailRemainsUnchanged() {
        val messages = listOf(summary(13, 80, unread = false), summary(14, 80))
        assertEquals(messages, markMailRead(messages, 13, 80))
        assertEquals(messages, markMailRead(messages, 99, 80))
        assertEquals(messages, markMailRead(messages, 14, 90))
    }
}

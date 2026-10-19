package com.rteats.mpeineo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailDraftSnapshotTest {
    @Test fun blankDraftHasNoDataToSave() {
        assertTrue(MailDraftSnapshot().isEmpty())
        assertTrue(MailDraftSnapshot(mail = OutgoingMail(body = "  ")).isEmpty())
    }

    @Test fun anyRecipientOrMessageFieldKeepsDraft() {
        assertFalse(MailDraftSnapshot(mail = OutgoingMail(to = "test@example.org")).isEmpty())
        assertFalse(MailDraftSnapshot(mail = OutgoingMail(cc = "test@example.org")).isEmpty())
        assertFalse(MailDraftSnapshot(mail = OutgoingMail(subject = "Subject")).isEmpty())
        assertFalse(MailDraftSnapshot(mail = OutgoingMail(body = "Draft text")).isEmpty())
    }
}

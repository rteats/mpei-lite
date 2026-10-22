package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.OutgoingMail
import org.junit.Assert.assertEquals
import org.junit.Test

class MailComposeControllerTest {
    @Test fun editingEachRecipientDoesNotChangeOtherRecipientsOrDraftBody() {
        val original = OutgoingMail(
            to = "to@example.org", cc = "cc@example.org", bcc = "bcc@example.org",
            subject = "Subject", body = "Message",
        )
        val changedTo = mailWithRecipient(original, RecipientField.TO, "new@example.org")
        assertEquals("new@example.org", mailRecipientValue(changedTo, RecipientField.TO))
        assertEquals("cc@example.org", mailRecipientValue(changedTo, RecipientField.CC))
        assertEquals("bcc@example.org", mailRecipientValue(changedTo, RecipientField.BCC))
        assertEquals("Subject", changedTo.subject)
        assertEquals("Message", changedTo.body)
        assertEquals("to@example.org", original.to)

        val changedCc = mailWithRecipient(changedTo, RecipientField.CC, "new-cc@example.org")
        assertEquals("new-cc@example.org", changedCc.cc)
        assertEquals("bcc@example.org", changedCc.bcc)

        val changedBcc = mailWithRecipient(changedCc, RecipientField.BCC, "new-bcc@example.org")
        assertEquals("new-bcc@example.org", changedBcc.bcc)
        assertEquals("new-cc@example.org", changedBcc.cc)
    }
}

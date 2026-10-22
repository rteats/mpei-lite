package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.MailSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailPagePolicyTest {
    private fun item(uid: Long, recipients: List<String>? = null) = MailSummary(
        uid = uid, uidValidity = 1L, sender = "sender",
        subject = "subject", sentAt = 0L, unread = false,
        hasAttachment = false, recipients = recipients,
    )

    @Test fun firstPageOffsetCountsOnlyNewHeadMessages() {
        val result = refreshedMailPage(
            incoming = listOf(item(5), item(4), item(3)),
            cached = listOf(item(4), item(3), item(2), item(1)),
            previousOffset = 25, previousHasMore = true,
            pageSize = 3, merge = ::mergeOwaInboxPages,
        )
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), result.messages.map { it.uid })
        assertEquals(26, result.nextOffset)
        assertTrue(result.hasMore)
    }

    @Test fun exhaustedOlderCacheIsNotReopenedByRefreshingFirstPage() {
        val result = refreshedMailPage(
            incoming = listOf(item(5), item(4)),
            cached = listOf(item(4), item(3), item(2)),
            previousOffset = 3, previousHasMore = false,
            pageSize = 2, merge = ::mergeOwaInboxPages,
        )
        assertFalse(result.hasMore)
        assertEquals(4, result.nextOffset)
    }

    @Test fun olderPageAlwaysAdvancesByServerCountEvenWithOverlap() {
        val result = appendedMailPage(
            cached = listOf(item(6), item(5), item(4)),
            incoming = listOf(item(4), item(3)),
            requestedOffset = 15, pageSize = 2,
            merge = ::mergeOwaInboxPages,
        )
        assertEquals(listOf(6L, 5L, 4L, 3L), result.messages.map { it.uid })
        assertEquals(17, result.nextOffset)
        assertTrue(result.hasMore)
    }

    @Test fun sentRefreshRetainsEnrichedRecipientLabels() {
        val cached = listOf(item(3, recipients = listOf("recipient@example.org")))
        val incoming = listOf(item(4), item(3))
        val result = refreshedMailPage(
            incoming = incoming, cached = cached,
            previousOffset = 10, previousHasMore = true,
            pageSize = 2, merge = ::mergeOwaSentPages,
        )
        assertEquals(11, result.nextOffset)
        assertEquals(listOf("recipient@example.org"),
            result.messages.first { it.uid == 3L }.recipients)
    }
}

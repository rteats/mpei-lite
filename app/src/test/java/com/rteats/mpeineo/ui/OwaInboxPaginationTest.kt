package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.MailSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class OwaInboxPaginationTest {
    private fun conversation(id: Long, validity: Long = 7L) = MailSummary(
        uid = id,
        uidValidity = validity,
        sender = "Test",
        subject = "Conversation $id",
        sentAt = id,
        unread = true,
        hasAttachment = false,
        owaConversationId = "item-$id",
    )

    @Test fun appendedPagesPreserveServerOrder() {
        val newer = listOf(conversation(5), conversation(4), conversation(3))
        val older = listOf(conversation(2), conversation(1))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L),
            mergeOwaInboxPages(newer, older).map { it.uid })
    }

    @Test fun overlappingPagesDoNotDuplicateConversations() {
        val newer = listOf(conversation(5), conversation(4), conversation(3))
        val older = listOf(conversation(3), conversation(2), conversation(1))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L),
            mergeOwaInboxPages(newer, older).map { it.uid })
    }

    @Test fun sameIdAcrossMailboxGenerationsIsDistinct() {
        val older = listOf(conversation(1, validity = 8L))
        assertEquals(2, mergeOwaInboxPages(listOf(conversation(1)), older).size)
    }

    @Test fun refreshRetainsOlderSearchablePages() {
        val loaded = listOf(5L, 4L, 3L, 2L, 1L).map(::conversation)
        val firstPageAfterDelivery = listOf(7L, 6L, 5L, 4L).map(::conversation)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L, 2L, 1L),
            mergeOwaInboxPages(firstPageAfterDelivery, loaded).map { it.uid })
    }

    @Test fun emptyPageNeverRemovesCachedEntries() {
        assertEquals(listOf(3L, 2L),
            mergeOwaInboxPages(listOf(conversation(3), conversation(2)), emptyList())
                .map { it.uid })
    }
}

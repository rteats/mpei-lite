package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwaNotificationCheckpointTest {
    private fun message(id: Long, time: Long) = MailSummary(
        uid = id,
        uidValidity = 0x4F5741L,
        sender = "Sender",
        subject = "Subject",
        sentAt = time,
        unread = true,
        hasAttachment = false,
    )

    @Test fun firstPollOnlyEstablishesBaseline() {
        val snapshot = calculateOwaNotificationChanges(
            listOf(message(1, 10), message(2, 20)), -1L, emptySet(),
        )
        assertTrue(snapshot.newMessages.isEmpty())
        assertEquals(20L, snapshot.latestTime)
        assertEquals(setOf("2"), snapshot.latestIds)
    }

    @Test fun subsequentPollRecognizesNewerAndSameTimestampConversation() {
        val snapshot = calculateOwaNotificationChanges(
            listOf(message(4, 30), message(3, 20), message(2, 20), message(1, 10)),
            20L, setOf("2"),
        )
        assertEquals(listOf(3L, 4L), snapshot.newMessages.map { it.uid })
        assertEquals(30L, snapshot.latestTime)
        assertEquals(setOf("4"), snapshot.latestIds)
    }

    @Test fun sameTimestampIdsAreRetainedAcrossDifferentPages() {
        val first = calculateOwaNotificationChanges(
            listOf(message(2, 20)), 20L, setOf("1"),
        )
        assertEquals(listOf(2L), first.newMessages.map { it.uid })
        assertEquals(setOf("1", "2"), first.latestIds)

        val second = calculateOwaNotificationChanges(
            listOf(message(1, 20), message(3, 20)), first.latestTime, first.latestIds,
        )
        assertEquals(listOf(3L), second.newMessages.map { it.uid })
        assertEquals(setOf("1", "2", "3"), second.latestIds)

        val third = calculateOwaNotificationChanges(
            listOf(message(2, 20), message(3, 20)), second.latestTime, second.latestIds,
        )
        assertTrue(third.newMessages.isEmpty())
    }

    @Test fun olderOrEmptyFetchCannotRegressWatermark() {
        val result = calculateOwaNotificationChanges(
            listOf(message(9, 10)), 50L, setOf("8"),
        )
        assertTrue(result.newMessages.isEmpty())
        assertEquals(50L, result.latestTime)
        assertEquals(setOf("8"), result.latestIds)

        val empty = calculateOwaNotificationChanges(emptyList(), 50L, result.latestIds)
        assertTrue(empty.newMessages.isEmpty())
        assertEquals(50L, empty.latestTime)
        assertEquals(setOf("8"), empty.latestIds)
    }

    @Test fun emptyMailboxCanBeBaselinedBeforeFirstArrival() {
        val baseline = calculateOwaNotificationChanges(emptyList(), -1L, emptySet())
        assertEquals(0L, baseline.latestTime)
        assertTrue(baseline.newMessages.isEmpty())
        val next = calculateOwaNotificationChanges(
            listOf(message(5, 100)), baseline.latestTime, baseline.latestIds,
        )
        assertEquals(listOf(5L), next.newMessages.map { it.uid })
    }
}

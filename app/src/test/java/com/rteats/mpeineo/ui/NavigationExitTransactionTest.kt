package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationExitTransactionTest {
    @Test fun successfulSlideCommitsOnce() {
        val transaction = NavigationExitTransaction()
        var pops = 0
        transaction.begin { pops++ }
        assertTrue(transaction.pending)
        assertTrue(transaction.settle())
        assertFalse(transaction.settle())
        assertEquals(1, pops)
        assertFalse(transaction.pending)
    }

    @Test fun interruptedSlideCanCommitThenStartNewRoute() {
        val transaction = NavigationExitTransaction()
        var route = "MAPS"
        transaction.begin { route = "SCHEDULE" }
        assertTrue(transaction.settle())
        route = "MAIL"
        assertFalse(transaction.settle())
        assertEquals("MAIL", route)
    }

    @Test(expected = IllegalStateException::class)
    fun overlappingExitsAreRejected() {
        val transaction = NavigationExitTransaction()
        transaction.begin {}
        transaction.begin {}
    }
}

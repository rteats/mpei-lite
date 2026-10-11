package com.rteats.mpeineo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsPageFlowTest {
    private fun page(url: String = BARS_MARKS_URL, marks: Boolean = false,
                     login: Boolean = false, auth: Boolean = false, list: Boolean = false) =
        BarsPageState(url = url, isMarksPage = marks, isLoginPage = login,
            isAuthFlow = auth, isStudentList = list)

    @Test fun pageClassificationPreservesLoginAndTwoFactor() {
        assertEquals(BarsPageKind.AUTH_FLOW, classifyBarsPage(page(login = true)))
        assertEquals(BarsPageKind.AUTH_FLOW, classifyBarsPage(page(auth = true)))
        assertEquals(BarsPageKind.AUTH_FLOW, classifyBarsPage(page(list = true)))
        assertEquals(BarsPageKind.MARKS_READY, classifyBarsPage(page(marks = true)))
        assertEquals(BarsPageKind.MARKS_LOADING, classifyBarsPage(page()))
        assertEquals(BarsPageKind.OTHER_BARS_PAGE,
            classifyBarsPage(page(url = BARS_LOGIN_URL)))
    }

    @Test fun foreignPagesDoNotTriggerNavigationOrExtraction() {
        assertEquals(BarsPageKind.UNTRUSTED,
            classifyBarsPage(page(url = "https://bars.mpei.ru.evil.test/", marks = true)))
    }

    private class FakeScheduler {
        val jobs = mutableListOf<Pair<Runnable, Long>>()
        fun schedule(task: Runnable, delay: Long) { jobs += task to delay }
        fun cancel(task: Runnable) { jobs.removeAll { it.first === task } }
        fun fire() { jobs.removeAt(0).first.run() }
    }

    @Test fun onePendingTimeoutAndOneRecoveryOnly() {
        val clock = FakeScheduler()
        var recovery = 0
        var failure = 0
        val waiter = BarsMarksReadiness(clock::schedule, clock::cancel,
            { recovery++ }, { failure++ })
        assertTrue(waiter.waitForMarks())
        assertFalse(waiter.waitForMarks())
        assertEquals(1, clock.jobs.size)
        assertEquals(5_000L, clock.jobs.single().second)
        clock.fire()
        assertEquals(1, recovery)
        assertTrue(waiter.waitForMarks())
        assertEquals(10_000L, clock.jobs.single().second)
        clock.fire()
        assertEquals(1, failure)
        assertFalse(waiter.waitForMarks())
    }

    @Test fun readyOrNavigatedPageCancelsStaleRecovery() {
        val clock = FakeScheduler()
        var recovery = 0
        val waiter = BarsMarksReadiness(clock::schedule, clock::cancel,
            { recovery++ }, { })
        waiter.waitForMarks()
        waiter.navigationStarted()
        assertTrue(clock.jobs.isEmpty())
        waiter.waitForMarks()
        waiter.reset()
        assertTrue(clock.jobs.isEmpty())
        assertTrue(waiter.waitForMarks())
        clock.fire()
        assertEquals(1, recovery)
        waiter.dispose()
        assertFalse(waiter.waitForMarks())
        assertTrue(clock.jobs.isEmpty())
    }

    @Test fun manualRetryResetsRecoveryBudget() {
        val clock = FakeScheduler()
        var recoveries = 0
        val waiter = BarsMarksReadiness(clock::schedule, clock::cancel,
            { recoveries++ }, { })
        waiter.waitForMarks()
        clock.fire()
        waiter.reset()
        assertTrue(waiter.waitForMarks())
        assertEquals(5_000L, clock.jobs.single().second)
        clock.fire()
        assertEquals(2, recoveries)
    }
}

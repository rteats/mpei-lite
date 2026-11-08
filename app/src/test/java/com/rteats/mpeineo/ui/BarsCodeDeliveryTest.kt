package com.rteats.mpeineo.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BarsCodeDeliveryTest {
    @Test fun firstCodeIsRequestedAsSoonAsChallengeOpens() {
        val status = MutableStateFlow(BarsNativeStatus(step = BarsNativeStep.SUBMITTING))
        var requests = 0

        enterBarsCodeChallenge(status) {
            assertEquals(BarsNativeStep.CODE, status.value.step)
            requests++
        }

        assertEquals(1, requests)
        assertFalse(status.value.codeSent)
    }

    @Test fun actionOnlySaysAgainAfterSuccessfulDelivery() {
        assertEquals("Отправляем код…", barsCodeSendActionLabel(
            BarsNativeStatus(step = BarsNativeStep.CODE, busySending = true)))
        assertEquals("Повторить отправку кода", barsCodeSendActionLabel(
            BarsNativeStatus(step = BarsNativeStep.CODE)))
        assertEquals("Отправить код ещё раз", barsCodeSendActionLabel(
            BarsNativeStatus(step = BarsNativeStep.CODE, codeSent = true)))
    }
}

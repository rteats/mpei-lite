package com.rteats.mpeineo.data

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsAuthFailureTest {
    @Test fun httpFailureRecordsOnlyNumericStatus() {
        val summary = barsFailureDiagnostic(
            BarsAuthStage.CREDENTIALS,
            BarsProtocolException(BarsFailureReason.HTTP_STATUS, 403),
        )
        assertTrue(summary.contains("stage=credentials category=http reason=http_status http_status=403"))
        assertFalse(summary.contains("https://"))
    }

    @Test fun networkFailuresAreDistinguished() {
        assertTrue(barsFailureDiagnostic(BarsAuthStage.LOGIN_FORM,
            UnknownHostException("private.example/login?token=secret")).contains("category=network reason=dns"))
        assertTrue(barsFailureDiagnostic(BarsAuthStage.CODE_DELIVERY,
            ConnectException("secret")).contains("category=network reason=connect"))
        assertTrue(barsFailureDiagnostic(BarsAuthStage.CODE_CONFIRMATION,
            SSLHandshakeException("secret")).contains("category=network reason=tls"))
        assertTrue(barsFailureDiagnostic(BarsAuthStage.SESSION_RESTORE,
            java.net.SocketTimeoutException("secret")).contains("reason=timeout_or_interrupted"))
    }

    @Test fun passwordAndMfaFailuresAreDifferent() {
        val credentials = barsUnacceptedAuthPage(
            "<form><input name='Password' value='private-password'></form>", codeChallenge = false)
        val code = barsUnacceptedAuthPage(
            "<form><input name='AF2_Code' value='123456'></form>", codeChallenge = true)
        val unexpected = barsUnacceptedAuthPage("<html>Service unavailable</html>", codeChallenge = true)
        assertEquals(BarsFailureReason.CREDENTIALS_REJECTED, credentials.reason)
        assertEquals(BarsFailureReason.CODE_REJECTED, code.reason)
        assertEquals(BarsFailureReason.UNEXPECTED_AUTH_PAGE, unexpected.reason)
    }

    @Test fun diagnosticsNeverIncludeThrowableMessagesOrHtml() {
        val secret = "password=PrivatePwd123 AF2_Code=123456 session=opaque-cookie"
        val cases = listOf(
            BarsProtocolException(BarsFailureReason.CODE_REJECTED),
            IOException(secret),
            UnknownHostException(secret),
            IllegalStateException(secret),
        )
        cases.forEach { failure ->
            val summary = barsFailureDiagnostic(BarsAuthStage.CODE_CONFIRMATION, failure)
            assertFalse(summary.contains(secret))
            assertFalse(summary.contains("PrivatePwd123"))
            assertFalse(summary.contains("123456"))
            assertFalse(summary.contains("opaque-cookie"))
        }
    }

    @Test fun codeDeliveryAndUnexpectedPagesHaveDistinctReasons() {
        val failedDelivery = barsFailureDiagnostic(BarsAuthStage.CODE_DELIVERY,
            BarsProtocolException(BarsFailureReason.CODE_SEND_REJECTED))
        val malformedJson = barsFailureDiagnostic(BarsAuthStage.CODE_DELIVERY,
            BarsProtocolException(BarsFailureReason.CODE_DELIVERY_INVALID_JSON))
        assertTrue(failedDelivery.contains("category=mfa reason=code_send_rejected"))
        assertTrue(malformedJson.contains("category=response reason=code_delivery_invalid_json"))
    }
}

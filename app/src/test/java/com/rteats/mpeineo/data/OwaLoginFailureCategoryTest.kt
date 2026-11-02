package com.rteats.mpeineo.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class OwaLoginFailureCategoryTest {
    @Test fun networkFailuresHaveSafeStableCategories() {
        assertEquals("timeout", owaNetworkFailureCategory(SocketTimeoutException()))
        assertEquals("dns", owaNetworkFailureCategory(UnknownHostException()))
        assertEquals("connection", owaNetworkFailureCategory(ConnectException()))
        assertEquals("tls", owaNetworkFailureCategory(SSLHandshakeException("failure")))
    }

    @Test fun httpStageDoesNotContainUserInformation() {
        val failure = OwaLoginStageException("session_verification", "http_error", 502)
        assertEquals("session_verification", failure.stage)
        assertEquals(502, failure.httpStatus)
        assertEquals("http_error", failure.category)
    }
}

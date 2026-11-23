package com.rteats.mpeineo.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Fixed, non-sensitive reason codes. Never use BARS response bodies or exception messages in logs. */
internal enum class BarsFailureReason(val category: String) {
    HTTP_STATUS("http"),
    UNEXPECTED_ORIGIN("protocol"),
    INVALID_REDIRECT("protocol"),
    TOO_MANY_REDIRECTS("protocol"),
    INVALID_FORM_ACTION("response"),
    EMPTY_RESPONSE("response"),
    OVERSIZED_RESPONSE("response"),
    LOGIN_FORM_UNAVAILABLE("response"),
    LOGIN_FORM_EXPIRED("state"),
    CREDENTIALS_REJECTED("credentials"),
    UNEXPECTED_AUTH_PAGE("response"),
    STUDENT_ROUTE_UNAVAILABLE("response"),
    STUDENT_SELECTION_AMBIGUOUS("response"),
    STUDENT_ROUTE_MISMATCH("response"),
    CODE_FORM_EXPIRED("state"),
    CODE_DELIVERY_INVALID_JSON("response"),
    CODE_SEND_REJECTED("mfa"),
    CODE_REJECTED("mfa"),
    SESSION_NOT_AUTHENTICATED("session"),
    SUMMARY_ROUTE_MISSING("state"),
}

internal class BarsProtocolException(
    val reason: BarsFailureReason,
    val httpStatus: Int? = null,
) : IOException(reason.name)

internal enum class BarsAuthStage {
    SESSION_RESTORE,
    LOGIN_FORM,
    CREDENTIALS,
    CODE_DELIVERY,
    CODE_CONFIRMATION,
    MARKS_PARSE,
    REFRESH_MAIN,
    REFRESH_SUMMARY,
    SAVED_LOGIN,
}

/**
 * Return only allowlisted classifications and an optional numeric HTTP status.
 * Throwable.message, response HTML, URLs, usernames, codes and cookies are never included.
 */
internal fun barsFailureDiagnostic(stage: BarsAuthStage, error: Throwable): String {
    val (category, reason) = when (error) {
        is BarsProtocolException -> error.reason.category to error.reason.name.lowercase()
        is UnknownHostException -> "network" to "dns"
        is InterruptedIOException -> "network" to "timeout_or_interrupted"
        is SSLException -> "network" to "tls"
        is ConnectException -> "network" to "connect"
        is SocketException -> "network" to "socket"
        is IOException -> if (stage == BarsAuthStage.SAVED_LOGIN) "storage" to "io"
            else "network" to "io"
        else -> "internal" to "unexpected"
    }
    val status = (error as? BarsProtocolException)?.httpStatus?.takeIf { it in 100..599 }
    val type = error.javaClass.simpleName.replace(Regex("[^A-Za-z0-9_]"), "_").take(48)
    return buildString {
        append("stage=").append(stage.name.lowercase())
        append(" category=").append(category)
        append(" reason=").append(reason)
        if (status != null) append(" http_status=").append(status)
        append(" type=").append(type)
    }
}

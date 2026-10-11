package com.rteats.mpeineo.ui

import java.net.URI

/** Document identity is host + path; BARS may change query parameters during auth. */
internal object BarsDocumentPolicy {
    fun location(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("bars.mpei.ru", ignoreCase = true)
        ) return@runCatching null
        uri.host.lowercase() + uri.path.orEmpty()
    }.getOrNull()

    fun isTrusted(url: String): Boolean = location(url) != null

    fun isCurrent(callbackUrl: String, visibleUrl: String): Boolean =
        location(callbackUrl)?.let { it == location(visibleUrl) } == true
}

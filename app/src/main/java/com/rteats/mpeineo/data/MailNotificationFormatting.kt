package com.rteats.mpeineo.data

/** Display-only fallback: retain the original IMAP subject in storage. */
internal fun mailNotificationSubject(subject: String): String =
    subject.trim().ifEmpty { "<Без темы>" }

internal fun mailNotificationSnippet(body: String, limit: Int = 500): String =
    body.replace(Regex("\\s+"), " ").trim().take(limit.coerceAtLeast(0))

internal fun mailNotificationLine(subject: String, snippet: String): String =
    listOf(mailNotificationSubject(subject), snippet.takeIf { it.isNotBlank() })
        .filterNotNull().joinToString(" · ")

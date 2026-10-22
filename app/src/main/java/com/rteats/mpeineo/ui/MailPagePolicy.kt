package com.rteats.mpeineo.ui

import com.rteats.mpeineo.data.MailSummary

/**
 * Server pagination reports the number of records fetched, not the deduplicated
 * visible row count. Otherwise a conversation moving between page boundaries
 * causes skipped messages or repeated pages.
 */
internal data class MailPageState(
    val messages: List<MailSummary>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

/**
 * A refreshed first page merges with the existing cache. Keep an older
 * exhausted end-of-list exhausted even when exactly one full page arrives.
 * The merge strategy differs for Sent (retain enriched recipient addresses).
 */
internal fun refreshedMailPage(
    incoming: List<MailSummary>,
    cached: List<MailSummary>,
    previousOffset: Int,
    previousHasMore: Boolean,
    pageSize: Int,
    merge: (List<MailSummary>, List<MailSummary>) -> List<MailSummary>,
): MailPageState {
    val oldIds = cached.mapTo(HashSet()) { it.uidValidity to it.uid }
    val newAtHead = incoming.count { (it.uidValidity to it.uid) !in oldIds }
    return MailPageState(
        messages = merge(incoming, cached),
        nextOffset = if (previousOffset > 0)
            maxOf(previousOffset + newAtHead, incoming.size) else incoming.size,
        hasMore = if (cached.size >= pageSize && !previousHasMore) false
            else incoming.size == pageSize,
    )
}

/** Page offsets always advance by raw server count, even when items overlap. */
internal fun appendedMailPage(
    cached: List<MailSummary>,
    incoming: List<MailSummary>,
    requestedOffset: Int,
    pageSize: Int,
    merge: (List<MailSummary>, List<MailSummary>) -> List<MailSummary>,
): MailPageState = MailPageState(
    messages = merge(cached, incoming),
    nextOffset = requestedOffset + incoming.size,
    hasMore = incoming.size == pageSize,
)

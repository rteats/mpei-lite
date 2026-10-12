package com.rteats.mpeineo.data

import java.time.LocalDate

// Retain roughly a semester of recent history and the upcoming semester.
// All other files remain untouched if their names do not match our cache format.
internal fun shouldPruneScheduleCacheWeek(
    filename: String,
    currentMonday: LocalDate,
): Boolean {
    val dateString = CACHE_WEEK_DATE.find(filename)?.groupValues?.get(1)
        ?: return false
    val week = runCatching { LocalDate.parse(dateString) }.getOrNull()
        ?: return true
    return week.isBefore(currentMonday.minusWeeks(12)) ||
        week.isAfter(currentMonday.plusWeeks(26))
}

private val CACHE_WEEK_DATE = Regex("""^.+-(\d{4}-\d{2}-\d{2})\.json$""")

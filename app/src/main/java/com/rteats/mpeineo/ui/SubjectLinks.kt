package com.rteats.mpeineo.ui

import java.util.Locale

/**
 * A deterministic cross-service link. Different BARS and timetable subjects
 * often have similar names, so never guess with substrings or fuzzy matches.
 */
internal fun subjectIdentity(raw: String): String = raw
    .lowercase(Locale.ROOT)
    .replace('ё', 'е')
    .replace(Regex("[«»\"“”.,:;()]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

/** Only one exact canonical match can be opened without user confirmation. */
internal fun findBarsSubject(
    scheduleName: String,
    disciplines: List<BarsDiscipline>,
): BarsDiscipline? {
    val identity = subjectIdentity(scheduleName)
    if (identity.isEmpty()) return null
    return disciplines.filter { subjectIdentity(it.disciplineName) == identity }
        .singleOrNull()
}

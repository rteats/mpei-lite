package com.rteats.mpeineo.data

import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Unified search excludes the legacy person endpoint to avoid duplicate lecturers. */
internal val primaryScheduleSearchTypes = listOf(
    ScheduleTargetType.GROUP, ScheduleTargetType.LECTURER, ScheduleTargetType.ROOM,
)

/** One-letter queries are useful offline; remote single-letter requests need a chosen filter. */
internal fun shouldQueryScheduleServer(query: String, type: ScheduleTargetType?): Boolean {
    val length = query.trim().length
    return length >= 2 || (length == 1 && type != null)
}

private fun normalized(text: String): String = text.lowercase(Locale.ROOT)
    .replace('ё', 'е')
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim().replace(Regex("\\s+"), " ")

/** Only use visual keyboard equivalents for compact group/room codes. */
private fun compactCode(text: String): String = normalized(text)
    .filter(Char::isLetterOrDigit)
    .map { letter ->
        when (letter) {
            'a' -> 'а'
            'b' -> 'в'
            'c' -> 'с'
            'e' -> 'е'
            'h' -> 'н'
            'k' -> 'к'
            'm' -> 'м'
            'o' -> 'о'
            'p' -> 'р'
            't' -> 'т'
            'x' -> 'х'
            'y' -> 'у'
            else -> letter
        }
    }.joinToString("")

private fun editDistanceAtMostTwo(left: String, right: String): Int {
    if (abs(left.length - right.length) > 2) return 3
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        for (j in right.indices) {
            current[j + 1] = min(
                min(current[j] + 1, previous[j + 1] + 1),
                previous[j] + if (left[i] == right[j]) 0 else 1,
            )
        }
        if (current.minOrNull()!! > 2) return 3
        previous = current
    }
    return previous[right.length]
}

/** A positive score means the result matches the visible query, including simple typos. */
internal fun scheduleMatchScore(target: ScheduleTarget, query: String): Int {
    val needle = normalized(query)
    if (needle.isEmpty()) return 0
    val name = normalized(target.name)
    val detail = normalized(target.description)
    val words = name.split(' ').filter(String::isNotBlank)
    var score = when {
        name == needle -> 1000
        name.startsWith("$needle ") -> 935
        name.startsWith(needle) -> 900
        words.any { it == needle } -> 840
        words.any { it.startsWith(needle) } -> 780
        name.contains(needle) -> 690
        needle.split(' ').filter(String::isNotBlank).all { name.contains(it) } -> 635
        detail.startsWith(needle) -> 510
        detail.contains(needle) -> 425
        else -> -1
    }
    if (target.type == ScheduleTargetType.GROUP || target.type == ScheduleTargetType.ROOM) {
        val cleanName = compactCode(target.name)
        val cleanQuery = compactCode(query)
        if (cleanQuery.isNotBlank()) {
            score = maxOf(score, when {
                cleanName == cleanQuery -> 980
                cleanName.startsWith(cleanQuery) -> 875
                cleanName.contains(cleanQuery) -> 640
                else -> -1
            })
        }
    }
    if (score < 0 && needle.length >= 4 && !needle.contains(' ')) {
        val distance = words.minOfOrNull { editDistanceAtMostTwo(needle, it) } ?: 3
        if (distance <= if (needle.length >= 7) 2 else 1) {
            score = 320 - distance * 65
        }
    }
    return score
}

internal fun rankScheduleTargets(
    targets: List<ScheduleTarget>,
    query: String,
    type: ScheduleTargetType? = null,
    favorites: List<ScheduleTarget> = emptyList(),
    recent: List<ScheduleTarget> = emptyList(),
    includeUnmatched: Boolean = false,
    limit: Int = 60,
): List<ScheduleTarget> {
    val pinned = favorites.map { it.type to it.id }.toSet()
    val history = recent.map { it.type to it.id }.toSet()
    return targets.asSequence()
        .filter { type == null || it.type == type }
        .distinctBy { it.type to it.id }
        .map { item ->
            val base = scheduleMatchScore(item, query)
            val key = item.type to item.id
            item to (if (base < 0) {
                if (includeUnmatched) 0 else -1
            } else base + (if (key in pinned) 65 else 0) +
                (if (key in history) 35 else 0))
        }
        .filter { it.second >= 0 }
        .sortedWith(
            compareByDescending<Pair<ScheduleTarget, Int>> { it.second }
                .thenBy { it.first.type.ordinal }
                .thenBy { it.first.name.lowercase(Locale.ROOT) },
        )
        .take(limit)
        .map { it.first }
        .toList()
}

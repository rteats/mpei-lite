package com.rteats.mpeineo.ui

import com.rteats.mpeineo.model.Lesson
import com.rteats.mpeineo.model.ScheduleTarget
import com.rteats.mpeineo.model.ScheduleTargetType
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal val timetableYears = 2000..2100
internal val timetableFirstDay: LocalDate = LocalDate.of(timetableYears.first, 1, 1)
internal val timetableLastDay: LocalDate = LocalDate.of(timetableYears.last, 12, 31)
internal val timetableDayCount: Int = timetableIndexOf(timetableLastDay) + 1

internal fun timetableDayAt(index: Int): LocalDate = timetableFirstDay.plusDays(index.toLong())

internal fun timetableIndexOf(date: LocalDate): Int =
    ChronoUnit.DAYS.between(timetableFirstDay, date).toInt()

internal fun timetableWeekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun timetableWeekOffset(referenceMonday: LocalDate, date: LocalDate): Int =
    ChronoUnit.WEEKS.between(referenceMonday, timetableWeekStart(date)).toInt()

private val spaces = Regex("\\s+")
private val words = Regex("[\\p{L}\\p{N}]+")
private val connectingWords = setOf("в", "во", "для", "и", "из", "к", "на", "о", "по", "с", "со")
private val lessonTimeFormat = DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT)

internal fun timetableAliasKey(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC).trim().replace(spaces, " ")
        .lowercase(Locale.ROOT).replace('ё', 'е')

internal fun timetableLessonLabel(officialName: String, aliases: Map<String, String>): String {
    val name = officialName.trim().replace(spaces, " ")
    aliases[timetableAliasKey(name)]?.takeIf(String::isNotBlank)?.let { return it }
    if (name.length <= 14) return name
    val terms = words.findAll(name).map { it.value }
        .filterNot { it.lowercase(Locale.ROOT) in connectingWords }.toList()
    return when (terms.size) {
        0 -> name.take(11) + "…"
        1 -> terms.single().take(10) + "."
        2 -> terms.joinToString(" ") { if (it.length > 6) it.take(3) + "." else it }
        else -> terms.take(8).joinToString("") { it.first().uppercase() }
    }
}

internal fun timetableLessonsByStart(lessons: List<Lesson>): Map<LocalTime?, List<Lesson>> =
    lessons.groupBy {
        runCatching { LocalTime.parse(it.startTime.trim(), lessonTimeFormat) }.getOrNull()
    }

internal fun timetableTeacherMatches(
    lecturer: String,
    targets: List<ScheduleTarget>,
): List<ScheduleTarget> {
    fun tokens(name: String) = words.findAll(timetableAliasKey(name)).map { it.value }.toList()
    val requested = tokens(lecturer)
    if (requested.isEmpty()) return emptyList()
    return targets.asSequence()
        .filter { it.type == ScheduleTargetType.LECTURER }
        .filter {
            val candidate = tokens(it.name)
            requested.size <= candidate.size && requested.zip(candidate).all { (query, part) ->
                query == part || (query.length == 1 && part.startsWith(query))
            }
        }
        .distinctBy { it.id }
        .toList()
}

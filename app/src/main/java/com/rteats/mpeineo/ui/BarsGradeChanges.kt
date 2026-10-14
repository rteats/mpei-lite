package com.rteats.mpeineo.ui

/** Detect new/corrected marks only within one student's semester. */
internal data class BarsGradeChange(
    val disciplineName: String,
    val activityName: String,
    val activityType: BarsActivityType,
    val before: String?,
    val after: String,
) {
    val isNew: Boolean get() = before == null
}

private data class GradeKey(
    val type: BarsActivityType,
    val name: String,
    val week: String,
    val occurrence: Int,
)

private fun trackedActivity(type: BarsActivityType) =
    type == BarsActivityType.CONTROL_ACTIVITY ||
        type == BarsActivityType.FINAL_MARK ||
        type == BarsActivityType.INTERMEDIATE_MARK

private fun BarsActivity.gradeFingerprint(): String? {
    if (!trackedActivity(type)) return null
    // Ignore date-only edits where the numeric grade has not changed.
    return markValue?.toString() ?: markAndDate?.trim()?.takeIf { it.isNotEmpty() }
}

private fun BarsDiscipline.gradedActivities(): Map<GradeKey, Pair<BarsActivity, String?>> {
    val counts = mutableMapOf<Triple<BarsActivityType, String, String>, Int>()
    return activities.asSequence().filter { trackedActivity(it.type) }.associate { activity ->
        val base = Triple(activity.type, activity.name.orEmpty().trim(), activity.weekNum.orEmpty().trim())
        val occurrence = counts.getOrDefault(base, 0)
        counts[base] = occurrence + 1
        GradeKey(base.first, base.second, base.third, occurrence) to
            (activity to activity.gradeFingerprint())
    }
}

internal fun detectBarsGradeChanges(
    previous: BarsExtractionPayload?,
    current: BarsExtractionPayload,
): Map<String, List<BarsGradeChange>> {
    if (previous == null || previous.disciplines.isEmpty()) return emptyMap()
    if (previous.name.isNotBlank() && current.name.isNotBlank() &&
        previous.name.trim() != current.name.trim()) return emptyMap()
    if (previous.group.isNotBlank() && current.group.isNotBlank() &&
        previous.group.trim() != current.group.trim()) return emptyMap()
    if (previous.semester.isNotBlank() && current.semester.isNotBlank() &&
        previous.semester.trim() != current.semester.trim()) return emptyMap()

    val old = previous.disciplines.associateBy { it.disciplineName.trim() }
    return current.disciplines.mapNotNull { next ->
        val before = old[next.disciplineName.trim()] ?: return@mapNotNull null
        val priorActivities = before.gradedActivities()
        val changed = next.gradedActivities().mapNotNull { (key, pair) ->
            val (activity, grade) = pair
            if (grade == null) return@mapNotNull null
            val was = priorActivities[key]?.second
            if (was == grade) return@mapNotNull null
            BarsGradeChange(
                next.disciplineName,
                activity.name?.takeIf(String::isNotBlank)
                    ?: if (activity.type == BarsActivityType.FINAL_MARK) "Итог"
                        else "Контрольное мероприятие",
                activity.type, was, grade,
            )
        }
        if (changed.isEmpty()) null else next.disciplineName to changed
    }.toMap()
}

internal fun gradeChangesCaption(changes: List<BarsGradeChange>): String {
    val created = changes.count { it.isNew }
    val corrected = changes.size - created
    return when {
        created > 0 && corrected > 0 -> "Новых: $created · исправлено: $corrected"
        created > 0 -> if (created == 1) "Новая оценка" else "Новых оценок: $created"
        corrected > 0 -> if (corrected == 1) "Оценка изменена" else "Изменено оценок: $corrected"
        else -> ""
    }
}

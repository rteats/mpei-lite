package com.rteats.mpeineo.ui

/**
 * A single source of truth for the vertical week-change gesture.
 *
 * The consumed portion is returned separately so a reversing finger first
 * collapses the pull, then gives any remaining drag back to LazyColumn.
 * This also avoids competing with Compose's own edge stretch.
 */
internal data class WeekPullStep(
    val distance: Float,
    val consumedY: Float,
)

internal fun advanceWeekPull(
    distance: Float,
    deltaY: Float,
    atStart: Boolean,
    atEnd: Boolean,
    maxDistance: Float,
): WeekPullStep {
    require(maxDistance > 0f)
    if (deltaY == 0f) return WeekPullStep(distance, 0f)

    return when {
        distance > 0f -> WeekPullStep(
            distance = (distance + deltaY).coerceIn(0f, maxDistance),
            consumedY = if (deltaY < 0f) deltaY.coerceAtLeast(-distance) else deltaY,
        )
        distance < 0f -> WeekPullStep(
            distance = (distance + deltaY).coerceIn(-maxDistance, 0f),
            consumedY = if (deltaY > 0f) deltaY.coerceAtMost(-distance) else deltaY,
        )
        deltaY > 0f && atStart -> WeekPullStep(deltaY.coerceAtMost(maxDistance), deltaY)
        deltaY < 0f && atEnd -> WeekPullStep(deltaY.coerceAtLeast(-maxDistance), deltaY)
        else -> WeekPullStep(0f, 0f)
    }
}

/** Returns -1 for previous week, +1 for next week, or 0 to cancel. */
internal fun weekDeltaForPull(distance: Float, threshold: Float): Int {
    require(threshold > 0f)
    return when {
        distance >= threshold -> -1
        distance <= -threshold -> 1
        else -> 0
    }
}

/**
 * Preview date range for an adjacent week. The navigation cue uses the same
 * Monday-to-Sunday boundaries as the visible agenda, including year changes.
 */
internal fun weekRange(monday: java.time.LocalDate, offset: Int):
    Pair<java.time.LocalDate, java.time.LocalDate> {
    val start = monday.plusWeeks(offset.toLong())
    return start to start.plusDays(6)
}

/** Fire only when a deliberate pull first crosses the switch threshold. */
internal fun shouldTickWeekPull(
    oldDistance: Float,
    newDistance: Float,
    threshold: Float,
    alreadyTicked: Boolean,
): Boolean = !alreadyTicked &&
    weekDeltaForPull(oldDistance, threshold) == 0 &&
    weekDeltaForPull(newDistance, threshold) != 0

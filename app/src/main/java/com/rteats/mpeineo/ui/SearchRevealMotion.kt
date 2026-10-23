package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.Rect

/**
 * Shared progress with simple staged phases. The background, controls and
 * result content expand independently; only the input field has a positional
 * transformation. All phases reverse using the exact same function.
 */
internal data class SearchElementsProgress(
    val field: Float,
    val background: Float,
    val controls: Float,
    val content: Float,
)

internal fun searchElementsProgress(progress: Float): SearchElementsProgress {
    val t = progress.coerceIn(0f, 1f)
    return SearchElementsProgress(
        field = t,
        background = (t / 0.55f).coerceIn(0f, 1f),
        controls = ((t - 0.06f) / 0.64f).coerceIn(0f, 1f),
        content = ((t - 0.23f) / 0.77f).coerceIn(0f, 1f),
    )
}

internal data class SearchFieldTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/** Keep the input in its real collapsed slot, expanding only that element. */
internal fun searchFieldTransform(
    source: Rect,
    destination: Rect,
    progress: Float,
): SearchFieldTransform {
    if (source.width <= 0f || source.height <= 0f ||
        destination.width <= 0f || destination.height <= 0f
    ) return SearchFieldTransform()
    val remaining = 1f - progress.coerceIn(0f, 1f)
    if (remaining == 0f) return SearchFieldTransform()
    return SearchFieldTransform(
        translationX = (source.center.x - destination.center.x) * remaining,
        translationY = (source.center.y - destination.center.y) * remaining,
        scaleX = 1f + (source.width / destination.width - 1f) * remaining,
        scaleY = 1f + (source.height / destination.height - 1f) * remaining,
    )
}

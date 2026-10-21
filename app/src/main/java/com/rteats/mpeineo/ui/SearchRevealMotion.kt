package com.rteats.mpeineo.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Material-style container reveal. The surface's bounds interpolate from the
 * measured collapsed search field, not an arbitrary offscreen start position.
 * The result content never scales. Only the transitioning search input has a
 * small scale adjustment to match the original search field bounds.
 */
internal fun searchRevealRect(field: Rect, viewport: Size, progress: Float): Rect {
    if (field.width <= 0f || field.height <= 0f) {
        return Rect(0f, 0f, viewport.width, viewport.height)
    }
    val t = progress.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    return Rect(
        lerp(field.left, 0f),
        lerp(field.top, 0f),
        lerp(field.right, viewport.width),
        lerp(field.bottom, viewport.height),
    )
}

internal data class SearchFieldTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/** Move the expanded input from its real collapsed bounds without moving layout siblings. */
internal fun searchFieldTransform(source: Rect, destination: Rect, progress: Float): SearchFieldTransform {
    if (source.width <= 0f || source.height <= 0f ||
        destination.width <= 0f || destination.height <= 0f) return SearchFieldTransform()
    val remaining = 1f - progress.coerceIn(0f, 1f)
    if (remaining == 0f) return SearchFieldTransform()
    return SearchFieldTransform(
        translationX = (source.center.x - destination.center.x) * remaining,
        translationY = (source.center.y - destination.center.y) * remaining,
        scaleX = 1f + (source.width / destination.width - 1f) * remaining,
        scaleY = 1f + (source.height / destination.height - 1f) * remaining,
    )
}

internal class SearchRevealShape(
    private val entry: Rect,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val bounds = searchRevealRect(entry, size, progress)
        val radius = with(density) { 28.dp.toPx() } * (1f - progress.coerceIn(0f, 1f))
        return Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
    }
}

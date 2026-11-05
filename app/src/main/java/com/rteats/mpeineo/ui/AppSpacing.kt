package com.rteats.mpeineo.ui

import androidx.compose.ui.unit.dp

/**
 * Shared 4dp spatial scale for app-owned layouts.
 *
 * Prefer Material 3 component defaults for their internal paddings. These tokens
 * apply to the spacing BETWEEN app sections and inside custom containers.
 * Exception sizes (e.g. 44dp icon badge) should remain documented.
 */
internal object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    val screenHorizontal = md
    val sectionGap = sm
    val contentGap = xs
}

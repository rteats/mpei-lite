package com.rteats.mpeineo.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/**
 * Common Material 3 Expressive page heading. LargeFlexibleTopAppBar owns its
 * expanded/collapsed typography; callers supply the same exit-until-collapsed
 * scroll connection to their scrolling content. The host app already consumes
 * system bars, so these nested screen headers must not reapply status insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageFlexibleAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeFlexibleTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        scrollBehavior = scrollBehavior,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

package com.rteats.mpeineo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Shared full-screen spatial motion. The exiting screen remains composed until
 * its spring has settled, so the previous page is exposed during Back.
 * Do not apply this to the persistent BARS WebView: its lifecycle is owned by
 * BarsWebViewTransport.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpatialScreenOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring,
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = spring,
        ),
    ) {
        content()
    }
}

/** Collapsible content changes height with a fast spatial spring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpatialExpansion(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring,
        ),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = spring,
        ),
    ) {
        content()
    }
}

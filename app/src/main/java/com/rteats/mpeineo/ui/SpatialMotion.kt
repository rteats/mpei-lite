package com.rteats.mpeineo.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Peer destinations fade while their independent Compose state survives disposal. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PrimaryDestinationMotion(
    destination: AppTab,
    backToSchedule: (() -> Unit)?,
    content: @Composable (AppTab) -> Unit,
) {
    val state = remember { SeekableTransitionState(destination) }
    val transition = rememberTransition(state, label = "Primary destination")
    val savedState = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(destination) { state.animateTo(destination) }

    if (backToSchedule != null) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { event -> state.seekTo(event.progress, AppTab.SCHEDULE) }
                backToSchedule()
            } catch (cancelled: CancellationException) {
                scope.launch { state.animateTo(destination) }
                throw cancelled
            }
        }
    }

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
    ) { tab ->
        savedState.SaveableStateProvider(tab.name) { content(tab) }
    }
}

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
    skipExitAnimation: Boolean = false,
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
        exit = if (skipExitAnimation) ExitTransition.None else slideOutHorizontally(
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

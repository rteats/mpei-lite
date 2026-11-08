package com.rteats.mpeineo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The predictive gesture owns a committed exit; the overlay owns ordinary Back. */
@Composable
internal fun MailDetailOverlay(
    visible: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = DetailScreenOverlay(visible, enabled, onBack, modifier,
    swipeTag = "mail-detail-swipe-back", content = content)

/** Full-screen detail layer shared by mail and Settings. */
@Composable
internal fun DetailScreenOverlay(
    visible: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    swipeTag: String = "detail-swipe-back",
    content: @Composable () -> Unit,
) {
    var dismissedByGesture by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) dismissedByGesture = false
    }
    SpatialScreenOverlay(
        visible = visible,
        skipExitAnimation = dismissedByGesture,
        modifier = modifier,
    ) {
        PredictiveBackDetail(
            onBack = onBack,
            onGestureCommit = { dismissedByGesture = true },
            enabled = enabled && visible,
            modifier = Modifier.testTag(swipeTag),
            content = content,
        )
    }
}

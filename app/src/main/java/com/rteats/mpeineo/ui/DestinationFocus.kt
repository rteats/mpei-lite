package com.rteats.mpeineo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Common, short-lived destination cue for a link into native content.
 * The caller owns scrolling/navigation. A distinct request key restarts the
 * cue even when the same destination is opened twice in succession.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberDestinationFocusPulse(requestKey: Any?): Float {
    // A busy schedule has many lesson cards; only a linked destination needs
    // animation state, not every visible row.
    if (requestKey == null) return 0f
    val pulse = remember { Animatable(0f) }
    // Focus only changes emphasis (alpha/color), not geometry. Effects
    // springs avoid overshoot; the pause is an intentional reading interval.
    val focusEnter = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val focusExit = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(requestKey) {
        pulse.snapTo(0f)
        pulse.animateTo(1f, animationSpec = focusEnter)
        delay(850L)
        pulse.animateTo(0f, animationSpec = focusExit)
    }
    return pulse.value
}

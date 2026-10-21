package com.rteats.mpeineo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Common, short-lived destination cue for a link into native content.
 * The caller owns scrolling/navigation. A distinct request key restarts the
 * cue even when the same destination is opened twice in succession.
 */
@Composable
internal fun rememberDestinationFocusPulse(requestKey: Any?): Float {
    // A busy schedule has many lesson cards; only a linked destination needs
    // animation state, not every visible row.
    if (requestKey == null) return 0f
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(requestKey) {
        pulse.snapTo(0f)
        pulse.animateTo(1f, animationSpec = tween(180))
        delay(850L)
        pulse.animateTo(
            0f,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
        )
    }
    return pulse.value
}

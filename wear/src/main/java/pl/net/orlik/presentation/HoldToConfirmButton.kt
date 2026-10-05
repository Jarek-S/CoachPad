package pl.net.orlik.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** How long the end-match button must be held, see docs section 8. */
const val END_MATCH_HOLD_MILLIS = 2_000

/**
 * Circular button that fires [onConfirm] only after being held for [holdMillis].
 * A ring along its edge fills while held; releasing early cancels and resets it.
 * Vibrates once on confirmation.
 */
@Composable
fun HoldToConfirmButton(
    onConfirm: () -> Unit,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    holdMillis: Int = END_MATCH_HOLD_MILLIS,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnConfirm by rememberUpdatedState(onConfirm)

    Box(
        modifier = modifier
            .size(size)
            .ring(width = 3.dp, trackColor = trackColor, progressColor = progressColor, progress = { progress.value })
            .pointerInput(holdMillis) {
                awaitEachGesture {
                    awaitFirstDown()
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentOnConfirm()
                    }
                    waitForUpOrCancellation()
                    hold.cancel()
                    scope.launch { progress.snapTo(0f) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

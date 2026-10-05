package pl.net.orlik.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.launch

/** How long the end-match button must be held, see docs section 8. */
const val END_MATCH_HOLD_MILLIS = 2_000

/**
 * Circular button that fires [onConfirm] only after being held for [holdMillis].
 * A ring around it fills while held; releasing early cancels and resets it.
 * Vibrates once on confirmation.
 */
@Composable
fun HoldToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = END_MATCH_HOLD_MILLIS,
    size: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .size(size)
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = this.size.copy(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            drawArc(
                ringColor, -90f, 360f * progress.value, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(trackColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

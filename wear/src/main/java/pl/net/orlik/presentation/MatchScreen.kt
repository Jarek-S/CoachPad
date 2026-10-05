package pl.net.orlik.presentation

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import java.util.Date
import kotlinx.coroutines.delay
import pl.net.orlik.R
import pl.net.orlik.data.ActiveMatch
import pl.net.orlik.presentation.theme.MatchNotesTheme
import pl.net.orlik.presentation.theme.MatchTimeColor
import pl.net.orlik.presentation.theme.MicColor
import pl.net.orlik.presentation.theme.OutlineColor
import pl.net.orlik.presentation.theme.SmallIconColor
import pl.net.orlik.presentation.theme.SmallLabelColor
import pl.net.orlik.presentation.theme.WallTimeColor
import pl.net.orlik.recording.MAX_RECORDING_MILLIS
import pl.net.orlik.shared.playTimeMillis

/** Clock opacity while the match is paused (docs section 10). */
private const val PAUSED_CLOCK_ALPHA = 0.4f

/** Stateless screen: draws [state], reports taps via callbacks (easy to preview). */
@Composable
fun MatchScreen(
    state: MatchUiState,
    onStartMatch: () -> Unit,
    onToggleRecording: () -> Unit,
    onTogglePause: () -> Unit,
    onEndMatch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            MatchUiState.Loading -> Unit
            MatchUiState.NoMatch -> StartMatch(onStartMatch)
            is MatchUiState.InProgress -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MatchClock(state.match)
                MicButton(isRecording = state.isRecording, onClick = onToggleRecording)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PauseButton(isPaused = state.match.isPaused, onClick = onTogglePause)
                    EndMatchButton(onEndMatch)
                }
            }
        }
    }
}

@Composable
private fun StartMatch(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GreenRoundButton(
            iconRes = R.drawable.ic_play,
            contentDescription = stringResource(R.string.start_match),
            size = 72.dp,
            onClick = onClick,
        )
        Text(stringResource(R.string.start_match), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Match time; a tap switches to wall-clock time (white) and back. */
@Composable
private fun MatchClock(match: ActiveMatch) {
    var showWallTime by rememberSaveable { mutableStateOf(false) }
    // Current time, refreshed every second while the clock is on screen
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    val context = LocalContext.current
    val text = if (showWallTime) {
        DateFormat.getTimeFormat(context).format(Date(now))
    } else {
        formatPlayTime(playTimeMillis(now, match.startedAt, match.pauses))
    }
    Text(
        text = text,
        color = if (showWallTime) WallTimeColor else MatchTimeColor,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
        // Fixed-width digits, so the clock does not jitter as it ticks
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        modifier = Modifier
            .alpha(if (match.isPaused && !showWallTime) PAUSED_CLOCK_ALPHA else 1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                showWallTime = !showWallTime
            },
    )
}

/** Main action: tap to start, tap again to stop (also stops by itself after the limit). */
@Composable
private fun MicButton(isRecording: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val limitProgress = remember { Animatable(0f) }
    // Vibrate on every start/stop, including the automatic stop at the time limit
    var lastRecording by remember { mutableStateOf(isRecording) }
    LaunchedEffect(isRecording) {
        if (isRecording != lastRecording) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            lastRecording = isRecording
        }
        limitProgress.snapTo(0f)
        if (isRecording) limitProgress.animateTo(1f, tween(MAX_RECORDING_MILLIS, easing = LinearEasing))
    }
    // Screen-off would move the app to the background and mute the microphone
    DisposableEffect(isRecording) {
        view.keepScreenOn = isRecording
        onDispose { view.keepScreenOn = false }
    }
    Box(
        modifier = Modifier
            .size(70.dp)
            .ring(
                width = 3.dp,
                trackColor = if (isRecording) OutlineColor else Color.Transparent,
                progressColor = MatchTimeColor,
                progress = { limitProgress.value },
            ),
        contentAlignment = Alignment.Center,
    ) {
        GreenRoundButton(
            iconRes = if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic,
            contentDescription = stringResource(if (isRecording) R.string.stop_recording else R.string.record),
            size = 58.dp,
            onClick = onClick,
        )
    }
}

@Composable
private fun GreenRoundButton(@DrawableRes iconRes: Int, contentDescription: String, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MicColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Plain tap with a confirming vibration. */
@Composable
private fun PauseButton(isPaused: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    SmallButtonColumn(label = stringResource(if (isPaused) R.string.resume_label else R.string.pause_label)) {
        Box(
            modifier = Modifier
                .size(SmallButtonSize)
                .clip(CircleShape)
                .ring(width = 3.dp, trackColor = OutlineColor)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            SmallIcon(
                iconRes = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause,
                contentDescription = stringResource(if (isPaused) R.string.resume_match else R.string.pause_match),
            )
        }
    }
}

/** Ends the match after a 2 s hold, see [HoldToConfirmButton]. */
@Composable
private fun EndMatchButton(onEndMatch: () -> Unit) {
    SmallButtonColumn(label = stringResource(R.string.end_label)) {
        HoldToConfirmButton(
            onConfirm = onEndMatch,
            trackColor = OutlineColor,
            progressColor = MatchTimeColor,
            size = SmallButtonSize,
        ) {
            SmallIcon(R.drawable.ic_flag, stringResource(R.string.end_match))
        }
    }
}

/** Minimum Wear OS touch target. */
private val SmallButtonSize = 48.dp

@Composable
private fun SmallButtonColumn(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Text(label, color = SmallLabelColor, fontSize = 11.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun SmallIcon(@DrawableRes iconRes: Int, contentDescription: String) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = SmallIconColor,
        modifier = Modifier.size(22.dp),
    )
}

@WearPreviewDevices
@Composable
fun NoMatchPreview() {
    MatchNotesTheme {
        MatchScreen(MatchUiState.NoMatch, onStartMatch = {}, onToggleRecording = {}, onTogglePause = {}, onEndMatch = {})
    }
}

@WearPreviewDevices
@Composable
fun InProgressPreview() {
    val match = ActiveMatch(matchId = "preview", startedAt = System.currentTimeMillis() - 34 * 60_000)
    MatchNotesTheme {
        MatchScreen(
            MatchUiState.InProgress(match),
            onStartMatch = {}, onToggleRecording = {}, onTogglePause = {}, onEndMatch = {},
        )
    }
}

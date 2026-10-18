package pl.net.orlik.presentation

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.State
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
import pl.net.orlik.presentation.theme.RecordingColor
import pl.net.orlik.presentation.theme.OutlineColor
import pl.net.orlik.presentation.theme.ShiftColor
import pl.net.orlik.presentation.theme.EdgeTrackColor
import pl.net.orlik.presentation.theme.SmallIconColor
import pl.net.orlik.presentation.theme.SmallLabelColor
import pl.net.orlik.presentation.theme.WallTimeColor
import pl.net.orlik.recording.MAX_RECORDING_MILLIS
import pl.net.orlik.shared.playTimeMillis
import pl.net.orlik.timer.ShiftTimer
import pl.net.orlik.timer.ShiftTimerState

/** Clock opacity while the match is paused (docs section 10). */
private const val PAUSED_CLOCK_ALPHA = 0.4f

/** Stateless screen: draws [state], reports taps via callbacks (easy to preview). */
@Composable
fun MatchScreen(
    state: MatchUiState,
    onStartMatch: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleRecording: () -> Unit,
    onConfirmShift: () -> Unit,
    onToggleShiftTimer: () -> Unit,
    onTogglePause: () -> Unit,
    onEndMatch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            MatchUiState.Loading -> Unit
            MatchUiState.NoMatch -> StartMatch(onStartMatch, onOpenSettings)
            is MatchUiState.InProgress ->
                MatchInProgress(state, onToggleRecording, onConfirmShift, onToggleShiftTimer, onTogglePause, onEndMatch)
        }
    }
}

@Composable
private fun MatchInProgress(
    state: MatchUiState.InProgress,
    onToggleRecording: () -> Unit,
    onConfirmShift: () -> Unit,
    onToggleShiftTimer: () -> Unit,
    onTogglePause: () -> Unit,
    onEndMatch: () -> Unit,
) {
    val match = state.match
    val now by currentTimeMillis()
    // Null when the shift timer is disabled: then none of its elements are shown
    val shiftState = match.shiftTimer?.stateAt(playTimeMillis(now, match.startedAt, match.pauses))
    if (match.shiftTimer != null && shiftState != null) {
        ShiftTimerEdge(match.shiftTimer, shiftState, isPaused = match.isPaused)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MatchClock(match, now, shiftState, onToggleShiftTimer)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MicButton(isRecording = state.isRecording, failedStarts = state.failedStarts, onClick = onToggleRecording)
            if (shiftState is ShiftTimerState.AwaitingConfirmation) ConfirmShiftButton(onConfirmShift)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PauseButton(isPaused = match.isPaused, onClick = onTogglePause)
            EndMatchButton(onEndMatch)
        }
    }
}

/** Current time, refreshed on every full second while on screen. */
@Composable
private fun currentTimeMillis(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000 - value % 1000)
    }
}

/**
 * Arc along the screen edge with the time left to the next substitution (full circle = whole
 * interval); a full amber ring once the substitution is due. Dimmed during a pause.
 */
@Composable
private fun ShiftTimerEdge(timer: ShiftTimer, state: ShiftTimerState, isPaused: Boolean) {
    val alpha = if (isPaused) PAUSED_CLOCK_ALPHA else 1f
    val modifier = when (state) {
        is ShiftTimerState.Running -> Modifier.ring(
            width = EdgeArcWidth,
            trackColor = EdgeTrackColor,
            progressColor = MatchTimeColor.copy(alpha = alpha),
            progress = { state.remainingMillis.toFloat() / timer.intervalMillis },
        )
        else -> Modifier.ring(width = EdgeArcWidth, trackColor = ShiftColor.copy(alpha = alpha))
    }
    Box(Modifier.fillMaxSize().padding(2.dp).then(modifier))
}

private val EdgeArcWidth = 4.dp

@Composable
private fun StartMatch(onClick: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundButton(
            iconRes = R.drawable.ic_play,
            contentDescription = stringResource(R.string.start_match),
            size = 72.dp,
            onClick = onClick,
        )
        Text(stringResource(R.string.start_match), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        // Settings are reachable only between matches: a match keeps the settings it started with
        // Green outline like the start button above it
        Box(
            modifier = Modifier
                .size(SmallButtonSize)
                .clip(CircleShape)
                .ring(width = 3.dp, trackColor = MicColor)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            SmallIcon(R.drawable.ic_settings, stringResource(R.string.settings))
        }
    }
}

/** What the match clock shows; a tap moves on to the next one. */
private enum class ClockView { MATCH_TIME, WALL_TIME, SHIFT_TIME }

/**
 * Match time; taps cycle through wall-clock time (white) and, when the shift timer is on,
 * time to the next substitution (amber), then back to match time.
 */
@Composable
private fun MatchClock(match: ActiveMatch, now: Long, shiftState: ShiftTimerState?, onToggleShiftTimer: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var view by rememberSaveable { mutableStateOf(ClockView.MATCH_TIME) }
    val shown = if (view == ClockView.SHIFT_TIME && shiftState == null) ClockView.MATCH_TIME else view
    val context = LocalContext.current
    val text = when (shown) {
        ClockView.MATCH_TIME -> formatPlayTime(playTimeMillis(now, match.startedAt, match.pauses))
        ClockView.WALL_TIME -> DateFormat.getTimeFormat(context).format(Date(now))
        ClockView.SHIFT_TIME -> formatShiftTime(shiftState!!)
    }
    Text(
        text = text,
        color = when (shown) {
            ClockView.MATCH_TIME -> MatchTimeColor
            ClockView.WALL_TIME -> WallTimeColor
            ClockView.SHIFT_TIME -> ShiftColor
        },
        fontSize = 44.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
        // Fixed-width digits, so the clock does not jitter as it ticks
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        modifier = Modifier
            // Match time and time to substitution both stand still during a pause
            .alpha(if (match.isPaused && shown != ClockView.WALL_TIME) PAUSED_CLOCK_ALPHA else 1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Holding turns the shift timer on or off (docs section 10); felt, as its arc may be hidden
                onLongClick = {
                    haptic.performHapticFeedback(if (shiftState == null) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onToggleShiftTimer()
                },
            ) {
                view = when (shown) {
                    ClockView.MATCH_TIME -> ClockView.WALL_TIME
                    ClockView.WALL_TIME -> if (shiftState != null) ClockView.SHIFT_TIME else ClockView.MATCH_TIME
                    ClockView.SHIFT_TIME -> ClockView.MATCH_TIME
                }
            },
    )
}

/** Main action: tap to start, tap again to stop (also stops by itself after the limit). */
@Composable
private fun MicButton(isRecording: Boolean, failedStarts: Int, onClick: () -> Unit) {
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
    // A start that failed changes nothing on screen, so it must be felt (design doc, section 8)
    LaunchedEffect(failedStarts) {
        if (failedStarts > 0) haptic.performHapticFeedback(HapticFeedbackType.Reject)
    }
    // Screen-off would move the app to the background and mute the microphone
    DisposableEffect(isRecording) {
        view.keepScreenOn = isRecording
        onDispose { view.keepScreenOn = false }
    }
    Box(
        modifier = Modifier
            .size(BigButtonBoxSize)
            .ring(
                width = 3.dp,
                trackColor = if (isRecording) OutlineColor else Color.Transparent,
                progressColor = MatchTimeColor,
                progress = { limitProgress.value },
            ),
        contentAlignment = Alignment.Center,
    ) {
        RoundButton(
            iconRes = if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic,
            contentDescription = stringResource(if (isRecording) R.string.stop_recording else R.string.record),
            size = BigButtonSize,
            onClick = onClick,
            color = if (isRecording) RecordingColor else MicColor,
        )
    }
}

/** Shown next to the microphone while the substitution is due; a plain tap with a vibration. */
@Composable
private fun ConfirmShiftButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // Same box as the microphone, so both buttons line up
    Box(modifier = Modifier.size(BigButtonBoxSize), contentAlignment = Alignment.Center) {
        RoundButton(
            iconRes = R.drawable.ic_check,
            contentDescription = stringResource(R.string.confirm_shift),
            size = BigButtonSize,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
            color = ShiftColor,
            // White is hard to see on amber
            iconTint = Color.Black,
        )
    }
}

private val BigButtonSize = 58.dp

/** Big button with room for the recording limit ring around it. */
private val BigButtonBoxSize = 70.dp

@Composable
private fun RoundButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    color: Color = MicColor,
    iconTint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
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
        MatchScreen(
            MatchUiState.NoMatch,
            onStartMatch = {}, onOpenSettings = {}, onToggleRecording = {}, onConfirmShift = {}, onToggleShiftTimer = {}, onTogglePause = {}, onEndMatch = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun InProgressPreview() {
    val match = ActiveMatch(matchId = "preview", startedAt = System.currentTimeMillis() - 34 * 60_000)
    MatchNotesTheme {
        MatchScreen(
            MatchUiState.InProgress(match),
            onStartMatch = {}, onOpenSettings = {}, onToggleRecording = {}, onConfirmShift = {}, onToggleShiftTimer = {}, onTogglePause = {}, onEndMatch = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun ShiftAlarmPreview() {
    val match = ActiveMatch(
        matchId = "preview",
        startedAt = System.currentTimeMillis() - 39 * 60_000,
        shiftTimer = ShiftTimer(intervalMillis = 5 * 60_000L, lastResetPlayMillis = 33 * 60_000L),
    )
    MatchNotesTheme {
        MatchScreen(
            MatchUiState.InProgress(match),
            onStartMatch = {}, onOpenSettings = {}, onToggleRecording = {}, onConfirmShift = {}, onToggleShiftTimer = {}, onTogglePause = {}, onEndMatch = {},
        )
    }
}

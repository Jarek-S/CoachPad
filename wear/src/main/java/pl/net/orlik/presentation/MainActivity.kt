package pl.net.orlik.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kotlinx.coroutines.delay
import pl.net.orlik.R
import pl.net.orlik.data.ActiveMatch
import pl.net.orlik.presentation.theme.MatchNotesTheme
import pl.net.orlik.shared.playTimeMillis

private val MatchTimeColor = Color(0xFF5DCAA5)
private val MicColor = Color(0xFF1D9E75)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp(viewModel: MatchViewModel = viewModel(factory = MatchViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // System dialog asking for microphone access; recording starts once granted
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleRecording()
    }
    // Leaving the screen ends a recording in progress (background apps get a muted microphone)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopRecording() }

    MatchNotesTheme {
        AppScaffold {
            ScreenScaffold {
                MatchScreen(
                    state = state,
                    onStartMatch = viewModel::startMatch,
                    onToggleRecording = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.toggleRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onTogglePause = viewModel::togglePause,
                    onEndMatch = viewModel::endMatch,
                )
            }
        }
    }
}

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
            MatchUiState.NoMatch -> Button(onClick = onStartMatch) {
                Text(stringResource(R.string.start_match))
            }
            is MatchUiState.InProgress -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MatchClock(state.match)
                MicButton(isRecording = state.isRecording, onClick = onToggleRecording)
                // Temporary text labels; replaced by pause and flag icons (separate checklist item)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PauseButton(isPaused = state.match.isPaused, onClick = onTogglePause)
                    HoldToConfirmButton(onConfirm = onEndMatch, size = 72.dp) {
                        SmallLabel(stringResource(R.string.end_match))
                    }
                }
            }
        }
    }
}

/** Main action: tap to start, tap again to stop (also stops by itself after the limit). */
@Composable
private fun MicButton(isRecording: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    // Vibrate on every start/stop, including the automatic stop at the time limit
    var lastRecording by remember { mutableStateOf(isRecording) }
    LaunchedEffect(isRecording) {
        if (isRecording != lastRecording) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            lastRecording = isRecording
        }
    }
    // Screen-off would move the app to the background and mute the microphone
    DisposableEffect(isRecording) {
        view.keepScreenOn = isRecording
        onDispose { view.keepScreenOn = false }
    }
    // Temporary text label; replaced by a microphone icon (separate checklist item)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MicColor, contentColor = Color.White),
    ) {
        Text(stringResource(if (isRecording) R.string.stop_recording else R.string.record))
    }
}

/** Plain tap with a confirming vibration; sized to match [HoldToConfirmButton]. */
@Composable
private fun PauseButton(isPaused: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .padding(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        SmallLabel(stringResource(if (isPaused) R.string.resume_match else R.string.pause_match))
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text = text, fontSize = 11.sp, textAlign = TextAlign.Center)
}

@Composable
private fun MatchClock(match: ActiveMatch) {
    // Current time, refreshed every second while the clock is on screen
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    Text(
        text = formatPlayTime(playTimeMillis(now, match.startedAt, match.pauses)),
        color = MatchTimeColor,
        fontSize = 40.sp,
    )
}

@WearPreviewDevices
@Composable
fun NoMatchPreview() {
    MatchNotesTheme { MatchScreen(state = MatchUiState.NoMatch, onStartMatch = {}, onToggleRecording = {}, onTogglePause = {}, onEndMatch = {}) }
}

@WearPreviewDevices
@Composable
fun InProgressPreview() {
    val match = ActiveMatch(matchId = "preview", startedAt = System.currentTimeMillis() - 35 * 60_000)
    MatchNotesTheme { MatchScreen(state = MatchUiState.InProgress(match), onStartMatch = {}, onToggleRecording = {}, onTogglePause = {}, onEndMatch = {}) }
}

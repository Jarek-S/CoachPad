package pl.net.orlik.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import pl.net.orlik.R
import pl.net.orlik.data.Match
import pl.net.orlik.data.Team
import pl.net.orlik.export.Score
import pl.net.orlik.review.LiveMatch
import pl.net.orlik.review.LiveScore
import pl.net.orlik.review.LiveStatus
import pl.net.orlik.review.QuickEvent
import pl.net.orlik.review.formatEventTime
import pl.net.orlik.shared.EventType
import pl.net.orlik.ui.theme.GlassBorder
import pl.net.orlik.ui.theme.GlassPanel
import pl.net.orlik.ui.theme.LiveClock
import pl.net.orlik.ui.theme.CoachPadTheme
import pl.net.orlik.ui.theme.Microphone
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.OnStadiumHint
import pl.net.orlik.ui.theme.OnStadiumMuted
import pl.net.orlik.ui.theme.RecordingRed
import pl.net.orlik.ui.theme.RedCard
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

/**
 * A match recorded live on the phone (design doc, section 3.3): the play clock and score, quick
 * buttons for goals, cards and notes, the microphone, pause and resume by a tap, the end by
 * holding. The screen stays on; leaving it does not stop the match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchScreen(
    state: LiveMatchUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onQuick: (QuickEvent) -> Unit,
    onToggleRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onType: (EventType) -> Unit,
    onTeam: (Team) -> Unit,
    onPlayer: (String) -> Unit,
    onAssist: (String) -> Unit,
    onNote: (String) -> Unit,
    onDeleteQuick: () -> Unit,
    onCloseSheet: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Android silences the microphone of an app in the background: a recording ends with the screen
    LifecycleStartEffect(Unit) { onStopOrDispose { onStopRecording() } }
    val live = state.live
    val paused = live?.status == LiveStatus.PAUSED
    val running = live != null && (live.status == LiveStatus.RUNNING || paused)
    state.editing?.let { edit ->
        QuickEventSheet(
            draft = edit.draft,
            time = edit.recordedAt?.let { formatEventTime(live?.playTimeAt(it), minuteOnly = false) } ?: "",
            match = state.match,
            lineup = state.lineup,
            onType = onType,
            onTeam = onTeam,
            onPlayer = onPlayer,
            onAssist = onAssist,
            onNote = onNote,
            onDelete = onDeleteQuick,
            onDone = onCloseSheet,
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = OnStadium,
        topBar = {
            TopAppBar(
                colors = stadiumTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(matchTitle(state.match?.homeTeam, state.match?.awayTeam))
                        Text(
                            stringResource(if (paused) R.string.live_paused else R.string.live_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (paused) OnStadiumHint else OnStadiumMuted,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (running) LiveButtons(paused, onPause, onResume, onEnd, Modifier.navigationBarsPadding())
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (live == null) return@Column
            PlayClock(live, now)
            ScoreLine(state.score, state.match)
            if (running) {
                QuickButtons(state.match, onQuick)
                Spacer(Modifier.size(8.dp))
                MicButton(state.recordingSince, state.micFailed, now, onToggleRecording)
            }
        }
    }
}

/** The play time, ticking while the match runs; still and muted in a pause. */
@Composable
private fun PlayClock(live: LiveMatch, now: () -> Long) {
    var at by remember { mutableLongStateOf(now()) }
    LaunchedEffect(live) {
        at = now()
        while (live.status == LiveStatus.RUNNING) {
            delay(CLOCK_TICK_MILLIS)
            at = now()
        }
    }
    Text(
        formatEventTime(live.playTimeAt(at), minuteOnly = false),
        fontSize = 80.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (live.status == LiveStatus.RUNNING) LiveClock else OnStadiumMuted,
    )
}

private const val CLOCK_TICK_MILLIS = 200L

/** "Orły 2:1 Rywale FC"; muted while a counted goal waits for its scorer under "Do przejrzenia". */
@Composable
private fun ScoreLine(score: LiveScore, match: Match?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            teamName(Team.HOME, match?.homeTeam, match?.awayTeam),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = OnStadiumMuted,
        )
        Text(
            stringResource(R.string.match_score, score.score.home, score.score.away),
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (score.provisional) OnStadiumMuted else Color.Unspecified,
        )
        Text(
            teamName(Team.AWAY, match?.homeTeam, match?.awayTeam),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = OnStadiumMuted,
        )
    }
}

/** Gol gospodarzy / Gol gości, Kartka / Notatka: each saves its event at the tap. */
@Composable
private fun QuickButtons(match: Match?, onQuick: (QuickEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickButton(stringResource(R.string.quick_goal, teamName(Team.HOME, match?.homeTeam, match?.awayTeam)), EventType.GOAL, Modifier.weight(1f)) {
                onQuick(QuickEvent.HOME_GOAL)
            }
            QuickButton(stringResource(R.string.quick_goal, teamName(Team.AWAY, match?.homeTeam, match?.awayTeam)), EventType.GOAL, Modifier.weight(1f)) {
                onQuick(QuickEvent.AWAY_GOAL)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickButton(stringResource(R.string.quick_card), EventType.YELLOW_CARD, Modifier.weight(1f)) { onQuick(QuickEvent.CARD) }
            QuickButton(stringResource(R.string.type_note), EventType.NOTE, Modifier.weight(1f)) { onQuick(QuickEvent.NOTE) }
        }
    }
}

@Composable
private fun QuickButton(label: String, type: EventType, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        EventTypeMark(type)
        Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Tap to record, tap to stop, as on the watch; red while recording, with its length. */
@Composable
private fun MicButton(recordingSince: Long?, failed: Boolean, now: () -> Long, onToggle: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onToggle()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) onToggle() else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recordingSince != null) RecordingRed else Microphone,
                contentColor = Color.White,
            ),
        ) {
            Icon(painterResource(R.drawable.ic_mic), stringResource(if (recordingSince != null) R.string.stop_mic else R.string.start_mic), Modifier.size(40.dp))
        }
        val hint = when {
            recordingSince != null -> {
                var at by remember { mutableLongStateOf(now()) }
                LaunchedEffect(recordingSince) {
                    while (true) {
                        at = now()
                        delay(CLOCK_TICK_MILLIS)
                    }
                }
                stringResource(R.string.recording_for, formatEventTime((at - recordingSince).coerceAtLeast(0), minuteOnly = false))
            }
            denied -> stringResource(R.string.mic_denied)
            failed -> stringResource(R.string.mic_failed)
            else -> stringResource(R.string.mic_hint)
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = if (denied || failed) OnStadiumHint else OnStadiumMuted)
    }
}

@Composable
private fun LiveButtons(paused: Boolean, onPause: () -> Unit, onResume: () -> Unit, onEnd: () -> Unit, modifier: Modifier) {
    GlassPanel(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.end_match_hint), style = MaterialTheme.typography.bodyMedium, color = OnStadiumMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = if (paused) onResume else onPause,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnStadium),
                    border = BorderStroke(1.dp, GlassBorder),
                ) {
                    Text(stringResource(if (paused) R.string.resume_match else R.string.pause_match))
                }
                HoldToConfirmButton(
                    onConfirm = onEnd,
                    color = RedCard.copy(alpha = 0.55f),
                    fillColor = RedCard,
                    modifier = Modifier.weight(1.6f),
                ) {
                    Text(stringResource(R.string.end_match), color = OnStadium, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview
@Composable
private fun LiveMatchScreenPreview() {
    CoachPadTheme {
        LiveMatchScreen(
            state = LiveMatchUiState(
                match = Match("m", homeTeam = "Orły", awayTeam = "Rywale FC"),
                live = LiveMatch(LiveStatus.RUNNING, startedAt = 0),
                score = LiveScore(Score(2, 1)),
            ),
            onBack = {},
            onPause = {},
            onResume = {},
            onEnd = {},
            onQuick = {},
            onToggleRecording = {},
            onStopRecording = {},
            onType = {},
            onTeam = {},
            onPlayer = {},
            onAssist = {},
            onNote = {},
            onDeleteQuick = {},
            onCloseSheet = {},
            now = { 23 * 60_000L + 41_000 },
        )
    }
}

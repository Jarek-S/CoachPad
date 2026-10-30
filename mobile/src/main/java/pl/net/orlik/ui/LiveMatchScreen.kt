package pl.net.orlik.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.net.orlik.R
import pl.net.orlik.data.Match
import pl.net.orlik.review.LiveMatch
import pl.net.orlik.review.LiveStatus
import pl.net.orlik.review.formatEventTime
import pl.net.orlik.ui.theme.GlassBorder
import pl.net.orlik.ui.theme.GlassPanel
import pl.net.orlik.ui.theme.LiveClock
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.OnStadiumHint
import pl.net.orlik.ui.theme.OnStadiumMuted
import pl.net.orlik.ui.theme.RedCard
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

/**
 * A match recorded live on the phone (design doc, section 3.3): the play clock, pause and resume
 * by a tap, the end by holding. The screen stays on; leaving it does not stop the match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchScreen(
    state: LiveMatchUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val live = state.live
    val paused = live?.status == LiveStatus.PAUSED
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
            if (live != null && (live.status == LiveStatus.RUNNING || paused)) {
                LiveButtons(paused, onPause, onResume, onEnd, Modifier.navigationBarsPadding())
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (live != null) PlayClock(live, now)
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
        fontSize = 88.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (live.status == LiveStatus.RUNNING) LiveClock else OnStadiumMuted,
    )
}

private const val CLOCK_TICK_MILLIS = 200L

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
    MatchNotesTheme {
        LiveMatchScreen(
            state = LiveMatchUiState(
                match = Match("m", homeTeam = "Orły", awayTeam = "Rywale FC"),
                live = LiveMatch(LiveStatus.RUNNING, startedAt = 0),
            ),
            onBack = {},
            onPause = {},
            onResume = {},
            onEnd = {},
            now = { 23 * 60_000L + 41_000 },
        )
    }
}

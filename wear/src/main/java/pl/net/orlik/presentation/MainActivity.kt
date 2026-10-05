package pl.net.orlik.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kotlinx.coroutines.delay
import pl.net.orlik.R
import pl.net.orlik.data.ActiveMatch
import pl.net.orlik.presentation.theme.MatchNotesTheme
import pl.net.orlik.shared.playTimeMillis

private val MatchTimeColor = Color(0xFF5DCAA5)

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
    MatchNotesTheme {
        AppScaffold {
            ScreenScaffold {
                MatchScreen(
                    state = state,
                    onStartMatch = viewModel::startMatch,
                    onEndMatch = viewModel::endMatch,
                )
            }
        }
    }
}

/** Stateless screen: draws [state], reports taps via callbacks (easy to preview). */
@Composable
fun MatchScreen(state: MatchUiState, onStartMatch: () -> Unit, onEndMatch: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            MatchUiState.Loading -> Unit
            MatchUiState.NoMatch -> Button(onClick = onStartMatch) {
                Text(stringResource(R.string.start_match))
            }
            is MatchUiState.InProgress -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MatchClock(state.match)
                // Temporary text label; replaced by a flag icon (separate checklist item)
                HoldToConfirmButton(onConfirm = onEndMatch, size = 72.dp) {
                    Text(
                        text = stringResource(R.string.end_match),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
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
    MatchNotesTheme { MatchScreen(state = MatchUiState.NoMatch, onStartMatch = {}, onEndMatch = {}) }
}

@WearPreviewDevices
@Composable
fun InProgressPreview() {
    val match = ActiveMatch(matchId = "preview", startedAt = System.currentTimeMillis() - 35 * 60_000)
    MatchNotesTheme { MatchScreen(state = MatchUiState.InProgress(match), onStartMatch = {}, onEndMatch = {}) }
}

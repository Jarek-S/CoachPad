package pl.net.orlik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pl.net.orlik.R
import pl.net.orlik.shared.sync.PendingMatch
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.ui.theme.MatchNotesTheme

/** Technical sync screen: what is still waiting on the watch. */
@Composable
fun SyncScreen(state: SyncUiState, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.pending_title), style = MaterialTheme.typography.headlineSmall)
        }
        when (state) {
            SyncUiState.Loading -> item { Text(stringResource(R.string.loading)) }
            SyncUiState.NoWatchData -> item { Text(stringResource(R.string.no_watch_data)) }
            is SyncUiState.Watch -> {
                if (state.summary.matchInProgress) {
                    item {
                        Text(
                            stringResource(R.string.match_in_progress_on_watch),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (state.summary.matches.isEmpty()) {
                    item { Text(stringResource(R.string.no_pending_matches)) }
                }
                items(state.summary.matches, key = { it.matchId }) { PendingMatchRow(it) }
            }
        }
    }
}

@Composable
private fun PendingMatchRow(match: PendingMatch) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(formatMatchStart(match.startedAt), style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.recordings, match.recordingCount, match.recordingCount) +
                    " · " + pluralStringResource(R.plurals.events, match.eventCount, match.eventCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val matchStartFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM, HH:mm", Locale.forLanguageTag("pl"))

private fun formatMatchStart(epochMillis: Long): String =
    matchStartFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@Preview(showBackground = true)
@Composable
private fun SyncScreenPreview() {
    MatchNotesTheme {
        SyncScreen(
            state = SyncUiState.Watch(
                QueueSummary(
                    matchInProgress = true,
                    matches = listOf(
                        PendingMatch("m1", 1_791_800_000_000, eventCount = 14, recordingCount = 12),
                        PendingMatch("m2", 1_791_890_000_000, eventCount = 3, recordingCount = 1),
                    ),
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}

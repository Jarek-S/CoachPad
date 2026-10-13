package pl.net.orlik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.net.orlik.R
import pl.net.orlik.shared.sync.PendingMatch
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.sync.SyncStatus
import pl.net.orlik.ui.theme.MatchNotesTheme

/** Technical sync screen: what is still waiting on the watch, and the sync buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    state: SyncUiState,
    onSync: (matchId: String?) -> Unit,
    onDelete: (matchId: String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.sync_title)) },
            )
        },
    ) { padding -> SyncContent(state, onSync, onDelete, padding) }
}

@Composable
private fun SyncContent(
    state: SyncUiState,
    onSync: (matchId: String?) -> Unit,
    onDelete: (matchId: String) -> Unit,
    contentPadding: PaddingValues,
) {
    val statusText = syncStatusText(state.sync)
    var toDelete by remember { mutableStateOf<PendingMatch?>(null) }
    toDelete?.let { match ->
        DeleteMatchDialog(
            match = match,
            onConfirm = {
                onDelete(match.matchId)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.pending_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                stringResource(R.string.events_on_phone, state.eventsOnPhone),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        statusText?.let { text -> item { Text(text, style = MaterialTheme.typography.bodyMedium) } }

        when (val watch = state.watch) {
            WatchQueue.Loading -> item { Text(stringResource(R.string.loading)) }
            WatchQueue.NoWatchData -> item { Text(stringResource(R.string.no_watch_data)) }
            is WatchQueue.Known -> {
                // The list below is what the watch reported at the last contact
                if (state.watchConnected == false) {
                    item {
                        Text(stringResource(R.string.watch_unavailable_last_known), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (watch.summary.matchInProgress) {
                    item {
                        Text(
                            stringResource(R.string.match_in_progress_on_watch),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (watch.summary.matches.isEmpty()) {
                    item { Text(stringResource(R.string.no_pending_matches)) }
                } else {
                    item {
                        Button(onClick = { onSync(null) }, enabled = state.canSync, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sync_all))
                        }
                    }
                }
                items(watch.summary.matches, key = { it.matchId }) { match ->
                    PendingMatchRow(
                        match = match,
                        enabled = state.canSync,
                        onSync = { onSync(match.matchId) },
                        onDelete = { toDelete = match },
                    )
                }
            }
        }
    }
}

@Composable
private fun syncStatusText(status: SyncStatus): String? = when (status) {
    SyncStatus.Idle -> null
    is SyncStatus.Running -> stringResource(R.string.sync_running, status.received)
    is SyncStatus.Done -> stringResource(R.string.sync_done, status.received)
    SyncStatus.Rejected -> stringResource(R.string.sync_rejected)
    SyncStatus.WatchUnavailable -> stringResource(R.string.sync_watch_unavailable)
    SyncStatus.NoResponse -> stringResource(R.string.sync_no_response)
}

@Composable
private fun PendingMatchRow(match: PendingMatch, enabled: Boolean, onSync: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatMatchStart(match.startedAt), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.recordings, match.recordingCount, match.recordingCount) +
                        " · " + pluralStringResource(R.plurals.events, match.eventCount, match.eventCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(R.string.delete), color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
            TextButton(onClick = onSync, enabled = enabled) {
                Text(stringResource(R.string.sync))
            }
        }
    }
}

/** Deleting is irreversible: the only copy of these events is on the watch. */
@Composable
private fun DeleteMatchDialog(match: PendingMatch, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_match_title)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_match_text,
                    formatMatchStart(match.startedAt),
                    pluralStringResource(R.plurals.recordings, match.recordingCount, match.recordingCount),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SyncScreenPreview() {
    MatchNotesTheme {
        SyncScreen(
            state = SyncUiState(
                watch = WatchQueue.Known(
                    QueueSummary(
                        matchInProgress = false,
                        matches = listOf(
                            PendingMatch("m1", 1_791_800_000_000, eventCount = 14, recordingCount = 12),
                            PendingMatch("m2", 1_791_890_000_000, eventCount = 3, recordingCount = 1),
                        ),
                    ),
                ),
                sync = SyncStatus.Running(received = 5),
                eventsOnPhone = 21,
                watchConnected = true,
            ),
            onSync = {},
            onDelete = {},
            onBack = {},
        )
    }
}

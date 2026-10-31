package pl.net.orlik.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.net.orlik.R
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.MatchListItem
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.deleteMatch
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.review.dayStartOf
import java.time.ZoneId
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.sync.QueueSummarySource
import pl.net.orlik.ui.theme.GreenDark
import pl.net.orlik.ui.theme.CoachPadTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.ReviewBadge
import pl.net.orlik.ui.theme.ReviewText
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

data class MatchesUiState(
    /** Null while loading. */
    val matches: List<MatchListItem>? = null,
    /** Matches still waiting on the watch, from its queue summary. */
    val pendingOnWatch: Int = 0,
)

class MatchesViewModel(
    private val dao: MatchEventDao,
    private val recordingsDir: File,
    summaries: Flow<QueueSummary?>,
) : ViewModel() {

    val state: StateFlow<MatchesUiState> = combine(dao.matches(), summaries) { list, summary ->
        MatchesUiState(list, summary?.matches?.size ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchesUiState())

    /** Creates a match planned for the chosen day and passes its id on, to open its lineups. */
    fun plan(utcDayMillis: Long, onPlanned: (String) -> Unit) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            dao.insertMatch(Match(id, plannedAt = dayStartOf(utcDayMillis, ZoneId.systemDefault())))
            onPlanned(id)
        }
    }

    fun delete(matchId: String) {
        viewModelScope.launch { deleteMatch(dao, recordingsDir, matchId) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MatchesViewModel(PhoneDatabase.get(app).matchEventDao(), recordingsDir(app), QueueSummarySource(app).summaries)
            }
        }
    }
}

/**
 * Start screen: the matches on the phone, and the way to sync more from the watch or plan the next
 * one; holding a match deletes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    state: MatchesUiState,
    onOpenMatch: (String) -> Unit,
    onOpenPlanned: (String) -> Unit,
    onOpenLive: (String) -> Unit,
    onNewMatch: (utcDayMillis: Long) -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteMatch: (String) -> Unit,
) {
    // "Nowy mecz" asks for the match day first
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    if (choosingDate) {
        MatchDayDialog(
            initialMillis = null,
            confirmLabel = stringResource(R.string.next),
            onConfirm = {
                choosingDate = false
                onNewMatch(it)
            },
            onDismiss = { choosingDate = false },
        )
    }
    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }
    state.matches?.firstOrNull { it.matchId == toDelete }?.let { match ->
        DeleteMatchDialog(match, onConfirm = { onDeleteMatch(match.matchId); toDelete = null }, onDismiss = { toDelete = null })
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = OnStadium,
        topBar = {
            TopAppBar(
                colors = stadiumTopAppBarColors(),
                title = { Text(stringResource(R.string.matches_title)) },
                actions = {
                    IconButton(onClick = onOpenSync) {
                        BadgedBox(badge = { if (state.pendingOnWatch > 0) Badge { Text("${state.pendingOnWatch}") } }) {
                            Icon(painterResource(R.drawable.ic_sync), stringResource(R.string.sync_title))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { choosingDate = true },
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text(stringResource(R.string.new_match)) },
            )
        },
    ) { padding ->
        val matches = state.matches
        if (matches != null && matches.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_matches), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onOpenSync) { Text(stringResource(R.string.sync_title)) }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Room for the "Nowy mecz" button over the end of the list
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(matches.orEmpty(), key = { it.matchId }) { match ->
                MatchCard(
                    match,
                    onClick = {
                        when {
                            match.live -> onOpenLive(match.matchId)
                            match.isPlanned -> onOpenPlanned(match.matchId)
                            else -> onOpenMatch(match.matchId)
                        }
                    },
                    onLongClick = { toDelete = match.matchId },
                )
            }
            if (matches.orEmpty().isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.delete_match_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnStadium.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: MatchListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val shape = CardDefaults.shape
    Card(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(matchTitle(match.homeTeam, match.awayTeam), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (match.live) stringResource(R.string.live_match) else matchWhen(match),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (match.live) FontWeight.SemiBold else null,
                    color = if (match.live) GreenDark else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (match.isPlanned) {
                        pluralStringResource(R.plurals.players, match.players, match.players)
                    } else {
                        pluralStringResource(R.plurals.recordings, match.recordings, match.recordings)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!match.isPlanned) {
                // The score of the goals confirmed so far; while some events wait, it is provisional and
                // muted, with what is left to review under it
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.match_score, match.homeGoals, match.awayGoals),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (match.toReview > 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                    if (match.toReview > 0) {
                        Text(
                            pluralStringResource(R.plurals.to_review_count, match.toReview, match.toReview),
                            color = ReviewText,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.background(ReviewBadge, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteMatchDialog(match: MatchListItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_phone_match_title)) },
        text = {
            Text(stringResource(R.string.delete_phone_match_text, matchTitle(match.homeTeam, match.awayTeam), matchWhen(match)))
        },
        confirmButton = {
            // Red like "Usuń" for a match still on the watch: it cannot be undone
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The match's start, or "Zaplanowany · sobota, 31 października" before kickoff. */
@Composable
private fun matchWhen(match: MatchListItem): String = when {
    match.startedAt != null -> formatMatchWhen(match.startedAt, match.fromNotes)
    match.plannedAt != null -> stringResource(R.string.planned_on, formatMatchDay(match.plannedAt))
    else -> stringResource(R.string.planned_match)
}

@Preview
@Composable
private fun MatchesScreenPreview() {
    CoachPadTheme {
        MatchesScreen(
            state = MatchesUiState(
                matches = listOf(
                    MatchListItem("p1", "Orły", "Sokoły", null, toReview = 0, recordings = 0, players = 14, plannedAt = 1_793_400_000_000),
                    MatchListItem("m1", "Orły", "Rywale FC", 1_791_800_000_000, toReview = 3, recordings = 12, homeGoals = 1),
                    MatchListItem("m2", null, null, 1_791_200_000_000, toReview = 0, recordings = 4, homeGoals = 3, awayGoals = 1),
                ),
                pendingOnWatch = 1,
            ),
            onOpenMatch = {},
            onOpenPlanned = {},
            onOpenLive = {},
            onNewMatch = {},
            onOpenSync = {},
            onOpenSettings = {},
            onDeleteMatch = {},
        )
    }
}

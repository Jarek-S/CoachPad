package pl.net.orlik.ui

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.net.orlik.R
import pl.net.orlik.data.MatchListItem
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.sync.QueueSummarySource
import pl.net.orlik.ui.theme.MatchNotesTheme
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

class MatchesViewModel(matches: Flow<List<MatchListItem>>, summaries: Flow<QueueSummary?>) : ViewModel() {

    val state: StateFlow<MatchesUiState> = combine(matches, summaries) { list, summary ->
        MatchesUiState(list, summary?.matches?.size ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchesUiState())

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MatchesViewModel(PhoneDatabase.get(app).matchEventDao().matches(), QueueSummarySource(app).summaries)
            }
        }
    }
}

/** Start screen: the matches on the phone, and the way to sync more from the watch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(state: MatchesUiState, onOpenMatch: (String) -> Unit, onOpenSync: () -> Unit, onOpenSettings: () -> Unit) {
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
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(matches.orEmpty(), key = { it.matchId }) { match ->
                MatchCard(match, onClick = { onOpenMatch(match.matchId) })
            }
        }
    }
}

@Composable
private fun MatchCard(match: MatchListItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(matchTitle(match.homeTeam, match.awayTeam), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatMatchStart(match.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(R.plurals.recordings, match.recordings, match.recordings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (match.toReview > 0) {
                Text(
                    pluralStringResource(R.plurals.to_review_count, match.toReview, match.toReview),
                    color = ReviewText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.background(ReviewBadge, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    stringResource(R.string.reviewed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MatchesScreenPreview() {
    MatchNotesTheme {
        MatchesScreen(
            state = MatchesUiState(
                matches = listOf(
                    MatchListItem("m1", "Orły", "Rywale FC", 1_791_800_000_000, toReview = 3, recordings = 12),
                    MatchListItem("m2", null, null, 1_791_200_000_000, toReview = 0, recordings = 4),
                ),
                pendingOnWatch = 1,
            ),
            onOpenMatch = {},
            onOpenSync = {},
            onOpenSettings = {},
        )
    }
}

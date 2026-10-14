package pl.net.orlik.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.net.orlik.R
import pl.net.orlik.data.Match
import pl.net.orlik.data.Team
import pl.net.orlik.review.MatchReview
import pl.net.orlik.review.ReviewHint
import pl.net.orlik.review.ReviewRow
import pl.net.orlik.review.formatDuration
import pl.net.orlik.review.formatPlayTime
import pl.net.orlik.shared.EventType
import pl.net.orlik.ui.theme.GreenDark
import pl.net.orlik.ui.theme.GreenLight
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.OnStadiumMuted
import pl.net.orlik.ui.theme.ReviewBadge
import pl.net.orlik.ui.theme.ReviewText
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

/** One match: events waiting for review, then the confirmed ones (collapsed by default). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchReviewScreen(
    state: MatchReviewUiState,
    onBack: () -> Unit,
    onTogglePlayback: (eventId: String, audioFileRef: String) -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    onTeamNames: (homeTeam: String, awayTeam: String) -> Unit,
) {
    var confirmedExpanded by rememberSaveable { mutableStateOf(false) }
    var editingNames by rememberSaveable { mutableStateOf(false) }
    if (editingNames) {
        TeamNamesDialog(
            match = state.match,
            onSave = { home, away ->
                onTeamNames(home, away)
                editingNames = false
            },
            onDismiss = { editingNames = false },
        )
    }
    val home = state.match?.homeTeam
    val away = state.match?.awayTeam
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
                        Text(matchTitle(home, away))
                        state.startedAt?.let {
                            Text(
                                formatMatchStart(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnStadiumMuted,
                            )
                        }
                    }
                },
                actions = {
                    if (state.match != null) {
                        IconButton(onClick = { editingNames = true }) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.team_names))
                        }
                    }
                },
            )
        },
    ) { padding ->
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
            item {
                SectionHeader(stringResource(R.string.to_review), state.review.toReview.size, ReviewText, ReviewBadge)
            }
            if (state.review.toReview.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.all_reviewed),
                        color = OnStadiumMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                eventCard(state.review.toReview, state, home, away, pending = true, onTogglePlayback, onOpenEvent)
            }
            item {
                TextButton(onClick = { confirmedExpanded = !confirmedExpanded }) {
                    SectionHeader(stringResource(R.string.confirmed), state.review.confirmed.size, GreenDark, GreenLight)
                    Icon(
                        painterResource(if (confirmedExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = OnStadiumMuted,
                    )
                }
            }
            if (confirmedExpanded) {
                eventCard(state.review.confirmed, state, home, away, pending = false, onTogglePlayback, onOpenEvent)
            }
        }
    }
}

/** Two fields; an empty one keeps the default "Gospodarze" / "Goście". */
@Composable
private fun TeamNamesDialog(match: Match?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var home by rememberSaveable { mutableStateOf(match?.homeTeam.orEmpty()) }
    var away by rememberSaveable { mutableStateOf(match?.awayTeam.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.team_names)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = home,
                    onValueChange = { home = it },
                    label = { Text(stringResource(R.string.team_home)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = away,
                    onValueChange = { away = it },
                    label = { Text(stringResource(R.string.team_away)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(home, away) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(home, away) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SectionHeader(title: String, count: Int, countColor: Color, badgeColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OnStadium)
        Text(
            "$count",
            color = countColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.background(badgeColor, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

/** The rows of one section on a single white card, as in the mockup. */
private fun LazyListScope.eventCard(
    rows: List<ReviewRow>,
    state: MatchReviewUiState,
    home: String?,
    away: String?,
    pending: Boolean,
    onTogglePlayback: (String, String) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    item {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    EventRow(
                        row = row,
                        durationMillis = state.durations[row.eventId],
                        playing = state.playingId == row.eventId,
                        pending = pending,
                        home = home,
                        away = away,
                        onTogglePlayback = onTogglePlayback,
                        onOpen = { onOpenEvent(row.eventId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    row: ReviewRow,
    durationMillis: Long?,
    playing: Boolean,
    pending: Boolean,
    home: String?,
    away: String?,
    onTogglePlayback: (String, String) -> Unit,
    onOpen: () -> Unit,
) {
    // Structural events have nothing to edit
    val editable = row.type?.isStructural != true
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = editable, onClick = onOpen).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val audio = row.audioFileRef
            if (audio != null) {
                PlayButton(playing, filled = pending, onClick = { onTogglePlayback(row.eventId, audio) })
                Text(
                    durationMillis?.let(::formatDuration) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.no_recording),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatPlayTime(row.playTimeMillis), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                row.type?.let { type ->
                    EventTypeMark(type)
                    val team = row.team?.let { teamName(it, home, away) }
                    Text(
                        if (team != null) "${eventTypeName(type)} · $team" else eventTypeName(type),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (row.cancelled) TextDecoration.LineThrough else null,
                    )
                }
            }
            when (row.hint) {
                ReviewHint.DESCRIBE -> Text(
                    stringResource(R.string.hint_describe),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReviewHint.MISSING_TEAM -> HintText(stringResource(R.string.hint_missing_team))
                ReviewHint.NO_GOAL -> HintText(stringResource(R.string.hint_no_goal))
                null -> Unit
            }
            row.cancelsGoalAt?.let { MutedText(stringResource(R.string.cancels_goal_at, formatPlayTime(it))) }
            if (row.cancelled) MutedText(stringResource(R.string.not_counted))
            // A description waiting for review takes the place of the note
            if (row.hint != ReviewHint.DESCRIBE) row.note?.let { MutedText("„$it”") }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ReviewText)
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The main element of a row (design doc, section 4): filled while pending, outlined once confirmed. */
@Composable
private fun PlayButton(playing: Boolean, filled: Boolean, onClick: () -> Unit) {
    val icon = painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play)
    val description = stringResource(if (playing) R.string.stop_recording else R.string.play_recording)
    if (filled) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(48.dp), shape = CircleShape) {
            Icon(icon, description)
        }
    } else {
        OutlinedIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        ) {
            Icon(icon, description, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview
@Composable
private fun MatchReviewScreenPreview() {
    fun row(id: String, minute: Int, type: EventType?, team: Team? = null, note: String? = null, hint: ReviewHint? = null) =
        ReviewRow(id, minute * 60_000L + 12_000, type, team, note, if (type?.isStructural == true) null else "$id.m4a", hint)
    MatchNotesTheme {
        MatchReviewScreen(
            state = MatchReviewUiState(
                match = Match("m1", "Orły", "Rywale FC"),
                startedAt = 1_791_800_000_000,
                review = MatchReview(
                    toReview = listOf(
                        row("a", 3, null, hint = ReviewHint.DESCRIBE),
                        row("b", 27, EventType.YELLOW_CARD, hint = ReviewHint.MISSING_TEAM),
                        row("c", 41, EventType.GOAL_DISALLOWED, Team.AWAY, note = "Spalony").copy(cancelsGoalAt = 38 * 60_000L + 50_000),
                    ),
                    confirmed = listOf(
                        row("s", 0, EventType.MATCH_START),
                        row("g", 12, EventType.GOAL, Team.HOME, "Marcin, asysta Jurek"),
                        row("h", 38, EventType.GOAL, Team.AWAY).copy(cancelled = true),
                    ),
                ),
                durations = mapOf("a" to 6_000L, "b" to 5_000L, "c" to 7_000L, "g" to 9_000L),
                playingId = "b",
            ),
            onBack = {},
            onTogglePlayback = { _, _ -> },
            onOpenEvent = {},
            onTeamNames = { _, _ -> },
        )
    }
}

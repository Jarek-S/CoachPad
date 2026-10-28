package pl.net.orlik.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.net.orlik.R
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchListItem
import pl.net.orlik.data.Team
import pl.net.orlik.export.ExportFormat
import pl.net.orlik.review.MatchReview
import pl.net.orlik.review.ReviewHint
import pl.net.orlik.review.ReviewRow
import pl.net.orlik.review.formatDuration
import pl.net.orlik.review.formatPlayTime
import pl.net.orlik.shared.EventType
import pl.net.orlik.stt.SttError
import pl.net.orlik.stt.TranscriptionStatus
import pl.net.orlik.ui.theme.GlassPanel
import pl.net.orlik.ui.theme.GreenDark
import pl.net.orlik.ui.theme.GreenLight
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.OnStadiumHint
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
    onOpenLineup: () -> Unit,
    onTranscribe: () -> Unit,
    onTakeOverPlanned: (plannedId: String) -> Unit,
    onExport: (ExportFormat) -> Unit,
) {
    var choosingPlanned by rememberSaveable { mutableStateOf(false) }
    if (choosingPlanned && state.planned.isNotEmpty()) {
        PlannedMatchDialog(
            planned = state.planned,
            onConfirm = {
                choosingPlanned = false
                onTakeOverPlanned(it)
            },
            onDismiss = { choosingPlanned = false },
        )
    }
    var confirmedExpanded by rememberSaveable { mutableStateOf(false) }
    // A format chosen while events still wait for review: asked about first (design doc, section 5)
    var unreviewedExport by rememberSaveable { mutableStateOf<ExportFormat?>(null) }
    unreviewedExport?.let { format ->
        UnreviewedExportDialog(
            count = state.review.toReview.size,
            onConfirm = {
                unreviewedExport = null
                onExport(format)
            },
            onDismiss = { unreviewedExport = null },
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
                        ExportMenu(
                            onExport = { format ->
                                if (state.review.toReview.isEmpty()) onExport(format) else unreviewedExport = format
                            },
                        )
                        IconButton(onClick = onOpenLineup) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.lineup_title))
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
            if (state.planned.isNotEmpty()) {
                item { PlannedMatchBar(onChoose = { choosingPlanned = true }) }
            }
            if (state.stt.available || state.stt.status != TranscriptionStatus.Idle) {
                item { TranscriptionBar(state.stt, onTranscribe) }
            }
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

/** "Udostępnij": the text as a message, or CSV / JSON as a file. */
@Composable
private fun ExportMenu(onExport: (ExportFormat) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                ExportFormat.TEXT to R.string.export_text,
                ExportFormat.CSV to R.string.export_csv,
                ExportFormat.JSON to R.string.export_json,
            ).forEach { (format, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onExport(format)
                    },
                )
            }
        }
    }
}

/** A synced match without a lineup, while planned matches wait on the list (design doc, section 11). */
@Composable
private fun PlannedMatchBar(onChoose: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.planned_offer), color = OnStadium, modifier = Modifier.weight(1f))
            Button(onClick = onChoose) { Text(stringResource(R.string.take_over_planned)) }
        }
    }
}

/** Which planned match this one is; the first is chosen to start with. */
@Composable
private fun PlannedMatchDialog(planned: List<MatchListItem>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(planned.first().matchId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.take_over_planned_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.take_over_planned_text))
                Column(Modifier.selectableGroup()) {
                    planned.forEach { match ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = match.matchId == chosen, role = Role.RadioButton) { chosen = match.matchId }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = match.matchId == chosen, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(matchTitle(match.homeTeam, match.awayTeam), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    pluralStringResource(R.plurals.players, match.players, match.players),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text(stringResource(R.string.take_over_planned)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun UnreviewedExportDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.export_unreviewed_title, count, count)) },
        text = { Text(stringResource(R.string.export_unreviewed_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.export_anyway)) } },
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
            playersText(row.player, row.assist)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            // A description waiting for review takes the place of the note
            if (row.hint != ReviewHint.DESCRIBE) row.note?.let { MutedText("„$it”") }
            row.transcript?.let { MutedText(stringResource(R.string.heard, it)) }
        }
    }
}

/** "Rozpoznaj nagrania (12)", the progress while it runs, or why it stopped. */
@Composable
private fun TranscriptionBar(stt: SttUiState, onTranscribe: () -> Unit) {
    val status = stt.status
    if (status == TranscriptionStatus.Idle && stt.untranscribed == 0) return
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (status) {
                is TranscriptionStatus.Running -> {
                    CircularProgressIndicator(Modifier.size(20.dp), color = OnStadium, strokeWidth = 2.dp)
                    Text(stringResource(R.string.transcribing, status.done + 1, status.total), color = OnStadium)
                }
                is TranscriptionStatus.Failed -> {
                    Text(
                        stringResource(
                            when (status.error) {
                                SttError.UNAUTHORIZED -> R.string.stt_failed_unauthorized
                                SttError.RATE_LIMITED -> R.string.stt_failed_rate_limited
                                SttError.NETWORK -> R.string.stt_failed_network
                                SttError.SERVICE -> R.string.stt_failed_service
                            },
                        ),
                        color = OnStadiumHint,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (stt.available && stt.untranscribed > 0) {
                        TextButton(onClick = onTranscribe) { Text(stringResource(R.string.retry), color = OnStadium) }
                    }
                }
                TranscriptionStatus.Idle -> {
                    Text(
                        pluralStringResource(R.plurals.untranscribed, stt.untranscribed, stt.untranscribed),
                        color = OnStadium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onTranscribe) { Text(stringResource(R.string.transcribe)) }
                }
            }
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
            onOpenLineup = {},
            onTranscribe = {},
            onTakeOverPlanned = {},
            onExport = {},
        )
    }
}

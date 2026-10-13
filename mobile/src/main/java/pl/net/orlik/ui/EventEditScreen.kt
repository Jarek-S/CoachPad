package pl.net.orlik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import pl.net.orlik.R
import pl.net.orlik.data.Match
import pl.net.orlik.data.Team
import pl.net.orlik.review.EventDraft
import pl.net.orlik.review.ReviewHint
import pl.net.orlik.review.ReviewPosition
import pl.net.orlik.review.ReviewRow
import pl.net.orlik.review.editableTypes
import pl.net.orlik.review.formatDuration
import pl.net.orlik.review.formatPlayTime
import pl.net.orlik.review.needsTeam
import pl.net.orlik.shared.EventType
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.ReviewText

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Edit screen, variant A (design doc, section 11): one event, then on to the next. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    state: EventEditUiState,
    onExit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePlayback: () -> Unit,
    positionMillis: () -> Long,
    onType: (EventType) -> Unit,
    onTeam: (Team) -> Unit,
    onNote: (String) -> Unit,
    onSkip: () -> Unit,
    onConfirm: () -> Unit,
) {
    // System back saves like the arrow, so nothing typed is lost
    BackHandler(onBack = onExit)
    val draft = state.draft
    val row = state.row
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                title = {
                    Text(
                        state.position?.let { stringResource(R.string.edit_position, it.index, it.count) }
                            ?: stringResource(R.string.confirmed),
                    )
                },
                actions = {
                    if (row?.audioFileRef != null) {
                        IconButton(onClick = onDelete) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_recording))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (draft != null) {
                EditButtons(
                    draft = draft,
                    pending = state.position != null,
                    onSkip = onSkip,
                    onConfirm = onConfirm,
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                )
            }
        },
    ) { padding ->
        if (row == null || draft == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                Text(formatPlayTime(row.playTimeMillis), fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                state.recordedAt?.let {
                    Text(
                        stringResource(R.string.play_minute_recorded, clockFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (row.audioFileRef != null) {
                Player(state.playing, state.durationMillis, positionMillis, onTogglePlayback)
            }
            Field(stringResource(R.string.type_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editableTypes.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { onType(type) },
                            label = { Text(eventTypeName(type)) },
                            leadingIcon = { EventTypeMark(type) },
                        )
                    }
                }
            }
            if (draft.type?.needsTeam != false) {
                Field(stringResource(R.string.team_label)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        Team.entries.forEachIndexed { index, team ->
                            SegmentedButton(
                                selected = draft.team == team,
                                onClick = { onTeam(team) },
                                shape = SegmentedButtonDefaults.itemShape(index, Team.entries.size),
                            ) {
                                Text(teamName(team, state.match?.homeTeam, state.match?.awayTeam))
                            }
                        }
                    }
                }
            }
            Field(stringResource(R.string.type_note)) {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = onNote,
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Big ▶ with the progress of the recording, as in the mockup. */
@Composable
private fun Player(playing: Boolean, durationMillis: Long?, positionMillis: () -> Long, onToggle: () -> Unit) {
    var position by remember { mutableLongStateOf(0) }
    LaunchedEffect(playing) {
        position = 0
        while (playing) {
            position = positionMillis()
            delay(100)
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp), shape = CircleShape) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
                    stringResource(if (playing) R.string.stop_recording else R.string.play_recording),
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { if (durationMillis != null && durationMillis > 0) position.toFloat() / durationMillis else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                Row {
                    Text(
                        formatDuration(position),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        durationMillis?.let(::formatDuration) ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditButtons(
    draft: EventDraft,
    pending: Boolean,
    onSkip: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Why "Zatwierdź" is not available yet
            val missing = when {
                draft.type == null -> R.string.choose_type
                !draft.canConfirm -> R.string.choose_team
                else -> null
            }
            missing?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = ReviewText) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pending) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.skip))
                    }
                }
                Button(onClick = onConfirm, enabled = draft.canConfirm, modifier = Modifier.weight(if (pending) 1.6f else 1f)) {
                    Text(stringResource(if (pending) R.string.confirm_next else R.string.save))
                }
            }
        }
    }
}

@Preview
@Composable
private fun EventEditScreenPreview() {
    MatchNotesTheme {
        EventEditScreen(
            state = EventEditUiState(
                match = Match("m1", "Orły", "Rywale FC"),
                row = ReviewRow("c", 41 * 60_000L + 2_000, null, null, null, "c.m4a", ReviewHint.DESCRIBE),
                recordedAt = 1_791_800_000_000,
                position = ReviewPosition(3, 3),
                draft = EventDraft(type = EventType.GOAL_DISALLOWED, note = "Spalony"),
                durationMillis = 7_000,
            ),
            onExit = {},
            onDelete = {},
            onTogglePlayback = {},
            positionMillis = { 2_000 },
            onType = {},
            onTeam = {},
            onNote = {},
            onSkip = {},
            onConfirm = {},
        )
    }
}

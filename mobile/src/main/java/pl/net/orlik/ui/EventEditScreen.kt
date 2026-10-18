package pl.net.orlik.ui

import androidx.activity.compose.BackHandler
import pl.net.orlik.review.hasPlayer
import pl.net.orlik.review.hasAssist
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import pl.net.orlik.ui.theme.InkMuted
import pl.net.orlik.ui.theme.GreenDark
import pl.net.orlik.review.playerSuggestions
import pl.net.orlik.data.LineupPlayer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
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
import pl.net.orlik.ui.theme.GlassBorder
import pl.net.orlik.ui.theme.GlassPanel
import pl.net.orlik.ui.theme.MatchNotesTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.OnStadiumHint
import pl.net.orlik.ui.theme.OnStadiumMuted
import pl.net.orlik.ui.theme.ReviewText
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

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
    onPlayer: (String) -> Unit,
    onAssist: (String) -> Unit,
    onLinkedGoal: (String?) -> Unit,
    onSkip: () -> Unit,
    onConfirm: () -> Unit,
) {
    // System back saves like the arrow, so nothing typed is lost
    BackHandler(onBack = onExit)
    val draft = state.draft
    val row = state.row
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = OnStadium,
        topBar = {
            TopAppBar(
                colors = stadiumTopAppBarColors(),
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
                Text(formatPlayTime(row.playTimeMillis), fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = OnStadium)
                state.recordedAt?.let {
                    Text(
                        stringResource(R.string.play_minute_recorded, clockFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnStadiumMuted,
                    )
                }
            }
            // The fields on a white card, the minute above it on the stadium
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (row.audioFileRef != null) {
                    Player(state.playing, state.durationMillis, positionMillis, onTogglePlayback)
                }
                row.transcript?.let {
                    Text(
                        stringResource(R.string.heard, it),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                if (draft.type?.hasPlayer == true) {
                    PlayerFields(draft, draft.team?.let { state.lineup[it] }.orEmpty(), onPlayer, onAssist)
                }
                if (draft.type == EventType.GOAL_DISALLOWED) {
                    GoalLink(draft, state.goalCandidates, state.match, onLinkedGoal)
                }
                Field(stringResource(R.string.type_note)) {
                    OutlinedTextField(
                        value = draft.note,
                        onValueChange = onNote,
                        placeholder = {
                            Text(
                                stringResource(
                                    if (draft.type?.hasPlayer == true) R.string.note_placeholder_extra else R.string.note_placeholder,
                                ),
                            )
                        },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                }
            }
        }
    }
}

/** "Unieważnia gol …  Zmień": the goal a disallowed goal cancels (design doc, section 8). */
@Composable
private fun GoalLink(draft: EventDraft, candidates: List<ReviewRow>, match: Match?, onLinkedGoal: (String?) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val linked = candidates.firstOrNull { it.eventId == draft.linkedGoalId }
    Field(stringResource(R.string.cancels_goal)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (text, color) = when {
                    draft.team == null -> stringResource(R.string.link_choose_team) to MaterialTheme.colorScheme.onSurfaceVariant
                    linked != null -> goalLabel(linked, match) to MaterialTheme.colorScheme.onSurface
                    candidates.isEmpty() -> stringResource(R.string.link_no_goal) to ReviewText
                    else -> stringResource(R.string.link_none) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (candidates.isNotEmpty()) {
                    TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.change)) }
                }
            }
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.cancels_goal)) },
            text = {
                Column {
                    candidates.reversed().forEach { goal ->
                        ChoiceRow(goalLabel(goal, match), draft.linkedGoalId == goal.eventId) {
                            onLinkedGoal(goal.eventId)
                            choosing = false
                        }
                    }
                    ChoiceRow(stringResource(R.string.link_none), draft.linkedGoalId == null) {
                        onLinkedGoal(null)
                        choosing = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, onClick = onClick).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

/** "38:50 · Gol · Rywale · Kuba", with the scorer (or else the note) when the goal has one. */
@Composable
private fun goalLabel(goal: ReviewRow, match: Match?): String {
    val parts = listOfNotNull(
        formatPlayTime(goal.playTimeMillis),
        eventTypeName(EventType.GOAL),
        goal.team?.let { teamName(it, match?.homeTeam, match?.awayTeam) },
        playersText(goal.player, goal.assist) ?: goal.note,
    )
    return parts.joinToString(" · ")
}

private enum class PlayerField { PLAYER, ASSIST }

/**
 * Scorer and assist (goals) or the player (cards). Typing offers the chosen team's lineup below the
 * fields (design doc, section 11); without a lineup, or with "spoza składu", the name stays as typed.
 */
@Composable
private fun PlayerFields(draft: EventDraft, lineup: List<LineupPlayer>, onPlayer: (String) -> Unit, onAssist: (String) -> Unit) {
    var focused by remember { mutableStateOf<PlayerField?>(null) }
    // "Wpisz … spoza składu" hides the list until the field is typed in or focused again
    var dismissed by remember { mutableStateOf(false) }
    fun focusOf(field: PlayerField): (Boolean) -> Unit = { hasFocus ->
        if (hasFocus) focused = field else if (focused == field) focused = null
        dismissed = false
    }
    val player: (String) -> Unit = { dismissed = false; onPlayer(it) }
    val assist: (String) -> Unit = { dismissed = false; onAssist(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.type?.hasAssist == true) {
            // Two short names side by side, as in the mockup
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(stringResource(R.string.scorer_label), draft.player, player, focusOf(PlayerField.PLAYER), Modifier.weight(1f))
                NameField(stringResource(R.string.assist_label), draft.assist, assist, focusOf(PlayerField.ASSIST), Modifier.weight(1f))
            }
        } else {
            NameField(stringResource(R.string.player_label), draft.player, player, focusOf(PlayerField.PLAYER), Modifier.fillMaxWidth())
        }
        val typed = when (focused) {
            PlayerField.PLAYER -> draft.player
            PlayerField.ASSIST -> draft.assist
            null -> null
        }
        if (typed != null && !dismissed && lineup.isNotEmpty()) {
            val choose = if (focused == PlayerField.ASSIST) onAssist else onPlayer
            PlayerSuggestions(
                suggestions = playerSuggestions(lineup, typed),
                typed = typed,
                chosen = lineup.any { it.name.equals(typed.trim(), ignoreCase = true) },
                onChoose = choose,
                onKeepTyped = { dismissed = true },
            )
        }
    }
}

/** "7 Marcin" rows with the typed part in bold, then "Wpisz „…” spoza składu". */
@Composable
private fun PlayerSuggestions(
    suggestions: List<LineupPlayer>,
    typed: String,
    chosen: Boolean,
    onChoose: (String) -> Unit,
    onKeepTyped: () -> Unit,
) {
    val text = typed.trim()
    val keepTyped = text.isNotEmpty() && !chosen
    if (suggestions.isEmpty() && !keepTyped) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            suggestions.take(MAX_SUGGESTIONS).forEach { player ->
                SuggestionRow(onClick = { onChoose(player.name) }) {
                    Text(
                        player.number?.toString() ?: "—",
                        fontWeight = FontWeight.SemiBold,
                        color = if (player.number != null) GreenDark else InkMuted,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(highlighted(player.name, if (text.all { it.isDigit() }) "" else text))
                }
            }
            if (keepTyped) {
                SuggestionRow(onClick = onKeepTyped) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = InkMuted, modifier = Modifier.width(32.dp).size(20.dp))
                    Text(stringResource(R.string.outside_lineup, text), color = InkMuted)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
    ) { content() }
}

/** The name with the first occurrence of [part] in bold, ignoring case. */
private fun highlighted(name: String, part: String): AnnotatedString = buildAnnotatedString {
    val start = if (part.isEmpty()) -1 else name.indexOf(part, ignoreCase = true)
    if (start < 0) {
        append(name)
    } else {
        append(name.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.substring(start, start + part.length)) }
        append(name.substring(start + part.length))
    }
}

private const val MAX_SUGGESTIONS = 6

/** A name typed by hand, or chosen from the lineup below it. */
@Composable
private fun NameField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Field(label) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
            )
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
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
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
    // Glass over the stadium instead of a white bar (design doc, section 11)
    GlassPanel(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Why "Zatwierdź" is not available yet
            val missing = when {
                draft.type == null -> R.string.choose_type
                !draft.canConfirm -> R.string.choose_team
                else -> null
            }
            missing?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = OnStadiumHint) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pending) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnStadium),
                        border = BorderStroke(1.dp, GlassBorder),
                    ) {
                        Text(stringResource(R.string.skip))
                    }
                }
                Button(
                    onClick = onConfirm,
                    enabled = draft.canConfirm,
                    modifier = Modifier.weight(if (pending) 1.6f else 1f),
                    // The default disabled colours are dark-on-light and vanish on the stadium
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = OnStadium.copy(alpha = 0.12f),
                        disabledContentColor = OnStadium.copy(alpha = 0.5f),
                    ),
                ) {
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
                draft = EventDraft(type = EventType.GOAL_DISALLOWED, team = Team.AWAY, note = "Spalony", linkedGoalId = "g", player = "Kowal"),
                durationMillis = 7_000,
                goalCandidates = listOf(ReviewRow("g", 38 * 60_000L + 50_000, EventType.GOAL, Team.AWAY, null, "g.m4a", null)),
            ),
            onExit = {},
            onDelete = {},
            onTogglePlayback = {},
            positionMillis = { 2_000 },
            onType = {},
            onTeam = {},
            onNote = {},
            onPlayer = {},
            onAssist = {},
            onLinkedGoal = {},
            onSkip = {},
            onConfirm = {},
        )
    }
}

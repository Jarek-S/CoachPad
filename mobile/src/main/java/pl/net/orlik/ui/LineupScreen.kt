package pl.net.orlik.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.net.orlik.R
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.Team
import pl.net.orlik.review.PlayerEntry
import pl.net.orlik.review.parsePlayerEntry
import pl.net.orlik.review.playerEntryText
import pl.net.orlik.review.isPlannedLater
import java.time.ZoneId
import pl.net.orlik.ui.theme.GlassBorder
import pl.net.orlik.ui.theme.GlassPanel
import pl.net.orlik.ui.theme.OnStadiumHint
import pl.net.orlik.ui.theme.Green
import pl.net.orlik.ui.theme.GreenDark
import pl.net.orlik.ui.theme.Ink
import pl.net.orlik.ui.theme.InkMuted
import pl.net.orlik.ui.theme.CoachPadTheme
import pl.net.orlik.ui.theme.OnStadium
import pl.net.orlik.ui.theme.OnStadiumMuted
import pl.net.orlik.ui.theme.Outline
import pl.net.orlik.ui.theme.stadiumTopAppBarColors

/** The match lineup, variant B (design doc, section 11): one tab per team, players added by "7 Marcin". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineupScreen(
    state: LineupUiState,
    onBack: () -> Unit,
    onTeamName: (Team, String) -> Unit,
    onAddPlayer: (Team, PlayerEntry) -> Unit,
    onUpdatePlayer: (id: Long, PlayerEntry) -> Unit,
    onRemovePlayer: (id: Long) -> Unit,
    onPlannedDay: (utcDayMillis: Long) -> Unit,
    onTakeOverPlanned: (plannedId: String) -> Unit,
    onEnterFromNotes: () -> Unit,
    onStartMatch: () -> Unit,
) {
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    if (choosingDate) {
        MatchDayDialog(
            initialMillis = state.match?.plannedAt,
            confirmLabel = stringResource(R.string.save),
            onConfirm = {
                choosingDate = false
                onPlannedDay(it)
            },
            onDismiss = { choosingDate = false },
        )
    }
    // Only before kickoff: a planned match is recorded one way or another (design doc, section 3.3)
    val plannedAt = state.match?.takeIf { it.playedAt == null && state.startedAt == null }?.plannedAt
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
    var team by rememberSaveable { mutableStateOf(Team.HOME) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    state.players.values.flatten().firstOrNull { it.id == editingId }?.let { player ->
        PlayerDialog(
            player = player,
            onSave = { entry ->
                onUpdatePlayer(player.id, entry)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }
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
                        Text(stringResource(R.string.lineup_title))
                        if (plannedAt != null) {
                            // The planned day, tapped to change it
                            Row(
                                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.change_match_day)) { choosingDate = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stringResource(R.string.planned_on, formatMatchDay(plannedAt)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnStadiumMuted,
                                )
                                Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(16.dp), tint = OnStadiumMuted)
                            }
                        } else {
                            state.startedAt?.let {
                                Text(formatMatchWhen(it, state.match?.playedAt != null), style = MaterialTheme.typography.bodyMedium, color = OnStadiumMuted)
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (plannedAt != null) {
                // A match is typed in from notes once its day has come
                val plannedLater = isPlannedLater(plannedAt, System.currentTimeMillis(), ZoneId.systemDefault())
                GlassPanel(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (plannedLater) {
                            Text(stringResource(R.string.planned_later), style = MaterialTheme.typography.bodyMedium, color = OnStadiumHint)
                        }
                        // One live match on the phone at a time
                        if (state.otherMatchLive) {
                            Text(stringResource(R.string.other_match_live), style = MaterialTheme.typography.bodyMedium, color = OnStadiumHint)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = onEnterFromNotes,
                                enabled = !plannedLater,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = OnStadium,
                                    disabledContentColor = OnStadium.copy(alpha = 0.5f),
                                ),
                                border = BorderStroke(1.dp, GlassBorder),
                            ) {
                                Text(stringResource(R.string.enter_from_notes))
                            }
                            Button(
                                onClick = onStartMatch,
                                enabled = !state.otherMatchLive,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = OnStadium.copy(alpha = 0.12f),
                                    disabledContentColor = OnStadium.copy(alpha = 0.5f),
                                ),
                            ) {
                                Text(stringResource(R.string.start_match))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val match = state.match ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A plan of another day than the one the watch started on (design doc, section 11)
            if (state.planned.isNotEmpty()) {
                OutlinedButton(
                    onClick = { choosingPlanned = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnStadium),
                    border = BorderStroke(1.dp, GlassBorder),
                ) {
                    Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.take_from_planned))
                }
            }
            TeamTabs(team, match, state.players, onSelect = { team = it })
            // Keyed by team, so the fields start from that team's saved values
            key(team) {
                TeamCard(
                    team = team,
                    savedName = if (team == Team.HOME) match.homeTeam else match.awayTeam,
                    players = state.players[team].orEmpty(),
                    onTeamName = { onTeamName(team, it) },
                    onAddPlayer = { onAddPlayer(team, it) },
                    onEditPlayer = { editingId = it },
                    onRemovePlayer = onRemovePlayer,
                )
            }
        }
    }
}

/** "Orły · 9 | Rywale FC · 0" on glass, the chosen team in green. */
@Composable
private fun TeamTabs(selected: Team, match: Match, players: Map<Team, List<LineupPlayer>>, onSelect: (Team) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Team.entries.forEach { team ->
                val isSelected = team == selected
                Surface(
                    selected = isSelected,
                    onClick = { onSelect(team) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Green else Color.Transparent,
                    contentColor = if (isSelected) Ink else OnStadium,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                R.string.lineup_tab,
                                teamName(team, match.homeTeam, match.awayTeam),
                                players[team].orEmpty().size,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamCard(
    team: Team,
    savedName: String?,
    players: List<LineupPlayer>,
    onTeamName: (String) -> Unit,
    onAddPlayer: (PlayerEntry) -> Unit,
    onEditPlayer: (Long) -> Unit,
    onRemovePlayer: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(savedName.orEmpty()) }
    var entry by rememberSaveable { mutableStateOf("") }
    val add: () -> Unit = {
        parsePlayerEntry(entry)?.let {
            onAddPlayer(it)
            entry = ""
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(stringResource(if (team == Team.HOME) R.string.team_name_home else R.string.team_name_away))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onTeamName(it)
                },
                placeholder = { Text(stringResource(if (team == Team.HOME) R.string.team_home else R.string.team_away)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Label(stringResource(R.string.add_player))
            // Enter adds the player and keeps the keyboard open for the next one
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                placeholder = { Text(stringResource(R.string.add_player_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = add, enabled = parsePlayerEntry(entry) != null) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_player))
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Green),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            if (players.isEmpty()) {
                Text(stringResource(R.string.lineup_empty), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            } else {
                players.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { player ->
                            PlayerChip(player, { onEditPlayer(player.id) }, { onRemovePlayer(player.id) }, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text(stringResource(R.string.lineup_tap_hint), style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Number, name and ✕; a tap corrects the player. */
@Composable
private fun PlayerChip(player: LineupPlayer, onEdit: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(44.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                player.number?.toString() ?: "—",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (player.number != null) GreenDark else InkMuted,
            )
            Text(
                player.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    stringResource(R.string.remove_player, player.name),
                    tint = InkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The same "7 Marcin" field as when adding. */
@Composable
private fun PlayerDialog(player: LineupPlayer, onSave: (PlayerEntry) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(playerEntryText(player.number, player.name)) }
    val entry = parsePlayerEntry(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.add_player_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { entry?.let(onSave) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { entry?.let(onSave) }, enabled = entry != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF061430, widthDp = 390, heightDp = 844)
@Composable
private fun LineupScreenPreview() {
    fun player(id: Long, number: Int?, name: String) = LineupPlayer(id, "m", Team.HOME, number, name)
    CoachPadTheme {
        LineupScreen(
            state = LineupUiState(
                match = Match("m", homeTeam = "Orły", awayTeam = "Rywale FC"),
                startedAt = 1_791_806_400_000,
                players = mapOf(
                    Team.HOME to listOf(
                        player(1, 1, "Kuba"),
                        player(2, 7, "Marcin"),
                        player(3, 9, "Jurek"),
                        player(4, 10, "Kuba S."),
                        player(5, null, "Piotrek"),
                    ),
                ),
            ),
            onBack = {},
            onTeamName = { _, _ -> },
            onAddPlayer = { _, _ -> },
            onUpdatePlayer = { _, _ -> },
            onRemovePlayer = {},
            onPlannedDay = {},
            onTakeOverPlanned = {},
            onEnterFromNotes = {},
            onStartMatch = {},
        )
    }
}

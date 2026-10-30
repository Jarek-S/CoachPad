package pl.net.orlik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import pl.net.orlik.R
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.Team
import pl.net.orlik.review.EventDraft
import pl.net.orlik.review.hasAssist
import pl.net.orlik.review.hasPlayer
import pl.net.orlik.shared.EventType

/**
 * The details of an event from a quick button, all optional: the scorer and assist of a goal, the
 * colour, team and player of a card, the text of a note. Each change is saved at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEventSheet(
    draft: EventDraft,
    time: String,
    match: Match?,
    lineup: Map<Team, List<LineupPlayer>>,
    onType: (EventType) -> Unit,
    onTeam: (Team) -> Unit,
    onPlayer: (String) -> Unit,
    onAssist: (String) -> Unit,
    onNote: (String) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val type = draft.type ?: return
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val title = listOfNotNull(
                eventTypeName(type),
                draft.team?.let { teamName(it, match?.homeTeam, match?.awayTeam) },
                time,
            ).joinToString(" · ")
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (type == EventType.YELLOW_CARD || type == EventType.RED_CARD) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EventType.YELLOW_CARD, EventType.RED_CARD).forEach { card ->
                        FilterChip(
                            selected = type == card,
                            onClick = { onType(card) },
                            label = { Text(eventTypeName(card)) },
                            leadingIcon = { EventTypeMark(card) },
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Team.entries.forEachIndexed { index, team ->
                        SegmentedButton(
                            selected = draft.team == team,
                            onClick = { onTeam(team) },
                            shape = SegmentedButtonDefaults.itemShape(index, Team.entries.size),
                        ) {
                            Text(teamName(team, match?.homeTeam, match?.awayTeam))
                        }
                    }
                }
            }
            if (type.hasPlayer) {
                val players = draft.team?.let { lineup[it] }.orEmpty()
                val playerLabel = stringResource(if (type.hasAssist) R.string.scorer_label else R.string.player_label)
                PlayerChoice(playerLabel, draft.player, players, onPlayer)
                if (type.hasAssist && (players.isEmpty() || draft.player.isNotBlank())) {
                    PlayerChoice(stringResource(R.string.assist_label), draft.assist, players.filter { it.name != draft.player }, onAssist)
                }
            }
            if (type == EventType.NOTE) {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = onNote,
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A button tapped by mistake
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

/** The team's lineup as chips ("7 Marcin"); tapping the chosen one again clears it. Without a lineup, a name to type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerChoice(label: String, chosen: String, players: List<LineupPlayer>, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (players.isEmpty()) {
            OutlinedTextField(
                value = chosen,
                onValueChange = onChoose,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                players.forEach { player ->
                    val selected = player.name == chosen
                    FilterChip(
                        selected = selected,
                        onClick = { onChoose(if (selected) "" else player.name) },
                        label = { Text(listOfNotNull(player.number?.toString(), player.name).joinToString(" ")) },
                    )
                }
            }
        }
    }
}

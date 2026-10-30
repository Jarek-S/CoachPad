package pl.net.orlik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pl.net.orlik.R
import pl.net.orlik.data.MatchListItem

/**
 * Which planned match a match from the watch is, by name, day and players; the first is chosen
 * to start with. From the review (plans of its day) or its lineups (all plans).
 */
@Composable
fun PlannedMatchDialog(planned: List<MatchListItem>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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
                                val players = pluralStringResource(R.plurals.players, match.players, match.players)
                                Text(
                                    match.plannedAt?.let { stringResource(R.string.planned_choice, formatMatchDay(it), players) } ?: players,
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

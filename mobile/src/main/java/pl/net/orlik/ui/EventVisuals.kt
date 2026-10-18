package pl.net.orlik.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pl.net.orlik.R
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType
import pl.net.orlik.ui.theme.Disallowed
import pl.net.orlik.ui.theme.RedCard
import pl.net.orlik.ui.theme.YellowCard

/** E.g. "sobota, 11 października, 14:00" or "Saturday, 11 October, 14:00", in the app's language. */
@Composable
fun formatMatchStart(epochMillis: Long): String =
    formatMatchStart(epochMillis, stringResource(R.string.match_start_format), LocalConfiguration.current.locales[0])

fun formatMatchStart(epochMillis: Long, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** The team's name set for the match, or "Gospodarze" / "Goście" until one is set. */
@Composable
fun teamName(team: Team, homeTeam: String?, awayTeam: String?): String = when (team) {
    Team.HOME -> homeTeam ?: stringResource(R.string.team_home)
    Team.AWAY -> awayTeam ?: stringResource(R.string.team_away)
}

/** "Orły – Rywale FC", or "Mecz" until a team name is set; a missing one shows as "Gospodarze" / "Goście". */
@Composable
fun matchTitle(homeTeam: String?, awayTeam: String?): String =
    if (homeTeam == null && awayTeam == null) {
        stringResource(R.string.match)
    } else {
        "${teamName(Team.HOME, homeTeam, awayTeam)} – ${teamName(Team.AWAY, homeTeam, awayTeam)}"
    }

/** "Marcin (asysta: Jurek)", or null when neither is set. */
@Composable
fun playersText(player: String?, assist: String?): String? =
    pl.net.orlik.review.playersText(
        player, assist, stringResource(R.string.player_with_assist), stringResource(R.string.assist_only),
    )

@Composable
fun eventTypeName(type: EventType): String = stringResource(eventTypeNameRes(type))

@StringRes
fun eventTypeNameRes(type: EventType): Int =
    when (type) {
        EventType.MATCH_START -> R.string.type_match_start
        EventType.MATCH_END -> R.string.type_match_end
        EventType.MATCH_PAUSE -> R.string.type_match_pause
        EventType.MATCH_RESUME -> R.string.type_match_resume
        EventType.GOAL -> R.string.type_goal
        EventType.GOAL_DISALLOWED -> R.string.type_goal_disallowed
        EventType.YELLOW_CARD -> R.string.type_yellow_card
        EventType.RED_CARD -> R.string.type_red_card
        EventType.NOTE -> R.string.type_note
    }

/** Small mark in front of the type name; structural events have none. */
@Composable
fun EventTypeMark(type: EventType, modifier: Modifier = Modifier) {
    when (type) {
        EventType.GOAL -> Icon(painterResource(R.drawable.ic_goal), null, modifier.size(16.dp), LocalContentColor.current)
        EventType.GOAL_DISALLOWED -> Icon(painterResource(R.drawable.ic_disallowed), null, modifier.size(16.dp), Disallowed)
        EventType.NOTE -> Icon(painterResource(R.drawable.ic_note), null, modifier.size(16.dp), LocalContentColor.current)
        EventType.YELLOW_CARD -> CardMark(YellowCard, modifier)
        EventType.RED_CARD -> CardMark(RedCard, modifier)
        else -> Unit
    }
}

@Composable
private fun CardMark(color: Color, modifier: Modifier) {
    Box(modifier.size(width = 10.dp, height = 14.dp).background(color, RoundedCornerShape(2.dp)))
}

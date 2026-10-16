package pl.net.orlik.export

import android.content.res.Resources
import pl.net.orlik.R
import pl.net.orlik.shared.EventType
import pl.net.orlik.ui.eventTypeNameRes

/** Labels of the text to share, in the app's language. */
fun shareTextLabelsOf(resources: Resources) = ShareTextLabels(
    homeTeam = resources.getString(R.string.team_home),
    awayTeam = resources.getString(R.string.team_away),
    datePattern = resources.getString(R.string.share_date_format),
    locale = resources.configuration.locales[0],
    notCounted = resources.getString(R.string.share_not_counted),
    disallowedGoal = resources.getString(R.string.type_goal_disallowed),
)

/** Labels of the CSV file, in the app's language. */
fun csvLabelsOf(resources: Resources) = CsvLabels(
    homeTeam = resources.getString(R.string.team_home),
    awayTeam = resources.getString(R.string.team_away),
    columns = resources.getStringArray(R.array.csv_columns).toList(),
    typeNames = EventType.entries.associateWith { resources.getString(eventTypeNameRes(it)) },
    yes = resources.getString(R.string.csv_yes),
    no = resources.getString(R.string.csv_no),
)

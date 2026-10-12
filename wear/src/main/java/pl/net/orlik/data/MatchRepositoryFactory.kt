package pl.net.orlik.data

import android.content.Context
import java.io.File
import pl.net.orlik.settings.PreferencesSettingsStore
import pl.net.orlik.sync.DataLayerQueueSummaryPublisher

/** The repository backed by the app's database and preferences; shared by the UI and the shift alarm service. */
fun matchRepository(context: Context): MatchRepository {
    val app = context.applicationContext
    return MatchRepository(
        dao = AppDatabase.get(app).matchEventDao(),
        settings = PreferencesSettingsStore(app),
        timerStore = PreferencesMatchTimerStore(app),
        queuePublisher = DataLayerQueueSummaryPublisher(app),
    )
}

/** Where voice recordings are stored; [MatchEvent.audioFileRef] is relative to it. */
fun recordingsDir(context: Context): File = File(context.applicationContext.filesDir, "recordings")

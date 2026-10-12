package pl.net.orlik.data

import pl.net.orlik.settings.SettingsStore
import pl.net.orlik.settings.ShiftTimerSettings
import pl.net.orlik.shared.EventType
import pl.net.orlik.timer.ShiftTimer

internal class FakeSettings : SettingsStore {
    var current = ShiftTimerSettings()
    override fun shiftTimerSettings() = current
    override fun saveShiftTimerSettings(settings: ShiftTimerSettings) {
        current = settings
    }
}

internal class FakeTimerStore : MatchTimerStore {
    private var saved: Pair<String, ShiftTimer?>? = null
    override fun load(matchId: String) = saved?.takeIf { it.first == matchId }?.second
    override fun save(matchId: String, timer: ShiftTimer?) {
        saved = matchId to timer
    }
    override fun clear() {
        saved = null
    }
}

/** In-memory DAO: enough to test repository logic without Room or a device. */
internal class FakeDao : MatchEventDao {
    val events = mutableListOf<MatchEvent>()

    override suspend fun insert(event: MatchEvent) {
        events += event
    }

    override suspend fun getByMatch(matchId: String) =
        events.filter { it.matchId == matchId }.sortedBy { it.recordedAt }

    override suspend fun getPending() = events.sortedBy { it.recordedAt }

    override suspend fun getOldestPending(matchId: String?) =
        events.filter { matchId == null || it.matchId == matchId }.minByOrNull { it.recordedAt }

    override suspend fun getById(id: String) = events.firstOrNull { it.id == id }

    override suspend fun deleteById(id: String) {
        events.removeAll { it.id == id }
    }

    override suspend fun getLatestStartedMatchId() =
        events.filter { it.type == EventType.MATCH_START }.maxByOrNull { it.recordedAt }?.matchId
}

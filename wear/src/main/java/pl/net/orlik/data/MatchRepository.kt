package pl.net.orlik.data

import java.util.UUID
import pl.net.orlik.shared.EventType
import pl.net.orlik.settings.SettingsStore
import pl.net.orlik.shared.Pause

/** Records match lifecycle events in the watch queue. */
class MatchRepository(
    private val dao: MatchEventDao,
    private val settings: SettingsStore,
    private val timerStore: MatchTimerStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * The match still in progress (e.g. after the app was restarted), or null.
     * Relies on its events staying in the queue — sync is blocked while a match is in progress.
     */
    suspend fun findActiveMatch(): ActiveMatch? {
        val matchId = dao.getLatestStartedMatchId() ?: return null
        val match = activeMatchFrom(dao.getByMatch(matchId)) ?: return null
        return match.copy(shiftTimer = timerStore.load(matchId))
    }

    /**
     * Starts a new match: generates its id, records MATCH_START and takes a snapshot of the
     * shift timer settings, which then stay fixed for the whole match.
     */
    suspend fun startMatch(): ActiveMatch {
        val match = ActiveMatch(
            matchId = UUID.randomUUID().toString(),
            startedAt = clock(),
            shiftTimer = settings.shiftTimerSettings().toShiftTimerOrNull(),
        )
        dao.insert(structuralEvent(match.matchId, EventType.MATCH_START, match.startedAt))
        timerStore.save(match.matchId, match.shiftTimer)
        return match
    }

    /** Pauses [match] by recording MATCH_PAUSE; returns the updated match. */
    suspend fun pauseMatch(match: ActiveMatch): ActiveMatch {
        val at = clock()
        dao.insert(structuralEvent(match.matchId, EventType.MATCH_PAUSE, at))
        return match.copy(pauses = match.pauses + Pause(pausedAt = at))
    }

    /** Resumes paused [match] by recording MATCH_RESUME; returns the updated match. */
    suspend fun resumeMatch(match: ActiveMatch): ActiveMatch {
        val at = clock()
        dao.insert(structuralEvent(match.matchId, EventType.MATCH_RESUME, at))
        return match.copy(pauses = match.pauses.dropLast(1) + match.pauses.last().copy(resumedAt = at))
    }

    /** Prepares a voice recording starting now; nothing is saved until [saveVoiceRecording]. */
    fun newVoiceRecording(match: ActiveMatch): VoiceRecording {
        val eventId = UUID.randomUUID().toString()
        return VoiceRecording(
            eventId = eventId,
            matchId = match.matchId,
            startedAt = clock(),
            fileName = "$eventId.m4a",
        )
    }

    /** Queues a finished recording as an event; its type is set later on the phone. */
    suspend fun saveVoiceRecording(recording: VoiceRecording) {
        dao.insert(
            MatchEvent(
                id = recording.eventId,
                matchId = recording.matchId,
                recordedAt = recording.startedAt,
                type = null,
                audioFileRef = recording.fileName,
            ),
        )
    }

    /** Ends [match] by recording MATCH_END. Allowed during a pause too. */
    suspend fun endMatch(match: ActiveMatch) {
        dao.insert(structuralEvent(match.matchId, EventType.MATCH_END, clock()))
        timerStore.clear()
    }

    private fun structuralEvent(matchId: String, type: EventType, at: Long) = MatchEvent(
        id = UUID.randomUUID().toString(),
        matchId = matchId,
        recordedAt = at,
        type = type,
        audioFileRef = null,
    )
}

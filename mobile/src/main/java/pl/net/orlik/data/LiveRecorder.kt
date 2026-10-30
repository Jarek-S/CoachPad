package pl.net.orlik.data

import java.util.UUID
import kotlinx.coroutines.flow.first
import pl.net.orlik.review.LiveAction
import pl.net.orlik.review.liveEventFor
import pl.net.orlik.review.liveMatchOf
import pl.net.orlik.shared.EventType

/**
 * Start, pause, resume and end of a match recorded live on the phone: the same structural events
 * the watch records, stored at once (design doc, section 3.3).
 */
class LiveRecorder(private val dao: MatchEventDao, private val now: () -> Long = System::currentTimeMillis) {

    /** Records [action] if it fits where the match is; a double tap records nothing twice. */
    suspend fun record(matchId: String, action: LiveAction) {
        val type = liveEventFor(liveMatchOf(dao.eventsOf(matchId).first()), action) ?: return
        val at = now()
        dao.insert(MatchEvent(UUID.randomUUID().toString(), matchId, recordedAt = at, type = type, audioFileRef = null, receivedAt = at))
    }

    /** A quick button: the event at once, with its time; the edit sheet fills in the rest. Returns its id. */
    suspend fun add(matchId: String, type: EventType, team: Team?): String {
        val at = now()
        val id = UUID.randomUUID().toString()
        dao.insert(MatchEvent(id, matchId, recordedAt = at, type = type, audioFileRef = null, receivedAt = at, team = team))
        return id
    }

    /** A voice recording made on the phone, timed at its start like the watch's (design doc, section 8). */
    suspend fun addRecording(matchId: String, eventId: String, startedAt: Long, audioFileRef: String) {
        dao.insert(MatchEvent(eventId, matchId, recordedAt = startedAt, type = null, audioFileRef = audioFileRef, receivedAt = now()))
    }
}

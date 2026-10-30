package pl.net.orlik.data

import java.util.UUID
import kotlinx.coroutines.flow.first
import pl.net.orlik.review.LiveAction
import pl.net.orlik.review.liveEventFor
import pl.net.orlik.review.liveMatchOf

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
}

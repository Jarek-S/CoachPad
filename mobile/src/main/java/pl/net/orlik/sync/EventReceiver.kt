package pl.net.orlik.sync

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.review.plannedMatchFor
import pl.net.orlik.shared.sync.EventMetadata

/**
 * Stores events arriving from the watch. An event counts as stored — and may be acknowledged —
 * only once its row and, for a recording, its audio file are both on the phone. Metadata and
 * audio travel separately and either may arrive first.
 */
class EventReceiver(
    private val dao: MatchEventDao,
    private val recordingsDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    // Recordings whose metadata came before the audio. Memory only: if lost, the watch resends both
    private val awaitingAudio = mutableMapOf<String, EventMetadata>()

    /** Returns true when the event is stored (now or by an earlier sync) and can be acknowledged. */
    suspend fun onMetadata(metadata: EventMetadata): Boolean = mutex.withLock {
        if (dao.exists(metadata.id)) return true
        if (metadata.hasAudio && !audioFile(metadata.id).isFile) {
            awaitingAudio[metadata.id] = metadata
            return false
        }
        store(metadata)
        true
    }

    /**
     * Saves a recording's audio; the file appears under its final name only once complete.
     * Returns true when this completes an event that can now be acknowledged.
     */
    suspend fun onAudio(eventId: String, input: InputStream): Boolean {
        val target = audioFile(eventId)
        withContext(Dispatchers.IO) {
            recordingsDir.mkdirs()
            val partial = File(recordingsDir, "${target.name}.part")
            partial.outputStream().use { input.copyTo(it) }
            check(partial.renameTo(target)) { "Cannot move $partial to $target" }
        }
        return mutex.withLock {
            val metadata = awaitingAudio.remove(eventId) ?: return false
            if (!dao.exists(eventId)) store(metadata)
            true
        }
    }

    private suspend fun store(metadata: EventMetadata) {
        // Before the events, so speech-to-text after the sync already has the planned lineup
        if (!dao.matchExists(metadata.matchId)) {
            plannedMatchFor(metadata.recordedAt, dao.plannedMatches())?.let { dao.takeOverPlanned(it, metadata.matchId) }
        }
        dao.insertMatch(Match(metadata.matchId))
        dao.insert(
            MatchEvent(
                id = metadata.id,
                matchId = metadata.matchId,
                recordedAt = metadata.recordedAt,
                type = metadata.type,
                audioFileRef = if (metadata.hasAudio) audioFile(metadata.id).name else null,
                receivedAt = clock(),
            ),
        )
    }

    private fun audioFile(eventId: String) = File(recordingsDir, "$eventId.m4a")
}

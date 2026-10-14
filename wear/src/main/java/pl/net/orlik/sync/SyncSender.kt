package pl.net.orlik.sync

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.net.orlik.data.MatchRepository
import pl.net.orlik.log.EventLog
import pl.net.orlik.log.short
import pl.net.orlik.shared.sync.Ack
import pl.net.orlik.shared.sync.DeleteMatch
import pl.net.orlik.shared.sync.EventMetadata
import pl.net.orlik.shared.sync.SyncRejected
import pl.net.orlik.shared.sync.SyncRequest

/** How the watch talks to the phone; [phone] is the Data Layer node id of the phone. */
interface SyncTransport {
    suspend fun sendRejected(phone: String, message: SyncRejected)
    suspend fun sendEvent(phone: String, metadata: EventMetadata)
    suspend fun sendAudio(phone: String, eventId: String, file: File)
    suspend fun sendDone(phone: String)
}

/**
 * Carries out the phone's commands on the queue. Sync sends it one event at a time and nothing
 * waits on the watch: a request sends the oldest event, and each ACK removes that event and sends
 * the next one. When no ACK arrives the sync simply stops and the phone reports the timeout.
 */
class SyncSender(
    private val repository: MatchRepository,
    private val transport: SyncTransport,
    private val recordingsDir: File,
    private val log: EventLog = EventLog.NONE,
) {
    private val mutex = Mutex()

    // Scope of the sync in progress. Memory only: if the process dies, the phone times out and asks again
    private var scope: SyncRequest? = null

    suspend fun onSyncRequest(phone: String, request: SyncRequest) = mutex.withLock {
        scope = request
        log.log("sync request, match: ${request.matchId?.short() ?: "all"}")
        sendNext(phone)
    }

    suspend fun onAck(phone: String, ack: Ack) = mutex.withLock {
        val removed = repository.removeSynced(ack.eventId)
        log.log("ack ${ack.eventId.short()}, removed from queue: ${removed != null}")
        removed?.audioFileRef?.let { File(recordingsDir, it).delete() }
        sendNext(phone)
    }

    /** Deletes a match's queued events and recordings; the republished summary confirms it. */
    suspend fun onDeleteMatch(command: DeleteMatch) = mutex.withLock {
        val deleted = repository.deleteMatch(command.matchId)
        log.log("delete match ${command.matchId.short()}: ${deleted.size} events")
        deleted.forEach { event ->
            event.audioFileRef?.let { File(recordingsDir, it).delete() }
        }
    }

    private suspend fun sendNext(phone: String) {
        val request = scope ?: return
        // Checked before every event: a match started mid-sync stops it too
        if (repository.findActiveMatch() != null) {
            scope = null
            log.log("sync rejected: match in progress")
            transport.sendRejected(phone, SyncRejected(SyncRejected.Reason.MATCH_IN_PROGRESS))
            return
        }
        val event = repository.nextToSync(request.matchId)
        if (event == null) {
            scope = null
            log.log("sync done")
            transport.sendDone(phone)
            return
        }
        // A recording whose file is missing (e.g. a failed recording) still goes, without audio
        val audio = event.audioFileRef?.let { File(recordingsDir, it) }?.takeIf { it.isFile }
        log.log(
            "send ${event.id.short()} ${event.type ?: "recording"}" +
                if (event.audioFileRef != null && audio == null) " (audio file MISSING)" else "",
        )
        transport.sendEvent(
            phone,
            EventMetadata(event.id, event.matchId, event.recordedAt, event.type, hasAudio = audio != null),
        )
        if (audio != null) transport.sendAudio(phone, event.id, audio)
    }
}

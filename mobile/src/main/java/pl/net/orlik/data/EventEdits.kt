package pl.net.orlik.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.net.orlik.review.EventDraft

/** A recording deleted on the edit screen; [previousStatus] is what "Cofnij" restores. */
data class DeletedRecording(val eventId: String, val previousStatus: ReviewStatus)

/** Changes made on the edit screen. */
class EventEdits(private val dao: MatchEventDao, private val recordingsDir: File) {

    /** Saves [draft]; [confirm] moves the event to "Potwierdzone", otherwise it keeps its status. */
    suspend fun save(eventId: String, draft: EventDraft, confirm: Boolean) {
        val event = dao.event(eventId) ?: return
        val status = if (confirm) ReviewStatus.CONFIRMED else event.status
        dao.updateReview(eventId, draft.type, draft.team, draft.storedNote, status)
    }

    /**
     * Hides a mistaken recording at once. Its audio stays until [purge], so "Cofnij" can bring it
     * back; structural events cannot be deleted (they set the play time).
     */
    suspend fun delete(eventId: String): DeletedRecording? {
        val event = dao.event(eventId) ?: return null
        if (event.type?.isStructural == true || event.status == ReviewStatus.DELETED) return null
        dao.setStatus(eventId, ReviewStatus.DELETED)
        return DeletedRecording(eventId, event.status)
    }

    suspend fun undo(deleted: DeletedRecording) {
        dao.setStatus(deleted.eventId, deleted.previousStatus)
    }

    /** Removes the audio of a deletion that was not undone. */
    suspend fun purge(deleted: DeletedRecording) {
        val event = dao.event(deleted.eventId) ?: return
        if (event.status == ReviewStatus.DELETED) removeAudio(event)
    }

    /** Removes audio left behind when the app closed while "Cofnij" was still offered. */
    suspend fun purgeAll() {
        dao.deletedWithAudio().forEach { removeAudio(it) }
    }

    private suspend fun removeAudio(event: MatchEvent) {
        val file = event.audioFileRef ?: return
        withContext(Dispatchers.IO) { File(recordingsDir, file).delete() }
        dao.clearAudio(event.id)
    }
}

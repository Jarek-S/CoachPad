package pl.net.orlik.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.review.EventDraft
import pl.net.orlik.shared.EventType

class EventEditsTest {

    private val dao = FakeDao()
    private val recordingsDir: File = Files.createTempDirectory("recordings").toFile()
    private val edits = EventEdits(dao, recordingsDir)

    private fun recording(id: String, status: ReviewStatus = ReviewStatus.TO_REVIEW): MatchEvent {
        File(recordingsDir, "$id.m4a").writeText("audio")
        return MatchEvent(id, "m1", 0, null, "$id.m4a", 0, status = status).also { dao.events += it }
    }

    @Test
    fun save_skipKeepsStatus_confirmConfirms() = runBlocking {
        recording("r")
        edits.save("r", EventDraft(EventType.GOAL, Team.HOME, " Marcin "), confirm = false)
        assertEquals(ReviewStatus.TO_REVIEW, dao.event("r")?.status)
        assertEquals("Marcin", dao.event("r")?.rawTranscript)

        edits.save("r", EventDraft(EventType.GOAL, Team.HOME, ""), confirm = true)
        val saved = dao.event("r")!!
        assertEquals(ReviewStatus.CONFIRMED, saved.status)
        assertEquals(Team.HOME, saved.team)
        assertNull(saved.rawTranscript)
    }

    @Test
    fun added_hasNoTime_keepsTypedMinute() = runBlocking {
        edits.add("m1", "a")
        val added = dao.event("a")!!
        assertNull(added.recordedAt)
        assertEquals(ReviewStatus.TO_REVIEW, added.status)

        edits.save("a", EventDraft(EventType.GOAL, Team.AWAY, minute = "23"), confirm = true)
        assertEquals(23, dao.event("a")?.minute)
        assertFalse(edits.delete("a")!!.recording)
    }

    @Test
    fun recorded_ignoresTypedMinute() = runBlocking {
        recording("r")
        edits.save("r", EventDraft(EventType.GOAL, Team.HOME, minute = "23"), confirm = true)
        assertNull(dao.event("r")?.minute)
    }

    @Test
    fun discard_onlyRemovesAnEventAddedByHand() = runBlocking {
        recording("r")
        edits.add("m1", "a")
        edits.discard("a")
        edits.discard("r")
        assertNull(dao.event("a"))
        assertTrue(dao.exists("r"))
    }

    @Test
    fun delete_keepsAudioUntilPurged() = runBlocking {
        recording("r")
        val deleted = edits.delete("r")!!
        assertEquals(ReviewStatus.DELETED, dao.event("r")?.status)
        assertTrue(File(recordingsDir, "r.m4a").exists())

        edits.purge(deleted)
        assertFalse(File(recordingsDir, "r.m4a").exists())
        assertNull(dao.event("r")?.audioFileRef)
        // The row stays, so the same UUID from the watch is still a duplicate
        assertEquals(ReviewStatus.DELETED, dao.event("r")?.status)
    }

    @Test
    fun undo_restoresPreviousStatusAndAudio() = runBlocking {
        recording("r", ReviewStatus.CONFIRMED)
        val deleted = edits.delete("r")!!
        edits.undo(deleted)
        edits.purge(deleted)
        assertEquals(ReviewStatus.CONFIRMED, dao.event("r")?.status)
        assertTrue(File(recordingsDir, "r.m4a").exists())
    }

    @Test
    fun delete_refusesStructuralEvents() = runBlocking {
        dao.events += MatchEvent("s", "m1", 0, EventType.MATCH_START, null, 0)
        assertNull(edits.delete("s"))
        assertEquals(ReviewStatus.CONFIRMED, dao.event("s")?.status)
    }

    @Test
    fun purgeAll_removesAudioLeftByDeletions() = runBlocking {
        recording("gone", ReviewStatus.DELETED)
        recording("kept")
        edits.purgeAll()
        assertFalse(File(recordingsDir, "gone.m4a").exists())
        assertTrue(File(recordingsDir, "kept.m4a").exists())
    }
}

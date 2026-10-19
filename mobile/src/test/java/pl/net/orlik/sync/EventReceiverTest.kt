package pl.net.orlik.sync

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.data.FakeDao
import pl.net.orlik.data.LineupPlayer
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.ReviewStatus
import pl.net.orlik.data.Team
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.sync.EventMetadata

class EventReceiverTest {

    private val dao = FakeDao()
    private val recordingsDir: File = Files.createTempDirectory("recordings").toFile()
    private val receiver = EventReceiver(dao, recordingsDir) { 9_000 }

    private val start = EventMetadata("e1", "m1", 1_000, EventType.MATCH_START, hasAudio = false)
    private val recording = EventMetadata("r1", "m1", 2_000, type = null, hasAudio = true)

    @Test
    fun structuralEvent_isStoredAndAcked() = runBlocking {
        assertTrue(receiver.onMetadata(start))

        val stored = dao.events.single()
        assertEquals(MatchEvent("e1", "m1", 1_000, EventType.MATCH_START, null, 9_000), stored)
        // Structural events need no review
        assertEquals(ReviewStatus.CONFIRMED, stored.status)
        assertEquals(listOf(Match("m1")), dao.matches)
    }

    @Test
    fun recording_isAckedOnlyAfterAudio() = runBlocking {
        assertFalse(receiver.onMetadata(recording))
        assertTrue(dao.events.isEmpty())

        assertTrue(receiver.onAudio("r1", "audio".byteInputStream()))

        assertEquals("r1.m4a", dao.events.single().audioFileRef)
        assertEquals(ReviewStatus.TO_REVIEW, dao.events.single().status)
        assertEquals("audio", File(recordingsDir, "r1.m4a").readText())
    }

    @Test
    fun audioBeforeMetadata_isAckedOnMetadata() = runBlocking {
        assertFalse(receiver.onAudio("r1", "audio".byteInputStream()))
        assertTrue(dao.events.isEmpty())

        assertTrue(receiver.onMetadata(recording))
        assertEquals("r1", dao.events.single().id)
    }

    @Test
    fun duplicate_isAckedWithoutSecondRow() = runBlocking {
        receiver.onMetadata(start)

        assertTrue(receiver.onMetadata(start))
        assertEquals(1, dao.events.size)
    }

    @Test
    fun duplicateRecording_isAckedRightAway() = runBlocking {
        receiver.onMetadata(recording)
        receiver.onAudio("r1", "audio".byteInputStream())

        // The watch resends both when it missed the ACK; metadata alone is enough to ACK again
        assertTrue(receiver.onMetadata(recording))
        assertFalse(receiver.onAudio("r1", "audio".byteInputStream()))
        assertEquals(1, dao.events.size)
    }

    @Test
    fun unfinishedAudio_leavesNoFinalFile() = runBlocking {
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("channel closed")
        }

        runCatching { receiver.onAudio("r1", broken) }

        assertFalse(File(recordingsDir, "r1.m4a").exists())
        assertFalse(receiver.onMetadata(recording))
        assertNull(dao.events.firstOrNull())
    }

    @Test
    fun newMatch_takesOverTheOnlyPlannedMatch() = runBlocking {
        dao.matches += Match("p1", "Orły", "Rywale FC", plannedAt = 500)
        dao.lineup += LineupPlayer(1, "p1", Team.HOME, 7, "Marcin")

        receiver.onMetadata(start)

        assertEquals(listOf(Match("m1", "Orły", "Rywale FC")), dao.matches)
        assertEquals("m1", dao.lineup.single().matchId)
    }

    @Test
    fun laterEvents_ofAKnownMatch_takeNoPlannedMatch() = runBlocking {
        receiver.onMetadata(start)
        dao.matches += Match("p1", "Orły", null, plannedAt = 1_500)

        receiver.onMetadata(recording)
        receiver.onAudio("r1", "audio".byteInputStream())

        assertEquals(listOf(Match("m1"), Match("p1", "Orły", null, plannedAt = 1_500)), dao.matches)
    }

    @Test
    fun emptyPlannedMatch_isNotTakenOver() = runBlocking {
        dao.matches += Match("p1", plannedAt = 500)

        receiver.onMetadata(start)

        assertEquals(listOf(Match("p1", plannedAt = 500), Match("m1")), dao.matches)
    }

    @Test
    fun severalPlannedMatches_areLeftAlone() = runBlocking {
        dao.matches += Match("p1", "Orły", null, plannedAt = 500)
        dao.matches += Match("p2", "Sokoły", null, plannedAt = 600)

        receiver.onMetadata(start)

        assertEquals(Match("m1"), dao.matches.last())
        assertEquals(3, dao.matches.size)
    }
}

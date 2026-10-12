package pl.net.orlik.sync

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.data.Match
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.MatchEventDao
import pl.net.orlik.data.ReviewStatus
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

    private class FakeDao : MatchEventDao {
        val events = mutableListOf<MatchEvent>()
        val matches = mutableListOf<Match>()

        override suspend fun insert(event: MatchEvent) {
            if (events.none { it.id == event.id }) events += event
        }

        override suspend fun insertMatch(match: Match) {
            if (matches.none { it.id == match.id }) matches += match
        }

        override suspend fun exists(id: String) = events.any { it.id == id }

        override fun count(): Flow<Int> = flowOf(events.size)
    }
}

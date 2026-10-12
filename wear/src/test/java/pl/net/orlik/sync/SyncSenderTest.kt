package pl.net.orlik.sync

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.net.orlik.data.FakeDao
import pl.net.orlik.data.FakeSettings
import pl.net.orlik.data.FakeTimerStore
import pl.net.orlik.data.MatchEvent
import pl.net.orlik.data.MatchRepository
import pl.net.orlik.shared.EventType
import pl.net.orlik.shared.sync.Ack
import pl.net.orlik.shared.sync.EventMetadata
import pl.net.orlik.shared.sync.SyncRejected
import pl.net.orlik.shared.sync.SyncRequest

class SyncSenderTest {

    private val dao = FakeDao()
    private val repository = MatchRepository(dao, FakeSettings(), FakeTimerStore(), queuePublisher = {})
    private val transport = RecordingTransport()
    private val recordingsDir: File = Files.createTempDirectory("recordings").toFile()
    private val sender = SyncSender(repository, transport, recordingsDir)

    @Test
    fun request_sendsOnlyTheOldestEvent() = runBlocking {
        endedMatch("m1", at = 1_000)

        sender.onSyncRequest(PHONE, SyncRequest())

        assertEquals(listOf("event m1-start"), transport.sent)
    }

    @Test
    fun eachAck_removesEvent_andSendsNext_untilDone() = runBlocking {
        endedMatch("m1", at = 1_000)

        sender.onSyncRequest(PHONE, SyncRequest())
        sender.onAck(PHONE, Ack("m1-start"))
        sender.onAck(PHONE, Ack("m1-end"))

        assertEquals(listOf("event m1-start", "event m1-end", "done"), transport.sent)
        assertTrue(dao.events.isEmpty())
    }

    @Test
    fun recording_sendsMetadataThenAudio_andAckDeletesFile() = runBlocking {
        dao.insert(MatchEvent("r1", "m1", 1_500, type = null, audioFileRef = "r1.m4a"))
        val file = File(recordingsDir, "r1.m4a").apply { writeText("audio") }

        sender.onSyncRequest(PHONE, SyncRequest())
        assertEquals(listOf("event r1 audio", "audio r1"), transport.sent)

        sender.onAck(PHONE, Ack("r1"))
        assertFalse(file.exists())
    }

    @Test
    fun recordingWithoutFile_isSentWithoutAudio() = runBlocking {
        dao.insert(MatchEvent("r1", "m1", 1_500, type = null, audioFileRef = "r1.m4a"))

        sender.onSyncRequest(PHONE, SyncRequest())

        assertEquals(listOf("event r1"), transport.sent)
    }

    @Test
    fun matchScope_skipsOtherMatches() = runBlocking {
        endedMatch("m1", at = 1_000)
        endedMatch("m2", at = 5_000)

        sender.onSyncRequest(PHONE, SyncRequest("m2"))
        sender.onAck(PHONE, Ack("m2-start"))
        sender.onAck(PHONE, Ack("m2-end"))

        assertEquals(listOf("event m2-start", "event m2-end", "done"), transport.sent)
        assertEquals(2, dao.events.size)
    }

    @Test
    fun matchInProgress_rejectsAndSendsNothing() = runBlocking {
        endedMatch("m1", at = 1_000)
        dao.insert(MatchEvent("m2-start", "m2", 5_000, EventType.MATCH_START, audioFileRef = null))

        sender.onSyncRequest(PHONE, SyncRequest())

        assertEquals(listOf("rejected MATCH_IN_PROGRESS"), transport.sent)
        assertEquals(3, dao.events.size)
    }

    @Test
    fun duplicateAck_isHarmless() = runBlocking {
        endedMatch("m1", at = 1_000)

        sender.onSyncRequest(PHONE, SyncRequest())
        sender.onAck(PHONE, Ack("m1-start"))
        sender.onAck(PHONE, Ack("m1-start"))

        // The repeated ACK resends the current oldest event; the phone deduplicates it
        assertEquals(listOf("event m1-start", "event m1-end", "event m1-end"), transport.sent)
        assertEquals(1, dao.events.size)
    }

    @Test
    fun ackWithoutSync_onlyRemovesEvent() = runBlocking {
        // E.g. the watch process restarted mid-sync and lost the scope
        endedMatch("m1", at = 1_000)

        sender.onAck(PHONE, Ack("m1-start"))

        assertTrue(transport.sent.isEmpty())
        assertEquals(1, dao.events.size)
    }

    private suspend fun endedMatch(matchId: String, at: Long) {
        dao.insert(MatchEvent("$matchId-start", matchId, at, EventType.MATCH_START, audioFileRef = null))
        dao.insert(MatchEvent("$matchId-end", matchId, at + 1_000, EventType.MATCH_END, audioFileRef = null))
    }

    private class RecordingTransport : SyncTransport {
        val sent = mutableListOf<String>()

        override suspend fun sendRejected(phone: String, message: SyncRejected) {
            sent += "rejected ${message.reason}"
        }

        override suspend fun sendEvent(phone: String, metadata: EventMetadata) {
            sent += "event ${metadata.id}" + if (metadata.hasAudio) " audio" else ""
        }

        override suspend fun sendAudio(phone: String, eventId: String, file: File) {
            sent += "audio $eventId"
        }

        override suspend fun sendDone(phone: String) {
            sent += "done"
        }
    }

    private companion object {
        const val PHONE = "phone"
    }
}

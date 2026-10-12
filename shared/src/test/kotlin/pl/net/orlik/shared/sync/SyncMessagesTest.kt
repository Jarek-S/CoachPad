package pl.net.orlik.shared.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.net.orlik.shared.EventType

class SyncMessagesTest {

    @Test
    fun eventMetadata_roundTrips() {
        val recording = EventMetadata("e1", "m1", 1_000, type = null, hasAudio = true)
        val start = EventMetadata("e2", "m1", 500, EventType.MATCH_START, hasAudio = false)

        assertEquals(recording, SyncCodec.decode<EventMetadata>(SyncCodec.encode(recording)))
        assertEquals(start, SyncCodec.decode<EventMetadata>(SyncCodec.encode(start)))
    }

    @Test
    fun syncRequest_defaultsToAllMatches() {
        assertNull(SyncCodec.decode<SyncRequest>("{}".encodeToByteArray()).matchId)
        assertEquals(SyncRequest("m1"), SyncCodec.decode<SyncRequest>(SyncCodec.encode(SyncRequest("m1"))))
    }

    @Test
    fun queueSummary_roundTrips() {
        val summary = QueueSummary(
            matchInProgress = true,
            matches = listOf(PendingMatch("m1", 1_000, 12), PendingMatch("m2", 9_000, 3)),
        )
        assertEquals(summary, SyncCodec.decode<QueueSummary>(SyncCodec.encode(summary)))
    }

    @Test
    fun otherMessages_roundTrip() {
        val rejected = SyncRejected(SyncRejected.Reason.MATCH_IN_PROGRESS)
        assertEquals(rejected, SyncCodec.decode<SyncRejected>(SyncCodec.encode(rejected)))
        assertEquals(Ack("e1"), SyncCodec.decode<Ack>(SyncCodec.encode(Ack("e1"))))
        assertEquals(DeleteMatch("m1"), SyncCodec.decode<DeleteMatch>(SyncCodec.encode(DeleteMatch("m1"))))
    }

    @Test
    fun unknownFields_areIgnored() {
        // A newer app version may add fields; an older one must still read the message
        val json = """{"eventId":"e1","receivedAt":123}"""
        assertEquals(Ack("e1"), SyncCodec.decode<Ack>(json.encodeToByteArray()))
    }

    @Test
    fun audioChannelPath_carriesEventId() {
        assertEquals("e1", DataLayerPaths.eventIdFromAudioChannel(DataLayerPaths.audioChannel("e1")))
        assertNull(DataLayerPaths.eventIdFromAudioChannel(DataLayerPaths.EVENT))
        assertNull(DataLayerPaths.eventIdFromAudioChannel("/sync/audio/"))
    }
}

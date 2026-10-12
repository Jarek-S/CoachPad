package pl.net.orlik.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import pl.net.orlik.data.matchRepository
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.shared.sync.Ack
import pl.net.orlik.shared.sync.DataLayerPaths
import pl.net.orlik.shared.sync.SyncCodec
import pl.net.orlik.shared.sync.SyncRequest

/**
 * Receives the phone's sync messages. The system starts this service for each message, also when
 * the app is not running. Callbacks run one at a time on a background thread, so blocking is fine.
 */
class WatchSyncService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val sender = syncSender(this)
        try {
            runBlocking {
                when (event.path) {
                    DataLayerPaths.SYNC_REQUEST ->
                        sender.onSyncRequest(event.sourceNodeId, SyncCodec.decode<SyncRequest>(event.data))
                    DataLayerPaths.ACK -> sender.onAck(event.sourceNodeId, SyncCodec.decode<Ack>(event.data))
                }
            }
        } catch (e: Exception) {
            // The phone notices the missing reply and reports it; the queue stays intact
            Log.w(TAG, "Sync message ${event.path} failed", e)
        }
    }

    private companion object {
        const val TAG = "WatchSync"
    }
}

@Volatile
private var sender: SyncSender? = null

/** One sender per process: it holds the scope of the sync in progress between messages. */
private fun syncSender(context: Context): SyncSender =
    sender ?: synchronized(SyncSender::class) {
        sender ?: SyncSender(
            repository = matchRepository(context),
            transport = DataLayerSyncTransport(context),
            recordingsDir = recordingsDir(context),
        ).also { sender = it }
    }

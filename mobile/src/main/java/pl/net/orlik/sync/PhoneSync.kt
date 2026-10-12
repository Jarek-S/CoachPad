package pl.net.orlik.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pl.net.orlik.data.PhoneDatabase
import pl.net.orlik.data.recordingsDir
import pl.net.orlik.shared.sync.Ack
import pl.net.orlik.shared.sync.DataLayerPaths
import pl.net.orlik.shared.sync.DeleteMatch
import pl.net.orlik.shared.sync.EventMetadata
import pl.net.orlik.shared.sync.SyncCodec
import pl.net.orlik.shared.sync.SyncRequest

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Running(val received: Int) : SyncStatus
    data class Done(val received: Int) : SyncStatus

    /** The watch refused: a match is in progress there. */
    data object Rejected : SyncStatus
    data object WatchUnavailable : SyncStatus

    /** The watch stopped answering mid-sync; nothing is lost, the queue stays on the watch. */
    data object NoResponse : SyncStatus
}

/**
 * Runs a sync started with the "Synchronizuj" button. The watch sends one event at a time and
 * sends the next one after our ACK, so all the phone does is store, acknowledge and watch the time.
 */
class PhoneSync private constructor(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val channelClient = Wearable.getChannelClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val receiver = EventReceiver(PhoneDatabase.get(context).receivedEventDao(), recordingsDir(context))
    // A failure is only logged: no ACK goes out, the watch keeps the event and the watchdog reports it
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w(TAG, "Sync step failed", e) },
    )

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var watchdog: Job? = null

    private val messageListener = MessageClient.OnMessageReceivedListener { event -> onMessage(event) }

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            val eventId = DataLayerPaths.eventIdFromAudioChannel(channel.path) ?: return
            scope.launch { receiveAudio(channel, eventId) }
        }
    }

    init {
        // Registered for the life of the process; a sync only runs while the app is open
        messageClient.addListener(messageListener)
        channelClient.registerChannelCallback(channelCallback)
    }

    /** Syncs one match, or all pending matches when [matchId] is null. */
    fun start(matchId: String? = null) {
        if (_status.value is SyncStatus.Running) return
        _status.value = SyncStatus.Running(received = 0)
        scope.launch {
            val sent = sendToWatch(DataLayerPaths.SYNC_REQUEST, SyncCodec.encode(SyncRequest(matchId))) {
                restartWatchdog()
            }
            if (!sent) finish(SyncStatus.WatchUnavailable)
        }
    }

    /**
     * Asks the watch to delete a match's events and recordings still queued there; what the phone
     * already has stays. The match leaving the queue summary confirms the deletion.
     */
    fun deleteMatch(matchId: String) {
        scope.launch {
            if (!sendToWatch(DataLayerPaths.DELETE_MATCH, SyncCodec.encode(DeleteMatch(matchId)))) {
                _status.value = SyncStatus.WatchUnavailable
            }
        }
    }

    /** Sends a message to the watch; false when no watch is connected or sending failed. */
    private suspend fun sendToWatch(path: String, payload: ByteArray, beforeSend: () -> Unit = {}): Boolean =
        try {
            val nodes = nodeClient.connectedNodes.await()
            val watch = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (watch != null) {
                beforeSend()
                messageClient.sendMessage(watch.id, path, payload).await()
            }
            watch != null
        } catch (e: Exception) {
            Log.w(TAG, "Message $path not sent", e)
            false
        }

    private fun onMessage(event: MessageEvent) {
        when (event.path) {
            DataLayerPaths.EVENT -> scope.launch {
                restartWatchdog()
                val metadata = SyncCodec.decode<EventMetadata>(event.data)
                if (receiver.onMetadata(metadata)) acknowledge(event.sourceNodeId, metadata.id)
            }
            DataLayerPaths.SYNC_DONE -> finish(SyncStatus.Done(receivedSoFar()))
            DataLayerPaths.SYNC_REJECTED -> finish(SyncStatus.Rejected)
        }
    }

    private suspend fun receiveAudio(channel: ChannelClient.Channel, eventId: String) {
        restartWatchdog()
        try {
            val complete = channelClient.getInputStream(channel).await().use { receiver.onAudio(eventId, it) }
            if (complete) acknowledge(channel.nodeId, eventId)
        } catch (e: Exception) {
            // No ACK: the watch keeps the event and the watchdog reports the stalled sync
            Log.w(TAG, "Audio of $eventId not received", e)
        } finally {
            channelClient.close(channel)
        }
    }

    private suspend fun acknowledge(watch: String, eventId: String) {
        _status.update { if (it is SyncStatus.Running) it.copy(received = it.received + 1) else SyncStatus.Running(1) }
        restartWatchdog()
        try {
            messageClient.sendMessage(watch, DataLayerPaths.ACK, SyncCodec.encode(Ack(eventId))).await()
        } catch (e: Exception) {
            Log.w(TAG, "ACK of $eventId not sent", e)
        }
    }

    private fun receivedSoFar() = (_status.value as? SyncStatus.Running)?.received ?: 0

    @Synchronized
    private fun restartWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(TIMEOUT_MILLIS)
            finish(SyncStatus.NoResponse)
        }
    }

    @Synchronized
    private fun finish(status: SyncStatus) {
        watchdog?.cancel()
        watchdog = null
        _status.value = status
    }

    companion object {
        private const val TAG = "PhoneSync"

        /** Longest wait for the watch's next message; an audio file normally takes well under a second. */
        private const val TIMEOUT_MILLIS = 15_000L

        @Volatile
        private var instance: PhoneSync? = null

        fun get(context: Context): PhoneSync =
            instance ?: synchronized(this) {
                instance ?: PhoneSync(context.applicationContext).also { instance = it }
            }
    }
}

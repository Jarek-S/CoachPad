package pl.net.orlik.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.net.orlik.shared.sync.DataLayerPaths
import pl.net.orlik.shared.sync.EventMetadata
import pl.net.orlik.shared.sync.SyncCodec
import pl.net.orlik.shared.sync.SyncDone
import pl.net.orlik.shared.sync.SyncRejected

/** [SyncTransport] over the Wearable Data Layer: messages for metadata, a channel per audio file. */
class DataLayerSyncTransport(context: Context) : SyncTransport {

    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val channelClient = Wearable.getChannelClient(context.applicationContext)

    override suspend fun sendRejected(phone: String, message: SyncRejected) {
        send(phone, DataLayerPaths.SYNC_REJECTED, SyncCodec.encode(message))
    }

    override suspend fun sendEvent(phone: String, metadata: EventMetadata) {
        send(phone, DataLayerPaths.EVENT, SyncCodec.encode(metadata))
    }

    override suspend fun sendDone(phone: String) {
        send(phone, DataLayerPaths.SYNC_DONE, SyncCodec.encode(SyncDone))
    }

    override suspend fun sendAudio(phone: String, eventId: String, file: File) {
        val channel = channelClient.openChannel(phone, DataLayerPaths.audioChannel(eventId)).await()
        val output = channelClient.getOutputStream(channel).await()
        // Closing the stream marks the end of the file; the phone closes the channel once it has read it
        withContext(Dispatchers.IO) {
            output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        }
    }

    private suspend fun send(phone: String, path: String, payload: ByteArray) {
        messageClient.sendMessage(phone, path, payload).await()
    }
}

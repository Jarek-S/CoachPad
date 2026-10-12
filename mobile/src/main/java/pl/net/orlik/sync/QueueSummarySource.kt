package pl.net.orlik.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import pl.net.orlik.shared.sync.DataLayerPaths
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.shared.sync.SyncCodec

/**
 * Reads the queue summary the watch publishes as a DataItem. The system keeps the last known
 * copy on the phone, so this works while the watch is out of range too.
 */
class QueueSummarySource(context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    // No host: matches the item from any node (there is a single watch)
    private val uri = Uri.Builder()
        .scheme(PutDataRequest.WEAR_URI_SCHEME)
        .path(DataLayerPaths.QUEUE_SUMMARY)
        .build()

    /** The latest summary, re-emitted on every change; null when the watch has not published one. */
    val summaries: Flow<QueueSummary?> = callbackFlow {
        fun reload() {
            dataClient.getDataItems(uri)
                .addOnSuccessListener { buffer ->
                    val summary = buffer.firstNotNullOfOrNull(::decode)
                    buffer.release()
                    trySend(summary)
                }
                .addOnFailureListener { Log.w(TAG, "Queue summary not read", it) }
        }

        val listener = DataClient.OnDataChangedListener { reload() }
        dataClient.addListener(listener, uri, DataClient.FILTER_LITERAL)
        reload()
        awaitClose { dataClient.removeListener(listener) }
    }

    private fun decode(item: DataItem): QueueSummary? {
        val bytes = DataMapItem.fromDataItem(item).dataMap.getByteArray(DataLayerPaths.QUEUE_SUMMARY_KEY)
            ?: return null
        return runCatching { SyncCodec.decode<QueueSummary>(bytes) }
            .onFailure { Log.w(TAG, "Queue summary not decoded", it) }
            .getOrNull()
    }

    private companion object {
        const val TAG = "QueueSummary"
    }
}

package pl.net.orlik.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.net.orlik.data.QueueSummaryPublisher
import pl.net.orlik.shared.sync.DataLayerPaths
import pl.net.orlik.shared.sync.QueueSummary
import pl.net.orlik.shared.sync.SyncCodec

/**
 * Publishes the summary as a DataItem. The write goes to the watch's local copy and the system
 * delivers it to the phone whenever they are connected, so this works offline too.
 */
class DataLayerQueueSummaryPublisher(context: Context) : QueueSummaryPublisher {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(summary: QueueSummary) {
        val request = PutDataMapRequest.create(DataLayerPaths.QUEUE_SUMMARY).apply {
            dataMap.putByteArray(DataLayerPaths.QUEUE_SUMMARY_KEY, SyncCodec.encode(summary))
        }.asPutDataRequest().setUrgent()
        try {
            // A local write, normally instant; the timeout only guards the match screen against a stuck Play services
            withContext(Dispatchers.IO) { Tasks.await(dataClient.putDataItem(request), TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Recording a match must never fail because of the summary; it is republished on the next change
            Log.w(TAG, "Queue summary not published", e)
        }
    }

    private companion object {
        const val TAG = "QueueSummary"
        const val TIMEOUT_SECONDS = 5L
    }
}

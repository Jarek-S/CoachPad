package pl.net.orlik.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Whether the watch is reachable now. The queue summary is the last known state and stays on the
 * phone when the watch is out of range, so the sync screen needs this to know if a command
 * would get through.
 */
class WatchConnection(context: Context) {

    private val nodeClient = Wearable.getNodeClient(context.applicationContext)

    /** Checked every few seconds while collected (only while the sync screen is open). */
    val connected: Flow<Boolean> = flow {
        while (true) {
            emit(isConnected())
            delay(POLL_MILLIS)
        }
    }.distinctUntilChanged()

    private suspend fun isConnected(): Boolean =
        try {
            nodeClient.connectedNodes.await().isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Connected nodes not read", e)
            false
        }

    private companion object {
        const val TAG = "WatchConnection"
        const val POLL_MILLIS = 5_000L
    }
}

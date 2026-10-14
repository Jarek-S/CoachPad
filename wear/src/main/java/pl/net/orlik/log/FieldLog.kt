package pl.net.orlik.log

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Field test log: what happened to every recording and sync step, kept on the watch so it can be
 * compared with what reached the phone after a match. System logs are overwritten within hours.
 */
fun interface EventLog {
    fun log(message: String)

    companion object {
        val NONE = EventLog {}
    }
}

/**
 * Appends timestamped lines to [file]. When it grows past [maxBytes] it becomes `<name>.1`
 * (replacing an older one), so at most about twice [maxBytes] is kept.
 */
class FileEventLog(
    private val file: File,
    private val maxBytes: Long = 256_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : EventLog {

    @Synchronized
    override fun log(message: String) {
        try {
            file.parentFile?.mkdirs()
            if (file.length() > maxBytes) {
                val previous = File(file.path + ".1")
                previous.delete()
                file.renameTo(previous)
            }
            file.appendText("${timeFormat.format(Instant.ofEpochMilli(clock()))} $message\n")
        } catch (e: Exception) {
            // The log must never break recording or sync
            Log.w("FieldLog", "Not logged: $message", e)
        }
    }

    private companion object {
        val timeFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}

/** First 8 characters of a UUID: enough to tell events apart in the log. */
fun String.short(): String = take(8)

@Volatile
private var instance: EventLog? = null

/** The app's field log in `files/log/field-log.txt`; one per process. */
fun fieldLog(context: Context): EventLog =
    instance ?: synchronized(EventLog::class) {
        instance ?: FileEventLog(File(context.applicationContext.filesDir, "log/field-log.txt")).also { instance = it }
    }

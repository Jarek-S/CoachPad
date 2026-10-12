package pl.net.orlik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(entities = [ReceivedEvent::class], version = 1)
abstract class PhoneDatabase : RoomDatabase() {

    abstract fun receivedEventDao(): ReceivedEventDao

    companion object {
        @Volatile
        private var instance: PhoneDatabase? = null

        /** Single database instance per process. */
        fun get(context: Context): PhoneDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhoneDatabase::class.java,
                    "match_notes.db",
                ).build().also { instance = it }
            }
    }
}

/** Where received recordings are stored; [ReceivedEvent.audioFileRef] is relative to it. */
fun recordingsDir(context: Context): File = File(context.applicationContext.filesDir, "recordings")

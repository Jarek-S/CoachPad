package pl.net.orlik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MatchEvent::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchEventDao(): MatchEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Single database instance per process. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "match_notes.db",
                ).build().also { instance = it }
            }
    }
}

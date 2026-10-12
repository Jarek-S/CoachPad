package pl.net.orlik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [MatchEvent::class, Match::class], version = 2)
abstract class PhoneDatabase : RoomDatabase() {

    abstract fun matchEventDao(): MatchEventDao

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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

/** Version 2 adds the review data; events already received keep their place in the review. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN team TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN raw_transcript TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN status TEXT NOT NULL DEFAULT 'TO_REVIEW'")
        db.execSQL("ALTER TABLE events ADD COLUMN linked_goal_event_id TEXT")
        db.execSQL(
            "UPDATE events SET status = 'CONFIRMED' " +
                "WHERE type IN ('MATCH_START', 'MATCH_END', 'MATCH_PAUSE', 'MATCH_RESUME')",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS matches (id TEXT NOT NULL, home_team TEXT, away_team TEXT, PRIMARY KEY(id))",
        )
        db.execSQL("INSERT OR IGNORE INTO matches (id) SELECT DISTINCT match_id FROM events")
    }
}

/** Where received recordings are stored; [MatchEvent.audioFileRef] is relative to it. */
fun recordingsDir(context: Context): File = File(context.applicationContext.filesDir, "recordings")

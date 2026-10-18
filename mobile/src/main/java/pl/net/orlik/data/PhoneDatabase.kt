package pl.net.orlik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(entities = [MatchEvent::class, Match::class, LineupPlayer::class], version = 4)
abstract class PhoneDatabase : RoomDatabase() {

    abstract fun matchEventDao(): MatchEventDao

    abstract fun lineupDao(): LineupDao

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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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

/** Version 3 adds the scorer / player and the assist, typed in review until team rosters (phase 5). */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN player_name TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN assist_name TEXT")
    }
}

/** Version 4 adds the match lineups (phase 5). */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS lineup_players (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "match_id TEXT NOT NULL, team TEXT NOT NULL, number INTEGER, name TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lineup_players_match_id ON lineup_players (match_id)")
    }
}

/** Where received recordings are stored; [MatchEvent.audioFileRef] is relative to it. */
fun recordingsDir(context: Context): File = File(context.applicationContext.filesDir, "recordings")

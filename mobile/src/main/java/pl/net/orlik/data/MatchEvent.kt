package pl.net.orlik.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pl.net.orlik.review.PlannedMatch
import pl.net.orlik.shared.EventType

enum class Team { HOME, AWAY }

enum class ReviewStatus {
    /** Shown under "Do przejrzenia". */
    TO_REVIEW,

    /** Shown under "Potwierdzone". */
    CONFIRMED,

    /**
     * A recording removed as a mistake: hidden everywhere and its audio deleted. The row stays so
     * that the same UUID sent again by the watch is dropped as a duplicate instead of coming back.
     */
    DELETED,
}

/** A match event on the phone: what the watch sent plus what the review adds. */
@Entity(
    tableName = "events",
    indices = [Index("match_id")],
)
data class MatchEvent(
    /** UUID generated on the watch; duplicates from a repeated sync are dropped by it. */
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "match_id")
    val matchId: String,

    /**
     * Epoch milliseconds, as recorded on the watch (or on the phone during a match); null for an
     * event typed in by hand afterwards (design doc, section 3.3).
     */
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long?,

    /** Structural MATCH_* type, the type set in review, or null for a recording not described yet. */
    val type: EventType?,

    /** Audio file name in the recordings directory; null for structural events. */
    @ColumnInfo(name = "audio_file_ref")
    val audioFileRef: String?,

    /** Epoch milliseconds when the phone stored the event. */
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    /** Null for structural events and notes, and until set in review. */
    val team: Team? = null,

    /** The note typed after listening ("Notatka"); later filled by speech-to-text. */
    @ColumnInfo(name = "raw_transcript")
    val rawTranscript: String? = null,

    @ColumnInfo(defaultValue = "'TO_REVIEW'")
    val status: ReviewStatus = initialStatus(type),

    /** For GOAL_DISALLOWED: the goal it cancels (design doc, section 8). */
    @ColumnInfo(name = "linked_goal_event_id")
    val linkedGoalEventId: String? = null,

    /** The scorer of a goal, the player of a card; typed by hand until team rosters (phase 5). */
    @ColumnInfo(name = "player_name")
    val playerName: String? = null,

    /** The assist of a goal. */
    @ColumnInfo(name = "assist_name")
    val assistName: String? = null,

    /** What speech-to-text heard in the recording; null until recognised ("" when it heard nothing). */
    val transcript: String? = null,

    /** The football minute typed for an event added by hand (23 for 23'); null when not given. */
    val minute: Int? = null,
)

/** Structural events need no review; everything else waits under "Do przejrzenia". */
fun initialStatus(type: EventType?): ReviewStatus =
    if (type?.isStructural == true) ReviewStatus.CONFIRMED else ReviewStatus.TO_REVIEW

/**
 * A match as the phone knows it. Team names are set on the phone; null until then. A match
 * planned on the phone before kickoff has its own id until a watch match takes it over.
 */
@Entity(tableName = "matches")
data class Match(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "home_team")
    val homeTeam: String? = null,

    @ColumnInfo(name = "away_team")
    val awayTeam: String? = null,

    /** Epoch milliseconds when the match was planned on the phone; null for a match from the watch. */
    @ColumnInfo(name = "planned_at")
    val plannedAt: Long? = null,

    /** When a match typed in from notes was played, as given; null for a recorded match. */
    @ColumnInfo(name = "played_at")
    val playedAt: Long? = null,
)

/**
 * A confirmed goal `g` not cancelled by a confirmed disallowed goal: the same rule as the score in
 * [pl.net.orlik.export.matchExportOf].
 */
private const val COUNTED_GOAL = """
    g.type = 'GOAL' AND g.status = 'CONFIRMED' AND NOT EXISTS (
        SELECT 1 FROM events d WHERE d.match_id = g.match_id AND d.type = 'GOAL_DISALLOWED'
            AND d.status = 'CONFIRMED' AND d.linked_goal_event_id = g.id
    )
"""

/** One row of the match list. */
data class MatchListItem(
    val matchId: String,
    val homeTeam: String?,
    val awayTeam: String?,
    /**
     * Time of the match's first event (MATCH_START when it was synced), else the date given for a
     * match typed in from notes; null for a planned match.
     */
    val startedAt: Long?,
    val toReview: Int,
    val recordings: Int,
    /** Players in both lineups. */
    val players: Int = 0,
    /** Counted goals, as in the export: confirmed and not cancelled by a confirmed disallowed goal. */
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
) {
    /** Planned on the phone, nothing from the watch yet. */
    val isPlanned: Boolean get() = startedAt == null
}

@Dao
interface MatchEventDao {

    /** Ignores an event that is already stored, so a repeated sync cannot duplicate it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: MatchEvent)

    /** Creates the match on its first received event; an existing one keeps its names. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatch(match: Match)

    @Query("SELECT EXISTS(SELECT 1 FROM events WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM matches WHERE id = :matchId)")
    suspend fun matchExists(matchId: String): Boolean

    /** Planned matches without events yet; one with neither team names nor players plans nothing. */
    @Query(
        """
        SELECT id, planned_at AS plannedAt FROM matches m
        WHERE planned_at IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM events e WHERE e.match_id = m.id)
            AND (home_team IS NOT NULL OR away_team IS NOT NULL
                OR EXISTS (SELECT 1 FROM lineup_players p WHERE p.match_id = m.id))
        """,
    )
    suspend fun plannedMatches(): List<PlannedMatch>

    @Query(
        """
        UPDATE matches SET home_team = (SELECT home_team FROM matches WHERE id = :plannedId),
            away_team = (SELECT away_team FROM matches WHERE id = :plannedId)
        WHERE id = :matchId
        """,
    )
    suspend fun copyTeamNames(plannedId: String, matchId: String)

    @Query("UPDATE lineup_players SET match_id = :matchId WHERE match_id = :plannedId")
    suspend fun moveLineup(plannedId: String, matchId: String)

    /**
     * A match from the watch takes the team names and lineups of a planned match, which goes: on
     * its first sync, or chosen later in its review.
     */
    @Transaction
    suspend fun takeOverPlanned(plannedId: String, matchId: String) {
        insertMatch(Match(matchId))
        copyTeamNames(plannedId, matchId)
        moveLineup(plannedId, matchId)
        deleteMatchRow(plannedId)
    }

    /** A match's events in time order, mistakes removed in review left out. */
    @Query("SELECT * FROM events WHERE match_id = :matchId AND status != 'DELETED' ORDER BY recorded_at")
    fun eventsOf(matchId: String): Flow<List<MatchEvent>>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    fun match(matchId: String): Flow<Match?>

    /** Matches on the phone: planned ones first, then newest first. */
    @Query(
        """
        SELECT matchId, homeTeam, awayTeam, startedAt, toReview, recordings, players, homeGoals, awayGoals FROM (
            SELECT m.id AS matchId, m.home_team AS homeTeam, m.away_team AS awayTeam, m.planned_at AS plannedAt,
                COALESCE((SELECT MIN(recorded_at) FROM events e WHERE e.match_id = m.id), m.played_at) AS startedAt,
                (SELECT COUNT(*) FROM events e WHERE e.match_id = m.id AND e.status = 'TO_REVIEW') AS toReview,
                (SELECT COUNT(*) FROM events e
                    WHERE e.match_id = m.id AND e.audio_file_ref IS NOT NULL AND e.status != 'DELETED') AS recordings,
                (SELECT COUNT(*) FROM lineup_players p WHERE p.match_id = m.id) AS players,
                (SELECT COUNT(*) FROM events g WHERE g.match_id = m.id AND g.team = 'HOME' AND $COUNTED_GOAL) AS homeGoals,
                (SELECT COUNT(*) FROM events g WHERE g.match_id = m.id AND g.team = 'AWAY' AND $COUNTED_GOAL) AS awayGoals
            FROM matches m
        )
        ORDER BY startedAt IS NOT NULL, COALESCE(startedAt, plannedAt) DESC
        """,
    )
    fun matches(): Flow<List<MatchListItem>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun event(id: String): MatchEvent?

    /** Saves what the edit screen changes. */
    @Query(
        """
        UPDATE events SET type = :type, team = :team, raw_transcript = :note, linked_goal_event_id = :linkedGoalId,
            player_name = :player, assist_name = :assist, minute = :minute, status = :status
        WHERE id = :id
        """,
    )
    suspend fun updateReview(
        id: String,
        type: EventType?,
        team: Team?,
        note: String?,
        linkedGoalId: String?,
        player: String?,
        assist: String?,
        minute: Int?,
        status: ReviewStatus,
    )

    /** Removes an event added by hand and left empty; nothing from the watch is removed like this. */
    @Query("DELETE FROM events WHERE id = :id AND recorded_at IS NULL AND audio_file_ref IS NULL")
    suspend fun deleteAddedEvent(id: String)

    @Query("UPDATE events SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: ReviewStatus)

    /** Deleted recordings whose audio file is still on the phone (kept while "Cofnij" is offered). */
    @Query("SELECT * FROM events WHERE status = 'DELETED' AND audio_file_ref IS NOT NULL")
    suspend fun deletedWithAudio(): List<MatchEvent>

    @Query("UPDATE events SET audio_file_ref = NULL WHERE id = :id")
    suspend fun clearAudio(id: String)

    /** Audio files of a match still on the phone, removed with it by [deleteMatch]. */
    @Query("SELECT audio_file_ref FROM events WHERE match_id = :matchId AND audio_file_ref IS NOT NULL")
    suspend fun audioFilesOf(matchId: String): List<String>

    @Query("DELETE FROM events WHERE match_id = :matchId")
    suspend fun deleteEventsOf(matchId: String)

    @Query("DELETE FROM lineup_players WHERE match_id = :matchId")
    suspend fun deleteLineupOf(matchId: String)

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatchRow(matchId: String)

    /** Removes a match with its events and lineups from the phone, all or nothing. */
    @Transaction
    suspend fun deleteMatch(matchId: String) {
        deleteEventsOf(matchId)
        deleteLineupOf(matchId)
        deleteMatchRow(matchId)
    }

    /** Events on the phone, mistakes removed in review not counted. */
    @Query("SELECT COUNT(*) FROM events WHERE status != 'DELETED'")
    fun count(): Flow<Int>
}

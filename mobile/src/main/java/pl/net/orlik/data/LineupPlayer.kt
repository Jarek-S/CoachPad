package pl.net.orlik.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A player in one team's lineup for one match (design doc, section 11: lineup per match). */
@Entity(
    tableName = "lineup_players",
    indices = [Index("match_id")],
)
data class LineupPlayer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "match_id")
    val matchId: String,

    val team: Team,

    /** Shirt number; null when the player has none. */
    val number: Int?,

    val name: String,
)

@Dao
interface LineupDao {

    /** Both teams of a match, in no particular order (sorted for display by [pl.net.orlik.review.lineupOrder]). */
    @Query("SELECT * FROM lineup_players WHERE match_id = :matchId")
    fun lineupOf(matchId: String): Flow<List<LineupPlayer>>

    @Insert
    suspend fun insert(player: LineupPlayer)

    @Query("UPDATE lineup_players SET number = :number, name = :name WHERE id = :id")
    suspend fun update(id: Long, number: Int?, name: String)

    @Query("DELETE FROM lineup_players WHERE id = :id")
    suspend fun delete(id: Long)

    /** One column at a time, so saving while typing never overwrites the other team's name. Null clears it. */
    @Query("UPDATE matches SET home_team = :name WHERE id = :matchId")
    suspend fun setHomeTeam(matchId: String, name: String?)

    @Query("UPDATE matches SET away_team = :name WHERE id = :matchId")
    suspend fun setAwayTeam(matchId: String, name: String?)
}

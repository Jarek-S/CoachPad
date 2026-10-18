package pl.net.orlik.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes a synced match from the phone: its events, lineups and recordings (design doc, section
 * 11). The watch removed these events after their ACK, so a later sync does not bring them back;
 * only events of the match still waiting on the watch would arrive as the match again.
 */
suspend fun deleteMatch(dao: MatchEventDao, recordingsDir: File, matchId: String) {
    val files = dao.audioFilesOf(matchId)
    dao.deleteMatch(matchId)
    withContext(Dispatchers.IO) { files.forEach { File(recordingsDir, it).delete() } }
}

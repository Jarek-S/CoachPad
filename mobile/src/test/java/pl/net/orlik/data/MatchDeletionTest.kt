package pl.net.orlik.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchDeletionTest {

    private val dao = FakeDao()
    private val recordingsDir: File = Files.createTempDirectory("recordings").toFile()

    private fun recording(id: String, matchId: String) {
        File(recordingsDir, "$id.m4a").writeText("audio")
        dao.events += MatchEvent(id, matchId, 0, null, "$id.m4a", 0)
    }

    @Test
    fun deleteMatch_removesItsEventsAndRecordingsOnly() = runBlocking {
        dao.matches += Match("m1")
        dao.matches += Match("m2")
        recording("a", "m1")
        recording("b", "m2")
        dao.events += MatchEvent("start", "m1", 0, null, null, 0)

        deleteMatch(dao, recordingsDir, "m1")

        assertEquals(listOf("b"), dao.events.map { it.id })
        assertEquals(listOf("m2"), dao.matches.map { it.id })
        assertFalse(File(recordingsDir, "a.m4a").exists())
        assertTrue(File(recordingsDir, "b.m4a").exists())
    }
}

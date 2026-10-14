package pl.net.orlik.log

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileEventLogTest {

    private val dir: File = Files.createTempDirectory("log").toFile()
    private val file = File(dir, "log/field-log.txt")

    @Test
    fun appendsTimestampedLines() {
        val log = FileEventLog(file, clock = { 0 })
        log.log("first")
        log.log("second")
        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].endsWith(" second"))
        assertTrue(lines[0].matches(Regex("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3} first""")))
    }

    @Test
    fun rotatesWhenTooBig_keepingOneOlderFile() {
        val log = FileEventLog(file, maxBytes = 100, clock = { 0 })
        repeat(30) { log.log("line $it") }
        val older = File(file.path + ".1")
        assertTrue(older.isFile)
        assertTrue(file.length() <= 100 + 40)
        // Nothing beyond the current file and one older one
        assertEquals(setOf("field-log.txt", "field-log.txt.1"), file.parentFile!!.list()!!.toSet())
        assertTrue(file.readLines().last().endsWith("line 29"))
    }
}

package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MemoryLog")
class MemoryLogTest {
    private val lines = mutableListOf<String>()
    private var reads = 0

    private fun log(scenario: String = "Grand Concert") =
        MemoryLog(7, scenario, sample = {
            reads++
            MemorySample(rssAnonKb = 227208L + reads, javaKb = 19612, threads = 42)
        }, emit = { lines.add(it) })

    @Test
    @DisplayName("the line names the event, run, turn, phase, scenario, step and the three readings")
    fun lineFormat() {
        val line = MemoryLog.formatLine("turn", 3, 56, "SENIOR_LATE_APRIL", "Grand Concert", "training", MemorySample(227208, 19612, 42))
        assertEquals("[MEM] event=turn game=3 turn=56 phase=SENIOR_LATE_APRIL scenario=Grand_Concert step=training rssAnonKB=227208 javaKB=19612 threads=42", line)
    }

    @Test
    @DisplayName("missing values print as a dash so every line keeps the same fields")
    fun missingValues() {
        val line = MemoryLog.formatLine("game_start", 1, null, null, "", "  ", MemorySample(null, 0, 0))
        assertEquals("[MEM] event=game_start game=1 turn=- phase=- scenario=- step=- rssAnonKB=- javaKB=0 threads=0", line)
    }

    @Test
    @DisplayName("one line per turn: later main-screen ticks of the same turn stay silent and read nothing")
    fun oncePerTurn() {
        val memory = log()
        assertTrue(memory.turnEnd(12, "JUNIOR_EARLY_JULY", "training"))
        assertFalse(memory.turnEnd(12, "JUNIOR_EARLY_JULY", "rest"))
        assertFalse(memory.turnEnd(12, "JUNIOR_EARLY_JULY", null))
        assertTrue(memory.turnEnd(13, "JUNIOR_LATE_JULY", "race"))
        assertEquals(2, lines.size)
        assertEquals(2, reads)
        assertTrue(lines[0].startsWith("[MEM] event=turn game=7 turn=12 phase=JUNIOR_EARLY_JULY scenario=Grand_Concert step=training "))
        assertTrue(lines[1].startsWith("[MEM] event=turn game=7 turn=13 phase=JUNIOR_LATE_JULY scenario=Grand_Concert step=race "))
    }

    @Test
    @DisplayName("a run writes a start line and an end line that carries its last logged turn")
    fun startAndEnd() {
        val memory = log("URA Finale")
        memory.gameStart()
        memory.turnEnd(40, "CLASSIC_LATE_AUGUST", "training")
        memory.gameEnd()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("[MEM] event=game_start game=7 turn=- phase=- scenario=URA_Finale step=- rssAnonKB="))
        assertTrue(lines[2].startsWith("[MEM] event=game_end game=7 turn=40 phase=- scenario=URA_Finale step=- rssAnonKB="))
    }

    @Test
    @DisplayName("a dailies run without turns writes only its start and end lines")
    fun dailiesRun() {
        val memory = log("Team Trials")
        memory.gameStart()
        memory.gameEnd()
        assertEquals(listOf("game_start", "game_end"), lines.map { it.substringAfter("event=").substringBefore(' ') })
        assertTrue(lines[1].contains(" turn=- "))
    }

    @Test
    @DisplayName("run ids count up across runs")
    fun runIds() {
        val first = MemoryLog.nextGameId()
        assertEquals(first + 1, MemoryLog.nextGameId())
    }

    @Test
    @DisplayName("the default reading counts at least the calling thread")
    fun defaultReading() {
        assertTrue(MemoryLog.readMemory().threads >= 1)
    }

    @Test
    @DisplayName("RssAnon is read from the kernel's status text, tabs and padding included")
    fun rssAnonFromStatus() {
        val status =
            listOf(
                "Name:\t1.uma_auto_plus",
                "VmRSS:\t  549700 kB",
                "RssAnon:\t  412340 kB",
                "RssFile:\t  118220 kB",
                "RssShmem:\t   19140 kB",
                "Threads:\t108",
            ).joinToString("\n")
        assertEquals(412340L, MemoryLog.parseRssAnonKb(status))
    }

    @Test
    @DisplayName("no RssAnon line, or only a look-alike, reads as unknown, never as 0")
    fun rssAnonMissing() {
        assertNull(MemoryLog.parseRssAnonKb("Name:\tx\nVmRSS:\t 1000 kB\n"))
        assertNull(MemoryLog.parseRssAnonKb("XRssAnon:\t 5 kB\n"))
        assertTrue(MemoryLog.formatLine("turn", 1, 2, null, "URA Finale", null, MemorySample(null, 1, 1)).contains(" rssAnonKB=- "))
    }

    @Test
    @DisplayName("each line counts captures and memory GCs since the previous line, starting at 0 for a new run")
    fun captureAndGcCounts() {
        val totals = ArrayDeque(listOf(500L to 3, 530L to 3, 600L to 5))
        val out = mutableListOf<String>()
        val memory =
            MemoryLog(2, "Grand Concert", sample = {
                val (captures, gcRuns) = totals.removeFirst()
                MemorySample(400_000, 30_000, 50, captures, gcRuns)
            }, emit = { out.add(it) })
        memory.gameStart()
        memory.turnEnd(1, "JUNIOR_EARLY_JANUARY", "training")
        memory.turnEnd(2, "JUNIOR_LATE_JANUARY", "training")
        assertEquals(listOf(" captures=0 gc=0", " captures=30 gc=0", " captures=70 gc=2"), out.map { it.substring(it.indexOf(" captures=")) })
    }
}

package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("GrandConcertCycle")
class GrandConcertCycleTest {
    @Test
    fun `songs bought on the first visit after a concert count for the new cycle`() {
        // El Condor Pasa, 2026-11-04 log: after the turn-24 concert a visit on the stale turn-24 date bought two songs that the
        // old counter credited to the ended cycle, so the turn-36 concert was entered as "2 new songs" with four bought.
        val cycle = GrandConcertCycle()
        repeat(2) { cycle.noteSongBought() }
        cycle.noteTurnAdvanced(24)
        cycle.noteConcertPerformed(24)
        cycle.syncToDate(24)
        repeat(2) { cycle.noteSongBought() }
        cycle.noteDateRead()
        cycle.noteTurnAdvanced(30)
        cycle.syncToDate(30)
        cycle.noteSongBought()
        cycle.noteDateRead()
        cycle.noteTurnAdvanced(34)
        cycle.syncToDate(34)
        cycle.noteSongBought()
        assertEquals(4, cycle.songsThisCycle, "the turn-36 concert is entered with all four")
    }

    @Test
    fun `the concert turn's visit counts down to zero`() {
        val cycle = GrandConcertCycle()
        cycle.noteDateRead()
        cycle.noteTurnAdvanced(35)
        val firstVisit = cycle.visitTurn(35)
        assertEquals(36, firstVisit, "the hook runs before turn 36's date read")
        assertEquals(0, GrandConcertCycle.turnsUntilConcert(firstVisit))
        cycle.noteDateRead()
        assertEquals(36, cycle.visitTurn(36))
        assertEquals(1, GrandConcertCycle.turnsUntilConcert(35))
        assertEquals(11, GrandConcertCycle.turnsUntilConcert(37))
        assertNull(GrandConcertCycle.turnsUntilConcert(73))
    }

    @Test
    fun `only a performed concert or a date past it ends the cycle`() {
        val cycle = GrandConcertCycle()
        cycle.syncToDate(30)
        cycle.noteSongBought()
        cycle.noteTurnAdvanced(35)
        assertEquals(36, cycle.visitTurn(35))
        assertNull(cycle.syncToDate(35), "an inferred turn never resets: only the date read or the escort does")
        assertEquals(1, cycle.songsThisCycle)

        assertEquals(36, cycle.noteConcertPerformed(35), "the pending screen still shows the turn before the concert")
        assertNull(cycle.noteConcertPerformed(36), "a second escort exit for the same concert keeps the new cycle's songs")
        assertNull(cycle.syncToDate(37))
        assertEquals(37, cycle.visitTurn(36), "a performed concert proves the turn after it")

        cycle.noteSongBought()
        assertEquals(48, cycle.syncToDate(49), "an escort that never saw the concert finish is caught by the date")
        assertEquals(0, cycle.songsThisCycle)
        assertEquals(60, cycle.noteConcertPerformed(null), "an unread date falls back to the next concert")
        assertEquals(1, cycle.visitTurn(1), "an unread date stays unread")
    }

    @Test
    fun `a race day on the prep screen moves the turn on from the cycle's own estimate`() {
        // Turn 34 trains; turn 35 is a race day handled on the prep screen, so no Main screen date read happens in between.
        val withRaceListRead = GrandConcertCycle()
        withRaceListRead.noteDateRead()
        withRaceListRead.trackStep(34, { it }) { true }
        withRaceListRead.trackStep(34, { it }) {
            withRaceListRead.noteDateRead() // the race list reads turn 35 before the race runs
            true
        }
        assertEquals(36, withRaceListRead.visitTurn(35))
        assertEquals(0, GrandConcertCycle.turnsUntilConcert(withRaceListRead.visitTurn(35)))

        val withoutRead = GrandConcertCycle()
        withoutRead.noteDateRead()
        withoutRead.trackStep(34, { it }) { true }
        withoutRead.trackStep(34, { it }) { true }
        assertEquals(36, withoutRead.visitTurn(34))
    }

    @Test
    fun `nested or failed steps never move the turn twice`() {
        val cycle = GrandConcertCycle()
        cycle.noteDateRead()
        cycle.trackStep(35, { it }) {
            cycle.trackStep(35, { it }) { true } // a rest inside a training step
            true
        }
        assertEquals(36, cycle.visitTurn(35))
        cycle.noteDateRead()
        assertEquals(false, cycle.trackStep(36, { it }) { false })
        assertEquals(36, cycle.visitTurn(36), "a step that reports no move leaves the turn")
    }

    @Test
    fun `without an advanced turn the visit stays on the date read`() {
        val cycle = GrandConcertCycle()
        cycle.noteDateRead()
        assertEquals(35, cycle.visitTurn(35), "an action that did not move the turn")
        cycle.noteTurnAdvanced(35)
        cycle.noteDateRead()
        assertEquals(35, cycle.visitTurn(35), "a date read after the action shows where the turn really is")
    }

    @Test
    fun `the campaign drives the cycle from the escort, the turn-advancing steps and the date read`() {
        val campaign = source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/campaigns/GrandConcert.kt")
        val escort = campaign.indexOf("if (runConcertEscort()) {")
        assertTrue(escort > 0)
        assertTrue(campaign.substring(escort, campaign.indexOf("return true", escort)).contains("cycle.noteConcertPerformed("))
        // Every step whose result says the turn moved marks it, also outside executeAction (a race day, a mood recovery).
        for (step in listOf("handleRaceEvents(", "handleRaceEventFallback(", "recoverEnergy(", "recoverMood(", "handleRecreationDate(", "executeAction(")) {
            val body = declaration(campaign, "override fun $step")
            assertTrue(body.contains("cycle.trackStep(date.day, "), "$step does not mark the turn from the cycle's estimate")
        }
        // An executed action marks only TRAIN; a failed race, rest, recovery or outing marks through its own result.
        assertTrue(declaration(campaign, "override fun executeAction(").contains("action == MainScreenAction.TRAIN"))
        assertTrue(declaration(campaign, "override fun handleRaceEventFallback(").contains("it.turnAdvanced"))
        assertTrue(method(campaign, "override fun updateDate(").contains("cycle.noteDateRead()"))
        assertTrue(campaign.contains("Entering the concert with \${cycle.songsThisCycle} new song(s) this cycle "), "log parsers match this line")
    }

    @Test
    fun `the spend loop protects the stage songs, not a remembered song`() {
        val campaign = source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/campaigns/GrandConcert.kt")
        val spend = method(campaign, "private fun spendVisit(")
        assertTrue(spend.contains("GrandConcertPolicy.nextSongCosts(list, stage, purchasedSongTitles)"))
        assertTrue(spend.contains("nextSongCosts = nextSongCosts"))
        assertFalse(spend.contains("lastSongTargetCost"), "a song from an earlier trio is gone once a technique trio shows")
        assertFalse(spend.contains("cheapestUnpurchasedInStage"), "the reserve and the preference read the same demand")
    }

    /** One declaration: up to the blank line that follows it. */
    private fun declaration(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue(start > 0, "missing $signature")
        return source.substring(start, source.indexOf("\n\n", start))
    }

    private fun method(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue(start > 0, "missing $signature")
        return source.substring(start, source.indexOf("\n    }\n", start))
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val file = File(dir, relative)
            if (file.isFile) return file.readText().replace("\r\n", "\n")
            dir = dir.parentFile
        }
        error("source file not found: $relative")
    }
}

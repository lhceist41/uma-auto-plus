package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The pure Grand Concert fan-first race selector: it ranks an already-collected candidate set of
 * visible rows with no dependence on the row prediction star, using fans first and aptitude only as a
 * soft tie-break, and never skips a required fan race over an OCR miss. The forced-race decision itself
 * is unchanged; these tests cover the ranking, the activation predicate, the fail-open fallback, and
 * the Copano turn-14 counterfactual.
 */
@DisplayName("Grand Concert fan-first race selection")
class GrandConcertFanRaceSelectorTest {
    /** Candidate is (fans: Int?, aptitudeCompatible: Boolean?, isRival). */
    private fun c(fans: Int?, apt: Boolean? = null, rival: Boolean = false) =
        GrandConcertFanRaceSelector.Candidate(fans, apt, rival)

    // ---- activation predicate ----

    @Test
    @DisplayName("applies only to a pure Grand Concert fan-pressure forced race")
    fun predicate() {
        fun p(gc: Boolean, fan: Boolean, trophy: Boolean, goalPts: Boolean) =
            GrandConcertFanRaceSelector.appliesToForcedRace(gc, fan, trophy, goalPts)
        assertTrue(p(gc = true, fan = true, trophy = false, goalPts = false)) // pure GC fan pressure
        assertFalse(p(gc = false, fan = true, trophy = false, goalPts = false)) // non-GC unchanged
        assertFalse(p(gc = true, fan = false, trophy = false, goalPts = false)) // no fan pressure
        assertFalse(p(gc = true, fan = true, trophy = true, goalPts = false)) // trophy requirement excluded
        assertFalse(p(gc = true, fan = true, trophy = false, goalPts = true)) // goal-points requirement excluded
    }

    // ---- ranking ----

    @Test
    @DisplayName("fans primary: a larger fan value wins even when the smaller race is aptitude-compatible")
    fun fansPrimary() {
        // Pins the Copano counterfactual: 3100 (aptitude false) beats 1600 (aptitude true).
        assertEquals(0, GrandConcertFanRaceSelector.select(listOf(c(3100, apt = false), c(1600, apt = true))).index)
    }

    @Test
    @DisplayName("aptitude breaks an exact fan tie")
    fun aptitudeSoftTie() {
        assertEquals(1, GrandConcertFanRaceSelector.select(listOf(c(3000, apt = false), c(3000, apt = true))).index)
    }

    @Test
    @DisplayName("aptitude never overrides a larger fan value")
    fun aptitudeDoesNotOverrideFans() {
        assertEquals(0, GrandConcertFanRaceSelector.select(listOf(c(5000, apt = false), c(3000, apt = true))).index)
    }

    @Test
    @DisplayName("a known fan value beats an unknown one")
    fun knownBeatsUnknown() {
        assertEquals(1, GrandConcertFanRaceSelector.select(listOf(c(null), c(1000))).index)
    }

    @Test
    @DisplayName("when every fan value is unknown, a deterministic required-race row is still chosen")
    fun allUnknownRequiredRaceFallback() {
        val sel = GrandConcertFanRaceSelector.select(listOf(c(null), c(null, rival = true)))
        assertEquals(0, sel.index) // index 0, never a legacy tier fallback
        assertEquals("all-fan-values-unknown-required-race-fallback", sel.reason)
    }

    @Test
    @DisplayName("Rival breaks only an exact fans-and-aptitude tie")
    fun rivalTieBreak() {
        assertEquals(1, GrandConcertFanRaceSelector.select(listOf(c(5000, apt = true), c(5000, apt = true, rival = true))).index)
    }

    @Test
    @DisplayName("the earliest index breaks a total tie")
    fun earliestFinalTie() {
        assertEquals(0, GrandConcertFanRaceSelector.select(listOf(c(5000, apt = true), c(5000, apt = true))).index)
    }

    @Test
    @DisplayName("markless rows (no tier is carried) rank purely by fans: row 1 with 3100 wins")
    fun marklessRowsPickFans() {
        // Even though on a live GC list row 2 carries a false SINGLE aptitude star, no tier reaches the
        // candidate, so 3100 wins over 1600 regardless of any star telemetry.
        assertEquals(0, GrandConcertFanRaceSelector.select(listOf(c(3100, apt = false), c(1600, apt = false))).index)
    }

    @Test
    @DisplayName("an empty candidate set returns no winner")
    fun empty() {
        assertEquals(-1, GrandConcertFanRaceSelector.select(emptyList()).index)
    }

    // ---- telemetry ----

    @Test
    @DisplayName("the GC_FAN_RACE_SELECT line records fans, aptitude, the winner, scope, and tier-ignored")
    fun telemetry() {
        val races = listOf(c(3100, apt = false), c(1600, apt = true))
        val sel = GrandConcertFanRaceSelector.select(races)
        val line = GrandConcertFanRaceSelector.telemetryLine(turn = 17, candidates = races, selection = sel, scanScope = "visible-page")
        assertTrue(line.contains("[GC_FAN_RACE_SELECT]"))
        assertTrue(line.contains("turn=17"))
        assertTrue(line.contains("scope=visible-page"))
        assertTrue(line.contains("winnerIdx=0"))
        assertTrue(line.contains("winnerFans=3100"))
        assertTrue(line.contains("winnerApt=N"))
        assertTrue(line.contains("tierIgnored=true"))
        assertTrue(line.contains("unknownFanFallback=false"))
    }

    // ---- source guards: null seam untouched, one gated GC seam, no tier in the GC contract ----

    @Test
    @DisplayName("the fan-deferral null seam is untouched and GC selection is a single predicate-gated seam")
    fun sourceGuards() {
        val pressure = source("bot/GrandConcertFanPressure.kt")
        assertTrue(pressure.contains("ReviewGatedPolicyInputs(null, null)"), "the fan-deferral seam must stay null")
        val racing = source("bot/Racing.kt")
        assertTrue(racing.contains("GrandConcertFanRaceSelector.appliesToForcedRace("), "GC selection must be predicate-gated")
        assertTrue(racing.contains("processGrandConcertForcedFanRace()"), "the dedicated pure-GC branch must exist")
        assertFalse(racing.contains("if (gcFanEfficient)"), "the old late tier-based GC branch must be removed")
        assertTrue(racing.contains("val index = legacyIndex()"), "the generic tail must use the legacy pick")
        val planner = source("bot/GrandConcertFanRaceScanPlanner.kt")
        assertFalse(planner.contains("PredictionTier"), "the GC planner must not carry a prediction tier")
        val selector = source("bot/GrandConcertFanRaceSelector.kt")
        assertFalse(selector.contains("PredictionTier"), "the GC selector must not depend on a prediction tier")
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")
            if (a.isFile) return a.readText()
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/$relative")
            if (b.isFile) return b.readText()
            dir = dir?.parentFile
        }
        error("could not locate $relative")
    }
}

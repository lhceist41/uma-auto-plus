package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The pure Grand Concert forced fan-race selector: it ranks an already-collected candidate set of
 * visible rows with no dependence on the row prediction star, known-first then aptitude-first then
 * fans-second, and never skips a required fan race over an OCR miss. The forced-race decision itself is
 * unchanged; these tests cover the ranking order, the activation predicate, the no-compatible-row
 * fail-open fallback, and the completed-career regret case.
 */
@DisplayName("Grand Concert forced fan-race selection")
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

    // ---- ranking: known status first ----

    private fun winner(vararg rows: GrandConcertFanRaceSelector.Candidate) =
        GrandConcertFanRaceSelector.select(rows.toList()).index

    @Test
    @DisplayName("a known fan value beats an unknown one, even when the unknown row looks aptitude-compatible")
    fun knownBeatsUnknownBeforeAptitude() {
        assertEquals(0, winner(c(1600, apt = false), c(null, apt = true))) // known incompatible beats unknown compatible
        assertEquals(0, winner(c(1000, apt = true), c(null, apt = false))) // known compatible beats unknown
        assertEquals(1, winner(c(null), c(1000))) // plain known beats unknown
    }

    @Test
    @DisplayName("when every fan value is unknown, a deterministic required-race row is still chosen (no aptitude sort)")
    fun allUnknownRequiredRaceFallback() {
        val sel = GrandConcertFanRaceSelector.select(listOf(c(null, apt = false), c(null, apt = true, rival = true)))
        assertEquals(0, sel.index) // index 0, never aptitude-sorted among untrusted rows
        assertEquals("all-fan-values-unknown-required-race-fallback", sel.reason)
    }

    // ---- ranking: aptitude-first among known rows ----

    @Test
    @DisplayName("a compatible lower-face race beats an incompatible higher-face race")
    fun compatibleLowerBeatsIncompatibleHigher() {
        assertEquals(1, winner(c(4200, apt = false), c(1600, apt = true))) // the core new contract
        assertEquals("aptitude-first", GrandConcertFanRaceSelector.select(listOf(c(4200, apt = false), c(1600, apt = true))).reason)
    }

    @Test
    @DisplayName("a compatible lower-face race beats an unknown-aptitude higher-face race")
    fun compatibleBeatsNullHigher() {
        assertEquals(1, winner(c(5000, apt = null), c(1200, apt = true)))
    }

    @Test
    @DisplayName("within the same aptitude class the larger face value wins")
    fun higherFansWithinAptitudeClass() {
        assertEquals(1, winner(c(1600, apt = true), c(3100, apt = true))) // compatible vs compatible
        assertEquals(1, winner(c(1600, apt = false), c(3100, apt = false))) // incompatible vs incompatible
        assertEquals(1, winner(c(1600, apt = null), c(3100, apt = false))) // null vs false: both not-compatible -> fans
    }

    // ---- ranking: Rival and index tie-breaks ----

    @Test
    @DisplayName("Rival breaks only an exact fans-and-aptitude-class tie")
    fun rivalTieBreak() {
        assertEquals(1, winner(c(1600, apt = true, rival = false), c(1600, apt = true, rival = true)))
    }

    @Test
    @DisplayName("aptitude class outranks Rival")
    fun aptitudeBeatsRival() {
        assertEquals(0, winner(c(1600, apt = true, rival = false), c(1600, apt = false, rival = true)))
    }

    @Test
    @DisplayName("a larger face value outranks Rival when the aptitude class ties")
    fun higherFansBeatsRival() {
        assertEquals(0, winner(c(3100, apt = true, rival = false), c(1600, apt = true, rival = true)))
    }

    @Test
    @DisplayName("the earliest index breaks a total tie")
    fun earliestFinalTie() {
        assertEquals(0, winner(c(5000, apt = true), c(5000, apt = true)))
    }

    // ---- fail-open: no compatible known row reproduces the prior fans-first winner ----

    @Test
    @DisplayName("with no aptitude-compatible known row, ranking is exactly the prior fans-first behavior")
    fun failOpenMatchesFansFirst() {
        assertEquals(0, winner(c(4200, apt = false), c(1600, apt = false))) // higher fans, both incompatible
        assertEquals(0, winner(c(4200, apt = null), c(1600, apt = null))) // higher fans, both unknown-aptitude
        // reason stays higher-fans (aptitude did not override anything).
        assertEquals("higher-fans", GrandConcertFanRaceSelector.select(listOf(c(4200, apt = false), c(1600, apt = false))).reason)
    }

    // ---- the completed-career regret case ----

    @Test
    @DisplayName("a high-face race the trainee is unsuited for loses to a lower-face race she is suited for")
    fun compatibleLowerFaceBeatsIncompatibleHighFace() {
        // A high-face turf race (aptitude false) versus a lower-face dirt race (aptitude true): the
        // suited race wins, because the unsuited high-face race would finish near the back and realize
        // almost none of its face fans.
        assertEquals(1, winner(c(3800, apt = false), c(1600, apt = true)))
    }

    @Test
    @DisplayName("an empty candidate set returns no winner")
    fun empty() {
        assertEquals(-1, GrandConcertFanRaceSelector.select(emptyList()).index)
    }

    // ---- telemetry ----

    @Test
    @DisplayName("the GC_FAN_RACE_SELECT line makes an aptitude-over-fans override auditable")
    fun telemetry() {
        // A compatible 1600 beats an incompatible 3100: the line shows the lower-face compatible winner
        // next to the higher-face incompatible row, so the override is auditable.
        val races = listOf(c(3100, apt = false), c(1600, apt = true))
        val sel = GrandConcertFanRaceSelector.select(races)
        val line = GrandConcertFanRaceSelector.telemetryLine(turn = 17, candidates = races, selection = sel, scanScope = "visible-page")
        assertTrue(line.contains("[GC_FAN_RACE_SELECT]"))
        assertTrue(line.contains("turn=17"))
        assertTrue(line.contains("scope=visible-page"))
        assertTrue(line.contains("winnerIdx=1"))
        assertTrue(line.contains("winnerFans=1600"))
        assertTrue(line.contains("winnerApt=Y"))
        assertTrue(line.contains("reason=aptitude-first"))
        assertTrue(line.contains("3100/aptN")) // the higher-face incompatible row is visible in the summary
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

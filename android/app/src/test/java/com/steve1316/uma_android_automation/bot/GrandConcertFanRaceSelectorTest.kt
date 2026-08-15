package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.indexOfBestByTierThenFans
import com.steve1316.uma_android_automation.types.PredictionTier
import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The pure Grand Concert fan-efficient race selector: it ranks an already-collected candidate set,
 * demoting Rival to a tie-break while preserving tier-first safety, and never invents an
 * expected-placement estimate. The forced-race decision itself is unchanged; these tests cover the
 * ranking, the activation predicate, and the fail-safe fallback.
 */
@DisplayName("Grand Concert fan-efficient race selection")
class GrandConcertFanRaceSelectorTest {
    /** RaceDetails is (fans, hasDoublePredictions, isRival, predictionTier). */
    private fun d(fans: Int, tier: PredictionTier = PredictionTier.DOUBLE, rival: Boolean = false) =
        RaceDetails(fans, tier == PredictionTier.DOUBLE, rival, tier)

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
    @DisplayName("Rival is demoted: a larger same-tier non-Rival race beats a Rival race")
    fun rivalDemoted() {
        // Legacy would discard all non-Rival rows and pick the Rival 5000; the GC selector picks 10000.
        val races = listOf(d(1200), d(5000, rival = true), d(10000))
        val legacy = if (races.any { it.isRival }) races.indices.filter { races[it].isRival }.let { r -> r[indexOfBestByTierThenFans(r.map { races[it] })] } else indexOfBestByTierThenFans(races)
        assertEquals(1, legacy) // legacy picks the Rival 5000
        assertEquals(2, GrandConcertFanRaceSelector.select(races).index) // GC picks the 10000 non-Rival
    }

    @Test
    @DisplayName("tier stays the primary safety priority: a DOUBLE beats a much larger SINGLE")
    fun tierFirst() {
        val races = listOf(d(15000, PredictionTier.SINGLE), d(3000, PredictionTier.DOUBLE))
        assertEquals(1, GrandConcertFanRaceSelector.select(races).index)
    }

    @Test
    @DisplayName("within a tier, the highest known fan value wins")
    fun fansWithinTier() {
        assertEquals(2, GrandConcertFanRaceSelector.select(listOf(d(1000), d(1500), d(5000))).index)
    }

    @Test
    @DisplayName("an unknown fan value never outranks a known one in the same tier")
    fun unknownLosesToKnown() {
        val sel = GrandConcertFanRaceSelector.select(listOf(d(-1), d(5000)))
        assertEquals(1, sel.index)
        assertFalse(sel.useLegacyFallback)
    }

    @Test
    @DisplayName("when every fan value is unknown, defer to the legacy selection")
    fun allUnknownFallsBack() {
        val sel = GrandConcertFanRaceSelector.select(listOf(d(-1), d(-1, PredictionTier.SINGLE)))
        assertTrue(sel.useLegacyFallback)
        assertEquals(-1, sel.index)
    }

    @Test
    @DisplayName("Rival breaks only an exact tier-and-fans tie")
    fun rivalTieBreak() {
        // Two identical DOUBLE 5000 rows, the second is the Rival: it wins the exact tie.
        assertEquals(1, GrandConcertFanRaceSelector.select(listOf(d(5000), d(5000, rival = true))).index)
    }

    @Test
    @DisplayName("a candidate set collected across pages ranks correctly (full-set ranking, scan deferred)")
    fun crossPageRanking() {
        // As if page 0 yielded [1000, 1500] and page 1 yielded [5000]; the selector ranks the union.
        assertEquals(2, GrandConcertFanRaceSelector.select(listOf(d(1000), d(1500), d(5000))).index)
    }

    // ---- telemetry ----

    @Test
    @DisplayName("the GC_FAN_RACE_SELECT line records candidates, the winner, and the scan scope")
    fun telemetry() {
        val races = listOf(d(1200), d(5000, rival = true), d(10000))
        val sel = GrandConcertFanRaceSelector.select(races)
        val line = GrandConcertFanRaceSelector.telemetryLine(turn = 17, candidates = races, selection = sel, scanScope = "visible-page")
        assertTrue(line.contains("[GC_FAN_RACE_SELECT]"))
        assertTrue(line.contains("turn=17"))
        assertTrue(line.contains("scope=visible-page"))
        assertTrue(line.contains("winnerIdx=2"))
        assertTrue(line.contains("winnerFans=10000"))
        assertTrue(line.contains("winnerRival=false"))
        assertTrue(line.contains("legacyFallback=false"))
    }

    // ---- source guards: null-seam untouched, activation is predicate-gated, full scan not wired ----

    @Test
    @DisplayName("the fan-deferral null seam is untouched and the selector is used only under the pure-fan predicate")
    fun sourceGuards() {
        val pressure = source("bot/GrandConcertFanPressure.kt")
        assertTrue(pressure.contains("ReviewGatedPolicyInputs(null, null)"), "the fan-deferral seam must stay null")
        val racing = source("bot/Racing.kt")
        assertTrue(racing.contains("GrandConcertFanRaceSelector.appliesToForcedRace("), "selection must be gated by the pure-fan predicate")
        assertTrue(racing.contains("if (gcFanEfficient)"), "the fan-efficient branch must be predicate-gated")
        assertTrue(racing.contains("legacyIndex()"), "a legacy fallback path must remain")
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

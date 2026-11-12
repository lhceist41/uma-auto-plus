package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The pure Grand Concert forced fan-race selector: it ranks an already-collected candidate set of
 * visible rows with no dependence on the row prediction star, known-first then aptitude tier then
 * fans, and never skips a required fan race over an OCR miss. A and E stand for "suited" and "unsuited"
 * where only the tier order matters.
 */
@DisplayName("Grand Concert forced fan-race selection")
class GrandConcertFanRaceSelectorTest {
    /** Candidate is (fans: Int?, worstAptitude: Aptitude?, isRival). */
    private fun c(fans: Int?, apt: Aptitude? = null, rival: Boolean = false) =
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
        assertEquals(0, winner(c(1600, apt = Aptitude.E), c(null, apt = Aptitude.A))) // known incompatible beats unknown compatible
        assertEquals(0, winner(c(1000, apt = Aptitude.A), c(null, apt = Aptitude.E))) // known compatible beats unknown
        assertEquals(1, winner(c(null), c(1000))) // plain known beats unknown
    }

    @Test
    @DisplayName("when every fan value is unknown, a deterministic required-race row is still chosen (no aptitude sort)")
    fun allUnknownRequiredRaceFallback() {
        val sel = GrandConcertFanRaceSelector.select(listOf(c(null, apt = Aptitude.E), c(null, apt = Aptitude.A, rival = true)))
        assertEquals(0, sel.index) // index 0, never aptitude-sorted among untrusted rows
        assertEquals("all-fan-values-unknown-required-race-fallback", sel.reason)
    }

    // ---- ranking: aptitude-first among known rows ----

    @Test
    @DisplayName("a compatible lower-face race beats an incompatible higher-face race")
    fun compatibleLowerBeatsIncompatibleHigher() {
        assertEquals(1, winner(c(4200, apt = Aptitude.E), c(1600, apt = Aptitude.A))) // the core new contract
        assertEquals("aptitude-first", GrandConcertFanRaceSelector.select(listOf(c(4200, apt = Aptitude.E), c(1600, apt = Aptitude.A))).reason)
    }

    @Test
    @DisplayName("a compatible lower-face race beats an unknown-aptitude higher-face race")
    fun compatibleBeatsNullHigher() {
        assertEquals(1, winner(c(5000, apt = null), c(1200, apt = Aptitude.A)))
    }

    @Test
    @DisplayName("within the same aptitude class the larger face value wins")
    fun higherFansWithinAptitudeClass() {
        assertEquals(1, winner(c(1600, apt = Aptitude.A), c(3100, apt = Aptitude.A))) // compatible vs compatible
        assertEquals(1, winner(c(1600, apt = Aptitude.E), c(3100, apt = Aptitude.E))) // incompatible vs incompatible
        assertEquals(1, winner(c(1600, apt = null), c(3100, apt = Aptitude.E))) // null vs false: both not-compatible -> fans
    }

    // ---- ranking: Rival and index tie-breaks ----

    @Test
    @DisplayName("Rival breaks only an exact fans-and-aptitude-class tie")
    fun rivalTieBreak() {
        assertEquals(1, winner(c(1600, apt = Aptitude.A, rival = false), c(1600, apt = Aptitude.A, rival = true)))
    }

    @Test
    @DisplayName("aptitude class outranks Rival")
    fun aptitudeBeatsRival() {
        assertEquals(0, winner(c(1600, apt = Aptitude.A, rival = false), c(1600, apt = Aptitude.E, rival = true)))
    }

    @Test
    @DisplayName("a larger face value outranks Rival when the aptitude class ties")
    fun higherFansBeatsRival() {
        assertEquals(0, winner(c(3100, apt = Aptitude.A, rival = false), c(1600, apt = Aptitude.A, rival = true)))
    }

    @Test
    @DisplayName("the earliest index breaks a total tie")
    fun earliestFinalTie() {
        assertEquals(0, winner(c(5000, apt = Aptitude.A), c(5000, apt = Aptitude.A)))
    }

    // ---- fail-open: no compatible known row reproduces the prior fans-first winner ----

    @Test
    @DisplayName("with no aptitude-compatible known row, ranking is exactly the prior fans-first behavior")
    fun failOpenMatchesFansFirst() {
        assertEquals(0, winner(c(4200, apt = Aptitude.E), c(1600, apt = Aptitude.E))) // higher fans, both incompatible
        assertEquals(0, winner(c(4200, apt = null), c(1600, apt = null))) // higher fans, both unknown-aptitude
        // reason stays higher-fans (aptitude did not override anything).
        assertEquals("higher-fans", GrandConcertFanRaceSelector.select(listOf(c(4200, apt = Aptitude.E), c(1600, apt = Aptitude.E))).reason)
    }

    // ---- the completed-career regret case ----

    @Test
    @DisplayName("a high-face race the trainee is unsuited for loses to a lower-face race she is suited for")
    fun compatibleLowerFaceBeatsIncompatibleHighFace() {
        // A high-face turf race (aptitude false) versus a lower-face dirt race (aptitude true): the
        // suited race wins, because the unsuited high-face race would finish near the back and realize
        // almost none of its face fans.
        assertEquals(1, winner(c(3800, apt = Aptitude.E), c(1600, apt = Aptitude.A)))
    }

    @Test
    @DisplayName("an empty candidate set returns no winner")
    fun empty() {
        assertEquals(-1, GrandConcertFanRaceSelector.select(emptyList()).index)
    }

    // ---- graded aptitude ----

    @Test
    @DisplayName("graded: C beats E at a lower face value, B beats C, S and A tie on tier and fans decide")
    fun gradedTiers() {
        assertEquals(1, winner(c(3100, Aptitude.E), c(1600, Aptitude.C)))
        assertEquals("aptitude-first", GrandConcertFanRaceSelector.select(listOf(c(3100, Aptitude.E), c(1600, Aptitude.C))).reason)
        assertEquals(0, winner(c(3300, Aptitude.B), c(7000, Aptitude.C)))
        assertEquals(1, winner(c(1600, Aptitude.A), c(3100, Aptitude.S)))
        assertEquals("higher-fans", GrandConcertFanRaceSelector.select(listOf(c(1600, Aptitude.A), c(3100, Aptitude.S))).reason)
        assertEquals(0, winner(c(1600, Aptitude.D), c(9000, Aptitude.G)))
        assertEquals(0, winner(c(1600, Aptitude.G), c(9000, null))) // an untrusted aptitude ranks below G
    }

    @Test
    @DisplayName("graded: known fans always beat unknown fans whatever the tier, all unknown still picks index 0, Rival breaks a full tie")
    fun gradedFallbacks() {
        assertEquals(1, winner(c(null, Aptitude.S), c(1600, Aptitude.D)))
        assertEquals(0, GrandConcertFanRaceSelector.select(listOf(c(null, Aptitude.E), c(null, Aptitude.S))).index)
        assertEquals(1, winner(c(1600, Aptitude.C), c(1600, Aptitude.C, rival = true)))
    }

    @Test
    @DisplayName("reporter's turn 14: Kitasan Black (Sprint E, Mile C) takes the 1,600 Mile row, not the 3,100 Sprint row the old pick took")
    fun reporterTurn14() {
        val log = fixture("fixtures/gcfan/kitasan_turn14.txt").lines()
        val select = log.single { it.contains("[GC_FAN_RACE_SELECT]") }
        assertTrue(select.contains("winnerIdx=0 winnerFans=3100"), "the old pick: $select")
        val fans = Regex("\\[(\\d+)/apt\\w (\\d+)/apt\\w]").find(select)!!.groupValues.drop(1).map { it.toInt() }
        val races = JSONObject(repoText("src/data/races.json"))
        val turn14 = races.keys().asSequence().map { races.getJSONObject(it) }.filter { it.getInt("turnNumber") == 14 }.toList()
        val kitasan = mapOf("Turf" to Aptitude.A, "Dirt" to Aptitude.G, "Sprint" to Aptitude.E, "Mile" to Aptitude.C, "Medium" to Aptitude.A, "Long" to Aptitude.A)
        val rows =
            fans.map { f ->
                val race = turn14.single { it.getInt("fans") == f }
                c(f, minOf(kitasan.getValue(race.getString("terrain")), kitasan.getValue(race.getString("distanceType"))))
            }
        assertEquals(listOf(Aptitude.E, Aptitude.C), rows.map { it.worstAptitude })
        val selection = GrandConcertFanRaceSelector.select(rows)
        assertEquals(1, selection.index)
        assertEquals("aptitude-first", selection.reason)
        assertTrue(GrandConcertFanRaceSelector.telemetryLine(14, rows, selection, "visible-page").contains("[3100/aptE 1600/aptC] winnerIdx=1 winnerFans=1600 winnerApt=C"))
    }

    // ---- telemetry ----

    @Test
    @DisplayName("the GC_FAN_RACE_SELECT line makes an aptitude-over-fans override auditable")
    fun telemetry() {
        // A compatible 1600 beats an incompatible 3100: the line shows the lower-face compatible winner
        // next to the higher-face incompatible row, so the override is auditable.
        val races = listOf(c(3100, apt = Aptitude.E), c(1600, apt = Aptitude.A))
        val sel = GrandConcertFanRaceSelector.select(races)
        val line = GrandConcertFanRaceSelector.telemetryLine(turn = 17, candidates = races, selection = sel, scanScope = "visible-page")
        assertTrue(line.contains("[GC_FAN_RACE_SELECT]"))
        assertTrue(line.contains("turn=17"))
        assertTrue(line.contains("scope=visible-page"))
        assertTrue(line.contains("winnerIdx=1"))
        assertTrue(line.contains("winnerFans=1600"))
        assertTrue(line.contains("winnerApt=A"))
        assertTrue(line.contains("reason=aptitude-first"))
        assertTrue(line.contains("3100/aptE")) // the higher-face unsuited row is visible in the summary
        assertTrue(line.contains("tierIgnored=true"))
        assertTrue(line.contains("unknownFanFallback=false"))
    }

    // ---- source guards: selection independent of deferral, one gated GC seam, no tier in the GC contract ----

    @Test
    @DisplayName("GC selection is independent of the fan deferral and a single predicate-gated seam")
    fun sourceGuards() {
        assertFalse(source("bot/GrandConcertFanRaceSelector.kt").contains("policyInputs"), "race selection must not depend on the deferral inputs")
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

    private fun fixture(relative: String): String = repoText("android/app/src/test/resources/$relative")

    private fun repoText(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText()
            dir = dir?.parentFile
        }
        error("could not locate $relative")
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

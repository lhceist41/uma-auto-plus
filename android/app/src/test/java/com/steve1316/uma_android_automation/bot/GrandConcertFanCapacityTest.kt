package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.GrandConcertFanPolicy.FanRaceDecision
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.RaceRow
import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.RaceGrade
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Grand Concert capacity floor: per usable turn the best calendar race times a place share by aptitude, every unmet
 * goal and gate checked every turn, and the fail-closed cases. Kitasan Black's states come from a reporter's log and
 * her base-card aptitudes (Turf A, Dirt G, Sprint E, Mile C, Medium A, Long A).
 */
@DisplayName("Grand Concert fan capacity floor")
class GrandConcertFanCapacityTest {
    private val facts = GrandConcertFanFacts.parse(repoFile("android/app/src/main/assets/gc_fan_runtime.json").readText()) ?: error("shipped asset should parse")

    private val kitasan = mapOf("Turf" to Aptitude.A, "Dirt" to Aptitude.G, "Sprint" to Aptitude.E, "Mile" to Aptitude.C, "Medium" to Aptitude.A, "Long" to Aptitude.A)

    private data class Race(val name: String, val grade: RaceGrade, val fans: Int, val terrain: String, val distanceType: String)

    private val calendar: Map<Int, List<Race>> =
        JSONObject(repoFile("src/data/races.json").readText()).let { all ->
            all.keys().asSequence().map { all.getJSONObject(it) }.map {
                val grade =
                    when (val g = it.getString("grade")) {
                        "Pre-OP" -> RaceGrade.PRE_OP
                        "OP" -> RaceGrade.OP
                        else -> RaceGrade.valueOf(g)
                    }
                it.getInt("turnNumber") to Race(it.getString("name"), grade, it.getInt("fans"), it.getString("terrain"), it.getString("distanceType"))
            }.groupBy({ it.first }, { it.second })
        }

    private fun worst(race: Race) = minOf(kitasan.getValue(race.terrain), kitasan.getValue(race.distanceType))

    private val kitasanRows = calendar.mapValues { (_, races) -> races.map { RaceRow(it.grade, it.fans, worst(it)) } }

    private fun check(turn: Int, fans: Int, debutFans: Int?, rows: Map<Int, List<RaceRow>> = kitasanRows, name: String = "Kitasan Black") =
        GrandConcertFanPressure.capacityCheck(facts, name, turn, fans, rows, GrandConcertFanPressure.fanBonus(debutFans))

    private fun decide(check: GrandConcertFanPressure.CapacityCheck, low: Boolean = false) = GrandConcertFanPolicy.decide(true, check, low)

    @Test
    @DisplayName("the reporter's turn 14 with 1,301 fans trains: a proven Fan Bonus covers the 5,000 goal and the Arima gate")
    fun reporterStateDefers() {
        val c = check(14, 1301, debutFans = 1301)
        assertEquals(1301 / 700.0, c.fanBonus)
        assertEquals(0.40, GrandConcertFanPressure.placeShare(Aptitude.A, c.fanBonus)) // 3rd place x 1.86 capped at 2nd
        assertNull(c.failSafeReason)
        assertTrue(c.everyRequirementHasSlack, "binding ${c.binding}")
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(c))
    }

    @Test
    @DisplayName("without a proven Fan Bonus the same turn races, and picks the C Mile row over the E Sprint row")
    fun noBonusForcesAndPicksTheBetterGrade() {
        val c = check(14, 1301, debutFans = null)
        assertEquals(1.0, c.fanBonus)
        assertFalse(c.everyRequirementHasSlack)
        assertEquals(FanRaceDecision.FORCE_RACE, decide(c))
        val rows = calendar.getValue(14)
        val pick = GrandConcertFanRaceSelector.select(rows.map { GrandConcertFanRaceSelector.Candidate(it.fans, worst(it), false) })
        assertEquals("Chukyo Junior Stakes", rows[pick.index].name)
        assertEquals(Aptitude.C, worst(rows[pick.index]))
    }

    @Test
    @DisplayName("a met 5,000 goal re-arms for Arima at turn 31 with 6,000 fans, and stays off with 22,000")
    fun laterGateRearms() {
        val short = check(31, 6000, debutFans = 1120)
        val scope = GrandConcertFanRequirement.evaluate(facts, "Kitasan Black", 31, 6000)
        assertTrue(scope is GrandConcertFanRequirement.Result.Inactive, "the current scope (St. Lite 1,750) is met: $scope")
        val rearmed = GrandConcertFanRequirement.rearm(scope, short, payoutsBelowAssumption = false)
        assertTrue(rearmed is GrandConcertFanRequirement.Result.Active && rearmed.rearmed, "$rearmed")
        assertEquals(48, (rearmed as GrandConcertFanRequirement.Result.Active).requirementTurn)
        assertEquals(25000, rearmed.targetFans)
        assertEquals(FanRaceDecision.FORCE_RACE, decide(short))

        val roomy = check(31, 22000, debutFans = 1120)
        val roomyScope = GrandConcertFanRequirement.evaluate(facts, "Kitasan Black", 31, 22000)
        assertEquals(roomyScope, GrandConcertFanRequirement.rearm(roomyScope, roomy, payoutsBelowAssumption = false))
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(roomy))
    }

    @Test
    @DisplayName("an active current scope is never replaced by the re-arm, and a failed check never re-arms")
    fun rearmLeavesOtherResultsAlone() {
        val active = GrandConcertFanRequirement.evaluate(facts, "Kitasan Black", 14, 1301)
        assertTrue(active is GrandConcertFanRequirement.Result.Active)
        assertEquals(active, GrandConcertFanRequirement.rearm(active, check(14, 1301, null), false))
        val inactive = GrandConcertFanRequirement.evaluate(facts, "Kitasan Black", 31, 6000)
        val failed = GrandConcertFanPressure.CapacityCheck.failSafe(31, "aptitudes-unread", 1.0)
        assertEquals(inactive, GrandConcertFanRequirement.rearm(inactive, failed, payoutsBelowAssumption = true))
        val unknown = GrandConcertFanRequirement.Result.Unknown("trainee-unmatched")
        assertEquals(unknown, GrandConcertFanRequirement.rearm(unknown, check(31, 6000, 1120), true))
    }

    @Test
    @DisplayName("two payouts below the assumed capacity race every turn until one meets it")
    fun payoutGuard() {
        assertFalse(GrandConcertFanPressure.payoutsBelowAssumption(listOf(100 to 640.0)))
        assertTrue(GrandConcertFanPressure.payoutsBelowAssumption(listOf(100 to 640.0, 90 to 640.0)))
        assertFalse(GrandConcertFanPressure.payoutsBelowAssumption(listOf(100 to 640.0, 90 to 640.0, 700 to 640.0)))
        assertFalse(GrandConcertFanPressure.payoutsBelowAssumption(listOf(640 to 640.0, 90 to 640.0)))
        val roomy = check(31, 22000, debutFans = 1120)
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(roomy, low = true))
        // The guard re-arms a met scope too: the assumption it rests on failed.
        val scope = GrandConcertFanRequirement.evaluate(facts, "Kitasan Black", 31, 22000)
        assertTrue(GrandConcertFanRequirement.rearm(scope, roomy, payoutsBelowAssumption = true) is GrandConcertFanRequirement.Result.Active)
    }

    @Test
    @DisplayName("nothing to gain today: no C-or-better race now but a later turn has one defers; every window turn at 0 races")
    fun nothingToGainToday() {
        val noneToday = kitasanRows + (14 to listOf(RaceRow(RaceGrade.OP, 3100, Aptitude.E)))
        val c = check(14, 1301, debutFans = null, rows = noneToday)
        assertFalse(c.everyRequirementHasSlack)
        assertEquals(0.0, c.todayCapacity)
        assertTrue(c.laterTurnHasCapacity)
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(c))

        val allZero = kitasanRows.mapValues { (_, rows) -> rows.map { it.copy(worstAptitude = Aptitude.D) } }
        val z = check(14, 1301, debutFans = null, rows = allZero)
        assertFalse(z.laterTurnHasCapacity)
        assertEquals(FanRaceDecision.FORCE_RACE, decide(z))
    }

    @Test
    @DisplayName("unread aptitudes, no race data for the career or the window, a fan read of 0 or 1, an unknown trainee and a due goal race")
    fun failSafeCases() {
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(GrandConcertFanPressure.CapacityCheck.failSafe(14, "aptitudes-unread", 1.0)))
        assertEquals("race-data-empty", check(14, 1301, null, rows = emptyMap()).failSafeReason)
        assertEquals("race-data-window-empty", check(14, 1301, null, rows = mapOf(14 to kitasanRows.getValue(14))).failSafeReason)
        assertEquals("fans-unread", check(14, 1, null).failSafeReason)
        assertEquals("fans-unread", check(14, 0, null).failSafeReason)
        assertEquals("trainee-unmatched", check(14, 1301, null, name = "No Such Trainee").failSafeReason)
        assertEquals("trainee-unmatched", check(14, 1301, null, name = "").failSafeReason)
        assertEquals("data-asset-missing-or-malformed", GrandConcertFanPressure.capacityCheck(null, "Kitasan Black", 14, 1301, kitasanRows, 1.0).failSafeReason)
        assertEquals("requirement-due-or-overdue", check(30, 4000, null).failSafeReason)
        assertEquals("requirement-due-or-overdue", check(31, 4000, null).failSafeReason)
        for (reason in listOf("race-data-empty", "fans-unread", "trainee-unmatched")) {
            assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(GrandConcertFanPressure.CapacityCheck.failSafe(14, reason, 1.0)), reason)
        }
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(check(14, 1, null)))
    }

    /** Two names that collapse to one normalized key ("tmoperao"), and a gate at 49 with a met goal race at 44. */
    private val syntheticFacts =
        GrandConcertFanFacts.parse(
            """
            { "schemaVersion": 1, "universalCompletedRaceFanFloor": 7, "characters": {
              "TM Opera O": { "fanGoals": [], "mandatoryRaces": [] },
              "T.M. Opera O": { "fanGoals": [], "mandatoryRaces": [] },
              "Window Char": { "fanGoals": [], "mandatoryRaces": [
                { "turn": 44, "isChoice": false, "options": [{ "raceName": "Goal Race", "fansNeeded": 100 }] },
                { "turn": 49, "isChoice": false, "options": [{ "raceName": "Gate", "fansNeeded": 50000 }] }
              ] }
            } }
            """.trimIndent(),
        ) ?: error("synthetic facts should parse")

    @Test
    @DisplayName("Summer turns and the trainee's own goal race turns are never counted as fan-race turns")
    fun windowSkipsSummerAndGoalRaceTurns() {
        // Classic-year Summer is July Early to August Late, turns 37 to 40; the goal race is turn 44; the gate's window ends at 48.
        val summer = 37..40
        assertTrue(summer.all { GameDate.isSummer(it) } && !GameDate.isSummer(36) && !GameDate.isSummer(41))
        val goalRace = 44
        val excluded = summer.toSet() + goalRace
        // OP rows with an A aptitude pay 1,000 x 0.25 at a 1.0 bonus on usable turns; the excluded turns carry a huge payout that would dominate if counted.
        val rows = (31..48).associateWith { turn -> listOf(RaceRow(RaceGrade.OP, if (turn in excluded) 1_000_000 else 1000, Aptitude.A)) }
        val c = GrandConcertFanPressure.capacityCheck(syntheticFacts, "Window Char", 30, 2000, rows, 1.0)
        assertNull(c.failSafeReason)
        val gate = c.requirements.single()
        assertEquals(48, gate.requirement.windowEnd)
        val usable = 18 - excluded.size // turns 31..48
        assertEquals((usable - 2) * 250.0, gate.capacity, "13 usable turns at 250 each, minus the two best")
        assertEquals(48000, gate.deficit)
    }

    @Test
    @DisplayName("a trainee name that matches two characters fails safe as trainee-ambiguous and races")
    fun ambiguousTraineeFailsSafe() {
        assertTrue(syntheticFacts.match("tmopera o") is GrandConcertFanFacts.Match.UnknownAmbiguous)
        val c = GrandConcertFanPressure.capacityCheck(syntheticFacts, "tmopera o", 14, 1301, kitasanRows, 1.0)
        assertEquals("trainee-ambiguous", c.failSafeReason)
        assertTrue(c.requirements.isEmpty())
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(c))
    }

    @Test
    @DisplayName("every requirement met at this turn's fans defers even though the flag was still set")
    fun nothingUnmetDefers() {
        val done = check(72, 100000, 1120)
        assertTrue(done.requirements.isEmpty())
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(done))
    }

    @Test
    @DisplayName("conservative entry: a Classic G1 counts only from 25,000 projected fans, a Classic G3 from 1,500")
    fun conservativeEntry() {
        val g1 = listOf(RaceRow(RaceGrade.G1, 10000, Aptitude.A))
        val g3 = listOf(RaceRow(RaceGrade.G3, 4000, Aptitude.A))
        assertEquals(0.0, GrandConcertFanPressure.turnCapacity(30, g1, 24999.0, 1.0))
        assertEquals(2500.0, GrandConcertFanPressure.turnCapacity(30, g1, 25000.0, 1.0))
        assertEquals(0.0, GrandConcertFanPressure.turnCapacity(30, g3, 1499.0, 1.0))
        assertEquals(1000.0, GrandConcertFanPressure.turnCapacity(30, g3, 1500.0, 1.0))
        // Junior entry is lower, and today's races count whatever their entry (the race list shows what is open).
        assertEquals(1000.0, GrandConcertFanPressure.turnCapacity(20, g3, 350.0, 1.0))
        assertEquals(2500.0, GrandConcertFanPressure.turnCapacity(30, g1, null, 1.0))
    }

    @Test
    @DisplayName("place shares: B or better 3rd place, C 6th, D or worse nothing, scaled by the Fan Bonus up to 2nd and 5th place")
    fun placeShares() {
        assertEquals(0.25, GrandConcertFanPressure.placeShare(Aptitude.B, 1.0))
        assertEquals(0.40, GrandConcertFanPressure.placeShare(Aptitude.S, 3.0))
        assertEquals(0.09, GrandConcertFanPressure.placeShare(Aptitude.C, 1.0))
        assertEquals(0.10, GrandConcertFanPressure.placeShare(Aptitude.C, 2.0))
        assertEquals(0.0, GrandConcertFanPressure.placeShare(Aptitude.D, 3.0))
        assertEquals(1.0, GrandConcertFanPressure.fanBonus(null))
        assertEquals(1.0, GrandConcertFanPressure.fanBonus(280)) // a lost debut proves no bonus
        assertEquals(1.6, GrandConcertFanPressure.fanBonus(1120))
    }

    @Test
    @DisplayName("the GC_FAN line names the binding requirement, its capacity and slack, k and the re-arm")
    fun telemetry() {
        val c = check(31, 6000, 1120)
        val snapshot = GrandConcertFanPressure.evaluate(facts, "Kitasan Black", 31, 6000)
        val line = GrandConcertFanPressure.telemetryLine(snapshot, c, concertBehindPace = false, FanRaceDecision.FORCE_RACE, payoutsLow = false, rearmed = true)
        assertTrue(line.contains("binding=MANDATORY_GATE(target=25000 turn=48) capacity="), line)
        assertTrue(line.contains("slack=-"), line)
        assertTrue(line.contains("k=1.60 rearmed=true failSafe=none decision=FORCE_RACE"), line)
        val unread = GrandConcertFanPressure.CapacityCheck.failSafe(31, "aptitudes-unread", 1.0)
        val failed = GrandConcertFanPressure.telemetryLine(snapshot, unread, false, FanRaceDecision.FAIL_SAFE_FORCE_RACE, payoutsLow = false, rearmed = false)
        assertTrue(failed.contains("binding=none today=0 k=1.00 rearmed=false failSafe=aptitudes-unread"), failed)
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.exists()) return f
            dir = dir?.parentFile
        }
        error("could not locate $relative")
    }
}

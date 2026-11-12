package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.RaceGrade
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.ceil

/**
 * Plays every shipped trainee's career from the debut to turn 72 under three payout models, once with the old flat
 * 400-fan rule, once with the capacity rule as production runs it, and once racing every usable turn (the most fans
 * any rule can earn under the model). Base-card aptitudes and goal-race placements come from master.mdb
 * (`card_rarity_data`, `single_mode_route_race`) in the roster fixture; the calendar is `src/data/races.json`.
 *
 * Model: the debut is won (700 x the model's debut ratio); no racing except fan pressure and goal races; no fan race in
 * Summer or on a goal race turn; goal races pay at least their required place; concerts pay nothing.
 */
@DisplayName("Grand Concert fan pressure across every shipped trainee")
class GrandConcertFanSweepTest {
    private val assetText = repoFile("android/app/src/main/assets/gc_fan_runtime.json").readText()
    private val facts = GrandConcertFanFacts.parse(assetText) ?: error("shipped asset should parse")
    private val fixture = JSONObject(repoFile("android/app/src/test/resources/fixtures/gcfan/gc_fan_roster.json").readText())
    private val entryFans = fixture.getJSONObject("entryFans").let { o -> o.keys().asSequence().associateWith { o.getInt(it) } }

    private data class CalendarRace(val name: String, val grade: RaceGrade, val fans: Int, val terrain: String, val distanceType: String)

    // File order matters: the old rule's pick ties on (suited, fans) and takes the first row, as the simulation did.
    private val calendar: Map<Int, List<CalendarRace>> =
        repoFile("src/data/races.json").readText().let { text ->
            val all = JSONObject(text)
            all.keys().asSequence().sortedBy { text.indexOf(JSONObject.quote(it)) }.map { all.getJSONObject(it) }.map {
                it.getInt("turnNumber") to
                    CalendarRace(it.getString("name"), grade(it.getString("grade")), it.getInt("fans"), it.getString("terrain"), it.getString("distanceType"))
            }.groupBy({ it.first }, { it.second })
        }

    private enum class Model(val debut: Double, val b: Double, val c: Double, val d: Double, val e: Double) {
        // No Fan Bonus, B+ finishes 3rd, C 6th, D 12th, E or worse last.
        PESSIMISTIC(1.0, 0.25, 0.09, 0.03, 0.005),

        // Medians measured on our Grand Concert careers, C and D shaded down for their small samples.
        REALISTIC(1.6, 1.60, 1.00, 0.15, 0.03),

        // A strong Fan Bonus on a weak trainee: B+ finishes 6th. Inconsistent with passing top-3 goal races, so a stress test only.
        ADVERSARIAL(1.6, 0.144, 0.064, 0.03, 0.005),
    }

    private enum class Policy { ORACLE, OLD, NEW }

    private data class Outcome(val forced: Int, val juniorForced: Int, val miss: String?, val shortTurnsNotForced: List<Int>)

    private data class Summary(val misses: Set<String>, val forced: Int, val juniorForced: Int, val juniorTrainees: Int)

    private inner class Career(val name: String, val model: Model, val policy: Policy, val parity: Boolean) {
        private val trainee = fixture.getJSONObject("trainees").getJSONObject(name)
        private val aptitudes = trainee.getJSONObject("aptitudes").let { o -> o.keys().asSequence().associateWith { Aptitude.valueOf(o.getString(it)) } }
        private val route = (0 until trainee.getJSONArray("route").length()).map { trainee.getJSONArray("route").getJSONObject(it) }
        private val goalRaces = route.filter { !it.has("type") }.associateBy { it.getInt("turn") }
        private val countGoalTurns = route.filter { it.has("type") && it.getInt("type") == 2 }.map { it.getInt("turn") }.toSet()
        private val charFacts = (facts.match(name) as GrandConcertFanFacts.Match.Matched).facts
        private val gates = charFacts.mandatoryGates

        fun worst(terrain: String, distanceType: String): Aptitude = minOf(aptitudes.getValue(terrain), aptitudes.getValue(distanceType))

        fun ratio(apt: Aptitude): Double =
            when {
                apt >= Aptitude.B -> model.b
                apt == Aptitude.C -> model.c
                apt == Aptitude.D -> model.d
                else -> model.e
            }

        private fun row(race: CalendarRace) = GrandConcertFanPressure.RaceRow(race.grade, race.fans, worst(race.terrain, race.distanceType))

        private fun enterable(turn: Int, fans: Int): List<CalendarRace> =
            calendar[turn].orEmpty().filter { fans >= (entryFans["$turn|${it.name}"] ?: GrandConcertFanPressure.conservativeEntryFans(turn, it.grade)) }

        /** The production selector over today's enterable rows. */
        private fun gradedPick(turn: Int, fans: Int): CalendarRace? {
            val rows = enterable(turn, fans)
            if (rows.isEmpty()) return null
            val pick = GrandConcertFanRaceSelector.select(rows.map { GrandConcertFanRaceSelector.Candidate(it.fans, worst(it.terrain, it.distanceType), false) })
            return rows[pick.index]
        }

        /**
         * The simulation blocks the five graded-race-count goal turns too, which production cannot see; [parity] drops those
         * turns' rows from the capacity walk so this port reproduces the report's numbers.
         */
        private fun capacityRows(turn: Int): Map<Int, List<GrandConcertFanPressure.RaceRow>> =
            calendar.filterKeys { !parity || it == turn || it !in countGoalTurns }.mapValues { (_, races) -> races.map(::row) }

        private var rowsCache: Map<Int, List<GrandConcertFanPressure.RaceRow>>? = null

        private fun rows(turn: Int) = if (parity) capacityRows(turn) else rowsCache ?: capacityRows(turn).also { rowsCache = it }

        fun play(): Outcome {
            var fans = 1
            var forced = 0
            var junior = 0
            var miss: String? = null
            var debutFans: Int? = null
            val paidOld = ArrayList<Int>()
            val paidNew = ArrayList<Pair<Int, Double>>()
            val shortNotForced = ArrayList<Int>()
            for (now in 12..72) {
                for (gate in gates) if (gate.turn == now && fans < gate.minFansNeeded) miss = miss ?: "gate@$now"
                val goalRace = goalRaces[now]
                if (goalRace != null) {
                    val face = goalRace.getInt("face")
                    if (now == 12) {
                        assertEquals(GrandConcertFanPressure.DEBUT_WIN_FANS, face, "$name debut face")
                        debutFans = (face * model.debut).toInt()
                        fans += debutFans
                    } else {
                        val share = maxOf(ratio(worst(goalRace.getString("terrain"), goalRace.getString("distanceType"))), placeShare(goalRace.getInt("place")))
                        fans += (face * share).toInt()
                    }
                } else if (now != 13 && !GameDate.isSummer(now)) {
                    var race: CalendarRace? = null
                    var assumed = 0.0
                    when (policy) {
                        Policy.ORACLE -> race = gradedPick(now, fans)
                        Policy.OLD ->
                            if (GrandConcertFanRequirement.evaluate(facts, name, now, fans) is GrandConcertFanRequirement.Result.Active &&
                                oldForces(now, fans, paidOld)
                            ) {
                                race = enterable(now, fans).maxWithOrNull(compareBy({ worst(it.terrain, it.distanceType) >= Aptitude.B }, { it.fans }))
                            }
                        Policy.NEW -> {
                            val k = GrandConcertFanPressure.fanBonus(debutFans)
                            val check = GrandConcertFanPressure.capacityCheck(facts, name, now, fans, rows(now), k)
                            val low = GrandConcertFanPressure.payoutsBelowAssumption(paidNew)
                            val scope = GrandConcertFanRequirement.evaluate(facts, name, now, fans)
                            val active = GrandConcertFanRequirement.rearm(scope, check, low) is GrandConcertFanRequirement.Result.Active
                            val decision = GrandConcertFanPolicy.decide(active, check, low)
                            if (GrandConcertFanPolicy.forcesRace(decision)) {
                                race = gradedPick(now, fans)
                                assumed = if (parity) race?.let { it.fans * GrandConcertFanPressure.placeShare(worst(it.terrain, it.distanceType), k) } ?: 0.0 else check.todayCapacity
                            } else if (miss == null && check.binding?.hasSlack == false && check.todayCapacity > 0.0) {
                                shortNotForced += now
                            }
                        }
                    }
                    if (race != null) {
                        val gain = (race.fans * ratio(worst(race.terrain, race.distanceType))).toInt()
                        if (policy == Policy.OLD) paidOld += gain
                        if (policy == Policy.NEW && assumed > 0.0) paidNew += gain to assumed
                        fans += gain
                        forced++
                        if (now <= 24) junior++
                    }
                }
                for (goal in charFacts.fanGoals) if (goal.deadlineTurn == now && fans < goal.targetFans) miss = miss ?: "goal@$now"
            }
            return Outcome(forced, junior, miss, shortNotForced)
        }

        // ---- the old rule, as it shipped: flat 400 fans a race, x1.5 + 2 margin, tightest later requirement binds ----

        private fun racesWithMargin(deficit: Int): Int = ceil(ceil(deficit / 400.0) * 3 / 2).toInt()

        private fun oldUsable(now: Int, end: Int): Int = ((now + 1)..end).count { it in 12..72 && !GameDate.isSummer(it) && gates.none { g -> g.turn == it } }

        private fun oldForces(now: Int, fans: Int, paid: List<Int>): Boolean {
            if (paid.size >= 2 && paid.takeLast(2).all { it < 400 }) return true
            val goal = charFacts.fanGoals.filter { fans < it.targetFans }.minByOrNull { it.deadlineTurn }
            val gate = gates.filter { fans < it.minFansNeeded }.minByOrNull { it.turn }
            val effective =
                listOfNotNull(
                    goal?.takeIf { it.deadlineTurn >= now }?.let { Triple<Int, Int?, Int>(it.deadlineTurn, it.targetFans, it.deadlineTurn) },
                    gate?.takeIf { it.turn >= now }?.let { Triple(it.turn, it.sharedFansNeeded, it.turn - 1) },
                ).minByOrNull { it.first } ?: return true
            val (turn, target, end) = effective
            if (target == null || turn <= now) return true
            var usable = oldUsable(now, end)
            var deficit = target - fans
            val later =
                charFacts.fanGoals.filter { fans < it.targetFans && it.deadlineTurn > turn }.map { it.targetFans to it.deadlineTurn } +
                    gates.filter { fans < it.minFansNeeded && it.turn > turn }.map { it.maxFansNeeded to it.turn - 1 }
            for ((value, laterEnd) in later) {
                val laterUsable = oldUsable(now, laterEnd)
                if (laterUsable - racesWithMargin(value - fans) < usable - racesWithMargin(deficit)) {
                    usable = laterUsable
                    deficit = value - fans
                }
            }
            return usable - 2 - racesWithMargin(deficit) < 0
        }
    }

    private val names: List<String> = fixture.getJSONObject("trainees").keys().asSequence().sorted().toList()

    private val runs = HashMap<Triple<Model, Policy, Boolean>, Map<String, Outcome>>()

    private fun run(model: Model, policy: Policy, parity: Boolean = false): Map<String, Outcome> =
        runs.getOrPut(Triple(model, policy, parity)) { names.associateWith { Career(it, model, policy, parity).play() } }

    private fun summary(model: Model, policy: Policy, parity: Boolean = false): Summary {
        val res = run(model, policy, parity)
        return Summary(res.filterValues { it.miss != null }.keys, res.values.sumOf { it.forced }, res.values.sumOf { it.juniorForced }, res.values.count { it.juniorForced > 0 })
    }

    @Test
    @DisplayName("every shipped trainee is in the roster fixture")
    fun fixtureCoversAsset() {
        assertEquals(JSONObject(assetText).getJSONObject("characters").keys().asSequence().toSet(), names.toSet())
    }

    @Test
    @DisplayName("pessimistic payouts: no requirement racing every turn meets is missed, and nothing today's rule meets is lost")
    fun pessimisticNeverLosesAFeasibleRequirement() {
        val oracle = summary(Model.PESSIMISTIC, Policy.ORACLE).misses
        val old = summary(Model.PESSIMISTIC, Policy.OLD).misses
        val new = summary(Model.PESSIMISTIC, Policy.NEW).misses
        assertEquals(emptySet<String>(), new - oracle, "avoidable misses")
        assertEquals(emptySet<String>(), new - old, "regressions against the old rule")
    }

    @Test
    @DisplayName("realistic payouts: nothing today's rule meets is lost")
    fun realisticNoRegression() {
        val old = summary(Model.REALISTIC, Policy.OLD).misses
        val new = summary(Model.REALISTIC, Policy.NEW).misses
        assertEquals(emptySet<String>(), new - old, "regressions against the old rule")
    }

    @Test
    @DisplayName("bounded limitation: the five trainees no rule can carry under pessimistic payouts race every short turn instead of deferring")
    fun infeasibleTraineesNeverDeferWhileShort() {
        val infeasible = listOf("Copano Rickey", "Curren Chan", "Hokko Tarumae", "Smart Falcon", "Wonder Acute")
        val outcomes = run(Model.PESSIMISTIC, Policy.NEW)
        assertEquals(infeasible.toSet(), summary(Model.PESSIMISTIC, Policy.ORACLE).misses)
        for (name in infeasible) {
            assertTrue(outcomes.getValue(name).miss != null, "$name is expected to miss under any rule")
            assertEquals(emptyList<Int>(), outcomes.getValue(name).shortTurnsNotForced, "$name deferred while short")
        }
    }

    @Test
    @DisplayName("the port reproduces the simulation's pessimistic and realistic numbers")
    fun parityWithSimulation() {
        fun row(model: Model, policy: Policy): String {
            val s = summary(model, policy, parity = true)
            val oracle = summary(model, Policy.ORACLE, parity = true).misses
            return "${s.misses.size} ${(s.misses - oracle).size} ${s.forced} ${s.juniorForced} ${s.juniorTrainees}"
        }
        assertEquals("5 0 3085 750 71", row(Model.PESSIMISTIC, Policy.ORACLE))
        assertEquals("38 33 805 345 65", row(Model.PESSIMISTIC, Policy.OLD))
        // The simulation has 980: production fails safe on a goal due this turn or overdue. That adds one race on Wonder
        // Acute's deadline turn and leaves seven fewer after a miss (Copano Rickey, Hokko Tarumae, Smart Falcon, Wonder
        // Acute), on turns where the game would already have ended the career.
        assertEquals("5 0 974 414 71", row(Model.PESSIMISTIC, Policy.NEW))
        assertEquals("0 0 153 134 33", row(Model.REALISTIC, Policy.OLD))
        assertEquals("0 0 103 70 45", row(Model.REALISTIC, Policy.NEW))
    }

    private fun placeShare(place: Int): Double =
        when (place) {
            1 -> 1.0
            2 -> 0.40
            3 -> 0.25
            4 -> 0.15
            5 -> 0.10
            else -> 0.0
        }

    private fun grade(text: String): RaceGrade =
        when (text) {
            "Pre-OP" -> RaceGrade.PRE_OP
            "OP" -> RaceGrade.OP
            else -> RaceGrade.valueOf(text)
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

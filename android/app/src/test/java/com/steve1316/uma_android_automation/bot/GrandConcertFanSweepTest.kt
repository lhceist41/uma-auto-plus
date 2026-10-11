package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.GameDate
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Plays every shipped trainee's fan requirements forward from every start turn and fan count, once with today's
 * race-whenever-required behaviour and once with the deferral rule, and fails if the rule misses a requirement that
 * racing would have met. Model: each fan race pays a flat amount, mandatory races and concerts pay nothing, no fan
 * race in Summer or on a mandatory race turn, and the rule may see a date lagging behind the real one (a failed read).
 */
@DisplayName("Grand Concert fan deferral never loses a requirement that racing meets")
class GrandConcertFanSweepTest {
    private val facts = GrandConcertFanFacts.parse(assetFile("gc_fan_runtime.json").readText()) ?: error("shipped asset should parse")
    private val names = JSONObject(assetFile("gc_fan_runtime.json").readText()).getJSONObject("characters").keys().asSequence().toList()

    @Test
    fun everyTraineeEveryStart() {
        val misses = mutableListOf<String>()
        for (name in names) {
            val trainee = Trainee(name)
            for (pay in listOf(300, 400)) {
                for (lag in 0..2) {
                    for (goalCountsDeadlineRace in listOf(true, false)) {
                        val base = trainee.outcomes(pay, lag, goalCountsDeadlineRace, rule = false)
                        val rule = trainee.outcomes(pay, lag, goalCountsDeadlineRace, rule = true)
                        for (turn in FIRST_TURN..LAST_TURN) {
                            for (index in 0 until START_FAN_STEPS) {
                                val fans = FIRST_FANS + index * FAN_STEP
                                val missed = rule(turn, fans)
                                if (missed != 0 && base(turn, fans) == 0) {
                                    misses += "$name start turn $turn fans $fans pay $pay lag $lag: misses turn $missed"
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(misses.isEmpty(), "${misses.size} misses, e.g. ${misses.take(5)}")
    }

    private inner class Trainee(val name: String) {
        private val charFacts = (facts.match(name) as GrandConcertFanFacts.Match.Matched).facts
        private val mandatoryTurns = charFacts.mandatoryGates.map { it.turn }.toSet()
        private val defers = HashMap<Long, Boolean>()
        private val required = HashMap<Long, Boolean>()

        // The requirement hook only runs while the nearest requirement is unmet.
        private fun required(turn: Int, fans: Int): Boolean =
            required.getOrPut(turn.toLong() shl 32 or fans.toLong()) {
                GrandConcertFanRequirement.evaluate(facts, name, turn, fans) is GrandConcertFanRequirement.Result.Active
            }

        private fun defers(turn: Int, fans: Int): Boolean =
            defers.getOrPut(turn.toLong() shl 32 or fans.toLong()) {
                val inputs = GrandConcertFanPressure.policyInputs(GrandConcertFanPressure.evaluate(facts, name, turn, fans), false)
                GrandConcertFanPolicy.decide(true, inputs.turnsUntilDeadline, inputs.racesStillNeeded, concertBehindPace = true) ==
                    GrandConcertFanPolicy.FanRaceDecision.DEFER_TO_TRAINING
            }

        /** Turn of the first missed requirement from (turn, fans), or 0 when the career gets through. */
        fun outcomes(pay: Int, lag: Int, goalCountsDeadlineRace: Boolean, rule: Boolean): (Int, Int) -> Int {
            val memo = HashMap<Long, Int>()

            fun play(turn: Int, fans: Int): Int {
                if (turn > LAST_TURN) return 0
                val key = turn.toLong() shl 32 or fans.toLong()
                memo[key]?.let { return it }
                val result =
                    run {
                        if (charFacts.mandatoryGates.any { it.turn == turn && fans < it.minFansNeeded }) return@run turn
                        if (!goalCountsDeadlineRace && charFacts.fanGoals.any { it.deadlineTurn == turn && fans < it.targetFans }) return@run turn
                        val races = required(turn, fans) && turn !in mandatoryTurns && turn !in summerTurns && (!rule || !defers(turn - lag, fans))
                        val after = if (races) fans + pay else fans
                        if (goalCountsDeadlineRace && charFacts.fanGoals.any { it.deadlineTurn == turn && after < it.targetFans }) return@run turn
                        play(turn + 1, after)
                    }
                memo[key] = result
                return result
            }
            return ::play
        }
    }

    private fun assetFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/assets")
            if (a.isDirectory) return File(a, relative)
            val b = File(dir, "android/app/src/main/assets")
            if (b.isDirectory) return File(b, relative)
            dir = dir?.parentFile
        }
        error("could not locate the assets root")
    }

    private companion object {
        val summerTurns = (GrandConcertRaceCalendar.FIRST_RACEABLE_TURN..GrandConcertRaceCalendar.LAST_CAREER_TURN).filter { GameDate.isSummer(it) }.toSet()

        const val FIRST_TURN = GrandConcertRaceCalendar.FIRST_RACEABLE_TURN
        const val LAST_TURN = GrandConcertRaceCalendar.LAST_CAREER_TURN
        const val FIRST_FANS = 2
        const val FAN_STEP = 100
        const val START_FAN_STEPS = 260
    }
}

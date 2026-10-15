package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.SkillCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateSkillPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.reserveForScenarioGolds
import com.steve1316.uma_android_automation.bot.SkillPlan.SkillPlanSettings
import com.steve1316.uma_android_automation.bot.SkillPlan.SpendingStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GrandConcertGoldReserveTest {
    private val gc = "Grand Concert"

    /** Rows from a Grand Concert career's Senior Late Jul skill screen: the planned ◎ upgrades ate the SP while See Ya Later! went unbought. */
    private val milesOnScreen =
        listOf(
            SkillCandidate("Mile Straightaways ○", price = 100, evaluationPoints = 239, isUserPlanned = true),
            SkillCandidate("Mile Straightaways ◎", price = 110, evaluationPoints = 49, isUserPlanned = true),
            SkillCandidate("Mile Corners ○", price = 70, evaluationPoints = 239, isUserPlanned = true),
            SkillCandidate("Mile Corners ◎", price = 77, evaluationPoints = 49, isUserPlanned = true),
            SkillCandidate("Pace Chaser Straightaways ◎", price = 98, evaluationPoints = 49, isUserPlanned = true),
            SkillCandidate("Lucky Seven", price = 99, evaluationPoints = 174),
        )
    private val seeYaLater = SkillCandidate("See Ya Later!", price = 304, evaluationPoints = 508)

    private val skillsPlan =
        SkillPlanSettings(
            bIsEnabled = true,
            strategy = SpendingStrategy.OPTIMIZE_RANK,
            bEnableBuyInheritedUniqueSkills = true,
            bEnableBuyNegativeSkills = false,
            skillNames = milesOnScreen.filter { it.isUserPlanned }.map { it.name },
        )

    /** The session's composition in getSkillsToBuy: golds first, then the usual plan under the SP the reserve leaves. */
    private fun session(
        candidates: List<SkillCandidate>,
        skillPoints: Int,
        settings: SkillPlanSettings = skillsPlan,
        scenario: String = gc,
        objective: SkillSpendObjective = SkillSpendObjective.RANK,
        trigger: SkillCheckTrigger? = SkillCheckTrigger.HIGH_WATER,
    ): List<Pair<String, Int>> {
        val (golds, reserved) = reserveForScenarioGolds(scenario, objective, trigger, candidates, skillPoints)
        val goldNames = golds.map { it.first }
        val rest = calculateSkillPurchases(candidates.filter { it.name !in goldNames }, skillPoints - reserved - golds.sumOf { it.second }, settings)
        return golds + rest
    }

    @Test
    fun `an offered gold that does not fit stops the upgrade spend and the SP carries`() {
        assertEquals(emptyList<Pair<String, Int>>() to 304, reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, milesOnScreen + seeYaLater, 285))
        val plan = session(milesOnScreen + seeYaLater, skillPoints = 285)
        assertTrue(plan.isEmpty(), "bought $plan")
        // Without the reserve the same screen spends on the ◎ upgrades.
        assertTrue(calculateSkillPurchases(milesOnScreen + seeYaLater, 285, skillsPlan).any { it.first.endsWith("◎") })
    }

    @Test
    fun `the same rule holds the SP at the pre-finals session`() {
        assertTrue(session(milesOnScreen + seeYaLater, skillPoints = 285, trigger = SkillCheckTrigger.SCENARIO_FINALS).isEmpty())
    }

    @Test
    fun `an affordable gold claims the budget first at its live price and the rest of the plan gets what is left`() {
        val plan = session(milesOnScreen + seeYaLater, skillPoints = 378)
        assertEquals("See Ya Later!" to 304, plan.first())
        assertEquals(listOf("Mile Corners ○"), plan.drop(1).map { it.first })
        assertTrue(plan.sumOf { it.second } <= 378)
    }

    @Test
    fun `with two golds offered the higher rating is planned first and the other holds the rest`() {
        val iWannaWin = SkillCandidate("I Wanna Win with You", price = 380, evaluationPoints = 633)
        val (golds, reserved) = reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, milesOnScreen + seeYaLater + iWannaWin, 400)
        assertEquals(listOf("I Wanna Win with You" to 380), golds)
        assertEquals(304, reserved)
        assertEquals(listOf("I Wanna Win with You"), session(milesOnScreen + seeYaLater + iWannaWin, skillPoints = 400).map { it.first })
    }

    @Test
    fun `Come What May counts like the other golds and ties on rating go to the cheaper one`() {
        val comeWhatMay = SkillCandidate("Come What May", price = 288, evaluationPoints = 508)
        val (golds, reserved) = reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.SCENARIO_FINALS, milesOnScreen + seeYaLater + comeWhatMay, 300)
        assertEquals(listOf("Come What May" to 288), golds)
        assertEquals(304, reserved)
    }

    @Test
    fun `no gold offered leaves the plan exactly as before`() {
        for (sp in listOf(0, 150, 285, 378, 1000)) {
            assertEquals(emptyList<Pair<String, Int>>() to 0, reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, milesOnScreen, sp))
            assertEquals(calculateSkillPurchases(milesOnScreen, sp, skillsPlan), session(milesOnScreen, sp))
        }
        // Golden run over the purchasing suite's seeded generator, every strategy of the pure planner (the live DEFAULT tail is empty there).
        val rng = Random(42)
        repeat(20) {
            val budget = rng.nextInt(100, 2000)
            val candidates =
                (1..rng.nextInt(10, 50)).map { i ->
                    SkillCandidate(
                        name = "Skill_$i",
                        price = rng.nextInt(20, 300),
                        evaluationPoints = rng.nextInt(10, 200),
                        isNegative = rng.nextDouble() < 0.1,
                        isInheritedUnique = rng.nextDouble() < 0.05,
                        isUserPlanned = rng.nextDouble() < 0.15,
                        communityTier = if (rng.nextBoolean()) rng.nextInt(0, 4) else null,
                    )
                }
            for (strategy in SpendingStrategy.entries) {
                val settings = skillsPlan.copy(strategy = strategy, skillNames = candidates.filter { it.isUserPlanned }.map { it.name })
                assertEquals(calculateSkillPurchases(candidates, budget, settings), session(candidates, budget, settings))
            }
        }
    }

    @Test
    fun `other scenarios, other objectives and other sessions are unchanged`() {
        val screen = milesOnScreen + seeYaLater
        for (sp in listOf(285, 378)) {
            val today = calculateSkillPurchases(screen, sp, skillsPlan)
            for (scenario in listOf("URA Finale", "Unity Cup", "Trackblazer")) {
                assertEquals(today, session(screen, sp, scenario = scenario))
            }
            for (objective in SkillSpendObjective.entries.filter { it != SkillSpendObjective.RANK }) {
                assertEquals(today, session(screen, sp, objective = objective))
            }
            for (trigger in listOf(SkillCheckTrigger.CAREER_COMPLETE, SkillCheckTrigger.CRITICAL_RACE, SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE, SkillCheckTrigger.MANUAL, null)) {
                assertEquals(today, session(screen, sp, trigger = trigger))
            }
        }
    }

    @Test
    fun `the reserve is released once the gold leaves the list`() {
        assertTrue(session(milesOnScreen + seeYaLater, skillPoints = 285).isEmpty())
        // Next session: the gold was granted by an event (obtained rows are not available) or is no longer offered.
        assertEquals(calculateSkillPurchases(milesOnScreen, 285, skillsPlan), session(milesOnScreen, skillPoints = 285))
        assertTrue(session(milesOnScreen, skillPoints = 285).isNotEmpty())
    }

    @Test
    fun `the live planner holds the reserve back from every phase`() {
        // getSkillsToBuy needs a live Game, so its wiring is pinned by source.
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/bot/SkillPlan.kt"
        while (dir != null && !java.io.File(dir, rel).isFile) dir = dir.parentFile
        val src: String = java.io.File(dir ?: throw AssertionError("$rel not found"), rel).readText()
        val start: Int = src.indexOf("    fun getSkillsToBuy(")
        assertTrue(start >= 0)
        val body: String = src.substring(start, src.indexOf("\n    }", start))
        assertTrue(body.contains("reserveForScenarioGolds("))
        assertTrue(body.contains("trigger = sessionEffectiveTrigger"))
        assertTrue(body.contains("price = it.screenPrice"))
        assertEquals(6, Regex("availableSkillPoints = planSkillPoints - result.values.sum\\(\\)").findAll(body).count())
        assertFalse(body.contains("availableSkillPoints = availableSkillPoints - result.values.sum()"))
    }
}

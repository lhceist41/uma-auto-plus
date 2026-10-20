package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.SkillCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateSkillPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.cheapestHeldGold
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
        turn: Int? = null,
    ): List<Pair<String, Int>> {
        val (golds, reserved) = reserveForScenarioGolds(scenario, objective, trigger, candidates, skillPoints, turn)
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
    fun `on the last turn an offered gold that does not fit holds nothing and the usual plan runs`() {
        val iWannaWin = SkillCandidate("I Wanna Win with You", price = 380, evaluationPoints = 633)
        assertEquals(
            emptyList<Pair<String, Int>>() to 0,
            reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, milesOnScreen + iWannaWin, 375, turn = 75),
        )
        val plan = session(milesOnScreen + iWannaWin, skillPoints = 375, turn = 75)
        assertTrue(plan.isNotEmpty())
        assertFalse(plan.any { it.first == "I Wanna Win with You" })
        assertTrue(plan.sumOf { it.second } <= 375)
    }

    @Test
    fun `before the last turn the same gold still holds the SP`() {
        val iWannaWin = SkillCandidate("I Wanna Win with You", price = 380, evaluationPoints = 633)
        for (turn in listOf(74, 73, 60)) {
            assertEquals(
                emptyList<Pair<String, Int>>() to 380,
                reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, milesOnScreen + iWannaWin, 375, turn = turn),
            )
            assertTrue(session(milesOnScreen + iWannaWin, skillPoints = 375, turn = turn).isEmpty())
        }
    }

    @Test
    fun `on the last turn a gold that fits is still planned first`() {
        val plan = session(milesOnScreen + seeYaLater, skillPoints = 378, turn = 75)
        assertEquals("See Ya Later!" to 304, plan.first())
    }

    @Test
    fun `the last-turn rule leaves other scenarios and objectives as they were`() {
        val iWannaWin = SkillCandidate("I Wanna Win with You", price = 380, evaluationPoints = 633)
        val none = emptyList<Pair<String, Int>>() to 0
        assertEquals(none, reserveForScenarioGolds("URA Finale", SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, milesOnScreen + iWannaWin, 375, turn = 75))
        assertEquals(none, reserveForScenarioGolds(gc, SkillSpendObjective.SPARKS, SkillCheckTrigger.HIGH_WATER, milesOnScreen + iWannaWin, 375, turn = 75))
        assertEquals(none, reserveForScenarioGolds(gc, SkillSpendObjective.RANK, SkillCheckTrigger.CAREER_COMPLETE, milesOnScreen + iWannaWin, 569, turn = 75))
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

    private val iWannaWin400 = SkillCandidate("I Wanna Win with You", price = 400, evaluationPoints = 633)

    /** What getSkillsToBuy leaves for Campaign: the held gold's cost, or null when nothing was held. */
    private fun heldCost(candidates: List<SkillCandidate>, skillPoints: Int, scenario: String = gc): Int? {
        val (golds, reserved) = reserveForScenarioGolds(scenario, SkillSpendObjective.RANK, SkillCheckTrigger.HIGH_WATER, candidates, skillPoints)
        return if (reserved > 0) cheapestHeldGold(candidates, golds) else null
    }

    private fun highWater(skillPoints: Int, handled: Boolean, day: Int = 65) =
        decideSkillCheck(
            skillPoints = skillPoints,
            highWaterThreshold = 350,
            enableSkillPointCheck = true,
            highWaterPlanEnabled = true,
            alreadyHandledHighWater = handled,
            day = day,
            preFinalsPlanEnabled = true,
            alreadyHandledPreFinals = false,
        )

    @Test
    fun `a gold held at 350 SP is bought by a re-armed high-water session once SP reaches its cost`() {
        val screen = milesOnScreen + iWannaWin400
        assertTrue(session(screen, skillPoints = 350).isEmpty())
        val held = heldCost(screen, 350)
        assertEquals(400, held)

        // The held session spent nothing, so SP stays above the threshold and the old flag alone never re-arms.
        assertEquals(SkillCheckAction.NONE, highWater(skillPoints = 410, handled = true).action)
        assertFalse(shouldRearmHighWater(true, held, heldAtSkillPoints = 350, skillPoints = 390))
        assertTrue(shouldRearmHighWater(true, held, heldAtSkillPoints = 350, skillPoints = 410))

        val rearmed = highWater(skillPoints = 410, handled = false)
        assertEquals(SkillCheckAction.RUN_PLAN, rearmed.action)
        assertEquals(SkillCheckTrigger.HIGH_WATER, rearmed.trigger)
        assertEquals("I Wanna Win with You" to 400, session(screen, skillPoints = 410).first())
    }

    @Test
    fun `two held golds re-arm at the cheaper one`() {
        val screen = milesOnScreen + seeYaLater + iWannaWin400
        assertTrue(session(screen, skillPoints = 300).isEmpty())
        assertEquals(304, heldCost(screen, 300))
        // A gold planned in the same session is not held: only the one that did not fit counts.
        assertEquals(400, heldCost(screen, 350))
    }

    @Test
    fun `no held gold leaves high water as it was`() {
        assertEquals(null, heldCost(milesOnScreen, 350))
        assertEquals(null, heldCost(milesOnScreen + iWannaWin400, 350, scenario = "URA Finale"))
        for (sp in listOf(0, 349, 350, 410, 1000)) {
            assertFalse(shouldRearmHighWater(true, null, heldAtSkillPoints = 0, skillPoints = sp))
            assertFalse(shouldRearmHighWater(false, null, heldAtSkillPoints = 0, skillPoints = sp))
        }
    }

    @Test
    fun `a bought or withdrawn gold, or a hold at the same SP, cannot loop the re-arm`() {
        // The re-armed session at 410 plans the gold, so nothing is held afterwards.
        assertEquals(null, heldCost(milesOnScreen + iWannaWin400, 410))
        // Withdrawn: the next session sees no gold and holds nothing.
        assertEquals(null, heldCost(milesOnScreen, 410))
        // Held again because the Learn screen read less SP than the Main screen: no re-open until SP grows past the new hold.
        assertFalse(shouldRearmHighWater(true, 400, heldAtSkillPoints = 410, skillPoints = 410))
        assertFalse(shouldRearmHighWater(false, 400, heldAtSkillPoints = 350, skillPoints = 410))
    }

    @Test
    fun `Campaign re-arms from the held cost and records it after high-water and pre-finals sessions`() {
        fun source(rel: String): String {
            var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (dir != null && !java.io.File(dir, rel).isFile) dir = dir.parentFile
            return java.io.File(dir ?: throw AssertionError("$rel not found"), rel).readText()
        }
        val base = "android/app/src/main/java/com/steve1316/uma_android_automation/bot/"
        val campaign = source(base + "Campaign.kt")
        val start = campaign.indexOf("    open fun performGlobalChecks(")
        val body = campaign.substring(start, campaign.indexOf("\n    }", start))
        val rearm = body.indexOf("if (shouldRearmHighWater(bHasHandledSkillPointCheck, heldGoldCost, heldGoldAtSkillPoints, trainee.skillPoints))")
        assertTrue(rearm in 0 until body.indexOf("decideSkillCheck("))
        assertEquals(2, Regex("heldGoldCost = skillPlan.sessionHeldGoldCost").findAll(body).count())

        val plan = source(base + "SkillPlan.kt")
        assertTrue(plan.contains("sessionHeldGoldCost = if (reservedSkillPoints > 0) cheapestHeldGold(goldCandidates, goldsFirst) else null"))
        val startBody = plan.substring(plan.indexOf("    fun start("), plan.indexOf("val bitmap: Bitmap", plan.indexOf("    fun start(")))
        assertTrue(startBody.contains("sessionHeldGoldCost = null"))
    }
}

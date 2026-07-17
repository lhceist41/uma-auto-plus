package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkillSpendTelemetry] - the `type:"skill_spend"` record shape and its skip-reason
 * derivation.
 *
 * The invariants worth pinning: optional identity is OMITTED rather than fabricated (a placeholder
 * would silently attribute a session to the wrong arm), `proposed` and `confirmed` stay distinct
 * (confirmed is screen evidence, not attempted taps), and a skip reason is only claimed when the
 * record can prove it.
 */
@DisplayName("SkillSpendTelemetry Tests")
class SkillSpendTelemetryTest {
    private fun record(
        outcome: SkillSpendOutcome = SkillSpendOutcome.COMMITTED,
        trigger: SkillCheckTrigger? = SkillCheckTrigger.HIGH_WATER,
        planKey: String? = PLAN_SKILL_POINT_CHECK,
        strategy: String? = "OPTIMIZE_KNAPSACK",
        trainee: String? = "Super_Creek",
        scenario: String? = "Unity_Cup",
        fp: String? = "1e681a57e1",
        turn: Int? = 40,
        spBefore: Int? = 400,
        spAfter: Int? = 58,
        proposed: List<ProposedSkill> = listOf(ProposedSkill("Professor of Curvature", 200), ProposedSkill("Swinging Maestro", 142)),
        confirmed: List<String> = listOf("Professor of Curvature", "Swinging Maestro"),
        skipped: List<SkippedSkill> = emptyList(),
        threshold: Int? = null,
        tier: String? = null,
        reason: String? = null,
        objective: String? = null,
        criticalRace: String? = null,
        criticalRaceSource: String? = null,
        turnsUntilRace: Int? = null,
        plannedSkill: String? = null,
        plannedSkillObservedPrice: Int? = null,
        strategyTailAllowed: Boolean? = null,
    ) = SkillSpendTelemetry.buildRecord(
        timestamp = 1784213172197L,
        outcome = outcome,
        trigger = trigger,
        planKey = planKey,
        strategy = strategy,
        trainee = trainee,
        scenario = scenario,
        fp = fp,
        turn = turn,
        spBefore = spBefore,
        spAfter = spAfter,
        proposed = proposed,
        confirmed = confirmed,
        skipped = skipped,
        threshold = threshold,
        tier = tier,
        reason = reason,
        objective = objective,
        criticalRace = criticalRace,
        criticalRaceSource = criticalRaceSource,
        turnsUntilRace = turnsUntilRace,
        plannedSkill = plannedSkill,
        plannedSkillObservedPrice = plannedSkillObservedPrice,
        strategyTailAllowed = strategyTailAllowed,
    )

    @Nested
    @DisplayName("record shape")
    inner class ShapeTests {
        @Test
        fun `a committed session carries every field`() {
            val r = record()
            assertEquals("skill_spend", r.getString("type"))
            assertEquals(1784213172197L, r.getLong("ts"))
            assertEquals("trigger-v4", r.getString("policy"))
            assertEquals("committed", r.getString("outcome"))
            assertEquals("HIGH_WATER", r.getString("trigger"))
            assertEquals(PLAN_SKILL_POINT_CHECK, r.getString("plan"))
            assertEquals("OPTIMIZE_KNAPSACK", r.getString("strategy"))
            assertEquals("Super_Creek", r.getString("trainee"))
            assertEquals("Unity_Cup", r.getString("scenario"))
            assertEquals("1e681a57e1", r.getString("fp"))
            assertEquals(40, r.getInt("turn"))
            assertEquals(400, r.getInt("spBefore"))
            assertEquals(58, r.getInt("spAfter"))
            assertEquals(58, r.getInt("unspent"))
            assertEquals(2, r.getJSONArray("proposed").length())
            assertEquals(2, r.getJSONArray("confirmed").length())
        }

        @Test
        fun `every outcome renders as its lower-snake token`() {
            assertEquals("committed", record(outcome = SkillSpendOutcome.COMMITTED).getString("outcome"))
            assertEquals("commit_unverified", record(outcome = SkillSpendOutcome.COMMIT_UNVERIFIED).getString("outcome"))
            assertEquals("empty_plan", record(outcome = SkillSpendOutcome.EMPTY_PLAN).getString("outcome"))
            assertEquals("nothing_to_buy", record(outcome = SkillSpendOutcome.NOTHING_TO_BUY).getString("outcome"))
            assertEquals("aborted_parse", record(outcome = SkillSpendOutcome.ABORTED_PARSE).getString("outcome"))
            assertEquals("aborted_entry", record(outcome = SkillSpendOutcome.ABORTED_ENTRY).getString("outcome"))
            assertEquals("failed", record(outcome = SkillSpendOutcome.FAILED).getString("outcome"))
        }

        @Test
        fun `proposed carries the planned price per skill`() {
            val proposed = record().getJSONArray("proposed")
            assertEquals("Professor of Curvature", proposed.getJSONObject(0).getString("name"))
            assertEquals(200, proposed.getJSONObject(0).getInt("price"))
        }

        @Test
        fun `an empty-plan record still identifies the session`() {
            val r = record(outcome = SkillSpendOutcome.EMPTY_PLAN, proposed = emptyList(), confirmed = emptyList(), spBefore = null, spAfter = null)
            assertEquals("empty_plan", r.getString("outcome"))
            assertEquals(PLAN_SKILL_POINT_CHECK, r.getString("plan"))
            assertEquals("Super_Creek", r.getString("trainee"))
            assertFalse(r.has("proposed"), "no planner output means no proposed array")
            assertFalse(r.has("spBefore"), "points were never read, so the field is absent")
        }

        @Test
        fun `a parse-abort record keeps the points it did read`() {
            val r = record(outcome = SkillSpendOutcome.ABORTED_PARSE, proposed = emptyList(), confirmed = emptyList(), spBefore = 500, spAfter = 500)
            assertEquals("aborted_parse", r.getString("outcome"))
            assertEquals(500, r.getInt("spBefore"))
            assertEquals(500, r.getInt("unspent"))
        }

        @Test
        fun `an unverified commit is recorded as its own outcome, not as committed`() {
            val r = record(outcome = SkillSpendOutcome.COMMIT_UNVERIFIED)
            assertEquals("commit_unverified", r.getString("outcome"))
            assertEquals(2, r.getJSONArray("confirmed").length(), "skills evidenced as obtained are still reported")
        }
    }

    @Nested
    @DisplayName("optional identity is omitted, never fabricated")
    inner class OptionalTests {
        @Test
        fun `absent identity fields are left out entirely`() {
            val r = record(trigger = null, planKey = null, strategy = null, trainee = null, scenario = null, fp = null, turn = null, spBefore = null, spAfter = null)
            for (key in listOf("trigger", "plan", "strategy", "trainee", "scenario", "fp", "turn", "spBefore", "spAfter", "unspent")) {
                assertFalse(r.has(key), "\"$key\" must be omitted, not defaulted")
            }
            // What is always known still lands, so the record is never anonymous.
            assertEquals("skill_spend", r.getString("type"))
            assertEquals("trigger-v4", r.getString("policy"))
            assertTrue(r.has("ts"))
        }

        @Test
        fun `an empty proposed or confirmed list is omitted rather than written as an empty array`() {
            val r = record(proposed = emptyList(), confirmed = emptyList(), skipped = emptyList())
            assertFalse(r.has("proposed"))
            assertFalse(r.has("confirmed"))
            assertFalse(r.has("skipped"))
        }
    }

    @Nested
    @DisplayName("Phase 2A fields (trigger-v3)")
    inner class Phase2AFieldTests {
        @Test
        fun `a CRITICAL_RACE record carries the race rationale and the objective`() {
            val r =
                record(
                    trigger = SkillCheckTrigger.CRITICAL_RACE,
                    objective = "race_reward",
                    criticalRace = "Kashiwa Kinen",
                    criticalRaceSource = "goal_ocr",
                    turnsUntilRace = 2,
                )
            assertEquals("CRITICAL_RACE", r.getString("trigger"))
            assertEquals("race_reward", r.getString("objective"))
            assertEquals("Kashiwa Kinen", r.getString("criticalRace"))
            assertEquals("goal_ocr", r.getString("criticalRaceSource"))
            assertEquals(2, r.getInt("turnsUntilRace"))
            assertFalse(r.has("plannedSkill"), "affordable fields never appear on a critical-race record")
        }

        @Test
        fun `a PLANNED_SKILL_AFFORDABLE record carries the observed-skill rationale`() {
            val r =
                record(
                    trigger = SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE,
                    objective = "sparks",
                    plannedSkill = "Swinging Maestro",
                    plannedSkillObservedPrice = 274,
                )
            assertEquals("PLANNED_SKILL_AFFORDABLE", r.getString("trigger"))
            assertEquals("sparks", r.getString("objective"))
            assertEquals("Swinging Maestro", r.getString("plannedSkill"))
            assertEquals(274, r.getInt("plannedSkillObservedPrice"))
            assertFalse(r.has("criticalRace"), "critical fields never appear on an affordable record")
        }

        @Test
        fun `objective rides on ordinary triggers too, and absent v3 fields stay omitted`() {
            val r = record(trigger = SkillCheckTrigger.HIGH_WATER, objective = "rank")
            assertEquals("rank", r.getString("objective"))
            for (key in listOf("criticalRace", "criticalRaceSource", "turnsUntilRace", "plannedSkill", "plannedSkillObservedPrice")) {
                assertFalse(r.has(key), "\"$key\" must be omitted when not passed")
            }
        }
    }

    @Nested
    @DisplayName("planner shaping (trigger-v4)")
    inner class PlannerShapingTests {
        @Test
        fun `a planned-only sparks session records the disabled tail`() {
            val r = record(objective = "sparks", strategyTailAllowed = false)
            assertFalse(r.getBoolean("strategyTailAllowed"))
            assertEquals("sparks", r.getString("objective"))
        }

        @Test
        fun `an allowed tail is recorded as true, including for manual-mode records`() {
            assertTrue(record(objective = "rank", strategyTailAllowed = true).getBoolean("strategyTailAllowed"))
            // Manual mode: the objective never gates the tail, so its records carry true.
            assertTrue(record(tier = "manual", strategyTailAllowed = true).getBoolean("strategyTailAllowed"))
        }

        @Test
        fun `the field is absent when the planner never resolved it`() {
            val r = record(outcome = SkillSpendOutcome.FAILED, strategyTailAllowed = null)
            assertFalse(r.has("strategyTailAllowed"))
        }

        @Test
        fun `the field never disturbs the trigger or the v3 rationale`() {
            val r =
                record(
                    trigger = SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE,
                    objective = "sparks",
                    plannedSkill = "Hydrate",
                    plannedSkillObservedPrice = 180,
                    strategyTailAllowed = false,
                )
            assertEquals("PLANNED_SKILL_AFFORDABLE", r.getString("trigger"))
            assertEquals("Hydrate", r.getString("plannedSkill"))
            assertEquals(180, r.getInt("plannedSkillObservedPrice"))
            assertFalse(r.getBoolean("strategyTailAllowed"))
        }
    }

    @Nested
    @DisplayName("threshold policy fields (trigger-v2)")
    inner class ThresholdPolicyTests {
        @Test
        fun `a manual-mode record carries threshold, tier and reason alongside a distinct trigger`() {
            val r = record(trigger = SkillCheckTrigger.SCENARIO_FINALS, threshold = 1000, tier = "manual", reason = "manual threshold 1000")
            assertEquals("trigger-v4", r.getString("policy"))
            assertEquals(1000, r.getInt("threshold"))
            assertEquals("manual", r.getString("tier"))
            assertEquals("manual threshold 1000", r.getString("reason"))
            // The trigger stays the spend cause; the reason stays the resolution story.
            assertEquals("SCENARIO_FINALS", r.getString("trigger"))
        }

        @Test
        fun `an adaptive record carries the resolved tier, never the raw auto value`() {
            val resolved = resolveSkillThreshold(SkillSpendMode.ADAPTIVE, AccountTier.ESTABLISHED, 350)
            val r = record(trigger = SkillCheckTrigger.HIGH_WATER, threshold = resolved.value, tier = resolved.tierToken(), reason = resolved.reason)
            assertEquals(600, r.getInt("threshold"))
            assertEquals("established", r.getString("tier"))
            assertEquals("adaptive threshold 600 (established)", r.getString("reason"))
            assertEquals("HIGH_WATER", r.getString("trigger"))
        }

        @Test
        fun `absent policy fields are omitted, matching every other optional field`() {
            val r = record(threshold = null, tier = null, reason = null)
            for (key in listOf("threshold", "tier", "reason")) {
                assertFalse(r.has(key), "\"$key\" must be omitted when unknown, not defaulted")
            }
        }

        @Test
        fun `the confirmation-gap arbiter ignores the policy fields entirely`() {
            // Same arithmetic as before trigger-v2: the new fields are attribution, not evidence.
            val proposed = listOf(ProposedSkill("A", 300), ProposedSkill("B", 263))
            assertTrue(SkillSpendTelemetry.confirmationIsIncomplete(proposed, setOf("A"), spBefore = 567, spAfter = 4))
            assertFalse(SkillSpendTelemetry.confirmationIsIncomplete(proposed, setOf("A", "B"), spBefore = 567, spAfter = 4))
        }
    }

    @Nested
    @DisplayName("proposed vs confirmed")
    inner class ProposedConfirmedTests {
        @Test
        fun `confirmed can be a strict subset of proposed`() {
            val r =
                record(
                    proposed = listOf(ProposedSkill("A", 100), ProposedSkill("B", 100)),
                    confirmed = listOf("A"),
                    skipped = listOf(SkippedSkill("B", SkillSpendTelemetry.SKIP_UNBOUGHT)),
                )
            assertEquals(2, r.getJSONArray("proposed").length())
            assertEquals(1, r.getJSONArray("confirmed").length())
            assertEquals("A", r.getJSONArray("confirmed").getString(0))
            assertEquals("B", r.getJSONArray("skipped").getJSONObject(0).getString("name"))
        }

        @Test
        fun `a session that proposed skills but confirmed none records the proposal only`() {
            val r = record(proposed = listOf(ProposedSkill("A", 100)), confirmed = emptyList())
            assertEquals(1, r.getJSONArray("proposed").length())
            assertFalse(r.has("confirmed"), "no evidence of a purchase means no confirmed array")
        }
    }

    @Nested
    @DisplayName("deriveSkipped()")
    inner class DeriveSkippedTests {
        private val proposed = listOf(ProposedSkill("Cheap", 50), ProposedSkill("Pricey", 300), ProposedSkill("Unseen", 100))

        @Test
        fun `a confirmed skill is never reported as skipped`() {
            val skipped = SkillSpendTelemetry.deriveSkipped(proposed, setOf("Cheap", "Pricey", "Unseen"), emptyMap(), 0)
            assertTrue(skipped.isEmpty())
        }

        @Test
        fun `a live price above the points left is provably unaffordable`() {
            val skipped = SkillSpendTelemetry.deriveSkipped(proposed, setOf("Cheap", "Unseen"), mapOf("Pricey" to 300), 58)
            assertEquals(1, skipped.size)
            assertEquals("Pricey", skipped[0].name)
            assertEquals(SkillSpendTelemetry.SKIP_UNAFFORDABLE, skipped[0].reason)
        }

        @Test
        fun `an affordable but unbought skill is only reported as unbought`() {
            // The pass cannot prove WHY (missed scroll vs dropped tap), so it does not claim to.
            val skipped = SkillSpendTelemetry.deriveSkipped(proposed, setOf("Pricey", "Unseen"), mapOf("Cheap" to 50), 400)
            assertEquals(1, skipped.size)
            assertEquals(SkillSpendTelemetry.SKIP_UNBOUGHT, skipped[0].reason)
        }

        @Test
        fun `a skill never seen on screen has no price and is reported as unbought`() {
            val skipped = SkillSpendTelemetry.deriveSkipped(proposed, setOf("Cheap", "Pricey"), mapOf("Cheap" to 50, "Pricey" to 300), 0)
            assertEquals(1, skipped.size)
            assertEquals("Unseen", skipped[0].name)
            assertEquals(SkillSpendTelemetry.SKIP_UNBOUGHT, skipped[0].reason, "an unknown price must not be called unaffordable")
        }

        @Test
        fun `nothing proposed means nothing skipped`() {
            assertTrue(SkillSpendTelemetry.deriveSkipped(emptyList(), emptySet(), emptyMap(), 0).isEmpty())
        }
    }

    @Nested
    @DisplayName("confirmationIsIncomplete()")
    inner class ConfirmationIsIncompleteTests {
        // The live careerComplete session that exposed the bug (Super Creek / Unity Cup, 2026-07-16):
        // the knapsack planned 563 points against a 567-point screen budget and the screen-read total
        // fell by exactly 563 - so all five were bought, while the obtained set reported only three.
        private val liveProposed =
            listOf(
                ProposedSkill("Disorient", 110),
                ProposedSkill("Dazzling Disorientation", 110),
                ProposedSkill("Pace Strategy", 170),
                ProposedSkill("Indomitable", 119),
                ProposedSkill("Standard Distance ○", 54),
            )
        private val liveConfirmed = setOf("Dazzling Disorientation", "Indomitable", "Standard Distance ○")

        @Test
        fun `spending more than the confirmed skills cost proves the confirmation missed a purchase`() {
            // 567 - 4 = 563 spent, but the three confirmed skills only account for 283.
            assertTrue(SkillSpendTelemetry.confirmationIsIncomplete(liveProposed, liveConfirmed, 567, 4))
        }

        @Test
        fun `an incomplete confirmation suppresses every skip reason`() {
            // The regression: two skills that WERE bought had been reported skipped as unaffordable.
            val incomplete = SkillSpendTelemetry.confirmationIsIncomplete(liveProposed, liveConfirmed, 567, 4)
            val skipped =
                if (incomplete) {
                    emptyList()
                } else {
                    SkillSpendTelemetry.deriveSkipped(liveProposed, liveConfirmed, emptyMap(), 4)
                }
            assertTrue(skipped.isEmpty(), "points prove these were bought; naming them skipped is fabrication")
        }

        @Test
        fun `a delta fully accounted for by the confirmed skills is complete`() {
            // The healthy high-water session from the same career: 365 - 25 = 340 = 180 + 160.
            val proposed = listOf(ProposedSkill("A Kiss for Courage", 180), ProposedSkill("Anchors Aweigh!", 160))
            val confirmed = setOf("A Kiss for Courage", "Anchors Aweigh!")
            assertFalse(SkillSpendTelemetry.confirmationIsIncomplete(proposed, confirmed, 365, 25))
        }

        @Test
        fun `a genuinely unbought skill still yields a skip reason when the delta reconciles`() {
            // 300 - 250 = 50 spent, exactly the confirmed skill's price, so "Pricey" really was not bought.
            val proposed = listOf(ProposedSkill("Cheap", 50), ProposedSkill("Pricey", 300))
            assertFalse(SkillSpendTelemetry.confirmationIsIncomplete(proposed, setOf("Cheap"), 300, 250))
        }

        @Test
        fun `an unknown points total proves nothing`() {
            assertFalse(SkillSpendTelemetry.confirmationIsIncomplete(liveProposed, liveConfirmed, null, 4))
            assertFalse(SkillSpendTelemetry.confirmationIsIncomplete(liveProposed, liveConfirmed, 567, null))
        }

        @Test
        fun `a session that spent nothing is never incomplete`() {
            assertFalse(SkillSpendTelemetry.confirmationIsIncomplete(liveProposed, emptySet(), 400, 400))
        }

        @Test
        fun `the record carries the flag only when the gap is proven`() {
            val flagged =
                SkillSpendTelemetry.buildRecord(
                    timestamp = 1L,
                    outcome = SkillSpendOutcome.COMMITTED,
                    trigger = SkillCheckTrigger.CAREER_COMPLETE,
                    planKey = PLAN_CAREER_COMPLETE,
                    strategy = null,
                    trainee = null,
                    scenario = null,
                    fp = null,
                    turn = null,
                    spBefore = 567,
                    spAfter = 4,
                    proposed = liveProposed,
                    confirmed = liveConfirmed.toList(),
                    skipped = emptyList(),
                    confirmedIncomplete = true,
                )
            assertTrue(flagged.getBoolean("confirmedIncomplete"))
            assertFalse(flagged.has("skipped"), "a flagged gap must not also name skills as skipped")

            val clean =
                SkillSpendTelemetry.buildRecord(
                    timestamp = 1L,
                    outcome = SkillSpendOutcome.COMMITTED,
                    trigger = SkillCheckTrigger.HIGH_WATER,
                    planKey = PLAN_SKILL_POINT_CHECK,
                    strategy = null,
                    trainee = null,
                    scenario = null,
                    fp = null,
                    turn = null,
                    spBefore = 365,
                    spAfter = 25,
                    proposed = emptyList(),
                    confirmed = emptyList(),
                    skipped = emptyList(),
                )
            assertFalse(clean.has("confirmedIncomplete"), "absence means no gap proven, so the key stays off")
        }
    }
}

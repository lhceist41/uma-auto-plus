package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.TrackDistance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [resolveSkillThreshold] - the V1 account-adaptive threshold seam.
 *
 * The invariants worth pinning: manual mode is a bit-for-bit passthrough of the configured
 * threshold (the opt-out must equal the pre-adaptive bot exactly), the tier table is fixed and
 * monotonic, AUTO is a fixed alias for DEVELOPING in V1, unknown persisted strings fall back to
 * the safe pair (Manual + Auto), and the resolved value drives [decideSkillCheck] at exactly the
 * same boundaries as a hand-configured threshold - adaptive Endgame must be indistinguishable
 * from the proven manual-1000 production arm.
 */
@DisplayName("Adaptive skill threshold policy")
class AdaptiveSkillPolicyTest {
    @Nested
    @DisplayName("manual passthrough")
    inner class ManualPassthrough {
        @Test
        fun `manual mode returns the configured threshold untouched for every tier`() {
            for (tier in AccountTier.entries) {
                for (threshold in listOf(0, 300, 350, 600, 1000, 777)) {
                    val resolved = resolveSkillThreshold(SkillSpendMode.MANUAL, tier, threshold)
                    assertEquals(threshold, resolved.value, "manual + $tier must pass $threshold through")
                    assertEquals("manual threshold $threshold", resolved.reason)
                    assertEquals("manual", resolved.tierToken(), "no tier governs a manual career")
                }
            }
        }

        @Test
        fun `manual mode does not clamp - even a zero threshold passes through`() {
            // 0 is a real configuration (the "spend immediately" chip); clamping it would change
            // behavior for existing users, which V1 must never do.
            assertEquals(0, resolveSkillThreshold(SkillSpendMode.MANUAL, AccountTier.AUTO, 0).value)
        }
    }

    @Nested
    @DisplayName("adaptive tier table")
    inner class AdaptiveTable {
        @Test
        fun `auto is a fixed alias for developing in V1`() {
            val resolved = resolveSkillThreshold(SkillSpendMode.ADAPTIVE, AccountTier.AUTO, 9999)
            assertEquals(350, resolved.value)
            assertEquals(AccountTier.DEVELOPING, resolved.resolvedTier)
            assertEquals("developing", resolved.tierToken(), "the corpus records the RESOLVED tier")
            assertEquals("adaptive threshold 350 (auto -> developing)", resolved.reason)
        }

        @Test
        fun `each explicit tier maps to its table value regardless of the manual threshold`() {
            val expected =
                mapOf(
                    AccountTier.NEW to 300,
                    AccountTier.DEVELOPING to 350,
                    AccountTier.ESTABLISHED to 600,
                    AccountTier.ENDGAME to 1000,
                )
            for ((tier, value) in expected) {
                val resolved = resolveSkillThreshold(SkillSpendMode.ADAPTIVE, tier, manualThreshold = 42)
                assertEquals(value, resolved.value, "$tier")
                assertEquals(tier, resolved.resolvedTier)
                assertEquals("adaptive threshold $value (${tier.name.lowercase()})", resolved.reason)
            }
        }

        @Test
        fun `thresholds are monotonic across account strength`() {
            val ladder = listOf(AccountTier.NEW, AccountTier.DEVELOPING, AccountTier.ESTABLISHED, AccountTier.ENDGAME).map { adaptiveThresholdFor(it) }
            for (i in 1 until ladder.size) {
                assertTrue(ladder[i] >= ladder[i - 1], "tier ladder must never spend later on a weaker account: $ladder")
            }
        }

        @Test
        fun `endgame matches the proven manual-1000 production arm exactly`() {
            assertEquals(
                resolveSkillThreshold(SkillSpendMode.MANUAL, AccountTier.AUTO, 1000).value,
                resolveSkillThreshold(SkillSpendMode.ADAPTIVE, AccountTier.ENDGAME, 350).value,
            )
        }
    }

    @Nested
    @DisplayName("persisted-string parsing")
    inner class Parsing {
        @Test
        fun `unknown or empty mode strings fall back to manual`() {
            for (raw in listOf("", "garbage", "adaptive2", "AUTO", "null", " on ")) {
                assertEquals(SkillSpendMode.MANUAL, SkillSpendMode.fromPersisted(raw), "\"$raw\"")
            }
        }

        @Test
        fun `mode parsing tolerates case and whitespace`() {
            for (raw in listOf("adaptive", "ADAPTIVE", " Adaptive ")) {
                assertEquals(SkillSpendMode.ADAPTIVE, SkillSpendMode.fromPersisted(raw), "\"$raw\"")
            }
            assertEquals(SkillSpendMode.MANUAL, SkillSpendMode.fromPersisted("manual"))
        }

        @Test
        fun `unknown tier strings fall back to auto`() {
            for (raw in listOf("", "garbage", "endgame+", "ug", "rank-f")) {
                assertEquals(AccountTier.AUTO, AccountTier.fromPersisted(raw), "\"$raw\"")
            }
        }

        @Test
        fun `tier parsing tolerates case and whitespace`() {
            assertEquals(AccountTier.ENDGAME, AccountTier.fromPersisted(" Endgame "))
            assertEquals(AccountTier.NEW, AccountTier.fromPersisted("NEW"))
            assertEquals(AccountTier.DEVELOPING, AccountTier.fromPersisted("developing"))
            assertEquals(AccountTier.ESTABLISHED, AccountTier.fromPersisted("established"))
            assertEquals(AccountTier.AUTO, AccountTier.fromPersisted("auto"))
        }
    }

    @Nested
    @DisplayName("Phase 2A: objective parsing and gating")
    inner class ObjectiveTests {
        @Test
        fun `blank and unknown persisted values fall back to rank`() {
            for (raw in listOf(null, "", "garbage", "RACE-REWARD", "competitive", "event_farm")) {
                assertEquals(SkillSpendObjective.RANK, SkillSpendObjective.fromPersisted(raw), "\"$raw\"")
            }
        }

        @Test
        fun `known values parse case-insensitively`() {
            assertEquals(SkillSpendObjective.RACE_REWARD, SkillSpendObjective.fromPersisted(" race_reward "))
            assertEquals(SkillSpendObjective.SAFE_COMPLETION, SkillSpendObjective.fromPersisted("SAFE_COMPLETION"))
            assertEquals(SkillSpendObjective.SPARKS, SkillSpendObjective.fromPersisted("sparks"))
            assertEquals(SkillSpendObjective.RANK, SkillSpendObjective.fromPersisted("rank"))
        }

        @Test
        fun `gates match the 2A contract exactly`() {
            assertFalse(SkillSpendObjective.RANK.allowsCriticalRace())
            assertFalse(SkillSpendObjective.RANK.allowsPlannedSkillAffordable())
            assertTrue(SkillSpendObjective.SAFE_COMPLETION.allowsCriticalRace())
            assertTrue(SkillSpendObjective.SAFE_COMPLETION.allowsPlannedSkillAffordable())
            assertTrue(SkillSpendObjective.RACE_REWARD.allowsCriticalRace())
            assertTrue(SkillSpendObjective.RACE_REWARD.allowsPlannedSkillAffordable())
            assertFalse(SkillSpendObjective.SPARKS.allowsCriticalRace(), "sparks gets the critical-race arm only with 2B purity work")
            assertTrue(SkillSpendObjective.SPARKS.allowsPlannedSkillAffordable())
        }
    }

    @Nested
    @DisplayName("2B-1: strategy-tail gate")
    inner class StrategyTailGateTests {
        @Test
        fun `sparks is the only objective without a tail`() {
            assertFalse(SkillSpendObjective.SPARKS.allowsStrategyTail())
            assertTrue(SkillSpendObjective.RANK.allowsStrategyTail())
            assertTrue(SkillSpendObjective.SAFE_COMPLETION.allowsStrategyTail())
            assertTrue(SkillSpendObjective.RACE_REWARD.allowsStrategyTail())
        }

        @Test
        fun `manual mode always allows the tail regardless of objective`() {
            for (objective in SkillSpendObjective.entries) {
                assertTrue(strategyTailAllowed(SkillSpendMode.MANUAL, objective), objective.name)
            }
        }

        @Test
        fun `adaptive mode delegates to the objective`() {
            assertFalse(strategyTailAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SPARKS))
            assertTrue(strategyTailAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.RANK))
            assertTrue(strategyTailAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SAFE_COMPLETION))
            assertTrue(strategyTailAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.RACE_REWARD))
        }

        @Test
        fun `the career-end fallback fires only for adaptive sparks at CAREER_COMPLETE`() {
            assertTrue(
                careerEndConstrainedFallbackAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SPARKS, SkillCheckTrigger.CAREER_COMPLETE),
                "the one qualifying combination",
            )
            // Mid-career sparks sessions stay planned-only: no fallback on any other trigger.
            for (trigger in SkillCheckTrigger.entries.filter { it != SkillCheckTrigger.CAREER_COMPLETE }) {
                assertFalse(
                    careerEndConstrainedFallbackAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SPARKS, trigger),
                    "sparks at $trigger must stay planned-only",
                )
            }
            assertFalse(
                careerEndConstrainedFallbackAllowed(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SPARKS, null),
                "an unresolved trigger never extends the session",
            )
        }

        @Test
        fun `rank, safe_completion, and race_reward never route through the fallback - their tails are unchanged`() {
            for (objective in listOf(SkillSpendObjective.RANK, SkillSpendObjective.SAFE_COMPLETION, SkillSpendObjective.RACE_REWARD)) {
                for (trigger in SkillCheckTrigger.entries) {
                    assertFalse(careerEndConstrainedFallbackAllowed(SkillSpendMode.ADAPTIVE, objective, trigger), "$objective at $trigger")
                }
                // Their full strategy tail remains allowed exactly as before the guard existed.
                assertTrue(strategyTailAllowed(SkillSpendMode.ADAPTIVE, objective), "$objective tail unchanged")
            }
        }

        @Test
        fun `manual mode never routes through the fallback for any objective or trigger`() {
            for (objective in SkillSpendObjective.entries) {
                for (trigger in SkillCheckTrigger.entries) {
                    assertFalse(careerEndConstrainedFallbackAllowed(SkillSpendMode.MANUAL, objective, trigger), "$objective at $trigger")
                }
            }
        }

        @Test
        fun `an unknown persisted objective resolves to rank and keeps the tail`() {
            val parsed = SkillSpendObjective.fromPersisted("mystery")
            assertEquals(SkillSpendObjective.RANK, parsed)
            assertTrue(strategyTailAllowed(SkillSpendMode.ADAPTIVE, parsed))
        }
    }

    @Nested
    @DisplayName("2B-2: recovery classification and gate")
    inner class RecoveryPolicyTests {
        @Test
        fun `icon families classify exactly`() {
            assertEquals(RecoveryClass.WHITE, recoveryClassOf(20021))
            assertEquals(RecoveryClass.GOLD, recoveryClassOf(20022))
            assertEquals(RecoveryClass.NONE, recoveryClassOf(20024), "the debuff family is never a recovery for injection")
            for (icon in listOf(20011, 20012, 20014, 20041, 20042, 20051, 10011, 0)) {
                assertEquals(RecoveryClass.NONE, recoveryClassOf(icon), "icon $icon")
            }
        }

        @Test
        fun `the general allow-list pins exactly the verified corner and straightaway pairs`() {
            assertEquals(setOf(200352, 200351, 200382, 200381), GENERAL_RECOVERY_IDS)
        }

        @Test
        fun `the gate matrix matches the 2B-2 contract`() {
            val long = TrackDistance.LONG
            val medium = TrackDistance.MEDIUM
            assertTrue(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SAFE_COMPLETION, long))
            assertTrue(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, SkillSpendObjective.RACE_REWARD, long))
            assertTrue(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SAFE_COMPLETION, medium))
            assertFalse(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, SkillSpendObjective.RACE_REWARD, medium))
            assertFalse(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, SkillSpendObjective.RANK, long))
            assertFalse(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, SkillSpendObjective.SPARKS, long))
            for (objective in SkillSpendObjective.entries) {
                assertFalse(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, objective, TrackDistance.SPRINT), objective.name)
                assertFalse(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, objective, TrackDistance.MILE), objective.name)
                assertFalse(allowsRecoveryInjection(SkillSpendMode.ADAPTIVE, objective, null), "unknown distance, ${objective.name}")
                assertFalse(allowsRecoveryInjection(SkillSpendMode.MANUAL, objective, long), "manual, ${objective.name}")
                assertFalse(allowsRecoveryInjection(SkillSpendMode.MANUAL, objective, medium), "manual, ${objective.name}")
            }
        }
    }

    @Nested
    @DisplayName("Phase 2A: goal-text classification and race matching")
    inner class GoalClassificationTests {
        private val races = listOf("Kashiwa Kinen", "Japan Dirt Derby", "JBC Classic", "Japan Cup", "Tokyo Yushun (Japanese Derby)")

        @Test
        fun `the Kashiwa goal text classifies as a race with the right name`() {
            val (kind, race) = classifyGoalText("Place top 3 in Kashiwa Kinen", races)
            assertEquals(GoalKind.RACE, kind)
            assertEquals("Kashiwa Kinen", race)
        }

        @Test
        fun `OCR line breaks and punctuation normalize away`() {
            assertEquals("Kashiwa Kinen", matchGoalRace("Place top 3 in\nKashiwa   Kinen!", races))
            assertEquals("Tokyo Yushun (Japanese Derby)", matchGoalRace("place 5th or better in tokyo yushun japanese derby", races))
        }

        @Test
        fun `the longest overlapping known name wins`() {
            val overlapping = listOf("Japan Cup", "Japan Cup Trial")
            assertEquals("Japan Cup Trial", matchGoalRace("Place top 3 in Japan Cup Trial", overlapping))
            assertEquals("Japan Cup", matchGoalRace("Place top 3 in Japan Cup", overlapping))
        }

        @Test
        fun `garbled partial text fails inertly instead of guessing`() {
            assertNull(matchGoalRace("Plce top 3 in Kashwa Kinn", races))
            assertEquals(GoalKind.OTHER, classifyGoalText("Plce top 3 in Kashwa Kinn", races).first)
        }

        @Test
        fun `fan and Result-Pt goals are classified for their own emergencies, never as races`() {
            assertEquals(GoalKind.FANS, classifyGoalText("Acquire 3,000 fans", races).first)
            assertEquals(GoalKind.RESULT_PTS, classifyGoalText("Earn 60 Result Pt", races).first)
            assertEquals(GoalKind.OTHER, classifyGoalText("Result Pt goal Achieved", listOf("Achieved Cup")).first)
        }

        @Test
        fun `blank text is UNKNOWN and a non-race goal is OTHER`() {
            assertEquals(GoalKind.UNKNOWN, classifyGoalText(null, races).first)
            assertEquals(GoalKind.UNKNOWN, classifyGoalText("  ", races).first)
            assertEquals(GoalKind.OTHER, classifyGoalText("Make your fated rival submit", races).first)
        }

        @Test
        fun `an empty candidate list keeps the matcher inert`() {
            assertNull(matchGoalRace("Place top 3 in Kashiwa Kinen", emptyList()))
        }
    }

    @Nested
    @DisplayName("Phase 2A: planned-skill evidence lifecycle")
    inner class EvidenceTests {
        private val plan = listOf("Professor of Curvature", "Swinging Maestro", "Groundwork")

        @Test
        fun `no trigger before the first observed parse`() {
            val store = PlannedSkillEvidenceStore()
            assertFalse(store.hasAnyObservation())
            assertNull(store.affordableCandidate(plan, skillPoints = 5000))
        }

        @Test
        fun `an observed affordable planned skill qualifies, an unaffordable one does not`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Swinging Maestro" to 342, "Groundwork" to 120), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertEquals("Groundwork" to 120, store.affordableCandidate(plan, skillPoints = 200))
            assertNull(store.affordableCandidate(plan, skillPoints = 100), "119 SP cannot afford the 120 observation")
            assertEquals("Swinging Maestro" to 342, store.affordableCandidate(plan, skillPoints = 400), "highest observed price wins when several qualify")
        }

        @Test
        fun `a never-observed Potential-gated skill never qualifies`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertNull(store.affordableCandidate(listOf("Chance of Victory"), skillPoints = 9999))
        }

        @Test
        fun `a confirmed purchase removes the skill from candidacy`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            store.recordParse(mapOf("Swinging Maestro" to 342), parseTurn = 41, fromAffordableSession = false, confirmedPurchases = listOf("Groundwork"))
            assertNull(store.affordableCandidate(listOf("Groundwork"), skillPoints = 9999))
        }

        @Test
        fun `a no-buy affordable session suppresses until an organic parse refreshes`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertEquals("Groundwork" to 120, store.affordableCandidate(plan, skillPoints = 200))
            // The affordable session ran and bought nothing: its own parse suppresses the skill.
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 41, fromAffordableSession = true, confirmedPurchases = emptyList())
            assertNull(store.affordableCandidate(plan, skillPoints = 5000))
            // A later organic parse (any other trigger) lifts the suppression.
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 45, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertEquals("Groundwork" to 120, store.affordableCandidate(plan, skillPoints = 5000))
        }

        @Test
        fun `the SP-growth belt bounds repeated firings regardless of outcome`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            store.markAffordableFired(skillPoints = 200)
            assertNull(store.affordableCandidate(plan, skillPoints = 200 + AFFORDABLE_REARM_SP_GROWTH - 1))
            assertEquals("Groundwork" to 120, store.affordableCandidate(plan, skillPoints = 200 + AFFORDABLE_REARM_SP_GROWTH))
        }

        @Test
        fun `a later parse replaces prices - hint discounts only lower them`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Swinging Maestro" to 342), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            store.recordParse(mapOf("Swinging Maestro" to 274), parseTurn = 50, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertEquals("Swinging Maestro" to 274, store.affordableCandidate(plan, skillPoints = 300))
        }

        @Test
        fun `a skill that unlocks later becomes eligible after the next organic parse`() {
            val store = PlannedSkillEvidenceStore()
            store.recordParse(mapOf("Groundwork" to 120), parseTurn = 40, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertNull(store.affordableCandidate(listOf("Swinging Maestro"), skillPoints = 9999))
            store.recordParse(mapOf("Groundwork" to 120, "Swinging Maestro" to 342), parseTurn = 55, fromAffordableSession = false, confirmedPurchases = emptyList())
            assertEquals("Swinging Maestro" to 342, store.affordableCandidate(listOf("Swinging Maestro"), skillPoints = 9999))
        }
    }

    @Nested
    @DisplayName("Phase 2A: trigger precedence in decideSkillCheck")
    inner class Phase2ATriggerPrecedence {
        private fun decide(
            criticalRaceDue: Boolean = false,
            affordableSkillDue: Boolean = false,
            skillPoints: Int = 500,
            threshold: Int = 1000,
            highWaterPlanEnabled: Boolean = true,
            day: Int = 40,
            preFinalsPlanEnabled: Boolean = true,
            alreadyHandledPreFinals: Boolean = false,
        ) = decideSkillCheck(
            skillPoints = skillPoints,
            highWaterThreshold = threshold,
            enableSkillPointCheck = true,
            highWaterPlanEnabled = highWaterPlanEnabled,
            alreadyHandledHighWater = false,
            day = day,
            preFinalsPlanEnabled = preFinalsPlanEnabled,
            alreadyHandledPreFinals = alreadyHandledPreFinals,
            criticalRaceDue = criticalRaceDue,
            affordableSkillDue = affordableSkillDue,
        )

        @Test
        fun `finals beats critical which beats affordable which beats high-water`() {
            val allDue = decide(criticalRaceDue = true, affordableSkillDue = true, skillPoints = 5000, day = PRE_FINALS_DAY)
            assertEquals(SkillCheckTrigger.SCENARIO_FINALS, allDue.trigger)
            val critAndAffordable = decide(criticalRaceDue = true, affordableSkillDue = true, skillPoints = 5000)
            assertEquals(SkillCheckTrigger.CRITICAL_RACE, critAndAffordable.trigger)
            val affordableAndHighWater = decide(affordableSkillDue = true, skillPoints = 5000)
            assertEquals(SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE, affordableAndHighWater.trigger)
            assertEquals(SkillCheckTrigger.HIGH_WATER, decide(skillPoints = 5000).trigger)
        }

        @Test
        fun `both 2A triggers run the skillPointCheck plan`() {
            assertEquals(PLAN_SKILL_POINT_CHECK, decide(criticalRaceDue = true).planKey)
            assertEquals(PLAN_SKILL_POINT_CHECK, decide(affordableSkillDue = true).planKey)
        }

        @Test
        fun `a disabled skillPointCheck plan disables both 2A triggers and keeps the breakpoint stop`() {
            assertEquals(SkillCheckAction.NONE, decide(criticalRaceDue = true, affordableSkillDue = true, highWaterPlanEnabled = false).action)
            val breakpoint = decide(skillPoints = 1200, highWaterPlanEnabled = false)
            assertEquals(SkillCheckAction.BREAKPOINT_STOP, breakpoint.action)
            assertEquals(SkillCheckTrigger.HIGH_WATER, breakpoint.trigger)
        }

        @Test
        fun `the defaulted call reproduces V1 behavior exactly`() {
            for (sp in listOf(0, 999, 1000, 1495)) {
                val v1 =
                    decideSkillCheck(
                        skillPoints = sp,
                        highWaterThreshold = 1000,
                        enableSkillPointCheck = true,
                        highWaterPlanEnabled = true,
                        alreadyHandledHighWater = false,
                        day = 40,
                        preFinalsPlanEnabled = true,
                        alreadyHandledPreFinals = false,
                    )
                val expected = if (sp >= 1000) SkillCheckAction.RUN_PLAN else SkillCheckAction.NONE
                assertEquals(expected, v1.action, "sp=$sp")
            }
        }
    }

    @Nested
    @DisplayName("resolved value drives decideSkillCheck unchanged")
    inner class TriggerRegression {
        private fun highWaterDecision(skillPoints: Int, threshold: Int): SkillCheckDecision =
            decideSkillCheck(
                skillPoints = skillPoints,
                highWaterThreshold = threshold,
                enableSkillPointCheck = true,
                highWaterPlanEnabled = true,
                alreadyHandledHighWater = false,
                day = 40,
                preFinalsPlanEnabled = true,
                alreadyHandledPreFinals = false,
            )

        @Test
        fun `adaptive endgame produces the same high-water boundary as manual 1000`() {
            val manual = resolveSkillThreshold(SkillSpendMode.MANUAL, AccountTier.AUTO, 1000)
            val adaptive = resolveSkillThreshold(SkillSpendMode.ADAPTIVE, AccountTier.ENDGAME, 350)
            for (sp in listOf(0, 999, 1000, 1001, 1495)) {
                assertEquals(
                    highWaterDecision(sp, manual.value).action,
                    highWaterDecision(sp, adaptive.value).action,
                    "sp=$sp must decide identically under manual-1000 and adaptive-endgame",
                )
            }
            assertEquals(SkillCheckAction.NONE, highWaterDecision(999, adaptive.value).action)
            assertEquals(SkillCheckAction.RUN_PLAN, highWaterDecision(1000, adaptive.value).action)
        }

        @Test
        fun `finals still outrank the high-water check under a resolved threshold`() {
            val resolved = resolveSkillThreshold(SkillSpendMode.ADAPTIVE, AccountTier.NEW, 350)
            val decision =
                decideSkillCheck(
                    skillPoints = 5000,
                    highWaterThreshold = resolved.value,
                    enableSkillPointCheck = true,
                    highWaterPlanEnabled = true,
                    alreadyHandledHighWater = false,
                    day = PRE_FINALS_DAY,
                    preFinalsPlanEnabled = true,
                    alreadyHandledPreFinals = false,
                )
            assertEquals(SkillCheckTrigger.SCENARIO_FINALS, decision.trigger)
        }

        @Test
        fun `breakpoint-stop still fires when the high-water plan is disabled`() {
            val resolved = resolveSkillThreshold(SkillSpendMode.ADAPTIVE, AccountTier.ESTABLISHED, 350)
            val decision =
                decideSkillCheck(
                    skillPoints = resolved.value,
                    highWaterThreshold = resolved.value,
                    enableSkillPointCheck = true,
                    highWaterPlanEnabled = false,
                    alreadyHandledHighWater = false,
                    day = 40,
                    preFinalsPlanEnabled = true,
                    alreadyHandledPreFinals = false,
                )
            assertEquals(SkillCheckAction.BREAKPOINT_STOP, decision.action)
        }
    }
}

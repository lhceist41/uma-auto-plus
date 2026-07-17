package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
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

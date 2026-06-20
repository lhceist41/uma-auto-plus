package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.SkillCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateCommonPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateOptimizeRankPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateSkillPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.isDoubleCircleUpgrade
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.matchesPreference
import com.steve1316.uma_android_automation.bot.SkillPlan.SkillPlanSettings
import com.steve1316.uma_android_automation.bot.SkillPlan.SpendingStrategy
import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Unit tests for the skill plan purchasing algorithms.
 *
 * Tests the pure companion functions on [SkillPlan] that determine which skills to buy:
 * - [SkillPlan.calculateOptimizeRankPurchases]: greedy rank-maximizing strategy.
 * - [SkillPlan.calculateCommonPurchases]: phased buying (negative, inherited unique, user-planned).
 * - [SkillPlan.calculateSkillPurchases]: full orchestrator combining common + strategy-specific logic.
 *
 * Includes randomized stress tests that generate dummy skill lists (10-100 skills) with
 * random prices, eval points, and flags, verifying budget and uniqueness invariants
 * across all three spending strategies (DEFAULT, OPTIMIZE_RANK, OPTIMIZE_SKILLS).
 */
@DisplayName("Skill Plan Purchasing Tests")
class SkillPlanPurchasingTest {
    // =========================================================================
    // SkillCandidate
    // =========================================================================

    @Nested
    @DisplayName("SkillCandidate")
    inner class SkillCandidateTests {
        @Test
        fun `evaluationPointRatio calculated correctly`() {
            val skill = SkillCandidate(name = "Test", price = 100, evaluationPoints = 50)
            assertEquals(0.5, skill.evaluationPointRatio, 0.001)
        }

        @Test
        fun `evaluationPointRatio is zero when price is zero`() {
            val skill = SkillCandidate(name = "Test", price = 0, evaluationPoints = 50)
            assertEquals(0.0, skill.evaluationPointRatio)
        }

        @Test
        fun `high eval points with low price gives high ratio`() {
            val skill = SkillCandidate(name = "Bargain", price = 50, evaluationPoints = 200)
            assertEquals(4.0, skill.evaluationPointRatio, 0.001)
        }
    }

    // =========================================================================
    // calculateOptimizeRankPurchases()
    // =========================================================================

    @Nested
    @DisplayName("calculateOptimizeRankPurchases()")
    inner class OptimizeRankTests {
        @Test
        fun `empty candidates returns empty`() {
            val result = calculateOptimizeRankPurchases(emptyList(), 1000)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `buys skill with best ratio first`() {
            val candidates =
                listOf(
                    SkillCandidate("Low Ratio", price = 100, evaluationPoints = 50), // 0.5
                    SkillCandidate("High Ratio", price = 50, evaluationPoints = 100), // 2.0
                    SkillCandidate("Mid Ratio", price = 80, evaluationPoints = 80), // 1.0
                )
            val result = calculateOptimizeRankPurchases(candidates, 1000)
            assertEquals(3, result.size)
            assertEquals("High Ratio", result[0].first) // Best ratio first
            assertEquals("Mid Ratio", result[1].first)
            assertEquals("Low Ratio", result[2].first)
        }

        @Test
        fun `respects budget constraint`() {
            val candidates =
                listOf(
                    SkillCandidate("Expensive", price = 200, evaluationPoints = 400), // 2.0
                    SkillCandidate("Cheap", price = 50, evaluationPoints = 80), // 1.6
                )
            val result = calculateOptimizeRankPurchases(candidates, 100)
            assertEquals(1, result.size)
            assertEquals("Cheap", result[0].first)
        }

        @Test
        fun `skips already planned skills`() {
            val candidates =
                listOf(
                    SkillCandidate("Skill A", price = 50, evaluationPoints = 100),
                    SkillCandidate("Skill B", price = 50, evaluationPoints = 80),
                )
            val result = calculateOptimizeRankPurchases(candidates, 1000, alreadyPlanned = listOf("Skill A"))
            assertEquals(1, result.size)
            assertEquals("Skill B", result[0].first)
        }

        @Test
        fun `skips zero-price skills`() {
            val candidates =
                listOf(
                    SkillCandidate("Free", price = 0, evaluationPoints = 100),
                    SkillCandidate("Paid", price = 50, evaluationPoints = 50),
                )
            val result = calculateOptimizeRankPurchases(candidates, 1000)
            assertEquals(1, result.size)
            assertEquals("Paid", result[0].first)
        }

        @Test
        fun `total spent does not exceed budget`() {
            val candidates =
                (1..20).map {
                    SkillCandidate("Skill_$it", price = 30 + it * 5, evaluationPoints = 40 + it * 10)
                }
            val budget = 200
            val result = calculateOptimizeRankPurchases(candidates, budget)
            val totalSpent = result.sumOf { it.second }
            assertTrue(totalSpent <= budget, "Spent $totalSpent > budget $budget")
        }

        @Test
        fun `maximizes eval points within budget`() {
            // Given equal prices, should prefer higher eval points
            val candidates =
                listOf(
                    SkillCandidate("Low EP", price = 100, evaluationPoints = 50),
                    SkillCandidate("High EP", price = 100, evaluationPoints = 150),
                )
            val result = calculateOptimizeRankPurchases(candidates, 100)
            assertEquals(1, result.size)
            assertEquals("High EP", result[0].first) // Better ratio
        }
    }

    // =========================================================================
    // calculateCommonPurchases()
    // =========================================================================

    @Nested
    @DisplayName("calculateCommonPurchases()")
    inner class CommonPurchasesTests {
        private val defaultSettings =
            SkillPlanSettings(
                bIsEnabled = true,
                strategy = SpendingStrategy.DEFAULT,
                bEnableBuyInheritedUniqueSkills = true,
                bEnableBuyNegativeSkills = true,
                skillNames = listOf("User Skill A", "User Skill B"),
            )

        @Test
        fun `buys negative skills first`() {
            val candidates =
                listOf(
                    SkillCandidate("Negative A", price = 50, evaluationPoints = 30, isNegative = true),
                    SkillCandidate("Regular", price = 50, evaluationPoints = 100),
                )
            val result = calculateCommonPurchases(candidates, 100, defaultSettings)
            assertEquals(1, result.size)
            assertEquals("Negative A", result[0].first)
        }

        @Test
        fun `skips negative skills when disabled`() {
            val settings = defaultSettings.copy(bEnableBuyNegativeSkills = false)
            val candidates =
                listOf(
                    SkillCandidate("Negative A", price = 50, evaluationPoints = 30, isNegative = true),
                )
            val result = calculateCommonPurchases(candidates, 1000, settings)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `buys inherited unique skills after negative`() {
            val candidates =
                listOf(
                    SkillCandidate("Negative", price = 30, evaluationPoints = 20, isNegative = true),
                    SkillCandidate("Inherited", price = 80, evaluationPoints = 120, isInheritedUnique = true),
                )
            val result = calculateCommonPurchases(candidates, 200, defaultSettings)
            assertEquals(2, result.size)
            assertEquals("Negative", result[0].first)
            assertEquals("Inherited", result[1].first)
        }

        @Test
        fun `skips inherited unique when disabled`() {
            val settings = defaultSettings.copy(bEnableBuyInheritedUniqueSkills = false)
            val candidates =
                listOf(
                    SkillCandidate("Inherited", price = 80, evaluationPoints = 120, isInheritedUnique = true),
                )
            val result = calculateCommonPurchases(candidates, 1000, settings)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `buys user-planned skills after inherited unique`() {
            val candidates =
                listOf(
                    SkillCandidate("Negative", price = 30, evaluationPoints = 20, isNegative = true),
                    SkillCandidate("User Skill A", price = 100, evaluationPoints = 80, isUserPlanned = true),
                    SkillCandidate("User Skill B", price = 120, evaluationPoints = 90, isUserPlanned = true),
                )
            val result = calculateCommonPurchases(candidates, 500, defaultSettings)
            assertEquals(3, result.size)
            assertEquals("Negative", result[0].first)
            assertEquals("User Skill A", result[1].first)
            assertEquals("User Skill B", result[2].first)
        }

        @Test
        fun `respects budget across all phases`() {
            val candidates =
                listOf(
                    SkillCandidate("Negative", price = 80, evaluationPoints = 20, isNegative = true),
                    SkillCandidate("Inherited", price = 80, evaluationPoints = 120, isInheritedUnique = true),
                    SkillCandidate("User Skill A", price = 80, evaluationPoints = 80, isUserPlanned = true),
                )
            val result = calculateCommonPurchases(candidates, 150, defaultSettings)
            // Can afford Negative(80) + Inherited(80) = 160 > 150
            // So only Negative(80) then can't afford Inherited(80) at 70 remaining
            assertEquals(1, result.size)
            assertEquals("Negative", result[0].first)
        }

        @Test
        fun `does not buy duplicates`() {
            val candidates =
                listOf(
                    SkillCandidate("Dual Role", price = 50, evaluationPoints = 30, isNegative = true, isInheritedUnique = true),
                )
            val result = calculateCommonPurchases(candidates, 1000, defaultSettings)
            // Should only appear once even though it matches both negative and inherited
            assertEquals(1, result.size)
        }
    }

    // =========================================================================
    // calculateSkillPurchases() - full orchestration
    // =========================================================================

    @Nested
    @DisplayName("calculateSkillPurchases()")
    inner class FullPurchaseTests {
        @Test
        fun `disabled plan returns empty`() {
            val settings =
                SkillPlanSettings(
                    bIsEnabled = false,
                    strategy = SpendingStrategy.DEFAULT,
                    bEnableBuyInheritedUniqueSkills = true,
                    bEnableBuyNegativeSkills = true,
                    skillNames = emptyList(),
                )
            val result =
                calculateSkillPurchases(
                    listOf(SkillCandidate("Skill", price = 50, evaluationPoints = 100)),
                    1000,
                    settings,
                )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `DEFAULT strategy buys common then rank-optimized`() {
            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.DEFAULT,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = true,
                    skillNames = emptyList(),
                )
            val candidates =
                listOf(
                    SkillCandidate("Negative", price = 30, evaluationPoints = 20, isNegative = true),
                    SkillCandidate("Best Ratio", price = 50, evaluationPoints = 200),
                    SkillCandidate("Worst Ratio", price = 100, evaluationPoints = 50),
                )
            val result = calculateSkillPurchases(candidates, 200, settings)
            // Negative first (common), then Best Ratio (rank strategy), then Worst Ratio
            assertTrue(result.isNotEmpty())
            assertEquals("Negative", result[0].first)
            assertEquals("Best Ratio", result[1].first)
        }

        @Test
        fun `OPTIMIZE_RANK buys by highest ratio`() {
            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_RANK,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = false,
                    skillNames = emptyList(),
                )
            val candidates =
                listOf(
                    SkillCandidate("A", price = 100, evaluationPoints = 100), // 1.0
                    SkillCandidate("B", price = 50, evaluationPoints = 150), // 3.0
                    SkillCandidate("C", price = 80, evaluationPoints = 160), // 2.0
                )
            val result = calculateSkillPurchases(candidates, 300, settings)
            assertEquals("B", result[0].first) // 3.0 ratio
            assertEquals("C", result[1].first) // 2.0 ratio
            assertEquals("A", result[2].first) // 1.0 ratio
        }

        @Test
        fun `OPTIMIZE_SKILLS prefers tiered skills then falls back to rank`() {
            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_SKILLS,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = false,
                    skillNames = emptyList(),
                )
            val candidates =
                listOf(
                    SkillCandidate("Tier SS", price = 150, evaluationPoints = 100, communityTier = 0), // SS
                    SkillCandidate("Tier B", price = 50, evaluationPoints = 80, communityTier = 3), // B
                    SkillCandidate("No Tier High Ratio", price = 30, evaluationPoints = 120), // No tier
                )
            val result = calculateSkillPurchases(candidates, 500, settings)
            // Tiered skills first (sorted by tier then ratio): SS then B
            // Then untiered via rank fallback
            assertTrue(result.any { it.first == "Tier SS" })
            assertTrue(result.any { it.first == "Tier B" })
            assertTrue(result.any { it.first == "No Tier High Ratio" })
        }

        // -----------------------------------------------------------------------
        // Random / stress tests with generated skill lists
        // -----------------------------------------------------------------------

        @Test
        fun `random skill list with OPTIMIZE_RANK respects budget invariant`() {
            val rng = Random(42)

            repeat(20) { iteration ->
                val budget = rng.nextInt(100, 2000)
                val numSkills = rng.nextInt(10, 50)

                val candidates =
                    (1..numSkills).map { i ->
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

                val settings =
                    SkillPlanSettings(
                        bIsEnabled = true,
                        strategy = SpendingStrategy.OPTIMIZE_RANK,
                        bEnableBuyInheritedUniqueSkills = rng.nextBoolean(),
                        bEnableBuyNegativeSkills = rng.nextBoolean(),
                        skillNames = candidates.filter { it.isUserPlanned }.map { it.name },
                    )

                val result = calculateSkillPurchases(candidates, budget, settings)
                val totalSpent = result.sumOf { it.second }

                assertTrue(totalSpent <= budget, "Iteration $iteration: spent $totalSpent > budget $budget")

                // No duplicates
                val names = result.map { it.first }
                assertEquals(names.size, names.distinct().size, "Iteration $iteration: duplicates found")
            }
        }

        @Test
        fun `random skill list with OPTIMIZE_SKILLS respects budget invariant`() {
            val rng = Random(99)

            repeat(20) { iteration ->
                val budget = rng.nextInt(200, 1500)
                val numSkills = rng.nextInt(15, 40)

                val candidates =
                    (1..numSkills).map { i ->
                        SkillCandidate(
                            name = "Skill_$i",
                            price = rng.nextInt(30, 250),
                            evaluationPoints = rng.nextInt(20, 180),
                            isNegative = rng.nextDouble() < 0.08,
                            isInheritedUnique = rng.nextDouble() < 0.05,
                            isUserPlanned = rng.nextDouble() < 0.1,
                            communityTier = if (rng.nextDouble() < 0.6) rng.nextInt(0, 4) else null,
                        )
                    }

                val settings =
                    SkillPlanSettings(
                        bIsEnabled = true,
                        strategy = SpendingStrategy.OPTIMIZE_SKILLS,
                        bEnableBuyInheritedUniqueSkills = true,
                        bEnableBuyNegativeSkills = true,
                        skillNames = candidates.filter { it.isUserPlanned }.map { it.name },
                    )

                val result = calculateSkillPurchases(candidates, budget, settings)
                val totalSpent = result.sumOf { it.second }

                assertTrue(totalSpent <= budget, "Iteration $iteration: spent $totalSpent > budget $budget")

                val names = result.map { it.first }
                assertEquals(names.size, names.distinct().size, "Iteration $iteration: duplicates found")
            }
        }

        @Test
        fun `stress test with 100 skills and tight budget`() {
            val rng = Random(777)
            val candidates =
                (1..100).map { i ->
                    SkillCandidate(
                        name = "Skill_$i",
                        price = rng.nextInt(10, 500),
                        evaluationPoints = rng.nextInt(5, 300),
                        isNegative = i <= 5,
                        isInheritedUnique = i in 6..8,
                        isUserPlanned = i in 9..15,
                        communityTier = if (i <= 60) i % 4 else null,
                    )
                }

            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_SKILLS,
                    bEnableBuyInheritedUniqueSkills = true,
                    bEnableBuyNegativeSkills = true,
                    skillNames = candidates.filter { it.isUserPlanned }.map { it.name },
                )

            val result = calculateSkillPurchases(candidates, 500, settings)
            val totalSpent = result.sumOf { it.second }

            assertTrue(totalSpent <= 500)
            assertTrue(result.isNotEmpty(), "Should buy at least some skills with 500 SP")

            // Negative skills should come first if bought
            val negativeIndices =
                result.mapIndexedNotNull { idx, (name, _) ->
                    if (candidates.find { it.name == name }?.isNegative == true) idx else null
                }
            val nonNegativeIndices =
                result.mapIndexedNotNull { idx, (name, _) ->
                    if (candidates.find { it.name == name }?.isNegative != true) idx else null
                }
            if (negativeIndices.isNotEmpty() && nonNegativeIndices.isNotEmpty()) {
                assertTrue(
                    negativeIndices.max() < nonNegativeIndices.min(),
                    "Negative skills should be purchased before other skills",
                )
            }
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // matchesPreference()

    @Nested
    @DisplayName("matchesPreference()")
    inner class MatchesPreferenceTests {
        @Test
        fun `no preference set means everything matches`() {
            assertTrue(matchesPreference(TrackDistance.SPRINT, RunningStyle.LATE_SURGER, emptyList(), TrackSurface.DIRT, null, null, null))
        }

        @Test
        fun `matching distance is kept and off-distance is excluded`() {
            assertTrue(matchesPreference(TrackDistance.MEDIUM, null, emptyList(), null, TrackDistance.MEDIUM, null, null))
            assertFalse(matchesPreference(TrackDistance.SPRINT, null, emptyList(), null, TrackDistance.MEDIUM, null, null))
        }

        @Test
        fun `generic skill with null axis is always kept`() {
            assertTrue(matchesPreference(null, null, emptyList(), null, TrackDistance.MEDIUM, RunningStyle.FRONT_RUNNER, TrackSurface.TURF))
        }

        @Test
        fun `explicit running style match is kept`() {
            assertTrue(matchesPreference(null, RunningStyle.FRONT_RUNNER, emptyList(), null, null, RunningStyle.FRONT_RUNNER, null))
        }

        @Test
        fun `inferred running style match is kept`() {
            assertTrue(matchesPreference(null, null, listOf(RunningStyle.FRONT_RUNNER), null, null, RunningStyle.FRONT_RUNNER, null))
        }

        @Test
        fun `committed running style with no matching inferred is excluded`() {
            assertFalse(matchesPreference(null, RunningStyle.LATE_SURGER, listOf(RunningStyle.LATE_SURGER), null, null, RunningStyle.FRONT_RUNNER, null))
        }

        @Test
        fun `matching surface kept and off-surface excluded`() {
            assertTrue(matchesPreference(null, null, emptyList(), TrackSurface.TURF, null, null, TrackSurface.TURF))
            assertFalse(matchesPreference(null, null, emptyList(), TrackSurface.DIRT, null, null, TrackSurface.TURF))
        }

        @Test
        fun `one off-axis fails the whole match`() {
            // distance matches MEDIUM but surface DIRT != preferred TURF -> excluded
            assertFalse(matchesPreference(TrackDistance.MEDIUM, null, emptyList(), TrackSurface.DIRT, TrackDistance.MEDIUM, null, TrackSurface.TURF))
        }

        @Test
        fun `explicit style match passes despite non-matching inferred styles`() {
            assertTrue(matchesPreference(null, RunningStyle.FRONT_RUNNER, listOf(RunningStyle.LATE_SURGER), null, null, RunningStyle.FRONT_RUNNER, null))
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Skip ◎ upgrades toggle

    @Nested
    @DisplayName("skip double-circle upgrades")
    inner class SkipDoubleCircleTests {
        @Test
        fun `isDoubleCircleUpgrade detects the marker and ignores other forms`() {
            assertTrue(isDoubleCircleUpgrade("Hanshin Racecourse ◎"))
            assertTrue(isDoubleCircleUpgrade("Corner Recovery ◎ "))
            assertFalse(isDoubleCircleUpgrade("Hanshin Racecourse ○"))
            assertFalse(isDoubleCircleUpgrade("Swinging Maestro"))
        }

        @Test
        fun `OPTIMIZE_KNAPSACK with toggle on excludes the ◎ upgrade and keeps the ○ form`() {
            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_KNAPSACK,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = false,
                    skillNames = emptyList(),
                )
            // The ◎ has the better raw ratio, so without the toggle the DP would prefer it.
            val candidates =
                listOf(
                    SkillCandidate("Corner Recovery ○", price = 100, evaluationPoints = 120), // 1.2
                    SkillCandidate("Corner Recovery ◎", price = 150, evaluationPoints = 220), // 1.47
                    SkillCandidate("Straightaway Acceleration", price = 80, evaluationPoints = 90),
                )

            val withUpgrade = calculateSkillPurchases(candidates, 1000, settings, skipDoubleCircle = false)
            assertTrue(withUpgrade.any { it.first == "Corner Recovery ◎" }, "Without the toggle the DP should be free to buy the ◎.")

            val withoutUpgrade = calculateSkillPurchases(candidates, 1000, settings, skipDoubleCircle = true)
            assertTrue(withoutUpgrade.none { it.first == "Corner Recovery ◎" }, "Toggle on must drop the ◎ from the knapsack candidate set.")
            assertTrue(withoutUpgrade.any { it.first == "Corner Recovery ○" }, "The ○ form should still be bought.")
            assertTrue(withoutUpgrade.any { it.first == "Straightaway Acceleration" })
        }

        @Test
        fun `OPTIMIZE_RANK with toggle on excludes the ◎ upgrade`() {
            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_RANK,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = false,
                    skillNames = emptyList(),
                )
            val candidates =
                listOf(
                    SkillCandidate("Standard Distance ○", price = 60, evaluationPoints = 70),
                    SkillCandidate("Standard Distance ◎", price = 120, evaluationPoints = 160),
                )

            val result = calculateSkillPurchases(candidates, 1000, settings, skipDoubleCircle = true)
            assertTrue(result.none { it.first == "Standard Distance ◎" })
            assertTrue(result.any { it.first == "Standard Distance ○" })
        }
    }
}

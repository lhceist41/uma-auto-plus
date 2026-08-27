package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.KnapsackChoice
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.SkillCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.buildKnapsackGroups
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateCommonPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateOptimizeKnapsackPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateOptimizeRankPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.calculateSkillPurchases
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.RecoveryCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.isDoubleCircleUpgrade
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.isRecoveryInjectionCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.matchesPreference
import com.steve1316.uma_android_automation.bot.SkillPlan.Companion.pickRecoveryCandidate
import com.steve1316.uma_android_automation.bot.SkillPlan.SkillPlanSettings
import com.steve1316.uma_android_automation.bot.SkillPlan.SpendingStrategy
import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.SkillData
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
    // Knapsack chain costing
    // =========================================================================

    @Nested
    @DisplayName("Knapsack chain costing")
    inner class KnapsackChainCostingTests {
        // A chain member's screen price includes its unpurchased prerequisites, so the gold's
        // 360 already contains the base's 200 (160 top-up).
        private val base = SkillCandidate(name = "Corner Recovery", price = 200, evaluationPoints = 180)
        private val gold = SkillCandidate(name = "Swinging Maestro", price = 360, evaluationPoints = 450)
        private val chains =
            mapOf(
                "Corner Recovery" to listOf("Corner Recovery", "Swinging Maestro"),
                "Swinging Maestro" to listOf("Corner Recovery", "Swinging Maestro"),
            )

        @Test
        fun `chain combo cost equals the on-screen combined price`() {
            assertEquals(360, KnapsackChoice(listOf(base, gold)).cost)
        }

        @Test
        fun `singleton choice cost is its own price`() {
            assertEquals(200, KnapsackChoice(listOf(base)).cost)
        }

        @Test
        fun `DP affords a chain combo at its on-screen price and emits incremental prices`() {
            val groups = buildKnapsackGroups(listOf(base, gold), chains)
            val plan = calculateOptimizeKnapsackPurchases(groups, budget = 360)
            assertEquals(listOf("Corner Recovery" to 200, "Swinging Maestro" to 160), plan)
        }

        @Test
        fun `combo is skipped when budget only covers the base`() {
            val groups = buildKnapsackGroups(listOf(base, gold), chains)
            val plan = calculateOptimizeKnapsackPurchases(groups, budget = 250)
            assertEquals(listOf("Corner Recovery" to 200), plan)
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

        @Test
        fun `OPTIMIZE_SKILLS buys a worse-ratio tiered skill over a better-ratio untiered one when budget forces a choice`() {
            // A budget-constrained case where OPTIMIZE_RANK and OPTIMIZE_SKILLS must disagree if the
            // community tier signal is actually wired in: only one of these two skills fits the budget, one
            // has by far the better eval-point ratio but no tier, the other is tiered but worse ratio. If
            // OPTIMIZE_SKILLS silently degenerated into OPTIMIZE_RANK (the historical bug this guards
            // against, caused by every community_tier being null), it would buy the untiered skill instead.
            val candidates =
                listOf(
                    SkillCandidate("Tiered B", price = 90, evaluationPoints = 90, communityTier = 3), // ratio 1.0, B tier
                    SkillCandidate("Untiered Best Ratio", price = 90, evaluationPoints = 900), // ratio 10.0, no tier
                )

            val rankSettings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_RANK,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = false,
                    skillNames = emptyList(),
                )
            val rankResult = calculateSkillPurchases(candidates, 100, rankSettings)
            assertEquals(listOf("Untiered Best Ratio"), rankResult.map { it.first })

            val skillsSettings = rankSettings.copy(strategy = SpendingStrategy.OPTIMIZE_SKILLS)
            val skillsResult = calculateSkillPurchases(candidates, 100, skillsSettings)
            assertEquals(listOf("Tiered B"), skillsResult.map { it.first })
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

    // =========================================================================
    // Planned-only shaping (2B-1): allowStrategyTail = false
    // =========================================================================

    @Nested
    @DisplayName("planned-only shaping (allowStrategyTail = false)")
    inner class PlannedOnlyTests {
        private fun plannedOnlySettings(
            strategy: SpendingStrategy = SpendingStrategy.OPTIMIZE_RANK,
            inherited: Boolean = false,
            negatives: Boolean = false,
            planned: List<String> = emptyList(),
        ) = SkillPlanSettings(
            bIsEnabled = true,
            strategy = strategy,
            bEnableBuyInheritedUniqueSkills = inherited,
            bEnableBuyNegativeSkills = negatives,
            skillNames = planned,
        )

        @Test
        fun `planned skills are bought and the tail candidate is excluded`() {
            val candidates =
                listOf(
                    SkillCandidate("Hydrate", price = 180, evaluationPoints = 120, isUserPlanned = true),
                    // A cheap, high-ratio filler the greedy tail would take first if it ran.
                    SkillCandidate("Triple 7s", price = 100, evaluationPoints = 300),
                )
            val result = calculateSkillPurchases(candidates, 1000, plannedOnlySettings(planned = listOf("Hydrate")), allowStrategyTail = false)
            assertEquals(listOf("Hydrate" to 180), result)
        }

        @Test
        fun `unplanned green and recovery flavored candidates stay unbought with budget to spare`() {
            val candidates =
                listOf(
                    SkillCandidate("Corner Recovery ○", price = 170, evaluationPoints = 100),
                    SkillCandidate("Right-Handed ○", price = 90, evaluationPoints = 80),
                    SkillCandidate("Professor of Curvature", price = 200, evaluationPoints = 160, isUserPlanned = true),
                )
            val result = calculateSkillPurchases(candidates, 2000, plannedOnlySettings(planned = listOf("Professor of Curvature")), allowStrategyTail = false)
            assertEquals(listOf("Professor of Curvature" to 200), result)
        }

        @Test
        fun `planned recovery is bought like any planned skill`() {
            val candidates = listOf(SkillCandidate("Swinging Maestro", price = 142, evaluationPoints = 130, isUserPlanned = true))
            val result = calculateSkillPurchases(candidates, 500, plannedOnlySettings(planned = listOf("Swinging Maestro")), allowStrategyTail = false)
            assertEquals(listOf("Swinging Maestro" to 142), result)
        }

        @Test
        fun `inherited uniques and negatives keep their existing toggles`() {
            val candidates =
                listOf(
                    SkillCandidate("Pure Heart", price = 200, evaluationPoints = 180, isInheritedUnique = true),
                    SkillCandidate("Corner Recovery ×", price = 100, evaluationPoints = 40, isNegative = true),
                )
            val on = calculateSkillPurchases(candidates, 1000, plannedOnlySettings(inherited = true, negatives = true), allowStrategyTail = false)
            assertEquals(setOf("Pure Heart", "Corner Recovery ×"), on.map { it.first }.toSet())
            val off = calculateSkillPurchases(candidates, 1000, plannedOnlySettings(), allowStrategyTail = false)
            assertTrue(off.isEmpty())
        }

        @Test
        fun `an empty plan with both toggles off buys nothing and leaves the whole budget unspent`() {
            val candidates =
                listOf(
                    SkillCandidate("Filler A", price = 100, evaluationPoints = 90),
                    SkillCandidate("Filler B", price = 120, evaluationPoints = 200),
                )
            val result = calculateSkillPurchases(candidates, 999, plannedOnlySettings(), allowStrategyTail = false)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `the knapsack strategy tail is skipped too`() {
            val candidates =
                listOf(
                    SkillCandidate("Planned Pick", price = 150, evaluationPoints = 90, isUserPlanned = true),
                    SkillCandidate("DP Favorite", price = 100, evaluationPoints = 400),
                )
            val result =
                calculateSkillPurchases(
                    candidates,
                    1000,
                    plannedOnlySettings(strategy = SpendingStrategy.OPTIMIZE_KNAPSACK, planned = listOf("Planned Pick")),
                    allowStrategyTail = false,
                )
            assertEquals(listOf("Planned Pick" to 150), result)
        }

        @Test
        fun `no duplicate purchase when a planned skill would also tempt the tail`() {
            val candidates = listOf(SkillCandidate("Hydrate", price = 180, evaluationPoints = 500, isUserPlanned = true))
            val result = calculateSkillPurchases(candidates, 1000, plannedOnlySettings(planned = listOf("Hydrate")), allowStrategyTail = false)
            assertEquals(1, result.size)
        }

        @Test
        fun `the default keeps the tail exactly as before`() {
            val candidates =
                listOf(
                    SkillCandidate("Planned Pick", price = 150, evaluationPoints = 90, isUserPlanned = true),
                    SkillCandidate("Tail Pick", price = 100, evaluationPoints = 400),
                )
            val allowed = calculateSkillPurchases(candidates, 1000, plannedOnlySettings(planned = listOf("Planned Pick")))
            assertEquals(setOf("Planned Pick", "Tail Pick"), allowed.map { it.first }.toSet())
        }
    }

    // =========================================================================
    // Recovery injection (2B-2): candidate predicate + deterministic picker
    // =========================================================================

    @Nested
    @DisplayName("recovery injection candidates (2B-2)")
    inner class RecoveryInjectionTests {
        // Real bundled-data fixtures: ids, icons, and condition strings are verbatim from
        // skills.json, so the axes derive through the REAL Conditions parser.
        private fun data(id: Int, name: String, iconId: Int, condition: String, inherited: Boolean = false) =
            SkillData(
                id = id,
                name = name,
                description = "",
                iconId = iconId,
                cost = 160,
                evalPt = 100,
                condition = condition,
                precondition = "",
                bIsInheritedUnique = inherited,
                communityTier = null,
                upgrade = null,
                downgrade = null,
            )

        private val deepBreaths = data(200742, "Deep Breaths", 20021, "distance_type==4&phase_random==1")
        private val cooldown = data(200741, "Cooldown", 20022, "distance_type==4&phase_random==1")
        private val hydrate = data(201352, "Hydrate", 20021, "running_style==2&phase_random==1")
        private val beStill = data(201692, "Be Still", 20021, "running_style==3&phase_laterhalf_random==0&order_rate>=40")
        private val cornerRecovery = data(200352, "Corner Recovery ○", 20021, "corner_random==1@corner_random==2@corner_random==3@corner_random==4")
        private val swingingMaestro = data(200351, "Swinging Maestro", 20022, "corner_random==1@corner_random==2@corner_random==3@corner_random==4")
        private val straightawayRecovery = data(200382, "Straightaway Recovery", 20021, "phase==1&corner==0")
        private val breathOfFreshAir = data(200381, "Breath of Fresh Air", 20022, "phase==1&corner==0")
        private val triple7s = data(201571, "Triple 7s", 20021, "remain_distance<=778&remain_distance>=776")
        private val shakeItOut = data(201621, "Shake It Out", 20021, "activate_count_end_after>=3")
        private val clearHeart = data(10451, "Clear Heart", 20021, "phase_random==1&order>=2&order_rate<=40", inherited = true)
        private val cornerRecoveryX = data(200353, "Corner Recovery ×", 20024, "corner_random==1@corner_random==2")
        private val familiarGround = data(202002, "Familiar Ground", 20021, "ground_type==2&phase_random==1&order_rate>=50")
        private val paceChaserSavvy = data(201532, "Pace Chaser Savvy ○", 20011, "distance_rate>=50&order_rate<=60")

        // The validation career's axes: Long / Late Surger / Turf.
        private fun candidateUnderLongLate(skill: SkillData): Boolean =
            isRecoveryInjectionCandidate(skill, TrackDistance.LONG, RunningStyle.LATE_SURGER, TrackSurface.TURF)

        @Test
        fun `distance and style compatible recoveries are candidates`() {
            assertTrue(candidateUnderLongLate(deepBreaths), "white with a matching Long axis")
            assertTrue(candidateUnderLongLate(cooldown), "gold with a matching Long axis")
            assertTrue(candidateUnderLongLate(beStill), "white with the matching Late style axis")
        }

        @Test
        fun `all four allow-listed general recoveries are candidates`() {
            for (skill in listOf(cornerRecovery, swingingMaestro, straightawayRecovery, breathOfFreshAir)) {
                assertTrue(candidateUnderLongLate(skill), skill.name)
            }
        }

        @Test
        fun `axis-incompatible recoveries are excluded`() {
            assertFalse(candidateUnderLongLate(hydrate), "Pace-style recovery under a Late preference")
            assertFalse(candidateUnderLongLate(familiarGround), "Dirt recovery under a Turf preference")
        }

        @Test
        fun `axis-free condition traps are excluded`() {
            assertFalse(candidateUnderLongLate(triple7s))
            assertFalse(candidateUnderLongLate(shakeItOut))
        }

        @Test
        fun `inherited uniques, debuffs, and non-recoveries are never candidates`() {
            assertFalse(candidateUnderLongLate(clearHeart), "inherited unique recovery satisfies ownership but is never injected")
            assertFalse(candidateUnderLongLate(cornerRecoveryX), "20024 debuff family")
            assertFalse(candidateUnderLongLate(paceChaserSavvy), "non-recovery icon")
        }

        @Test
        fun `classification still counts inherited uniques for ownership`() {
            assertEquals(RecoveryClass.WHITE, recoveryClassOf(clearHeart.iconId))
        }

        @Test
        fun `picker prefers white over gold, then price, then id`() {
            val white170 = RecoveryCandidate("Corner Recovery ○", RecoveryClass.WHITE, 170, 200352)
            val white160 = RecoveryCandidate("Deep Breaths", RecoveryClass.WHITE, 160, 200742)
            val gold100 = RecoveryCandidate("Cooldown", RecoveryClass.GOLD, 100, 200741)
            assertEquals(white160, pickRecoveryCandidate(listOf(white170, gold100, white160)), "cheap gold never outranks white")
            assertEquals(gold100, pickRecoveryCandidate(listOf(gold100)), "gold injected when no white exists")
            val twin = RecoveryCandidate("Straightaway Recovery", RecoveryClass.WHITE, 160, 200382)
            assertEquals(twin, pickRecoveryCandidate(listOf(white160, twin)), "equal price ties break on the lower skill id (200382 < 200742)")
            assertNull(pickRecoveryCandidate(emptyList()))
        }
    }

    // =========================================================================
    // Career-end constrained fallback (sparks at CAREER_COMPLETE)
    // =========================================================================

    @Nested
    @DisplayName("Career-End Fallback Candidate Set")
    inner class CareerEndFallbackTests {
        @Test
        fun `wrong-distance candidates are excluded by the axes gate`() {
            // A Long trainee (the 716-point career ran preferred distance Long).
            assertFalse(
                matchesPreference(TrackDistance.SPRINT, null, emptyList(), null, TrackDistance.LONG, null, null),
                "a Sprint-committed skill never enters a Long career's fallback",
            )
            assertTrue(matchesPreference(TrackDistance.LONG, null, emptyList(), null, TrackDistance.LONG, null, null))
        }

        @Test
        fun `wrong-style candidates are excluded by the axes gate`() {
            assertFalse(
                matchesPreference(null, RunningStyle.END_CLOSER, emptyList(), null, null, RunningStyle.PACE_CHASER, null),
                "an End Closer skill never enters a Pace Chaser career's fallback",
            )
            assertFalse(
                matchesPreference(null, null, listOf(RunningStyle.END_CLOSER), null, null, RunningStyle.PACE_CHASER, null),
                "inferred-style commitment excludes too",
            )
            assertTrue(matchesPreference(null, RunningStyle.PACE_CHASER, emptyList(), null, null, RunningStyle.PACE_CHASER, null))
        }

        @Test
        fun `wrong-surface candidates are excluded by the axes gate`() {
            assertFalse(
                matchesPreference(null, null, emptyList(), TrackSurface.DIRT, null, null, TrackSurface.TURF),
                "a Dirt-committed skill never enters a Turf career's fallback",
            )
            assertTrue(matchesPreference(null, null, emptyList(), TrackSurface.TURF, null, null, TrackSurface.TURF))
        }

        @Test
        fun `generic axis-free skills remain compatible`() {
            assertTrue(
                matchesPreference(null, null, emptyList(), null, TrackDistance.LONG, RunningStyle.PACE_CHASER, TrackSurface.TURF),
                "a skill with no axis commitment is profile-compatible by definition",
            )
        }

        @Test
        fun `negative candidates never enter the fallback - the existing toggle owns them in the common phase`() {
            assertFalse(
                SkillPlan.Companion.careerEndFallbackCandidateAllowed(
                    isNegative = true,
                    isInheritedUnique = false,
                    isDoubleCircle = false,
                    skipDoubleCircleUpgrades = false,
                    matchesAxes = true,
                ),
            )
        }

        @Test
        fun `inherited unique candidates never enter the fallback - the existing setting owns them in the common phase`() {
            assertFalse(
                SkillPlan.Companion.careerEndFallbackCandidateAllowed(
                    isNegative = false,
                    isInheritedUnique = true,
                    isDoubleCircle = false,
                    skipDoubleCircleUpgrades = false,
                    matchesAxes = true,
                ),
            )
        }

        @Test
        fun `the double-circle skip toggle is honored`() {
            assertFalse(
                SkillPlan.Companion.careerEndFallbackCandidateAllowed(
                    isNegative = false,
                    isInheritedUnique = false,
                    isDoubleCircle = true,
                    skipDoubleCircleUpgrades = true,
                    matchesAxes = true,
                ),
                "skip enabled: the upgrade tier is dropped",
            )
            assertTrue(
                SkillPlan.Companion.careerEndFallbackCandidateAllowed(
                    isNegative = false,
                    isInheritedUnique = false,
                    isDoubleCircle = true,
                    skipDoubleCircleUpgrades = false,
                    matchesAxes = true,
                ),
                "skip disabled: the upgrade tier may be planned",
            )
        }

        @Test
        fun `a compatible axis-passing candidate enters the fallback`() {
            assertTrue(
                SkillPlan.Companion.careerEndFallbackCandidateAllowed(
                    isNegative = false,
                    isInheritedUnique = false,
                    isDoubleCircle = false,
                    skipDoubleCircleUpgrades = true,
                    matchesAxes = true,
                ),
            )
        }

        @Test
        fun `planned skills stay first - the common phase buys them before the fallback plans the remainder`() {
            // The 716-point shape: planned skills affordable at career end must be bought by the
            // common phase, and only the remaining budget reaches the fallback knapsack.
            val settings =
                SkillPlanSettings(
                    bIsEnabled = true,
                    strategy = SpendingStrategy.OPTIMIZE_SKILLS,
                    bEnableBuyInheritedUniqueSkills = false,
                    bEnableBuyNegativeSkills = false,
                    skillNames = listOf("Medium Corners ○", "Long Corners ○"),
                )
            val planned =
                listOf(
                    SkillCandidate("Medium Corners ○", price = 66, evaluationPoints = 239, isUserPlanned = true),
                    SkillCandidate("Long Corners ○", price = 99, evaluationPoints = 239, isUserPlanned = true),
                )
            val common = calculateCommonPurchases(planned, 881, settings)
            assertEquals(listOf("Medium Corners ○", "Long Corners ○"), common.map { it.first })
            val remainingBudget = 881 - common.sumOf { it.second }
            assertEquals(716, remainingBudget, "the incident's exact leftover reaches the fallback")

            // The fallback then spends that remainder on compatible candidates via the knapsack.
            val compatible =
                listOf(
                    SkillCandidate("Up-Tempo", price = 96, evaluationPoints = 239),
                    SkillCandidate("Soft Step", price = 96, evaluationPoints = 239),
                    SkillCandidate("Tactical Tweak", price = 108, evaluationPoints = 239),
                    SkillCandidate("Medium Straightaways ○", price = 80, evaluationPoints = 239),
                    SkillCandidate("Pace Chaser Savvy ○", price = 71, evaluationPoints = 191),
                )
            val plan = calculateOptimizeKnapsackPurchases(buildKnapsackGroups(compatible, emptyMap()), remainingBudget)
            assertTrue(plan.isNotEmpty(), "716 points must buy compatible skills instead of being discarded")
            assertEquals(compatible.size, plan.size, "every compatible candidate is affordable under 716")
            assertTrue(plan.sumOf { it.second } <= remainingBudget)
        }

        @Test
        fun `no compatible candidate yields an empty fallback plan - the safe terminal, never a crash`() {
            assertTrue(calculateOptimizeKnapsackPurchases(buildKnapsackGroups(emptyList(), emptyMap()), 716).isEmpty())
        }
    }
}

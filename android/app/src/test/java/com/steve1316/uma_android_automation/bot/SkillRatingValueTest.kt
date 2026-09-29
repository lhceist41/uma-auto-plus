package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.SkillListEntry
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import com.steve1316.uma_android_automation.types.ownedSkillsWithoutUnique
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Skill values the way the game's rating counts them.
 *
 * The game scores only the highest version of a skill the trainee owns, and the own unique through its
 * level bonus. On 8 careers with the game's own rating, counting each displayed skill at its own value
 * matched within the Details read's ○/◎ noise; folding a gold's white into it overshot by about 1,400,
 * and the estimate ran high by exactly the unique's inherited-version row.
 */
@DisplayName("Skill rating value")
class SkillRatingValueTest {
    // Late Surger Straightaways at aptitude A: ○ 217 x 1.1, ◎ 262 x 1.1.
    private val circle = 239
    private val doubleCircle = 288

    @Nested
    @DisplayName("purchaseRatingGain")
    inner class PurchaseGain {
        @Test
        fun `a double circle over an owned circle adds only their difference`() {
            assertEquals(49, SkillListEntry.purchaseRatingGain(doubleCircle, listOf(circle to true)))
        }

        @Test
        fun `buying through an unowned lower version yields the upper skill alone`() {
            assertEquals(doubleCircle, SkillListEntry.purchaseRatingGain(doubleCircle, listOf(circle to false)))
            // Burning Spirit SPD (633) bought with Ignited Spirit SPD (263) unowned.
            assertEquals(633, SkillListEntry.purchaseRatingGain(633, listOf(263 to false)))
        }

        @Test
        fun `the nearest owned version is the one replaced`() {
            assertEquals(559 - 239, SkillListEntry.purchaseRatingGain(559, listOf(288 to false, 239 to true)))
            assertEquals(559 - 288, SkillListEntry.purchaseRatingGain(559, listOf(288 to true, 239 to true)))
        }

        @Test
        fun `a skill with no lower version adds its own points`() {
            assertEquals(217, SkillListEntry.purchaseRatingGain(217, emptyList()))
        }
    }

    @Nested
    @DisplayName("career-end tail")
    inner class CareerEndTail {
        private val rankEnd = { anySkill: Boolean, styleKnown: Boolean ->
            careerEndTailFilter(SkillSpendObjective.RANK, SkillCheckTrigger.CAREER_COMPLETE, anySkill, styleKnown)
        }

        /** Whether a Late Surger, Medium, Turf trainee's tail may buy a skill with these conditions. */
        private fun allows(filter: CareerEndTailFilter, style: RunningStyle?, distance: TrackDistance?, prefStyle: RunningStyle? = RunningStyle.LATE_SURGER) =
            SkillPlan.knapsackTailAllows(filter, distance, style, emptyList(), null, TrackDistance.MEDIUM, prefStyle, TrackSurface.TURF)

        @Test
        fun `only the rank objective's career-end session widens the profile filter`() {
            for (anySkill in listOf(false, true)) {
                val widened =
                    SkillSpendObjective.entries.flatMap { objective ->
                        (SkillCheckTrigger.entries + listOf<SkillCheckTrigger?>(null))
                            .filter { careerEndTailFilter(objective, it, anySkill, runningStyleKnown = true) != CareerEndTailFilter.PROFILE }
                            .map { objective to it }
                    }
                assertEquals(listOf(SkillSpendObjective.RANK to SkillCheckTrigger.CAREER_COMPLETE), widened)
            }
        }

        @Test
        fun `by default an other-style skill is not bought`() {
            assertEquals(CareerEndTailFilter.RUNNING_STYLE, rankEnd(false, true))
            assertFalse(allows(rankEnd(false, true), RunningStyle.FRONT_RUNNER, null), "Front Runner Corners on a Late Surger")
        }

        @Test
        fun `by default an off-distance skill of the trainee's style is bought`() {
            assertTrue(allows(rankEnd(false, true), RunningStyle.LATE_SURGER, TrackDistance.MILE), "a Mile Late Surger skill on a Medium trainee")
            assertTrue(allows(rankEnd(false, true), null, TrackDistance.MILE), "Mile Straightaways")
            assertFalse(allows(CareerEndTailFilter.PROFILE, null, TrackDistance.MILE), "mid-career keeps the distance filter")
        }

        @Test
        fun `with the setting on an other-style skill is bought`() {
            assertEquals(CareerEndTailFilter.ANY_SKILL, rankEnd(true, true))
            assertTrue(allows(rankEnd(true, true), RunningStyle.FRONT_RUNNER, TrackDistance.MILE))
        }

        @Test
        fun `an unknown running style falls back to the profile filter, never any skill`() {
            assertEquals(CareerEndTailFilter.PROFILE, rankEnd(false, false))
            assertFalse(allows(rankEnd(false, false), null, TrackDistance.MILE, prefStyle = null), "the distance filter still applies")
        }
    }

    @Nested
    @DisplayName("ownedSkillsWithoutUnique")
    inner class OwnUnique {
        @Test
        fun `the trainee's own unique is not an owned skill for the estimate`() {
            val read = listOf("Cut and Drive!", "Tether", "Burning Spirit SPD")
            assertEquals(listOf("Tether", "Burning Spirit SPD"), ownedSkillsWithoutUnique(read, "Cut and Drive!"))
        }

        @Test
        fun `an unread unique cell removes nothing`() {
            val read = listOf("Tether", "Burning Spirit SPD")
            assertEquals(read, ownedSkillsWithoutUnique(read, null))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val entry by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/types/SkillListEntry.kt") }
        private val plan by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/SkillPlan.kt") }
        private val list by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/types/SkillList.kt") }

        @Test
        fun `the list's evaluation points use the purchase gain, never a folded lower version`() {
            val body = entry.substring(entry.indexOf("private fun calculateEvaluationPoints(): Int {"))
            assertTrue(body.substring(0, body.indexOf("\n    }")).contains("purchaseRatingGain(ownEvaluationPoints, lowerVersions)"))
            assertFalse(entry.contains("res += prev.evaluationPoints"))
        }

        @Test
        fun `only the knapsack tail consults the career-end rule, with the player's setting, ahead of its filter`() {
            assertEquals(1, Regex("careerEndTailFilter\\(").findAll(plan).count())
            assertTrue(plan.contains("SettingsHelper.getBooleanSetting(\"skills\", \"careerEndBuyAnySkill\", false)"))
            val knapsack = plan.substring(plan.indexOf("private fun getSkillsToBuyOptimizeKnapsackStrategy("))
            val rule = knapsack.indexOf("careerEndTailFilter(campaign.skillSpendObjective, sessionEffectiveTrigger, careerEndBuyAnySkill, preferredRunningStyle != null)")
            val filter = knapsack.indexOf("knapsackTailAllows(\n                        tailFilter,")
            assertTrue(rule in 0 until filter, "the rule is decided before the filter it chooses")
        }

        @Test
        fun `the Details read takes the unique from the first cell of the first page`() {
            assertTrue(list.contains("if (pass == 0 && row == 0 && col == 0) uniqueName = name"))
            assertTrue(list.contains("val skills = ownedSkillsWithoutUnique(ownedNames, uniqueName)"))
            assertTrue(list.contains("return DetailsSkillsResult(skills, uniqueLevel, uniqueName)"))
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }
}

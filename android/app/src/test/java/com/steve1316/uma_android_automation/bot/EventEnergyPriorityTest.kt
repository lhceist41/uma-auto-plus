package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Event option weights for energy and random outcomes.
 *
 * Every preset turns Prioritize Energy on, and it used to weigh each energy point x100 at any energy, so a
 * +15 energy option beat +15 Speed at 84% energy and +20 energy beat 30 skill points at full energy. The
 * fixtures below are logged events (Vodka and Taiki Shuttle careers): each option's non-energy weight is the
 * logged weight minus its energy part.
 */
@DisplayName("Event energy priority")
class EventEnergyPriorityTest {
    /** The option the bot takes: the first one with the highest weight. */
    private fun pick(energy: Int, vararg options: Pair<Int, Int>): Int {
        val weights = options.map { (nonEnergyWeight, energyGain) -> nonEnergyWeight + energyGain * TrainingEvent.eventEnergyMultiplier(energy, prioritizeEnergy = true) }
        return weights.indexOf(weights.max())
    }

    @Nested
    @DisplayName("eventEnergyMultiplier")
    inner class Multiplier {
        @Test
        fun `prioritized energy counts x100 only below 50 percent`() {
            val table = listOf(29 to 100, 30 to 100, 49 to 100, 50 to 2, 69 to 2, 70 to 1, 89 to 1, 90 to 0, 100 to 0)
            for ((energy, expected) in table) assertEquals(expected, TrainingEvent.eventEnergyMultiplier(energy, prioritizeEnergy = true), "energy $energy")
        }

        @Test
        fun `without the setting the weight is unchanged`() {
            val table = listOf(29 to 4, 30 to 3, 49 to 3, 50 to 2, 69 to 2, 70 to 1, 89 to 1, 90 to 0)
            for ((energy, expected) in table) assertEquals(expected, TrainingEvent.eventEnergyMultiplier(energy, prioritizeEnergy = false), "energy $energy")
        }
    }

    @Nested
    @DisplayName("logged events")
    inner class LoggedEvents {
        // Mejiro Ardan, "Let's Use Our Imaginations": Energy +15 (logged weight 1500) against Speed +15 (65).
        private val energy15 = 0 to 15
        private val speed15 = 65 to 0

        @Test
        fun `Speed +15 beats Energy +15 at 69 and 84 percent energy`() {
            assertEquals(1, pick(69, energy15, speed15))
            assertEquals(1, pick(84, energy15, speed15))
        }

        @Test
        fun `Energy +15 still wins at 25 percent energy`() {
            assertEquals(0, pick(25, energy15, speed15))
        }

        @Test
        fun `Please Rerun Task at 31 percent energy keeps the energy option`() {
            // Mihono Bourbon: Energy +5, Mood +1 (0 at GREAT), bond +5 (logged 520) against Speed +10, hint, bond (105).
            assertEquals(0, pick(31, 20 to 5, 105 to 0))
        }

        @Test
        fun `Skill points +30 beats Energy +20 at full energy`() {
            // Matikanefukukitaru, "When Piety and Kindness Intersect" at 100%: SP +30, bond (50) against Energy +20, bond (2020).
            assertEquals(0, pick(100, 50 to 0, 20 to 20))
        }
    }

    @Nested
    @DisplayName("weighEventOption")
    inner class RandomOutcomes {
        /** The line weights the scorer gives these lines ("All stats +7" counts all five stats). */
        private val weigh: (String) -> Int = { line ->
            when (line.trim()) {
                "Randomly either" -> 50
                "All stats +7" -> 35
                "Matikanefukukitaru bond +7" -> 20
                "Wit +4" -> 4
                "Event chain ended" -> -300
                "Energy +5" -> 500
                "Matikanefukukitaru bond +5" -> 20
                else -> 0
            }
        }

        @Test
        fun `a random-outcome option is weighed by its outcomes' average, not their sum`() {
            // Mystery Fortune Ritual option 1, as the event data ships it.
            val option = "Randomly either\n----------\nAll stats +7\nMatikanefukukitaru bond +7\n----------\n\n----------\nWit +4\nEvent chain ended".split("\n")
            assertEquals(50 + (55 + -296) / 2, TrainingEvent.weighEventOption(option, weigh))
        }

        @Test
        fun `a stated chance line separates two outcomes and gives the second its chance`() {
            val option = "Randomly either\n----------\nWit +4\n----------\nor (~30%)\n----------\nAll stats +7".split("\n")
            // Wit +4 at the remaining 70%, All stats +7 at 30%.
            assertEquals(50 + (4 * 70 + 35 * 30) / 100, TrainingEvent.weighEventOption(option, weigh))
        }

        /** Line weights for the stated-chance cases. */
        private val weighChance: (String) -> Int = { line ->
            when (line.trim()) {
                "Randomly either" -> 50
                "All stats +20" -> 100
                "Mood -2" -> -150
                "All stats -15" -> -75
                "Get Night Owl status" -> -25
                "All stats +40" -> 200
                "Speed -8" -> -40
                "Speed +10" -> 60
                else -> 0
            }
        }

        @Test
        fun `an option weighs its outcomes by the chances the event states`() {
            // "Acupuncture (Just an Acupuncturist, No Worries! ☆)" option 1, as the event data ships it: the good outcome
            // at 30%, the bad one at 70%. The plain average (50 + (100 - 250) / 2 = -25) would overvalue it.
            val option = "Randomly either\n----------\nAll stats +20\nor (~70%)\nor (~70%)\nMood -2\nAll stats -15\nGet Night Owl status".split("\n")
            assertEquals(50 + (100 * 30 + -250 * 70) / 100, TrainingEvent.weighEventOption(option, weighChance))
        }

        @Test
        fun `a rare good outcome no longer outweighs a sure small gain`() {
            // A 15% chance of All stats +40 against an 85% chance of Speed -8, beside a sure Speed +10.
            val gamble = "Randomly either\n----------\nSpeed -8\nor (~15%)\nAll stats +40".split("\n")
            val sure = listOf("Speed +10")
            val gambleWeight = TrainingEvent.weighEventOption(gamble, weighChance)
            assertEquals(50 + (-40 * 85 + 200 * 15) / 100, gambleWeight)
            assertTrue(gambleWeight < TrainingEvent.weighEventOption(sure, weighChance), "the plain average (50 + 80 = 130) picked the gamble")
        }

        @Test
        fun `outcomes without stated chances, or with chances that do not add up, count equally`() {
            assertEquals((100 - 250) / 2, TrainingEvent.expectedOutcomeWeight(listOf(100 to null, -250 to null)))
            assertEquals((100 * 30 + -250 * 70) / 100, TrainingEvent.expectedOutcomeWeight(listOf(100 to null, -250 to 70)))
            assertEquals((100 * 40 + -250 * 60) / 100, TrainingEvent.expectedOutcomeWeight(listOf(100 to 40, -250 to 60)))
            assertEquals((100 - 250) / 2, TrainingEvent.expectedOutcomeWeight(listOf(100 to 60, -250 to 70)), "chances over 100%")
            assertEquals((100 - 250) / 2, TrainingEvent.expectedOutcomeWeight(listOf(100 to 30, -250 to 30)), "every chance stated, not 100%")
            assertEquals(0, TrainingEvent.expectedOutcomeWeight(emptyList()))
        }

        @Test
        fun `any other option is the sum of its lines`() {
            val option = "Energy +5\nMatikanefukukitaru bond +5\nEvent chain ended".split("\n")
            assertEquals(500 + 20 - 300, TrainingEvent.weighEventOption(option, weigh))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val source by lazy {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            val relative = "android/app/src/main/java/com/steve1316/uma_android_automation/bot/TrainingEvent.kt"
            var text: String? = null
            repeat(8) {
                if (text == null && File(dir, relative).isFile) text = File(dir, relative).readText().replace("\r\n", "\n")
                dir = dir?.parentFile
            }
            text ?: throw AssertionError("$relative not found from the test working directory")
        }

        @Test
        fun `the scorer uses the energy multiplier, the option weigher and the all-stats rule`() {
            assertTrue(source.contains("energyValue * eventEnergyMultiplier(campaign.trainee.energy, enablePrioritizeEnergyOptions)"))
            assertFalse(source.contains("energyValue * 100"))
            assertTrue(source.contains("selectionWeight[rewardIndex] = weighEventOption(formattedReward) { line ->"))
            assertTrue(source.contains("} else if (line.trim().lowercase().startsWith(\"all stats\")) {"))
            assertTrue(source.contains("formattedLine.toInt() * 5"))
        }
    }
}

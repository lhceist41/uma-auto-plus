package com.steve1316.uma_android_automation.bot

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The keep-original-vs-keep-rerolled policy, including the mandatory decision on the live
 * 2026-07-08 anchor career (the only fully captured Spark Selection flow): the policy must
 * choose the Original set, and because the pager opens on the Rerolled page, that choice
 * forces a chevron navigation - keeping the displayed page would keep the wrong set.
 */
@DisplayName("Spark keep policy")
class SparkKeepPolicyTest {
    // ------------------------------------------------------------------
    // skills.json-backed white classification (same lookup the app ships)
    // ------------------------------------------------------------------

    private val skillNames: Set<String> by lazy {
        var dir: File? = File(System.getProperty("user.dir"))
        var file: File? = null
        repeat(5) {
            val candidate = File(dir, "src/data/skills.json")
            if (candidate.isFile) {
                file = candidate
                return@repeat
            }
            dir = dir?.parentFile
        }
        val data = requireNotNull(file) { "src/data/skills.json not found from ${System.getProperty("user.dir")}" }
        JSONObject(data.readText()).keys().asSequence().toSet()
    }

    /** Spark whites show the base skill name; the catalog keys upgrade tiers with a circle. */
    private fun whiteClassOf(name: String): SparkWhiteClass =
        if (name in skillNames || "$name ○" in skillNames) SparkWhiteClass.SKILL else SparkWhiteClass.RACE

    private fun white(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.WHITE, whiteClassOf(name))

    private fun stat(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.STAT)

    private fun pink(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.APTITUDE)

    private fun unique(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.UNIQUE)

    private fun complete(rows: List<SparkRowFact>) = SparkSetReading(rows, SparkScanTermination.COMPLETE_END_MARKER)

    private val profile =
        SparkChooserProfile(
            traineeIdentity = "[Jokester Vibes] Tosen Jordan",
            objective = "sparks",
            blueTargetsOrdered = listOf("Speed", "Stamina", "Power"),
            preferredDistance = "Medium",
            preferredStyle = "Pace Chaser",
            preferredSurface = "Turf",
            plannedSkillNames = emptyList(),
        )

    // The anchor career's two sets, stars exactly as the fixture pixels read them
    // (SparkScreenProbeFixtureTest pins the same values against the captures).
    private val anchorOriginal =
        complete(
            listOf(
                stat("Stamina", 2),
                pink("Pace Chaser", 2),
                unique("Ignited Spirit: Speed +", 1),
                white("Pop & Polish", 1),
                white("Tenno Sho (Spring)", 3),
                white("Preferred Position", 2),
                white("Pace Chaser Straightaways", 2),
                white("Pace Chaser Corners", 1),
                white("Sympathy", 2),
                white("Osaka Hai", 1),
            ),
        )

    private val anchorRerolled =
        complete(
            listOf(
                stat("Wit", 2),
                pink("Pace Chaser", 1),
                unique("Ignited Spirit SPD", 2),
                white("Pop & Polish", 1),
                white("Medium Straightaways", 2),
                white("Straightaway Recovery", 1),
                white("Pace Chaser Corners", 1),
            ),
        )

    @Nested
    @DisplayName("the anchor career")
    inner class Anchor {
        @Test
        fun `the race sparks classify as races and the skill sparks as skills`() {
            assertEquals(SparkWhiteClass.RACE, whiteClassOf("Tenno Sho (Spring)"))
            assertEquals(SparkWhiteClass.RACE, whiteClassOf("Osaka Hai"))
            for (skill in listOf("Pop & Polish", "Preferred Position", "Pace Chaser Straightaways", "Pace Chaser Corners", "Sympathy", "Straightaway Recovery", "Medium Straightaways")) {
                assertEquals(SparkWhiteClass.SKILL, whiteClassOf(skill), skill)
            }
        }

        @Test
        fun `the policy keeps the Original set`() {
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorRerolled, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertTrue(choice.certain)
            // The 3-star "Tenno Sho (Spring)" race spark is a protected holding the rerolled
            // set cannot match at any class.
            assertEquals("three_star_protection", choice.decidedBy)
            assertEquals(1, choice.original.protectedRelevantWhite3)
            assertEquals(0, choice.rerolled.protectedRelevantWhite3)
        }

        @Test
        fun `keeping the Original requires leaving the displayed Rerolled page`() {
            // The pager opens on page 1 = Rerolled (pinned against the capture by the fixture
            // test's page-dot assertions). The winner is the OTHER page, so the handler's
            // navigate-to-winner branch must fire; confirming the displayed page would keep
            // the wrong set.
            val displayedPageOnEntry = SparkSetSide.REROLLED
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorRerolled, profile)
            assertNotEquals(displayedPageOnEntry, choice.side)
        }

        @Test
        fun `the rerolled set would win the unique tier alone - protection outranks it`() {
            // The redraw upgraded the unique (2-star vs 1-star). A policy that compared
            // uniques before the 3-star protection would discard the Tenno Sho holding.
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorRerolled, profile)
            assertTrue(choice.rerolled.uniqueStars > choice.original.uniqueStars)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
        }
    }

    @Nested
    @DisplayName("tier ordering")
    inner class Tiers {
        private fun bareSet(blueName: String, blueStars: Int, extra: List<SparkRowFact> = emptyList()) =
            complete(listOf(stat(blueName, blueStars), pink("Turf", 1), unique("u", 1)) + extra)

        @Test
        fun `a clearly stronger rerolled set is chosen`() {
            val original = bareSet("Guts", 1)
            val rerolled = bareSet("Speed", 3)
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.REROLLED, choice.side)
            assertEquals("three_star_protection", choice.decidedBy)
        }

        @Test
        fun `a 3-star target blue is protected against any lesser trade`() {
            val original = bareSet("Speed", 3)
            val rerolled =
                bareSet("Stamina", 2, extra = listOf(white("Tenno Sho (Spring)", 3), white("Pop & Polish", 3)))
            assertEquals(SparkSetSide.ORIGINAL, SparkKeepPolicy.choose(original, rerolled, profile).side)
        }

        @Test
        fun `a desired 3-star pink is protected against a better blue that is not 3-star`() {
            val original = complete(listOf(stat("Stamina", 1), pink("Pace Chaser", 3), unique("u", 1)))
            val rerolled = complete(listOf(stat("Speed", 2), pink("Turf", 1), unique("u", 1)))
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("three_star_protection", choice.decidedBy)
        }

        @Test
        fun `a relevant 3-star white is protected the same way`() {
            val original = bareSet("Stamina", 1, extra = listOf(white("Osaka Hai", 3)))
            val rerolled = bareSet("Stamina", 2)
            assertEquals(SparkSetSide.ORIGINAL, SparkKeepPolicy.choose(original, rerolled, profile).side)
        }

        @Test
        fun `a 3-star target blue may buy out a lower protection class`() {
            // Trading a 3-star relevant white for a 3-star target blue is the farm program's
            // whole point: the protection classes are ranked, not absolute.
            val original = bareSet("Guts", 1, extra = listOf(white("Osaka Hai", 3)))
            val rerolled = bareSet("Speed", 3)
            assertEquals(SparkSetSide.REROLLED, SparkKeepPolicy.choose(original, rerolled, profile).side)
        }

        @Test
        fun `blues outrank pinks, uniques, and whites`() {
            val original = bareSet("Speed", 2)
            val rerolled = complete(listOf(stat("Guts", 2), pink("Pace Chaser", 2), unique("u", 2), white("Osaka Hai", 2)))
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("blue", choice.decidedBy)
        }

        @Test
        fun `at equal stars the earlier target stat wins the blue tier`() {
            val original = bareSet("Stamina", 2)
            val rerolled = bareSet("Speed", 2)
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.REROLLED, choice.side, "Speed is ranked above Stamina in the profile")
            assertEquals("blue", choice.decidedBy)
        }

        @Test
        fun `matched pinks beat raw pinks`() {
            val original = complete(listOf(stat("Speed", 2), pink("Pace Chaser", 2), unique("u", 1)))
            val rerolled = complete(listOf(stat("Speed", 2), pink("Dirt", 3), unique("u", 1)))
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("pink", choice.decidedBy)
        }

        @Test
        fun `irrelevant whites never outweigh a superior target blue`() {
            val original = bareSet("Speed", 2)
            val rerolled =
                bareSet("Speed", 1, extra = listOf(white("Sympathy", 2), white("Pop & Polish", 2), white("Preferred Position", 2)))
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("blue", choice.decidedBy)
        }

        @Test
        fun `unplanned skill whites carry no relevance weight, planned ones do`() {
            val planned = profile.copy(plannedSkillNames = listOf("Sympathy"))
            val original = bareSet("Speed", 2, extra = listOf(white("Sympathy", 2)))
            val rerolled = bareSet("Speed", 2, extra = listOf(white("Pop & Polish", 2)))
            val choice = SparkKeepPolicy.choose(original, rerolled, planned)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("relevant_whites", choice.decidedBy)
            // Without the plan both whites are irrelevant and the sets tie into keep-original.
            val unplanned = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals("tie", unplanned.decidedBy)
        }

        @Test
        fun `an identical set ties and the tie keeps the Original`() {
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorOriginal.copy(), profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("tie", choice.decidedBy)
            assertTrue(choice.certain)
        }
    }

    @Nested
    @DisplayName("uncertainty stays conservative")
    inner class Uncertainty {
        @Test
        fun `an incomplete reading on either side forces the keep-original fallback`() {
            for (termination in listOf(SparkScanTermination.TIMED_OUT_PARTIAL, SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.FAILED)) {
                val partialRerolled = SparkSetReading(anchorRerolled.rows, termination)
                val choice = SparkKeepPolicy.choose(anchorOriginal, partialRerolled, profile)
                assertEquals(SparkSetSide.ORIGINAL, choice.side, termination.name)
                assertFalse(choice.certain, termination.name)
                assertEquals("incomplete_read", choice.decidedBy)

                val partialOriginal = SparkSetReading(anchorOriginal.rows, termination)
                val reversed = SparkKeepPolicy.choose(partialOriginal, anchorRerolled, profile)
                assertEquals(SparkSetSide.ORIGINAL, reversed.side, "even an incomplete original is kept over a complete redraw")
                assertFalse(reversed.certain)
            }
        }

        @Test
        fun `an unreadable 3-star white protects the original but earns the rerolled side nothing`() {
            val unreadable3 = SparkRowFact(SPARK_UNREADABLE_NAME, 3, SparkRowKind.WHITE, SparkWhiteClass.UNKNOWN)
            val original = complete(listOf(stat("Stamina", 1), pink("Turf", 1), unique("u", 1), unreadable3))
            val betterBlue = complete(listOf(stat("Speed", 2), pink("Turf", 1), unique("u", 1)))
            val choice = SparkKeepPolicy.choose(original, betterBlue, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side, "uncertainty protects a holding")
            assertEquals("three_star_protection", choice.decidedBy)

            // The mirrored case: an unreadable 3-star white in the REROLLED set is no reason
            // to keep it.
            val rerolledWithUnknown = complete(listOf(stat("Stamina", 1), pink("Turf", 1), unique("u", 1), unreadable3))
            val plainOriginal = complete(listOf(stat("Speed", 2), pink("Turf", 1), unique("u", 1)))
            val mirrored = SparkKeepPolicy.choose(plainOriginal, rerolledWithUnknown, profile)
            assertEquals(SparkSetSide.ORIGINAL, mirrored.side)
        }

        @Test
        fun `an unreadable blue scores as non-target and cannot beat a readable target blue`() {
            val original = complete(listOf(stat("Speed", 1), pink("Turf", 1), unique("u", 1)))
            val rerolled = complete(listOf(stat(SPARK_UNREADABLE_NAME, 2), pink("Turf", 1), unique("u", 1)))
            val choice = SparkKeepPolicy.choose(original, rerolled, profile)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
        }

        @Test
        fun `an empty profile still produces a decision and ties keep the original`() {
            val empty = SparkChooserProfile(null, null, emptyList(), null, null, null, emptyList())
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorRerolled, empty)
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertTrue(choice.certain)
        }
    }

    @Nested
    @DisplayName("breakdowns")
    inner class Breakdowns {
        @Test
        fun `the record map carries the fields the choice record persists`() {
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorRerolled, profile)
            val map = choice.original.toRecordMap()
            for (key in listOf(
                "target_blue_stars",
                "blue_target_rank",
                "raw_blue_stars",
                "matched_pink_stars",
                "raw_pink_stars",
                "unique_stars",
                "relevant_white_stars",
                "protected_three_star",
                "unknown_white_three_star",
                "total_stars",
                "rows",
                "unreadable_rows",
                "complete",
            )) {
                assertTrue(key in map, key)
            }
            assertEquals(10, map["rows"])
            assertEquals(true, map["complete"])
        }

        @Test
        fun `the anchor breakdown is exactly what the pixels showed`() {
            val choice = SparkKeepPolicy.choose(anchorOriginal, anchorRerolled, profile)
            with(choice.original) {
                assertEquals(2, targetBlueStars)
                assertEquals(1, blueTargetRank)
                assertEquals(2, matchedPinkStars)
                assertEquals(1, uniqueStars)
                assertEquals(17, totalStars)
            }
            with(choice.rerolled) {
                assertEquals(0, targetBlueStars, "Wit is not a target stat")
                assertEquals(-1, blueTargetRank)
                assertEquals(1, matchedPinkStars)
                assertEquals(2, uniqueStars)
                assertEquals(10, totalStars)
            }
        }
    }
}

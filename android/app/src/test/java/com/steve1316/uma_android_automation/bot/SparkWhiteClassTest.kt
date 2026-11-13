package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.VeteranFactorDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * White spark rows take their class from the shipped spark-name catalog. The labels are every white row text read on
 * live career-end spark screens (OCR damage included), each with the family a person assigned it.
 */
@DisplayName("Spark white class from the spark-name catalog")
class SparkWhiteClassTest {
    private val catalog =
        VeteranFactorDomain.parse(assetFile(VeteranFactorDomain.ASSET_NAME).readText())
            ?: error("the shipped veteran_factor_domain.json should parse")

    private val catalogFamily: (String) -> String? = { catalog.resolve(it, SparkRowKind.WHITE).sourceFamily }

    private fun classOf(name: String): SparkWhiteClass = sparkWhiteClass(name, catalogFamily)

    private val skillLabels =
        listOf(
            "Corner Adept O",
            "Playtime's Over!",
            "Firm Conditions O",
            "Homestretch Haste",
            "On the Attack",
            "Sunny Sign",
            "Medium Straightaways O",
            "Steadfast",
            "Pace Chaser Savvy O",
            "Straightaway Adept",
            "Tether",
            "Pace Chaser Straightaways O",
            "Corner Recovery O",
            "Groundwork",
            "Ignited Spirit SPD",
            "Maverick O",
            "Prepared to Pass",
            "Straightaway Recovery",
            "Medium Corners O",
            "Sympathy",
            "Outer Swell",
            "MaverickO",
            "Murmur",
            "Slick Surge",
            "Competitive Spirit O",
            "Early Lead",
            "End Closer Straightaways O",
            "Left-Handed O",
            "Mile Corners O",
            "Unyielding Spirit",
            "Long Corners O",
            "Pace Chaser Corners O",
            "Levelheaded",
            "Lone Wolf",
            "Mile Straightaways O",
            "Nimble Navigator",
            "Up-Tempo",
            "Opening Gambit",
            "Preferred Position",
            "Shifting Gears",
            "laytime's Over!",
            "Be Still",
            "Fall Runner O",
            "Fast-Paced",
            "Flustered Pace Chasers",
            "Nakayama Racecourse O",
            "Right-Handed O",
            "Straightaway Spurt",
            "Focus",
            "Hakodate Racecourse O",
            "Front Runner Straightaways O",
            "Glittering Star",
            "Lucky Seven",
            "Prudent Positioning",
            "Sprint Straightaways O",
            "Standing By",
            "Tail Held High",
            "Deep Breaths",
            "Productive Plan",
            "gnited Spirit SPD",
            "-ong Corners O",
            "Final Push",
            "Front Runner Savvy O",
            "Ignited Spirit PWR",
            "Late Surger Savvy O",
            "Moxie",
            "Pace Strategy",
            "Ramp Up",
            "Shake It Out",
            "Standard Distance O",
            "Triple 7s",
            "-eft-Handed O",
            "Funabashi Racecourse O",
            "Go with the Flow",
            "Late Surger Straightaways O",
            "Passing Pro",
            "Shrewd Step",
            "Eager",
            "Extra Tank",
            "Flustered Front Runners",
            "Forward, March!",
            "Frenzied Pace Chasers",
            "Front Runner Corners O",
            "Hawkeye",
            "Head-On",
            "Hesitant Front Runners",
            "Hesitant Pace Chasers",
            "Hydrate",
            "I,500,000 CC",
            "Leader's Pride",
            "Masterful Gambit",
            "Outer Post Proficiency O",
            "Pressure",
            "Rainy Days O",
            "Solid Steps",
            "Spring Runner O",
            "Sprinting Gear",
            "Straightaway Acceleration",
            "Strategist",
            "Subdued Late Surgers",
            "Summer Runner O",
            "Tactical Tweak",
            "Uma Stan",
            "Wet Conditions O",
            "Winter Runner O",
            "ate Surger Corners O",
            "1,500,000 CC",
            "Dn the Attack",
            "Dpening Gambit",
            "End Closer Straightaways",
            "Hanshin Racecourse O",
            "Hanshin RacecourseO",
            "Late Surger Corners O",
            "On the VWay to Our Dream",
            "On the Way to Our Dream",
            "ate Surger Savvy O",
            "evelheaded",
        )

    private val raceLabels =
        listOf(
            "Arima Kinen",
            "Takarazuka Kinen",
            "Yasuda Kinen",
            "Osaka Hai",
            "Satsuki Sho",
            "Japan C.",
            "Japanese Derby",
            "JBC Classic",
            "Japanese Oaks",
            "M.C. Nambu Hai",
            "Asahi Hai F.S.",
            "February S.",
            "Kashiwa Kinen",
            "NHK Mile C.",
            "Shuka Sho",
            "Tenno Sho (Autumn)",
            "Oka Sho",
            "Kikuka Sho",
            "Hanshin J.F.",
            "Mile Ch.",
            "Tokyo Daishoten",
            "Takamatsunomiya Kinen",
            "lapan C.",
            "Dka Sho",
            "Sprinters s.",
            "Teio Sho",
            "Dsaka Hai",
            "apanese Oaks",
            "Champions C.",
            "Sprinters S.",
            "Tenno Sho (Spring)",
        )

    private val scenarioLabels =
        listOf(
            "Our Grand Concert",
            "Dur Grand Concert",
            "Unity Cup",
            "Unity CupP",
            "Jnity Cup",
        )

    private fun mismatches(labels: List<String>, expected: SparkWhiteClass): List<String> =
        labels.mapNotNull { label -> classOf(label).takeIf { it != expected }?.let { "\"$label\" -> $it" } }

    @Nested
    @DisplayName("live labels")
    inner class LiveLabels {
        @Test
        fun `the table holds every distinct live label once`() {
            assertEquals(117, skillLabels.size)
            assertEquals(31, raceLabels.size)
            assertEquals(5, scenarioLabels.size)
            assertEquals(153, (skillLabels + raceLabels + scenarioLabels).toSet().size)
        }

        @Test
        fun `every skill label is SKILL, damaged reads included`() {
            assertEquals(emptyList<String>(), mismatches(skillLabels, SparkWhiteClass.SKILL))
        }

        @Test
        fun `every race label is RACE, abbreviated and damaged reads included`() {
            assertEquals(emptyList<String>(), mismatches(raceLabels, SparkWhiteClass.RACE))
        }

        @Test
        fun `scenario sparks count as RACE`() {
            assertEquals(emptyList<String>(), mismatches(scenarioLabels, SparkWhiteClass.RACE))
        }

        @Test
        fun `a race read the catalog cannot place stays UNKNOWN`() {
            assertEquals(SparkWhiteClass.UNKNOWN, classOf("lapanese Derby"))
        }
    }

    @Nested
    @DisplayName("no class without evidence")
    inner class NoEvidence {
        @Test
        fun `garbage, blank and unreadable names are UNKNOWN`() {
            for (name in listOf("Qxzj Wvvk Pfft", "", "   ", SPARK_UNREADABLE_NAME)) {
                assertEquals(SparkWhiteClass.UNKNOWN, classOf(name), "\"$name\"")
            }
        }

        @Test
        fun `without a catalog every white is UNKNOWN`() {
            for (name in listOf("Arima Kinen", "Playtime's Over!", "Unity Cup")) {
                assertEquals(SparkWhiteClass.UNKNOWN, sparkWhiteClass(name, null), name)
            }
        }

        @Test
        fun `a family outside skill, race and scenario is UNKNOWN`() {
            assertEquals(SparkWhiteClass.UNKNOWN, sparkWhiteClass("Speed", { "stat" }))
            assertEquals(SparkWhiteClass.UNKNOWN, sparkWhiteClass("Arima Kinen", { null }))
        }
    }

    @Nested
    @DisplayName("keep decisions with the catalog classes")
    inner class Decisions {
        private val profile =
            SparkChooserProfile(
                traineeIdentity = "test",
                objective = "sparks",
                blueTargetsOrdered = listOf("Speed"),
                preferredDistance = null,
                preferredStyle = "Pace Chaser",
                preferredSurface = null,
                plannedSkillNames = emptyList(),
            )

        private fun stat(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.STAT)

        private fun pink(stars: Int) = SparkRowFact("Pace Chaser", stars, SparkRowKind.APTITUDE)

        private fun unique(stars: Int) = SparkRowFact("u", stars, SparkRowKind.UNIQUE)

        private fun white(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.WHITE, classOf(name))

        private fun set(vararg rows: SparkRowFact) = SparkSetReading(rows.toList(), SparkScanTermination.COMPLETE_END_MARKER)

        private fun choose(original: SparkSetReading, rerolled: SparkSetReading) = SparkKeepPolicy.choose(original, rerolled, profile)

        @Test
        fun `a 3-star race white on the original is protected against a better blue`() {
            val choice =
                choose(
                    set(stat("Guts", 1), pink(2), unique(1), white("Arima Kinen", 3)),
                    set(stat("Speed", 2), pink(2), unique(1), white("Corner Adept O", 1)),
                )
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("three_star_protection", choice.decidedBy)
        }

        @Test
        fun `a 3-star desired pink outranks a 3-star race white`() {
            val choice =
                choose(
                    set(stat("Guts", 1), pink(3), unique(1)),
                    set(stat("Speed", 2), pink(2), unique(1), white("Arima Kinen", 3)),
                )
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("three_star_protection", choice.decidedBy)
        }

        @Test
        fun `an unplaceable 3-star white earns the rerolled set nothing and protects the original`() {
            val onRerolled =
                choose(
                    set(stat("Speed", 2), pink(2), unique(1)),
                    set(stat("Guts", 1), pink(2), unique(1), white("lapanese Derby", 3)),
                )
            assertEquals(SparkSetSide.ORIGINAL, onRerolled.side)
            assertEquals("blue", onRerolled.decidedBy)

            val onOriginal =
                choose(
                    set(stat("Guts", 1), pink(2), unique(1), white("lapanese Derby", 3)),
                    set(stat("Speed", 2), pink(2), unique(1)),
                )
            assertEquals(SparkSetSide.ORIGINAL, onOriginal.side)
            assertEquals("three_star_protection", onOriginal.decidedBy)
        }

        @Test
        fun `an unplanned 3-star skill white does not beat a better blue`() {
            val choice =
                choose(
                    set(stat("Speed", 2), pink(2), unique(1)),
                    set(stat("Guts", 1), pink(2), unique(1), white("laytime's Over!", 3)),
                )
            assertEquals(SparkSetSide.ORIGINAL, choice.side)
            assertEquals("blue", choice.decidedBy)
        }

        @Test
        fun `a 3-star race white only on the rerolled side wins it (unchanged policy)`() {
            val choice =
                choose(
                    set(stat("Speed", 2), pink(2), unique(1)),
                    set(stat("Guts", 1), pink(2), unique(1), white("Arima Kinen", 3)),
                )
            assertEquals(SparkSetSide.REROLLED, choice.side)
            assertEquals("three_star_protection", choice.decidedBy)
        }
    }

    @Nested
    @DisplayName("source guard")
    inner class SourceGuard {
        @Test
        fun `the navigator classes white rows through the catalog, not the skill lookup`() {
            val navigator = File(sourceRoot(), "CareerLaunchNavigator.kt").readText()
            val at = navigator.indexOf("private fun resolveSparkWhiteClass(")
            assertTrue(at >= 0)
            val body = navigator.substring(at, navigator.indexOf("\n    }\n", at))
            assertTrue("sparkWhiteClass(" in body && "sparkNameCatalog" in body, "the white class comes from the spark-name catalog")
            assertFalse("getSkillData" in body, "the skill lookup classed races as skills")
        }
    }

    private fun assetFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/assets")
            if (a.isDirectory) return File(a, relative)
            val b = File(dir, "android/app/src/main/assets")
            if (b.isDirectory) return File(b, relative)
            dir = dir?.parentFile
        }
        error("could not locate the assets root")
    }

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (candidate.isDirectory) return candidate
            val fromRepoRoot = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (fromRepoRoot.isDirectory) return fromRepoRoot
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}

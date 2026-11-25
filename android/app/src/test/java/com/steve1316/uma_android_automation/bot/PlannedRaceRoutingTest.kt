package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.usesSmartRacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A mandatory planned race is entered by name (smart racing's by-name search) whatever the fan-farming setting says; the standard picker
 * never looks for it and once raced American JCC and Nakayama Kinen over a planned Tokai Stakes and February Stakes with fan farming off.
 */
@DisplayName("Planned race routing and standard-tail name read")
class PlannedRaceRoutingTest {
    private fun route(
        fanOrGoalPts: Boolean = false,
        trophy: Boolean = false,
        planned: Boolean = false,
        fanEmergency: Boolean = false,
        racingPlan: Boolean = true,
        farmingFans: Boolean = false,
        forceRacing: Boolean = false,
        junior: Boolean = false,
    ): Boolean = usesSmartRacing(fanOrGoalPts, trophy, planned, fanEmergency, racingPlan, farmingFans, forceRacing, junior)

    @Test
    fun `a planned race is searched by name with fan farming off`() {
        assertTrue(route(planned = true, farmingFans = false))
        // Control: the same settings without a planned race keep the standard picker.
        assertFalse(route(planned = false, farmingFans = false))
    }

    @Test
    fun `a planned race is searched by name with fan farming on`() {
        assertTrue(route(planned = true, farmingFans = true))
    }

    @Test
    fun `force racing keeps the standard picker ahead of a planned race`() {
        // Force racing overrides every other racing setting and must race every turn; the by-name search skips the turn when the planned race is missing.
        assertFalse(route(planned = true, farmingFans = true, forceRacing = true))
        assertFalse(route(planned = true, farmingFans = false, forceRacing = true))
        assertFalse(route(planned = true, forceRacing = true, junior = true))
        assertFalse(route(farmingFans = true, forceRacing = true))
    }

    @Test
    fun `a fan emergency keeps the old rule ahead of a planned race`() {
        // Fan farming off: the standard picker admits single-star rows and scrolls, so an unmet fan goal is not lost to a planned race the search misses.
        assertFalse(route(planned = true, fanEmergency = true, farmingFans = false))
        assertFalse(route(planned = true, fanEmergency = true, junior = true))
        // Fan farming on: the planned race was already searched by name during an emergency, and still is.
        assertTrue(route(planned = true, fanEmergency = true, farmingFans = true))
        // Control: the emergency alone does not change the no-plan rule.
        assertFalse(route(fanEmergency = true, farmingFans = false))
    }

    @Test
    fun `a planned Junior Year race is searched by name, plain smart racing still waits for Classic`() {
        assertTrue(route(planned = true, junior = true))
        assertTrue(route(planned = true, farmingFans = true, junior = true))
        assertFalse(route(farmingFans = true, junior = true))
    }

    @Test
    fun `fan and goal-points requirements keep the standard picker ahead of the plan`() {
        assertFalse(route(fanOrGoalPts = true, planned = true, farmingFans = true))
        assertFalse(route(fanOrGoalPts = true, planned = true, junior = true))
        assertFalse(route(fanOrGoalPts = true, trophy = true, planned = true))
    }

    @Test
    fun `a trophy requirement keeps its own year rule ahead of the plan`() {
        assertTrue(route(trophy = true, planned = true))
        assertFalse(route(trophy = true, planned = true, junior = true))
    }

    @Test
    fun `without a racing plan only a trophy requirement uses smart racing`() {
        assertFalse(route(racingPlan = false, farmingFans = true))
        assertTrue(route(racingPlan = false, trophy = true))
    }

    @Test
    fun `the plan is dropped while a goal requirement is active, so it never outranks one`() {
        val handle = racingSource().substringAfter("private fun handleExtraRace(").substringBefore("val aptitudesMatch")
        assertTrue(
            Regex(
                """if \(fanRequirementForcesRace \|\| hasTrophyRequirement \|\| hasInsufficientGoalRacePtsRequirement\) \{\s*Pair\(null, null\)\s*\} else \{\s*findMandatoryExtraRaceForCurrentTurn\(\)""",
            ).containsMatchIn(handle),
        )
    }

    // The standard multi-row tail runs on a device, so its name read is pinned in source. extractRaceName's crop (x -455, width 585) is calibrated for the
    // prediction anchor (x 881 at 1080 wide); from the selection bracket (x ~1019) it ends at 1149 and every attempt is rejected, so the pick read "".

    @Test
    fun `the standard tail reads the picked row's name from its prediction anchor`() {
        val tail = racingSource().substringAfter("private fun processStandardRacing(").substringBefore("private fun processGrandConcertForcedFanRace(")
        val reads = Regex("""extractRaceName\(([^)]*)\)""").findAll(tail).map { it.groupValues[1].trim() }.toList()
        assertTrue("filteredAnchors[index]" in reads, "tail reads: $reads")
        for (bracketPoint in listOf("filteredLocations", "extraRaceLocations", "selectedExtraRace", "target")) {
            assertFalse(reads.any { it.startsWith(bracketPoint) }, "extractRaceName fed a bracket point: $reads")
        }
        // The anchor list stays parallel to the bracket list and goes through the same goal-grade filter.
        assertTrue(Regex("""extraRaceLocations\.add\(selectedExtraRace\)\s*rowAnchors\.add\(anchor\.location\)""").containsMatchIn(tail))
        assertEquals(1, Regex("""filteredAnchors = goalIndices\.map \{ rowAnchors\[it] \}""").findAll(tail).count())
    }

    private fun racingSource(): String = File(kotlinRoot(), "bot/Racing.kt").readText().replace("\r\n", "\n")

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        error("Kotlin source root not found from ${System.getProperty("user.dir")}")
    }
}

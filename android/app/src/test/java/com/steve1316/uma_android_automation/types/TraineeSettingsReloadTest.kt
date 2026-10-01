package com.steve1316.uma_android_automation.types

import com.steve1316.uma_android_automation.types.Trainee.Companion.Stats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** A rotation resync swaps in another trainee's settings mid-career: preset-owned values must follow, observed values must survive. */
@DisplayName("Trainee training settings follow a mid-career settings swap")
class TraineeSettingsReloadTest {
    private fun targets(base: Int): Map<TrackDistance, Stats> =
        TrackDistance.entries.withIndex().associate { (i, distance) ->
            val offset = base + i * 10
            distance to Stats(speed = 1100 + offset, stamina = 400 + offset, power = 700 + offset, guts = 200 + offset, wit = 300 + offset)
        }

    private val mileTargets = targets(0)
    private val longTargets = targets(5)

    @Test
    @DisplayName("after the swap the trainee trains toward the new slot's distance and targets")
    fun reloadAppliesNewSlot() {
        val trainee = Trainee()
        trainee.applyTrainingSettings("Mile", mileTargets)
        assertEquals(TrackDistance.MILE, trainee.trackDistance)

        trainee.applyTrainingSettings("Long", longTargets)

        assertEquals(TrackDistance.LONG, trainee.trackDistance)
        for (distance in TrackDistance.entries) {
            assertEquals(longTargets.getValue(distance).asMap(), trainee.getStatTargetsByDistance(distance), "targets for $distance")
        }
        assertEquals(longTargets.getValue(TrackDistance.LONG).asMap(), trainee.getStatTargetsByDistance())
    }

    @Test
    @DisplayName("an empty override in the new slot falls back to the observed best aptitude, not the old override")
    fun emptyOverrideFallsBackToAptitude() {
        val trainee = Trainee()
        trainee.trackDistanceAptitudes[TrackDistance.MEDIUM] = Aptitude.A
        trainee.applyTrainingSettings("Mile", mileTargets)
        assertEquals(TrackDistance.MILE, trainee.trackDistance)

        trainee.applyTrainingSettings("", longTargets)

        assertEquals(TrackDistance.MEDIUM, trainee.trackDistance)
    }

    @Test
    @DisplayName("screen-observed state survives the reload")
    fun observedStateUnchanged() {
        val trainee = Trainee()
        trainee.trackDistanceAptitudes[TrackDistance.LONG] = Aptitude.A
        trainee.trackDistanceAptitudes[TrackDistance.MILE] = Aptitude.C
        trainee.trackSurfaceAptitudes[TrackSurface.TURF] = Aptitude.A
        trainee.setTraineeStats(speed = 612, stamina = 480, power = 350, guts = 210, wit = 300)
        trainee.observeFanCount(4321)
        val distanceBefore = trainee.trackDistanceAptitudes.toMap()
        val surfaceBefore = trainee.trackSurfaceAptitudes.toMap()
        val stylesBefore = trainee.runningStyleAptitudes.toMap()
        val statsBefore = trainee.stats.asMap()

        trainee.applyTrainingSettings("Long", longTargets)

        assertEquals(distanceBefore, trainee.trackDistanceAptitudes)
        assertEquals(surfaceBefore, trainee.trackSurfaceAptitudes)
        assertEquals(stylesBefore, trainee.runningStyleAptitudes)
        assertEquals(statsBefore, trainee.stats.asMap())
        assertEquals(4321, trainee.fans)
    }

    @Test
    @DisplayName("the settings read re-reads the distance override on every call, not only at construction")
    fun settingsReadReReadsOverride() {
        val body = sourceBody("types/Trainee.kt", "fun setStatTargetsByDistances() {")
        assertTrue(
            body.contains("applyTrainingSettings(SettingsHelper.getStringSetting(\"training\", \"preferredDistanceOverride\"), targets)"),
            "setStatTargetsByDistances must read the override and apply it together with the targets",
        )
    }

    @Test
    @DisplayName("the rotation resync reload refreshes the trainee and every cached preset-owned Campaign setting before rebuilding Training")
    fun campaignReloadRefreshesTraineeAndCachedSettings() {
        val body = sourceBody("bot/Campaign.kt", "private fun reloadTraineeConfig() {")
        val rebuild = body.indexOf("training = Training(game, this)")
        val refreshes =
            listOf(
                "trainee.setStatTargetsByDistances()",
                "mustRestBeforeSummer = readMustRestBeforeSummer()",
                "moodFloor = readMoodFloor()",
                "skillSpendObjective = readSkillSpendObjective()",
                "resolvedSkillThreshold = resolveAndLogSkillThreshold()",
            )
        for (call in refreshes) {
            val at = body.indexOf(call)
            assertTrue(at >= 0, "reloadTraineeConfig must call `$call`")
            assertTrue(at < rebuild, "`$call` must run before Training is rebuilt")
        }
    }

    @Test
    @DisplayName("the skill check threshold is read through the reloadable policy, not frozen at construction")
    fun skillThresholdFollowsTheReloadedPolicy() {
        val source = campaignSourceText()
        assertTrue(source.contains("protected val skillPointsRequired: Int\n        get() = resolvedSkillThreshold.value"))
    }

    @Test
    @DisplayName("Grand Concert lesson scoring takes its stat priority from the rebuilt Training")
    fun grandConcertStatPriorityFollowsTraining() {
        val source = sourceFile("bot/campaigns/GrandConcert.kt").readText().replace("\r\n", "\n")
        assertTrue(source.contains("private val statPriority: List<StatName>\n        get() = training.statPrioritization"))
        assertTrue(!source.contains("\"statPrioritization\""), "GrandConcert must not keep its own copy of the stat priority setting")
    }

    @Test
    @DisplayName("the Trackblazer shop reads its excluded items on each use, so a resync applies the new preset's list")
    fun trackblazerExcludedItemsFollowTheLiveSettings() {
        val source = sourceFile("types/TrackblazerShopList.kt").readText().replace("\r\n", "\n")
        assertTrue(source.contains("private val excludedItemsString: String\n        get() = SettingsHelper.getStringSetting("))
    }

    @Test
    @DisplayName("the reload log line follows the resync line")
    fun reloadLogFollowsResyncLine() {
        val source = campaignSourceText()
        val resynced = source.indexOf("[ROTATION] Resynced onto interrupted career")
        val reloaded = source.indexOf("[CONFIG_DRIFT] trainee settings reloaded")
        assertTrue(resynced >= 0 && reloaded > resynced)
    }

    private fun campaignSourceText(): String = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")

    private fun sourceBody(relative: String, header: String): String {
        val source = sourceFile(relative).readText().replace("\r\n", "\n")
        assertTrue(source.contains(header), "missing `$header` in $relative")
        return source.substringAfter(header).substringBefore("\n    }\n")
    }

    private fun sourceFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("$relative not found from ${System.getProperty("user.dir")}")
    }
}

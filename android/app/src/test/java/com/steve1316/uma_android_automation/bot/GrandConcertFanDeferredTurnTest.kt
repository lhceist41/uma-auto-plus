package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.forcesFanRace
import com.steve1316.uma_android_automation.bot.Racing.Companion.plainRequirementReason
import com.steve1316.uma_android_automation.bot.Racing.Companion.requirementForcesExtraRace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A turn whose fan arm the Grand Concert policy deferred follows the user's racing settings: every read that would treat
 * the turn as fan-forced reads [Racing.fanRequirementForcesRace] instead of the raw flag, while the reads that compute or
 * consume the deferral keep the raw flag. A forced (not deferred) turn is unchanged. Racing needs a live Game, so the
 * sites are pinned in source and the decision itself is the pure [forcesFanRace].
 */
@DisplayName("Grand Concert deferred fan turn follows the user's racing settings")
class GrandConcertFanDeferredTurnTest {
    // ---- the decision ----

    @Test
    @DisplayName("the fan arm is off only while the current decision deferred it; a forced decision keeps it")
    fun effectiveFanArm() {
        assertFalse(forcesFanRace(hasFanRequirement = true, deferred = true))
        assertTrue(forcesFanRace(hasFanRequirement = true, deferred = false)) // forced: decideNextAction reset it and never set it
        assertFalse(forcesFanRace(hasFanRequirement = false, deferred = false))
    }

    @Test
    @DisplayName("a deferred turn whose day is re-read by smart racing still applies the user's filters and opportunity cost")
    fun deferralSurvivesADateReRead() {
        // processSmartRacing re-reads the date before both reads; a corrected or misread day must not turn forcing back on.
        val smart = body("processSmartRacing")
        val reRead = smart.indexOf("campaign.updateDate()")
        assertTrue(reRead in 0 until smart.indexOf("filterRacesByCriteria("), "the hazard this guards: the date is re-read before the filters")
        assertTrue(reRead < smart.indexOf("if (fanRequirementForcesRace || hasTrophyRequirement) {"), "and before the opportunity-cost read")
        val start = racing.indexOf("val fanRequirementForcesRace: Boolean")
        val getter = racing.substring(start, racing.indexOf('\n', racing.indexOf("get()", start)))
        assertFalse(getter.contains("date"), "the deferral must not depend on the live date: $getter")
        assertFalse(source("bot/Campaign.kt").contains("racing.fanRequirementDeferred = date"), "the decision's mark must not be a day")
    }

    @Test
    @DisplayName("deferred: no fans-first Grand Concert selector, no forced extra race, settings-based race reason; forced: all unchanged")
    fun deferredTurnThroughThePureGates() {
        val deferred = forcesFanRace(true, deferred = true)
        val forced = forcesFanRace(true, deferred = false)
        assertFalse(GrandConcertFanRaceSelector.appliesToForcedRace(true, deferred, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false))
        assertTrue(GrandConcertFanRaceSelector.appliesToForcedRace(true, forced, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false))
        assertFalse(requirementForcesExtraRace(false, deferred, false, false, ignoreFanRequirement = false))
        assertTrue(requirementForcesExtraRace(false, forced, false, false, ignoreFanRequirement = false))

        fun reason(fanArm: Boolean) =
            plainRequirementReason("Grand Concert needs 5,000 fans", fanArm, false, null, null, 1301, false, null, null, false, 3)
        assertEquals("fan farming is on and this turn falls on your racing interval (every 3 turn(s))", reason(deferred))
        assertEquals("Grand Concert needs 5,000 fans", reason(forced))
    }

    // ---- every read that must follow the deferral (each fails on the code before the fix) ----

    private val racing by lazy { source("bot/Racing.kt") }

    private fun body(name: String, src: String = racing): String {
        val start = src.indexOf("fun $name(")
        require(start >= 0) { "missing fun $name" }
        val next = Regex("\n    (private |internal |override |open )?fun ").find(src, start + 1)?.range?.first ?: src.length
        return src.substring(start, next)
    }

    private fun assertNoRawFanFlag(text: String, where: String) {
        val code = text.lines().joinToString("\n") { it.substringBefore("//") }
        assertFalse(Regex("\\bhasFanRequirement\\b").containsMatchIn(code), "$where still reads the raw hasFanRequirement: a deferred turn would act fan-forced")
    }

    @Test
    @DisplayName("eligibility and smart racing apply the user's race filters on a deferred turn (filterRacesByCriteria)")
    fun filtersFollowTheDeferral() {
        val filter = body("filterRacesByCriteria")
        assertTrue(filter.contains("val isRequirementActive = fanRequirementForcesRace || hasTrophyRequirement"))
        assertNoRawFanFlag(filter, "filterRacesByCriteria")
    }

    @Test
    @DisplayName("a deferred turn keeps the consecutive-race warning and passes isMandatoryRace=false (handleRaceEvents)")
    fun consecutiveWarningFollowsTheDeferral() {
        val events = body("handleRaceEvents")
        assertTrue(events.contains("if (isScheduledRace || fanRequirementForcesRace || hasTrophyRequirement || hasInsufficientGoalRacePtsRequirement)"))
        assertTrue(events.contains("\"isMandatoryRace\" to (isScheduledRace || fanRequirementForcesRace ||"))
        assertNoRawFanFlag(events, "handleRaceEvents")
    }

    @Test
    @DisplayName("a deferred turn reaches the mandatory racing plan lookup and smart racing (handleExtraRace)")
    fun planAndSmartRacingFollowTheDeferral() {
        val extra = body("handleExtraRace")
        val planGate = extra.indexOf("if (fanRequirementForcesRace || hasTrophyRequirement || hasInsufficientGoalRacePtsRequirement) {")
        assertTrue(planGate in 0 until extra.indexOf("findMandatoryExtraRaceForCurrentTurn()"), "the plan lookup must be skipped only for a forcing fan arm")
        assertTrue(extra.contains("if (fanRequirementForcesRace || hasInsufficientGoalRacePtsRequirement) {"), "the smart-vs-standard choice must follow the deferral")
        assertNoRawFanFlag(extra, "handleExtraRace")
    }

    @Test
    @DisplayName("smart racing's opportunity-cost check applies on a deferred turn (processSmartRacing)")
    fun opportunityCostFollowsTheDeferral() {
        val smart = body("processSmartRacing")
        assertTrue(smart.contains("if (fanRequirementForcesRace || hasTrophyRequirement) {"))
        assertNoRawFanFlag(smart, "processSmartRacing")
    }

    @Test
    @DisplayName("a deferred turn never takes the fans-first Grand Concert selector or scrolls for the requirement (processStandardRacing)")
    fun selectorFollowsTheDeferral() {
        val standard = body("processStandardRacing")
        assertTrue(standard.contains("fanPressureActive = bFanEmergencyActive || fanRequirementForcesRace,"))
        assertTrue(standard.contains("(fanRequirementForcesRace || bFanEmergencyActive ||"))
        assertNoRawFanFlag(standard, "processStandardRacing")
    }

    @Test
    @DisplayName("the race line gives the settings reason on a deferred turn (requirementActive, requirementReason)")
    fun reasonLineFollowsTheDeferral() {
        val active = racing.substring(racing.indexOf("private val requirementActive: Boolean"), racing.indexOf("private fun requirementReason()"))
        assertTrue(active.contains("get() = fanRequirementForcesRace || hasTrophyRequirement"))
        val reason = body("requirementReason")
        assertTrue(reason.contains("hasFanRequirement = fanRequirementForcesRace,"))
    }

    // ---- the reads that compute or consume the deferral keep the raw flag ----

    @Test
    @DisplayName("the facts writer, the deferral entry points and the ignore-aware eligibility arms still read the raw flag")
    fun deferralInputsKeepTheRawFlag() {
        assertTrue(racing.contains("hasFanRequirement = GrandConcertFanRequirement.resolveHasFanRequirement(hasFanRequirement, scenarioFanRequirement)"))
        val eligibility = body("checkEligibilityToStartExtraRacingProcess")
        assertTrue(eligibility.contains("requirementForcesExtraRace(enableForceRacing, hasFanRequirement, hasTrophyRequirement, hasInsufficientGoalRacePtsRequirement, ignoreFanRequirement)"))
        assertTrue(eligibility.contains("if (hasFanRequirement && !ignoreFanRequirement) {"))
        val campaign = source("bot/Campaign.kt")
        assertTrue(campaign.contains("val isRacingRequirementActive = racing.hasFanRequirement || racing.hasTrophyRequirement"))
        val gc = source("bot/campaigns/GrandConcert.kt")
        assertTrue(body("considerFanRaceDeferral", gc).contains("if (!racing.hasFanRequirement || racing.hasTrophyRequirement"))
    }

    @Test
    @DisplayName("decideNextAction clears the deferral at every decision and marks the turn only when the fan arm is deferred")
    fun decisionMarksTheDeferredTurn() {
        val decide = body("decideNextAction", source("bot/Campaign.kt"))
        val reset = decide.indexOf("racing.fanRequirementDeferred = false")
        val firstReturn = decide.indexOf("return choose(")
        assertTrue(reset in 0 until firstReturn, "the reset must run before any early return")
        val deferredBranch = decide.indexOf("if (fanRaceDeferred) {")
        val mark = decide.indexOf("racing.fanRequirementDeferred = true")
        val forcedReturn = decide.indexOf("return choose(MainScreenAction.RACE, \"racing requirement active (fan/trophy/goal-pts)\")")
        assertTrue(mark in deferredBranch until forcedReturn, "only the deferred branch may mark the turn")
        assertEquals(1, source("bot/Campaign.kt").split("racing.fanRequirementDeferred = true").size - 1)
    }

    // ---- payouts are recorded on the main thread only ----

    @Test
    @DisplayName("fan-pressure payouts are recorded only by the main-thread deferral; the turn-start re-arm only reads a volatile flag")
    fun payoutsRecordedOnTheMainThreadOnly() {
        val gc = source("bot/campaigns/GrandConcert.kt")
        assertEquals(2, Regex("recordFanPressurePayout\\(\\)").findAll(gc).count(), "one definition and one call")
        val deferral = body("considerFanRaceDeferral", gc)
        assertTrue(deferral.contains("recordFanPressurePayout()"))
        assertTrue(deferral.indexOf("fanPayoutsBelowAssumption = paidLow") > deferral.indexOf("recordFanPressurePayout()"))
        val rearm = body("currentFanRequirementFromScenarioFacts", gc)
        assertFalse(rearm.contains("recordFanPressurePayout"), "the turn-start thread must not record payouts")
        assertFalse(rearm.contains("fanPressureRacePayouts"), "the turn-start thread must not touch the payout deque")
        assertTrue(rearm.contains("fanPayoutsBelowAssumption"))
        assertTrue(gc.contains("@Volatile private var fanPayoutsBelowAssumption = false"))
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            for (root in listOf("src/main/java", "android/app/src/main/java")) {
                val f = File(dir, "$root/com/steve1316/uma_android_automation/$relative")
                if (f.isFile) return f.readText().replace("\r\n", "\n")
            }
            dir = dir?.parentFile
        }
        error("could not locate $relative")
    }
}

package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.FAN_EMERGENCY_TURN_WINDOW
import com.steve1316.uma_android_automation.bot.Racing.Companion.admitsSingleStarRowsForGradedGoal
import com.steve1316.uma_android_automation.bot.Racing.Companion.canonicalizeRaceLabelForLookup
import com.steve1316.uma_android_automation.bot.Racing.Companion.countsForGoalGrade
import com.steve1316.uma_android_automation.bot.Racing.Companion.enterablePredictionAnchors
import com.steve1316.uma_android_automation.bot.Racing.Companion.goalGradeDbLabels
import com.steve1316.uma_android_automation.bot.Racing.Companion.goalGradeLabel
import com.steve1316.uma_android_automation.bot.Racing.Companion.indexOfBestByTierThenFans
import com.steve1316.uma_android_automation.bot.Racing.Companion.isFanEmergency
import com.steve1316.uma_android_automation.bot.Racing.Companion.mergePredictionAnchors
import com.steve1316.uma_android_automation.bot.Racing.Companion.minimumGoalGrade
import com.steve1316.uma_android_automation.bot.Racing.Companion.requirementForcesExtraRace
import com.steve1316.uma_android_automation.bot.Racing.Companion.restrictsToG1Only
import com.steve1316.uma_android_automation.types.PredictionTier
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.opencv.core.Point

/**
 * Unit tests for the race-list prediction anchor merging, the tier-aware extra-race
 * selection, and the fan-emergency policy predicate.
 */
@DisplayName("Racing Race Selection Tests")
class RacingRaceSelectionTest {
    // ////////////////////////////////////////////////////////////////////////////////////////////
    // isFanEmergency

    @Test
    @DisplayName("Fan emergency requires an unmet fan goal")
    fun fanEmergencyRequiresFanRequirement() {
        assertFalse(isFanEmergency(hasFanRequirement = false, turnsRemaining = 2))
    }

    @Test
    @DisplayName("Fan emergency activates inside the turn window")
    fun fanEmergencyInsideWindow() {
        assertTrue(isFanEmergency(hasFanRequirement = true, turnsRemaining = 0))
        assertTrue(isFanEmergency(hasFanRequirement = true, turnsRemaining = FAN_EMERGENCY_TURN_WINDOW))
    }

    @Test
    @DisplayName("Fan emergency stays off outside the turn window")
    fun fanEmergencyOutsideWindow() {
        assertFalse(isFanEmergency(hasFanRequirement = true, turnsRemaining = FAN_EMERGENCY_TURN_WINDOW + 1))
    }

    @Test
    @DisplayName("Fan emergency stays off when the turns-remaining OCR failed")
    fun fanEmergencyOcrFailure() {
        assertFalse(isFanEmergency(hasFanRequirement = true, turnsRemaining = -1))
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // requirementForcesExtraRace - the extra-race eligibility gate (integration boundary for the
    // Grand Concert fan-deferral: a deferred fan requirement must not re-force a race here).

    @Test
    @DisplayName("Case A: a deferred fan requirement alone does not force an extra race")
    fun deferredFanRequirementAloneDoesNotForceRace() {
        // Fan requirement active, deferred this turn, nothing else: the eligibility gate must not
        // force a race. Before the fan arm was excluded here, this same flag re-forced the race the
        // campaign had just deferred - the leak this test locks shut.
        assertFalse(
            requirementForcesExtraRace(
                enableForceRacing = false,
                hasFanRequirement = true,
                hasTrophyRequirement = false,
                hasInsufficientGoalRacePtsRequirement = false,
                ignoreFanRequirement = true,
            ),
        )
    }

    @Test
    @DisplayName("Case B: an independent race reason still forces a race under a deferred fan requirement")
    fun independentReasonStillForcesRaceWhileFanDeferred() {
        // Trophy, goal-points, and force-racing each still force the race even while the fan arm is
        // deferred; only the fan arm is suppressed.
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = true, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = true),
        )
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = true, ignoreFanRequirement = true),
        )
        assertTrue(
            requirementForcesExtraRace(enableForceRacing = true, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = true),
        )
    }

    @Test
    @DisplayName("Case C: a non-deferred fan requirement forces a race exactly as before")
    fun nonDeferredFanRequirementStillForcesRace() {
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = false),
        )
    }

    @Test
    @DisplayName("Case D: the default (non-GC) caller is unchanged; no requirement means no forced race")
    fun defaultCallerUnchanged() {
        // ignoreFanRequirement = false is the default for every existing caller, so a fan requirement
        // still forces a race and an empty requirement set still does not.
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = false),
        )
        assertFalse(
            requirementForcesExtraRace(false, hasFanRequirement = false, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = false),
        )
        // The ignore flag only affects the fan arm: with no fan requirement it changes nothing.
        assertFalse(
            requirementForcesExtraRace(false, hasFanRequirement = false, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = true),
        )
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // mergePredictionAnchors

    @Test
    @DisplayName("Doubles alone merge to double-tier anchors sorted by y")
    fun mergeDoublesOnly() {
        val merged = mergePredictionAnchors(listOf(Point(900.0, 800.0), Point(900.0, 400.0)), emptyList())
        assertEquals(2, merged.size)
        assertEquals(400.0, merged[0].location.y)
        assertEquals(800.0, merged[1].location.y)
        assertTrue(merged.all { it.tier == PredictionTier.DOUBLE })
    }

    @Test
    @DisplayName("Singles alone merge to single-tier anchors")
    fun mergeSinglesOnly() {
        val merged = mergePredictionAnchors(emptyList(), listOf(Point(900.0, 400.0), Point(900.0, 600.0)))
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.tier == PredictionTier.SINGLE })
    }

    @Test
    @DisplayName("A single match on the same row as a double is dropped")
    fun mergeDropsSingleOnSameRowAsDouble() {
        // A single-star template can weakly match inside a taller star stack ~30px off-center.
        val merged =
            mergePredictionAnchors(
                listOf(Point(900.0, 400.0)),
                listOf(Point(905.0, 430.0), Point(900.0, 650.0)),
            )
        assertEquals(2, merged.size)
        assertEquals(PredictionTier.DOUBLE, merged[0].tier)
        assertEquals(400.0, merged[0].location.y)
        assertEquals(PredictionTier.SINGLE, merged[1].tier)
        assertEquals(650.0, merged[1].location.y)
    }

    @Test
    @DisplayName("Duplicate single matches within one badge are deduplicated")
    fun mergeDropsDuplicateSingles() {
        val merged = mergePredictionAnchors(emptyList(), listOf(Point(900.0, 400.0), Point(902.0, 425.0)))
        assertEquals(1, merged.size)
        assertEquals(400.0, merged[0].location.y)
    }

    @Test
    @DisplayName("Starless points become none-tier anchors sorted with the rest")
    fun mergeStarlessOnly() {
        val merged = mergePredictionAnchors(emptyList(), emptyList(), listOf(Point(881.0, 600.0), Point(881.0, 400.0)))
        assertEquals(2, merged.size)
        assertEquals(400.0, merged[0].location.y)
        assertTrue(merged.all { it.tier == PredictionTier.NONE })
    }

    @Test
    @DisplayName("A starless point on the same row as a star anchor is dropped")
    fun mergeDropsStarlessOnStarRow() {
        // The fans icon exists on every row, so rows with a real prediction icon produce both a
        // star match and a projected starless point. The real tier must win.
        val merged =
            mergePredictionAnchors(
                listOf(Point(900.0, 400.0)),
                listOf(Point(900.0, 650.0)),
                listOf(Point(902.0, 402.0), Point(898.0, 648.0), Point(900.0, 880.0)),
            )
        assertEquals(3, merged.size)
        assertEquals(PredictionTier.DOUBLE, merged[0].tier)
        assertEquals(PredictionTier.SINGLE, merged[1].tier)
        assertEquals(PredictionTier.NONE, merged[2].tier)
        assertEquals(880.0, merged[2].location.y)
    }

    @Test
    @DisplayName("Rows a full entry apart are kept separate")
    fun mergeKeepsSeparateRows() {
        // Race list rows are ~200px apart; nothing at that distance may be merged.
        val merged =
            mergePredictionAnchors(
                listOf(Point(900.0, 400.0)),
                listOf(Point(900.0, 600.0)),
            )
        assertEquals(2, merged.size)
    }

    @Test
    @DisplayName("A fans row re-anchors a coincident single star onto the fans glyph, keeping tier")
    fun mergeReanchorsSingleOntoFansRow() {
        // Grand Concert regression (live turn-14 fixture): a row's distance-aptitude star cross-fires
        // the single-star template ~47px below the row's name line, while the row's fans glyph projects
        // onto the name line. The row must survive at the fans-glyph position (correct name OCR), not at
        // the aptitude star's y, and keep the star-derived tier.
        val fansRow1 = Point(881.0, 1194.5) // Hakodate row fans glyph, no nearby star
        val fansRow2 = Point(881.0, 1424.5) // Chukyo row fans glyph, on the name line
        val aptitudeStar = Point(881.0, 1471.0) // Chukyo aptitude star, below the name line
        val merged = mergePredictionAnchors(emptyList(), listOf(aptitudeStar), listOf(fansRow1, fansRow2))
        assertEquals(2, merged.size)
        assertEquals(PredictionTier.NONE, merged[0].tier)
        assertEquals(1194.5, merged[0].location.y)
        // Re-anchored from the aptitude star (1471) onto the fans glyph (1424.5); tier kept.
        assertEquals(PredictionTier.SINGLE, merged[1].tier)
        assertEquals(1424.5, merged[1].location.y)
    }

    @Test
    @DisplayName("A double star coincident with a fans glyph re-anchors onto the fans point")
    fun mergeReanchorsDoubleOntoFansRow() {
        val merged = mergePredictionAnchors(listOf(Point(900.0, 400.0)), emptyList(), listOf(Point(900.0, 405.0)))
        assertEquals(1, merged.size)
        assertEquals(PredictionTier.DOUBLE, merged[0].tier)
        assertEquals(405.0, merged[0].location.y)
    }

    @Test
    @DisplayName("With no starless points, star anchors keep their own positions (production path)")
    fun mergeWithoutStarlessKeepsStarPositions() {
        // findPredictionAnchors(includeStarless = false) passes no starless points, so the re-anchor
        // branch never runs and every anchor stays at its star match. This is the production Standard
        // Racing initial-detection path and must be unchanged by the fans re-anchor fix.
        val merged = mergePredictionAnchors(listOf(Point(900.0, 400.0)), listOf(Point(900.0, 650.0)))
        assertEquals(2, merged.size)
        assertEquals(400.0, merged[0].location.y)
        assertEquals(650.0, merged[1].location.y)
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // indexOfBestByTierThenFans

    @Test
    @DisplayName("Among equal tiers the highest fan count wins")
    fun selectionPicksMaxFansWithinTier() {
        val races =
            listOf(
                RaceDetails(2000, true),
                RaceDetails(7000, true),
                RaceDetails(3300, true),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("A double-star race beats a bigger single-star race")
    fun selectionPrefersTierOverFans() {
        val races =
            listOf(
                RaceDetails(15000, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(1200, true),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("With singles only, the highest fan count wins")
    fun selectionFallsBackToBestSingle() {
        val races =
            listOf(
                RaceDetails(1500, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(3300, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(-1, false),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("A single-star race beats a row with no prediction icon")
    fun selectionPrefersSingleOverNone() {
        val races =
            listOf(
                RaceDetails(5000, false),
                RaceDetails(800, false, predictionTier = PredictionTier.SINGLE),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("An empty list returns -1")
    fun selectionEmptyList() {
        assertEquals(-1, indexOfBestByTierThenFans(emptyList()))
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // RaceDetails tier default

    @Test
    @DisplayName("RaceDetails derives its tier from hasDoublePredictions when not specified")
    fun raceDetailsTierDefault() {
        assertEquals(PredictionTier.DOUBLE, RaceDetails(1000, true).predictionTier)
        assertEquals(PredictionTier.NONE, RaceDetails(1000, false).predictionTier)
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // canonicalizeRaceLabelForLookup (O/0 distance-token fix)

    @Test
    @DisplayName("an uppercase O inside a distance token is fixed to 0")
    fun canonFixesDistanceToken() {
        assertEquals("Chukyo Turf 1600m (Mile) Left", canonicalizeRaceLabelForLookup("Chukyo Turf 160Om (Mile) Left"))
        assertEquals("1600m", canonicalizeRaceLabelForLookup("160Om"))
    }

    @Test
    @DisplayName("multiple O in one distance token are all fixed")
    fun canonFixesMultipleO() {
        assertEquals("Kyoto Turf 1600m (Mile) Right", canonicalizeRaceLabelForLookup("Kyoto Turf 16OOm (Mile) Right"))
    }

    @Test
    @DisplayName("a valid distance token is left unchanged")
    fun canonLeavesValidDistance() {
        assertEquals("Chukyo Turf 1600m (Mile) Left", canonicalizeRaceLabelForLookup("Chukyo Turf 1600m (Mile) Left"))
    }

    @Test
    @DisplayName("venue words, grade badges, and unrelated O characters are never rewritten")
    fun canonLeavesNonDistanceO() {
        assertEquals("Tokyo Turf 2400m (Long) Left", canonicalizeRaceLabelForLookup("Tokyo Turf 2400m (Long) Left")) // Tokyo O untouched
        assertEquals("Ooi Dirt 2000m (Long) Left", canonicalizeRaceLabelForLookup("Ooi Dirt 2000m (Long) Left")) // Ooi venue untouched
        assertEquals("OP", canonicalizeRaceLabelForLookup("OP")) // grade badge untouched
        assertEquals("O", canonicalizeRaceLabelForLookup("O")) // a lone O is not a distance token
        assertEquals("Om", canonicalizeRaceLabelForLookup("Om")) // no digit -> not a distance token
    }

    // ---- routing reachability for a facts-derived Grand Concert fan requirement ----
    // These pin, without touching routing code, that once checkRacingRequirements sets
    // hasFanRequirement=true from committed Grand Concert facts, the existing consumers force the
    // race and reach the dedicated forced-fan selector branch.

    @Test
    @DisplayName("a facts-derived fan requirement forces extra-race eligibility, and a reviewed defer suppresses only the fan arm")
    fun factsFanRequirementForcesEligibility() {
        // hasFanRequirement=true (from facts) forces eligibility even with force racing and farming off.
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = false),
        )
        // A future reviewed deferral (ignoreFanRequirement=true) suppresses ONLY the fan arm.
        assertFalse(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = true),
        )
        // Trophy and goal-points remain independent reasons to race even while the fan arm is suppressed.
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = true, hasInsufficientGoalRacePtsRequirement = false, ignoreFanRequirement = true),
        )
        assertTrue(
            requirementForcesExtraRace(false, hasFanRequirement = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = true, ignoreFanRequirement = true),
        )
    }

    @Test
    @DisplayName("a facts-derived fan requirement reaches the GC forced-fan branch (trophy/goal-points absent, GC only)")
    fun factsFanRequirementReachesGcBranch() {
        // processStandardRacing computes fanPressureActive = bFanEmergencyActive || hasFanRequirement.
        // With hasFanRequirement=true from facts (bFanEmergencyActive stays false in GC), the branch applies.
        assertTrue(
            GrandConcertFanRaceSelector.appliesToForcedRace(scenarioIsGrandConcert = true, fanPressureActive = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false),
        )
        // A trophy or goal-points requirement keeps the generic path; the GC fan branch does not apply.
        assertFalse(
            GrandConcertFanRaceSelector.appliesToForcedRace(scenarioIsGrandConcert = true, fanPressureActive = true, hasTrophyRequirement = true, hasInsufficientGoalRacePtsRequirement = false),
        )
        assertFalse(
            GrandConcertFanRaceSelector.appliesToForcedRace(scenarioIsGrandConcert = true, fanPressureActive = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = true),
        )
        // A non-Grand-Concert scenario never takes the GC fan branch even under fan pressure.
        assertFalse(
            GrandConcertFanRaceSelector.appliesToForcedRace(scenarioIsGrandConcert = false, fanPressureActive = true, hasTrophyRequirement = false, hasInsufficientGoalRacePtsRequirement = false),
        )
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // restrictsToG1Only - the positive goal-criteria decision that gates race-list filtering.
    //
    // These pin the correctness core of the goal-tier reader: race selection restricts to G1 only
    // when the criteria line was positively read as "In G1,", and never from a mere failure to read
    // Pre-OP or G3. An unread tier (null) must stay permissive, or a criteria line the game shrank
    // to fit its slot filters the list to zero on a G1-less turn and cancels required racing until
    // the career goal expires. The scale-sweep template match and readGoalCriteriaTier() themselves
    // need OpenCV and a real Bitmap, so they are validated on-device, not here - the same boundary
    // upstream draws by unit-testing only the pure decision.

    @Test
    @DisplayName("A positively read Pre-OP-or-above goal never restricts to G1")
    fun preOpDoesNotRestrictToG1() {
        assertFalse(restrictsToG1Only(GoalCriteriaTier.PRE_OP_OR_ABOVE))
    }

    @Test
    @DisplayName("A positively read G3-or-above goal never restricts to G1")
    fun g3DoesNotRestrictToG1() {
        assertFalse(restrictsToG1Only(GoalCriteriaTier.G3_OR_ABOVE))
    }

    @Test
    @DisplayName("A positively read G1-only goal restricts to G1")
    fun g1OnlyRestrictsToG1() {
        assertTrue(restrictsToG1Only(GoalCriteriaTier.G1_ONLY))
    }

    @Test
    @DisplayName("An unreadable criteria tier (null) stays permissive and does not restrict to G1")
    fun unknownTierStaysPermissive() {
        // The career-loss regression guard: an unread goal must race any grade rather than be treated
        // as G1-only, which on a turn with no G1 in the pool would cancel racing and expire the goal.
        assertFalse(restrictsToG1Only(null))
    }

    @Test
    @DisplayName("G1_ONLY is the only tier that restricts race selection to G1")
    fun onlyG1OnlyRestricts() {
        // Enumerate every tier plus the unread case so a future tier added to the enum cannot silently
        // start restricting to G1 without a deliberate decision here.
        val restricting = (GoalCriteriaTier.entries + listOf<GoalCriteriaTier?>(null)).filter { restrictsToG1Only(it) }
        assertEquals(listOf(GoalCriteriaTier.G1_ONLY), restricting)
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // admitsSingleStarRowsForGradedGoal / enterablePredictionAnchors

    private fun near(turnsRemaining: Int) = turnsRemaining in 0..FAN_EMERGENCY_TURN_WINDOW

    @Test
    @DisplayName("A G1-only goal admits single-star rows inside the deadline window")
    fun g1GoalAdmitsSinglesNearDeadline() {
        for (turnsRemaining in 0..FAN_EMERGENCY_TURN_WINDOW) {
            assertTrue(admitsSingleStarRowsForGradedGoal(true, near(turnsRemaining), turnsRemaining, countdownReadable = true), "turnsRemaining=$turnsRemaining")
        }
    }

    @Test
    @DisplayName("A G1-only goal far from its deadline keeps single-star rows out")
    fun g1GoalFarFromDeadlineKeepsSinglesOut() {
        val turnsRemaining = FAN_EMERGENCY_TURN_WINDOW + 1
        assertFalse(admitsSingleStarRowsForGradedGoal(true, near(turnsRemaining), turnsRemaining, countdownReadable = true))
        assertFalse(admitsSingleStarRowsForGradedGoal(true, near(11), 11, countdownReadable = true))
    }

    @Test
    @DisplayName("An unread G1-goal deadline errs toward admitting single-star rows")
    fun g1GoalUnreadDeadlineAdmitsSingles() {
        assertTrue(admitsSingleStarRowsForGradedGoal(true, false, Int.MAX_VALUE, countdownReadable = true))
    }

    @Test
    @DisplayName("A scenario that never reads the countdown (Grand Concert) does not admit on an unread deadline")
    fun unreadableCountdownDoesNotAdmitSingles() {
        assertFalse(admitsSingleStarRowsForGradedGoal(true, false, Int.MAX_VALUE, countdownReadable = false))
        assertTrue(admitsSingleStarRowsForGradedGoal(true, true, 0, countdownReadable = false))
    }

    @Test
    @DisplayName("Without a G1-only goal the deadline never admits single-star rows")
    fun noG1GoalNeverAdmitsSingles() {
        assertFalse(admitsSingleStarRowsForGradedGoal(false, true, 0, countdownReadable = true))
        assertFalse(admitsSingleStarRowsForGradedGoal(false, false, Int.MAX_VALUE, countdownReadable = true))
    }

    @Test
    @DisplayName("Junior G1 replay: the T23 and T24 single-star G1 rows become enterable near the deadline")
    fun juniorG1ReplayAdmitsSingleStarRows() {
        // Race-list matches logged at turns 23 and 24 (1080x1920): row 1 a double-star Pre-OP, row 2 a single-star G1 the old rule dropped.
        val anchors = mergePredictionAnchors(listOf(Point(881.0, 1197.0)), listOf(Point(881.0, 1244.0), Point(881.0, 1427.0)))
        assertEquals(listOf(PredictionTier.DOUBLE, PredictionTier.SINGLE), anchors.map { it.tier })
        assertEquals(1, enterablePredictionAnchors(anchors, allowSingles = false).size)

        // Goal deadline at the end of turn 24.
        for ((turn, turnsRemaining) in listOf(23 to 1, 24 to 0)) {
            val allowSingles = admitsSingleStarRowsForGradedGoal(true, near(turnsRemaining), turnsRemaining, countdownReadable = true)
            val enterable = enterablePredictionAnchors(anchors, allowSingles)
            assertEquals(2, enterable.size, "turn $turn")
            assertEquals(1427.0, enterable[1].location.y, "turn $turn")
        }
    }

    @Test
    @DisplayName("Once G1-filtered, the single-star G1 is the pick; a double-star G1 still outranks it")
    fun g1FilteredSingleIsPicked() {
        assertEquals(0, indexOfBestByTierThenFans(listOf(RaceDetails(7000, false, predictionTier = PredictionTier.SINGLE))))
        val bothG1 =
            listOf(
                RaceDetails(7000, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(6500, true),
            )
        assertEquals(1, indexOfBestByTierThenFans(bothG1))
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // minimumGoalGrade / countsForGoalGrade / goalGradeLabel

    @Test
    @DisplayName("Each read goal tier maps to its minimum grade; an unread tier has none")
    fun goalTierMinimumGrades() {
        assertEquals(RaceGrade.PRE_OP, minimumGoalGrade(GoalCriteriaTier.PRE_OP_OR_ABOVE))
        assertEquals(RaceGrade.G3, minimumGoalGrade(GoalCriteriaTier.G3_OR_ABOVE))
        assertEquals(RaceGrade.G1, minimumGoalGrade(GoalCriteriaTier.G1_ONLY))
        assertEquals(null, minimumGoalGrade(null))
    }

    @Test
    @DisplayName("A G3-or-above goal counts G3, G2 and G1 races and never an OP or Pre-OP race")
    fun g3GoalNeverCountsOpOrPreOp() {
        val counted = RaceGrade.entries.filter { countsForGoalGrade(it, RaceGrade.G3) }
        assertEquals(listOf(RaceGrade.G3, RaceGrade.G2, RaceGrade.G1), counted)
    }

    @Test
    @DisplayName("A Pre-OP-or-above goal counts Pre-OP through G1 but never a maiden, debut, finale or exhibition race")
    fun preOpGoalCountsGradedRacesOnly() {
        val counted = RaceGrade.entries.filter { countsForGoalGrade(it, RaceGrade.PRE_OP) }
        assertEquals(listOf(RaceGrade.PRE_OP, RaceGrade.OP, RaceGrade.G3, RaceGrade.G2, RaceGrade.G1), counted)
    }

    @Test
    @DisplayName("A G1-only goal counts exactly the races the G1 check counted")
    fun g1GoalCountsOnlyG1() {
        for (grade in RaceGrade.entries) {
            assertEquals(grade == RaceGrade.G1, countsForGoalGrade(grade, RaceGrade.G1), "grade=$grade")
        }
    }

    @Test
    @DisplayName("The race DB query uses the races-table grade spellings, including \"Pre-OP\"")
    fun goalGradeDbLabelsMatchRacesTable() {
        // src/data/races.json, which seeds the races table, spells the grades "Pre-OP", "OP", "G3", "G2" and "G1".
        assertEquals(listOf("Pre-OP", "OP", "G3", "G2", "G1"), goalGradeDbLabels(RaceGrade.PRE_OP))
        assertEquals(listOf("G3", "G2", "G1"), goalGradeDbLabels(RaceGrade.G3))
        assertEquals(listOf("G1"), goalGradeDbLabels(RaceGrade.G1))
    }

    @Test
    @DisplayName("Goal log labels keep the G1 wording and name the other tiers")
    fun goalGradeLabels() {
        assertEquals("G1", goalGradeLabel(RaceGrade.G1))
        assertEquals("G3-or-above", goalGradeLabel(RaceGrade.G3))
        assertEquals("Pre-OP-or-above", goalGradeLabel(RaceGrade.PRE_OP))
    }

    @Test
    @DisplayName("For a G3 goal the OP row is filtered out and the single-star G3 is the pick near the deadline, not far from it")
    fun g3GoalPicksSingleStarG3NearDeadline() {
        // Two rows: an OP race with a double star and a G3 with a single star.
        val anchors = mergePredictionAnchors(listOf(Point(881.0, 1197.0)), listOf(Point(881.0, 1427.0)))
        val grades = listOf(RaceGrade.OP, RaceGrade.G3)
        val races = listOf(RaceDetails(1600, true), RaceDetails(3300, false, predictionTier = PredictionTier.SINGLE))
        val gradedGoal = minimumGoalGrade(GoalCriteriaTier.G3_OR_ABOVE) != null

        fun pick(turnsRemaining: Int): Int? {
            val allowSingles = admitsSingleStarRowsForGradedGoal(gradedGoal, near(turnsRemaining), turnsRemaining, countdownReadable = true)
            val enterable = enterablePredictionAnchors(anchors, allowSingles).map { anchors.indexOf(it) }
            val goalRows = enterable.filter { countsForGoalGrade(grades[it], RaceGrade.G3) }
            if (goalRows.isEmpty()) return null
            return goalRows[indexOfBestByTierThenFans(goalRows.map { races[it] })]
        }

        assertEquals(1, pick(turnsRemaining = 2))
        assertEquals(null, pick(turnsRemaining = FAN_EMERGENCY_TURN_WINDOW + 1))
    }
}

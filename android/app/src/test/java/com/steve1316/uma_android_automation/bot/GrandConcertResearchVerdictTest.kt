package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The research verdict as executable policy. Every test here pins one of the twelve verdict
 * propositions: which document is authoritative, what was imported, what was rejected, that both
 * income formulas stay inferred and lose to a visible preview, that Great Success is gauge-first and
 * feasibility-aware, that the 18-song route can become infeasible, that scheduling is never learning,
 * that a second schedule is unknown, that the stat reserve is dynamic, that the 21-song pivot yields
 * to every higher constraint, and that nothing here is actionable.
 */
@DisplayName("Grand Concert research verdict (report-only)")
class GrandConcertResearchVerdictTest {
    private val v = GrandConcertResearchVerdict

    @Nested
    @DisplayName("source hierarchy")
    inner class SourceHierarchy {
        @Test
        fun `Document 2 is the specification of record`() {
            assertEquals(ResearchSource.DOCUMENT_2_OPTIMAL_RUN, v.SPECIFICATION_OF_RECORD)
        }

        @Test
        fun `Document 1 contributes only the skill-point projection and the technique pivot`() {
            assertEquals(
                setOf(Document1Import.SKILL_POINT_PROJECTION, Document1Import.TECHNIQUE_PIVOT),
                v.DOCUMENT_1_IMPORTS,
            )
        }

        @Test
        fun `the four rejected claims are recorded, including story-date link gating`() {
            assertEquals(
                setOf(
                    RejectedClaim.STORY_DATE_LINK_GATING,
                    RejectedClaim.PP_FORMULA_CONFIRMED_LABEL,
                    RejectedClaim.ALWAYS_SCHEDULE_IMMEDIATELY,
                    RejectedClaim.FLAT_STAT_OFFSET,
                ),
                v.REJECTED_CLAIMS,
            )
        }

        @Test
        fun `the exponential model is confirmed live and the linear model is falsified`() {
            assertEquals(2, v.POINT_INCOME_MODELS.size)
            assertEquals(ExponentialSupportModel, v.CONFIRMED_INCOME_MODEL)
            assertEquals(LinearSupportModel, v.FALSIFIED_INCOME_MODEL)
            assertEquals(Provenance.GLOBAL_CONFIRMED, ExponentialSupportModel.provenance)
            // The falsified model is retained for the record but was never confirmed.
            assertNotEquals(Provenance.GLOBAL_CONFIRMED, LinearSupportModel.provenance)
            // Two caveats stay open and must not read as settled.
            assertFalse(v.INCOME_LINK_TERM_CONFIRMED)
            assertFalse(v.INCOME_COUNTING_RULE_RESOLVED)
        }
    }

    @Nested
    @DisplayName("performance-point income")
    inner class PointIncome {
        // The captured Global figures: level-1 non-Wit facility, and level-1 Wit facility.
        @Test
        fun `the two models agree at every captured support count`() {
            // Zero, one, and two supports: identical, and equal to the guide/live values 10, 11, 13.
            assertFalse(v.modelsDiverge(9, 1, 0, 0))
            assertEquals(10, ExponentialSupportModel.estimate(9, 1, 0, 0))
            assertEquals(10, LinearSupportModel.estimate(9, 1, 0, 0))

            assertFalse(v.modelsDiverge(9, 1, 1, 0))
            assertEquals(11, LinearSupportModel.estimate(9, 1, 1, 0))

            assertFalse(v.modelsDiverge(9, 1, 2, 0))
            assertEquals(13, ExponentialSupportModel.estimate(9, 1, 2, 0))
            assertEquals(13, LinearSupportModel.estimate(9, 1, 2, 0))
        }

        @Test
        fun `the live Guts fixture value of 13 reproduces on both models`() {
            // Guts Lv1, two visible participants, Dance +13 (fixture training_guts_before.png).
            assertEquals(13, ExponentialSupportModel.estimate(v.baseYield(StatName.GUTS), 1, 2, 0))
            assertEquals(13, LinearSupportModel.estimate(v.baseYield(StatName.GUTS), 1, 2, 0))
        }

        @Test
        fun `the Wit base of 5 reproduces the captured Wit value of 6`() {
            assertEquals(5, v.baseYield(StatName.WIT))
            assertEquals(6, ExponentialSupportModel.estimate(5, 1, 0, 0))
            assertEquals(6, LinearSupportModel.estimate(5, 1, 0, 0))
        }

        @Test
        fun `three supports is the live-confirmed discriminator, impossible under linear`() {
            assertTrue(v.modelsDiverge(9, 1, 3, 0))
            assertEquals(15, ExponentialSupportModel.estimate(9, 1, 3, 0)) // observed live (Guts)
            assertEquals(14, LinearSupportModel.estimate(9, 1, 3, 0))
            // The decider: the linear floor never yields the observed 15 at base-9 Lvl-1, any C.
            assertTrue((0..9).none { LinearSupportModel.estimate(9, 1, it, 0) == 15 })
            // Wit base-5 C=3: exponential 9 (observed live), linear 8.
            assertEquals(9, ExponentialSupportModel.estimate(5, 1, 3, 0))
            assertEquals(8, LinearSupportModel.estimate(5, 1, 3, 0))
        }

        @Test
        fun `a visible preview is authoritative over both formulas even when it contradicts them`() {
            // At two supports both formulas say 13; an absurd preview of 99 still wins.
            val r = v.resolvePointGain(preview = 99, s = 9, f = 1, c = 2, l = 0)
            assertEquals(99, r.value)
            assertTrue(r.authoritative)
            assertFalse(r.uncertain)
            assertEquals("preview", r.source)
        }

        @Test
        fun `a preview wins even where the two models disagree`() {
            val r = v.resolvePointGain(preview = 13, s = 9, f = 1, c = 3, l = 0)
            assertEquals(13, r.value)
            assertTrue(r.authoritative)
            assertFalse(r.modelsAgree)
            assertEquals(14, r.modelLow)
            assertEquals(15, r.modelHigh)
        }

        @Test
        fun `a null preview yields no actionable value, only a band and an uncertainty flag`() {
            val r = v.resolvePointGain(preview = null, s = 9, f = 1, c = 3, l = 0)
            assertNull(r.value)
            assertFalse(r.authoritative)
            assertTrue(r.uncertain)
            assertEquals(14, r.modelLow)
            assertEquals(15, r.modelHigh)
        }
    }

    @Nested
    @DisplayName("scenario-link and Light Hello have no story-date gate")
    inner class NoLinkGate {
        @Test
        fun `story-date link gating is rejected and structurally absent`() {
            assertFalse(v.STORY_DATE_LINK_GATING)
            assertTrue(v.REJECTED_CLAIMS.contains(RejectedClaim.STORY_DATE_LINK_GATING))
        }

        @Test
        fun `a link support raises income whenever present, with no turn parameter to gate it`() {
            // estimate() takes (s, f, c, l) and no turn/concert index, so a link support counts
            // from unlock. Both models value a scenario-link support above a plain one.
            assertTrue(ExponentialSupportModel.estimate(9, 1, 2, 1) > ExponentialSupportModel.estimate(9, 1, 2, 0))
            assertTrue(LinearSupportModel.estimate(9, 1, 2, 1) > LinearSupportModel.estimate(9, 1, 2, 0))
        }

        @Test
        fun `Light Hello's plus twenty counts only after a detected trigger`() {
            assertEquals(0, v.lightHelloLowestTypeBonus(triggerDetected = false))
            assertEquals(20, v.lightHelloLowestTypeBonus(triggerDetected = true))
        }
    }

    @Nested
    @DisplayName("Great Success is gauge-first and feasibility-aware")
    inner class GreatSuccess {
        @Test
        fun `needed increments come from the observed gauge, never from a total song count`() {
            assertEquals(0, v.neededSongIncrements(3))
            assertEquals(2, v.neededSongIncrements(1))
            assertNull(v.neededSongIncrements(null))
        }

        @Test
        fun `the Great Success song floor is three, Global-confirmed from master data`() {
            assertEquals(3, v.HYPE_GAUGE_SONG_INCREMENTS.value)
            assertEquals(Provenance.GLOBAL_CONFIRMED, v.HYPE_GAUGE_SONG_INCREMENTS.provenance)
        }

        @Test
        fun `a full gauge is secured regardless of reachability`() {
            assertEquals(
                GreatSuccessPosture.SECURED,
                v.greatSuccessPosture(observedHypeIncrements = 3, reachableSafely = null, wouldForceInferiorTurns = false, threatensRequiredObjective = false),
            )
        }

        @Test
        fun `a safely reachable gauge is a hard constraint`() {
            assertEquals(
                GreatSuccessPosture.HARD,
                v.greatSuccessPosture(1, reachableSafely = true, wouldForceInferiorTurns = false, threatensRequiredObjective = false),
            )
        }

        @Test
        fun `an inferior-turn cost or an unreachable gauge softens the goal`() {
            assertEquals(
                GreatSuccessPosture.SOFT,
                v.greatSuccessPosture(1, reachableSafely = true, wouldForceInferiorTurns = true, threatensRequiredObjective = false),
            )
            assertEquals(
                GreatSuccessPosture.SOFT,
                v.greatSuccessPosture(1, reachableSafely = false, wouldForceInferiorTurns = false, threatensRequiredObjective = false),
            )
        }

        @Test
        fun `an objective-threatening gauge is abandoned`() {
            assertEquals(
                GreatSuccessPosture.ABANDONED,
                v.greatSuccessPosture(1, reachableSafely = true, wouldForceInferiorTurns = false, threatensRequiredObjective = true),
            )
        }

        @Test
        fun `unreadable reachability is unknown, never a silent hard constraint`() {
            assertEquals(
                GreatSuccessPosture.UNKNOWN,
                v.greatSuccessPosture(1, reachableSafely = null, wouldForceInferiorTurns = false, threatensRequiredObjective = false),
            )
        }

        @Test
        fun `the marginal Great Success value is plus seven per stat, soft-capped`() {
            val lowStats = StatName.entries.associateWith { 800 }
            assertEquals(35, v.marginalGreatSuccessValue(lowStats))

            val oneHigh = lowStats.toMutableMap().apply { this[StatName.SPEED] = 1250 }
            // Speed's +7 lands entirely above 1200 (half) -> 3; the other four stay +7 -> 28; total 31.
            assertEquals(31, v.marginalGreatSuccessValue(oneHigh))
        }
    }

    @Nested
    @DisplayName("soft-cap transform")
    inner class SoftCap {
        @Test
        fun `gains below the soft cap are full`() {
            assertEquals(7, v.transformedGain(current = 800, rawGain = 7, cap = 1600))
        }

        @Test
        fun `gains above the soft cap are halved`() {
            assertEquals(5, v.transformedGain(current = 1250, rawGain = 10, cap = 1600))
        }

        @Test
        fun `gains straddling the soft cap count full below and half above`() {
            // 1198,1199 full (2.0); 1200,1201 half (1.0); floor(3.0) = 3.
            assertEquals(3, v.transformedGain(current = 1198, rawGain = 4, cap = 1600))
        }

        @Test
        fun `gains at or above the hard cap are lost`() {
            assertEquals(0, v.transformedGain(current = 1600, rawGain = 7, cap = 1600))
        }

        @Test
        fun `a non-positive raw gain is zero`() {
            assertEquals(0, v.transformedGain(1000, 0, 1600))
            assertEquals(0, v.transformedGain(1000, -5, 1600))
        }
    }

    @Nested
    @DisplayName("dynamic future stat reserve replaces any flat offset")
    inner class FutureReserve {
        @Test
        fun `a flat stat offset is a rejected claim`() {
            assertTrue(v.REJECTED_CLAIMS.contains(RejectedClaim.FLAT_STAT_OFFSET))
        }

        @Test
        fun `five secured Great Successes reserve about fifty far below the soft cap`() {
            assertEquals(50, v.futureStatReserve(current = 800, cap = 1600, remainingConcerts = 5, pGreatSuccess = 1.0, girlsLegendUExpected = false))
        }

        @Test
        fun `only the normal-success floor reserves fifteen`() {
            assertEquals(15, v.futureStatReserve(800, 1600, 5, pGreatSuccess = 0.0, girlsLegendUExpected = false))
        }

        @Test
        fun `an expected special song adds ten to the reserve`() {
            assertEquals(60, v.futureStatReserve(800, 1600, 5, 1.0, girlsLegendUExpected = true))
            assertEquals(25, v.futureStatReserve(800, 1600, 5, 0.0, girlsLegendUExpected = true))
        }

        @Test
        fun `the reserve is halved near the soft cap and zero at the cap`() {
            assertEquals(25, v.futureStatReserve(1250, 1600, 5, 1.0, false))
            assertEquals(0, v.futureStatReserve(1600, 1600, 5, 1.0, false))
        }
    }

    @Nested
    @DisplayName("18-song route feasibility")
    inner class RouteFeasibility {
        @Test
        fun `the route is feasible when the projection reaches the target`() {
            val f = v.eighteenSongFeasibility(currentSongs = 10, songsFromCurrentBalances = 2, songsFromProjectedSafeTraining = 5, automaticFutureSongs = 2)
            assertTrue(f.feasible)
            assertEquals(19, f.maxFutureSongs)
            assertEquals(18, f.target)
            assertNull(f.reason)
        }

        @Test
        fun `the route can become infeasible, with a reason`() {
            val f = v.eighteenSongFeasibility(currentSongs = 8, songsFromCurrentBalances = 1, songsFromProjectedSafeTraining = 2, automaticFutureSongs = 1)
            assertFalse(f.feasible)
            assertEquals(12, f.maxFutureSongs)
            assertNotNull(f.reason)
        }
    }

    @Nested
    @DisplayName("21-song technique pivot yields to every higher constraint")
    inner class TechniquePivot {
        // A fully-permissive baseline; each test flips exactly one constraint to prove it blocks.
        private fun pivot(
            songs: Int? = 21,
            hype: Boolean = true,
            route: Boolean = true,
            objective: Boolean = false,
            safe: Boolean = true,
            weak: Boolean = true,
        ) = v.techniquePivotAllowed(songs, hype, route, objective, safe, weak)

        @Test
        fun `the pivot needs the 21-song threshold`() {
            assertFalse(pivot(songs = 20))
            assertFalse(pivot(songs = null))
        }

        @Test
        fun `the pivot yields to the hype floor`() {
            assertFalse(pivot(hype = false))
        }

        @Test
        fun `the pivot yields to the 18-song route`() {
            assertFalse(pivot(route = false))
        }

        @Test
        fun `the pivot yields to a mandatory objective`() {
            assertFalse(pivot(objective = true))
        }

        @Test
        fun `the pivot yields to turn safety`() {
            assertFalse(pivot(safe = false))
        }

        @Test
        fun `the pivot fires only when remaining song offers are weak and all else is satisfied`() {
            assertFalse(pivot(weak = false))
            assertTrue(pivot())
        }
    }

    @Nested
    @DisplayName("scheduled is never learned, and a second schedule is unknown")
    inner class Scheduling {
        private val songCard =
            LessonListCard(
                slot = 1,
                title = "Full Speed Ahead! Umadol Power",
                kind = LessonCardKind.SONG,
                masteryText = "Speed +22",
                concertText = "Friendship Training Effectiveness +5%",
                cost = PerformancePointVector.of(32, 0, 0, 12, 0),
                learnable = false,
                scheduled = false,
            )

        @Test
        fun `scheduling is inert and applies nothing`() {
            assertEquals(LessonEffects.INERT, ScheduledLessonModel.scheduleEffects())
            val e = ScheduledLessonModel.scheduleEffects()
            assertEquals(0, e.learnedSongDelta)
            assertEquals(0, e.hypeAdded)
            assertFalse(e.masteryApplied)
            assertFalse(e.concertBonusQueued)
            assertEquals(PerformancePointVector.of(0, 0, 0, 0, 0), e.pointsSpent)
        }

        @Test
        fun `learning a song is the only transition that banks its effects`() {
            val e = ScheduledLessonModel.learnEffects(songCard)
            assertEquals(1, e.learnedSongDelta)
            assertEquals(1, e.hypeAdded)
            assertTrue(e.masteryApplied)
            assertTrue(e.concertBonusQueued)
        }

        @Test
        fun `a scheduled song never counts toward the concert while a learned one does`() {
            val h = HypeState(HypeTier.MILD, gaugeConfidence = true, previewedIncrease = false, appliedIncrease = false, learnedSongs = 1, scheduledSongs = 0)
            val scheduled = h.afterScheduling()
            assertEquals(1, scheduled.songsTowardConcert)
            assertEquals(1, scheduled.scheduledSongs)
            assertFalse(scheduled.appliedIncrease)
            assertTrue(scheduled.previewedIncrease)

            val learned = h.afterLearningSong()
            assertEquals(2, learned.songsTowardConcert)
            assertTrue(learned.appliedIncrease)
        }

        @Test
        fun `a second scheduled target is unknown, not assumed`() {
            assertEquals(MultipleScheduleBehavior.MULTIPLE_TARGET_UNKNOWN, v.SECOND_SCHEDULE_BEHAVIOR.value)
            assertEquals(Provenance.UNKNOWN, v.SECOND_SCHEDULE_BEHAVIOR.provenance)
        }
    }

    @Nested
    @DisplayName("Document 1 imports carry guide provenance")
    inner class Doc1Imports {
        @Test
        fun `the skill-point projection uses guide figures and is not verified balance arithmetic`() {
            assertEquals(25, v.SKILL_POINTS_PER_LEARNED_SONG.value)
            assertEquals(Provenance.GUIDE, v.SKILL_POINTS_PER_LEARNED_SONG.provenance)
            assertEquals(5, v.SKILL_POINTS_PER_LEARNED_TECHNIQUE.value)
            assertEquals(Provenance.GUIDE, v.SKILL_POINTS_PER_LEARNED_TECHNIQUE.provenance)
            assertFalse(v.SKILL_POINT_PROJECTION_VERIFIED)
            assertEquals(650, v.projectedSkillPointsFromLessons(songsLearned = 18, techniquesLearned = 40))
        }

        @Test
        fun `the technique-pivot threshold is a guide figure of 21`() {
            assertEquals(21, v.TECHNIQUE_PIVOT_SONG_THRESHOLD.value)
            assertEquals(Provenance.GUIDE, v.TECHNIQUE_PIVOT_SONG_THRESHOLD.provenance)
        }
    }

    @Nested
    @DisplayName("everything stays non-actionable")
    inner class NonActionable {
        @Test
        fun `the verdict module is report-only`() {
            assertFalse(v.actionable)
        }

        @Test
        fun `the lesson report and decision remain non-actionable`() {
            assertFalse(GrandConcertLessonReport(emptyList(), emptyList(), emptyList()).actionable)
            assertFalse(GrandConcertDecision(recommendedSlot = null, certain = false).actionable)
        }
    }
}

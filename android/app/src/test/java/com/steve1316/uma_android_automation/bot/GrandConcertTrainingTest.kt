package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The Grand Concert training preview model and the turn-transition verifier, pinned against the
 * 2026-07-23 Guts-training capture pair (see fixtures/grandconcert/PROVENANCE.md). The exact
 * numbers here are the ones the two screenshots show, re-derived from the pixels.
 */
@DisplayName("Grand Concert training transition")
class GrandConcertTrainingTest {
    // The selected training preview on training_guts_before.png.
    private val preview =
        GrandConcertTrainingPreview(
            facility = StatName.GUTS,
            level = 1,
            failureChance = 9,
            statGains = mapOf(StatName.SPEED to 3, StatName.POWER to 3, StatName.GUTS to 9),
            skillPointGain = 8,
            performanceGains = mapOf(PerformancePointType.DANCE to 13),
            visibleParticipants = 2,
        )

    private fun balances(da: Int?, pa: Int?, vo: Int?, vi: Int?, co: Int?) =
        PerformanceBalances(
            mapOf(
                PerformancePointType.DANCE to da,
                PerformancePointType.PASSION to pa,
                PerformancePointType.VOCAL to vo,
                PerformancePointType.VISUAL to vi,
                PerformancePointType.COMPOSURE to co,
            ),
        )

    // Scheduled lesson costs derived from the on-screen deficits: Da "22 more" over balance 10 =>
    // cost 32; Vi "2 more" over balance 10 => cost 12.
    private val scheduled = mapOf(PerformancePointType.DANCE to 32, PerformancePointType.VISUAL to 12)

    private val before =
        GrandConcertScenarioState(
            turnsUntilDebut = 7,
            turnsUntilConcert = 19,
            balances = balances(10, 10, 10, 10, 0),
            stats = mapOf(StatName.SPEED to 181, StatName.STAMINA to 108, StatName.POWER to 152, StatName.GUTS to 173, StatName.WIT to 147),
            scheduledCost = scheduled,
        )

    private val after =
        GrandConcertScenarioState(
            turnsUntilDebut = 6,
            turnsUntilConcert = 18,
            balances = balances(23, 10, 10, 10, 0),
            stats = mapOf(StatName.SPEED to 184, StatName.STAMINA to 118, StatName.POWER to 155, StatName.GUTS to 182, StatName.WIT to 147),
            scheduledCost = scheduled,
        )

    @Nested
    @DisplayName("preview")
    inner class Preview {
        @Test
        fun `facility and level`() {
            assertEquals(StatName.GUTS, preview.facility)
            assertEquals(1, preview.level)
        }

        @Test
        fun `failure chance`() {
            assertEquals(9, preview.failureChance)
        }

        @Test
        fun `ordinary stat gains, with unlisted stats previewing zero`() {
            assertEquals(3, preview.previewedStatGain(StatName.SPEED))
            assertEquals(3, preview.previewedStatGain(StatName.POWER))
            assertEquals(9, preview.previewedStatGain(StatName.GUTS))
            assertEquals(0, preview.previewedStatGain(StatName.STAMINA))
            assertEquals(0, preview.previewedStatGain(StatName.WIT))
        }

        @Test
        fun `skill point gain`() {
            assertEquals(8, preview.skillPointGain)
        }

        @Test
        fun `performance type and amount`() {
            assertEquals(PerformancePointType.DANCE, preview.observedPerformanceType)
            assertEquals(13, preview.performanceGains[PerformancePointType.DANCE])
        }

        @Test
        fun `two visible participants`() {
            assertEquals(2, preview.visibleParticipants)
        }

        @Test
        fun `the observed per-turn type overrides the static facility prior`() {
            // Guts previewed Dance this turn; the static primary for Guts is Visual. A bot that
            // trusted the static map would attribute the 13 points to the wrong type.
            assertEquals(PerformancePointType.VISUAL, GrandConcertFacilityModel.staticPrimaryType(StatName.GUTS))
            assertNotEquals(GrandConcertFacilityModel.staticPrimaryType(StatName.GUTS), preview.observedPerformanceType)
            assertTrue(preview.performanceTypeOverridesStatic)
            assertEquals(Provenance.COMMUNITY_MODEL, GrandConcertFacilityModel.staticProvenance)
        }
    }

    @Nested
    @DisplayName("scheduled-deficit arithmetic")
    inner class Deficits {
        @Test
        fun `Da deficit is 22 before and 9 after`() {
            assertEquals(22, before.scheduledRemaining(PerformancePointType.DANCE))
            assertEquals(9, after.scheduledRemaining(PerformancePointType.DANCE))
        }

        @Test
        fun `Vi deficit stays 2`() {
            assertEquals(2, before.scheduledRemaining(PerformancePointType.VISUAL))
            assertEquals(2, after.scheduledRemaining(PerformancePointType.VISUAL))
        }

        @Test
        fun `remaining clamps at zero and never goes negative`() {
            val overfunded = before.copy(balances = balances(40, 10, 10, 10, 0))
            assertEquals(0, overfunded.scheduledRemaining(PerformancePointType.DANCE))
        }

        @Test
        fun `an unread balance yields an unknown deficit, not a guessed one`() {
            val partial = before.copy(balances = balances(null, 10, 10, 10, 0))
            assertNull(partial.scheduledRemaining(PerformancePointType.DANCE))
        }

        @Test
        fun `a type with no scheduled lesson has zero remaining`() {
            assertEquals(0, before.scheduledRemaining(PerformancePointType.PASSION))
        }
    }

    @Nested
    @DisplayName("transition verification")
    inner class Verify {
        @Test
        fun `the Da balance moved 10 to 23, matching the previewed 13`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            assertTrue(r.performanceOk)
        }

        @Test
        fun `both countdowns dropped by exactly one`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            assertTrue(r.debutCountdownOk)
            assertTrue(r.concertCountdownOk)
        }

        @Test
        fun `deficits reconcile`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            assertTrue(r.deficitsOk)
        }

        @Test
        fun `the mandatory checks pass and the turn is ok`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            assertTrue(r.ok)
        }

        @Test
        fun `the intervening event permits the unexplained Stamina delta`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            assertTrue(r.ok, "the +10 Stamina must not fail the verdict when an event was possible")
            assertFalse(r.statsFullyExplained)
        }

        @Test
        fun `the unexplained Stamina delta is recorded, not dropped`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            val sta = r.unexplainedStatDeltas.single { it.stat == StatName.STAMINA }
            assertEquals(10, sta.observed)
            assertEquals(0, sta.previewed)
            assertEquals(10, sta.unexplained)
            assertTrue(r.notes.any { it.contains("intervening event") && it.contains("STAMINA") })
        }

        @Test
        fun `performance arithmetic stays mandatory even with an intervening event allowed`() {
            // Corrupt only the Da balance: the intervening-event allowance must not excuse a
            // performance-point mismatch.
            val wrongPerf = after.copy(balances = balances(20, 10, 10, 10, 0))
            val r = GrandConcertTransition.verify(before, preview, wrongPerf, interveningEventPossible = true)
            assertFalse(r.performanceOk)
            assertFalse(r.ok)
        }

        @Test
        fun `deficit arithmetic stays mandatory even with an intervening event allowed`() {
            val unreadDa = after.copy(balances = balances(null, 10, 10, 10, 0))
            val r = GrandConcertTransition.verify(before, preview, unreadDa, interveningEventPossible = true)
            assertFalse(r.deficitsOk)
        }

        @Test
        fun `with no intervening event allowed, the same Stamina surplus is flagged as a hard mismatch`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = false)
            // The mandatory checks still pass; the difference is only in how the stat surplus is
            // described and that stats are not fully explained.
            assertTrue(r.ok)
            assertFalse(r.statsFullyExplained)
            assertTrue(r.notes.any { it.contains("no intervening event was allowed") })
        }

        @Test
        fun `the ordinary stats that did match are reported as matching`() {
            val r = GrandConcertTransition.verify(before, preview, after, interveningEventPossible = true)
            for (stat in listOf(StatName.SPEED, StatName.POWER, StatName.GUTS, StatName.WIT)) {
                assertTrue(r.statDeltas.single { it.stat == stat }.matchesPreview, "$stat should match the preview")
            }
        }
    }

    @Nested
    @DisplayName("policy evidence (report-only)")
    inner class PolicyEvidence {
        @Test
        fun `training evidence never learns a permanent facility-to-type mapping`() {
            val lines = GrandConcertPolicy.describeTrainingEvidence(preview, before)
            // It reports the observed Dance gain and explicitly notes the static prior is Visual,
            // rather than asserting Guts means Dance.
            assertTrue(lines.any { it.contains("Dance +13") })
            assertTrue(lines.any { it.contains("static prior is Visual") })
            assertFalse(lines.any { it.contains("Guts always") || it.contains("Guts gives Dance") })
        }

        @Test
        fun `it surfaces the allowed evidence and the scheduled-deficit contribution`() {
            val lines = GrandConcertPolicy.describeTrainingEvidence(preview, before)
            assertTrue(lines.any { it.contains("failure risk 9%") })
            assertTrue(lines.any { it.contains("2 visible support participant") && it.contains("no bond or effect inferred") })
            assertTrue(lines.any { it.contains("toward the scheduled Dance deficit (22 -> 9)") })
            assertTrue(lines.any { it.contains("19 turn(s) until the next concert") })
        }

        @Test
        fun `it never claims support effects from the portrait count alone`() {
            val lines = GrandConcertPolicy.describeTrainingEvidence(preview, before)
            assertTrue(lines.any { it.contains("portrait count only") })
        }

        @Test
        fun `the decision engine remains non-actionable`() {
            val offers =
                LessonOfferSet(
                    listOf(
                        LessonCard(1, LessonCardKind.SONG, "Run n' Run!", LessonCost(mapOf(PerformancePointType.DANCE to 5)), songEffect = SongEffect("Skill Pts +22")),
                        LessonCard(2, LessonCardKind.TECHNIQUE, "Rhythm Basics", LessonCost(mapOf(PerformancePointType.DANCE to 5)), techniqueEffect = TechniqueEffect("Speed +5")),
                        LessonCard(3, LessonCardKind.TECHNIQUE, "Yoga Basics", LessonCost(mapOf(PerformancePointType.DANCE to 5)), techniqueEffect = TechniqueEffect("Guts +5")),
                    ),
                )
            val d =
                GrandConcertPolicy.recommend(
                    GrandConcertRunState(balances = balances(100, 100, 100, 100, 100), offers = offers, songsLearned = 2, segment = ConcertSegment.BEFORE_PROMO_2, lessonUnlocked = true),
                )
            assertFalse(d.actionable)
        }
    }
}

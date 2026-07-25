package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The Grand Concert Lesson and Concert-Info models, pinned against the 2026-07-23 launch-night
 * captures (fixtures/grandconcert/PROVENANCE.md). The exact titles, costs, and effects here are
 * the ones the live screens show, re-read from the pixels.
 */
@DisplayName("Grand Concert lessons and concert info")
class GrandConcertLessonsTest {
    private fun v(da: Int?, pa: Int?, vo: Int?, vi: Int?, co: Int?) = PerformancePointVector.of(da, pa, vo, vi, co)

    // The live Technique list (technique_list.png): balances all 10, three technique cards.
    private val techBalances = v(10, 10, 10, 10, 10)
    private val audienceInvolvement =
        LessonListCard(0, "Audience Involvement Basics", LessonCardKind.TECHNIQUE, "Stamina +5", "None", v(0, 10, 0, 0, 0), learnable = true, scheduled = false)
    private val groupLesson =
        LessonListCard(1, "Group Lesson Basics", LessonCardKind.TECHNIQUE, "Skill Hint Lvl +1 (Front Runner)", "None", v(15, 0, 0, 0, 0), learnable = false, scheduled = false)
    private val composureTraining =
        LessonListCard(2, "Composure Training Basics", LessonCardKind.TECHNIQUE, "Wit +5", "None", v(0, 0, 0, 0, 10), learnable = true, scheduled = false)

    // The live Song list (song_list.png): balances Da/Pa/Vo/Vi 10, Co 0; three song cards.
    private val songBalances = v(10, 10, 10, 10, 0)
    private val runNRun =
        LessonListCard(0, "Run n' Run!", LessonCardKind.SONG, "Skill Pts +22", "Friendship Training Effectiveness +5%", v(14, 0, 0, 16, 14), learnable = false, scheduled = false)
    private val believeInMiracles =
        LessonListCard(1, "Believe in Miracles!", LessonCardKind.SONG, "Training Wit Gain +1", "Specialty Priority +5", v(0, 21, 0, 0, 21), learnable = false, scheduled = false)
    private val fullSpeedAhead =
        LessonListCard(2, "Full Speed Ahead! Umadol Power☆", LessonCardKind.SONG, "Speed +22", "Friendship Training Effectiveness +5%", v(32, 0, 0, 12, 0), learnable = false, scheduled = false)

    @Nested
    @DisplayName("lesson list")
    inner class ListModel {
        @Test
        fun `the technique list carries all five balances and three technique cards`() {
            val list = LessonList(techBalances, listOf(audienceInvolvement, groupLesson, composureTraining), hasFullStats = true, hasConcertInfo = true)
            assertTrue(list.balances.fullyKnown)
            assertEquals(3, list.cards.size)
            assertTrue(list.cards.all { it.kind == LessonCardKind.TECHNIQUE })
            assertTrue(list.complete)
        }

        @Test
        fun `technique titles read exactly`() {
            assertEquals(
                listOf("Audience Involvement Basics", "Group Lesson Basics", "Composure Training Basics"),
                listOf(audienceInvolvement, groupLesson, composureTraining).map { it.title },
            )
        }

        @Test
        fun `technique costs read exactly, including the unaffordable middle card`() {
            assertEquals(10, audienceInvolvement.cost[PerformancePointType.PASSION])
            assertEquals(15, groupLesson.cost[PerformancePointType.DANCE])
            assertEquals(10, composureTraining.cost[PerformancePointType.COMPOSURE])
        }

        @Test
        fun `learnable state matches affordability against the balances`() {
            assertEquals(true, audienceInvolvement.cost.affordableWith(techBalances))
            assertEquals(false, groupLesson.cost.affordableWith(techBalances)) // Da 15 > 10
            assertEquals(true, composureTraining.cost.affordableWith(techBalances))
        }

        @Test
        fun `the three song titles read exactly, matching the client's own spellings`() {
            assertEquals(
                listOf("Run n' Run!", "Believe in Miracles!", "Full Speed Ahead! Umadol Power☆"),
                listOf(runNRun, believeInMiracles, fullSpeedAhead).map { it.title },
            )
        }

        @Test
        fun `all three song cost vectors read exactly`() {
            assertEquals(v(14, 0, 0, 16, 14).values, runNRun.cost.values)
            assertEquals(v(0, 21, 0, 0, 21).values, believeInMiracles.cost.values)
            assertEquals(v(32, 0, 0, 12, 0).values, fullSpeedAhead.cost.values)
        }

        @Test
        fun `a song carries a concert bonus, a technique does not`() {
            assertTrue(fullSpeedAhead.hasConcertBonus)
            assertFalse(audienceInvolvement.hasConcertBonus)
        }

        @Test
        fun `the scheduled song is marked scheduled`() {
            val scheduled = fullSpeedAhead.copy(scheduled = true)
            assertTrue(scheduled.scheduled == true)
        }
    }

    @Nested
    @DisplayName("confirmation dialogs")
    inner class Confirmations {
        // learn_confirm_technique.png: affordable Stamina +5, Points Left Over Da10/Pa0/Vo10/Vi10/Co10.
        private val learnConfirm =
            LessonConfirmation(isSchedule = false, title = "Audience Involvement Basics", kind = LessonCardKind.TECHNIQUE, masteryText = "Stamina +5", concertText = "None", pointsLeftOver = v(10, 0, 10, 10, 10), hasCancel = true, hasAffirmative = true)

        // schedule_confirm_technique.png: Group Lesson Basics, Points Left Over Da -5 (negative).
        private val scheduleConfirmTech =
            LessonConfirmation(isSchedule = true, title = "Group Lesson Basics", kind = LessonCardKind.TECHNIQUE, masteryText = "Skill Hint Lvl +1 (Front Runner)", concertText = "None", pointsLeftOver = v(-5, 10, 10, 10, 10), hasCancel = true, hasAffirmative = true)

        // schedule_confirm_song.png: Full Speed Ahead, Points Left Over Da -22, Vi -2.
        private val scheduleConfirmSong =
            LessonConfirmation(isSchedule = true, title = "Full Speed Ahead! Umadol Power☆", kind = LessonCardKind.SONG, masteryText = "Speed +22", concertText = "Friendship Training Effectiveness +5%", pointsLeftOver = v(-22, 10, 10, -2, 0), hasCancel = true, hasAffirmative = true)

        @Test
        fun `the learn confirmation is affordable and matches its card exactly`() {
            assertFalse(learnConfirm.isSchedule)
            assertTrue(learnConfirm.affordabilityConsistent)
            assertEquals(LearnVerdict.EXACT_MATCH, learnConfirm.verifyAgainst(audienceInvolvement))
        }

        @Test
        fun `a punctuation-to-letter OCR slip on the dialog is still the same card - the live Getaway cancel`() {
            // Observed 2026-07-24: the dialog read "Getawayl Fallin' Love" for the card listed as
            // "Getaway! Fallin' Love" (the "!" OCR'd as "l", which folding cannot cancel) and the
            // gate wrongly cancelled a legitimate purchase. One edit of tolerance covers it.
            assertTrue(lessonTitlesCompatible("Getawayl Fallin' Love", "Getaway! Fallin' Love"))
            val intended = audienceInvolvement.copy(title = "Getaway! Fallin' Love", kind = LessonCardKind.SONG)
            val dialog = learnConfirm.copy(title = "Getawayl Fallin' Love", kind = LessonCardKind.SONG)
            assertEquals(LearnVerdict.EXACT_MATCH, dialog.verifyAgainst(intended))
        }

        @Test
        fun `edit tolerance does not blur genuinely different titles`() {
            assertFalse(lessonTitlesCompatible("Vocal Training Basics", "Formation Basics"))
            assertFalse(lessonTitlesCompatible("Run n' Run!", "Run For Our Dream"))
        }

        @Test
        fun `a confirmation for a different card is a contradiction`() {
            assertEquals(LearnVerdict.CONTRADICTION, learnConfirm.verifyAgainst(composureTraining))
        }

        @Test
        fun `an unreadable title makes the verdict ambiguous, never a match`() {
            val blurred = learnConfirm.copy(title = null)
            assertEquals(LearnVerdict.AMBIGUOUS, blurred.verifyAgainst(audienceInvolvement))
        }

        @Test
        fun `the technique schedule confirmation is a schedule with a negative Da`() {
            assertTrue(scheduleConfirmTech.isSchedule)
            assertTrue(scheduleConfirmTech.pointsLeftOver.hasNegative)
            assertEquals(-5, scheduleConfirmTech.pointsLeftOver[PerformancePointType.DANCE])
            assertTrue(scheduleConfirmTech.affordabilityConsistent)
        }

        @Test
        fun `the song schedule confirmation distinguishes song from technique and parses negatives`() {
            assertTrue(scheduleConfirmSong.isSchedule)
            assertEquals(LessonCardKind.SONG, scheduleConfirmSong.kind)
            assertEquals(-22, scheduleConfirmSong.pointsLeftOver[PerformancePointType.DANCE])
            assertEquals(-2, scheduleConfirmSong.pointsLeftOver[PerformancePointType.VISUAL])
        }

        @Test
        fun `a learn dialog whose points went negative is internally inconsistent`() {
            val bad = learnConfirm.copy(pointsLeftOver = v(-1, 0, 10, 10, 10))
            assertFalse(bad.affordabilityConsistent)
        }
    }

    @Nested
    @DisplayName("scheduling completion")
    inner class Completion {
        @Test
        fun `the scheduling-complete dialog carries the card identity`() {
            val done = SchedulingComplete("Full Speed Ahead! Umadol Power☆", LessonCardKind.SONG)
            assertTrue(done.readable)
            assertEquals("Full Speed Ahead! Umadol Power☆", done.title)
        }
    }

    @Nested
    @DisplayName("scheduled-state invariants")
    inner class ScheduledState {
        @Test
        fun `scheduling spends nothing and applies nothing`() {
            val e = ScheduledLessonModel.scheduleEffects()
            assertEquals(v(0, 0, 0, 0, 0).values, e.pointsSpent.values)
            assertFalse(e.masteryApplied)
            assertFalse(e.concertBonusQueued)
            assertEquals(0, e.hypeAdded)
            assertEquals(0, e.learnedSongDelta)
        }

        @Test
        fun `learning a song applies the mastery, queues the concert bonus, and counts the song`() {
            val e = ScheduledLessonModel.learnEffects(fullSpeedAhead)
            assertEquals(v(32, 0, 0, 12, 0).values, e.pointsSpent.values)
            assertTrue(e.masteryApplied)
            assertTrue(e.concertBonusQueued)
            assertEquals(1, e.hypeAdded)
            assertEquals(1, e.learnedSongDelta)
        }

        @Test
        fun `learning a technique adds no hype and no learned song`() {
            val e = ScheduledLessonModel.learnEffects(audienceInvolvement)
            assertTrue(e.masteryApplied)
            assertFalse(e.concertBonusQueued)
            assertEquals(0, e.hypeAdded)
            assertEquals(0, e.learnedSongDelta)
        }

        @Test
        fun `the scheduled deficit derives from cost minus balance and clamps at zero`() {
            // Full Speed Ahead scheduled against balances Da10/Vi10: Da deficit 22, Vi deficit 2.
            assertEquals(22, ScheduledLessonModel.scheduledRemaining(fullSpeedAhead.cost, songBalances, PerformancePointType.DANCE))
            assertEquals(2, ScheduledLessonModel.scheduledRemaining(fullSpeedAhead.cost, songBalances, PerformancePointType.VISUAL))
            assertEquals(0, ScheduledLessonModel.scheduledRemaining(fullSpeedAhead.cost, songBalances, PerformancePointType.PASSION))
        }

        @Test
        fun `an unread balance leaves the deficit unknown`() {
            assertNull(ScheduledLessonModel.scheduledRemaining(fullSpeedAhead.cost, v(null, 10, 10, 10, 0), PerformancePointType.DANCE))
        }
    }

    @Nested
    @DisplayName("Concert Info")
    inner class Concert {
        // concert_info.png
        private val info =
            ConcertInfo(
                concertIndex = 1,
                hypeTier = HypeTier.MILD,
                songsLearned = 1,
                bonuses =
                    listOf(
                        ConcertBonusPanel("Friendship Training Effectiveness", "+0%", "+0%"),
                        ConcertBonusPanel("Specialty Priority", "+0", "+5"),
                        ConcertBonusPanel("Support Chain Event Frequency", "Lvl 0", "Lvl 0"),
                    ),
                setList = listOf("Make debut!"),
            )

        @Test
        fun `concert index is 1`() = assertEquals(1, info.concertIndex)

        @Test
        fun `hype tier is Mild Hype`() {
            assertEquals(HypeTier.MILD, info.hypeTier)
            assertEquals(HypeTier.MILD, HypeTier.fromText("Mild Hype"))
        }

        @Test
        fun `songs learned is 1`() = assertEquals(1, info.songsLearned)

        @Test
        fun `specialty priority goes 0 to 5`() {
            val sp = info.bonuses.single { it.name == "Specialty Priority" }
            assertEquals("+0", sp.beforeText)
            assertEquals("+5", sp.afterText)
        }

        @Test
        fun `friendship effectiveness is 0 percent and support chain is level 0`() {
            assertEquals("+0%", info.bonuses.single { it.name.contains("Friendship") }.afterText)
            assertEquals("Lvl 0", info.bonuses.single { it.name.contains("Support Chain") }.afterText)
        }

        @Test
        fun `the set list includes Make debut`() {
            assertTrue(info.setList.any { it.contains("Make debut", ignoreCase = true) })
        }
    }

    @Nested
    @DisplayName("Hype model")
    inner class Hype {
        private val base = HypeState(HypeTier.MILD, gaugeConfidence = true, previewedIncrease = false, appliedIncrease = false, learnedSongs = 1, scheduledSongs = 0)

        @Test
        fun `scheduling sets a preview but never applies hype or counts the song`() {
            val after = base.afterScheduling()
            assertTrue(after.previewedIncrease)
            assertFalse(after.appliedIncrease)
            assertEquals(1, after.learnedSongs)
            assertEquals(1, after.scheduledSongs)
            assertEquals(1, after.songsTowardConcert, "scheduled songs must not count toward the concert")
        }

        @Test
        fun `learning a song is the only thing that applies hype and counts it`() {
            val after = base.afterScheduling().afterLearningSong()
            assertTrue(after.appliedIncrease)
            assertFalse(after.previewedIncrease)
            assertEquals(2, after.learnedSongs)
            assertEquals(2, after.songsTowardConcert)
        }

        @Test
        fun `an unread hype gauge is UNKNOWN, not a tier`() {
            assertEquals(HypeTier.UNKNOWN, HypeTier.fromText(null))
            assertEquals(HypeTier.UNKNOWN, HypeTier.fromText("???"))
        }
    }

    @Nested
    @DisplayName("policy (report-only)")
    inner class Policy {
        @Test
        fun `an offer report is never actionable`() {
            val list = LessonList(techBalances, listOf(audienceInvolvement, groupLesson, composureTraining), hasFullStats = true, hasConcertInfo = true)
            val r = GrandConcertPolicy.describeLessonOffer(list, songsLearned = 1, hypeTier = HypeTier.MILD, turnsUntilConcert = 5)
            assertFalse(r.actionable)
        }

        @Test
        fun `scheduled songs are not counted toward the concert target`() {
            val list = LessonList(songBalances, listOf(runNRun, believeInMiracles, fullSpeedAhead.copy(scheduled = true)), hasFullStats = true, hasConcertInfo = true)
            val r = GrandConcertPolicy.describeLessonOffer(list, songsLearned = 1, hypeTier = HypeTier.MILD, turnsUntilConcert = 3)
            assertTrue(r.notes.any { it.contains("scheduled songs are not counted") })
        }

        @Test
        fun `an unreadable cost makes the report not fully readable`() {
            val blurredCost = audienceInvolvement.copy(cost = v(null, 10, 0, 0, 0))
            val list = LessonList(techBalances, listOf(blurredCost, groupLesson, composureTraining), hasFullStats = true, hasConcertInfo = true)
            val r = GrandConcertPolicy.describeLessonOffer(list, songsLearned = 1, hypeTier = HypeTier.MILD, turnsUntilConcert = 5)
            assertFalse(r.fullyReadable)
            assertTrue(r.missingEvidence.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("generic-screen guards and Global terms")
    inner class Guards {
        @Test
        fun `every lesson and concert screen requires the manual handoff`() {
            for (s in listOf(LessonScreen.LESSON_LIST, LessonScreen.LEARN_CONFIRMATION, LessonScreen.SCHEDULE_CONFIRMATION, LessonScreen.SCHEDULING_COMPLETE, LessonScreen.CONCERT_INFO)) {
                assertTrue(LessonScreenGuard.requiresHandoff(s), "$s must be guarded")
            }
        }

        @Test
        fun `the career screen and unknown are not force-guarded here`() {
            assertFalse(LessonScreenGuard.requiresHandoff(LessonScreen.CAREER))
            assertFalse(LessonScreenGuard.requiresHandoff(LessonScreen.UNKNOWN))
        }

        @Test
        fun `concert info routes to the concert handoff, the rest to the lesson-shop handoff`() {
            assertEquals(GrandConcertHandoffReason.CONCERT_NOT_AUTOMATED, LessonScreenGuard.handoffReason(LessonScreen.CONCERT_INFO))
            assertEquals(GrandConcertHandoffReason.LESSON_SHOP_NOT_AUTOMATED, LessonScreenGuard.handoffReason(LessonScreen.LESSON_LIST))
            assertEquals(GrandConcertHandoffReason.LESSON_SHOP_NOT_AUTOMATED, LessonScreenGuard.handoffReason(LessonScreen.SCHEDULE_CONFIRMATION))
        }

        @Test
        fun `the fifth performance type is Composure on Global`() {
            assertEquals("Composure", PerformancePointType.COMPOSURE.displayName)
        }

        @Test
        fun `the live song spellings differ from pre-launch guesses and are preserved exactly`() {
            // Pre-launch catalog had "RUNxRUN!", "My Favorite Treasure Box", "GIRLS' LEGEND U";
            // the live client shows the forms below. Any OCR built on the old spellings would miss.
            assertEquals("Run n' Run!", runNRun.title)
            assertEquals("Believe in Miracles!", believeInMiracles.title)
            assertTrue(fullSpeedAhead.title!!.startsWith("Full Speed Ahead! Umadol Power"))
        }
    }
}

package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The read-only Grand Concert decision engine. Its job is to refuse confidently: every test
 * here is about what the engine must NOT claim when the shop cannot be fully read.
 */
@DisplayName("Grand Concert decision engine (report-only)")
class GrandConcertPolicyTest {
    private fun cost(vararg pairs: Pair<PerformancePointType, Int?>) = LessonCost(pairs.toMap())

    private fun balances(vararg pairs: Pair<PerformancePointType, Int?>) = PerformanceBalances(pairs.toMap())

    private val fullBalances =
        balances(
            PerformancePointType.DANCE to 100,
            PerformancePointType.PASSION to 100,
            PerformancePointType.VOCAL to 100,
            PerformancePointType.VISUAL to 100,
            PerformancePointType.COMPOSURE to 100,
        )

    private fun song(slot: Int, name: String, mastery: String, price: Int = 21) =
        LessonCard(
            slot = slot,
            kind = LessonCardKind.SONG,
            name = name,
            cost = cost(PerformancePointType.DANCE to price, PerformancePointType.VISUAL to price),
            songEffect = SongEffect(mastery, Provenance.GLOBAL_CONFIRMED),
        )

    private fun technique(slot: Int, name: String, effect: String, price: Int = 10) =
        LessonCard(
            slot = slot,
            kind = LessonCardKind.TECHNIQUE,
            name = name,
            cost = cost(PerformancePointType.DANCE to price),
            techniqueEffect = TechniqueEffect(effect, Provenance.GLOBAL_CONFIRMED),
        )

    @Nested
    @DisplayName("hard constraints")
    inner class HardConstraints {
        @Test
        fun `an unreadable card is never recommended and never called certain`() {
            val offers =
                LessonOfferSet(
                    listOf(
                        song(1, "Believe in Miracles!", "Training Wit Gain +1"),
                        LessonCard(slot = 2), // unreadable
                        technique(3, "Dance Step Basics", "Speed +5"),
                    ),
                )
            val d = GrandConcertPolicy.recommend(GrandConcertRunState(balances = fullBalances, offers = offers))
            assertNull(d.recommendedSlot)
            assertFalse(d.certain)
            assertTrue(d.evidence.missing.any { it.contains("slot 2") }, d.evidence.missing.toString())
        }

        @Test
        fun `an unknown balance blocks any recommendation`() {
            val offers = LessonOfferSet(listOf(song(1, "Go This Way", "Training Power Gain +1"), technique(2, "Rhythm Basics", "Speed +5"), technique(3, "Yoga Basics", "Guts +5")))
            val partial = balances(PerformancePointType.DANCE to 100, PerformancePointType.VISUAL to null)
            val d = GrandConcertPolicy.recommend(GrandConcertRunState(balances = partial, offers = offers))
            assertNull(d.recommendedSlot)
            assertTrue(d.evidence.missing.any { it.contains("performance balance") })
        }

        @Test
        fun `affordability is never claimed against an unknown cost`() {
            val unknownCost = LessonCard(slot = 1, kind = LessonCardKind.SONG, name = "Dream Sky", cost = cost(PerformancePointType.DANCE to null))
            assertNull(unknownCost.cost.affordableWith(fullBalances))
            assertFalse(unknownCost.readable)
        }

        @Test
        fun `affordability is never claimed against an unknown balance`() {
            val c = cost(PerformancePointType.DANCE to 10)
            assertNull(c.affordableWith(balances(PerformancePointType.DANCE to null)))
            assertEquals(true, c.affordableWith(balances(PerformancePointType.DANCE to 10)))
            assertEquals(false, c.affordableWith(balances(PerformancePointType.DANCE to 9)))
        }

        @Test
        fun `nothing affordable is a confident refusal, not a guess`() {
            val pricey = LessonOfferSet(listOf(song(1, "A", "Training Wit Gain +1", price = 999), song(2, "B", "Training Wit Gain +1", price = 999), song(3, "C", "Training Wit Gain +1", price = 999)))
            val d = GrandConcertPolicy.recommend(GrandConcertRunState(balances = fullBalances, offers = pricey))
            assertNull(d.recommendedSlot)
            assertTrue(d.certain, "refusing when nothing is affordable is a proven fact, not uncertainty")
            assertTrue(d.constraintsSatisfied.any { it.contains("unaffordable") })
        }

        @Test
        fun `a decision is never actionable - this engine only reports`() {
            val offers = LessonOfferSet(listOf(song(1, "Run n' Run!", "Skill Pts +22"), technique(2, "Rhythm Basics", "Speed +5"), technique(3, "Yoga Basics", "Guts +5")))
            val d = GrandConcertPolicy.recommend(GrandConcertRunState(balances = fullBalances, offers = offers))
            assertFalse(d.actionable)
        }

        @Test
        fun `a locked Lesson system yields no recommendation at all`() {
            val d = GrandConcertPolicy.recommend(GrandConcertRunState(lessonUnlocked = false))
            assertNull(d.recommendedSlot)
            assertFalse(d.certain)
        }
    }

    @Nested
    @DisplayName("preferences")
    inner class Preferences {
        private fun state(offers: LessonOfferSet, songs: Int? = 5, segment: ConcertSegment = ConcertSegment.BEFORE_PROMO_2) =
            GrandConcertRunState(balances = fullBalances, offers = offers, songsLearned = songs, segment = segment, lessonUnlocked = true)

        @Test
        fun `a permanent training bonus outranks a one-shot stat gain`() {
            val offers =
                LessonOfferSet(
                    listOf(
                        song(1, "Here Comes Our Time", "Power +22"),
                        song(2, "Grow Up and Shine!", "Training Skill Pt Gain +3"),
                        technique(3, "Dance Step Basics", "Speed +5"),
                    ),
                )
            val d = GrandConcertPolicy.recommend(state(offers))
            assertEquals(2, d.recommendedSlot)
            assertTrue(d.certain)
        }

        @Test
        fun `an energy technique outranks a plain stat technique so it can compete with Rest`() {
            val offers =
                LessonOfferSet(
                    listOf(
                        technique(1, "Dance Step Basics", "Speed +5"),
                        technique(2, "Relaxing Body Massage", "Energy +30"),
                        technique(3, "Makeup Basics", "Guts +5"),
                    ),
                )
            assertEquals(2, GrandConcertPolicy.recommend(state(offers)).recommendedSlot)
        }

        @Test
        fun `a tie is reported as uncertain and recommends nothing`() {
            val offers =
                LessonOfferSet(
                    listOf(
                        technique(1, "Dance Step Basics", "Speed +5"),
                        technique(2, "Makeup Basics", "Guts +5"),
                        technique(3, "Vocal Training Basics", "Power +5"),
                    ),
                )
            val d = GrandConcertPolicy.recommend(state(offers))
            assertNull(d.recommendedSlot)
            assertFalse(d.certain)
            assertTrue(d.reasons.any { it.contains("equally") })
        }
    }

    @Nested
    @DisplayName("song targets")
    inner class SongTargets {
        @Test
        fun `being below the Great Success floor is surfaced as a risk`() {
            val offers = LessonOfferSet(listOf(song(1, "Dream Sky", "Wit +22"), technique(2, "Rhythm Basics", "Speed +5"), technique(3, "Yoga Basics", "Guts +5")))
            val d =
                GrandConcertPolicy.recommend(
                    GrandConcertRunState(balances = fullBalances, offers = offers, songsLearned = 1, segment = ConcertSegment.BEFORE_PROMO_3, lessonUnlocked = true),
                )
            assertTrue(d.constraintsAtRisk.any { it.contains("3 songs") || it.contains("fewer than 3") }, d.constraintsAtRisk.toString())
        }

        @Test
        fun `the 16 and 18 song targets are checked before the Grand Concert`() {
            val offers = LessonOfferSet(listOf(song(1, "Dream Sky", "Wit +22"), technique(2, "Rhythm Basics", "Speed +5"), technique(3, "Yoga Basics", "Guts +5")))
            val d =
                GrandConcertPolicy.recommend(
                    GrandConcertRunState(balances = fullBalances, offers = offers, songsLearned = 12, segment = ConcertSegment.BEFORE_GRAND, lessonUnlocked = true),
                )
            assertTrue(d.constraintsAtRisk.any { it.contains("16") })
            assertTrue(d.constraintsAtRisk.any { it.contains("18") })
        }

        @Test
        fun `unknown song progress is reported as unknown, not as zero`() {
            val offers = LessonOfferSet(listOf(song(1, "Dream Sky", "Wit +22"), technique(2, "Rhythm Basics", "Speed +5"), technique(3, "Yoga Basics", "Guts +5")))
            val d = GrandConcertPolicy.recommend(GrandConcertRunState(balances = fullBalances, offers = offers, songsLearned = null, lessonUnlocked = true))
            assertTrue(d.constraintsAtRisk.any { it.contains("unknown") })
        }
    }

    @Nested
    @DisplayName("strategy scoring")
    inner class StrategyScoring {
        private fun v(da: Int, pa: Int, vo: Int, vi: Int, co: Int) = PerformancePointVector.of(da, pa, vo, vi, co)

        private fun songScore(title: String?, mastery: String?, concert: String?, cost: PerformancePointVector, ctx: LessonScoreContext) =
            GrandConcertPolicy.scoreLesson(LessonCardKind.SONG, title, mastery, concert, null, cost, ctx)

        private fun techScore(effect: String?, cost: PerformancePointVector, ctx: LessonScoreContext = LessonScoreContext()) =
            GrandConcertPolicy.scoreLesson(LessonCardKind.TECHNIQUE, "Some Basics", null, null, effect, cost, ctx)

        @Test
        fun `the catalog supplies the bonus types, so garbled bonus text does not change a known song's score`() {
            val ctx = LessonScoreContext(segment = ConcertSegment.BEFORE_PROMO_2)
            val cost = v(0, 21, 0, 21, 0)
            val garbled = songScore("Run for Our Dream!", "#!#@", "#!#@", cost, ctx)
            val clean = songScore("Run for Our Dream!", "Training Skill Pt Gain +2", "Specialty Priority +5", cost, ctx)
            assertEquals(clean, garbled)
        }

        @Test
        fun `a compounding skill point song outranks an immediate stat song mid-career`() {
            val ctx = LessonScoreContext(segment = ConcertSegment.BEFORE_PROMO_2)
            val runForOurDream = songScore("Run for Our Dream!", null, null, v(0, 21, 0, 21, 0), ctx)
            val hereComesOurTime = songScore("Here Comes Our Time", null, null, v(0, 0, 32, 0, 12), ctx)
            assertTrue(runForOurDream > hereComesOurTime, "$runForOurDream vs $hereComesOurTime")
        }

        @Test
        fun `a queued friendship bonus is worth more right before the fourth concert than before bonds exist`() {
            val cost = v(42, 0, 0, 26, 0)
            val early = songScore("Precious Treasure Box", null, null, cost, LessonScoreContext(segment = ConcertSegment.BEFORE_PROMO_1))
            val late = songScore("Precious Treasure Box", null, null, cost, LessonScoreContext(segment = ConcertSegment.BEFORE_PROMO_4))
            assertTrue(late > early, "$late vs $early")
        }

        @Test
        fun `pre-Grand a queued concert bonus is nearly worthless and an immediate bonus wins`() {
            val ctx = LessonScoreContext(segment = ConcertSegment.BEFORE_GRAND, songsLearnedTotal = 18)
            val immediate = songScore("Run n' Run!", null, null, v(14, 0, 0, 16, 14), ctx)
            val compounding = songScore("Grow Up, Shine!", null, null, v(21, 0, 21, 0, 21), ctx)
            assertTrue(immediate > compounding, "$immediate vs $compounding")
        }

        @Test
        fun `the per-cycle floor turns concert urgency into a decisive bonus even for a weak song`() {
            val cost = v(0, 21, 0, 21, 0)
            val urgent = songScore("Ring Ring Diary", null, null, cost, LessonScoreContext(songsLearnedThisCycle = 2, turnsUntilConcert = 2))
            val satisfied = songScore("Ring Ring Diary", null, null, cost, LessonScoreContext(songsLearnedThisCycle = 3, turnsUntilConcert = 2))
            assertTrue(urgent - satisfied >= 200, "$urgent vs $satisfied")
            assertTrue(urgent > techScore("Skill Points +5", v(10, 0, 0, 0, 0)))
        }

        @Test
        fun `vocal cost is penalized over dance cost on the standard deck`() {
            val ctx = LessonScoreContext()
            val vocalPriced = songScore("Some New Song", "Speed +22", null, v(0, 0, 21, 0, 0), ctx)
            val dancePriced = songScore("Some New Song", "Speed +22", null, v(21, 0, 0, 0, 0), ctx)
            assertTrue(dancePriced > vocalPriced, "$dancePriced vs $vocalPriced")
        }

        @Test
        fun `an energy technique dominates other techniques at low energy and collapses near full`() {
            val energyCost = v(0, 0, 0, 0, 30)
            val low = techScore("Energy +30", energyCost, LessonScoreContext(energyPercent = 40))
            val skillPoints = techScore("Skill Points +8", v(0, 16, 0, 0, 0), LessonScoreContext(energyPercent = 40))
            assertTrue(low > skillPoints, "$low vs $skillPoints")

            val nearFull = techScore("Energy +30", energyCost, LessonScoreContext(energyPercent = 95))
            val plainStat = techScore("Speed +5", v(10, 0, 0, 0, 0), LessonScoreContext(energyPercent = 95))
            assertTrue(nearFull < plainStat, "$nearFull vs $plainStat")
        }

        @Test
        fun `career-complete costs are flat, so the drain buys the card it declined live`() {
            // The exact decline from the first validation career: an affordable Power technique at
            // Vo 24 whose identity could not be read. Scarcity-weighted it scored -3 and the drain
            // stopped with 227 Dance unspent; with flat expiring-point costs it clears the line.
            // (The cost-tier classifier now also identifies it, so the garbled-identity variant is
            // reconstructed with an unclassifiable cost of 25.)
            val atEnd = techScore("###", v(0, 0, 0, 0, 25), LessonScoreContext(careerComplete = true))
            assertTrue(atEnd >= GrandConcertPolicy.SPEND_MIN_SCORE_CAREER_COMPLETE, "$atEnd")
            val midCareer = techScore("###", v(0, 0, 25, 0, 0), LessonScoreContext())
            assertTrue(midCareer < GrandConcertPolicy.SPEND_MIN_SCORE, "$midCareer stays below the mid-career line")
        }

        @Test
        fun `career-complete scoring keeps immediate value, zeroes queued bonuses, and demotes compounding and energy`() {
            val end = LessonScoreContext(careerComplete = true)
            val immediateSp = songScore("Run n' Run!", null, null, v(14, 0, 0, 16, 14), end)
            val compounding = songScore("Grow Up and Shine!", null, null, v(21, 0, 21, 0, 21), end)
            assertTrue(immediateSp > compounding, "$immediateSp vs $compounding")

            // The Friendship +10% queued bonus can never activate after the final concert, so the
            // song is worth only its immediate Speed here (it scored ~200 before the 4th concert).
            val treasureBoxAtEnd = songScore("Precious Treasure Box", null, null, v(42, 0, 0, 26, 0), end)
            assertTrue(treasureBoxAtEnd < 40, "$treasureBoxAtEnd")

            // Recovered energy has no training turns left to protect; a plain stat still counts.
            val energyAtEnd = techScore("Energy +30", v(0, 0, 0, 0, 30), end)
            val statAtEnd = techScore("Stamina +8", v(0, 16, 0, 0, 0), end)
            assertTrue(energyAtEnd < statAtEnd, "$energyAtEnd vs $statAtEnd")
        }

        @Test
        fun `describeLessonOffer flags a live cost that diverges from the catalog and stays quiet when it agrees`() {
            // The "sources disagree about this song" note was dropped once master.mdb settled every
            // cost, and it had been actively harmful: it short-circuited the live-vs-catalog
            // comparison, so on the songs it covered a genuine cost change would have been reported
            // as a stale-sources warning instead of the actual delta.
            val balances = PerformancePointVector.of(100, 100, 100, 100, 100)
            val agreeing = LessonListCard(0, "Dream Sky", LessonCardKind.SONG, "Wit +22", "Friendship Training Effectiveness +5%", v(0, 22, 0, 0, 22), learnable = true, scheduled = false)
            val divergent = LessonListCard(1, "Zero Is Where the Center Stands!", LessonCardKind.SONG, "Training Speed Gain +1", "Support Chain Event Frequency +1 level", v(24, 0, 0, 24, 0), learnable = true, scheduled = false)
            val list = LessonList(balances, listOf(agreeing, divergent), hasFullStats = true, hasConcertInfo = true)
            val r = GrandConcertPolicy.describeLessonOffer(list, HypeTier.UNKNOWN, LessonScoreContext())
            assertTrue(r.notes.any { it.contains("differs") && it.contains("Zero Is Where the Center Stands!") }, r.notes.toString())
            assertFalse(r.notes.any { it.contains("Dream Sky") }, r.notes.toString())
        }

        /**
         * The two cards a live Copano Rickey career refused at career end, leaving 213 points to
         * expire. Both are real: an Energy technique costing 25 Vocal and a Skill Hint costing 30
         * Dance, each scored -10 under half-cost pricing.
         */
        @Test
        fun `a career-end technique stays purchasable even when its cost dwarfs its value`() {
            val end = LessonScoreContext(careerComplete = true)
            val energy = techScore("Energy +20", v(0, 0, 25, 0, 0), end)
            val hint = techScore("Skill Hint Lvl +3", v(30, 0, 0, 0, 0), end)
            assertTrue(energy >= GrandConcertPolicy.SPEND_MIN_SCORE_CAREER_COMPLETE, "energy scored $energy")
            assertTrue(hint >= GrandConcertPolicy.SPEND_MIN_SCORE_CAREER_COMPLETE, "skill hint scored $hint")
        }

        @Test
        fun `mid-career pricing is unchanged, so an overpriced technique is still refused`() {
            // The same energy card mid-career at full energy: cost must still be able to veto it,
            // because mid-career points have somewhere better to go.
            val mid = LessonScoreContext(energyPercent = 100)
            assertTrue(techScore("Energy +20", v(0, 0, 60, 0, 0), mid) < 1)
        }

        @Test
        fun `career end still ranks a stat technique above a filler one`() {
            val end = LessonScoreContext(careerComplete = true)
            val stat = techScore("Speed +12", v(0, 0, 0, 0, 30), end)
            val filler = techScore("Energy +20", v(0, 0, 25, 0, 0), end)
            assertTrue(stat > filler, "stat=$stat filler=$filler")
        }

        @Test
        fun `career end prefers the cheaper of two otherwise identical cards`() {
            val end = LessonScoreContext(careerComplete = true)
            val cheap = techScore("Speed +12", v(10, 0, 0, 0, 0), end)
            val dear = techScore("Speed +12", v(200, 0, 0, 0, 0), end)
            assertTrue(cheap > dear, "cheap=$cheap dear=$dear")
        }

        @Test
        fun `the career-end floor never rescues a card the reader could not classify`() {
            // UNKNOWN kind short-circuits before scoreTechnique, so the floor cannot make an
            // unclassifiable card look purchasable.
            val end = LessonScoreContext(careerComplete = true)
            assertEquals(0, GrandConcertPolicy.scoreLesson(LessonCardKind.UNKNOWN, null, null, null, null, v(0, 0, 25, 0, 0), end))
        }
    }

    @Nested
    @DisplayName("spend picker")
    inner class SpendPicker {
        private fun line(slot: Int, score: Int, affordable: Boolean?, scheduled: Boolean = false) =
            LessonOfferLine(slot, "offer$slot", LessonCardKind.TECHNIQUE, affordable, scheduled, score)

        private fun report(vararg lines: LessonOfferLine) =
            GrandConcertLessonReport(lines.sortedByDescending { it.score }, emptyList(), emptyList())

        @Test
        fun `picks the best offer that is provably affordable`() {
            val r = report(line(0, 120, true), line(1, 80, true), line(2, 200, false))
            assertEquals(0, GrandConcertPolicy.chooseSpend(r)?.slot, "the 200 is unaffordable, so the 120 wins")
        }

        @Test
        fun `stops rather than buy junk, unknown affordability, or a scheduled card`() {
            val r = report(line(0, 24, true), line(1, 300, null), line(2, 300, true, scheduled = true))
            assertNull(GrandConcertPolicy.chooseSpend(r))
        }

        @Test
        fun `the career-complete stop line accepts anything positive because the points expire`() {
            val r = report(line(0, 13, true))
            assertNull(GrandConcertPolicy.chooseSpend(r), "13 is below the mid-career stop line")
            assertEquals(0, GrandConcertPolicy.chooseSpend(r, GrandConcertPolicy.SPEND_MIN_SCORE_CAREER_COMPLETE)?.slot)
        }

        @Test
        fun `the live technique trio now buys the skill point technique instead of stalling`() {
            // The exact 2026-07-24 offer that stalled: garbled effect lines, clean titles, Da 10 /
            // Vo 15 / Pa 25 costs. With the title catalog the Skill Pts technique clears the line.
            val balances = PerformancePointVector.of(60, 60, 60, 60, 60)
            val cards =
                listOf(
                    LessonListCard(0, "Watch an Up-and-Coming ldol's Concer", LessonCardKind.TECHNIQUE, "~w»RIB T W T", "nulic", PerformancePointVector.of(10, 0, 0, 0, 0), learnable = true, scheduled = false),
                    LessonListCard(1, "Group Lesson Basics", LessonCardKind.TECHNIQUE, "~PIIEE FRINIR VS T 1", "nulic", PerformancePointVector.of(0, 0, 15, 0, 0), learnable = true, scheduled = false),
                    LessonListCard(2, "Facial-SIimming Massage", LessonCardKind.TECHNIQUE, null, "NONe", PerformancePointVector.of(0, 25, 0, 0, 0), learnable = true, scheduled = false),
                )
            val list = LessonList(balances, cards, hasFullStats = true, hasConcertInfo = true)
            val r = GrandConcertPolicy.describeLessonOffer(list, HypeTier.UNKNOWN, LessonScoreContext(energyPercent = 90))
            val pick = GrandConcertPolicy.chooseSpend(r)
            assertEquals(0, pick?.slot, r.ranked.toString())
        }

        @Test
        fun `gate advance buys the cheapest technique when no song is offered and nothing clears the line`() {
            val r =
                report(
                    line(0, 18, true).copy(weightedCost = 8.5),
                    line(1, 4, true).copy(weightedCost = 22.5),
                    line(2, 15, true).copy(weightedCost = 13.6),
                )
            assertNull(GrandConcertPolicy.chooseSpend(r))
            assertEquals(0, GrandConcertPolicy.chooseGateAdvance(r)?.slot, "cheapest eligible technique wins")
        }

        @Test
        fun `urgency widens the gate-advance cost cap - the cycle 4 stall`() {
            // The exact shape that left cycle 4 at two songs: every gate technique priced above
            // the calm cap. Urgent (floor unmet, concert close) accepts the cheapest of them.
            val r = report(line(0, 7, true).copy(weightedCost = 26.4), line(1, 2, true).copy(weightedCost = 24.0), line(2, -6, true).copy(weightedCost = 22.5))
            assertNull(GrandConcertPolicy.chooseGateAdvance(r), "calm cap keeps refusing")
            assertEquals(1, GrandConcertPolicy.chooseGateAdvance(r, urgent = true)?.slot, "urgent takes the cheapest positive-score technique")
        }

        @Test
        fun `song-first buys any affordable song while the cycle is under the Great Success floor`() {
            // The measured 2026-07-26 failure class: roughly two of five concerts missed the
            // three-song condition while affordable songs sat below the score floor.
            val lowScoreSong = LessonOfferLine(0, "cheap song", LessonCardKind.SONG, true, false, 12, 30.0, rawCostTotal = 44)
            val bigTech = LessonOfferLine(1, "shiny tech", LessonCardKind.TECHNIQUE, true, false, 90, 10.0, rawCostTotal = 16)
            val r = GrandConcertLessonReport(listOf(bigTech, lowScoreSong), emptyList(), emptyList())
            assertEquals(0, GrandConcertPolicy.chooseSongFirst(r, 0)?.slot, "a 12-score song beats no song at 0/3")
            assertEquals(0, GrandConcertPolicy.chooseSongFirst(r, 2)?.slot, "still under the floor at 2/3")
            assertNull(GrandConcertPolicy.chooseSongFirst(r, 3), "at the floor the normal ranking takes over")
            assertNull(GrandConcertPolicy.chooseSongFirst(r, null), "an unknown count never forces a buy")
        }

        @Test
        fun `song-first never buys an unaffordable, unproven, or scheduled song`() {
            val unaffordable = LessonOfferLine(0, "song A", LessonCardKind.SONG, false, false, 200, 60.0, rawCostTotal = 63)
            val unknown = LessonOfferLine(1, "song B", LessonCardKind.SONG, null, false, 150, 55.0, rawCostTotal = 50)
            val scheduled = LessonOfferLine(2, "song C", LessonCardKind.SONG, true, true, 100, 45.0, rawCostTotal = 42)
            val r = GrandConcertLessonReport(listOf(unaffordable, unknown, scheduled), emptyList(), emptyList())
            assertNull(GrandConcertPolicy.chooseSongFirst(r, 0))
        }

        @Test
        fun `the milestone extra above the floor is conditional on the technique reserve`() {
            // The 3-4-4-3-3 cadence: cycles 2 and 3 target a fourth song, but only when the
            // purchase provably leaves the reserve intact, so the 17-song chase can never starve
            // the next cycle's three-song floor.
            val song = LessonOfferLine(0, "extra song", LessonCardKind.SONG, true, false, 40, 35.0, rawCostTotal = 44)
            val r = GrandConcertLessonReport(listOf(song), emptyList(), emptyList())
            assertEquals(0, GrandConcertPolicy.chooseSongFirst(r, 3, cycleTarget = 4, totalBalance = 130)?.slot, "130 - 44 = 86 keeps the reserve")
            assertNull(GrandConcertPolicy.chooseSongFirst(r, 3, cycleTarget = 4, totalBalance = 100), "100 - 44 = 56 breaks the reserve")
            assertNull(GrandConcertPolicy.chooseSongFirst(r, 4, cycleTarget = 4, totalBalance = 300), "at the target the normal ranking takes over")
            assertNull(GrandConcertPolicy.chooseSongFirst(r, 3, cycleTarget = 4, totalBalance = null), "an unknown balance never funds the extra")
            assertNull(
                GrandConcertPolicy.chooseSongFirst(r.copy(ranked = listOf(song.copy(rawCostTotal = null))), 3, cycleTarget = 4, totalBalance = 300),
                "an unreadable cost never funds the extra",
            )
            // The unconditional floor path ignores the balance entirely: under 3 songs, count
            // beats everything, exactly as before.
            assertEquals(0, GrandConcertPolicy.chooseSongFirst(r, 2, cycleTarget = 4, totalBalance = 45)?.slot, "the floor path stays unconditional")
        }

        @Test
        fun `the cycle targets follow the milestone cadence and fall back to the floor`() {
            assertEquals(listOf(3, 4, 4, 3, 3), (0..4).map { GrandConcertPolicy.songTargetForCycle(it) })
            assertEquals(GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value, GrandConcertPolicy.songTargetForCycle(5), "after the Grand the floor is the fallback")
        }

        @Test
        fun `the technique reserve blocks a technique that would drain the pool below the floor`() {
            // Tonight's starvation shape: the 48-61 cycle bought techniques after its own songs
            // and the Grand finale's cycle started broke. Balance 80, technique costing 20 would
            // leave 60, below the 70 reserve.
            val tech = LessonOfferLine(0, "tech", LessonCardKind.TECHNIQUE, true, false, 60, 12.0, rawCostTotal = 20)
            val r = GrandConcertLessonReport(listOf(tech), emptyList(), emptyList())
            assertNull(
                GrandConcertPolicy.chooseSpend(r, totalBalance = 80, reserveActive = true),
                "80 - 20 = 60 < ${GrandConcertPolicy.TECH_RESERVE_TOTAL}",
            )
            assertEquals(0, GrandConcertPolicy.chooseSpend(r, totalBalance = 80, reserveActive = false)?.slot, "reserve off (career end or after the Grand)")
            assertEquals(0, GrandConcertPolicy.chooseSpend(r, totalBalance = 120, reserveActive = true)?.slot, "120 - 20 = 100 stays above the floor")
            assertNull(
                GrandConcertPolicy.chooseSpend(r.copy(ranked = listOf(tech.copy(rawCostTotal = null))), totalBalance = 120, reserveActive = true),
                "an unreadable cost fails toward the reserve, never through it",
            )
        }

        @Test
        fun `the technique reserve never blocks a song`() {
            val song = LessonOfferLine(0, "song", LessonCardKind.SONG, true, false, 90, 45.0, rawCostTotal = 63)
            val r = GrandConcertLessonReport(listOf(song), emptyList(), emptyList())
            assertEquals(0, GrandConcertPolicy.chooseSpend(r, totalBalance = 75, reserveActive = true)?.slot, "songs are what the reserve is FOR")
        }

        @Test
        fun `gate advance never fires when a song is offered or a normal pick exists`() {
            val withSong =
                GrandConcertLessonReport(
                    listOf(
                        LessonOfferLine(0, "song", LessonCardKind.SONG, false, false, 40, 60.0),
                        LessonOfferLine(1, "tech", LessonCardKind.TECHNIQUE, true, false, 10, 8.5),
                    ),
                    emptyList(),
                    emptyList(),
                )
            assertNull(GrandConcertPolicy.chooseGateAdvance(withSong), "an unaffordable song still means the gate is open")

            val withNormalPick = report(line(0, 40, true).copy(weightedCost = 8.5), line(1, 10, true).copy(weightedCost = 5.0))
            assertNull(GrandConcertPolicy.chooseGateAdvance(withNormalPick), "the normal pick owns this trio")
        }
    }

    @Nested
    @DisplayName("provenance")
    inner class ProvenanceLabels {
        @Test
        fun `every seeded target carries a provenance label`() {
            assertEquals(Provenance.GLOBAL_CONFIRMED, GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.provenance)
            assertEquals(Provenance.COMMUNITY_MODEL, GrandConcertPolicy.LINK_EVENT_SONG_TARGET.provenance)
            assertEquals(Provenance.COMMUNITY_MODEL, GrandConcertPolicy.SPECIAL_SONG_TARGET.provenance)
            assertEquals(Provenance.JP_CONFIRMED, GrandConcertScenario.SCENARIO_LINK_TRAINEES.provenance)
        }

        @Test
        fun `the fifth performance type is Composure on Global, with Mental accepted as an alias`() {
            // The Global client's own text data says "Composure Training" and scores "Composure";
            // several community guides say "Mental". The client wins, the guide spelling is
            // tolerated on input only.
            assertEquals(PerformancePointType.COMPOSURE, PerformancePointType.fromText("Composure"))
            assertEquals(PerformancePointType.COMPOSURE, PerformancePointType.fromText("Mental"))
            assertEquals("Composure", PerformancePointType.COMPOSURE.displayName)
            assertEquals(5, PerformancePointType.entries.size)
        }

        @Test
        fun `unreadable balances stay unknown rather than defaulting`() {
            val b = balances(PerformancePointType.DANCE to 10)
            assertFalse(b.complete)
            assertNull(b[PerformancePointType.VOCAL])
            assertEquals(4, b.unknownTypes.size)
        }
    }
}

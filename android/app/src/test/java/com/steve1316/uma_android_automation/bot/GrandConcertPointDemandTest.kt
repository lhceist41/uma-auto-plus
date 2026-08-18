package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.PerformancePointType.COMPOSURE
import com.steve1316.uma_android_automation.bot.PerformancePointType.DANCE
import com.steve1316.uma_android_automation.bot.PerformancePointType.PASSION
import com.steve1316.uma_android_automation.bot.PerformancePointType.VISUAL
import com.steve1316.uma_android_automation.bot.PerformancePointType.VOCAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The widened Grand Concert training-demand set: the pure per-color merge of the current song, the
 * gate-advancing technique (while the song gate is closed), and one next-song lookahead (while behind
 * the cadence), plus the catalog lookup that supplies the next song. Everything here is pure; the
 * scorer's own ceiling/proximity behavior is pinned in TrainingScoringTest.
 */
@DisplayName("Grand Concert widened point demand")
class GrandConcertPointDemandTest {
    private fun v(da: Int, pa: Int, vo: Int, vi: Int, co: Int) = PerformancePointVector.of(da, pa, vo, vi, co)

    private fun bal(da: Int, pa: Int, vo: Int, vi: Int, co: Int): Map<PerformancePointType, Int?> =
        mapOf(DANCE to da, PASSION to pa, VOCAL to vo, VISUAL to vi, COMPOSURE to co)

    private fun merge(
        currentSong: PerformancePointVector? = null,
        gateTech: PerformancePointVector? = null,
        offerHadSong: Boolean = currentSong != null,
        nextSong: PerformancePointVector? = null,
        balances: Map<PerformancePointType, Int?> = bal(0, 0, 0, 0, 0),
    ) = GrandConcertPointDemand.merge(currentSong, gateTech, offerHadSong, nextSong, balances)

    // ---- component selection by gate state ----

    @Test
    @DisplayName("current-song demand when a song is on offer: existing behavior preserved, gate suppressed")
    fun currentSongWhenGateOpen() {
        val m = merge(currentSong = v(21, 0, 0, 21, 0), gateTech = v(15, 0, 0, 0, 0), offerHadSong = true, balances = bal(5, 0, 0, 5, 0))
        assertEquals(mapOf(DANCE to 21, VISUAL to 21), m.currentSongDemand)
        assertEquals(emptyMap<PerformancePointType, Int>(), m.gateTechniqueDemand) // gate suppressed while a song is buyable
        assertEquals(mapOf(DANCE to 16, VISUAL to 16), m.deficit)
    }

    @Test
    @DisplayName("gate-technique demand when the song gate is closed (no song on offer): the color-stranding fix")
    fun gateTechniqueWhenGateClosed() {
        val m = merge(currentSong = v(21, 0, 0, 21, 0), gateTech = v(10, 15, 25, 0, 0), offerHadSong = false, balances = bal(1, 10, 10, 10, 10))
        assertEquals(emptyMap<PerformancePointType, Int>(), m.currentSongDemand) // no active song, so no current-song demand
        assertEquals(mapOf(DANCE to 10, PASSION to 15, VOCAL to 25), m.gateTechniqueDemand)
        assertEquals(mapOf(DANCE to 9, PASSION to 5, VOCAL to 15), m.deficit) // gate colors, balance-reduced
    }

    // ---- the real completed-career early fixture (technique-gate phase) ----

    @Test
    @DisplayName("early technique-gate fixture: old current-song-only demand is empty, the new gate demand is present")
    fun earlyGatePhaseFixture() {
        // Real early balances (Da=1, Pa=10, Vo=10, Vi=10, Co=10) and observed gate-technique costs
        // (Da10, Pa15, Vo25). With no song on offer the old current-song-only model produced no demand;
        // the widened model steers toward the gate colors that unlock the next song.
        val balances = bal(1, 10, 10, 10, 10)
        val oldStyle = merge(currentSong = null, gateTech = v(10, 15, 25, 0, 0), offerHadSong = false, balances = balances).currentSongDemand
        assertEquals(emptyMap<PerformancePointType, Int>(), oldStyle)
        val m = merge(currentSong = null, gateTech = v(10, 15, 25, 0, 0), offerHadSong = false, balances = balances)
        assertEquals(mapOf(DANCE to 9, PASSION to 5, VOCAL to 15), m.deficit)
        // A color the gate does not need (Composure) never enters the demand, so a surplus Composure
        // gain earns no mission credit.
        assertFalse(m.deficit.containsKey(COMPOSURE))
    }

    // ---- next-song lookahead (cumulative-behind), added sequentially ----

    @Test
    @DisplayName("next-song demand adds to the active purchase sequentially when the career is behind")
    fun nextSongAddsWhenBehind() {
        // Active song needs Da/Vi; the next song needs Pa. Both are bought in sequence, so the colors add.
        val m = merge(currentSong = v(21, 0, 0, 21, 0), offerHadSong = true, nextSong = v(0, 42, 0, 0, 0), balances = bal(0, 0, 0, 0, 0))
        assertEquals(mapOf(PASSION to 42), m.nextSongDemand)
        assertEquals(mapOf(DANCE to 21, PASSION to 42, VISUAL to 21), m.deficit)
    }

    @Test
    @DisplayName("a shared color sums the active and next-song costs, minus the balance once")
    fun sharedColorSumsMinusBalanceOnce() {
        val m = merge(currentSong = v(20, 0, 0, 0, 0), offerHadSong = true, nextSong = v(30, 0, 0, 0, 0), balances = bal(10, 0, 0, 0, 0))
        // 20 + 30 = 50 demanded, minus the current balance 10 = 40.
        assertEquals(40, m.deficit[DANCE])
    }

    @Test
    @DisplayName("next-song demand disappears when the caller supplies no next song (cadence restored)")
    fun nextSongStandsDown() {
        val m = merge(currentSong = v(21, 0, 0, 21, 0), offerHadSong = true, nextSong = null, balances = bal(0, 0, 0, 0, 0))
        assertEquals(emptyMap<PerformancePointType, Int>(), m.nextSongDemand)
        assertEquals(mapOf(DANCE to 21, VISUAL to 21), m.deficit)
    }

    // ---- surplus / unread-balance safety ----

    @Test
    @DisplayName("a surplus color earns no credit and an unread balance is omitted")
    fun surplusAndUnreadBalance() {
        // Da over-covered (balance 30 vs cost 21) -> deficit 0; Passion balance unread -> omitted.
        val m = merge(currentSong = v(21, 22, 0, 0, 0), offerHadSong = true, balances = mapOf(DANCE to 30, PASSION to null))
        assertEquals(0, m.deficit[DANCE]) // present but zero (surplus earns no credit)
        assertFalse(m.deficit.containsKey(PASSION)) // unread balance is never guessed against
    }

    // ---- next-song catalog lookup ----

    @Test
    @DisplayName("the next-song lookup returns the cheapest unpurchased, non-free song in the current stage")
    fun catalogCheapestUnpurchased() {
        val s = GrandConcertSongCatalog.cheapestUnpurchasedInStage(currentPhase = 1, purchasedTitles = emptySet())
        assertTrue(s != null && !s.free && s.phase <= 1)
        assertEquals(42, s!!.cost.total()) // the cheapest phase-1 shop songs cost 42 points
    }

    @Test
    @DisplayName("the next-song lookup excludes an already-purchased song")
    fun catalogExcludesPurchased() {
        val first = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet())!!
        val afterBuying = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, setOf(first.title))
        assertNotEquals(first.title, afterBuying?.title) // the bought song is not offered again
    }

    @Test
    @DisplayName("the next-song lookup excludes free songs and scopes to the current stage")
    fun catalogExcludesFreeAndScopesStage() {
        // Phase 1 must never surface a phase-2+ song.
        assertTrue(GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet())!!.phase <= 1)
        // GIRLS' LEGEND U (phase 5, free) is never returned as a demand target even at the final stage.
        val late = GrandConcertSongCatalog.cheapestUnpurchasedInStage(5, emptySet())
        assertTrue(late == null || !late.free)
    }

    @Test
    @DisplayName("the next-song lookup returns null when every stage song is purchased")
    fun catalogNullWhenAllPurchased() {
        val allPhase1 = GrandConcertSongCatalog.songs.filter { it.phase <= 1 && !it.free }.map { it.title }.toSet()
        assertNull(GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, allPhase1))
    }

    // ---- current-song distinctness: the current song is not returned as its own next song ----

    @Test
    @DisplayName("the next-song lookup excludes the supplied current song (and a canonicalized OCR variant of it)")
    fun catalogExcludesCurrentSong() {
        val cheapest = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet())!!
        val next = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet(), excludeTitle = cheapest.title)
        assertNotEquals(cheapest.title, next?.title) // the current song is not returned as its own next song
        assertTrue(next != null && !next.free && next.phase <= 1)
        // Canonicalization: an OCR-style variant (lowercased, punctuation stripped) still excludes it,
        // via the same match() path used for purchased titles - no second normalization system.
        val variant = cheapest.title.lowercase().filter { it.isLetterOrDigit() }
        assertNotEquals(cheapest.title, GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet(), excludeTitle = variant)?.title)
    }

    @Test
    @DisplayName("with no excludeTitle (the gate-phase case), the stage-cheapest song is still returned")
    fun catalogGatePhaseKeepsStaleTarget() {
        // During a gate phase the assembly passes excludeTitle = null, so a stale last-song target that
        // happens to be the stage-cheapest is NOT dropped - it may legitimately be the next song.
        val cheapest = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet())
        assertEquals(cheapest?.title, GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet(), excludeTitle = null)?.title)
    }

    @Test
    @DisplayName("when the current song is the stage-cheapest, the merged demand does not double-count it")
    fun demandDistinctnessNoDoubleCount() {
        // Mirror the assembly: the current song on offer is the stage-cheapest, and the next-song lookup
        // excludes it, so the two demands are distinct songs.
        val current = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet())!!
        val nextCost = GrandConcertSongCatalog.cheapestUnpurchasedInStage(1, emptySet(), excludeTitle = current.title)?.cost
        assertNotNull(nextCost)
        val distinct = merge(currentSong = current.cost, offerHadSong = true, nextSong = nextCost, balances = bal(0, 0, 0, 0, 0))
        // The pre-fix behavior would have returned the current song again as the next song, doubling its
        // colors. A distinct next song produces a different (non-doubled) merged deficit.
        val doubled = merge(currentSong = current.cost, offerHadSong = true, nextSong = current.cost, balances = bal(0, 0, 0, 0, 0))
        assertNotEquals(doubled.deficit, distinct.deficit)
    }
}

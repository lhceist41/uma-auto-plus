package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.GrandConcertFanRaceScanPlanner.LookupTier
import com.steve1316.uma_android_automation.bot.GrandConcertFanRaceScanPlanner.ScanCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure groundwork for the future below-the-fold fan-race scan: the fail-closed DB-identity trust
 * policy, cross-page dedup, fans-first ranking over the page union, and the second-pass restoration
 * guard. No prediction tier is carried. None of this is wired into production; the live path still
 * ranks only the visible page.
 */
@DisplayName("Grand Concert fan-race scan planner (groundwork, not wired)")
class GrandConcertFanRaceScanPlannerTest {
    private fun c(
        name: String = "Race",
        canonical: String? = name,
        tier: LookupTier = LookupTier.EXACT,
        matchCount: Int = 1,
        dbFans: Int? = 0,
        apt: Boolean? = null,
        rival: Boolean = false,
        page: Int = 0,
    ) = ScanCandidate(name, canonical, tier, matchCount, dbFans, apt, rival, page)

    // ---- DB-identity trust policy (exact-only) ----

    @Test
    @DisplayName("DB fans is trusted only for a unique exact resolution")
    fun trustPolicy() {
        assertEquals(5000, c(dbFans = 5000, tier = LookupTier.EXACT, matchCount = 1).trustedDbFans) // unique exact
        assertNull(c(dbFans = 5000, tier = LookupTier.EXACT, matchCount = 2).trustedDbFans) // same-turn collision
        assertNull(c(dbFans = 5000, tier = LookupTier.FUZZY, matchCount = 1).trustedDbFans) // fuzzy is never trusted
        assertNull(c(dbFans = null, tier = LookupTier.NONE, matchCount = 0, canonical = null).trustedDbFans) // no match
    }

    @Test
    @DisplayName("aptitude is trusted only for a unique exact resolution")
    fun aptitudeTrust() {
        assertEquals(true, c(apt = true, tier = LookupTier.EXACT, matchCount = 1).trustedAptitudeCompatible)
        assertNull(c(apt = true, tier = LookupTier.FUZZY, matchCount = 1).trustedAptitudeCompatible)
        assertNull(c(apt = true, tier = LookupTier.EXACT, matchCount = 2).trustedAptitudeCompatible)
    }

    @Test
    @DisplayName("a same-turn formatted-name collision (two races, one label) fails closed to unknown")
    fun sameTurnCollisionFailsClosed() {
        // Turn 31: Oka Sho (10500) and Arlington Cup (3800) share one formatted name -> matchCount 2.
        val collided = c(name = "Hanshin Turf 1600m (Mile) Right / Outer", canonical = null, matchCount = 2, dbFans = 10500)
        assertFalse(collided.isTrusted)
        assertNull(collided.trustedDbFans)
    }

    @Test
    @DisplayName("the same race name on different turns resolves by turn scope")
    fun turnScopedIdentity() {
        val t43 = c(canonical = "Mainichi Okan")
        val t67 = c(canonical = "Mainichi Okan")
        assertEquals("43|Mainichi Okan", GrandConcertFanRaceScanPlanner.trustedIdentity(43, t43))
        assertEquals("67|Mainichi Okan", GrandConcertFanRaceScanPlanner.trustedIdentity(67, t67))
    }

    // ---- cross-page dedup ----

    @Test
    @DisplayName("overlapping pages collapse a trusted duplicate but preserve untrusted rows separately")
    fun dedup() {
        val cands =
            listOf(
                c(canonical = "Race A", dbFans = 1000, page = 0),
                c(canonical = "Race A", dbFans = 1000, page = 1), // same trusted identity -> merged
                c(name = "Fuzzy Row", canonical = null, tier = LookupTier.FUZZY, matchCount = 1, dbFans = 9999, page = 0),
                c(name = "Fuzzy Row", canonical = null, tier = LookupTier.FUZZY, matchCount = 1, dbFans = 9999, page = 1), // untrusted -> kept
            )
        val plan = GrandConcertFanRaceScanPlanner.plan(24, cands, bottomProven = true)
        assertEquals(3, plan.deduped.size) // 1 trusted A + 2 untrusted fuzzy rows
    }

    // ---- fans-first ranking union + fuzzy safety ----

    @Test
    @DisplayName("ranks the page union by trusted DB fans: a page-1 5000 beats a page-0 1500")
    fun rankingUnion() {
        val cands = listOf(c(canonical = "Small", dbFans = 1500, page = 0), c(canonical = "Big", dbFans = 5000, page = 1))
        val plan = GrandConcertFanRaceScanPlanner.plan(24, cands, bottomProven = true)
        assertEquals(1, plan.winnerIndex)
        assertTrue(plan.bottomProven)
    }

    @Test
    @DisplayName("fans-first: a larger fan value beats an aptitude-compatible smaller one")
    fun fansPrimaryOverAptitude() {
        val cands = listOf(c(canonical = "Big", dbFans = 3100, apt = false), c(canonical = "Small", dbFans = 1600, apt = true))
        assertEquals(0, GrandConcertFanRaceScanPlanner.plan(24, cands, bottomProven = true).winnerIndex)
    }

    @Test
    @DisplayName("aptitude breaks an exact trusted fan tie")
    fun aptitudeExactFanTie() {
        val cands = listOf(c(canonical = "Plain", dbFans = 3000, apt = false), c(canonical = "Fit", dbFans = 3000, apt = true))
        assertEquals(1, GrandConcertFanRaceScanPlanner.plan(24, cands, bottomProven = true).winnerIndex)
    }

    @Test
    @DisplayName("an untrusted fuzzy 10000 never outranks a trusted exact 5000")
    fun fuzzyNeverOutranksTrusted() {
        val cands =
            listOf(
                c(name = "Fuzzy Big", canonical = null, tier = LookupTier.FUZZY, matchCount = 1, dbFans = 10000, page = 0),
                c(canonical = "Exact Mid", dbFans = 5000, page = 0),
            )
        // The fuzzy row's DB fans is untrusted (unknown), so the exact 5000 wins.
        assertEquals(1, GrandConcertFanRaceScanPlanner.plan(24, cands, bottomProven = true).winnerIndex)
    }

    @Test
    @DisplayName("when no row carries a trusted DB fan value, a deterministic row (index 0) is still chosen")
    fun allUntrustedDeterministicFallback() {
        val cands =
            listOf(
                c(name = "F1", canonical = null, tier = LookupTier.FUZZY, matchCount = 1, dbFans = 9000),
                c(name = "F2", canonical = null, tier = LookupTier.NONE, matchCount = 0, dbFans = null),
            )
        val plan = GrandConcertFanRaceScanPlanner.plan(24, cands, bottomProven = true)
        assertEquals(0, plan.winnerIndex)
        assertEquals("all-fan-values-unknown-required-race-fallback", plan.reason)
    }

    @Test
    @DisplayName("an incomplete scan carries bottomProven=false so it can never claim a full-list optimum")
    fun incompleteScanFlagged() {
        val plan = GrandConcertFanRaceScanPlanner.plan(24, listOf(c(canonical = "A", dbFans = 5000)), bottomProven = false)
        assertFalse(plan.bottomProven)
    }

    // ---- second-pass restoration guard ----

    @Test
    @DisplayName("restoration accepts only a re-detected row with the same trusted identity")
    fun restorationGuard() {
        val winner = c(canonical = "Target Race")
        assertTrue(GrandConcertFanRaceScanPlanner.restorationMatches(24, winner, c(canonical = "Target Race")))
        assertFalse(GrandConcertFanRaceScanPlanner.restorationMatches(24, winner, c(canonical = "Other Race"))) // different identity
        // An untrusted second-pass resolution (fuzzy) is never a safe tap target.
        assertFalse(
            GrandConcertFanRaceScanPlanner.restorationMatches(24, winner, c(canonical = null, tier = LookupTier.FUZZY, matchCount = 1)),
        )
    }
}

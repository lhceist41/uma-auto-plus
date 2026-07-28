package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Roster matching against the REAL OCR strings from the 2026-07-28 12:39 scan, verbatim.
 *
 * That scan read all 35 cells with zero blanks and still halted the queue: the target's own cell,
 * "[Azure Amazon] Hishi Amazon 1", scored 0.000 while "[Clippety-Tippety-Clopl Matikanetannhauser"
 * scored 0.546. Both numbers came out of the alignment, not out of any similarity between the
 * names, so these tests pin the behaviour to the strings the game actually produces rather than to
 * tidy ones.
 */
@DisplayName("Trainee roster matching (real OCR corpus)")
class TraineeRosterMatchTest {
    /** The live match threshold in CareerLaunchNavigator. */
    private val threshold = 0.86

    /** Every cell the 12:39 scan read, in scan order, exactly as it was logged. */
    private val roster =
        listOf(
            "[Get to Winning!] Winning Ticket",
            "[Wild Top Gear] Vodka",
            "[Jokester t Vibes] Tosen Jordan",
            "[Wild Frontier] Taiki Shuttle",
            "[Emperor",
            "[Platanus Witchl Sweep Tosho",
            "[Murmuring Stream] Super Creek",
            "[Blossom in Learningl Sakura Bakushin O",
            "[Rosy Dreams] Rice Shower",
            "[Poinsettia Ribbon] Nice Nature",
            "[CODE: ICING] Mihono Bourbon",
            "[Down the Line] Mejiro Ryan",
            "[Line Breakthrough] Mejiro Palmer",
            "(Frontline Elegance] Mejiro McQueen",
            "[Turbulent Blue] Meisho Doto",
            "[ScrambletZone] Mayano Top Gun",
            "[Clippety-Tippety-Clopl Matikanetannhauser",
            "[RisingFortunel Matikanefukukitaru",
            "(Formula R] Maruzensky",
            "[King of Emeralds] King Halo",
            "[Azure Amazon] Hishi Amazon 1",
            "(Bestest Prize D] Haru Urara",
            "(Saintly Jade Cleric] Grass Wonder M",
            "(Stone-Piercing Blue] Grass Wonder",
            "(Saintly Jade Cleric] Grass Wonder",
            "[Red Strifel Gold Ship",
            "[Autumn Cosmos] Gold City",
            "[El<Número 1] El Condor Pasa",
            "[Kukulkan Warrior] El Condor Pasa",
            "[Peak Blue] Daiwa Scarlet",
            "(EightfoldFortune] Copano Rickey",
            "[Rouge Caroler] Biwa Hayahide",
            "[Iron Ambition] Bamboo Memory",
            "[Empress Road] Air Groove",
            "[tach-nologyl Agnes Tachyon",
        )

    private fun cellsAboveThreshold(target: String) = roster.filter { TraineeNameMatcher.score(target, it) >= threshold }

    @Nested
    @DisplayName("the target wins its own cell, and only its own cell")
    inner class TargetWinsTests {
        @Test
        fun `Hishi Amazon matches exactly one cell despite the trailing OCR badge`() {
            val hits = cellsAboveThreshold("Hishi Amazon")
            assertEquals(listOf("[Azure Amazon] Hishi Amazon 1"), hits)
        }

        @Test
        fun `Meisho Doto matches exactly one cell`() {
            assertEquals(listOf("[Turbulent Blue] Meisho Doto"), cellsAboveThreshold("Meisho Doto"))
        }

        @Test
        fun `Biwa Hayahide matches exactly one cell`() {
            assertEquals(listOf("[Rouge Caroler] Biwa Hayahide"), cellsAboveThreshold("Biwa Hayahide"))
        }

        @Test
        fun `every trainee in the roster finds herself and nobody else`() {
            // The strong form: sweep every distinct character in the corpus.
            val targets =
                mapOf(
                    "Winning Ticket" to "[Get to Winning!] Winning Ticket",
                    "Vodka" to "[Wild Top Gear] Vodka",
                    "Tosen Jordan" to "[Jokester t Vibes] Tosen Jordan",
                    "Taiki Shuttle" to "[Wild Frontier] Taiki Shuttle",
                    "Sweep Tosho" to "[Platanus Witchl Sweep Tosho",
                    "Super Creek" to "[Murmuring Stream] Super Creek",
                    "Rice Shower" to "[Rosy Dreams] Rice Shower",
                    "Nice Nature" to "[Poinsettia Ribbon] Nice Nature",
                    "Mihono Bourbon" to "[CODE: ICING] Mihono Bourbon",
                    "Mejiro Ryan" to "[Down the Line] Mejiro Ryan",
                    "Mejiro Palmer" to "[Line Breakthrough] Mejiro Palmer",
                    "Mejiro McQueen" to "(Frontline Elegance] Mejiro McQueen",
                    "Meisho Doto" to "[Turbulent Blue] Meisho Doto",
                    "Mayano Top Gun" to "[ScrambletZone] Mayano Top Gun",
                    "Matikanetannhauser" to "[Clippety-Tippety-Clopl Matikanetannhauser",
                    "Matikanefukukitaru" to "[RisingFortunel Matikanefukukitaru",
                    "Maruzensky" to "(Formula R] Maruzensky",
                    "King Halo" to "[King of Emeralds] King Halo",
                    "Hishi Amazon" to "[Azure Amazon] Hishi Amazon 1",
                    "Haru Urara" to "(Bestest Prize D] Haru Urara",
                    "Gold Ship" to "[Red Strifel Gold Ship",
                    "Gold City" to "[Autumn Cosmos] Gold City",
                    "Daiwa Scarlet" to "[Peak Blue] Daiwa Scarlet",
                    "Copano Rickey" to "(EightfoldFortune] Copano Rickey",
                    "Biwa Hayahide" to "[Rouge Caroler] Biwa Hayahide",
                    "Bamboo Memory" to "[Iron Ambition] Bamboo Memory",
                    "Air Groove" to "[Empress Road] Air Groove",
                    "Agnes Tachyon" to "[tach-nologyl Agnes Tachyon",
                )
            for ((target, expected) in targets) {
                val hits = cellsAboveThreshold(target)
                assertTrue(hits.contains(expected), "'$target' should match '$expected' but hits were $hits")
                assertEquals(1, hits.size, "'$target' matched more than her own cell: $hits")
            }
        }
    }

    @Nested
    @DisplayName("the guard still refuses")
    inner class RefusalTests {
        @Test
        fun `a trainee absent from the roster matches nothing`() {
            // Refusing here was correct both times it happened live; that must not weaken.
            for (absent in listOf("Kitasan Black", "Silence Suzuka", "Special Week", "Oguri Cap", "Tokai Teio")) {
                assertEquals(emptyList<String>(), cellsAboveThreshold(absent), "'$absent' is not owned and must match nothing")
            }
        }

        @Test
        fun `Gold Ship and Gold City never match each other`() {
            // The shared-first-word case the per-word floor exists for. Sliding the window must not
            // have opened it back up.
            assertTrue(TraineeNameMatcher.score("Gold Ship", "[Autumn Cosmos] Gold City") < threshold)
            assertTrue(TraineeNameMatcher.score("Gold City", "[Red Strifel Gold Ship") < threshold)
        }

        @Test
        fun `sibling outfits of the same character are still distinguishable by outfit`() {
            // Both El Condor Pasa outfits are owned; an outfit-qualified target must pick one.
            assertTrue(TraineeNameMatcher.score("[Kukulkan Warrior] El Condor Pasa", "[Kukulkan Warrior] El Condor Pasa") >= threshold)
            assertTrue(TraineeNameMatcher.score("[Kukulkan Warrior] El Condor Pasa", "[El<Número 1] El Condor Pasa") < threshold)
        }

        @Test
        fun `the two Matikane names do not match each other`() {
            assertTrue(TraineeNameMatcher.score("Matikanetannhauser", "[RisingFortunel Matikanefukukitaru") < threshold)
            assertTrue(TraineeNameMatcher.score("Matikanefukukitaru", "[Clippety-Tippety-Clopl Matikanetannhauser") < threshold)
        }
    }

    @Nested
    @DisplayName("the 2026-07-28 numbers")
    inner class RegressionTests {
        @Test
        fun `the correct cell no longer scores zero`() {
            val s = TraineeNameMatcher.score("Hishi Amazon", "[Azure Amazon] Hishi Amazon 1")
            assertTrue(s >= threshold, "expected a match, got $s")
        }

        @Test
        fun `an unrelated long name no longer outranks the correct one`() {
            val correct = TraineeNameMatcher.score("Hishi Amazon", "[Azure Amazon] Hishi Amazon 1")
            val wrong = TraineeNameMatcher.score("Hishi Amazon", "[Clippety-Tippety-Clopl Matikanetannhauser")
            assertTrue(correct > wrong, "correct=$correct must beat wrong=$wrong")
            assertTrue(wrong < threshold, "the wrong trainee must stay below threshold, got $wrong")
        }

        @Test
        fun `similarity still ranks the nearest cell for the failure message`() {
            // score() answers "is this her" and says 0.0 when it is not, so the diagnostic needs its
            // own number: the nearest cell to an absent trainee should be a same-length-ish name,
            // not whichever unrelated string happened to share letters.
            val nearest = roster.maxByOrNull { TraineeNameMatcher.similarity("Hishi Amazon", it) }
            assertEquals("[Azure Amazon] Hishi Amazon 1", nearest)
        }
    }
}

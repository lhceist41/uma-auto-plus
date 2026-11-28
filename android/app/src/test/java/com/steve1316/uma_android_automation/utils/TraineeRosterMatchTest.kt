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

    /**
     * Near misses against the 50 cells of account 2's 2026-11-26 14:02 scan, verbatim. Whole-string
     * similarity put "[Down the Line] Mejiro Ryan" at 0.879 to "Mejiro Dober", which hid the answer
     * that she is owned only as Sapphire Sojourn and blamed name matching instead.
     */
    @Nested
    @DisplayName("near misses (2026-11-26 roster)")
    inner class NearnessTests {
        private val near = 0.70

        /** Banner as logged, paired with the character it shows. */
        private val roster1126 =
            listOf(
                "[Red Strifel Gold Ship" to "Gold Ship",
                "(Stone-Piercing Blue] Grass Wonder" to "Grass Wonder",
                "[Empress Road] Air Groove" to "Air Groove",
                "[ScrambletZone] Mayano Top Gun" to "Mayano Top Gun",
                "[Down the Line] Mejiro Ryan" to "Mejiro Ryan",
                "[tach-nologyl Agnes Tachyon" to "Agnes Tachyon",
                "[Get to Winning!] Winning Ticket" to "Winning Ticket",
                "[Blossom in Learningl Sakura Bakushin O" to "Sakura Bakushin O",
                "[RisingFortunel Matikanefukukitaru" to "Matikanefukukitaru",
                "[Poinsettia Ribbon] Nice Nature" to "Nice Nature",
                "[King of Emeralds] King Halo" to "King Halo",
                "[Hopp'nHappy Heart] Special Week" to "Special Week",
                "[Innocent Silence] Silence Suzuka" to "Silence Suzuka",
                "(Formula R] Maruzensky" to "Maruzensky",
                "[HottSummer Night Maruzensky" to "Maruzensky",
                "[Ashen Miracle] Oguri Cap" to "Oguri Cap",
                "[Wild Top Gear] Vodka" to "Vodka",
                "[Peak Blue] Daiwa Scarlet" to "Daiwa Scarlet",
                "[Wild Frontier] Taiki Shuttle" to "Taiki Shuttle",
                "(Frontline Elegance] Mejiro McQueen" to "Mejiro McQueen",
                "[ElAN\u00famero 1] El Condor Pasa" to "El Condor Pasa",
                "[Maverick] Narita Brian" to "Narita Brian",
                "[Quercus Civilis] Air Groove" to "Air Groove",
                "[Reeling in the Big Onel Seiun Sky" to "Seiun Sky",
                "[Creeping Shadow] Manhattan Cafe" to "Manhattan Cafe",
                "[Rosy Dreams] Rice Shower" to "Rice Shower",
                "[Murmuring Stream] Super Creek" to "Super Creek",
                "(Bestest Prize D] Haru Urara" to "Haru Urara",
                "[Gilded Shrine to Gloryl Kitasan Black" to "Kitasan Black",
                "[Flare] Aston Machan" to "Aston Machan",
                "[Special Dreamer] Special Week" to "Special Week",
                "[Succ\u00e8s \u00c9toil\u0117] Fuji Kiseki" to "Fuji Kiseki",
                "(Starlight Beat] Oguri Cap" to "Oguri Cap",
                "[End of the Skies] Mejiro McQueen" to "Mejiro McQueen",
                "[Kukulkan Warrior] El Condor Pasa" to "El Condor Pasa",
                "[O Sole Suo!] T.M. Opera O" to "T.M. Opera O",
                "[Emperor's Path] Symboli Rudolf" to "Symboli Rudolf",
                "(Fast as Lightning Tamamo Cross" to "Tamamo Cross",
                "[Rouge Caroler] Biwa Hayahide" to "Biwa Hayahide",
                "[Buono t Alla Moda] Hishi Akebono" to "Hishi Akebono",
                "[Precise Chocolatier] Eishin Flash" to "Eishin Flash",
                "[Princess of Pink] Kawakami Princess" to "Kawakami Princess",
                "[Rocket*Star] Seeking the Pearl" to "Seeking the Pearl",
                "[Desperate Measures] Nakayama Festa" to "Nakayama Festa",
                "[New Year > New Uraral] Haru Urara" to "Haru Urara",
                "[Iron Ambition] Bamboo Memory" to "Bamboo Memory",
                "[Turbulent Blue] Meisho Doto" to "Meisho Doto",
                "(Sapphire Sojourn] Mejiro Dober" to "Mejiro Dober",
                "[Run & Win] Nice Nature" to "Nice Nature",
                "[Clippety-Tippety-Clopl Matikanetannhauser" to "Matikanetannhauser",
            )

        private fun maxNearness(target: String, excludeOutfits: List<String>) =
            roster1126.map { it.first }.filter { b -> excludeOutfits.none { TraineeNameMatcher.hasOutfit(b, it) } }.maxOf { TraineeNameMatcher.nearness(target, it) }

        @Test
        fun `a different trainee sharing a family name or letters is not a near miss`() {
            val pairs =
                listOf(
                    "Mejiro Dober" to "[Down the Line] Mejiro Ryan",
                    "Mejiro Dober" to "[End of the Skies] Mejiro McQueen",
                    "Shinko Windy" to "[Gilded Shrine to Gloryl Kitasan Black",
                    "Shinko Windy" to "[Reeling in the Big Onel Seiun Sky",
                    "Biwa Hayahide" to "[Peak Blue] Daiwa Scarlet",
                    "Nakayama Festa" to "[Creeping Shadow] Manhattan Cafe",
                    "Matikanetannhauser" to "[RisingFortunel Matikanefukukitaru",
                )
            for ((target, banner) in pairs) {
                assertTrue(TraineeNameMatcher.similarity(target, banner) >= near, "precondition: whole-string similarity calls $banner near $target")
                assertTrue(TraineeNameMatcher.nearness(target, banner) < near, "$banner must not be near $target")
            }
        }

        @Test
        fun `a misread of her own name stays near`() {
            assertEquals(0.0, TraineeNameMatcher.score("Biwa Hayahide", "[Rouge Caroler] Bwa Hayahide"), "precondition: the match misses it")
            assertTrue(TraineeNameMatcher.nearness("Biwa Hayahide", "[Rouge Caroler] Bwa Hayahide") >= near)
            assertTrue(TraineeNameMatcher.nearness("Mejiro Dober", "[Off the Line] Mejro Dobe") >= near)
            assertEquals("[Azure Amazon] Hishi Amazon 1", roster.maxByOrNull { TraineeNameMatcher.nearness("Hishi Amazon", it) })
        }

        /** A one-letter misread of her plain cell must keep the only-another-outfit answer closed: that cell is still on the roster. */
        @Test
        fun `a one-letter misread of her own plain cell stays near, so a read sibling outfit does not claim she owns only that`() {
            val cases =
                listOf(
                    Triple("Curren Chan", "Ma Chérie of the New Moon", "[Fille Éclair] Curren ahan"),
                    Triple("El Condor Pasa", "Kukulkan Warrior", "[El☆Número 1] l Condor Pasa"),
                    Triple("El Condor Pasa", "Kukulkan Warrior", "[El☆Número 1] Ea Condor Pasa"),
                    Triple("Biwa Hayahide", "Rouge Caroler", "[pf. Winning Equation...] wiwa Hayahide"),
                    Triple("Admire Vega", "Full-Color Fangirling", "[Starry Nocturne] Admire gega"),
                )
            for ((target, sibling, misread) in cases) {
                val excludes = listOf(sibling)
                val cells = roster1126.map { it.first } + "[$sibling] $target" + misread
                assertTrue(TraineeNameMatcher.score(target, misread) < threshold, "precondition: the scan misses '$misread'")
                val seen = cells.firstNotNullOfOrNull { TraineeNameMatcher.excludedOutfitOf(target, it, excludes, threshold) }
                assertEquals(sibling, seen, "precondition: her sibling outfit is read")
                assertTrue(TraineeNameMatcher.nearness(target, misread) >= near, "'$misread' must stay near $target")
                val nearest = cells.filter { b -> excludes.none { TraineeNameMatcher.hasOutfit(b, it) } }.maxOf { TraineeNameMatcher.nearness(target, it) }
                assertFalse(RosterScanPolicy.onlyExcludedOutfitOwned(0, seen, nearest, near), "$target with '$misread' on the roster")
            }
        }

        @Test
        fun `no character on the roster is near another character's name`() {
            for ((_, character) in roster1126.distinctBy { it.second }) {
                for ((banner, other) in roster1126) {
                    if (other == character) continue
                    assertTrue(TraineeNameMatcher.nearness(character, banner) < near, "$banner came out near $character")
                }
            }
        }

        @Test
        fun `every roster cell is near its own character`() {
            for ((banner, character) in roster1126) assertTrue(TraineeNameMatcher.nearness(character, banner) >= near, "$banner not near $character")
        }

        @Test
        fun `Mejiro Dober owned only as Sapphire Sojourn is reported as that, not as a name-matching miss`() {
            val excludes = listOf("Sapphire Sojourn")
            val seen = roster1126.firstNotNullOfOrNull { TraineeNameMatcher.excludedOutfitOf("Mejiro Dober", it.first, excludes, threshold) }
            assertEquals("Sapphire Sojourn", seen)
            assertTrue(roster1126.map { it.first }.filter { b -> excludes.none { TraineeNameMatcher.hasOutfit(b, it) } }.maxOf { TraineeNameMatcher.similarity("Mejiro Dober", it) } >= near, "precondition: the old gate closed")
            assertTrue(RosterScanPolicy.onlyExcludedOutfitOwned(0, seen, maxNearness("Mejiro Dober", excludes), near))
        }

        @Test
        fun `Shinko Windy, who is not owned, stays not found and reads as nothing resembling her`() {
            assertNull(roster1126.firstNotNullOfOrNull { TraineeNameMatcher.excludedOutfitOf("Shinko Windy", it.first, emptyList(), threshold) })
            assertTrue(roster1126.none { TraineeNameMatcher.score("Shinko Windy", it.first) >= threshold })
            val nearest = maxNearness("Shinko Windy", emptyList())
            assertTrue(nearest < near, "a cell came out near Shinko Windy at $nearest")
            assertFalse(RosterScanPolicy.onlyExcludedOutfitOwned(0, null, nearest, near))
        }
    }
}

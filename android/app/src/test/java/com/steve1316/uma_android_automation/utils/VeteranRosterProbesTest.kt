package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Parser unit tests for [VeteranRosterProbes], pinned against literal OCR strings measured off the
 * PL-R1 fixture set (`validation/parentlab-plr1-roster/`, gitignored; the values here are the exact
 * clean reads, since the fixture is not decoded in this JUnit target - see PL-R1a's live-calibration
 * requirement for the on-device OCR-noise proof). Every fixture-observed value is pinned exactly;
 * every malformed/impossible variant proves the parser reports null instead of guessing.
 */
@DisplayName("VeteranRosterProbes Tests")
class VeteranRosterProbesTest {
    @Nested
    @DisplayName("Roster list status bar")
    inner class RosterListParsing {
        @Test
        fun `registered reads the fixture value exactly`() {
            assertEquals(257 to 260, parseRegistered("Registered  257/260"))
        }

        @Test
        fun `registered rejects used greater than capacity`() {
            assertNull(parseRegistered("300/260"))
        }

        @Test
        fun `registered rejects a missing numerator`() {
            assertNull(parseRegistered("Registered /260"))
        }

        @Test
        fun `filters off reads true`() {
            assertEquals(true, parseFiltersOff("Filters: OFF"))
        }

        @Test
        fun `filters on reads false, never silently treated as off`() {
            assertEquals(false, parseFiltersOff("Filters: ON"))
        }

        @Test
        fun `filters unreadable stays unresolved`() {
            assertNull(parseFiltersOff(""))
        }

        @Test
        fun `sort key matches the known vocabulary`() {
            assertEquals("Rating", parseSortKey("Rating"))
            assertEquals("Date Acquired", parseSortKey("Date Acquired"))
        }

        @Test
        fun `sort key garbled OCR stays unresolved rather than guessed`() {
            assertNull(parseSortKey("Ratlng"))
        }

        @Test
        fun `sort direction reads Asc and Desc`() {
            assertEquals("Asc", parseSortDirection("Asc"))
            assertEquals("Desc", parseSortDirection("Desc"))
            assertNull(parseSortDirection("Asx"))
        }
    }

    @Nested
    @DisplayName("Umamusume Details header")
    inner class HeaderParsing {
        @Test
        fun `name and outfit split the two-line fixture read`() {
            val (outfit, name) = parseNameOutfit("[Wild Frontier]\nTaiki Shuttle")
            assertEquals("Wild Frontier", outfit)
            assertEquals("Taiki Shuttle", name)
        }

        @Test
        fun `a missing outfit bracket leaves outfit unresolved but keeps the name`() {
            val (outfit, name) = parseNameOutfit("Taiki Shuttle")
            assertNull(outfit)
            assertEquals("Taiki Shuttle", name)
        }

        @Test
        fun `rank validates against the real rank-tier domain`() {
            assertEquals("A", parseRank("A\nRANK"))
            assertEquals("SS", parseRank("SS\nRANK"))
        }

        @Test
        fun `rank outside the known domain stays unresolved`() {
            assertNull(parseRank("ZZ\nRANK"))
        }

        @Test
        fun `rating reads the exact fixture integer`() {
            assertEquals(10192, parseRating("10,192"))
        }

        @Test
        fun `rating rejects an unreadable region`() {
            assertNull(parseRating(""))
        }
    }

    @Nested
    @DisplayName("Stat cells - all 5 final stats")
    inner class StatCellParsing {
        @Test
        fun `every fixture stat cell reads grade and value together`() {
            // Taiki Shuttle (fixture 02/05/06)
            assertEquals(StatCellRead("A+", 949), parseStatCell("A+ 949"))
            assertEquals(StatCellRead("B", 699), parseStatCell("B 699"))
            assertEquals(StatCellRead("B", 648), parseStatCell("B 648"))
            assertEquals(StatCellRead("B", 687), parseStatCell("B 687"))
            assertEquals(StatCellRead("C", 420), parseStatCell("C 420"))
        }

        @Test
        fun `a doubled-letter tier grade reads whole, not just its first letter`() {
            // Copano Rickey (fixture 08): Speed 1164 reads "SS+", not "S+" - this is the exact
            // second-Veteran case the design doc's "prevent a one-card overfit" note called for.
            assertEquals(StatCellRead("SS+", 1164), parseStatCell("SS+ 1164"))
            assertEquals(StatCellRead("D", 344), parseStatCell("D 344"))
        }

        @Test
        fun `a missing grade still resolves the value`() {
            assertEquals(StatCellRead(null, 949), parseStatCell("949"))
        }

        @Test
        fun `an implausible stat value is rejected, not clamped`() {
            assertEquals(StatCellRead("A", null), parseStatCell("A 25000"))
        }
    }

    @Nested
    @DisplayName("Aptitude cells - all 10 aptitude grades")
    inner class AptitudeCellParsing {
        @Test
        fun `every fixture aptitude cell resolves to its grade`() {
            assertEquals("A", parseAptitudeGrade("Turf A"))
            assertEquals("B", parseAptitudeGrade("Dirt B"))
            assertEquals("A", parseAptitudeGrade("Sprint A"))
            assertEquals("A", parseAptitudeGrade("Mile A"))
            assertEquals("E", parseAptitudeGrade("Medium E"))
            assertEquals("G", parseAptitudeGrade("Long G"))
            assertEquals("C", parseAptitudeGrade("Front C"))
            assertEquals("A", parseAptitudeGrade("Pace A"))
            assertEquals("E", parseAptitudeGrade("Late E"))
            assertEquals("G", parseAptitudeGrade("End G"))
        }

        @Test
        fun `the second fixture's aptitude cells resolve too (Copano Rickey, fixture 08)`() {
            assertEquals("F", parseAptitudeGrade("Turf F"))
            assertEquals("S", parseAptitudeGrade("Pace S"))
        }

        @Test
        fun `a cell with no grade glyph stays unresolved`() {
            assertNull(parseAptitudeGrade("Sprint"))
        }

        @Test
        fun `a glyph outside S-G stays unresolved, never guessed`() {
            assertNull(parseAptitudeGrade("Sprint Z"))
        }
    }

    @Nested
    @DisplayName("Career Info block")
    inner class CareerInfoParsing {
        @Test
        fun `career record reads races and wins`() {
            assertEquals(18 to 13, parseCareerRecord("Career Record   Races: 18  Wins: 13"))
        }

        @Test
        fun `career record rejects wins greater than races`() {
            assertNull(parseCareerRecord("Races: 5 Wins: 10"))
        }

        @Test
        fun `fans earned reads the exact fixture integer`() {
            assertEquals(191730, parseFansEarned("Fans Earned   191,730"))
        }

        @Test
        fun `career scenario keeps its internal colon`() {
            assertEquals("The Beginning: URA Finale", parseCareerScenario("Career Scenario   The Beginning: URA Finale"))
        }

        @Test
        fun `career rating reads independently of the header rating pill`() {
            assertEquals(10192, parseCareerRatingValue("Rating   10,192"))
        }

        @Test
        fun `date acquired normalizes to ISO`() {
            assertEquals("2026-08-10", parseDateAcquired("Date Acquired   Aug 10, 2026"))
        }

        @Test
        fun `date acquired with an unrecognised month stays unresolved`() {
            assertNull(parseDateAcquired("Date Acquired   Zzz 10, 2026"))
        }
    }

    @Nested
    @DisplayName("Favorite marker classification")
    inner class FavoriteMarkerClassification {
        @Test
        fun `a pure grayscale glyph classifies as not_set`() {
            val sampler = SparkPixelSampler { _, _ -> 0xFF888888.toInt() }
            assertEquals(FavoriteMarkerRead.NOT_SET, classifyFavoriteMarker(sampler, cx = 0, cy = 0))
        }

        @Test
        fun `a saturated glyph classifies as unknown, never a specific icon guess`() {
            val sampler = SparkPixelSampler { _, _ -> 0xFFFF6600.toInt() }
            assertEquals(FavoriteMarkerRead.UNKNOWN, classifyFavoriteMarker(sampler, cx = 0, cy = 0))
        }
    }

    @Nested
    @DisplayName("rosterFingerprint - ported FNV-1a-128, cross-checked against the TS side")
    inner class FingerprintTests {
        @Test
        fun `contentHash128 matches the TS reference vectors exactly`() {
            // Computed independently by running src/lib/parentLab/identity.ts's own algorithm under
            // Node for these two inputs. A match here proves the Kotlin ULong port is bit-exact, not
            // merely internally self-consistent.
            assertEquals("cbf29ce48422232584222325cbf29ce4", contentHash128(""))
            assertEquals("e71fa2190541574b7bab84f7414251ac", contentHash128("abc"))
        }

        @Test
        fun `rosterFingerprint matches the fixture Taiki Shuttle vector`() {
            val evidence =
                RosterIdentityEvidence(
                    character = "Taiki Shuttle",
                    outfit = "Wild Frontier",
                    rank = "A",
                    rating = 10192,
                    stats = listOf(949, 699, 648, 687, 420),
                    aptitudes = listOf("A", "B", "A", "A", "E", "G", "C", "A", "E", "G"),
                )
            assertEquals("9767b3f3f4ca4da5d8b36ba708ac7df0", rosterFingerprint(evidence))
        }

        @Test
        fun `same evidence always hashes identical`() {
            val evidence = RosterIdentityEvidence("Copano Rickey", "Anime Expo", "A", 10381, listOf(900, 700, 650, 690, 430), listOf("A", "B", "A", "A", "E", "G", "C", "A", "E", "G"))
            assertEquals(rosterFingerprint(evidence), rosterFingerprint(evidence))
        }

        @Test
        fun `distinct evidence hashes distinct`() {
            val a = RosterIdentityEvidence("Taiki Shuttle", "Wild Frontier", "A", 10192, listOf(949, 699, 648, 687, 420), listOf("A", "B", "A", "A", "E", "G", "C", "A", "E", "G"))
            val b = a.copy(rating = 10193)
            assertEquals(false, rosterFingerprint(a) == rosterFingerprint(b))
        }
    }
}

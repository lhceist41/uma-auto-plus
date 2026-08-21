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
            // The exact live read: the adjacent list icon bleeds into the crop and mangles the third
            // glyph. A short A-or-D token still decodes this closed two-value toggle unambiguously.
            assertEquals("Asc", parseSortDirection("AsE"))
            assertEquals("Desc", parseSortDirection("DesE"))
            assertNull(parseSortDirection(""))
            assertNull(parseSortDirection("Rating"))
            assertNull(parseSortDirection("Filters: OFF"))
            assertNull(parseSortDirection("Registered 257/260"))
        }
    }

    @Nested
    @DisplayName("Umamusume Details header - rating (grade badges are pixel-classified, see VeteranBadgeClassifierTest)")
    inner class HeaderParsing {
        @Test
        fun `rating reads the exact fixture integer`() {
            assertEquals(10192, parseRating("10,192"))
        }

        @Test
        fun `rating rejects an unreadable region`() {
            assertNull(parseRating(""))
        }

        @Test
        fun `stat value parses digits and rejects an implausible read`() {
            assertEquals(949, parseStatValue("949"))
            assertEquals(1164, parseStatValue("1164"))
            assertNull(parseStatValue("25000"))
            assertNull(parseStatValue(""))
            // A dropped-digit read must not enter identity. Both of these were observed live on the
            // 20-entry walk (Guts "1", Wit "4") and were fingerprinted as if real.
            assertNull(parseStatValue("1"))
            assertNull(parseStatValue("4"))
            assertEquals(90, parseStatValue("90"))
        }
    }

    @Nested
    @DisplayName("Stat value resolution - independent-read consensus, fail-closed")
    inner class StatValueResolution {
        private fun cand(value: Int, variant: String) = NumericReadCandidate(value, variant, value.toString())

        @Test
        fun `no candidates stays unresolved`() {
            assertNull(resolveStatValue(emptyList()))
        }

        @Test
        fun `a lone plausible high read is accepted`() {
            // The Power leading-digit clip: the primary "7042" fell out of range and only the widened
            // geometry read the value. One trusted-range read is enough.
            assertEquals(1042, resolveStatValue(listOf(cand(1042, "wide"))))
        }

        @Test
        fun `two geometries agreeing form a consensus`() {
            assertEquals(1042, resolveStatValue(listOf(cand(1042, "primary"), cand(1042, "wide"))))
        }

        @Test
        fun `consensus beats a single-variant dissenter`() {
            assertEquals(
                500,
                resolveStatValue(listOf(cand(500, "primary"), cand(500, "wide"), cand(999, "tight"))),
            )
        }

        @Test
        fun `two distinct plausible values with no consensus stay unresolved`() {
            assertNull(resolveStatValue(listOf(cand(900, "primary"), cand(800, "wide"))))
        }

        @Test
        fun `a top tie between two corroborated values is a conflict`() {
            assertNull(
                resolveStatValue(listOf(cand(500, "a"), cand(500, "b"), cand(999, "c"), cand(999, "d"))),
            )
        }

        @Test
        fun `a lone suspicious-low read is rejected`() {
            // 61/41/25/23/10 were accepted under the old flat minimum and minted wrong fingerprints.
            for (v in listOf(10, 23, 25, 34, 41, 48, 61, 79)) {
                assertNull(resolveStatValue(listOf(cand(v, "primary"))), "lone $v must not be trusted")
            }
        }

        @Test
        fun `a suspicious-low read the same geometry merely repeats cannot corroborate itself`() {
            // Two threshold re-reads of one box share the "primary" variant, so they are one vote: a
            // geometry-driven dropped digit that reads the same wrong low value twice is still unresolved.
            assertNull(
                resolveStatValue(listOf(NumericReadCandidate(61, "primary", "61"), NumericReadCandidate(61, "primary", "61"))),
            )
        }

        @Test
        fun `a genuinely low value is accepted only when two independent geometries agree`() {
            assertEquals(61, resolveStatValue(listOf(cand(61, "primary"), cand(61, "wide"))))
        }

        @Test
        fun `a suspicious-low read never vetoes a trusted-range read from another geometry`() {
            // Live: the primary box clipped 1061 to "061" (= 61) while the widened box read the true
            // 1061. The below-floor artifact must not force the entry unresolved - the widened read is
            // exactly what it is there to recover.
            assertEquals(1061, resolveStatValue(listOf(cand(61, "primary"), cand(1061, "wide"))))
            assertEquals(1079, resolveStatValue(listOf(cand(79, "primary"), cand(1079, "wide"))))
        }

        @Test
        fun `two trusted-range values still conflict even with a low read present`() {
            // Ignoring the below-floor read does not lower the bar for a genuine high-vs-high
            // disagreement: those still fail closed.
            assertNull(resolveStatValue(listOf(cand(61, "primary"), cand(900, "wide"), cand(800, "tight"))))
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
        fun `career rating reads with or without the pill label`() {
            assertEquals(10192, parseCareerRatingValue("Rating   10,192"))
            // Live capture: OCR returns only the value for this row, not the "Rating" pill label.
            assertEquals(10192, parseCareerRatingValue("10,192"))
        }

        @Test
        fun `career rating stays unresolved on a wrong-tab crop with no number`() {
            // The exact Skills-tab OCR seen live at the Career Rating crop position.
            assertNull(parseCareerRatingValue("TTIe"))
            assertNull(parseCareerRatingValue(""))
        }

        @Test
        fun `date acquired normalizes to ISO with or without the pill label`() {
            assertEquals("2026-08-10", parseDateAcquired("Date Acquired   Aug 10, 2026"))
            // Live capture: OCR returns only the date value for this row, not the label.
            assertEquals("2026-08-10", parseDateAcquired("Aug 10, 2026"))
        }

        @Test
        fun `date acquired with an unrecognised month or a wrong-tab crop stays unresolved`() {
            assertNull(parseDateAcquired("Zzz 10, 2026"))
            // The exact Skills-tab OCR seen live at the Date Acquired crop position.
            assertNull(parseDateAcquired("a Medium"))
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

    @Nested
    @DisplayName("Screen classification")
    inner class ScreenClassification {
        @Test
        fun `a registered read is the roster list`() {
            assertEquals(RosterScreenKind.ROSTER_LIST, classifyRosterScreen(257 to 260, "anything"))
        }

        @Test
        fun `an umamusume details title is the details dialog`() {
            assertEquals(RosterScreenKind.UMAMUSUME_DETAILS, classifyRosterScreen(null, "Umamusume Details"))
        }

        @Test
        fun `the startup notification banner never reads as a details dialog`() {
            // The bot's own "Automation is now running" heads-up notification covers the title band at
            // session start; its OCR must classify UNKNOWN so the reader re-captures, never DETAILS.
            assertEquals(RosterScreenKind.UNKNOWN, classifyRosterScreen(null, "Status  now  Automation is now running"))
        }

        @Test
        fun `an unreadable frame stays unknown`() {
            assertEquals(RosterScreenKind.UNKNOWN, classifyRosterScreen(null, ""))
        }
    }

    @Nested
    @DisplayName("Walk geometry and the tap deny list")
    inner class WalkSafety {
        /** Every coordinate the roster walk is allowed to tap, with the screen it is tapped on. */
        private val allowedTaps =
            listOf(
                Triple("first_card", RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X to ROSTER_FIRST_CARD_Y),
                Triple("roster_back", RosterScreenKind.ROSTER_LIST, ROSTER_BACK_X to ROSTER_BACK_Y),
                Triple("next_chevron", RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X to DETAIL_NEXT_CHEVRON_Y),
                Triple("detail_close", RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_CLOSE_X to DETAIL_CLOSE_Y),
            )

        @Test
        fun `every deny zone rejects its own centre`() {
            for (zone in ROSTER_DENY_ZONES) {
                val cx = (zone.x0 + zone.x1) / 2
                val cy = (zone.y0 + zone.y1) / 2
                assertEquals(zone.label, deniedZoneAt(zone.screen, cx, cy)?.label, "${zone.label} centre")
            }
        }

        @Test
        fun `a deny zone only applies to its own screen`() {
            val favorite = ROSTER_DENY_ZONES.first { it.label == "detail_favorite_marker" }
            val cx = (favorite.x0 + favorite.x1) / 2
            val cy = (favorite.y0 + favorite.y1) / 2
            assertNull(deniedZoneAt(RosterScreenKind.ROSTER_LIST, cx, cy))
        }

        @Test
        fun `no allowed tap lands in a deny zone, jitter included`() {
            // CoordinateTap jitters a coordinate tap by up to half its 25x25 fallback region per axis.
            // Asserting the whole half-region envelope is stricter than the actual [-6, +6] draw, so a
            // future jitter change cannot quietly walk a tap into a Transfer or Batch Favorite button.
            val envelope = com.steve1316.uma_android_automation.bot.CoordinateTap.REGION / 2
            for ((label, screen, point) in allowedTaps) {
                for (dx in -envelope..envelope) {
                    for (dy in -envelope..envelope) {
                        val hit = deniedZoneAt(screen, point.first + dx, point.second + dy)
                        assertNull(hit, "$label jittered to (${point.first + dx}, ${point.second + dy}) hit ${hit?.label}")
                    }
                }
            }
        }

        @Test
        fun `the deny list covers every irreversible control PL-R1 identified on these two screens`() {
            val labels = ROSTER_DENY_ZONES.map { it.label }.toSet()
            assertEquals(
                setOf("roster_transfer", "roster_batch_favorite", "detail_favorite_marker", "detail_share", "detail_change_outfit", "detail_epithet_pencil"),
                labels,
            )
        }

        @Test
        fun `the favorite marker sample point sits inside its own deny zone`() {
            // The marker is read by pixel and must never be tapped; pinning the read point inside the
            // deny rect keeps the two definitions of "where the favorite glyph is" from drifting.
            assertEquals("detail_favorite_marker", deniedZoneAt(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_FAVORITE_CX, DETAIL_FAVORITE_CY)?.label)
        }
    }
}

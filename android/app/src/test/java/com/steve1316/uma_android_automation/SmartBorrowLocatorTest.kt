package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Smart Borrow selection LOCATOR + intent parsing.
 *
 * Proves the pure decision that binds an offline recommendation (a supportCardId with canonical
 * identity fields) to a live picker row, and that it fails closed on everything that must never tap a
 * card nobody chose: the card absent, a wrong-limit-break copy, or two equivalent offerings.
 */
@DisplayName("Smart Borrow locator")
class SmartBorrowLocatorTest {
    private fun intent(
        character: String = "Kitasan Black",
        title: String? = "Fire at My Heels",
        expectedLimitBreak: Int? = 4,
        expectedLevel: Int? = 50,
        sourceAlias: String? = null,
        supportCardId: Int = 30001,
    ) = SmartBorrowIntent(
        schema = SMART_BORROW_INTENT_SCHEMA,
        schemaVersion = 1,
        targetProfile = "Medium",
        sourceBorrowScanId = "bp-live",
        supportCardId = supportCardId,
        canonicalCharacter = character,
        canonicalTitle = title,
        displayName = "$character [$title]",
        rarity = "SSR",
        expectedLevel = expectedLevel,
        expectedLimitBreak = expectedLimitBreak,
        sourceAlias = sourceAlias,
        resolutionPath = "EXACT_TITLE",
        recommendationEvidenceDigest = "djb2-00000000",
    )

    private fun row(
        pageIndex: Int = 0,
        character: String? = "Kitasan Black",
        outfit: String? = "Fire at My Heels",
        limitBreakIndex: Int? = 4,
        level: Int? = 50,
        ownerAlias: String? = null,
        blocked: Boolean = false,
    ) = LocatableBorrowRow(pageIndex, character, outfit, limitBreakIndex, level, ownerAlias, blocked, "high")

    @Nested
    @DisplayName("locate")
    inner class Locate {
        @Test
        fun `the exact intended card is located`() {
            val match = SmartBorrowLocator.locate(intent(), listOf(row(character = "Silence Suzuka", outfit = "Beyond This Shining Moment"), row()))
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertEquals("Kitasan Black", match.row?.character)
        }

        @Test
        fun `an OCR-noisy title still resolves through the canonical matcher`() {
            // The picker split the name band into character + outfit; the outfit gained bracket noise.
            val match = SmartBorrowLocator.locate(intent(), listOf(row(outfit = "Fire at My Heelsl")))
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
        }

        @Test
        fun `the wrong card is never selected`() {
            val match = SmartBorrowLocator.locate(intent(), listOf(row(character = "Winning Ticket", outfit = "Dreams Do Come True!")))
            assertEquals(SmartBorrowLocateVerdict.NOT_FOUND, match.verdict)
            assertNull(match.row)
        }

        @Test
        fun `a same-character wrong-outfit row does not match`() {
            val match = SmartBorrowLocator.locate(intent(), listOf(row(outfit = "Blossoming Bond")))
            assertEquals(SmartBorrowLocateVerdict.NOT_FOUND, match.verdict)
        }

        @Test
        fun `a blocked row is never located even when the identity matches`() {
            val match = SmartBorrowLocator.locate(intent(), listOf(row(blocked = true)))
            assertEquals(SmartBorrowLocateVerdict.NOT_FOUND, match.verdict)
        }
    }

    @Nested
    @DisplayName("limit break gate")
    inner class LimitBreakGate {
        @Test
        fun `a known limit-break mismatch fails closed`() {
            val match = SmartBorrowLocator.locate(intent(expectedLimitBreak = 4), listOf(row(limitBreakIndex = 2)))
            assertEquals(SmartBorrowLocateVerdict.LB_MISMATCH, match.verdict)
            assertNull(match.row)
        }

        @Test
        fun `an unobserved limit break on either side is not decisive`() {
            assertEquals(SmartBorrowLocateVerdict.LOCATED, SmartBorrowLocator.locate(intent(expectedLimitBreak = null), listOf(row(limitBreakIndex = 2))).verdict)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, SmartBorrowLocator.locate(intent(expectedLimitBreak = 4), listOf(row(limitBreakIndex = null))).verdict)
        }
    }

    @Nested
    @DisplayName("equivalent offerings")
    inner class Equivalents {
        @Test
        fun `two equivalent offerings without an alias resolve to a deterministic equivalent, not a block`() {
            // Same card + same limit break from two owners is one equivalence class: owner has no gameplay
            // effect, so a deterministic equivalent is chosen (LOCATED), not blocked.
            val match = SmartBorrowLocator.locate(intent(sourceAlias = null), listOf(row(ownerAlias = "owner-a"), row(ownerAlias = "owner-b")))
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertTrue(match.equivalentSource)
            assertEquals(2, match.equivalenceClassSize)
        }

        @Test
        fun `the deterministic equivalent pick is stable regardless of row order`() {
            val a = row(ownerAlias = "owner-a")
            val b = row(ownerAlias = "owner-b")
            val first = SmartBorrowLocator.locate(intent(sourceAlias = null), listOf(a, b)).row
            val second = SmartBorrowLocator.locate(intent(sourceAlias = null), listOf(b, a)).row
            assertEquals(first?.ownerAlias, second?.ownerAlias, "the same equivalence class always yields the same pick")
        }

        @Test
        fun `a source alias picks the intent's own source among several equivalents`() {
            val target = row(ownerAlias = "owner-b")
            val match = SmartBorrowLocator.locate(intent(sourceAlias = "owner-b"), listOf(row(ownerAlias = "owner-a"), target))
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertTrue(match.disambiguatedByAlias)
            assertFalse(match.equivalentSource)
            assertSame(target, match.row)
        }

        @Test
        fun `an alias that is no longer present still allows a deterministic equivalent`() {
            // The intent's own owner rotated out, but the same card is still offered by others: allowed.
            val match = SmartBorrowLocator.locate(intent(sourceAlias = "owner-gone"), listOf(row(ownerAlias = "owner-a"), row(ownerAlias = "owner-b")))
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertTrue(match.equivalentSource)
        }

        @Test
        fun `the same character and title at different known limit breaks is non-equivalent and blocks`() {
            // Only possible when the intent's own limit break is unknown: two different copies, not one class.
            val match = SmartBorrowLocator.locate(
                intent(expectedLimitBreak = null),
                listOf(row(limitBreakIndex = 2, ownerAlias = "owner-a"), row(limitBreakIndex = 4, ownerAlias = "owner-b")),
            )
            assertEquals(SmartBorrowLocateVerdict.AMBIGUOUS, match.verdict)
            assertNull(match.row)
        }
    }

    @Nested
    @DisplayName("selection authority + tap-safe identity (A3-R2)")
    inner class SelectionAuthority {
        @Test
        fun `LOCATED with a complete traversal authorises a tap`() {
            assertTrue(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.LOCATED, traversalComplete = true))
        }

        @Test
        fun `LOCATED with a stalled traversal does NOT authorise a tap`() {
            assertFalse(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.LOCATED, traversalComplete = false))
        }

        @Test
        fun `no non-LOCATED status ever authorises a tap, even with a complete traversal`() {
            for (s in SmartBorrowLocateResult.Status.values().filter { it != SmartBorrowLocateResult.Status.LOCATED }) {
                assertFalse(canSelectLocatedBorrow(s, traversalComplete = true), "$s must not authorise a tap")
            }
        }

        @Test
        fun `tap-safe identity requires a compatible limit break, not only character and title`() {
            val i = intent(expectedLimitBreak = 4)
            assertTrue(rowMatchesIntentIdentity(i, "Kitasan Black", "Fire at My Heels", 4), "same card + same LB matches")
            assertFalse(rowMatchesIntentIdentity(i, "Kitasan Black", "Fire at My Heels", 3), "a wrong-LB copy of the same card must NOT match the tap-safe identity")
            assertFalse(rowMatchesIntentIdentity(i, "Silence Suzuka", "Fire at My Heels", 4), "a wrong character never matches")
            assertFalse(rowMatchesIntentIdentity(i, "Kitasan Black", "Blossoming Bond", 4), "a wrong title never matches")
            assertTrue(rowMatchesIntentIdentity(i, "Kitasan Black", "Fire at My Heels", null), "an unread row LB is not decisive")
            assertTrue(rowMatchesIntentIdentity(intent(expectedLimitBreak = null), "Kitasan Black", "Fire at My Heels", 3), "an unknown intent LB is not decisive")
        }
    }

    @Nested
    @DisplayName("intent parsing")
    inner class Parsing {
        @Test
        fun `a well-formed intent parses with every field`() {
            val json = """
                {
                  "schema": "deck_lab_smart_borrow_intent",
                  "schema_version": 1,
                  "target_profile": "Medium",
                  "source_borrow_scan_id": "bp-live",
                  "support_card_id": 30001,
                  "canonical_character": "Kitasan Black",
                  "canonical_title": "Fire at My Heels",
                  "display_name": "Kitasan Black [Fire at My Heels]",
                  "rarity": "SSR",
                  "expected_level": 50,
                  "expected_limit_break": 4,
                  "source_alias": "owner-deadbeef",
                  "resolution_path": "EXACT_TITLE",
                  "warnings": [],
                  "recommendation_evidence_digest": "djb2-1234abcd"
                }
            """.trimIndent()
            val parsed = parseSmartBorrowIntent(json)
            assertEquals(30001, parsed?.supportCardId)
            assertEquals("Kitasan Black", parsed?.canonicalCharacter)
            assertEquals(4, parsed?.expectedLimitBreak)
            assertEquals("owner-deadbeef", parsed?.sourceAlias)
        }

        @Test
        fun `an unobserved limit break parses as null, not zero`() {
            val json = """{"schema":"deck_lab_smart_borrow_intent","support_card_id":30001,"canonical_character":"Kitasan Black","canonical_title":"Fire at My Heels","expected_limit_break":null}"""
            assertNull(parseSmartBorrowIntent(json)?.expectedLimitBreak)
        }

        @Test
        fun `the wrong schema fails closed to null`() {
            assertNull(parseSmartBorrowIntent("""{"schema":"something_else","support_card_id":1,"canonical_character":"x"}"""))
        }

        @Test
        fun `a missing load-bearing field fails closed to null`() {
            assertNull(parseSmartBorrowIntent("""{"schema":"deck_lab_smart_borrow_intent","canonical_character":"Kitasan Black"}"""))
            assertNull(parseSmartBorrowIntent("""{"schema":"deck_lab_smart_borrow_intent","support_card_id":30001}"""))
        }

        @Test
        fun `malformed json fails closed to null`() {
            assertNull(parseSmartBorrowIntent("not json"))
            assertNull(parseSmartBorrowIntent(""))
        }
    }
}

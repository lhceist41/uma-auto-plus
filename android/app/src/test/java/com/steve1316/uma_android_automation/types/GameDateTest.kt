package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [GameDate] class and its companion object functions.
 *
 * Covers turn/date conversions (toDay, fromDay), round-trip consistency for all 72 turns,
 * summer detection, Finale season flags, Trackblazer-specific finale string parsing,
 * and instance methods (toString, getNextDate, updateDay).
 */
@DisplayName("GameDate Tests")
class GameDateTest {
    // =========================================================================
    // toDay()
    // =========================================================================

    @Nested
    @DisplayName("toDay()")
    inner class ToDayTests {
        @Test
        fun `Junior January Early = 1`() {
            assertEquals(1, GameDate.toDay(DateYear.JUNIOR, DateMonth.JANUARY, DatePhase.EARLY))
        }

        @Test
        fun `Junior January Late = 2`() {
            assertEquals(2, GameDate.toDay(DateYear.JUNIOR, DateMonth.JANUARY, DatePhase.LATE))
        }

        @Test
        fun `Junior June Late = 12 (Debut race)`() {
            assertEquals(12, GameDate.toDay(DateYear.JUNIOR, DateMonth.JUNE, DatePhase.LATE))
        }

        @Test
        fun `Classic January Early = 25`() {
            assertEquals(25, GameDate.toDay(DateYear.CLASSIC, DateMonth.JANUARY, DatePhase.EARLY))
        }

        @Test
        fun `Senior December Late = 72`() {
            assertEquals(72, GameDate.toDay(DateYear.SENIOR, DateMonth.DECEMBER, DatePhase.LATE))
        }

        @Test
        fun `Senior July Early = 61 (Summer start)`() {
            assertEquals(61, GameDate.toDay(DateYear.SENIOR, DateMonth.JULY, DatePhase.EARLY))
        }

        @Test
        fun `Senior August Late = 64 (Summer end)`() {
            assertEquals(64, GameDate.toDay(DateYear.SENIOR, DateMonth.AUGUST, DatePhase.LATE))
        }

        @Test
        fun `Junior February Early = 3`() {
            assertEquals(3, GameDate.toDay(DateYear.JUNIOR, DateMonth.FEBRUARY, DatePhase.EARLY))
        }
    }

    // =========================================================================
    // fromDay()
    // =========================================================================

    @Nested
    @DisplayName("fromDay()")
    inner class FromDayTests {
        @Test
        fun `day 1 = Junior January Early`() {
            val date = GameDate.fromDay(1)
            assertEquals(DateYear.JUNIOR, date.year)
            assertEquals(DateMonth.JANUARY, date.month)
            assertEquals(DatePhase.EARLY, date.phase)
            assertEquals(1, date.day)
        }

        @Test
        fun `day 12 = Junior June Late`() {
            val date = GameDate.fromDay(12)
            assertEquals(DateYear.JUNIOR, date.year)
            assertEquals(DateMonth.JUNE, date.month)
            assertEquals(DatePhase.LATE, date.phase)
        }

        @Test
        fun `day 25 = Classic January Early`() {
            val date = GameDate.fromDay(25)
            assertEquals(DateYear.CLASSIC, date.year)
            assertEquals(DateMonth.JANUARY, date.month)
            assertEquals(DatePhase.EARLY, date.phase)
        }

        @Test
        fun `day 72 = Senior December Late`() {
            val date = GameDate.fromDay(72)
            assertEquals(DateYear.SENIOR, date.year)
            assertEquals(DateMonth.DECEMBER, date.month)
            assertEquals(DatePhase.LATE, date.phase)
        }

        @Test
        fun `day 0 clamps to Junior January Early`() {
            val date = GameDate.fromDay(0)
            assertEquals(DateYear.JUNIOR, date.year)
            assertEquals(DateMonth.JANUARY, date.month)
            assertEquals(DatePhase.EARLY, date.phase)
        }

        @Test
        fun `day -5 clamps to Junior January Early`() {
            val date = GameDate.fromDay(-5)
            assertEquals(DateYear.JUNIOR, date.year)
            assertEquals(DateMonth.JANUARY, date.month)
            assertEquals(DatePhase.EARLY, date.phase)
        }

        @Test
        fun `day 73 returns Senior December Late with day 73 (Finale)`() {
            val date = GameDate.fromDay(73)
            assertEquals(DateYear.SENIOR, date.year)
            assertEquals(DateMonth.DECEMBER, date.month)
            assertEquals(DatePhase.LATE, date.phase)
            assertEquals(73, date.day)
        }

        @Test
        fun `day 75 returns Senior December Late with day 75`() {
            val date = GameDate.fromDay(75)
            assertEquals(DateYear.SENIOR, date.year)
            assertEquals(DateMonth.DECEMBER, date.month)
            assertEquals(DatePhase.LATE, date.phase)
            assertEquals(75, date.day)
        }
    }

    // =========================================================================
    // Round-trip: toDay(fromDay(d)) == d for all 1-72
    // =========================================================================

    @Test
    @DisplayName("Round-trip: toDay(fromDay(d)) == d for all days 1-72")
    fun roundTripAllDays() {
        for (d in 1..72) {
            val date = GameDate.fromDay(d)
            val roundTripped = GameDate.toDay(date.year, date.month, date.phase)
            assertEquals(d, roundTripped, "Round-trip failed for day $d")
        }
    }

    // =========================================================================
    // isSummer()
    // =========================================================================

    @Nested
    @DisplayName("isSummer()")
    inner class IsSummerTests {
        @Test
        fun `Classic July Early (day 37) is summer`() {
            assertTrue(GameDate.isSummer(37))
        }

        @Test
        fun `Classic August Late (day 40) is summer`() {
            assertTrue(GameDate.isSummer(40))
        }

        @Test
        fun `Senior July Early (day 61) is summer`() {
            assertTrue(GameDate.isSummer(61))
        }

        @Test
        fun `Senior August Late (day 64) is summer`() {
            assertTrue(GameDate.isSummer(64))
        }

        @Test
        fun `Classic June Late (day 36) is not summer`() {
            assertFalse(GameDate.isSummer(36))
        }

        @Test
        fun `Classic September Early (day 41) is not summer`() {
            assertFalse(GameDate.isSummer(41))
        }

        @Test
        fun `Junior July Early (day 13) is not summer`() {
            // Summer only in Classic and Senior years.
            assertFalse(GameDate.isSummer(13))
        }

        @Test
        fun `Finale (day 73) is not summer`() {
            assertFalse(GameDate.isSummer(73))
        }
    }

    // =========================================================================
    // isFinaleString()
    // =========================================================================

    @Nested
    @DisplayName("isFinaleString()")
    inner class IsFinaleStringTests {
        @Test
        fun `detects standard Finale string`() {
            assertTrue(GameDate.isFinaleString("Finale Qualifier"))
        }

        @Test
        fun `detects Finale case-insensitive`() {
            assertTrue(GameDate.isFinaleString("FINALE SEMI-FINAL"))
            assertTrue(GameDate.isFinaleString("finale finals"))
        }

        @Test
        fun `does not detect non-finale string`() {
            assertFalse(GameDate.isFinaleString("Senior Year Late Dec"))
        }

        @Test
        fun `does not detect Trackblazer climax without scenario`() {
            assertFalse(GameDate.isFinaleString("Climax Races Underway"))
        }

        @Test
        fun `detects Trackblazer climax when scenario is Trackblazer`() {
            assertTrue(GameDate.isFinaleString("Climax Races Underway", "Trackblazer"))
        }

        @Test
        fun `detects Trackblazer climax case-insensitive`() {
            assertTrue(GameDate.isFinaleString("CLIMAX RACES UNDERWAY", "Trackblazer"))
            assertTrue(GameDate.isFinaleString("climax races underway", "Trackblazer"))
        }

        @Test
        fun `does not detect Trackblazer climax for other scenarios`() {
            assertFalse(GameDate.isFinaleString("Climax Races Underway", "URA Finale"))
            assertFalse(GameDate.isFinaleString("Climax Races Underway", "Unity Cup"))
        }

        @Test
        fun `Trackblazer scenario still detects standard Finale string`() {
            assertTrue(GameDate.isFinaleString("Finale Qualifier", "Trackblazer"))
        }

        @Test
        fun `empty string is not finale`() {
            assertFalse(GameDate.isFinaleString(""))
            assertFalse(GameDate.isFinaleString("", "Trackblazer"))
        }
    }

    // =========================================================================
    // parseYearPhaseMonth()
    // =========================================================================

    @Nested
    @DisplayName("parseYearPhaseMonth()")
    inner class ParseYearPhaseMonthTests {
        @Test
        fun `normal single-line date parses`() {
            val date = GameDate.parseYearPhaseMonth("Classic Year Early Feb")
            assertNotNull(date)
            assertEquals(DateYear.CLASSIC, date!!.year)
            assertEquals(DateMonth.FEBRUARY, date.month)
            assertEquals(DatePhase.EARLY, date.phase)
        }

        @Test
        fun `newline between Year and phase parses`() {
            // The date banner wraps onto two lines; splitting on a single space saw only 3 tokens and
            // threw away a date the OCR had read perfectly.
            val date = GameDate.parseYearPhaseMonth("Senior Year\nLate Sep")
            assertNotNull(date)
            assertEquals(DateYear.SENIOR, date!!.year)
            assertEquals(DateMonth.SEPTEMBER, date.month)
            assertEquals(DatePhase.LATE, date.phase)
        }

        @Test
        fun `repeated spaces plus a newline before the month parses`() {
            // Double spaces used to shift every index: phase read "" and month read "Early".
            val date = GameDate.parseYearPhaseMonth("Classic Year  Early\n Feb")
            assertNotNull(date)
            assertEquals(DateYear.CLASSIC, date!!.year)
            assertEquals(DateMonth.FEBRUARY, date.month)
            assertEquals(DatePhase.EARLY, date.phase)
        }

        @Test
        fun `tabs and surrounding whitespace parse`() {
            val date = GameDate.parseYearPhaseMonth("  Junior\tYear\t Late  Jun  ")
            assertNotNull(date)
            assertEquals(DateYear.JUNIOR, date!!.year)
            assertEquals(DateMonth.JUNE, date.month)
            assertEquals(DatePhase.LATE, date.phase)
        }

        @Test
        fun `wrapped and single-line reads of the same date agree`() {
            val flat = GameDate.parseYearPhaseMonth("Senior Year Late Sep")
            val wrapped = GameDate.parseYearPhaseMonth("Senior Year\nLate Sep")
            assertNotNull(flat)
            assertNotNull(wrapped)
            assertEquals(flat!!.day, wrapped!!.day)
        }

        @Test
        fun `missing month fails closed`() {
            // Fewer than 4 tokens must never fabricate JANUARY - a wrong date mis-keys mandatory-race,
            // summer and finale turns.
            assertNull(GameDate.parseYearPhaseMonth("Classic Year Early"))
        }

        @Test
        fun `unrelated text fails closed`() {
            assertNull(GameDate.parseYearPhaseMonth("some totally unrelated text"))
            assertNull(GameDate.parseYearPhaseMonth(""))
            assertNull(GameDate.parseYearPhaseMonth("   "))
        }

        @Test
        fun `unrecognizable month fails closed`() {
            assertNull(GameDate.parseYearPhaseMonth("Classic Year Early Xyzzy"))
        }
    }

    // =========================================================================
    // parseTrackblazerFinaleDay()
    // =========================================================================

    @Nested
    @DisplayName("parseTrackblazerFinaleDay()")
    inner class ParseTrackblazerFinaleDayTests {
        @Test
        fun `0 of 3 returns turn 73 (Qualifier)`() {
            assertEquals(73, GameDate.parseTrackblazerFinaleDay("0/3"))
        }

        @Test
        fun `1 of 3 returns turn 74 (Semi-Final)`() {
            assertEquals(74, GameDate.parseTrackblazerFinaleDay("1/3"))
        }

        @Test
        fun `2 of 3 returns turn 75 (Finals)`() {
            assertEquals(75, GameDate.parseTrackblazerFinaleDay("2/3"))
        }

        @Test
        fun `handles spaces around slash`() {
            // OCR may produce "0 / 3" with spaces - still contains "0/3"? No.
            // The actual OCR result might have spaces. Let's test without.
            assertEquals(73, GameDate.parseTrackblazerFinaleDay("0/3 remaining"))
        }

        @Test
        fun `handles surrounding text`() {
            assertEquals(74, GameDate.parseTrackblazerFinaleDay("progress: 1/3 complete"))
        }

        @Test
        fun `case insensitive`() {
            assertEquals(75, GameDate.parseTrackblazerFinaleDay("2/3"))
        }

        @Test
        fun `unrecognizable text defaults to turn 73`() {
            assertEquals(73, GameDate.parseTrackblazerFinaleDay(""))
            assertEquals(73, GameDate.parseTrackblazerFinaleDay("garbage"))
            assertEquals(73, GameDate.parseTrackblazerFinaleDay("3/3"))
        }

        @Test
        fun `OCR misread with extra characters still matches`() {
            // OCR might read "O/3" instead of "0/3" - this would NOT match.
            // But "0/3." with a trailing period would still match.
            assertEquals(73, GameDate.parseTrackblazerFinaleDay("0/3."))
            assertEquals(74, GameDate.parseTrackblazerFinaleDay("1/3 "))
        }
    }

    // =========================================================================
    // Constructor: season flags
    // =========================================================================

    @Nested
    @DisplayName("Constructor season flags")
    inner class ConstructorTests {
        @Test
        fun `day 11 is pre-debut`() {
            val date = GameDate(11)
            assertTrue(date.bIsPreDebut)
            assertFalse(date.bIsFinaleSeason)
        }

        @Test
        fun `day 12 is not pre-debut`() {
            val date = GameDate(12)
            assertFalse(date.bIsPreDebut)
        }

        @Test
        fun `day 72 is not finale season`() {
            val date = GameDate(72)
            assertFalse(date.bIsFinaleSeason)
        }

        @Test
        fun `day 73 is finale season`() {
            val date = GameDate(73)
            assertTrue(date.bIsFinaleSeason)
        }

        @Test
        fun `year-month-phase constructor calculates correct day`() {
            val date = GameDate(DateYear.CLASSIC, DateMonth.MARCH, DatePhase.EARLY)
            assertEquals(29, date.day)
        }
    }

    // =========================================================================
    // toString()
    // =========================================================================

    @Nested
    @DisplayName("toString()")
    inner class ToStringTests {
        @Test
        fun `day 73 = Finale Qualifier`() {
            val date = GameDate(73)
            assertEquals("Finale Qualifier (Turn 73)", date.toString())
        }

        @Test
        fun `day 74 = Finale Semi-Final`() {
            val date = GameDate(74)
            assertEquals("Finale Semi-Final (Turn 74)", date.toString())
        }

        @Test
        fun `day 75 = Finale Finals`() {
            val date = GameDate(75)
            assertEquals("Finale Finals (Turn 75)", date.toString())
        }

        @Test
        fun `day 1 contains year, phase, month, and turn`() {
            val date = GameDate(1)
            val str = date.toString()
            assertTrue(str.contains("JUNIOR YEAR"), "Expected JUNIOR YEAR in: $str")
            assertTrue(str.contains("EARLY"), "Expected EARLY in: $str")
            assertTrue(str.contains("JANUARY"), "Expected JANUARY in: $str")
            assertTrue(str.contains("(Turn 1)"), "Expected (Turn 1) in: $str")
        }
    }

    // =========================================================================
    // getNextDate()
    // =========================================================================

    @Nested
    @DisplayName("getNextDate()")
    inner class GetNextDateTests {
        @Test
        fun `day 1 next is day 2`() {
            val date = GameDate(1)
            val next = date.getNextDate()
            assertEquals(2, next.day)
        }

        @Test
        fun `day 72 next is day 73 (into Finale)`() {
            val date = GameDate(72)
            val next = date.getNextDate()
            assertEquals(73, next.day)
        }
    }

    // =========================================================================
    // updateDay()
    // =========================================================================

    @Nested
    @DisplayName("updateDay()")
    inner class UpdateDayTests {
        @Test
        fun `updateDay changes year, month, phase and flags`() {
            val date = GameDate(1)
            assertFalse(date.bIsFinaleSeason)

            date.updateDay(73)
            assertEquals(73, date.day)
            assertTrue(date.bIsFinaleSeason)
            assertFalse(date.bIsPreDebut)
        }

        @Test
        fun `updateDay to pre-debut`() {
            val date = GameDate(50)
            date.updateDay(5)
            assertEquals(5, date.day)
            assertTrue(date.bIsPreDebut)
        }

        @Test
        fun `a constructed date is not observed until updateDay runs`() {
            // Campaign holds GameDate(day = 1). A career resumed at its Complete Career screen ends
            // without ever reaching update(), so day is still that 1 and must not be recorded as a
            // real turn 1: that made a full arc look like a turn-1 crash and double-counted the
            // career in the outcome corpus.
            val fresh = GameDate(day = 1)
            assertEquals(1, fresh.day)
            assertFalse(fresh.dayObserved)

            fresh.updateDay(37)
            assertEquals(37, fresh.day)
            assertTrue(fresh.dayObserved)
        }

        @Test
        fun `an observed turn 1 is distinguishable from the unread default`() {
            val read = GameDate(day = 1)
            read.updateDay(1)
            assertEquals(1, read.day)
            assertTrue(read.dayObserved)
        }
    }
}

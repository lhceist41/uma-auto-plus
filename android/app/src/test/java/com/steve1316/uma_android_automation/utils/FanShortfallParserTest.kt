package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CustomImageUtils.parseFanShortfall] - the goal-criteria line parser behind the
 * entry-criteria arm of the fan emergency.
 *
 * The criteria line renders as "Criteria: 1,223 fan(s) to go" while unmet and "Entry criteria met!"
 * once satisfied. OCR regularly mangles the "(s)" suffix, so the parser requires only a number
 * followed by the "fan" stem; met-text and unrelated reads must parse to null so the emergency
 * never arms on a satisfied or absent criteria.
 */
@DisplayName("parseFanShortfall Tests")
class FanShortfallParserTest {
    @Test
    @DisplayName("Clean criteria line with thousands separator parses to the shortfall")
    fun `clean criteria line parses`() {
        assertEquals(1223, CustomImageUtils.parseFanShortfall("Criteria: 1,223 fan(s) to go"))
    }

    @Test
    @DisplayName("OCR-mangled fan(s) suffix still parses")
    fun `mangled suffix parses`() {
        assertEquals(1223, CustomImageUtils.parseFanShortfall("1223 fanls) to go"))
        assertEquals(4500, CustomImageUtils.parseFanShortfall("4,500 fans to go"))
    }

    @Test
    @DisplayName("Criteria-met text parses to null")
    fun `criteria met is null`() {
        assertNull(CustomImageUtils.parseFanShortfall("Entry criteria met!"))
    }

    @Test
    @DisplayName("Blank and unrelated reads parse to null")
    fun `unrelated text is null`() {
        assertNull(CustomImageUtils.parseFanShortfall(""))
        assertNull(CustomImageUtils.parseFanShortfall("Place top 5 in Satsuki Sho"))
        assertNull(CustomImageUtils.parseFanShortfall("fan(s) to go"))
    }

    @Test
    @DisplayName("A fan token with no preceding number parses to null")
    fun `number required before fan token`() {
        assertNull(CustomImageUtils.parseFanShortfall("Japan Cup"))
        assertNull(CustomImageUtils.parseFanShortfall("fans"))
    }
}

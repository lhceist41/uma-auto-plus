package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure scrollbar shape gate. The OpenCV colour masking in `detectScrollBar` needs native OpenCV and is validated on-device, but the tall-and-narrow geometry rule that rejects
 * bright non-scrollbar shapes once hue and saturation are left unconstrained is pure and covered here. The gate is what makes the wide-open colour windows safe: without it, a pale badge or a
 * rounded card corner that happens to fall in the value band could be crowned the largest contour and returned as the bar.
 */
@DisplayName("Scrollbar shape gate")
class ScrollBarShapeTest {
    @Test
    @DisplayName("The real rail and thumb pass: they are far taller than they are wide")
    fun testRealScrollBarGeometryPasses() {
        // Measured on the reference device.
        assertTrue(isPlausibleScrollBarShape(width = 10, height = 607), "The rail (10x607) is a scrollbar sliver")
        assertTrue(isPlausibleScrollBarShape(width = 10, height = 425), "The thumb (10x425) is a scrollbar sliver")
    }

    @Test
    @DisplayName("Bright non-scrollbar shapes are rejected on geometry alone")
    fun testBrightLookAlikesRejected() {
        assertFalse(isPlausibleScrollBarShape(width = 120, height = 40), "A wide, short pale badge is not a scrollbar")
        assertFalse(isPlausibleScrollBarShape(width = 48, height = 48), "A square bright icon is not a scrollbar")
        assertFalse(isPlausibleScrollBarShape(width = 30, height = 20), "A rounded card corner blob is not a scrollbar")
    }

    @Test
    @DisplayName("The gate is strictly taller-than-twice-wide, so a 2:1 shape does not qualify")
    fun testBoundaryIsStrict() {
        assertFalse(isPlausibleScrollBarShape(width = 10, height = 20), "Exactly 2:1 is not tall enough")
        assertTrue(isPlausibleScrollBarShape(width = 10, height = 21), "Just past 2:1 qualifies")
    }
}

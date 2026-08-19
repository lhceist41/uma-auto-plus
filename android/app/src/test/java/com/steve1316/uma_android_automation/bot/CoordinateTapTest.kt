package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Fixed-coordinate tap jitter parity.
 *
 * Intentional coordinate taps used to pass a descriptive label with no backing asset, so the
 * automation library logged a missing-asset error and fell back to a 25x25 jitter region on every
 * tap. [CoordinateTap] reproduces that jitter locally (then taps with imageName=null, no asset
 * lookup), so the ONLY acceptance risk is where a tap can physically land. These tests pin the
 * effective landing envelope to the decompiled library behavior.
 *
 * Decompiled library math (MyAccessibilityService.randomizeTapLocation, region 25): each axis is
 * `(coord - region / 2).toInt() + ((region * 0.25).toInt() .. (region * 0.75).toInt()).random()`
 * = `(coord - 12) + [6, 18]` = a uniform integer in `[coord - 6, coord + 6]`.
 */
@DisplayName("Fixed-coordinate tap jitter")
class CoordinateTapTest {
    private companion object {
        const val X = 175.0
        const val Y = 1585.0
        const val HALF_ENVELOPE = 6
    }

    @Nested
    @DisplayName("region default")
    inner class RegionDefault {
        @Test
        fun `default region is the library's 25x25 fallback`() {
            assertEquals(25, CoordinateTap.REGION)
        }
    }

    @Nested
    @DisplayName("bounds")
    inner class Bounds {
        @Test
        fun `every sample lands within the plus or minus 6 envelope on both axes`() {
            val rng = Random(42)
            repeat(5000) {
                val (jx, jy) = CoordinateTap.jitter(X, Y, rng = rng)
                assertTrue(jx in (X.toInt() - HALF_ENVELOPE)..(X.toInt() + HALF_ENVELOPE), "jx=$jx escaped x envelope")
                assertTrue(jy in (Y.toInt() - HALF_ENVELOPE)..(Y.toInt() + HALF_ENVELOPE), "jy=$jy escaped y envelope")
            }
        }

        @Test
        fun `the full envelope is reachable and jitter actually varies`() {
            val rng = Random(7)
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            repeat(5000) {
                val (jx, jy) = CoordinateTap.jitter(X, Y, rng = rng)
                minX = minOf(minX, jx)
                maxX = maxOf(maxX, jx)
                minY = minOf(minY, jy)
                maxY = maxOf(maxY, jy)
            }
            // Parity with the library: extremes are exactly coord +/- 6, and both extremes occur (varies).
            assertEquals(X.toInt() - HALF_ENVELOPE, minX)
            assertEquals(X.toInt() + HALF_ENVELOPE, maxX)
            assertEquals(Y.toInt() - HALF_ENVELOPE, minY)
            assertEquals(Y.toInt() + HALF_ENVELOPE, maxY)
            assertTrue(maxX > minX, "x jitter did not vary")
            assertTrue(maxY > minY, "y jitter did not vary")
        }
    }

    @Nested
    @DisplayName("library formula parity")
    inner class Parity {
        // Independent re-implementation of the decompiled library formula for an exact per-draw match.
        private fun libraryAxis(coord: Double, region: Int, rng: Random): Int {
            val low = (region * 0.25).toInt()
            val high = (region * 0.75).toInt()
            return (coord - region / 2).toInt() + (low..high).random(rng)
        }

        @Test
        fun `jitter matches the library formula draw-for-draw under a shared seed`() {
            // Same seed + same call order => identical draw sequence; the helper must not diverge.
            val a = Random(12345)
            val b = Random(12345)
            repeat(1000) {
                val (jx, jy) = CoordinateTap.jitter(X, Y, rng = a)
                val expX = libraryAxis(X, CoordinateTap.REGION, b)
                val expY = libraryAxis(Y, CoordinateTap.REGION, b)
                assertEquals(expX, jx)
                assertEquals(expY, jy)
            }
        }

        @Test
        fun `parity holds across a spread of coordinates`() {
            for (cx in listOf(0.0, 1.0, 12.0, 13.0, 540.0, 175.4, 1584.9)) {
                val a = Random(99)
                val b = Random(99)
                val (jx, _) = CoordinateTap.jitter(cx, cx, rng = a)
                val exp = libraryAxis(cx, CoordinateTap.REGION, b)
                assertEquals(exp, jx, "mismatch at coord=$cx")
            }
        }
    }
}

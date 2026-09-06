package com.steve1316.uma_android_automation.components

import com.steve1316.automation_library.data.SharedData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

private const val SKIP_OFF_TEMPLATE_WIDTH = 244
private const val SKIP_OFF_TEMPLATE_HEIGHT = 84
private const val SKIP_ON_TEMPLATE_WIDTH = 242
private const val SKIP_ON_TEMPLATE_HEIGHT = 81

/** True if a template of the given footprint, placed at (originX, originY), fits entirely inside [region]. */
private fun regionContainsFootprint(region: IntArray, originX: Int, originY: Int, footprintWidth: Int = SKIP_OFF_TEMPLATE_WIDTH, footprintHeight: Int = SKIP_OFF_TEMPLATE_HEIGHT): Boolean {
    val (x, y, w, h) = region
    return originX >= x && originY >= y && originX + footprintWidth <= x + w && originY + footprintHeight <= y + h
}

/**
 * The spatially constrained region [ButtonSkipOff] searches within, which replaced [Region.bottomHalf]
 * to close a false-positive match against the launch Quick Mode dialog's "Quick" button (see
 * `docs-local/validation/skip-chevron/2026-09-05-independent-review-prototype-revision-2.md`).
 *
 * [persistentSkipPillRegion] is exercised directly rather than through [Region.persistentSkipPill]
 * where more than one resolution is needed: [Region] is a Kotlin object, so its `val` fields freeze
 * to whatever [SharedData] holds at first JVM access and cannot be recomputed for a second resolution
 * within the same test run.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Persistent Skip pill region")
class PersistentSkipPillRegionTest {
    @BeforeAll
    fun setDisplayMetrics() {
        // Region.persistentSkipPill (used by the ButtonSkipOff wiring test below) freezes on first
        // access, so this must run before anything in this process has touched Region.
        SharedData.displayWidth = 1080
        SharedData.displayHeight = 1920
    }

    @Nested
    @DisplayName("footprint containment at the 1080x1920 baseline")
    inner class BaselineFootprint {
        private val region = persistentSkipPillRegion(1080, 1920)

        @Test
        @DisplayName("the observed Off pill origin (265, 1831) is inside")
        fun observedOffOriginInside() {
            assertTrue(regionContainsFootprint(region, 265, 1831))
        }

        @Test
        @DisplayName("the Quick button's origin (506, 1831) is outside")
        fun quickButtonOriginOutside() {
            assertFalse(regionContainsFootprint(region, 506, 1831))
        }

        @Test
        @DisplayName("x boundary: 203 and 321 accept, 202 and 322 reject")
        fun xBoundary() {
            assertTrue(regionContainsFootprint(region, 203, 1831))
            assertTrue(regionContainsFootprint(region, 321, 1831))
            assertFalse(regionContainsFootprint(region, 202, 1831))
            assertFalse(regionContainsFootprint(region, 322, 1831))
        }

        @Test
        @DisplayName("y boundary: 1771 and 1836 accept, 1770 and 1837 reject")
        fun yBoundary() {
            assertTrue(regionContainsFootprint(region, 265, 1771))
            assertTrue(regionContainsFootprint(region, 265, 1836))
            assertFalse(regionContainsFootprint(region, 265, 1770))
            assertFalse(regionContainsFootprint(region, 265, 1837))
        }

        @Test
        @DisplayName("the observed one- and two-chevron On pill origin (263, 1831) is inside")
        fun observedOnOriginInside() {
            assertTrue(regionContainsFootprint(region, 263, 1831, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
        }

        @Test
        @DisplayName("the Quick button's On-sized footprint at (504, 1831) is outside")
        fun quickButtonOnFootprintOutside() {
            assertFalse(regionContainsFootprint(region, 504, 1831, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
        }

        @Test
        @DisplayName("On x boundary: 203 and 323 accept, 202 and 324 reject")
        fun onXBoundary() {
            assertTrue(regionContainsFootprint(region, 203, 1831, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
            assertTrue(regionContainsFootprint(region, 323, 1831, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
            assertFalse(regionContainsFootprint(region, 202, 1831, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
            assertFalse(regionContainsFootprint(region, 324, 1831, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
        }

        @Test
        @DisplayName("On y boundary: 1771 and 1839 accept, 1770 and 1840 reject")
        fun onYBoundary() {
            assertTrue(regionContainsFootprint(region, 263, 1771, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
            assertTrue(regionContainsFootprint(region, 263, 1839, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
            assertFalse(regionContainsFootprint(region, 263, 1770, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
            assertFalse(regionContainsFootprint(region, 263, 1840, SKIP_ON_TEMPLATE_WIDTH, SKIP_ON_TEMPLATE_HEIGHT))
        }
    }

    @Nested
    @DisplayName("screen-bounds and template-fit invariants")
    inner class Invariants {
        @Test
        @DisplayName("the region never runs past the bottom of the screen, at baseline and a scaled resolution")
        fun clampedToScreen() {
            for ((displayWidth, displayHeight) in listOf(1080 to 1920, 1440 to 2560)) {
                val (_, y, _, h) = persistentSkipPillRegion(displayWidth, displayHeight)
                assertTrue(y + h <= displayHeight, "y=$y h=$h displayHeight=$displayHeight")
            }
        }

        @Test
        @DisplayName("the region is always large enough for the full skip_off template, at baseline and a scaled resolution")
        fun fitsTemplate() {
            for ((displayWidth, displayHeight) in listOf(1080 to 1920, 1440 to 2560)) {
                val (_, _, w, h) = persistentSkipPillRegion(displayWidth, displayHeight)
                assertTrue(w >= SKIP_OFF_TEMPLATE_WIDTH, "w=$w at ${displayWidth}x$displayHeight")
                assertTrue(h >= SKIP_OFF_TEMPLATE_HEIGHT, "h=$h at ${displayWidth}x$displayHeight")
            }
        }

        @Test
        @DisplayName("the region is always large enough for the full skip_on template, at baseline and a scaled resolution")
        fun fitsOnTemplate() {
            for ((displayWidth, displayHeight) in listOf(1080 to 1920, 1440 to 2560)) {
                val (_, _, w, h) = persistentSkipPillRegion(displayWidth, displayHeight)
                assertTrue(w >= SKIP_ON_TEMPLATE_WIDTH, "w=$w at ${displayWidth}x$displayHeight")
                assertTrue(h >= SKIP_ON_TEMPLATE_HEIGHT, "h=$h at ${displayWidth}x$displayHeight")
            }
        }

        @Test
        @DisplayName("the baseline region is the frozen presence window, clamped to the screen (203, 1771, 362, 149)")
        fun baselineMatchesFrozenWindow() {
            assertEquals(listOf(203, 1771, 362, 149), persistentSkipPillRegion(1080, 1920).toList())
        }
    }

    @Nested
    @DisplayName("component wiring")
    inner class ComponentWiring {
        @Test
        @DisplayName("ButtonSkipOff searches the constrained pill region, not bottomHalf")
        fun buttonSkipOffUsesPillRegion() {
            assertEquals(Region.persistentSkipPill.toList(), ButtonSkipOff.template.region.toList())
        }

        @Test
        @DisplayName("ButtonSkipOn searches the constrained pill region, not bottomHalf")
        fun buttonSkipOnUsesPillRegion() {
            assertEquals(Region.persistentSkipPill.toList(), ButtonSkipOn.template.region.toList())
        }
    }
}

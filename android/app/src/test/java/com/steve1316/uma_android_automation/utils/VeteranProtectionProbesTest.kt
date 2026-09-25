package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Pure classifiers and geometry for the protection probe. Control shapes use sampled native
 * checkbox/radio colours measured on device; blank crops must stay unknown.
 */
@DisplayName("Veteran protection probe classifiers")
class VeteranProtectionProbesTest {
    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Nested
    @DisplayName("neutral filter baseline")
    inner class Baseline {
        private val neutral = VeteranFilterDimension.entries.associateWith { FilterDimensionState.NEUTRAL }

        @Test
        fun `all dimensions must be positively neutral`() {
            assertEquals(FilterBaselineState.NEUTRAL_VERIFIED, filterBaselineState(neutral))
        }

        @Test
        fun `an unrelated active filter rejects both favorite and memo empty proofs`() {
            for (target in listOf(VeteranFilterDimension.FAVORITES, VeteranFilterDimension.MEMO)) {
                val active = neutral + (VeteranFilterDimension.DISTANCE to FilterDimensionState.ACTIVE)
                assertEquals(FilterBaselineState.NOT_NEUTRAL, filterBaselineState(active), "$target cannot use a zero-match reading")
            }
        }

        @Test
        fun `an unreadable or unobserved unrelated filter rejects both proofs`() {
            for (target in listOf(VeteranFilterDimension.FAVORITES, VeteranFilterDimension.MEMO)) {
                assertEquals(FilterBaselineState.UNKNOWN, filterBaselineState(neutral + (VeteranFilterDimension.COMMON_SPARKS to FilterDimensionState.UNKNOWN)), "$target cannot use an unreadable filter")
                assertEquals(FilterBaselineState.UNKNOWN, filterBaselineState(neutral - VeteranFilterDimension.APTITUDE_SPARKS), "$target cannot use a reset tap without full verification")
            }
        }

        @Test
        fun `bottom grid alone never verifies the whole baseline`() {
            val bottom = mapOf(VeteranFilterDimension.FAVORITES to FilterDimensionState.NEUTRAL, VeteranFilterDimension.MEMO to FilterDimensionState.NEUTRAL)
            assertEquals(FilterBaselineState.UNKNOWN, filterBaselineState(bottom))
        }

        @Test
        fun `unknown diagnostics do not hide a separately observed active dimension`() {
            val readings = neutral + (VeteranFilterDimension.ATTRIBUTE_SPARKS to FilterDimensionState.UNKNOWN) +
                (VeteranFilterDimension.COMMON_SPARKS to FilterDimensionState.ACTIVE)
            assertEquals(FilterBaselineState.NOT_NEUTRAL, filterBaselineState(readings))
            assertEquals(FilterBaselineState.UNKNOWN, filterBaselineState(neutral, unexpectedSection = true))
        }

        @Test
        fun `an empty target cannot be inferred through an unrelated active intersection`() {
            for (target in listOf(VeteranFilterDimension.FAVORITES, VeteranFilterDimension.MEMO)) {
                val exact = neutral + (target to FilterDimensionState.ACTIVE)
                assertTrue(exactFilterTargetState(exact, target))
                assertFalse(exactFilterTargetState(exact + (VeteranFilterDimension.DISTANCE to FilterDimensionState.ACTIVE), target))
                assertFalse(exactFilterTargetState(exact + (VeteranFilterDimension.COMMON_SPARKS to FilterDimensionState.ACTIVE), target))
                assertFalse(exactFilterTargetState(exact + (VeteranFilterDimension.APTITUDE_SPARKS to FilterDimensionState.UNKNOWN), target))
                assertFalse(exactFilterTargetState(exact - VeteranFilterDimension.UNIQUE_SPARKS, target))
                assertFalse(exactFilterTargetState(exact, target, unexpectedSection = true))
            }
        }
    }

    @Nested
    @DisplayName("OK/Apply button state")
    inner class ApplyButton {
        @Test
        fun `the bright green fill classifies as enabled`() {
            val sampler = SparkPixelSampler { _, _ -> argb(133, 208, 10) }
            assertEquals(ApplyButtonState.ENABLED, classifyApplyButton(sampler))
        }

        @Test
        fun `the dark olive fill classifies as disabled`() {
            val sampler = SparkPixelSampler { _, _ -> argb(85, 130, 6) }
            assertEquals(ApplyButtonState.DISABLED, classifyApplyButton(sampler))
        }

        @Test
        fun `a green in the dead band between the two returns unknown, never a guess`() {
            // 170 sits in the [155, 185] gap that never occurs on the real button; a reading here means
            // the frame is not the dialog, so the probe must fail closed.
            val sampler = SparkPixelSampler { _, _ -> argb(100, 170, 10) }
            assertEquals(ApplyButtonState.UNKNOWN, classifyApplyButton(sampler))
        }

        @Test
        fun `a blank wrong-location OK crop is unknown rather than disabled`() {
            assertEquals(ApplyButtonState.UNKNOWN, classifyApplyButton(SparkPixelSampler { _, _ -> argb(241, 241, 241) }))
        }
    }

    @Nested
    @DisplayName("filter checkbox state")
    inner class Checkbox {
        private fun nativeBox(selected: Boolean): SparkPixelSampler = SparkPixelSampler { x, y ->
            val dx = x - 106
            val dy = y - 712
            when {
                dy == -35 && dx in -30..30 -> argb(207, 207, 207)
                dy == 35 && dx in -30..30 -> argb(119, 119, 136)
                dx == -35 && dy in -30..30 -> argb(161, 161, 178)
                dx == 35 && dy in -30..30 -> argb(161, 161, 178)
                listOf(-16 to 4, -7 to 13, 18 to -13).any { (tx, ty) -> dx in tx - 4..tx + 4 && dy in ty - 4..ty + 4 } ->
                    if (selected) argb(125, 205, 36) else argb(200, 200, 204)
                else -> argb(241, 241, 241)
            }
        }

        @Test
        fun `a shaped green checkmark classifies active`() {
            assertEquals(FilterControlState.ACTIVE, classifyFilterCheckbox(nativeBox(true), 106, 712))
        }

        @Test
        fun `a shaped grey checkmark classifies neutral`() {
            assertEquals(FilterControlState.NEUTRAL, classifyFilterCheckbox(nativeBox(false), 106, 712))
        }

        @Test
        fun `blank crop and displaced box remain unknown`() {
            assertEquals(FilterControlState.UNKNOWN, classifyFilterCheckbox(SparkPixelSampler { _, _ -> argb(241, 241, 241) }, 106, 712))
            assertEquals(FilterControlState.UNKNOWN, classifyFilterCheckbox(nativeBox(false), 444, 712))
        }
    }

    @Nested
    @DisplayName("filter grade radio state")
    inner class Radio {
        private fun nativeRadio(selected: Boolean): SparkPixelSampler = SparkPixelSampler { x, y ->
            val dx = x - 106
            val dy = y - 640
            when {
                (kotlin.math.abs(dx) == 35 && dy == 0) || (dx == 0 && kotlin.math.abs(dy) == 35) -> argb(130, 130, 130)
                dx in -10..10 && dy in -10..10 -> if (selected) argb(85, 130, 23) else argb(150, 150, 152)
                else -> argb(162, 162, 162)
            }
        }

        @Test
        fun `dimmed selected child still classifies active`() {
            assertEquals(FilterControlState.ACTIVE, classifyFilterRadio(nativeRadio(true), 106, 640))
            assertEquals(FilterControlState.NEUTRAL, classifyFilterRadio(nativeRadio(false), 106, 640))
            assertEquals(FilterControlState.UNKNOWN, classifyFilterRadio(SparkPixelSampler { _, _ -> argb(162, 162, 162) }, 106, 640))
        }
    }

    @Nested
    @DisplayName("dialog title recognition")
    inner class Title {
        @Test
        fun `either word of the title is accepted, and noise is rejected`() {
            assertTrue(isDisplaySettingsTitle("Display Settings"))
            assertTrue(isDisplaySettingsTitle("DISPLAY SETTINGS"))
            assertTrue(isDisplaySettingsTitle("Dispiay Settings")) // OCR mangled the first word; "SETTINGS" still matches
            assertFalse(isDisplaySettingsTitle("Umamusume Details"))
            assertFalse(isDisplaySettingsTitle(""))
        }
    }

    @Nested
    @DisplayName("checkbox grid geometry")
    inner class Geometry {
        @Test
        fun `there are exactly 15 favorite-icon categories plus Not Set`() {
            assertEquals(15, FAVORITE_ICON_CHECKBOXES.size)
            assertEquals(16, ALL_FAVORITE_CHECKBOXES.size)
            assertEquals(FAVORITE_NOT_SET_CHECKBOX, ALL_FAVORITE_CHECKBOXES.first())
        }

        @Test
        fun `every checkbox centre is distinct and Not Set is excluded from the favorited partition`() {
            val all = ALL_FAVORITE_CHECKBOXES + listOf(MEMO_HAS_CHECKBOX, MEMO_NO_CHECKBOX)
            val centres = all.map { it.cx to it.cy }
            assertEquals(centres.size, centres.toSet().size, "no two checkboxes share a centre")
            assertFalse(FAVORITE_ICON_CHECKBOXES.contains(FAVORITE_NOT_SET_CHECKBOX), "favorited partition never includes Not Set")
        }

        @Test
        fun `checkboxes sit on the three measured columns`() {
            val columns = (ALL_FAVORITE_CHECKBOXES + listOf(MEMO_HAS_CHECKBOX, MEMO_NO_CHECKBOX)).map { it.cx }.toSet()
            assertEquals(setOf(106, 444, 782), columns)
        }
    }
}

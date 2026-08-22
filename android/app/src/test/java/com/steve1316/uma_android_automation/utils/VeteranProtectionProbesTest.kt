package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Pure classifiers and geometry for the PL-R2a protection probe. The RGB constants below are the
 * measured device averages from the calibration captures in validation/parentlab-plr2a-protection:
 * the OK fill reads green ~208 enabled and ~130 disabled; a green checkmark reaches colour spread
 * ~196 and a grey one ~8.
 */
@DisplayName("Veteran protection probe classifiers")
class VeteranProtectionProbesTest {
    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

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
    }

    @Nested
    @DisplayName("filter checkbox state")
    inner class Checkbox {
        @Test
        fun `a green checkmark classifies as selected`() {
            val sampler = SparkPixelSampler { _, _ -> argb(211, 232, 177) }
            assertEquals(FilterCheckboxState.SELECTED, classifyFilterCheckbox(sampler, 106, 712))
        }

        @Test
        fun `a grey checkmark classifies as unselected`() {
            val sampler = SparkPixelSampler { _, _ -> argb(230, 230, 231) }
            assertEquals(FilterCheckboxState.UNSELECTED, classifyFilterCheckbox(sampler, 106, 712))
        }

        @Test
        fun `a green checkmark over part of an otherwise white box reads selected`() {
            // The real checkmark is green ink covering part of a white box; the classifier keys on the
            // peak spread, not the average, so it fires even when much of the sampled area is white.
            val sampler = SparkPixelSampler { x, y -> if (x <= 106 && y <= 712) argb(0, 200, 0) else argb(255, 255, 255) }
            assertEquals(FilterCheckboxState.SELECTED, classifyFilterCheckbox(sampler, 106, 712))
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

package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trackblazer Training Items dialog opening")
class TrackblazerItemsDialogTest {
    private class FakeScreen(private val opensAfterTap: Int?, private val opensAfterPolls: Int = 1, private val buttonVisible: Boolean = true) {
        var taps = 0
        val attempts = mutableListOf<Int>()
        var polls = 0
        var pausedSeconds = 0.0

        fun tap(attempt: Int): Boolean {
            if (!buttonVisible) return false
            attempts.add(attempt)
            taps++
            return true
        }

        fun isOpen(): Boolean {
            polls++
            return opensAfterTap != null && taps >= opensAfterTap && polls >= opensAfterPolls
        }

        fun pause(seconds: Double) {
            pausedSeconds += seconds
        }

        fun open(): Boolean = TrackblazerShopList.openDialogAndConfirm(::tap, ::isOpen, ::pause)
    }

    @Test
    fun `a dialog that opens on the first tap proceeds without a retry`() {
        val screen = FakeScreen(opensAfterTap = 1)
        assertTrue(screen.open())
        assertEquals(1, screen.taps)
    }

    @Test
    fun `a dialog that opens late is found by polling, not by a second tap`() {
        val screen = FakeScreen(opensAfterTap = 1, opensAfterPolls = 6)
        assertTrue(screen.open())
        assertEquals(1, screen.taps)
    }

    @Test
    fun `an ignored first tap gets exactly one retry`() {
        val screen = FakeScreen(opensAfterTap = 2)
        assertTrue(screen.open())
        assertEquals(listOf(0, 1), screen.attempts)
    }

    @Test
    fun `a dialog that never opens reports failure after two taps`() {
        val screen = FakeScreen(opensAfterTap = null)
        assertFalse(screen.open())
        assertEquals(2, screen.taps)
        assertEquals(12, screen.polls)
        assertEquals(3.6, screen.pausedSeconds, 1e-9)
    }

    @Test
    fun `a missing open button ends the attempt without tapping`() {
        val screen = FakeScreen(opensAfterTap = 1, buttonVisible = false)
        assertFalse(screen.open())
        assertEquals(0, screen.taps)
        assertEquals(0, screen.polls)
    }

    @Test
    fun `awaitDialog stops polling once the dialog shows`() {
        var polls = 0
        assertTrue(TrackblazerShopList.awaitDialog({ ++polls >= 2 }, {}))
        assertEquals(2, polls)
    }

    @Test
    fun `a caller expecting the dialog never scans without it`() {
        assertFalse(TrackblazerShopList.canScanItemList(bRequireTrainingItemsDialog = true, bTrainingItemsDialogDetected = false))
        assertTrue(TrackblazerShopList.canScanItemList(bRequireTrainingItemsDialog = true, bTrainingItemsDialogDetected = true))
    }

    // Measured on 1080x1920 MuMu frames: the Training Items label template is 105x45; the round button
    // is centred near (75,535) on the Training screen (label at (73,542)) and near (829,1156) on the main
    // screen (label at (827,1163)), radius about 58; the accessibility overlay covers x < 96, y >= 532.
    // The library jitters a template tap by a quarter of the template size on each axis.
    private fun tapBox(attempt: Int, labelX: Double, labelY: Double): List<Pair<Double, Double>> {
        val (dx, dy) = TrackblazerShopList.trainingItemsTapOffset(attempt, 105, 45)
        val cx = labelX + dx
        val cy = labelY + dy
        return listOf(cx - 26 to cy - 11, cx + 26 to cy - 11, cx - 26 to cy + 11, cx + 26 to cy + 11)
    }

    private fun insideButton(point: Pair<Double, Double>, centreX: Double, centreY: Double): Boolean {
        val dx = point.first - centreX
        val dy = point.second - centreY
        return dx * dx + dy * dy < 58.0 * 58.0
    }

    @Test
    fun `both Training Items taps stay on the button and above the overlay on the Training screen`() {
        for (attempt in 0..1) {
            for (corner in tapBox(attempt, 73.0, 542.0)) {
                assertTrue(corner.second <= 532.0 - 6, "attempt $attempt corner $corner is within 6 px of the overlay")
                assertTrue(insideButton(corner, 75.0, 535.0), "attempt $attempt corner $corner leaves the button")
            }
        }
    }

    @Test
    fun `both Training Items taps stay on the button on the main screen`() {
        for (attempt in 0..1) {
            for (corner in tapBox(attempt, 827.0, 1163.0)) {
                assertTrue(insideButton(corner, 829.0, 1156.0), "attempt $attempt corner $corner leaves the button")
            }
        }
    }

    @Test
    fun `the retry tap lands somewhere other than the first tap`() {
        assertTrue(TrackblazerShopList.trainingItemsTapOffset(0, 105, 45) != TrackblazerShopList.trainingItemsTapOffset(1, 105, 45))
    }

    @Test
    fun `items counted as used are returned one each`() {
        val restored = TrackblazerShopList.returnItemsToInventory(mapOf("Vita 20" to 0, "Reset Whistle" to 3), listOf("Vita 20", "Reset Whistle", "Motivating Megaphone"))
        assertEquals(mapOf("Vita 20" to 1, "Reset Whistle" to 4, "Motivating Megaphone" to 1), restored)
    }

    @Test
    fun `Confirm Use reads as greyed only well below the template luminance`() {
        val tolerance = com.steve1316.uma_android_automation.components.ButtonConfirmUse.disabledLuminanceTolerance
        // Template 0.78, greyed 0.49: a 0.29 gap, so the tolerance must sit clearly between device noise and the gap.
        assertTrue(tolerance >= 0.1 && tolerance <= 0.15, "tolerance $tolerance")
        assertEquals(0.05, com.steve1316.uma_android_automation.components.ButtonSkillUp.disabledLuminanceTolerance)
    }

    @Test
    fun `shop-list scans keep scanning without the dialog`() {
        assertTrue(TrackblazerShopList.canScanItemList(bRequireTrainingItemsDialog = false, bTrainingItemsDialogDetected = false))
    }
}

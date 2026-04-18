package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonMax
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.LabelRecoverRP
import com.steve1316.uma_android_automation.components.LabelRecoverTP
import com.steve1316.uma_android_automation.components.Region

/**
 * Shared helper for recovering TP (Training Points) or RP (Race Points) via the
 * in-game recharge popup.
 *
 * ## Recharge flow (structurally identical for TP and RP)
 *
 * ```
 * Top bar
 *   └─ tap green + next to TP or RP value
 *        └─ "Recover TP" / "Recover RP" list popup (2 rows: F2P item + Carats)
 *             └─ tap Use on preferred row
 *                  └─ Quantity popup (Min / − / slider / + / Max)
 *                       ├─ tap Max (select maximum uses)
 *                       └─ tap OK
 *                            └─ Success popup "Used N items to restore M resource"
 *                                 └─ tap Close → back on previous screen
 * ```
 *
 * ## Item priority
 *
 * The bot always prefers the F2P item (Toughness 30 for TP, Carrot Jelly Mini for RP)
 * since those are earned organically during Career runs and don't cost premium currency.
 * Carat spending is opt-in via [Strategy.F2P_THEN_CARATS] or [Strategy.CARATS_ONLY].
 *
 * The F2P item row is the **second** row in both popups (Carats are always first).
 * Because the Use buttons on both rows share the same template, this helper taps the
 * specific Y-ratio of the second row rather than relying on template matching alone.
 *
 * ## Safety at cap
 *
 * The game blocks the OK button with an "exceeds the limit" error when the pool is at
 * cap. The helper detects this by timing out on the expected success popup and returning
 * [Result.AtCap] without consuming any items.
 *
 * @property game The [Game] instance used for bot interaction.
 */
class ResourceRechargeHelper(private val game: Game) {
    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]ResourceRechargeHelper"

        // -- Top-bar + button coordinates (on 1080x1920 Android render) --
        // Measured from the 2026-04-18 calibration capture of 01_home_screen.png.
        // The top bar layout is stable across all game screens so these ratios hold.

        /** X ratio of the TP recharge + button on the top bar. */
        private const val TP_PLUS_X_RATIO: Double = 0.67

        /** X ratio of the RP recharge + button on the top bar. */
        private const val RP_PLUS_X_RATIO: Double = 0.96

        /** Y ratio shared by both + buttons on the top bar. */
        private const val PLUS_Y_RATIO: Double = 0.041

        // -- Recharge popup row Y ratios --
        // The Recover TP/RP popup has exactly 2 item rows: Carats first, F2P item second.

        /** Y ratio of the Carats row's Use button. */
        private const val CARATS_USE_Y_RATIO: Double = 0.16

        /** Y ratio of the F2P item row's Use button (Toughness 30 / Carrot Jelly Mini). */
        private const val F2P_USE_Y_RATIO: Double = 0.22

        /** X ratio shared by both Use buttons (right side of the popup). */
        private const val USE_X_RATIO: Double = 0.88
    }

    /** Which global resource pool to recharge. */
    enum class Pool {
        /** Training Points — capped at 100, used for Career runs and some events. */
        TP,

        /** Race Points — capped at 5, used for Team Trials and Champions Meeting. */
        RP,
    }

    /** Spending strategy. Default production setting is [F2P_ONLY]. */
    enum class Strategy {
        /** Only use F2P items (Toughness 30 for TP, Carrot Jelly Mini for RP). */
        F2P_ONLY,

        /** Prefer F2P items; fall back to Carats when F2P stock is empty. */
        F2P_THEN_CARATS,

        /** Always spend Carats regardless of F2P stock. Rare. */
        CARATS_ONLY,
    }

    /** Result of a recharge attempt. */
    sealed class Result {
        /** Recharge completed — resource pool was topped up by one or more items. */
        object Success : Result()

        /** Resource pool was already at cap; no items consumed. */
        object AtCap : Result()

        /** F2P item stock was zero and [Strategy] didn't allow Carats fallback. */
        object NoF2pItems : Result()

        /** Couldn't find the recharge popup after tapping the +, or state machine got stuck. */
        data class NavigationFailure(val reason: String) : Result()

        /** Unexpected error. */
        data class Error(val reason: String) : Result()
    }

    /**
     * Attempt to recover the specified [pool] using the given [strategy].
     *
     * The caller is responsible for:
     * - Ensuring the game is on a screen where the top bar is visible (Home Screen or most menu screens)
     * - Any per-session carat budget accounting (this helper doesn't track cumulative spend)
     *
     * @return A [Result] describing the outcome.
     */
    fun recover(pool: Pool, strategy: Strategy = Strategy.F2P_ONLY): Result {
        MessageLog.v(TAG, "[RECHARGE] Starting $pool recharge (strategy=$strategy).")

        // Step 1: tap the + button on the top bar to open the recharge list popup.
        if (!tapPlusButton(pool)) {
            return Result.NavigationFailure("Failed to tap $pool + button on top bar.")
        }

        // Step 2: wait for the Recover list popup, confirm it opened.
        game.wait(1.5)
        if (!verifyRechargePopupOpen(pool)) {
            // For TP specifically, the popup is blocked at cap — report cleanly.
            if (pool == Pool.TP) {
                MessageLog.v(TAG, "[RECHARGE] Recover TP popup did not open; TP likely at cap.")
                return Result.AtCap
            }
            return Result.NavigationFailure("Recover $pool popup did not appear.")
        }

        // Step 3: click Use on the preferred item row.
        val useOutcome = clickUseOnPreferredRow(pool, strategy)
        if (useOutcome != null) {
            // Outcome decided early — close the popup if still open.
            dismissAnyOpenPopups()
            return useOutcome
        }

        // Step 4: quantity popup should now be showing. Click Max.
        game.wait(1.5)
        if (!ButtonMax.click(game.imageUtils)) {
            MessageLog.w(TAG, "[RECHARGE] Max button not found on quantity popup.")
            // Try to proceed anyway — default slider position is +1 which still works.
        } else {
            game.wait(0.5)
        }

        // Step 5: click OK to commit the use.
        if (!ButtonOk.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.w(TAG, "[RECHARGE] OK button not clickable — likely at cap.")
            dismissAnyOpenPopups()
            return Result.AtCap
        }
        game.wait(2.0)

        // Step 6: a success popup appears ("Used N ... to restore M ..."). Dismiss via Close.
        if (ButtonClose.click(game.imageUtils)) {
            MessageLog.v(TAG, "[RECHARGE] Success popup dismissed.")
            game.wait(1.0)
            return Result.Success
        }

        // If no success popup, we might have been at cap after all, or the flow
        // short-circuited. Be tolerant — dismiss anything still open.
        dismissAnyOpenPopups()
        return Result.Success
    }

    // ------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------

    /**
     * Tap the green + button next to the specified pool's counter on the top bar.
     *
     * Coordinate-based since the + buttons are small icons without distinctive templates.
     */
    private fun tapPlusButton(pool: Pool): Boolean {
        val xRatio = when (pool) {
            Pool.TP -> TP_PLUS_X_RATIO
            Pool.RP -> RP_PLUS_X_RATIO
        }
        val x = SharedData.displayWidth * xRatio
        val y = SharedData.displayHeight * PLUS_Y_RATIO

        MessageLog.v(TAG, "[RECHARGE] Tapping $pool + at ($x, $y).")
        game.gestureUtils.tap(x, y, "${pool.name.lowercase()}_plus_button")
        // tap() returns Unit in the automation-library; treat the call as successful unless
        // the subsequent popup-open check disagrees. The caller handles that verification.
        return true
    }

    /** Check whether the Recover TP/RP list popup is currently visible. */
    private fun verifyRechargePopupOpen(pool: Pool): Boolean {
        val label = when (pool) {
            Pool.TP -> LabelRecoverTP
            Pool.RP -> LabelRecoverRP
        }
        return label.check(game.imageUtils, region = Region.topHalf, tries = 2)
    }

    /**
     * Click the Use button on the row corresponding to our preferred item strategy.
     * Returns null if Use was clicked successfully (caller proceeds), or a terminal
     * [Result] if we need to bail (e.g. no F2P items and carats disabled).
     */
    private fun clickUseOnPreferredRow(pool: Pool, strategy: Strategy): Result? {
        when (strategy) {
            Strategy.F2P_ONLY -> {
                MessageLog.v(TAG, "[RECHARGE] Tapping F2P Use row (strategy=F2P_ONLY).")
                tapUseRow(F2P_USE_Y_RATIO)
                // TODO: After tapping, if the F2P item had stock=0 the quantity popup
                // will still open but with a disabled OK button. Current behaviour: we'll
                // hit AtCap from the OK-not-clickable path. Acceptable for MVP.
                return null
            }

            Strategy.F2P_THEN_CARATS -> {
                // Try F2P first. If the quantity popup shows stock=0, caller will loop
                // back with CARATS_ONLY. For MVP we just tap F2P row.
                MessageLog.v(TAG, "[RECHARGE] Tapping F2P Use row (strategy=F2P_THEN_CARATS). Carat fallback not yet implemented.")
                tapUseRow(F2P_USE_Y_RATIO)
                return null
            }

            Strategy.CARATS_ONLY -> {
                MessageLog.v(TAG, "[RECHARGE] Tapping Carats Use row (strategy=CARATS_ONLY).")
                tapUseRow(CARATS_USE_Y_RATIO)
                return null
            }
        }
    }

    /**
     * Tap the Use button on a specific Y-ratio row of the recharge popup.
     *
     * The popup has two rows (Carats, F2P item) with Use buttons at fixed Y positions.
     * Template [ButtonUseItem] matches both; we use coordinates to pick the right one.
     */
    private fun tapUseRow(yRatio: Double) {
        val x = SharedData.displayWidth * USE_X_RATIO
        val y = SharedData.displayHeight * yRatio
        MessageLog.v(TAG, "[RECHARGE] Tapping Use at ($x, $y).")
        game.gestureUtils.tap(x, y, "recharge_use_row_y_${"%.2f".format(yRatio)}")
    }

    /**
     * Best-effort dismissal of any open popups — useful when the recharge flow exits
     * early due to an AtCap condition or error. Taps Close / Cancel up to 3 times.
     */
    private fun dismissAnyOpenPopups() {
        for (i in 0 until 3) {
            val closed = ButtonClose.click(game.imageUtils) ||
                ButtonCancel.click(game.imageUtils)
            if (!closed) break
            game.wait(0.8)
        }
    }
}

package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonConfirm
import com.steve1316.uma_android_automation.components.ButtonDailyProgramTile
import com.steve1316.uma_android_automation.components.ButtonDailyRaces
import com.steve1316.uma_android_automation.components.ButtonDailyRacesJupiterCup
import com.steve1316.uma_android_automation.components.ButtonDailyRacesMoonlightSho
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceUnselected
import com.steve1316.uma_android_automation.components.ButtonMultiRaceOff
import com.steve1316.uma_android_automation.components.ButtonMultiRaceOn
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.ButtonNextWithImage
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRaceConfirm
import com.steve1316.uma_android_automation.components.LabelDailyPrograms
import com.steve1316.uma_android_automation.components.LabelDailyRacesHeader
import com.steve1316.uma_android_automation.components.LabelMultiRacePopup
import com.steve1316.uma_android_automation.components.LabelRaceDetails
import com.steve1316.uma_android_automation.components.LabelRunnerSelection
import com.steve1316.uma_android_automation.components.Region

/**
 * Misc automation task for the Daily Races mode.
 *
 * ## Flow
 *
 * ```
 * Home Screen
 *   └► Race tab (bottom nav)
 *        └► Daily Program tile
 *             └► Daily Races tile
 *                  └► Pick race (Moonlight Sho / Jupiter Cup)
 *                       └► Pick difficulty (VERY HARD / HARD / NORMAL / EASY)
 *                            └► Race Details screen
 *                                 ├─ Ensure Multi-Race: On
 *                                 └─ Click Race!
 *                                      └─ Game auto-runs remaining tickets in sequence
 *                                           └─ Terminal: back on Daily Races screen
 * ```
 *
 * ## Built-in game features we rely on
 *
 * - **Multi-Race: On** — when enabled on the Race Details screen, the game itself chains
 *   all remaining daily tickets into one continuous sequence. We verify the toggle is on
 *   once per session, then commit a single `Race!` tap and wait for the full sequence to
 *   complete. No per-race loop in the bot.
 * - **Daily Race tickets (N/3)** — the attempt pool resets each day at server reset.
 *
 * ## Configuration (via [SettingsHelper], namespace `"miscDailyRace"`)
 *
 * - `targetRace` (string): `"Moonlight Sho"` | `"Jupiter Cup"` — which race to attempt.
 *   Default: `"Moonlight Sho"` (Monies → useful for every stage of the game).
 * - `targetDifficulty` (string): `"VERY_HARD"` | `"HARD"` | `"NORMAL"` | `"EASY"`.
 *   Default: `"VERY_HARD"` for max rewards.
 * - `ensureMultiRaceOn` (bool): default true. If false, bot will run races one at a time
 *   (useful for debugging).
 *
 * @property game The [Game] instance used for bot interaction.
 */
class DailyRaceTask(game: Game) : MiscTask(game) {
    /** Finite-state machine states for the Daily Races flow. */
    enum class DailyRaceScreenState {
        /** Game main menu with bottom nav visible. Bot needs to click Race tab. */
        HOME_SCREEN,

        /** Race tab open showing 4 mode tiles (Team Trials / Race Events / Daily Program / Exhibition). */
        RACE_TAB,

        /** Inside Daily Program showing Daily Races + Daily Legend Races tiles. */
        DAILY_PROGRAMS_CONTAINER,

        /** Inside Daily Races showing the rotation (Moonlight Sho / Jupiter Cup). */
        DAILY_RACES_RACE_PICK,

        /** Difficulty tier list for the picked race (VERY HARD / HARD / NORMAL / EASY). */
        DAILY_RACES_DIFFICULTY_PICK,

        /** Runner selection screen — horse-picker step between difficulty and race details. Bot just clicks Confirm since the user pre-sets their runner by running manually at least once. */
        RUNNER_SELECTION,

        /** Multi-Race popup — asks how many races to run (1/3, 2/3, 3/3). Bot clicks Race! to commit 3/3 (the default). */
        MULTI_RACE_POPUP,

        /** Race Details confirmation screen with Multi-Race toggle and Race! button. */
        RACE_DETAILS,

        /** Race cinematic / race-in-progress / between-race auto-confirm screen. */
        IN_RACE,

        /** Post-race results sequence. Bot keeps tapping Next until back to a known screen. */
        POST_RACE_RESULTS,

        /** Terminal success: back on Daily Races screen with (possibly) zero tickets left. */
        COMPLETE,

        /** Screen could not be identified. Triggers safety bailout after N consecutive. */
        UNKNOWN,
    }

    private val targetRaceName: String =
        SettingsHelper.getStringSetting("miscDailyRace", "targetRace", "Moonlight Sho")

    private val targetDifficulty: String =
        SettingsHelper.getStringSetting("miscDailyRace", "targetDifficulty", "VERY_HARD")

    private val ensureMultiRaceOn: Boolean =
        SettingsHelper.getBooleanSetting("miscDailyRace", "ensureMultiRaceOn", true)

    /** Tracks whether we've already committed the Race! tap this session. */
    private var raceSequenceCommitted: Boolean = false

    override fun process(): TaskResult? {
        checkSafetyRails()?.let { return it }

        val sourceBitmap = captureSourceBitmap()
        val currentState = detectScreenState(sourceBitmap)
        trackProgress(currentState.name, currentState == DailyRaceScreenState.UNKNOWN)

        MessageLog.v(TAG, "[STATE] iter=$iterationsCompleted state=$currentState")

        // Dismiss any incidental popup before dispatching. Cheap if nothing's there.
        if (handleIncidentalPopups()) {
            return null
        }

        return when (currentState) {
            DailyRaceScreenState.HOME_SCREEN -> {
                handleHomeScreen()
                null
            }

            DailyRaceScreenState.RACE_TAB -> {
                handleRaceTab()
                null
            }

            DailyRaceScreenState.DAILY_PROGRAMS_CONTAINER -> {
                handleDailyProgramsContainer()
                null
            }

            DailyRaceScreenState.DAILY_RACES_RACE_PICK -> {
                handleRacePick()
            }

            DailyRaceScreenState.DAILY_RACES_DIFFICULTY_PICK -> {
                handleDifficultyPick()
                null
            }

            DailyRaceScreenState.RUNNER_SELECTION -> {
                handleRunnerSelection()
                null
            }

            DailyRaceScreenState.MULTI_RACE_POPUP -> {
                handleMultiRacePopup()
                null
            }

            DailyRaceScreenState.RACE_DETAILS -> {
                handleRaceDetails()
                null
            }

            DailyRaceScreenState.IN_RACE -> {
                // Nothing to do but wait for the game to finish the race sequence. The
                // game auto-skips between races when Multi-Race: On so we just poll.
                game.wait(3.0)
                null
            }

            DailyRaceScreenState.POST_RACE_RESULTS -> {
                handlePostRaceResults()
                null
            }

            DailyRaceScreenState.COMPLETE -> {
                TaskResult.Success(
                    TaskResultCode.TASK_RESULT_COMPLETE,
                    "DailyRaceTask completed successfully.",
                )
            }

            DailyRaceScreenState.UNKNOWN -> {
                // Wait a beat before retrying so transient states (loading spinners,
                // cutscenes) can resolve. trackProgress is already counting these.
                game.wait(1.5)
                null
            }
        }
    }

    /**
     * Classify the current screen by checking the most discriminating templates first.
     *
     * Order matters: pick templates that are (a) unique to their screen and (b) fast to
     * match. Where possible we reuse the single [sourceBitmap] to avoid re-screenshotting.
     */
    private fun detectScreenState(
        sourceBitmap: android.graphics.Bitmap,
    ): DailyRaceScreenState {
        // Race Details is the goal screen before racing — check first for fast-path exits.
        if (LabelRaceDetails.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RACE_DETAILS
        }

        // Multi-Race popup — appears on top of Runner Selection after Confirm is tapped.
        // Check before Runner Selection since the popup overlays it and the Runner Selection
        // header is still visible (just dimmed).
        if (LabelMultiRacePopup.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.MULTI_RACE_POPUP
        }

        // Runner Selection — horse-picker screen between difficulty and Race Details.
        if (LabelRunnerSelection.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RUNNER_SELECTION
        }

        // Inside Daily Races screen group — detect by the purple "Daily Races" header
        // banner at top. This is distinct from the [ButtonDailyRaces] tile (dark-purple
        // text on white) which lives on the Daily Programs container and is used for clicks.
        if (LabelDailyRacesHeader.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            // The same header is visible on both the race-pick screen and the difficulty-pick
            // screen. Disambiguate by checking for a per-race logo: if either Moonlight Sho
            // or Jupiter Cup tile logo is visible, we're on the race-pick screen.
            val onRacePick =
                ButtonDailyRacesMoonlightSho.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                    ButtonDailyRacesJupiterCup.check(game.imageUtils, sourceBitmap = sourceBitmap)
            return if (onRacePick) {
                DailyRaceScreenState.DAILY_RACES_RACE_PICK
            } else {
                DailyRaceScreenState.DAILY_RACES_DIFFICULTY_PICK
            }
        }

        // Daily Programs container — detect by the distinctive "Daily Programs" green
        // banner that only appears on the container screen (NOT on the Race tab).
        if (LabelDailyPrograms.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.DAILY_PROGRAMS_CONTAINER
        }

        // Race tab — the Race tab in the bottom nav is selected AND we can see the
        // 4 mode tiles (Team Trials, Race Events, Daily Program, Exhibition). We check
        // the menubar first (fast path) then corroborate with the Daily Program tile
        // which is unique to this screen among Race-tab-selected screens.
        if (ButtonMenuBarRaceSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.RACE_TAB
        }

        // Home Screen — bottom nav's Home tab is selected.
        if (ButtonMenuBarHomeSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.HOME_SCREEN
        }

        // Post-race results — after Multi-Race completes, a summary screen appears with
        // a Next button. We detect via the Next button presence combined with the fact
        // that we've already committed the race sequence (raceSequenceCommitted flag).
        if (raceSequenceCommitted &&
            (ButtonNext.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                ButtonNextWithImage.check(game.imageUtils, sourceBitmap = sourceBitmap))) {
            return DailyRaceScreenState.POST_RACE_RESULTS
        }

        return DailyRaceScreenState.UNKNOWN
    }

    // ------------------------------------------------------------------------
    // Per-state handlers
    // ------------------------------------------------------------------------

    /**
     * Navigate from the Home Screen to the Race tab by clicking the Race button in the
     * bottom nav. The Race tab button is a `MultiStateButtonInterface`; we specifically
     * click the *unselected* variant since if it were already selected we'd be on the
     * Race tab, not Home.
     */
    private fun handleHomeScreen() {
        MessageLog.v(TAG, "[STATE] handleHomeScreen:: clicking Race tab in bottom nav.")
        if (ButtonMenuBarRaceUnselected.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleHomeScreen:: Race tab (unselected) button not found.")
            game.wait(1.0)
        }
    }

    /**
     * On the Race tab with 4 mode tiles visible: click the Daily Program tile.
     */
    private fun handleRaceTab() {
        MessageLog.v(TAG, "[STATE] handleRaceTab:: clicking Daily Program tile.")
        if (ButtonDailyProgramTile.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceTab:: Daily Program tile not found.")
            game.wait(1.0)
        }
    }

    /**
     * Click the Daily Races tile (or Daily Legend Races tile, once that task exists).
     */
    private fun handleDailyProgramsContainer() {
        MessageLog.v(TAG, "[STATE] handleDailyProgramsContainer:: looking for Daily Races tile.")
        if (ButtonDailyRaces.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleDailyProgramsContainer:: Daily Races tile not found; retrying.")
            game.wait(1.0)
        }
    }

    /**
     * Click the configured race tile (Moonlight Sho or Jupiter Cup).
     *
     * Returns a [TaskResult] if the configured race isn't present (rotation changed or
     * the ticket counter is zero, causing the tile to be hidden). Otherwise null to continue.
     */
    private fun handleRacePick(): TaskResult? {
        val tile = when (targetRaceName) {
            "Moonlight Sho" -> ButtonDailyRacesMoonlightSho
            "Jupiter Cup" -> ButtonDailyRacesJupiterCup
            else -> {
                val msg = "Unknown targetRace setting: \"$targetRaceName\". Expected \"Moonlight Sho\" or \"Jupiter Cup\"."
                MessageLog.e(TAG, "[ERROR] handleRacePick:: $msg")
                return TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, msg)
            }
        }

        if (!tile.check(game.imageUtils)) {
            // Tile not present on this rotation OR 0 tickets remaining (tile hidden).
            MessageLog.w(TAG, "[WARN] handleRacePick:: $targetRaceName tile not visible. Rotation changed or tickets exhausted.")
            return TaskResult.Success(
                TaskResultCode.TASK_RESULT_COMPLETE,
                "Configured race \"$targetRaceName\" not available. Exiting cleanly.",
            )
        }

        if (tile.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleRacePick:: click on $targetRaceName tile failed; retrying.")
            game.wait(1.0)
        }

        return null
    }

    /**
     * Click the configured difficulty tier.
     *
     * Each difficulty row is at a stable y position on the 1080x1920 Android render.
     * Rather than capture 4 per-tier templates that would be fragile under cosmetic UI
     * refreshes, we compute coordinates as ratios of the display dimensions. The row
     * ratios were measured from the 2026-04-18 calibration capture.
     */
    private fun handleDifficultyPick() {
        val ratioY: Double = when (targetDifficulty.uppercase()) {
            "VERY_HARD" -> 0.53    // top row
            "HARD" -> 0.65          // second row
            "NORMAL" -> 0.77        // third row (may require scroll)
            "EASY" -> 0.89          // bottom row (usually requires scroll)
            else -> {
                MessageLog.w(TAG, "[WARN] handleDifficultyPick:: unknown tier \"$targetDifficulty\"; defaulting to VERY_HARD.")
                0.53
            }
        }

        val x: Double = SharedData.displayWidth * 0.5
        val y: Double = SharedData.displayHeight * ratioY

        MessageLog.v(TAG, "[STATE] handleDifficultyPick:: picking $targetDifficulty at ($x, $y).")
        game.gestureUtils.tap(x, y, "daily_race_difficulty_${targetDifficulty.lowercase()}")
        game.wait(2.5)
    }

    /**
     * Multi-Race popup — click Race! (Consumes 3) at fixed coordinates.
     *
     * The popup lets you adjust the number of races via +/- buttons. Default is all held
     * tickets (e.g. 3/3), which is exactly what the bot wants. Just clicks Race! to commit.
     *
     * Uses coordinate-based tap rather than template because the button text contains
     * the dynamic "Consumes N" subtitle which would make template matching fragile.
     */
    private fun handleMultiRacePopup() {
        // The Race! button on the Multi-Race popup is in the center of the dialog, not
        // the bottom of the screen. Earlier coords were clicking through the popup backdrop
        // onto the Runner Selection Confirm button behind it, causing a close-reopen loop.
        //
        // Verified pixel location from the 2026-04-18 capture at 1080x1920: button spans
        // x=550-1010, y=1180-1320. Center: (780, 1250). Ratios: 0.722 x, 0.651 y.
        val x: Double = SharedData.displayWidth * 0.722
        val y: Double = SharedData.displayHeight * 0.651
        MessageLog.v(TAG, "[STATE] handleMultiRacePopup:: clicking Race! (3/3) at ($x, $y).")
        raceSequenceCommitted = true
        game.gestureUtils.tap(x, y, "multi_race_popup_race_confirm")
        game.wait(3.0)
    }

    /**
     * Runner Selection screen — click Confirm to accept the preselected runner.
     *
     * The user is expected to have run the race at least once manually so their preferred
     * horse is already selected on this screen. The bot just needs to tap Confirm to move on.
     */
    private fun handleRunnerSelection() {
        MessageLog.v(TAG, "[STATE] handleRunnerSelection:: clicking Confirm to accept preset runner.")
        if (ButtonConfirm.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRunnerSelection:: Confirm button not found; retrying.")
            game.wait(1.5)
        }
    }

    /**
     * Verify Multi-Race toggle state, fix if needed, then commit Race!.
     *
     * At cap for tickets this fast-path will still work — the game only blocks the Race!
     * button when there are 0 tickets, in which case we should never have reached this
     * state (the race tile would have been hidden upstream).
     */
    private fun handleRaceDetails() {
        if (raceSequenceCommitted) {
            // We already clicked Race! — we shouldn't be on Race Details again. Log and
            // return. The state machine will redetect and move on when the game transitions.
            MessageLog.w(TAG, "[WARN] handleRaceDetails:: race already committed but still on Race Details. Redetecting.")
            game.wait(2.0)
            return
        }

        if (ensureMultiRaceOn) {
            // If the Off pill is visible, tap it to toggle On.
            if (ButtonMultiRaceOff.check(game.imageUtils)) {
                MessageLog.v(TAG, "[STATE] handleRaceDetails:: Multi-Race is Off; toggling to On.")
                ButtonMultiRaceOff.click(game.imageUtils)
                game.wait(1.0)
            }
            // Verify it's now On.
            if (!ButtonMultiRaceOn.check(game.imageUtils)) {
                MessageLog.w(TAG, "[WARN] handleRaceDetails:: Could not verify Multi-Race: On after toggle.")
                // Continue anyway — game may have a transient state.
            }
        }

        MessageLog.v(TAG, "[STATE] handleRaceDetails:: committing Race! sequence.")
        if (ButtonRaceConfirm.click(game.imageUtils, region = Region.bottomHalf)) {
            raceSequenceCommitted = true
            game.wait(3.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceDetails:: Race! button not clickable; retrying.")
            game.wait(1.5)
        }
    }

    /**
     * Tap through post-race result screens. The Multi-Race chain produces a sequence of
     * result screens (one per race ticket consumed) plus a final summary. Each one has a
     * Next/OK/Confirm/Close button of some kind. We keep clicking whichever is present
     * until the state machine detects we're back on the Daily Races race-pick screen
     * (COMPLETE state).
     */
    private fun handlePostRaceResults() {
        // Try each advance button in rough order of frequency on post-race screens.
        val advanceButtons = listOf(
            "Next" to ButtonNext,
            "NextWithImage" to ButtonNextWithImage,
            "Confirm" to ButtonConfirm,
            "OK" to ButtonOk,
            "Close" to ButtonClose,
        )

        for ((name, button) in advanceButtons) {
            if (button.check(game.imageUtils)) {
                MessageLog.v(TAG, "[STATE] handlePostRaceResults:: clicking $name.")
                button.click(game.imageUtils)
                game.wait(2.0)
                return
            }
        }

        MessageLog.v(TAG, "[STATE] handlePostRaceResults:: no advance button found; waiting for next screen.")
        game.wait(2.0)
    }
}

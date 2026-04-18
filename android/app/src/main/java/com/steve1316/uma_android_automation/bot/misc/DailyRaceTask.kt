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
 * - Multi-Race: On (set on Race Details) makes the game chain all remaining daily tickets
 *   into one sequence, so we verify the toggle once, commit a single `Race!` tap, and wait
 *   for the whole sequence - no per-race loop in the bot.
 * - Daily Race tickets (N/3) reset each day at server reset.
 *
 * ## Configuration (via [SettingsHelper], namespace `"miscDailyRace"`)
 *
 * - `targetRace` (string): `"Moonlight Sho"` | `"Jupiter Cup"`. Default `"Moonlight Sho"`
 *   (Monies, useful at every stage of the game).
 * - `targetDifficulty` (string): `"VERY_HARD"` | `"HARD"` | `"NORMAL"` | `"EASY"`.
 *   Default `"VERY_HARD"` for max rewards.
 * - `ensureMultiRaceOn` (bool): default true. False runs races one at a time, for debugging.
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

        /** Horse-picker step between difficulty and race details. Bot just clicks Confirm; the user pre-sets their runner by running manually once. */
        RUNNER_SELECTION,

        /** Multi-Race popup asking how many races to run. Bot clicks Race! to commit the default 3/3. */
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
                // Just poll; with Multi-Race: On the game auto-skips between races.
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
                // Wait before retrying so transient states (loading spinners, cutscenes) can
                // resolve. trackProgress already counts these toward the bailout.
                game.wait(1.5)
                null
            }
        }
    }

    /**
     * Classify the current screen, most discriminating templates first.
     *
     * Order matters: prefer templates that are unique to their screen and fast to match.
     * Reuse the single [sourceBitmap] where possible to avoid re-screenshotting.
     */
    private fun detectScreenState(
        sourceBitmap: android.graphics.Bitmap,
    ): DailyRaceScreenState {
        // Race Details is the goal screen before racing - check first for fast-path exits.
        if (LabelRaceDetails.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RACE_DETAILS
        }

        // Multi-Race popup overlays Runner Selection after Confirm; check before Runner
        // Selection since its (dimmed) header is still visible beneath the popup.
        if (LabelMultiRacePopup.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.MULTI_RACE_POPUP
        }

        // Runner Selection - horse-picker screen between difficulty and Race Details.
        if (LabelRunnerSelection.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RUNNER_SELECTION
        }

        // Daily Races screen group - detect by the purple "Daily Races" header banner. Distinct
        // from the [ButtonDailyRaces] tile (dark-purple text on white) on the Daily Programs
        // container, which is used for clicks.
        if (LabelDailyRacesHeader.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            // The header shows on both the race-pick and difficulty-pick screens. A visible
            // Moonlight Sho or Jupiter Cup tile logo means we're on the race-pick screen.
            val onRacePick =
                ButtonDailyRacesMoonlightSho.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                    ButtonDailyRacesJupiterCup.check(game.imageUtils, sourceBitmap = sourceBitmap)
            return if (onRacePick) {
                DailyRaceScreenState.DAILY_RACES_RACE_PICK
            } else {
                DailyRaceScreenState.DAILY_RACES_DIFFICULTY_PICK
            }
        }

        // Daily Programs container - detect by the green "Daily Programs" banner, which only
        // appears here (NOT on the Race tab).
        if (LabelDailyPrograms.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.DAILY_PROGRAMS_CONTAINER
        }

        // Race tab - Race nav tab selected, showing the 4 mode tiles. The menubar check is the
        // fast path here.
        if (ButtonMenuBarRaceSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.RACE_TAB
        }

        // Home Screen - bottom nav's Home tab is selected.
        if (ButtonMenuBarHomeSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.HOME_SCREEN
        }

        // Post-race results - a summary screen with a Next button. Gate on raceSequenceCommitted
        // so we only treat a Next button as results after we've actually started the sequence.
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
     * Navigate from Home to the Race tab via the bottom-nav Race button. Click the
     * *unselected* variant: if it were already selected we'd be on the Race tab, not Home.
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
     * Each difficulty row is at a stable y position on the 1080x1920 render. Computing
     * coordinates as display-dimension ratios avoids 4 per-tier templates that would be
     * fragile under cosmetic UI refreshes; the ratios come from a calibration capture.
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
     * Multi-Race popup - click Race! at fixed coordinates.
     *
     * The popup defaults to all held tickets (e.g. 3/3), which is what we want, so just commit
     * Race!. Coordinate tap rather than template: the button's dynamic "Consumes N" subtitle
     * makes template matching fragile.
     */
    private fun handleMultiRacePopup() {
        // The Race! button on the Multi-Race popup is at the bottom-right of the dialog.
        // Measured ratios from the capture at 1080x1920: center ~x=770, y=1640.
        val x: Double = SharedData.displayWidth * 0.713
        val y: Double = SharedData.displayHeight * 0.854
        MessageLog.v(TAG, "[STATE] handleMultiRacePopup:: clicking Race! (3/3) at ($x, $y).")
        raceSequenceCommitted = true
        game.gestureUtils.tap(x, y, "multi_race_popup_race_confirm")
        game.wait(3.0)
    }

    /**
     * Runner Selection - click Confirm to accept the preselected runner. The user is expected
     * to have run the race manually once so their preferred horse is already selected here.
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
     * Verify the Multi-Race toggle, fix if needed, then commit Race!.
     *
     * The game only blocks Race! at 0 tickets, and at 0 tickets the race tile is hidden
     * upstream so we'd never reach this state - any ticket count we see here is raceable.
     */
    private fun handleRaceDetails() {
        if (raceSequenceCommitted) {
            // Already committed Race!; shouldn't be on Race Details again. Wait for the game
            // to transition and let the state machine redetect.
            MessageLog.w(TAG, "[WARN] handleRaceDetails:: race already committed but still on Race Details. Redetecting.")
            game.wait(2.0)
            return
        }

        if (ensureMultiRaceOn) {
            if (ButtonMultiRaceOff.check(game.imageUtils)) {
                MessageLog.v(TAG, "[STATE] handleRaceDetails:: Multi-Race is Off; toggling to On.")
                ButtonMultiRaceOff.click(game.imageUtils)
                game.wait(1.0)
            }
            if (!ButtonMultiRaceOn.check(game.imageUtils)) {
                MessageLog.w(TAG, "[WARN] handleRaceDetails:: Could not verify Multi-Race: On after toggle.")
                // Continue anyway; may be a transient state.
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
     * Tap through post-race result screens. The Multi-Race chain emits one result screen per
     * ticket plus a final summary, each with some Next/OK/Confirm/Close button; click whichever
     * is present until the state machine reaches COMPLETE (back on the race-pick screen).
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

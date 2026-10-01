package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.CoordinateTap
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.components.ButtonCancel
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
import com.steve1316.uma_android_automation.components.LabelDailySale
import com.steve1316.uma_android_automation.components.LabelMultiRacePopup
import com.steve1316.uma_android_automation.components.LabelRaceDetails
import com.steve1316.uma_android_automation.components.LabelRunnerSelection
import com.steve1316.uma_android_automation.components.Region

/**
 * Misc task for Daily Races: Home, Race tab, Daily Program, Daily Races, race and difficulty, Race Details, then one
 * Race! tap. With Multi-Race: On the game chains all remaining tickets (reset daily at server reset), so there is no
 * per-race loop.
 *
 * Settings (namespace "miscDailyRace"): targetRace ("Moonlight Sho" default | "Jupiter Cup"), targetDifficulty (default
 * "VERY_HARD" for max rewards), ensureMultiRaceOn (default true; false runs races one at a time for debugging).
 */
class DailyRaceTask(game: Game) : MiscTask(game) {
    enum class DailyRaceScreenState {
        HOME_SCREEN,

        RACE_TAB,

        DAILY_PROGRAMS_CONTAINER,

        DAILY_RACES_RACE_PICK,

        DAILY_RACES_DIFFICULTY_PICK,

        /** Bot just clicks Confirm; the user pre-sets their runner by running manually once. */
        RUNNER_SELECTION,

        /** Bot clicks Race! to commit the default 3/3. */
        MULTI_RACE_POPUP,

        DAILY_SALE_POPUP,

        RACE_DETAILS,

        IN_RACE,

        POST_RACE_RESULTS,

        COMPLETE,

        UNKNOWN,
    }

    private val targetRaceName: String =
        SettingsHelper.getStringSetting("miscDailyRace", "targetRace", "Moonlight Sho")

    private val targetDifficulty: String =
        SettingsHelper.getStringSetting("miscDailyRace", "targetDifficulty", "VERY_HARD")

    private val ensureMultiRaceOn: Boolean =
        SettingsHelper.getBooleanSetting("miscDailyRace", "ensureMultiRaceOn", true)

    private var raceSequenceCommitted: Boolean = false

    override fun process(): TaskResult? {
        checkSafetyRails()?.let { return it }

        val sourceBitmap = captureSourceBitmap()
        val currentState = detectScreenState(sourceBitmap)
        trackProgress(currentState.name, currentState == DailyRaceScreenState.UNKNOWN)

        MessageLog.v(TAG, "[STATE] iter=$iterationsCompleted state=$currentState")

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

            DailyRaceScreenState.DAILY_SALE_POPUP -> {
                handleDailySalePopup()
                null
            }

            DailyRaceScreenState.RACE_DETAILS -> {
                handleRaceDetails()
                null
            }

            DailyRaceScreenState.IN_RACE -> {
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
                game.wait(1.5)
                null
            }
        }
    }

    /** Most discriminating templates first; reuses [sourceBitmap] to avoid re-screenshotting. */
    private fun detectScreenState(
        sourceBitmap: android.graphics.Bitmap,
    ): DailyRaceScreenState {
        if (LabelRaceDetails.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RACE_DETAILS
        }

        // Multi-Race popup overlays Runner Selection, whose dimmed header is still visible beneath; check it first.
        if (LabelMultiRacePopup.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.MULTI_RACE_POPUP
        }

        // Daily Sale popup appears after races finish with the Runner Selection header still visible beneath; check it
        // first.
        if (LabelDailySale.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.DAILY_SALE_POPUP
        }

        if (LabelRunnerSelection.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RUNNER_SELECTION
        }

        // Purple "Daily Races" header banner; distinct from the dark-purple-on-white [ButtonDailyRaces] tile used for
        // clicks.
        if (LabelDailyRacesHeader.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            // The header shows on both race-pick and difficulty-pick; a visible race tile logo means race-pick.
            val onRacePick =
                ButtonDailyRacesMoonlightSho.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                    ButtonDailyRacesJupiterCup.check(game.imageUtils, sourceBitmap = sourceBitmap)
            return if (onRacePick) {
                DailyRaceScreenState.DAILY_RACES_RACE_PICK
            } else {
                DailyRaceScreenState.DAILY_RACES_DIFFICULTY_PICK
            }
        }

        // Green "Daily Programs" banner, which only appears here.
        if (LabelDailyPrograms.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.DAILY_PROGRAMS_CONTAINER
        }

        if (ButtonMenuBarRaceSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.RACE_TAB
        }

        if (ButtonMenuBarHomeSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.HOME_SCREEN
        }

        // Gated on raceSequenceCommitted so a Next button only counts as results once the sequence started.
        if (raceSequenceCommitted &&
            (
                ButtonNext.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                    ButtonNextWithImage.check(game.imageUtils, sourceBitmap = sourceBitmap)
            )
        ) {
            return DailyRaceScreenState.POST_RACE_RESULTS
        }

        return DailyRaceScreenState.UNKNOWN
    }

    // ------------------------------------------------------------------------
    // Per-state handlers
    // ------------------------------------------------------------------------

    /** Click the *unselected* Race nav variant: if it were selected we would already be on the Race tab. */
    private fun handleHomeScreen() {
        MessageLog.v(TAG, "[STATE] handleHomeScreen:: clicking Race tab in bottom nav.")
        if (ButtonMenuBarRaceUnselected.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleHomeScreen:: Race tab (unselected) button not found.")
            game.wait(1.0)
        }
    }

    private fun handleRaceTab() {
        MessageLog.v(TAG, "[STATE] handleRaceTab:: clicking Daily Program tile.")
        if (ButtonDailyProgramTile.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceTab:: Daily Program tile not found.")
            game.wait(1.0)
        }
    }

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
     * Returns a [TaskResult] when the configured race tile is absent (rotation changed or zero tickets hid it); null to
     * continue.
     */
    private fun handleRacePick(): TaskResult? {
        val tile =
            when (targetRaceName) {
                "Moonlight Sho" -> ButtonDailyRacesMoonlightSho
                "Jupiter Cup" -> ButtonDailyRacesJupiterCup
                else -> {
                    val msg = "Unknown targetRace setting: \"$targetRaceName\". Expected \"Moonlight Sho\" or \"Jupiter Cup\"."
                    MessageLog.e(TAG, "[ERROR] handleRacePick:: $msg")
                    return TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, msg)
                }
            }

        if (!tile.check(game.imageUtils)) {
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
     * Difficulty rows sit at stable display-ratio positions from a calibration capture, avoiding 4 per-tier templates
     * that are fragile under UI refreshes.
     */
    private fun handleDifficultyPick() {
        val ratioY: Double =
            when (targetDifficulty.uppercase()) {
                "VERY_HARD" -> 0.53 // top row
                "HARD" -> 0.65 // second row
                "NORMAL" -> 0.77 // third row (may require scroll)
                "EASY" -> 0.89 // bottom row (usually requires scroll)
                else -> {
                    MessageLog.w(TAG, "[WARN] handleDifficultyPick:: unknown tier \"$targetDifficulty\"; defaulting to VERY_HARD.")
                    0.53
                }
            }

        val x: Double = SharedData.displayWidth * 0.5
        val y: Double = SharedData.displayHeight * ratioY

        MessageLog.v(TAG, "[STATE] handleDifficultyPick:: picking $targetDifficulty at ($x, $y).")
        CoordinateTap.tap(game.gestureUtils, x, y, "daily_race_difficulty_${targetDifficulty.lowercase()}")
        game.wait(2.5)
    }

    /**
     * Coordinate tap rather than template: the button's dynamic "Consumes N" subtitle makes matching fragile. The popup
     * defaults to all held tickets.
     */
    private fun handleMultiRacePopup() {
        // Race! is centered in the dialog; bottom-anchored coords tapped through the backdrop onto the Runner Selection
        // Confirm behind it and looped close-reopen.
        val x: Double = SharedData.displayWidth * 0.722
        val y: Double = SharedData.displayHeight * 0.651
        MessageLog.v(TAG, "[STATE] handleMultiRacePopup:: clicking Race! (3/3) at ($x, $y).")
        raceSequenceCommitted = true
        CoordinateTap.tap(game.gestureUtils, x, y, "multi_race_popup_race_confirm")
        game.wait(3.0)
    }

    /** Always cancel the limited-time purchase advert; shopping is left to the user. */
    private fun handleDailySalePopup() {
        MessageLog.v(TAG, "[STATE] handleDailySalePopup:: dismissing via Cancel (user decides shopping).")
        if (ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleDailySalePopup:: Cancel button not found; retrying.")
            game.wait(1.5)
        }
    }

    /** Accepts the preselected runner; the user is expected to have run the race manually once. */
    private fun handleRunnerSelection() {
        MessageLog.v(TAG, "[STATE] handleRunnerSelection:: clicking Confirm to accept preset runner.")
        if (ButtonConfirm.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRunnerSelection:: Confirm button not found; retrying.")
            game.wait(1.5)
        }
    }

    /** At 0 tickets the race tile is hidden upstream, so any ticket count seen here is raceable. */
    private fun handleRaceDetails() {
        if (raceSequenceCommitted) {
            // Already committed; wait for the game to transition and redetect.
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
     * The Multi-Race chain emits a result screen per ticket plus a summary; click whichever advance button is present
     * until COMPLETE.
     */
    private fun handlePostRaceResults() {
        val advanceButtons =
            listOf(
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

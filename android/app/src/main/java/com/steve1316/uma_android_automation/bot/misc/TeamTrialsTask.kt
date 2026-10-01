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
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceUnselected
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.ButtonNextWithImage
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonSeeAllRaceResults
import com.steve1316.uma_android_automation.components.ButtonSelectOpponent
import com.steve1316.uma_android_automation.components.ButtonTeamRace
import com.steve1316.uma_android_automation.components.ButtonTeamTrials
import com.steve1316.uma_android_automation.components.LabelItemsSelected
import com.steve1316.uma_android_automation.components.LabelTeamTrials
import com.steve1316.uma_android_automation.components.Region

/**
 * Team Trials: Race tab -> Team Trials -> Team Race -> pick opponent -> Team Preview -> Items Selected
 * -> Race! (1 RP) -> result screens -> back on Team Trials home. Loops until [maxMatchesPerSession] (a
 * fuse on top of the game's own RP cap) or the game stops us. Opponents are listed strongest to
 * weakest, so the default `opponentPick` BOTTOM is the easiest fight.
 */
class TeamTrialsTask(game: Game) : MiscTask(game) {
    enum class TeamTrialsScreenState {
        HOME_SCREEN,

        RACE_TAB,

        TEAM_TRIALS_HOME,

        SELECT_OPPONENT,

        TEAM_PREVIEW,

        ITEMS_SELECTED_POPUP,

        IN_MATCH,

        POST_MATCH_RESULTS,

        COMPLETE,

        UNKNOWN,
    }

    enum class OpponentPick {
        TOP,

        MIDDLE,

        BOTTOM,
    }

    private val opponentPick: OpponentPick =
        when (SettingsHelper.getStringSetting("miscTeamTrials", "opponentPick", "BOTTOM").uppercase()) {
            "TOP" -> OpponentPick.TOP
            "MIDDLE" -> OpponentPick.MIDDLE
            else -> OpponentPick.BOTTOM
        }

    private val maxMatchesPerSession: Int =
        SettingsHelper.getIntSetting("miscTeamTrials", "maxMatchesPerSession", 5)

    private var matchesCompleted: Int = 0

    private var matchInProgress: Boolean = false

    override fun process(): TaskResult? {
        checkSafetyRails()?.let { return it }

        val sourceBitmap = captureSourceBitmap()
        val currentState = detectScreenState(sourceBitmap)
        trackProgress(currentState.name, currentState == TeamTrialsScreenState.UNKNOWN)

        MessageLog.v(TAG, "[STATE] iter=$iterationsCompleted state=$currentState matches=$matchesCompleted")

        // The Items Selected popup and Daily Sale are named states below, not dismissed here.
        if (currentState != TeamTrialsScreenState.ITEMS_SELECTED_POPUP && handleIncidentalPopups()) {
            return null
        }

        return when (currentState) {
            TeamTrialsScreenState.HOME_SCREEN -> {
                handleHomeScreen()
                null
            }

            TeamTrialsScreenState.RACE_TAB -> {
                handleRaceTab()
                null
            }

            TeamTrialsScreenState.TEAM_TRIALS_HOME -> {
                handleTeamTrialsHome()
            }

            TeamTrialsScreenState.SELECT_OPPONENT -> {
                handleSelectOpponent()
                null
            }

            TeamTrialsScreenState.TEAM_PREVIEW -> {
                handleTeamPreview()
                null
            }

            TeamTrialsScreenState.ITEMS_SELECTED_POPUP -> {
                handleItemsSelected()
                null
            }

            TeamTrialsScreenState.IN_MATCH -> {
                game.wait(3.0)
                null
            }

            TeamTrialsScreenState.POST_MATCH_RESULTS -> {
                handlePostMatchResults()
                null
            }

            TeamTrialsScreenState.COMPLETE -> {
                TaskResult.Success(
                    TaskResultCode.TASK_RESULT_COMPLETE,
                    "TeamTrialsTask completed. Matches run this session: $matchesCompleted.",
                )
            }

            TeamTrialsScreenState.UNKNOWN -> {
                game.wait(1.5)
                null
            }
        }
    }

    private fun detectScreenState(
        sourceBitmap: android.graphics.Bitmap,
    ): TeamTrialsScreenState {
        // Items Selected first: its dismissal differs from handleIncidentalPopups, so identify it before that fires.
        if (LabelItemsSelected.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.ITEMS_SELECTED_POPUP
        }

        if (ButtonSelectOpponent.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.SELECT_OPPONENT
        }

        // The Team Race button is only visible outside the sub-screens.
        if (ButtonTeamRace.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.TEAM_TRIALS_HOME
        }

        if (ButtonSeeAllRaceResults.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.POST_MATCH_RESULTS
        }

        // Team Trials header label: catch-all for any TT screen not detected above. It must come BEFORE the
        // bottom-nav fallbacks: the Race tab is highlighted on every TT screen, so otherwise Team Preview and
        // post-match screens read as RACE_TAB and the bot loops clicking the TT tile. [matchInProgress] tells pre from post-match.
        if (LabelTeamTrials.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return if (matchInProgress) {
                TeamTrialsScreenState.POST_MATCH_RESULTS
            } else {
                TeamTrialsScreenState.TEAM_PREVIEW
            }
        }


        if (ButtonMenuBarRaceSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return TeamTrialsScreenState.RACE_TAB
        }

        if (ButtonMenuBarHomeSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return TeamTrialsScreenState.HOME_SCREEN
        }

        return TeamTrialsScreenState.UNKNOWN
    }

    // ------------------------------------------------------------------------
    // Per-state handlers
    // ------------------------------------------------------------------------

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
        MessageLog.v(TAG, "[STATE] handleRaceTab:: clicking Team Trials tile.")
        if (ButtonTeamTrials.click(game.imageUtils)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceTab:: Team Trials tile not found.")
            game.wait(1.0)
        }
    }

    /** The RP check happens upstream in the queue runner; at least 1 RP is assumed here. */
    private fun handleTeamTrialsHome(): TaskResult? {
        if (matchesCompleted >= maxMatchesPerSession) {
            MessageLog.v(TAG, "[STATE] handleTeamTrialsHome:: reached cap ($maxMatchesPerSession). Exiting.")
            return TaskResult.Success(
                TaskResultCode.TASK_RESULT_COMPLETE,
                "TeamTrialsTask: reached per-session cap of $maxMatchesPerSession matches.",
            )
        }

        matchInProgress = false

        MessageLog.v(TAG, "[STATE] handleTeamTrialsHome:: starting match ${matchesCompleted + 1}/$maxMatchesPerSession.")
        if (ButtonTeamRace.click(game.imageUtils)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleTeamTrialsHome:: Team Race button click failed.")
            game.wait(1.5)
        }
        return null
    }

    /** Opponent tile art is randomised, so templates cannot find "the bottom row"; click ratios of the display instead. */
    private fun handleSelectOpponent() {
        // Ratios measured from 1080x1920 captures: rows at y=0.19..0.32, 0.34..0.47, 0.49..0.62; click each row's center.
        val ratioY: Double =
            when (opponentPick) {
                OpponentPick.TOP -> 0.26
                OpponentPick.MIDDLE -> 0.41
                OpponentPick.BOTTOM -> 0.56
            }

        val x: Double = SharedData.displayWidth * 0.5
        val y: Double = SharedData.displayHeight * ratioY

        MessageLog.v(TAG, "[STATE] handleSelectOpponent:: picking $opponentPick row at ($x, $y).")
        CoordinateTap.tap(game.gestureUtils, x, y, "select_opponent_${opponentPick.name.lowercase()}")
        game.wait(2.5)
    }

    private fun handleTeamPreview() {
        MessageLog.v(TAG, "[STATE] handleTeamPreview:: clicking Next to advance to item picker.")
        if (ButtonNextWithImage.click(game.imageUtils)) {
            game.wait(2.0)
        } else if (ButtonNext.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleTeamPreview:: no Next button found.")
            game.wait(1.5)
        }
    }

    /**
     * Coordinate tap (measured center 770, 1370) rather than the ButtonRaceConfirm template, which also
     * matches the Team Preview "Next" button behind the popup and lands on the dimmed underlay.
     */
    private fun handleItemsSelected() {
        MessageLog.v(TAG, "[STATE] handleItemsSelected:: skipping item picker, clicking Race!.")
        val x: Double = SharedData.displayWidth * 0.713
        val y: Double = SharedData.displayHeight * 0.714
        CoordinateTap.tap(game.gestureUtils, x, y, "items_selected_race_confirm")
        matchInProgress = true
        game.wait(3.0)
    }

    /** Post-match cascade; known tail: See All Race Results, Next, Story Unlocked Close, Next (chibi), TT home. */
    private fun handlePostMatchResults() {
        if (ButtonSeeAllRaceResults.check(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: See All Race Results.")
            ButtonSeeAllRaceResults.click(game.imageUtils, region = Region.bottomHalf)
            game.wait(2.5)
            return
        }

        // The standby screen's Race 1 / Race 2 green header bands (y~1300) falsely match ButtonNext and
        // ButtonNextWithImage, so tap the known location (center 540, 1775; ratios 0.5, 0.925) while in standby.
        if (matchInProgress && iterationsWithoutProgress >= 3) {
            val x = SharedData.displayWidth * 0.5
            val y = SharedData.displayHeight * 0.925
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: coord-fallback tap on See All Race Results at ($x, $y).")
            CoordinateTap.tap(game.gestureUtils, x, y, "see_all_race_results_coord_fallback")
            game.wait(2.5)
            return
        }

        if (ButtonCancel.check(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: dismissing Daily Sale / cancel popup.")
            ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)
            game.wait(2.0)
            return
        }

        if (ButtonClose.check(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: dismissing popup via Close.")
            ButtonClose.click(game.imageUtils, region = Region.bottomHalf)
            game.wait(2.0)
            return
        }

        // Chibi Next (green, two chibi characters): bottom half only, to avoid the standby "Race 2" header band (y~1300).
        if (ButtonNextWithImage.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked chibi Next.")
            game.wait(2.5)
            matchesCompleted += 1
            return
        }

        if (ButtonNext.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked generic Next.")
            game.wait(2.0)
            return
        }

        if (ButtonOk.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked OK.")
            game.wait(2.0)
            return
        }

        if (ButtonConfirm.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked Confirm.")
            game.wait(2.0)
            return
        }

        MessageLog.w(TAG, "[WARN] handlePostMatchResults:: no advance button found; waiting.")
        game.wait(2.0)
    }
}

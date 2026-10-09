package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
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
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeUnselected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceUnselected
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.ButtonNextWithImage
import com.steve1316.uma_android_automation.components.ButtonNo
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRaceExclamationShiftedUp
import com.steve1316.uma_android_automation.components.ButtonRestore
import com.steve1316.uma_android_automation.components.ButtonSeeAllRaceResults
import com.steve1316.uma_android_automation.components.ButtonSkip
import com.steve1316.uma_android_automation.components.ButtonTeamRace
import com.steve1316.uma_android_automation.components.ButtonTeamTrials
import com.steve1316.uma_android_automation.components.LabelDailySale
import com.steve1316.uma_android_automation.components.LabelItemsSelected
import com.steve1316.uma_android_automation.components.LabelTeamTrials
import com.steve1316.uma_android_automation.components.Region
import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.TeamTrialsGeometry
import com.steve1316.uma_android_automation.utils.findOpponentCardCentres
import com.steve1316.uma_android_automation.utils.gameY
import com.steve1316.uma_android_automation.utils.parseRpCount
import com.steve1316.uma_android_automation.utils.quickModePillOn
import com.steve1316.uma_android_automation.utils.readRpPips
import kotlin.random.Random

/**
 * Team Trials: Race tab -> Team Trials -> Team Race -> pick opponent -> Team Preview -> Items Selected
 * -> Race! (1 RP) -> standby -> See All Race Results -> Skip -> result screens -> back on Team Trials home.
 * Loops until the RP pips read 0 or [maxMatchesPerSession], then returns to Home. Opponents are listed
 * strongest to weakest, so the default `opponentPick` BOTTOM is the easiest fight. RP is never restored.
 */
class TeamTrialsTask(game: Game) : MiscTask(game) {
    enum class TeamTrialsScreenState {
        HOME_SCREEN,

        RACE_TAB,

        TEAM_TRIALS_HOME,

        SELECT_OPPONENT,

        TEAM_PREVIEW,

        ITEMS_SELECTED_POPUP,

        RESTORE_RP_PROMPT,

        DAILY_SALE_POPUP,

        STANDBY,

        RESULT_REVEAL,

        POST_MATCH_RESULTS,

        UNKNOWN,
    }

    enum class OpponentPick {
        TOP,

        MIDDLE,

        BOTTOM,

        /** One of the three rows at random each match, for variety. */
        RANDOM,
        ;

        /** Row index (0 = top) of the three listed opponents. */
        fun rowIndex(rng: Random = Random.Default): Int =
            when (this) {
                TOP -> 0
                MIDDLE -> 1
                BOTTOM -> 2
                RANDOM -> rng.nextInt(3)
            }

        companion object {
            fun fromSetting(raw: String): OpponentPick = entries.firstOrNull { it.name == raw.trim().uppercase() } ?: BOTTOM
        }
    }

    companion object {
        /** Only a screen right after the standby or the reveal can be a splash; any other unknown screen, a dialog included, is never tapped. */
        internal fun splashTapAllowed(
            matchInProgress: Boolean,
            consecutiveUnknowns: Int,
            lastKnownState: String,
        ): Boolean =
            matchInProgress &&
                consecutiveUnknowns >= 2 &&
                (lastKnownState == TeamTrialsScreenState.STANDBY.name || lastKnownState == TeamTrialsScreenState.RESULT_REVEAL.name)
    }

    private val opponentPick: OpponentPick =
        OpponentPick.fromSetting(SettingsHelper.getStringSetting("miscTeamTrials", "opponentPick", "BOTTOM"))

    private val maxMatchesPerSession: Int =
        SettingsHelper.getIntSetting("miscTeamTrials", "maxMatchesPerSession", 5)

    /** Matches committed with Race! (each spent 1 RP). */
    private var matchesCompleted: Int = 0

    private var matchInProgress: Boolean = false

    private var quickModeChecked: Boolean = false

    /** Set when no further match will start; the task then walks back to Home. */
    private var finishReason: String? = null

    override fun process(): TaskResult? {
        checkSafetyRails()?.let { return it }

        val sourceBitmap = captureSourceBitmap()
        val currentState = detectScreenState(sourceBitmap)
        trackProgress(currentState.name, currentState == TeamTrialsScreenState.UNKNOWN)

        MessageLog.v(TAG, "[STATE] iter=$iterationsCompleted state=$currentState matches=$matchesCompleted")

        // These are named states below, not dismissed by the shared handler.
        val ownDialog =
            currentState == TeamTrialsScreenState.ITEMS_SELECTED_POPUP ||
                currentState == TeamTrialsScreenState.RESTORE_RP_PROMPT ||
                currentState == TeamTrialsScreenState.DAILY_SALE_POPUP
        if (!ownDialog && handleIncidentalPopups()) {
            return null
        }

        return when (currentState) {
            TeamTrialsScreenState.HOME_SCREEN -> handleHomeScreen()

            TeamTrialsScreenState.RACE_TAB -> {
                handleRaceTab()
                null
            }

            TeamTrialsScreenState.TEAM_TRIALS_HOME -> {
                handleTeamTrialsHome(sourceBitmap)
                null
            }

            TeamTrialsScreenState.SELECT_OPPONENT -> {
                handleSelectOpponent(sourceBitmap)
                null
            }

            TeamTrialsScreenState.TEAM_PREVIEW -> {
                handleTeamPreview()
                null
            }

            TeamTrialsScreenState.ITEMS_SELECTED_POPUP -> {
                handleItemsSelected(sourceBitmap)
                null
            }

            TeamTrialsScreenState.RESTORE_RP_PROMPT -> {
                handleRestoreRpPrompt()
                null
            }

            TeamTrialsScreenState.DAILY_SALE_POPUP -> {
                handleDailySalePopup()
                null
            }

            TeamTrialsScreenState.STANDBY -> {
                handleStandby(sourceBitmap)
                null
            }

            TeamTrialsScreenState.RESULT_REVEAL -> {
                handleResultReveal()
                null
            }

            TeamTrialsScreenState.POST_MATCH_RESULTS -> {
                handlePostMatchResults()
                null
            }

            TeamTrialsScreenState.UNKNOWN -> {
                handleUnknown(sourceBitmap)
                null
            }
        }
    }

    private fun sampler(bitmap: Bitmap): SparkPixelSampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

    private fun detectScreenState(sourceBitmap: Bitmap): TeamTrialsScreenState {
        if (LabelDailySale.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.DAILY_SALE_POPUP
        }

        if (ButtonRestore.check(game.imageUtils, sourceBitmap = sourceBitmap) && ButtonNo.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.RESTORE_RP_PROMPT
        }

        // The label scores just under its 0.9 on the phone; the dialog's own Race! button is the steadier signal.
        if (LabelItemsSelected.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
            ButtonRaceExclamationShiftedUp.check(game.imageUtils, sourceBitmap = sourceBitmap)
        ) {
            return TeamTrialsScreenState.ITEMS_SELECTED_POPUP
        }

        if (findOpponentCardCentres(sampler(sourceBitmap), sourceBitmap.width, sourceBitmap.height) != null) {
            return TeamTrialsScreenState.SELECT_OPPONENT
        }

        // The Team Race button is only visible outside the sub-screens.
        if (ButtonTeamRace.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.TEAM_TRIALS_HOME
        }

        if (ButtonSeeAllRaceResults.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.STANDBY
        }

        // Skip shows only during the result reveal (the per-race list and the WIN/LOSE/DRAW splash).
        if (ButtonSkip.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.RESULT_REVEAL
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

        // RACE FINISHED and WINNINGS have no header, only a Next.
        if (matchInProgress &&
            (
                ButtonNext.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf) ||
                    ButtonNextWithImage.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)
            )
        ) {
            return TeamTrialsScreenState.POST_MATCH_RESULTS
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

    private fun handleHomeScreen(): TaskResult? {
        finishReason?.let { reason ->
            MessageLog.i(TAG, "[TEAM_TRIALS] Back on Home after $matchesCompleted match(es): $reason")
            return TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, "TeamTrialsTask completed. Matches run this session: $matchesCompleted. $reason")
        }
        MessageLog.v(TAG, "[STATE] handleHomeScreen:: clicking Race tab in bottom nav.")
        if (ButtonMenuBarRaceUnselected.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleHomeScreen:: Race tab (unselected) button not found.")
            game.wait(1.0)
        }
        return null
    }

    private fun handleRaceTab() {
        if (finishReason != null) {
            goHome()
            return
        }
        MessageLog.v(TAG, "[STATE] handleRaceTab:: clicking Team Trials tile.")
        if (ButtonTeamTrials.click(game.imageUtils)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceTab:: Team Trials tile not found.")
            game.wait(1.0)
        }
    }

    private fun goHome() {
        if (ButtonMenuBarHomeUnselected.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] goHome:: Home tab not found.")
            game.wait(1.0)
        }
    }

    /** RP from the top-bar pips, or the "n/5" counter by OCR when the pips do not read. */
    private fun readRp(bitmap: Bitmap): Int? {
        readRpPips(sampler(bitmap), bitmap.width, bitmap.height)?.let { return it }
        val raw =
            try {
                game.imageUtils.performOCROnRegion(
                    bitmap,
                    770,
                    gameY(52.0, ScreenBand.TOP, bitmap.width, bitmap.height).toInt(),
                    110,
                    40,
                    scale = 3.0,
                    debugName = "team_trials_rp",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                ""
            }
        return parseRpCount(raw).also { MessageLog.i(TAG, "[TEAM_TRIALS] RP pips unreadable; counter OCR \"$raw\" -> $it.") }
    }

    private fun handleTeamTrialsHome(bitmap: Bitmap) {
        matchInProgress = false
        quickModeChecked = false

        if (finishReason == null && matchesCompleted >= maxMatchesPerSession) {
            finishReason = "Reached the per-session cap of $maxMatchesPerSession matches."
        }
        if (finishReason == null) {
            val rp = readRp(bitmap)
            MessageLog.i(TAG, "[TEAM_TRIALS] RP read on the Team Trials home: ${rp ?: "unreadable"}.")
            // An unreadable RP still tries Team Race: at 0 RP the game offers a restore, which is declined.
            if (rp == 0) finishReason = "No RP left."
        }
        if (finishReason != null) {
            goHome()
            return
        }

        MessageLog.v(TAG, "[STATE] handleTeamTrialsHome:: starting match ${matchesCompleted + 1}/$maxMatchesPerSession.")
        if (ButtonTeamRace.click(game.imageUtils)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleTeamTrialsHome:: Team Race button click failed.")
            game.wait(1.5)
        }
    }

    /** Opponent tile art is randomised, so the cards are found by their white bodies and tapped at their centres. */
    private fun handleSelectOpponent(bitmap: Bitmap) {
        val centres = findOpponentCardCentres(sampler(bitmap), bitmap.width, bitmap.height) ?: return
        val row = opponentPick.rowIndex()
        val x = bitmap.width / 2.0
        val y = centres[row].toDouble()
        MessageLog.i(TAG, "[TEAM_TRIALS] Opponent row ${row + 1} of 3 (setting $opponentPick) at ($x, $y).")
        CoordinateTap.tap(game.gestureUtils, x, y, "select_opponent_row_${row + 1}")
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

    /** No item is picked. Race! by its template, else at its place; ButtonRaceConfirm would match the Next behind the popup. */
    private fun handleItemsSelected(bitmap: Bitmap) {
        MessageLog.v(TAG, "[STATE] handleItemsSelected:: skipping item picker, clicking Race!.")
        if (!ButtonRaceExclamationShiftedUp.click(game.imageUtils)) {
            val y = gameY(TeamTrialsGeometry.ITEMS_RACE_Y.toDouble(), ScreenBand.DIALOG, bitmap.width, bitmap.height)
            CoordinateTap.tap(game.gestureUtils, TeamTrialsGeometry.ITEMS_RACE_X.toDouble(), y, "items_selected_race_confirm")
        }
        if (!matchInProgress) {
            matchInProgress = true
            matchesCompleted += 1
            MessageLog.i(TAG, "[TEAM_TRIALS] Match $matchesCompleted committed (1 RP).")
        }
        game.wait(3.0)
    }

    /** RP is never restored, with items or Carats. */
    private fun handleRestoreRpPrompt() {
        MessageLog.i(TAG, "[TEAM_TRIALS] The game offered an RP restore; declining.")
        finishReason = "No RP left; restore declined."
        if (ButtonNo.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleRestoreRpPrompt:: No button not found; retrying.")
            game.wait(1.5)
        }
    }

    /** Daily Sale is dismissed; buying is not automated yet. */
    private fun handleDailySalePopup() {
        MessageLog.i(TAG, "[TEAM_TRIALS] Daily Sale shown; dismissing via Cancel.")
        if (ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleDailySalePopup:: Cancel button not found; retrying.")
            game.wait(1.5)
        }
    }

    private fun handleStandby(bitmap: Bitmap) {
        if (!quickModeChecked) {
            quickModeChecked = true
            if (quickModePillOn(sampler(bitmap), bitmap.width, bitmap.height)) {
                MessageLog.v(TAG, "[TEAM_TRIALS] Quick Mode is ON.")
            } else {
                // The OFF pill has not been captured, so it is not toggled blind.
                MessageLog.w(TAG, "[TEAM_TRIALS] Quick Mode pill did not read ON; leaving it as is.")
            }
        }
        MessageLog.v(TAG, "[STATE] handleStandby:: See All Race Results.")
        if (!ButtonSeeAllRaceResults.click(game.imageUtils, region = Region.bottomHalf)) {
            val y = gameY(TeamTrialsGeometry.SEE_ALL_Y.toDouble(), ScreenBand.BOTTOM, bitmap.width, bitmap.height)
            CoordinateTap.tap(game.gestureUtils, TeamTrialsGeometry.SEE_ALL_X.toDouble(), y, "see_all_race_results_coord_fallback")
        }
        game.wait(2.5)
    }

    private fun handleResultReveal() {
        MessageLog.v(TAG, "[STATE] handleResultReveal:: Skip.")
        if (ButtonSkip.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            game.wait(1.0)
        }
    }

    /** Post-match tail: RACE FINISHED Next, rewards Next, WINNINGS Next, then the chibi Next (not Race Again) to TT home. */
    private fun handlePostMatchResults() {
        if (ButtonCancel.check(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: dismissing popup via Cancel.")
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

        if (ButtonNextWithImage.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked chibi Next.")
            game.wait(2.5)
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

    /** A result splash that showed no Skip advances on a tap at its "TAP" prompt. */
    private fun handleUnknown(bitmap: Bitmap) {
        if (splashTapAllowed(matchInProgress, consecutiveUnknowns, lastKnownStateName)) {
            val y = gameY(TeamTrialsGeometry.SPLASH_TAP_Y.toDouble(), ScreenBand.BOTTOM, bitmap.width, bitmap.height)
            CoordinateTap.tap(game.gestureUtils, TeamTrialsGeometry.SPLASH_TAP_X.toDouble(), y, "result_splash_tap")
        }
        game.wait(1.5)
    }
}

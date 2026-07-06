package com.steve1316.uma_android_automation.bot.campaigns

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.DialogHandlerResult
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.ButtonNextRaceEnd
import com.steve1316.uma_android_automation.components.ButtonSelectOpponent
import com.steve1316.uma_android_automation.components.ButtonSkip
import com.steve1316.uma_android_automation.components.ButtonUnityCupRace
import com.steve1316.uma_android_automation.components.ButtonUnityCupRaceFinal
import com.steve1316.uma_android_automation.components.ButtonUnityCupSeeAllRaceResults
import com.steve1316.uma_android_automation.components.ButtonUnityCupWatchMainRace
import com.steve1316.uma_android_automation.components.DialogInterface
import com.steve1316.uma_android_automation.components.IconDoubleCircle
import com.steve1316.uma_android_automation.components.IconSingleCircle
import com.steve1316.uma_android_automation.components.IconTrainingEventHorseshoe
import com.steve1316.uma_android_automation.components.IconUnityCupRaceEndLogo
import com.steve1316.uma_android_automation.components.IconUnityCupTutorialHeader
import com.steve1316.uma_android_automation.components.LabelUnityCupOpponentSelectionLaurel
import org.opencv.core.Point
import kotlin.math.abs

/**
 * Handles the Unity Cup scenario with scenario-specific logic and handling.
 *
 * @property game The [Game] instance for interacting with the game state.
 */
class UnityCup(game: Game) : Campaign(game) {
    /** Flag indicating if the tutorial has been disabled. */
    private var tutorialDisabled = false

    /** Flag indicating if the bot is currently in the finals. */
    private var bIsFinals: Boolean = false

    /** The index of the currently selected opponent. */
    private var selectedOpponentIndex: Int = 0

    /** Flag indicating if the opponent selection should be overridden. */
    private var bOverrideOpponentSelection: Boolean = false

    /**
     * Best weighted prediction score seen across the three opponents this selection cycle, and the
     * opponent index holding it. When no opponent clears [confidentWinPredictionScore], the
     * confirmation loop races [bestPredictionIndex] (the highest win chance) instead of a fixed
     * position, since a showdown loss costs team rank and stats. Reset when a new selection begins.
     */
    private var bestPredictionScore: Int = -1
    private var bestPredictionIndex: Int = 0

    /**
     * Minimum weighted prediction score (double circle = 2, single circle = 1, across the five
     * discipline slots) for a match to count as a confident win worth taking outright. 6 keeps the
     * old three-double-circle bar and additionally admits strong-singles rows (2 doubles + 2
     * singles, 1 double + 4 singles). Singles substitute for doubles at 2:1 - this is NOT a raw
     * circle count; three singles alone score 3 and fail. Opponents are checked hardest-first, so
     * the first to clear this bar is also the highest-reward safe pick.
     */
    private val confidentWinPredictionScore: Int = 6

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    override fun handleDialogs(dialog: DialogInterface?, args: Map<String, Any>): DialogHandlerResult {
        val result: DialogHandlerResult = super.handleDialogs(dialog, args)
        if (result !is DialogHandlerResult.Unhandled) {
            return result
        }

        when (result.dialog.name) {
            "auto_fill" -> {
                result.dialog.close(game.imageUtils)
            }

            "unity_cup_confirmation" -> {
                if (bIsFinals) {
                    result.dialog.ok(game.imageUtils)
                } else if (bOverrideOpponentSelection || analyzeOpponentRacePrediction()) {
                    result.dialog.ok(game.imageUtils)
                } else {
                    result.dialog.close(game.imageUtils)
                    if (selectedOpponentIndex >= 2) {
                        // All three opponents checked; none cleared the confident-win bar. Race the
                        // opponent with the best weighted prediction (best win chance) instead of a
                        // fixed position - a loss costs team rank and stats. Ties were already
                        // resolved toward the easier opponent in analyzeOpponentRacePrediction.
                        MessageLog.w(
                            TAG,
                            "[WARN] handleDialogs:: No opponent cleared the confident-win bar. Falling back to opponent #${bestPredictionIndex + 1} (best prediction score: $bestPredictionScore).",
                        )
                        selectedOpponentIndex = bestPredictionIndex
                        bOverrideOpponentSelection = true
                    } else {
                        selectedOpponentIndex++
                    }
                }
                game.wait(0.5)
                return DialogHandlerResult.Handled(result.dialog)
            }

            else -> {
                Log.w(TAG, "[WARN] handleDialogs:: Unknown dialog \"${result.dialog.name}\" detected so it will not be handled.")
                return DialogHandlerResult.Unhandled(result.dialog)
            }
        }
        game.wait(0.5)
        return DialogHandlerResult.Handled(result.dialog)
    }

    override fun handleTrainingEvent() {
        if (!tutorialDisabled) {
            tutorialDisabled =
                if (IconUnityCupTutorialHeader.check(game.imageUtils)) {
                    // If the tutorial is detected, select the second option to close it.
                    MessageLog.i(TAG, "\n[UNITY_CUP] Detected tutorial for Unity Cup. Closing it now...")
                    val trainingOptionLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
                    if (trainingOptionLocations.size >= 2) {
                        game.gestureUtils.tap(trainingOptionLocations[1].x, trainingOptionLocations[1].y, IconTrainingEventHorseshoe.template.path)
                        true
                    } else {
                        // A partial render or capture can match fewer than 2 horseshoes; indexing
                        // [1] crashed the run. Stay un-dismissed and retry on the next tick.
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Tutorial header detected but only ${trainingOptionLocations.size} option(s) found. Retrying next tick.")
                        false
                    }
                } else {
                    MessageLog.i(TAG, "\n[UNITY_CUP] Tutorial must have already been dismissed.")
                    super.handleTrainingEvent()
                    true
                }
        } else {
            super.handleTrainingEvent()
        }
    }

    override fun handleRaceEvents(isScheduledRace: Boolean): Boolean {
        if (ButtonUnityCupRace.check(game.imageUtils)) {
            // Handle the Unity Cup race.
            MessageLog.i(TAG, "[UNITY_CUP] Will start the process for Unity Cup race handling.")
            handleRaceEventsUnityCup()
            return true
        }

        // Fall back to the regular race handling logic.
        return super.handleRaceEvents(isScheduledRace)
    }

    override fun checkCampaignSpecificConditions(): Boolean {
        return handleRaceEventsUnityCup()
    }

    /**
     * Scores the currently-selected opponent's prediction row on the confirmation screen (five
     * discipline slots; double circle = 2 points, single circle = 1) and decides whether the match
     * is a confident win. Also records the score against the running best so the exhaustion
     * fallback in [handleDialogs] can race the highest-win-chance opponent.
     *
     * @return True if the weighted score clears [confidentWinPredictionScore], false otherwise.
     */
    private fun analyzeOpponentRacePrediction(): Boolean {
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        // 0.85 instead of the 0.8 default: true glyphs self-match at 0.98+, while the double
        // template scores ~0.79 on a bold single-circle ring and generic ring ornaments reach
        // ~0.83 - the higher bar keeps both cross-fire classes out without costing real matches.
        val doubleCircles = IconDoubleCircle.findAll(game.imageUtils, sourceBitmap = sourceBitmap, region = game.imageUtils.regionMiddle, confidence = 0.85)
        // The single-circle template is the double's outer ring, so it can weakly co-match on a
        // double-circle glyph. Any single match within half a glyph (~20px) of a double is the same
        // slot and gets dropped; real slots sit ~197px apart.
        var singleCircles =
            IconSingleCircle.findAll(game.imageUtils, sourceBitmap = sourceBitmap, region = game.imageUtils.regionMiddle, confidence = 0.85)
                .count { single -> doubleCircles.none { double -> abs(double.x - single.x) < 20 && abs(double.y - single.y) < 20 } }
        // The row has exactly five slots. Counts beyond that mean a template is false-firing
        // somewhere in the region; clamp so a phantom match cannot quietly lower the win bar.
        if (doubleCircles.size + singleCircles > 5) {
            MessageLog.w(
                TAG,
                "[WARN] analyzeOpponentRacePrediction:: Implausible prediction counts (${doubleCircles.size} double + $singleCircles single > 5 slots). Clamping singles; check the single_circle template for false matches.",
            )
            singleCircles = (5 - doubleCircles.size).coerceAtLeast(0)
        }
        val score = doubleCircles.size * 2 + singleCircles

        // Track the strongest prediction seen so far. >= breaks ties toward the later (weaker/easier)
        // opponent, the safer bet once no opponent clears the confident-win bar. Opponents are always
        // checked in top-to-bottom (hardest-to-easiest) order, so the last tie is the easiest.
        if (score >= bestPredictionScore) {
            bestPredictionScore = score
            bestPredictionIndex = selectedOpponentIndex
        }

        return if (score >= confidentWinPredictionScore) {
            MessageLog.i(
                TAG,
                "[UNITY_CUP] Opponent #${selectedOpponentIndex + 1} predictions: ${doubleCircles.size} double + $singleCircles single = score $score; a confident win. Selecting it now...",
            )
            true
        } else {
            MessageLog.i(
                TAG,
                "[UNITY_CUP] Opponent #${selectedOpponentIndex + 1} predictions: ${doubleCircles.size} double + $singleCircles single = score $score; below the confident-win bar. Checking the next opponent.",
            )
            false
        }
    }

    /**
     * Handles the scenario-specific process for Unity Cup races.
     *
     * @return True if the race sequence was completed, false otherwise.
     */
    private fun handleRaceEventsUnityCup(): Boolean {
        MessageLog.i(TAG, "[UNITY_CUP] Starting process for handling the Unity Cup racing process.")

        // If none of these exist then we aren't in any Unity Cup screens at the moment. Abort.
        if (!ButtonUnityCupRace.check(game.imageUtils) && !ButtonUnityCupRaceFinal.check(game.imageUtils) && !ButtonUnityCupWatchMainRace.check(game.imageUtils)) {
            return false
        }

        // We use this as a means of exiting the loop if it runs too long.
        val executionTimeThresholdMs = 30000 // 30 seconds.
        val startTime = System.currentTimeMillis()

        while (true) {
            val sourceBitmap: Bitmap = game.imageUtils.getSourceBitmap()
            when {
                handleDialogs() is DialogHandlerResult.Handled -> {}

                // Go to opponent selection screen.
                ButtonUnityCupRace.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                    selectedOpponentIndex = 0
                    bOverrideOpponentSelection = false
                    bestPredictionScore = -1
                    bestPredictionIndex = 0
                    game.waitForLoading()
                }

                ButtonUnityCupRaceFinal.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                    MessageLog.i(TAG, "[UNITY_CUP] Final race detected with Team Zenith.")
                    bIsFinals = true
                    game.waitForLoading()
                }

                // Handle opponent selection.
                ButtonSelectOpponent.check(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                    val opponents: ArrayList<Point> = LabelUnityCupOpponentSelectionLaurel.findAll(game.imageUtils, sourceBitmap = sourceBitmap)
                    if (opponents.size != 3) {
                        MessageLog.e(TAG, "[ERROR] handleRaceEventsUnityCup:: Failed to detect all three opponents on opponent selection screen.")
                        return false
                    }
                    // findAll order is not guaranteed; index semantics are top-to-bottom.
                    opponents.sortBy { it.y }

                    selectedOpponentIndex = selectedOpponentIndex.coerceIn(0, opponents.lastIndex)
                    val opponent = opponents[selectedOpponentIndex]
                    game.gestureUtils.tap(opponent.x, opponent.y, LabelUnityCupOpponentSelectionLaurel.template.path)
                    // Tiny delay to allow the opponent selection click to register fully.
                    game.wait(0.1, skipWaitingForLoading = true)
                    MessageLog.i(TAG, "[UNITY_CUP] Selecting opponent #${selectedOpponentIndex + 1} at $opponent.")
                    ButtonSelectOpponent.click(game.imageUtils, sourceBitmap = sourceBitmap)
                    // Clicking SelectOpponent requires connect to server. Don't skip waiting for loading otherwise we might miss handling a dialog.
                    game.wait(game.dialogWaitDelay)
                }

                // If the skip button is locked, need to manually run the race.
                ButtonUnityCupSeeAllRaceResults.check(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                    when (ButtonUnityCupSeeAllRaceResults.checkDisabled(game.imageUtils, sourceBitmap)) {
                        // Manually run the race.
                        true -> {
                            MessageLog.d(TAG, "[DEBUG] handleRaceEventsUnityCup:: See All Race Results button is locked. Manually running race...")
                            if (ButtonUnityCupWatchMainRace.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                                MessageLog.i(TAG, "[INFO] Clicked Watch Main Race button.")
                                game.waitForLoading()
                                racing.runRaceWithRetries()
                            } else {
                                MessageLog.w(TAG, "[WARN] handleRaceEventsUnityCup:: Failed to click the Watch Main Race button.")
                            }
                        }

                        // Skip the race.
                        false -> {
                            if (ButtonUnityCupSeeAllRaceResults.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                                MessageLog.i(TAG, "[INFO] Clicked the See All Race Results button to skip the race.")
                                game.waitForLoading()
                            } else {
                                MessageLog.w(TAG, "[WARN] handleRaceEventsUnityCup:: Failed to click the See All Race Results button.")
                            }
                        }

                        // Shouldn't ever fail this since we already detected it once.
                        null -> {
                            MessageLog.e(TAG, "[ERROR] handleRaceEventsUnityCup:: Detected See All Race Results button, but then failed to check its disabled state.")
                        }
                    }
                }

                // This is our only natural exit point from this function.
                IconUnityCupRaceEndLogo.check(game.imageUtils, sourceBitmap = sourceBitmap) && ButtonNext.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                    MessageLog.i(TAG, "[INFO] Race event completed.")
                    return true
                }

                ButtonNext.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {}

                ButtonSkip.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {}

                ButtonNextRaceEnd.click(game.imageUtils, sourceBitmap = sourceBitmap) -> {
                    // Clicking this button triggers connection to server.
                    game.waitForLoading()
                }

                // Exit from function if it runs too long.
                System.currentTimeMillis() - startTime > executionTimeThresholdMs -> {
                    MessageLog.w(TAG, "[WARN] handleRaceEventsUnityCup:: Race event took too long to complete. Aborting...")
                    // Clear selection state so a later re-entry starts a clean cycle. The reset in
                    // the Race-button branch is skipped when recovery taps reach the selection
                    // screen another way, and a leftover bOverrideOpponentSelection would insta-OK
                    // the first opponent of the NEXT showdown with no analysis.
                    selectedOpponentIndex = 0
                    bOverrideOpponentSelection = false
                    bestPredictionScore = -1
                    bestPredictionIndex = 0
                    return false
                }

                // Tap on the screen to skip past any intermediate screens.
                else -> {
                    game.tap(350.0, 750.0, taps = 3)
                }
            }
        }
    }
}

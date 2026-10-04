package com.steve1316.uma_android_automation.bot.campaigns

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.DialogHandlerResult
import com.steve1316.uma_android_automation.bot.EnteredRace
import com.steve1316.uma_android_automation.bot.EnteredRacePath
import com.steve1316.uma_android_automation.bot.EnteredRaceResolution
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.OwnUiForeground
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
     * Best weighted prediction score seen across the three opponents this cycle, and its index; the fallback races
     * [bestPredictionIndex] since a showdown loss costs team rank and stats. Reset when a new selection begins.
     */
    private var bestPredictionScore: Int = -1
    private var bestPredictionIndex: Int = 0

    /**
     * Minimum weighted score (double circle = 2, single = 1, five slots) for a confident win; 6 also admits
     * strong-singles rows (2 doubles + 2 singles, 1 double + 4 singles). Not a raw circle count: three singles score 3
     * and fail.
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
                        // None cleared the confident-win bar: race the best-scoring opponent (ties already resolved
                        // toward the easier one).
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
                        OwnUiForeground.waitForGame()
                        game.gestureUtils.tap(trainingOptionLocations[1].x, trainingOptionLocations[1].y, IconTrainingEventHorseshoe.template.path)
                        true
                    } else {
                        // A partial render can match fewer than 2 horseshoes (indexing [1] crashed the run); stay
                        // un-dismissed and retry.
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
            // Record a non-catalog entered-race fact only when the showdown actually completed (the inner function
            // returns false on abort/timeout).
            if (handleRaceEventsUnityCup()) {
                recordEnteredRace(EnteredRace(date.day, EnteredRaceResolution.NON_CATALOG, EnteredRacePath.UNITY_CUP_SHOWDOWN))
            }
            return true
        }

        // Fall back to the regular race handling logic.
        return super.handleRaceEvents(isScheduledRace)
    }

    override fun checkCampaignSpecificConditions(): Boolean {
        return handleRaceEventsUnityCup()
    }

    /**
     * Scores the selected opponent's prediction row (double circle = 2, single = 1) and records it against the running
     * best for the exhaustion fallback. Returns true when it clears [confidentWinPredictionScore].
     */
    private fun analyzeOpponentRacePrediction(): Boolean {
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        // 0.85, not 0.8: true glyphs self-match at 0.98+, the double template scores ~0.79 on a bold single ring and
        // ring ornaments reach ~0.83.
        val doubleCircles = IconDoubleCircle.findAll(game.imageUtils, sourceBitmap = sourceBitmap, region = game.imageUtils.regionMiddle, confidence = 0.85)
        // The single template is the double's outer ring, so it can co-match on a double; singles within ~20px of a
        // double are the same slot and are dropped (real slots sit ~197px apart).
        var singleCircles =
            IconSingleCircle.findAll(game.imageUtils, sourceBitmap = sourceBitmap, region = game.imageUtils.regionMiddle, confidence = 0.85)
                .count { single -> doubleCircles.none { double -> abs(double.x - single.x) < 20 && abs(double.y - single.y) < 20 } }
        // The row has exactly five slots; clamp so a false-firing template cannot lower the win bar.
        if (doubleCircles.size + singleCircles > 5) {
            MessageLog.w(
                TAG,
                "[WARN] analyzeOpponentRacePrediction:: Implausible prediction counts (${doubleCircles.size} double + $singleCircles single > 5 slots). Clamping singles; check the single_circle template for false matches.",
            )
            singleCircles = (5 - doubleCircles.size).coerceAtLeast(0)
        }
        val score = doubleCircles.size * 2 + singleCircles

        // Ties go to the later (easier) opponent; opponents are checked hardest-to-easiest.
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
        // The race-end logo (post-race standings) is part of the gate too: if a showdown call ever
        // returns before its Next-exit, the career loop re-enters here on the standings screen, and
        // without this the gate would reject it (no race-start button present) and strand the screen
        // as "unknown" until the 25-cycle career abort - the exact death seen on 2026-07-08.
        if (!ButtonUnityCupRace.check(game.imageUtils) &&
            !ButtonUnityCupRaceFinal.check(game.imageUtils) &&
            !ButtonUnityCupWatchMainRace.check(game.imageUtils) &&
            !IconUnityCupRaceEndLogo.check(game.imageUtils)
        ) {
            return false
        }

        // Exit-loop guard: a full showdown runs ~30s on the See-All skip path and longer when watched; a 30s cap once
        // timed out 0.1s before the Next button finished sliding in. 120s leaves margin and the stall watchdog still
        // backstops a true hang.
        val executionTimeThresholdMs = 120000 // 2 minutes.
        var startTime = System.currentTimeMillis()

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
                    OwnUiForeground.waitForGame()
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
                    // Clear selection state: recovery taps can reach the selection screen without the Race-button
                    // reset, and a leftover bOverrideOpponentSelection would insta-OK the next showdown's first
                    // opponent.
                    selectedOpponentIndex = 0
                    bOverrideOpponentSelection = false
                    bestPredictionScore = -1
                    bestPredictionIndex = 0
                    return false
                }

                // Tap on the screen to skip past any intermediate screens. Time held for our own screen stays off the cap.
                else -> {
                    val heldMs = game.heldMsForOwnUi()
                    if (heldMs != null) startTime += heldMs else game.tap(350.0, 750.0, taps = 3)
                }
            }
        }
    }
}

package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
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
import com.steve1316.uma_android_automation.components.ButtonRaceConfirm
import com.steve1316.uma_android_automation.components.ButtonSeeAllRaceResults
import com.steve1316.uma_android_automation.components.ButtonSelectOpponent
import com.steve1316.uma_android_automation.components.ButtonTeamRace
import com.steve1316.uma_android_automation.components.ButtonTeamTrials
import com.steve1316.uma_android_automation.components.LabelItemsSelected
import com.steve1316.uma_android_automation.components.LabelTeamTrials
import com.steve1316.uma_android_automation.components.Region

/**
 * Misc automation task for the Team Trials mode.
 *
 * ## Flow (as confirmed by the user on 2026-04-18)
 *
 * ```
 * Home Screen
 *   └► Race tab
 *        └► Team Trials tile
 *             └► Team Trials home (Class N banner, rank, Team Race button)
 *                  └► Team Race button
 *                       └► Select Opponent (3 opponents, sorted top=strongest → bottom=weakest)
 *                            └► Pick opponent by difficulty preference (default: BOTTOM = easiest)
 *                                 └► Team Preview (Flusi vs enemy, 3 races, Next button)
 *                                      └► Items Selected popup (skip item picking)
 *                                           └► Race! (RP Cost: 1)
 *                                                └► Match auto-plays with Quick Mode: On
 *                                                     └► See All Race Results
 *                                                          └► Result screens (variable)
 *                                                               └► Back on Team Trials home
 * ```
 *
 * The tail of the flow (from match-end back to TT home) is implemented as a single
 * advance-through-result-screens state that keeps clicking the first available
 * recognised button until we detect the Team Trials home banner again.
 *
 * ## Resource accounting
 *
 * Each match costs **1 RP**. The state machine loops matches until either
 * [maxMatchesPerSession] is hit or the game stops us (Team Race button disabled /
 * opponent list empty). Future versions can invoke ResourceRechargeHelper to top up
 * RP when enabled.
 *
 * ## Configuration (via [SettingsHelper], namespace `"miscTeamTrials"`)
 *
 * - `opponentPick` (string): `"TOP"` | `"MIDDLE"` | `"BOTTOM"`. Default: `"BOTTOM"` for
 *   the safest default win-rate. Matches the user's rule-of-thumb that opponents are
 *   always sorted top=strongest → bottom=weakest, so picking the bottom row is the
 *   easiest fight.
 * - `maxMatchesPerSession` (int, default 5): caps the per-session match count. The game
 *   already caps us at RP (5 matches per full pool); this is a belt-and-suspenders fuse.
 *
 * @property game The [Game] instance used for bot interaction.
 */
class TeamTrialsTask(game: Game) : MiscTask(game) {
    /** Finite-state machine states for the Team Trials flow. */
    enum class TeamTrialsScreenState {
        /** Game main menu with bottom nav. Bot needs to click Race tab. */
        HOME_SCREEN,

        /** Race tab showing 4 mode tiles. */
        RACE_TAB,

        /** Team Trials home (Class banner, rank, Team Race button). Terminal between-match state. */
        TEAM_TRIALS_HOME,

        /** Select Opponent screen with 3 opponent rows + refresh button. */
        SELECT_OPPONENT,

        /** Team Preview — Flusi vs enemy, race lineup, Next button at bottom. */
        TEAM_PREVIEW,

        /** "Items Selected" popup asking which consumables to use. Bot skips and commits Race!. */
        ITEMS_SELECTED_POPUP,

        /** Match is playing (Quick Mode: On fast-forwards to the result screen). */
        IN_MATCH,

        /** Post-match result screens — bot keeps advancing until back at TEAM_TRIALS_HOME. */
        POST_MATCH_RESULTS,

        /** Terminal: out of RP or hit max-matches cap. */
        COMPLETE,

        /** Screen could not be identified. Triggers safety bailout after N consecutive. */
        UNKNOWN,
    }

    /** Opponent picking strategy. */
    enum class OpponentPick {
        /** Top row = strongest opponent. Highest risk, highest point reward. */
        TOP,

        /** Middle row = moderate. Balanced win-rate vs points. */
        MIDDLE,

        /** Bottom row = weakest opponent. Safest default. */
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

    /** Number of matches completed this session. Used to enforce [maxMatchesPerSession]. */
    private var matchesCompleted: Int = 0

    /** True once we've committed the Race! on the current match and are waiting for it to resolve. */
    private var matchInProgress: Boolean = false

    override fun process(): TaskResult? {
        checkSafetyRails()?.let { return it }

        val sourceBitmap = captureSourceBitmap()
        val currentState = detectScreenState(sourceBitmap)
        trackProgress(currentState.name, currentState == TeamTrialsScreenState.UNKNOWN)

        MessageLog.v(TAG, "[STATE] iter=$iterationsCompleted state=$currentState matches=$matchesCompleted")

        // Dismiss any incidental popup before dispatching. Cheap if nothing's there.
        // We do NOT dismiss the Items Selected popup or Daily Sale via this path — those
        // are handled as named states below so the bot commits the correct action.
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
                // Game is fast-forwarding through the match (Quick Mode: On). Poll.
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
        // Items Selected popup check first — its dismissal path is different from
        // handleIncidentalPopups, so we must identify it before the popup handler fires.
        if (LabelItemsSelected.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.ITEMS_SELECTED_POPUP
        }

        // Select Opponent banner is unique to that screen.
        if (ButtonSelectOpponent.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.SELECT_OPPONENT
        }

        // Team Trials home — the Team Race button is unique to this screen and is only
        // visible when we're NOT in a sub-screen (opponent select, preview, match, results).
        if (ButtonTeamRace.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.TEAM_TRIALS_HOME
        }

        // See All Race Results button — distinctive post-match.
        if (ButtonSeeAllRaceResults.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return TeamTrialsScreenState.POST_MATCH_RESULTS
        }

        // Team Trials label at top-left — catch-all for any TT screen that isn't one of
        // the specifically-detected ones above. Must come BEFORE the bottom-nav fallbacks
        // because the Race bottom-nav tab is highlighted on every TT screen, not just the
        // outer Race tab — so without this ordering Team Preview / post-match screens get
        // misclassified as RACE_TAB and the bot loops trying to click the TT tile.
        // We distinguish pre/post-match via the [matchInProgress] flag which is set true
        // when we commit Race! on the Items Selected popup and reset false on TT home.
        if (LabelTeamTrials.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return if (matchInProgress) {
                TeamTrialsScreenState.POST_MATCH_RESULTS
            } else {
                TeamTrialsScreenState.TEAM_PREVIEW
            }
        }

        // Outer navigation — only fires when NOT inside a Team Trials screen (those have
        // the TT header label above and would have returned earlier).

        // Race tab — bottom nav Race tab selected, on the 4-tile Race menu.
        if (ButtonMenuBarRaceSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return TeamTrialsScreenState.RACE_TAB
        }

        // Home Screen — bottom nav's Home tab is selected.
        if (ButtonMenuBarHomeSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return TeamTrialsScreenState.HOME_SCREEN
        }

        return TeamTrialsScreenState.UNKNOWN
    }

    // ------------------------------------------------------------------------
    // Per-state handlers
    // ------------------------------------------------------------------------

    /**
     * Click Race tab in the bottom nav to navigate to the Race tab.
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
     * On the Race tab: click Team Trials tile to enter Team Trials home.
     */
    private fun handleRaceTab() {
        MessageLog.v(TAG, "[STATE] handleRaceTab:: clicking Team Trials tile.")
        if (ButtonTeamTrials.click(game.imageUtils)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceTab:: Team Trials tile not found.")
            game.wait(1.0)
        }
    }

    /**
     * On Team Trials home: either commit another match (Team Race) or exit cleanly if
     * we've hit the per-session cap. RP check happens upstream in the queue runner —
     * by the time we're here, we assume at least 1 RP is available.
     */
    private fun handleTeamTrialsHome(): TaskResult? {
        if (matchesCompleted >= maxMatchesPerSession) {
            MessageLog.v(TAG, "[STATE] handleTeamTrialsHome:: reached cap ($maxMatchesPerSession). Exiting.")
            return TaskResult.Success(
                TaskResultCode.TASK_RESULT_COMPLETE,
                "TeamTrialsTask: reached per-session cap of $maxMatchesPerSession matches.",
            )
        }

        // Reset the in-progress flag now that we're back at home between matches.
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

    /**
     * Pick an opponent by the configured strategy. Because opponent tile art is unique per
     * opponent (randomised from the whole roster), we cannot use template matching to
     * identify "the bottom row". Instead we compute click coordinates as ratios of the
     * display dimensions — the Select Opponent screen has the 3 rows at stable vertical
     * positions.
     */
    private fun handleSelectOpponent() {
        // Stable ratios measured from the 2026-04-18 captures on a 1080x1920 Android
        // render. The 3 opponent rows occupy roughly y=0.19..0.32 (top), 0.34..0.47
        // (middle), 0.49..0.62 (bottom). We click the vertical center of each.
        val ratioY: Double = when (opponentPick) {
            OpponentPick.TOP -> 0.26
            OpponentPick.MIDDLE -> 0.41
            OpponentPick.BOTTOM -> 0.56
        }

        val x: Double = SharedData.displayWidth * 0.5
        val y: Double = SharedData.displayHeight * ratioY

        MessageLog.v(TAG, "[STATE] handleSelectOpponent:: picking $opponentPick row at ($x, $y).")
        game.gestureUtils.tap(x, y, "select_opponent_${opponentPick.name.lowercase()}")
        game.wait(2.5)
    }

    /**
     * Team Preview → click Next to advance to the Items Selected popup.
     */
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
     * Items Selected popup → skip item picking and commit Race! directly.
     *
     * The popup always has a green Race! button (with "RP Cost: 1" subtitle) regardless of
     * whether items are selected. Bot never picks items — minimum viable automation, user
     * can always pre-set favourites.
     *
     * Uses coordinate-based tap rather than ButtonRaceConfirm template because the latter
     * also matches the Team Preview's "Next" button visible behind the popup, causing the
     * click to land on the dimmed underlay instead of the popup's actual Race! button.
     * Pixel-measured location: x=565-975, y=1320-1420 → center (770, 1370) → ratios.
     */
    private fun handleItemsSelected() {
        MessageLog.v(TAG, "[STATE] handleItemsSelected:: skipping item picker, clicking Race!.")
        val x: Double = SharedData.displayWidth * 0.713
        val y: Double = SharedData.displayHeight * 0.714
        game.gestureUtils.tap(x, y, "items_selected_race_confirm")
        matchInProgress = true
        game.wait(3.0)
    }

    /**
     * Post-match cascade: advance through result screens by clicking whichever recognised
     * button is present. The known tail (from the 2026-04-18 live test run) is:
     *   [See All Race Results] → Next → [Story Unlocked popup] Close → Next (chibi) → TT home
     * Daily Sale popup may also appear — handled by Cancel.
     *
     * After the cascade we land back on Team Trials Home; the next iteration's
     * [handleTeamTrialsHome] increments [matchesCompleted] and starts the next match.
     */
    private fun handlePostMatchResults() {
        // Try See All Race Results first — only appears once per match.
        if (ButtonSeeAllRaceResults.check(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: See All Race Results.")
            ButtonSeeAllRaceResults.click(game.imageUtils)
            game.wait(2.5)
            return
        }

        // Cancel incidental popups like Daily Sale — user can shop manually.
        if (ButtonCancel.check(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: dismissing Daily Sale / cancel popup.")
            ButtonCancel.click(game.imageUtils)
            game.wait(2.0)
            return
        }

        // Close — for Story Unlocked popups or similar.
        if (ButtonClose.check(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: dismissing popup via Close.")
            ButtonClose.click(game.imageUtils)
            game.wait(2.0)
            return
        }

        // Chibi Next (green with two chibi characters) — the post-result button.
        // Clicking it transitions us out of the post-match cascade.
        if (ButtonNextWithImage.click(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked chibi Next.")
            game.wait(2.5)
            matchesCompleted += 1
            return
        }

        // Generic Next — bot's normal fallback advance.
        if (ButtonNext.click(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked generic Next.")
            game.wait(2.0)
            return
        }

        // OK / Confirm as final fallbacks.
        if (ButtonOk.click(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked OK.")
            game.wait(2.0)
            return
        }

        if (ButtonConfirm.click(game.imageUtils)) {
            MessageLog.v(TAG, "[STATE] handlePostMatchResults:: clicked Confirm.")
            game.wait(2.0)
            return
        }

        MessageLog.w(TAG, "[WARN] handlePostMatchResults:: no advance button found; waiting.")
        game.wait(2.0)
    }
}

package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.CoordinateTap
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonComplete
import com.steve1316.uma_android_automation.components.ButtonConfirm
import com.steve1316.uma_android_automation.components.ButtonDailyProgramTile
import com.steve1316.uma_android_automation.components.ButtonDailyRaces
import com.steve1316.uma_android_automation.components.ButtonDailyRacesJupiterCup
import com.steve1316.uma_android_automation.components.ButtonDailyRacesMoonlightSho
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarHomeUnselected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceSelected
import com.steve1316.uma_android_automation.components.ButtonMenuBarRaceUnselected
import com.steve1316.uma_android_automation.components.ButtonMultiRaceOff
import com.steve1316.uma_android_automation.components.ButtonMultiRaceOn
import com.steve1316.uma_android_automation.components.ButtonRaceConfirm
import com.steve1316.uma_android_automation.components.LabelDailyPrograms
import com.steve1316.uma_android_automation.components.LabelDailyRacesHeader
import com.steve1316.uma_android_automation.components.LabelDailySale
import com.steve1316.uma_android_automation.components.LabelMultiRacePopup
import com.steve1316.uma_android_automation.components.LabelRaceDetails
import com.steve1316.uma_android_automation.components.LabelRaceResultsHeader
import com.steve1316.uma_android_automation.components.LabelRunnerSelection
import com.steve1316.uma_android_automation.components.Region
import com.steve1316.uma_android_automation.utils.DailyRaceGeometry
import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.difficultyRow
import com.steve1316.uma_android_automation.utils.difficultyRowTapY
import com.steve1316.uma_android_automation.utils.gameY
import com.steve1316.uma_android_automation.utils.parseTicketCount

/**
 * Daily Races: Home -> Race tab -> Daily Program -> Daily Races -> race -> difficulty -> Race Details (Multi-Race: On)
 * -> Runner Selection (the game's pick) -> Multi-Race popup -> Race! with all held tickets -> Race Results ->
 * Total Rewards Close -> Daily Sale Cancel -> Home. One multi-race spends every ticket; the session ends on Home.
 *
 * Settings (namespace "miscDailyRace"): targetRace ("Moonlight Sho" | "Jupiter Cup"), targetDifficulty (default
 * "VERY_HARD"), ensureMultiRaceOn (default true: turn the pill On when it reads Off; false leaves the pill alone
 * and skips the session when it reads Off). Daily Races never run single races.
 */
class DailyRaceTask(game: Game) : MiscTask(game) {
    enum class DailyRaceScreenState {
        HOME_SCREEN,

        RACE_TAB,

        DAILY_PROGRAMS_CONTAINER,

        DAILY_RACES_RACE_PICK,

        DAILY_RACES_DIFFICULTY_PICK,

        RACE_DETAILS,

        RUNNER_SELECTION,

        MULTI_RACE_POPUP,

        /** Each race's result, then Total Rewards, under one "Race Results" header. */
        RACE_RESULTS,

        DAILY_SALE_POPUP,

        /** Back on Home once the session is over. */
        COMPLETE,

        UNKNOWN,
    }

    companion object {
        /** OCR gets this many Daily Races screens to read the ticket counter before the session stops unraced. */
        const val MAX_TICKET_READS = 3

        /** Looks at the race pick before the configured race counts as out of rotation; the list can still be animating in. */
        const val MAX_RACE_TILE_MISSES = 2

        /**
         * Runner Selection follows Race! on Race Details, which the shared dialog handler can also tap when the header
         * misses. Only a Race! this task sent after reading Multi-Race: On may lead on to the race.
         */
        internal fun runnerConfirmAllowed(
            multiRaceVerified: Boolean,
            sessionOver: Boolean,
            ticketsHeld: Int?,
        ): Boolean = multiRaceVerified && !sessionOver && (ticketsHeld ?: 0) > 0

        /** A difficulty list this task did not open from the configured race tile is never raced on: start over once, then stop. */
        internal fun difficultyListStep(
            racePicked: Boolean,
            startedOver: Boolean,
        ): DifficultyListStep =
            when {
                racePicked -> DifficultyListStep.TAP
                !startedOver -> DifficultyListStep.START_OVER
                else -> DifficultyListStep.STOP
            }

        /** Why a committed multi-race cannot be reported as done; null when it can. A tap alone is not proof the races ran. */
        internal fun multiRaceFailure(
            committed: Boolean,
            resultsSeen: Boolean,
            ticketsLeft: Int?,
        ): String? =
            when {
                !committed -> null
                !resultsSeen -> "Race! was tapped on the Multi-Race popup, but no race results were seen."
                ticketsLeft != null && ticketsLeft > 0 -> "$ticketsLeft ticket(s) still held after the multi-race."
                else -> null
            }
    }

    enum class DifficultyListStep {
        TAP,

        START_OVER,

        STOP,
    }

    private val targetRaceName: String =
        SettingsHelper.getStringSetting("miscDailyRace", "targetRace", "Moonlight Sho")

    private val targetDifficulty: String =
        SettingsHelper.getStringSetting("miscDailyRace", "targetDifficulty", "VERY_HARD")

    private val ensureMultiRaceOn: Boolean =
        SettingsHelper.getBooleanSetting("miscDailyRace", "ensureMultiRaceOn", true)

    /** Tickets read on the Daily Races screen before racing; the multi-race spends all of them. */
    private var ticketsHeld: Int? = null

    private var ticketReads: Int = 0

    private var multiRaceToggled: Boolean = false

    /** Set right before this task taps Race! with Multi-Race read On. */
    private var multiRaceVerified: Boolean = false

    private var raceTileMisses: Int = 0

    /** Set when this task tapped the configured race's tile; only then is the difficulty list that race's. */
    private var racePicked: Boolean = false

    private var startedOverForRace: Boolean = false

    /** Set once Race Results or Total Rewards shows after the commit. */
    private var resultsSeen: Boolean = false

    /** The counter read back on the Daily Races screen after the multi-race; null when not read or unreadable. */
    private var ticketsLeft: Int? = null

    /** Consecutive frames with the Daily Races header and no race logo; one alone may be the race pick still loading. */
    private var headerOnlyFrames: Int = 0

    /** Set on the popup's Race! tap, which commits every held ticket. */
    private var raceSequenceCommitted: Boolean = false

    /** Set when no race will start; the task then walks back to Home. */
    private var finishReason: String? = null

    /** The finish reason is a refusal (could not verify, bad setting), not "no tickets" or "done". */
    private var finishFailed: Boolean = false

    private val sessionOver: Boolean
        get() = raceSequenceCommitted || finishReason != null

    override fun process(): TaskResult? {
        checkSafetyRails()?.let { return it }

        val sourceBitmap = captureSourceBitmap()
        val currentState = detectScreenState(sourceBitmap)
        trackProgress(currentState.name, currentState == DailyRaceScreenState.UNKNOWN)

        MessageLog.v(TAG, "[STATE] iter=$iterationsCompleted state=$currentState committed=$raceSequenceCommitted")

        // These are named states below; the shared handler would tap Race! on Race Details without the Multi-Race check.
        val ownDialog =
            currentState == DailyRaceScreenState.RACE_DETAILS ||
                currentState == DailyRaceScreenState.MULTI_RACE_POPUP ||
                currentState == DailyRaceScreenState.RACE_RESULTS ||
                currentState == DailyRaceScreenState.DAILY_SALE_POPUP
        if (!ownDialog && handleIncidentalPopups()) {
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
                handleDailyRacesScreen(sourceBitmap, onRacePick = true)
                null
            }

            DailyRaceScreenState.DAILY_RACES_DIFFICULTY_PICK -> {
                handleDailyRacesScreen(sourceBitmap, onRacePick = false)
                null
            }

            DailyRaceScreenState.RACE_DETAILS -> {
                handleRaceDetails(sourceBitmap)
                null
            }

            DailyRaceScreenState.RUNNER_SELECTION -> {
                handleRunnerSelection()
                null
            }

            DailyRaceScreenState.MULTI_RACE_POPUP -> {
                handleMultiRacePopup(sourceBitmap)
                null
            }

            DailyRaceScreenState.RACE_RESULTS -> {
                if (raceSequenceCommitted) resultsSeen = true
                handleRaceResults()
                null
            }

            DailyRaceScreenState.DAILY_SALE_POPUP -> {
                handleDailySalePopup()
                null
            }

            DailyRaceScreenState.COMPLETE -> {
                val races = if (raceSequenceCommitted) ticketsHeld ?: 0 else 0
                val reason = finishReason ?: "Multi-race done."
                MessageLog.i(TAG, "[DAILY_RACES] Back on Home after $races race(s): $reason")
                val unconfirmed = multiRaceFailure(raceSequenceCommitted, resultsSeen, ticketsLeft)
                when {
                    finishFailed -> TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, "DailyRaceTask stopped on Home without racing. $reason")
                    unconfirmed != null -> {
                        MessageLog.w(TAG, "[DAILY_RACES] $unconfirmed")
                        TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, "DailyRaceTask could not confirm the multi-race. $unconfirmed")
                    }
                    else -> TaskResult.Success(TaskResultCode.TASK_RESULT_COMPLETE, "DailyRaceTask completed. Races run this session: $races. $reason")
                }
            }

            DailyRaceScreenState.UNKNOWN -> {
                game.wait(1.5)
                null
            }
        }
    }

    /** Dialogs first: each overlays a screen whose own header stays visible beneath it. */
    private fun detectScreenState(sourceBitmap: Bitmap): DailyRaceScreenState {
        val priorHeaderOnlyFrames = headerOnlyFrames
        headerOnlyFrames = 0

        if (LabelDailySale.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.DAILY_SALE_POPUP
        }

        if (LabelMultiRacePopup.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.MULTI_RACE_POPUP
        }

        if (LabelRaceResultsHeader.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.RACE_RESULTS
        }

        if (LabelRaceDetails.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.RACE_DETAILS
        }

        if (LabelRunnerSelection.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            return DailyRaceScreenState.RUNNER_SELECTION
        }

        // The purple header shows on both race pick and difficulty pick; a race tile logo means race pick.
        if (LabelDailyRacesHeader.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.topHalf)) {
            val onRacePick =
                ButtonDailyRacesMoonlightSho.check(game.imageUtils, sourceBitmap = sourceBitmap) ||
                    ButtonDailyRacesJupiterCup.check(game.imageUtils, sourceBitmap = sourceBitmap)
            if (onRacePick) return DailyRaceScreenState.DAILY_RACES_RACE_PICK
            headerOnlyFrames = priorHeaderOnlyFrames + 1
            return if (headerOnlyFrames >= 2) DailyRaceScreenState.DAILY_RACES_DIFFICULTY_PICK else DailyRaceScreenState.UNKNOWN
        }

        if (LabelDailyPrograms.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
            return DailyRaceScreenState.DAILY_PROGRAMS_CONTAINER
        }

        if (ButtonMenuBarRaceSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return DailyRaceScreenState.RACE_TAB
        }

        if (ButtonMenuBarHomeSelected.check(game.imageUtils, sourceBitmap = sourceBitmap, region = Region.bottomHalf)) {
            return if (sessionOver) DailyRaceScreenState.COMPLETE else DailyRaceScreenState.HOME_SCREEN
        }

        return DailyRaceScreenState.UNKNOWN
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

    private fun goHome() {
        if (ButtonMenuBarHomeUnselected.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] goHome:: Home tab not found.")
            game.wait(1.0)
        }
    }

    private fun finish(
        reason: String,
        failed: Boolean,
    ) {
        if (finishReason == null) {
            finishReason = reason
            finishFailed = failed
            MessageLog.i(TAG, "[DAILY_RACES] $reason")
        }
    }

    private fun handleRaceTab() {
        if (sessionOver) {
            goHome()
            return
        }
        MessageLog.v(TAG, "[STATE] handleRaceTab:: clicking the Daily Program label.")
        if (ButtonDailyProgramTile.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceTab:: Daily Program label not found.")
            game.wait(1.0)
        }
    }

    private fun handleDailyProgramsContainer() {
        if (sessionOver) {
            goHome()
            return
        }
        MessageLog.v(TAG, "[STATE] handleDailyProgramsContainer:: clicking the Daily Races label.")
        if (ButtonDailyRaces.click(game.imageUtils)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleDailyProgramsContainer:: Daily Races label not found; retrying.")
            game.wait(1.0)
        }
    }

    /** The header's "n/m" counter; an event can double the daily tickets, so the count is read, never assumed. */
    private fun readTickets(bitmap: Bitmap): Int? {
        val raw =
            try {
                game.imageUtils.performOCROnRegion(
                    bitmap,
                    DailyRaceGeometry.TICKET_OCR_X,
                    gameY(DailyRaceGeometry.TICKET_OCR_Y.toDouble(), ScreenBand.TOP, bitmap.width, bitmap.height).toInt(),
                    DailyRaceGeometry.TICKET_OCR_W,
                    DailyRaceGeometry.TICKET_OCR_H,
                    scale = 3.0,
                    debugName = "daily_race_tickets",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                ""
            }
        val count = parseTicketCount(raw)
        MessageLog.i(TAG, "[DAILY_RACES] Ticket counter OCR \"$raw\" -> ${count?.let { "${it.first}/${it.second}" } ?: "unreadable"}.")
        return count?.first
    }

    /** Race pick and difficulty pick share the header and its ticket counter. */
    private fun handleDailyRacesScreen(
        bitmap: Bitmap,
        onRacePick: Boolean,
    ) {
        if (raceSequenceCommitted && finishReason == null) {
            val left = readTickets(bitmap)
            ticketsLeft = left
            if (left != 0) MessageLog.w(TAG, "[DAILY_RACES] Expected 0 tickets after the multi-race, read ${left ?: "unreadable"}.")
            finish("Multi-race of ${ticketsHeld ?: 0} race(s) done; tickets left: ${left ?: "unreadable"}.", failed = false)
        }
        if (sessionOver) {
            goHome()
            return
        }

        if (ticketsHeld == null) {
            val held = readTickets(bitmap)
            if (held == null) {
                ticketReads += 1
                if (ticketReads >= MAX_TICKET_READS) finish("Ticket counter unreadable after $ticketReads reads; not racing.", failed = true)
                game.wait(1.0)
                return
            }
            if (held == 0) {
                finish("No Daily Race tickets left.", failed = false)
                goHome()
                return
            }
            ticketsHeld = held
        }

        if (onRacePick) pickRace() else pickDifficulty(bitmap)
    }

    private fun pickRace() {
        val tile =
            when (targetRaceName) {
                "Moonlight Sho" -> ButtonDailyRacesMoonlightSho
                "Jupiter Cup" -> ButtonDailyRacesJupiterCup
                else -> {
                    finish("Unknown targetRace setting \"$targetRaceName\"; not racing.", failed = true)
                    return
                }
            }
        if (tile.click(game.imageUtils)) {
            racePicked = true
            game.wait(2.0)
            return
        }
        raceTileMisses += 1
        if (raceTileMisses >= MAX_RACE_TILE_MISSES) {
            finish("$targetRaceName is not in today's rotation; not racing.", failed = true)
        } else {
            game.wait(1.0)
        }
    }

    private fun pickDifficulty(bitmap: Bitmap) {
        when (difficultyListStep(racePicked, startedOverForRace)) {
            DifficultyListStep.TAP -> {}
            DifficultyListStep.START_OVER -> {
                startedOverForRace = true
                MessageLog.i(TAG, "[DAILY_RACES] Difficulty list reached without picking $targetRaceName here; starting over from Home.")
                goHome()
                return
            }
            DifficultyListStep.STOP -> {
                finish("Difficulty list reached twice without picking $targetRaceName; not racing.", failed = true)
                return
            }
        }
        val row = difficultyRow(targetDifficulty)
        val y = difficultyRowTapY(row, bitmap.width, bitmap.height)
        if (y == null) {
            // The list scrolls on shorter screens; the scrolled positions have not been captured.
            finish("Difficulty $targetDifficulty sits below the visible list on this screen; not racing.", failed = true)
            return
        }
        val x = bitmap.width / 2.0
        MessageLog.i(TAG, "[DAILY_RACES] Difficulty $targetDifficulty (row ${row + 1} of 4) at ($x, $y).")
        CoordinateTap.tap(game.gestureUtils, x, y, "daily_race_difficulty_${targetDifficulty.lowercase()}")
        game.wait(2.5)
    }

    /** Race! only once Multi-Race reads On; a pill that reads neither On nor Off is never tapped blind. */
    private fun handleRaceDetails(bitmap: Bitmap) {
        if (sessionOver || ticketsHeld == null) {
            MessageLog.v(TAG, "[STATE] handleRaceDetails:: not racing; Cancel.")
            ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)
            game.wait(2.0)
            return
        }

        val on = ButtonMultiRaceOn.check(game.imageUtils, sourceBitmap = bitmap)
        val off = ButtonMultiRaceOff.check(game.imageUtils, sourceBitmap = bitmap)
        if (!on || off) {
            if (off && !on && ensureMultiRaceOn && !multiRaceToggled) {
                MessageLog.i(TAG, "[DAILY_RACES] Multi-Race reads Off; turning it On.")
                multiRaceToggled = true
                ButtonMultiRaceOff.click(game.imageUtils)
                game.wait(1.0)
                return
            }
            finish("Multi-Race could not be verified On (On=$on, Off=$off, toggled=$multiRaceToggled, ensure=$ensureMultiRaceOn); not racing.", failed = true)
            ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)
            game.wait(2.0)
            return
        }

        MessageLog.v(TAG, "[STATE] handleRaceDetails:: Multi-Race: On; Race!.")
        if (ButtonRaceConfirm.click(game.imageUtils, region = Region.bottomHalf)) {
            multiRaceVerified = true
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRaceDetails:: Race! button not found; retrying.")
            game.wait(1.5)
        }
    }

    /**
     * Keeps the game's runner pick before racing. After the multi-race this screen returns under the Daily Sale;
     * Back heads for the Daily Races list so its counter is read once more, and any other screen still walks Home.
     */
    private fun handleRunnerSelection() {
        if (raceSequenceCommitted && finishReason == null) {
            if (!ButtonBack.click(game.imageUtils)) goHome() else game.wait(2.0)
            return
        }
        if (!runnerConfirmAllowed(multiRaceVerified, sessionOver, ticketsHeld)) {
            if (!sessionOver) finish("Runner Selection reached without a verified Multi-Race; not racing.", failed = true)
            goHome()
            return
        }
        MessageLog.v(TAG, "[STATE] handleRunnerSelection:: Confirm.")
        if (ButtonConfirm.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.5)
        } else {
            MessageLog.w(TAG, "[WARN] handleRunnerSelection:: Confirm button not found; retrying.")
            game.wait(1.5)
        }
    }

    /** The stepper defaults to every held ticket and is left there. Race! is tapped at its place: its "Consumes n" line varies. */
    private fun handleMultiRacePopup(bitmap: Bitmap) {
        if (finishReason != null || ticketsHeld == null || !multiRaceVerified) {
            if (finishReason == null) finish("Multi-Race popup reached without a verified Multi-Race; not racing.", failed = true)
            ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)
            game.wait(2.0)
            return
        }
        val y = gameY(DailyRaceGeometry.POPUP_RACE_Y.toDouble(), ScreenBand.DIALOG, bitmap.width, bitmap.height)
        CoordinateTap.tap(game.gestureUtils, DailyRaceGeometry.POPUP_RACE_X.toDouble(), y, "multi_race_popup_race_confirm")
        if (!raceSequenceCommitted) {
            raceSequenceCommitted = true
            MessageLog.i(TAG, "[DAILY_RACES] Multi-race committed: $ticketsHeld race(s), $ticketsHeld ticket(s).")
        }
        game.wait(3.0)
    }

    /** The races page by themselves; Complete only hurries them. Total Rewards ends with Close. */
    private fun handleRaceResults() {
        if (ButtonClose.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handleRaceResults:: Total Rewards Close.")
            game.wait(2.5)
            return
        }
        if (ButtonComplete.click(game.imageUtils, region = Region.bottomHalf)) {
            MessageLog.v(TAG, "[STATE] handleRaceResults:: Complete.")
        }
        game.wait(2.0)
    }

    /** Buying is not automated yet: Cancel, so the session spends nothing but tickets. */
    private fun handleDailySalePopup() {
        MessageLog.i(TAG, "[DAILY_RACES] Daily Sale shown; dismissing via Cancel.")
        if (ButtonCancel.click(game.imageUtils, region = Region.bottomHalf)) {
            game.wait(2.0)
        } else {
            MessageLog.w(TAG, "[WARN] handleDailySalePopup:: Cancel button not found; retrying.")
            game.wait(1.5)
        }
    }
}

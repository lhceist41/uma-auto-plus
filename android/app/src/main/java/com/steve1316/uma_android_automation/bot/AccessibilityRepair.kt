package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.utils.OwnInputProbeResult

// No in-app action is known to revive MuMu's dead gesture dispatch (restarting MuMu is the reported cure);
// these only pick the stop reason and count what was tried.

/** A repair was needed but WRITE_SECURE_SETTINGS is missing, so nothing could be tried. */
internal const val A11Y_GRANT_MISSING = "A11Y_GRANT_MISSING"

internal const val A11Y_INPUT_DEAD = "A11Y_INPUT_DEAD"

/** The bot's own taps still reached the screen, and the game ignored them. */
internal const val GAME_NOT_RESPONDING = "GAME_NOT_RESPONDING"

/** As [A11Y_INPUT_DEAD], but the own-input probe could not tell the taps from the game. */
internal const val TAPS_HAD_NO_EFFECT = "TAPS_HAD_NO_EFFECT"

/** A dialog showed none of the buttons the bot taps, so nothing was tapped and neither input nor the game is in question. */
internal const val DIALOG_NOT_CLOSED = "DIALOG_NOT_CLOSED"

/** A tap that reached the probe window proves the bot's input works, so the game is not responding. An inconclusive probe proves neither side, so dead input is not claimed. */
internal fun stuckInputKey(rebindKey: String?, probe: OwnInputProbeResult): String? =
    when (probe) {
        OwnInputProbeResult.ARRIVED -> GAME_NOT_RESPONDING
        OwnInputProbeResult.LOST -> rebindKey
        OwnInputProbeResult.INCONCLUSIVE -> if (rebindKey == A11Y_INPUT_DEAD) TAPS_HAD_NO_EFFECT else rebindKey
    }

/** Only an input-repair key asks [probe] whether the bot's own taps still reach the screen. */
internal fun stuckKeyAfterProbe(key: String, probe: () -> OwnInputProbeResult): String =
    if (key == A11Y_INPUT_DEAD || key == A11Y_GRANT_MISSING) stuckInputKey(key, probe()) ?: key else key

/** A game that freezes again after this many restarts halts with [GAME_NOT_RESPONDING] instead of restarting forever. */
internal const val MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN = 2

/** Where the game can never be closed (Android 14+), a re-front leaves it as it was, so it is the run's last try; where it can, a re-front only means Home missed once. */
internal fun unresponsiveReopensAfter(reopen: GameReopen, used: Int, sdk: Int): Int =
    if (reopen == GameReopen.REFRONTED && !reopenClosesGame(attempt = 2, sdk = sdk)) MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN else used

internal fun reopensUnresponsiveGame(key: String?, careerObserved: Boolean, reopensThisRun: Int): Boolean =
    key == GAME_NOT_RESPONDING && careerObserved && reopensThisRun < MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN

/** Null when the ladder asked for no rebinds, so the caller keeps its own reason. */
internal fun accessibilityStopKey(rebindsIssued: Int, rebindsRefused: Int): String? =
    when {
        rebindsRefused > 0 -> A11Y_GRANT_MISSING
        rebindsIssued > 0 -> A11Y_INPUT_DEAD
        else -> null
    }

/** A repair refused during this navigation explains any stuck failure but a dialog whose buttons were never found; an unrecognized screen stays a screen the bot could not get past. */
internal fun navigatorStuckKey(repairRefused: Boolean, rebindIssuedOnThisScreen: Boolean, dialogButtonsMissing: Boolean = false): String =
    when {
        dialogButtonsMissing -> DIALOG_NOT_CLOSED
        repairRefused -> A11Y_GRANT_MISSING
        rebindIssuedOnThisScreen -> A11Y_INPUT_DEAD
        else -> "STUCK_ON_SCREEN"
    }

internal const val STRONG_TOGGLE_GRACE_TICKS = 6

/** The rebinds one stuck episode asked for; a ladder only climbs while the screen is unchanged, so an outlived rebind counts as having changed nothing. */
internal class RebindEpisode(private val onNoChange: () -> Unit = {}) {
    var issued = 0
        private set
    var refused = 0
        private set

    var withoutChange = 0
        private set

    private var lastIssued = false

    fun start() {
        issued = 0
        refused = 0
        withoutChange = 0
        lastIssued = false
    }

    fun record(wasIssued: Boolean) {
        closeLast()
        if (wasIssued) issued++ else refused++
        lastIssued = wasIssued
    }

    fun closeLast() {
        if (lastIssued) {
            withoutChange++
            lastIssued = false
            onNoChange()
        }
    }

    fun stopKey(): String? = accessibilityStopKey(issued, refused)
}

/** One more same-state detection; post-career pages share one state, so the last two differing handler branches mean the page changed. */
internal fun stuckCountAfter(count: Int, labelBefore: String?, lastLabel: String?): Int =
    if (labelBefore != null && lastLabel != null && labelBefore != lastLabel) 1 else count + 1

internal fun shouldTryStrongToggle(episode: RebindEpisode, usedThisRun: Boolean): Boolean = !usedThisRun && episode.withoutChange >= 2

/** The run is not replayed (it cannot help without the grant or with dead taps) and the saved queue is kept, so a Start after the fix continues it. The first key wins. */
internal fun requestAccessibilityHalt(key: String) {
    if (StartModule.accessibilityHaltKey == null) StartModule.accessibilityHaltKey = key
}

/** An error result, so the run loop reaches its halt branch: a manual-stop result would end the queue and discard the saved run. */
internal fun accessibilityHaltResult(key: String, reason: String): TaskResult {
    requestAccessibilityHalt(key)
    return TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, reason)
}

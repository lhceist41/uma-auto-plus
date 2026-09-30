package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.StartModule

// Honest accounting for the accessibility repairs the stuck-input ladders ask for. No in-app action
// has been shown to revive MuMu's dead gesture dispatch (restarting MuMu is the reported cure), so
// these decide the stop reason and count what was tried; they promise no revival.

/** A repair was needed but WRITE_SECURE_SETTINGS is missing, so nothing could be tried. */
internal const val A11Y_GRANT_MISSING = "A11Y_GRANT_MISSING"

/** Rebinds were issued and the bot's taps still had no effect. */
internal const val A11Y_INPUT_DEAD = "A11Y_INPUT_DEAD"

/** The bot's own taps still reached the screen, and the game ignored them: the game stopped responding. */
internal const val GAME_NOT_RESPONDING = "GAME_NOT_RESPONDING"

/**
 * The stop reason for taps that changed nothing, once the own-input probe has run. A tap that
 * reached the bot's own probe window proves its input works, so the game is the one not responding,
 * whatever the rebinds did. Otherwise [rebindKey] stands: a probe that could not run proves nothing.
 */
internal fun stuckInputKey(rebindKey: String?, ownInputArrived: Boolean?): String? = if (ownInputArrived == true) GAME_NOT_RESPONDING else rebindKey

/**
 * Restarts of an unresponsive game allowed per run, on a screen the bot knows. A game that freezes
 * again on the same screen after that halts with [GAME_NOT_RESPONDING] instead of restarting forever.
 */
internal const val MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN = 2

/**
 * The run's restarts used after [reopen], counting the one just made in [used]. Where the game can
 * never be closed ([reopenClosesGame] false on [sdk], Android 14 and later), a re-front leaves a
 * frozen game as it was, so it is the run's one and last try: the next stop halts. Where it can, a
 * re-front means only that Home missed once, so it counts as one failed try and the next may close.
 */
internal fun unresponsiveReopensAfter(reopen: GameReopen, used: Int, sdk: Int): Int =
    if (reopen == GameReopen.REFRONTED && !reopenClosesGame(attempt = 2, sdk = sdk)) MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN else used

/** Whether a stop for [key] restarts the game instead: an unresponsive game, a career seen, and the run's restarts left. */
internal fun reopensUnresponsiveGame(key: String?, careerObserved: Boolean, reopensThisRun: Int): Boolean =
    key == GAME_NOT_RESPONDING && careerObserved && reopensThisRun < MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN

/**
 * The stop reason for a ladder that gave up after asking for rebinds: a refused one means the grant
 * is missing; issued ones that changed nothing mean taps stayed dead. Null when the ladder asked for
 * none, so the caller keeps its own reason.
 */
internal fun accessibilityStopKey(rebindsIssued: Int, rebindsRefused: Int): String? =
    when {
        rebindsRefused > 0 -> A11Y_GRANT_MISSING
        rebindsIssued > 0 -> A11Y_INPUT_DEAD
        else -> null
    }

/**
 * The career-launch navigator's key for a stuck failure. A repair refused during this navigation
 * explains any of them. A known screen that clicks did not move even after an issued rebind is dead
 * input. Anything else, an unrecognized screen above all, stays a screen the bot could not get past.
 */
internal fun navigatorStuckKey(repairRefused: Boolean, rebindIssuedOnThisScreen: Boolean): String =
    when {
        repairRefused -> A11Y_GRANT_MISSING
        rebindIssuedOnThisScreen -> A11Y_INPUT_DEAD
        else -> "STUCK_ON_SCREEN"
    }

/** Ticks a ladder waits after the stronger toggle before it stops, to see whether taps came back. */
internal const val STRONG_TOGGLE_GRACE_TICKS = 6

/**
 * The rebinds one stuck episode of a ladder asked for. A ladder only climbs while nothing on screen
 * changes, so a rebind the episode outlives changed nothing: it is counted when the next rebind, the
 * stronger toggle or the stop comes. [onNoChange] feeds the session's ledger counter.
 */
internal class RebindEpisode(private val onNoChange: () -> Unit = {}) {
    var issued = 0
        private set
    var refused = 0
        private set

    /** Issued rebinds that were followed by no screen change. */
    var withoutChange = 0
        private set

    private var lastIssued = false

    /** Starts a new episode, forgetting the last one. */
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

    /** The ladder is still stuck: the last issued rebind changed nothing. */
    fun closeLast() {
        if (lastIssued) {
            withoutChange++
            lastIssued = false
            onNoChange()
        }
    }

    fun stopKey(): String? = accessibilityStopKey(issued, refused)
}

/** Whether to try the stronger toggle now: at most once per run, and only after two rebinds changed nothing. */
internal fun shouldTryStrongToggle(episode: RebindEpisode, usedThisRun: Boolean): Boolean = !usedThisRun && episode.withoutChange >= 2

/**
 * Halts the queue with [key] once this run ends. The run is not replayed (a replay cannot help
 * without the grant or with dead taps), and the saved queue is kept, so a Start after the fix
 * continues it. The first key of a session wins.
 */
internal fun requestAccessibilityHalt(key: String) {
    if (StartModule.accessibilityHaltKey == null) StartModule.accessibilityHaltKey = key
}

/**
 * The result of a run that halts for [key] before its career loop starts. An error, so the run loop
 * reaches its halt branch: a manual-stop result would end the queue and discard the saved run.
 */
internal fun accessibilityHaltResult(key: String, reason: String): TaskResult {
    requestAccessibilityHalt(key)
    return TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, reason)
}

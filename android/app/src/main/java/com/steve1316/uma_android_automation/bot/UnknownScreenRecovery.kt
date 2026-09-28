package com.steve1316.uma_android_automation.bot

/**
 * Pure decisions for the unknown-screen recovery ladder's game-relaunch rung, split out of
 * [Campaign.recoverFromUnknownScreen] so they can be unit-tested without the Android-coupled ladder
 * body (screen capture, gestures, OCR).
 *
 * Context: on a stuck-screen episode the ladder escalates gesture rebinds, then relaunches the game,
 * then stops. On 2026-07-21 the relaunch itself killed a live foreground game (it used CLEAR_TASK
 * from a background service; the follow-up cold start was dropped), the ladder assumed the dispatched
 * intent meant success, and the queue then continued the next run onto the dead game. These helpers
 * bound the relaunch to a retry budget and decide when a stop should pause the queue.
 */

/**
 * Whether the game-relaunch rung should fire on this tick.
 *
 * @param count The current consecutive-unknown-screen count.
 * @param threshold The count at which the relaunch rung fires (sits above the gesture rebinds, below
 *   the stop cap).
 * @param attemptsUsed Relaunch attempts already spent this stuck episode.
 * @param maxAttempts The per-episode relaunch budget.
 * @param careerObserved Whether a career screen was actually seen this task (so a bot parked at the
 *   pre-career lobby never relaunches).
 */
internal fun shouldRelaunchGame(
    count: Int,
    threshold: Int,
    attemptsUsed: Int,
    maxAttempts: Int,
    careerObserved: Boolean,
): Boolean = count == threshold && careerObserved && attemptsUsed in 0 until maxAttempts

/**
 * Whether a stop at the unknown-screen cap should be reported as game-unrecoverable (pause the queue)
 * rather than a generic error (subject to the normal stopOnError rule).
 *
 * True once at least one relaunch was attempted this episode and a driveable game screen still never
 * returned: the game is gone or genuinely un-driveable, so continuing the queue onto it can only fail.
 * A stop reached without ever relaunching (e.g. a brief unknown streak that hit the cap without the
 * career-observed gate) stays a generic error.
 */
internal fun stopIsGameUnrecoverable(attemptsUsed: Int): Boolean = attemptsUsed > 0

/**
 * Whether UMA Auto+'s own screen is in front, set from [com.steve1316.uma_android_automation.MainActivity]'s
 * onResume and onPause in this process. While it is, the capture shows our app instead of the game, so a
 * blind tap lands in our UI (one dismissed the player's stop-after-this-career dialog) and an unknown-screen
 * streak escalates to rebinds and a game relaunch over the player. Android pauses an activity before the next
 * one resumes and a new process starts false, so outside multi-window the flag cannot stay true over the game.
 */
internal object OwnUiForeground {
    @Volatile
    var resumed: Boolean = false
}

/** Waits via [wait] instead of blind input while [ownUiInFront]; true when it waited, so the caller neither taps nor counts the tick. */
internal fun holdForOwnUi(ownUiInFront: Boolean, wait: () -> Unit): Boolean {
    if (!ownUiInFront) return false
    wait()
    return true
}

/** The milliseconds [hold] spent holding for our own screen on [clock], or null when it did not hold, so a timed loop can keep them off its cap. */
internal fun heldMsForOwnUi(clock: () -> Long, hold: () -> Boolean): Long? {
    val from = clock()
    return if (hold()) clock() - from else null
}

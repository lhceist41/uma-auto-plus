package com.steve1316.uma_android_automation.bot

/**
 * Pure decisions for the unknown-screen ladder's game-relaunch rung. A relaunch once killed a live game
 * (CLEAR_TASK from a background service, cold start dropped) and the queue ran on onto the dead game, so
 * relaunches are budgeted and a stop after one pauses the queue.
 */

/** Whether the game-relaunch rung fires on this tick; a bot parked at the pre-career lobby ([careerObserved] false) never relaunches. */
internal fun shouldRelaunchGame(
    count: Int,
    threshold: Int,
    attemptsUsed: Int,
    maxAttempts: Int,
    careerObserved: Boolean,
): Boolean = count == threshold && careerObserved && attemptsUsed in 0 until maxAttempts

/** A stop after at least one relaunch that never brought a driveable screen back means the game is gone, so the queue pauses instead of continuing onto it. */
internal fun stopIsGameUnrecoverable(attemptsUsed: Int): Boolean = attemptsUsed > 0

/**
 * Whether UMA Auto+'s own screen is in front, set from MainActivity onResume/onPause. The capture then shows our
 * app, so a blind tap lands in our UI and an unknown-screen streak would relaunch the game over the player.
 * Android pauses an activity before the next resumes and a new process starts false, so outside multi-window
 * it cannot stay true over the game.
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

/**
 * The shade's close animation is waited out only when the system performed the dismissal ([dispatched]); MuMu reports false on nearly
 * every unknown-screen tick, where a fixed wait only added half a second.
 */
internal fun settleAfterShadeDismiss(dispatched: Boolean, waitForShadeClose: () -> Unit, waitForLoading: () -> Unit) {
    if (dispatched) waitForShadeClose() else waitForLoading()
}

/** The milliseconds [hold] spent holding for our own screen on [clock], or null when it did not hold, so a timed loop can keep them off its cap. */
internal fun heldMsForOwnUi(clock: () -> Long, hold: () -> Boolean): Long? {
    val from = clock()
    return if (hold()) clock() - from else null
}

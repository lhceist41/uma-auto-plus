package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.StartModule

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

    private val TAG: String = "[${MainActivity.loggerTag}]OwnUiForeground"

    // Covers the game window taking input focus back after our activity pauses.
    private const val GAME_REFOCUS_MS = 1_000L

    /** Called before every bot tap and swipe: holds it while our screen is in front so it lands on the game. True when it held. */
    fun waitForGame(): Boolean {
        if (!resumed) return false
        MessageLog.i(TAG, "[MISC] UMA Auto+ is in front of the game; waiting for the game before tapping.")
        waitWhileOwnUi(
            ownUiInFront = { resumed },
            stopRequested = { !BotService.isRunning || StartModule.queueStopRequested || StartModule.queueSkipRequested },
            step = {
                Game.heartbeat()
                Thread.sleep(100L)
            },
        )
        Thread.sleep(GAME_REFOCUS_MS)
        return true
    }
}

/** Runs [step] while [ownUiInFront], throwing [InterruptedException] once [stopRequested]; true when it waited. */
internal fun waitWhileOwnUi(ownUiInFront: () -> Boolean, stopRequested: () -> Boolean, step: () -> Unit): Boolean {
    var waited = false
    while (ownUiInFront()) {
        if (stopRequested()) throw InterruptedException("Stopped while UMA Auto+ was in front of the game.")
        step()
        waited = true
    }
    return waited
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

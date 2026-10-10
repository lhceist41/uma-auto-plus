package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
import kotlin.math.abs

/** A cell left out of the comparison: the bot's own floating button and the status bar. */
internal const val FROZEN_GRID_MASKED = -1

/** A status bar, when one shows, ends above this y; the game's top bar starts below it. */
internal const val FROZEN_GRID_STATUS_BAR_PX = 48

/** Around the floating button's window, for its shadow. */
internal const val FROZEN_GRID_BUTTON_MARGIN = 16

/** A frozen game: this many captures, this long, with no other cell changed, while the bot sent this many screen-changing taps. */
internal const val FROZEN_MIN_CAPTURES = 3
internal const val FROZEN_MIN_STILL_MS = 8_000L
internal const val FROZEN_MIN_TAPS = 3

/** A misc mode restarts a frozen game once per run; a second freeze ends the run as before. */
internal const val MAX_MISC_FROZEN_GAME_REOPENS = 1

/**
 * [homeLumaGrid] without the cells the bot draws on itself. Four MuMu freezes changed 0 of the other cells between frames
 * seconds apart (only the floating button's cell moved); an idle Training screen changed 148 of 576.
 *
 * @param button The floating button's window as (x, y, size); null when there is none.
 */
internal fun frozenFrameGrid(
    width: Int,
    height: Int,
    button: Triple<Int, Int, Int>?,
    topInset: Int,
    pixel: (Int, Int) -> Int,
): IntArray {
    val grid = homeLumaGrid(width, height, pixel)
    val topMasked = maxOf(FROZEN_GRID_STATUS_BAR_PX, topInset)
    for (i in grid.indices) {
        val x = ((2 * (i % HOME_GRID_W) + 1) * width) / (2 * HOME_GRID_W)
        val y = ((2 * (i / HOME_GRID_W) + 1) * height) / (2 * HOME_GRID_H)
        val onButton =
            button != null &&
                x >= button.first - FROZEN_GRID_BUTTON_MARGIN &&
                x < button.first + button.third + FROZEN_GRID_BUTTON_MARGIN &&
                y >= button.second - FROZEN_GRID_BUTTON_MARGIN &&
                y < button.second + button.third + FROZEN_GRID_BUTTON_MARGIN
        if (onButton || y < topMasked) grid[i] = FROZEN_GRID_MASKED
    }
    return grid
}

internal fun framesStill(
    a: IntArray,
    b: IntArray,
): Boolean =
    a.size == b.size &&
        a.indices.all { a[it] == FROZEN_GRID_MASKED || b[it] == FROZEN_GRID_MASKED || abs(a[it] - b[it]) <= HOME_CELL_DELTA }

/**
 * Whether the game stopped drawing. A live screen can hold still (an event's choice screen did for 4 s), so stillness counts
 * only while the bot kept sending taps that would change a live screen.
 */
internal class FrozenFrameTracker {
    private var reference: IntArray? = null
    private var stillSinceMs = 0L
    private var captures = 0
    private var actionsAtStart = 0L
    private var actionsNow = 0L

    /** [actions] is the caller's running count of screen-changing taps at the capture. */
    fun record(
        grid: IntArray,
        actions: Long,
        nowMs: Long,
    ) {
        actionsNow = actions
        val still = reference?.let { framesStill(it, grid) } ?: false
        if (!still) {
            reference = grid
            stillSinceMs = nowMs
            captures = 1
            actionsAtStart = actions
            return
        }
        captures++
    }

    fun frozen(nowMs: Long): Boolean =
        captures >= FROZEN_MIN_CAPTURES && nowMs - stillSinceMs >= FROZEN_MIN_STILL_MS && actionsNow - actionsAtStart >= FROZEN_MIN_TAPS

    fun reset() {
        reference = null
        captures = 0
    }
}

/**
 * The frames of one Back streak. A tick that pressed no Back (a dialog, the main screen, any other handler) ends the
 * streak, so a static screen revisited between progress ticks never adds up to a freeze. The probe runs once per streak.
 */
internal class BackStreakWatch {
    private val frames = FrozenFrameTracker()
    private var pressedSinceTickStart = false
    private var probed = false

    /** At the start of every tick, before any handler. */
    fun startTick() {
        if (!pressedSinceTickStart) {
            frames.reset()
            probed = false
        }
        pressedSinceTickStart = false
    }

    /** [grid] is the frame from before this press; [presses] the streak's presses so far, one per tick. */
    fun backPressed(
        grid: IntArray,
        presses: Long,
        nowMs: Long,
    ) {
        pressedSinceTickStart = true
        frames.record(grid, presses, nowMs)
    }

    /** True once per streak, when the frame reads frozen with the game in front; the caller then probes. */
    fun probeNow(
        nowMs: Long,
        otherAppInFront: () -> Boolean,
    ): Boolean {
        if (probed || !frames.frozen(nowMs) || otherAppInFront()) return false
        probed = true
        return true
    }
}

internal enum class BackStreakStep {
    CONTINUE,

    /** The probe showed the bot's taps arriving on a frame that never changed: the game stopped responding. */
    REOPEN_FROZEN_GAME,

    STOP,
}

/**
 * Only an ARRIVED probe goes to the restart. LOST, INCONCLUSIVE or no probe leave the streak as it was before this check:
 * it runs on to [maxPresses], where it stops with the old reason.
 */
internal fun backStreakStep(
    presses: Int,
    maxPresses: Int,
    probe: OwnInputProbeResult?,
): BackStreakStep =
    when {
        probe == OwnInputProbeResult.ARRIVED -> BackStreakStep.REOPEN_FROZEN_GAME
        presses >= maxPresses -> BackStreakStep.STOP
        else -> BackStreakStep.CONTINUE
    }

/** Only a still frame with the game in front, and the bot's own taps proven to reach the screen (ARRIVED), is a frozen game. */
internal fun miscFrozenGameReopens(
    frozen: Boolean,
    otherAppInFront: () -> Boolean,
    probe: () -> OwnInputProbeResult,
    reopensUsed: Int,
): Boolean = frozen && reopensUsed < MAX_MISC_FROZEN_GAME_REOPENS && !otherAppInFront() && probe() == OwnInputProbeResult.ARRIVED

package com.steve1316.uma_android_automation.bot

import kotlin.math.abs

/** How one [Game.reopenGame] call ended. */
internal enum class GameReopen {
    /** The game was brought to the front as it was: not closed, not restarted. */
    REFRONTED,

    /** The game was closed and launched again, and its title screen came up. */
    RESTARTED,

    /** The game was sent Home, closed if Android allowed it, and launched, but no title screen came up. */
    NOT_RESTARTED,

    /** No launch could be dispatched: the game is not installed under its package, or the launch threw. */
    NOT_DISPATCHED,
}

/** How [restartFrozenGame] ended. */
internal enum class FrozenGameRestart {
    /** Home was not dispatched, or the screen did not change and settle after it: nothing was closed. */
    SCREEN_UNCHANGED_AFTER_HOME,
    RESTARTED,

    /** The fresh-task launch showed no title; the plain launch after it did. */
    RESTARTED_ON_PLAIN_LAUNCH,
    NOT_RESTARTED,
    NOT_DISPATCHED,
}

/**
 * Whether a reopen closes the game instead of only bringing it to the front: from the second attempt
 * of a stuck episode (the first re-front changed nothing), and only below Android 14, where
 * `killBackgroundProcesses` may still end another app's background process.
 */
internal fun reopenClosesGame(attempt: Int, sdk: Int): Boolean = attempt >= 2 && sdk < 34

/**
 * Seconds after Home at which the game's background process is asked to end. The game only
 * qualifies once Android ranks it a cached background app (adj 700): 5 s after Home from the game's
 * Home screen, about 33 s after Home mid-career (MuMu, Android 12, 2026-09-29 and 2026-09-30). The
 * app cannot see another package's process on Android 12, so the calls repeat across the whole
 * window; each one is a no-op until the game qualifies and after it has gone.
 */
internal val GAME_KILL_SECONDS_AFTER_HOME = listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0)

/** The launch waits for the last kill call to land. */
internal const val GAME_LAUNCH_SECONDS_AFTER_HOME = 60.0

/** Luma grid of one frame, the cells [screenLeftGame] compares. */
private const val HOME_GRID_W = 32
private const val HOME_GRID_H = 18

/** A cell whose luma moved by more than this counts as changed. */
private const val HOME_CELL_DELTA = 24

/**
 * Share of cells that must change for Home to count as having left the game. Home from the game
 * changed 0.89 and 0.93 of them (the Home screen, a frozen event); a frozen game changed 0.00 to 0.01
 * between frames. An animated game can pass it too: the title changed 0.70 to 0.84 between frames
 * (MuMu captures, 2026-09-29 and 2026-09-30), which is why [homeLanded] also needs a still screen.
 */
internal const val HOME_LEFT_GAME_SHARE = 0.8

/**
 * Most cells that may change between two frames of the screen Home landed on, [GAME_HOME_STILL_SECONDS]
 * apart. The MuMu launcher changed 0.00 of them, and 0.06 when its ad banner rotated; the animated
 * title 0.70 or more (the same captures).
 */
internal const val HOME_STILL_MAX_SHARE = 0.10

/** The luma of a [HOME_GRID_W] x [HOME_GRID_H] grid of cell centres, read through [pixel] (an ARGB int). */
internal fun homeLumaGrid(width: Int, height: Int, pixel: (Int, Int) -> Int): IntArray =
    IntArray(HOME_GRID_W * HOME_GRID_H) { i ->
        val x = ((2 * (i % HOME_GRID_W) + 1) * width) / (2 * HOME_GRID_W)
        val y = ((2 * (i / HOME_GRID_W) + 1) * height) / (2 * HOME_GRID_H)
        val c = pixel(x, y)
        (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }

/** Share of the cells that changed between two grids; grids of different sizes count as all changed. */
internal fun changedShare(a: IntArray, b: IntArray): Double {
    if (a.size != b.size || a.isEmpty()) return 1.0
    return a.indices.count { abs(a[it] - b[it]) > HOME_CELL_DELTA }.toDouble() / a.size
}

/** True when at least [HOME_LEFT_GAME_SHARE] of the cells changed between [before] and [after]. */
internal fun screenLeftGame(before: IntArray, after: IntArray): Boolean = changedShare(before, after) >= HOME_LEFT_GAME_SHARE

/**
 * True when Home left the game: the screen changed ([screenLeftGame]) and then held still ([settled]
 * a moment after [after] changed under [HOME_STILL_MAX_SHARE]). A launcher is still; a game that
 * kept running in front animates.
 */
internal fun homeLanded(before: IntArray, after: IntArray, settled: IntArray): Boolean =
    screenLeftGame(before, after) && changedShare(after, settled) < HOME_STILL_MAX_SHARE

/** How long a launch has to show the title screen: 20 s after a kill on MuMu, 40 s for a cold start. */
internal const val GAME_TITLE_WAIT_SECONDS = 60.0
internal const val GAME_TITLE_POLL_SECONDS = 2.0
internal const val GAME_HOME_SETTLE_SECONDS = 2.0

/** The gap between the two after-Home captures that must match ([homeLanded]). */
internal const val GAME_HOME_STILL_SECONDS = 1.0

/**
 * Closes a frozen game and launches it fresh.
 *
 * 1. Press Home and confirm it was dispatched and the screen left the game and held still
 *    ([homeLanded]). Otherwise nothing is closed: a kill is a no-op on a game in front, and clearing
 *    its task would tear it down.
 * 2. Ask for the game's background process to end at every [GAME_KILL_SECONDS_AFTER_HOME] mark.
 * 3. Launch into a fresh task ([launch] with clearTask). A plain launch after a kill re-enters the
 *    surviving task and hung at the splash screen on MuMu (2026-09-29); clearing the task gave a
 *    fresh activity and the title in 20 s (2026-09-30). Clearing a task whose process is still alive
 *    tore the game down with nothing relaunched (2026-07-21), which is why the launch waits for the
 *    kill window and why a missing title gets one plain launch, which cold-starts a dead game.
 *
 * The title screen is the only proof: the app cannot tell whether a kill landed.
 */
internal fun restartFrozenGame(
    captureGrid: () -> IntArray,
    pressHome: () -> Boolean,
    killGame: () -> Unit,
    launch: (clearTask: Boolean) -> Boolean,
    titleShowing: () -> Boolean,
    sleep: (seconds: Double) -> Unit,
): FrozenGameRestart {
    val before = captureGrid()
    if (!pressHome()) return FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME
    sleep(GAME_HOME_SETTLE_SECONDS)
    val after = captureGrid()
    sleep(GAME_HOME_STILL_SECONDS)
    if (!homeLanded(before, after, captureGrid())) return FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME

    var elapsed = GAME_HOME_SETTLE_SECONDS + GAME_HOME_STILL_SECONDS
    for (at in GAME_KILL_SECONDS_AFTER_HOME) {
        sleep(at - elapsed)
        elapsed = at
        killGame()
    }
    sleep(GAME_LAUNCH_SECONDS_AFTER_HOME - elapsed)

    val freshLaunched = launch(true)
    if (freshLaunched && titleWithin(titleShowing, sleep)) return FrozenGameRestart.RESTARTED
    val plainLaunched = launch(false)
    if (plainLaunched && titleWithin(titleShowing, sleep)) return FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH
    return if (freshLaunched || plainLaunched) FrozenGameRestart.NOT_RESTARTED else FrozenGameRestart.NOT_DISPATCHED
}

private fun titleWithin(titleShowing: () -> Boolean, sleep: (Double) -> Unit): Boolean {
    var waited = 0.0
    while (waited < GAME_TITLE_WAIT_SECONDS) {
        sleep(GAME_TITLE_POLL_SECONDS)
        waited += GAME_TITLE_POLL_SECONDS
        if (titleShowing()) return true
    }
    return false
}

/** The ladder's words for how a reopen ended: a restart is named only when the title screen proved it. */
internal fun reopenOutcomeWords(reopen: GameReopen): String =
    when (reopen) {
        GameReopen.REFRONTED -> "brought the game to the front; it was not restarted"
        GameReopen.RESTARTED -> "restarted the game (its title screen came up)"
        GameReopen.NOT_RESTARTED -> "launched the game again, but no title screen came up"
        GameReopen.NOT_DISPATCHED -> "could not launch the game"
    }

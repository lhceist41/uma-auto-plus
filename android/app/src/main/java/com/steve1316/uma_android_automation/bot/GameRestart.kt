package com.steve1316.uma_android_automation.bot

import kotlin.math.abs

internal enum class GameReopen {
    REFRONTED,
    RESTARTED,
    NOT_RESTARTED,
    NOT_DISPATCHED,
}

internal enum class FrozenGameRestart {
    /** Home did not leave the game: nothing was closed. */
    SCREEN_UNCHANGED_AFTER_HOME,
    RESTARTED,
    RESTARTED_ON_PLAIN_LAUNCH,
    NOT_RESTARTED,
    NOT_DISPATCHED,
}

/** Closing starts at the second attempt, and only below Android 14, where `killBackgroundProcesses` may still end another app's background process. */
internal fun reopenClosesGame(attempt: Int, sdk: Int): Boolean = attempt >= 2 && sdk < 34

/**
 * Seconds after Home at which the game's background process is asked to end. It qualifies only once Android
 * caches it: 5 s after Home from the game's Home screen, about 33 s mid-career (MuMu, Android 12). Another
 * package's process is invisible there, so the calls repeat; each is a no-op until it qualifies.
 */
internal val GAME_KILL_SECONDS_AFTER_HOME = listOf(5.0, 15.0, 25.0, 35.0, 45.0, 55.0)

internal const val GAME_LAUNCH_SECONDS_AFTER_HOME = 60.0

private const val HOME_GRID_W = 32
private const val HOME_GRID_H = 18

private const val HOME_CELL_DELTA = 24

/** Home from the game changed 0.89 to 0.93 of the cells, a frozen game 0.00 to 0.01; the animated title changes 0.70 to 0.84, so [homeLanded] also needs a still screen. */
internal const val HOME_LEFT_GAME_SHARE = 0.8

/** The MuMu launcher changes 0.00 of the cells between frames (0.06 when its ad banner rotates), the animated title 0.70 or more. */
internal const val HOME_STILL_MAX_SHARE = 0.10

internal fun homeLumaGrid(width: Int, height: Int, pixel: (Int, Int) -> Int): IntArray =
    IntArray(HOME_GRID_W * HOME_GRID_H) { i ->
        val x = ((2 * (i % HOME_GRID_W) + 1) * width) / (2 * HOME_GRID_W)
        val y = ((2 * (i / HOME_GRID_W) + 1) * height) / (2 * HOME_GRID_H)
        val c = pixel(x, y)
        (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }

internal fun changedShare(a: IntArray, b: IntArray): Double {
    if (a.size != b.size || a.isEmpty()) return 1.0
    return a.indices.count { abs(a[it] - b[it]) > HOME_CELL_DELTA }.toDouble() / a.size
}

/** Pixels cannot tell: a game turning to a new still screen passed [homeLanded] in 36 of 3,943 running-game frame triples (MuMu). Null when the package cannot be read. */
internal fun homeLeftGameByWindow(frontPackage: String?): Boolean? = frontPackage?.let { it != Game.GAME_PACKAGE }

internal fun screenLeftGame(before: IntArray, after: IntArray): Boolean = changedShare(before, after) >= HOME_LEFT_GAME_SHARE

/** A launcher is still after Home; a game still running in front animates. */
internal fun homeLanded(before: IntArray, after: IntArray, settled: IntArray): Boolean =
    screenLeftGame(before, after) && changedShare(after, settled) < HOME_STILL_MAX_SHARE

/** A launch shows the title in 20 s after a kill on MuMu, 40 s for a cold start. */
internal const val GAME_TITLE_WAIT_SECONDS = 60.0
internal const val GAME_TITLE_POLL_SECONDS = 2.0
internal const val GAME_HOME_SETTLE_SECONDS = 2.0

internal const val GAME_HOME_STILL_SECONDS = 1.0

/**
 * Nothing is closed unless Home is confirmed to have left the game: a kill is a no-op on a game in front, and
 * clearing its task would tear it down. The launch clears the task because a plain launch after a kill
 * re-entered the surviving task and hung at the splash on MuMu; it waits for the kill window because clearing
 * a task whose process is alive left nothing relaunched. A missing title gets one plain launch, which
 * cold-starts a dead game. The title screen is the only proof a kill landed.
 */
internal fun restartFrozenGame(
    captureGrid: () -> IntArray,
    pressHome: () -> Boolean,
    frontPackage: () -> String?,
    killGame: () -> Unit,
    launch: (clearTask: Boolean) -> Boolean,
    titleShowing: () -> Boolean,
    sleep: (seconds: Double) -> Unit,
): FrozenGameRestart {
    val before = captureGrid()
    if (!pressHome()) return FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME
    sleep(GAME_HOME_SETTLE_SECONDS)
    var elapsed = GAME_HOME_SETTLE_SECONDS
    val left =
        homeLeftGameByWindow(frontPackage()) ?: run {
            val after = captureGrid()
            sleep(GAME_HOME_STILL_SECONDS)
            elapsed += GAME_HOME_STILL_SECONDS
            homeLanded(before, after, captureGrid())
        }
    if (!left) return FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME

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

/** A restart is named only when the title screen proved it. */
internal fun reopenOutcomeWords(reopen: GameReopen): String =
    when (reopen) {
        GameReopen.REFRONTED -> "brought the game to the front; it was not restarted"
        GameReopen.RESTARTED -> "restarted the game (its title screen came up)"
        GameReopen.NOT_RESTARTED -> "launched the game again, but no title screen came up"
        GameReopen.NOT_DISPATCHED -> "could not launch the game"
    }

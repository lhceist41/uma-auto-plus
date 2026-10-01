package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.ProgressTracker
import kotlin.random.Random

/**
 * Coordinate taps without the library's missing-asset error noise. The library's `tap(x, y, imageName)` uses
 * `imageName` only to size the jitter; an unbacked label logs a FileNotFoundException and falls back to 25x25.
 * This reproduces that jitter and taps with `imageName = null`. Template-driven clicks must keep their template path.
 */
object CoordinateTap {
    // Matches the library's missing-asset fallback region (MyAccessibilityService.randomizeTapLocation).
    const val REGION: Int = 25

    private val TAG: String = "[${MainActivity.loggerTag}]CoordinateTap"

    /** Mirrors the library's 25x25 fallback jitter: a uniform integer in [coord - 6, coord + 6] per axis. */
    fun jitter(x: Double, y: Double, region: Int = REGION, rng: Random = Random.Default): Pair<Int, Int> {
        val low = (region * 0.25).toInt()
        val high = (region * 0.75).toInt()
        val jx = (x - region / 2).toInt() + (low..high).random(rng)
        val jy = (y - region / 2).toInt() + (low..high).random(rng)
        return Pair(jx, jy)
    }

    /** Jitters (x, y) like the library fallback and emits one `[COORD_TAP]` trace. Returns the point tapped. */
    fun resolve(x: Double, y: Double, label: String, region: Int = REGION): Pair<Int, Int> {
        val (jx, jy) = jitter(x, y, region)
        MessageLog.i(TAG, "[COORD_TAP] label=$label x=${x.toInt()} y=${y.toInt()} jitter=${region}x$region tapped=($jx, $jy)")
        return Pair(jx, jy)
    }

    /** Raw coordinate tap without the post-tap loading wait; use [Game.tapCoordinate] when that wait is needed. */
    fun tap(service: MyAccessibilityService, x: Double, y: Double, label: String, taps: Int = 1): Pair<Int, Int> {
        val (jx, jy) = resolve(x, y, label)
        service.tap(jx.toDouble(), jy.toDouble(), null, taps = taps)
        ProgressTracker.noteAction()
        return Pair(jx, jy)
    }
}

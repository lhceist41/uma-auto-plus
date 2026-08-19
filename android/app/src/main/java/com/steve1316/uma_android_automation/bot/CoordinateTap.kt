package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.uma_android_automation.MainActivity
import kotlin.random.Random

/**
 * Intentional fixed-coordinate taps.
 *
 * The automation library's `tap(x, y, imageName)` uses `imageName` only to size a small tap-location
 * jitter: it loads `images/<imageName>.png` and jitters within that template's bounds. Many fork-side
 * taps pass a descriptive label with no backing asset on purpose (a deliberate coordinate tap, not a
 * template match). On every such tap the library logs a `FileNotFoundException` plus
 * "Using a region of 25x25 as a fallback in order to proceed with tap location randomization.", then
 * jitters within a 25x25 region anyway. That error noise is misleading; nothing is actually wrong.
 *
 * This helper reproduces the library's 25x25 fallback jitter locally, then calls the library tap with
 * `imageName = null` (the null path taps the exact coordinate, no asset lookup), so a coordinate tap
 * keeps the same landing envelope without the spurious error. Route only genuine coordinate taps
 * through here; real template-driven clicks must keep passing their template path so the library sizes
 * the jitter to the matched template.
 */
object CoordinateTap {
    // Matches the library's missing-asset fallback region (MyAccessibilityService.randomizeTapLocation).
    const val REGION: Int = 25

    private val TAG: String = "[${MainActivity.loggerTag}]CoordinateTap"

    /**
     * Reproduces the library's tap-location jitter for a [region]x[region] box centered on (x, y).
     *
     * Library math (decompiled from android-cv-automation-library MyAccessibilityService): each axis is
     * `left + offset` where `left = (coord - region / 2).toInt()` (integer half) and
     * `offset = ((region * 0.25).toInt() .. (region * 0.75).toInt()).random()`. For the 25x25 fallback the
     * offset is a uniform integer in [6, 18] and left is `coord - 12`, giving a uniform integer in
     * [coord - 6, coord + 6] per axis. The library's post-sample clamp loop can never re-loop (its
     * continue condition is unsatisfiable), so it draws exactly one sample -- this mirrors that.
     */
    fun jitter(x: Double, y: Double, region: Int = REGION, rng: Random = Random.Default): Pair<Int, Int> {
        val low = (region * 0.25).toInt()
        val high = (region * 0.75).toInt()
        val jx = (x - region / 2).toInt() + (low..high).random(rng)
        val jy = (y - region / 2).toInt() + (low..high).random(rng)
        return Pair(jx, jy)
    }

    /**
     * Jitters (x, y) like the library's coordinate fallback and emits one concise `[COORD_TAP]` trace so
     * the log makes clear this is an intentional coordinate interaction, not a failed template match.
     * Returns the point actually tapped.
     */
    fun resolve(x: Double, y: Double, label: String, region: Int = REGION): Pair<Int, Int> {
        val (jx, jy) = jitter(x, y, region)
        MessageLog.i(TAG, "[COORD_TAP] label=$label x=${x.toInt()} y=${y.toInt()} jitter=${region}x$region tapped=($jx, $jy)")
        return Pair(jx, jy)
    }

    /**
     * Dispatches an intentional coordinate tap through [service] with no asset lookup. This is the raw
     * path: it does not perform the game's post-tap loading wait, matching callers that previously used
     * `gestureUtils.tap(x, y, label)` directly. Callers that need the loading wait should use
     * [Game.tapCoordinate] instead. Returns the point actually tapped.
     */
    fun tap(service: MyAccessibilityService, x: Double, y: Double, label: String, taps: Int = 1): Pair<Int, Int> {
        val (jx, jy) = resolve(x, y, label)
        service.tap(jx.toDouble(), jy.toDouble(), null, taps = taps)
        return Pair(jx, jy)
    }
}

package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.utils.CANONICAL_WIDTH

/** How long a tile's failure bubble may take to settle after its tab shows before the tile is read without it. */
internal const val FAILURE_CHANCE_SETTLE_MS = 1000L

/** How long to wait after the "Failure" label shows before reading it, in seconds. The label shows before its number renders. */
internal const val FAILURE_BUBBLE_SETTLE_S = 0.2

/** Where each facility's "Failure" label rests at the canonical width (match centres in live logs, 191 px apart). */
private val FAILURE_LABEL_X: Map<StatName, Int> =
    mapOf(
        StatName.SPEED to 159,
        StatName.STAMINA to 350,
        StatName.POWER to 541,
        StatName.GUTS to 732,
        StatName.WIT to 923,
    )

/**
 * The part of the screen a facility's failure bubble shows in: the bottom half, one fifth of the width, centred on that facility's label.
 * On a tab switch the previous facility's bubble is still fading out over its own button while the new header already shows, and a
 * search over the whole bottom half read that stale bubble on about 1 in 100 tiles.
 *
 * @return The search region (x, y, width, height).
 */
internal fun failureBubbleColumn(
    stat: StatName,
    screenWidth: Int,
    screenHeight: Int,
): IntArray {
    val columnWidth = screenWidth / StatName.entries.size
    val centre = FAILURE_LABEL_X.getValue(stat) * screenWidth / CANONICAL_WIDTH
    val left = (centre - columnWidth / 2).coerceIn(0, screenWidth - columnWidth)
    return intArrayOf(left, screenHeight / 2, columnWidth, screenHeight / 2)
}

/**
 * Captures the frame a training tile is analyzed on. The failure bubble is still sliding and fading in when the tab header
 * shows, and the "Failure" label does not match until it has settled, so a frame where [locate] finds nothing is recaptured
 * after [pause] until one shows it or the settle budget is spent. Once the label shows, [settle] lets its number render and
 * one more frame is taken; the label is located again on it, keeping the first location when that misses.
 *
 * @return The last frame captured and the label's location, null when no frame within the budget showed it.
 */
internal fun <F : Any, L : Any> captureSettledFailureChanceFrame(
    capture: () -> F,
    locate: (F) -> L?,
    nowMs: () -> Long,
    pause: () -> Unit,
    settle: () -> Unit,
): Pair<F, L?> {
    val deadline = nowMs() + FAILURE_CHANCE_SETTLE_MS
    var frame = capture()
    var location = locate(frame)
    while (location == null && nowMs() < deadline) {
        pause()
        frame = capture()
        location = locate(frame)
    }
    if (location == null) return Pair(frame, null)
    settle()
    val settled = capture()
    // A label found mid-slide sits up to about 20 px off its resting place, which cuts the number in half.
    return Pair(settled, locate(settled) ?: location)
}

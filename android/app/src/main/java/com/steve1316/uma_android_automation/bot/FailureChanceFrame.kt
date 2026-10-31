package com.steve1316.uma_android_automation.bot

/** How long a tile's failure bubble may take to settle after its tab shows before the tile is read without it. */
internal const val FAILURE_CHANCE_SETTLE_MS = 1000L

/**
 * Captures the frame a training tile is analyzed on. The failure bubble is still sliding and fading in when the tab header
 * shows, and the "Failure" label does not match until it has settled, so a frame where [locate] finds nothing is recaptured
 * after [pause] until one shows it or the settle budget is spent. The location returned was found on the frame returned.
 *
 * @return The last frame captured and the label's location on it, null when no frame within the budget showed it.
 */
internal fun <F : Any, L : Any> captureSettledFailureChanceFrame(
    capture: () -> F,
    locate: (F) -> L?,
    nowMs: () -> Long,
    pause: () -> Unit,
): Pair<F, L?> {
    val deadline = nowMs() + FAILURE_CHANCE_SETTLE_MS
    var frame = capture()
    var location = locate(frame)
    while (location == null && nowMs() < deadline) {
        pause()
        frame = capture()
        location = locate(frame)
    }
    return Pair(frame, location)
}

package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MyAccessibilityService
import java.util.Locale
import kotlin.math.roundToInt

/** The game lays out by width, so every capture is read at this width: a W-wide screen is the 1080-wide layout scaled by W/1080 on both axes. */
const val CANONICAL_WIDTH = 1080

/** An unknown (0) width stays unknown. */
fun canonicalWidth(rawWidth: Int): Int = if (rawWidth <= 0) rawWidth else CANONICAL_WIDTH

fun canonicalHeight(
    rawWidth: Int,
    rawHeight: Int,
): Int = if (rawWidth <= 0 || rawWidth == CANONICAL_WIDTH) rawHeight else (rawHeight.toDouble() * CANONICAL_WIDTH / rawWidth).roundToInt()

fun rawToCanonical(
    raw: Double,
    rawWidth: Int,
): Double = if (rawWidth <= 0 || rawWidth == CANONICAL_WIDTH) raw else raw * CANONICAL_WIDTH / rawWidth

fun canonicalToRaw(
    canonical: Double,
    rawWidth: Int,
): Double = if (rawWidth <= 0 || rawWidth == CANONICAL_WIDTH) canonical else canonical * rawWidth / CANONICAL_WIDTH

/** The size a [width]x[height] capture is resized to, or null when it is already canonical. */
fun canonicalCaptureSize(
    width: Int,
    height: Int,
): Pair<Int, Int>? = if (width <= 0 || width == CANONICAL_WIDTH) null else canonicalWidth(width) to canonicalHeight(width, height)

/** [capture] itself when it is already canonical, else [resize] to the canonical size. */
internal fun <T> toCanonicalCapture(
    capture: T,
    width: Int,
    height: Int,
    resize: (Int, Int) -> T,
): T {
    val (w, h) = canonicalCaptureSize(width, height) ?: return capture
    return resize(w, h)
}

/** An (x, y, size) window on the raw screen, such as the floating button's, in canonical pixels. */
fun canonicalRect(
    rect: Triple<Int, Int, Int>,
    rawWidth: Int = SharedData.displayWidth,
): Triple<Int, Int, Int> {
    if (rawWidth <= 0 || rawWidth == CANONICAL_WIDTH) return rect
    return Triple(
        rawToCanonical(rect.first.toDouble(), rawWidth).roundToInt(),
        rawToCanonical(rect.second.toDouble(), rawWidth).roundToInt(),
        rawToCanonical(rect.third.toDouble(), rawWidth).roundToInt(),
    )
}

/** Empty at 1080 wide, else the canonical size the bot reads, for the device log and bug reports. */
fun canonicalSizeNote(
    rawWidth: Int,
    rawHeight: Int,
): String = if (rawWidth <= 0 || rawWidth == CANONICAL_WIDTH) "" else " (read as ${CANONICAL_WIDTH}x${canonicalHeight(rawWidth, rawHeight)})"

/** One Start log line: the size the bot reads and the factor every tap and swipe point is multiplied by (1.0000 on a 1080-wide screen). */
fun tapMapLine(
    rawWidth: Int,
    rawHeight: Int,
): String =
    "[LAYOUT] Screen read at ${canonicalWidth(rawWidth)}x${canonicalHeight(rawWidth, rawHeight)}; " +
        "taps and swipes map to the screen by ${String.format(Locale.US, "%.4f", canonicalToRaw(1.0, rawWidth))}."

/** The captured screen in canonical pixels, from the raw size the capture service recorded. */
object CanonicalScreen {
    val width: Int get() = canonicalWidth(SharedData.displayWidth)
    val height: Int get() = canonicalHeight(SharedData.displayWidth, SharedData.displayHeight)

    /** True when every capture is resized to the canonical width, so its pixels are resampled rather than the game's own. */
    val resized: Boolean get() = canonicalCaptureSize(SharedData.displayWidth, SharedData.displayHeight) != null
}

internal fun rawTapPoint(
    x: Double,
    y: Double,
    rawWidth: Int,
): Pair<Double, Double> = canonicalToRaw(x, rawWidth) to canonicalToRaw(y, rawWidth)

/** Start and end of a canonical swipe on the raw screen; its distance scales with the same factor. */
internal fun rawSwipePoints(
    oldX: Float,
    oldY: Float,
    newX: Float,
    newY: Float,
    rawWidth: Int,
): FloatArray = floatArrayOf(oldX, oldY, newX, newY).map { canonicalToRaw(it.toDouble(), rawWidth).toFloat() }.toFloatArray()

/**
 * Every bot tap goes through here: matches and measured points are canonical, the screen takes raw pixels. Callers pass
 * `gestureUtils` per call (a stored service dies when MuMu rebinds it). The library's jitter stays around the raw point.
 */
fun MyAccessibilityService.tapCanonical(
    x: Double,
    y: Double,
    imageName: String? = null,
    longPress: Boolean = false,
    pressDuration: Double = 1.0,
    taps: Int = 1,
): Boolean {
    val (rawX, rawY) = rawTapPoint(x, y, SharedData.displayWidth)
    return tap(rawX, rawY, imageName, longPress, pressDuration, taps)
}

fun MyAccessibilityService.swipeCanonical(
    oldX: Float,
    oldY: Float,
    newX: Float,
    newY: Float,
    duration: Long = 500L,
): Boolean {
    val raw = rawSwipePoints(oldX, oldY, newX, newY, SharedData.displayWidth)
    return swipe(raw[0], raw[1], raw[2], raw[3], duration)
}

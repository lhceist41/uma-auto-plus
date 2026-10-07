package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

/** The game does not scale at width 1080: on taller screens TOP moves by the top cutout inset, BOTTOM by the extra height, DIALOG by half of it, MIDDLE halfway between TOP and BOTTOM (measured on 1080x2316, inset 94). */
enum class ScreenBand { TOP, MIDDLE, BOTTOM, DIALOG }

@Volatile
var screenTopInset: Int = 0

/** Only 1080-wide surfaces at least 1920 tall are mapped; others keep their old proportional tap place and probe pixels. */
fun gameY(
    y1920: Double,
    band: ScreenBand,
    width: Int,
    height: Int,
    topInset: Int = screenTopInset,
): Double {
    if (width == 1080 && height == 1920) return y1920
    if (!isMappedSurface(width, height)) return y1920 * height / 1920.0
    val scale = width / 1080.0
    val refHeight = height / scale
    val inset = topInset / scale
    val extra = refHeight - 1920.0
    val offset =
        when (band) {
            ScreenBand.TOP -> inset
            ScreenBand.MIDDLE -> (inset + extra) / 2.0
            ScreenBand.BOTTOM -> extra
            ScreenBand.DIALOG -> extra / 2.0
        }
    return (y1920 + offset) * scale
}

/** True when a capture is wider than the screen it shows: row-stride padding on the right. */
fun capturePaddedPastScreen(
    captureWidth: Int,
    screenWidth: Int,
): Boolean = screenWidth > 0 && captureWidth > screenWidth

/** Other widths would also need template scales that are not measured yet. */
fun isMappedSurface(
    width: Int,
    height: Int,
): Boolean = width == 1080 && height >= 1920

fun SparkPixelSampler.onScreen(
    band: ScreenBand,
    width: Int,
    height: Int,
): SparkPixelSampler {
    if (height == 1920 || !isMappedSurface(width, height)) return this
    return SparkPixelSampler { x, y -> argb(x, gameY(y.toDouble(), band, width, height).roundToInt()) }
}

/** An (x, y, width, height) region measured on 1080x1920, moved with its band; other surfaces keep the region as it was. */
fun IntArray.onScreen(
    band: ScreenBand,
    width: Int,
    height: Int,
): IntArray {
    if (height == 1920 || !isMappedSurface(width, height)) return this
    return intArrayOf(this[0], gameY(this[1].toDouble(), band, width, height).roundToInt(), this[2], this[3])
}

/** The window-level cutout inset, which Android can raise above the raw cutout (measured 71 raw, 94 for the window). */
fun rememberScreenTopInset(context: Context): Int {
    screenTopInset =
        try {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    context
                        .createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                        .getSystemService(WindowManager::class.java)
                        .maximumWindowMetrics.windowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
                        .top
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> display.cutout?.safeInsetTop ?: 0
                else -> 0
            }
        } catch (_: Exception) {
            screenTopInset
        }
    return screenTopInset
}

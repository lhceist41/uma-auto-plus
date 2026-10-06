package com.steve1316.uma_android_automation.utils

/** A screen area, in 1080x1920 coordinates, that the floating button must stay off. */
internal data class KeepClearArea(
    val name: String,
    val band: ScreenBand,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/** The goal and fan text rows the bot reads, and the stats and buttons in the lower half that it reads and taps. */
internal val OVERLAY_KEEP_CLEAR =
    listOf(
        KeepClearArea("goal banner", ScreenBand.TOP, 365, 140, 550, 55),
        KeepClearArea("stats and bottom buttons", ScreenBand.BOTTOM, 0, 1230, 1080, 690),
    )

/** Names of the keep-clear areas the button window overlaps on a screen of this size. */
internal fun overlayKeepClearHits(
    buttonX: Int,
    buttonY: Int,
    buttonSize: Int,
    width: Int,
    height: Int,
    topInset: Int,
): List<String> {
    val scale = width / 1080.0
    return OVERLAY_KEEP_CLEAR
        .filter { area ->
            val left = area.x * scale
            val top = gameY(area.y.toDouble(), area.band, width, height, topInset)
            val bottom = gameY((area.y + area.h).toDouble(), area.band, width, height, topInset)
            buttonX < left + area.w * scale && buttonX + buttonSize > left && buttonY < bottom && buttonY + buttonSize > top
        }.map { it.name }
}

package com.steve1316.uma_android_automation.utils

/** Footer of the game's paged help dialogs (Back / Close / Help under a page-dot row), measured at 1080x1920 on fixture grandconcert/paged_help_last_page. */
object PagedHelpGeometry {
    const val DOT_ROW_Y = 1733
    const val DOT_X_START = 380
    const val DOT_X_END = 700
    const val BUTTON_Y = 1778
    const val BACK_X_START = 90
    const val BACK_X_END = 200
    const val HELP_X_START = 870
    const val HELP_X_END = 990
    const val GAP_LEFT_X = 270
    const val GAP_RIGHT_X = 780
    const val CLOSE_X = 540
    const val CLOSE_Y = 1812
}

private fun rgb(sampler: SparkPixelSampler, x: Int, y: Int): Triple<Int, Int, Int> {
    val p = sampler.argb(x, y)
    return Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
}

private fun isWhite(sampler: SparkPixelSampler, x: Int, y: Int): Boolean {
    val (r, g, b) = rgb(sampler, x, y)
    return minOf(r, g, b) >= 215
}

/**
 * True for a paged help dialog: brown page dots with one green (current page) dot, white Back and Help buttons flanking the centre button.
 * The dot row is what no Details, Strategy or event dialog has; on such a dialog the misc Back only turns the page back.
 */
fun pagedHelpDialogPresent(sampler: SparkPixelSampler): Boolean {
    var brown = 0
    var green = 0
    for (x in PagedHelpGeometry.DOT_X_START..PagedHelpGeometry.DOT_X_END step 2) {
        val (r, g, b) = rgb(sampler, x, PagedHelpGeometry.DOT_ROW_Y)
        if (r in 70..180 && g < 110 && b < 70 && r - g >= 30) brown++
        if (g >= 180 && b <= 90 && g - b >= 100 && g >= r) green++
    }
    if (brown < 4 || green < 2) return false
    val y = PagedHelpGeometry.BUTTON_Y
    if (!(PagedHelpGeometry.BACK_X_START..PagedHelpGeometry.BACK_X_END step 10).all { isWhite(sampler, it, y) }) return false
    if (!(PagedHelpGeometry.HELP_X_START..PagedHelpGeometry.HELP_X_END step 10).all { isWhite(sampler, it, y) }) return false
    return !isWhite(sampler, PagedHelpGeometry.GAP_LEFT_X, y) && !isWhite(sampler, PagedHelpGeometry.GAP_RIGHT_X, y)
}

/** Misc Next and Back taking turns undo each other; the streak counts the turns, and a repeat of either step ends it. */
internal fun miscNextBackSwapStreak(streak: Int, previousWasNext: Boolean?, nextNow: Boolean): Int =
    if (previousWasNext == null || previousWasNext == nextNow) 0 else streak + 1

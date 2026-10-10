package com.steve1316.uma_android_automation.utils

/**
 * Daily Races places, measured on the 1080x2316 phone captures (fixtures/dailyraces, cutout inset 94).
 * Band-mapped y values are the phone positions moved back through [gameY]; no MuMu capture confirms them yet.
 */
object DailyRaceGeometry {
    /**
     * Difficulty card centres (Very Hard, Hard, Normal, Easy), TOP band: phone cards 1043-1232, pitch 218.
     * The older MuMu taps (1018, 1248, 1478, 1709) land within 25 px of these.
     */
    val DIFFICULTY_ROW_YS = intArrayOf(1043, 1261, 1479, 1697)
    const val DIFFICULTY_CARD_HALF = 90

    /** Bottom of the scrolling difficulty list, BOTTOM band: phone 1993. A card past it is cut off. */
    const val DIFFICULTY_LIST_BOTTOM = 1597

    /** "n/m" ticket counter in the Daily Races header, TOP band: phone digits x 953-1009, y 117-142. */
    const val TICKET_OCR_X = 900
    const val TICKET_OCR_Y = 10
    const val TICKET_OCR_W = 160
    const val TICKET_OCR_H = 50

    /** Multi-Race popup "Race!", DIALOG band: phone (777, 1448); the older 1920 tap (780, 1250) agrees. */
    const val POPUP_RACE_X = 777
    const val POPUP_RACE_Y = 1250
}

/** Card row for a `targetDifficulty` setting, 0 = Very Hard (top); unknown values fall back to Very Hard. */
fun difficultyRow(setting: String): Int =
    when (setting.trim().uppercase()) {
        "HARD" -> 1
        "NORMAL" -> 2
        "EASY" -> 3
        else -> 0
    }

/** Tap y of a difficulty card in capture pixels; null when the card would sit below the list's bottom edge. */
fun difficultyRowTapY(
    row: Int,
    width: Int,
    height: Int,
): Double? {
    val centre = DailyRaceGeometry.DIFFICULTY_ROW_YS.getOrNull(row) ?: return null
    val cardBottom = gameY((centre + DailyRaceGeometry.DIFFICULTY_CARD_HALF).toDouble(), ScreenBand.TOP, width, height)
    if (cardBottom > gameY(DailyRaceGeometry.DIFFICULTY_LIST_BOTTOM.toDouble(), ScreenBand.BOTTOM, width, height)) return null
    return gameY(centre.toDouble(), ScreenBand.TOP, width, height)
}

/** The "n/m" ticket counter as OCR reads it ("6/6" -> 6 held of 6); null unless it reads as a whole count. */
fun parseTicketCount(raw: String): Pair<Int, Int>? {
    val text = raw.replace(" ", "").replace('O', '0').replace('o', '0')
    val m = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)").find(text) ?: return null
    val held = m.groupValues[1].toInt()
    val max = m.groupValues[2].toInt()
    return if (max in 1..12 && held <= max) held to max else null
}

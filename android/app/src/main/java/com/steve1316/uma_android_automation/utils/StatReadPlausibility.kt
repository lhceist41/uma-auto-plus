package com.steve1316.uma_android_automation.utils

/**
 * Plausibility rules for a single OCR'd stat value.
 *
 * The stat readers already reject values ABOVE the scenario cap, but nothing rejected values far
 * below the last verified one. Tesseract drops leading digits often enough that this bites: GUTS
 * read 684 and then 7 twice in a row (2026-07-26 23:41/23:42), and [Trainee.updateStats]'s
 * consistency rule promoted the 7 to trusted on the second read, because its mismatch tracker seeds
 * `lastMismatchedValue` at -1 and so treats any small value as "consistent". Three such promotions
 * happened across seven recorded sessions (GUTS 684 -> 7, GUTS 703 -> 7, SPEED 231 -> 1). A career
 * that ends while a stat sits corrupted writes that value to the outcome ledger AND to
 * `StartModule.lastCareerEndStats`, which prices the 30 TP spark redraw.
 *
 * The invariant is bounded-drop, not strictly monotonic: some events really do reduce a stat.
 * The worst quantified single-event decrease in this repo's own game data (the JSON files under
 * `src/data`) is -20, against 69 occurrences of -5 and 3 of -10. [MAX_SINGLE_EVENT_DROP] is 100, five times
 * that worst case, so stacked or unquantified effects still pass while every observed corruption
 * (drops of 230, 677 and 696) is rejected.
 *
 * Kept free of Android types so the rules are unit-testable.
 */
object StatReadPlausibility {
    /**
     * How far below the last verified value a fresh read may legitimately land. See the class
     * doc for the derivation: worst observed real event is -20, so this carries 5x headroom.
     */
    const val MAX_SINGLE_EVENT_DROP: Int = 100

    /**
     * Strips the non-digit noise Tesseract leaves around a stat and parses what remains.
     *
     * Returns null when nothing numeric is left, which the readers map to their -1 sentinel.
     * Trailing punctuation is the common case ("354:" reads as 354).
     */
    fun parseStatDigits(text: String): Int? {
        val digits = text.replace(Regex("[^0-9]"), "")
        return digits.toIntOrNull()
    }

    /**
     * True when [parsed] sits so far below [lastVerified] that no in-game event explains it.
     *
     * Inactive until a baseline exists: [lastVerified] is <= 0 both before a career's first
     * successful read (stats initialize to -1) and for the whole of a career the bot resumed, so
     * the first read of any career is always accepted. That is also what keeps a fresh career's
     * genuinely low early stats from being measured against the previous career's endgame values.
     */
    fun isImplausibleDrop(parsed: Int, lastVerified: Int): Boolean {
        if (lastVerified <= 0) return false
        return parsed < lastVerified - MAX_SINGLE_EVENT_DROP
    }
}

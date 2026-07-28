package com.steve1316.uma_android_automation.utils

/**
 * Certainty rules for the trainee roster scan's paging.
 *
 * The scan reads a grid page, swipes down, and starts the next page below the rows the swipe
 * carried over. That skip is an optimisation, and it was trusting two things it had no right to:
 * that every cell of the previous page actually read, and that the swipe distance was known.
 *
 * On 2026-07-28 both were false at once. Five cells read blank and were silently dropped (no log,
 * no retry), then a swipe measured 262px, the scan computed a one-row advance, and started the next
 * page at row 1 on the belief that row 0 had already been covered. It had been tapped and had
 * failed. Those five cells held Hishi Amazon, Haru Urara, both Grass Wonders and Gold Ship, so an
 * owned trainee was reported as absent from the roster and the queue halted.
 *
 * The rule here is that a skip must be earned. Any uncertainty on the previous page - a blank read,
 * or a swipe whose distance could not be measured - drops the next page back to row 0 and lets the
 * name dedup absorb the re-reads. Re-reading a row costs a few seconds; skipping one cost a halted
 * queue and two unrun careers.
 *
 * Kept free of Android types so the arithmetic is unit-testable.
 */
object RosterScanPolicy {
    /**
     * Retries allowed on a blank cell read before the cell is recorded as failed.
     *
     * Deliberately small and explicitly best-effort: when the cause is positional (a tap landing
     * off-cell after an unconfirmed scroll) an identical re-tap fails identically, so this recovers
     * only the transient case. The re-anchored second pass is the real recovery.
     */
    const val MAX_BLANK_RETRIES: Int = 2

    /** True while [attempt] (0-based, counting retries only) is still within the cap. */
    fun shouldRetryBlank(attempt: Int): Boolean = attempt < MAX_BLANK_RETRIES

    /**
     * The row the next page may start at.
     *
     * @param gridRows rows in one grid page.
     * @param advancedRows rows the swipe actually moved, or null when it could not be measured.
     * @param previousPageFullyRead false when any cell on the previous page failed to read, which
     *   makes that page's coverage unproven no matter what the swipe reported.
     */
    fun nextStartRow(gridRows: Int, advancedRows: Int?, previousPageFullyRead: Boolean): Int {
        // Unmeasurable swipe: the carry-over is unknown, so nothing may be assumed already read.
        if (advancedRows == null) return 0
        // Measured, but the previous page has a hole in it: re-read rather than paper over it.
        if (!previousPageFullyRead) return 0
        return (gridRows - advancedRows).coerceIn(0, gridRows)
    }

    /**
     * Whether a not-found scan has earned one full re-anchored second pass.
     *
     * Only when a read actually failed: a clean scan that did not find the target really did not
     * find it, and paying a second 90-second pass to confirm that helps nobody.
     */
    fun needsSecondPass(failedReads: Int, passIndex: Int): Boolean = failedReads > 0 && passIndex == 0
}

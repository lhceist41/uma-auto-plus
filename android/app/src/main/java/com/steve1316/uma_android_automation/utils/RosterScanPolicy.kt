package com.steve1316.uma_android_automation.utils

/**
 * Certainty rules for the trainee roster scan.
 *
 * On 2026-07-28 five cells read blank and were silently dropped (no log, no retry), and a page skip
 * built on that page then started the next page below them. Those five cells held Hishi Amazon,
 * Haru Urara, both Grass Wonders and Gold Ship, so an owned trainee was reported as absent and the
 * queue halted. The scan therefore reads every page from row 0 (the name dedup absorbs the
 * re-reads), retries a blank cell, and earns a second pass when a read failed.
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
     * Whether a not-found scan has earned one full re-anchored second pass.
     *
     * Only when a read actually failed: a clean scan that did not find the target really did not
     * find it, and paying a second 90-second pass to confirm that helps nobody.
     */
    fun needsSecondPass(failedReads: Int, passIndex: Int): Boolean = failedReads > 0 && passIndex == 0

    /**
     * Whether a not-found scan is explained by the target owning only an outfit her preset skips.
     *
     * Only on a complete read with no near-miss name: with a failed cell the plain outfit may simply not
     * have been read, and a non-excluded cell at or above [nearNameSimilarity] may be her plain outfit
     * misread, so "she is on the roster only as that outfit" would be an unsupported claim either way.
     */
    fun onlyExcludedOutfitOwned(failedReads: Int, excludedOutfitSeen: String?, nearestSimilarity: Double, nearNameSimilarity: Double): Boolean =
        failedReads == 0 && excludedOutfitSeen != null && nearestSimilarity < nearNameSimilarity
}

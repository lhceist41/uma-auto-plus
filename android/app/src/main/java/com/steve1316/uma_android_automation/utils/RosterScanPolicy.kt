package com.steve1316.uma_android_automation.utils

/**
 * Certainty rules for the trainee roster scan: every page is read from row 0 (name dedup absorbs the
 * re-reads), a blank cell is retried, and a failed read earns a second pass, so a dropped cell never
 * reports an owned trainee as absent. Kept free of Android types so it is unit-testable.
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

    /** Only on a complete read with no near-miss name: otherwise her plain outfit may be unread or misread. */
    fun onlyExcludedOutfitOwned(failedReads: Int, excludedOutfitSeen: String?, nearestSimilarity: Double, nearNameSimilarity: Double): Boolean =
        failedReads == 0 && excludedOutfitSeen != null && nearestSimilarity < nearNameSimilarity
}

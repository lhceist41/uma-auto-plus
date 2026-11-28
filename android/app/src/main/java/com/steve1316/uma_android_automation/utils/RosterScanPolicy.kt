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

    /**
     * Page swipes allowed per pass: a loop guard, not the normal end. At one row per swipe (the
     * shortest full swipe seen moves 1.08 rows) a pass covers 32 rows, above every released roster;
     * the scan normally ends on the measured bottom or on [SCAN_RESERVE_MS].
     */
    const val MAX_PAGE_SWIPES: Int = 30

    /**
     * Time left before the navigation deadline below which a scan stops paging. Covers one more
     * page (15 s measured on 1080x1920), the launch after a match (67 s measured from the match to
     * the loaded career) and about a minute of margin: past the deadline the whole queue halts.
     */
    const val SCAN_RESERVE_MS: Long = 150_000L

    /** Why a scan pass stops paging. [stoppedEarly] means the rows below were never read. */
    enum class PageEnd(val stoppedEarly: Boolean) {
        CONTINUE(false),
        BOTTOM(false),
        OUT_OF_TIME(true),
        PAGE_LIMIT(true),
    }

    /**
     * Decides after the swipe that follows [page]. The bottom is a measured swipe that did not move
     * ([deltaPx] under [minMovePx] after its retries). A page with no new names ends the scan only
     * when neither the swipe that led to it ([arrivedUnmeasured]) nor this one could be measured: a
     * measured swipe shorter than a row (210 px seen mid-list) can show no new row without being at
     * the end.
     */
    fun pageEnd(page: Int, maxSwipes: Int, newThisPage: Int, arrivedUnmeasured: Boolean, deltaPx: Int?, minMovePx: Float, msLeft: Long): PageEnd =
        when {
            deltaPx != null && Math.abs(deltaPx) < minMovePx -> PageEnd.BOTTOM
            deltaPx == null && arrivedUnmeasured && newThisPage == 0 -> PageEnd.BOTTOM
            msLeft < SCAN_RESERVE_MS -> PageEnd.OUT_OF_TIME
            page >= maxSwipes -> PageEnd.PAGE_LIMIT
            else -> PageEnd.CONTINUE
        }

    /** A second pass runs only when one as long as the first still ends before the reserve. */
    fun fitsAnotherPass(msLeft: Long, passMs: Long): Boolean = msLeft - passMs >= SCAN_RESERVE_MS

    /** Only on a complete read with no near-miss name: otherwise her plain outfit may be unread or misread. */
    fun onlyExcludedOutfitOwned(failedReads: Int, excludedOutfitSeen: String?, nearestSimilarity: Double, nearNameSimilarity: Double): Boolean =
        failedReads == 0 && excludedOutfitSeen != null && nearestSimilarity < nearNameSimilarity
}

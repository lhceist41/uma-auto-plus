package com.steve1316.uma_android_automation

internal const val BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING = "debugMode_injectBuildAwareBorrowMidListAccessibilityStall"

/** Only the canonical boolean value arms this validation fault. Missing or malformed values stay off. */
internal fun buildAwareBorrowMidListStallEnabled(raw: String?): Boolean =
    raw?.trim()?.equals("true", ignoreCase = true) == true

/**
 * Unique-row threshold for the mid-list stall injector. The page-advance swipe covers roughly 3 rows
 * (BORROW_PAGE_SWIPE_PX = 3 * BORROW_ROW_PITCH_PX), so 10 unique rows is reached after real forward
 * progress across several pages -- comfortably below the reproducible ~31-row natural stall near the
 * current live Friends pool's tail, while still deep enough into the walk to prove genuine semantic
 * progress preceded the injected gap. Validation-only; never exposed as a tunable runtime setting.
 */
internal const val BUILD_AWARE_BORROW_MID_LIST_STALL_THRESHOLD = 10

/**
 * Validation-only boundary for the build-aware Smart Borrow LOCATE walker (A3-R11): a single
 * deterministic mid-list Accessibility dead-dispatch, so the production host-recovery rung can be
 * exercised on demand instead of waiting for the natural swallow that only reproduces near the list
 * tail.
 *
 * When armed, real Accessibility page-advance dispatches proceed unchanged until the walker has
 * observed at least [threshold] unique rows -- real semantic forward progress, not wall-clock timing
 * or a raw gesture count. At that point this injector begins ONE injected no-movement gap: it swallows
 * every subsequent Accessibility advance dispatch (the failed advance, both stronger retries, and both
 * post-rebind attempts), so the walker's own screen-signature comparison -- not this injector -- is
 * what declares "the list did not move".
 *
 * [release] must be called from the walker's recoverHost callback BEFORE the real host recovery runs,
 * so the production host rung, its swipe, and every read after it are real. Once an injected gap has
 * opened, this injector never re-arms: crossing the threshold again later (or [release] being called
 * again) cannot open a second gap.
 */
internal class BuildAwareBorrowMidListStallInjector(
    private val armed: Boolean = false,
    private val threshold: Int = BUILD_AWARE_BORROW_MID_LIST_STALL_THRESHOLD,
    private val log: (String) -> Unit = {},
) {
    var injected: Boolean = false
        private set
    var released: Boolean = false
        private set
    var suppressedAttempts: Int = 0
        private set

    init {
        if (armed) log("ARMED threshold=$threshold")
    }

    /** Returns true only when the Accessibility swipe callback was dispatched. [uniqueRows] is the
     * walker's monotone semantic-progress signal (observed unique rows so far), read fresh on every call. */
    fun dispatch(
        uniqueRows: Int,
        attempt: Int,
        swipe: () -> Unit,
    ): Boolean {
        if (!armed || released) {
            swipe()
            return true
        }
        if (!injected) {
            if (uniqueRows < threshold) {
                log("progress uniqueRows=$uniqueRows")
                swipe()
                return true
            }
            injected = true
            suppressedAttempts++
            log("FAULT_INJECTED uniqueRows=$uniqueRows attempt=$attempt")
            return false
        }
        suppressedAttempts++
        log("continuing injected ladder attempt=$attempt")
        return false
    }

    /** Releases the injected gap before the real host-recovery rung runs. Idempotent, and one-shot:
     * once called, [dispatch] never swallows again for the rest of this walk. */
    fun release() {
        if (released) return
        released = true
        log("RELEASED before host recovery")
    }
}

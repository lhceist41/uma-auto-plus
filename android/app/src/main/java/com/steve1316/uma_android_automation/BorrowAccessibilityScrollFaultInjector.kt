package com.steve1316.uma_android_automation

internal const val BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING = "debugMode_swallowBorrowAccessibilityScroll"

/** Only the canonical boolean value arms this validation fault. Missing or malformed values stay off. */
internal fun borrowAccessibilityScrollFaultEnabled(raw: String?): Boolean =
    raw?.trim()?.equals("true", ignoreCase = true) == true

/**
 * Validation-only boundary for production Borrow-list Accessibility scroll dispatch.
 *
 * When armed, the production walker still requests every normal, stronger, and post-rebind
 * attempt, but the actual Accessibility swipe callback is not invoked. Screen reads remain the
 * authority for deciding that the list did not move.
 */
internal class BorrowAccessibilityScrollFaultInjector(
    private val armed: Boolean = false,
    private val log: (String) -> Unit = {},
) {
    var requestedAttempts: Int = 0
        private set
    var suppressedAttempts: Int = 0
        private set

    /** Returns true only when the Accessibility swipe callback was dispatched. */
    fun dispatch(
        attempt: Int,
        swipe: () -> Unit,
    ): Boolean {
        requestedAttempts++
        if (!armed) {
            swipe()
            return true
        }

        log("Borrow Accessibility scroll attempt requested; attempt=$attempt.")
        suppressedAttempts++
        log("FAULT_INJECTED: suppressed Borrow Accessibility scroll dispatch; attempt=$attempt.")
        return false
    }
}

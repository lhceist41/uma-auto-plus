package com.steve1316.uma_android_automation

/**
 * Factual evidence from one run of the support-deck selector rehearsal diagnostic
 * ([CareerLaunchNavigator.rehearseRequiredSupportDeck]).
 *
 * It records ONLY what happened on the already-open career-start Support Formation screen: the
 * requested deck, the decks read, the arrow taps taken, and whether the production selector
 * positively verified the exact target before any borrow. It makes NO claim about optimality,
 * causal correctness, or any future career outcome -- it is live-rehearsal evidence, nothing more.
 *
 * Smart Borrow is intentionally not exercised by the rehearsal (the production borrow path is
 * entangled with the Start-Career-owning handleSupportDeckScreen loop and needs its own contained
 * extraction), so [smartBorrowAttempted] and [postBorrowVerified] are always false here. [status]
 * PRE_BORROW_VERIFIED is the success case for this diagnostic's scope.
 */
internal data class SupportDeckRehearsalResult(
    val status: Status,
    val requestedDeck: Int? = null,
    val initialDeck: Int? = null,
    val finalDeck: Int? = null,
    val selectorSteps: Int = 0,
    val reads: Int = 0,
    val taps: Int = 0,
    val preBorrowVerified: Boolean = false,
    val smartBorrowAttempted: Boolean = false,
    val postBorrowVerified: Boolean = false,
    val failureReason: String? = null,
) {
    enum class Status {
        /** runQueue.supportDeckIndex is 0/off -- nothing to rehearse. */
        SETTING_OFF,

        /** The configured deck is outside the valid 1..10 range. */
        INVALID_TARGET,

        /** The screen precondition failed: the game is not on the real Support Formation screen. */
        NOT_ON_SUPPORT_FORMATION,

        /** The production [SupportDeckSelector] failed closed (unreadable, stalled, wrong range, step limit). */
        SELECTOR_BLOCKED,

        /** The selector reached and positively verified the exact requested deck (borrow not included). */
        PRE_BORROW_VERIFIED,
    }
}

package com.steve1316.uma_android_automation

/**
 * Factual evidence from one run of the Smart Borrow rehearsal diagnostic
 * ([CareerLaunchNavigator.rehearseSmartBorrowForRequiredDeck]).
 *
 * It records ONLY what happened on the already-open career-start Support Formation screen: the
 * requested deck, the deck read before the borrow, whether the shared production borrow boundary
 * opened the friend slot and selected a card, any duplicate / active-trainee conflict the game
 * flagged and how many replacement passes ran, whether the screen returned to Support Formation, and
 * the fresh post-borrow deck read plus whether it still equals the requested deck. It makes NO claim
 * about optimality, causal correctness, or any future career outcome -- it is live-rehearsal
 * evidence, nothing more.
 *
 * The rehearsal drives the SAME [CareerLaunchNavigator.runBorrowStep] boundary the normal launch
 * uses; it never presses Start Career and spends no TP. [status] POST_BORROW_VERIFIED is the success
 * case (the borrow ran and the required deck was still active on a fresh read afterward).
 */
internal data class SmartBorrowRehearsalResult(
    val status: Status,
    val requestedDeck: Int? = null,
    val preBorrowDeck: Int? = null,
    val friendSlotOpened: Boolean = false,
    val smartBorrowAttempted: Boolean = false,
    val smartBorrowSelected: Boolean = false,
    val replacementPasses: Int = 0,
    val duplicateConflictObserved: Boolean = false,
    val traineeConflictObserved: Boolean = false,
    val returnedToSupportFormation: Boolean = false,
    val postBorrowDeck: Int? = null,
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

        /** The visible deck before the borrow did not exactly equal the requested deck -- no friend-slot action taken. */
        PRE_BORROW_DECK_MISMATCH,

        /** The friend slot was not empty at the start, so the rehearsal fails closed rather than replace an unknown borrow. */
        FRIEND_SLOT_NOT_AVAILABLE,

        /** The shared borrow boundary failed closed (no valid card, replacement budget exhausted, or stopped). */
        BORROW_PICK_FAILED,

        /** The borrow finished but the screen did not return to Support Formation (or the slot stayed empty). */
        RETURN_TO_SUPPORT_FORMATION_FAILED,

        /** The post-borrow "Deck N" read was unreadable/ambiguous -- fails closed. */
        POST_BORROW_DECK_UNREADABLE,

        /** The post-borrow deck read did not equal the requested deck -- fails closed. */
        POST_BORROW_DECK_MISMATCH,

        /** The borrow ran and a fresh post-borrow read confirmed the required deck is still active. */
        POST_BORROW_VERIFIED,
    }
}

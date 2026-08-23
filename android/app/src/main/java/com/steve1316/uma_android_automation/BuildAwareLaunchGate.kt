package com.steve1316.uma_android_automation

/**
 * Pure, Android-free launch-transaction gate for the build-aware Smart Borrow launch (A2).
 *
 * A2 integrates the already-proven build-aware borrow selection into the production career-launch
 * transaction, one tap before Start Career. The structural invariant this file exists to enforce:
 *
 *     the Start Career tap is IMPOSSIBLE unless the transaction reached READY_TO_START_CAREER.
 *
 * That is enforced by [canStartCareer], the single predicate any Start Career tap primitive must pass
 * before it may fire. It is not a logging convention: a tap guarded by `require(canStartCareer(state))`
 * cannot run in any other state.
 *
 * The gate is a pure function of already-observed facts ([LaunchPreconditions]) so it runs unchanged in
 * a JVM unit test. The live navigator observes each fact (fresh borrow re-locate, exact selection,
 * selected-slot identity verification, owned-deck integrity, screen state) and hands them here; the gate
 * decides the terminal state. It NEVER falls back to a legacy borrow: a missing or non-build-aware intent
 * is [LaunchTransactionState.BORROW_NOT_AVAILABLE] (blocked), never a silent substitution.
 */

/** The narrow launch-transaction state. A2 reaches at most [READY_TO_START_CAREER] and then rolls back;
 * only A3 may advance a READY transaction to [STARTED] by actually tapping Start Career. */
enum class LaunchTransactionState {
    /** No launch decision has been made yet. */
    PREPARING,

    /** No usable intent, or the intent is not a build-aware recommendation: the build-aware launch is
     * not applicable and there is NO legacy fallback in this mode. Blocked. */
    BORROW_NOT_AVAILABLE,

    /** The build-aware intent's card is no longer uniquely present in a borrow pool re-scanned THIS
     * transaction (gone, changed, or ambiguous): the intent is stale against the live pool. Blocked. */
    BORROW_POOL_STALE,

    /** The exact intended row was tapped and the friend slot is filled. */
    BORROW_SELECTED,

    /** The filled friend slot verified as the intent's card via the reopened picker's Selected marker. */
    BORROW_IDENTITY_VERIFIED,

    /** Owned-deck integrity and the upstream launch identity gates hold. */
    LAUNCH_PRECONDITIONS_VERIFIED,

    /** Every precondition holds: the transaction may (in A3) tap Start Career, and only in this state. */
    READY_TO_START_CAREER,

    /** A precondition after selection failed. The caller must roll the borrow back before returning. */
    LAUNCH_BLOCKED,

    /** A3 only: Start Career was actually tapped. A2 never reaches this. */
    STARTED,
}

/**
 * The facts the live navigator observed for one launch transaction, each already reduced to a verdict.
 * Every field defaults to the SAFE (blocking) value, so a precondition the navigator forgot to set can
 * never accidentally read as satisfied.
 */
data class LaunchPreconditions(
    /** A usable intent was loaded and parsed. */
    val intentPresent: Boolean = false,
    /** The intent's recommendation_source is BUILD_AWARE (the only source this mode launches on). */
    val intentBuildAware: Boolean = false,
    /** A borrow pool re-scanned THIS transaction found the intent's card as exactly one borrowable row. */
    val freshLocateUnique: Boolean = false,
    /** The exact intended row was tapped and the friend slot committed (filled). */
    val borrowSelected: Boolean = false,
    /** The committed slot verified as the intent's card (SelectedSlotVerdict.VERIFIED). */
    val borrowIdentityVerified: Boolean = false,
    /** The owned support deck is unchanged (deck identity/number held; only the Friends slot changed). */
    val ownedDeckIntact: Boolean = false,
    /** The upstream launch identity gates (scenario / trainee / lineage) hold. In the A2 dry-run the
     * operator established these at the parked career-start; A3 wires the production navigate() gates. */
    val upstreamLaunchGatesHeld: Boolean = false,
    /** The transaction is on the expected career-start Support Formation screen. */
    val onSupportFormation: Boolean = false,
    /** The Start Career control is present on the screen. */
    val startCareerPresent: Boolean = false,
    /** No OTHER debug diagnostic is armed that would contend with a real launch. */
    val noDebugConflict: Boolean = false,
)

object BuildAwareLaunchGate {
    /**
     * The single authority on whether a launch transaction reached readiness. Evaluated once, after the
     * navigator has performed and reduced every live step. Returns [LaunchTransactionState.READY_TO_START_CAREER]
     * only when every precondition holds; otherwise the earliest blocking state on the ladder, so the
     * evidence record names exactly how far the transaction got and why it stopped.
     *
     * Ladder order matters: it mirrors the live sequence (intent -> freshness -> select -> verify ->
     * preconditions -> screen), so a stale pool is reported as [BORROW_POOL_STALE] rather than masked by a
     * later selection failure that could never have happened.
     */
    fun evaluate(p: LaunchPreconditions): LaunchTransactionState =
        when {
            !p.intentPresent || !p.intentBuildAware -> LaunchTransactionState.BORROW_NOT_AVAILABLE
            !p.freshLocateUnique -> LaunchTransactionState.BORROW_POOL_STALE
            !p.borrowSelected -> LaunchTransactionState.LAUNCH_BLOCKED
            !p.borrowIdentityVerified -> LaunchTransactionState.LAUNCH_BLOCKED
            !p.ownedDeckIntact || !p.upstreamLaunchGatesHeld -> LaunchTransactionState.LAUNCH_BLOCKED
            !p.onSupportFormation || !p.startCareerPresent || !p.noDebugConflict -> LaunchTransactionState.LAUNCH_BLOCKED
            else -> LaunchTransactionState.READY_TO_START_CAREER
        }

    /**
     * The structural gate every Start Career tap primitive MUST pass. True only for
     * [LaunchTransactionState.READY_TO_START_CAREER]. A tap guarded by this predicate is impossible in
     * any other state; A2 never calls a tap at all, but the guard is what makes A3's tap safe by construction.
     */
    fun canStartCareer(state: LaunchTransactionState): Boolean = state == LaunchTransactionState.READY_TO_START_CAREER

    /** The furthest stage the ladder reached, for the evidence record: which milestones held before the
     * gate's verdict. Distinct from [evaluate] (which reports the blocking reason) so a blocked transaction
     * still shows it got as far as, say, [BORROW_IDENTITY_VERIFIED] before the deck check failed. */
    fun furthestStageReached(p: LaunchPreconditions): LaunchTransactionState =
        when {
            !p.intentPresent || !p.intentBuildAware -> LaunchTransactionState.PREPARING
            !p.freshLocateUnique -> LaunchTransactionState.PREPARING
            !p.borrowSelected -> LaunchTransactionState.PREPARING
            !p.borrowIdentityVerified -> LaunchTransactionState.BORROW_SELECTED
            !p.ownedDeckIntact || !p.upstreamLaunchGatesHeld -> LaunchTransactionState.BORROW_IDENTITY_VERIFIED
            !p.onSupportFormation || !p.startCareerPresent || !p.noDebugConflict -> LaunchTransactionState.LAUNCH_PRECONDITIONS_VERIFIED
            else -> LaunchTransactionState.READY_TO_START_CAREER
        }
}

/**
 * Outcome of one build-aware launch transaction (A2 dry-run, and the shape A3 will consume). Carries the
 * gate verdict, the reduced preconditions, and the evidence behind them. [slotCommitted] is true when a
 * borrow is in the Friends slot and the caller still owes a rollback (the A2 dry-run always rolls back
 * before returning; A3 taps Start Career instead only when [state] is READY_TO_START_CAREER).
 */
data class BuildAwareLaunchResult(
    val state: LaunchTransactionState,
    val preconditions: LaunchPreconditions,
    val furthestStage: LaunchTransactionState,
    val intent: SmartBorrowIntent? = null,
    val locateMatch: SmartBorrowLocateMatch? = null,
    val rowsObserved: Int = 0,
    val verification: SelectedSlotVerification? = null,
    /** A card is committed to the Friends slot and a rollback is owed. */
    val slotCommitted: Boolean = false,
    /** The dry-run's rollback restored the empty Friends slot. */
    val rolledBackToEmpty: Boolean = false,
    val deckNumberAtStart: Int? = null,
    val deckNumberAtEnd: Int? = null,
    val tpRawAtStart: String? = null,
    val tpRawAtEnd: String? = null,
    /** Always false in A2: the dry-run never taps Start Career. */
    val startCareerTapped: Boolean = false,
    val reason: String? = null,
) {
    val ready: Boolean get() = state == LaunchTransactionState.READY_TO_START_CAREER
}

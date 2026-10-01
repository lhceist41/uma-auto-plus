package com.steve1316.uma_android_automation.bot

/**
 * One career's 30 TP Spark Reroll transaction: the lifecycle from the original-set read through the spend, both
 * selection pages, and the verified final confirmation.
 * The selection screens irreversibly discard one of two sets, so the choice needs the same discipline as the Finish
 * click: exact career identity, explicit states, and blocking when the transaction is missing.
 * Only explicit lifecycle events mutate the gate (mirrors [CareerFinalizeGate], deliberately not shared); construction
 * never does. [SparkRerollGate.beginCareer] creates it at the career attachment boundary in `Game.start()` immediately
 * before `task.start()`, never earlier: arming in the queue loop let the cold-start navigation's pass through Home
 * clear it as stale. Misc tasks never arm.
 * It is invalidated by the next attachment, any non-COMPLETE run result, an interrupted between-run navigation, and
 * reaching Home after a spend or terminal outcome. It is in memory on purpose: a restart loses it, and every selection
 * handler blocks safely on a missing transaction.
 * Home destroys only post-spend or terminal transactions: a pre-spend one authorizes nothing destructive (the spend
 * re-reads live and is EV-gated), while a post-spend one at Home is stale and must never govern a later selection
 * screen.
 */

/**
 * Post-spend transaction lifetime (30 minutes): selection runs seconds after the spend and the between-run navigation
 * deadline is 10 minutes, so three times that tolerates dialog recovery while an abandoned career's transaction stays
 * unusable.
 */
internal const val SPARK_TRANSACTION_MAX_AGE_MS: Long = 30L * 60L * 1000L

/** The states of one reroll transaction, in flow order. */
internal enum class SparkTxState {
    /** Career started; nothing read yet. */
    IDLE,

    /** The complete original set was read off the SPARKS screen. */
    ORIGINAL_CAPTURED,

    /** The EV gate priced the redraw positive; the spend clicks may proceed. */
    SPEND_APPROVED,

    /** The 30 TP spend button was clicked. Latches [SparkRerollTransaction.spendEverConfirmed]. */
    SPEND_CONFIRMED,

    /** The complete rerolled set was read off the "Sparks Rerolled" result screen. */
    REROLLED_CAPTURED,

    /** The "Spark Selection" intro dialog was advanced. */
    SELECTION_INTRO_PASSED,

    /** Both pager pages were read in full on the pager itself. */
    BOTH_SETS_VERIFIED,

    /** The keep policy chose a side. */
    WINNER_SELECTED,

    /** The Confirmation dialog's header was read and matches the chosen side. */
    FINAL_CONFIRMATION_VERIFIED,

    /** The final Confirm was clicked with all records written. Terminal. */
    COMPLETE,

    /** Spend declined (gate negative, setting off, or unavailable). Terminal for the reroll. */
    DECLINED,

    /** A safety stop: contradiction, unreadable screen, or a missing prerequisite. Terminal. */
    BLOCKED,
}

/** Result of a transition attempt: refused transitions carry the reason for the log. */
internal data class SparkTxResult(val ok: Boolean, val reason: String = "")

/** Mutable, but every transition is guarded, so a duplicate spend or second final confirmation is impossible. */
internal class SparkRerollTransaction internal constructor(
    val careerNonce: String,
    val queueRun: Int?,
    val startedAtMs: Long,
) {
    @Volatile
    var state: SparkTxState = SparkTxState.IDLE
        private set

    /** trainee|scenario|run|nonce, bound when the original set is captured. */
    @Volatile
    var careerToken: String? = null
        private set

    var traineeIdentity: String? = null
        private set
    var scenario: String? = null
        private set

    var originalRead: SparkSetReading? = null
        private set
    var rerolledRead: SparkSetReading? = null
        private set

    /** Sets read off the pager pages themselves (authoritative for the choice). */
    private val pagerReads = mutableMapOf<SparkSetSide, SparkSetReading>()
    private val pagerPagesVerified = mutableSetOf<SparkSetSide>()

    /**
     * Per-side read-authority outcomes recorded when the chooser scored them, so the choice record persists the reads
     * the decision consumed.
     */
    private val readAuthorities = mutableMapOf<SparkSetSide, SparkReadAuthorityResult>()

    var choice: SparkChoice? = null
        private set
    var winner: SparkSetSide? = null
        private set

    /** Latched on the first confirmed spend click; a second spend can never be approved. */
    var spendEverConfirmed: Boolean = false
        private set
    var spendConfirmedAtMs: Long? = null
        private set
    var spendReason: String? = null
        private set
    var tpRestoreSource: String? = null
        private set

    var keptRecorded: Boolean = false
        private set
    var choiceRecorded: Boolean = false
        private set
    var blockedReason: String? = null
        private set

    /** One Cancel-and-retry when the confirmation header disagrees or is unreadable; the second failure blocks. */
    var confirmationRetryUsed: Boolean = false
        private set

    /** One chevron-navigation retry per target page; the second failure blocks. */
    var pagerNavRetryUsed: Boolean = false
        private set

    /** Pages whose one full re-read is spent; after that the decision proceeds on what the bot has. */
    private val pagerRescansUsed = mutableSetOf<SparkSetSide>()

    val postSpend: Boolean
        get() =
            state in
                setOf(
                    SparkTxState.SPEND_CONFIRMED,
                    SparkTxState.REROLLED_CAPTURED,
                    SparkTxState.SELECTION_INTRO_PASSED,
                    SparkTxState.BOTH_SETS_VERIFIED,
                    SparkTxState.WINNER_SELECTED,
                    SparkTxState.FINAL_CONFIRMATION_VERIFIED,
                )

    val terminal: Boolean
        get() = state == SparkTxState.COMPLETE || state == SparkTxState.DECLINED || state == SparkTxState.BLOCKED

    /**
     * No 30 TP committed yet: a pre-spend transaction authorizes only lossless actions, which is why it survives a Home
     * pass.
     */
    val preSpend: Boolean
        get() = !spendEverConfirmed && !terminal

    fun pagerRead(side: SparkSetSide): SparkSetReading? = pagerReads[side]

    /**
     * The earlier capture of [side] tagged with this transaction's identity, or null when it was never captured (a fast
     * transition can skip the result screen).
     */
    fun earlierCapture(side: SparkSetSide): SparkSideCapture? {
        val reading = if (side == SparkSetSide.ORIGINAL) originalRead else rerolledRead
        return reading?.let { SparkSideCapture(it, side, careerNonce) }
    }

    val recordedReadAuthorities: Map<SparkSetSide, SparkReadAuthorityResult>
        get() = readAuthorities.toMap()

    /** Records which read the chooser scored for one side; bookkeeping only. */
    fun recordReadAuthority(result: SparkReadAuthorityResult) {
        readAuthorities[result.side] = result
    }

    /**
     * Records that the pager was confirmed on [side] by heading OCR and page dots agreeing. Only the swipe path can
     * establish this, and the keep-original fallback needs it: knowing which page is on screen makes confirming from a
     * partial read safe.
     */
    fun markPagerPageVerified(side: SparkSetSide) {
        pagerPagesVerified.add(side)
    }

    fun pagerPageVerified(side: SparkSetSide): Boolean = side in pagerPagesVerified

    fun captureOriginal(read: SparkSetReading, traineeIdentity: String?, scenario: String?): SparkTxResult {
        if (state == SparkTxState.ORIGINAL_CAPTURED) return SparkTxResult(true, "already captured")
        if (state != SparkTxState.IDLE) return refused("captureOriginal", "state is $state")
        this.originalRead = read
        this.traineeIdentity = traineeIdentity
        this.scenario = scenario
        this.careerToken = buildSparkCareerToken(traineeIdentity ?: "unknown", scenario ?: "unknown", queueRun, careerNonce)
        state = SparkTxState.ORIGINAL_CAPTURED
        return SparkTxResult(true)
    }

    fun approveSpend(reason: String): SparkTxResult {
        if (spendEverConfirmed) return refused("approveSpend", "a spend was already confirmed on this career")
        if (state != SparkTxState.ORIGINAL_CAPTURED) return refused("approveSpend", "state is $state")
        if (originalRead?.complete != true) return refused("approveSpend", "the original set read is incomplete")
        spendReason = reason
        state = SparkTxState.SPEND_APPROVED
        return SparkTxResult(true)
    }

    fun declineSpend(reason: String): SparkTxResult {
        if (state != SparkTxState.IDLE && state != SparkTxState.ORIGINAL_CAPTURED && state != SparkTxState.SPEND_APPROVED) {
            return refused("declineSpend", "state is $state")
        }
        spendReason = reason
        state = SparkTxState.DECLINED
        return SparkTxResult(true)
    }

    fun confirmSpend(nowMs: Long, restoreSource: String? = null): SparkTxResult {
        if (state != SparkTxState.SPEND_APPROVED) return refused("confirmSpend", "state is $state")
        spendEverConfirmed = true
        spendConfirmedAtMs = nowMs
        tpRestoreSource = restoreSource
        state = SparkTxState.SPEND_CONFIRMED
        return SparkTxResult(true)
    }

    fun captureRerolled(read: SparkSetReading): SparkTxResult {
        if (state == SparkTxState.REROLLED_CAPTURED) return SparkTxResult(true, "already captured")
        if (state != SparkTxState.SPEND_CONFIRMED) return refused("captureRerolled", "state is $state")
        rerolledRead = read
        state = SparkTxState.REROLLED_CAPTURED
        return SparkTxResult(true)
    }

    fun introPassed(): SparkTxResult {
        if (state == SparkTxState.SELECTION_INTRO_PASSED) return SparkTxResult(true, "already passed")
        // SPEND_CONFIRMED is allowed too: a fast transition can skip the result-screen read, so the pager's Rerolled
        // page supplies the set.
        if (state != SparkTxState.REROLLED_CAPTURED && state != SparkTxState.SPEND_CONFIRMED) {
            return refused("introPassed", "state is $state")
        }
        state = SparkTxState.SELECTION_INTRO_PASSED
        return SparkTxResult(true)
    }

    fun recordPagerRead(side: SparkSetSide, read: SparkSetReading): SparkTxResult {
        if (!postSpend) return refused("recordPagerRead", "state is $state")
        pagerReads[side] = read
        return SparkTxResult(true)
    }

    fun setsVerified(): SparkTxResult {
        if (state == SparkTxState.BOTH_SETS_VERIFIED) return SparkTxResult(true, "already verified")
        if (!postSpend) return refused("setsVerified", "state is $state")
        if (pagerReads[SparkSetSide.ORIGINAL] == null || pagerReads[SparkSetSide.REROLLED] == null) {
            return refused("setsVerified", "both pager pages must be read first")
        }
        state = SparkTxState.BOTH_SETS_VERIFIED
        return SparkTxResult(true)
    }

    /**
     * A certain choice requires both pager reads (BOTH_SETS_VERIFIED); the uncertain keep-original fallback is allowed
     * from any post-spend state once the Original page was read, since it cannot lose the career's own set.
     */
    fun selectWinner(chosen: SparkChoice): SparkTxResult {
        if (state == SparkTxState.WINNER_SELECTED) return SparkTxResult(true, "already selected")
        val fallbackOk =
            !chosen.certain && chosen.side == SparkSetSide.ORIGINAL && postSpend && pagerReads[SparkSetSide.ORIGINAL] != null
        if (state != SparkTxState.BOTH_SETS_VERIFIED && !fallbackOk) {
            return refused("selectWinner", "state is $state and the keep-original fallback conditions do not hold")
        }
        choice = chosen
        winner = chosen.side
        state = SparkTxState.WINNER_SELECTED
        return SparkTxResult(true)
    }

    fun verifyFinalConfirmation(): SparkTxResult {
        if (state == SparkTxState.FINAL_CONFIRMATION_VERIFIED) return SparkTxResult(true, "already verified")
        if (state != SparkTxState.WINNER_SELECTED) return refused("verifyFinalConfirmation", "state is $state")
        state = SparkTxState.FINAL_CONFIRMATION_VERIFIED
        return SparkTxResult(true)
    }

    fun markKeptRecorded() {
        keptRecorded = true
    }

    fun markChoiceRecorded() {
        choiceRecorded = true
    }

    fun useConfirmationRetry(): Boolean {
        if (confirmationRetryUsed) return false
        confirmationRetryUsed = true
        return true
    }

    fun usePagerNavRetry(): Boolean {
        if (pagerNavRetryUsed) return false
        pagerNavRetryUsed = true
        return true
    }

    fun pagerRescanUsed(side: SparkSetSide): Boolean = side in pagerRescansUsed

    /** Claims [side]'s single full rescan; false once spent, which bounds the re-read at one per page. */
    fun usePagerRescan(side: SparkSetSide): Boolean {
        if (side in pagerRescansUsed) return false
        pagerRescansUsed.add(side)
        return true
    }

    fun replacePagerRead(side: SparkSetSide, read: SparkSetReading): SparkTxResult {
        if (!postSpend) return refused("replacePagerRead", "state is $state")
        pagerReads[side] = read
        return SparkTxResult(true)
    }

    /** A spend career may only complete with its kept-set and choice records written. */
    fun complete(): SparkTxResult {
        if (state == SparkTxState.COMPLETE) return SparkTxResult(true, "already complete")
        if (state != SparkTxState.FINAL_CONFIRMATION_VERIFIED) return refused("complete", "state is $state")
        if (spendEverConfirmed && (!keptRecorded || !choiceRecorded)) {
            return refused("complete", "a spend career cannot complete without its kept and choice records")
        }
        state = SparkTxState.COMPLETE
        return SparkTxResult(true)
    }

    fun block(reason: String): SparkTxResult {
        if (state == SparkTxState.BLOCKED) return SparkTxResult(true, "already blocked")
        blockedReason = reason
        state = SparkTxState.BLOCKED
        return SparkTxResult(true)
    }

    private fun refused(transition: String, why: String): SparkTxResult = SparkTxResult(false, "$transition refused: $why")
}

/**
 * Built independently of the finalize token so the two cannot couple: trainee, scenario, queue run (0 for single runs),
 * and per-career nonce.
 */
internal fun buildSparkCareerToken(
    traineeIdentity: String,
    scenario: String,
    queueRun: Int?,
    careerNonce: String,
): String = "$traineeIdentity|$scenario|run${queueRun ?: 0}|$careerNonce"

/**
 * Only a COMPLETE career's spark flow is next on screen; every other run result invalidates the transaction (same rule
 * as the finalize verdict).
 */
internal fun shouldClearSparkTransactionForRunResult(code: TaskResultCode): Boolean = code != TaskResultCode.TASK_RESULT_COMPLETE

/**
 * Whether [transaction] may drive the post-spend selection screens: it must be post-spend and younger than
 * [SPARK_TRANSACTION_MAX_AGE_MS]. Anything else (restart, hand-played career, terminal state, stale spend) blocks
 * instead of guessing.
 */
internal fun sparkSelectionDrivable(transaction: SparkRerollTransaction?, nowMs: Long): Boolean {
    if (transaction == null || !transaction.postSpend) return false
    val spentAt = transaction.spendConfirmedAtMs ?: return false
    return (nowMs - spentAt) in 0..SPARK_TRANSACTION_MAX_AGE_MS
}

/**
 * Process-local holder for the current career's reroll transaction. Only [beginCareer] (the one creation site, enforced
 * by a source-guard test), [invalidate] and [clearOnHome] mutate it. Constructors never do: the navigator builds
 * throwaway Game/Campaign objects during startup, and a constructor-side clear once erased the finalization verdict.
 */
internal object SparkRerollGate {
    @Volatile
    var transaction: SparkRerollTransaction? = null
        private set

    /**
     * Called at the career attachment boundary in `Game.start()` and nowhere else: drops the previous career's
     * transaction and installs a fresh one. Never from a constructor, the queue loop before launch navigation, or a
     * misc task.
     */
    fun beginCareer(nonce: String, queueRun: Int?, nowMs: Long) {
        transaction = SparkRerollTransaction(nonce, queueRun, nowMs)
    }

    /** Invalidates the transaction: non-COMPLETE run results, interrupted navigation, manual stop. */
    fun invalidate(reason: String) {
        transaction = null
    }

    /**
     * The navigator reached Home. Clears only a transaction that committed 30 TP or reached a terminal state. A
     * pre-spend one survives: Home is also crossed on the way into a career and by the daily-reset bounce mid-career,
     * and clearing it there left a live career unable to price its redraw. It authorizes only lossless actions, and the
     * next attachment replaces it.
     */
    fun clearOnHome(): Boolean {
        val current = transaction ?: return false
        if (current.preSpend) return false
        transaction = null
        return true
    }

    /** Full reset, for test isolation only. */
    fun reset() {
        transaction = null
    }
}

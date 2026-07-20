package com.steve1316.uma_android_automation.bot

/**
 * Spark Reroll transaction: the explicit lifecycle of one career's 30 TP reroll, from the
 * original-set read through the spend, both selection pages, the verified final confirmation,
 * and the corpus records.
 *
 * Why this exists: the reroll's post-spend flow used to be tracked by three navigator booleans
 * that died with the instance and carried no career identity. Ten live spends were resolved by
 * the generic results handler clicking whatever page the game showed first, and no kept-set
 * record was written for any of them. The selection screens irreversibly discard one of two
 * sets ("You won't be able to change Sparks later."), so the choice needs the same lifecycle
 * discipline as the Finish click: exact career identity, explicit states, and fail-safe
 * behavior when the transaction is missing.
 *
 * Lifecycle rule (mirrors [CareerFinalizeGate], deliberately not shared with it): **only
 * explicit lifecycle events mutate the gate; object construction never does.** A transaction
 * is created by [SparkRerollGate.beginCareer] at the CAREER ATTACHMENT boundary - the point in
 * `Game.start()` where the bot is about to hand control to the career task - and nowhere else.
 * It is invalidated by the next career attachment, by any run result other than COMPLETE, by an
 * interrupted between-run navigation, and by the navigator reaching Home AFTER a spend or a
 * terminal outcome. In-memory on purpose: a process restart loses it, and every selection
 * handler treats a missing transaction as "block safely and leave the screen for the operator"
 * rather than guessing.
 *
 * **Why arming moved (2026-07-19 live defect).** The transaction used to be armed in the queue
 * run loop, BEFORE `Game.start()` ran its cold-start launch navigation. That navigation
 * legitimately passes the game's Home screen on its way INTO the career, and the navigator's
 * Home handler cleared the brand-new transaction as stale:
 *
 *     [NAV] Attempt 0: Detected state = HOME_SCREEN
 *     [SPARKS] Home reached with a live reroll transaction (state IDLE); clearing it as stale.
 *
 * The whole career then ran with no transaction and the redraw could not be priced. Run start
 * is simply not proof that a career exists - only reaching the career task is. `Game.start()`
 * arms immediately before `task.start()`, which is the FIRST point at which exactly one of
 * these is true: the bot was already on the training menu, it started on a career-end screen,
 * or auto-navigation reported reaching the training menu. Misc tasks (Daily Races, Team
 * Trials) are not careers and deliberately never arm.
 *
 * The Home clear is additionally phase-aware, which independently defuses the same class of
 * bug (and the known mid-career daily-reset bounce back to the lobby): only a POST-SPEND or
 * terminal transaction is destroyed at Home. A pre-spend transaction authorizes nothing
 * destructive - the spend re-reads the set live and is gated by the EV policy, and confirming
 * an ordinary keep dialog keeps the career's own set - so surviving a Home pass costs nothing,
 * while a post-spend transaction at Home is genuinely stale and must never govern a later
 * selection screen.
 */

/**
 * How long a post-spend transaction stays usable, in milliseconds. Selection navigation runs
 * seconds after the spend and the between-run navigation deadline is 10 minutes; three times
 * that bound tolerates heavy dialog recovery while making a transaction from an abandoned
 * earlier career structurally unusable.
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

    /** The spend was declined (gate negative, setting off, spend unavailable). Terminal for
     * the reroll; the ordinary keep-original flow continues outside the transaction. */
    DECLINED,

    /** A safety stop: contradiction, unreadable screen, or a missing prerequisite. Terminal. */
    BLOCKED,
}

/** Result of a transition attempt: refused transitions carry the reason for the log. */
internal data class SparkTxResult(val ok: Boolean, val reason: String = "")

/**
 * One career's reroll transaction. Mutable by design (the navigator drives it screen by
 * screen), but every mutation is a guarded transition that refuses out-of-order calls, so a
 * duplicate spend or a second final confirmation is structurally impossible.
 */
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

    var choice: SparkChoice? = null
        private set
    var winner: SparkSetSide? = null
        private set

    /** Latched forever on the first confirmed spend click; a second spend can never be
     * approved on the same transaction. */
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

    /** One Cancel-and-retry is allowed when the confirmation header disagrees or is
     * unreadable; the second failure blocks. */
    var confirmationRetryUsed: Boolean = false
        private set

    /** One chevron-navigation retry per target page; the second failure blocks. */
    var pagerNavRetryUsed: Boolean = false
        private set

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

    /** No 30 TP has been committed on this career yet. A pre-spend transaction can only ever
     * authorize lossless actions (pricing a redraw from a fresh read, or confirming the
     * career's own rolled set), which is why it survives a Home pass. */
    val preSpend: Boolean
        get() = !spendEverConfirmed && !terminal

    fun pagerRead(side: SparkSetSide): SparkSetReading? = pagerReads[side]

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
        // SPEND_CONFIRMED is allowed too: a fast transition can skip the result-screen read,
        // in which case the pager's own Rerolled page read supplies the set.
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
     * Record the policy's choice. A certain choice requires both pager reads
     * (BOTH_SETS_VERIFIED); the uncertain keep-original fallback is allowed from any
     * post-spend state as long as the Original page itself was read, because it is the one
     * choice that cannot lose the career's own set.
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

/** Same shape as the finalize token, built independently so the two features cannot couple:
 * trainee identity, scenario, queue run (0 for single runs), and the per-career nonce minted
 * at run start. */
internal fun buildSparkCareerToken(
    traineeIdentity: String,
    scenario: String,
    queueRun: Int?,
    careerNonce: String,
): String = "$traineeIdentity|$scenario|run${queueRun ?: 0}|$careerNonce"

/**
 * Whether a finished run's result must invalidate the transaction. Identical rule to the
 * finalize verdict: only a COMPLETE career's spark flow is the next thing on screen; every
 * other result (manual stop, abort, unhandled error, breakpoint, queue skip) means it is not.
 */
internal fun shouldClearSparkTransactionForRunResult(code: TaskResultCode): Boolean = code != TaskResultCode.TASK_RESULT_COMPLETE

/**
 * Whether the post-spend selection screens may be driven by [transaction]: it must exist, be
 * in a post-spend state, and its spend must be younger than [SPARK_TRANSACTION_MAX_AGE_MS].
 * Anything else - no transaction (process restart, hand-played career), a terminal state, or
 * a stale spend - means the selection screens block instead of guessing.
 */
internal fun sparkSelectionDrivable(transaction: SparkRerollTransaction?, nowMs: Long): Boolean {
    if (transaction == null || !transaction.postSpend) return false
    val spentAt = transaction.spendConfirmedAtMs ?: return false
    return (nowMs - spentAt) in 0..SPARK_TRANSACTION_MAX_AGE_MS
}

/**
 * Process-local holder for the current career's reroll transaction.
 *
 * Only explicit lifecycle events mutate it: [beginCareer] from the real career task's run
 * loop (the one creation site, enforced by a source-guard test), [invalidate] from the
 * run-result path and interrupted navigations, and [clearOnHome] from the navigator's Home
 * handler. Constructors never touch it - the navigator builds throwaway Game/Campaign objects
 * during startup, and a constructor-side clear is exactly the defect that once erased the
 * career-finalization verdict.
 */
internal object SparkRerollGate {
    @Volatile
    var transaction: SparkRerollTransaction? = null
        private set

    /** Called at the CAREER ATTACHMENT boundary in `Game.start()` (and nowhere else): drops
     * whatever the previous career left and installs a fresh identity for the career the bot
     * is about to play. Never called from a constructor, from the queue run loop before
     * launch navigation, or for a misc (non-career) task. */
    fun beginCareer(nonce: String, queueRun: Int?, nowMs: Long) {
        transaction = SparkRerollTransaction(nonce, queueRun, nowMs)
    }

    /** Invalidate the transaction entirely: non-COMPLETE run results, interrupted
     * navigation, manual stop. A missing transaction on a selection screen blocks safely. */
    fun invalidate(reason: String) {
        transaction = null
    }

    /**
     * The navigator reached the game's Home screen. Clears only a transaction that has already
     * committed 30 TP or reached a terminal state - the cases where a survivor could wrongly
     * govern a later career's selection screens.
     *
     * A pre-spend, non-terminal transaction deliberately SURVIVES: Home is also crossed on the
     * way into a career (cold-start launch navigation) and by the game's daily-reset bounce
     * back to the lobby mid-career, and destroying it there is exactly the defect that left a
     * whole live career unable to price its redraw. Nothing is risked by keeping it, because a
     * pre-spend transaction can authorize only lossless actions and the next career attachment
     * replaces it outright.
     *
     * @return true when a transaction was actually cleared.
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

package com.steve1316.uma_android_automation.bot

/**
 * Correlation handoff between a career LAUNCH (the between-run / cold-start navigation that steers
 * Legacy Select, Support Deck, and Start Career) and the CAREER that launch ultimately produces.
 *
 * Why a dedicated primitive, separate from [CareerFinalizeGate.context] and
 * [SparkRerollGate.transaction]: lineage is read on the Legacy Select screen DURING launch
 * navigation, before the new career has attached. At that moment both sibling gates still describe
 * the PREVIOUS career - they are re-created only at the career-attachment boundary in Game.start.
 * Tagging a lineage read with either of them would label it with the wrong career: the between-run
 * off-by-one. This gate mints a fresh id for the launch being navigated ([pending]), and the new
 * career adopts it as [active] only when it actually attaches, so a lineage read and the career it
 * belongs to share one id that can never be the previous run's.
 *
 * Lifecycle - only explicit events mutate this gate; constructors never do, mirroring
 * [SparkRerollGate] and [CareerFinalizeGate] (a constructor-side clear is the exact defect that
 * once erased a career-finalization verdict):
 *   - [beginLaunch]  navigator, once per navigate() pass: mints a fresh pending id, discarding any
 *                    un-adopted pending left by a launch that never attached a career.
 *   - [adopt]        Game.start career-attachment boundary: moves pending -> active so the career's
 *                    telemetry reads its own launch id. With no pending (a resumed career, a process
 *                    restart mid-career) it mints a fresh active id, so telemetry is still labelled
 *                    but carries no launch that any lineage event could join.
 *   - [invalidate]   non-COMPLETE run result / interrupted navigation: drops the active id.
 *   - [reset]        test isolation only.
 *
 * In-memory on purpose: a process or service restart loses both ids and the next launch mints
 * anew, which fails safe - an orphaned lineage event whose id no career adopted simply never joins.
 */
internal data class LaunchTransaction(
    /** The correlation id carried by the lineage event and, once adopted, by the career's telemetry.
     * Unique across launches (the monotonic [launchSeq]) and across process launches (the process
     * nonce), deterministic within one process given the sequence of lifecycle calls. */
    val id: String,
    /** Monotonic per-process launch counter. Distinguishes back-to-back launches of the same trainee
     * and scenario that would otherwise share every other identity field. */
    val launchSeq: Int,
    val mintedAtMs: Long,
)

internal object LaunchTransactionGate {
    /** The id minted for the launch currently being navigated, not yet adopted by a career. The
     * Legacy Select lineage capture reads THIS - never [active], which still holds the previous
     * career's id until the new career attaches. */
    @Volatile
    var pending: LaunchTransaction? = null
        private set

    /** The id the currently-running career adopted at its attachment boundary: the correlation the
     * career's own telemetry records stamp. */
    @Volatile
    var active: LaunchTransaction? = null
        private set

    @Volatile
    private var processNonce: String? = null

    @Volatile
    private var launchSeq: Int = 0

    /**
     * Set the per-process nonce that disambiguates ids across process launches. Production
     * initializes it lazily on the first mint; tests set a fixed value for deterministic ids.
     * Idempotent: a no-op once set, so a later call cannot renumber ids mid-process.
     */
    fun initProcess(nonce: String) {
        if (processNonce == null) processNonce = nonce
    }

    private fun nextTransaction(nowMs: Long): LaunchTransaction {
        if (processNonce == null) processNonce = java.util.UUID.randomUUID().toString().substring(0, 8)
        launchSeq += 1
        return LaunchTransaction(id = "$processNonce-$launchSeq", launchSeq = launchSeq, mintedAtMs = nowMs)
    }

    /**
     * Called by the navigator once per navigate() launch pass (and nowhere else): mints a fresh
     * pending id for the launch about to be steered, discarding any un-adopted pending from a
     * previous launch that never attached a career. The lineage capture later in the same pass
     * READS this pending; it must never mint, so a capture retried within one pass keeps one id.
     */
    fun beginLaunch(nowMs: Long): LaunchTransaction {
        val tx = nextTransaction(nowMs)
        pending = tx
        return tx
    }

    /**
     * Called at the CAREER ATTACHMENT boundary in Game.start (and nowhere else): the new career
     * adopts the pending launch id as [active] and the pending slot is cleared, so the next
     * beginLaunch cannot see it and no stale pending can ever be adopted by a later career. With no
     * pending (a career resumed without launch navigation, or a mid-career process restart) a fresh
     * active id is minted so telemetry is still labelled; no lineage event can join that id.
     */
    fun adopt(nowMs: Long): LaunchTransaction {
        val adopted = pending ?: nextTransaction(nowMs)
        active = adopted
        pending = null
        return adopted
    }

    /** Invalidate the active id: non-COMPLETE run results, interrupted navigation, manual stop.
     * Leaves any freshly-minted pending alone (a new launch may already be under way). */
    fun invalidate() {
        active = null
    }

    /** Full reset, for test isolation only. */
    fun reset() {
        pending = null
        active = null
        processNonce = null
        launchSeq = 0
    }
}

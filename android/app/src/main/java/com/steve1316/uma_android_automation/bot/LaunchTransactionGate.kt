package com.steve1316.uma_android_automation.bot

/**
 * Correlation id handoff between a career launch and the career it produces. Lineage is read on Legacy Select during
 * launch navigation, while [CareerFinalizeGate.context] and [SparkRerollGate.transaction] still describe the PREVIOUS
 * career, so tagging with them would be off by one. A launch mints [pending]; the career adopts it as [active] only
 * when it attaches (Game.start).
 *
 * Only explicit events mutate this gate, never a constructor (a constructor-side clear once erased a finalization
 * verdict). In-memory on purpose: a restart loses both ids and an orphaned lineage event simply never joins.
 */
internal data class LaunchTransaction(
    /** Unique across launches ([launchSeq]) and across process launches (the process nonce). */
    val id: String,
    val launchSeq: Int,
    val mintedAtMs: Long,
)

internal object LaunchTransactionGate {
    /** Minted for the launch being navigated. Lineage capture reads THIS, never [active], which still holds the previous career's id. */
    @Volatile
    var pending: LaunchTransaction? = null
        private set

    @Volatile
    var active: LaunchTransaction? = null
        private set

    @Volatile
    private var processNonce: String? = null

    @Volatile
    private var launchSeq: Int = 0

    /** Idempotent, so a later call cannot renumber ids mid-process. Tests set a fixed value. */
    fun initProcess(nonce: String) {
        if (processNonce == null) processNonce = nonce
    }

    private fun nextTransaction(nowMs: Long): LaunchTransaction {
        if (processNonce == null) processNonce = java.util.UUID.randomUUID().toString().substring(0, 8)
        launchSeq += 1
        return LaunchTransaction(id = "$processNonce-$launchSeq", launchSeq = launchSeq, mintedAtMs = nowMs)
    }

    /** Once per navigate() pass. Lineage capture only READS pending, so a retried capture keeps one id. */
    fun beginLaunch(nowMs: Long): LaunchTransaction {
        val tx = nextTransaction(nowMs)
        pending = tx
        return tx
    }

    /** Career attachment boundary only. With no pending (resumed career, mid-career restart) a fresh id is minted that no lineage event can join. */
    fun adopt(nowMs: Long): LaunchTransaction {
        val adopted = pending ?: nextTransaction(nowMs)
        active = adopted
        pending = null
        return adopted
    }

    /** Leaves a freshly minted pending alone: a new launch may already be under way. */
    fun invalidate() {
        active = null
    }

    fun reset() {
        pending = null
        active = null
        processNonce = null
        launchSeq = 0
    }
}

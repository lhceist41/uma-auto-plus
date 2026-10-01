package com.steve1316.uma_android_automation.bot

/**
 * Cross-layer launch-identity enforcement: the React Start barrier hands the verified preset revision and content hash
 * to this gate; the bot session entry re-reads the revision from SQLite and asks for a verdict before any settings are
 * consumed or any tap is made. A mismatch means a settings write landed between verification and load (a time-of-check
 * to time-of-use window), and the session aborts without a tap.
 * The expected identity is single-use. A session started without one (legacy or non-UI entry) is [Verdict.NOT_SET]: the
 * caller warns and proceeds. After a MISMATCH the gate latches [isBlockedAfterMismatch], because the mismatch consumed
 * the expectation and a second overlay start would otherwise trust the stale config just rejected. The latch is
 * process-local and cleared only by a fresh UI-verified [setExpected], so fresh-process non-UI crash recovery keeps
 * working.
 * Pure and JVM-testable: no settings, SQLite or game dependency.
 */
object LaunchIdentityGate {
    enum class Verdict { PASS, MISMATCH, NOT_SET }

    data class Expected(val revision: Int, val hash: String)

    @Volatile
    private var expected: Expected? = null

    /**
     * Poison latch: set on a MISMATCH, cleared only by a fresh UI-verified [setExpected] (or [clear]), so the rejected
     * stale config cannot slip in on a retry. A process restart clears it.
     */
    @Volatile
    private var blockedAfterMismatch: Boolean = false

    /**
     * One-shot validation hook, armed only via [LogStreamServer]'s default-off CMD:ARM_LAUNCH_MISMATCH_TEST: forces the
     * next verdict that has an expected identity to MISMATCH and latches the block like a real one. Fail-safe (can only
     * turn a PASS into an abort), never persisted, not re-armed by [setExpected].
     */
    @Volatile
    private var forceMismatchOnceForTest: Boolean = false

    /** What React verified, for logging after a verdict. Null once consumed or never set. */
    val current: Expected?
        get() = expected

    /**
     * Store the identity the React barrier just verified. Also clears [blockedAfterMismatch]: the only in-process path
     * that re-arms launch verification after a mismatch.
     */
    fun setExpected(revision: Int, hash: String) {
        expected = Expected(revision, hash)
        blockedAfterMismatch = false
    }

    /** Clear all in-process state without a verdict (test isolation / a full re-arm). */
    fun clear() {
        expected = null
        blockedAfterMismatch = false
        forceMismatchOnceForTest = false
    }

    /**
     * Compares the loaded revision and settings hash against the expected identity and consumes the expectation. Both
     * must match: the revision alone missed a bot that read an older copy of the settings than the app had verified.
     * PASS and MISMATCH only occur when an expectation was set; a MISMATCH latches [blockedAfterMismatch].
     */
    fun verdict(loadedRevision: Int, loadedHash: String): Verdict {
        val e = expected ?: return Verdict.NOT_SET
        expected = null
        if (forceMismatchOnceForTest) {
            // Validation hook: force MISMATCH before the real comparison, so the marker below, not StartModule's
            // mismatch log, is the truthful record of the abort.
            forceMismatchOnceForTest = false
            blockedAfterMismatch = true
            try {
                android.util.Log.i("LaunchIdentityGate", "[VALIDATION] forced launch-identity MISMATCH consumed (synthetic; real revision not compared)")
            } catch (_: Exception) {
            }
            return Verdict.MISMATCH
        }
        if (e.revision == loadedRevision && e.hash == loadedHash) return Verdict.PASS
        blockedAfterMismatch = true
        return Verdict.MISMATCH
    }

    fun isBlockedAfterMismatch(): Boolean = blockedAfterMismatch

    /**
     * Arms the one-shot forced-mismatch hook (see [forceMismatchOnceForTest]); reached only through the default-off
     * Remote Log Viewer channel. Fail-safe: can only force an abort.
     */
    fun armForcedMismatchForTest() {
        forceMismatchOnceForTest = true
    }

    /** A greppable description of the expectation for the session log. */
    fun describe(e: Expected): String = "revision=${e.revision} hash=${e.hash}"

    /** The categories the app's Start check hashes. Must equal LAUNCH_CRITICAL_CATEGORIES in src/lib/launchConfig.ts. */
    val HASHED_CATEGORIES = setOf("general", "training", "trainingEvent", "skills", "racing", "runQueue")

    /** Rows inside those categories left out of the hash. Must equal LAUNCH_IDENTITY_EXCLUDED_KEYS in src/lib/launchConfig.ts. */
    val EXCLUDED_KEYS = setOf("general.settingsRevision", "racing.racingPlanData")

    /**
     * 32-bit FNV-1a over UTF-16 code units, 8 lowercase hex digits: stableHash in src/lib/launchConfig.ts. Kotlin Char
     * and Int wrap exactly like JS charCodeAt and Math.imul.
     */
    fun stableHash(input: String): String {
        var h = 0x811c9dc5.toInt()
        for (c in input) {
            h = h xor c.code
            h *= 0x01000193
        }
        return Integer.toHexString(h).padStart(8, '0')
    }

    /**
     * Mirrors identityFromRows in src/lib/launchConfig.ts: the category is the text before the first '.', rows outside
     * [HASHED_CATEGORIES] or in [EXCLUDED_KEYS] are dropped, and the raw values form "category.key=value" lines sorted
     * by UTF-16 order (same as JS) and joined with newlines.
     */
    fun identityHash(rows: Map<String, String>): String {
        val lines = rows.filter { (rowKey, _) -> rowKey.substringBefore('.') in HASHED_CATEGORIES && rowKey !in EXCLUDED_KEYS }.map { (rowKey, value) -> "$rowKey=$value" }.sorted()
        return stableHash(lines.joinToString("\n"))
    }

    /** [identityHash] of the bot's launch snapshot, whose rows are keyed "category/key". */
    fun snapshotIdentityHash(snapshot: Map<String, String>): String = identityHash(snapshot.mapKeys { (key, _) -> key.replaceFirst('/', '.') })
}

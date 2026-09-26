package com.steve1316.uma_android_automation.bot

/**
 * Cross-layer launch-identity enforcement.
 *
 * The React Start barrier verifies that the intended preset is persisted (flush + atomic
 * read-back) and then hands the verified identity -- the preset-apply revision plus the
 * content hash it computed -- to this gate before starting BotService. The bot session entry
 * re-reads the revision from SQLite and asks this gate for a verdict BEFORE any settings are
 * consumed or any game interaction happens. A mismatch means a settings write landed between
 * React's verification and the Kotlin load (the time-of-check to time-of-use window), and the
 * session aborts without a single tap instead of launching a configuration nobody verified.
 *
 * The expected identity is single-use: the verdict consumes it, so a stale identity from an
 * earlier Start can never validate a later, unrelated session. A session started without any
 * identity (legacy or non-UI entry) is reported as [Verdict.NOT_SET]; the caller warns and
 * proceeds, preserving non-UI start paths.
 *
 * Once a MISMATCH is seen in this process the gate latches [isBlockedAfterMismatch]: the mismatch
 * consumes the expectation, so the very next verdict is [Verdict.NOT_SET] again -- and without the
 * latch a second overlay start would trust the same stale config the mismatch just rejected. The
 * latch is process-local (never persisted), cleared only by a fresh UI-verified [setExpected]; a
 * process restart resets it, keeping legitimate fresh-process non-UI crash recovery working.
 *
 * A one-shot [armForcedMismatchForTest] validation hook lets the default-off Remote Log Viewer
 * command channel force the NEXT verdict to MISMATCH, so the live overlay can deterministically
 * exercise the mismatch -> sticky-block path without a stale disk. It is fail-safe (can only turn a
 * would-be PASS into an abort), process-local, never persisted, and self-clearing.
 *
 * Pure and JVM-testable: no settings reads and no SQLite or game dependency. The only Android touch
 * is a best-effort android.util.Log marker on the validation-hook path, wrapped so it can never
 * change the verdict and a no-op under the unit tests' returnDefaultValues.
 */
object LaunchIdentityGate {
    /** The outcome of comparing the loaded revision against the React-verified one. */
    enum class Verdict { PASS, MISMATCH, NOT_SET }

    data class Expected(val revision: Int, val hash: String)

    @Volatile
    private var expected: Expected? = null

    /**
     * Process-local poison latch: set once a MISMATCH is seen, cleared only by a fresh UI-verified
     * [setExpected] (or [clear]). While set, the caller must refuse an unverified NOT_SET start so
     * the stale config the mismatch just rejected cannot slip in on a retry. Never persisted, so a
     * process restart clears it and fresh-process non-UI crash recovery is unaffected.
     */
    @Volatile
    private var blockedAfterMismatch: Boolean = false

    /**
     * One-shot, process-local validation hook. When armed (only via [LogStreamServer]'s default-off
     * CMD:ARM_LAUNCH_MISMATCH_TEST), the next verdict that has an expected identity is forced to
     * [Verdict.MISMATCH] BEFORE the real comparison and latches [blockedAfterMismatch]
     * exactly as a real mismatch would, so the live overlay can drive the sticky-guard path
     * deterministically. Fail-safe: it can only make a would-be PASS abort, never the reverse.
     * Consumed once, never persisted, and NOT re-armed by [setExpected].
     */
    @Volatile
    private var forceMismatchOnceForTest: Boolean = false

    /** What React verified, for logging after a verdict. Null once consumed or never set. */
    val current: Expected?
        get() = expected

    /**
     * Store the identity the React barrier just verified. Called right before BotService starts.
     * A fresh UI-verified identity also clears [blockedAfterMismatch]: this is the only in-process
     * path that re-arms launch verification after a prior mismatch poisoned it.
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
     * Compare the freshly-loaded revision and settings hash ([identityHash] of the rows the bot
     * read) against the expected identity and CONSUME the expectation (single-use). Both must
     * match: the revision alone missed a bot that read an older copy of the settings than the app
     * had saved and verified, and launched on it. [Verdict.PASS] and [Verdict.MISMATCH] only occur
     * when an expectation was set; [Verdict.NOT_SET] means this session was started without one. A
     * [Verdict.MISMATCH] also latches [blockedAfterMismatch] so the next unverified start fails closed.
     */
    fun verdict(loadedRevision: Int, loadedHash: String): Verdict {
        val e = expected ?: return Verdict.NOT_SET
        expected = null
        if (forceMismatchOnceForTest) {
            // Validation hook consumed: force a synthetic MISMATCH before the real comparison and latch
            // the sticky block exactly as a real mismatch would. One-shot (self-clears). The real
            // revision and hash are deliberately NOT compared here, so the marker below -- not
            // StartModule's mismatch log -- is the truthful record of why this launch aborted.
            forceMismatchOnceForTest = false
            blockedAfterMismatch = true
            try {
                android.util.Log.i("LaunchIdentityGate", "[VALIDATION] forced launch-identity MISMATCH consumed (synthetic; real revision not compared)")
            } catch (_: Exception) {
            }
            return Verdict.MISMATCH
        }
        if (e.revision == loadedRevision && e.hash == loadedHash) return Verdict.PASS
        // A mismatch poisons the process: the expectation is now consumed, so the next verdict is
        // NOT_SET, and the caller must fail closed until a fresh UI setExpected re-arms the gate.
        blockedAfterMismatch = true
        return Verdict.MISMATCH
    }

    /**
     * Whether a MISMATCH has been seen in this process since the last [setExpected] or [clear]. The
     * caller uses this to fail a NOT_SET start closed after a mismatch instead of trusting disk.
     */
    fun isBlockedAfterMismatch(): Boolean = blockedAfterMismatch

    /**
     * Arm the one-shot forced-mismatch validation hook (see [forceMismatchOnceForTest]). Reached only
     * through the default-off Remote Log Viewer command channel ([LogStreamServer]); there is no UI or
     * settings path. Idempotent -- arming twice is the same as once. Fail-safe: can only force an abort.
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
     * 32-bit FNV-1a over UTF-16 code units, as 8 lowercase hex digits: stableHash in
     * src/lib/launchConfig.ts. A Kotlin Char is one UTF-16 code unit, like JS charCodeAt, and Int
     * multiplication wraps exactly like Math.imul.
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
     * The settings identity hash of stored rows keyed "category.key", computed as identityFromRows
     * in src/lib/launchConfig.ts computes it: the category is the text before the first '.', rows
     * outside [HASHED_CATEGORIES] and the [EXCLUDED_KEYS] are dropped, and the raw stored values
     * form "category.key=value" lines, sorted by UTF-16 code unit (Kotlin's String order, the same
     * as JS's default sort) and joined with newlines.
     */
    fun identityHash(rows: Map<String, String>): String {
        val lines = rows.filter { (rowKey, _) -> rowKey.substringBefore('.') in HASHED_CATEGORIES && rowKey !in EXCLUDED_KEYS }.map { (rowKey, value) -> "$rowKey=$value" }.sorted()
        return stableHash(lines.joinToString("\n"))
    }

    /** [identityHash] of the bot's launch snapshot, whose rows are keyed "category/key". */
    fun snapshotIdentityHash(snapshot: Map<String, String>): String = identityHash(snapshot.mapKeys { (key, _) -> key.replaceFirst('/', '.') })
}

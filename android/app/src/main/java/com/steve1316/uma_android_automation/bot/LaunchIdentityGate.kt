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
 * Pure and JVM-testable: no Android types, no settings reads of its own.
 */
object LaunchIdentityGate {
    /** The outcome of comparing the loaded revision against the React-verified one. */
    enum class Verdict { PASS, MISMATCH, NOT_SET }

    data class Expected(val revision: Int, val hash: String)

    @Volatile
    private var expected: Expected? = null

    /** What React verified, for logging after a verdict. Null once consumed or never set. */
    val current: Expected?
        get() = expected

    /** Store the identity the React barrier just verified. Called right before BotService starts. */
    fun setExpected(revision: Int, hash: String) {
        expected = Expected(revision, hash)
    }

    /** Clear without a verdict (test isolation / an aborted launch attempt). */
    fun clear() {
        expected = null
    }

    /**
     * Compare the freshly-loaded revision against the expected identity and CONSUME the
     * expectation (single-use). [Verdict.PASS] and [Verdict.MISMATCH] only occur when an
     * expectation was set; [Verdict.NOT_SET] means this session was started without one.
     */
    fun verdict(loadedRevision: Int): Verdict {
        val e = expected ?: return Verdict.NOT_SET
        expected = null
        return if (e.revision == loadedRevision) Verdict.PASS else Verdict.MISMATCH
    }

    /** A greppable description of the expectation for the session log. */
    fun describe(e: Expected): String = "revision=${e.revision} hash=${e.hash}"
}

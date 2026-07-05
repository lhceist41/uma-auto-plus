package com.steve1316.uma_android_automation.bot

/**
 * The career-outcome label emitted in the `[CAREER_END]` ledger, derived from the task result code
 * and whether the bot confirmed a force-end at its source.
 *
 * - `INCOMPLETE` - the run did not finish a career under bot control: a user stop (breakpoint /
 *   stop-at-date / manual) or a bot failure (watchdog, timeout, unhandled exception). These must
 *   NEVER be counted as force-ends; only the result code separates them, so branch on it first.
 * - `FORCE_END` - a force-end the bot observed at its source. Today that is only a lost mandatory
 *   race the game will not let us retry past ([Campaign] sets the flag in handleTryAgainDialog).
 * - `COMPLETED` - reached the career-end screen with no confirmed force-end: a true win OR an
 *   unflagged early force-end (a fan / Result-Pts checkpoint miss is invisible at its trigger).
 *   `turn` is the discriminator here (a full arc ends near the scenario max, a force-end ends early).
 *
 * Pure and side-effect-free so the three branches are unit-testable without a live [Campaign].
 */
internal fun classifyCareerOutcome(resultCode: TaskResultCode, careerForceEnded: Boolean): String =
    when {
        resultCode != TaskResultCode.TASK_RESULT_COMPLETE -> "INCOMPLETE"
        careerForceEnded -> "FORCE_END"
        else -> "COMPLETED"
    }

/**
 * Stable short fingerprint of the config arm a career ran under, for the outcome corpus
 * (Stage 3 of the outcome-measurement plan). Two runs with the same fingerprint are
 * comparable; any change to an enumerated tunable or the app version starts a new arm.
 *
 * Canonicalization sorts by key so map iteration order can never split an arm. Pure and
 * side-effect-free so it is unit-testable without a live [Campaign].
 */
internal fun outcomeConfigFingerprint(appVersion: String, cfg: Map<String, String>): String {
    val canonical = cfg.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" } + ";app=$appVersion"
    return shortSha1(canonical)
}

/** First 10 hex chars of the SHA-1 of [text]; also used to digest racing-plan content. */
internal fun shortSha1(text: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(10)
}

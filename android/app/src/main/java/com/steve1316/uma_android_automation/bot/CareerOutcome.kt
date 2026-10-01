package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Career-outcome label for the `[CAREER_END]` ledger, from the task result code and whether the bot confirmed a force-end.
 * - `INCOMPLETE`: a user stop or bot failure; never a force-end, so branch on the result code first.
 * - `FORCE_END`: observed at its source (today only a lost mandatory race the game will not let us retry past).
 * - `COMPLETED`: reached the career-end screen with no confirmed force-end; a true win or an unflagged early force-end,
 *   which `turn` separates.
 */
internal fun classifyCareerOutcome(resultCode: TaskResultCode, careerForceEnded: Boolean): String =
    when {
        resultCode != TaskResultCode.TASK_RESULT_COMPLETE -> "INCOMPLETE"
        careerForceEnded -> "FORCE_END"
        else -> "COMPLETED"
    }

/**
 * Splits the `COMPLETED` ambiguity: `finaleWins >= finaleRaces > 0` is `WIN`, `finaleWins < finaleRaces` is
 * `FINALE_LOST`, and no observed finale stays `COMPLETED`, because only URA-style finales tag `RaceGrade.FINALE` and a
 * Unity Cup / Trackblazer completion must never be mislabeled. Other outcomes pass through.
 */
internal fun classifyCareerQuality(outcome: String, finaleRaces: Int, finaleWins: Int): String =
    when {
        outcome != "COMPLETED" -> outcome
        finaleRaces == 0 -> outcome
        finaleWins >= finaleRaces -> "WIN"
        else -> "FINALE_LOST"
    }

/**
 * Stable short fingerprint of the config arm a career ran under: any change to an enumerated tunable or the app version
 * starts a new arm. Sorted by key so map iteration order never splits an arm.
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

/** [reopen] opens the Details dialog once more only when [read] left stats unaccepted; stats still unaccepted are reported last-known, not fresh. */
internal fun readCareerEndStats(read: () -> Set<StatName>, reopen: (Set<StatName>) -> Boolean): Set<StatName> {
    val first = read()
    if (first.isEmpty() || !reopen(first)) return first
    return read()
}

internal val CAREER_END_STAT_KEYS: Map<StatName, String> =
    mapOf(StatName.SPEED to "spd", StatName.STAMINA to "sta", StatName.POWER to "pwr", StatName.GUTS to "grt", StatName.WIT to "wit")

/** A stat reported as unread (-1) is not last-known. */
internal fun lastKnownLedgerKeys(notAccepted: Set<StatName>, reportedUnread: Set<StatName>): List<String> =
    StatName.entries.filter { it in notAccepted && it !in reportedUnread }.map { CAREER_END_STAT_KEYS.getValue(it) }

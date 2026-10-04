package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName

/**
 * Career-outcome label for the `[CAREER_END]` ledger, from the task result code and whether the bot confirmed a force-end.
 * - `INCOMPLETE`: a user stop or bot failure; never a force-end, so branch on the result code first.
 * - `FORCE_END`: observed at its source: a lost mandatory race the game will not let us retry past, or the career-end
 *   screen reached before the Finale season ([endedBeforeFinale]).
 * - `COMPLETED`: reached the career-end screen with no confirmed force-end; a true win, or a career whose last turn
 *   was never read.
 */
internal fun classifyCareerOutcome(resultCode: TaskResultCode, careerForceEnded: Boolean): String =
    when {
        resultCode != TaskResultCode.TASK_RESULT_COMPLETE -> "INCOMPLETE"
        careerForceEnded -> "FORCE_END"
        else -> "COMPLETED"
    }

/**
 * Every scenario's full arc runs through the Finale season (turns 73-75); the game ends a career before it only on a
 * missed goal. The turn must be one read off the screen: an unread turn proves nothing.
 */
internal fun endedBeforeFinale(resultCode: TaskResultCode, lastObservedTurn: Int?): Boolean =
    resultCode == TaskResultCode.TASK_RESULT_COMPLETE && lastObservedTurn != null && lastObservedTurn <= 72

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

/** GC careers ended at 1.62x to 2.34x their last in-career count, while one inserted digit gives at least 5.5x. */
private const val CAREER_END_FAN_FALLBACK_MAX_RATIO = 5L

/**
 * The result screen groups the fan total in threes ("225,250"), so a read that broke the grouping lost or misread a character ("225,25o" would
 * strip to 22525). A total below the in-career count [heldFans] cannot be the final one. A space-grouped or plain digit run lost its separator, and
 * a lost separator can hide an inserted digit ("2251250"), so it counts only between a real [heldFans] (not the default 1 or unknown) and
 * [CAREER_END_FAN_FALLBACK_MAX_RATIO] times it. Anything else gives null. toIntOrNull: an overlong read must not throw, which would end the run.
 */
internal fun parseCareerEndFans(raw: String, heldFans: Int): Int? {
    val text = raw.trim().removePrefix("+").trim()
    val value = text.filter { it.isDigit() }.toIntOrNull() ?: return null
    if (Regex("""\d{1,3}(\s*[,.]\s*\d{3})*""").matches(text)) return value.takeIf { it >= heldFans }
    if (!Regex("""\d+|\d{1,3}(\s+\d{3})+""").matches(text)) return null
    return value.takeIf { heldFans > 1 && it >= heldFans && it.toLong() < CAREER_END_FAN_FALLBACK_MAX_RATIO * heldFans }
}

package com.steve1316.uma_android_automation.bot

/**
 * Sentinel for "no trustworthy reading". Matches what `determineSkillPoints` already returned on a
 * failed OCR, so callers that already treat -1 as keep-last-known-good need no new handling.
 */
const val SKILL_POINTS_UNREADABLE: Int = -1

/**
 * Parses the Skill Point field's OCR text into a value, or [SKILL_POINTS_UNREADABLE].
 *
 * The old parser stripped every non-digit and concatenated whatever survived, so any second number in
 * the crop silently multiplied the reading: a true `71` plus one stray digit reads as `71X` - 710-719,
 * every one of which clears a 350 high-water bar. That is the shape of the false HIGH_WATER session
 * recorded on 2026-07-16 (skill screen said 71, the trigger wanted >= 350).
 *
 * The constraint here is evidence, not a clamp: the Skill Point field holds exactly ONE number, so a
 * read offering two of them is ambiguous about which is the value and is refused. No maximum is
 * imposed - a genuine 4-digit total stays valid, because bounding the range would be guesswork.
 *
 * Refusing costs nothing: -1 means keep-last-known-good and the next turn's read self-heals, which is
 * the behaviour the existing code already relies on.
 */
fun parseSkillPointsText(raw: String): Int {
    val runs: List<String> = Regex("\\d+").findAll(raw).map { it.value }.toList()
    if (runs.size != 1) return SKILL_POINTS_UNREADABLE
    // Guard the parse itself: a run longer than Int can hold would otherwise throw.
    return runs[0].toIntOrNull() ?: SKILL_POINTS_UNREADABLE
}

/** Verdict on a candidate high-water crossing, re-read from a fresh capture. */
enum class SkillPointConfirmation {
    /** The fresh read agrees the threshold is crossed. Dispatch. */
    CONFIRMED,

    /** The fresh read is valid and below the bar: the candidate was contaminated. Do not dispatch. */
    REJECTED,

    /** The fresh read failed. Nothing is proven either way, so do not dispatch. */
    UNREADABLE,
}

/**
 * Confirms a candidate high-water crossing against an independent re-read.
 *
 * Only ever consulted when the per-turn value already appears to cross the bar, so a normal turn pays
 * nothing. A legitimate crossing re-reads as still-crossing and dispatches unchanged; a one-off
 * contaminated candidate re-reads below the bar and is refused.
 *
 * [SkillPointConfirmation.REJECTED] must not mark the threshold handled - the trainee genuinely may
 * cross it later in the same career, and consuming the flag here would silently forfeit that purchase.
 *
 * @param fresh An independent re-read, or [SKILL_POINTS_UNREADABLE].
 * @param threshold The configured high-water bar.
 */
fun confirmHighWater(fresh: Int, threshold: Int): SkillPointConfirmation =
    when {
        fresh == SKILL_POINTS_UNREADABLE || fresh < 0 -> SkillPointConfirmation.UNREADABLE
        fresh >= threshold -> SkillPointConfirmation.CONFIRMED
        else -> SkillPointConfirmation.REJECTED
    }

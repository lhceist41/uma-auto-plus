package com.steve1316.uma_android_automation.bot

/** -1 means no trustworthy reading: callers keep the last known good value. */
const val SKILL_POINTS_UNREADABLE: Int = -1

/**
 * The Skill Point field holds exactly ONE number, so a crop offering two is ambiguous and refused (a stray digit
 * after `71` used to read as 710-719 and falsely cleared the high-water bar). No maximum is imposed.
 */
fun parseSkillPointsText(raw: String): Int {
    val runs: List<String> = Regex("\\d+").findAll(raw).map { it.value }.toList()
    if (runs.size != 1) return SKILL_POINTS_UNREADABLE
    return runs[0].toIntOrNull() ?: SKILL_POINTS_UNREADABLE
}

enum class SkillPointConfirmation {
    CONFIRMED,

    REJECTED,

    UNREADABLE,
}

/**
 * Re-read check for a candidate high-water crossing. [SkillPointConfirmation.REJECTED] must not mark the threshold
 * handled: the trainee may cross it later in the career and consuming the flag would forfeit that purchase.
 */
fun confirmHighWater(fresh: Int, threshold: Int): SkillPointConfirmation =
    when {
        fresh == SKILL_POINTS_UNREADABLE || fresh < 0 -> SkillPointConfirmation.UNREADABLE
        fresh >= threshold -> SkillPointConfirmation.CONFIRMED
        else -> SkillPointConfirmation.REJECTED
    }

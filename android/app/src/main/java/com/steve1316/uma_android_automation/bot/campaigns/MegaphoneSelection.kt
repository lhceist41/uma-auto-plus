package com.steve1316.uma_android_automation.bot.campaigns

/** Pure megaphone-tier selection, free of Android dependencies so it can be unit-tested directly. */
object MegaphoneSelection {
    /** Megaphone tiers in best-to-worst order, paired with the turn duration each grants when used. */
    val TIERS =
        listOf(
            "Empowering Megaphone" to 2,
            "Motivating Megaphone" to 3,
            "Coaching Megaphone" to 4,
        )

    /** Best megaphone in inventory whose per-tier minimum main-stat-gain threshold is met; a tier blocked by its threshold falls through to the next cheaper one. Null when none qualifies. */
    fun bestEligibleMegaphone(
        mainGain: Int,
        inventory: Map<String, Int>,
        thresholds: Map<String, Int>,
    ): String? =
        TIERS.firstOrNull { (name, _) -> (inventory[name] ?: 0) > 0 && mainGain >= (thresholds[name] ?: 0) }?.first

    fun durationFor(name: String): Int = TIERS.firstOrNull { it.first == name }?.second ?: 0
}

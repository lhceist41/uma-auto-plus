package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.SettingsHelper

/*
 * V1 of the account-adaptive Skill Point policy: resolves the high-water threshold that
 * decideSkillCheck receives, and nothing else. Manual mode passes the user's configured
 * `skills.skillPointCheck` through untouched, so the default behavior is bit-for-bit identical
 * to the pre-adaptive bot. Adaptive mode maps an account-strength tier to a threshold from a
 * fixed table - no learning, no Team Rank reads, no optimizer changes.
 *
 * The tier labels are deliberately about the account's practical strength (support quality,
 * roster depth), not literal Team Rank: rank is a lifetime-accumulation number and a poor proxy
 * for what the current deck can fund. AUTO exists so a user who does not want to self-assess
 * gets a conservative middle value; in V1 it is a fixed alias for DEVELOPING.
 */

/** How the high-water Skill Point threshold is chosen. */
internal enum class SkillSpendMode {
    /** Use `skills.skillPointCheck` exactly as configured - the pre-adaptive behavior. */
    MANUAL,

    /** Derive the threshold from the configured [AccountTier]. */
    ADAPTIVE,

    ;

    companion object {
        /** Parses the persisted setting. Anything unrecognized falls back to [MANUAL] - the safe
         * default is always the long-standing behavior, never a policy the user did not pick. */
        fun fromPersisted(value: String): SkillSpendMode = if (value.trim().equals("adaptive", ignoreCase = true)) ADAPTIVE else MANUAL
    }
}

/** User-declared account strength. Labels describe roster/support quality, not literal Team Rank. */
internal enum class AccountTier {
    /** No self-assessment: resolves to [DEVELOPING] in V1 (conservative middle). */
    AUTO,

    /** Early account, thin supports: spend early so mid-career races are not run skill-less. */
    NEW,

    /** Growing roster: the long-standing default threshold. */
    DEVELOPING,

    /** Reliable roster: can hold points longer for more efficient buys. */
    ESTABLISHED,

    /** Strong roster: hold for big knapsack-efficient purchases (the proven 1000 arm). */
    ENDGAME,

    ;

    companion object {
        /** Parses the persisted setting; unrecognized values fall back to [AUTO]. */
        fun fromPersisted(value: String): AccountTier = entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: AUTO
    }
}

/**
 * The threshold decision for one career: the value [decideSkillCheck] will receive, how it was
 * chosen, and a stable human-readable reason. The reason explains threshold RESOLUTION only -
 * the telemetry `trigger` field keeps recording what actually caused a spend (HIGH_WATER,
 * SCENARIO_FINALS, CAREER_COMPLETE, MANUAL) and must not be blurred with this.
 */
internal data class ResolvedSkillThreshold(
    val value: Int,
    val mode: SkillSpendMode,
    val resolvedTier: AccountTier,
    val reason: String,
) {
    /** Corpus token for the `tier` telemetry field: `manual` in manual mode (no tier governs),
     * else the RESOLVED tier - AUTO records as `developing`, with the auto provenance kept in
     * [reason]. */
    fun tierToken(): String = if (mode == SkillSpendMode.MANUAL) "manual" else resolvedTier.name.lowercase()
}

/** The V1 tier table. DEVELOPING matches the long-standing 350 default arm and ENDGAME matches
 * the maintainer's proven 1000 arm; NEW sits below the default so weak accounts still trigger
 * mid-career, ESTABLISHED between the two. AUTO is resolved before this is consulted. */
internal fun adaptiveThresholdFor(tier: AccountTier): Int =
    when (tier) {
        AccountTier.NEW -> 300
        AccountTier.AUTO, AccountTier.DEVELOPING -> 350
        AccountTier.ESTABLISHED -> 600
        AccountTier.ENDGAME -> 1000
    }

/**
 * Resolves the effective high-water threshold. Pure - all inputs are parameters, so the table
 * and the manual passthrough are pinned by JUnit without a live Campaign.
 *
 * @param mode The persisted `skills.skillSpendMode`.
 * @param configuredTier The persisted `skills.accountTier` (may be [AccountTier.AUTO]).
 * @param manualThreshold The persisted `skills.skillPointCheck`, passed through untouched in
 *   manual mode - no clamping, so manual behavior stays bit-for-bit identical.
 */
internal fun resolveSkillThreshold(
    mode: SkillSpendMode,
    configuredTier: AccountTier,
    manualThreshold: Int,
): ResolvedSkillThreshold {
    if (mode == SkillSpendMode.MANUAL) {
        return ResolvedSkillThreshold(
            value = manualThreshold,
            mode = mode,
            resolvedTier = configuredTier,
            reason = "manual threshold $manualThreshold",
        )
    }
    val resolvedTier = if (configuredTier == AccountTier.AUTO) AccountTier.DEVELOPING else configuredTier
    val value = adaptiveThresholdFor(resolvedTier)
    val reason =
        if (configuredTier == AccountTier.AUTO) {
            "adaptive threshold $value (auto -> ${resolvedTier.name.lowercase()})"
        } else {
            "adaptive threshold $value (${resolvedTier.name.lowercase()})"
        }
    return ResolvedSkillThreshold(value = value, mode = mode, resolvedTier = resolvedTier, reason = reason)
}

/**
 * Settings-reading shim over [resolveSkillThreshold]: reads the two mode/tier settings (with
 * their safe fallbacks) and the manual threshold with the exact same call Campaign has always
 * used, so manual mode cannot drift from the historical read path.
 */
internal fun resolveSkillThresholdFromSettings(): ResolvedSkillThreshold {
    val mode = SkillSpendMode.fromPersisted(SettingsHelper.getStringSetting("skills", "skillSpendMode", "manual"))
    val tier = AccountTier.fromPersisted(SettingsHelper.getStringSetting("skills", "accountTier", "auto"))
    val manualThreshold = SettingsHelper.getIntSetting("skills", "skillPointCheck")
    return resolveSkillThreshold(mode, tier, manualThreshold)
}

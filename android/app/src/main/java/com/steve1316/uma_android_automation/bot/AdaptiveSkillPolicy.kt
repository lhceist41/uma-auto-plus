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

/*
 * Phase 2A: profile-objective gating and the two adaptive-only dynamic triggers.
 *
 * The objective is PRESET-owned (stamped on every preset apply, defaulting to RANK) while mode
 * and tier stay user-global. Both triggers are gated so that a RANK objective - every preset
 * that has not opted in - reproduces V1 adaptive behavior exactly.
 */

/** What the applied preset's career is trying to achieve. Gates the Phase 2A triggers only;
 * Manual mode and the planner strategies ignore it entirely in 2A. */
internal enum class SkillSpendObjective {
    /** Reliability first: spend before critical races, lock planned skills when affordable. */
    SAFE_COMPLETION,

    /** Evaluation efficiency - the default, and the V1-identical behavior (both triggers inert). */
    RANK,

    /** Inheritance farming. 2A enables only the planned-skill trigger; the planned-only purity
     * planner behavior is Phase 2B. */
    SPARKS,

    /** A must-win race is the career's point (e.g. the Kashiwa sash). Both triggers enabled. */
    RACE_REWARD,

    ;

    /** CRITICAL_RACE gate: reliability-driven objectives only. */
    fun allowsCriticalRace(): Boolean = this == SAFE_COMPLETION || this == RACE_REWARD

    /** PLANNED_SKILL_AFFORDABLE gate: everything except pure rank farming. */
    fun allowsPlannedSkillAffordable(): Boolean = this != RANK

    /** Corpus token, e.g. `race_reward`. */
    fun token(): String = name.lowercase()

    companion object {
        /** Parses the persisted preset value; blank/unknown falls back to [RANK] - the safe
         * default is always the behavior every existing preset already has. */
        fun fromPersisted(value: String?): SkillSpendObjective =
            when (value?.trim()?.lowercase()) {
                "safe_completion" -> SAFE_COMPLETION
                "sparks" -> SPARKS
                "race_reward" -> RACE_REWARD
                else -> RANK
            }
    }
}

/** Classification of the Main screen's current-goal text. */
internal enum class GoalKind {
    /** The goal is a race objective whose name matched the races table. */
    RACE,

    /** A fan-count goal (the fan-emergency machinery owns these). */
    FANS,

    /** A Trackblazer Result-Pts goal (its own emergency owns these). */
    RESULT_PTS,

    /** Readable text that is none of the above - inert for the critical-race trigger. */
    OTHER,

    /** Countdown or text unavailable this turn - inert. */
    UNKNOWN,
}

/**
 * One turn's mandatory-goal reading, produced by Campaign at most once per [turn] inside the
 * global checks. Valid ONLY while `turn == date.day`: a previous turn's race snapshot must never
 * drive a spend, so consumers re-check the key instead of trusting whatever is stored.
 */
internal data class GoalDeadlineSnapshot(
    val turn: Int,
    val turnsRemaining: Int?,
    val text: String?,
    val kind: GoalKind,
    val raceName: String?,
)

/** The critical-race window: spend when the race is this many turns away (race day itself is 0
 * and never fires - the spend must land before the race). */
internal const val CRITICAL_RACE_MIN_TURNS = 1
internal const val CRITICAL_RACE_MAX_TURNS = 2

/** Minimum SP for a critical-race spend to be worth opening the screen: real white skills start
 * around 100-180 base (Deep Breaths 160, Corner Recovery o 170), so 150 buys something useful
 * while never opening on shrapnel. */
internal const val MIN_CRITICAL_SPEND = 150

/** SP growth required after an AFFORDABLE-triggered session before another may fire - the belt
 * that bounds repeated opens even when evidence stays qualifying. Roughly one training turn's
 * income plus slack. */
internal const val AFFORDABLE_REARM_SP_GROWTH = 120

/** Normalizes goal text / race names for matching: lowercase, apostrophe variants dropped,
 * punctuation to spaces, whitespace (including OCR line breaks) collapsed. */
internal fun normalizeGoalText(raw: String): String =
    raw.lowercase()
        .replace(Regex("['’‘`]"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/**
 * Finds the race a goal text refers to: a normalized known race name must appear as a whole
 * substring of the normalized text, longest known name winning when several overlap. No
 * edit-distance fuzzing - garbled OCR fails to null, which keeps the trigger inert rather than
 * guessing the wrong race.
 */
internal fun matchGoalRace(text: String, raceNames: Collection<String>): String? {
    val normalizedText = normalizeGoalText(text)
    if (normalizedText.isEmpty()) return null
    return raceNames
        .asSequence()
        .filter { it.isNotBlank() }
        .map { it to normalizeGoalText(it) }
        .filter { (_, normalized) -> normalized.isNotEmpty() && normalizedText.contains(normalized) }
        .maxByOrNull { (_, normalized) -> normalized.length }
        ?.first
}

/**
 * Classifies one goal text. The fan and Result-Pt arms reuse the production wording rules from
 * Racing's emergencies ("fans" plural on purpose; "Result Pt" with the achieved/MAX stand-down)
 * so the two classifiers can never disagree about whose emergency a goal belongs to.
 */
internal fun classifyGoalText(text: String?, raceNames: Collection<String>): Pair<GoalKind, String?> {
    if (text.isNullOrBlank()) return GoalKind.UNKNOWN to null
    if (text.contains("fans", ignoreCase = true)) return GoalKind.FANS to null
    // Achieved/MAX stand-down BEFORE the Result-Pt arm, mirroring Racing's own emergency rule:
    // "Result Pt goal Achieved" is a met goal, not an active Result-Pts objective.
    if (text.contains("Achieved", ignoreCase = true) || text.contains("MAX", ignoreCase = false)) return GoalKind.OTHER to null
    if (text.contains("Result Pt", ignoreCase = true)) return GoalKind.RESULT_PTS to null
    val race = matchGoalRace(text, raceNames)
    return if (race != null) GoalKind.RACE to race else GoalKind.OTHER to null
}

/** Trigger-specific rationale for the skill-spend record being written, set by Campaign around
 * the session and consumed by SkillPlan's telemetry. Null fields simply stay off the record. */
internal data class SkillTriggerContext(
    val trigger: SkillCheckTrigger,
    val criticalRace: String? = null,
    val criticalRaceSource: String? = null,
    val turnsUntilRace: Int? = null,
    val plannedSkill: String? = null,
    val plannedSkillObservedPrice: Int? = null,
)

/**
 * Observed-availability evidence behind PLANNED_SKILL_AFFORDABLE. Only skills SEEN available on
 * a real parsed skill screen this career can qualify, priced at their OBSERVED screen price -
 * prices only fall as hint levels rise, so `SP >= observedPrice` stays a sufficient condition on
 * the current price. No speculative opens, no permanent absent-marking: a skill that unlocks
 * later becomes eligible at the next organic parse, and a Potential-gated skill that never
 * appears simply never qualifies (zero wasted opens - the Copano lesson).
 *
 * Pure Kotlin so JUnit pins the whole lifecycle; Campaign holds one instance per career.
 */
internal class PlannedSkillEvidenceStore {
    private data class Observed(val price: Int, val parseTurn: Int)

    private val observed = LinkedHashMap<String, Observed>()
    private val purchased = mutableSetOf<String>()
    private val suppressed = mutableSetOf<String>()
    private var lastAffordableTriggerSp: Int? = null

    /**
     * Replaces the observation set from one successful skill-screen parse. Failed parses must
     * simply not call this - prior evidence stays. An AFFORDABLE-triggered session that bought
     * nothing suppresses the skills it saw until a non-AFFORDABLE parse refreshes them (the
     * no-buy guard); any session that did buy, or any organic parse, clears the suppression.
     */
    fun recordParse(availableWithPrices: Map<String, Int>, parseTurn: Int, fromAffordableSession: Boolean, confirmedPurchases: Collection<String>) {
        purchased.addAll(confirmedPurchases)
        observed.clear()
        for ((name, price) in availableWithPrices) observed[name] = Observed(price, parseTurn)
        if (fromAffordableSession && confirmedPurchases.isEmpty()) {
            suppressed.addAll(observed.keys)
        } else {
            suppressed.clear()
        }
    }

    /** Marks an AFFORDABLE firing so the SP-growth belt arms against the SP it fired at. */
    fun markAffordableFired(skillPoints: Int) {
        lastAffordableTriggerSp = skillPoints
    }

    /**
     * The qualifying planned skill, or null. Deterministic representative: highest observed
     * price, plan order breaking ties. Belt: once an AFFORDABLE session fired, the next needs
     * [AFFORDABLE_REARM_SP_GROWTH] more SP than the last firing regardless of its outcome.
     */
    fun affordableCandidate(planNames: Collection<String>, skillPoints: Int): Pair<String, Int>? {
        val last = lastAffordableTriggerSp
        if (last != null && skillPoints < last + AFFORDABLE_REARM_SP_GROWTH) return null
        return planNames
            .asSequence()
            .filter { it !in purchased && it !in suppressed }
            .mapNotNull { name -> observed[name]?.let { name to it.price } }
            .filter { (_, price) -> price <= skillPoints }
            .maxByOrNull { (_, price) -> price }
    }

    /** True once any parse has been recorded - before first contact the trigger is inert. */
    fun hasAnyObservation(): Boolean = observed.isNotEmpty()
}

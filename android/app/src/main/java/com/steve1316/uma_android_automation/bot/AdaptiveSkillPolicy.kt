package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.types.TrackDistance

/*
 * Resolves the high-water Skill Point threshold given to decideSkillCheck: Manual passes `skills.skillPointCheck` through untouched, Adaptive maps
 * an account-strength tier to a fixed threshold. Tiers describe practical strength (support quality, roster depth), not Team Rank, a poor proxy for what the deck can fund.
 */

/** How the high-water Skill Point threshold is chosen. */
internal enum class SkillSpendMode {
    MANUAL,

    ADAPTIVE,

    ;

    companion object {
        /** Unrecognized values fall back to [MANUAL], never a policy the user did not pick. */
        fun fromPersisted(value: String): SkillSpendMode = if (value.trim().equals("adaptive", ignoreCase = true)) ADAPTIVE else MANUAL
    }
}

internal enum class AccountTier {
    /** Resolves to [DEVELOPING] (conservative middle). */
    AUTO,

    NEW,

    DEVELOPING,

    ESTABLISHED,

    ENDGAME,

    ;

    companion object {
        fun fromPersisted(value: String): AccountTier = entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: AUTO
    }
}

/** Why the threshold was chosen. Explains resolution only; the telemetry `trigger` field still records what actually caused a spend. */
internal data class ResolvedSkillThreshold(
    val value: Int,
    val mode: SkillSpendMode,
    val resolvedTier: AccountTier,
    val reason: String,
) {
    /** Corpus token for `tier`: `manual` in manual mode, else the resolved tier (AUTO records as `developing`, provenance kept in [reason]). */
    fun tierToken(): String = if (mode == SkillSpendMode.MANUAL) "manual" else resolvedTier.name.lowercase()
}

/** DEVELOPING matches the long-standing 350 default arm and ENDGAME the maintainer's proven 1000 arm; AUTO is resolved before this is consulted. */
internal fun adaptiveThresholdFor(tier: AccountTier): Int =
    when (tier) {
        AccountTier.NEW -> 300
        AccountTier.AUTO, AccountTier.DEVELOPING -> 350
        AccountTier.ESTABLISHED -> 600
        AccountTier.ENDGAME -> 1000
    }

/** Manual mode passes the configured threshold through unclamped, so its behavior stays bit-for-bit unchanged. */
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

/** Reads the mode/tier settings and the manual threshold exactly as Campaign always has, so manual mode cannot drift from the historical read path. */
internal fun resolveSkillThresholdFromSettings(): ResolvedSkillThreshold {
    val mode = SkillSpendMode.fromPersisted(SettingsHelper.getStringSetting("skills", "skillSpendMode", "manual"))
    val tier = AccountTier.fromPersisted(SettingsHelper.getStringSetting("skills", "accountTier", "auto"))
    val manualThreshold = SettingsHelper.getIntSetting("skills", "skillPointCheck")
    return resolveSkillThreshold(mode, tier, manualThreshold)
}

/*
 * The objective is preset-owned (stamped on every preset apply, default RANK) while mode and tier stay user-global;
 * RANK reproduces the base adaptive behavior exactly.
 */

/** What the applied preset's career is trying to achieve. Gates the dynamic triggers and, in Adaptive mode only, the planner's strategy tail. */
internal enum class SkillSpendObjective {
    SAFE_COMPLETION,

    RANK,

    /** Inheritance farming: planned-only purchasing, so leftover SP is accepted instead of drained into spark-diluting filler. */
    SPARKS,

    RACE_REWARD,

    ;

    fun allowsCriticalRace(): Boolean = this == SAFE_COMPLETION || this == RACE_REWARD

    fun allowsPlannedSkillAffordable(): Boolean = this != RANK

    /** A sparks career buys only planned skills (plus the inherited/negative toggles); consulted only in Adaptive mode, see [strategyTailAllowed]. */
    fun allowsStrategyTail(): Boolean = this != SPARKS

    fun token(): String = name.lowercase()

    companion object {
        fun fromPersisted(value: String?): SkillSpendObjective =
            when (value?.trim()?.lowercase()) {
                "safe_completion" -> SAFE_COMPLETION
                "sparks" -> SPARKS
                "race_reward" -> RACE_REWARD
                else -> RANK
            }
    }
}

/** Manual mode always allows the strategy tail; Adaptive delegates to the objective. */
internal fun strategyTailAllowed(mode: SkillSpendMode, objective: SkillSpendObjective): Boolean =
    mode != SkillSpendMode.ADAPTIVE || objective.allowsStrategyTail()

/**
 * Whether a planned-only session may extend into the career-end knapsack fallback. Unspent points are discarded by the game at Finish,
 * so refusing to spend then protects nothing. Only Adaptive + sparks + CAREER_COMPLETE qualifies.
 */
internal fun careerEndConstrainedFallbackAllowed(
    mode: SkillSpendMode,
    objective: SkillSpendObjective,
    trigger: SkillCheckTrigger?,
): Boolean =
    mode == SkillSpendMode.ADAPTIVE &&
        objective == SkillSpendObjective.SPARKS &&
        trigger == SkillCheckTrigger.CAREER_COMPLETE

internal enum class CareerEndTailFilter {
    /** The preset's running style, distance and surface. */
    PROFILE,

    /** Any skill the trainee's running style can activate, at any distance or surface. */
    RUNNING_STYLE,
    ANY_SKILL,
}

/**
 * Only the rank objective's CAREER_COMPLETE session widens the profile filter: after the last race a skill no longer helps this career.
 * It keeps skills the running style can activate, since an off-distance or off-surface skill still fires when the veteran races there
 * (Team Trials lets the player pick); [anySkill] drops the style check too. An unknown running style keeps the profile filter.
 */
internal fun careerEndTailFilter(objective: SkillSpendObjective, trigger: SkillCheckTrigger?, anySkill: Boolean, runningStyleKnown: Boolean): CareerEndTailFilter =
    when {
        objective != SkillSpendObjective.RANK || trigger != SkillCheckTrigger.CAREER_COMPLETE -> CareerEndTailFilter.PROFILE
        anySkill -> CareerEndTailFilter.ANY_SKILL
        runningStyleKnown -> CareerEndTailFilter.RUNNING_STYLE
        else -> CareerEndTailFilter.PROFILE
    }

/** Icon-family classifier: 20021 is white recovery (including inherited-unique recoveries, which the candidate predicate excludes), 20022 gold upgrades.
 * 20024 debuffs and every other icon are NONE (debuffs belong to the negative-skill machinery, iconId % 10 == 4). */
internal enum class RecoveryClass {
    NONE,
    WHITE,
    GOLD,
}

internal fun recoveryClassOf(iconId: Int): RecoveryClass =
    when (iconId) {
        20021 -> RecoveryClass.WHITE
        20022 -> RecoveryClass.GOLD
        else -> RecoveryClass.NONE
    }

/** Axis-free recovery skills safe as injection candidates (corner and straightaway pairs). Everything else is excluded, which keeps
 * condition-trap skills like Triple 7s (fires only at 776-778m remaining) and Shake It Out out. */
internal val GENERAL_RECOVERY_IDS: Set<Int> =
    setOf(
        200352, // Corner Recovery ○
        200351, // Swinging Maestro
        200382, // Straightaway Recovery
        200381, // Breath of Fresh Air
    )

/** Whether this career may inject a recovery purchase: Adaptive only; Long under safe_completion or race_reward, Medium only under safe_completion; an unresolved distance fails inertly. */
internal fun allowsRecoveryInjection(
    mode: SkillSpendMode,
    objective: SkillSpendObjective,
    preferredDistance: TrackDistance?,
): Boolean {
    if (mode != SkillSpendMode.ADAPTIVE) return false
    return when (preferredDistance) {
        TrackDistance.LONG -> objective == SkillSpendObjective.SAFE_COMPLETION || objective == SkillSpendObjective.RACE_REWARD
        TrackDistance.MEDIUM -> objective == SkillSpendObjective.SAFE_COMPLETION
        else -> false
    }
}

internal enum class GoalKind {
    /** The goal is a race objective whose name matched the races table. */
    RACE,

    /** A fan-count goal (the fan-emergency machinery owns these). */
    FANS,

    /** A Trackblazer Result-Pts goal (its own emergency owns these). */
    RESULT_PTS,

    OTHER,

    UNKNOWN,
}

/** One turn's mandatory-goal reading, valid only while `turn == date.day`: a previous turn's race snapshot must never drive a spend. */
internal data class GoalDeadlineSnapshot(
    val turn: Int,
    val turnsRemaining: Int?,
    val text: String?,
    val kind: GoalKind,
    val raceName: String?,
)

/** Spend when the race is this many turns away; race day (0) never fires since the spend must land before the race. */
internal const val CRITICAL_RACE_MIN_TURNS = 1
internal const val CRITICAL_RACE_MAX_TURNS = 2

/** Minimum SP worth opening the screen for a critical-race spend: real white skills start around 100-180 base (Deep Breaths 160, Corner Recovery o 170). */
internal const val MIN_CRITICAL_SPEND = 150

/** SP growth required after an AFFORDABLE-triggered session before another may fire; bounds repeated opens. */
internal const val AFFORDABLE_REARM_SP_GROWTH = 120

internal fun normalizeGoalText(raw: String): String =
    raw.lowercase()
        .replace(Regex("['’‘`]"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/** A normalized known race name must appear as a whole substring, longest wins. No edit-distance fuzzing: garbled OCR fails to null, keeping the trigger inert rather than guessing the wrong race. */
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

/** The fan and Result-Pt arms reuse Racing's emergency wording rules ("fans" plural on purpose; "Result Pt" with the achieved/MAX stand-down) so the two classifiers never disagree. */
internal fun classifyGoalText(text: String?, raceNames: Collection<String>): Pair<GoalKind, String?> {
    if (text.isNullOrBlank()) return GoalKind.UNKNOWN to null
    if (text.contains("fans", ignoreCase = true)) return GoalKind.FANS to null
    // Achieved/MAX stand-down comes before the Result-Pt arm, as in Racing's emergency rule.
    if (text.contains("Achieved", ignoreCase = true) || text.contains("MAX", ignoreCase = false)) return GoalKind.OTHER to null
    if (text.contains("Result Pt", ignoreCase = true)) return GoalKind.RESULT_PTS to null
    val race = matchGoalRace(text, raceNames)
    return if (race != null) GoalKind.RACE to race else GoalKind.OTHER to null
}

/** Trigger-specific rationale for the skill-spend record, set by Campaign around the session; null fields stay off the record. */
internal data class SkillTriggerContext(
    val trigger: SkillCheckTrigger,
    val criticalRace: String? = null,
    val criticalRaceSource: String? = null,
    val turnsUntilRace: Int? = null,
    val plannedSkill: String? = null,
    val plannedSkillObservedPrice: Int? = null,
)

/**
 * Observed-availability evidence behind PLANNED_SKILL_AFFORDABLE: only skills seen available on a parsed skill screen this career qualify, at their
 * observed price (prices only fall as hint levels rise, so `SP >= observedPrice` stays sufficient). A Potential-gated skill that never appears never qualifies.
 */
internal class PlannedSkillEvidenceStore {
    private data class Observed(val price: Int, val parseTurn: Int)

    private val observed = LinkedHashMap<String, Observed>()
    private val purchased = mutableSetOf<String>()
    private val suppressed = mutableSetOf<String>()
    private var lastAffordableTriggerSp: Int? = null

    /** Replaces the observations from one successful parse; failed parses must not call this. An AFFORDABLE session that bought nothing suppresses the skills it saw until a non-AFFORDABLE parse; a buying session or organic parse clears it. */
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

    fun markAffordableFired(skillPoints: Int) {
        lastAffordableTriggerSp = skillPoints
    }

    /** Deterministic representative: highest observed price, plan order breaking ties. After an AFFORDABLE firing the next needs [AFFORDABLE_REARM_SP_GROWTH] more SP regardless of outcome. */
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

    fun hasAnyObservation(): Boolean = observed.isNotEmpty()
}

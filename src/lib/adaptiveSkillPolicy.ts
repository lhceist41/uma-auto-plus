/**
 * Display-side mirror of the Kotlin adaptive skill-threshold table (bot/AdaptiveSkillPolicy.kt).
 * The bot resolves the acting value on-device; this exists only so the settings UI can show the
 * user what a tier resolves to. Both sides pin the same table in their test suites, so a change
 * to one without the other fails a test rather than silently drifting.
 */

export type SkillSpendMode = "manual" | "adaptive"
export type AccountTier = "auto" | "new" | "developing" | "established" | "endgame"

/** The shipped defaults. Consumed by defaultSettings in BotStateContext so the tested constants
 * ARE the defaults - Manual preserves the pre-adaptive behavior bit-for-bit, and the tier is
 * inert until the user opts into Adaptive. */
export const DEFAULT_SKILL_SPEND_MODE: SkillSpendMode = "manual"
export const DEFAULT_ACCOUNT_TIER: AccountTier = "auto"

/** Phase 2A: the preset-owned career objective. "rank" is both the default and the
 * V1-identical behavior (the adaptive dynamic triggers stay inert). */
export type SkillSpendObjective = "safe_completion" | "rank" | "sparks" | "race_reward"
export const DEFAULT_SKILL_SPEND_OBJECTIVE: SkillSpendObjective = "rank"
export const SKILL_SPEND_OBJECTIVES: readonly SkillSpendObjective[] = ["safe_completion", "rank", "sparks", "race_reward"]

/**
 * The objective a preset apply must stamp: the preset's own value when it declares one, else the
 * default. Used by BOTH real apply paths (Home preset apply and the rotation snapshot builder) -
 * without the stamp, a preset that never sets the field would silently inherit the previous
 * preset's objective through the category spread.
 */
export function presetObjectiveOf(presetSettings: unknown): SkillSpendObjective {
    const raw = (presetSettings as { skills?: { skillSpendObjective?: unknown } } | null | undefined)?.skills?.skillSpendObjective
    return SKILL_SPEND_OBJECTIVES.includes(raw as SkillSpendObjective) ? (raw as SkillSpendObjective) : DEFAULT_SKILL_SPEND_OBJECTIVE
}

/** Display label for the read-only objective line ("race_reward" -> "Race reward"). */
export function objectiveLabel(objective: string): string {
    const known = SKILL_SPEND_OBJECTIVES.includes(objective as SkillSpendObjective) ? objective : DEFAULT_SKILL_SPEND_OBJECTIVE
    const words = known.replace(/_/g, " ")
    return words.charAt(0).toUpperCase() + words.slice(1)
}

/**
 * 2B-1: whether an objective buys planned skills only in Adaptive mode (the broad strategy tail
 * is skipped and leftover SP is accepted). Mirrors the Kotlin gate
 * (SkillSpendObjective.allowsStrategyTail): true only for "sparks". Unknown values normalize to
 * the default objective first, matching presetObjectiveOf.
 */
export function isPlannedOnlyObjective(objective: string): boolean {
    const known = SKILL_SPEND_OBJECTIVES.includes(objective as SkillSpendObjective) ? (objective as SkillSpendObjective) : DEFAULT_SKILL_SPEND_OBJECTIVE
    return known === "sparks"
}

/**
 * 2B-2: whether recovery-deficit protection can arm for a profile, for the read-only Adaptive
 * info line. Mirrors the Kotlin gate (allowsRecoveryInjection): Long careers under
 * safe_completion or race_reward, Medium only under safe_completion, everything else inert.
 * Unknown objectives normalize to the default first; unknown distances fail inert.
 */
export function recoveryProtectionArms(objective: string, preferredDistance: string): boolean {
    const known = SKILL_SPEND_OBJECTIVES.includes(objective as SkillSpendObjective) ? (objective as SkillSpendObjective) : DEFAULT_SKILL_SPEND_OBJECTIVE
    const distance = preferredDistance.trim().toLowerCase()
    if (distance === "long") return known === "safe_completion" || known === "race_reward"
    if (distance === "medium") return known === "safe_completion"
    return false
}

/** The V1 tier table. Auto is a fixed alias for Developing (conservative middle). */
export const ADAPTIVE_TIER_THRESHOLDS: Record<Exclude<AccountTier, "auto">, number> = {
    new: 300,
    developing: 350,
    established: 600,
    endgame: 1000,
}

/** The tier that actually governs: auto resolves to developing in V1. */
export function resolveAccountTier(tier: AccountTier): Exclude<AccountTier, "auto"> {
    return tier === "auto" ? "developing" : tier
}

/** The threshold a tier resolves to (after auto-aliasing). */
export function adaptiveThresholdFor(tier: AccountTier): number {
    return ADAPTIVE_TIER_THRESHOLDS[resolveAccountTier(tier)]
}

/** Capitalized tier label for display ("developing" -> "Developing"). */
function tierLabel(tier: string): string {
    return tier.charAt(0).toUpperCase() + tier.slice(1)
}

/**
 * The read-only line the settings page shows under the tier picker, e.g.
 * "Adaptive threshold: 600 SP (Established)" or "Adaptive threshold: 350 SP (Auto → Developing)".
 */
export function adaptiveThresholdLabel(tier: AccountTier): string {
    const resolved = resolveAccountTier(tier)
    const value = ADAPTIVE_TIER_THRESHOLDS[resolved]
    const suffix = tier === "auto" ? `Auto → ${tierLabel(resolved)}` : tierLabel(resolved)
    return `Adaptive threshold: ${value} SP (${suffix})`
}

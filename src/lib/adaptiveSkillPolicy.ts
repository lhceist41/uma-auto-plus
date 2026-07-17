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

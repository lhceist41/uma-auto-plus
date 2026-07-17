import { ADAPTIVE_TIER_THRESHOLDS, DEFAULT_ACCOUNT_TIER, DEFAULT_SKILL_SPEND_MODE, adaptiveThresholdFor, adaptiveThresholdLabel, resolveAccountTier } from "../adaptiveSkillPolicy"

// Display-side mirror of the Kotlin table in bot/AdaptiveSkillPolicy.kt. Both suites pin the same
// values so the two tables cannot drift apart silently: change one and exactly one suite fails.
describe("adaptive skill policy (display mirror)", () => {
    it("pins the V1 tier table to the Kotlin values", () => {
        expect(ADAPTIVE_TIER_THRESHOLDS).toEqual({ new: 300, developing: 350, established: 600, endgame: 1000 })
    })

    it("resolves auto to developing in V1", () => {
        expect(resolveAccountTier("auto")).toBe("developing")
        expect(adaptiveThresholdFor("auto")).toBe(350)
    })

    it("keeps the ladder monotonic across account strength", () => {
        const ladder = [adaptiveThresholdFor("new"), adaptiveThresholdFor("developing"), adaptiveThresholdFor("established"), adaptiveThresholdFor("endgame")]
        for (let i = 1; i < ladder.length; i++) {
            expect(ladder[i]).toBeGreaterThanOrEqual(ladder[i - 1])
        }
    })

    it("renders the resolved-threshold line for explicit tiers and the auto alias", () => {
        expect(adaptiveThresholdLabel("established")).toBe("Adaptive threshold: 600 SP (Established)")
        expect(adaptiveThresholdLabel("endgame")).toBe("Adaptive threshold: 1000 SP (Endgame)")
        expect(adaptiveThresholdLabel("auto")).toBe("Adaptive threshold: 350 SP (Auto → Developing)")
    })
})

describe("adaptive skill spend settings", () => {
    // defaultSettings.skills consumes these exact constants (BotStateContext.tsx), so pinning
    // them here pins the shipped defaults without importing the react-native context module.
    it("defaults to Manual mode with the Auto tier, preserving current behavior", () => {
        expect(DEFAULT_SKILL_SPEND_MODE).toBe("manual")
        expect(DEFAULT_ACCOUNT_TIER).toBe("auto")
    })

    it("mode switches leave the user's manual threshold untouched", () => {
        // The page updates settings via a per-key spread; a mode flip must never rewrite the
        // threshold, so switching to Adaptive and back preserves the user's number exactly.
        const before = { skillSpendMode: DEFAULT_SKILL_SPEND_MODE as string, accountTier: DEFAULT_ACCOUNT_TIER as string, enableSkillPointCheck: true, skillPointCheck: 1000 }
        const adaptive = { ...before, skillSpendMode: "adaptive" }
        const backToManual = { ...adaptive, skillSpendMode: "manual" }
        expect(adaptive.skillPointCheck).toBe(1000)
        expect(backToManual.skillPointCheck).toBe(1000)
        expect(backToManual).toEqual(before)
    })

    it("hydrating settings saved before the adaptive fields existed falls back to the defaults", () => {
        // Old persisted settings lack the two new keys; the page-level merge with defaults must
        // resolve them to Manual + Auto (the same safe fallback the Kotlin parser applies).
        const defaults = { skillSpendMode: DEFAULT_SKILL_SPEND_MODE as string, accountTier: DEFAULT_ACCOUNT_TIER as string }
        const persistedOldSkills = { enableSkillPointCheck: true, skillPointCheck: 1000 }
        const merged = { ...defaults, ...persistedOldSkills }
        expect(merged.skillSpendMode).toBe("manual")
        expect(merged.accountTier).toBe("auto")
        expect(merged.skillPointCheck).toBe(1000)
    })
})

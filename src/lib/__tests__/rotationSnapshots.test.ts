import { buildRotationSnapshotRows, baseCharacter, deriveInGameName, deriveExcludeOutfits } from "../rotationSnapshots"
import { characterPresets } from "../../data/characterPresets"

// ===========================================================================
// buildRotationSnapshotRows
// ===========================================================================

describe("buildRotationSnapshotRows", () => {
    // Use a real preset so the (presetKey, scenario) lookup resolves; index 0 keeps the test robust
    // to preset renames/reordering (it asserts on structure, never on a specific preset's content).
    const preset = characterPresets[0]

    // A base that carries both the big static reference DBs (which must be EXCLUDED from snapshots)
    // and ordinary per-trainee keys (which must be RETAINED), plus the control categories.
    const baseSettings = {
        general: { scenario: "Placeholder Scenario", enablePopupCheck: true },
        trainingEvent: {
            characterEventData: { huge: "x".repeat(1000) },
            supportEventData: { huge: "y".repeat(1000) },
            scenarioEventData: { huge: "z".repeat(1000) },
            supportEventOverrides: {},
            scenarioEventOverrides: {},
            ocrConfidence: 80,
        },
        racing: {
            racingPlanData: "B".repeat(1000),
            racingPlan: "[]",
            enableRacingPlan: true,
            enableMandatoryRacingPlan: false,
        },
        runQueue: { enableRunQueue: true, enableTraineeRotation: true },
        queueState: { currentRun: 2 },
        // discord holds the bot token (global config, must NOT be snapshotted per-trainee); misc holds
        // two transient/UI-state keys that must be excluded, plus an ordinary key that must be retained.
        discord: { discordToken: "secret-token-123", enableDiscordNotifications: false },
        misc: { currentProfileName: "MyProfile", formattedSettingsString: "cached dump", enableSettingsDisplay: true },
        // skills carries the user's global skill-spend choice: threshold 750 with the mid-career check
        // turned OFF ("Career end"). Every preset ships skillPointCheck 350 AND enableSkillPointCheck true,
        // so BOTH base values must survive the merge - the enable flag is deliberately the opposite of the
        // preset's so its assertion actually discriminates (regression guard for the clobber bug).
        skills: { skillPointCheck: 750, enableSkillPointCheck: false, preferredRunningStyle: "inherit" },
    } as any

    const { rows, missing } = buildRotationSnapshotRows(baseSettings, [{ inGameName: "Test", presetKey: preset.name, scenario: preset.scenario }])

    it("resolves the preset (no missing entries) and produces rows", () => {
        expect(missing).toHaveLength(0)
        expect(rows.length).toBeGreaterThan(0)
    })

    it("namespaces every row with the rot0_ prefix", () => {
        expect(rows.every((r) => r.category.startsWith("rot0_"))).toBe(true)
    })

    it("excludes the static reference databases (the keys that bloated the DB)", () => {
        for (const key of ["characterEventData", "supportEventData", "scenarioEventData", "racingPlanData"]) {
            expect(rows.find((r) => r.key === key)).toBeUndefined()
        }
    })

    it("retains per-trainee config keys in the snapshot", () => {
        expect(rows.find((r) => r.category === "rot0_trainingEvent" && r.key === "supportEventOverrides")).toBeDefined()
        expect(rows.find((r) => r.category === "rot0_racing" && r.key === "racingPlan")).toBeDefined()
    })

    it("excludes the queue/rotation control categories", () => {
        expect(rows.find((r) => r.category === "rot0_runQueue" || r.category === "rot0_queueState")).toBeUndefined()
    })

    it("excludes the discord category and the transient misc keys (no secret/stale-state leak into snapshots)", () => {
        expect(rows.find((r) => r.category === "rot0_discord")).toBeUndefined()
        expect(rows.find((r) => r.value === "secret-token-123")).toBeUndefined()
        expect(rows.find((r) => r.category === "rot0_misc" && r.key === "currentProfileName")).toBeUndefined()
        expect(rows.find((r) => r.category === "rot0_misc" && r.key === "formattedSettingsString")).toBeUndefined()
    })

    it("retains ordinary (non-denylisted) misc keys", () => {
        expect(rows.find((r) => r.category === "rot0_misc" && r.key === "enableSettingsDisplay")).toBeDefined()
    })

    it("forces the entry's scenario into general.scenario", () => {
        const scenarioRow = rows.find((r) => r.category === "rot0_general" && r.key === "scenario")
        expect(scenarioRow?.value).toBe(preset.scenario)
    })

    it("preserves the user's skill-spend threshold + enable flag over the preset's uniform values", () => {
        // Base = user's choice (threshold 750, mid-career check OFF); every preset ships 350 + check ON.
        // Both base values must win the merge. The threshold assertion (750 vs the preset's 350) and the
        // enable assertion (false vs the preset's true) each fail if their own preservation line is dropped
        // - the class of bug this change prevents (a "Career end" user silently re-enabled at every switch).
        const thresholdRow = rows.find((r) => r.category === "rot0_skills" && r.key === "skillPointCheck")
        expect(thresholdRow?.value).toBe(750)
        const enableRow = rows.find((r) => r.category === "rot0_skills" && r.key === "enableSkillPointCheck")
        expect(enableRow?.value).toBe(false)
    })
})

// ===========================================================================
// preset-name parsing (In-Game Name / exclude-outfit derivation)
// ===========================================================================

describe("preset-name derivation", () => {
    it("derives '[Outfit] Name' for outfit-specific presets", () => {
        expect(deriveInGameName("El Condor Pasa (Kukulkan Warrior)")).toBe("[Kukulkan Warrior] El Condor Pasa")
    })

    it("passes plain character names through unchanged", () => {
        expect(deriveInGameName("Symboli Rudolf")).toBe("Symboli Rudolf")
    })

    it("derives the bare character name for (Legacy Farm) build variants - no such banner exists in-game", () => {
        // Regression: "[Legacy Farm] Air Groove" as the OCR target scanned all 32 roster trainees at
        // 0.437 best score (threshold 0.86) and stopped the queue with "trainee not found".
        expect(deriveInGameName("Air Groove (Legacy Farm)")).toBe("Air Groove")
        expect(deriveInGameName("Daiwa Scarlet (Legacy Farm)")).toBe("Daiwa Scarlet")
        expect(deriveInGameName("El Condor Pasa (Legacy Farm)")).toBe("El Condor Pasa")
    })

    it("strips both outfit and variant suffixes for character-identity comparison", () => {
        expect(baseCharacter("El Condor Pasa (Kukulkan Warrior)")).toBe("El Condor Pasa")
        expect(baseCharacter("Air Groove (Legacy Farm)")).toBe("Air Groove")
        expect(baseCharacter("Air Groove")).toBe("Air Groove")
    })

    it("gives variant presets the same sibling-outfit protection as the plain base name", () => {
        // The bare "El Condor Pasa" target would also match "[Kukulkan Warrior] El Condor Pasa" on the
        // grid, so the legacy entry must exclude the sibling outfit exactly like the plain preset does.
        expect(deriveExcludeOutfits("El Condor Pasa (Legacy Farm)")).toContain("Kukulkan Warrior")
        expect(deriveExcludeOutfits("El Condor Pasa")).toContain("Kukulkan Warrior")
    })

    it("never collects a build variant as an outfit to exclude", () => {
        for (const name of ["El Condor Pasa", "Air Groove", "Daiwa Scarlet"]) {
            expect(deriveExcludeOutfits(name)).not.toContain("Legacy Farm")
        }
    })

    it("gives outfit-specific presets no exclusions (their full-banner target already disambiguates)", () => {
        expect(deriveExcludeOutfits("El Condor Pasa (Kukulkan Warrior)")).toEqual([])
    })
})

import { buildRotationSnapshotRows, baseCharacter, deriveInGameName, deriveExcludeOutfits, presetLine, presetOutfitLabel, presetSubtitle, presetTraineeName } from "../rotationSnapshots"
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

    it("stamps the entry's own trainee identity into the snapshot (single-run verification source)", () => {
        // The snapshot rows become the LIVE settings when the rotation applies them, so each entry
        // must carry ITS trainee - a stale appliedPresetTrainee inherited from the base would make
        // a later single run verify Trainee Select against the wrong trainee on purpose.
        const traineeRow = rows.find((r) => r.category === "rot0_general" && r.key === "appliedPresetTrainee")
        expect(traineeRow?.value).toBe("Test")
        const excludesRow = rows.find((r) => r.category === "rot0_general" && r.key === "appliedPresetTraineeExcludes")
        expect(excludesRow?.value).toBe("")
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

    it("stamps rank for an objective-less preset even when the base carries another objective", () => {
        // The leakage bug this stamp prevents: apply Copano URA (race_reward), then snapshot an
        // old preset - the spread over the base would carry race_reward onto a preset that never
        // asked for it. Index-0 is a pre-2A preset with no objective, and the base deliberately
        // carries the "wrong" one.
        const leakyBase = { ...baseSettings, skills: { ...baseSettings.skills, skillSpendObjective: "race_reward" } }
        const { rows: stampedRows } = buildRotationSnapshotRows(leakyBase, [{ inGameName: "Test", presetKey: preset.name, scenario: preset.scenario }])
        const objectiveRow = stampedRows.find((r) => r.category === "rot0_skills" && r.key === "skillSpendObjective")
        expect(objectiveRow?.value).toBe("rank")
    })

    it("stamps the preset's own objective when it declares one (Copano URA race_reward)", () => {
        const copanoUra = characterPresets.find((p) => p.name === "Copano Rickey" && p.scenario === "URA Finale")
        expect(copanoUra).toBeDefined()
        const { rows: copanoRows, missing: copanoMissing } = buildRotationSnapshotRows(baseSettings, [
            { inGameName: "Copano Rickey", presetKey: copanoUra!.name, scenario: copanoUra!.scenario },
        ])
        expect(copanoMissing).toHaveLength(0)
        const objectiveRow = copanoRows.find((r) => r.category === "rot0_skills" && r.key === "skillSpendObjective")
        expect(objectiveRow?.value).toBe("race_reward")
    })

    it("reports a removed preset as missing and still builds every valid row, leaving the rotation intact", () => {
        // A queue saved before a preset was removed must not throw or lose its other entries.
        const [first, second] = characterPresets
        const rotation = [
            { inGameName: "First", presetKey: first.name, scenario: first.scenario },
            { inGameName: "Daiwa Scarlet", presetKey: "Daiwa Scarlet (Legacy Farm)", scenario: "URA Finale" },
            { inGameName: "Third", presetKey: second.name, scenario: second.scenario },
        ]
        const before = JSON.parse(JSON.stringify(rotation))
        let result: ReturnType<typeof buildRotationSnapshotRows> | undefined
        expect(() => {
            result = buildRotationSnapshotRows(baseSettings, rotation)
        }).not.toThrow()
        expect(result!.missing).toEqual([{ index: 1, presetKey: "Daiwa Scarlet (Legacy Farm)", scenario: "URA Finale" }])
        const prefixes = new Set(result!.rows.map((r) => r.category.split("_")[0]))
        expect(prefixes).toEqual(new Set(["rot0", "rot2"]))
        expect(rotation).toEqual(before)
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

    it("derives the bare character name for removed build-variant names - no such banner exists in-game", () => {
        // Regression: "[Legacy Farm] Air Groove" as the OCR target scanned all 32 roster trainees at
        // 0.437 best score (threshold 0.86) and stopped the queue with "trainee not found". The presets
        // are gone, but saved settings and queues can still carry their names.
        expect(deriveInGameName("Air Groove (Legacy Farm)")).toBe("Air Groove")
        expect(deriveInGameName("Daiwa Scarlet (Legacy Farm)")).toBe("Daiwa Scarlet")
        expect(deriveInGameName("El Condor Pasa (Legacy Farm)")).toBe("El Condor Pasa")
        expect(deriveInGameName("Super Creek (Blue Farm)")).toBe("Super Creek")
    })

    it("strips both outfit and variant suffixes for character-identity comparison", () => {
        expect(baseCharacter("El Condor Pasa (Kukulkan Warrior)")).toBe("El Condor Pasa")
        expect(baseCharacter("Air Groove (Legacy Farm)")).toBe("Air Groove")
        expect(baseCharacter("Super Creek (Blue Farm)")).toBe("Super Creek")
        expect(baseCharacter("Air Groove")).toBe("Air Groove")
    })

    it("gives a removed variant name the same sibling-outfit protection as the plain base name", () => {
        // The bare "El Condor Pasa" target would also match "[Kukulkan Warrior] El Condor Pasa" on the
        // grid, so a stale legacy name must exclude the sibling outfit exactly like the plain preset does.
        expect(deriveExcludeOutfits("El Condor Pasa (Legacy Farm)")).toContain("Kukulkan Warrior")
        expect(deriveExcludeOutfits("El Condor Pasa")).toContain("Kukulkan Warrior")
    })

    it("never collects a build variant as an outfit to exclude", () => {
        for (const name of ["El Condor Pasa", "Air Groove", "Daiwa Scarlet", "Super Creek (Blue Farm)", "El Condor Pasa (Legacy Farm)"]) {
            expect(deriveExcludeOutfits(name)).not.toContain("Legacy Farm")
            expect(deriveExcludeOutfits(name)).not.toContain("Blue Farm")
        }
    })

    it("gives outfit-specific presets no exclusions (their full-banner target already disambiguates)", () => {
        expect(deriveExcludeOutfits("El Condor Pasa (Kukulkan Warrior)")).toEqual([])
    })

    it("excludes every non-base outfit in the outfit data, including ones without a preset", () => {
        expect(deriveExcludeOutfits("Agnes Tachyon")).toContain("Lunatic Lab")
        expect(deriveExcludeOutfits("Agnes Tachyon")).not.toContain("tach-nology")
    })

    it("finds the outfit data under its 'TM Opera O' spelling", () => {
        expect(deriveExcludeOutfits("T.M. Opera O")).toEqual(["New Year, Same Radiance!"])
    })
})

describe("preset display name vs trainee selection identity (traineeName)", () => {
    it("targets the real trainee 'Super Creek', never the phantom '[Blue Farm] Super Creek'", () => {
        // The live failure: deriveInGameName turned the display name into a nonexistent outfit target,
        // scored 0.000 against '[Murmuring Stream] Super Creek', and stopped the queue. A stale saved
        // name must still resolve to the real trainee now that the preset itself is gone.
        expect(deriveInGameName("Super Creek (Blue Farm)")).toBe("Super Creek")
        expect(deriveInGameName("Super Creek (Blue Farm)")).not.toBe("[Blue Farm] Super Creek")
        expect(deriveExcludeOutfits("Super Creek (Blue Farm)")).not.toContain("Blue Farm")
    })

    it("leaves presets without traineeName unchanged (backward compatible)", () => {
        expect(presetTraineeName("Symboli Rudolf")).toBe("Symboli Rudolf")
        expect(presetTraineeName("El Condor Pasa (Kukulkan Warrior)")).toBe("El Condor Pasa (Kukulkan Warrior)")
        expect(deriveInGameName("El Condor Pasa (Kukulkan Warrior)")).toBe("[Kukulkan Warrior] El Condor Pasa")
        expect(deriveInGameName("Super Creek")).toBe("Super Creek")
    })

    it("resolves each removed farm name to its real trainee, not '[Legacy Farm] <name>'", () => {
        for (const [display, trainee] of [
            ["Air Groove (Legacy Farm)", "Air Groove"],
            ["Daiwa Scarlet (Legacy Farm)", "Daiwa Scarlet"],
            ["El Condor Pasa (Legacy Farm)", "El Condor Pasa"],
        ] as const) {
            expect(deriveInGameName(display)).toBe(trainee)
            expect(deriveInGameName(display)).not.toBe(`[Legacy Farm] ${trainee}`)
        }
    })
})

// ===========================================================================
// Grass Wonder outfit disambiguation (Saintly Jade Cleric)
// ===========================================================================

describe("Grass Wonder outfit disambiguation (Saintly Jade Cleric)", () => {
    // Second owned outfit of the same character: the SJC presets are real-outfit presets
    // ("Grass Wonder (Saintly Jade Cleric)"), so they target the full banner, while the base
    // "Grass Wonder" presets keep a bare target and must now exclude the sibling outfit.
    it("targets the full '[Saintly Jade Cleric] Grass Wonder' banner for the SJC presets", () => {
        expect(presetTraineeName("Grass Wonder (Saintly Jade Cleric)")).toBe("Grass Wonder (Saintly Jade Cleric)")
        expect(deriveInGameName("Grass Wonder (Saintly Jade Cleric)")).toBe("[Saintly Jade Cleric] Grass Wonder")
    })

    it("keeps the base presets on the bare 'Grass Wonder' target", () => {
        expect(presetTraineeName("Grass Wonder")).toBe("Grass Wonder")
        expect(deriveInGameName("Grass Wonder")).toBe("Grass Wonder")
    })

    it("arms the base target's exclusion against the SJC banner (base never launches SJC)", () => {
        expect(deriveExcludeOutfits("Grass Wonder")).toContain("Saintly Jade Cleric")
    })

    it("gives the SJC target no exclusions (its outfit-prefixed banner already disambiguates)", () => {
        expect(deriveExcludeOutfits("Grass Wonder (Saintly Jade Cleric)")).toEqual([])
    })

    it("stamps the SJC identity and safe_completion objective into a rotation snapshot", () => {
        const base = {
            general: { scenario: "Placeholder", enablePopupCheck: true, appliedPresetTrainee: "Stale Trainee", appliedPresetTraineeExcludes: "Stale Outfit" },
            skills: { skillPointCheck: 750, enableSkillPointCheck: false, skillSpendObjective: "rank" },
        } as any
        const { rows, missing } = buildRotationSnapshotRows(base, [
            {
                inGameName: deriveInGameName("Grass Wonder (Saintly Jade Cleric)"),
                presetKey: "Grass Wonder (Saintly Jade Cleric)",
                scenario: "Unity Cup",
                excludeOutfits: deriveExcludeOutfits("Grass Wonder (Saintly Jade Cleric)"),
            },
        ])
        expect(missing).toHaveLength(0)
        expect(rows.find((r) => r.category === "rot0_general" && r.key === "appliedPresetTrainee")?.value).toBe("[Saintly Jade Cleric] Grass Wonder")
        expect(rows.find((r) => r.category === "rot0_general" && r.key === "appliedPresetTraineeExcludes")?.value).toBe("")
        expect(rows.find((r) => r.category === "rot0_skills" && r.key === "skillSpendObjective")?.value).toBe("safe_completion")
    })

    it("stamps the base identity, the SJC exclusion, and rank into a base Grass Wonder snapshot", () => {
        // A stale applied-SJC base would otherwise ride along; the base entry must carry its own
        // bare target plus the sibling exclusion, and must stamp rank over a leaky objective.
        const base = {
            general: { scenario: "Placeholder", enablePopupCheck: true },
            skills: { skillPointCheck: 750, enableSkillPointCheck: false, skillSpendObjective: "safe_completion" },
        } as any
        const { rows, missing } = buildRotationSnapshotRows(base, [
            {
                inGameName: deriveInGameName("Grass Wonder"),
                presetKey: "Grass Wonder",
                scenario: "Unity Cup",
                excludeOutfits: deriveExcludeOutfits("Grass Wonder"),
            },
        ])
        expect(missing).toHaveLength(0)
        expect(rows.find((r) => r.category === "rot0_general" && r.key === "appliedPresetTrainee")?.value).toBe("Grass Wonder")
        expect(rows.find((r) => r.category === "rot0_general" && r.key === "appliedPresetTraineeExcludes")?.value).toBe("Saintly Jade Cleric")
        expect(rows.find((r) => r.category === "rot0_skills" && r.key === "skillSpendObjective")?.value).toBe("rank")
    })
})

describe("outfit text shown to the player", () => {
    it("brackets an alternate outfit the way the game titles the card", () => {
        expect(presetOutfitLabel("Biwa Hayahide (Rouge Caroler)")).toBe("[Rouge Caroler]")
    })

    it("brackets a base outfit title that reads like code", () => {
        expect(presetOutfitLabel("Mihono Bourbon")).toBe("[MB-19890425]")
        expect(presetOutfitLabel("Biwa Hayahide")).toBe("[pf. Winning Equation...]")
    })

    it("shows a removed farm name as a build, never as an outfit", () => {
        expect(presetOutfitLabel("Daiwa Scarlet (Legacy Farm)")).toBe("Legacy Farm build")
        expect(presetOutfitLabel("Super Creek (Blue Farm)")).toBe("Blue Farm build")
    })

    it("labels every removed farm name in Home and rotation text as a build, never a bracketed outfit", () => {
        const removed = [
            ["Daiwa Scarlet (Legacy Farm)", "URA Finale", "Daiwa Scarlet"],
            ["El Condor Pasa (Legacy Farm)", "URA Finale", "El Condor Pasa"],
            ["Air Groove (Legacy Farm)", "URA Finale", "Air Groove"],
            ["Super Creek (Blue Farm)", "Unity Cup", "Super Creek"],
        ] as const
        for (const [name, scenario, trainee] of removed) {
            for (const text of [presetLine(name, scenario), presetSubtitle(name, scenario)]) {
                expect(text).toMatch(/ build/)
                expect(text).not.toMatch(/\[(Legacy|Blue) Farm\]/)
                expect(text).not.toContain("(")
            }
            expect(presetLine(name, scenario).startsWith(`${trainee} · `)).toBe(true)
        }
    })

    it("shows nothing for a preset with no known outfit", () => {
        expect(presetOutfitLabel("Nobody At All")).toBe("")
    })

    it("builds the Home and rotation subtitle with dots and no raw preset key", () => {
        expect(presetSubtitle("Biwa Hayahide (Rouge Caroler)", "URA Finale")).toBe("[Rouge Caroler] · URA Finale")
        expect(presetSubtitle("Super Creek (Blue Farm)", "Unity Cup")).toBe("Blue Farm build · Unity Cup")
        expect(presetSubtitle("Nobody At All", "URA Finale")).toBe("URA Finale")
        for (const p of characterPresets) {
            const text = presetSubtitle(p.name, p.scenario)
            expect(text).not.toContain("(")
            expect(text).not.toContain("\u2014")
        }
    })

    it("names a preset in warnings and the rotation row as character, outfit and scenario", () => {
        expect(presetLine("Biwa Hayahide (Rouge Caroler)", "URA Finale")).toBe("Biwa Hayahide · [Rouge Caroler] · URA Finale")
        expect(presetLine("Super Creek (Blue Farm)", "Unity Cup")).toBe("Super Creek · Blue Farm build · Unity Cup")
        expect(presetLine("Nobody At All", "URA Finale")).toBe("Nobody At All · URA Finale")
        for (const p of characterPresets) expect(presetLine(p.name, p.scenario)).not.toMatch(/[()\u2014]/)
    })
})

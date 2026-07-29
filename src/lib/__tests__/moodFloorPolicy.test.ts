import fs from "fs"
import path from "path"
import { MOOD_FLOORS, DEFAULT_MOOD_FLOOR, presetMoodFloorOf, moodFloorLabel, MoodFloor } from "../moodFloorPolicy"
import { buildRotationSnapshotRows, RotationBatchRow } from "../rotationSnapshots"
import { characterPresets, CharacterPreset } from "../../data/characterPresets"

/**
 * The mood floor is preset-owned state, like the skill-spend objective. These tests pin two
 * things: that the option table matches what Kotlin actually accepts, and that applying a preset
 * resolves the value from the preset alone, never from whatever ran before it and never from the
 * support deck.
 *
 * React-Native-free, following the sibling lib suites: no BotStateContext import (it pulls in RN,
 * which Jest cannot parse here), so wiring into defaultSettings and searchConfig is asserted
 * against those files' source text instead.
 */

const srcFile = (...parts: string[]) => fs.readFileSync(path.join(__dirname, "..", "..", ...parts), "utf8")

/** Mirror of the Kotlin parser in Campaign.kt, so a drift on either side fails here. */
function kotlinResolve(raw: string): "NORMAL" | "GOOD" | "GREAT" {
    switch (raw.toLowerCase()) {
        case "normal":
            return "NORMAL"
        case "great":
            return "GREAT"
        default:
            return "GOOD"
    }
}

/** Preset-shaped fixture carrying only a training block. */
const presetWith = (moodFloor?: unknown) => (moodFloor === undefined ? { training: {} } : { training: { moodFloor } })

describe("mood floor option table", () => {
    it("offers exactly the values Kotlin resolves to a distinct Mood", () => {
        expect(MOOD_FLOORS).toEqual(["Normal", "Good", "Great"])
        expect(MOOD_FLOORS.map(kotlinResolve)).toEqual(["NORMAL", "GOOD", "GREAT"])
    })

    it("excludes Mood values Kotlin would silently collapse to Good", () => {
        // Mood.AWFUL and Mood.BAD exist in the enum but the parser has no branch for them, so
        // offering them would be a control that does not do what it says.
        for (const notOffered of ["Awful", "Bad"]) {
            expect(MOOD_FLOORS).not.toContain(notOffered as MoodFloor)
            expect(kotlinResolve(notOffered)).toBe("GOOD")
        }
    })

    it("defaults to Good, and defaultSettings consumes that constant", () => {
        expect(DEFAULT_MOOD_FLOOR).toBe("Good")
        expect(kotlinResolve(DEFAULT_MOOD_FLOOR)).toBe("GOOD")
        expect(srcFile("context", "BotStateContext.tsx")).toContain("moodFloor: DEFAULT_MOOD_FLOOR")
    })

    it("labels every option and names the default", () => {
        for (const floor of MOOD_FLOORS) expect(moodFloorLabel(floor).length).toBeGreaterThan(0)
        expect(moodFloorLabel("Good")).toMatch(/default/i)
    })

    it("exposes a Training settings control offering the whole table", () => {
        const ui = srcFile("pages", "TrainingSettings", "index.tsx")
        expect(ui).toContain('searchId="mood-floor"')
        expect(ui).toContain('updateTrainingSetting("moodFloor", value)')
        // The option list is generated from MOOD_FLOORS, so it cannot drift from the table.
        expect(ui).toContain("MOOD_FLOORS.map")
    })

    it("is registered in settings search under the Training page with the expected terms", () => {
        const config = srcFile("data", "searchConfig.ts")
        const entry = config.slice(config.indexOf('id: "mood-floor"'))
        expect(entry).toContain('id: "mood-floor"')
        expect(entry.slice(0, 500)).toContain('page: "TrainingSettings"')
        const haystack = entry.slice(0, 500).toLowerCase()
        for (const term of ["mood", "condition", "good", "great", "training"]) expect(haystack).toContain(term)
    })
})

describe("preset resolution", () => {
    it("takes the preset's value when it declares a recognized one", () => {
        expect(presetMoodFloorOf(presetWith("Great"))).toBe("Great")
        expect(presetMoodFloorOf(presetWith("Normal"))).toBe("Normal")
        expect(presetMoodFloorOf(presetWith("Good"))).toBe("Good")
    })

    it("falls back to the default when the preset is silent", () => {
        expect(presetMoodFloorOf(presetWith())).toBe(DEFAULT_MOOD_FLOOR)
        expect(presetMoodFloorOf({})).toBe(DEFAULT_MOOD_FLOOR)
        expect(presetMoodFloorOf(null)).toBe(DEFAULT_MOOD_FLOOR)
        expect(presetMoodFloorOf(undefined)).toBe(DEFAULT_MOOD_FLOOR)
    })

    it("normalizes casing and padding the way Kotlin's lowercase match does", () => {
        for (const raw of ["great", "GREAT", "  Great  ", "gReAt"]) expect(presetMoodFloorOf(presetWith(raw))).toBe("Great")
    })

    it("fails safe on invalid values instead of propagating them", () => {
        for (const bad of ["Awful", "Bad", "Excellent", "", "  ", 3, true, null, {}, []]) {
            const resolved = presetMoodFloorOf(presetWith(bad))
            expect(resolved).toBe(DEFAULT_MOOD_FLOOR)
            // Whatever we resolve must survive the Kotlin parser as the same Mood.
            expect(kotlinResolve(resolved)).toBe("GOOD")
        }
    })

    it("is idempotent under repeated application", () => {
        const preset = presetWith("Great")
        const once = presetMoodFloorOf(preset)
        expect(presetMoodFloorOf(preset)).toBe(once)
        expect(presetMoodFloorOf(preset)).toBe(once)
    })

    it("resolves the shipped Agnes Tachyon and Copano Rickey presets to their own values", () => {
        const tachyon = characterPresets.filter((p: CharacterPreset) => p.name === "Agnes Tachyon" && (p.scenario === "URA Finale" || p.scenario === "Unity Cup"))
        expect(tachyon.length).toBeGreaterThan(0)
        for (const p of tachyon) expect(presetMoodFloorOf(p.settings)).toBe("Great")

        const copano = characterPresets.filter((p: CharacterPreset) => p.name === "Copano Rickey")
        expect(copano.length).toBeGreaterThan(0)
        for (const p of copano) expect(presetMoodFloorOf(p.settings)).toBe("Good")
    })

    it("gives every shipped preset a value Kotlin accepts", () => {
        for (const p of characterPresets) expect(MOOD_FLOORS).toContain(presetMoodFloorOf(p.settings))
    })
})

/** Minimal RN-free base settings carrying the categories the snapshot builder touches. */
function makeBase(overrides: { moodFloor?: string; extra?: Record<string, unknown> } = {}): any {
    return {
        general: { scenario: "Placeholder Scenario", appliedPresetTrainee: "", appliedPresetTraineeExcludes: "" },
        training: { moodFloor: overrides.moodFloor ?? DEFAULT_MOOD_FLOOR, maximumFailureChance: 15 },
        trainingEvent: { supportEventOverrides: {}, scenarioEventOverrides: {} },
        racing: { racingPlan: "", enableRacingPlan: false, enableMandatoryRacingPlan: false },
        skills: { skillPointCheck: 350, enableSkillPointCheck: true, skillSpendObjective: "rank" },
        runQueue: { enableRunQueue: true, enableTraineeRotation: true },
        ...(overrides.extra ?? {}),
    }
}

const entryFor = (preset: CharacterPreset) => ({
    inGameName: preset.name,
    presetKey: preset.name,
    scenario: preset.scenario,
    excludeOutfits: [] as string[],
})

const floorOf = (rows: RotationBatchRow[], index: number) => rows.find((r) => r.category === `rot${index}_training` && r.key === "moodFloor")?.value

const tachyonPreset = characterPresets.find((p: CharacterPreset) => p.name === "Agnes Tachyon" && p.scenario === "URA Finale")!
const copanoPreset = characterPresets.find((p: CharacterPreset) => p.name === "Copano Rickey" && p.scenario === "URA Finale")!

describe("rotation snapshots do not leak the previous trainee's floor", () => {
    it("Agnes Tachyon then Copano Rickey resolves Great then Good", () => {
        const { rows } = buildRotationSnapshotRows(makeBase(), [entryFor(tachyonPreset), entryFor(copanoPreset)])
        expect(floorOf(rows, 0)).toBe("Great")
        expect(floorOf(rows, 1)).toBe("Good")
    })

    it("the order of the cycle does not change either entry's value", () => {
        const { rows } = buildRotationSnapshotRows(makeBase(), [entryFor(copanoPreset), entryFor(tachyonPreset)])
        expect(floorOf(rows, 0)).toBe("Good")
        expect(floorOf(rows, 1)).toBe("Great")
    })

    it("a strict floor already sitting in the base settings does not survive into a preset that is silent", () => {
        // The shipped-state repair: a user whose live settings still carry the leaked Great gets
        // Good back on the next rotation build, without any database surgery.
        const { rows } = buildRotationSnapshotRows(makeBase({ moodFloor: "Great" }), [entryFor(copanoPreset)])
        expect(floorOf(rows, 0)).toBe("Good")
    })

    it("rebuilding the same rotation twice produces the same values", () => {
        const cycle = [entryFor(tachyonPreset), entryFor(copanoPreset)]
        const first = buildRotationSnapshotRows(makeBase(), cycle).rows
        const second = buildRotationSnapshotRows(makeBase(), cycle).rows
        expect(floorOf(second, 0)).toBe(floorOf(first, 0))
        expect(floorOf(second, 1)).toBe(floorOf(first, 1))
    })

    it("leaves unrelated training settings present and intact", () => {
        const { rows } = buildRotationSnapshotRows(makeBase(), [entryFor(copanoPreset)])
        const failure = rows.find((r) => r.category === "rot0_training" && r.key === "maximumFailureChance")?.value
        expect(failure).toBeDefined()
        expect(Number.isNaN(Number(failure))).toBe(false)
    })
})

describe("mood floor is deck-agnostic", () => {
    /**
     * Synthetic deck shapes with deliberately arbitrary identities. The point is that NOTHING in
     * mood-floor resolution reads them, so no allowlist or per-card branch can creep in.
     */
    const DECK_SHAPES: { name: string; deck: { id: string; type: string; level: number; limitBreak: number; borrowed: boolean }[] }[] = [
        {
            name: "3 Speed / 2 Wit / Friend",
            deck: [
                { id: "alpha", type: "Speed", level: 50, limitBreak: 4, borrowed: false },
                { id: "beta", type: "Speed", level: 45, limitBreak: 2, borrowed: false },
                { id: "gamma", type: "Speed", level: 50, limitBreak: 4, borrowed: false },
                { id: "delta", type: "Wit", level: 40, limitBreak: 1, borrowed: false },
                { id: "epsilon", type: "Wit", level: 35, limitBreak: 0, borrowed: false },
                { id: "zeta", type: "Speed", level: 50, limitBreak: 4, borrowed: true },
            ],
        },
        {
            name: "2 Speed / 1 Stamina / 2 Wit / Friend",
            deck: [
                { id: "eta", type: "Speed", level: 50, limitBreak: 4, borrowed: false },
                { id: "theta", type: "Speed", level: 30, limitBreak: 0, borrowed: false },
                { id: "iota", type: "Stamina", level: 45, limitBreak: 3, borrowed: false },
                { id: "kappa", type: "Wit", level: 50, limitBreak: 4, borrowed: false },
                { id: "lambda", type: "Wit", level: 25, limitBreak: 0, borrowed: false },
                { id: "mu", type: "Friend", level: 50, limitBreak: 4, borrowed: true },
            ],
        },
        {
            name: "mixed low-rarity deck",
            deck: [
                { id: "nu", type: "Power", level: 20, limitBreak: 0, borrowed: false },
                { id: "xi", type: "Guts", level: 15, limitBreak: 0, borrowed: false },
                { id: "omicron", type: "Wit", level: 20, limitBreak: 0, borrowed: false },
                { id: "pi", type: "Speed", level: 25, limitBreak: 1, borrowed: false },
                { id: "rho", type: "Stamina", level: 20, limitBreak: 0, borrowed: false },
                { id: "sigma", type: "Power", level: 10, limitBreak: 0, borrowed: true },
            ],
        },
        {
            name: "deck without a Friend support",
            deck: [
                { id: "tau", type: "Speed", level: 50, limitBreak: 4, borrowed: false },
                { id: "upsilon", type: "Speed", level: 50, limitBreak: 4, borrowed: false },
                { id: "phi", type: "Stamina", level: 50, limitBreak: 4, borrowed: false },
                { id: "chi", type: "Power", level: 50, limitBreak: 4, borrowed: false },
                { id: "psi", type: "Wit", level: 50, limitBreak: 4, borrowed: false },
                { id: "omega", type: "Guts", level: 50, limitBreak: 4, borrowed: false },
            ],
        },
        {
            name: "deck containing entirely different card identities",
            deck: [
                { id: "card-1", type: "Wit", level: 44, limitBreak: 2, borrowed: true },
                { id: "card-2", type: "Guts", level: 33, limitBreak: 1, borrowed: false },
                { id: "card-3", type: "Power", level: 22, limitBreak: 0, borrowed: false },
                { id: "card-4", type: "Stamina", level: 11, limitBreak: 0, borrowed: false },
                { id: "card-5", type: "Speed", level: 50, limitBreak: 4, borrowed: false },
            ],
        },
    ]

    /** Base settings carrying a deck shape in a real, unrelated field. */
    const baseWithDeck = (deck: unknown) => makeBase({ extra: { runQueue: { enableRunQueue: true, enableTraineeRotation: true, preferredBorrowName: JSON.stringify(deck) } } })

    it.each(DECK_SHAPES)("resolves the same floor for Copano regardless of deck: $name", ({ deck }) => {
        expect(presetMoodFloorOf(copanoPreset.settings)).toBe("Good")
        const { rows } = buildRotationSnapshotRows(baseWithDeck(deck), [entryFor(copanoPreset)])
        expect(floorOf(rows, 0)).toBe("Good")
    })

    it.each(DECK_SHAPES)("resolves the same floor for Agnes Tachyon regardless of deck: $name", ({ deck }) => {
        expect(presetMoodFloorOf(tachyonPreset.settings)).toBe("Great")
        const { rows } = buildRotationSnapshotRows(baseWithDeck(deck), [entryFor(tachyonPreset)])
        expect(floorOf(rows, 0)).toBe("Great")
    })

    it("produces one identical floor across every deck shape for a given preset", () => {
        const results = new Set(DECK_SHAPES.map(({ deck }) => floorOf(buildRotationSnapshotRows(baseWithDeck(deck), [entryFor(copanoPreset)]).rows, 0)))
        expect(results.size).toBe(1)
        expect([...results][0]).toBe("Good")
    })

    it("changing only the deck cannot move the resolved floor", () => {
        const plain = presetMoodFloorOf(copanoPreset.settings)
        // The resolver reads exactly one path, so deck-shaped data alongside it is inert.
        expect(presetMoodFloorOf({ ...copanoPreset.settings, supportDeck: DECK_SHAPES[0].deck })).toBe(plain)
        expect(presetMoodFloorOf({ ...copanoPreset.settings, supportDeck: DECK_SHAPES[3].deck })).toBe(plain)
    })

    it("introduces no named support-card dependency in the policy module", () => {
        // A guard against someone later "fixing" a trainee by allowlisting a card here.
        const source = srcFile("lib", "moodFloorPolicy.ts")
        for (const forbidden of ["Kitasan", "Tachyon", "Light Hello", "Tokai Teio", "supportDeck", "supportCards", "limitBreak", "borrow"]) {
            expect(source).not.toContain(forbidden)
        }
    })
})

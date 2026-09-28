import fs from "fs"
import path from "path"
import { PERSONAL_SETTINGS, keepPersonalSettings } from "../personalSettings"
import { buildRotationSnapshotRows } from "../rotationSnapshots"
import { characterPresets } from "../../data/characterPresets"
import { characterBaseOutfits, presetOutfit } from "../../data/presetMeta"

// A value no preset ships, so a kept value can never be mistaken for a preset's own.
const SENTINEL = "player-value"

function personalPairs(): [string, string][] {
    return Object.entries(PERSONAL_SETTINGS).flatMap(([category, keys]) => keys.map((key): [string, string] => [category, key]))
}

/** A base whose every personal key holds the player's value, plus a stale value for one preset-owned key. */
function playerBase(): any {
    const base: any = { general: {}, racing: {}, trainingEvent: {}, training: {}, misc: {}, skills: { skillPointCheck: 750, enableSkillPointCheck: false } }
    for (const [category, key] of personalPairs()) base[category][key] = SENTINEL
    return base
}

describe("PERSONAL_SETTINGS", () => {
    it("lists exactly the device timing, OCR tuning, display and stop-point keys", () => {
        expect(PERSONAL_SETTINGS).toEqual({
            general: ["waitDelay", "dialogWaitDelay", "enableStopBeforeFinals", "enableStopAtDate", "stopAtDates", "enableCraneGameAttempt"],
            racing: ["enableStopOnMandatoryRaces"],
            trainingEvent: ["ocrConfidence", "enableAutomaticOCRRetry", "enableHideOCRComparisonResults"],
            training: ["enableYoloStatDetection", "enableTrainingAnalysisValidation"],
            misc: ["enableSettingsDisplay", "enableMessageIdDisplay", "messageLogFontSize", "overlayButtonSizeDP"],
        })
    })

    it("names only keys every preset actually ships (a typo here would silently keep nothing)", () => {
        for (const [category, key] of personalPairs()) {
            for (const preset of characterPresets) {
                expect((preset.settings as any)[category]).toHaveProperty(key)
            }
        }
    })

    it("never lists the keys a preset must own", () => {
        const listed = new Set(personalPairs().map(([category, key]) => `${category}.${key}`))
        for (const owned of ["general.scenario", "general.enablePopupCheck", "racing.enableRacingPlan", "racing.racingPlan", "skills.skillPointCheck"]) {
            expect(listed.has(owned)).toBe(false)
        }
    })
})

describe("keepPersonalSettings", () => {
    it("puts back every personal key the preset overwrote", () => {
        const merged: any = { general: {}, racing: {}, trainingEvent: {}, training: {}, misc: {} }
        for (const [category, key] of personalPairs()) merged[category][key] = "preset-value"
        keepPersonalSettings(merged, playerBase())
        for (const [category, key] of personalPairs()) expect(merged[category][key]).toBe(SENTINEL)
    })

    it("leaves every non-personal key as the merge produced it", () => {
        const merged: any = { general: { scenario: "URA Finale", waitDelay: 1 }, racing: { racingPlan: "[1]" }, skills: { skillSpendObjective: "sparks" } }
        keepPersonalSettings(merged, { ...playerBase(), general: { scenario: "Unity Cup", waitDelay: 9 }, racing: { racingPlan: "[]" } })
        expect(merged.general.scenario).toBe("URA Finale")
        expect(merged.racing.racingPlan).toBe("[1]")
        expect(merged.skills.skillSpendObjective).toBe("sparks")
        expect(merged.general.waitDelay).toBe(9)
    })

    it("keeps a value that is false, zero or empty (falsy is still the player's choice)", () => {
        const merged: any = { general: { enableStopBeforeFinals: true, waitDelay: 5, stopAtDates: "x" } }
        keepPersonalSettings(merged, { general: { enableStopBeforeFinals: false, waitDelay: 0, stopAtDates: "" } })
        expect(merged.general).toEqual({ enableStopBeforeFinals: false, waitDelay: 0, stopAtDates: "" })
    })

    it("leaves a key the base does not carry as the preset set it", () => {
        const merged: any = { general: { waitDelay: 5, dialogWaitDelay: 7 } }
        keepPersonalSettings(merged, { general: { dialogWaitDelay: 2 } })
        expect(merged.general).toEqual({ waitDelay: 5, dialogWaitDelay: 2 })
    })

    it("tolerates a category missing on either side", () => {
        const merged: any = { general: { waitDelay: 5 } }
        expect(() => keepPersonalSettings(merged, { racing: { enableStopOnMandatoryRaces: true } })).not.toThrow()
        expect(() => keepPersonalSettings({ misc: { messageLogFontSize: 1 } }, {})).not.toThrow()
        expect(merged.general.waitDelay).toBe(5)
    })

    it("does not mutate the base", () => {
        const base = playerBase()
        const before = JSON.stringify(base)
        keepPersonalSettings({ general: { waitDelay: 1 }, misc: {} }, base)
        expect(JSON.stringify(base)).toBe(before)
    })
})

describe("rotation snapshots keep the player's personal settings", () => {
    const preset = characterPresets[0]
    const rowValue = (rows: { category: string; key: string; value: any }[], category: string, key: string) =>
        rows.find((r) => r.category === `rot0_${category}` && r.key === key)?.value

    const base = playerBase()
    const { rows } = buildRotationSnapshotRows(base, [{ inGameName: "Test", presetKey: preset.name, scenario: preset.scenario }])

    it("every personal key carries the player's value even though the preset ships its own", () => {
        for (const [category, key] of personalPairs()) {
            expect((preset.settings as any)[category][key]).not.toBe(SENTINEL)
            expect(rowValue(rows, category, key)).toBe(SENTINEL)
        }
    })

    it("still applies the preset's non-personal keys in those categories", () => {
        const personal = new Set(personalPairs().map(([category, key]) => `${category}.${key}`))
        const skipped = new Set(["general.scenario"])
        let checked = 0
        for (const category of ["general", "racing", "trainingEvent", "training"]) {
            for (const [key, value] of Object.entries((preset.settings as any)[category])) {
                if (personal.has(`${category}.${key}`) || skipped.has(`${category}.${key}`)) continue
                if (typeof value === "object" && value !== null) continue
                if (key === "racingPlanData") continue
                expect([category, key, rowValue(rows, category, key)]).toEqual([category, key, value])
                checked++
            }
        }
        expect(checked).toBeGreaterThan(5)
    })

    it("keeps the skill-spend preservation working alongside it", () => {
        expect(rowValue(rows, "skills", "skillPointCheck")).toBe(750)
        expect(rowValue(rows, "skills", "enableSkillPointCheck")).toBe(false)
    })
})

describe("Home preset apply keeps the player's personal settings", () => {
    const source = fs.readFileSync(path.join(__dirname, "..", "..", "pages", "Home", "index.tsx"), "utf8")
    const apply = source.slice(source.indexOf("const handlePresetChange"))

    it("imports the shared helper", () => {
        expect(source).toContain('import { keepPersonalSettings } from "../../lib/personalSettings"')
    })

    it("restores the player's values from the settings captured before the merge, after the merge", () => {
        const calls = apply.match(/keepPersonalSettings\(/g) ?? []
        expect(calls).toHaveLength(1)
        expect(apply).toContain("keepPersonalSettings(merged, bsc.settings)")
        expect(apply.indexOf("keepPersonalSettings(")).toBeGreaterThan(apply.indexOf("(merged as any)[category] = values"))
        expect(apply.indexOf("keepPersonalSettings(")).toBeGreaterThan(apply.indexOf("merged.skills.skillPointCheck = preservedSkillPointCheck"))
    })
})

describe("base outfit names for the newest characters", () => {
    const expected: Record<string, string> = {
        "Agnes Digital": "Full-Color Fangirling",
        "Inari One": "Edomurasaki",
        "Ines Fujin": "Always Electrifying",
        "Mejiro Bright": "Brunissage Line",
        "Satono Diamond": "Natural Brilliance",
    }

    it("resolves each plain-named preset to its base outfit", () => {
        for (const [name, outfit] of Object.entries(expected)) {
            expect(characterPresets.some((p) => p.name === name)).toBe(true)
            expect(presetOutfit(name)).toBe(outfit)
        }
    })

    it("has an outfit for every plain-named preset (the picker and Home never show a blank)", () => {
        const plain = [...new Set(characterPresets.filter((p) => !/\(/.test(p.name)).map((p) => p.name))]
        expect(plain.filter((name) => !presetOutfit(name))).toEqual([])
    })

    it("names each base outfit as its character's lowest card id in the bundled build-budget data", () => {
        const data = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "..", "data", "build_budget_data.json"), "utf8"))
        const lowest = new Map<string, { cardId: number; outfit: string }>()
        for (const card of data.traineeGrowth) {
            const seen = lowest.get(card.character)
            if (!seen || card.cardId < seen.cardId) lowest.set(card.character, { cardId: card.cardId, outfit: card.outfit })
        }
        for (const [name, outfit] of Object.entries(characterBaseOutfits)) {
            expect([name, lowest.get(name)?.outfit]).toEqual([name, `[${outfit}]`])
        }
    })
})

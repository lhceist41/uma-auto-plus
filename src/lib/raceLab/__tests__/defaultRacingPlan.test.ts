import { readFileSync } from "node:fs"
import { join } from "node:path"
import { defaultSettings } from "../../../context/BotStateContext"
import racesData from "../../../data/races.json"
import { applyMigrations, convertSettingsToBatch, deepMerge } from "../../settingsUtils"
import { loadRaceCatalog } from "../catalog.node.ts"
import { buildPlanFeasibilityPreview } from "../planFeasibility.ts"
import { parsePlan, validatePlan } from "../planValidator.ts"
import type { PlannedRace } from "../types.ts"

// Import the real defaults without loading the provider's native styling runtime.
jest.mock("react-native-css-interop/jsx-runtime", () => jest.requireActual("react/jsx-runtime"))

const catalog = loadRaceCatalog(join(process.cwd(), "src/data/compiled"))
const races = Object.values(racesData)
const generated: PlannedRace[] = JSON.parse(defaultSettings.racing.racingPlan)
const legacyPlan = JSON.stringify(races.map((race, priority) => ({ raceName: race.name, date: race.date, priority })))
const customPlan = JSON.stringify([{ raceName: "Marine Cup", date: "Senior Class April, First Half", turnNumber: 55, priority: 7 }])

function preview(racingPlan: string) {
    return buildPlanFeasibilityPreview({
        racingPlan,
        scenario: defaultSettings.general.scenario,
        trackblazerConsecutiveRacesLimit: defaultSettings.scenarioOverrides.trackblazerConsecutiveRacesLimit,
        ignoreConsecutiveRaceWarning: defaultSettings.racing.ignoreConsecutiveRaceWarning,
        enableForceRacing: defaultSettings.racing.enableForceRacing,
    })
}

describe("default racing plan", () => {
    it("keeps every race in source order with its original priority and canonical turn", () => {
        expect(generated).toHaveLength(races.length)
        generated.forEach((entry, index) => {
            const race = races[index]
            expect(Number.isInteger(entry.turnNumber)).toBe(true)
            expect(entry).toEqual({ raceName: race.name, date: race.date, priority: index, turnNumber: race.turnNumber })
            expect(catalog.raceByKey(entry.raceName, entry.turnNumber)).toMatchObject({ date: entry.date, turnNumber: race.turnNumber })
        })
        expect(racesData).toEqual(JSON.parse(readFileSync(join(process.cwd(), "src/data/races.json"), "utf8")))
        expect(defaultSettings.racing.racingPlanData).toBe(JSON.stringify(racesData))
        expect(defaultSettings.racing.enableRacingPlan).toBe(false)
        expect(defaultSettings.racing.enableMandatoryRacingPlan).toBe(false)
    })

    it("distinguishes the Classic and Senior Marine Cup instances", () => {
        expect(generated.filter((race) => race.raceName === "Marine Cup")).toEqual([
            expect.objectContaining({ date: "Classic Class April, First Half", turnNumber: 31 }),
            expect.objectContaining({ date: "Senior Class April, First Half", turnNumber: 55 }),
        ])
        expect(catalog.racesByName("Marine Cup").map(({ date, turnNumber }) => ({ date, turnNumber }))).toEqual([
            { date: "Classic Class April, First Half", turnNumber: 31 },
            { date: "Senior Class April, First Half", turnNumber: 55 },
        ])
    })

    it("is fully readable without hiding conflicts in the all-races schedule", () => {
        const parsed = parsePlan(defaultSettings.racing.racingPlan)
        expect(parsed.issues.length).toBe(0)
        expect(parsed.plan).toEqual(generated)
        const report = validatePlan(parsed.plan, catalog)
        expect(report.issues.filter((issue) => issue.code === "raceNotFound" || issue.code === "planTurnMismatch")).toEqual([])
        expect(report.issues.some((issue) => issue.code === "conflictingRacesOnTurn")).toBe(true)
        const result = preview(defaultSettings.racing.racingPlan)
        expect(result.kind).toBe("checked")
        if (result.kind !== "checked") throw new Error("Default plan was not checked")
        expect(result.raceCount).toBe(races.length)
        expect(result.errorCount).toBeGreaterThan(0)
        expect(result.warningCount).toBeGreaterThan(0)
    })

    it("still rejects every entry in the original missing-turn shape", () => {
        const parsed = parsePlan(legacyPlan)
        expect(parsed.plan).toEqual([])
        expect(parsed.issues).toHaveLength(races.length)
        expect(parsed.issues.every((issue) => issue.code === "malformedPlanEntry")).toBe(true)
        expect(preview(legacyPlan).kind).toBe("unreadable")
    })

    it.each([
        { raceName: "Marine Cup", date: "Senior Class April, First Half" },
        { raceName: "Marine Cup", turnNumber: 55 },
        { date: "Senior Class April, First Half", turnNumber: 55 },
        { raceName: "Marine Cup", date: "Senior Class April, First Half", turnNumber: "55" },
        { raceName: "Marine Cup", date: "Senior Class April, First Half", turnNumber: 55.5 },
    ])("does not salvage a plan containing an incomplete or mistyped entry: %j", (invalid) => {
        const mixed = JSON.stringify([...JSON.parse(customPlan), invalid])
        expect(parsePlan(mixed).issues).toEqual([expect.objectContaining({ code: "malformedPlanEntry" })])
        expect(preview(mixed).kind).toBe("unreadable")
    })

    it.each([
        ["empty", "[]", "empty"],
        ["custom", customPlan, "checked"],
        ["legacy", legacyPlan, "unreadable"],
        ["invalid JSON", "{", "unreadable"],
        ["non-array JSON", "{}", "unreadable"],
    ])("preserves the %s saved/imported plan through settings merge, migrations and serialization", (_name, racingPlan, kind) => {
        const incoming = { racing: { racingPlan } }
        const before = JSON.stringify(incoming)
        const merged = deepMerge(JSON.parse(JSON.stringify(defaultSettings)), incoming)
        const migrated = applyMigrations(merged)
        expect(migrated.settings.racing.racingPlan).toBe(racingPlan)
        expect(migrated.anyMigrated).toBe(false)
        expect(convertSettingsToBatch(migrated.settings)).toContainEqual({ category: "racing", key: "racingPlan", value: racingPlan })
        expect(preview(migrated.settings.racing.racingPlan).kind).toBe(kind)
        expect(JSON.stringify(incoming)).toBe(before)
    })

    it.each([{}, { racing: {} }])("supplies the readable default when the saved/imported plan is absent: %j", (incoming) => {
        const merged = deepMerge(JSON.parse(JSON.stringify(defaultSettings)), incoming)
        const { settings } = applyMigrations(merged)
        expect(settings.racing.racingPlan).toBe(defaultSettings.racing.racingPlan)
        expect(preview(settings.racing.racingPlan).kind).toBe("checked")
    })
})

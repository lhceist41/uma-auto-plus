import { buildRotationSnapshotRows } from "../rotationSnapshots"
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

    it("forces the entry's scenario into general.scenario", () => {
        const scenarioRow = rows.find((r) => r.category === "rot0_general" && r.key === "scenario")
        expect(scenarioRow?.value).toBe(preset.scenario)
    })
})

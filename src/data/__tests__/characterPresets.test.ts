import { avoidAdvisoryFor, characterPresets, trainerAdvisories } from "../characterPresets"

describe("avoidAdvisoryFor", () => {
    it("flags Haru Urara in Trackblazer as an avoid (turf-aptitude mismatch)", () => {
        const avoid = avoidAdvisoryFor("Haru Urara", "Trackblazer")
        expect(avoid).not.toBeNull()
        expect(avoid?.scenario).toBe("Trackblazer")
        expect(avoid?.reason.length).toBeGreaterThan(0)
    })

    it("flags Haru Urara in Unity Cup as an avoid too", () => {
        expect(avoidAdvisoryFor("Haru Urara", "Unity Cup")).not.toBeNull()
    })

    it("does not flag Haru Urara in URA Finale (her recommended scenario)", () => {
        expect(avoidAdvisoryFor("Haru Urara", "URA Finale")).toBeNull()
    })

    it("returns null for a trainee that has no advisory entry", () => {
        expect(avoidAdvisoryFor("Nonexistent Trainee", "Trackblazer")).toBeNull()
    })
})

describe("trainerAdvisories data integrity", () => {
    it("every avoid entry carries a scenario and a non-empty reason", () => {
        for (const advisory of Object.values(trainerAdvisories)) {
            for (const avoid of advisory.avoid ?? []) {
                expect(avoid.scenario.length).toBeGreaterThan(0)
                expect(avoid.reason.trim().length).toBeGreaterThan(0)
            }
        }
    })
})

describe("Super Creek (Blue Farm) preset", () => {
    const find = (name: string, scenario: string) => characterPresets.find((p) => p.name === name && p.scenario === scenario)
    const base = () => find("Super Creek", "Unity Cup")
    const blueFarm = () => find("Super Creek (Blue Farm)", "Unity Cup")

    it("leaves the original Super Creek Unity Cup preset unchanged (Speed+Stamina focus)", () => {
        const b = base()
        expect(b).toBeDefined()
        expect(b!.settings.training?.focusOnSparkStatTarget).toEqual(["Speed", "Stamina"])
    })

    it("exists as a distinct preset under Unity Cup", () => {
        const bf = blueFarm()
        expect(bf).toBeDefined()
        expect(bf!.scenario).toBe("Unity Cup")
        expect(bf).not.toBe(base())
    })

    it("declares its canonical trainee identity as plain 'Super Creek' (display name is a variant label)", () => {
        expect(blueFarm()!.traineeName).toBe("Super Creek")
        expect(base()!.traineeName).toBeUndefined() // the competitive preset selects by its own name
    })

    it("focuses the spark rescue on all five stats, each exactly once", () => {
        const focus = blueFarm()!.settings.training!.focusOnSparkStatTarget!
        expect(focus).toEqual(["Speed", "Stamina", "Power", "Guts", "Wit"])
        expect(new Set(focus).size).toBe(5)
    })

    it("matches the source Creek Unity build in every setting except the spark target", () => {
        const strip = (s: unknown) => {
            const clone = JSON.parse(JSON.stringify(s))
            delete clone.training.focusOnSparkStatTarget
            return clone
        }
        expect(strip(blueFarm()!.settings)).toEqual(strip(base()!.settings))
    })

    it("is independent of the source preset — a deep clone (what apply/rotation snapshots do) cannot mutate it", () => {
        const bf = blueFarm()!
        const b = base()!
        expect(bf.settings.training!.focusOnSparkStatTarget).not.toBe(b.settings.training!.focusOnSparkStatTarget)
        const applied = JSON.parse(JSON.stringify(bf))
        applied.settings.training.focusOnSparkStatTarget.push("Speed")
        expect(bf.settings.training!.focusOnSparkStatTarget).toHaveLength(5)
        expect(b.settings.training!.focusOnSparkStatTarget).toEqual(["Speed", "Stamina"])
    })

    it("is not marked recommended (stays unproven until its own careers are analyzed)", () => {
        // The name suffix is the label; deliberately no recommended-badge entry -> no "proven" hint.
        expect(trainerAdvisories["Super Creek (Blue Farm)"]?.recommended ?? []).toEqual([])
    })
})

import { avoidAdvisoryFor, trainerAdvisories } from "../characterPresets"

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

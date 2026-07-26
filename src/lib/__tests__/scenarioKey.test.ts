import {
    GRAND_CONCERT_ALIASES,
    GRAND_CONCERT_KEY,
    GRAND_CONCERT_WARNING,
    isGrandConcert,
    normalizeScenarioKey,
    scenarioCapabilities,
} from "../scenarioKey"
import scenarios from "../../data/scenarios.json"

describe("scenario key normalization", () => {
    it("resolves every accepted Grand Concert spelling to the one canonical key", () => {
        for (const alias of GRAND_CONCERT_ALIASES) {
            expect(normalizeScenarioKey(alias)).toBe(GRAND_CONCERT_KEY)
            expect(isGrandConcert(alias)).toBe(true)
        }
    })

    it("ignores casing, punctuation, and surrounding whitespace", () => {
        for (const spelling of [
            "  brighter together our grand concert  ",
            "BRIGHTER TOGETHER! OUR GRAND CONCERT",
            "Brighter Together, Our Grand Concert",
            "grand live",
            "GrandConcert",
        ]) {
            expect(normalizeScenarioKey(spelling)).toBe(GRAND_CONCERT_KEY)
        }
    })

    it("leaves every other scenario untouched", () => {
        for (const other of ["URA Finale", "Unity Cup", "Trackblazer", "Daily Races", "Team Trials"]) {
            expect(normalizeScenarioKey(other)).toBe(other)
            expect(isGrandConcert(other)).toBe(false)
        }
    })

    it("does not match a merely similar scenario name", () => {
        expect(isGrandConcert("Grand Masters")).toBe(false)
        expect(isGrandConcert("Concert Hall")).toBe(false)
    })

    it("keeps unset distinguishable from a real scenario", () => {
        expect(normalizeScenarioKey(undefined)).toBe("")
        expect(normalizeScenarioKey(null)).toBe("")
        expect(normalizeScenarioKey("   ")).toBe("")
        expect(isGrandConcert("")).toBe(false)
    })

    it("is idempotent", () => {
        expect(normalizeScenarioKey(normalizeScenarioKey("Grand Live"))).toBe(GRAND_CONCERT_KEY)
    })
})

describe("scenario capabilities", () => {
    // Grand Concert was queue-gated while its Lesson and concert screens still stopped for manual
    // input. They no longer do, and the navigator pages the carousel to it like any other
    // scenario, so it is now capable across the board and must stay that way under every alias.
    it("gives Grand Concert the full queue capability set under every alias", () => {
        for (const alias of GRAND_CONCERT_ALIASES) {
            const caps = scenarioCapabilities(alias)
            expect(caps).toEqual({ runQueue: true, rotation: true, tpRestore: true, singleRun: true })
        }
    })

    it("leaves the other scenarios fully capable", () => {
        for (const other of ["URA Finale", "Unity Cup", "Trackblazer"]) {
            const caps = scenarioCapabilities(other)
            expect(caps).toEqual({ runQueue: true, rotation: true, tpRestore: true, singleRun: true })
        }
    })

    it("describes Grand Concert in player-facing words without internal labels", () => {
        expect(GRAND_CONCERT_WARNING).toMatch(/unattended/i)
        expect(GRAND_CONCERT_WARNING).toMatch(/Lesson/)
        expect(GRAND_CONCERT_WARNING).toMatch(/press Start|resume/i)
        // It must no longer claim the queue features are unavailable.
        expect(GRAND_CONCERT_WARNING).not.toMatch(/unavailable|experimental/i)
        // No planning vocabulary may leak into player-facing text.
        expect(GRAND_CONCERT_WARNING).not.toMatch(/layer|phase|batch|task \d|scaffold/i)
    })
})

describe("scenario data registration", () => {
    it("registers the canonical key in the scenario event data", () => {
        expect(Object.keys(scenarios)).toContain(GRAND_CONCERT_KEY)
    })

    it("keeps the existing scenarios registered", () => {
        for (const other of ["URA Finale", "Unity Cup", "Trackblazer"]) {
            expect(Object.keys(scenarios)).toContain(other)
        }
    })
})

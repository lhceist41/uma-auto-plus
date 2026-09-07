import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, resolve } from "node:path"
import process from "node:process"
import { buildPlanFeasibilityPreview } from "../planFeasibility.ts"
import type { PlanFeasibilityInput, PlanFeasibilityPreview } from "../planFeasibility.ts"

const REPO_ROOT = process.cwd()

// Real catalog fixtures: Marine Cup runs on turns 31 and 55, Fukuryu Stakes shares turn 31, and turns
// 20..23 each carry a race, so a planned streak is reachable without inventing data.
const TURN_20_TO_23: [string, string, number][] = [
    ["Artemis Stakes", "Junior Class October, Second Half", 20],
    ["Daily Hai Junior Stakes", "Junior Class November, First Half", 21],
    ["Akamatsu Sho", "Junior Class November, Second Half", 22],
    ["Asahi Hai Futurity Stakes", "Junior Class December, First Half", 23],
]

function planJson(entries: [string, string, number][]): string {
    return JSON.stringify(entries.map(([raceName, date, turnNumber], i) => ({ raceName, date, priority: i, turnNumber })))
}

function preview(racingPlan: string, over: Partial<PlanFeasibilityInput> = {}): PlanFeasibilityPreview {
    return buildPlanFeasibilityPreview({
        racingPlan,
        scenario: "URA Finale",
        trackblazerConsecutiveRacesLimit: 2,
        ignoreConsecutiveRaceWarning: false,
        enableForceRacing: false,
        ...over,
    })
}

function messages(result: PlanFeasibilityPreview): string[] {
    return result.kind === "checked" || result.kind === "unreadable" ? result.findings.map((f) => f.message) : []
}

describe("plan feasibility preview", () => {
    it("flags a race the catalog does not have", () => {
        const r = preview(planJson([["No Such Race", "Classic Class April, First Half", 31]]))
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.errorCount).toBe(1)
        expect(messages(r)[0]).toContain('there is no race called "No Such Race"')
    })

    it("flags a known race planned on the wrong turn and names the turns it does run on", () => {
        const r = preview(planJson([["Marine Cup", "Classic Class April, First Half", 32]]))
        expect(r.kind).toBe("checked")
        const message = messages(r)[0]
        expect(message).toContain('"Marine Cup" does not run on turn 32')
        expect(message).toContain("turns 31, 55")
    })

    it("flags two different races booked on the same turn", () => {
        const r = preview(
            planJson([
                ["Marine Cup", "Classic Class April, First Half", 31],
                ["Fukuryu Stakes", "Classic Class April, First Half", 31],
            ])
        )
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.errorCount).toBe(1)
        expect(messages(r)[0]).toContain("Turn 31 has 2 different races planned (Fukuryu Stakes, Marine Cup)")
    })

    it("flags the same race listed twice on a turn", () => {
        const r = preview(
            planJson([
                ["Marine Cup", "Classic Class April, First Half", 31],
                ["Marine Cup", "Classic Class April, First Half", 31],
            ])
        )
        expect(messages(r)[0]).toContain('Turn 31 lists "Marine Cup" 2 times')
    })

    it("flags a streak past the Trackblazer limit and reports the configured number", () => {
        const r = preview(planJson(TURN_20_TO_23), { scenario: "Trackblazer" })
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.consecutiveCheck).toEqual({ mode: "trackblazerLimit", limit: 2 })
        expect(r.warningCount).toBe(1)
        expect(messages(r)[0]).toContain("Turns 20 to 23: 4 races in a row")
        expect(messages(r)[0]).toContain("stop at 2 consecutive races")
    })

    // Trackblazer.kt#handleRaceEvents aborts on `consecutiveRaceCount > consecutiveRacesLimit`, and the
    // OCR'd count already includes the race about to be run, so a streak of exactly the limit is allowed
    // and one more is not. Pin both sides of that boundary rather than inferring it from 2 vs 4.
    it.each([
        [2, 0],
        [3, 1],
    ])("at a Trackblazer limit of 2, a streak of %i produces %i over-limit warnings", (length, expected) => {
        const r = preview(planJson(TURN_20_TO_23.slice(0, length)), { scenario: "Trackblazer" })
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.warningCount).toBe(expected)
    })

    it("flags back-to-back races outside Trackblazer, where no limit is configured", () => {
        const r = preview(planJson(TURN_20_TO_23.slice(0, 2)))
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.consecutiveCheck).toEqual({ mode: "gameWarning", limit: null })
        expect(r.warningCount).toBe(1)
        expect(messages(r)[0]).toContain("Turns 20 to 21: 2 races in a row")
    })

    it.each([["ignoreConsecutiveRaceWarning"], ["enableForceRacing"]])("does not flag races in a row when %s races through the warning", (flag) => {
        const r = preview(planJson(TURN_20_TO_23), { scenario: "Trackblazer", [flag]: true })
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.consecutiveCheck.mode).toBe("forced")
        expect(r.warningCount).toBe(0)
    })

    // "Treat Planned Races as Mandatory" is not a consecutive-race force flag, so it is not part of
    // PlanFeasibilityInput. Racing.kt#handleRaceEvents aborts the extra race on
    // `!(overrideIgnore || enableForceRacing || ignoreConsecutiveRaceWarning)` before handleExtraRace is
    // reached, and Trackblazer.kt#handleRaceEvents aborts on those two racing flags alone. Passing it
    // anyway proves the preview ignores it at runtime, which the type alone cannot (tests strip types).
    const withMandatoryPlan = (over: Partial<PlanFeasibilityInput>): Partial<PlanFeasibilityInput> => ({ ...over, enableMandatoryRacingPlan: true }) as Partial<PlanFeasibilityInput>

    it("keeps the Trackblazer over-limit warning when only mandatory-plan mode is on", () => {
        const r = preview(planJson(TURN_20_TO_23), withMandatoryPlan({ scenario: "Trackblazer", trackblazerConsecutiveRacesLimit: 2 }))
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.consecutiveCheck).toEqual({ mode: "trackblazerLimit", limit: 2 })
        expect(r.warningCount).toBe(1)
        expect(messages(r)[0]).toContain("Turns 20 to 23: 4 races in a row")
    })

    it("keeps the game-warning pressure outside Trackblazer when only mandatory-plan mode is on", () => {
        const r = preview(planJson(TURN_20_TO_23.slice(0, 2)), withMandatoryPlan({ scenario: "URA Finale" }))
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.consecutiveCheck).toEqual({ mode: "gameWarning", limit: null })
        expect(r.warningCount).toBe(1)
        expect(messages(r)[0]).toContain("Turns 20 to 21: 2 races in a row")
    })

    it("treats an empty plan as empty rather than as a problem", () => {
        for (const empty of ["", "[]", "   "]) expect(preview(empty)).toEqual({ kind: "empty" })
    })

    // Settings imported from a JSON file are deep-merged field by field with no type validation, so
    // racingPlan can arrive as any JSON value. The declared type is string, hence the casts: this proves
    // the adapter survives what the type system cannot enforce once types are stripped at runtime.
    it.each([[123], [null], [[]], [{}], [true], [undefined]])("fails closed on a non-string racing plan (%p) without throwing", (value) => {
        const r = preview(value as unknown as string)
        expect(r.kind).toBe("unreadable")
        expect(messages(r)).toEqual(["The saved racing plan is not valid data, so the bot would ignore all of it. Use Clear and pick your races again."])
    })

    it("fails closed on malformed JSON without throwing", () => {
        const r = preview("{not json")
        expect(r.kind).toBe("unreadable")
        expect(messages(r)[0]).toContain("not valid data")
        expect(messages(r)[0]).not.toContain("JSON")
    })

    it("treats entries missing a field the runtime requires as an unreadable plan", () => {
        // Racing.kt#loadUserPlannedRaces reads raceName, date and turnNumber with getString/getInt, so a
        // missing one throws there and discards the whole plan. The shipped default plan has no turnNumber.
        const noTurn = JSON.stringify([{ raceName: "Marine Cup", date: "Classic Class April, First Half", priority: 0 }])
        const noDate = JSON.stringify([{ raceName: "Marine Cup", priority: 0, turnNumber: 31 }])
        for (const bad of [noTurn, noDate]) {
            const r = preview(bad)
            expect(r.kind).toBe("unreadable")
            expect(messages(r)[0]).toContain("ignores the whole plan")
        }
    })

    it("reports a clean plan as checked without promising it will succeed", () => {
        const r = preview(planJson([["Marine Cup", "Classic Class April, First Half", 31]]))
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.raceCount).toBe(1)
        expect(r.errorCount).toBe(0)
        expect(r.warningCount).toBe(0)
        expect(r.findings).toEqual([])
    })

    it("caps the listed findings and counts the rest", () => {
        const many: [string, string, number][] = []
        for (let i = 0; i < 30; i++) many.push([`Missing Race ${i}`, "Classic Class April, First Half", 30 + i * 2])
        const r = preview(planJson(many))
        expect(r.kind).toBe("checked")
        if (r.kind !== "checked") return
        expect(r.errorCount).toBe(30)
        expect(r.findings.length).toBe(12)
        expect(r.hiddenCount).toBe(18)
    })

    it("follows the plan it is given rather than any other stored plan", () => {
        const clean = preview(planJson([["Marine Cup", "Classic Class April, First Half", 31]]))
        const broken = preview(planJson([["Marine Cup", "Classic Class April, First Half", 32]]))
        expect(clean.kind === "checked" && clean.errorCount).toBe(0)
        expect(broken.kind === "checked" && broken.errorCount).toBe(1)
    })
})

describe("app import boundary", () => {
    const APP_ENTRY = join(REPO_ROOT, "src/lib/raceLab/planFeasibility.ts")

    // Bare specifiers resolve to the same built-ins as their "node:" forms and Metro cannot supply either.
    const NODE_BUILTINS = new Set(["assert", "buffer", "child_process", "crypto", "fs", "http", "https", "module", "net", "os", "path", "process", "stream", "url", "util", "worker_threads", "zlib"])

    /** Node built-ins a module imports, by either specifier form. Only quoted import/require targets. */
    function nodeImports(source: string): string[] {
        const found: string[] = []
        for (const match of source.matchAll(/(?:from|require\()\s*["']([^"']+)["']/g)) {
            const specifier = match[1]
            if (specifier.startsWith("node:") || NODE_BUILTINS.has(specifier)) found.push(specifier)
        }
        return found
    }

    it("detects both specifier forms and leaves ordinary imports and identifiers alone", () => {
        expect(nodeImports('import { readFileSync } from "fs"')).toEqual(["fs"])
        expect(nodeImports('import { join } from "node:path"')).toEqual(["node:path"])
        expect(nodeImports('const { createHash } = require("crypto")')).toEqual(["crypto"])
        expect(nodeImports('import { useMemo } from "react"\nconst path = buildPath()\ncrypto.subtle')).toEqual([])
        expect(nodeImports('import type { RaceSource } from "../masterData/reader.ts"')).toEqual([])
    })

    /** Every .ts module reachable from `entry` by static relative import. */
    function reachableModules(entry: string): string[] {
        const seen = new Set<string>()
        const queue = [entry]
        while (queue.length > 0) {
            const file = queue.pop() as string
            if (seen.has(file)) continue
            seen.add(file)
            const source = readFileSync(file, "utf8")
            for (const match of source.matchAll(/from\s+"(\.[^"]+)"/g)) {
                const target = resolve(dirname(file), match[1])
                const candidates = [target, `${target}.ts`, `${target}.tsx`, join(target, "index.ts"), join(target, "index.tsx")]
                const resolved = candidates.find((c) => {
                    try {
                        return readdirSync(dirname(c)).includes(c.slice(dirname(c).length + 1)) && c.endsWith(".ts")
                    } catch {
                        return false
                    }
                })
                if (resolved !== undefined) queue.push(resolved)
            }
        }
        return [...seen]
    }

    it("reaches no node built-in from the module the app imports", () => {
        const modules = reachableModules(APP_ENTRY)
        // The walk must actually cross module boundaries, or the assertion below proves nothing.
        expect(modules.length).toBeGreaterThan(4)
        expect(modules.some((m) => m.endsWith("reader.ts"))).toBe(true)
        expect(modules.some((m) => m.endsWith("planValidator.ts"))).toBe(true)
        const offenders = modules.flatMap((m) => nodeImports(readFileSync(m, "utf8")).map((specifier) => `${m}: ${specifier}`))
        expect(offenders).toEqual([])
    })
})

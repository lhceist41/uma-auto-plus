import { readFileSync, statSync } from "node:fs"
import { execFileSync } from "node:child_process"
import { join } from "node:path"
import process from "node:process"

// The generator is a CLI (top-level .mjs). These are integration tests: they run it as a subprocess and
// assert the RaceLab-adoption invariants on its real output. Jest cwd is the repo root.
const REPO_ROOT = process.cwd()
const GENERATOR = join(REPO_ROOT, "scripts/generate-racing-plan.mjs")
const GENERATOR_SRC = readFileSync(GENERATOR, "utf8")

function run(args: string[]): { code: number; stdout: string } {
    try {
        const stdout = execFileSync("node", [GENERATOR, ...args], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] })
        return { code: 0, stdout }
    } catch (e: unknown) {
        const err = e as { status?: number; stdout?: string }
        return { code: err.status ?? -1, stdout: err.stdout ?? "" }
    }
}

const APT = "turf=A,dirt=A,sprint=A,mile=A,medium=A,long=A"

describe("generate-racing-plan consumes RaceLab (adoption seam)", () => {
    it("1. imports RaceLab factual APIs and no longer reads raw races.json / hand-rolls objective canonicalization", () => {
        expect(GENERATOR_SRC).toMatch(/from "\.\.\/src\/lib\/raceLab\/catalog\.ts"/)
        expect(GENERATOR_SRC).toMatch(/buildObjectiveTimeline/)
        expect(GENERATOR_SRC).toMatch(/loadRaceCatalog/)
        // it must not read the raw race data file directly anymore (a comment may still name it)
        expect(GENERATOR_SRC).not.toMatch(/"src\/data\/races\.json"/)
        expect(GENERATOR_SRC).not.toMatch(/readFileSync\([^)]*races\.json/)
    })

    it("2 + 3. a same-name race planned in both years keeps distinct canonical turnNumbers", () => {
        // Admire Vega (all-A) plans Sprinters Stakes and Arima Kinen in both of their years.
        const out = run(["--character", "Admire Vega", "--aptitudes", APT]).stdout
        const arima = [...out.matchAll(/raceName: "Arima Kinen", date: "[^"]*", priority: \d+, turnNumber: (\d+)/g)].map((m) => Number(m[1]))
        expect(arima.length).toBe(2)
        expect(new Set(arima).size).toBe(2) // two distinct turns, never collapsed to one
        // and it emits the bare-name collision warning (collision awareness preserved)
        expect(out).toContain('"Arima Kinen" is planned in both its years')
    })

    it("4. a genuine choice objective is shown as a choice (Daiwa Scarlet turn 34)", () => {
        const out = run(["--character", "Daiwa Scarlet", "--aptitudes", "turf=A,dirt=G,sprint=G,mile=A,medium=A,long=A"]).stdout
        expect(out).toMatch(/goals on file:.*t34 Japanese Oaks \/ Tokyo Yushun \(Japanese Derby\)/)
    })

    it("5. the Copano Rickey turn-69 degenerate choice stays truthful (both source options shown, one canonical race)", () => {
        const out = run(["--character", "Copano Rickey", "--aptitudes", "turf=G,dirt=A,sprint=A,mile=A,medium=A,long=A"]).stdout
        expect(out).toMatch(/goals on file:.*t69 JBC Classic \/ JBC Classic/)
    })

    it("6 + 7. valid optional non-objective races are planned, and objective turns are omitted without error", () => {
        const out = run(["--character", "Copano Rickey", "--aptitudes", "turf=G,dirt=A,sprint=A,mile=A,medium=A,long=A"])
        expect(out.code).toBe(0) // omitting the mandatory objectives from the plan is not an error
        // objective turns (e.g. 47 Champions Cup, 69 JBC Classic) must never appear as planned optional entries
        const plannedTurns = [...out.stdout.matchAll(/priority: \d+, turnNumber: (\d+)/g)].map((m) => Number(m[1]))
        for (const objTurn of [31, 47, 52, 57, 60, 67, 69, 72]) expect(plannedTurns).not.toContain(objTurn)
    })

    it("8. the generated plan never conflicts with an objective turn (candidates skip goal turns)", () => {
        const out = run(["--character", "Daiwa Scarlet", "--aptitudes", APT]).stdout
        const objTurns = [29, 31, 34, 44, 45, 54, 68, 72] // Daiwa's objective turns
        const plannedTurns = [...out.matchAll(/priority: \d+, turnNumber: (\d+)/g)].map((m) => Number(m[1]))
        expect(plannedTurns.filter((t) => objTurns.includes(t))).toEqual([])
    })

    it("9. output is deterministic (byte-identical across runs)", () => {
        const a = run(["--character", "Mejiro McQueen", "--aptitudes", "turf=A,dirt=G,sprint=G,mile=E,medium=A,long=A"]).stdout
        const b = run(["--character", "Mejiro McQueen", "--aptitudes", "turf=A,dirt=G,sprint=G,mile=E,medium=A,long=A"]).stdout
        expect(a).toBe(b)
    })

    it("11. running the generator does not mutate the compiled or raw data inputs", () => {
        const paths = ["src/data/compiled/races.json", "src/data/compiled/manifest.json", "src/data/character_objectives.json"]
        const before = paths.map((p) => statSync(join(REPO_ROOT, p)).mtimeMs + ":" + readFileSync(join(REPO_ROOT, p), "utf8").length)
        run(["--character", "Special Week", "--aptitudes", APT])
        const after = paths.map((p) => statSync(join(REPO_ROOT, p)).mtimeMs + ":" + readFileSync(join(REPO_ROOT, p), "utf8").length)
        expect(after).toEqual(before)
    })

    it("12. objective scope is explicitly URA; no Trackblazer/Unity Cup objective claim is made", () => {
        const out = run(["--character", "Special Week", "--aptitudes", APT]).stdout
        expect(out).toContain("(URA)")
        expect(out).not.toMatch(/Trackblazer|Unity Cup/)
    })

    it("10 + edge. --goals override still works and a character absent from objectives without --goals fails cleanly", () => {
        expect(run(["--character", "Nonexistent Uma", "--aptitudes", APT]).code).toBe(1)
        expect(run(["--character", "Nonexistent Uma", "--aptitudes", APT, "--goals", "44,56"]).code).toBe(0)
        expect(run(["--character", "Mejiro McQueen", "--aptitudes", "turf=A,dirt=G,sprint=G,mile=E,medium=A,long=A", "--goals", "42,44,56,60,68"]).code).toBe(0)
    })
})

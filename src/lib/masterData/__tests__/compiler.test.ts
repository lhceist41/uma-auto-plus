import { readFileSync, mkdirSync, writeFileSync, rmSync } from "node:fs"
import { execFileSync } from "node:child_process"
import { join } from "node:path"
import process from "node:process"
import { compileMasterData } from "../compiler.ts"
import { createMasterDataReader } from "../reader.ts"
import type { RawFamily, RawInput, MasterDataManifest } from "../types.ts"

// Jest runs with cwd = repo root (jest.config.js lives there). import.meta is unavailable under the
// babel/Hermes transform, so paths are resolved from cwd instead.
const REPO_ROOT = process.cwd()
const DATA_DIR = join(REPO_ROOT, "src/data")
const HERE = join(REPO_ROOT, "src/lib/masterData/__tests__")

// ---- Synthetic fixture builder ----

const CANONICAL_PATH: Record<RawFamily, string> = {
    skills: "src/data/skills.json",
    races: "src/data/races.json",
    characters: "src/data/characters.json",
    supports: "src/data/supports.json",
    scenarios: "src/data/scenarios.json",
    objectives: "src/data/character_objectives.json",
}

function skill(id: number, over: Record<string, unknown> = {}): Record<string, unknown> {
    return { id, name_en: `Skill${id}`, desc_en: "d", icon_id: 1, cost: 100, eval_pt: 90, condition: "order>=1", precondition: "", inherited: false, community_tier: null, upgrade: null, downgrade: null, ...over }
}
function race(name: string, turnNumber: number, over: Record<string, unknown> = {}): Record<string, unknown> {
    return { name, turnNumber, date: "Junior Class January, First Half", raceTrack: "Tokyo", course: null, direction: "Left", grade: "G1", terrain: "Turf", distanceType: "Mile", distanceMeters: 1600, fans: 5000, nameFormatted: "f", ...over }
}

/** A minimal but fully valid six-file dataset. `over` replaces a family's whole object. */
function six(over: Partial<Record<RawFamily, unknown>> = {}): RawInput[] {
    const base: Record<RawFamily, unknown> = {
        skills: { A: skill(1), B: skill(2, { upgrade: 1 }) },
        // "Marine Cup" appears at two turns => a bare-name collision by design.
        races: { r1: race("Marine Cup", 10), r2: race("Marine Cup", 20), r3: race("Derby", 10, { grade: "G1", distanceType: "Medium", distanceMeters: 2400 }) },
        characters: { C1: { name: "C1" } },
        supports: { S1: { name: "S1" } },
        scenarios: { SC1: { name: "SC1" } },
        objectives: { C1: { name: "C1", mandatoryRaces: [{ turn: 10, isChoice: false, options: [{ raceName: "Marine Cup" }] }] } },
    }
    const merged = { ...base, ...over }
    return (Object.keys(merged) as RawFamily[]).map((family) => ({ family, path: CANONICAL_PATH[family], bytes: JSON.stringify(merged[family]) }))
}

function raw(family: RawFamily, bytes: string): RawInput {
    return { family, path: CANONICAL_PATH[family], bytes }
}

// ---- Baseline ----

describe("compileMasterData - valid baseline", () => {
    it("compiles a valid dataset with the expected identities and exit code", () => {
        const r = compileMasterData(six())
        expect(r.ok).toBe(true)
        expect(r.artifacts).not.toBeNull()
        expect(r.stats.skillCompiledCount).toBe(2)
        expect(r.stats.uniqueSkillIdCount).toBe(2)
        expect(r.stats.raceCompiledCount).toBe(3)
        expect(r.stats.uniqueRaceKeyCount).toBe(3)
        expect(r.stats.distinctBareRaceNameCount).toBe(2) // Marine Cup + Derby
        expect(r.stats.bareNameCollisionCount).toBe(1) // one extra Marine Cup
        expect(r.stats.objectiveReferencesChecked).toBe(1)
        expect(r.stats.objectiveReferencesUnresolved).toBe(0)
        expect(r.exitCode).toBe(0)
        expect(r.manifest?.source).toHaveLength(6)
    })

    it("sorts skills by id and races by (turnNumber, name)", () => {
        const doc = JSON.parse(compileMasterData(six()).artifacts!.skills)
        expect(doc.skills.map((s: { id: number }) => s.id)).toEqual([1, 2])
        const races = JSON.parse(compileMasterData(six()).artifacts!.races).races
        expect(races.map((x: { name: string; turnNumber: number }) => `${x.name}@${x.turnNumber}`)).toEqual(["Derby@10", "Marine Cup@10", "Marine Cup@20"])
    })
})

// ---- Part U: synthetic negative tests ----

describe("compileMasterData - hard errors (no artifact written)", () => {
    const expectHardError = (inputs: RawInput[], code: string) => {
        const r = compileMasterData(inputs)
        expect(r.ok).toBe(false)
        expect(r.artifacts).toBeNull()
        expect(r.exitCode).toBe(2)
        expect(r.errors.map((e) => e.code)).toContain(code)
    }

    it("1. duplicate skill id", () => {
        expectHardError(six({ skills: { A: skill(1), B: skill(1) } }), "skillDuplicateId")
    })
    it("2a. structurally malformed skill chain (non-integer) is a hard error", () => {
        expectHardError(six({ skills: { A: skill(1), B: skill(2, { upgrade: "one" }) } }), "skillChainMalformed")
    })
    it("2b. self-referencing skill chain is a hard error", () => {
        expectHardError(six({ skills: { A: skill(1, { upgrade: 1 }), B: skill(2) } }), "skillChainSelfReference")
    })
    it("3. duplicate race (name, turnNumber)", () => {
        expectHardError(six({ races: { a: race("Dup", 10), b: race("Dup", 10) } }), "raceDuplicateKey")
    })
    it("4. invalid race turn range", () => {
        expectHardError(six({ races: { a: race("X", 0), b: race("Y", 10) } }), "raceInvalidTurn")
    })
    it("5. invalid required race enum", () => {
        expectHardError(six({ races: { a: race("X", 10, { grade: "G9" }) } }), "raceInvalidEnum")
    })
    it("6. unresolved objective race reference", () => {
        expectHardError(six({ objectives: { C1: { name: "C1", mandatoryRaces: [{ turn: 99, isChoice: false, options: [{ raceName: "Marine Cup" }] }] } } }), "objectiveRaceUnresolved")
    })
    it("7a. malformed raw JSON", () => {
        const inputs = six()
        inputs[0] = raw("skills", "{not json")
        expectHardError(inputs, "malformedJson")
    })
    it("7b. unexpected top-level shape (array)", () => {
        const inputs = six()
        inputs[1] = raw("races", "[]")
        expectHardError(inputs, "unexpectedTopLevelShape")
    })
})

describe("compileMasterData - warnings and honest preservation", () => {
    it("8. additive unknown source field -> schema-drift warning (still compiles)", () => {
        const r = compileMasterData(six({ skills: { A: skill(1, { newFangledField: 7 }), B: skill(2) } }))
        expect(r.ok).toBe(true)
        expect(r.validation.warnings.map((w) => w.code)).toContain("schemaDriftUnknownField")
        expect(r.validation.warnings.find((w) => w.code === "schemaDriftUnknownField")?.detail).toContain("newFangledField")
        expect(r.exitCode).toBe(1)
    })
    it("9. large source-count change vs previous manifest -> warning", () => {
        const prev = { source: [{ path: "src/data/skills.json", recordCount: 1000 }] } as unknown as MasterDataManifest
        const r = compileMasterData(six(), { previousManifest: prev })
        expect(r.validation.warnings.map((w) => w.code)).toContain("largeCountChange")
    })
    it("10. nullable race field (course) is preserved as null, never defaulted", () => {
        const doc = JSON.parse(compileMasterData(six()).artifacts!.races).races
        expect(doc.every((x: { course: unknown }) => x.course === null)).toBe(true)
    })
    it("a dangling chain reference to an ABSENT skill id is a warning, not a hard error (real-data behavior)", () => {
        // upgrade: 999 is a valid integer but no skill 999 exists -> source incompleteness, not corruption.
        const r = compileMasterData(six({ skills: { A: skill(1, { upgrade: 999 }), B: skill(2) } }))
        expect(r.ok).toBe(true)
        expect(r.validation.warnings.map((w) => w.code)).toContain("chainReferenceUnresolved")
    })
})

// ---- Part L: determinism ----

describe("compileMasterData - determinism", () => {
    it("14. double compile of identical inputs is byte-identical for all three artifacts", () => {
        const a = compileMasterData(six()).artifacts!
        const b = compileMasterData(six()).artifacts!
        expect(a.skills).toBe(b.skills)
        expect(a.races).toBe(b.races)
        expect(a.manifest).toBe(b.manifest)
        expect(compileMasterData(six()).fingerprint).toBe(compileMasterData(six()).fingerprint)
    })
    it("manifest contains no wall-clock timestamp field", () => {
        const m = compileMasterData(six()).manifest as unknown as Record<string, unknown>
        expect(JSON.stringify(m)).not.toMatch(/\bgeneratedAt\b|\btimestamp\b/)
    })
})

// ---- Part M + S: CLI safe-write and --check (integration via the real script) ----

describe("compile-master-data CLI", () => {
    const SCRIPT = join(REPO_ROOT, "scripts/compile-master-data.mjs")
    const TMP = join(HERE, "__cli_tmp__")
    const runCli = (args: string[]): { code: number; out: string } => {
        try {
            const out = execFileSync("node", [SCRIPT, ...args], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] })
            return { code: 0, out }
        } catch (e: unknown) {
            const err = e as { status?: number; stdout?: string; stderr?: string }
            return { code: err.status ?? -1, out: (err.stdout ?? "") + (err.stderr ?? "") }
        }
    }
    beforeEach(() => {
        rmSync(TMP, { recursive: true, force: true })
        mkdirSync(join(TMP, "in"), { recursive: true })
        mkdirSync(join(TMP, "out"), { recursive: true })
    })
    afterAll(() => rmSync(TMP, { recursive: true, force: true }))

    const writeSix = (dir: string, inputs: RawInput[]) => {
        const nameByFamily: Record<RawFamily, string> = { skills: "skills.json", races: "races.json", characters: "characters.json", supports: "supports.json", scenarios: "scenarios.json", objectives: "character_objectives.json" }
        for (const i of inputs) writeFileSync(join(dir, nameByFamily[i.family]), i.bytes, "utf8")
    }

    it("11. a failed compile leaves pre-existing compiled outputs untouched", () => {
        writeSix(join(TMP, "in"), six({ skills: { A: skill(1), B: skill(1) } })) // duplicate id -> hard error
        const sentinel = "SENTINEL-DO-NOT-OVERWRITE"
        for (const f of ["skills.json", "races.json", "manifest.json"]) writeFileSync(join(TMP, "out", f), sentinel, "utf8")
        const { code } = runCli(["--input-dir", join(TMP, "in"), "--output-dir", join(TMP, "out")])
        expect(code).toBe(2)
        for (const f of ["skills.json", "races.json", "manifest.json"]) expect(readFileSync(join(TMP, "out", f), "utf8")).toBe(sentinel)
    })

    it("15. --check is clean immediately after a successful compile", () => {
        writeSix(join(TMP, "in"), six())
        expect(runCli(["--input-dir", join(TMP, "in"), "--output-dir", join(TMP, "out")]).code).toBe(0)
        expect(runCli(["--input-dir", join(TMP, "in"), "--output-dir", join(TMP, "out"), "--check"]).code).toBe(0)
    })

    it("16. --check reports stale (exit 3) without rewriting when source changed", () => {
        writeSix(join(TMP, "in"), six())
        runCli(["--input-dir", join(TMP, "in"), "--output-dir", join(TMP, "out")])
        const before = readFileSync(join(TMP, "out", "skills.json"), "utf8")
        // Change the source so a recompile would differ.
        writeSix(join(TMP, "in"), six({ skills: { A: skill(1), B: skill(2), C: skill(3) } }))
        const { code } = runCli(["--input-dir", join(TMP, "in"), "--output-dir", join(TMP, "out"), "--check"])
        expect(code).toBe(3)
        expect(readFileSync(join(TMP, "out", "skills.json"), "utf8")).toBe(before) // not rewritten
    })
})

// ---- Part V: golden real-data tests (architecture-protecting, plus a labelled snapshot) ----

describe("real committed dataset", () => {
    const RAW = ["skills", "races", "characters", "supports", "scenarios", "objectives"] as const
    const FILE: Record<string, string> = { skills: "skills.json", races: "races.json", characters: "characters.json", supports: "supports.json", scenarios: "scenarios.json", objectives: "character_objectives.json" }
    const inputs = (): RawInput[] => RAW.map((family) => ({ family, path: CANONICAL_PATH[family], bytes: readFileSync(join(DATA_DIR, FILE[family]), "utf8") }))

    it("compiles cleanly (only explained source-incompleteness warnings) with all six sources in the manifest", () => {
        const r = compileMasterData(inputs())
        expect(r.ok).toBe(true)
        expect(r.manifest?.source.map((s) => s.family).sort()).toEqual(["characters", "objectives", "races", "scenarios", "skills", "supports"])
        // The only warnings on real data are the known dangling downgrade chain references.
        expect(new Set(r.validation.warnings.map((w) => w.code))).toEqual(new Set(["chainReferenceUnresolved"]))
    })

    it("skill IDs are unique and race composite keys are unique (architecture invariants)", () => {
        const r = compileMasterData(inputs())
        expect(r.stats.uniqueSkillIdCount).toBe(r.stats.skillCompiledCount)
        expect(r.stats.uniqueRaceKeyCount).toBe(r.stats.raceCompiledCount)
        expect(r.stats.objectiveReferencesUnresolved).toBe(0)
    })

    it("bare-name collisions exist and the reader returns every colliding race", () => {
        const r = compileMasterData(inputs())
        expect(r.stats.bareNameCollisionCount).toBeGreaterThan(0)
        const reader = createMasterDataReader(r.artifacts!)
        const collidingName = reader.races.find((race) => reader.racesByName(race.name).length > 1)!.name
        expect(reader.racesByName(collidingName).length).toBeGreaterThan(1)
    })

    it("compiled artifacts hash-verify through the reader", () => {
        const r = compileMasterData(inputs())
        const reader = createMasterDataReader(r.artifacts!) // throws on any hash/version mismatch
        expect(reader.fingerprint).toBe(r.fingerprint)
    })

    it("current snapshot counts (update intentionally when the scrape changes)", () => {
        const r = compileMasterData(inputs())
        expect(r.stats.skillCompiledCount).toBe(694)
        expect(r.stats.uniqueSkillIdCount).toBe(694)
        expect(r.stats.raceCompiledCount).toBe(402)
        expect(r.stats.uniqueRaceKeyCount).toBe(402)
        expect(r.stats.distinctBareRaceNameCount).toBe(296)
        expect(r.stats.bareNameCollisionCount).toBe(106)
        expect(r.stats.objectiveReferencesChecked).toBe(494)
    })

    // Part D: pin the exact unresolved-chain reference set so a third dangling ref cannot pass silently
    // merely by sharing the warning class. Refs are extracted structurally from the deterministic detail.
    it("the unresolved-chain warning set is exactly the two known dangling downgrades", () => {
        const r = compileMasterData(inputs())
        const refs = r.validation.warnings
            .filter((w) => w.code === "chainReferenceUnresolved")
            .map((w) => {
                const m = w.detail.match(/skill (\d+) (\w+) -> (\d+)/)
                if (!m) throw new Error(`unexpected chain-warning detail: ${w.detail}`)
                return `${m[1]}/${m[2]}/${m[3]}`
            })
            .sort()
        expect(refs).toEqual(["201102/downgrade/201103", "201211/downgrade/201212"])
        // They stay warnings, and the real compile still succeeds with warning status.
        expect(r.ok).toBe(true)
        expect(r.exitCode).toBe(1)
    })
})

// Part B + C: U+2014 escaping is textual only, and no generated artifact carries a literal U+2014.
describe("U+2014 escaping in generated artifacts", () => {
    const EM_DASH = String.fromCharCode(0x2014)
    const RAW = ["skills", "races", "characters", "supports", "scenarios", "objectives"] as const
    const FILE: Record<string, string> = { skills: "skills.json", races: "races.json", characters: "characters.json", supports: "supports.json", scenarios: "scenarios.json", objectives: "character_objectives.json" }
    const inputs = (): RawInput[] => RAW.map((family) => ({ family, path: `src/data/${FILE[family]}`, bytes: readFileSync(join(DATA_DIR, FILE[family]), "utf8") }))

    it("Part C: no generated artifact (skills/races/manifest) contains a literal U+2014", () => {
        const a = compileMasterData(inputs()).artifacts!
        expect(a.skills.includes(EM_DASH)).toBe(false)
        expect(a.races.includes(EM_DASH)).toBe(false)
        expect(a.manifest.includes(EM_DASH)).toBe(false)
        // The escape is present in text form where the source had an em dash.
        expect(a.skills.includes("\\u2014")).toBe(true)
    })

    it("Part B: decoded compiled skill names equal the raw names exactly, em dash preserved", () => {
        const rawSkills = JSON.parse(readFileSync(join(DATA_DIR, "skills.json"), "utf8")) as Record<string, { id: number; name_en: string }>
        const compiled = JSON.parse(compileMasterData(inputs()).artifacts!.skills).skills as { id: number; name: string }[]
        // Both raw names that carry an em dash.
        for (const id of [101041, 100441]) {
            const rawName = Object.values(rawSkills).find((s) => s.id === id)!.name_en
            const compName = compiled.find((s) => s.id === id)!.name
            expect(rawName.includes(EM_DASH)).toBe(true) // raw genuinely has the code point
            expect(compName).toBe(rawName) // decoded value identical, not an ASCII-hyphen substitute
            expect(compName.includes(EM_DASH)).toBe(true)
        }
    })

    it("a synthetic em-dash name round-trips through the escape unchanged", () => {
        const name = `Alpha${EM_DASH}Beta`
        const raw: RawInput[] = inputs().map((i) => (i.family === "skills" ? { ...i, bytes: JSON.stringify({ X: { id: 1, name_en: name, cost: 10, eval_pt: 5, condition: "", precondition: "", inherited: false, community_tier: null, upgrade: null, downgrade: null } }) } : i))
        const art = compileMasterData(raw).artifacts!.skills
        expect(art.includes(EM_DASH)).toBe(false)
        expect(JSON.parse(art).skills[0].name).toBe(name)
    })
})

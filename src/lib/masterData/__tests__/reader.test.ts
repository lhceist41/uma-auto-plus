import { createHash } from "node:crypto"
import { compileMasterData } from "../compiler.ts"
import { createMasterDataReader, MasterDataReaderError } from "../reader.ts"
import type { RawFamily, RawInput } from "../types.ts"

const sha = (s: string): string => createHash("sha256").update(s, "utf8").digest("hex")
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
    return { name, turnNumber, date: "d", raceTrack: "Tokyo", course: null, direction: "Left", grade: "G1", terrain: "Turf", distanceType: "Mile", distanceMeters: 1600, fans: 5000, nameFormatted: "f", ...over }
}
function six(over: Partial<Record<RawFamily, unknown>> = {}): RawInput[] {
    const base: Record<RawFamily, unknown> = {
        skills: { A: skill(1), B: skill(2) },
        races: { r1: race("Marine Cup", 10), r2: race("Marine Cup", 20), r3: race("Derby", 34, { distanceType: "Medium", distanceMeters: 2400 }) },
        characters: { C1: { name: "C1" } },
        supports: { S1: { name: "S1" } },
        scenarios: { SC1: { name: "SC1" } },
        objectives: {},
    }
    const merged = { ...base, ...over }
    return (Object.keys(merged) as RawFamily[]).map((family) => ({ family, path: CANONICAL_PATH[family], bytes: JSON.stringify(merged[family]) }))
}

/** Builds compiled-source strings with correct hashes, so a specific failure can be injected in isolation. */
function sources(skillsDoc: unknown[], racesDoc: unknown[], opts: { skillsVersion?: number; racesVersion?: number; manifestVersion?: number; skillsRaw?: string; breakSkillsHash?: boolean; dropRacesEntry?: boolean } = {}): { manifest: string; skills: string; races: string } {
    const skills = opts.skillsRaw ?? JSON.stringify({ schemaVersion: opts.skillsVersion ?? 1, skills: skillsDoc }, null, 2) + "\n"
    const races = JSON.stringify({ schemaVersion: opts.racesVersion ?? 1, races: racesDoc }, null, 2) + "\n"
    const compiled = [{ path: "src/data/compiled/skills.json", sha256: opts.breakSkillsHash ? sha("wrong") : sha(skills), recordCount: skillsDoc.length }]
    if (!opts.dropRacesEntry) compiled.push({ path: "src/data/compiled/races.json", sha256: sha(races), recordCount: racesDoc.length })
    const manifest = JSON.stringify({ manifestSchemaVersion: opts.manifestVersion ?? 1, fingerprint: "fp-test", compiled }, null, 2) + "\n"
    return { manifest, skills, races }
}

describe("createMasterDataReader - success", () => {
    const built = compileMasterData(six())
    const reader = createMasterDataReader(built.artifacts!)

    it("hash-verifies and exposes the fingerprint", () => {
        expect(reader.fingerprint).toBe(built.fingerprint)
        expect(reader.skills).toHaveLength(2)
        expect(reader.races).toHaveLength(3)
    })
    it("skillById returns the record or undefined", () => {
        expect(reader.skillById(1)?.name).toBe("Skill1")
        expect(reader.skillById(404)).toBeUndefined()
    })
    it("raceByKey uses the composite key", () => {
        expect(reader.raceByKey("Marine Cup", 10)?.turnNumber).toBe(10)
        expect(reader.raceByKey("Marine Cup", 999)).toBeUndefined()
    })
    it("13. racesByName returns ALL colliding races, empty for none", () => {
        expect(reader.racesByName("Marine Cup").map((r) => r.turnNumber).sort()).toEqual([10, 20])
        expect(reader.racesByName("Derby")).toHaveLength(1)
        expect(reader.racesByName("Nope")).toEqual([])
    })
})

describe("createMasterDataReader - Part W immutability", () => {
    const reader = createMasterDataReader(compileMasterData(six()).artifacts!)
    it("returned records and arrays are frozen; a mutation cannot change later lookups", () => {
        expect(Object.isFrozen(reader.skills)).toBe(true)
        expect(Object.isFrozen(reader.skillById(1))).toBe(true)
        try {
            ;(reader.skillById(1) as { name: string }).name = "HACKED"
        } catch {
            /* strict-mode throw is acceptable */
        }
        expect(reader.skillById(1)?.name).toBe("Skill1")
        try {
            ;(reader.racesByName("Marine Cup") as unknown as unknown[]).push({})
        } catch {
            /* frozen array */
        }
        expect(reader.racesByName("Marine Cup")).toHaveLength(2)
    })
})

describe("createMasterDataReader - Part P failure behavior", () => {
    const throwsCode = (fn: () => unknown, code: string) => {
        try {
            fn()
            throw new Error("expected a MasterDataReaderError")
        } catch (e) {
            expect(e).toBeInstanceOf(MasterDataReaderError)
            expect((e as MasterDataReaderError).code).toBe(code)
        }
    }

    it("12. artifact hash mismatch", () => {
        throwsCode(() => createMasterDataReader(sources([skill(1)], [race("R", 10)], { breakSkillsHash: true })), "artifactHashMismatch")
    })
    it("unsupported manifest version", () => {
        throwsCode(() => createMasterDataReader(sources([skill(1)], [race("R", 10)], { manifestVersion: 99 })), "unsupportedManifestVersion")
    })
    it("unsupported skills schema version", () => {
        throwsCode(() => createMasterDataReader(sources([skill(1)], [race("R", 10)], { skillsVersion: 99 })), "unsupportedSkillsVersion")
    })
    it("duplicate canonical skill id in the artifact", () => {
        throwsCode(() => createMasterDataReader(sources([skill(1), skill(1)], [race("R", 10)])), "duplicateSkillId")
    })
    it("missing compiled artifact entry in the manifest", () => {
        throwsCode(() => createMasterDataReader(sources([skill(1)], [race("R", 10)], { dropRacesEntry: true })), "missingArtifactEntry")
    })
    it("malformed compiled JSON (with a matching hash) still fails, never falls back to raw", () => {
        throwsCode(() => createMasterDataReader(sources([], [race("R", 10)], { skillsRaw: "{not json" })), "malformedCompiledJson")
    })
})

// Local Master-Data Compiler v1 - typed, read-only reader.
//
// Loads the compiled manifest + skills + races, verifies each artifact's SHA256 against the manifest,
// checks schema versions, and exposes deterministic lookups. It never falls back to the raw layer (that
// would create a second runtime authority) and never mutates loaded data (records are frozen).

import { createHash } from "node:crypto"
import { readFileSync } from "node:fs"
import { join } from "node:path"
import { MANIFEST_SCHEMA_VERSION, SKILLS_SCHEMA_VERSION, RACES_SCHEMA_VERSION } from "./types.ts"
import type { CompiledSkill, CompiledRace, MasterDataManifest } from "./types.ts"

/** A deterministic reader failure. Distinct `code` values let callers branch without string matching. */
export class MasterDataReaderError extends Error {
    // Explicit field (not a constructor parameter property) so node's strip-only TS loader accepts this.
    readonly code: string
    constructor(code: string, message: string) {
        super(message)
        this.name = "MasterDataReaderError"
        this.code = code
    }
}

/** The three compiled artifact contents (exact bytes), as loaded from disk or supplied by a test. */
export interface CompiledSources {
    manifest: string
    skills: string
    races: string
}

/** The read-only reader surface. Returned records/arrays are frozen. */
export interface MasterDataReader {
    readonly manifest: MasterDataManifest
    readonly fingerprint: string
    /** All compiled skills, frozen, in canonical (id-ascending) order. */
    readonly skills: readonly CompiledSkill[]
    /** All compiled races, frozen, in canonical order. */
    readonly races: readonly CompiledRace[]
    /** Skill by numeric id, or undefined. */
    skillById(id: number): CompiledSkill | undefined
    /** Race by the canonical composite key, or undefined. */
    raceByKey(name: string, turnNumber: number): CompiledRace | undefined
    /** ALL races sharing a bare name (bare names collide by design); empty array when none. */
    racesByName(name: string): readonly CompiledRace[]
}

function sha256(text: string): string {
    return createHash("sha256").update(text, "utf8").digest("hex")
}

function raceKeyString(name: string, turnNumber: number): string {
    return JSON.stringify([name, turnNumber])
}

function parseOrThrow(text: string, artifact: string): unknown {
    try {
        return JSON.parse(text)
    } catch (e) {
        throw new MasterDataReaderError("malformedCompiledJson", `${artifact}: ${e instanceof Error ? e.message : String(e)}`)
    }
}

/**
 * Builds a reader from the three artifact contents. Verifies schema versions and both artifact hashes
 * against the manifest before exposing any data. Throws {@link MasterDataReaderError} on any mismatch.
 */
export function createMasterDataReader(sources: CompiledSources): MasterDataReader {
    const manifest = parseOrThrow(sources.manifest, "manifest.json") as MasterDataManifest
    if (manifest?.manifestSchemaVersion !== MANIFEST_SCHEMA_VERSION) {
        throw new MasterDataReaderError("unsupportedManifestVersion", `manifest schema version ${manifest?.manifestSchemaVersion} != supported ${MANIFEST_SCHEMA_VERSION}`)
    }

    // Verify each compiled artifact's bytes against the manifest hash before trusting its content.
    const expected = new Map((manifest.compiled ?? []).map((c) => [c.path, c.sha256]))
    verifyArtifactHash(expected, "src/data/compiled/skills.json", sources.skills)
    verifyArtifactHash(expected, "src/data/compiled/races.json", sources.races)

    const skillsDoc = parseOrThrow(sources.skills, "skills.json") as { schemaVersion?: number; skills?: CompiledSkill[] }
    if (skillsDoc?.schemaVersion !== SKILLS_SCHEMA_VERSION) {
        throw new MasterDataReaderError("unsupportedSkillsVersion", `skills schema version ${skillsDoc?.schemaVersion} != supported ${SKILLS_SCHEMA_VERSION}`)
    }
    const racesDoc = parseOrThrow(sources.races, "races.json") as { schemaVersion?: number; races?: CompiledRace[] }
    if (racesDoc?.schemaVersion !== RACES_SCHEMA_VERSION) {
        throw new MasterDataReaderError("unsupportedRacesVersion", `races schema version ${racesDoc?.schemaVersion} != supported ${RACES_SCHEMA_VERSION}`)
    }

    const skills = skillsDoc.skills ?? []
    const races = racesDoc.races ?? []

    // Build lookups, rejecting a duplicate canonical key in the artifact (an integrity failure).
    const byId = new Map<number, CompiledSkill>()
    for (const s of skills) {
        if (byId.has(s.id)) throw new MasterDataReaderError("duplicateSkillId", `compiled skills carry duplicate id ${s.id}`)
        byId.set(s.id, Object.freeze(s))
    }
    const byKey = new Map<string, CompiledRace>()
    const byName = new Map<string, CompiledRace[]>()
    for (const r of races) {
        const k = raceKeyString(r.name, r.turnNumber)
        if (byKey.has(k)) throw new MasterDataReaderError("duplicateRaceKey", `compiled races carry duplicate key (${r.name}, ${r.turnNumber})`)
        Object.freeze(r.key)
        Object.freeze(r)
        byKey.set(k, r)
        const list = byName.get(r.name)
        if (list) list.push(r)
        else byName.set(r.name, [r])
    }

    const frozenSkills = Object.freeze(skills.slice())
    const frozenRaces = Object.freeze(races.slice())
    for (const list of byName.values()) Object.freeze(list)

    return {
        manifest: Object.freeze(manifest),
        fingerprint: manifest.fingerprint,
        skills: frozenSkills,
        races: frozenRaces,
        skillById: (id) => byId.get(id),
        raceByKey: (name, turnNumber) => byKey.get(raceKeyString(name, turnNumber)),
        racesByName: (name) => byName.get(name) ?? [],
    }
}

function verifyArtifactHash(expected: Map<string, string>, path: string, content: string): void {
    const want = expected.get(path)
    if (want === undefined) throw new MasterDataReaderError("missingArtifactEntry", `manifest has no compiled entry for ${path}`)
    const got = sha256(content)
    if (got !== want) throw new MasterDataReaderError("artifactHashMismatch", `${path} sha256 ${got} != manifest ${want}`)
}

/**
 * Convenience fs loader for the CLI and real use. Reads the three artifacts from a compiled directory and
 * builds a verified reader. A missing artifact throws a deterministic {@link MasterDataReaderError}.
 */
export function loadMasterDataFromDir(compiledDir: string): MasterDataReader {
    const read = (name: string): string => {
        try {
            return readFileSync(join(compiledDir, name), "utf8")
        } catch (e) {
            throw new MasterDataReaderError("missingArtifact", `cannot read ${name} from ${compiledDir}: ${e instanceof Error ? e.message : String(e)}`)
        }
    }
    return createMasterDataReader({ manifest: read("manifest.json"), skills: read("skills.json"), races: read("races.json") })
}

// Local Master-Data Compiler v1 - typed, read-only reader.
//
// Loads the compiled manifest + skills + races, verifies each artifact's SHA256 against the manifest,
// checks schema versions, and exposes deterministic lookups. It never falls back to the raw layer (that
// would create a second runtime authority) and never mutates loaded data (records are frozen). The
// filesystem convenience loader lives in reader.node.ts so this module stays importable from the app.

import { canonicalJson, sha256Hex } from "./digest.ts"
import { MANIFEST_SCHEMA_VERSION, SKILLS_SCHEMA_VERSION, RACES_SCHEMA_VERSION } from "./types.ts"
import type { CompiledSkill, CompiledRace, MasterDataManifest } from "./types.ts"

const SKILLS_ARTIFACT_PATH = "src/data/compiled/skills.json"
const RACES_ARTIFACT_PATH = "src/data/compiled/races.json"

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

/** The verified race half of the compiled layer: everything a race catalog needs. */
export interface RaceSource {
    readonly fingerprint: string
    /** All compiled races, frozen, in canonical order. */
    readonly races: readonly CompiledRace[]
    /** Race by the canonical composite key, or undefined. */
    raceByKey(name: string, turnNumber: number): CompiledRace | undefined
    /** ALL races sharing a bare name (bare names collide by design); empty array when none. */
    racesByName(name: string): readonly CompiledRace[]
}

/** The read-only reader surface. Returned records/arrays are frozen. */
export interface MasterDataReader extends RaceSource {
    readonly manifest: MasterDataManifest
    /** All compiled skills, frozen, in canonical (id-ascending) order. */
    readonly skills: readonly CompiledSkill[]
    /** Skill by numeric id, or undefined. */
    skillById(id: number): CompiledSkill | undefined
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

function readManifest(text: string): MasterDataManifest {
    const manifest = parseOrThrow(text, "manifest.json") as MasterDataManifest
    if (manifest?.manifestSchemaVersion !== MANIFEST_SCHEMA_VERSION) {
        throw new MasterDataReaderError("unsupportedManifestVersion", `manifest schema version ${manifest?.manifestSchemaVersion} != supported ${MANIFEST_SCHEMA_VERSION}`)
    }
    return manifest
}

function compiledHashes(manifest: MasterDataManifest): Map<string, string> {
    return new Map((manifest.compiled ?? []).map((c) => [c.path, c.sha256]))
}

function verifyArtifactHash(expected: Map<string, string>, path: string, content: string): void {
    const want = expected.get(path)
    if (want === undefined) throw new MasterDataReaderError("missingArtifactEntry", `manifest has no compiled entry for ${path}`)
    const got = sha256Hex(content)
    if (got !== want) throw new MasterDataReaderError("artifactHashMismatch", `${path} sha256 ${got} != manifest ${want}`)
}

function readRaces(text: string): CompiledRace[] {
    const doc = parseOrThrow(text, "races.json") as { schemaVersion?: number; races?: CompiledRace[] }
    if (doc?.schemaVersion !== RACES_SCHEMA_VERSION) {
        throw new MasterDataReaderError("unsupportedRacesVersion", `races schema version ${doc?.schemaVersion} != supported ${RACES_SCHEMA_VERSION}`)
    }
    return doc.races ?? []
}

/** Freezes the race records and builds the canonical lookups, rejecting a duplicate key in the artifact. */
function buildRaceSource(fingerprint: string, races: CompiledRace[]): RaceSource {
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
    for (const list of byName.values()) Object.freeze(list)
    const frozenRaces = Object.freeze(races.slice())
    return {
        fingerprint,
        races: frozenRaces,
        raceByKey: (name, turnNumber) => byKey.get(raceKeyString(name, turnNumber)),
        racesByName: (name) => byName.get(name) ?? [],
    }
}

/**
 * Builds a reader from the three artifact contents. Verifies schema versions and both artifact hashes
 * against the manifest before exposing any data. Throws {@link MasterDataReaderError} on any mismatch.
 */
export function createMasterDataReader(sources: CompiledSources): MasterDataReader {
    const manifest = readManifest(sources.manifest)

    // Verify each compiled artifact's bytes against the manifest hash before trusting its content.
    const expected = compiledHashes(manifest)
    verifyArtifactHash(expected, SKILLS_ARTIFACT_PATH, sources.skills)
    verifyArtifactHash(expected, RACES_ARTIFACT_PATH, sources.races)

    const skillsDoc = parseOrThrow(sources.skills, "skills.json") as { schemaVersion?: number; skills?: CompiledSkill[] }
    if (skillsDoc?.schemaVersion !== SKILLS_SCHEMA_VERSION) {
        throw new MasterDataReaderError("unsupportedSkillsVersion", `skills schema version ${skillsDoc?.schemaVersion} != supported ${SKILLS_SCHEMA_VERSION}`)
    }
    const races = readRaces(sources.races)
    const skills = skillsDoc.skills ?? []

    const byId = new Map<number, CompiledSkill>()
    for (const s of skills) {
        if (byId.has(s.id)) throw new MasterDataReaderError("duplicateSkillId", `compiled skills carry duplicate id ${s.id}`)
        byId.set(s.id, Object.freeze(s))
    }
    const frozenSkills = Object.freeze(skills.slice())

    return {
        ...buildRaceSource(manifest.fingerprint, races),
        manifest: Object.freeze(manifest),
        skills: frozenSkills,
        skillById: (id) => byId.get(id),
    }
}

/**
 * Builds a verified race source from already-parsed compiled documents. The app bundles the artifacts as
 * parsed JSON rather than text, so the documents are re-serialized with the compiler's canonical writer and
 * the races artifact is hash-checked against the same manifest entry {@link createMasterDataReader} checks.
 * Skills are not read here, so nothing is claimed about them.
 */
export function createRaceSourceFromDocuments(manifestDoc: unknown, racesDoc: unknown): RaceSource {
    const manifest = readManifest(canonicalJson(manifestDoc))
    const racesText = canonicalJson(racesDoc)
    verifyArtifactHash(compiledHashes(manifest), RACES_ARTIFACT_PATH, racesText)
    return buildRaceSource(manifest.fingerprint, readRaces(racesText))
}

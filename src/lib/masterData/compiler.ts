// Local Master-Data Compiler v1 - pure, deterministic compile logic.
//
// Given the six raw `src/data/*.json` files (as exact byte strings), this produces canonical compiled
// skills + races artifacts and a provenance manifest, plus a validation report. It never reads or writes
// files, never reaches the network, and never mutates its inputs; the CLI wraps it with fs I/O.
//
// Determinism: compiled records are sorted by their canonical identity, JSON is stringified with a fixed
// 2-space indent and trailing newline, every object is built in a fixed key order, and findings are
// sorted. No wall-clock time, absolute path, or random value enters the output. The same raw bytes plus
// the same compiler version yield byte-identical artifacts and an identical fingerprint on any machine.

import { createHash } from "node:crypto"
import {
    COMPILER_VERSION,
    NORMALIZATION_VERSION,
    MANIFEST_SCHEMA_VERSION,
    SKILLS_SCHEMA_VERSION,
    RACES_SCHEMA_VERSION,
    EXIT_CLEAN,
    EXIT_WARNINGS,
    EXIT_VALIDATION,
} from "./types.ts"
import type { RawInput, RawFamily, CompiledSkill, CompiledRace, RaceFanPayout, MasterDataManifest, RawSourceEntry, CompiledArtifactEntry, ValidationFinding, CompileError, CompileResult } from "./types.ts"

const SKILLS_ARTIFACT_PATH = "src/data/compiled/skills.json"
const RACES_ARTIFACT_PATH = "src/data/compiled/races.json"

/** Closed race enum domains, from the proven current dataset. A value outside these is a hard error. */
const RACE_GRADES = new Set(["G1", "G2", "G3", "OP", "Pre-OP"])
const RACE_TERRAINS = new Set(["Turf", "Dirt"])
const RACE_DIRECTIONS = new Set(["Left", "Right"])
const RACE_DISTANCE_TYPES = new Set(["Sprint", "Mile", "Medium", "Long"])
const RACE_COURSES = new Set(["Inner", "Outer"]) // plus null

/** Generous sane bound for a career turn number (current data spans 14..72); outside this is a hard error. */
const MAX_TURN_NUMBER = 120

/** Known raw keys per compiled family, for additive schema-drift detection (Part J). */
const KNOWN_SKILL_KEYS = new Set(["id", "name_en", "desc_en", "icon_id", "cost", "eval_pt", "condition", "precondition", "inherited", "community_tier", "upgrade", "downgrade"])
const KNOWN_RACE_KEYS = new Set(["name", "date", "raceTrack", "course", "direction", "grade", "terrain", "distanceType", "distanceMeters", "fans", "fanPayoutsByPlace", "turnNumber", "nameFormatted"])

/** Large-change thresholds vs a previous manifest (Part K): warn past 20% or 10 rows, whichever is larger. */
const LARGE_CHANGE_ABS = 10
const LARGE_CHANGE_PCT = 0.2

// ---- Small helpers ----

function sha256(text: string): string {
    return createHash("sha256").update(text, "utf8").digest("hex")
}

/**
 * Deterministic JSON: fixed 2-space indent, trailing newline, key order taken from construction order.
 *
 * The U+2014 (em dash) code point is re-emitted as the six-character JSON escape (backslash-u-2014). This
 * is purely textual: JSON.stringify only ever places that code point inside a string value (every
 * structural token is ASCII), and the escape decodes back to the identical code point, so JSON.parse
 * yields exactly the same string. The repository forbids literal U+2014 in newly added files (AGENTS.md
 * section 7); a compiled artifact is a new tracked file, and skill/race source text legitimately contains
 * em dashes (game skill names), so escaping keeps the canonical value intact while producing a committable
 * artifact. Applies to every artifact this serializer emits, so future em-dash source is handled too.
 */
function canonicalJson(value: unknown): string {
    return JSON.stringify(value, null, 2).split(String.fromCharCode(0x2014)).join("\\u2014") + "\n"
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isInt(value: unknown): value is number {
    return typeof value === "number" && Number.isInteger(value)
}

function asStringOrNull(value: unknown): string | null {
    return typeof value === "string" ? value : null
}

function asIntOrNull(value: unknown): number | null {
    return isInt(value) ? value : null
}

/** Sorts findings by (code, detail) so the manifest's arrays are order-independent of discovery. */
function sortFindings(findings: ValidationFinding[]): ValidationFinding[] {
    return [...findings].sort((a, b) => (a.code < b.code ? -1 : a.code > b.code ? 1 : a.detail < b.detail ? -1 : a.detail > b.detail ? 1 : 0))
}

// ---- Public entry ----

export interface CompileOptions {
    /** A previously-compiled manifest, if one exists, to enable large-change detection. */
    previousManifest?: MasterDataManifest | null
}

/**
 * Compiles the six raw inputs into canonical artifacts + manifest. On any hard error, returns
 * `{ ok: false, artifacts: null }` so the caller writes nothing.
 */
export function compileMasterData(inputs: RawInput[], options: CompileOptions = {}): CompileResult {
    const errors: CompileError[] = []
    const warnings: ValidationFinding[] = []
    const info: ValidationFinding[] = []

    // Index inputs by family and require all six exactly once.
    const byFamily = new Map<RawFamily, RawInput>()
    for (const input of inputs) {
        if (byFamily.has(input.family)) errors.push({ code: "duplicateRawFamily", detail: `family ${input.family} supplied more than once` })
        byFamily.set(input.family, input)
    }
    const required: RawFamily[] = ["skills", "races", "characters", "supports", "scenarios", "objectives"]
    for (const family of required) {
        if (!byFamily.has(family)) errors.push({ code: "missingRawFamily", detail: `required raw family ${family} was not supplied` })
    }
    if (errors.length > 0) return fail(errors, warnings, info)

    // Parse every file; a malformed file or unexpected top-level shape is a hard error (never dropped).
    const parsed = new Map<RawFamily, Record<string, unknown>>()
    for (const family of required) {
        const input = byFamily.get(family) as RawInput
        let value: unknown
        try {
            value = JSON.parse(input.bytes)
        } catch (e) {
            errors.push({ code: "malformedJson", detail: `${input.path}: ${e instanceof Error ? e.message : String(e)}` })
            continue
        }
        if (!isObject(value)) {
            errors.push({ code: "unexpectedTopLevelShape", detail: `${input.path}: expected a JSON object map, got ${Array.isArray(value) ? "array" : typeof value}` })
            continue
        }
        parsed.set(family, value)
    }
    if (errors.length > 0) return fail(errors, warnings, info)

    // Skills.
    const skills = compileSkills(parsed.get("skills") as Record<string, unknown>, errors, warnings, info)
    // Races.
    const races = compileRaces(parsed.get("races") as Record<string, unknown>, errors, warnings, info)
    // Cross-file: objective race references resolve against the compiled race keys.
    const raceKeySet = new Set(races.map((r) => raceKeyString(r.name, r.turnNumber)))
    const objStats = validateObjectives(parsed.get("objectives") as Record<string, unknown>, raceKeySet, errors)
    // Conservative shape checks for the non-compiled families.
    validateConservative("characters", parsed.get("characters") as Record<string, unknown>, errors)
    validateConservative("supports", parsed.get("supports") as Record<string, unknown>, errors)
    validateConservative("scenarios", parsed.get("scenarios") as Record<string, unknown>, errors)

    // Large-change detection vs a prior manifest (warning only).
    if (options.previousManifest) detectLargeChanges(options.previousManifest, byFamily, parsed, warnings)

    // Informational: the raw layer records no upstream source version.
    info.push({ code: "provenanceUnavailable", detail: "raw source files record no upstream version/patch/scrape-time; manifest provenance fields are explicit null" })

    if (errors.length > 0) return fail(errors, warnings, info)

    // Build artifacts (all inputs validated; safe to serialize).
    const skillsStr = canonicalJson({ schemaVersion: SKILLS_SCHEMA_VERSION, skills })
    const racesStr = canonicalJson({ schemaVersion: RACES_SCHEMA_VERSION, races })

    const source = buildSourceEntries(byFamily, parsed)
    const compiled: CompiledArtifactEntry[] = [
        { path: SKILLS_ARTIFACT_PATH, sha256: sha256(skillsStr), recordCount: skills.length },
        { path: RACES_ARTIFACT_PATH, sha256: sha256(racesStr), recordCount: races.length },
    ].sort((a, b) => (a.path < b.path ? -1 : 1))

    const fingerprint = computeFingerprint(source, compiled)

    const manifest: MasterDataManifest = {
        manifestSchemaVersion: MANIFEST_SCHEMA_VERSION,
        compiler: { version: COMPILER_VERSION, normalizationVersion: NORMALIZATION_VERSION, skillsSchemaVersion: SKILLS_SCHEMA_VERSION, racesSchemaVersion: RACES_SCHEMA_VERSION },
        fingerprint,
        knownProvenance: { generator: "scripts/data-scraper", recordedSourceVersion: null, recordedGamePatch: null, recordedScrapeTime: null },
        source,
        compiled,
        validation: { warnings: sortFindings(warnings), info: sortFindings(info) },
    }
    const manifestStr = canonicalJson(manifest)

    const stats = {
        skillRawCount: Object.keys(parsed.get("skills") as object).length,
        skillCompiledCount: skills.length,
        uniqueSkillIdCount: new Set(skills.map((s) => s.id)).size,
        raceRawCount: Object.keys(parsed.get("races") as object).length,
        raceCompiledCount: races.length,
        uniqueRaceKeyCount: raceKeySet.size,
        distinctBareRaceNameCount: new Set(races.map((r) => r.name)).size,
        bareNameCollisionCount: races.length - new Set(races.map((r) => r.name)).size,
        objectiveReferencesChecked: objStats.checked,
        objectiveReferencesUnresolved: objStats.unresolved,
    }

    return {
        ok: true,
        errors: [],
        artifacts: { skills: skillsStr, races: racesStr, manifest: manifestStr },
        manifest,
        validation: manifest.validation,
        stats,
        fingerprint,
        exitCode: warnings.length > 0 ? EXIT_WARNINGS : EXIT_CLEAN,
    }
}

function fail(errors: CompileError[], warnings: ValidationFinding[], info: ValidationFinding[]): CompileResult {
    return {
        ok: false,
        errors,
        artifacts: null,
        manifest: null,
        validation: { warnings: sortFindings(warnings), info: sortFindings(info) },
        stats: { skillRawCount: 0, skillCompiledCount: 0, uniqueSkillIdCount: 0, raceRawCount: 0, raceCompiledCount: 0, uniqueRaceKeyCount: 0, distinctBareRaceNameCount: 0, bareNameCollisionCount: 0, objectiveReferencesChecked: 0, objectiveReferencesUnresolved: 0 },
        fingerprint: null,
        exitCode: EXIT_VALIDATION,
    }
}

// ---- Skills ----

function compileSkills(raw: Record<string, unknown>, errors: CompileError[], warnings: ValidationFinding[], info: ValidationFinding[]): CompiledSkill[] {
    const skills: CompiledSkill[] = []
    const idSeen = new Map<number, string>()
    const unknownKeys = new Set<string>()

    for (const [key, value] of Object.entries(raw)) {
        if (!isObject(value)) {
            errors.push({ code: "skillNotObject", detail: `skill "${key}" is not an object` })
            continue
        }
        for (const k of Object.keys(value)) if (!KNOWN_SKILL_KEYS.has(k)) unknownKeys.add(k)

        const id = value.id
        if (!isInt(id)) {
            errors.push({ code: "skillInvalidId", detail: `skill "${key}" has no integer id (got ${JSON.stringify(id)})` })
            continue
        }
        const name = value.name_en
        if (typeof name !== "string" || name.length === 0) {
            errors.push({ code: "skillInvalidName", detail: `skill id ${id} has no non-empty name_en` })
            continue
        }
        if (idSeen.has(id)) {
            errors.push({ code: "skillDuplicateId", detail: `duplicate skill id ${id} ("${idSeen.get(id)}" and "${key}")` })
            continue
        }
        idSeen.set(id, key)

        const upgrade = compileChainRef(id, value.upgrade, "upgrade", errors)
        const downgrade = compileChainRef(id, value.downgrade, "downgrade", errors)

        skills.push({
            id,
            name,
            desc: asStringOrNull(value.desc_en),
            iconId: asIntOrNull(value.icon_id),
            cost: asIntOrNull(value.cost),
            evalPt: asIntOrNull(value.eval_pt),
            condition: asStringOrNull(value.condition),
            precondition: asStringOrNull(value.precondition),
            inherited: typeof value.inherited === "boolean" ? value.inherited : null,
            tier: asIntOrNull(value.community_tier),
            upgrade,
            downgrade,
        })
    }

    // Resolve chain references now that every id is known. An integer ref to an absent id is source
    // incompleteness (a warning), NOT a hard error - the real EN dataset legitimately carries a couple of
    // downgrade references to lower-tier variants that were never released/scraped for EN. A structurally
    // malformed ref (non-integer, or a self-reference) was already rejected as a hard error above.
    const ids = new Set(skills.map((s) => s.id))
    for (const s of skills) {
        for (const [field, ref] of [["upgrade", s.upgrade], ["downgrade", s.downgrade]] as const) {
            if (ref !== null && !ids.has(ref)) {
                warnings.push({ code: "chainReferenceUnresolved", detail: `skill ${s.id} ${field} -> ${ref} references a skill absent from this dataset` })
            }
        }
    }

    if (unknownKeys.size > 0) warnings.push({ code: "schemaDriftUnknownField", detail: `skills: unknown additive field(s) ${[...unknownKeys].sort().join(", ")} (preserved-by-ignore)` })
    const nullTier = skills.filter((s) => s.tier === null).length
    if (nullTier > 0) info.push({ code: "nullFieldPrevalence", detail: `skills: tier is null on ${nullTier}/${skills.length} records` })

    skills.sort((a, b) => a.id - b.id)
    return skills
}

/**
 * Validates one chain field. A present-but-non-integer value or a self-reference is a hard error
 * (structural malformation). A valid integer is returned as-is; its resolvability is checked later.
 */
function compileChainRef(id: number, value: unknown, field: string, errors: CompileError[]): number | null {
    if (value === null || value === undefined) return null
    if (!isInt(value)) {
        errors.push({ code: "skillChainMalformed", detail: `skill ${id} ${field} is not an integer id: ${JSON.stringify(value)}` })
        return null
    }
    if (value === id) {
        errors.push({ code: "skillChainSelfReference", detail: `skill ${id} ${field} references itself` })
        return null
    }
    return value
}

// ---- Races ----

function raceKeyString(name: string, turnNumber: number): string {
    // Unambiguous internal composite key. JSON.stringify escapes any separator that could collide.
    return JSON.stringify([name, turnNumber])
}

function compileRaces(raw: Record<string, unknown>, errors: CompileError[], warnings: ValidationFinding[], info: ValidationFinding[]): CompiledRace[] {
    const races: CompiledRace[] = []
    const keySeen = new Set<string>()
    const unknownKeys = new Set<string>()
    let nullCourse = 0

    for (const [rawKey, value] of Object.entries(raw)) {
        if (!isObject(value)) {
            errors.push({ code: "raceNotObject", detail: `race "${rawKey}" is not an object` })
            continue
        }
        for (const k of Object.keys(value)) if (!KNOWN_RACE_KEYS.has(k)) unknownKeys.add(k)

        const name = value.name
        if (typeof name !== "string" || name.length === 0) {
            errors.push({ code: "raceInvalidName", detail: `race "${rawKey}" has no non-empty name` })
            continue
        }
        const turnNumber = value.turnNumber
        if (!isInt(turnNumber) || turnNumber < 1 || turnNumber > MAX_TURN_NUMBER) {
            errors.push({ code: "raceInvalidTurn", detail: `race "${name}" has invalid turnNumber ${JSON.stringify(turnNumber)} (expected integer 1..${MAX_TURN_NUMBER})` })
            continue
        }
        const enumErr = validateRaceEnums(name, value, errors)
        if (enumErr) continue
        const distanceMeters = value.distanceMeters
        const fans = value.fans
        if (!isInt(distanceMeters) || distanceMeters <= 0) {
            errors.push({ code: "raceInvalidNumeric", detail: `race "${name}" has invalid distanceMeters ${JSON.stringify(distanceMeters)}` })
            continue
        }
        if (!isInt(fans) || fans < 0) {
            errors.push({ code: "raceInvalidNumeric", detail: `race "${name}" has invalid fans ${JSON.stringify(fans)}` })
            continue
        }
        const raceTrack = value.raceTrack
        if (typeof raceTrack !== "string" || raceTrack.length === 0) {
            errors.push({ code: "raceInvalidField", detail: `race "${name}" has no non-empty raceTrack` })
            continue
        }

        const keyStr = raceKeyString(name, turnNumber)
        if (keySeen.has(keyStr)) {
            errors.push({ code: "raceDuplicateKey", detail: `duplicate race (name, turnNumber) = (${name}, ${turnNumber})` })
            continue
        }
        keySeen.add(keyStr)

        if (value.course === null) nullCourse++

        // Optional placement-to-fans payout curve: carried through (order-sorted) when the raw
        // record has it, omitted otherwise. Backwards-compatible - a race scraped before the field
        // simply lacks it, and the current committed data has none, so this changes no current output.
        let fanPayoutsByPlace: RaceFanPayout[] | undefined
        const rawPayouts = value.fanPayoutsByPlace
        if (rawPayouts !== undefined) {
            const bad =
                !Array.isArray(rawPayouts) ||
                rawPayouts.some((p) => {
                    if (!isObject(p)) return true
                    const place = p.place
                    const fansAtPlace = p.fans
                    if (!isInt(place) || !isInt(fansAtPlace)) return true
                    return place < 1 || fansAtPlace < 0
                })
            if (bad) {
                errors.push({ code: "raceInvalidNumeric", detail: `race "${name}" has invalid fanPayoutsByPlace ${JSON.stringify(rawPayouts)}` })
                continue
            }
            fanPayoutsByPlace = (rawPayouts as RaceFanPayout[]).map((p) => ({ place: p.place, fans: p.fans })).sort((a, b) => a.place - b.place)
        }

        races.push({
            key: { name, turnNumber },
            name,
            turnNumber,
            date: asStringOrNull(value.date),
            grade: value.grade as string,
            raceTrack,
            course: value.course === null ? null : (value.course as string),
            direction: value.direction as string,
            terrain: value.terrain as string,
            distanceType: value.distanceType as string,
            distanceMeters,
            fans,
            ...(fanPayoutsByPlace !== undefined ? { fanPayoutsByPlace } : {}),
            nameFormatted: asStringOrNull(value.nameFormatted),
        })
    }

    if (unknownKeys.size > 0) warnings.push({ code: "schemaDriftUnknownField", detail: `races: unknown additive field(s) ${[...unknownKeys].sort().join(", ")} (preserved-by-ignore)` })

    // Bare-name collisions are valid by design (a race recurs across turns); reported as info, never fixed.
    const bareNames = races.map((r) => r.name)
    const collisions = bareNames.length - new Set(bareNames).size
    info.push({ code: "bareRaceNameCollision", detail: `${collisions} race records share a bare name with another record (${new Set(bareNames).size} distinct names across ${races.length} races)` })
    if (nullCourse > 0) info.push({ code: "nullFieldPrevalence", detail: `races: course is null on ${nullCourse}/${races.length} records` })

    // Deterministic order: turnNumber asc, then name asc (the pair is unique, so this fully orders).
    races.sort((a, b) => a.turnNumber - b.turnNumber || (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
    return races
}

function validateRaceEnums(name: string, value: Record<string, unknown>, errors: CompileError[]): boolean {
    let bad = false
    const check = (field: string, domain: Set<string>): void => {
        const v = value[field]
        if (typeof v !== "string" || !domain.has(v)) {
            errors.push({ code: "raceInvalidEnum", detail: `race "${name}" ${field}=${JSON.stringify(v)} is not one of {${[...domain].join(", ")}}` })
            bad = true
        }
    }
    check("grade", RACE_GRADES)
    check("terrain", RACE_TERRAINS)
    check("direction", RACE_DIRECTIONS)
    check("distanceType", RACE_DISTANCE_TYPES)
    // course is nullable; when present it must be a known value.
    if (value.course !== null && (typeof value.course !== "string" || !RACE_COURSES.has(value.course))) {
        errors.push({ code: "raceInvalidEnum", detail: `race "${name}" course=${JSON.stringify(value.course)} is not one of {Inner, Outer, null}` })
        bad = true
    }
    return bad
}

// ---- Objectives -> races ----

function validateObjectives(raw: Record<string, unknown>, raceKeySet: Set<string>, errors: CompileError[]): { checked: number; unresolved: number } {
    let checked = 0
    let unresolved = 0
    for (const [char, obj] of Object.entries(raw)) {
        if (!isObject(obj)) {
            errors.push({ code: "objectiveNotObject", detail: `objective "${char}" is not an object` })
            continue
        }
        // Optional fan-count route goals extracted from master.mdb (target fans by a deadline turn).
        // Validated for shape here; not compiled into an artifact, but the raw layer carries them.
        const fanGoals = obj.fanGoals
        if (fanGoals !== undefined) {
            const bad =
                !Array.isArray(fanGoals) ||
                fanGoals.some((g) => {
                    if (!isObject(g)) return true
                    const { turn, targetFans, scenarioGroupId, appliesToScenarioIds } = g
                    if (!isInt(turn) || !isInt(targetFans) || !isInt(scenarioGroupId)) return true
                    if (turn < 1 || targetFans <= 0) return true
                    return !Array.isArray(appliesToScenarioIds) || appliesToScenarioIds.some((s) => !isInt(s))
                })
            if (bad) errors.push({ code: "objectiveFanGoalMalformed", detail: `objective "${char}" has a malformed fanGoals entry` })
        }
        const mrs = obj.mandatoryRaces
        if (mrs === undefined) continue // a character with no mandatory races is valid.
        if (!Array.isArray(mrs)) {
            errors.push({ code: "objectiveShape", detail: `objective "${char}" mandatoryRaces is not an array` })
            continue
        }
        for (const mr of mrs) {
            if (!isObject(mr) || !isInt(mr.turn) || !Array.isArray(mr.options)) {
                errors.push({ code: "objectiveShape", detail: `objective "${char}" has a malformed mandatoryRace entry` })
                continue
            }
            for (const opt of mr.options) {
                if (!isObject(opt) || typeof opt.raceName !== "string") {
                    errors.push({ code: "objectiveShape", detail: `objective "${char}" turn ${mr.turn} has a malformed option` })
                    continue
                }
                checked++
                // Disambiguate by (raceName, turn) against the composite race key - never a bare-name join.
                if (!raceKeySet.has(raceKeyString(opt.raceName, mr.turn))) {
                    unresolved++
                    errors.push({ code: "objectiveRaceUnresolved", detail: `objective "${char}" references race (${opt.raceName}, turn ${mr.turn}) which has no matching (name, turnNumber) in races` })
                }
            }
        }
    }
    return { checked, unresolved }
}

// ---- Conservative shape validation for non-compiled families ----

function validateConservative(family: string, raw: Record<string, unknown>, errors: CompileError[]): void {
    for (const [key, value] of Object.entries(raw)) {
        if (!isObject(value)) errors.push({ code: "unexpectedShape", detail: `${family} entry "${key}" is not an object` })
    }
}

// ---- Manifest support ----

function buildSourceEntries(byFamily: Map<RawFamily, RawInput>, parsed: Map<RawFamily, Record<string, unknown>>): RawSourceEntry[] {
    const entries: RawSourceEntry[] = []
    for (const [family, input] of byFamily) {
        entries.push({
            family,
            path: input.path,
            sha256: sha256(input.bytes),
            recordCount: Object.keys(parsed.get(family) as object).length,
            provenance: { origin: "data-scraper", sourceVersion: null },
        })
    }
    return entries.sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0))
}

function computeFingerprint(source: RawSourceEntry[], compiled: CompiledArtifactEntry[]): string {
    // Only deterministic content: versions + sorted raw (path, sha256) + sorted compiled (path, sha256).
    const material = {
        compilerVersion: COMPILER_VERSION,
        normalizationVersion: NORMALIZATION_VERSION,
        manifestSchemaVersion: MANIFEST_SCHEMA_VERSION,
        skillsSchemaVersion: SKILLS_SCHEMA_VERSION,
        racesSchemaVersion: RACES_SCHEMA_VERSION,
        raw: source.map((s) => ({ path: s.path, sha256: s.sha256 })).sort((a, b) => (a.path < b.path ? -1 : 1)),
        compiled: compiled.map((c) => ({ path: c.path, sha256: c.sha256 })).sort((a, b) => (a.path < b.path ? -1 : 1)),
    }
    return sha256(JSON.stringify(material))
}

function detectLargeChanges(previous: MasterDataManifest, byFamily: Map<RawFamily, RawInput>, parsed: Map<RawFamily, Record<string, unknown>>, warnings: ValidationFinding[]): void {
    const prevByPath = new Map(previous.source.map((s) => [s.path, s.recordCount]))
    for (const [family, input] of byFamily) {
        const prev = prevByPath.get(input.path)
        if (prev === undefined) continue
        const cur = Object.keys(parsed.get(family) as object).length
        const delta = Math.abs(cur - prev)
        if (delta >= Math.max(LARGE_CHANGE_ABS, Math.ceil(prev * LARGE_CHANGE_PCT))) {
            warnings.push({ code: "largeCountChange", detail: `${family}: record count changed ${prev} -> ${cur} (delta ${cur - prev})` })
        }
    }
}

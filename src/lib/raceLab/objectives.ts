// RaceLab v1 - objective-race modeling over character_objectives.json.
//
// Each character's mandatory-race turns are modeled as ObjectiveRequirements whose options are resolved
// canonically by `(raceName, turn) -> (name, turnNumber)` against the catalog - never by bare name. Choice
// objectives keep ALL their options. An option that fails to resolve is a deterministic error, never a
// bare-name guess. These objectives are URA-scoped: character_objectives.json models the URA career, so
// the timeline is explicitly tagged URA and no equivalence to Trackblazer/Unity Cup is claimed.

import { readFileSync } from "node:fs"
import type { RaceCatalog } from "./catalog.ts"
import type { ObjectiveTimeline, ObjectiveRequirement, ResolvedObjectiveOption } from "./types.ts"

/** A deterministic RaceLab failure with a stable `code`. */
export class RaceLabError extends Error {
    // Explicit field (not a constructor parameter property) so node's strip-only TS loader accepts it.
    readonly code: string
    constructor(code: string, message: string) {
        super(message)
        this.name = "RaceLabError"
        this.code = code
    }
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function asStringOrNull(value: unknown): string | null {
    return typeof value === "string" ? value : null
}

function asFiniteNumberOrNull(value: unknown): number | null {
    return typeof value === "number" && Number.isFinite(value) ? value : null
}

/**
 * Builds one character's objective timeline, resolving every option canonically. Throws {@link RaceLabError}
 * (`objectiveNotFound`, `objectiveMalformed`, `objectiveRaceUnresolved`) on any structural problem or an
 * option that does not resolve to a canonical `(name, turnNumber)`.
 */
export function buildObjectiveTimeline(character: string, rawObjectives: Record<string, unknown>, catalog: RaceCatalog): ObjectiveTimeline {
    const entry = rawObjectives[character]
    if (!isObject(entry)) throw new RaceLabError("objectiveNotFound", `no objective entry for character "${character}"`)
    const mandatory = entry.mandatoryRaces
    const requirements: ObjectiveRequirement[] = []
    if (mandatory !== undefined) {
        if (!Array.isArray(mandatory)) throw new RaceLabError("objectiveMalformed", `"${character}" mandatoryRaces is not an array`)
        for (const mr of mandatory) {
            if (!isObject(mr) || typeof mr.turn !== "number" || !Array.isArray(mr.options)) {
                throw new RaceLabError("objectiveMalformed", `"${character}" has a malformed mandatoryRace entry`)
            }
            const turn = mr.turn
            const options: ResolvedObjectiveOption[] = []
            for (const opt of mr.options) {
                if (!isObject(opt) || typeof opt.raceName !== "string") {
                    throw new RaceLabError("objectiveMalformed", `"${character}" turn ${turn} has a malformed option`)
                }
                const canonicalRace = catalog.raceByKey(opt.raceName, turn)
                if (canonicalRace === undefined) {
                    throw new RaceLabError("objectiveRaceUnresolved", `"${character}" objective (${opt.raceName}, turn ${turn}) has no canonical (name, turnNumber) match`)
                }
                options.push({
                    raceName: opt.raceName,
                    canonicalRace,
                    rawMeta: { grade: asStringOrNull(opt.grade), surface: asStringOrNull(opt.surface), distanceType: asStringOrNull(opt.distanceType), fans: asFiniteNumberOrNull(opt.fans) },
                })
            }
            // A choice objective keeps every option; isChoice reflects the source flag OR >1 option.
            requirements.push({ turn, isChoice: mr.isChoice === true || options.length > 1, options })
        }
    }
    requirements.sort((a, b) => a.turn - b.turn)
    return { character, scenario: "URA", requirements }
}

/** Reconciliation summary across the whole objective source set (for the real-data proof). */
export interface ObjectiveReconciliation {
    characterCount: number
    optionCount: number
    unresolvedCount: number
    choiceRequirementCount: number
}

/**
 * Builds every character's timeline and returns them plus a reconciliation summary. Any unresolved option
 * throws (fail-closed) - the real source set is expected to resolve fully.
 */
export function buildAllObjectiveTimelines(rawObjectives: Record<string, unknown>, catalog: RaceCatalog): { timelines: Map<string, ObjectiveTimeline>; reconciliation: ObjectiveReconciliation } {
    const timelines = new Map<string, ObjectiveTimeline>()
    let optionCount = 0
    let choiceRequirementCount = 0
    for (const character of Object.keys(rawObjectives).sort()) {
        const timeline = buildObjectiveTimeline(character, rawObjectives, catalog)
        timelines.set(character, timeline)
        for (const req of timeline.requirements) {
            optionCount += req.options.length
            if (req.isChoice) choiceRequirementCount++
        }
    }
    return { timelines, reconciliation: { characterCount: timelines.size, optionCount, unresolvedCount: 0, choiceRequirementCount } }
}

/** Loads the raw objectives JSON from disk (read-only). */
export function loadRawObjectives(path: string): Record<string, unknown> {
    const parsed = JSON.parse(readFileSync(path, "utf8"))
    if (!isObject(parsed)) throw new RaceLabError("objectiveMalformed", `${path} is not a JSON object map`)
    return parsed
}

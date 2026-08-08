// RaceLab v1 - race-plan validation.
//
// Parses the existing runtime racing-plan format (a JSON array of {raceName, date, turnNumber, priority?},
// per Racing.kt#loadUserPlannedRaces) and validates it against canonical race + objective data. Every race
// is resolved by the composite `(name, turnNumber)` key, so a same-name race is NEVER silently bound to
// the wrong turn. Findings are severity-tagged (error/warning/info) and deterministically ordered. It uses
// no "optimal"/"best" language and makes no policy recommendation - the runtime remains the race authority.

import { readFileSync } from "node:fs"
import type { RaceCatalog } from "./catalog.ts"
import { RaceLabError } from "./objectives.ts"
import { buildSchedule, analyzePressure } from "./pressure.ts"
import type { PlannedRace, PlanIssue, ObjectiveTimeline, PlanValidationReport, IssueSeverity } from "./types.ts"

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

/**
 * Parses a racing-plan string (the runtime format: JSON array of plan entries). Malformed JSON, a non-array
 * root, or a malformed entry are returned as issues rather than thrown, so validation can report them.
 */
export function parsePlan(input: string): { plan: PlannedRace[]; issues: PlanIssue[] } {
    const issues: PlanIssue[] = []
    const trimmed = input.trim()
    if (trimmed.length === 0 || trimmed === "[]") return { plan: [], issues }
    let parsed: unknown
    try {
        parsed = JSON.parse(trimmed)
    } catch (e) {
        return { plan: [], issues: [{ severity: "error", code: "planParseError", detail: e instanceof Error ? e.message : String(e), turn: null }] }
    }
    if (!Array.isArray(parsed)) {
        return { plan: [], issues: [{ severity: "error", code: "planNotArray", detail: "racing plan must be a JSON array", turn: null }] }
    }
    const plan: PlannedRace[] = []
    parsed.forEach((entry, i) => {
        if (!isObject(entry) || typeof entry.raceName !== "string" || typeof entry.turnNumber !== "number" || !Number.isInteger(entry.turnNumber)) {
            issues.push({ severity: "error", code: "malformedPlanEntry", detail: `plan entry ${i} is missing a string raceName or integer turnNumber`, turn: null })
            return
        }
        plan.push({
            raceName: entry.raceName,
            date: typeof entry.date === "string" ? entry.date : "",
            turnNumber: entry.turnNumber,
            priority: typeof entry.priority === "number" && Number.isInteger(entry.priority) ? entry.priority : 0,
        })
    })
    return { plan, issues }
}

const SEVERITY_RANK: Record<IssueSeverity, number> = { error: 0, warning: 1, info: 2 }

/**
 * Validates a parsed plan against the catalog and (optionally) an objective timeline. `consecutiveLimit`,
 * when supplied, surfaces streaks reaching a scenario-specific limit (e.g. Trackblazer's default 2).
 */
export function validatePlan(plan: readonly PlannedRace[], catalog: RaceCatalog, objectiveTimeline?: ObjectiveTimeline, options: { consecutiveLimit?: number; parseIssues?: readonly PlanIssue[] } = {}): PlanValidationReport {
    const issues: PlanIssue[] = [...(options.parseIssues ?? [])]

    // Existence + canonical (name, turn) resolution; never bind a bare name to a guessed turn.
    for (const entry of plan) {
        const race = catalog.raceByKey(entry.raceName, entry.turnNumber)
        if (race === undefined) {
            const byName = catalog.racesByName(entry.raceName)
            if (byName.length > 0) {
                issues.push({ severity: "error", code: "planTurnMismatch", detail: `"${entry.raceName}" is not run on turn ${entry.turnNumber}; it exists on turn(s) ${byName.map((r) => r.turnNumber).join(", ")}`, turn: entry.turnNumber })
            } else {
                issues.push({ severity: "error", code: "raceNotFound", detail: `no canonical race named "${entry.raceName}"`, turn: entry.turnNumber })
            }
        }
    }

    // Same-turn double booking / conflicting races on one turn.
    const byTurn = new Map<number, PlannedRace[]>()
    for (const entry of plan) {
        const list = byTurn.get(entry.turnNumber)
        if (list) list.push(entry)
        else byTurn.set(entry.turnNumber, [entry])
    }
    for (const [turn, entries] of byTurn) {
        if (entries.length < 2) continue
        const distinctNames = new Set(entries.map((e) => e.raceName))
        if (distinctNames.size > 1) {
            issues.push({ severity: "error", code: "conflictingRacesOnTurn", detail: `turn ${turn} has ${distinctNames.size} different planned races: ${[...distinctNames].sort().join(", ")}`, turn })
        } else {
            issues.push({ severity: "error", code: "duplicateTurn", detail: `turn ${turn} lists the same race ${entries.length} times`, turn })
        }
    }

    // Objective compatibility. Runtime enters mandatory objectives itself, so a plan omitting an objective
    // turn is NOT an error; a plan choosing a DIFFERENT race on an objective turn conflicts with the forced
    // objective and IS an error. A plan choosing an allowed objective race is informational.
    if (objectiveTimeline) {
        for (const req of objectiveTimeline.requirements) {
            const planned = byTurn.get(req.turn)
            if (!planned || planned.length === 0) continue // omitted; runtime handles it separately.
            const allowed = new Set(req.options.map((o) => o.raceName))
            for (const p of planned) {
                if (allowed.has(p.raceName)) {
                    issues.push({ severity: "info", code: req.isChoice ? "matchesChoiceObjective" : "matchesObjective", detail: `turn ${req.turn}: planned "${p.raceName}" matches the ${req.isChoice ? "choice " : ""}objective`, turn: req.turn })
                } else {
                    issues.push({ severity: "error", code: "objectiveConflict", detail: `turn ${req.turn}: planned "${p.raceName}" conflicts with the mandatory ${req.isChoice ? "choice " : ""}objective (${[...allowed].sort().join(" / ")})`, turn: req.turn })
                }
            }
        }
    }

    // Schedule pressure (descriptive): streaks -> warnings, same-turn stacks -> info.
    const pressure = analyzePressure(buildSchedule(objectiveTimeline?.requirements ?? [], plan), { consecutiveLimit: options.consecutiveLimit })
    for (const streak of pressure.streaks) {
        const reaches = streak.reachesConsecutiveLimit === true ? ` (reaches the supplied consecutive-race limit)` : ""
        issues.push({ severity: "warning", code: "consecutiveRaceStreak", detail: `turns ${streak.startTurn}..${streak.endTurn}: ${streak.length} consecutive race turns${reaches}`, turn: streak.startTurn })
    }
    for (const stack of pressure.sameTurn) {
        issues.push({ severity: "info", code: "sameTurnRaces", detail: `turn ${stack.turn}: ${stack.raceCount} races scheduled on one turn`, turn: stack.turn })
    }

    issues.sort((a, b) => (a.turn ?? -1) - (b.turn ?? -1) || SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity] || (a.code < b.code ? -1 : a.code > b.code ? 1 : a.detail < b.detail ? -1 : a.detail > b.detail ? 1 : 0))
    return { plan: [...plan], issues, pressure, ok: !issues.some((i) => i.severity === "error") }
}

/** Loads a plan from a file path or an inline JSON string (offline, read-only). */
export function loadPlan(pathOrString: string): { plan: PlannedRace[]; issues: PlanIssue[] } {
    const looksLikeJson = pathOrString.trim().startsWith("[")
    if (looksLikeJson) return parsePlan(pathOrString)
    let content: string
    try {
        content = readFileSync(pathOrString, "utf8")
    } catch (e) {
        throw new RaceLabError("planFileUnreadable", `cannot read plan file ${pathOrString}: ${e instanceof Error ? e.message : String(e)}`)
    }
    return parsePlan(content)
}

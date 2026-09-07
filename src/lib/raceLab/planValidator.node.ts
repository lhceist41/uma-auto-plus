// RaceLab v1 - filesystem plan loader for Node callers (CLI, tests).

import { readFileSync } from "node:fs"
import { RaceLabError } from "./objectives.ts"
import { parsePlan } from "./planValidator.ts"
import type { PlannedRace, PlanIssue } from "./types.ts"

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

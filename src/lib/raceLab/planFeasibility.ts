// RaceLab v1 - racing-plan feasibility preview for the Racing Plan settings page.
//
// Read-only. It runs the existing plan checks over the compiled race catalog and turns the findings into
// player copy. It never edits a plan, never touches settings, and never claims a plan will succeed: the
// runtime remains the race authority and it decides at the screen what it can actually enter.
//
// The catalog is built from the compiled artifacts the app bundles as parsed JSON, hash-verified against
// the same manifest the CLI verifies. If that verification fails the preview reports that it could not
// check anything rather than falling back to the raw race data (which is not the compiled authority).

import compiledManifest from "../../data/compiled/manifest.json"
import compiledRaces from "../../data/compiled/races.json"
import { logWarningWithTimestamp } from "../logger"
import { createRaceSourceFromDocuments } from "../masterData/reader.ts"
import { createRaceCatalog } from "./catalog.ts"
import type { RaceCatalog } from "./catalog.ts"
import { parsePlan, validatePlan } from "./planValidator.ts"
import type { PlanIssue, PlannedRace, StreakWindow } from "./types.ts"

/** The settings the preview reads. All of them are already on, or reachable from, the Racing Plan page. */
export interface PlanFeasibilityInput {
    /** The racing-plan JSON the page is currently showing. */
    racingPlan: string
    scenario: string
    trackblazerConsecutiveRacesLimit: number
    ignoreConsecutiveRaceWarning: boolean
    enableForceRacing: boolean
}

export interface PlanPreviewFinding {
    severity: "error" | "warning"
    /** Stable list key. */
    id: string
    message: string
}

/**
 * How consecutive races were judged. `trackblazerLimit` is the only mode with a number the runtime
 * actually enforces (Trackblazer.kt#shouldAllowConsecutiveRace); every other scenario defers to the game's
 * own consecutive-race warning, which the bot declines unless a force setting is on.
 */
export interface ConsecutiveCheck {
    mode: "trackblazerLimit" | "gameWarning" | "forced"
    limit: number | null
}

export type PlanFeasibilityPreview =
    | { kind: "empty" }
    | { kind: "unavailable" }
    | { kind: "unreadable"; findings: PlanPreviewFinding[] }
    | { kind: "checked"; raceCount: number; errorCount: number; warningCount: number; findings: PlanPreviewFinding[]; hiddenCount: number; consecutiveCheck: ConsecutiveCheck }

/** Findings listed at once; the rest are counted. A whole-calendar plan can produce hundreds. */
const MAX_LISTED_FINDINGS = 12

/** Bound on a race name echoed back into player copy, so a pasted plan cannot flood the panel. */
const MAX_NAME_LENGTH = 60

let cachedCatalog: RaceCatalog | null | undefined

function raceCatalog(): RaceCatalog | null {
    if (cachedCatalog === undefined) {
        try {
            cachedCatalog = createRaceCatalog(createRaceSourceFromDocuments(compiledManifest, compiledRaces))
        } catch (e) {
            cachedCatalog = null
            logWarningWithTimestamp(`Racing plan preview: compiled race data failed verification (${e instanceof Error ? e.message : String(e)}).`)
        }
    }
    return cachedCatalog
}

function clipName(name: string): string {
    return name.length > MAX_NAME_LENGTH ? name.slice(0, MAX_NAME_LENGTH) + "..." : name
}

function turnList(turns: readonly number[]): string {
    return turns.length === 1 ? `turn ${turns[0]}` : `turns ${turns.join(", ")}`
}

/** Player copy for one validation finding, or null for findings the panel does not list. */
function describeIssue(issue: PlanIssue, plan: readonly PlannedRace[], catalog: RaceCatalog): string | null {
    const name = issue.raceName === undefined ? "" : clipName(issue.raceName)
    switch (issue.code) {
        case "raceNotFound":
            return `Turn ${issue.turn}: there is no race called "${name}". Remove it and pick the race again from the list below.`
        case "planTurnMismatch": {
            const turns = catalog.racesByName(issue.raceName ?? "").map((r) => r.turnNumber)
            if (turns.length === 0) return `"${name}" does not run on turn ${issue.turn}.`
            return `"${name}" does not run on turn ${issue.turn}. It runs on ${turnList(turns)}.`
        }
        case "conflictingRacesOnTurn": {
            const names = [...new Set(plan.filter((p) => p.turnNumber === issue.turn).map((p) => clipName(p.raceName)))].sort()
            return `Turn ${issue.turn} has ${names.length} different races planned (${names.join(", ")}). Only one race can be run on a turn.`
        }
        case "duplicateTurn": {
            const count = plan.filter((p) => p.turnNumber === issue.turn).length
            return `Turn ${issue.turn} lists "${name}" ${count} times. The extra copies do nothing.`
        }
        default:
            return null
    }
}

function describeStreak(streak: StreakWindow, check: ConsecutiveCheck): string {
    const span = `Turns ${streak.startTurn} to ${streak.endTurn}: ${streak.length} races in a row.`
    if (check.mode === "trackblazerLimit") {
        return `${span} Trackblazer is set to stop at ${check.limit} consecutive races, so the bot may skip the rest.`
    }
    return `${span} The bot backs out when the game warns about consecutive races, so it may skip some of these.`
}

/**
 * Streaks the current settings make worth showing. A force setting means the bot races through the game's
 * warning, so back-to-back races are not a reason to warn the player.
 */
function selectStreaks(streaks: readonly StreakWindow[], check: ConsecutiveCheck): StreakWindow[] {
    if (check.mode === "forced") return []
    if (check.mode === "trackblazerLimit") return streaks.filter((s) => check.limit !== null && s.length > check.limit)
    return [...streaks]
}

function consecutiveCheckFor(input: PlanFeasibilityInput): ConsecutiveCheck {
    // Only these two flags reach the consecutive-race gates: Racing.kt#handleRaceEvents and
    // Trackblazer.kt#handleRaceEvents both abort the extra race unless one of them is set.
    if (input.enableForceRacing || input.ignoreConsecutiveRaceWarning) return { mode: "forced", limit: null }
    if (input.scenario === "Trackblazer") return { mode: "trackblazerLimit", limit: input.trackblazerConsecutiveRacesLimit }
    return { mode: "gameWarning", limit: null }
}

const PLAN_UNPARSEABLE: PlanPreviewFinding = {
    severity: "error",
    id: "planUnparseable",
    message: "The saved racing plan is not valid data, so the bot would ignore all of it. Use Clear and pick your races again.",
}

/** Findings for a plan the runtime could not read at all. Racing.kt discards the whole plan in that case. */
function unreadableFindings(issues: readonly PlanIssue[]): PlanPreviewFinding[] {
    const findings: PlanPreviewFinding[] = []
    if (issues.some((i) => i.code === "planParseError" || i.code === "planNotArray")) {
        findings.push(PLAN_UNPARSEABLE)
    }
    const malformed = issues.filter((i) => i.code === "malformedPlanEntry").length
    if (malformed > 0) {
        findings.push({
            severity: "error",
            id: "planEntriesIncomplete",
            message: `${malformed} planned ${malformed === 1 ? "race is" : "races are"} missing information the bot needs. The bot ignores the whole plan when that happens. Use Clear and pick your races again.`,
        })
    }
    return findings
}

const SEVERITY_ORDER = { error: 0, warning: 1 } as const

/**
 * Checks the supplied racing plan and returns what the panel should show. Never throws: a plan it cannot
 * read, and race data it cannot verify, both come back as their own state.
 */
export function buildPlanFeasibilityPreview(input: PlanFeasibilityInput): PlanFeasibilityPreview {
    // An imported settings file is deep-merged field by field with no type check, so racingPlan can hold
    // any JSON value by the time it reaches here. parsePlan takes a string by contract, so screen it first.
    if (typeof input.racingPlan !== "string") return { kind: "unreadable", findings: [PLAN_UNPARSEABLE] }

    const parsed = parsePlan(input.racingPlan)
    if (parsed.issues.length > 0) return { kind: "unreadable", findings: unreadableFindings(parsed.issues) }
    if (parsed.plan.length === 0) return { kind: "empty" }

    const catalog = raceCatalog()
    if (catalog === null) return { kind: "unavailable" }

    const report = validatePlan(parsed.plan, catalog)
    const check = consecutiveCheckFor(input)

    const findings: PlanPreviewFinding[] = []
    report.issues.forEach((issue, i) => {
        const message = describeIssue(issue, report.plan, catalog)
        if (message !== null) findings.push({ severity: "error", id: `${issue.code}-${issue.turn}-${i}`, message })
    })
    for (const streak of selectStreaks(report.pressure.streaks, check)) {
        findings.push({ severity: "warning", id: `streak-${streak.startTurn}`, message: describeStreak(streak, check) })
    }
    findings.sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity])

    const errorCount = findings.filter((f) => f.severity === "error").length
    return {
        kind: "checked",
        raceCount: report.plan.length,
        errorCount,
        warningCount: findings.length - errorCount,
        findings: findings.slice(0, MAX_LISTED_FINDINGS),
        hiddenCount: Math.max(0, findings.length - MAX_LISTED_FINDINGS),
        consecutiveCheck: check,
    }
}

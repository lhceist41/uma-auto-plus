// RaceLab v1 - schedule-pressure analysis.
//
// Purely descriptive analysis over the turns that carry a required (objective) or planned race: it reports
// consecutive-turn streaks, same-turn race stacks, and the gaps between scheduled turns. It never predicts
// fatigue, injury, or reward, and it never treats any consecutive-race limit as universal. The only
// source-proven limit is Trackblazer's `trackblazerConsecutiveRacesLimit` (default 2, configurable), so a
// limit is applied only when the caller supplies one and it is labelled scenario-specific.

import type { ObjectiveRequirement, PlannedRace, ScheduleEntry, ScheduleSource, StreakWindow, SameTurnWindow, GapWindow, PressureReport } from "./types.ts"

interface TurnAccumulator {
    names: Set<string>
    fromObjective: boolean
    fromPlan: boolean
    hasChoiceObjective: boolean
}

function sourceOf(acc: TurnAccumulator): ScheduleSource {
    return acc.fromObjective && acc.fromPlan ? "both" : acc.fromObjective ? "objective" : "plan"
}

/**
 * Merges objective requirements and/or a plan into a deterministic per-turn schedule. A single-option
 * objective contributes its concrete race name; a choice objective contributes one unnamed slot (the
 * specific race is a runtime choice, never guessed here); each plan entry contributes its race name.
 */
export function buildSchedule(requirements: readonly ObjectiveRequirement[] = [], plan: readonly PlannedRace[] = []): ScheduleEntry[] {
    const perTurn = new Map<number, TurnAccumulator>()
    const get = (turn: number): TurnAccumulator => {
        let acc = perTurn.get(turn)
        if (!acc) {
            acc = { names: new Set(), fromObjective: false, fromPlan: false, hasChoiceObjective: false }
            perTurn.set(turn, acc)
        }
        return acc
    }
    for (const req of requirements) {
        const acc = get(req.turn)
        acc.fromObjective = true
        if (req.isChoice) acc.hasChoiceObjective = true
        else if (req.options.length > 0) acc.names.add(req.options[0].raceName)
    }
    for (const p of plan) {
        const acc = get(p.turnNumber)
        acc.fromPlan = true
        acc.names.add(p.raceName)
    }
    return [...perTurn.entries()]
        .sort((a, b) => a[0] - b[0])
        .map(([turn, acc]) => ({ turn, source: sourceOf(acc), raceCount: acc.names.size > 0 ? acc.names.size : acc.hasChoiceObjective ? 1 : 0 }))
        .filter((e) => e.raceCount > 0)
}

/**
 * Produces the deterministic schedule-pressure report. `consecutiveLimit`, when supplied, flags streaks
 * that reach it - only meaningful for a scenario with a proven limit (Trackblazer default 2).
 */
export function analyzePressure(schedule: readonly ScheduleEntry[], options: { consecutiveLimit?: number } = {}): PressureReport {
    const entries = [...schedule].sort((a, b) => a.turn - b.turn)
    const limit = options.consecutiveLimit

    const sameTurn: SameTurnWindow[] = entries.filter((e) => e.raceCount > 1).map((e) => ({ kind: "sameTurn", turn: e.turn, raceCount: e.raceCount, source: e.source }))

    const streaks: StreakWindow[] = []
    const gaps: GapWindow[] = []
    let runStart = -1
    let runSources = new Set<ScheduleSource>()
    for (let i = 0; i < entries.length; i++) {
        if (runStart === -1) {
            runStart = i
            runSources = new Set([entries[i].source])
        }
        const isLast = i === entries.length - 1
        const breaksRun = isLast || entries[i + 1].turn !== entries[i].turn + 1
        if (!isLast) {
            const gap = entries[i + 1].turn - entries[i].turn
            if (gap > 1) gaps.push({ kind: "gap", fromTurn: entries[i].turn, toTurn: entries[i + 1].turn, gap })
            else runSources.add(entries[i + 1].source)
        }
        if (breaksRun) {
            const length = i - runStart + 1
            if (length >= 2) {
                const src: ScheduleSource = runSources.size > 1 ? "both" : [...runSources][0]
                streaks.push({
                    kind: "streak",
                    startTurn: entries[runStart].turn,
                    endTurn: entries[i].turn,
                    length,
                    source: src,
                    reachesConsecutiveLimit: limit === undefined ? null : length >= limit,
                })
            }
            runStart = -1
        }
    }

    return { entries, streaks, sameTurn, gaps }
}

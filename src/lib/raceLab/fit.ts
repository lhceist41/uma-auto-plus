// RaceLab v1 - factual aptitude-fit classification.
//
// Given a canonical race and CareerState-compatible aptitude maps, this reports the trainee's exact
// aptitude grade for the race's surface and distance, plus whether that grade meets the CURRENT-RUNTIME
// race-entry gate (`>= B` on both axes, mirroring Racing.kt#checkRaceAptitudeMatch). It never invents a
// default grade, never maps to invented "excellent/poor" tiers, and never implies a win probability.
// Running-style fit is intentionally absent: a race carries no running-style requirement to fit against.

import { aptitudeRank, RUNTIME_APTITUDE_GATE, APTITUDE_ORDER } from "./types.ts"
import type { CompiledRace, CareerStateAptitudes, AxisFit, RaceFit, AptitudeGrade } from "./types.ts"

const GATE_RANK = aptitudeRank(RUNTIME_APTITUDE_GATE)

function gradeOf(map: Record<string, string> | undefined, key: string): AptitudeGrade | null {
    if (!map) return null
    const raw = map[key]
    return typeof raw === "string" && APTITUDE_ORDER.includes(raw as AptitudeGrade) ? (raw as AptitudeGrade) : null
}

function axisFit(axis: "surface" | "distance", aptitudeKey: string, map: Record<string, string> | undefined): AxisFit {
    const grade = gradeOf(map, aptitudeKey)
    return {
        axis,
        aptitudeKey,
        grade,
        meetsRuntimeGate: grade === null ? null : aptitudeRank(grade) >= GATE_RANK,
        status: grade === null ? "unavailable" : "known",
    }
}

/**
 * Classifies a race's surface + distance aptitude fit against the supplied aptitude maps. The race's
 * `terrain` and `distanceType` are upcased to the CareerState map keys (Turf -> TURF, Mile -> MILE).
 */
export function classifyRaceFit(race: CompiledRace, aptitudes: CareerStateAptitudes): RaceFit {
    const surface = axisFit("surface", race.terrain.toUpperCase(), aptitudes.surface)
    const distance = axisFit("distance", race.distanceType.toUpperCase(), aptitudes.distance)
    const meetsCurrentRuntimeAptitudeGate = surface.meetsRuntimeGate === null || distance.meetsRuntimeGate === null ? null : surface.meetsRuntimeGate && distance.meetsRuntimeGate
    return { surface, distance, meetsCurrentRuntimeAptitudeGate }
}

/**
 * Whether the trainee meets the exact current-runtime race-entry aptitude gate for this race (surface >= B
 * AND distance >= B). Returns null when either grade is unavailable (the gate cannot be evaluated offline).
 * This mirrors current runtime behavior for compatibility; it is NOT an ideal-strategy or win claim.
 */
export function meetsCurrentRuntimeAptitudeGate(race: CompiledRace, aptitudes: CareerStateAptitudes): boolean | null {
    return classifyRaceFit(race, aptitudes).meetsCurrentRuntimeAptitudeGate
}

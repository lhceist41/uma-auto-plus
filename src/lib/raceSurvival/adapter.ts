// STAM-1 Race Survival Shadow Model - the target-profile adapter. Pure, offline, deterministic.
//
// ParentLab and DeckLab already agree on what a build target is: a distance, a surface and a running
// style, in one shared vocabulary. This model does not add a third one. It reads that vocabulary
// through a structural profile that both labs' resolved builds already satisfy, and keeps everything
// race-survival needs (the actual race, the Stamina on the sheet, the recovery package, the risk
// budget) on its own side of the line.
//
// The one thing the adapter does beyond mapping is checking that the race and the target agree. A
// Long-target build being priced against a Mile race is not an error the model should swallow: it is
// reported as a note, because it usually means the caller resolved the wrong race.

import type { TargetDistance, TargetRunningStyle, TargetSurface } from "../parentLab/targetBuild.ts"
import type { CompiledRace } from "../masterData/types.ts"
import type { DebuffBudget, DistanceType, GroundCondition, RaceStrategy, RaceSurface, RaceSurvivalInput } from "./types.ts"
import { RaceSurvivalError } from "./types.ts"
import { distanceTypeFor } from "./evidence.ts"
import { createRaceSurvivalInput } from "./envelope.ts"

/**
 * The shared build target, structurally.
 *
 * ParentLab's `TargetBuild` and DeckLab's `DeckTargetBuild` both satisfy this without changes, which
 * is the point: one vocabulary, three consumers, no adapter-shaped copy of either lab's type.
 */
export interface RaceSurvivalTargetProfile {
    readonly label?: string
    readonly distance?: TargetDistance | null
    readonly surface?: TargetSurface | null
    readonly runningStyle?: TargetRunningStyle | null
}

/** The race-survival-specific half: none of this belongs in a build-planning profile. */
export interface RaceSurvivalBuildInputs {
    readonly stamina: number
    readonly guts?: number | null
    readonly targetSpeed?: number | null
    readonly recoverySkillIds?: readonly number[]
    readonly debuffBudget?: DebuffBudget
    readonly groundCondition?: GroundCondition | null
    readonly rushRiskPolicy?: string | null
    readonly marginFraction?: number
    /** Overrides the profile's running style. Used when a race is being priced for a specific plan. */
    readonly strategy?: RaceStrategy | null
}

/** What the adapter could not reconcile, carried so a report can say so rather than a caller guessing. */
export const ADAPTER_NOTES = [
    /** The profile names no running style, so the caller had to supply one explicitly. */
    "RUNNING_STYLE_FROM_CALLER",
    /** The profile's distance band and the race's do not match. */
    "TARGET_DISTANCE_DIFFERS_FROM_RACE",
    /** The profile's surface and the race's do not match. */
    "TARGET_SURFACE_DIFFERS_FROM_RACE",
] as const
export type AdapterNote = (typeof ADAPTER_NOTES)[number]

export interface AdaptedRaceSurvivalInput {
    readonly input: RaceSurvivalInput
    readonly notes: readonly AdapterNote[]
}

/** Maps the compiled catalogue's terrain spelling onto the shared surface vocabulary. */
export function surfaceForTerrain(terrain: string): RaceSurface {
    const normalized = terrain.trim().toLowerCase()
    if (normalized === "turf") return "turf"
    if (normalized === "dirt") return "dirt"
    throw new RaceSurvivalError("unknownTerrain", `race terrain "${terrain}" is neither turf nor dirt`)
}

/** Maps a build target's distance band onto the skill-condition distance band. They are the same four. */
function targetDistanceAsDistanceType(distance: TargetDistance): DistanceType {
    return distance
}

/**
 * Turns a shared build target plus a canonical race plus the build's own numbers into a survival input.
 *
 * The running style is the profile's when it has one and the caller's otherwise; a caller override
 * always wins, because a plan can legitimately price the same build under a different style.
 */
export function toRaceSurvivalInput(profile: RaceSurvivalTargetProfile, race: CompiledRace, build: RaceSurvivalBuildInputs): AdaptedRaceSurvivalInput {
    const notes: AdapterNote[] = []
    const surface = surfaceForTerrain(race.terrain)

    const strategy = build.strategy ?? profile.runningStyle ?? null
    if (!strategy) {
        throw new RaceSurvivalError("noRunningStyle", "neither the target profile nor the caller named a running style; the MaxHP coefficient cannot be chosen without one")
    }
    if (!profile.runningStyle || build.strategy) notes.push("RUNNING_STYLE_FROM_CALLER")

    if (profile.distance && targetDistanceAsDistanceType(profile.distance) !== distanceTypeFor(race.distanceMeters)) {
        notes.push("TARGET_DISTANCE_DIFFERS_FROM_RACE")
    }
    if (profile.surface && profile.surface !== surface) {
        notes.push("TARGET_SURFACE_DIFFERS_FROM_RACE")
    }

    const input = createRaceSurvivalInput({
        targetRace: race.name,
        raceTrack: race.raceTrack,
        distanceMeters: race.distanceMeters,
        surface,
        groundCondition: build.groundCondition ?? null,
        strategy,
        stamina: build.stamina,
        guts: build.guts ?? null,
        targetSpeed: build.targetSpeed ?? null,
        recoverySkillIds: build.recoverySkillIds ?? [],
        debuffBudget: build.debuffBudget ?? "BASE",
        rushRiskPolicy: build.rushRiskPolicy ?? null,
        marginFraction: build.marginFraction ?? 0,
    })

    return { input, notes: Object.freeze(notes) }
}

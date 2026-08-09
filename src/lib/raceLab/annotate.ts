// RaceLab v1 - historical annotation (pure, ReplayLab-independent).
//
// Annotates a historical sequenced turn with FACTS drawn only from an objective timeline plus the turn's
// own recorded action/race-day flags, and - when the caller supplies the landed DecisionTrace `enteredRace`
// telemetry (Phase 2B) - the actual completed race's canonical identity/metadata. Identity is ONLY ever
// upgraded from explicit entered-race telemetry, never inferred from objectives, plan, turn, scenario,
// schedule, or a bare race name. This is a pure function over minimal record shapes: it does not import or
// modify ReplayLab, and it never predicts outcomes, ranks races, or claims value/optimality/causation.

import { classifyRaceFit } from "./fit.ts"
import type { RaceCatalog } from "./catalog.ts"
import type { ObjectiveTimeline, CareerStateAptitudes, RaceFit, RaceKey } from "./types.ts"

/**
 * The producer `enteredRace` fact for a completed race, as a RaceLab-local structural type matching the
 * landed DecisionTrace field (NOT ReplayLab-coupled). `valid`/`issues` are optional: a caller that already
 * ran ReplayLab's validation may pass its verdict through, but RaceLab does its own minimal join-gating so
 * it never fabricates canonical certainty from a malformed fact.
 */
export interface HistoricalEnteredRaceFact {
    turnNumber: number
    resolution: string
    path: string
    name?: string
    matchCount?: number
    valid?: boolean
    issues?: string[]
}

/** The factual canonical metadata of a joined race, copied from the compiled catalog. No value/quality field. */
export interface CanonicalRaceMeta {
    name: string
    turnNumber: number
    grade: string
    /** The race's track surface (compiled `terrain`), e.g. "Turf" / "Dirt". */
    surface: string
    distanceType: string
    /** Distance in meters (compiled `distanceMeters`). */
    distance: number
}

/** Why an entered-race fact could not be canonically joined. */
export type EnteredRaceNotJoinableReason = "ambiguous" | "unresolved" | "nonCatalog" | "invalid"

/**
 * The result of joining an entered-race fact against RaceLab's CURRENT compiled catalog. Every branch stamps
 * the catalog `fingerprint` so the annotation is reproducible against a stated dataset. `catalogLookupFailed`
 * means the producer named a race + turn that the current dataset does not contain (drift) - the producer
 * name/turn are preserved and never silently reinterpreted.
 */
export type EnteredRaceCatalogJoin =
    | { status: "resolved"; catalogFingerprint: string; race: CanonicalRaceMeta }
    | { status: "catalogLookupFailed"; catalogFingerprint: string; name: string; turnNumber: number }
    | { status: "notJoinable"; catalogFingerprint: string; reason: EnteredRaceNotJoinableReason }

/** How a canonically-joined entered race relates to a supplied URA objective timeline. URA-scoped only. */
export type EnteredRaceObjectiveRelation = "matchesMandatoryObjective" | "matchesChoiceOption" | "nonObjective" | "unavailable"

/** The additive entered-race annotation for a historical turn. Present only when the caller supplied a fact + catalog. */
export interface EnteredRaceAnnotation {
    /** The preserved producer fact (raw resolution/path tokens kept verbatim). */
    fact: { turnNumber: number; resolution: string; path: string; name: string | null; matchCount: number | null }
    catalog: EnteredRaceCatalogJoin
    /** Factual aptitude fit of the actual race (only when the join resolved AND aptitudes were supplied); else null. */
    fit: RaceFit | null
    /** URA objective relation (only when the join resolved AND a URA timeline was supplied); else "unavailable". */
    objectiveRelation: EnteredRaceObjectiveRelation
}

/** The minimal per-turn history a caller extracts from ReplayLab-style records. */
export interface HistoricalTurnInput {
    /** Authoritative sequence number if the turn was JOINED; diagnostic only. */
    seq?: number | null
    /** Observed turn number, or null when the date was unread. */
    turn: number | null
    /** The committed main-screen action for the turn (e.g. "RACE", "TRAIN"), or null. */
    committedAction: string | null
    /** Recorded pre-decision race-day flags, if available. */
    raceDayFlags?: { mandatory: boolean; scheduled: boolean; goalRibbon: boolean } | null
    /** The landed DecisionTrace entered-race fact for this turn, if the caller has it (Phase 2B). */
    enteredRace?: HistoricalEnteredRaceFact
}

/** One objective option's factual annotation for a historical turn. */
export interface AnnotatedObjectiveOption {
    key: RaceKey
    raceName: string
    fit: RaceFit | null
}

/** The factual annotation of one historical turn. */
export interface TurnAnnotation {
    seq: number | null
    turn: number | null
    raceActionRecorded: boolean
    isObjectiveTurn: boolean
    objectiveIsChoice: boolean
    objectiveOptions: AnnotatedObjectiveOption[]
    raceDayFlags: { mandatory: boolean; scheduled: boolean; goalRibbon: boolean } | null
    /**
     * The actual completed optional race's canonical name when explicit entered-race telemetry named it
     * truthfully (a valid `exact`/`fuzzy` fact with a name); otherwise "unavailable". Never inferred. This
     * preserves the producer's named identity even if the current catalog cannot join it (dataset drift).
     */
    enteredRaceIdentity: string
    /** Additive canonical enrichment of the actual entered race. Present only when a fact + catalog were supplied. */
    enteredRace?: EnteredRaceAnnotation
}

/** True only when the producer fact names a race it can stand behind: a valid exact/fuzzy fact with a name. */
function factHasCanonicalName(fact: HistoricalEnteredRaceFact): boolean {
    if (fact.valid === false) return false
    if (fact.resolution !== "exact" && fact.resolution !== "fuzzy") return false
    return typeof fact.name === "string" && fact.name.length > 0
}

/** Derives the legacy `enteredRaceIdentity`: the producer name for a valid exact/fuzzy+name fact, else "unavailable". */
function deriveEnteredRaceIdentity(fact: HistoricalEnteredRaceFact | undefined): string {
    if (fact === undefined) return "unavailable"
    return factHasCanonicalName(fact) ? (fact.name as string) : "unavailable"
}

/** Joins a fact against the current catalog, refusing any join that would assert false canonical certainty. */
function joinEnteredRace(fact: HistoricalEnteredRaceFact, catalog: RaceCatalog): EnteredRaceCatalogJoin {
    const fingerprint = catalog.fingerprint()
    // An invalid fact never yields a canonical join (a later consumer must not treat it as certain).
    if (fact.valid === false) return { status: "notJoinable", catalogFingerprint: fingerprint, reason: "invalid" }
    switch (fact.resolution) {
        case "nonCatalog":
            return { status: "notJoinable", catalogFingerprint: fingerprint, reason: "nonCatalog" }
        case "unresolved":
            return { status: "notJoinable", catalogFingerprint: fingerprint, reason: "unresolved" }
        case "ambiguousSet":
            return { status: "notJoinable", catalogFingerprint: fingerprint, reason: "ambiguous" }
        case "exact":
        case "fuzzy": {
            // A named exact/fuzzy fact joins by the canonical (name, turnNumber) key ONLY - never a bare name.
            // A nameless fuzzy fact (fuzzy multi-match) stays ambiguous; the producer's OCR certainty is kept.
            if (typeof fact.name !== "string" || fact.name.length === 0) {
                return { status: "notJoinable", catalogFingerprint: fingerprint, reason: "ambiguous" }
            }
            const race = catalog.raceByKey(fact.name, fact.turnNumber)
            if (race === undefined) {
                return { status: "catalogLookupFailed", catalogFingerprint: fingerprint, name: fact.name, turnNumber: fact.turnNumber }
            }
            return {
                status: "resolved",
                catalogFingerprint: fingerprint,
                race: { name: race.name, turnNumber: race.turnNumber, grade: race.grade, surface: race.terrain, distanceType: race.distanceType, distance: race.distanceMeters },
            }
        }
        default:
            // An unknown/future resolution token is preserved on the fact but is not a joinable canonical identity.
            return { status: "notJoinable", catalogFingerprint: fingerprint, reason: "unresolved" }
    }
}

/** The URA objective relation for a canonically-resolved entered race, by exact (name, turnNumber) key. */
function objectiveRelationFor(key: RaceKey, timeline: ObjectiveTimeline | undefined): EnteredRaceObjectiveRelation {
    if (timeline === undefined) return "unavailable"
    for (const requirement of timeline.requirements) {
        for (const option of requirement.options) {
            if (option.canonicalRace.key.name === key.name && option.canonicalRace.key.turnNumber === key.turnNumber) {
                return requirement.isChoice ? "matchesChoiceOption" : "matchesMandatoryObjective"
            }
        }
    }
    return "nonObjective"
}

/** Builds the additive entered-race annotation: preserved fact + catalog join + fit + URA objective relation. */
function buildEnteredRaceAnnotation(
    fact: HistoricalEnteredRaceFact,
    catalog: RaceCatalog,
    objectiveTimeline: ObjectiveTimeline | undefined,
    aptitudes: CareerStateAptitudes | undefined,
): EnteredRaceAnnotation {
    const join = joinEnteredRace(fact, catalog)
    let fit: RaceFit | null = null
    let objectiveRelation: EnteredRaceObjectiveRelation = "unavailable"
    if (join.status === "resolved") {
        const resolved = catalog.raceByKey(join.race.name, join.race.turnNumber)
        // resolved is defined here (the join just confirmed it); guard for the type-checker only.
        if (resolved !== undefined) {
            if (aptitudes) fit = classifyRaceFit(resolved, aptitudes)
            objectiveRelation = objectiveRelationFor(resolved.key, objectiveTimeline)
        }
    }
    return {
        fact: { turnNumber: fact.turnNumber, resolution: fact.resolution, path: fact.path, name: fact.name ?? null, matchCount: fact.matchCount ?? null },
        catalog: join,
        fit,
        objectiveRelation,
    }
}

/**
 * Annotates one historical turn. When the turn matches an objective requirement, the objective options
 * (and, if aptitudes are given, their fit) are attached. When the caller supplies the DecisionTrace
 * `enteredRace` fact AND a catalog, the actual completed race is canonically enriched (Phase 2B); without a
 * catalog only the legacy `enteredRaceIdentity` is upgraded from the producer name. Identity is never inferred.
 */
export function annotateHistoricalTurn(
    input: HistoricalTurnInput,
    objectiveTimeline: ObjectiveTimeline | undefined,
    aptitudes?: CareerStateAptitudes,
    catalog?: RaceCatalog,
): TurnAnnotation {
    const requirement = input.turn !== null && objectiveTimeline ? objectiveTimeline.requirements.find((r) => r.turn === input.turn) : undefined
    const objectiveOptions: AnnotatedObjectiveOption[] = requirement
        ? requirement.options.map((o) => ({ key: o.canonicalRace.key, raceName: o.raceName, fit: aptitudes ? classifyRaceFit(o.canonicalRace, aptitudes) : null }))
        : []
    const annotation: TurnAnnotation = {
        seq: input.seq ?? null,
        turn: input.turn,
        raceActionRecorded: input.committedAction === "RACE",
        isObjectiveTurn: requirement !== undefined,
        objectiveIsChoice: requirement?.isChoice ?? false,
        objectiveOptions,
        raceDayFlags: input.raceDayFlags ?? null,
        enteredRaceIdentity: deriveEnteredRaceIdentity(input.enteredRace),
    }
    // Canonical enrichment needs the catalog; without it the producer name still surfaces via enteredRaceIdentity.
    if (input.enteredRace !== undefined && catalog !== undefined) {
        annotation.enteredRace = buildEnteredRaceAnnotation(input.enteredRace, catalog, objectiveTimeline, aptitudes)
    }
    return annotation
}

/** Annotates a whole historical sequence, sorted by seq then turn for deterministic output. */
export function annotateHistory(
    inputs: readonly HistoricalTurnInput[],
    objectiveTimeline: ObjectiveTimeline | undefined,
    aptitudes?: CareerStateAptitudes,
    catalog?: RaceCatalog,
): TurnAnnotation[] {
    return [...inputs]
        .sort((a, b) => (a.seq ?? Number.MAX_SAFE_INTEGER) - (b.seq ?? Number.MAX_SAFE_INTEGER) || (a.turn ?? Number.MAX_SAFE_INTEGER) - (b.turn ?? Number.MAX_SAFE_INTEGER))
        .map((i) => annotateHistoricalTurn(i, objectiveTimeline, aptitudes, catalog))
}

// RaceLab v1 - thin public export surface for Node callers. Factual, offline, read-only race intelligence
// over the canonical master-data layer. No policy authority, no outcome prediction, no simulation.
//
// This surface includes the filesystem loaders, so it is not importable from the app bundle. The app
// imports the pure modules it needs directly (see planFeasibility.ts).

export * from "./types.ts"
export { createRaceCatalog } from "./catalog.ts"
export { loadRaceCatalog } from "./catalog.node.ts"
export type { RaceCatalog } from "./catalog.ts"
export { RaceLabError, buildObjectiveTimeline, buildAllObjectiveTimelines, loadRawObjectives } from "./objectives.ts"
export type { ObjectiveReconciliation } from "./objectives.ts"
export { classifyRaceFit, meetsCurrentRuntimeAptitudeGate } from "./fit.ts"
export { buildSchedule, analyzePressure } from "./pressure.ts"
export { parsePlan, validatePlan } from "./planValidator.ts"
export { loadPlan } from "./planValidator.node.ts"
export { annotateHistoricalTurn, annotateHistory } from "./annotate.ts"
export type {
    HistoricalTurnInput,
    TurnAnnotation,
    AnnotatedObjectiveOption,
    HistoricalEnteredRaceFact,
    EnteredRaceAnnotation,
    EnteredRaceCatalogJoin,
    EnteredRaceObjectiveRelation,
    EnteredRaceNotJoinableReason,
    CanonicalRaceMeta,
} from "./annotate.ts"

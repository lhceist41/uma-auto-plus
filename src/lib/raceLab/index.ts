// RaceLab v1 - thin public export surface. Factual, offline, read-only race intelligence over the
// canonical master-data layer. No policy authority, no outcome prediction, no simulation.

export * from "./types.ts"
export { createRaceCatalog, loadRaceCatalog } from "./catalog.ts"
export type { RaceCatalog } from "./catalog.ts"
export { RaceLabError, buildObjectiveTimeline, buildAllObjectiveTimelines, loadRawObjectives } from "./objectives.ts"
export type { ObjectiveReconciliation } from "./objectives.ts"
export { classifyRaceFit, meetsCurrentRuntimeAptitudeGate } from "./fit.ts"
export { buildSchedule, analyzePressure } from "./pressure.ts"
export { parsePlan, validatePlan, loadPlan } from "./planValidator.ts"
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

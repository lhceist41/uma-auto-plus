// STAM-2 Joint Build Budget Planner - public surface.
//
// Shadow only: nothing here is read by the bot at runtime. It answers "which parent pair, deck and
// borrow best pay for the Stamina this race needs, and what does paying for it cost" for offline
// reports, and it changes no training choice, lesson purchase, skill buy, deck selection, borrow
// selection or career launch.

export * from "./types.ts"
export * from "./evidence.ts"
export * from "./inheritance.ts"
export * from "./training.ts"
export * from "./archetypes.ts"
export * from "./recovery.ts"
export * from "./budget.ts"
export * from "./joint.ts"
export * from "./adapter.ts"
export * from "./borrow.ts"
export * from "./borrowRanking.ts"
export * from "./borrowReport.ts"
export * from "./report.ts"

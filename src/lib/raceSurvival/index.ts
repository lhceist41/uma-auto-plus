// STAM-1 Race Survival Shadow Model - public surface.
//
// Shadow only: nothing here is read by the bot at runtime. It answers "how much Stamina does this
// build need to survive this race" for offline reports and for the future Build Budget Planner, and
// it changes no training choice, lesson purchase, skill buy or career launch.

export * from "./types.ts"
export * from "./mechanics.ts"
export * from "./evidence.ts"
export * from "./envelope.ts"
export * from "./adapter.ts"
export * from "./report.ts"

// DeckLab - offline, read-only support-deck advisor.
//
// Answers "given trainee X, scenario Y, target Z and the cards this account actually owns at their
// current limit breaks and levels, which deck should run, what is worth borrowing, and why" from the
// game's own decoded tables plus a maintained owned-card snapshot. Nothing in here touches a device,
// launches a career, or changes a deck.

export * from "./supportCardData.ts"
export * from "./inventory.ts"
export * from "./deckTarget.ts"
export * from "./cardValue.ts"
export * from "./deck.ts"
export * from "./deckSearch.ts"
export * from "./communityPrior.ts"
export * from "./telemetry.ts"
export * from "./report.ts"

// STAM-2 Joint Build Budget Planner - adapters onto ParentLab and DeckLab. Pure and deterministic.
//
// The planner does not own a Veteran model, a deck model or a trainee model. It owns a budget, and
// the three inputs to that budget already have owners. So this file translates rather than
// duplicates, and where a translation would lose something it says so instead of quietly dropping it.
//
// One translation is worth reading twice. ParentLab's `ParentPair.coverage` lists only the Sparks that
// matched the target build's PRIORITY lists, which is exactly right for ranking pairs and exactly
// wrong for budgeting them: a Power Spark on a Long build is not a priority and still hands over
// Power. So the adapter reads the parents' full factor sets off their Veteran evidence, not off the
// pair's coverage, and folds the Legacy Origin blocks in beside them. Which ancestor carried a Spark
// does not change what the ladder pays, but how many carriers there were does, and the inheritance
// bracket reads that count.

import type { CardValueProfile } from "../deckLab/cardValue.ts"
import type { DeckScore } from "../deckLab/deck.ts"
import type { InspirationFactorRecord } from "../parentLab/inspiration.ts"
import type { ParentPair } from "../parentLab/parentPairing.ts"
import type { VeteranEvidence } from "../parentLab/retentionEvidence.ts"
import type { BuildBudgetEvidence } from "./evidence.ts"
import type { DeckCandidate } from "./joint.ts"
import { BUDGET_STATS, BuildBudgetError, type BudgetFactor, type BudgetParentPair, type BudgetStat, type BudgetTrainee } from "./types.ts"

/** How a trainee's numbers were obtained. */
export interface TraineeRequest {
    readonly traineeName: string
    /** Outfit title as the game prints it, e.g. "[Wild Frontier]". Picks between a character's cards. */
    readonly outfit?: string | null
    /** Star level. Defaults to the highest the card ships, which is what an unlocked account has. */
    readonly starLevel?: number | null
    /** Overrides the decoded starting stats entirely, for a caller reading them off a live career. */
    readonly startStats?: Readonly<Record<BudgetStat, number>> | null
}

function normalize(value: string): string {
    return value
        .normalize("NFKC")
        .replace(/[^0-9a-zA-Z]+/g, "")
        .toUpperCase()
}

/**
 * Resolves a trainee onto the decoded growth rates and starting stats.
 *
 * Growth is per OUTFIT, not per character, which is why the outfit is a first-class argument rather
 * than a detail: the same character can ship a 20% Stamina outfit and a 0% Stamina one, and picking
 * the wrong one changes every training projection downstream. When the caller names no outfit and the
 * character has more than one, the lowest card id wins and the ambiguity is thrown, not guessed.
 */
export function resolveBudgetTrainee(evidence: BuildBudgetEvidence, request: TraineeRequest): BudgetTrainee {
    const outfits = evidence.outfitsFor(request.traineeName)
    if (!outfits.length) {
        throw new BuildBudgetError("unknownTrainee", `${request.traineeName} is not in the decoded trainee card table`)
    }
    let chosen = outfits[0]
    if (request.outfit) {
        const wanted = normalize(request.outfit)
        const match = outfits.find((o) => o.outfit && normalize(o.outfit) === wanted)
        if (!match) {
            throw new BuildBudgetError("unknownOutfit", `${request.traineeName} has no outfit ${request.outfit}; it ships ${outfits.map((o) => o.outfit ?? `card ${o.cardId}`).join(", ")}`)
        }
        chosen = match
    } else if (outfits.length > 1) {
        throw new BuildBudgetError(
            "ambiguousOutfit",
            `${request.traineeName} ships ${outfits.length} outfits (${outfits.map((o) => o.outfit ?? `card ${o.cardId}`).join(", ")}); name one, because growth rates differ between them`,
        )
    }

    const stars = evidence.starLevelsFor(chosen.cardId)
    const starLevel = request.starLevel ?? (stars.length ? stars[stars.length - 1] : null)
    const base = starLevel === null ? null : evidence.baseFor(chosen.cardId, starLevel)
    if (!base && !request.startStats) {
        throw new BuildBudgetError("noTraineeBase", `no decoded starting stats for ${request.traineeName} at ${String(starLevel)} stars`)
    }

    const growth = {} as Record<BudgetStat, number>
    const growthKeys: Readonly<Record<BudgetStat, string>> = { Speed: "speed", Stamina: "stamina", Power: "power", Guts: "guts", Wit: "wit" }
    for (const stat of BUDGET_STATS) growth[stat] = chosen.growth[growthKeys[stat]] ?? 0

    const startStats = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) startStats[stat] = request.startStats?.[stat] ?? base?.startStats[stat] ?? 0

    return {
        traineeName: chosen.character ?? request.traineeName,
        cardId: chosen.cardId,
        starLevel,
        startStats,
        growth,
        aptitudes: base?.aptitudes ?? null,
        origin: request.startStats ? "OPERATOR" : "DECODED",
    }
}

/** Every Spark a Veteran can pass on: its own, plus the Legacy Origin blocks behind it. */
export function veteranFactors(veteran: VeteranEvidence): readonly BudgetFactor[] {
    const out: BudgetFactor[] = []
    const push = (record: InspirationFactorRecord) => {
        if (!record.canonicalName) return
        out.push({ family: record.kind, canonicalName: record.canonicalName, stars: record.stars })
    }
    for (const record of veteran.selfFactors ?? []) push(record)
    for (const block of veteran.capture?.legacyAncestorFactors ?? []) for (const record of block) push(record)
    return out
}

/**
 * Turns a ranked ParentLab pair into a budget input.
 *
 * The two Veteran evidence records are required rather than optional: without them the adapter would
 * have to fall back on the pair's priority-filtered coverage, and a budget built off that would
 * silently understate every Spark the target build did not happen to ask for.
 */
export function toBudgetParentPair(pair: ParentPair, parentAEvidence: VeteranEvidence, parentBEvidence: VeteranEvidence): BudgetParentPair {
    const label = [pair.parentA, pair.parentB].map((p) => `${p.character ?? "unread"}${p.outfit ? ` ${p.outfit}` : ""}`).join(" + ")
    return {
        label,
        parentIds: [pair.parentA.rosterFingerprint ?? `scanIndex:${pair.parentA.scanIndex}`, pair.parentB.rosterFingerprint ?? `scanIndex:${pair.parentB.scanIndex}`],
        factors: [...veteranFactors(parentAEvidence), ...veteranFactors(parentBEvidence)],
        affinityPoints: [pair.parentA.affinity.points, pair.parentB.affinity.points],
        evidenceComplete: pair.parentA.selfFactorsTrusted && pair.parentB.selfFactorsTrusted,
    }
}

/** Turns a scored DeckLab deck into a budget candidate, keeping DeckLab's own label. */
export function toDeckCandidate(label: string, score: DeckScore): DeckCandidate {
    return { label, score }
}

/** The Stamina-typed support cards in a deck. Composition only; no card is read by name. */
export function staminaCards(cards: readonly CardValueProfile[]): readonly CardValueProfile[] {
    return cards.filter((c) => c.card.supportType === "Stamina")
}

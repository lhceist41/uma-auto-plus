// DeckLab - legal decks and what a whole deck is worth. Pure, offline, deterministic.
//
// A deck is not six cards added up. Three of its properties are genuinely cross-card, and each of them
// is modelled here rather than assumed away:
//
//   Type crowding. A card raises the odds of appearing on its own training, but there is one of each
//   training on the board, so the fifth Speed card competes with the four already there for the same
//   slots. Per-type totals are therefore aggregated concavely, and a deck that stacks one type shows a
//   lower coverage than the sum of its cards suggests. The curve is editorial; the direction is not.
//
//   Hint overlap. Two cards that hint the same skills cover fewer skills together than apart. Skill
//   coverage is the size of the union of the decoded hint pools, and redundancy is how much of the
//   summed pool that union throws away.
//
//   The borrow slot. A borrowed card does not add a seventh slot, it takes one, so its value is the
//   difference it makes against the owned card it displaces, not its own value. That is computed by
//   evaluating the deck with and without it.
//
// Legality is a mix of two things and they are labelled separately. The scenario restriction is
// decoded from the game's own table. The six-slot count, the single borrow, and the rule that no
// character may appear twice (with the trainee counting as one of them) are known game rules this
// repository has recorded rather than decoded, and they are marked KNOWN_GAME_RULE so a reader knows
// which is which.

import { VALUE_DIMENSIONS, type CardValueProfile, type ValueDimension } from "./cardValue.ts"
import type { DeckTargetBuild } from "./deckTarget.ts"
import { TRAINING_SUPPORT_TYPES, type SupportCardIndex, type SupportType } from "./supportCardData.ts"

/** Slots in a support deck. A known game rule, not decoded from master.mdb. */
export const DECK_SIZE = 6

/** Borrowed cards allowed in one deck. A known game rule, not decoded from master.mdb. */
export const MAX_BORROWED = 1

export const LEGALITY_SOURCES = ["DECODED", "KNOWN_GAME_RULE"] as const
export type LegalitySource = (typeof LEGALITY_SOURCES)[number]

export const DECK_VIOLATIONS = ["WRONG_SIZE", "DUPLICATE_CARD", "DUPLICATE_CHARACTER", "TRAINEE_CHARACTER_IN_DECK", "TOO_MANY_BORROWED", "SCENARIO_RESTRICTED_CARD"] as const
export type DeckViolation = (typeof DECK_VIOLATIONS)[number]

export const VIOLATION_SOURCE: Readonly<Record<DeckViolation, LegalitySource>> = {
    WRONG_SIZE: "KNOWN_GAME_RULE",
    DUPLICATE_CARD: "KNOWN_GAME_RULE",
    DUPLICATE_CHARACTER: "KNOWN_GAME_RULE",
    TRAINEE_CHARACTER_IN_DECK: "KNOWN_GAME_RULE",
    TOO_MANY_BORROWED: "KNOWN_GAME_RULE",
    SCENARIO_RESTRICTED_CARD: "DECODED",
}

export interface DeckLegality {
    readonly legal: boolean
    readonly violations: readonly { readonly violation: DeckViolation; readonly source: LegalitySource; readonly detail: string }[]
}

/**
 * Checks a deck against every rule DeckLab knows.
 *
 * The character rule includes the trainee: training a character locks out that character's support
 * card. That was learned from a rejected recommendation rather than read out of the database, so it
 * carries the KNOWN_GAME_RULE label like the rest of the deck-shape rules.
 */
export function checkDeckLegality(deck: readonly CardValueProfile[], build: DeckTargetBuild, options: { readonly requireFullSize?: boolean } = {}): DeckLegality {
    const violations: { violation: DeckViolation; source: LegalitySource; detail: string }[] = []
    const add = (violation: DeckViolation, detail: string) => violations.push({ violation, source: VIOLATION_SOURCE[violation], detail })

    if ((options.requireFullSize ?? true) && deck.length !== DECK_SIZE) add("WRONG_SIZE", `deck has ${deck.length} cards, a deck holds ${DECK_SIZE}`)
    if (deck.length > DECK_SIZE) add("WRONG_SIZE", `deck has ${deck.length} cards, a deck holds ${DECK_SIZE}`)

    const cardIds = new Set<number>()
    const charaIds = new Map<number, string>()
    for (const profile of deck) {
        if (cardIds.has(profile.card.supportCardId)) add("DUPLICATE_CARD", `${profile.card.displayName} appears twice`)
        cardIds.add(profile.card.supportCardId)
        const existing = charaIds.get(profile.card.characterId)
        if (existing && existing !== profile.card.displayName) add("DUPLICATE_CHARACTER", `${profile.card.characterName} appears as both ${existing} and ${profile.card.displayName}`)
        else if (existing) add("DUPLICATE_CHARACTER", `${profile.card.characterName} appears twice`)
        charaIds.set(profile.card.characterId, profile.card.displayName)

        if (build.traineeCharaId !== null && profile.card.characterId === build.traineeCharaId) {
            add("TRAINEE_CHARACTER_IN_DECK", `${profile.card.displayName} is the trainee's own card`)
        }
        if (!profile.scenarioFit.legal) add("SCENARIO_RESTRICTED_CARD", `${build.scenarioName} forbids ${profile.card.displayName}`)
    }

    const borrowed = deck.filter((p) => p.borrowed).length
    if (borrowed > MAX_BORROWED) add("TOO_MANY_BORROWED", `${borrowed} borrowed cards, at most ${MAX_BORROWED} is allowed`)

    return { legal: violations.length === 0, violations }
}

export type DeckComposition = Readonly<Record<SupportType, number>>

export function compositionOf(deck: readonly CardValueProfile[]): DeckComposition {
    const counts = { Speed: 0, Stamina: 0, Power: 0, Guts: 0, Wit: 0, Friend: 0, Group: 0 } as Record<SupportType, number>
    for (const profile of deck) counts[profile.card.supportType] += 1
    return counts
}

/**
 * Concave aggregation of same-type value: the nth card of a type counts for value/n.
 *
 * Cards raise the odds of appearing on their own training, and the board shows one training per type,
 * so same-type cards compete with each other for the same appearances. The crowding is steep: a sixth
 * Speed card is not adding a sixth Speed card's worth of anything, it is thinning the friendship
 * gauges of the five already there.
 *
 * A gentler square-root curve was tried first and is wrong for this: under it a deck of six identical
 * Speed cards scored slightly ABOVE a balanced deck of the same six cards spread across types, which
 * no real deck-building does. The harmonic curve expresses the crowding the model exists to express.
 *
 * The shape is editorial and labelled wherever it lands in a report. What is not editorial is the
 * direction: the marginal value of the fifth same-type card is lower than the first.
 */
export function concaveSum(values: readonly number[]): number {
    const sorted = [...values].sort((a, b) => b - a)
    let total = 0
    sorted.forEach((value, i) => {
        total += value / (i + 1)
    })
    return Number(total.toFixed(4))
}

/**
 * How much of the deck composite balance is allowed to move.
 *
 * The composite is target stat coverage scaled by how well the deck spreads across the stats the
 * target actually wants. The floor keeps a lopsided deck from being zeroed out, since a stacked deck
 * is a worse deck rather than no deck.
 */
export const BALANCE_FLOOR = 0.75

/**
 * The deck-level composite.
 *
 * This is deliberately NOT the sum of the six card composites. That sum was the first version and it
 * is wrong for the same reason a naive per-type total is wrong: it counts a sixth Speed card as
 * though it were the first, so the highest-scoring deck under it was six cards of one type. Building
 * the composite out of the deck dimensions means the crowding curve, the target weights and the type
 * spread all reach the number a reader is ranking on.
 *
 * Editorial, like everything it is built from.
 */
export function deckComposite(dimensions: Readonly<Record<DeckDimension, number>>): number {
    return Number((dimensions.targetStatCoverage * (BALANCE_FLOOR + (1 - BALANCE_FLOOR) * dimensions.trainingTypeBalance)).toFixed(4))
}

export const DECK_DIMENSIONS = [
    "targetStatCoverage",
    "trainingTypeBalance",
    "scenarioSynergy",
    "skillCoverage",
    "raceBonusTotal",
    "hintCoverage",
    "initialBondProfile",
    "friendshipRamp",
    "energyAndEventSupport",
    "redundancy",
    "borrowValue",
    "accountOpportunityCost",
] as const
export type DeckDimension = (typeof DECK_DIMENSIONS)[number]

export interface DeckScore {
    readonly cards: readonly CardValueProfile[]
    readonly composition: DeckComposition
    readonly legality: DeckLegality
    readonly dimensions: Readonly<Record<DeckDimension, number>>
    /** The per-card dimensions summed, for a reader who wants the raw totals. */
    readonly cardDimensionTotals: Readonly<Record<ValueDimension, number>>
    /** Editorial overall ordering. Never a game figure. */
    readonly composite: number
    readonly compositeOrigin: "EDITORIAL_WEIGHTS"
    readonly confidence: "HIGH" | "MEDIUM" | "LOW"
    readonly unknownMechanics: readonly string[]
    readonly borrowedCard: CardValueProfile | null
}

function sumCardDimensions(deck: readonly CardValueProfile[]): Record<ValueDimension, number> {
    const out = {} as Record<ValueDimension, number>
    for (const dimension of VALUE_DIMENSIONS) out[dimension] = 0
    for (const profile of deck) {
        for (const dimension of VALUE_DIMENSIONS) out[dimension] += profile.dimensions[dimension]
    }
    for (const dimension of VALUE_DIMENSIONS) out[dimension] = Number(out[dimension].toFixed(4))
    return out
}

/**
 * Scores a whole deck.
 *
 * targetStatCoverage is the number that carries most of the meaning: per priority stat, the concave
 * sum of the composites of the cards of that type, weighted by how much the target wants that stat.
 * It is what makes six individually strong cards lose to a better-spread legal deck.
 */
export function scoreDeck(index: SupportCardIndex, deck: readonly CardValueProfile[], build: DeckTargetBuild, options: { readonly requireFullSize?: boolean } = {}): DeckScore {
    const legality = checkDeckLegality(deck, build, options)
    const composition = compositionOf(deck)

    const byType = new Map<SupportType, CardValueProfile[]>()
    for (const profile of deck) {
        if (!byType.has(profile.card.supportType)) byType.set(profile.card.supportType, [])
        byType.get(profile.card.supportType)!.push(profile)
    }

    let targetStatCoverage = 0
    for (const stat of TRAINING_SUPPORT_TYPES) {
        const pool = byType.get(stat) ?? []
        targetStatCoverage += build.statWeight[stat] * concaveSum(pool.map((p) => p.composite))
    }
    // Friend and Group cards raise every training rather than one, so they enter coverage at the mean
    // stat weight instead of being dropped for having no type of their own.
    const meanWeight = TRAINING_SUPPORT_TYPES.reduce((sum, stat) => sum + build.statWeight[stat], 0) / TRAINING_SUPPORT_TYPES.length
    for (const type of ["Friend", "Group"] as const) {
        const pool = byType.get(type) ?? []
        targetStatCoverage += meanWeight * concaveSum(pool.map((p) => p.composite))
    }

    // Balance is how evenly the deck spreads across the types the target actually wants: 1 when every
    // priority stat is represented, falling as the deck piles into fewer of them.
    const wanted = build.statPriority.length ? build.statPriority : [...TRAINING_SUPPORT_TYPES]
    const covered = wanted.filter((stat) => (byType.get(stat) ?? []).length > 0).length
    const heaviest = Math.max(0, ...TRAINING_SUPPORT_TYPES.map((stat) => (byType.get(stat) ?? []).length))
    const trainingTypeBalance = Number((wanted.length ? covered / wanted.length : 0).toFixed(4)) * Number((deck.length ? 1 - Math.max(0, heaviest - 2) / deck.length : 0).toFixed(4))

    const hintUnion = new Set<number>()
    let hintSum = 0
    for (const profile of deck) {
        const card = index.byId.get(profile.card.supportCardId)
        if (!card) continue
        hintSum += card.hintSkillIds.length
        for (const id of card.hintSkillIds) hintUnion.add(id)
    }
    const redundancy = hintSum > 0 ? Number((1 - hintUnion.size / hintSum).toFixed(4)) : 0

    const matchedSkills = new Set<number>()
    for (const profile of deck) for (const id of profile.targetFit.matchedPrioritySkillIds) matchedSkills.add(id)

    const cardTotals = sumCardDimensions(deck)
    const scenarioSynergy = deck.filter((p) => p.scenarioFit.scenarioSpecialCharacter).length
    const friendshipRamp = Number((concaveSum(deck.map((p) => p.dimensions.friendshipBonus)) * (1 + cardTotals.initialBond / 500)).toFixed(4))

    const unknowns = new Set<string>()
    for (const profile of deck) for (const unknown of profile.unknownMechanics) unknowns.add(unknown)

    let confidence: DeckScore["confidence"] = "HIGH"
    if (deck.some((p) => p.confidence === "MEDIUM") || unknowns.size) confidence = "MEDIUM"
    if (deck.some((p) => p.confidence === "LOW")) confidence = "LOW"

    const borrowedCard = deck.find((p) => p.borrowed) ?? null

    const dimensions: Record<DeckDimension, number> = {
        targetStatCoverage: Number(targetStatCoverage.toFixed(4)),
        trainingTypeBalance: Number(trainingTypeBalance.toFixed(4)),
        scenarioSynergy,
        skillCoverage: matchedSkills.size,
        raceBonusTotal: cardTotals.raceBonus,
        hintCoverage: hintUnion.size,
        initialBondProfile: cardTotals.initialBond,
        friendshipRamp,
        energyAndEventSupport: Number((cardTotals.energyCostReduction + cardTotals.eventRecovery + cardTotals.witFriendshipRecovery).toFixed(4)),
        redundancy,
        // Filled in by the borrow analysis, which needs a second deck to compare against.
        borrowValue: 0,
        accountOpportunityCost: 0,
    }

    return {
        cards: deck,
        composition,
        legality,
        dimensions,
        cardDimensionTotals: cardTotals,
        composite: deckComposite(dimensions),
        compositeOrigin: "EDITORIAL_WEIGHTS",
        confidence,
        unknownMechanics: [...unknowns].sort(),
        borrowedCard,
    }
}

/** A deck score with the borrow dimensions filled in against a stated no-borrow baseline. */
export function withBorrowValue(score: DeckScore, baseline: DeckScore | null, displaced: CardValueProfile | null): DeckScore {
    if (!score.borrowedCard || !baseline) return score
    return {
        ...score,
        dimensions: {
            ...score.dimensions,
            borrowValue: Number((score.dimensions.targetStatCoverage - baseline.dimensions.targetStatCoverage).toFixed(4)),
            accountOpportunityCost: displaced ? Number(displaced.composite.toFixed(4)) : 0,
        },
    }
}

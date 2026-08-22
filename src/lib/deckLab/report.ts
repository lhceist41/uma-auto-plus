// DeckLab - the advisor report. Pure, offline, deterministic.
//
// The rule this module exists to enforce: no unexplained number. Every card in a recommended deck
// carries the reasons it is there, expressed in the game's own decoded figures, plus what it would
// replace and what about it DeckLab could not quantify. Every deck carries its strengths, its
// weaknesses, the trade-offs it makes against the other archetypes, and the mechanics that went
// uncounted.
//
// Two things are stated in the report rather than left for a reader to infer:
//
//   Which numbers are the game's and which are this repository's. Decoded effect values are the
//   game's. The composite, the stat weights and the concave aggregation are editorial, and every
//   place one appears is labelled.
//
//   What the search did not do. The working pool, the combinations evaluated, and the decks left out
//   of the Pareto pass are all reported, so "the best deck" reads as "the best deck found over this
//   pool" without a reader having to know that is what it means.

import { EDITORIAL_DIMENSION_WEIGHTS, VALUE_DIMENSIONS, type CardValueProfile, type ValueDimension } from "./cardValue.ts"
import { DECK_SIZE, type DeckScore } from "./deck.ts"
import type { DeckTargetBuild } from "./deckTarget.ts"
import type { ArchetypeResult, BorrowOption, DeckSearchResult } from "./deckSearch.ts"
import { priorCommentaryFor, type CommunityPriorIndex, type PriorCommentary } from "./communityPrior.ts"
import type { InventoryCompleteness, OwnedSupportInventorySnapshot } from "./inventory.ts"
import type { SupportCardIndex } from "./supportCardData.ts"

export const DECK_REPORT_SCHEMA = "deck_lab_shadow_advisor_report"
export const DECK_REPORT_SCHEMA_VERSION = 1

/** Dimensions worth naming in prose when a card leads on them. */
const HEADLINE_DIMENSIONS: readonly ValueDimension[] = [
    "friendshipBonus",
    "trainingEffectiveness",
    "specialtyPriority",
    "initialBond",
    "moodEffect",
    "raceBonus",
    "hintLevels",
    "skillPointBonus",
    "failureProtection",
    "energyCostReduction",
]

export interface CardExplanation {
    readonly supportCardId: number
    readonly displayName: string
    readonly role: string
    readonly rarity: string
    readonly limitBreak: number
    readonly level: number
    readonly levelCap: number
    readonly borrowed: boolean
    readonly whyIncluded: readonly string[]
    readonly targetFit: string
    readonly scenarioFit: string
    readonly limitBreakImpact: string
    readonly skillContribution: string
    readonly whatItReplaces: string | null
    readonly unknownMechanics: readonly string[]
    readonly confidence: string
    readonly decodedEffects: Readonly<Record<string, number>>
}

/**
 * Explains one card's presence in a deck.
 *
 * Reasons are drawn from the decoded figures, in descending order of what they contribute under the
 * editorial weights, and each names the game's own effect and its value. A card that is in the deck
 * only because nothing better was legal gets told that too.
 */
export function explainCard(index: SupportCardIndex, profile: CardValueProfile, build: DeckTargetBuild, replaces: CardValueProfile | null = null): CardExplanation {
    const contributions = HEADLINE_DIMENSIONS.map((dimension) => ({
        dimension,
        value: profile.dimensions[dimension],
        weighted: profile.dimensions[dimension] * EDITORIAL_DIMENSION_WEIGHTS[dimension],
    }))
        .filter((c) => c.value > 0)
        .sort((a, b) => b.weighted - a.weighted || a.dimension.localeCompare(b.dimension))

    const effectLabel = (dimension: ValueDimension): string => dimension.replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase())

    const whyIncluded = contributions.slice(0, 4).map((c) => `${effectLabel(c.dimension)} ${c.value} at level ${profile.limitBreakState.level}`)
    if (!whyIncluded.length) whyIncluded.push("no decoded effect of this card is above zero at its current level")

    const typeWeight = profile.targetFit.trainingTypeWeight
    const targetFit =
        typeWeight === null
            ? `${profile.card.supportType} cards raise every training rather than one, so the target's stat ordering neither promotes nor punishes this card`
            : `${profile.card.supportType} carries weight ${typeWeight} for this target (${build.statPriorityOrigin === "OPERATOR" ? "operator-stated priority" : build.statPriorityOrigin === "DEFAULT_BY_DISTANCE" ? `editorial default for a ${build.distance} build` : "no stated priority"})`

    const scenarioBits: string[] = []
    if (profile.scenarioFit.scenarioSpecialCharacter) scenarioBits.push(`${build.scenarioName} names ${profile.card.characterName} as one of its own characters (decoded; the size of that bonus is not)`)
    if (profile.scenarioFit.statCapAlignment > 0) scenarioBits.push(`this scenario raises the ${profile.card.supportType} cap by ${Math.round(profile.scenarioFit.statCapAlignment * 100)}% of its total cap bonus`)
    if (!scenarioBits.length) scenarioBits.push(`${build.scenarioName} gives this card no decoded scenario bonus`)

    const lb = profile.limitBreakState
    const lbBits = [`limit break ${lb.limitBreak}, level ${lb.level} of a ${lb.levelCap} cap`]
    if (lb.unlevelledHeadroom > 0) {
        lbBits.push(`${lb.unlevelledHeadroom} levels are already unlocked but not bought, worth ${lb.compositeFromLevelling} composite`)
    }
    if (lb.uniqueUnlockLevel !== null) {
        lbBits.push(lb.uniqueUnlocked ? `its unique perk is active (unlocks at level ${lb.uniqueUnlockLevel})` : `its unique perk is DORMANT: it needs level ${lb.uniqueUnlockLevel} and the card is at ${lb.level}`)
    }

    const card = index.byId.get(profile.card.supportCardId)
    const skillContribution = build.prioritySkillIds.size
        ? profile.targetFit.matchedPrioritySkillIds.length
            ? `hints ${profile.targetFit.matchedPrioritySkillIds.length} of the target's priority skills, out of a pool of ${profile.targetFit.hintPoolSize}`
            : `hints none of the target's priority skills, out of a pool of ${profile.targetFit.hintPoolSize}`
        : `can hint ${card?.hintSkillIds.length ?? 0} skills; the target named none, so none of them counted`

    const decodedEffects: Record<string, number> = {}
    for (const dimension of VALUE_DIMENSIONS) {
        if (profile.dimensions[dimension] !== 0) decodedEffects[dimension] = profile.dimensions[dimension]
    }

    return {
        supportCardId: profile.card.supportCardId,
        displayName: profile.card.displayName,
        role: profile.card.supportType,
        rarity: profile.card.rarity,
        limitBreak: lb.limitBreak,
        level: lb.level,
        levelCap: lb.levelCap,
        borrowed: profile.borrowed,
        whyIncluded,
        targetFit,
        scenarioFit: scenarioBits.join("; "),
        limitBreakImpact: lbBits.join("; "),
        skillContribution,
        whatItReplaces: replaces ? `${replaces.card.displayName} (composite ${replaces.composite})` : null,
        unknownMechanics: profile.unknownMechanics,
        confidence: profile.confidence,
        decodedEffects,
    }
}

export interface DeckExplanation {
    readonly composition: Readonly<Record<string, number>>
    readonly dimensions: Readonly<Record<string, number>>
    readonly composite: number
    readonly compositeOrigin: string
    readonly confidence: string
    readonly strengths: readonly string[]
    readonly weaknesses: readonly string[]
    readonly tradeoffs: readonly string[]
    readonly unknownMechanics: readonly string[]
    readonly cards: readonly CardExplanation[]
    readonly communityPrior: PriorCommentary | null
}

function describeComposition(deck: DeckScore): string {
    return Object.entries(deck.composition)
        .filter(([, count]) => count > 0)
        .map(([type, count]) => `${count} ${type}`)
        .join(", ")
}

export function explainDeck(index: SupportCardIndex, deck: DeckScore, build: DeckTargetBuild, alternatives: readonly ArchetypeResult[], prior: CommunityPriorIndex | null): DeckExplanation {
    const strengths: string[] = []
    const weaknesses: string[] = []
    const tradeoffs: string[] = []

    strengths.push(`composition ${describeComposition(deck)}`)
    if (deck.dimensions.scenarioSynergy > 0) strengths.push(`${deck.dimensions.scenarioSynergy} cards belong to characters ${build.scenarioName} names as its own`)
    if (deck.dimensions.hintCoverage > 0) strengths.push(`${deck.dimensions.hintCoverage} distinct skills are hintable across the deck`)
    if (deck.dimensions.raceBonusTotal > 0) strengths.push(`Race Bonus totals ${deck.dimensions.raceBonusTotal}`)
    if (deck.dimensions.initialBondProfile > 0) strengths.push(`Initial Bond totals ${deck.dimensions.initialBondProfile}, which is how fast the friendship multipliers switch on`)

    const missingPriority = build.statPriority.filter((stat) => (deck.composition[stat] ?? 0) === 0)
    if (missingPriority.length) weaknesses.push(`no card covers ${missingPriority.join(" or ")}, which the target lists as a priority stat`)
    if (deck.dimensions.redundancy > 0.5) weaknesses.push(`${Math.round(deck.dimensions.redundancy * 100)}% of the deck's hint capacity is spent on skills another card already hints`)
    const dormant = deck.cards.filter((c) => c.limitBreakState.uniqueUnlockLevel !== null && !c.limitBreakState.uniqueUnlocked)
    if (dormant.length) weaknesses.push(`${dormant.length} cards carry a unique perk that is dormant because the card is below its unlock level: ${dormant.map((c) => `${c.card.displayName} needs level ${c.limitBreakState.uniqueUnlockLevel}`).join(", ")}`)
    const headroom = deck.cards.filter((c) => c.limitBreakState.unlevelledHeadroom > 0)
    if (headroom.length) weaknesses.push(`${headroom.length} cards sit below the cap their current limit break already allows, so levelling them costs no limit-break material`)
    if (!deck.legality.legal) weaknesses.push(`this deck is not legal: ${deck.legality.violations.map((v) => v.detail).join("; ")}`)

    const deckIds = deck.cards
        .map((c) => c.card.supportCardId)
        .sort((a, b) => a - b)
        .join(",")
    for (const alternative of alternatives) {
        const otherIds = alternative.deck.cards
            .map((c) => c.card.supportCardId)
            .sort((a, b) => a - b)
            .join(",")
        if (otherIds === deckIds || !alternative.metric) continue
        const delta = Number((alternative.deck.dimensions[alternative.metric] - deck.dimensions[alternative.metric]).toFixed(4))
        if (delta > 0) tradeoffs.push(`${alternative.archetype} scores ${delta} higher on ${alternative.metric}, at a composite of ${alternative.deck.composite} against this deck's ${deck.composite}`)
    }

    return {
        composition: deck.composition,
        dimensions: deck.dimensions,
        composite: deck.composite,
        compositeOrigin: `${deck.compositeOrigin}: the weights are this repository's editorial judgement, not a decoded game figure`,
        confidence: deck.confidence,
        strengths,
        weaknesses,
        tradeoffs,
        unknownMechanics: deck.unknownMechanics,
        cards: deck.cards.map((profile) => explainCard(index, profile, build)),
        communityPrior: priorCommentaryFor(
            deck.cards.map((c) => ({ supportCardId: c.card.supportCardId, displayName: c.card.displayName })),
            prior,
        ),
    }
}

export interface BorrowExplanation {
    readonly borrowed: string
    readonly displaced: string | null
    readonly improvement: number
    readonly improvedDimensions: Readonly<Record<string, number>>
    readonly explanation: CardExplanation
}

export interface TargetReport {
    readonly label: string
    readonly trainee: string | null
    readonly scenario: string
    readonly distance: string | null
    readonly surface: string | null
    readonly runningStyle: string | null
    readonly statPriority: readonly string[]
    readonly statPriorityOrigin: string
    readonly targetGaps: readonly string[]
    readonly searchCompleteness: DeckSearchResult["completeness"]
    readonly excludedCardSummary: readonly string[]
    readonly recommended: { readonly archetype: string; readonly deck: DeckExplanation } | null
    readonly recommendedIsDominant: boolean
    readonly archetypes: readonly { readonly archetype: string; readonly metric: string | null; readonly deck: DeckExplanation }[]
    readonly borrow: readonly BorrowExplanation[]
    readonly noBorrowAvailable: boolean
}

export interface DeckLabReport {
    readonly schema: string
    readonly schemaVersion: number
    readonly catalogueSource: string
    readonly catalogueCards: number
    readonly inventory: {
        readonly name: string
        readonly snapshotDate: string | null
        readonly evidenceSource: string
        readonly isFixture: boolean
        readonly completeness: InventoryCompleteness
        readonly unresolved: readonly { readonly rawCharacter: string; readonly rawTitle: string; readonly reason: string; readonly detail: string }[]
    }
    readonly communityPrior: { readonly present: boolean; readonly sourceName: string | null; readonly capturedOn: string | null; readonly provenance: string | null; readonly resolved: number; readonly unresolved: number }
    readonly editorialWeights: Readonly<Record<string, number>>
    readonly targets: readonly TargetReport[]
    readonly caveats: readonly string[]
}

/** A one-line summary of why cards never reached the working pool, grouped by reason. */
function summarizeExclusions(result: DeckSearchResult): string[] {
    const byReason = new Map<string, number>()
    const named = new Map<string, string[]>()
    for (const exclusion of result.excludedCards) {
        const key = exclusion.reason.startsWith("dominated by") ? "dominated by another card of the same role on every decoded dimension" : exclusion.reason
        byReason.set(key, (byReason.get(key) ?? 0) + 1)
        if (!named.has(key)) named.set(key, [])
        if (named.get(key)!.length < 4) named.get(key)!.push(exclusion.card.card.displayName)
    }
    return [...byReason.entries()]
        .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
        .map(([reason, count]) => `${count} cards: ${reason} (for example ${named.get(reason)!.join(", ")})`)
}

function explainBorrow(index: SupportCardIndex, option: BorrowOption, build: DeckTargetBuild): BorrowExplanation {
    return {
        borrowed: option.borrowed.card.displayName,
        displaced: option.displaced?.card.displayName ?? null,
        improvement: option.improvement,
        improvedDimensions: option.improvedDimensions as Record<string, number>,
        explanation: explainCard(index, option.borrowed, build, option.displaced),
    }
}

export function buildTargetReport(index: SupportCardIndex, result: DeckSearchResult, prior: CommunityPriorIndex | null, topBorrow: number): TargetReport {
    const build = result.build
    const archetypes = result.archetypes.map((entry) => ({
        archetype: entry.archetype,
        metric: entry.metric,
        deck: explainDeck(index, entry.deck, build, result.archetypes, prior),
    }))

    // A single recommendation is only made when one deck is alone on the frontier. Otherwise the
    // archetypes are the answer, because picking between them is a judgement the operator owns.
    const recommended = result.dominantDeck
        ? { archetype: "DOMINANT", deck: explainDeck(index, result.dominantDeck, build, result.archetypes, prior) }
        : archetypes.find((a) => a.archetype === "BEST_BALANCED") ?? null

    return {
        label: build.label,
        trainee: build.traineeName,
        scenario: build.scenarioName,
        distance: build.distance,
        surface: build.surface,
        runningStyle: build.runningStyle,
        statPriority: build.statPriority,
        statPriorityOrigin: build.statPriorityOrigin,
        targetGaps: build.gaps,
        searchCompleteness: result.completeness,
        excludedCardSummary: summarizeExclusions(result),
        recommended: recommended ? { archetype: recommended.archetype, deck: recommended.deck } : null,
        recommendedIsDominant: result.dominantDeck !== null,
        archetypes,
        borrow: result.borrowOptions.slice(0, topBorrow).map((option) => explainBorrow(index, option, build)),
        noBorrowAvailable: result.bestNoBorrow !== null,
    }
}

export interface BuildReportInput {
    readonly index: SupportCardIndex
    readonly inventory: OwnedSupportInventorySnapshot
    readonly completeness: InventoryCompleteness
    readonly isFixture: boolean
    readonly results: readonly DeckSearchResult[]
    readonly prior: CommunityPriorIndex | null
    readonly topBorrow?: number
}

export function buildDeckLabReport(input: BuildReportInput): DeckLabReport {
    const caveats: string[] = []
    caveats.push("Every effect value in this report is read from the game's own tables at the level each card is actually at. The composite scores, the stat weights and the concave same-type aggregation are this repository's editorial judgement and are labelled wherever they appear.")
    caveats.push(`A deck is ${DECK_SIZE} cards, at most one of them borrowed, with no character appearing twice and the trainee counting as one of them. Those are known game rules recorded here, not decoded from the game's data; the scenario card restriction is decoded.`)
    if (input.isFixture) caveats.push("THIS REPORT USED A FIXTURE INVENTORY. It demonstrates the engine and says nothing about any real account.")
    if (!input.completeness.trustedForAccountClaims) {
        caveats.push("The owned inventory is not trusted for account-wide claims, so nothing here should be read as 'you own nothing better'.")
    }
    if (!input.prior) caveats.push("No community ranking prior was supplied, so no external opinion is reflected anywhere in this report.")
    if (input.results.some((r) => !r.completeness.exhaustiveOverOwnedPool)) {
        caveats.push("The deck search is approximate: it enumerates every legal deck over a bounded working pool, not over every card owned. Widening the pool with --pool-limit does find better decks.")
    }

    return {
        schema: DECK_REPORT_SCHEMA,
        schemaVersion: DECK_REPORT_SCHEMA_VERSION,
        catalogueSource: input.index.data.source,
        catalogueCards: input.index.data.cards.length,
        inventory: {
            name: input.inventory.inventoryName,
            snapshotDate: input.inventory.snapshotDate,
            evidenceSource: input.inventory.evidenceSource,
            isFixture: input.isFixture,
            completeness: input.completeness,
            unresolved: input.inventory.unresolved,
        },
        communityPrior: input.prior
            ? {
                  present: true,
                  sourceName: input.prior.snapshot.sourceName,
                  capturedOn: input.prior.snapshot.capturedOn,
                  provenance: input.prior.snapshot.provenance,
                  resolved: input.prior.resolved,
                  unresolved: input.prior.unresolved,
              }
            : { present: false, sourceName: null, capturedOn: null, provenance: null, resolved: 0, unresolved: 0 },
        editorialWeights: EDITORIAL_DIMENSION_WEIGHTS,
        targets: input.results.map((result) => buildTargetReport(input.index, result, input.prior, input.topBorrow ?? 5)),
        caveats,
    }
}

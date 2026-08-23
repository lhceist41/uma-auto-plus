// DeckLab - the deck search. Pure, offline, deterministic, bounded.
//
// The whole owned pool cannot be enumerated: choosing six from eighty-two is a quarter of a billion
// combinations. So the search is explicitly approximate, and says so. Two stages:
//
//   Reduce. Cards are pruned only when another card of the same role is at least as good on every
//   decoded dimension AND on the composite, which is real dominance rather than a ranking cut. What
//   survives is then narrowed to a working pool that deliberately keeps the leader of every deck
//   dimension, not just the top composites, so an archetype that wants hint coverage or race bonus
//   still has its cards available to be chosen.
//
//   Enumerate. Every legal combination of the working pool is scored. That part is exhaustive over
//   the pool and is not exhaustive over the account, and searchCompleteness() states exactly that.
//
// Nothing here claims a global optimum. A deck is reported as the best DeckLab found under a named
// target with a stated pool, which is a claim that can be checked.

import { ownedCardInput, targetWeightedComposite, valueCard, type CardValueProfile } from "./cardValue.ts"
import { checkDeckLegality, DECK_DIMENSIONS, DECK_SIZE, scoreDeck, withBorrowValue, type DeckDimension, type DeckScore } from "./deck.ts"
import type { DeckTargetBuild } from "./deckTarget.ts"
import type { OwnedSupportInventorySnapshot, OwnedSupportCard } from "./inventory.ts"
import { levelCapFor, type SupportCardIndex } from "./supportCardData.ts"

/**
 * How wide the working pool is allowed to get before enumeration.
 *
 * Enumeration is combinatorial, so this is the knob that decides how approximate the answer is. At 24
 * a search takes a couple of seconds and evaluates about 120,000 decks; widening it does find better
 * decks, which is precisely why the result is reported as the best found over a stated pool rather
 * than as an optimum. Raise it with --pool-limit when the extra time is worth it.
 */
export const DEFAULT_POOL_LIMIT = 24

/**
 * How many scored decks the Pareto pass is allowed to consider.
 *
 * Pareto dominance is a pairwise test, so running it over every legal deck is quadratic: a pool of
 * twenty produces about thirty thousand decks and a billion comparisons. The candidate set is instead
 * the strongest decks by composite together with the outright leader of every Pareto dimension, so no
 * dimension can lose its champion to the cut. The frontier is therefore the frontier of that set, and
 * searchCompleteness says so rather than implying it is the frontier of everything.
 */
export const DEFAULT_FRONTIER_CANDIDATES = 400

/** Named deck archetypes, each with the deck dimension it optimises. */
export const DECK_ARCHETYPES = [
    "BEST_BALANCED",
    "BEST_TARGET_STATS",
    "BEST_SKILL_POINTS",
    "BEST_HINT_COVERAGE",
    "BEST_SCENARIO_SYNERGY",
    "BEST_CONSISTENCY",
    "BEST_NO_BORROW",
    "BEST_BORROW_UPGRADE",
] as const
export type DeckArchetype = (typeof DECK_ARCHETYPES)[number]

/** The deck dimensions a Pareto comparison runs over. Higher is better for every one of them. */
const PARETO_DIMENSIONS: readonly DeckDimension[] = [
    "targetStatCoverage",
    "trainingTypeBalance",
    "scenarioSynergy",
    "skillCoverage",
    "raceBonusTotal",
    "hintCoverage",
    "initialBondProfile",
    "friendshipRamp",
    "energyAndEventSupport",
]

export interface SearchCompleteness {
    readonly ownedCards: number
    readonly legalForScenario: number
    readonly afterDominancePrune: number
    readonly workingPool: number
    readonly combinationsEvaluated: number
    readonly legalDecksFound: number
    /** True only when the working pool is the whole legal pool, so nothing was left out. */
    readonly exhaustiveOverOwnedPool: boolean
    /** Legal decks the Pareto pass actually compared. */
    readonly frontierCandidates: number
    readonly notes: readonly string[]
}

export interface CardExclusion {
    readonly card: CardValueProfile
    readonly reason: string
}

/**
 * True when `a` is at least as good as `b` on every decoded dimension and strictly better somewhere.
 *
 * Same role only. Comparing a Speed card against a Wit card on dimensions alone would prune away the
 * only card of a type the target needs, which is the opposite of what dominance is for.
 */
export function dominates(a: CardValueProfile, b: CardValueProfile): boolean {
    if (a.card.supportType !== b.card.supportType) return false
    if (a.card.supportCardId === b.card.supportCardId) return false
    let strictlyBetter = false
    for (const key of Object.keys(a.dimensions) as (keyof typeof a.dimensions)[]) {
        if (a.dimensions[key] < b.dimensions[key]) return false
        if (a.dimensions[key] > b.dimensions[key]) strictlyBetter = true
    }
    if (a.composite < b.composite) return false
    if (a.composite > b.composite) strictlyBetter = true
    // A card that can hint a priority skill its rival cannot is not dominated, whatever the numbers say.
    const aSkills = new Set(a.targetFit.matchedPrioritySkillIds)
    for (const id of b.targetFit.matchedPrioritySkillIds) if (!aSkills.has(id)) return false
    // Nor is a card the scenario names as its own, since that value is real and uncounted.
    if (b.scenarioFit.scenarioSpecialCharacter && !a.scenarioFit.scenarioSpecialCharacter) return false
    return strictlyBetter
}

export function pruneDominated(pool: readonly CardValueProfile[]): { kept: CardValueProfile[]; removed: CardExclusion[] } {
    const kept: CardValueProfile[] = []
    const removed: CardExclusion[] = []
    for (const candidate of pool) {
        const dominator = pool.find((other) => dominates(other, candidate))
        if (dominator) removed.push({ card: candidate, reason: `dominated by ${dominator.card.displayName} on every decoded dimension` })
        else kept.push(candidate)
    }
    return { kept, removed }
}

/** The per-card dimensions an archetype leans on, kept in the pool so archetypes stay reachable. */
const DIVERSITY_KEYS = ["friendshipBonus", "trainingEffectiveness", "specialtyPriority", "initialBond", "raceBonus", "hintLevels", "hintFrequency", "skillPointBonus", "failureProtection"] as const

/**
 * Narrows a pruned pool to a working pool.
 *
 * Takes the strongest cards by target-weighted composite, then adds back the leader of every
 * dimension an archetype cares about, one card of every training type, and every card the scenario
 * names as its own. That last set matters: a scenario card can be unremarkable on the decoded numbers
 * and still be the point of the deck.
 */
export function selectWorkingPool(pool: readonly CardValueProfile[], build: DeckTargetBuild, limit: number): CardValueProfile[] {
    const ranked = [...pool].sort((a, b) => targetWeightedComposite(b, build) - targetWeightedComposite(a, build) || a.card.supportCardId - b.card.supportCardId)
    const chosen = new Map<number, CardValueProfile>()
    const take = (card: CardValueProfile | undefined) => {
        if (card) chosen.set(card.card.supportCardId, card)
    }

    for (const key of DIVERSITY_KEYS) {
        take([...pool].sort((a, b) => b.dimensions[key] - a.dimensions[key] || a.card.supportCardId - b.card.supportCardId)[0])
    }
    for (const type of new Set(pool.map((p) => p.card.supportType))) {
        take(ranked.find((p) => p.card.supportType === type))
    }
    for (const card of pool) if (card.scenarioFit.scenarioSpecialCharacter) take(card)

    for (const card of ranked) {
        if (chosen.size >= limit) break
        take(card)
    }
    return [...chosen.values()].sort((a, b) => a.card.supportCardId - b.card.supportCardId)
}

/** Every legal deck over a pool, scored. Exhaustive over the pool it is given. */
function enumerateDecks(index: SupportCardIndex, pool: readonly CardValueProfile[], build: DeckTargetBuild): { decks: DeckScore[]; combinations: number } {
    const decks: DeckScore[] = []
    let combinations = 0
    const current: CardValueProfile[] = []

    const walk = (start: number) => {
        if (current.length === DECK_SIZE) {
            combinations += 1
            const legality = checkDeckLegality(current, build)
            if (legality.legal) decks.push(scoreDeck(index, [...current], build))
            return
        }
        // Not enough cards left to fill the deck.
        if (pool.length - start < DECK_SIZE - current.length) return
        for (let i = start; i < pool.length; i++) {
            const candidate = pool[i]
            // Character uniqueness is checked as the deck is built rather than at the end, which is
            // what keeps the enumeration tractable on a pool with several cards per character.
            if (current.some((p) => p.card.characterId === candidate.card.characterId)) continue
            current.push(candidate)
            walk(i + 1)
            current.pop()
        }
    }
    walk(0)
    return { decks, combinations }
}

/**
 * The decks a Pareto pass runs over: the strongest by composite, plus the leader of every dimension.
 *
 * Including the per-dimension leaders is what keeps the reduction honest. A deck that wins hint
 * coverage outright is on the true frontier by definition, and taking only the top composites would
 * drop it.
 */
export function frontierCandidateSet(decks: readonly DeckScore[], limit: number): DeckScore[] {
    if (decks.length <= limit) return [...decks]
    const chosen = new Map<string, DeckScore>()
    const keyOf = (deck: DeckScore) =>
        deck.cards
            .map((c) => c.card.supportCardId)
            .sort((a, b) => a - b)
            .join(",")
    for (const key of PARETO_DIMENSIONS) {
        const leader = bestBy(decks, key)
        if (leader) chosen.set(keyOf(leader), leader)
    }
    for (const deck of [...decks].sort((a, b) => b.composite - a.composite)) {
        if (chosen.size >= limit) break
        chosen.set(keyOf(deck), deck)
    }
    return [...chosen.values()]
}

/** Non-dominated decks over the deck dimensions. */
export function paretoFrontier(decks: readonly DeckScore[]): DeckScore[] {
    const dominatesDeck = (a: DeckScore, b: DeckScore): boolean => {
        let strictlyBetter = false
        for (const key of PARETO_DIMENSIONS) {
            if (a.dimensions[key] < b.dimensions[key]) return false
            if (a.dimensions[key] > b.dimensions[key]) strictlyBetter = true
        }
        return strictlyBetter
    }
    return decks.filter((deck) => !decks.some((other) => dominatesDeck(other, deck)))
}

/** Archetype -> the deck dimension it maximises. BEST_BALANCED and the borrow pair are special. */
const ARCHETYPE_METRIC: Readonly<Partial<Record<DeckArchetype, DeckDimension>>> = {
    BEST_TARGET_STATS: "targetStatCoverage",
    BEST_SKILL_POINTS: "skillCoverage",
    BEST_HINT_COVERAGE: "hintCoverage",
    BEST_SCENARIO_SYNERGY: "scenarioSynergy",
    BEST_CONSISTENCY: "trainingTypeBalance",
}

export interface ArchetypeResult {
    readonly archetype: DeckArchetype
    readonly metric: DeckDimension | null
    readonly deck: DeckScore
}

export interface BorrowOption {
    readonly borrowed: CardValueProfile
    readonly displaced: CardValueProfile | null
    readonly deck: DeckScore
    /** Change in target stat coverage against the best no-borrow deck. */
    readonly improvement: number
    /** The deck dimensions this borrow improves, and by how much. */
    readonly improvedDimensions: Readonly<Partial<Record<DeckDimension, number>>>
}

export interface DeckSearchResult {
    readonly build: DeckTargetBuild
    readonly completeness: SearchCompleteness
    readonly excludedCards: readonly CardExclusion[]
    readonly workingPool: readonly CardValueProfile[]
    readonly bestNoBorrow: DeckScore | null
    readonly archetypes: readonly ArchetypeResult[]
    readonly frontier: readonly DeckScore[]
    readonly borrowOptions: readonly BorrowOption[]
    /** Set only when one deck leads every Pareto dimension outright. */
    readonly dominantDeck: DeckScore | null
}

function bestBy(decks: readonly DeckScore[], metric: DeckDimension): DeckScore | null {
    let best: DeckScore | null = null
    for (const deck of decks) {
        if (!best) {
            best = deck
            continue
        }
        const delta = deck.dimensions[metric] - best.dimensions[metric]
        if (delta > 0 || (delta === 0 && deck.composite > best.composite)) best = deck
    }
    return best
}

/** True when every deck scores the same on a metric, so naming a winner by it would be noise. */
function metricIsFlat(decks: readonly DeckScore[], metric: DeckDimension): boolean {
    if (decks.length < 2) return true
    const first = decks[0].dimensions[metric]
    return decks.every((deck) => deck.dimensions[metric] === first)
}

export interface DeckSearchOptions {
    readonly poolLimit?: number
    readonly frontierCandidates?: number
    /** Cards available to borrow. Empty means borrow analysis is skipped. */
    readonly borrowCandidates?: readonly OwnedSupportCard[]
    readonly noBorrow?: boolean
}

/**
 * The best way to fit one borrowed card into an existing deck.
 *
 * A borrowed card takes a slot rather than adding one, so it is tried against each card already in
 * the deck and the swap that raises target stat coverage most is kept. Swaps that would leave two
 * cards of the same character, or that break any other legality rule, are skipped rather than scored.
 *
 * Extracted so the build-aware borrow ranking in src/lib/buildBudget/ evaluates borrows under exactly
 * the same swap rule this search uses. Two different swap rules would make the two rankings
 * incomparable while looking as though they disagreed about the cards.
 *
 * Returns null when the card is illegal for the target or when no legal swap exists. An improvement
 * of zero or below is still returned: whether a non-improving borrow is worth reporting is the
 * caller's decision, not this function's.
 */
export function bestBorrowSwap(index: SupportCardIndex, baseDeck: DeckScore, borrowedProfile: CardValueProfile, build: DeckTargetBuild): BorrowOption | null {
    if (!borrowedProfile.scenarioFit.legal) return null
    if (build.traineeCharaId !== null && borrowedProfile.card.characterId === build.traineeCharaId) return null

    let best: BorrowOption | null = null
    for (const displaced of baseDeck.cards) {
        const rest = baseDeck.cards.filter((p) => p.card.supportCardId !== displaced.card.supportCardId)
        if (rest.some((p) => p.card.characterId === borrowedProfile.card.characterId)) continue
        const deck = [...rest, borrowedProfile]
        const legality = checkDeckLegality(deck, build)
        if (!legality.legal) continue
        const scored = withBorrowValue(scoreDeck(index, deck, build), baseDeck, displaced)
        const improvement = Number((scored.dimensions.targetStatCoverage - baseDeck.dimensions.targetStatCoverage).toFixed(4))
        const improvedDimensions: Partial<Record<DeckDimension, number>> = {}
        for (const key of DECK_DIMENSIONS) {
            const delta = Number((scored.dimensions[key] - baseDeck.dimensions[key]).toFixed(4))
            if (delta > 0) improvedDimensions[key] = delta
        }
        const option: BorrowOption = { borrowed: borrowedProfile, displaced, deck: scored, improvement, improvedDimensions }
        // Ties break on the displaced card's own id so the same deck and the same borrow always
        // produce the same swap, whatever order the deck's cards happen to be in.
        if (!best || option.improvement > best.improvement || (option.improvement === best.improvement && displaced.card.supportCardId < best.displaced!.card.supportCardId)) best = option
    }
    return best
}

export function searchDecks(
    index: SupportCardIndex,
    inventory: OwnedSupportInventorySnapshot,
    build: DeckTargetBuild,
    options: DeckSearchOptions = {},
): DeckSearchResult {
    const poolLimit = options.poolLimit ?? DEFAULT_POOL_LIMIT
    const notes: string[] = []

    const valued = inventory.cards.map((card) => valueCard(index, ownedCardInput(card), build))
    const excluded: CardExclusion[] = []
    const legalPool: CardValueProfile[] = []
    for (const profile of valued) {
        if (!profile.scenarioFit.legal) {
            excluded.push({ card: profile, reason: `${build.scenarioName} forbids this card` })
            continue
        }
        if (build.traineeCharaId !== null && profile.card.characterId === build.traineeCharaId) {
            excluded.push({ card: profile, reason: "training this character locks out her own support card" })
            continue
        }
        legalPool.push(profile)
    }

    const { kept, removed } = pruneDominated(legalPool)
    excluded.push(...removed)

    const workingPool = selectWorkingPool(kept, build, poolLimit)
    if (workingPool.length < kept.length) {
        notes.push(`${kept.length - workingPool.length} undominated cards were left out of the working pool of ${poolLimit}, so the search is approximate over the account`)
    }
    for (const card of kept) {
        if (!workingPool.some((p) => p.card.supportCardId === card.card.supportCardId)) {
            excluded.push({ card, reason: "outside the working pool" })
        }
    }

    const { decks, combinations } = enumerateDecks(index, workingPool, build)
    if (!decks.length) notes.push("no legal deck could be built from the working pool")

    const bestNoBorrow = bestBy(decks, "targetStatCoverage")

    const archetypes: ArchetypeResult[] = []
    if (decks.length) {
        for (const [archetype, metric] of Object.entries(ARCHETYPE_METRIC) as [DeckArchetype, DeckDimension][]) {
            if (metricIsFlat(decks, metric)) {
                notes.push(`${archetype} was suppressed: every legal deck scores the same on ${metric}`)
                continue
            }
            const deck = bestBy(decks, metric)
            if (deck) archetypes.push({ archetype, metric, deck })
        }
        // Balanced is the best composite rather than any single dimension, which is what "balanced"
        // means once the composite already folds in every dimension at its editorial weight.
        const balanced = [...decks].sort((a, b) => b.composite - a.composite)[0]
        if (balanced) archetypes.unshift({ archetype: "BEST_BALANCED", metric: null, deck: balanced })
        if (bestNoBorrow) archetypes.push({ archetype: "BEST_NO_BORROW", metric: "targetStatCoverage", deck: bestNoBorrow })
    }

    const borrowOptions: BorrowOption[] = []
    const borrowCandidates = options.noBorrow ? [] : (options.borrowCandidates ?? [])
    if (borrowCandidates.length && bestNoBorrow) {
        for (const candidate of borrowCandidates) {
            const borrowedProfile = valueCard(index, { ...ownedCardInput(candidate, true), owned: false }, build)
            const best = bestBorrowSwap(index, bestNoBorrow, borrowedProfile, build)
            if (best) borrowOptions.push(best)
        }
        borrowOptions.sort((a, b) => b.improvement - a.improvement || a.borrowed.card.supportCardId - b.borrowed.card.supportCardId)
        const top = borrowOptions[0]
        if (top && top.improvement > 0) archetypes.push({ archetype: "BEST_BORROW_UPGRADE", metric: "borrowValue", deck: top.deck })
        else if (top) notes.push("no borrow candidate improved on the best no-borrow deck")
    } else if (!borrowCandidates.length && !options.noBorrow) {
        notes.push("no borrow candidates were supplied, so borrow analysis was skipped")
    }

    const frontierCandidateLimit = options.frontierCandidates ?? DEFAULT_FRONTIER_CANDIDATES
    const candidates = frontierCandidateSet(decks, frontierCandidateLimit)
    if (candidates.length < decks.length) {
        notes.push(`the Pareto frontier was computed over ${candidates.length} of ${decks.length} legal decks: the strongest by composite plus the leader of every dimension`)
    }
    const frontier = paretoFrontier(candidates).sort((a, b) => b.composite - a.composite)
    const dominantDeck = frontier.length === 1 && candidates.length > 1 ? frontier[0] : null

    return {
        build,
        completeness: {
            ownedCards: inventory.cards.length,
            legalForScenario: legalPool.length,
            afterDominancePrune: kept.length,
            workingPool: workingPool.length,
            combinationsEvaluated: combinations,
            legalDecksFound: decks.length,
            exhaustiveOverOwnedPool: workingPool.length === kept.length,
            frontierCandidates: candidates.length,
            notes,
        },
        excludedCards: excluded,
        workingPool,
        bestNoBorrow,
        archetypes,
        frontier,
        borrowOptions,
        dominantDeck,
    }
}

/**
 * A hypothetical borrow pool: every catalogue card of the named types, fully limit-broken and levelled.
 *
 * Used when no real friend-list data is available. It answers "what would be worth borrowing", not
 * "what can you borrow", and every report that uses it says so.
 */
export function hypotheticalBorrowPool(index: SupportCardIndex, options: { readonly onlySSR?: boolean } = {}): OwnedSupportCard[] {
    const out: OwnedSupportCard[] = []
    for (const card of index.data.cards) {
        if (!card.title) continue
        if (options.onlySSR !== false && card.rarity !== "SSR") continue
        const cap = levelCapFor(index.data, card.rarity, 4)
        out.push({
            card: {
                supportCardId: card.id,
                characterId: card.charaId,
                characterName: index.characterName(card.charaId),
                displayName: `${index.characterName(card.charaId)} [${card.title}]`,
                title: card.title,
                rarity: card.rarity,
                supportType: card.supportType,
            },
            limitBreak: 4,
            level: cap,
            levelCap: cap,
            unlevelledHeadroom: 0,
            owned: true,
            matchMethod: "CHARACTER_AND_TITLE",
            warnings: [],
            evidenceSource: "hypothetical fully limit-broken borrow",
        })
    }
    return out.sort((a, b) => a.card.supportCardId - b.card.supportCardId)
}

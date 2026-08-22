// DeckLab - resolving one observed card name band onto exactly one catalogue card. Pure, offline,
// deterministic, fail-closed.
//
// A printed card name is read off the screen and arrives noisy: an OCR that turns "Is" into "ls", a
// trailing "!" into an "l", a decorative "♪" into a stray glyph, or a level badge into "D1" appended
// to the title. The exact `character|title` key the owned inventory joins on cannot survive that, so a
// perfectly identifiable card fails closed as unresolved. This module adds the one recovery that is
// safe: it first pins the character exactly, then recovers the card only from within that one
// character's finite, known set of cards. It never fuzzy-matches a title against the whole catalogue,
// and it refuses rather than guesses whenever two of a character's cards are both plausible.
//
// Why observed support type is not a filter here. On the borrow picker the card's training type is a
// small coloured icon, and the live scan proves it is read wrong often (a Stamina card seen as Speed,
// a Friend card seen as Power, a Guts card seen as Wit). Worse than useless: for a character that owns
// both a Guts and a Wit card, trusting a wrong "Wit" read would pick the wrong card. So type is
// carried as corroboration only and never decides identity. Rarity reads are far more reliable and are
// allowed to block a fuzzy recovery, but never a strong exact-title match, where the title alone is the
// identity.

import { normalizeName, SUPPORT_RARITIES, type SupportCardIndex, type SupportCardRecord, type SupportRarity } from "./supportCardData.ts"

/** How a row was resolved onto its card, strongest first. */
export const CARD_RESOLUTION_PATHS = ["EXACT_TITLE", "TITLE_ONLY", "CHARACTER_LOCAL_FUZZY", "CHARACTER_AND_RARITY"] as const
export type CardResolutionPath = (typeof CARD_RESOLUTION_PATHS)[number]

/** Why a row did not resolve onto exactly one card. */
export const CARD_RESOLUTION_REJECTS = ["NO_CHARACTER_OR_TITLE", "NO_CANDIDATE", "AMBIGUOUS_TITLE", "AMBIGUOUS_FUZZY", "LOW_SIMILARITY", "RARITY_CONFLICT"] as const
export type CardResolutionReject = (typeof CARD_RESOLUTION_REJECTS)[number]

/** One observed name band, before any catalogue resolution. */
export interface ObservedCardIdentity {
    readonly character: string
    readonly title: string
    readonly rarity: string | null
    readonly supportType: string | null
}

export interface CardIdentityMatch {
    readonly card: SupportCardRecord
    readonly path: CardResolutionPath
    /** Title similarity that won the match: 1 for an exact or title-only match, 0..1 for a fuzzy one. */
    readonly score: number
    /** Similarity lead over the second-best candidate: 1 when there was only one candidate or the match was exact. */
    readonly margin: number
    /** Whether the observed rarity agreed with the card, or null when no rarity was observed. */
    readonly rarityCorroborated: boolean | null
    /** Whether the observed support type agreed with the card, or null when none was observed. Never used to decide identity. */
    readonly typeCorroborated: boolean | null
}

export interface CardIdentityReject {
    readonly reason: CardResolutionReject
    readonly detail: string
}

export interface CardIdentityOptions {
    /** Allow character-local fuzzy recovery of a noisy title. Off makes this an exact-only resolver. */
    readonly allowFuzzy?: boolean
    /** Minimum title similarity a fuzzy match must reach. */
    readonly minSimilarity?: number
    /** Minimum similarity lead a fuzzy winner must hold over the second-best candidate of the same character. */
    readonly minMargin?: number
}

/** Fuzzy recovery is deliberately conservative: a clear win over a character's other cards, or nothing. */
export const DEFAULT_MIN_SIMILARITY = 0.66
export const DEFAULT_MIN_MARGIN = 0.15

/** Levenshtein edit distance between two strings. */
function editDistance(a: string, b: string): number {
    if (a === b) return 0
    if (!a.length) return b.length
    if (!b.length) return a.length
    let prev = new Array(b.length + 1)
    let curr = new Array(b.length + 1)
    for (let j = 0; j <= b.length; j++) prev[j] = j
    for (let i = 1; i <= a.length; i++) {
        curr[0] = i
        for (let j = 1; j <= b.length; j++) {
            const cost = a[i - 1] === b[j - 1] ? 0 : 1
            curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        ;[prev, curr] = [curr, prev]
    }
    return prev[b.length]
}

/** Edit-distance similarity in 0..1, where 1 is identical. Two empty strings are identical. */
export function titleSimilarity(a: string, b: string): number {
    const longest = Math.max(a.length, b.length)
    if (longest === 0) return 1
    return 1 - editDistance(a, b) / longest
}

function isKnownRarity(value: string | null): value is SupportRarity {
    return value !== null && (SUPPORT_RARITIES as readonly string[]).includes(value)
}

function corroboration(observed: ObservedCardIdentity, card: SupportCardRecord): { rarityCorroborated: boolean | null; typeCorroborated: boolean | null } {
    return {
        rarityCorroborated: isKnownRarity(observed.rarity) ? observed.rarity === card.rarity : null,
        typeCorroborated: observed.supportType ? observed.supportType === card.supportType : null,
    }
}

/**
 * Resolves one observed name band onto exactly one catalogue card, or says why it will not.
 *
 * The ladder is additive and each rung is stricter about what counts as one card:
 *   1. EXACT_TITLE          - the character is known and one of its cards has this exact normalized title.
 *   2. TITLE_ONLY           - the character name is not in the catalogue's character table (a group or
 *                             staff card recorded under a different name), but the title is unique catalogue-wide.
 *   3. CHARACTER_LOCAL_FUZZY - the character is known and exactly one of its cards is a clear title match
 *                             once OCR noise is allowed for; the win must clear both a similarity floor and
 *                             a margin over every other card that character owns.
 *   4. CHARACTER_AND_RARITY - the row carried no title at all (an R card), and the character owns exactly
 *                             one card of the observed rarity.
 *
 * Support type is recorded as corroboration and never decides identity. Rarity may block a fuzzy
 * recovery but is not allowed to overturn an exact-title match.
 */
export function resolveCardIdentity(index: SupportCardIndex, observed: ObservedCardIdentity, options: CardIdentityOptions = {}): CardIdentityMatch | CardIdentityReject {
    const allowFuzzy = options.allowFuzzy ?? true
    const minSimilarity = options.minSimilarity ?? DEFAULT_MIN_SIMILARITY
    const minMargin = options.minMargin ?? DEFAULT_MIN_MARGIN

    const charKey = normalizeName(observed.character)
    const titleKey = normalizeName(observed.title)
    if (!charKey && !titleKey) return { reason: "NO_CHARACTER_OR_TITLE", detail: "row names neither a character nor a title" }

    const charCards = charKey ? (index.byNormalizedCharacter.get(charKey) ?? []) : []
    const accept = (card: SupportCardRecord, path: CardResolutionPath, score: number, margin: number): CardIdentityMatch => ({ card, path, score, margin, ...corroboration(observed, card) })

    // 1. Exact title within the resolved character. The strongest match; rarity/type never overturn it.
    if (charKey && titleKey && charCards.length) {
        const exact = charCards.filter((c) => normalizeName(c.title) === titleKey)
        if (exact.length === 1) return accept(exact[0], "EXACT_TITLE", 1, 1)
        if (exact.length > 1) {
            const byRarity = isKnownRarity(observed.rarity) ? exact.filter((c) => c.rarity === observed.rarity) : exact
            if (byRarity.length === 1) return accept(byRarity[0], "EXACT_TITLE", 1, 1)
            return { reason: "AMBIGUOUS_TITLE", detail: `character and title match ${exact.length} cards` }
        }
    }

    // 2. Title alone, only for a character the catalogue's character table does not name. Unique catalogue-wide or nothing.
    if (titleKey && !charCards.length) {
        const byTitle: SupportCardRecord[] = []
        for (const [key, cards] of index.byNormalizedKey) if (key.endsWith(`|${titleKey}`)) byTitle.push(...cards)
        if (byTitle.length === 1) return accept(byTitle[0], "TITLE_ONLY", 1, 1)
        if (byTitle.length > 1) {
            const byRarity = isKnownRarity(observed.rarity) ? byTitle.filter((c) => c.rarity === observed.rarity) : byTitle
            if (byRarity.length === 1) return accept(byRarity[0], "TITLE_ONLY", 1, 1)
            return { reason: "AMBIGUOUS_TITLE", detail: `title alone matches ${byTitle.length} cards` }
        }
    }

    // 3. Character-local fuzzy recovery of a noisy title. Only ever chooses among the one character's own cards.
    if (allowFuzzy && charKey && titleKey && charCards.length) {
        const scored = charCards
            .filter((c) => c.title)
            .map((c) => ({ card: c, sim: titleSimilarity(titleKey, normalizeName(c.title)) }))
            .sort((a, b) => b.sim - a.sim || a.card.id - b.card.id)
        if (scored.length) {
            const best = scored[0]
            const second = scored[1]
            const margin = second ? best.sim - second.sim : 1
            if (best.sim < minSimilarity) return { reason: "LOW_SIMILARITY", detail: `best title match for this character scored ${best.sim.toFixed(3)}, below ${minSimilarity}` }
            if (scored.length > 1 && margin < minMargin) return { reason: "AMBIGUOUS_FUZZY", detail: `two of this character's cards are within ${minMargin} similarity (${best.sim.toFixed(3)} vs ${second.sim.toFixed(3)})` }
            // A confidently-read rarity that disagrees blocks a fuzzy recovery: identity is uncertain here, so a conflict is a real doubt, not a misread of a card already pinned by an exact title.
            if (isKnownRarity(observed.rarity) && best.card.rarity !== observed.rarity) return { reason: "RARITY_CONFLICT", detail: `fuzzy match is ${best.card.rarity}, observed ${observed.rarity}` }
            return accept(best.card, "CHARACTER_LOCAL_FUZZY", best.sim, margin)
        }
    }

    // 4. An R card carries no title. The character plus the observed rarity must name exactly one card.
    if (charKey && !titleKey && isKnownRarity(observed.rarity) && charCards.length) {
        const pool = charCards.filter((c) => c.rarity === observed.rarity)
        if (pool.length === 1) return accept(pool[0], "CHARACTER_AND_RARITY", 1, 1)
        if (pool.length > 1) return { reason: "AMBIGUOUS_TITLE", detail: `character and rarity match ${pool.length} cards` }
    }

    return { reason: "NO_CANDIDATE", detail: charKey || titleKey ? "no catalogue card matches this character and title" : "row names neither a character nor a title" }
}

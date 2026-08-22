// ParentLab PL-R4 - parent-pair components, the Pareto frontier and the recommendation rule. Pure,
// offline, deterministic, read-only.
//
// Inheritance takes two parents, so a ranking of single Veterans answers the wrong question. This
// module scores PAIRS, and it deliberately refuses to collapse a pair onto one number:
//
//   Every dimension is monotone "higher is better" and is reported with its own value. The frontier
//   is a set of category winners, each verified non-dominated against every other pair, not a top-of-
//   the-list pick. An overall recommendation is emitted only when one pair dominates every other pair
//   on every known dimension, which is a provable statement rather than a preference.
//
//   Coverage and stacking are kept as separate dimensions. Coverage takes the better of the two
//   parents per factor, so a complementary pair beats a redundant one; stacking sums both parents,
//   which is the reading that matters for blue factors. A redundant strong pair and a complementary
//   pair genuinely do not dominate each other, and the output says so instead of picking a side.
//
// The one figure that could be mistaken for a decoded affinity total is never produced. Pair affinity
// is reported as named components with the unknown components listed alongside them.

import { pairwiseRelation, type SuccessionRelationIndex } from "./affinityData.ts"
import { affinityBreakdown, type AffinityComponent, type AffinityComponentBreakdown } from "./affinityEvidence.ts"
import { allMatches, type CandidateFactorMatch, type FactorFamily, type ParentCandidate } from "./parentCandidate.ts"
import type { FactorScarcityIndex } from "./retentionTypes.ts"
import { DISTANCE_APTITUDE_FACTOR, RUNNING_STYLE_APTITUDE_FACTOR, SURFACE_APTITUDE_FACTOR, type TargetBuild } from "./targetBuild.ts"

/**
 * The dominance dimensions, in the fixed order they are reported.
 *
 * Rating, stat totals and career results are deliberately absent. A dominance dimension decides which
 * pairs can be discarded, and a generic Veteran with a high rating must never be able to discard one
 * carrying the rare factor the build needs.
 */
export const PAIR_DIMENSIONS = [
    /** Base relation points from the target trainee to each parent, summed. */
    "knownAffinityPoints",
    /** Per distinct priority blue factor, the better of the two parents' stars, summed. */
    "statCoverageStars",
    /** Both parents' matched blue stars summed. Blue factors stack, so redundancy is real value here. */
    "statStackedStars",
    /** Per distinct priority pink factor, the better of the two parents' stars, summed. */
    "aptitudeCoverageStars",
    /** Per distinct priority green factor, the better of the two parents' stars, summed. */
    "uniqueCoverageStars",
    /** Per distinct priority white factor, the better of the two parents' stars, summed. */
    "whiteCoverageStars",
    /** Scarcity-weighted value of the distinct priority factors the pair covers. */
    "scarcityValue",
    /** Matched priority stars sitting in both parents' Legacy Origin blocks. */
    "legacySupportStars",
    /** Distinct priority factors of any family the pair covers between them. */
    "distinctPriorityCoverage",
    /** Coverage net of the factors both parents duplicate. */
    "nonRedundantCoverage",
    /** 0 same character, 1 same character different outfit, 2 different characters. */
    "lineageDiversity",
] as const
export type PairDimension = (typeof PAIR_DIMENSIONS)[number]

export type PairDimensions = Readonly<Record<PairDimension, number>>

/**
 * Ordinal scarcity weights.
 *
 * These are deliberately NOT probabilities. The account inventory supports "how many owned Veterans
 * can supply this factor at this star count", which is a count, and turning a count into a reroll
 * likelihood would be a fabricated number. The weights only order.
 */
export const SCARCITY_WEIGHT_UNIQUE = 4
export const SCARCITY_WEIGHT_SCARCE = 2
export const SCARCITY_WEIGHT_COMMON = 1

function scarcityWeight(carriers: number): number {
    if (carriers <= 0) return 0
    if (carriers === 1) return SCARCITY_WEIGHT_UNIQUE
    if (carriers <= 3) return SCARCITY_WEIGHT_SCARCE
    return SCARCITY_WEIGHT_COMMON
}

/** Confidence in a pair's advisory, using the repository's four-level vocabulary. */
export const PAIR_CONFIDENCES = ["HIGH", "MEDIUM", "LOW", "INSUFFICIENT"] as const
export type PairConfidence = (typeof PAIR_CONFIDENCES)[number]

export const PAIR_CONFIDENCE_RANK: Readonly<Record<PairConfidence, number>> = { HIGH: 3, MEDIUM: 2, LOW: 1, INSUFFICIENT: 0 }

/** Why a pair reads the way it does. Codes, so a consumer keys on them rather than on prose. */
export const PAIR_REASON_CODES = [
    "COVERS_TARGET_DISTANCE_APTITUDE",
    "COVERS_TARGET_SURFACE_APTITUDE",
    "COVERS_TARGET_RUNNING_STYLE_APTITUDE",
    "CARRIES_RARE_RELEVANT_FACTOR",
    "STRONG_KNOWN_BASE_AFFINITY",
    "WEAK_KNOWN_BASE_AFFINITY",
    "COMPLEMENTARY_PRIORITY_COVERAGE",
    "REDUNDANT_PRIORITY_FACTORS",
    "SAME_CHARACTER_PAIR",
    "SAME_OUTFIT_PAIR",
    "PARENT_SHARES_TARGET_CHARACTER",
    "LEGACY_SUPPORT_PRESENT",
    "NO_PRIORITY_FACTOR_COVERAGE",
    "AFFINITY_COMPONENT_UNRESOLVED",
    "NO_TRUSTED_FACTOR_CAPTURE",
    "ANCESTOR_IDENTITY_UNKNOWN",
    "SCARCITY_NOT_ACCOUNT_WIDE",
] as const
export type PairReasonCode = (typeof PAIR_REASON_CODES)[number]

/** One factor the pair covers, and which parent supplied the better copy. */
export interface PairFactorCoverage {
    readonly factorKey: string
    readonly family: FactorFamily
    readonly canonicalName: string
    /** Best stars across the pair. */
    readonly stars: number
    /** Sum of both parents' stars on this factor. Equals `stars` when only one parent carries it. */
    readonly stackedStars: number
    readonly carriedByA: boolean
    readonly carriedByB: boolean
    readonly observedCarriersAtStars: number
    readonly scarcityWeight: number
}

/** One ranked pair, with every component preserved. */
export interface ParentPair {
    /** Deterministic identity for the pair: the two parents' keys, sorted. */
    readonly pairKey: string
    readonly parentA: ParentCandidate
    readonly parentB: ParentCandidate
    readonly dimensions: PairDimensions
    readonly affinity: AffinityComponentBreakdown
    /** True when both parents' relation to the target resolved. */
    readonly affinityResolved: boolean
    /** The parent-to-parent relation. Computable, but whether the game counts it is not decoded, so
     * it is reported here and never added into `affinity.knownPointsTotal`. */
    readonly parentPairRelationPoints: number | null
    readonly coverage: readonly PairFactorCoverage[]
    /** Priority factors both parents carry. Reported for explanation; not a dominance dimension. */
    readonly sharedPriorityFactors: number
    readonly confidence: PairConfidence
    readonly reasonCodes: readonly PairReasonCode[]
    readonly explanation: string
    /** Evidence gaps carried up from either parent, deduplicated and sorted. */
    readonly gaps: readonly string[]
}

function candidateKey(c: ParentCandidate): string {
    return c.rosterFingerprint ?? `scanIndex:${c.scanIndex}`
}

function lineageDiversity(a: ParentCandidate, b: ParentCandidate): number {
    const sameCharacter = a.character !== null && b.character !== null && a.character === b.character
    if (!sameCharacter) return 2
    const sameOutfit = (a.outfit ?? "") === (b.outfit ?? "")
    return sameOutfit ? 0 : 1
}

function coverageOf(a: ParentCandidate, b: ParentCandidate): readonly PairFactorCoverage[] {
    const byKey = new Map<string, PairFactorCoverage>()
    const add = (match: CandidateFactorMatch, side: "A" | "B") => {
        const held = byKey.get(match.factorKey)
        if (!held) {
            byKey.set(match.factorKey, {
                factorKey: match.factorKey,
                family: match.family,
                canonicalName: match.canonicalName,
                stars: match.stars,
                stackedStars: match.stars,
                carriedByA: side === "A",
                carriedByB: side === "B",
                observedCarriersAtStars: match.observedCarriersAtStars,
                scarcityWeight: scarcityWeight(match.observedCarriersAtStars),
            })
            return
        }
        const stars = Math.max(held.stars, match.stars)
        byKey.set(match.factorKey, {
            ...held,
            stars,
            stackedStars: held.stackedStars + match.stars,
            carriedByA: held.carriedByA || side === "A",
            carriedByB: held.carriedByB || side === "B",
            // The scarcity of the covered factor is measured at the star count the pair can actually
            // supply, so a pair that raises the star count is scored at the rarer, higher floor.
            observedCarriersAtStars: stars === match.stars ? match.observedCarriersAtStars : held.observedCarriersAtStars,
            scarcityWeight: scarcityWeight(stars === match.stars ? match.observedCarriersAtStars : held.observedCarriersAtStars),
        })
    }
    for (const m of allMatches(a.relevance)) add(m, "A")
    for (const m of allMatches(b.relevance)) add(m, "B")
    return [...byKey.values()].sort((x, y) => (x.factorKey < y.factorKey ? -1 : x.factorKey > y.factorKey ? 1 : 0))
}

function familyCoverageStars(coverage: readonly PairFactorCoverage[], family: FactorFamily): number {
    let sum = 0
    for (const c of coverage) if (c.family === family) sum += c.stars
    return sum
}

function familyStackedStars(coverage: readonly PairFactorCoverage[], family: FactorFamily): number {
    let sum = 0
    for (const c of coverage) if (c.family === family) sum += c.stackedStars
    return sum
}

/** Builds one pair record. Pure: it reads the two candidates and the relation index and nothing else. */
export function buildParentPair(
    a: ParentCandidate,
    b: ParentCandidate,
    build: TargetBuild,
    index: SuccessionRelationIndex,
    scarcity: FactorScarcityIndex,
    /** Median of the resolved single-parent relation points for this target, used only to label a
     * pair's affinity as strong or weak relative to what this account can actually field. */
    affinityMedian: number | null,
): ParentPair {
    const [first, second] = candidateKey(a) <= candidateKey(b) ? [a, b] : [b, a]
    const pairKey = `${candidateKey(first)}|${candidateKey(second)}`

    const coverage = coverageOf(first, second)
    const shared = coverage.filter((c) => c.carriedByA && c.carriedByB).length
    const distinct = coverage.length

    const affinityComponents: AffinityComponent[] = []
    if (first.affinity.resolved && first.affinity.points !== null) {
        affinityComponents.push({ id: "TARGET_TO_PARENT_A", label: `${build.targetTrainee} to ${first.character ?? "unknown"}`, points: first.affinity.points, sharedRelationTypes: first.affinity.sharedRelationTypes ?? 0 })
    }
    if (second.affinity.resolved && second.affinity.points !== null) {
        affinityComponents.push({ id: "TARGET_TO_PARENT_B", label: `${build.targetTrainee} to ${second.character ?? "unknown"}`, points: second.affinity.points, sharedRelationTypes: second.affinity.sharedRelationTypes ?? 0 })
    }
    const affinityResolved = first.affinity.resolved && second.affinity.resolved
    const affinity = affinityBreakdown(affinityComponents)

    const parentRelation = pairwiseRelation(index, first.charaId, second.charaId)

    const dimensions: PairDimensions = {
        knownAffinityPoints: affinityResolved ? affinity.knownPointsTotal : 0,
        statCoverageStars: familyCoverageStars(coverage, "stat"),
        statStackedStars: familyStackedStars(coverage, "stat"),
        aptitudeCoverageStars: familyCoverageStars(coverage, "aptitude"),
        uniqueCoverageStars: familyCoverageStars(coverage, "unique"),
        whiteCoverageStars: familyCoverageStars(coverage, "white"),
        scarcityValue: coverage.reduce((sum, c) => sum + c.scarcityWeight * c.stars, 0),
        legacySupportStars: first.legacy.totalPriorityStars + second.legacy.totalPriorityStars,
        distinctPriorityCoverage: distinct,
        nonRedundantCoverage: distinct - shared,
        lineageDiversity: lineageDiversity(first, second),
    }

    const trusted = first.selfFactorsTrusted && second.selfFactorsTrusted
    const identified = first.rosterFingerprint !== null && second.rosterFingerprint !== null
    const buildHasPriorities = !build.gaps.includes("NO_PRIORITY_FACTORS")
    let confidence: PairConfidence
    if (!identified) confidence = "INSUFFICIENT"
    else if (!trusted) confidence = "LOW"
    else if (!affinityResolved || !scarcity.accountWide || !buildHasPriorities) confidence = "MEDIUM"
    else confidence = "HIGH"

    const reasonCodes = new Set<PairReasonCode>()
    // Matched against the specific canonical name each build axis implies, not against the whole pink
    // priority list, so a surface match is never reported as a distance match.
    const covered = new Set(coverage.map((c) => `${c.family}:${c.canonicalName.toUpperCase()}`))
    if (build.distance && covered.has(`aptitude:${DISTANCE_APTITUDE_FACTOR[build.distance].toUpperCase()}`)) reasonCodes.add("COVERS_TARGET_DISTANCE_APTITUDE")
    if (build.surface && covered.has(`aptitude:${SURFACE_APTITUDE_FACTOR[build.surface].toUpperCase()}`)) reasonCodes.add("COVERS_TARGET_SURFACE_APTITUDE")
    if (build.runningStyle && covered.has(`aptitude:${RUNNING_STYLE_APTITUDE_FACTOR[build.runningStyle].toUpperCase()}`)) reasonCodes.add("COVERS_TARGET_RUNNING_STYLE_APTITUDE")
    if (coverage.some((c) => c.observedCarriersAtStars > 0 && c.observedCarriersAtStars <= 3)) reasonCodes.add("CARRIES_RARE_RELEVANT_FACTOR")
    if (affinityResolved && affinityMedian !== null) {
        if (affinity.knownPointsTotal >= affinityMedian * 2) reasonCodes.add("STRONG_KNOWN_BASE_AFFINITY")
        else if (affinity.knownPointsTotal < affinityMedian) reasonCodes.add("WEAK_KNOWN_BASE_AFFINITY")
    }
    if (distinct > 0 && shared === 0) reasonCodes.add("COMPLEMENTARY_PRIORITY_COVERAGE")
    if (shared > 0) reasonCodes.add("REDUNDANT_PRIORITY_FACTORS")
    if (dimensions.lineageDiversity <= 1) reasonCodes.add("SAME_CHARACTER_PAIR")
    if (dimensions.lineageDiversity === 0) reasonCodes.add("SAME_OUTFIT_PAIR")
    if (first.sameCharacterAsTarget || second.sameCharacterAsTarget) reasonCodes.add("PARENT_SHARES_TARGET_CHARACTER")
    if (dimensions.legacySupportStars > 0) reasonCodes.add("LEGACY_SUPPORT_PRESENT")
    if (distinct === 0) reasonCodes.add("NO_PRIORITY_FACTOR_COVERAGE")
    if (!affinityResolved) reasonCodes.add("AFFINITY_COMPONENT_UNRESOLVED")
    if (!trusted) reasonCodes.add("NO_TRUSTED_FACTOR_CAPTURE")
    if ((first.legacy.blocksObserved ?? 0) > 0 || (second.legacy.blocksObserved ?? 0) > 0) reasonCodes.add("ANCESTOR_IDENTITY_UNKNOWN")
    if (!scarcity.accountWide) reasonCodes.add("SCARCITY_NOT_ACCOUNT_WIDE")

    const gaps = [...new Set<string>([...first.gaps, ...second.gaps])].sort()

    return {
        pairKey,
        parentA: first,
        parentB: second,
        dimensions,
        affinity,
        affinityResolved,
        parentPairRelationPoints: parentRelation ? parentRelation.points : null,
        coverage,
        sharedPriorityFactors: shared,
        confidence,
        reasonCodes: [...reasonCodes].sort(),
        explanation: explainPair(first, second, dimensions, coverage, shared, affinityResolved, affinity, build),
        gaps,
    }
}

function describe(c: ParentCandidate): string {
    return `${c.character ?? "unknown"}${c.outfit ? ` (${c.outfit})` : ""}`
}

function explainPair(
    a: ParentCandidate,
    b: ParentCandidate,
    dimensions: PairDimensions,
    coverage: readonly PairFactorCoverage[],
    shared: number,
    affinityResolved: boolean,
    affinity: AffinityComponentBreakdown,
    build: TargetBuild,
): string {
    const parts: string[] = []
    parts.push(`${describe(a)} + ${describe(b)} for ${build.label}.`)
    if (affinityResolved) {
        const detail = affinity.known.map((c) => `${c.points}`).join(" + ")
        parts.push(`Known base relation to the target: ${detail} = ${affinity.knownPointsTotal} points; grandparent, race-result and aggregation components are not decoded, so this is not the game's affinity total.`)
    } else {
        parts.push("Known base relation to the target did not resolve for at least one parent.")
    }
    if (coverage.length === 0) {
        parts.push("Neither parent carries a priority factor for this build.")
    } else {
        const named = coverage
            .slice()
            .sort((x, y) => y.stars - x.stars || (x.factorKey < y.factorKey ? -1 : 1))
            .slice(0, 6)
            .map((c) => `${c.canonicalName} ${c.stars}*${c.carriedByA && c.carriedByB ? " (both)" : ""}`)
            .join(", ")
        parts.push(`Covers ${coverage.length} priority factor${coverage.length === 1 ? "" : "s"}: ${named}.`)
        parts.push(shared === 0 ? "The two parents duplicate none of them." : `${shared} of them are carried by both parents.`)
    }
    if (dimensions.legacySupportStars > 0) parts.push(`Legacy Origin blocks add ${dimensions.legacySupportStars} matched priority star${dimensions.legacySupportStars === 1 ? "" : "s"}, from ancestors the panel does not name.`)
    return parts.join(" ")
}

/** True when `a` is at least as good as `b` on every dimension and strictly better on one. */
export function dominates(a: PairDimensions, b: PairDimensions): boolean {
    let strict = false
    for (const dim of PAIR_DIMENSIONS) {
        if (a[dim] < b[dim]) return false
        if (a[dim] > b[dim]) strict = true
    }
    return strict
}

/** The frontier categories, each naming the single dimension it maximises. */
export const FRONTIER_CATEGORIES = [
    { category: "BEST_KNOWN_AFFINITY", dimension: "knownAffinityPoints" },
    { category: "BEST_BLUE_SPARKS", dimension: "statCoverageStars" },
    { category: "BEST_BLUE_STACKED", dimension: "statStackedStars" },
    { category: "BEST_PINK_SPARKS", dimension: "aptitudeCoverageStars" },
    { category: "BEST_GREEN_SPARKS", dimension: "uniqueCoverageStars" },
    { category: "BEST_WHITE_SPARKS", dimension: "whiteCoverageStars" },
    { category: "RAREST_FACTOR_COMBO", dimension: "scarcityValue" },
    { category: "BEST_LEGACY_SUPPORT", dimension: "legacySupportStars" },
    { category: "BEST_COVERAGE_BREADTH", dimension: "distinctPriorityCoverage" },
    { category: "BEST_LOW_REDUNDANCY", dimension: "nonRedundantCoverage" },
    { category: "BEST_LINEAGE_DIVERSITY", dimension: "lineageDiversity" },
] as const satisfies readonly { category: string; dimension: PairDimension }[]

export type FrontierCategory = (typeof FRONTIER_CATEGORIES)[number]["category"] | "BEST_BALANCED"

/** One frontier entry: the winning pair for a category, and whether anything dominates it. */
export interface FrontierEntry {
    readonly category: FrontierCategory
    readonly dimension: PairDimension | null
    readonly value: number
    readonly pairKey: string
    /** How many pairs tie the winner on this dimension. A large number means the category is weakly
     * discriminating for this build, which the reader should see. */
    readonly tiedPairs: number
    /** Verified against every pair, not only the frontier. */
    readonly dominatedByOtherPair: boolean
}

/**
 * Normalized position of a pair's dimension value inside the population, 0..1.
 *
 * Only used for the balance index, which orders the report. It never decides the recommendation, and
 * every underlying component travels with the pair, so nothing here hides behind the single number.
 */
function normalize(value: number, min: number, max: number): number {
    // A dimension every pair scores identically on separates nothing, so it contributes a constant
    // and cannot change the ordering.
    if (max <= min) return 0
    return (value - min) / (max - min)
}

/** Mean normalized dimension value. Presentation ordering only. */
export function balanceIndex(dimensions: PairDimensions, mins: PairDimensions, maxs: PairDimensions): number {
    let sum = 0
    for (const dim of PAIR_DIMENSIONS) sum += normalize(dimensions[dim], mins[dim], maxs[dim])
    return Math.round((sum / PAIR_DIMENSIONS.length) * 1000000) / 1000000
}

/** A category that could not separate anything, and why. Reported rather than shown as a winner: a
 * "best" pair on a dimension every pair scores identically is not a finding. */
export interface InactiveFrontierCategory {
    readonly category: FrontierCategory
    readonly dimension: PairDimension
    readonly reason: "DIMENSION_CONSTANT_ACROSS_PAIRS"
    readonly value: number
}

export interface PairRankingResult {
    readonly pairsEvaluated: number
    readonly frontier: readonly FrontierEntry[]
    readonly inactiveCategories: readonly InactiveFrontierCategory[]
    /** Pairs referenced by the frontier or the top list, keyed for lookup. */
    readonly pairsByKey: ReadonlyMap<string, ParentPair>
    /** Top pairs by balance index, best first. Presentation order, explicitly not a verdict. */
    readonly topPairs: readonly { readonly pairKey: string; readonly balanceIndex: number }[]
    /** Set only when one pair dominates every other pair on every dimension. */
    readonly dominantPairKey: string | null
    readonly dimensionMins: PairDimensions
    readonly dimensionMaxs: PairDimensions
}

/**
 * Ranks every pair in `pairs`.
 *
 * The recommendation search is exhaustive without being quadratic: a pair that dominates every other
 * pair must attain the maximum on every dimension, so it is necessarily a category winner. Only the
 * category winners are tested, and each test runs against the whole population.
 */
export function rankParentPairs(pairs: readonly ParentPair[], topCount: number): PairRankingResult {
    const mins = {} as Record<PairDimension, number>
    const maxs = {} as Record<PairDimension, number>
    for (const dim of PAIR_DIMENSIONS) {
        mins[dim] = Number.POSITIVE_INFINITY
        maxs[dim] = Number.NEGATIVE_INFINITY
    }
    for (const pair of pairs) {
        for (const dim of PAIR_DIMENSIONS) {
            const v = pair.dimensions[dim]
            if (v < mins[dim]) mins[dim] = v
            if (v > maxs[dim]) maxs[dim] = v
        }
    }
    if (pairs.length === 0) {
        for (const dim of PAIR_DIMENSIONS) {
            mins[dim] = 0
            maxs[dim] = 0
        }
    }

    const scored = pairs.map((pair) => ({ pair, balance: balanceIndex(pair.dimensions, mins, maxs) }))
    scored.sort((x, y) => y.balance - x.balance || (x.pair.pairKey < y.pair.pairKey ? -1 : x.pair.pairKey > y.pair.pairKey ? 1 : 0))

    const referenced = new Map<string, ParentPair>()
    const topPairs = scored.slice(0, Math.max(0, topCount)).map((s) => {
        referenced.set(s.pair.pairKey, s.pair)
        return { pairKey: s.pair.pairKey, balanceIndex: s.balance }
    })

    const winners = new Map<string, ParentPair>()
    const frontier: FrontierEntry[] = []
    const inactiveCategories: InactiveFrontierCategory[] = []
    for (const { category, dimension } of FRONTIER_CATEGORIES) {
        // Two passes so the tie count is the number of pairs sharing the maximum, not an artefact of
        // the order the maximum was discovered in. The winner among ties is the lowest pair key, which
        // keeps the report identical across rebuilds.
        let max = Number.NEGATIVE_INFINITY
        for (const pair of pairs) {
            const v = pair.dimensions[dimension]
            if (v > max) max = v
        }
        if (pairs.length > 0 && maxs[dimension] === mins[dimension]) {
            // Every pair scores the same here, usually because the build named no factor of this
            // family. Naming a winner would dress a non-result up as one.
            inactiveCategories.push({ category, dimension, reason: "DIMENSION_CONSTANT_ACROSS_PAIRS", value: max })
            continue
        }
        let best: ParentPair | null = null
        let tied = 0
        for (const pair of pairs) {
            if (pair.dimensions[dimension] !== max) continue
            tied++
            if (best === null || pair.pairKey < best.pairKey) best = pair
        }
        if (!best) continue
        winners.set(best.pairKey, best)
        referenced.set(best.pairKey, best)
        frontier.push({ category, dimension, value: best.dimensions[dimension], pairKey: best.pairKey, tiedPairs: tied, dominatedByOtherPair: false })
    }
    if (scored.length > 0) {
        const balanced = scored[0].pair
        winners.set(balanced.pairKey, balanced)
        referenced.set(balanced.pairKey, balanced)
        frontier.push({ category: "BEST_BALANCED", dimension: null, value: scored[0].balance, pairKey: balanced.pairKey, tiedPairs: scored.filter((s) => s.balance === scored[0].balance).length, dominatedByOtherPair: false })
    }

    // Non-domination is verified against every pair, so a frontier entry that says it is not dominated
    // is making a statement about the whole population rather than about the frontier.
    const resolvedFrontier = frontier.map((entry) => {
        const pair = referenced.get(entry.pairKey)
        if (!pair) return entry
        let dominated = false
        for (const other of pairs) {
            if (other.pairKey === pair.pairKey) continue
            if (dominates(other.dimensions, pair.dimensions)) {
                dominated = true
                break
            }
        }
        return { ...entry, dominatedByOtherPair: dominated }
    })

    // Exhaustive without being quadratic. A pair that dominates every other pair holds the maximum on
    // every dimension, so every one of its normalized values is 1 and its balance index is 1 - the
    // highest attainable. No other pair can match that, because a pair tying it on every dimension
    // would not be dominated. So the balance winner is always in `winners`, and testing the winners is
    // enough. Each test still runs against the whole population.
    // With fewer than two pairs there is nothing to dominate, and "better than every other pair" would
    // be vacuously true. A claim nobody could have failed is not a finding, so none is made.
    let dominantPairKey: string | null = null
    if (pairs.length >= 2) {
        for (const candidate of winners.values()) {
            let dominatesAll = true
            for (const other of pairs) {
                if (other.pairKey === candidate.pairKey) continue
                if (!dominates(candidate.dimensions, other.dimensions)) {
                    dominatesAll = false
                    break
                }
            }
            if (dominatesAll) {
                dominantPairKey = candidate.pairKey
                break
            }
        }
    }

    return {
        pairsEvaluated: pairs.length,
        frontier: resolvedFrontier,
        inactiveCategories,
        pairsByKey: referenced,
        topPairs,
        dominantPairKey,
        dimensionMins: mins as PairDimensions,
        dimensionMaxs: maxs as PairDimensions,
    }
}

/** Median of the resolved single-parent relation points across candidates, or null when none resolved. */
export function affinityMedianOf(candidates: readonly ParentCandidate[]): number | null {
    const points = candidates.filter((c) => c.affinity.resolved && c.affinity.points !== null).map((c) => c.affinity.points as number)
    if (points.length === 0) return null
    points.sort((a, b) => a - b)
    const mid = Math.floor(points.length / 2)
    return points.length % 2 === 1 ? points[mid] : (points[mid - 1] + points[mid]) / 2
}

/** Every unordered pair of candidates, in a deterministic order. */
export function enumerateParentPairs(
    candidates: readonly ParentCandidate[],
    build: TargetBuild,
    index: SuccessionRelationIndex,
    scarcity: FactorScarcityIndex,
    affinityMedian: number | null,
): readonly ParentPair[] {
    const ordered = candidates.slice().sort((a, b) => (candidateKey(a) < candidateKey(b) ? -1 : candidateKey(a) > candidateKey(b) ? 1 : 0))
    const out: ParentPair[] = []
    for (let i = 0; i < ordered.length; i++) {
        for (let j = i + 1; j < ordered.length; j++) {
            out.push(buildParentPair(ordered[i], ordered[j], build, index, scarcity, affinityMedian))
        }
    }
    return out
}

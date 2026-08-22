import { readFileSync } from "node:fs"
import { join } from "node:path"
import { buildAffinityAdvisorReport, buildAffinityTargetReport } from "../affinityAdvisor.ts"
import { buildSuccessionRelationIndex, normalizeCharacterName, pairwiseRelation, parseSuccessionRelationData, resolveCharaId, SuccessionRelationDataError, type SuccessionRelationIndex } from "../affinityData.ts"
import { AFFINITY_MECHANIC_EVIDENCE, UNKNOWN_AFFINITY_COMPONENTS } from "../affinityEvidence.ts"
import { buildInspirationIndex, parseInspirationRecords } from "../inspiration.ts"
import { buildParentCandidates } from "../parentCandidate.ts"
import { affinityMedianOf, buildParentPair, dominates, enumerateParentPairs, rankParentPairs } from "../parentPairing.ts"
import { buildFactorScarcityIndex, buildRetentionEvidence } from "../retentionEvidence.ts"
import { buildRosterSnapshots, parseRosterScanRecords } from "../roster.ts"
import { buildTargetBuild } from "../targetBuild.ts"

// Fixtures go through the real ingest paths (roster rows and captures as JSONL, relation data through
// the real parser) so a change to any of them shows up here rather than being papered over.

const SCAN = "rs-affinity-0001"
const ISCAN = "insp-affinity-0001"
const T = Date.UTC(2026, 7, 22, 12, 0, 0)

const TARGET = "Target Uma"
const ALPHA = "Alpha Uma"
const BETA = "Beta Uma"
const GAMMA = "Gamma Uma"

const APTITUDES = { turf: "A", dirt: "G", sprint: "C", mile: "A", medium: "A", long: "B", front: "A", pace: "A", late: "B", end: "C" }

/**
 * A small relation payload with known arithmetic:
 *   target <-> alpha = 5 + 3 = 8   (two shared types)
 *   target <-> beta  = 2           (one shared type)
 *   target <-> gamma = 0           (resolved, unrelated)
 *   alpha  <-> beta  = 1
 */
const RELATION_PAYLOAD = {
    schema: "parent_lab_succession_relations",
    schemaVersion: 1,
    source: "test fixture",
    rankBands: [
        { rank: 1, minValue: 0, maxValue: 50 },
        { rank: 2, minValue: 51, maxValue: 150 },
        { rank: 3, minValue: 151, maxValue: 9999 },
    ],
    characters: { [TARGET]: 9001, [ALPHA]: 9002, [BETA]: 9003, [GAMMA]: 9004 },
    relations: [
        { relationType: 1, relationPoint: 5, members: [9001, 9002] },
        { relationType: 2, relationPoint: 3, members: [9001, 9002] },
        { relationType: 3, relationPoint: 2, members: [9001, 9003] },
        { relationType: 4, relationPoint: 1, members: [9002, 9003] },
        { relationType: 5, relationPoint: 1, members: [9004] },
    ],
    diagnostics: {
        relationTypes: 5,
        relationTypesWithMembers: 5,
        relationTypesWithoutMembers: 0,
        pairwiseCapableTypes: 4,
        memberRows: 9,
        charactersWithMembership: 4,
        maxPairwisePoints: 8,
        topRankBandMinValue: 151,
    },
}

function relationIndex(): SuccessionRelationIndex {
    return buildSuccessionRelationIndex(parseSuccessionRelationData(RELATION_PAYLOAD))
}

function rosterHeader(count: number, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_scan",
        schemaVersion: 1,
        scanId: SCAN,
        startedAt: T,
        completedAt: T,
        displayedRegisteredUsed: count,
        displayedRegisteredCapacity: 260,
        filtersOff: true,
        sortKey: "rating",
        sortDirection: "descending",
        entryLimit: 0,
        entriesEnumerated: count,
        uniqueFingerprints: count,
        unidentifiedCount: 0,
        duplicateFingerprintCount: 0,
        countDiscrepancy: 0,
        terminationReason: "count_reached",
        enumerationComplete: true,
        identityComplete: true,
        completeness: "trusted_complete",
        evidenceCropCount: 0,
        app: "test",
        screenWidth: 1600,
        screenHeight: 900,
        ...o,
    })
}

function rosterEntry(index: number, character: string, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_entry",
        schemaVersion: 1,
        scanId: SCAN,
        scanIndex: index,
        observedAt: T,
        character,
        outfit: `${character} Outfit`,
        rank: "S",
        rating: 15000,
        stats: { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 },
        statGrades: { spd: "A", sta: "B", pwr: "B", grt: "C", wit: "C" },
        aptitudes: APTITUDES,
        favoriteState: "not_set",
        protectionState: "not_protected",
        careerInfo: null,
        rosterFingerprint: `fp-${index}`,
        readCompleteness: 1,
        identityMultiplicity: 1,
        unresolvedFields: [],
        diagnostics: null,
        ...o,
    })
}

function factor(kind: string, name: string, stars: number) {
    return {
        rowIndex: 0,
        column: "left",
        kind,
        displayName: name,
        normalizedName: name.toUpperCase(),
        stars,
        canonicalName: name,
        canonicalPath: "strong",
        factorFingerprint: `${kind}:${name.toUpperCase()}:${stars}`,
        structuralFingerprint: `${kind}:${stars}`,
        ambiguous: false,
    }
}

function ancestor(index: number, factors: ReturnType<typeof factor>[]) {
    return {
        ancestorIndex: index,
        portraitObserved: true,
        rank: null,
        factorCount: factors.length,
        ancestorFactorFingerprint: `anc:${index}`,
        ancestorStructuralFingerprint: `ancs:${index}`,
        factorSetTrusted: true,
        factors,
    }
}

function capture(index: number, character: string, factors: ReturnType<typeof factor>[], o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "veteran_inspiration",
        schemaVersion: 2,
        scanId: ISCAN,
        scanIndex: index,
        observedAt: T,
        rosterFingerprint: `fp-${index}`,
        character,
        outfit: `${character} Outfit`,
        rank: "S",
        selfPortraitObserved: true,
        selfFactorCount: factors.length,
        selfFactorFingerprint: `set:fp-${index}`,
        selfStructuralFingerprint: `struct:fp-${index}`,
        selfFactorSetTrusted: true,
        selfFactors: factors,
        legacyAncestors: [],
        termination: "reached_bottom",
        sparkCaptureComplete: true,
        screenReadCompleteness: 1,
        unresolvedFields: [],
        diagnostics: null,
        ...o,
    })
}

interface Fixture {
    /** One entry per element: character plus its own factors, and optional legacy blocks. */
    readonly veterans: readonly { character: string; factors: ReturnType<typeof factor>[]; legacy?: ReturnType<typeof factor>[][]; captureTrusted?: boolean; rating?: number }[]
    readonly target?: string
    readonly distance?: "sprint" | "mile" | "medium" | "long" | null
    readonly surface?: "turf" | "dirt" | null
    readonly statFactors?: readonly string[] | null
    readonly aptitudeFactors?: readonly string[] | null
    readonly uniqueFactors?: readonly string[] | null
}

function build(fixture: Fixture) {
    const entries = fixture.veterans.map((v, i) => rosterEntry(i, v.character, v.rating === undefined ? {} : { rating: v.rating }))
    const captures = fixture.veterans.map((v, i) =>
        capture(i, v.character, v.factors, {
            legacyAncestors: (v.legacy ?? []).map((block, j) => ancestor(j, block)),
            ...(v.captureTrusted === false ? { selfFactorSetTrusted: false, selfFactorFingerprint: null } : {}),
        }),
    )
    const snapshot = buildRosterSnapshots(parseRosterScanRecords([rosterHeader(entries.length), ...entries].join("\n"), "roster_scan.jsonl"))[0]
    const inspiration = buildInspirationIndex(parseInspirationRecords(captures.join("\n"), "veteran_inspiration.jsonl"))
    const evidence = buildRetentionEvidence(snapshot, inspiration, null)
    const scarcity = buildFactorScarcityIndex(evidence)
    const relations = relationIndex()
    const targetBuild = buildTargetBuild(
        {
            targetTrainee: fixture.target ?? TARGET,
            distance: fixture.distance === undefined ? "mile" : fixture.distance,
            surface: fixture.surface === undefined ? "turf" : fixture.surface,
            statFactors: fixture.statFactors ?? null,
            aptitudeFactors: fixture.aptitudeFactors ?? null,
            uniqueFactors: fixture.uniqueFactors ?? null,
        },
        relations,
    )
    const candidates = buildParentCandidates(evidence.veterans, targetBuild, scarcity, relations)
    const median = affinityMedianOf(candidates)
    const pairs = enumerateParentPairs(candidates, targetBuild, relations, scarcity, median)
    return { snapshot, evidence, scarcity, relations, targetBuild, candidates, pairs, median }
}

function pairOf(pairs: ReturnType<typeof build>["pairs"], a: number, b: number) {
    const key = [`fp-${a}`, `fp-${b}`].sort().join("|")
    const found = pairs.find((p) => p.pairKey === key)
    if (!found) throw new Error(`no pair ${key}`)
    return found
}

describe("succession relation data", () => {
    it("collapses punctuation so the roster and the database agree on one character", () => {
        expect(normalizeCharacterName("TM Opera O")).toBe(normalizeCharacterName("T.M. Opera O"))
        expect(normalizeCharacterName("El Condor Pasa")).not.toBe(normalizeCharacterName("El Condor Pasu"))
    })

    it("sums the points of every shared relation type", () => {
        const index = relationIndex()
        expect(pairwiseRelation(index, 9001, 9002)).toEqual({ charaIdA: 9001, charaIdB: 9002, points: 8, sharedRelationTypes: [1, 2] })
        expect(pairwiseRelation(index, 9001, 9003)?.points).toBe(2)
    })

    it("separates an unrelated pair from an unmeasurable one", () => {
        const index = relationIndex()
        // Resolved and genuinely unrelated: zero points, but a real answer.
        expect(pairwiseRelation(index, 9001, 9004)).toEqual({ charaIdA: 9001, charaIdB: 9004, points: 0, sharedRelationTypes: [] })
        // Outside the domain, and a character against itself: no answer at all.
        expect(pairwiseRelation(index, 9001, null)).toBeNull()
        expect(pairwiseRelation(index, 9001, 9001)).toBeNull()
        expect(resolveCharaId(index, "Nobody At All")).toBeNull()
    })

    it("rejects a payload whose schema or version does not match", () => {
        expect(() => parseSuccessionRelationData({ ...RELATION_PAYLOAD, schema: "something_else" })).toThrow(SuccessionRelationDataError)
        expect(() => parseSuccessionRelationData({ ...RELATION_PAYLOAD, schemaVersion: 99 })).toThrow(SuccessionRelationDataError)
    })

    it("parses the committed asset, and no single pair can reach the top rank band", () => {
        const raw = JSON.parse(readFileSync(join(__dirname, "..", "..", "..", "data", "succession_relations.json"), "utf8"))
        const data = parseSuccessionRelationData(raw)
        const index = buildSuccessionRelationIndex(data)
        expect(data.relations.length).toBe(data.diagnostics.relationTypesWithMembers)
        expect(index.charaIdByName.size).toBe(data.diagnostics.charactersWithMembership)
        // The evidence that the in-game total aggregates more than one pair, straight from the data.
        expect(data.diagnostics.maxPairwisePoints).toBeLessThan(data.diagnostics.topRankBandMinValue)
    })
})

describe("target build", () => {
    it("labels a distance-derived blue priority as a default and an operator list as operator", () => {
        const index = relationIndex()
        const derived = buildTargetBuild({ targetTrainee: TARGET, distance: "long" }, index)
        expect(derived.statPriorityOrigin).toBe("DEFAULT_BY_DISTANCE")
        expect(derived.priorityAptitudeFactors).toContain("Long")
        const operator = buildTargetBuild({ targetTrainee: TARGET, distance: "long", statFactors: ["Wit"] }, index)
        expect(operator.statPriorityOrigin).toBe("OPERATOR")
        expect(operator.priorityStatFactors).toEqual(["Wit"])
    })

    it("reports an unresolvable target rather than inventing a chara id", () => {
        const build = buildTargetBuild({ targetTrainee: "Nobody At All", distance: "mile" }, relationIndex())
        expect(build.targetCharaId).toBeNull()
        expect(build.gaps).toContain("TARGET_CHARACTER_NOT_IN_RELATION_DOMAIN")
    })
})

describe("affinity component", () => {
    it("prefers the higher verified base affinity when nothing else differs", () => {
        const factors = [factor("aptitude", "Mile", 2), factor("stat", "Speed", 2)]
        const fx = build({
            veterans: [
                { character: ALPHA, factors },
                { character: BETA, factors },
                { character: GAMMA, factors },
            ],
        })
        const alphaBeta = pairOf(fx.pairs, 0, 1)
        const betaGamma = pairOf(fx.pairs, 1, 2)
        // alpha 8 + beta 2 = 10 against beta 2 + gamma 0 = 2, every other dimension identical.
        expect(alphaBeta.dimensions.knownAffinityPoints).toBe(10)
        expect(betaGamma.dimensions.knownAffinityPoints).toBe(2)
        expect(dominates(alphaBeta.dimensions, betaGamma.dimensions)).toBe(true)
        expect(dominates(betaGamma.dimensions, alphaBeta.dimensions)).toBe(false)
    })

    it("never presents the known components as the game's affinity total", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 1)] }, { character: BETA, factors: [factor("aptitude", "Mile", 1)] }] })
        const pair = pairOf(fx.pairs, 0, 1)
        expect(pair.affinity.isGameAffinityTotal).toBe(false)
        expect(pair.affinity.unknown).toEqual(UNKNOWN_AFFINITY_COMPONENTS)
        expect(pair.affinity.knownPointsTotal).toBe(10)
        // The parent-to-parent relation is computable but its inclusion is not decoded, so it stays out.
        expect(pair.parentPairRelationPoints).toBe(1)
        expect(pair.affinity.known.map((c) => c.points)).toEqual([8, 2])
    })

    it("leaves the affinity component unresolved rather than guessing when the target is unknown", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [] }, { character: BETA, factors: [] }], target: "Nobody At All" })
        const pair = pairOf(fx.pairs, 0, 1)
        expect(pair.affinityResolved).toBe(false)
        expect(pair.dimensions.knownAffinityPoints).toBe(0)
        expect(pair.affinity.known).toEqual([])
        expect(pair.reasonCodes).toContain("AFFINITY_COMPONENT_UNRESOLVED")
    })
})

describe("factor relevance", () => {
    it("ranks a target-relevant 3-star pink factor above an unrelated high-rating Veteran", () => {
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("aptitude", "Mile", 3)], rating: 1000 },
                { character: BETA, factors: [factor("aptitude", "Long", 3), factor("stat", "Guts", 3)], rating: 40000 },
                { character: GAMMA, factors: [] },
            ],
        })
        const relevant = fx.candidates[0]
        const highRating = fx.candidates[1]
        expect(relevant.relevance?.aptitude.maxStars).toBe(3)
        expect(highRating.relevance?.aptitude.maxStars).toBe(0)
        expect(highRating.relevance?.stat.maxStars).toBe(0)
        // The pair carrying the on-target factor beats the pair carrying only the off-target ones.
        expect(pairOf(fx.pairs, 0, 2).dimensions.aptitudeCoverageStars).toBe(3)
        expect(pairOf(fx.pairs, 1, 2).dimensions.aptitudeCoverageStars).toBe(0)
    })

    it("credits a rare relevant factor above a common one at the same star count", () => {
        // Mile 2 sits on four Veterans, Turf 2 on one. Same stars, different scarcity.
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("aptitude", "Mile", 2)] },
                { character: BETA, factors: [factor("aptitude", "Mile", 2)] },
                { character: GAMMA, factors: [factor("aptitude", "Mile", 2)] },
                { character: ALPHA, factors: [factor("aptitude", "Mile", 2)] },
                { character: BETA, factors: [factor("aptitude", "Turf", 2)] },
                { character: GAMMA, factors: [] },
            ],
        })
        const common = fx.candidates[0].relevance?.aptitude.matched[0]
        const rare = fx.candidates[4].relevance?.aptitude.matched[0]
        expect(common?.observedCarriersAtStars).toBe(4)
        expect(common?.scarcity).toBe("OBSERVED_COMMON")
        expect(rare?.observedCarriersAtStars).toBe(1)
        expect(rare?.scarcity).toBe("ACCOUNT_UNIQUE")
        expect(pairOf(fx.pairs, 4, 5).dimensions.scarcityValue).toBeGreaterThan(pairOf(fx.pairs, 0, 5).dimensions.scarcityValue)
    })

    it("gives a duplicated factor less marginal coverage than a complementary one", () => {
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("aptitude", "Mile", 2)] },
                { character: BETA, factors: [factor("aptitude", "Mile", 2)] },
                { character: GAMMA, factors: [factor("aptitude", "Turf", 2)] },
            ],
        })
        const duplicate = pairOf(fx.pairs, 0, 1)
        const complementary = pairOf(fx.pairs, 0, 2)
        expect(duplicate.dimensions.aptitudeCoverageStars).toBe(2)
        expect(complementary.dimensions.aptitudeCoverageStars).toBe(4)
        expect(duplicate.sharedPriorityFactors).toBe(1)
        expect(complementary.sharedPriorityFactors).toBe(0)
        expect(complementary.dimensions.nonRedundantCoverage).toBeGreaterThan(duplicate.dimensions.nonRedundantCoverage)
    })

    it("keeps the factor families apart instead of summing stars across them", () => {
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("stat", "Speed", 3), factor("stat", "Power", 3)] },
                { character: BETA, factors: [factor("aptitude", "Mile", 1)] },
            ],
        })
        const pair = pairOf(fx.pairs, 0, 1)
        expect(pair.dimensions.statCoverageStars).toBe(6)
        expect(pair.dimensions.aptitudeCoverageStars).toBe(1)
        // Six blue stars must not be able to stand in for the one pink factor the build asked for.
        expect(dominates(pair.dimensions, pair.dimensions)).toBe(false)
    })
})

describe("pair complementarity", () => {
    it("does not let a redundant strong pair dominate a complementary one", () => {
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("aptitude", "Mile", 3), factor("stat", "Speed", 3)] },
                { character: BETA, factors: [factor("aptitude", "Mile", 3), factor("stat", "Speed", 3)] },
                { character: GAMMA, factors: [factor("aptitude", "Turf", 3)] },
            ],
        })
        const redundant = pairOf(fx.pairs, 0, 1)
        const complementary = pairOf(fx.pairs, 0, 2)
        // The redundant pair really is worth more where factors stack, and the complementary pair
        // really does cover more ground. Neither fact is allowed to erase the other.
        expect(redundant.dimensions.statStackedStars).toBeGreaterThan(complementary.dimensions.statStackedStars)
        expect(complementary.dimensions.distinctPriorityCoverage).toBeGreaterThan(redundant.dimensions.distinctPriorityCoverage)
        expect(dominates(redundant.dimensions, complementary.dimensions)).toBe(false)
        expect(dominates(complementary.dimensions, redundant.dimensions)).toBe(false)
    })

    it("recommends a pair only when it dominates every other pair", () => {
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("aptitude", "Mile", 3), factor("stat", "Speed", 3)] },
                { character: BETA, factors: [factor("aptitude", "Mile", 3), factor("stat", "Speed", 3)] },
                { character: GAMMA, factors: [factor("aptitude", "Turf", 3), factor("stat", "Power", 3)] },
            ],
        })
        expect(rankParentPairs(fx.pairs, 3).dominantPairKey).toBeNull()

        // Two Veterans only, so there is exactly one pair and no comparison was ever made. A claim
        // that could not have failed is not made.
        const single = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 1)] }, { character: BETA, factors: [factor("aptitude", "Turf", 1)] }] })
        expect(single.pairs.length).toBe(1)
        expect(rankParentPairs(single.pairs, 3).dominantPairKey).toBeNull()

        // A strictly better pair on every dimension does get recommended.
        const clear = build({
            veterans: [
                { character: ALPHA, factors: [factor("aptitude", "Mile", 3), factor("aptitude", "Turf", 3), factor("stat", "Speed", 3), factor("stat", "Power", 3)] },
                { character: BETA, factors: [] },
                { character: GAMMA, factors: [] },
            ],
        })
        const ranked = rankParentPairs(clear.pairs, 3)
        expect(ranked.dominantPairKey).toBe("fp-0|fp-1")
    })

    it("does not name a winner for a category no pair can differ on", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 1)] }, { character: BETA, factors: [factor("aptitude", "Turf", 1)] }, { character: GAMMA, factors: [] }] })
        const ranked = rankParentPairs(fx.pairs, 3)
        // The build names no green or white priority, so those categories separate nothing.
        const inactive = ranked.inactiveCategories.map((c) => c.category)
        expect(inactive).toContain("BEST_GREEN_SPARKS")
        expect(inactive).toContain("BEST_WHITE_SPARKS")
        expect(ranked.frontier.map((f) => f.category)).not.toContain("BEST_GREEN_SPARKS")
    })
})

describe("lineage", () => {
    it("keeps a Veteran's own factors separate from the ones behind it", () => {
        const fx = build({
            veterans: [
                { character: ALPHA, factors: [factor("stat", "Speed", 1)], legacy: [[factor("aptitude", "Mile", 3)], [factor("aptitude", "Turf", 3)]] },
                { character: BETA, factors: [] },
            ],
        })
        const candidate = fx.candidates[0]
        // The ancestry's pink factors never appear in the Veteran's own relevance.
        expect(candidate.relevance?.aptitude.matched).toEqual([])
        expect(candidate.relevance?.stat.maxStars).toBe(1)
        expect(candidate.legacy.totalPriorityStars).toBe(6)
        expect(candidate.legacy.blocksObserved).toBe(2)
        const pair = pairOf(fx.pairs, 0, 1)
        expect(pair.dimensions.aptitudeCoverageStars).toBe(0)
        expect(pair.dimensions.legacySupportStars).toBe(6)
    })

    it("leaves the ancestors unnamed rather than attributing them", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [], legacy: [[factor("aptitude", "Mile", 2)]] }, { character: BETA, factors: [] }] })
        const candidate = fx.candidates[0]
        expect(candidate.legacy.ancestorIdentityKnown).toBe(false)
        expect(candidate.gaps).toContain("LEGACY_ANCESTOR_IDENTITY_UNKNOWN")
        expect(pairOf(fx.pairs, 0, 1).reasonCodes).toContain("ANCESTOR_IDENTITY_UNKNOWN")
        expect(AFFINITY_MECHANIC_EVIDENCE.find((e) => e.id === "GRANDPARENT_IDENTITY")?.status).toBe("UNKNOWN")
    })

    it("counts a Veteran with no capture as unmeasured, not as carrying nothing", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)], captureTrusted: false }, { character: BETA, factors: [] }] })
        expect(fx.candidates[0].relevance).toBeNull()
        expect(fx.candidates[0].gaps).toContain("NO_TRUSTED_FACTOR_CAPTURE")
        expect(fx.candidates[0].selfFactorCount).toBeNull()
    })
})

describe("confidence", () => {
    it("lowers confidence when a parent has no trusted factor evidence", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)] }, { character: BETA, factors: [factor("aptitude", "Turf", 2)], captureTrusted: false }] })
        expect(pairOf(fx.pairs, 0, 1).confidence).toBe("LOW")
    })

    it("caps confidence at MEDIUM while an affinity component is unresolved", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)] }, { character: BETA, factors: [factor("aptitude", "Turf", 2)] }], target: "Nobody At All" })
        const pair = pairOf(fx.pairs, 0, 1)
        expect(pair.confidence).toBe("MEDIUM")
        expect(pair.affinityResolved).toBe(false)
    })

    it("reaches HIGH only with full evidence, and even then claims no affinity total", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)] }, { character: BETA, factors: [factor("aptitude", "Turf", 2)] }] })
        const pair = pairOf(fx.pairs, 0, 1)
        expect(fx.scarcity.accountWide).toBe(true)
        expect(pair.confidence).toBe("HIGH")
        expect(pair.affinity.isGameAffinityTotal).toBe(false)
        expect(pair.affinity.unknown.length).toBeGreaterThan(0)
    })
})

describe("advisor document", () => {
    it("carries the evidence inventory and the unknown components with the ranking", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)] }, { character: BETA, factors: [factor("aptitude", "Turf", 2)] }, { character: GAMMA, factors: [] }] })
        const report = buildAffinityAdvisorReport({ evidence: fx.evidence, scarcity: fx.scarcity, relations: fx.relations, builds: [fx.targetBuild], topCount: 2 })
        expect(report.schema).toBe("parent_lab_affinity_advisor")
        expect(report.generatedAt).toBe(fx.evidence.observedAt)
        expect(report.unknownAffinityComponents).toEqual(UNKNOWN_AFFINITY_COMPONENTS)
        expect(report.affinityEvidence.some((e) => e.status === "UNKNOWN" && e.usableInAdvisor)).toBe(false)
        const target = report.targets[0]
        expect(target.pairsEvaluated).toBe(3)
        expect(target.topPairKeys.length).toBe(2)
        expect(target.missingEvidence).toEqual(expect.arrayContaining([...UNKNOWN_AFFINITY_COMPONENTS]))
        for (const pair of target.pairs) expect(pair.knownAffinity.isGameAffinityTotal).toBe(false)
    })

    it("is deterministic: the same inputs produce the same document", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)] }, { character: BETA, factors: [factor("aptitude", "Turf", 2)] }, { character: GAMMA, factors: [factor("stat", "Speed", 1)] }] })
        const input = { evidence: fx.evidence, scarcity: fx.scarcity, relations: fx.relations, builds: [fx.targetBuild], topCount: 3 }
        expect(JSON.stringify(buildAffinityTargetReport(input, fx.targetBuild))).toBe(JSON.stringify(buildAffinityTargetReport(input, fx.targetBuild)))
    })

    it("builds a pair record without a ranking pass", () => {
        const fx = build({ veterans: [{ character: ALPHA, factors: [factor("aptitude", "Mile", 2)] }, { character: BETA, factors: [factor("aptitude", "Turf", 2)] }] })
        const pair = buildParentPair(fx.candidates[0], fx.candidates[1], fx.targetBuild, fx.relations, fx.scarcity, fx.median)
        expect(pair.pairKey).toBe("fp-0|fp-1")
        expect(pair.explanation).toContain("not the game's affinity total")
    })
})

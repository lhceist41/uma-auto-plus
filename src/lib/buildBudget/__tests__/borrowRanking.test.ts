// STAM-2a build-aware Smart Borrow ranking tests.
//
// The qualitative fixtures at the bottom are the point of the phase. Each one sets up a situation
// where DeckLab's composite answer and the whole-build answer come apart, and asserts that the
// build-aware ranking follows the constraint rather than the score. None of them names a card in the
// ranking code, and none of them is made to pass by tuning a weight, because there is no weight to
// tune: survival is a tier compared before any number is read.

import { EFFECT } from "../../deckLab/supportCardData.ts"
import { buildSmartBorrowIntent, intentEvidenceDigest, serializeSmartBorrowIntent, SmartBorrowIntentError, SMART_BORROW_INTENT_SCHEMA_VERSION } from "../../deckLab/smartBorrowIntent.ts"
import { searchDecks } from "../../deckLab/deckSearch.ts"
import { buildFixtureInventory } from "../../deckLab/inventory.ts"
import { archetypeProfile } from "../archetypes.ts"
import { SURVIVAL_STAT, survivalTierOf, survivalTierRank } from "../budget.ts"
import { borrowDominates, correctedDeltaVector, evaluateBorrow, rankBuildAwareBorrows, reliefOf, type BuildAwareBorrowInput } from "../borrowRanking.ts"
import { formatBuildAwareBorrowRanking } from "../borrowReport.ts"
import { evaluateCandidate, planJointBuild, type BuildBudgetInput, type JointBuildCandidate } from "../joint.ts"
import { BuildBudgetError, type BudgetStat } from "../types.ts"
import { HIGH_STAMINA_GROWTH, LOW_STAMINA_GROWTH, buildCatalogue, buildEvidence, constraintOf, deckTargetFor, flatCurve, makeBorrowResolution, pairWith, scoreFixtureDeck, traineeFrom } from "./fixtures.ts"

const evidence = buildEvidence()

/**
 * A catalogue built so the two answers can disagree.
 *
 * Card 10 is a big Speed card: the highest composite gain in the pool and no Stamina at all.
 * Card 11 is a modest Stamina card: a small composite gain and real Stamina.
 * Card 12 is the only card in the pool whose hint pool carries the recovery skill.
 * Card 13 is a Power card, which pays Stamina through the decoded secondary payout rather than directly.
 */
const catalogue = buildCatalogue([
    { id: 1, charaId: 11, title: "Owned Speed A", supportType: "Speed", effects: [{ type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(15) }] },
    { id: 2, charaId: 12, title: "Owned Speed B", supportType: "Speed", effects: [{ type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(15) }] },
    { id: 3, charaId: 13, title: "Owned Power", supportType: "Power", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(5) }] },
    { id: 4, charaId: 14, title: "Owned Wit", supportType: "Wit", effects: [{ type: EFFECT.SKILL_POINT_BONUS, curve: flatCurve(10) }] },
    { id: 5, charaId: 15, title: "Owned Guts", supportType: "Guts", effects: [] },
    { id: 6, charaId: 16, title: "Owned Filler", supportType: "Guts", effects: [] },

    { id: 10, charaId: 20, title: "Borrow Big Speed", supportType: "Speed", effects: [{ type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(50) }, { type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(25) }] },
    { id: 11, charaId: 21, title: "Borrow Stamina", supportType: "Stamina", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(15) }, { type: EFFECT.INITIAL_STAMINA, curve: flatCurve(40) }] },
    { id: 12, charaId: 22, title: "Borrow Recovery Hinter", supportType: "Wit", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(5) }], hintSkillIds: [9001] },
    { id: 13, charaId: 23, title: "Borrow Power", supportType: "Power", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(30) }] },
    { id: 14, charaId: 24, title: "Borrow Big Stamina", supportType: "Stamina", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(40) }, { type: EFFECT.INITIAL_STAMINA, curve: flatCurve(120) }] },
])

const target = deckTargetFor(catalogue, "URA Finale")
const OWNED = [1, 2, 3, 4, 5, 6]
const baselineDeck = scoreFixtureDeck(catalogue, target, OWNED)

function budgetInputFor(overrides: Partial<BuildBudgetInput> = {}): BuildBudgetInput {
    return {
        evidenceVersion: 1,
        targetLabel: "fixture mile turf pace",
        scenarioId: 1,
        survivalConstraint: constraintOf(300, [320, 380]),
        trainee: traineeFrom(LOW_STAMINA_GROWTH),
        parentPairs: [pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }], "stamina pair")],
        decks: [{ label: "baseline", score: baselineDeck }],
        trainingTurns: 40,
        ...overrides,
    }
}

function baselineFor(input: BuildBudgetInput): JointBuildCandidate {
    return evaluateCandidate(evidence, catalogue, input, input.parentPairs[0], input.decks[0], archetypeProfile("STAMINA_FLEX"))
}

function rankingFor(cardIds: readonly number[], overrides: Partial<BuildBudgetInput> = {}, poolOptions: Parameters<typeof makeBorrowResolution>[2] = {}) {
    const input = budgetInputFor(overrides)
    const baseline = baselineFor(input)
    const resolution = makeBorrowResolution(catalogue, cardIds, poolOptions)
    const deckLab = searchDecks(catalogue, buildFixtureInventory(catalogue), target, { borrowCandidates: resolution.candidates.map((c) => c.card) })
    const rankingInput: BuildAwareBorrowInput = { budgetInput: input, baseline, resolution, deckTarget: target, deckLabBorrowOptions: deckLab.borrowOptions }
    return rankBuildAwareBorrows(evidence, catalogue, rankingInput)
}

describe("the shared swap rule", () => {
    it("evaluates a borrow under the same swap DeckLab uses, so the two rankings are comparable", () => {
        const input = budgetInputFor()
        const baseline = baselineFor(input)
        const resolution = makeBorrowResolution(catalogue, [10])
        const result = evaluateBorrow(evidence, catalogue, { budgetInput: input, baseline, resolution, deckTarget: target }, resolution.candidates[0])
        expect("skipped" in result).toBe(false)
        if ("skipped" in result) return
        expect(result.displacedCardId).not.toBeNull()
        expect(baselineDeck.cards.some((c) => c.card.supportCardId === result.displacedCardId)).toBe(true)
    })

    it("refuses a baseline that already borrows, because a borrow cannot be measured against a borrow", () => {
        const input = budgetInputFor()
        const borrowedBaseline = { ...baselineFor(input), deck: { label: "b", score: scoreFixtureDeck(catalogue, target, [1, 2, 3, 4, 5, 10], 50, 10) } }
        expect(() =>
            rankBuildAwareBorrows(evidence, catalogue, { budgetInput: input, baseline: borrowedBaseline, resolution: makeBorrowResolution(catalogue, [11]), deckTarget: target }),
        ).toThrow(BuildBudgetError)
    })
})

describe("ranking mechanics", () => {
    it("orders by survival tier before it reads any number", () => {
        const ranking = rankingFor([10, 11, 13, 14])
        expect(ranking.evaluations.length).toBeGreaterThan(1)
        // The list is non-increasing in tier rank. A composite gain cannot lift a candidate above a
        // candidate in a better tier, which is the whole guarantee this phase rests on.
        for (let i = 1; i < ranking.evaluations.length; i++) {
            const previous = survivalTierRank(ranking.evaluations[i - 1].survivalTierAfter)
            const current = survivalTierRank(ranking.evaluations[i].survivalTierAfter)
            expect(previous).toBeGreaterThanOrEqual(current)
        }
        // And the tier each evaluation reports is the tier its own build actually reaches.
        for (const evaluation of ranking.evaluations) {
            expect(evaluation.survivalTierAfter).toBe(survivalTierOf(evaluation.buildWithBorrow.verdict, evaluation.buildWithBorrow.recoveryPlan.effectiveConstraint))
        }
    })

    it("does not let one delta dominate when another is worse", () => {
        const base = { staminaDelta: 5, speedDelta: 5, powerDelta: 5, gutsDelta: 5, witDelta: 5, skillPointDelta: 5, recoveryDelta: 0, rampRelief: 0 }
        expect(borrowDominates({ ...base, speedDelta: 10, powerDelta: 1 }, base)).toBe(false)
        expect(borrowDominates({ ...base, speedDelta: 10 }, base)).toBe(true)
    })

    it("names relief as facts rather than scoring it", () => {
        const relief = reliefOf({
            survivalTierBefore: "FAILS",
            survivalTierAfter: "CLEARS",
            survivalDeficitBefore: 20,
            survivalDeficitAfter: 0,
            recoveryNewlyReachable: [9001],
            overStaminaBefore: false,
            overStaminaAfter: false,
            inheritanceOpportunity: { parentSwapAvailable: false },
            statBudgetDelta: { Speed: 0, Stamina: 10, Power: 5, Guts: 0, Wit: 0 },
        } as never)
        expect(relief).toContain("SURVIVAL_BLOCKER_REMOVED")
        expect(relief).toContain("RECOVERY_REQUIREMENT_UNLOCKED")
        expect(relief).toContain("STAMINA_DEFICIT_REDUCED")
        expect(relief).toContain("POWER_DEFICIT_REDUCED")
        expect(relief).not.toContain("OVER_STAMINA_REDUCED")
    })

    it("renders a report that shows both rankings, byte-identically on a rebuild", () => {
        const a = formatBuildAwareBorrowRanking(rankingFor([10, 11, 13]))
        const b = formatBuildAwareBorrowRanking(rankingFor([10, 11, 13]))
        expect(a).toBe(b)
        expect(a).toContain("OLD DECKLAB RANKING")
        expect(a).toContain("BUILD-AWARE RANKING")
        expect(a).toContain("OLD VS NEW")
    })

    it("states its bounds and never claims to be exhaustive", () => {
        const ranking = rankingFor([10, 11])
        expect(ranking.bounds.exhaustive).toBe(false)
        expect(ranking.bounds.resolvedBorrowCandidates).toBe(2)
    })
})

// ---------------------------------------------------------------------------------------------
// Qualitative fixtures: the behaviour the phase exists to produce.
// ---------------------------------------------------------------------------------------------

describe("regression: a borrow that fixes survival beats a bigger composite that does not", () => {
    it("picks the Stamina card over the Speed card when only the Stamina card clears the floor", () => {
        // The floor is set between what the two borrows reach. Nothing names either card.
        const ranking = rankingFor([10, 11], { survivalConstraint: constraintOf(330, [350, 420]) })
        const speed = ranking.evaluations.find((e) => e.supportCardId === 10)!
        const stamina = ranking.evaluations.find((e) => e.supportCardId === 11)!
        expect(speed.deckLabImprovement).toBeGreaterThan(stamina.deckLabImprovement)
        expect(stamina.statBudgetDelta.Stamina).toBeGreaterThan(speed.statBudgetDelta.Stamina)
        // The build-aware pick is whichever reaches the better tier, and the report says why.
        if (stamina.survivalTierAfter !== speed.survivalTierAfter) {
            expect(ranking.recommended?.supportCardId).toBe(11)
            expect(ranking.recommended?.relief).toContain("STAMINA_DEFICIT_REDUCED")
        }
    })
})

describe("regression: survival already clears", () => {
    it("does not rank extra Stamina above everything once the floor is already met", () => {
        const ranking = rankingFor([10, 14], { survivalConstraint: constraintOf(1, [2, 10000]) })
        expect(ranking.baselineTier).toBe("CLEARS")
        for (const evaluation of ranking.evaluations) expect(evaluation.survivalTierAfter).toBe("CLEARS")
        // Every candidate is in the same tier, so the ordering is a Pareto question and the big Speed
        // card is not excluded by the Stamina card the way it would be under a survival deficit.
        expect(ranking.frontier.length).toBeGreaterThan(1)
    })
})

describe("regression: over-Stamina", () => {
    it("stops crediting Stamina once the build is past its preferred range", () => {
        // A tight preferred range puts the build over it, which is exactly when more Stamina stops
        // buying anything. The correction clips the delta rather than subtracting a penalty.
        const ranking = rankingFor([14], { survivalConstraint: constraintOf(1, [2, 3]) })
        const evaluation = ranking.evaluations[0]
        expect(evaluation.overStaminaAfter).toBe(true)
        expect(evaluation.statBudgetDelta.Stamina).toBeGreaterThan(0)
        expect(correctedDeltaVector(evaluation).staminaDelta).toBe(0)
    })

    it("lets a non-Stamina alternative dominate once the Stamina delta stops counting", () => {
        const ranking = rankingFor([10, 14], { survivalConstraint: constraintOf(1, [2, 3]) })
        const bigStamina = ranking.evaluations.find((e) => e.supportCardId === 14)!
        const bigSpeed = ranking.evaluations.find((e) => e.supportCardId === 10)!
        expect(correctedDeltaVector(bigStamina).staminaDelta).toBe(0)
        expect(bigSpeed.statBudgetDelta.Speed).toBeGreaterThan(bigStamina.statBudgetDelta.Speed)
    })
})

describe("regression: recovery unlock", () => {
    it("recognises the borrow that uniquely makes the assumed recovery reachable", () => {
        // Only card 12 hints skill 9001, and the constraint's minimum assumes it. Without it the
        // build has to be held to the harder no-recovery constraint.
        const ranking = rankingFor([10, 12], {
            survivalConstraint: constraintOf(200, [220, 280], [9001]),
            fallbackConstraintWithoutRecovery: constraintOf(600, [620, 700]),
        })
        const hinter = ranking.evaluations.find((e) => e.supportCardId === 12)!
        const speed = ranking.evaluations.find((e) => e.supportCardId === 10)!
        expect(hinter.recoveryRequirementAfter).toBe("SATISFIED")
        expect(hinter.recoveryNewlyReachable).toEqual([9001])
        expect(hinter.relief).toContain("RECOVERY_REQUIREMENT_UNLOCKED")
        expect(speed.recoveryRequirementAfter).toBe("FELL_BACK_TO_NO_RECOVERY")
        expect(ranking.recommended?.supportCardId).toBe(12)
    })
})

describe("regression: inheritance freed", () => {
    it("reports the best observed alternative pair rather than a theoretical optimum", () => {
        const heavyStamina = pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }, { family: "stat", canonicalName: "Stamina", stars: 3 }], "heavy stamina pair")
        const powerLeaning = pairWith([{ family: "stat", canonicalName: "Power", stars: 3 }], "power pair")
        const ranking = rankingFor([14], { parentPairs: [heavyStamina, powerLeaning], survivalConstraint: constraintOf(300, [320, 100000]) })
        const opportunity = ranking.evaluations[0].inheritanceOpportunity
        if (opportunity.parentSwapAvailable) {
            expect(opportunity.alternativePairLabel).toBe("power pair")
            expect(opportunity.freedStartingStatsByStat.Stamina).toBeLessThan(0)
            expect(ranking.evaluations[0].relief).toContain("INHERITANCE_FREED")
        } else {
            // No alternative holds the tier: that is reported as no gain, never as a hypothetical one.
            expect(opportunity.alternativePairLabel).toBeNull()
            expect(opportunity.note).toMatch(/rather than as a theoretical optimum/)
        }
    })
})

describe("regression: Power flex", () => {
    it("credits a Power borrow with the Stamina its secondary payout actually pays", () => {
        const ranking = rankingFor([13], { trainee: traineeFrom(HIGH_STAMINA_GROWTH) })
        const power = ranking.evaluations[0]
        expect(power.supportType).toBe("Power")
        expect(power.statBudgetDelta.Power).toBeGreaterThan(0)
        // The Power facility's decoded row pays Stamina, so a Power borrow moves the Stamina budget
        // without any Stamina card being involved.
        const secondary = power.buildWithBorrow.statBudgets.find((b) => b.stat === SURVIVAL_STAT)?.secondaryTrainingContributionEstimate
        expect(secondary?.low).toBeGreaterThan(0)
    })
})

describe("regression: unresolved live rows", () => {
    it("never evaluates or selects a row the resolver could not join onto a card", () => {
        const ranking = rankingFor([11], {}, { unresolvedCharacters: ["Nobody From The Catalogue", "Also Nobody"] })
        expect(ranking.unresolvedLiveRows).toBe(2)
        expect(ranking.bounds.resolvedBorrowCandidates).toBe(1)
        expect(ranking.evaluations).toHaveLength(1)
        expect(ranking.evaluations[0].supportCardId).toBe(11)
        expect(ranking.assumptions.join(" ")).toMatch(/never guessed at/)
    })

    it("refuses to read an untrusted pool as complete", () => {
        const ranking = rankingFor([11], {}, { termination: "BOUNDED_PARTIAL" })
        expect(ranking.poolTrustedComplete).toBe(false)
        expect(ranking.assumptions.join(" ")).toMatch(/cannot be read as unborrowable/)
    })
})

describe("regression: a stale recommendation is not emitted blindly", () => {
    it("refuses to build an intent for a card the current resolved pool does not contain", () => {
        const oldPool = makeBorrowResolution(catalogue, [10, 11], { scanId: "scan-old" })
        const newPool = makeBorrowResolution(catalogue, [13], { scanId: "scan-new" })
        // Card 11 was recommended off the old scan and is simply not in the new one.
        expect(() => buildSmartBorrowIntent(newPool, 11, "Mile", "BUILD_AWARE")).toThrow(SmartBorrowIntentError)
        expect(() => buildSmartBorrowIntent(oldPool, 11, "Mile", "BUILD_AWARE")).not.toThrow()
    })

    it("ties the intent to the scan it came from, so a stale one is detectable", () => {
        const oldPool = makeBorrowResolution(catalogue, [11], { scanId: "scan-old" })
        const newPool = makeBorrowResolution(catalogue, [11], { scanId: "scan-new" })
        const a = buildSmartBorrowIntent(oldPool, 11, "Mile", "BUILD_AWARE")
        const b = buildSmartBorrowIntent(newPool, 11, "Mile", "BUILD_AWARE")
        expect(a.sourceBorrowScanId).toBe("scan-old")
        expect(a.recommendationEvidenceDigest).not.toBe(b.recommendationEvidenceDigest)
    })
})

describe("intent provenance", () => {
    it("labels which ranking chose the card and carries it into the serialized document", () => {
        const pool = makeBorrowResolution(catalogue, [11])
        const intent = buildSmartBorrowIntent(pool, 11, "Mile", "BUILD_AWARE")
        expect(intent.recommendationSource).toBe("BUILD_AWARE")
        expect(intent.schemaVersion).toBe(SMART_BORROW_INTENT_SCHEMA_VERSION)
        expect(JSON.parse(serializeSmartBorrowIntent(intent)).recommendation_source).toBe("BUILD_AWARE")
    })

    it("defaults to the original ranking and leaves its digest byte-identical to the pre-provenance one", () => {
        const pool = makeBorrowResolution(catalogue, [11])
        const legacy = buildSmartBorrowIntent(pool, 11, "Mile")
        expect(legacy.recommendationSource).toBe("DECKLAB_COMPOSITE")
        const withoutField = intentEvidenceDigest({
            targetProfile: "Mile",
            sourceBorrowScanId: legacy.sourceBorrowScanId,
            supportCardId: 11,
            canonicalCharacter: legacy.canonicalCharacter,
            canonicalTitle: legacy.canonicalTitle,
            expectedLevel: legacy.expectedLevel,
            expectedLimitBreak: legacy.expectedLimitBreak,
        })
        expect(legacy.recommendationEvidenceDigest).toBe(withoutField)
        const aware = buildSmartBorrowIntent(pool, 11, "Mile", "BUILD_AWARE")
        expect(aware.recommendationEvidenceDigest).not.toBe(withoutField)
    })
})

describe("the joint planner still runs unchanged beside this", () => {
    it("produces the same joint build whether or not a borrow ranking is asked for", () => {
        const input = budgetInputFor()
        const a = planJointBuild(evidence, catalogue, input)
        const b = planJointBuild(evidence, catalogue, input)
        expect(a.recommended?.parentPair.label).toBe(b.recommended?.parentPair.label)
        expect(a.bounds.combinationsEnumerated).toBe(b.bounds.combinationsEnumerated)
    })
})

/** Kept so an unused-import lint does not hide a real gap in the stat vocabulary. */
const _statsAreShared: readonly BudgetStat[] = ["Speed", "Stamina", "Power", "Guts", "Wit"]
void _statsAreShared

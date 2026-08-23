// STAM-2 Joint Build Budget Planner tests.
//
// Two kinds of test live here and they are kept apart deliberately.
//
// The unit tests below prove one mechanic each: that a ladder is read off the right rung, that a
// deficit is measured at the low end and a surplus at the median, that Power training's secondary
// Stamina lands in the secondary column and not the primary one.
//
// The regression fixtures at the bottom prove QUALITATIVE behaviour the planner is supposed to
// reproduce without anybody having encoded it: that a zero-growth trainee with no Stamina card leans
// on inheritance, that a high-growth trainee with a strong Stamina card can be given too much of it,
// that Power-flex works only where the numbers let it, that a low-limit-break card carries more
// friendship-ramp burden than the same card fully broken, and that a build which cannot reach the
// recovery its survival minimum assumed is not allowed to claim that minimum. None of those five is
// tuned for: they are read off the same model the real reports run on, and if the model stops
// producing them the right response is to look at the model, not at the fixture.

import { EFFECT } from "../../deckLab/supportCardData.ts"
import { allocateTurns, archetypeProfile, deckFitsArchetype, operatorAllocation } from "../archetypes.ts"
import { valueBorrow } from "../borrow.ts"
import { SURVIVAL_STAT, buildStatBudgets, jointConfidence, readSurvivalVerdict } from "../budget.ts"
import { createBuildBudgetEvidence, normalizeFactorName } from "../evidence.ts"
import { priceInheritance, resolveFactorLevel } from "../inheritance.ts"
import { clampedStaminaMargin, dominates, evaluateCandidate, paretoFrontier, planJointBuild, type BuildBudgetInput, type ParetoVector } from "../joint.ts"
import { resolveRecoveryAccess } from "../recovery.ts"
import { formatJointBuildRecommendation } from "../report.ts"
import { DEFAULT_TRAINING_TURNS, deckStartingContribution, friendshipRampBurden, projectTrainingProduction } from "../training.ts"
import { BuildBudgetError, bracketOf, weakestConfidence, type BudgetStat } from "../types.ts"
import { resolveBudgetTrainee } from "../adapter.ts"
import {
    BLUE_START_LADDER,
    HIGH_STAMINA_GROWTH,
    LOW_STAMINA_GROWTH,
    buildCatalogue,
    buildEvidence,
    constraintOf,
    deckTargetFor,
    flatCurve,
    pairWith,
    scoreFixtureDeck,
    traineeFrom,
} from "./fixtures.ts"

const evidence = buildEvidence()

/** A catalogue with one card per role, so a deck's composition is the only thing that varies. */
const catalogue = buildCatalogue([
    { id: 1, charaId: 11, title: "Speed One", supportType: "Speed", effects: [{ type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(20) }] },
    { id: 2, charaId: 12, title: "Speed Two", supportType: "Speed", effects: [{ type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(20) }] },
    { id: 3, charaId: 13, title: "Power One", supportType: "Power", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(10) }] },
    { id: 4, charaId: 14, title: "Power Two", supportType: "Power", effects: [{ type: EFFECT.TRAINING_EFFECTIVENESS, curve: flatCurve(10) }] },
    {
        id: 5,
        charaId: 15,
        title: "Stamina Strong",
        supportType: "Stamina",
        effects: [
            { type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(25) },
            { type: EFFECT.STAMINA_BONUS, curve: flatCurve(2) },
            { type: EFFECT.INITIAL_STAMINA, curve: flatCurve(30) },
        ],
        hintSkillIds: [9001],
    },
    { id: 6, charaId: 16, title: "Wit One", supportType: "Wit", effects: [{ type: EFFECT.SKILL_POINT_BONUS, curve: flatCurve(20) }] },
    { id: 7, charaId: 17, title: "Guts One", supportType: "Guts", effects: [] },
])

const target = deckTargetFor(catalogue, "URA Finale")
const staminaDeck = scoreFixtureDeck(catalogue, target, [1, 2, 3, 5, 6, 7])
const powerDeck = scoreFixtureDeck(catalogue, target, [1, 2, 3, 4, 6, 7])

function inputFor(overrides: Partial<BuildBudgetInput> = {}): BuildBudgetInput {
    return {
        evidenceVersion: 1,
        targetLabel: "fixture long turf late",
        scenarioId: 1,
        survivalConstraint: constraintOf(250, [270, 400]),
        trainee: traineeFrom(LOW_STAMINA_GROWTH),
        parentPairs: [pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }])],
        decks: [{ label: "stamina deck", score: staminaDeck }],
        trainingTurns: 40,
        ...overrides,
    }
}

describe("evidence", () => {
    it("refuses a document whose schema is not the one it knows", () => {
        expect(() => createBuildBudgetEvidence(JSON.stringify({ schema: "something_else", schemaVersion: 1 }))).toThrow(BuildBudgetError)
    })

    it("refuses a document whose schema version has moved", () => {
        expect(() => createBuildBudgetEvidence(JSON.stringify({ schema: "build_budget_evidence", schemaVersion: 99 }))).toThrow(BuildBudgetError)
    })

    it("joins a factor name across grade glyphs, case and spacing", () => {
        expect(normalizeFactorName("Firm Conditions ○")).toBe(normalizeFactorName("firm  conditions"))
        expect(normalizeFactorName("Stamina")).toBe("STAMINA")
    })

    it("reads the blue ladder, the scenario cap bonus and the career length off the document", () => {
        const group = evidence.factorGroup("stat", "Stamina")
        expect(group?.effects.find((e) => e.kind === "START_STAT")?.value1).toEqual(BLUE_START_LADDER)
        expect(evidence.scenarioCapBonus(3)?.Speed).toBe(400)
        expect(evidence.careerTurns(1)).toBe(78)
    })

    it("keeps camp trainings out of the base-training lookup", () => {
        expect(evidence.baseTraining(1, "Power")?.isCamp).toBe(false)
        expect(evidence.baseTraining(1, "Power")?.stats.Stamina).toBe(6)
    })
})

describe("inheritance", () => {
    it("brackets the ladder level between the strongest single carrier and the summed stars", () => {
        expect(resolveFactorLevel([3], 10)).toMatchObject({ lowLevel: 3, highLevel: 3, clamped: false })
        expect(resolveFactorLevel([3, 3], 10)).toMatchObject({ lowLevel: 3, highLevel: 6, clamped: false })
        expect(resolveFactorLevel([3, 3, 3, 3], 10)).toMatchObject({ lowLevel: 3, highLevel: 10, clamped: true })
    })

    it("reads a pink Spark off its own two-rung ladder, not the blue ten-rung one", () => {
        const priced = priceInheritance(evidence, [
            { family: "aptitude", canonicalName: "Long", stars: 3 },
            { family: "aptitude", canonicalName: "Long", stars: 3 },
        ])
        expect(priced.priced[0].level.ladderLength).toBe(2)
        expect(priced.aptitudeSteps.Long).toBe(2)
    })

    it("prices a single three-star Stamina Spark at the ladder's third rung, both ends", () => {
        const priced = priceInheritance(evidence, [{ family: "stat", canonicalName: "Stamina", stars: 3 }])
        expect(priced.startStats.Stamina).toEqual(bracketOf(7, 7))
        expect(priced.capBonus.Stamina).toBe(1)
    })

    it("widens the bracket when both parents carry the same Spark", () => {
        const priced = priceInheritance(evidence, [
            { family: "stat", canonicalName: "Stamina", stars: 3 },
            { family: "stat", canonicalName: "Stamina", stars: 3 },
        ])
        expect(priced.startStats.Stamina.low).toBe(7)
        expect(priced.startStats.Stamina.high).toBe(16)
    })

    it("reports a Spark it cannot name rather than dropping it silently", () => {
        const priced = priceInheritance(evidence, [{ family: "stat", canonicalName: "Charisma", stars: 3 }])
        expect(priced.priced).toHaveLength(0)
        expect(priced.unpriced[0]).toMatchObject({ canonicalName: "Charisma", reason: "NAME_NOT_IN_FACTOR_DOMAIN" })
    })

    it("hands the recovery skill a white Spark grants to the recovery check", () => {
        const priced = priceInheritance(evidence, [{ family: "white", canonicalName: "Deep Breaths", stars: 3 }])
        expect(priced.skillIds).toEqual([9001])
    })
})

describe("turn allocation", () => {
    it("distributes whole turns that sum to the budget exactly", () => {
        const allocation = allocateTurns(archetypeProfile("STAMINA_FLEX"), 40)
        const total = (Object.values(allocation.byStat) as number[]).reduce((s, v) => s + v, 0)
        expect(total).toBe(40)
    })

    it("keeps a zero-weight facility at zero rather than rounding it up", () => {
        const allocation = allocateTurns(archetypeProfile("NO_STAMINA_FLEX"), 41)
        expect(allocation.byStat.Stamina).toBe(0)
    })

    it("gates archetypes on deck composition, never on a card name", () => {
        expect(deckFitsArchetype(archetypeProfile("POWER_FLEX"), 0)).toBe(true)
        expect(deckFitsArchetype(archetypeProfile("POWER_FLEX"), 1)).toBe(false)
        expect(deckFitsArchetype(archetypeProfile("STAMINA_FLEX"), 0)).toBe(false)
    })

    it("accepts an operator allocation and reports its own total", () => {
        const allocation = operatorAllocation({ Speed: 10, Stamina: 10, Power: 10, Guts: 0, Wit: 0 } as Record<BudgetStat, number>)
        expect(allocation.trainingTurns).toBe(30)
        expect(allocation.origin).toBe("OPERATOR")
    })
})

describe("training production", () => {
    const allocation = operatorAllocation({ Speed: 0, Stamina: 0, Power: 10, Guts: 0, Wit: 0 } as Record<BudgetStat, number>)

    it("puts Power training's Stamina payout in the secondary column, not the primary one", () => {
        const production = projectTrainingProduction(evidence, catalogue, powerDeck.cards, 1, traineeFrom(LOW_STAMINA_GROWTH).growth, allocation)
        expect(production.primary.Power.low).toBeGreaterThan(0)
        expect(production.primary.Stamina.low).toBe(0)
        // Base 6 Stamina per Power training, times the two Power cards' unconditional 10% training
        // effectiveness each, times ten turns: 6 * 1.2 * 10. Growth on Stamina is zero for this trainee.
        expect(production.secondary.Stamina.low).toBeCloseTo(72, 5)
    })

    it("pays less secondary Stamina in the scenario whose decoded row pays less", () => {
        const ura = projectTrainingProduction(evidence, catalogue, powerDeck.cards, 1, traineeFrom(LOW_STAMINA_GROWTH).growth, allocation)
        const gc = projectTrainingProduction(evidence, catalogue, powerDeck.cards, 3, traineeFrom(LOW_STAMINA_GROWTH).growth, allocation)
        expect(gc.secondary.Stamina.low).toBeLessThan(ura.secondary.Stamina.low)
    })

    it("applies the trainee's growth percentage to the gain", () => {
        const speedTurns = operatorAllocation({ Speed: 10, Stamina: 0, Power: 0, Guts: 0, Wit: 0 } as Record<BudgetStat, number>)
        const low = projectTrainingProduction(evidence, catalogue, powerDeck.cards, 1, traineeFrom(LOW_STAMINA_GROWTH).growth, speedTurns)
        const high = projectTrainingProduction(evidence, catalogue, powerDeck.cards, 1, traineeFrom(HIGH_STAMINA_GROWTH).growth, speedTurns)
        // 20% Speed growth against 10%: 11 * 1.2 * 10 = 132 against 11 * 1.1 * 10 = 121.
        expect(low.primary.Speed.low).toBeCloseTo(132, 5)
        expect(high.primary.Speed.low).toBeCloseTo(121, 5)
    })

    it("brackets the deck's contribution rather than claiming one multiplier", () => {
        const production = projectTrainingProduction(evidence, catalogue, staminaDeck.cards, 1, traineeFrom(HIGH_STAMINA_GROWTH).growth, operatorAllocation({
            Speed: 0,
            Stamina: 10,
            Power: 0,
            Guts: 0,
            Wit: 0,
        } as Record<BudgetStat, number>))
        expect(production.primary.Stamina.high).toBeGreaterThan(production.primary.Stamina.low)
    })

    it("reads the deck's decoded initial stats and stat ceiling raises", () => {
        const start = deckStartingContribution(catalogue, staminaDeck.cards)
        expect(start.initialStats.Stamina).toBe(30)
    })
})

describe("stat budgets", () => {
    const production = projectTrainingProduction(evidence, catalogue, staminaDeck.cards, 1, traineeFrom(HIGH_STAMINA_GROWTH).growth, allocateTurns(archetypeProfile("STAMINA_FLEX"), 40))

    function budgetsWith(constraintMin: number, preferred: readonly [number, number] | null) {
        return buildStatBudgets({
            trainee: traineeFrom(HIGH_STAMINA_GROWTH),
            inheritance: priceInheritance(evidence, [{ family: "stat", canonicalName: "Stamina", stars: 3 }]),
            deckStart: deckStartingContribution(catalogue, staminaDeck.cards),
            production,
            scenarioCapBonus: evidence.scenarioCapBonus(1) as Record<BudgetStat, number>,
            baselineStatCap: 1200,
            constraint: constraintOf(constraintMin, preferred),
            confidence: "moderate",
        })
    }

    it("puts a survival floor on Stamina and on no other stat", () => {
        const budgets = budgetsWith(200, [220, 260])
        expect(budgets.find((b) => b.stat === SURVIVAL_STAT)?.requiredFloor).toBe(200)
        for (const budget of budgets.filter((b) => b.stat !== SURVIVAL_STAT)) expect(budget.requiredFloor).toBeNull()
    })

    it("measures the deficit at the low end of the projection, not the midpoint", () => {
        const stamina = budgetsWith(100000, null).find((b) => b.stat === SURVIVAL_STAT)
        expect(stamina?.deficitToMinimum).toBeGreaterThan(0)
        const generous = budgetsWith(1, null).find((b) => b.stat === SURVIVAL_STAT)
        expect(generous?.deficitToMinimum).toBe(0)
    })

    it("clamps a projection at the stat ceiling and says it did", () => {
        const budgets = buildStatBudgets({
            trainee: traineeFrom(HIGH_STAMINA_GROWTH),
            inheritance: priceInheritance(evidence, []),
            deckStart: deckStartingContribution(catalogue, staminaDeck.cards),
            production,
            scenarioCapBonus: evidence.scenarioCapBonus(1) as Record<BudgetStat, number>,
            baselineStatCap: 100,
            constraint: null,
            confidence: "moderate",
        })
        const stamina = budgets.find((b) => b.stat === SURVIVAL_STAT)
        expect(stamina?.cappedOut).toBe(true)
        expect(stamina?.projected.median).toBeLessThanOrEqual(stamina?.statCap ?? 0)
    })

    it("never reports a confidence above its weakest input", () => {
        expect(weakestConfidence("moderate", "low")).toBe("low")
        const unpriced = priceInheritance(evidence, [{ family: "stat", canonicalName: "Charisma", stars: 3 }])
        expect(jointConfidence(constraintOf(1, null, [], "moderate"), unpriced, "DECODED", true)).toBe("low")
        const priced = priceInheritance(evidence, [{ family: "stat", canonicalName: "Stamina", stars: 3 }])
        expect(jointConfidence(constraintOf(1, null, [], "low"), priced, "DECODED", true)).toBe("low")
        expect(jointConfidence(constraintOf(1, null, [], "moderate"), priced, "DECODED", true)).toBe("moderate")
    })
})

describe("survival verdict", () => {
    function verdictFor(projectedLow: number, projectedMedian: number, floor: number, preferred: readonly [number, number] | null) {
        return readSurvivalVerdict([
            {
                stat: "Stamina",
                requiredFloor: floor,
                preferredRange: preferred,
                startStat: 0,
                inheritanceFlat: bracketOf(0, 0),
                inheritanceCap: 0,
                supportInitialStats: 0,
                supportCapBonus: 0,
                scenarioCapBonus: 0,
                statCap: 1400,
                deckTrainingContributionEstimate: bracketOf(0, 0),
                secondaryTrainingContributionEstimate: bracketOf(0, 0),
                supportEventEstimate: bracketOf(0, 0),
                scenarioContributionEstimate: bracketOf(0, 0),
                projected: { low: projectedLow, median: projectedMedian, high: projectedMedian },
                deficitToMinimum: Math.max(0, floor - projectedLow),
                surplusAbovePreferred: preferred ? Math.max(0, projectedMedian - preferred[1]) : null,
                cappedOut: false,
                confidence: "moderate",
                assumptions: [],
            },
            {
                stat: "Power",
                requiredFloor: null,
                preferredRange: null,
                startStat: 0,
                inheritanceFlat: bracketOf(0, 0),
                inheritanceCap: 0,
                supportInitialStats: 0,
                supportCapBonus: 0,
                scenarioCapBonus: 0,
                statCap: 1400,
                deckTrainingContributionEstimate: bracketOf(0, 0),
                secondaryTrainingContributionEstimate: bracketOf(0, 0),
                supportEventEstimate: bracketOf(0, 0),
                scenarioContributionEstimate: bracketOf(0, 0),
                projected: { low: 300, median: 300, high: 300 },
                deficitToMinimum: null,
                surplusAbovePreferred: null,
                cappedOut: false,
                confidence: "moderate",
                assumptions: [],
            },
        ])
    }

    it("does not flag over-Stamina on a build that has not cleared the floor", () => {
        const verdict = verdictFor(100, 900, 600, [640, 700])
        expect(verdict.survivesSelectedRisk).toBe(false)
        expect(verdict.overStaminaRisk).toBe(false)
    })

    it("flags over-Stamina and names the stat with the most unused ceiling", () => {
        const verdict = verdictFor(700, 900, 600, [640, 700])
        expect(verdict.overStaminaRisk).toBe(true)
        expect(verdict.displacedStat).toBe("Power")
        expect(verdict.displacedHeadroom).toBeGreaterThan(0)
    })

    it("stays quiet when the build sits inside the preferred range", () => {
        const verdict = verdictFor(660, 670, 600, [640, 700])
        expect(verdict.survivesSelectedRisk).toBe(true)
        expect(verdict.overStaminaRisk).toBe(false)
    })
})

describe("recovery access", () => {
    it("counts a skill a Spark grants as inherited and free", () => {
        const plan = resolveRecoveryAccess(catalogue, powerDeck.cards, { constraint: constraintOf(600, null, [9001]), inheritedSkillIds: [9001] })
        expect(plan.status).toBe("SATISFIED")
        expect(plan.entries[0].route).toBe("INHERITED")
        expect(plan.skillPointCost).toBeNull()
    })

    it("counts a skill a deck card hints as reachable but priced", () => {
        const costs = new Map([[9001, 320]])
        const plan = resolveRecoveryAccess(catalogue, staminaDeck.cards, { constraint: constraintOf(600, null, [9001]), inheritedSkillIds: [], skillPointCosts: costs })
        expect(plan.entries[0].route).toBe("DECK_HINT")
        expect(plan.skillPointCost).toBe(320)
    })

    it("refuses the constraint when the assumed recovery is out of reach and no fallback exists", () => {
        const plan = resolveRecoveryAccess(catalogue, powerDeck.cards, { constraint: constraintOf(600, null, [9001]), inheritedSkillIds: [] })
        expect(plan.status).toBe("NOT_SATISFIED")
        expect(plan.unreachable).toEqual([9001])
    })

    it("falls back to the no-recovery constraint when the caller supplied one", () => {
        const plan = resolveRecoveryAccess(catalogue, powerDeck.cards, {
            constraint: constraintOf(600, null, [9001]),
            fallbackWithoutRecovery: constraintOf(720, [740, 800]),
            inheritedSkillIds: [],
        })
        expect(plan.status).toBe("FELL_BACK_TO_NO_RECOVERY")
        expect(plan.effectiveConstraint.minimumStamina).toBe(720)
    })
})

describe("pareto", () => {
    const base: ParetoVector = {
        staminaMargin: 10,
        speedBudget: 100,
        powerBudget: 100,
        gutsBudget: 100,
        witBudget: 100,
        skillPointValue: 100,
        recoveryReliability: 2,
        friendshipRampRelief: -10,
        lineageValue: 10,
        borrowIndependence: 1,
    }

    it("does not let one dimension dominate when another is worse", () => {
        expect(dominates({ ...base, speedBudget: 200, powerBudget: 50 }, base)).toBe(false)
        expect(dominates({ ...base, speedBudget: 200 }, base)).toBe(true)
    })

    it("keeps only the candidates nothing else dominates outright", () => {
        const strong = { ...base, speedBudget: 200 }
        const tradeoff = { ...base, powerBudget: 200, speedBudget: 50 }
        const dominated = { ...base, speedBudget: 10 }
        const kept = paretoFrontier([
            { pareto: strong },
            { pareto: tradeoff },
            { pareto: dominated },
        ] as never)
        expect(kept).toHaveLength(2)
    })

    it("credits survival margin only up to the top of the preferred range", () => {
        const budget = {
            requiredFloor: 600,
            preferredRange: [640, 700] as readonly [number, number],
            projected: { low: 1200, median: 1200, high: 1200 },
        }
        expect(clampedStaminaMargin(budget as never)).toBe(100)
    })
})

describe("joint search", () => {
    it("reports the bounds it searched and never claims to be exhaustive", () => {
        const result = planJointBuild(evidence, catalogue, inputFor())
        expect(result.bounds.exhaustive).toBe(false)
        expect(result.bounds.combinationsEnumerated).toBeGreaterThan(0)
    })

    it("refuses to run with no parent pair or no deck", () => {
        expect(() => planJointBuild(evidence, catalogue, inputFor({ parentPairs: [] }))).toThrow(BuildBudgetError)
        expect(() => planJointBuild(evidence, catalogue, inputFor({ decks: [] }))).toThrow(BuildBudgetError)
    })

    it("evaluates every archetype whose deck composition allows it", () => {
        const result = planJointBuild(evidence, catalogue, inputFor({ decks: [{ label: "stamina deck", score: staminaDeck }, { label: "power deck", score: powerDeck }] }))
        expect(new Set(result.byArchetype.map((c) => c.archetype))).toEqual(new Set(["STAMINA_FLEX", "POWER_FLEX", "NO_STAMINA_FLEX"]))
    })

    it("sets aside a build that misses the floor and says by how much", () => {
        const result = planJointBuild(evidence, catalogue, inputFor({ survivalConstraint: constraintOf(100000, null) }))
        expect(result.recommended).toBeNull()
        expect(result.rejected.length).toBeGreaterThan(0)
        expect(result.rejected[0].rejection).toBe("STAMINA_FLOOR_NOT_MET")
        expect(result.rejected[0].rejectionDetail).toMatch(/short of the required/)
    })

    it("renders a report that decomposes rather than asserting", () => {
        const result = planJointBuild(evidence, catalogue, inputFor())
        const text = formatJointBuildRecommendation(result)
        expect(text).toContain("SURVIVAL REQUIREMENT")
        expect(text).toContain("STAT BUDGETS")
        expect(text).toContain("TRADEOFFS")
        expect(text).toContain("NOT PRICED")
    })

    it("renders byte-identically on a repeated run", () => {
        const a = formatJointBuildRecommendation(planJointBuild(evidence, catalogue, inputFor()))
        const b = formatJointBuildRecommendation(planJointBuild(evidence, catalogue, inputFor()))
        expect(a).toBe(b)
    })

    it("defaults the turn budget to the named editorial constant", () => {
        const result = planJointBuild(evidence, catalogue, inputFor({ trainingTurns: undefined }))
        expect(result.byArchetype[0].allocation.trainingTurns).toBe(DEFAULT_TRAINING_TURNS)
    })
})

describe("trainee resolution", () => {
    it("resolves growth off the outfit, not the character", () => {
        const trainee = resolveBudgetTrainee(evidence, { traineeName: "Fixture Stayer", outfit: "[All Stamina]" })
        expect(trainee.growth.Stamina).toBe(20)
        expect(trainee.origin).toBe("DECODED")
    })

    it("refuses to guess between two outfits of the same character", () => {
        const twoOutfits = buildEvidence([
            LOW_STAMINA_GROWTH,
            { ...HIGH_STAMINA_GROWTH, cardId: 900102, character: LOW_STAMINA_GROWTH.character, outfit: "[Second Outfit]" },
        ])
        expect(() => resolveBudgetTrainee(twoOutfits, { traineeName: LOW_STAMINA_GROWTH.character })).toThrow(BuildBudgetError)
    })

    it("refuses a trainee the decoded table does not carry", () => {
        expect(() => resolveBudgetTrainee(evidence, { traineeName: "Nobody At All" })).toThrow(BuildBudgetError)
    })
})

// ---------------------------------------------------------------------------------------------
// Regression fixtures: qualitative behaviour the model must keep producing on its own.
// ---------------------------------------------------------------------------------------------

describe("regression: zero Stamina growth with no Stamina card", () => {
    // The observation this reproduces: a trainee with no Stamina growth, running a deck with no
    // Stamina card, has almost no way to convert turns into Stamina, so inheritance is the lever that
    // actually moves the deficit. Nothing in the planner encodes that; it falls out of a growth
    // multiplier of 1.0 on a facility nothing is invested in.
    const trainee = traineeFrom(LOW_STAMINA_GROWTH)
    const constraint = constraintOf(400, [420, 460])

    function deficitWithStaminaSparks(stars: readonly number[]): number {
        const result = planJointBuild(evidence, catalogue, {
            ...inputFor(),
            trainee,
            survivalConstraint: constraint,
            decks: [{ label: "power deck", score: powerDeck }],
            archetypes: ["POWER_FLEX"],
            parentPairs: [pairWith(stars.map((s) => ({ family: "stat", canonicalName: "Stamina", stars: s })))],
        })
        const candidate = result.byArchetype[0]
        return candidate.verdict.staminaDeficit
    }

    it("relieves the deficit as Stamina inheritance is added", () => {
        const none = deficitWithStaminaSparks([])
        const one = deficitWithStaminaSparks([3])
        const both = deficitWithStaminaSparks([3, 3])
        expect(none).toBeGreaterThan(0)
        expect(one).toBeLessThan(none)
        expect(both).toBeLessThanOrEqual(one)
    })
})

describe("regression: high Stamina growth plus a strong Stamina card", () => {
    // The observation this reproduces: piling Stamina inheritance onto a build that already clears the
    // floor buys nothing, and the planner should say so rather than rank it higher. The over-Stamina
    // flag is what does that, and the clamped margin is what stops the frontier preferring it anyway.
    const trainee = traineeFrom(HIGH_STAMINA_GROWTH)
    const constraint = constraintOf(300, [320, 360])

    function candidateWith(stars: readonly number[]) {
        const result = planJointBuild(evidence, catalogue, {
            ...inputFor(),
            trainee,
            survivalConstraint: constraint,
            decks: [{ label: "stamina deck", score: staminaDeck }],
            archetypes: ["STAMINA_FLEX"],
            parentPairs: [pairWith(stars.map((s) => ({ family: "stat", canonicalName: "Stamina", stars: s })))],
        })
        return result.byArchetype[0]
    }

    it("flags over-Stamina and names an alternative stat with room left", () => {
        const heavy = candidateWith([3, 3])
        expect(heavy.verdict.survivesSelectedRisk).toBe(true)
        expect(heavy.verdict.overStaminaRisk).toBe(true)
        expect(heavy.recommendationClass).toBe("OVER_STAMINA")
        expect(heavy.verdict.displacedStat).not.toBeNull()
    })

    it("credits extra Stamina only while it is still buying safety at the floor", () => {
        // Below the top of the preferred range, more Stamina genuinely makes the floor safer and the
        // margin says so. That is not the failure the clamp exists to prevent.
        const light = candidateWith([1])
        const heavy = candidateWith([3, 3])
        expect(heavy.pareto.staminaMargin).toBeGreaterThan(light.pareto.staminaMargin)
    })

    it("stops crediting it once the projection is past the preferred range", () => {
        // With a tight preferred range both builds are already over it, so the extra Sparks buy no
        // further margin and cannot out-rank a build that spent the same points elsewhere.
        function tightMargin(stars: readonly number[]): number {
            const result = planJointBuild(evidence, catalogue, {
                ...inputFor(),
                trainee,
                survivalConstraint: constraintOf(200, [210, 220]),
                decks: [{ label: "stamina deck", score: staminaDeck }],
                archetypes: ["STAMINA_FLEX"],
                parentPairs: [pairWith(stars.map((s) => ({ family: "stat", canonicalName: "Stamina", stars: s })))],
            })
            return result.byArchetype[0].pareto.staminaMargin
        }
        expect(tightMargin([3, 3])).toBe(tightMargin([1]))
        expect(tightMargin([1])).toBe(20)
    })
})

describe("regression: Power flex is viable only where the numbers close the floor", () => {
    // No trainee is named and no rule says "Power flex works for X". The archetype clears the floor
    // when the decoded secondary payout plus inheritance reaches it, and fails when it does not.
    const trainee = traineeFrom(LOW_STAMINA_GROWTH)
    const pair = pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }])

    function powerFlex(minimum: number) {
        const result = planJointBuild(evidence, catalogue, {
            ...inputFor(),
            trainee,
            parentPairs: [pair],
            survivalConstraint: constraintOf(minimum, [minimum + 40, minimum + 100]),
            decks: [{ label: "power deck", score: powerDeck }],
            archetypes: ["POWER_FLEX"],
        })
        return result.byArchetype[0]
    }

    it("clears a floor the secondary payout can reach", () => {
        expect(powerFlex(150).verdict.survivesSelectedRisk).toBe(true)
    })

    it("fails a floor it cannot, and reports the shortfall rather than a softer label", () => {
        const tough = powerFlex(900)
        expect(tough.verdict.survivesSelectedRisk).toBe(false)
        expect(tough.recommendationClass).toBe("STAMINA_DEFICIT")
        expect(tough.verdict.staminaDeficit).toBeGreaterThan(0)
    })
})

describe("regression: a low-limit-break card carries more friendship ramp burden", () => {
    // Same printed card, two account states. The one with less initial bond has more of its value
    // parked behind a gauge, which is a real difference between owning a card and owning it broken.
    const lowBond = buildCatalogue([
        {
            id: 50,
            charaId: 50,
            title: "Ramp Test",
            supportType: "Stamina",
            effects: [
                { type: EFFECT.FRIENDSHIP_BONUS, curve: flatCurve(25) },
                { type: EFFECT.INITIAL_FRIENDSHIP_GAUGE, curve: [0, -1, -1, -1, -1, -1, 15, -1, -1, -1, 35] },
            ],
        },
    ])
    const rampTarget = deckTargetFor(lowBond, "URA Finale")

    it("scores the under-levelled copy as the heavier ramp", () => {
        const underLevelled = friendshipRampBurden(lowBond, scoreFixtureDeck(lowBond, rampTarget, [50], 20).cards)
        const fullyBroken = friendshipRampBurden(lowBond, scoreFixtureDeck(lowBond, rampTarget, [50], 50).cards)
        expect(underLevelled).toBeGreaterThan(fullyBroken)
    })
})

describe("regression: recovery mismatch invalidates the minimum", () => {
    // A survival minimum that assumes a gold recovery is not a minimum for a build that cannot get it.
    // The planner must either re-solve against a harder constraint or refuse, never quietly keep the
    // easier number.
    const withRecoveryConstraint = constraintOf(300, [320, 360], [9001])
    const withoutRecoveryConstraint = constraintOf(560, [580, 640])

    it("refuses the build outright when no no-recovery constraint was supplied", () => {
        const result = planJointBuild(evidence, catalogue, {
            ...inputFor(),
            survivalConstraint: withRecoveryConstraint,
            decks: [{ label: "power deck", score: powerDeck }],
            archetypes: ["POWER_FLEX"],
            parentPairs: [pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }])],
        })
        expect(result.recommended).toBeNull()
        expect(result.rejected[0].rejection).toBe("RECOVERY_NOT_REACHABLE")
    })

    it("re-solves against the harder no-recovery constraint when one exists", () => {
        const result = planJointBuild(evidence, catalogue, {
            ...inputFor(),
            survivalConstraint: withRecoveryConstraint,
            fallbackConstraintWithoutRecovery: withoutRecoveryConstraint,
            decks: [{ label: "power deck", score: powerDeck }],
            archetypes: ["POWER_FLEX"],
            parentPairs: [pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }])],
        })
        const candidate = result.byArchetype[0]
        expect(candidate.recoveryPlan.status).toBe("FELL_BACK_TO_NO_RECOVERY")
        expect(candidate.statBudgets.find((b) => b.stat === SURVIVAL_STAT)?.requiredFloor).toBe(560)
    })

    it("keeps the easy minimum when the recovery is genuinely reachable", () => {
        const result = planJointBuild(evidence, catalogue, {
            ...inputFor(),
            survivalConstraint: withRecoveryConstraint,
            decks: [{ label: "stamina deck", score: staminaDeck }],
            archetypes: ["STAMINA_FLEX"],
            parentPairs: [pairWith([{ family: "stat", canonicalName: "Stamina", stars: 3 }])],
        })
        expect(result.byArchetype[0].recoveryPlan.status).toBe("SATISFIED")
        expect(result.byArchetype[0].statBudgets.find((b) => b.stat === SURVIVAL_STAT)?.requiredFloor).toBe(300)
    })
})

describe("borrow valuation", () => {
    it("values a borrow by the constraint it relieves, not by the score it adds", () => {
        const withoutBorrow = evaluateCandidate(evidence, catalogue, inputFor({ decks: [{ label: "power deck", score: powerDeck }] }), inputFor().parentPairs[0], { label: "power deck", score: powerDeck }, archetypeProfile("POWER_FLEX"))
        const borrowedDeck = scoreFixtureDeck(catalogue, target, [1, 2, 3, 5, 6, 7], 50, 5)
        const withBorrow = evaluateCandidate(evidence, catalogue, inputFor({ decks: [{ label: "borrowed", score: borrowedDeck }] }), inputFor().parentPairs[0], { label: "borrowed", score: borrowedDeck }, archetypeProfile("STAMINA_FLEX"))
        const effect = valueBorrow(withBorrow, withoutBorrow)
        expect(effect).not.toBeNull()
        expect(effect?.borrowCardId).toBe(5)
        expect(effect?.relief.length).toBeGreaterThan(0)
    })

    it("returns nothing for a deck that borrows nothing", () => {
        const candidate = evaluateCandidate(evidence, catalogue, inputFor(), inputFor().parentPairs[0], { label: "stamina deck", score: staminaDeck }, archetypeProfile("STAMINA_FLEX"))
        expect(valueBorrow(candidate, candidate)).toBeNull()
    })
})

describe("the marginal tier", () => {
    // A build whose midpoint clears the floor and whose pessimistic end does not is a different object
    // from one that misses at both ends, and the planner must not report them under one label.
    function planAgainst(minimum: number) {
        return planJointBuild(evidence, catalogue, inputFor({ survivalConstraint: constraintOf(minimum, [minimum + 20, minimum + 80]) }))
    }

    it("separates a build that clears at the midpoint from one that misses at both ends", () => {
        const clears = planAgainst(250)
        expect(clears.recommended).not.toBeNull()
        expect(clears.marginal).toHaveLength(0)

        // The fixture build projects Stamina 323.8 at the floor and 341.8 at the midpoint, so a floor
        // of 340 falls between the two ends and is exactly the case this tier exists for.
        const marginal = planAgainst(340)
        expect(marginal.recommended).toBeNull()
        expect(marginal.marginal.length).toBeGreaterThan(0)
        expect(marginal.marginal[0].recommendationClass).toBe("STAMINA_MARGINAL")
        expect(marginal.marginal[0].verdict.clearsAtMidpoint).toBe(true)

        const hopeless = planAgainst(100000)
        expect(hopeless.marginal).toHaveLength(0)
        expect(hopeless.rejected[0].recommendationClass).toBe("STAMINA_DEFICIT")
    })

    it("says how many more Stamina trainings would close the gap", () => {
        const marginal = planAgainst(340)
        const turns = marginal.marginal[0].verdict.staminaTurnsToCloseDeficit
        expect(turns).not.toBeNull()
        expect(turns).toBeGreaterThan(0)
    })

    it("declares the projection a floor rather than leaving the bias unstated", () => {
        const result = planAgainst(250)
        expect(result.projectionBias).toBe("FLOOR")
        expect(formatJointBuildRecommendation(result)).toContain("HOW TO READ THESE NUMBERS")
    })
})

describe("an unsatisfiable survival requirement", () => {
    // STAM-1 returns a null minimum when the debuff budget removes at least as much HP as the build
    // can hold, which is a real answer and not a missing one. It must not be reported as a shortfall
    // against an unknown number.
    it("is reported as unsatisfiable rather than as an unmet floor", () => {
        const result = planJointBuild(evidence, catalogue, inputFor({ survivalConstraint: constraintOf(null, null) }))
        expect(result.recommended).toBeNull()
        expect(result.rejected[0].rejection).toBe("STAMINA_FLOOR_NOT_MET")
        expect(result.rejected[0].rejectionDetail).toMatch(/unsatisfiable rather than merely unmet/)
    })
})

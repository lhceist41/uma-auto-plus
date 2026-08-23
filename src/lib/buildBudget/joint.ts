// STAM-2 Joint Build Budget Planner - the joint search. Pure, offline, deterministic.
//
// Evaluates parent pairs against decks against archetypes, all measured on the same race's survival
// requirement, and returns a Pareto frontier rather than a winner. The frontier is the honest shape:
// a build that clears the Stamina floor with more Power and less Wit is not worse than one that
// clears it the other way round, and collapsing the two onto one number would be a preference this
// planner has no business having.
//
// Survival is not one dimension among many. It partitions: candidates that clear the floor at the low
// end of their own projection are ranked against each other, and candidates that do not are reported
// separately with the reason, never quietly outscored into the middle of a list.
//
// The search is bounded and says so. Every parent pair against every deck against every archetype is
// a product that grows fast enough to be useless, so the caller supplies top-N pairs and top-M decks
// already ranked by their own labs, and this file reports exactly what it enumerated. Nothing here
// claims global optimality, because nothing here searched globally.

import type { CardValueProfile } from "../deckLab/cardValue.ts"
import type { DeckScore } from "../deckLab/deck.ts"
import type { SupportCardIndex } from "../deckLab/supportCardData.ts"
import type { SurvivalConstraint } from "../raceSurvival/types.ts"
import { ARCHETYPE_PROFILES, allocateTurns, deckFitsArchetype, operatorAllocation, type ArchetypeProfile } from "./archetypes.ts"
import { SURVIVAL_STAT, buildStatBudgets, jointConfidence, readSurvivalVerdict, type SurvivalVerdict } from "./budget.ts"
import type { BuildBudgetEvidence } from "./evidence.ts"
import { priceInheritance, type InheritanceContribution } from "./inheritance.ts"
import { resolveRecoveryAccess, type RecoveryPlan } from "./recovery.ts"
import { DEFAULT_TRAINING_TURNS, deckStartingContribution, friendshipRampBurden, projectTrainingProduction, type TrainingProduction, type TurnAllocation } from "./training.ts"
import {
    BUILD_BUDGET_SCHEMA,
    BUILD_BUDGET_SCHEMA_VERSION,
    BUDGET_STATS,
    BuildBudgetError,
    UNPRICED_BUDGET_MECHANICS,
    type BudgetConfidence,
    type BudgetParentPair,
    type BudgetStat,
    type BudgetTrainee,
    type BuildArchetype,
    type RecommendationClass,
    type StatBudget,
    type TradeoffLine,
} from "./types.ts"

/** One deck the search is allowed to consider, as DeckLab scored it. */
export interface DeckCandidate {
    readonly label: string
    readonly score: DeckScore
}

/** Everything the planner needs to answer for one target. Versioned; every field is explicit. */
export interface BuildBudgetInput {
    readonly evidenceVersion: number
    readonly targetLabel: string
    /** The game's own scenario id. Decides the base training gains and the stat cap bonuses. */
    readonly scenarioId: number
    readonly survivalConstraint: SurvivalConstraint
    /** A constraint solved with no recovery, used when the assumed recovery is out of reach. */
    readonly fallbackConstraintWithoutRecovery?: SurvivalConstraint | null
    readonly trainee: BudgetTrainee
    readonly parentPairs: readonly BudgetParentPair[]
    readonly decks: readonly DeckCandidate[]
    /** Trainings per career. Editorial; defaults to DEFAULT_TRAINING_TURNS and is reported as such. */
    readonly trainingTurns?: number
    /** Overrides the archetype turn profiles entirely when supplied. */
    readonly turnsByStat?: Readonly<Record<BudgetStat, number>> | null
    readonly traineeSkillIds?: readonly number[]
    readonly skillPointCosts?: ReadonlyMap<number, number> | null
    /** Archetypes to evaluate. Defaults to all three. */
    readonly archetypes?: readonly BuildArchetype[]
}

/** The dimensions a candidate is compared on. Every one is "higher is better" after normalization. */
export const PARETO_DIMENSIONS = [
    "staminaMargin",
    "speedBudget",
    "powerBudget",
    "gutsBudget",
    "witBudget",
    "skillPointValue",
    "recoveryReliability",
    "friendshipRampRelief",
    "lineageValue",
    "borrowIndependence",
] as const
export type ParetoDimension = (typeof PARETO_DIMENSIONS)[number]
export type ParetoVector = Readonly<Record<ParetoDimension, number>>

/** Why a candidate was not evaluated, or was evaluated and set aside. */
export const CANDIDATE_REJECTIONS = ["DECK_DOES_NOT_FIT_ARCHETYPE", "DECK_ILLEGAL", "STAMINA_FLOOR_NOT_MET", "RECOVERY_NOT_REACHABLE"] as const
export type CandidateRejection = (typeof CANDIDATE_REJECTIONS)[number]

export interface JointBuildCandidate {
    readonly archetype: BuildArchetype
    readonly parentPair: BudgetParentPair
    readonly deck: DeckCandidate
    readonly allocation: TurnAllocation
    readonly inheritance: InheritanceContribution
    readonly production: TrainingProduction
    readonly recoveryPlan: RecoveryPlan
    readonly statBudgets: readonly StatBudget[]
    readonly verdict: SurvivalVerdict
    readonly friendshipRampBurden: number
    readonly pareto: ParetoVector
    readonly recommendationClass: RecommendationClass
    readonly tradeoffs: readonly TradeoffLine[]
    readonly confidence: BudgetConfidence
    readonly rejection: CandidateRejection | null
    readonly rejectionDetail: string | null
}

/** What the planner enumerated, so a reader knows what "best" was best out of. */
export interface SearchBounds {
    readonly parentPairsConsidered: number
    readonly decksConsidered: number
    readonly archetypesConsidered: number
    readonly combinationsEnumerated: number
    readonly combinationsRejected: number
    readonly exhaustive: false
    readonly note: string
}

export interface JointBuildRecommendation {
    readonly schema: typeof BUILD_BUDGET_SCHEMA
    readonly schemaVersion: number
    readonly evidenceVersion: number
    readonly target: string
    readonly scenarioId: number
    readonly survivalConstraint: SurvivalConstraint
    readonly trainee: BudgetTrainee
    readonly bounds: SearchBounds
    /** Candidates that clear the floor at the low end of their own projection, Pareto-filtered. */
    readonly frontier: readonly JointBuildCandidate[]
    /** The frontier entry with the widest survival margin inside the preferred range. */
    readonly recommended: JointBuildCandidate | null
    /** Best candidate per archetype, whether or not it made the frontier. */
    readonly byArchetype: readonly JointBuildCandidate[]
    /** Candidates set aside, with the reason. At least one is always reported when any exists. */
    readonly rejected: readonly JointBuildCandidate[]
    readonly confidence: BudgetConfidence
    readonly assumptions: readonly string[]
    readonly unknownMechanics: readonly string[]
}

function staminaCardCount(cards: readonly CardValueProfile[]): number {
    return cards.filter((c) => c.card.supportType === SURVIVAL_STAT).length
}

/**
 * The survival margin, credited only up to the top of the preferred range.
 *
 * This is the single most important line in the Pareto vector. Without the clamp, "more Stamina" wins
 * every comparison and the frontier fills with builds that bought Stamina they will never spend,
 * which is precisely the failure the over-Stamina flag exists to catch. Clamping makes the dimension
 * mean "how safely does this clear the bar" and stops it meaning "how much did it overshoot".
 */
export function clampedStaminaMargin(budget: StatBudget | undefined): number {
    if (!budget || budget.requiredFloor === null) return 0
    const margin = budget.projected.low - budget.requiredFloor
    const ceiling = budget.preferredRange ? budget.preferredRange[1] - budget.requiredFloor : margin
    return Math.min(margin, Math.max(0, ceiling))
}

function recoveryReliabilityScore(plan: RecoveryPlan): number {
    if (plan.status === "SATISFIED") return 2
    if (plan.status === "NO_RECOVERY_ASSUMED") return 2
    if (plan.status === "FELL_BACK_TO_NO_RECOVERY") return 1
    return 0
}

/**
 * Lineage value: what the pair is worth beyond the stats it hands over.
 *
 * Base relation points are a decoded number and the only affinity figure this repository will state;
 * the lineage aggregation the game displays is not decoded and is not reconstructed. Priced Spark
 * coverage rides alongside it because a pair whose Sparks all resolved is a pair whose contribution
 * is known, which is itself worth something when choosing between two similar options.
 */
export function lineageValue(pair: BudgetParentPair, inheritance: InheritanceContribution): number {
    const affinity = pair.affinityPoints.reduce((sum: number, points) => sum + (points ?? 0), 0)
    const pricedShare = inheritance.priced.length + inheritance.unpriced.length === 0 ? 0 : inheritance.priced.length / (inheritance.priced.length + inheritance.unpriced.length)
    return Number((affinity + pricedShare * 10).toFixed(4))
}

function paretoVector(candidate: Omit<JointBuildCandidate, "pareto" | "recommendationClass" | "tradeoffs">): ParetoVector {
    const byStat = new Map(candidate.statBudgets.map((b) => [b.stat, b]))
    const borrowed = candidate.deck.score.borrowedCard !== null
    return {
        staminaMargin: Number(clampedStaminaMargin(byStat.get(SURVIVAL_STAT)).toFixed(4)),
        speedBudget: Number((byStat.get("Speed")?.projected.median ?? 0).toFixed(4)),
        powerBudget: Number((byStat.get("Power")?.projected.median ?? 0).toFixed(4)),
        gutsBudget: Number((byStat.get("Guts")?.projected.median ?? 0).toFixed(4)),
        witBudget: Number((byStat.get("Wit")?.projected.median ?? 0).toFixed(4)),
        skillPointValue: Number(candidate.production.skillPoints.median.toFixed(4)),
        recoveryReliability: recoveryReliabilityScore(candidate.recoveryPlan),
        friendshipRampRelief: Number((-candidate.friendshipRampBurden).toFixed(4)),
        lineageValue: lineageValue(candidate.parentPair, candidate.inheritance),
        borrowIndependence: borrowed ? 0 : 1,
    }
}

/** True when a is at least as good on every dimension and strictly better on one. */
export function dominates(a: ParetoVector, b: ParetoVector): boolean {
    let strictlyBetter = false
    for (const dimension of PARETO_DIMENSIONS) {
        if (a[dimension] < b[dimension]) return false
        if (a[dimension] > b[dimension]) strictlyBetter = true
    }
    return strictlyBetter
}

export function paretoFrontier(candidates: readonly JointBuildCandidate[]): JointBuildCandidate[] {
    return candidates.filter((candidate) => !candidates.some((other) => other !== candidate && dominates(other.pareto, candidate.pareto)))
}

/**
 * Names what this build is, from what the numbers did rather than from what it was asked to be.
 *
 * The order matters: a build that does not clear the floor is a STAMINA_DEFICIT whatever archetype
 * produced it, and a build whose recovery does not reach it is RECOVERY_DEPENDENT before it is
 * anything else, because both of those are conditions a reader must not miss under a friendlier label.
 */
export function classifyCandidate(archetype: BuildArchetype, verdict: SurvivalVerdict, plan: RecoveryPlan, budgets: readonly StatBudget[], borrowed: boolean): RecommendationClass {
    if (plan.status === "NOT_SATISFIED") return "RECOVERY_DEPENDENT"
    if (!verdict.survivesSelectedRisk) return "STAMINA_DEFICIT"
    if (verdict.overStaminaRisk) return "OVER_STAMINA"
    if (plan.status === "FELL_BACK_TO_NO_RECOVERY") return "RECOVERY_DEPENDENT"
    if (budgets.some((b) => b.cappedOut)) return "CAP_LIMITED"
    if (borrowed) return "BORROW_DEPENDENT"
    if (archetype === "POWER_FLEX") return "POWER_FLEX"
    if (archetype === "STAMINA_FLEX") return "STAMINA_FLEX"
    return "BALANCED"
}

/**
 * The levers this build used to pay for its Stamina, each with what it cost.
 *
 * Built from the candidate's own decomposition, never from a table of known tradeoffs. A lever only
 * appears when it actually supplied something, which is why a no-Stamina-card build shows no deck
 * slot line and a build with no useful Sparks shows no inheritance line.
 */
export function describeTradeoffs(candidate: Omit<JointBuildCandidate, "tradeoffs" | "recommendationClass">): TradeoffLine[] {
    const lines: TradeoffLine[] = []
    const stamina = candidate.statBudgets.find((b) => b.stat === SURVIVAL_STAT)
    if (!stamina) return lines

    if (stamina.inheritanceFlat.high > 0) {
        const otherStats = BUDGET_STATS.filter((s) => s !== SURVIVAL_STAT)
            .map((s) => ({ stat: s, value: candidate.statBudgets.find((b) => b.stat === s)?.inheritanceFlat.median ?? 0 }))
            .filter((entry) => entry.value > 0)
        lines.push({
            lever: "inheritance",
            staminaGained: Number(stamina.inheritanceFlat.median.toFixed(1)),
            costs: [
                `the same Sparks also supply ${otherStats.length ? otherStats.map((e) => `${e.value.toFixed(1)} ${e.stat}`).join(", ") : "no other stat"}; a pair weighted differently would move these figures against each other`,
            ],
        })
    }

    const staminaCards = staminaCardCount(candidate.deck.score.cards)
    if (staminaCards > 0 && stamina.deckTrainingContributionEstimate.median > 0) {
        lines.push({
            lever: "dedicated Stamina support",
            staminaGained: Number(stamina.deckTrainingContributionEstimate.median.toFixed(1)),
            costs: [`${staminaCards} of six deck slot${staminaCards === 1 ? "" : "s"} spent on Stamina cards`, `${candidate.allocation.byStat[SURVIVAL_STAT]} of ${candidate.allocation.trainingTurns} trainings spent on the Stamina facility`],
        })
    }

    if (stamina.secondaryTrainingContributionEstimate.median > 0) {
        lines.push({
            lever: "secondary Stamina from other facilities",
            staminaGained: Number(stamina.secondaryTrainingContributionEstimate.median.toFixed(1)),
            costs: ["no deck slot and no Stamina turns, but the amount is fixed by the scenario's decoded training rows and cannot be increased directly"],
        })
    }

    const payable = candidate.recoveryPlan.entries.filter((e) => e.route === "DECK_HINT")
    if (payable.length) {
        const cost = candidate.recoveryPlan.skillPointCost
        lines.push({
            lever: "recovery skills",
            staminaGained: 0,
            costs: [`${payable.length} recovery skill${payable.length === 1 ? "" : "s"} must be bought for ${cost === null ? "an unpriced number of" : cost} skill points, and the survival minimum is void without them`],
        })
    }

    if (candidate.deck.score.borrowedCard) {
        lines.push({
            lever: "borrowed card",
            staminaGained: 0,
            costs: [`the build depends on ${candidate.deck.score.borrowedCard.card.displayName} being available to borrow at launch`],
        })
    }

    return lines
}

/** Evaluates one (pair, deck, archetype) combination. */
export function evaluateCandidate(
    evidence: BuildBudgetEvidence,
    index: SupportCardIndex,
    input: BuildBudgetInput,
    pair: BudgetParentPair,
    deck: DeckCandidate,
    profile: ArchetypeProfile,
): JointBuildCandidate {
    const trainingTurns = input.trainingTurns ?? DEFAULT_TRAINING_TURNS
    const allocation: TurnAllocation = input.turnsByStat ? operatorAllocation(input.turnsByStat) : allocateTurns(profile, trainingTurns)

    const inheritance = priceInheritance(evidence, pair.factors)
    const recoveryPlan = resolveRecoveryAccess(index, deck.score.cards, {
        constraint: input.survivalConstraint,
        fallbackWithoutRecovery: input.fallbackConstraintWithoutRecovery ?? null,
        inheritedSkillIds: inheritance.skillIds,
        traineeSkillIds: input.traineeSkillIds,
        skillPointCosts: input.skillPointCosts,
    })

    const production = projectTrainingProduction(evidence, index, deck.score.cards, input.scenarioId, input.trainee.growth, allocation)
    const deckStart = deckStartingContribution(index, deck.score.cards)
    const scenarioCapBonus = evidence.scenarioCapBonus(input.scenarioId)
    if (!scenarioCapBonus) throw new BuildBudgetError("unknownScenario", `no decoded stat cap bonus for scenario ${input.scenarioId}`)

    const confidence = jointConfidence(recoveryPlan.effectiveConstraint, inheritance, input.trainee.origin, recoveryPlan.status === "SATISFIED" || recoveryPlan.status === "NO_RECOVERY_ASSUMED")

    const statBudgets = buildStatBudgets({
        trainee: input.trainee,
        inheritance,
        deckStart,
        production,
        scenarioCapBonus,
        baselineStatCap: evidence.document.statCaps.baseline,
        constraint: recoveryPlan.status === "NOT_SATISFIED" ? null : recoveryPlan.effectiveConstraint,
        confidence,
    })
    const verdict = readSurvivalVerdict(statBudgets)
    const rampBurden = friendshipRampBurden(index, deck.score.cards)

    const partial = {
        archetype: profile.archetype,
        parentPair: pair,
        deck,
        allocation,
        inheritance,
        production,
        recoveryPlan,
        statBudgets,
        verdict,
        friendshipRampBurden: rampBurden,
        confidence,
        rejection: null as CandidateRejection | null,
        rejectionDetail: null as string | null,
    }

    const pareto = paretoVector(partial)
    const recommendationClass = classifyCandidate(profile.archetype, verdict, recoveryPlan, statBudgets, deck.score.borrowedCard !== null)
    const tradeoffs = describeTradeoffs({ ...partial, pareto })

    let rejection: CandidateRejection | null = null
    let rejectionDetail: string | null = null
    if (recoveryPlan.status === "NOT_SATISFIED") {
        rejection = "RECOVERY_NOT_REACHABLE"
        rejectionDetail = `recovery ${recoveryPlan.unreachable.join(", ")} is not reachable and no no-recovery constraint was supplied`
    } else if (!verdict.survivesSelectedRisk) {
        rejection = "STAMINA_FLOOR_NOT_MET"
        const stamina = statBudgets.find((b) => b.stat === SURVIVAL_STAT)
        rejectionDetail = `projected Stamina floor ${Math.round(stamina?.projected.low ?? 0)} is ${Math.round(verdict.staminaDeficit)} short of the required ${stamina?.requiredFloor ?? "unknown"}`
    } else if (!deck.score.legality.legal) {
        rejection = "DECK_ILLEGAL"
        rejectionDetail = deck.score.legality.violations.map((v) => v.violation).join(", ")
    }

    return { ...partial, pareto, recommendationClass, tradeoffs, rejection, rejectionDetail }
}

/**
 * Runs the bounded joint search.
 *
 * Deterministic throughout: candidates are enumerated in the order the caller supplied their pairs and
 * decks, ties are broken on that order, and nothing here reads a clock or a random source.
 */
export function planJointBuild(evidence: BuildBudgetEvidence, index: SupportCardIndex, input: BuildBudgetInput): JointBuildRecommendation {
    if (!input.parentPairs.length) throw new BuildBudgetError("noParentPairs", "the joint search needs at least one parent pair")
    if (!input.decks.length) throw new BuildBudgetError("noDecks", "the joint search needs at least one deck")

    const wanted = input.archetypes ?? ARCHETYPE_PROFILES.map((p) => p.archetype)
    const profiles = ARCHETYPE_PROFILES.filter((p) => wanted.includes(p.archetype))

    const evaluated: JointBuildCandidate[] = []
    const rejected: JointBuildCandidate[] = []
    let enumerated = 0

    for (const profile of profiles) {
        for (const deck of input.decks) {
            if (!deckFitsArchetype(profile, staminaCardCount(deck.score.cards))) continue
            for (const pair of input.parentPairs) {
                enumerated += 1
                const candidate = evaluateCandidate(evidence, index, input, pair, deck, profile)
                if (candidate.rejection) rejected.push(candidate)
                else evaluated.push(candidate)
            }
        }
    }

    const frontier = paretoFrontier(evaluated).sort((a, b) => b.pareto.staminaMargin - a.pareto.staminaMargin || b.pareto.speedBudget - a.pareto.speedBudget)

    const byArchetype: JointBuildCandidate[] = []
    for (const profile of profiles) {
        const pool = [...evaluated, ...rejected].filter((c) => c.archetype === profile.archetype)
        if (!pool.length) continue
        // Best of an archetype means best that survives; if none does, the least-short one, so a reader
        // can see how far the archetype actually was from working rather than only that it failed.
        const surviving = pool.filter((c) => c.verdict.survivesSelectedRisk)
        const ranked = surviving.length ? surviving : pool
        const best = ranked.reduce((held, candidate) => {
            if (!held) return candidate
            if (surviving.length) return candidate.pareto.staminaMargin > held.pareto.staminaMargin ? candidate : held
            return candidate.verdict.staminaDeficit < held.verdict.staminaDeficit ? candidate : held
        })
        byArchetype.push(best)
    }

    const recommended = frontier.length ? frontier[0] : null

    const assumptions: string[] = [
        `Search bounds: ${input.parentPairs.length} parent pairs, ${input.decks.length} decks, ${profiles.length} archetypes. This is the caller's shortlist, ranked by ParentLab and DeckLab before it got here, and the frontier below is the frontier of that shortlist and nothing wider.`,
        `Scenario ${input.scenarioId}: base training gains, stat cap bonuses and career length are all read per scenario rather than shared.`,
        `Stat ceiling baseline ${evidence.document.statCaps.baseline}, decoded as the value every trainee card ships.`,
    ]
    if (recommended) assumptions.push(...recommended.inheritance.assumptions, ...recommended.production.assumptions, ...recommended.recoveryPlan.assumptions)

    const confidence: BudgetConfidence = recommended ? recommended.confidence : "low"

    return {
        schema: BUILD_BUDGET_SCHEMA,
        schemaVersion: BUILD_BUDGET_SCHEMA_VERSION,
        evidenceVersion: input.evidenceVersion,
        target: input.targetLabel,
        scenarioId: input.scenarioId,
        survivalConstraint: input.survivalConstraint,
        trainee: input.trainee,
        bounds: {
            parentPairsConsidered: input.parentPairs.length,
            decksConsidered: input.decks.length,
            archetypesConsidered: profiles.length,
            combinationsEnumerated: enumerated,
            combinationsRejected: rejected.length,
            exhaustive: false,
            note: "Bounded search over a caller-supplied shortlist. No claim of global optimality: combinations outside the shortlist were never evaluated.",
        },
        frontier,
        recommended,
        byArchetype,
        rejected: rejected.sort((a, b) => a.verdict.staminaDeficit - b.verdict.staminaDeficit),
        confidence,
        assumptions,
        unknownMechanics: UNPRICED_BUDGET_MECHANICS.map((m) => `${m.status} ${m.mechanic}`),
    }
}

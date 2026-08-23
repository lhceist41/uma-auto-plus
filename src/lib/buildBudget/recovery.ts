// STAM-2 Joint Build Budget Planner - recovery access. Pure, offline, deterministic.
//
// STAM-1's survival minimum is conditional. It says "this much Stamina survives, GIVEN these recovery
// skills fire". A planner that reads the minimum and ignores the condition would hand back builds
// that survive on paper and die on the track, so this file does one job: decide whether a build can
// actually get the skills the minimum assumed, and refuse the minimum when it cannot.
//
// A skill counts as reachable only through a route this repository can point at:
//
//   INHERITED     a green or white Spark in the parent pair grants it. Decoded from the same
//                 succession ladders the inheritance pricing uses.
//   DECK_HINT     a support card in the deck carries it in its decoded hint pool. Buying it still
//                 costs skill points, which is priced, but the skill is at least available.
//   TRAINEE_OWN   the caller states the trainee's own skill list carries it.
//
// A skill that exists in the catalogue and nowhere in the build is NOT_REACHABLE, and its absence
// invalidates the constraint that assumed it. "The card exists somewhere in the game" is not access.

import type { CardValueProfile } from "../deckLab/cardValue.ts"
import type { SupportCardIndex } from "../deckLab/supportCardData.ts"
import type { SurvivalConstraint } from "../raceSurvival/types.ts"
import { BuildBudgetError } from "./types.ts"

/** How a build can get hold of a recovery skill. */
export const RECOVERY_ROUTES = ["INHERITED", "DECK_HINT", "TRAINEE_OWN", "NOT_REACHABLE"] as const
export type RecoveryRoute = (typeof RECOVERY_ROUTES)[number]

export interface RecoveryAccessEntry {
    readonly skillId: number
    readonly route: RecoveryRoute
    /** Skill-point cost of buying it, when the caller supplied a cost table. Null when unknown. */
    readonly skillPointCost: number | null
    /** Which deck cards can hint it, for a report to name. */
    readonly hintingCardIds: readonly number[]
}

/** Whether the survival constraint's own assumption survives contact with this build. */
export const RECOVERY_PLAN_STATUSES = [
    /** Every recovery the constraint assumed is reachable. The minimum stands. */
    "SATISFIED",
    /** Some recovery is unreachable, and the caller supplied no constraint that does without it. */
    "NOT_SATISFIED",
    /** Some recovery is unreachable, and the fallback constraint that assumes none is used instead. */
    "FELL_BACK_TO_NO_RECOVERY",
    /** The constraint assumed no recovery in the first place. */
    "NO_RECOVERY_ASSUMED",
] as const
export type RecoveryPlanStatus = (typeof RECOVERY_PLAN_STATUSES)[number]

export interface RecoveryPlan {
    readonly status: RecoveryPlanStatus
    readonly entries: readonly RecoveryAccessEntry[]
    readonly reachable: readonly number[]
    readonly unreachable: readonly number[]
    /** Total skill points the reachable-but-unowned recovery would cost. Null when no cost table. */
    readonly skillPointCost: number | null
    /** The constraint the planner should actually hold the build to, after the check. */
    readonly effectiveConstraint: SurvivalConstraint
    readonly assumptions: readonly string[]
}

export interface RecoveryAccessInput {
    readonly constraint: SurvivalConstraint
    /** A constraint solved with no recovery at all, for when the assumed recovery is out of reach. */
    readonly fallbackWithoutRecovery?: SurvivalConstraint | null
    /** Skill ids the parent pair's Sparks grant, from the inheritance pricing. */
    readonly inheritedSkillIds: readonly number[]
    /** Skill ids the trainee already owns, when the caller knows them. */
    readonly traineeSkillIds?: readonly number[]
    /** skill id -> skill point cost, from the compiled skill catalogue. Optional. */
    readonly skillPointCosts?: ReadonlyMap<number, number> | null
}

/**
 * Resolves whether a build can reach the recovery its survival constraint assumed.
 *
 * The route order is deliberate. Inheritance is free and certain, so it is checked first; a deck hint
 * is available but has to be paid for in skill points, which is a real cost the opportunity model
 * needs to see; the trainee's own list is caller knowledge and is trusted as stated.
 */
export function resolveRecoveryAccess(index: SupportCardIndex, deck: readonly CardValueProfile[], input: RecoveryAccessInput): RecoveryPlan {
    const required = [...new Set(input.constraint.recoveryRequirements)].sort((a, b) => a - b)
    const inherited = new Set(input.inheritedSkillIds)
    const traineeOwn = new Set(input.traineeSkillIds ?? [])

    const hintersBySkill = new Map<number, number[]>()
    for (const profile of deck) {
        const card = index.byId.get(profile.card.supportCardId)
        if (!card) throw new BuildBudgetError("unknownCard", `support card ${profile.card.supportCardId} is not in the catalogue`)
        for (const skillId of card.hintSkillIds) {
            const held = hintersBySkill.get(skillId) ?? []
            held.push(card.id)
            hintersBySkill.set(skillId, held)
        }
    }

    const entries: RecoveryAccessEntry[] = required.map((skillId) => {
        const hintingCardIds = [...new Set(hintersBySkill.get(skillId) ?? [])].sort((a, b) => a - b)
        const route: RecoveryRoute = inherited.has(skillId) ? "INHERITED" : hintingCardIds.length ? "DECK_HINT" : traineeOwn.has(skillId) ? "TRAINEE_OWN" : "NOT_REACHABLE"
        const cost = input.skillPointCosts?.get(skillId) ?? null
        return { skillId, route, skillPointCost: cost, hintingCardIds }
    })

    const reachable = entries.filter((e) => e.route !== "NOT_REACHABLE").map((e) => e.skillId)
    const unreachable = entries.filter((e) => e.route === "NOT_REACHABLE").map((e) => e.skillId)

    // Inherited skills arrive owned; anything reached through a hint still has to be bought.
    const payable = entries.filter((e) => e.route === "DECK_HINT")
    const skillPointCost = input.skillPointCosts ? payable.reduce((sum, e) => sum + (e.skillPointCost ?? 0), 0) : null

    let status: RecoveryPlanStatus
    let effectiveConstraint = input.constraint
    if (!required.length) {
        status = "NO_RECOVERY_ASSUMED"
    } else if (!unreachable.length) {
        status = "SATISFIED"
    } else if (input.fallbackWithoutRecovery) {
        status = "FELL_BACK_TO_NO_RECOVERY"
        effectiveConstraint = input.fallbackWithoutRecovery
    } else {
        status = "NOT_SATISFIED"
    }

    const assumptions: string[] = []
    if (status === "NO_RECOVERY_ASSUMED") {
        assumptions.push("The survival constraint assumed no recovery, so nothing about this build's skill access can invalidate it.")
    }
    if (status === "SATISFIED") {
        assumptions.push("Every recovery skill the survival minimum assumed is reachable through inheritance, a deck hint or the trainee's own list.")
    }
    if (status === "FELL_BACK_TO_NO_RECOVERY") {
        assumptions.push(
            `Recovery ${unreachable.join(", ")} is not reachable by this build, so the minimum that assumed it does not apply. The no-recovery constraint is used instead, which is a higher Stamina bar.`,
        )
    }
    if (status === "NOT_SATISFIED") {
        assumptions.push(`Recovery ${unreachable.join(", ")} is not reachable by this build and no no-recovery constraint was supplied, so the survival requirement cannot be evaluated honestly for it.`)
    }
    if (payable.length) {
        const costText = skillPointCost === null ? "an unpriced number of" : String(skillPointCost)
        assumptions.push(`${payable.length} recovery skill${payable.length === 1 ? "" : "s"} reach the build only as a deck hint and still cost ${costText} skill points to buy.`)
    }
    assumptions.push("Reachability is not activation. STAM-1 reports recovery as potential HP because no activation probability is decoded for any skill.")

    return { status, entries, reachable, unreachable, skillPointCost, effectiveConstraint, assumptions }
}

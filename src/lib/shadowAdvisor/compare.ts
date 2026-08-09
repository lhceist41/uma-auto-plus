// Shadow Advisor S1 - comparison layer. Strictly separate from policy generation: it may see the advisor's
// already-computed recommendation AND the same turn's committed ReplayDecision, but it NEVER feeds any
// committed fact back into recommendation generation (the recommendation is an input, read-only). It also
// never compares against enteredRace: a RACE-committed turn is comparisonNotApplicable, because entered-race
// is post-decision execution data that must stay outside the advisor entirely.

import type { ReplayDecision } from "../replayLab.ts"
import type { ShadowRecommendation, ComparisonResult, AdvisorAction } from "./types.ts"

/** The committed-action families S1 can compare against. RACE/DATE/NONE are outside the advisor's model. */
const ADVISOR_COMPARABLE_ACTIONS: ReadonlySet<string> = new Set<AdvisorAction>(["TRAIN", "REST", "RECOVER_MOOD"])

/**
 * Classifies how the advisor recommendation relates to the bot's committed decision. `trainingSource` is
 * copied through as factual metadata (a forced pick differs from an ANALYSIS-selected one) for later
 * segmentation; it is NEVER used as advisor policy input. This function does not mutate the recommendation.
 */
export function compareToCommitted(recommendation: ShadowRecommendation, decision: ReplayDecision): ComparisonResult {
    const committedAction = decision.committedAction
    const committedTraining = decision.committedTraining
    const trainingSource = decision.trainingContest.trainingSource
    const advisorAction = recommendation.recommended?.action ?? null
    const advisorTraining = recommendation.recommended?.trainingType ?? null
    const meta = { advisorAction, advisorTraining, committedAction, committedTraining, trainingSource }

    // The advisor produced no actionable recommendation for this turn.
    if (recommendation.status !== "recommendationAvailable" || advisorAction === null) {
        return { state: "advisorUnavailable", ...meta }
    }
    // The bot committed an action S1 does not model (RACE / DATE / NONE / unknown): not comparable.
    if (committedAction === null || !ADVISOR_COMPARABLE_ACTIONS.has(committedAction)) {
        return { state: "comparisonNotApplicable", ...meta }
    }
    // Different action family (e.g. bot TRAIN, advisor REST).
    if (committedAction !== advisorAction) {
        return { state: "differentAction", ...meta }
    }
    // Same action family. For TRAIN, distinguish a facility disagreement.
    if (advisorAction === "TRAIN" && advisorTraining !== null && committedTraining !== null && advisorTraining !== committedTraining) {
        return { state: "sameActionDifferentTraining", ...meta }
    }
    return { state: "sameAction", ...meta }
}

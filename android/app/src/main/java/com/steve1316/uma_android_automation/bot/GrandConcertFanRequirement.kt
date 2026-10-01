package com.steve1316.uma_android_automation.bot

/**
 * Derives whether the requirement for the CURRENT period (the earliest goal-or-gate turn at or after now,
 * met or not) is still unmet at the current fan count. Unlike [GrandConcertFanPressure.evaluate] it does not
 * look past a satisfied current scope to a later unmet one (the Junior over-racing trap): once the current
 * goal is met the alarm must shut off even if a much larger requirement waits months later.
 */
object GrandConcertFanRequirement {
    enum class Type { FAN_GOAL, MANDATORY_GATE }

    enum class MatchKind { EXACT, NORMALIZED }

    /** A non-Grand-Concert scenario has no route-facts signal: the caller keeps its legacy value and logs nothing. */
    const val REASON_NO_SCENARIO_FACTS = "no-scenario-facts"

    sealed class Result {
        /**
         * Unmet: force racing. [targetFans] is the larger threshold when a same-turn goal and gate are both unmet.
         */
        data class Active(
            val type: Type,
            val targetFans: Int,
            val requirementTurn: Int,
            val deficit: Int,
            val match: MatchKind,
            val goalUnmet: Boolean,
            val gateUnmet: Boolean,
        ) : Result()

        data class Inactive(val reason: String, val match: MatchKind) : Result()

        /** Facts cannot answer authoritatively: the caller keeps its legacy behavior, never forcing from a guess. */
        data class Unknown(val reason: String) : Result()
    }

    fun evaluate(facts: GrandConcertFanFacts?, rawName: String, currentTurn: Int, currentFans: Int): Result {
        if (facts == null) return Result.Unknown("facts-unavailable")
        if (rawName.isBlank()) return Result.Unknown("trainee-name-empty")
        return when (val match = facts.match(rawName)) {
            is GrandConcertFanFacts.Match.UnknownNoMatch -> Result.Unknown("trainee-unmatched")
            is GrandConcertFanFacts.Match.UnknownAmbiguous -> Result.Unknown("trainee-ambiguous")
            is GrandConcertFanFacts.Match.Matched ->
                currentScope(match.facts, currentTurn, currentFans, if (match.exact) MatchKind.EXACT else MatchKind.NORMALIZED)
        }
    }

    private fun currentScope(cf: GrandConcertCharacterFanFacts, currentTurn: Int, currentFans: Int, match: MatchKind): Result {
        // Met requirements still define the period boundary, so they are not filtered out before the scope is chosen.
        val requirementTurns =
            (cf.fanGoals.map { it.deadlineTurn } + cf.mandatoryGates.map { it.turn }).filter { it >= currentTurn }
        val scopeTurn = requirementTurns.minOrNull() ?: return Result.Inactive("no-remaining-requirement", match)

        // A mandatory gate is unmet only below its cheapest option: within an ambiguous choice gate the minimum is
        // satisfiable, so never force on the unprovable higher threshold.
        val unmetGoal =
            cf.fanGoals.filter { it.deadlineTurn == scopeTurn && currentFans < it.targetFans }.maxByOrNull { it.targetFans }
        val unmetGate =
            cf.mandatoryGates.filter { it.turn == scopeTurn && currentFans < it.minFansNeeded }.maxByOrNull { it.minFansNeeded }
        val goalUnmet = unmetGoal != null
        val gateUnmet = unmetGate != null

        // A fully-met current scope stands down: no lookahead to a later unmet requirement (premature over-racing).
        if (!goalUnmet && !gateUnmet) return Result.Inactive("current-scope-met", match)

        // Same-turn goal and gate both unmet: force to the larger threshold; a tie prefers the goal type.
        val goalThreshold = unmetGoal?.targetFans
        val gateThreshold = unmetGate?.minFansNeeded
        val (type, target) =
            if (goalThreshold != null && (gateThreshold == null || goalThreshold >= gateThreshold)) {
                Type.FAN_GOAL to goalThreshold
            } else {
                Type.MANDATORY_GATE to gateThreshold!!
            }
        return Result.Active(type, target, scopeTurn, (target - currentFans).coerceAtLeast(0), match, goalUnmet, gateUnmet)
    }

    /**
     * Facts own `hasFanRequirement` when authoritative; [Result.Unknown] preserves the caller's legacy/template
     * value.
     */
    fun resolveHasFanRequirement(legacyValue: Boolean, result: Result): Boolean =
        when (result) {
            is Result.Active -> true
            is Result.Inactive -> false
            is Result.Unknown -> legacyValue
        }
}
